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
import com.homeaway.datapullclient.config.EMRProperties;
import com.homeaway.datapullclient.input.ClusterProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.stream.Collectors;
import com.homeaway.datapullclient.config.DataPullProperties;

@Slf4j
public class DataPullTask implements Runnable {

    //private Logger log = LoggerManag;
    private static final String MAIN_CLASS = "core.DataPull";

    private final String taskId;

    private final String jsonS3Path;

    private final String s3FilePath;

    private final String jksS3Path;

    private static final String BOOTSTRAP_FOLDER = "datapull-opensource/bootstrapfiles";

    private String s3JarPath;

    @Autowired
    private DataPullClientConfig config;

    private List<String> masterSecurityGroup;
    private List<String> slaveSecurityGroup;

    private static final String JSON_WITH_INPUT_FILE_PATH = "{\r\n  \"jsoninputfile\": {\r\n    \"s3path\": \"%s\"\r\n  }\r\n}";
    private Map<String, Tag> emrTags = new HashMap<>();
    private ClusterProperties clusterProperties;
    private boolean hasBootStrapAction = false;

    public DataPullTask(String taskId, String s3File){
        this.s3FilePath = s3File;
        this.taskId = taskId;
        this.jsonS3Path= s3FilePath+".json";
        this.jksS3Path= s3File+".sh";
    }

    @Override
    public void run(){
        runSparkCluster();
    }

    private void runSparkCluster(){
        if(log.isDebugEnabled())
            log.debug("runSparkCluster ->");

        log.info("Started cluster config taskId = "+taskId);

        AmazonElasticMapReduce emr  = config.getEMRClient();

        ListClustersResult res  = emr.listClusters();

        List<ClusterSummary> clusters  = res.getClusters().stream().filter(x -> x.getName().equals(taskId) &&
                (ClusterState.valueOf(x.getStatus().getState()).equals(ClusterState.RUNNING) || ClusterState.valueOf(x.getStatus().getState()).equals(ClusterState.WAITING) || ClusterState.valueOf(x.getStatus().getState()).equals(ClusterState.STARTING))).
                collect(Collectors.toList());

        DataPullProperties dataPullProperties = config.getDataPullProperties();
        String logFilePath = dataPullProperties.getLogFilePath();
        String s3RepositoryBucketName = dataPullProperties.getS3BucketName();
        s3JarPath = s3JarPath.equals("") ? dataPullProperties.getS3JarPath() : s3JarPath;
        String logPath = logFilePath == null || logFilePath.equals("") ?
                "s3://"+s3RepositoryBucketName+"/" + "datapull-opensource/logs/SparkLogs" : logFilePath;

        String jarPath = s3JarPath == null || s3JarPath.equals("")  ?
                "s3://"+s3RepositoryBucketName+"/" + "datapull-opensource/jars/DataMigrationFramework-1.0-SNAPSHOT-jar-with-dependencies.jar" : s3JarPath;

        if(!clusters.isEmpty()){
            ClusterSummary summary  = clusters.get(0);

            if(summary != null){
                runTaskOnExistingCluster(summary.getId(), jarPath, Boolean.valueOf(Objects.toString(clusterProperties.getTerminateClusterAfterExecution(),"false")),Objects.toString(clusterProperties.getSparksubmitparams(), ""));
            }
        }
        else{
            RunJobFlowResult result = runTaskInNewCluster(emr,logPath, jarPath,Objects.toString(clusterProperties.getSparksubmitparams(), ""));
        }

        log.info("Task "+taskId+" submitted to EMR cluster");

        if(log.isDebugEnabled())
            log.debug("runSparkCluster <- return");
    }

    public static List<String> toList(String[] array) {
        if (array==null) {
            return new ArrayList(0);
        } else {
            int size = array.length;
            List<String> list = new ArrayList(size);
            for(int i = 0; i < size; i++) {
                list.add(array[i]);
            }
            return list;
        }
    }

    private List<String> prepareSparkSubmitParams(String SparkSubmitParams){
        List<String> sparkSubmitParamsList=new ArrayList<>();
        String[] sparkSubmitParamsArray=null;
        if(SparkSubmitParams !=""){
            sparkSubmitParamsArray= SparkSubmitParams.split("\\s+");

            sparkSubmitParamsList.add("spark-submit");

            sparkSubmitParamsList.addAll(toList(sparkSubmitParamsArray));
            sparkSubmitParamsList.add(String.format(JSON_WITH_INPUT_FILE_PATH, jsonS3Path));
        }

        return sparkSubmitParamsList;
    }

