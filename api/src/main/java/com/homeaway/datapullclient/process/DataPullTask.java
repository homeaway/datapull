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

import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduce;
import com.amazonaws.services.elasticmapreduce.model.*;
import com.homeaway.datapullclient.config.DataPullClientConfig;
import com.homeaway.datapullclient.config.DataPullProperties;
import com.homeaway.datapullclient.config.EMRProperties;
import com.homeaway.datapullclient.input.ClusterProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Array;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
public class DataPullTask implements Runnable {

    //private Logger log = LoggerManag;
    private static final String MAIN_CLASS = "core.DataPull";

    private final String taskId;

    private final String jsonS3Path;

    private final String s3FilePath;

    private final String jksS3Path;

    private String s3JarPath;

    @Autowired
    private DataPullClientConfig config;

    private static final String JSON_WITH_INPUT_FILE_PATH = "{\r\n  \"jsoninputfile\": {\r\n    \"s3path\": \"%s\"\r\n  }\r\n}";
    private final Map<String, Tag> emrTags = new HashMap<>();
    private ClusterProperties clusterProperties;
    private Boolean haveBootstrapAction;

    public DataPullTask(final String taskId, final String s3File, final String jksFilePath) {
        s3FilePath = s3File;
        this.taskId = taskId;
        jsonS3Path = this.s3FilePath + ".json";
        jksS3Path = jksFilePath;
    }

    public static List<String> toList(final String[] array) {
        if (array == null) {
            return new ArrayList(0);
        } else {
            final int size = array.length;
            final List<String> list = new ArrayList(size);
            for (int i = 0; i < size; i++) {
                list.add(array[i]);
            }
            return list;
        }
    }

    @Override
    public void run() {
        this.runSparkCluster();
    }

    private void runSparkCluster() {

        DataPullTask.log.info("Started cluster config taskId = " + this.taskId);

        final AmazonElasticMapReduce emr = this.config.getEMRClient();
        final int MAX_RETRY = 16;
        final DataPullProperties dataPullProperties = this.config.getDataPullProperties();
        final String logFilePath = dataPullProperties.getLogFilePath();
        final String s3RepositoryBucketName = dataPullProperties.getS3BucketName();
        final String logPath = logFilePath == null || logFilePath.equals("") ?
                "s3://" + s3RepositoryBucketName + "/" + "datapull-opensource/logs/SparkLogs" : logFilePath;

        s3JarPath = s3JarPath == null || s3JarPath.equals("") ?
                "s3://" + s3RepositoryBucketName + "/" + "datapull-opensource/jars/DataMigrationFramework-1.0-SNAPSHOT-jar-with-dependencies.jar" : s3JarPath;

        List<ClusterSummary> clusters = new ArrayList<>();
        ListClustersRequest listClustersRequest = new ListClustersRequest();
        //Only get clusters that are in usable state
        listClustersRequest.setClusterStates(Arrays.asList(ClusterState.RUNNING.toString(), ClusterState.WAITING.toString(), ClusterState.STARTING.toString()));

        ListClustersResult listClustersResult = retryListClusters(emr, MAX_RETRY, listClustersRequest);
        while (true) {
            for (ClusterSummary cluster : listClustersResult.getClusters()) {
                if (cluster.getName().equalsIgnoreCase(this.taskId)) {
                    clusters.add(cluster);
                }
            }
            if (listClustersResult.getMarker() != null) {
                listClustersRequest.setMarker(listClustersResult.getMarker());
                listClustersResult = retryListClusters(emr, MAX_RETRY, listClustersRequest);
            } else {
                break;
            }
        }

        //find all datapull EMR clusters to be reaped
        List<ClusterSummary> reapClusters = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -2);
        listClustersRequest.setClusterStates(Arrays.asList(ClusterState.WAITING.toString()));
        listClustersRequest.setCreatedBefore(cal.getTime());
        listClustersResult = retryListClusters(emr, MAX_RETRY, listClustersRequest);
        while (true) {
            for (ClusterSummary cluster : listClustersResult.getClusters()) {
                if (cluster.getName().matches(".*-emr-.*-pipeline")) {
                    ListStepsRequest listSteps = new ListStepsRequest().withClusterId(cluster.getId());
                    ListStepsResult steps = retryListSteps(emr, MAX_RETRY, listSteps);

                    Date maxStepEndTime = new Date(0);
                    while (true) {
                        for (StepSummary step : steps.getSteps()) {
                            Date stepEndDate = step.getStatus().getTimeline().getEndDateTime();
                            if (stepEndDate != null && stepEndDate.after(maxStepEndTime)) {
                                maxStepEndTime = stepEndDate;
                            }
                        }
                        if (steps.getMarker() != null) {
                            listSteps.setMarker(steps.getMarker());
                            steps = retryListSteps(emr, MAX_RETRY, listSteps);
                        } else {
                            break;
                        }
                    }
                    if (maxStepEndTime.before(cal.getTime())) {
                        reapClusters.add(cluster);
                    }
                }
            }
            if (listClustersResult.getMarker() != null) {
                listClustersRequest.setMarker(listClustersResult.getMarker());
                listClustersResult = retryListClusters(emr, MAX_RETRY, listClustersRequest);
            } else {
                break;
            }
        }

