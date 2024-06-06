/* Copyright (c) 2019 Expedia Group.
 * All rights reserved.  http://www.homeaway.com

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 *      http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.homeaway.datapullclient.process;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.homeaway.datapullclient.config.DataPullClientConfig;
import com.homeaway.datapullclient.config.DataPullContext;
import com.homeaway.datapullclient.config.DataPullContextHolder;
import com.homeaway.datapullclient.config.DataPullProperties;
import com.homeaway.datapullclient.config.EMRProperties;
import com.homeaway.datapullclient.exception.InvalidPointedJsonException;
import com.homeaway.datapullclient.exception.ProcessingException;
import com.homeaway.datapullclient.input.ClusterProperties;
import com.homeaway.datapullclient.input.JsonInputFile;
import com.homeaway.datapullclient.input.Migration;
import com.homeaway.datapullclient.input.Source;
import com.homeaway.datapullclient.service.DataPullClientService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.stream.Collectors;


@Service
@Slf4j
public class DataPullRequestProcessor implements DataPullClientService {

    private static final String JKS_FILE_STRING ="aws s3 cp %s /mnt/bootstrapfiles/ \n";
    private static final String PIPELINE_NAME_SUFFIX = "pipeline";
    private static final String PIPELINE_NAME_DELIMITER = "-";
    private static final int POOL_SIZE = 10;
    private static final String EMR = "emr";
    private static final String CREATOR = "useremailaddress";
    private Schema inputJsonSchema;
    @Autowired
    private DataPullClientConfig config;

    @Value( "${env:dev}" )
    private String env;

    private static final String DATAPULL_HISTORY_FOLDER = "datapull-opensource/history";
    private static final String BOOTSTRAP_FOLDER = "datapull-opensource/bootstrapfiles";

    private final Map<String, Future<?>> tasksMap = new ConcurrentHashMap<>();

    private final ThreadPoolTaskScheduler scheduler;

    List<String> subnets = new ArrayList<>();
    public DataPullRequestProcessor(){
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(POOL_SIZE);
        scheduler.setThreadNamePrefix("DataPull_Thread_Pool");
        scheduler.initialize();
    }

    @PostConstruct
    public void runHistoricalTasksAndReadSchemaJson() throws ProcessingException {
        readExistingDataPullInputs();
        try{
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:/input_json_schema.json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(resources[0].getInputStream()));
            JSONObject jsonSchema = new JSONObject(
                    new JSONTokener(resources[0].getInputStream()));
            inputJsonSchema = SchemaLoader.load(jsonSchema);
        } catch (IOException e) {
            throw new ProcessingException("Unable to create JSON validator", e);
        }
    }

    @Override
    public void runDataPull(String json) throws ProcessingException {
        if (log.isDebugEnabled())
            log.debug("runDataPull -> json = " + json);

        runDataPull(json, false, true);

        if (log.isDebugEnabled())
            log.debug("runDataPull <- return");
    }

    @Override
    public void runSimpleDataPull(String awsenv, String pipelinename) {
        //DO nothing
    }

    private void runDataPull(String json, boolean isStart, boolean validateJson) throws ProcessingException {
        String originalInputJson = json;
        json = extractUserJsonFromS3IfProvided(json, isStart);

        final EMRProperties emrProperties = this.config.getEmrProperties();

        if (log.isDebugEnabled())
            log.debug("runDataPull -> json = " + json + " isStart = " + isStart);

        try{
            if(validateJson){
                json = validateAndEnrich(json);
            }

            log.info("Running datapull for json : " + json + " cron expression = " + isStart + "env =" + env);
            final ObjectNode node = new ObjectMapper().readValue(json, ObjectNode.class);
            List<Map.Entry<String, JsonNode>> result = new LinkedList<Map.Entry<String, JsonNode>>();
            Iterator<Map.Entry<String, JsonNode>> nodes = node.fields();
            while(nodes.hasNext()){
                result.add(nodes.next());
            }
            JsonNode clusterNode = result.stream().filter
                    (y  -> y.getKey().equalsIgnoreCase("cluster")).map(x -> x.getValue()).findAny().get();

            JsonNode migrationsNode = result.stream().filter
                    (y  -> y.getKey().equalsIgnoreCase("migrations")).map(x -> x.getValue()).findAny().get();

            if(clusterNode == null)
                throw new ProcessingException("Invalid Json!!! Cluster properties cannot be null");

            String creator = node.has(CREATOR) ? node.findValue(CREATOR).asText() : "";
            ObjectMapper mapper = new ObjectMapper();
            ClusterProperties reader = mapper.treeToValue(clusterNode, ClusterProperties.class);
            Migration[] myObjects = mapper.treeToValue(migrationsNode, Migration[].class);
            String cronExp = Objects.toString(reader.getCronExpression(), "");
            if(!cronExp.isEmpty())
                cronExp = validateAndProcessCronExpression(cronExp);
            String pipeline = Objects.toString(reader.getPipelineName(), UUID.randomUUID().toString());
            String pipelineEnv = Objects.toString(reader.getAwsEnv(), env);
            DataPullProperties dataPullProperties = config.getDataPullProperties();
            String applicationHistoryFolder = dataPullProperties.getApplicationHistoryFolder();
            String s3RepositoryBucketName = dataPullProperties.getS3BucketName();

            String jobName = pipelineEnv + PIPELINE_NAME_DELIMITER + EMR + PIPELINE_NAME_DELIMITER + pipeline + PIPELINE_NAME_DELIMITER + PIPELINE_NAME_SUFFIX;
            String applicationHistoryFolderPath = applicationHistoryFolder == null || applicationHistoryFolder.isEmpty() ?
                    s3RepositoryBucketName + "/" + DATAPULL_HISTORY_FOLDER : applicationHistoryFolder;
            String bootstrapFilePath = s3RepositoryBucketName + "/" + BOOTSTRAP_FOLDER;
            String filePath = applicationHistoryFolderPath + "/" + jobName;
            String bootstrapFile = jobName + ".sh";
            String jksFilePath = bootstrapFilePath + "/" + bootstrapFile;
            String bootstrapActionStringFromUser = Objects.toString(reader.getBootstrapactionstring(), "");
            String defaultBootstrapString= emrProperties.getDefaultBootstrapString();
            Boolean haveBootstrapAction = createBootstrapScript(myObjects, bootstrapFile, bootstrapFilePath, bootstrapActionStringFromUser, defaultBootstrapString);

            DataPullTask task = createDataPullTask(filePath, jksFilePath, reader, jobName, creator, node.path("sparkjarfile").asText(), haveBootstrapAction);

            if(!isStart) {
                json = originalInputJson.equals(json) ? json : originalInputJson;
                saveConfig(applicationHistoryFolderPath, jobName + ".json", json);
            }
            if (!isStart && tasksMap.containsKey(jobName))
                cancelExistingTask(jobName);
            if(!(isStart  && cronExp.isEmpty())){
                Future<?> future = !cronExp.isEmpty() ? scheduler.schedule(task, new CronTrigger(cronExp))
                        : scheduler.schedule(task, new Date(System.currentTimeMillis() + 1 * 1000));
                tasksMap.put(jobName, future);
            }
        } catch (IOException e) {
            throw new ProcessingException("exception while starting datapull " + e.getLocalizedMessage());
        }

        if (log.isDebugEnabled())
            log.debug("runDataPull <- return");
    }

    List<String> rotateSubnets(){

        if(subnets.isEmpty()){
            subnets= getSubnet();
        }else{
            List<String> subnetIds_shuffled = new ArrayList<>(subnets);
            Collections.rotate(subnetIds_shuffled, 1);
            subnets.clear();
            subnets.addAll(subnetIds_shuffled);
        }
        return subnets;
    }
    public List<String> getSubnet(){
        final DataPullProperties dataPullProperties = this.config.getDataPullProperties();

        List<String> subnetIds = new ArrayList<>();

        subnetIds.add(dataPullProperties.getApplicationSubnet1());

        if (StringUtils.isNotBlank(dataPullProperties.getApplicationSubnet2())) {
            subnetIds.add(dataPullProperties.getApplicationSubnet2());
        }

        if (StringUtils.isNotBlank(dataPullProperties.getApplicationSubnet3())) {
            subnetIds.add(dataPullProperties.getApplicationSubnet3());
        }
        return  subnetIds;

    }
    private StringBuilder createBootstrapString(Object[] paths, String bootstrapActionStringFromUser) throws ProcessingException {

        StringBuilder stringBuilder = new StringBuilder();

        for (Object obj : paths) {
            if (!obj.toString().startsWith("s3://")) {
                throw new ProcessingException("Invalid bootstrap file path. Please give a valid s3 path");
            } else {
                stringBuilder.append(String.format(JKS_FILE_STRING, obj.toString())).append(System.getProperty("line.separator"));
            }
        }
        if (bootstrapActionStringFromUser != null && !bootstrapActionStringFromUser.isEmpty()) {
            stringBuilder.append(bootstrapActionStringFromUser).append(System.getProperty("line.separator"));
        }
        return stringBuilder;
    }

    private Boolean createBootstrapScript(Migration[] myObjects, String bootstrapFile, String bootstrapFilePath, String bootstrapActionStringFromUser, String defaultbootstrapString) throws ProcessingException {

        StringBuilder stringBuilder = new StringBuilder();
        List<String> list = new ArrayList<>();
        Boolean haveBootstrapAction = false;

        for (Migration mig : myObjects) {

            if (mig.getSource() != null) {
                if (mig.getSource().getJksfiles() != null) {
                    list.addAll(Arrays.asList(mig.getSource().getJksfiles()));
                }
            } else {
                Source[] sources = mig.getSources();
                if (sources != null && sources.length > 0) {
                    for (Source source : sources) {
                        if (source.getJksfiles() != null) {
                            list.addAll(Arrays.asList(source.getJksfiles()));
                        }
                    }
                }
            }
            if (mig.getDestination() != null) {
                if (mig.getDestination().getJksfiles() != null) {
                    list.addAll(Arrays.asList(mig.getDestination().getJksfiles()));
                }
            }
        }
        if (!list.isEmpty() || !bootstrapActionStringFromUser.isEmpty()) {
            stringBuilder = createBootstrapString(list.toArray(), bootstrapActionStringFromUser);
        }
        if(!defaultbootstrapString.isEmpty()){
            stringBuilder.append(defaultbootstrapString);
        }
        if (stringBuilder.length() > 0) {
            saveConfig(bootstrapFilePath, bootstrapFile, stringBuilder.toString());
            haveBootstrapAction = true;
        }

        return haveBootstrapAction;
    }

    private DataPullTask createDataPullTask(String fileS3Path, String jksFilePath, ClusterProperties properties, String jobName, String creator, String customJarFilePath,  Boolean haveBootstrapAction) {
        String creatorTag = String.join(" ", Arrays.asList(creator.split(",|;")));
        DataPullTask task = config.getTask(jobName, fileS3Path, jksFilePath,rotateSubnets()).withClusterProperties(properties).withCustomJar(customJarFilePath).haveBootstrapAction(haveBootstrapAction)
                .addTag("Creator", creatorTag).addTag("Env", Objects.toString(properties.getAwsEnv(), env)).addTag("Name", jobName)
                .addTag("AssetProtectionLevel", "99").addTag("ComponentInfo", properties.getComponentInfo())
                .addTag("Portfolio", properties.getPortfolio()).addTag("Product", properties.getProduct()).addTag("Team", properties.getTeam()).addTag("tool", "datapull")
                .addTag("Brand", properties.getBrand())
                .addTag("Application", properties.getApplication())
                .addTag("CostCenter", properties.getCostCenter());

        if (properties.getTags() != null && !properties.getTags().isEmpty()) {
            task.addTags(properties.getTags());
        }

        return task;
    }

    private String validateAndProcessCronExpression(String cronExp) throws ProcessingException {
        String[] cronTokens = cronExp.split(" ");
        long numeralCharCount = Arrays.stream(cronTokens).filter(x -> !x.equals("*")).count();
        if(cronTokens.length > 0 && cronTokens.length != 5){
            throw new ProcessingException("Invalid Cron Expression");
        }
        cronExp = numeralCharCount > 0 ? "0 "+cronExp : "";
        return cronExp;

    }

    private void cancelExistingTask(String taskName) {
        Future<?> task = tasksMap.get(taskName);
        task.cancel(false);
        tasksMap.remove(taskName);
    }

    private void saveConfig(String path, String fileName, String json) throws ProcessingException {

        if (log.isDebugEnabled())
            log.debug("saveConfig -> fileName=" + fileName + " json=" + json);

        AmazonS3 s3Client = config.getS3Client();

        final byte[] contentAsBytes = json.getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream contentsAsStream = new ByteArrayInputStream(contentAsBytes);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType("application/json");
        metadata.addUserMetadata("x-amz-meta-title", fileName);

        PutObjectRequest request = new PutObjectRequest(path, fileName, contentsAsStream, metadata);
        s3Client.putObject(request);
        if (log.isDebugEnabled())
            log.debug("saveConfig <- return = " + path);
    }

    private void readExistingDataPullInputs() throws ProcessingException {
        List<String> files = getPendingTaskNames();
        for(int i = 0; i < files.size()-1311; i++){
            try {
                readAndExcecuteInputJson(files.get(i));
            } catch (InvalidPointedJsonException e) {
                log.error(e.getMessage());
            }
        }
    }

    private List<String> getPendingTaskNames(){
        if (log.isDebugEnabled())
            log.debug("getPendingTaskNames -> ");

        AmazonS3 s3Client = config.getS3Client();

        DataPullProperties dataPullProperties = config.getDataPullProperties();
        String applicationHistoryFolder = dataPullProperties.getApplicationHistoryFolder();
        String s3RepositoryBucketName = dataPullProperties.getS3BucketName();

        String applicationHistoryFolderPath = applicationHistoryFolder == null || applicationHistoryFolder.equals("") ?
                s3RepositoryBucketName + "/" + DATAPULL_HISTORY_FOLDER : applicationHistoryFolder;

        String historyFolderPrefix = applicationHistoryFolderPath.substring(applicationHistoryFolderPath.indexOf("/") + 1);
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest().withBucketName(s3RepositoryBucketName)
                .withPrefix(historyFolderPrefix+"/").withDelimiter("/");
        ObjectListing objectListing = s3Client.listObjects(listObjectsRequest);
        //ObjectListing objectListing = s3Client.listObjects(s3RepositoryBucketName);
        List<String> fileNames = new ArrayList<>();
        while (true) {
            List<String> fn = objectListing.getObjectSummaries().stream().filter(x -> !x.getKey().isEmpty() && x.getKey().endsWith(".json"))
                    .map(x -> x.getKey().substring(x.getKey().indexOf("/")+1)).collect(Collectors.toList());
            fileNames.addAll(fn);
            if (objectListing.isTruncated()) {
                objectListing = s3Client.listNextBatchOfObjects(objectListing);
            } else {
                break;
            }
        }

        log.info("pending task names  = " + fileNames);

        if (log.isDebugEnabled())
            log.debug("getPendingTaskNames <- return " + fileNames);
        return fileNames;
    }

    private String readAndExcecuteInputJson(String fileName) throws ProcessingException {
        if (log.isDebugEnabled())
            log.debug("readAndExcecuteInputJson -> fileName=" + fileName);

        DataPullProperties dataPullProperties = config.getDataPullProperties();
        String applicationHistoryFolder = dataPullProperties.getApplicationHistoryFolder();
        String s3RepositoryBucketName = dataPullProperties.getS3BucketName();

        AmazonS3 s3Client = config.getS3Client();
        String applicationHistoryFolderPath = applicationHistoryFolder == null || applicationHistoryFolder.equals("") ?
                s3RepositoryBucketName + "/" + DATAPULL_HISTORY_FOLDER : applicationHistoryFolder;
        String result = readFileFromS3(s3Client, applicationHistoryFolderPath, fileName.substring(fileName.indexOf("/") + 1));
        runDataPull(result, true, false);

        if (log.isDebugEnabled())
            log.debug("readAndExcecuteInputJson <- return=" + result);

        return result;
    }

    public String readFileFromS3(AmazonS3 s3Client, String bucketName, String path) throws ProcessingException {
        S3Object object = s3Client.getObject(new GetObjectRequest(bucketName, path));
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(object.getObjectContent()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line);
            }
        } catch (IOException exception) {
            throw new ProcessingException("Input json file invalid");
        }
        return out.toString();
    }

    private String validateAndEnrich(String json) throws ProcessingException {
        JSONObject jsonString = new JSONObject(new JSONTokener(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));

        try{
            DataPullContext context = DataPullContextHolder.getContext();
            if(context != null){
                String authenticatedUser = context.getAuthenticatedUser();
                if(authenticatedUser != null){
                    jsonString.put("authenticated_user", authenticatedUser);
                }
            }

            inputJsonSchema.validate(jsonString);
            return jsonString.toString();
        } catch (ValidationException exception) {
            throw new ProcessingException("Json Validation failed "+exception.getMessage(), exception);
        }
    }

    public String extractUserJsonFromS3IfProvided(String json, boolean isStart) throws ProcessingException {
        List<String> jsonS3PathList = new ArrayList<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(json);
            while (jsonNode.has("jsoninputfile")) {
                JsonNode jsonInputFileNode = jsonNode.path("jsoninputfile");
                JsonInputFile jsonInputFile = mapper.treeToValue(jsonInputFileNode, JsonInputFile.class);
                String s3path = jsonInputFile.getS3Path();
                if (jsonS3PathList.contains(s3path)) {
                    throw new ProcessingException("JSON is pointing to same JSON.");
                }
                jsonS3PathList.add(s3path);
                AmazonS3 s3Client = config.getS3Client();
                String bucketName = s3path.substring(0, s3path.indexOf("/"));
                String path = s3path.substring(s3path.indexOf("/") + 1);
                json = readFileFromS3(s3Client, bucketName, path);
                jsonNode = mapper.readTree(json);
            }
        } catch (IOException e) {
            if (isStart) {
                if (jsonS3PathList.size() != 0) {
                    throw new InvalidPointedJsonException("Invalid input json at path - " + jsonS3PathList.get(jsonS3PathList.size() - 1));
                } else {
                    throw new InvalidPointedJsonException("Invalid input json - " + json);
                }

            } else {
                throw new ProcessingException(e.getMessage());
            }
        }
        return json;
    }
}