    private RunJobFlowResult runTaskInNewCluster(AmazonElasticMapReduce emr, String logPath, String jarPath,String sparkSubmitParams) {

        List<String> sparkSubmitParamsList= prepareSparkSubmitParams(sparkSubmitParams);

        HadoopJarStepConfig runExampleConfig= null;

        if(sparkSubmitParams !=null && !sparkSubmitParams.isEmpty()){
            runExampleConfig = new HadoopJarStepConfig()
                    .withJar("command-runner.jar")
                    .withArgs(sparkSubmitParamsList);
        }
        else{
            runExampleConfig = new HadoopJarStepConfig()
                    .withJar("command-runner.jar")
                    .withArgs("spark-submit", "--class", MAIN_CLASS, jarPath, String.format(JSON_WITH_INPUT_FILE_PATH, jsonS3Path));
        }
        StepConfig customExampleStep = new StepConfig()
                .withName(taskId)
                .withActionOnFailure("CONTINUE")
                .withHadoopJarStep(runExampleConfig);

        Application spark = new Application().withName("Spark");

        EMRProperties emrProperties = config.getEmrProperties();
        int instanceCount = emrProperties.getInstanceCount();
        String masterType = emrProperties.getMasterType();
        String emrSubnet = emrProperties.getSubnet();
        DataPullProperties datapullProperties = config.getDataPullProperties();

        String applicationSubnet = datapullProperties.getApplicationSubnetId();
        emrSubnet  = emrSubnet != null && !emrSubnet.trim().isEmpty() ? emrSubnet : applicationSubnet;
        int count = Integer.valueOf(Objects.toString(clusterProperties.getEmrInstanceCount(), Integer.toString(instanceCount)));
        JobFlowInstancesConfig jobConfig = new JobFlowInstancesConfig()
                .withEc2KeyName(Objects.toString(clusterProperties.getEc2KeyName(), emrProperties.getKeyName())) //passing invalid key will make the process terminate
                //can be removed in case of default vpc
                .withEc2SubnetId(emrSubnet).withMasterInstanceType(Objects.toString(clusterProperties.getMasterInstanceType(), masterType))
                .withInstanceCount(count)
                .withKeepJobFlowAliveWhenNoSteps(!Boolean.valueOf(Objects.toString(clusterProperties.getTerminateClusterAfterExecution(), "true")));

        String masterSG = emrProperties.getMasterSg();
        String slaveSG = emrProperties.getSlaveSg();
        String serviceAccesss = emrProperties.getServiceAccessSg();
        String masterSecurityGroup = Objects.toString(clusterProperties.getMasterSecurityGroup(), masterSG != null ? masterSG : "");
        String slaveSecurityGroup = Objects.toString(clusterProperties.getSlaveSecurityGroup(), slaveSG != null ?slaveSG : "");
        String serviceAccessSecurityGroup = Objects.toString(clusterProperties.getServiceAccessSecurityGroup(), serviceAccesss != null ? serviceAccesss : "");

        if(!masterSecurityGroup.isEmpty()){
            jobConfig.withEmrManagedMasterSecurityGroup(masterSecurityGroup);
        }
        if(!slaveSecurityGroup.isEmpty()){
            jobConfig.withEmrManagedSlaveSecurityGroup(slaveSecurityGroup);
        }
        if(!serviceAccessSecurityGroup.isEmpty()){
            jobConfig.withServiceAccessSecurityGroup(serviceAccessSecurityGroup);
        }
        String slaveType = emrProperties.getSlaveType();
        if(count > 1) {
            jobConfig.withSlaveInstanceType(Objects.toString(clusterProperties.getSlaveInstanceType(),slaveType));
        }

        addTagsToEMRCluster();

        Map<String, String> sparkProperties = new HashMap<>();
        sparkProperties.put("maximizeResourceAllocation", "true");

        String emrReleaseVersion = emrProperties.getEmrReleaseVersion();
        String serviceRole = emrProperties.getServiceRole();
        String jobFlowRole = emrProperties.getJobFlowRole();

        RunJobFlowRequest request = new RunJobFlowRequest()
                .withName(taskId)
                .withReleaseLabel(Objects.toString(clusterProperties.getEmrReleaseVersion(), emrReleaseVersion))
                .withSteps(customExampleStep)
                .withApplications(spark)
                .withLogUri(logPath)
                .withServiceRole(serviceRole)
                .withJobFlowRole(Objects.toString(clusterProperties.getInstanceProfile(), jobFlowRole))  //addAdditionalInfoEntry("maximizeResourceAllocation", "true")
                .withVisibleToAllUsers(true)
                .withTags(emrTags.values()).withConfigurations(new Configuration().withClassification("spark").withProperties(sparkProperties))
                .withInstances(jobConfig);

        if(hasBootStrapAction){
            BootstrapActionConfig bsConfig = new BootstrapActionConfig();
            bsConfig.setName("bootstrapaction");
            bsConfig.setScriptBootstrapAction(new ScriptBootstrapActionConfig().withPath("s3://"+jksS3Path));
            request.withBootstrapActions(bsConfig);
        }

        return emr.runJobFlow(request);
    }