        DataPullTask.log.info("Number of useable clusters for taskId " + this.taskId + " = " + clusters.size());
        DataPullTask.log.info("Number of reapable clusters = " + reapClusters.size());

        if (!clusters.isEmpty()) {
            final ClusterSummary summary = clusters.get(0);
            final Boolean forceRestart = clusterProperties.getForceRestart();

            if (summary != null && !forceRestart) {
                this.runTaskOnExistingCluster(summary.getId(), this.s3JarPath, Boolean.valueOf(Objects.toString(this.clusterProperties.getTerminateClusterAfterExecution(), "false")), Objects.toString(this.clusterProperties.getSparksubmitparams(), ""));
            } else if (summary != null && forceRestart) {
                emr.terminateJobFlows(new TerminateJobFlowsRequest().withJobFlowIds(summary.getId()));
                DataPullTask.log.info("Task " + this.taskId + " is forced to be terminated");
                this.runTaskInNewCluster(emr, logPath, this.s3JarPath, Objects.toString(this.clusterProperties.getSparksubmitparams(), ""), haveBootstrapAction);
                DataPullTask.log.info("Task " + this.taskId + " submitted to EMR cluster");
            }

        } else {
            final RunJobFlowResult result = this.runTaskInNewCluster(emr, logPath, this.s3JarPath, Objects.toString(this.clusterProperties.getSparksubmitparams(), ""), haveBootstrapAction);
        }

        DataPullTask.log.info("Task " + this.taskId + " submitted to EMR cluster");