    private void addTagsToEMRCluster() {
        EMRProperties emrProperties = config.getEmrProperties();
        Map<String, String> tags = config.getEmrProperties().getTags();
        addTags(tags);
    }

    private void runTaskOnExistingCluster(String id, String jarPath, boolean terminateClusterAfterExecution,String sparkSubmitParams) {

        List<String> sparkSubmitParamsList= prepareSparkSubmitParams(sparkSubmitParams);

        HadoopJarStepConfig runExampleConfig= null;

        if(sparkSubmitParams !=null && !sparkSubmitParams.isEmpty()){
            runExampleConfig = new HadoopJarStepConfig()
                    .withJar("command-runner.jar")
                    .withArgs(sparkSubmitParamsList);
        }
        else{
            runExampleConfig = new HadoopJarStepConfig()
                    .withJar("command-runner.jar")
                    .withArgs("spark-submit", "--class", MAIN_CLASS, jarPath, String.format(JSON_WITH_INPUT_FILE_PATH, jsonS3Path));
        }

        StepConfig step = new StepConfig()
                .withName(taskId)
                .withHadoopJarStep(runExampleConfig).withActionOnFailure("CONTINUE");

        AddJobFlowStepsRequest req = new AddJobFlowStepsRequest();
        req.withJobFlowId(id);
        req.withSteps(step);
        config.getEMRClient().addJobFlowSteps(req);
        if(terminateClusterAfterExecution){
            addTerminateStep(id);
        }
    }

    private void addTerminateStep(String clusterId) {
        HadoopJarStepConfig runExampleConfig = new HadoopJarStepConfig()
                .withJar("command-runner.jar")
                .withArgs("spark-submit", "--executor-memory", "2g", "--class", MAIN_CLASS, "TerminateCluster.jar", String.format(JSON_WITH_INPUT_FILE_PATH, jsonS3Path));

        StepConfig terminateClusterStep = new StepConfig()
                .withName("TerminateCluster")
                .withHadoopJarStep(runExampleConfig).withActionOnFailure("TERMINATE_CLUSTER");

        AddJobFlowStepsRequest req = new AddJobFlowStepsRequest();
        req.withJobFlowId(clusterId);
        req.withSteps(terminateClusterStep);
        config.getEMRClient().addJobFlowSteps(req);
    }

    public DataPullTask addTag(String tagName, String value) {
        if(tagName != null && value != null && !emrTags.containsKey(tagName)){
            Tag tag = new Tag();tag.setKey(tagName);
            tag.setValue(value);
            emrTags.put(tagName, tag);
        }

        return this;
    }

    @Override
    public String toString() {
        DataPullProperties dataPullProperties = config.getDataPullProperties();
        EMRProperties emrProperties = config.getEmrProperties();
        return "DataPullTask{" +
                "taskId='" + taskId + '\'' +
                ", jsonS3Path='" + jsonS3Path + '\'' +
                ", logFilePath='" + dataPullProperties.getLogFilePath() + '\'' +
                ", s3RepositoryBucketName='" + dataPullProperties.getS3BucketName() + '\'' +
                ", s3JarPath='" + dataPullProperties.getS3JarPath() + '\'' +
                ", instanceCount=" + emrProperties.getInstanceCount() +
                ", masterType='" + emrProperties.getMasterType() + '\'' +
                ", slaveType='" + emrProperties.getSlaveType() + '\'' +
                ", serviceRole='" + emrProperties.getServiceRole() + '\'' +
                ", jobFlowRole='" + emrProperties.getJobFlowRole() + '\'' +
                ", emrReleaseVersion='" + emrProperties.getEmrReleaseVersion() + '\'' +
                ", config=" + config +
                '}';
    }


    public DataPullTask withClusterProperties(ClusterProperties properties) {
        this.clusterProperties = properties;
        return this;
    }

    public DataPullTask addBootStrapAction(boolean hasBootStrapAction) {
        this.hasBootStrapAction = hasBootStrapAction;
        return this;
    }

    public DataPullTask withCustomJar(String customJarPath){
        this.s3JarPath = customJarPath;
        return this;
    }
    
    public DataPullTask addTags(Map<String,String> tags) {
        tags.forEach((tagName,value) -> {
            addTag(tagName, value);
        });
        return this;
    }
}