        if (!reapClusters.isEmpty()) {
            reapClusters.forEach(cluster -> {
                String clusterIdToReap = cluster.getId();
                String clusterNameToReap = cluster.getName();
                //ensure we don't reap the cluster we just used
                if (!clusters.isEmpty() && clusters.get(0).getId().equals(clusterIdToReap)) {
                    DataPullTask.log.info("Cannot reap in-use cluster " + clusterNameToReap + " with Id " + clusterIdToReap);
                } else {
                    DataPullTask.log.info("About to reap cluster " + clusterNameToReap + " with Id " + clusterIdToReap);
                    emr.terminateJobFlows(new TerminateJobFlowsRequest().withJobFlowIds(Arrays.asList(clusterIdToReap)));
                    DataPullTask.log.info("Reaped cluster " + clusterNameToReap + " with Id " + clusterIdToReap);
                }
            });
        }
    }

    private List<String> arrayToList(Array args) {

        return null;
    }

    private List<String> prepareSparkSubmitParams(final String SparkSubmitParams) {
        final List<String> sparkSubmitParamsList = new ArrayList<>();
        String[] sparkSubmitParamsArray = null;
        if (SparkSubmitParams != "") {
            sparkSubmitParamsArray = SparkSubmitParams.split("\\s+");

            sparkSubmitParamsList.add("spark-submit");

            sparkSubmitParamsList.addAll(DataPullTask.toList(sparkSubmitParamsArray));
        }

        return sparkSubmitParamsList;
    }

    private RunJobFlowResult runTaskInNewCluster(final AmazonElasticMapReduce emr, final String logPath, final String jarPath, final String sparkSubmitParams, final Boolean haveBootstrapAction) {

        HadoopJarStepConfig runExampleConfig = null;

        ArrayList<String> sparkSubmitParamsList = new ArrayList<>();
        if (sparkSubmitParams != null && !sparkSubmitParams.isEmpty()) {
            sparkSubmitParamsList = (ArrayList<String>) this.prepareSparkSubmitParams(sparkSubmitParams);
        } else {
            List<String> sparkBaseParams = new ArrayList<>();
            sparkBaseParams.addAll(toList(new String[]{"spark-submit", "--conf", "spark.default.parallelism=3", "--conf", "spark.storage.blockManagerSlaveTimeoutMs=1200s", "--conf", "spark.executor.heartbeatInterval=900s", "--conf", "spark.driver.extraJavaOptions=-Djavax.net.ssl.trustStore=/etc/pki/java/cacerts/ -Djavax.net.ssl.trustStorePassword=changeit", "--conf", "spark.executor.extraJavaOptions=-Djavax.net.ssl.trustStore=/etc/pki/java/cacerts/ -Djavax.net.ssl.trustStorePassword=changeit", "--packages", "org.apache.spark:spark-sql-kafka-0-10_2.11:2.4.4,org.apache.spark:spark-avro_2.11:2.4.4", "--class", DataPullTask.MAIN_CLASS, jarPath}));
            sparkSubmitParamsList.addAll(sparkBaseParams);
        }

        if (clusterProperties.getSpark_submit_arguments() != null) {
            sparkSubmitParamsList.addAll(clusterProperties.getSpark_submit_arguments());
        } else {
            sparkSubmitParamsList.addAll(Arrays.asList(String.format(DataPullTask.JSON_WITH_INPUT_FILE_PATH, this.jsonS3Path)));
        }

        runExampleConfig = new HadoopJarStepConfig()
                .withJar("command-runner.jar")
                .withArgs(sparkSubmitParamsList);

        final StepConfig customExampleStep = new StepConfig()
                .withName(this.taskId)
                .withActionOnFailure("CONTINUE")
                .withHadoopJarStep(runExampleConfig);

        final Application spark = new Application().withName("Spark");
        final Application hive = new Application().withName("Hive");

        final EMRProperties emrProperties = this.config.getEmrProperties();
        final int instanceCount = emrProperties.getInstanceCount();
        final String masterType = emrProperties.getMasterType();
        final DataPullProperties datapullProperties = this.config.getDataPullProperties();


        final String applicationSubnet = Objects.toString(this.clusterProperties.getSubnetId(),datapullProperties.getApplicationSubnet1());

        final int count = Integer.valueOf(Objects.toString(this.clusterProperties.getEmrInstanceCount(), Integer.toString(instanceCount)));
        final JobFlowInstancesConfig jobConfig = new JobFlowInstancesConfig()
                .withEc2KeyName(Objects.toString(this.clusterProperties.getEc2KeyName(), emrProperties.getEc2KeyName())) //passing invalid key will make the process terminate
                //can be removed in case of default vpc
                .withEc2SubnetId(applicationSubnet).withMasterInstanceType(Objects.toString(this.clusterProperties.getMasterInstanceType(), masterType))
                .withInstanceCount(count)
                .withKeepJobFlowAliveWhenNoSteps(!Boolean.valueOf(Objects.toString(this.clusterProperties.getTerminateClusterAfterExecution(), "true")));

        final String masterSG = emrProperties.getEmrSecurityGroupMaster();
        final String slaveSG = emrProperties.getEmrSecurityGroupSlave();
        final String serviceAccesss = emrProperties.getEmrSecurityGroupServiceAccess();
        final String masterSecurityGroup = Objects.toString(this.clusterProperties.getMasterSecurityGroup(), masterSG != null ? masterSG : "");
        final String slaveSecurityGroup = Objects.toString(this.clusterProperties.getSlaveSecurityGroup(), slaveSG != null ? slaveSG : "");
        final String serviceAccessSecurityGroup = Objects.toString(this.clusterProperties.getServiceAccessSecurityGroup(), serviceAccesss != null ? serviceAccesss : "");

        if (!masterSecurityGroup.isEmpty()) {
            jobConfig.withEmrManagedMasterSecurityGroup(masterSecurityGroup);
        }
        if (!slaveSecurityGroup.isEmpty()) {
            jobConfig.withEmrManagedSlaveSecurityGroup(slaveSecurityGroup);
        }
        if (!serviceAccessSecurityGroup.isEmpty()) {
            jobConfig.withServiceAccessSecurityGroup(serviceAccessSecurityGroup);
        }
        final String slaveType = emrProperties.getSlaveType();
        if (count > 1) {
            jobConfig.withSlaveInstanceType(Objects.toString(this.clusterProperties.getSlaveInstanceType(), slaveType));
        }

        this.addTagsToEMRCluster();

        final Map<String, String> sparkProperties = new HashMap<>();
        sparkProperties.put("maximizeResourceAllocation", "true");

        final String emrReleaseVersion = emrProperties.getEmrRelease();
        final String serviceRole = emrProperties.getServiceRole();
        final String jobFlowRole = emrProperties.getJobFlowRole();
        final String emrSecurityConfiguration = Objects.toString(clusterProperties.getEmr_security_configuration(), "");

        Map<String, String> emrfsProperties = new HashMap<String, String>();
        emrfsProperties.put("fs.s3.canned.acl", "BucketOwnerFullControl");

        Configuration myEmrfsConfig = new Configuration()
                .withClassification("emrfs-site")
                .withProperties(emrfsProperties);

        Map<String, String> sparkHiveProperties = emrProperties.getSparkHiveProperties().entrySet().stream()
                .filter(keyVal -> !keyVal.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        sparkHiveProperties.putAll(this.clusterProperties.getSparkHiveProperties());

        Map<String, String> hiveProperties = emrProperties.getHiveProperties().entrySet().stream()
                .filter(keyVal -> !keyVal.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        hiveProperties.putAll(this.clusterProperties.getHiveProperties());

        Map<String, String> hdfsProperties = this.clusterProperties.getHdfsProperties();

        Map<String, String> sparkDefaultsProperties = this.clusterProperties.getSparkDefaultsProperties();
        Map<String, String> sparkEnvProperties = this.clusterProperties.getSparkEnvProperties();
        Map<String, String> sparkMetricsProperties = this.clusterProperties.getSparkMetricsProperties();

        Configuration sparkHiveConfig = new Configuration()
                .withClassification("spark-hive-site")
                .withProperties(sparkHiveProperties);

        Configuration hiveConfig = new Configuration()
                .withClassification("hive-site")
                .withProperties(hiveProperties);

        Configuration sparkDefaultsConfig = new Configuration()
                .withClassification("spark-defaults")
                .withProperties(sparkDefaultsProperties);
        
        Configuration hdfsConfig = new Configuration()
                .withClassification("hdfs-site")
                .withProperties(hdfsProperties);

        Configuration sparkEnvConfig = new Configuration()
                .withClassification("spark-env")
                .withProperties(sparkEnvProperties);

        Configuration sparkMetricsConfig = new Configuration()
                .withClassification("spark-metrics")
                .withProperties(sparkMetricsProperties);

        final RunJobFlowRequest request = new RunJobFlowRequest()
                .withName(this.taskId)
                .withReleaseLabel(Objects.toString(this.clusterProperties.getEmrReleaseVersion(), emrReleaseVersion))
                .withSteps(customExampleStep)
                .withApplications(spark, hive)
                .withLogUri(logPath)
                .withServiceRole(Objects.toString(this.clusterProperties.getEmrServiceRole(), serviceRole))
                .withJobFlowRole(Objects.toString(this.clusterProperties.getInstanceProfile(), jobFlowRole))  //addAdditionalInfoEntry("maximizeResourceAllocation", "true")
                .withVisibleToAllUsers(true)
                .withTags(this.emrTags.values()).withConfigurations(new Configuration().withClassification("spark").withProperties(sparkProperties), myEmrfsConfig)
                .withInstances(jobConfig);

        if(!this.clusterProperties.getCoreSiteProperties().isEmpty()){
            Configuration coreSiteConfig = new Configuration()
                    .withClassification("core-site")
                    .withProperties(this.clusterProperties.getCoreSiteProperties());
            request.withConfigurations(coreSiteConfig);
        }

        if (!hiveProperties.isEmpty()) {
            request.withConfigurations(hiveConfig);
        }

        if (!sparkHiveProperties.isEmpty()) {
            request.withConfigurations(sparkHiveConfig);
        }

        if (!sparkDefaultsProperties.isEmpty()) {
            request.withConfigurations(sparkDefaultsConfig);
        }

        if (!sparkEnvProperties.isEmpty()) {
            request.withConfigurations(sparkEnvConfig);
        }

        if (!sparkMetricsProperties.isEmpty()) {
            request.withConfigurations(sparkMetricsConfig);
        }
        if (!hdfsProperties.isEmpty()) {
            request.withConfigurations(hdfsConfig);

        if (!emrSecurityConfiguration.isEmpty()) {
            request.withSecurityConfiguration(emrSecurityConfiguration);
        }

        final BootstrapActionConfig bsConfig = new BootstrapActionConfig();
        final ScriptBootstrapActionConfig sbsConfig = new ScriptBootstrapActionConfig();
        String bootstrapActionFilePathFromUser = Objects.toString(clusterProperties.getBootstrap_action_file_path(), "");
        if (!bootstrapActionFilePathFromUser.isEmpty()) {
            bsConfig.setName("Bootstrap action from file");
            sbsConfig.withPath(bootstrapActionFilePathFromUser);
            if (clusterProperties.getBootstrap_action_arguments() != null) {
                sbsConfig.setArgs(clusterProperties.getBootstrap_action_arguments());
            }
            bsConfig.setScriptBootstrapAction(sbsConfig);
            request.withBootstrapActions(bsConfig);
        } else if (haveBootstrapAction) {
            bsConfig.setName("bootstrapaction");
            sbsConfig.withPath("s3://" + this.jksS3Path);
            bsConfig.setScriptBootstrapAction(sbsConfig);
            request.withBootstrapActions(bsConfig);
        }
        return emr.runJobFlow(request);
    }

    private void addTagsToEMRCluster() {
        final EMRProperties emrProperties = this.config.getEmrProperties();
        final Map<String, String> tags = this.config.getEmrProperties().getTags();
        this.addTags(tags);
    }

    private void runTaskOnExistingCluster(final String id, final String jarPath, final boolean terminateClusterAfterExecution, final String sparkSubmitParams) {

        HadoopJarStepConfig runExampleConfig = null;

        List<String> sparkSubmitParamsListOnExistingCluster = new ArrayList<>();
        if (sparkSubmitParams != null && !sparkSubmitParams.isEmpty()) {
            sparkSubmitParamsListOnExistingCluster = this.prepareSparkSubmitParams(sparkSubmitParams);
        } else {
            List<String> sparkBaseParams = new ArrayList<>();
            sparkBaseParams.addAll(toList(new String[]{"spark-submit", "--conf", "spark.default.parallelism=3", "--conf", "spark.storage.blockManagerSlaveTimeoutMs=1200s", "--conf", "spark.executor.heartbeatInterval=900s", "--conf", "spark.driver.extraJavaOptions=-Djavax.net.ssl.trustStore=/etc/pki/java/cacerts/ -Djavax.net.ssl.trustStorePassword=changeit", "--conf", "spark.executor.extraJavaOptions=-Djavax.net.ssl.trustStore=/etc/pki/java/cacerts/ -Djavax.net.ssl.trustStorePassword=changeit", "--packages", "org.apache.spark:spark-sql-kafka-0-10_2.11:2.4.4,org.apache.spark:spark-avro_2.11:2.4.4", "--class", DataPullTask.MAIN_CLASS, jarPath}));
            sparkSubmitParamsListOnExistingCluster.addAll(sparkBaseParams);
        }

        if (clusterProperties.getSpark_submit_arguments() != null) {
            sparkSubmitParamsListOnExistingCluster.addAll(clusterProperties.getSpark_submit_arguments());
        } else {
            sparkSubmitParamsListOnExistingCluster.addAll(Arrays.asList(String.format(DataPullTask.JSON_WITH_INPUT_FILE_PATH, this.jsonS3Path)));
        }

        runExampleConfig = new HadoopJarStepConfig()
                .withJar("command-runner.jar")
                .withArgs(sparkSubmitParamsListOnExistingCluster);

        final StepConfig step = new StepConfig()
                .withName(this.taskId)
                .withHadoopJarStep(runExampleConfig).withActionOnFailure("CONTINUE");

        final AddJobFlowStepsRequest req = new AddJobFlowStepsRequest();
        req.withJobFlowId(id);
        req.withSteps(step);
        this.config.getEMRClient().addJobFlowSteps(req);
        if (terminateClusterAfterExecution) {
            this.addTerminateStep(id);
        }
    }

    private void addTerminateStep(final String clusterId) {
        final HadoopJarStepConfig runExampleConfig = new HadoopJarStepConfig()
                .withJar("command-runner.jar")
                .withArgs("spark-submit", "--executor-memory", "2g", "--class", DataPullTask.MAIN_CLASS, "TerminateCluster.jar", String.format(DataPullTask.JSON_WITH_INPUT_FILE_PATH, this.jsonS3Path));

        final StepConfig terminateClusterStep = new StepConfig()
                .withName("TerminateCluster")
                .withHadoopJarStep(runExampleConfig).withActionOnFailure("TERMINATE_CLUSTER");

        final AddJobFlowStepsRequest req = new AddJobFlowStepsRequest();
        req.withJobFlowId(clusterId);
        req.withSteps(terminateClusterStep);
        this.config.getEMRClient().addJobFlowSteps(req);
    }

    public DataPullTask addTag(final String tagName, final String value) {
        if (tagName != null && value != null && !this.emrTags.containsKey(tagName)) {
            final Tag tag = new Tag();
            tag.setKey(tagName);
            tag.setValue(value.replaceAll("[^a-z@ A-Z0-9_.:/=+\\\\-]", ""));
            this.emrTags.put(tagName, tag);
        }

        return this;
    }

    @Override
    public String toString() {
        final DataPullProperties dataPullProperties = this.config.getDataPullProperties();
        final EMRProperties emrProperties = this.config.getEmrProperties();
        return "DataPullTask{" +
                "taskId='" + this.taskId + '\'' +
                ", jsonS3Path='" + this.jsonS3Path + '\'' +
                ", logFilePath='" + dataPullProperties.getLogFilePath() + '\'' +
                ", s3RepositoryBucketName='" + dataPullProperties.getS3BucketName() + '\'' +
                ", s3JarPath='" + this.s3JarPath + '\'' +
                ", instanceCount=" + emrProperties.getInstanceCount() +
                ", masterType='" + emrProperties.getMasterType() + '\'' +
                ", slaveType='" + emrProperties.getSlaveType() + '\'' +
                ", serviceRole='" + emrProperties.getServiceRole() + '\'' +
                ", jobFlowRole='" + emrProperties.getJobFlowRole() + '\'' +
                ", emrReleaseVersion='" + emrProperties.getEmrRelease() + '\'' +
                ", config=" + this.config +
                '}';
    }


    public DataPullTask withClusterProperties(final ClusterProperties properties) {
        clusterProperties = properties;
        return this;
    }

    public DataPullTask withCustomJar(final String customJarPath) {
        s3JarPath = customJarPath;
        return this;
    }

    public DataPullTask haveBootstrapAction(final Boolean haveBootstrapAction) {
        this.haveBootstrapAction = haveBootstrapAction;
        return this;
    }

    public DataPullTask addTags(final Map<String, String> tags) {
        tags.forEach((tagName, value) -> {
            this.addTag(tagName, value);
        });
        return this;
    }

    private ListStepsResult retryListSteps(final AmazonElasticMapReduce emr, final int MaxRetry, final ListStepsRequest listSteps) {
        ListStepsResult steps = null;
        int retry = 0;
        while (steps == null && retry <= MaxRetry) {
            try {
                steps = emr.listSteps(listSteps);
            } catch (AmazonElasticMapReduceException e) {
                retry++;
                try {
                    int second = ThreadLocalRandom.current().nextInt(1, 5);
                    Thread.sleep(1000 * second);
                } catch (InterruptedException interruptedException) {
                    interruptedException.printStackTrace();
                }
                if (retry == MaxRetry)
                    throw e;
                log.info("Task: " + this.taskId + " emr list steps - AWS ElasticMapReduce reach rate limit, retry times: " + retry);
            }
        }
        return steps;
    }

    private ListClustersResult retryListClusters(final AmazonElasticMapReduce emr, final int MaxRetry, final ListClustersRequest listClustersRequest) {
        ListClustersResult listClustersResult = null;
        int retry = 0;
        while (listClustersResult == null && retry <= MaxRetry) {
            try {
                listClustersResult = emr.listClusters(listClustersRequest);
            } catch (AmazonElasticMapReduceException e) {
                retry++;
                try {
                    int second = ThreadLocalRandom.current().nextInt(1, 5);
                    Thread.sleep(1000 * second);
                } catch (InterruptedException interruptedException) {
                    interruptedException.printStackTrace();
                }
                if (retry == MaxRetry)
                    throw e;
                log.info("Task: " + this.taskId + " emr list clusters - AWS ElasticMapReduce reach rate limit, retry times: " + retry);
            }
        }

        return listClustersResult;
    }
}
