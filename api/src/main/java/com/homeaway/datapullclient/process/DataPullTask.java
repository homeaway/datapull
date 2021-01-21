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

import java.util.*;
import java.util.stream.Collectors;

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

    private static final String JSON_WITH_INPUT_FILE_PATH = "{\r\n  \"jsoninputfile\": {\r\n    \"s3path\": \"%s\"\r\n  }\r\n}";
    private final Map<String, Tag> emrTags = new HashMap<>();
    private ClusterProperties clusterProperties;
    private boolean hasBootStrapAction;

    public DataPullTask(final String taskId, final String s3File) {
        s3FilePath = s3File;
        this.taskId = taskId;
        jsonS3Path = this.s3FilePath + ".json";
        jksS3Path = s3File + ".sh";
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
    public void run(){
        this.runSparkCluster();
    }

    private void runSparkCluster(){

        DataPullTask.log.info("Started cluster config taskId = " + this.taskId);

        final AmazonElasticMapReduce emr = this.config.getEMRClient();

        final ListClustersResult res = emr.listClusters();

        final List<ClusterSummary> clusters = res.getClusters().stream().filter(x -> x.getName().equals(this.taskId) &&
                (ClusterState.valueOf(x.getStatus().getState()).equals(ClusterState.RUNNING) || ClusterState.valueOf(x.getStatus().getState()).equals(ClusterState.WAITING) || ClusterState.valueOf(x.getStatus().getState()).equals(ClusterState.STARTING))).
                collect(Collectors.toList());

        final DataPullProperties dataPullProperties = this.config.getDataPullProperties();
        final String logFilePath = dataPullProperties.getLogFilePath();
        final String s3RepositoryBucketName = dataPullProperties.getS3BucketName();
        final String logPath = logFilePath == null || logFilePath.equals("") ?
                "s3://"+s3RepositoryBucketName+"/" + "datapull-opensource/logs/SparkLogs" : logFilePath;

        s3JarPath = "s3://" + s3RepositoryBucketName + "/" + "datapull-opensource/jars/DataMigrationFramework-1.0-SNAPSHOT-jar-with-dependencies.jar";

        if(!clusters.isEmpty()){
            final ClusterSummary summary = clusters.get(0);

            if(summary != null){
                this.runTaskOnExistingCluster(summary.getId(), this.s3JarPath, Boolean.valueOf(Objects.toString(this.clusterProperties.getTerminateClusterAfterExecution(), "false")), Objects.toString(this.clusterProperties.getSparksubmitparams(), ""));
            }
        }
        else{
            final RunJobFlowResult result = this.runTaskInNewCluster(emr, logPath, this.s3JarPath, Objects.toString(this.clusterProperties.getSparksubmitparams(), ""));
        }

        DataPullTask.log.info("Task " + this.taskId + " submitted to EMR cluster");
    }

    private List<String> prepareSparkSubmitParams(final String SparkSubmitParams) {
        final List<String> sparkSubmitParamsList = new ArrayList<>();
        String[] sparkSubmitParamsArray=null;
        if(SparkSubmitParams !=""){
            sparkSubmitParamsArray= SparkSubmitParams.split("\\s+");

            sparkSubmitParamsList.add("spark-submit");

            sparkSubmitParamsList.addAll(DataPullTask.toList(sparkSubmitParamsArray));
            sparkSubmitParamsList.add(String.format(DataPullTask.JSON_WITH_INPUT_FILE_PATH, this.jsonS3Path));
        }

        return sparkSubmitParamsList;
    }

    private RunJobFlowResult runTaskInNewCluster(final AmazonElasticMapReduce emr, final String logPath, final String jarPath, final String sparkSubmitParams) {

        final List<String> sparkSubmitParamsList = this.prepareSparkSubmitParams(sparkSubmitParams);

        HadoopJarStepConfig runExampleConfig= null;

        if(sparkSubmitParams !=null && !sparkSubmitParams.isEmpty()){
            runExampleConfig = new HadoopJarStepConfig()
                    .withJar("command-runner.jar")
                    .withArgs(sparkSubmitParamsList);
        }
        else{
            runExampleConfig = new HadoopJarStepConfig()
                    .withJar("command-runner.jar")
                    .withArgs("spark-submit", "--packages", "org.apache.spark:spark-sql-kafka-0-10_2.11:2.4.4,org.apache.spark:spark-avro_2.11:2.4.4", "--class", DataPullTask.MAIN_CLASS, jarPath, String.format(DataPullTask.JSON_WITH_INPUT_FILE_PATH, this.jsonS3Path));
        }
        final StepConfig customExampleStep = new StepConfig()
                .withName(this.taskId)
                .withActionOnFailure("CONTINUE")
                .withHadoopJarStep(runExampleConfig);

        final Application spark = new Application().withName("Spark");

        final EMRProperties emrProperties = this.config.getEmrProperties();
        final int instanceCount = emrProperties.getInstanceCount();
        final String masterType = emrProperties.getMasterType();
        final DataPullProperties datapullProperties = this.config.getDataPullProperties();

        final String applicationSubnet = datapullProperties.getApplicationSubnet1();

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

        if(!masterSecurityGroup.isEmpty()){
            jobConfig.withEmrManagedMasterSecurityGroup(masterSecurityGroup);
        }
        if(!slaveSecurityGroup.isEmpty()){
            jobConfig.withEmrManagedSlaveSecurityGroup(slaveSecurityGroup);
        }
        if(!serviceAccessSecurityGroup.isEmpty()){
            jobConfig.withServiceAccessSecurityGroup(serviceAccessSecurityGroup);
        }
        final String slaveType = emrProperties.getSlaveType();
        if(count > 1) {
            jobConfig.withSlaveInstanceType(Objects.toString(this.clusterProperties.getSlaveInstanceType(), slaveType));
        }

        this.addTagsToEMRCluster();

        final Map<String, String> sparkProperties = new HashMap<>();
        sparkProperties.put("maximizeResourceAllocation", "true");

        final String emrReleaseVersion = emrProperties.getEmrRelease();
        final String serviceRole = emrProperties.getServiceRole();
        final String jobFlowRole = emrProperties.getJobFlowRole();


        Map<String, String> emrfsProperties = new HashMap<String, String>();
        emrfsProperties.put("fs.s3.canned.acl", "BucketOwnerFullControl");

        Configuration myEmrfsConfig = new Configuration()
                .withClassification("emrfs-site")
                .withProperties(emrfsProperties);

        final RunJobFlowRequest request = new RunJobFlowRequest()
                .withName(this.taskId)
                .withReleaseLabel(Objects.toString(this.clusterProperties.getEmrReleaseVersion(), emrReleaseVersion))
                .withSteps(customExampleStep)
                .withApplications(spark)
                .withLogUri(logPath)
                .withServiceRole(Objects.toString(this.clusterProperties.getEmrServiceRole(), serviceRole))
                .withJobFlowRole(Objects.toString(this.clusterProperties.getInstanceProfile(), jobFlowRole))  //addAdditionalInfoEntry("maximizeResourceAllocation", "true")
                .withVisibleToAllUsers(true)
                .withTags(this.emrTags.values()).withConfigurations(new Configuration().withClassification("spark").withProperties(sparkProperties),myEmrfsConfig)
                .withInstances(jobConfig);

        if (this.hasBootStrapAction) {
            final BootstrapActionConfig bsConfig = new BootstrapActionConfig();
            bsConfig.setName("bootstrapaction");
            bsConfig.setScriptBootstrapAction(new ScriptBootstrapActionConfig().withPath("s3://" + this.jksS3Path));
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

        final List<String> sparkSubmitParamsList = this.prepareSparkSubmitParams(sparkSubmitParams);

        HadoopJarStepConfig runExampleConfig= null;

        if(sparkSubmitParams !=null && !sparkSubmitParams.isEmpty()){
            runExampleConfig = new HadoopJarStepConfig()
                    .withJar("command-runner.jar")
                    .withArgs(sparkSubmitParamsList);
        }
        else{
            runExampleConfig = new HadoopJarStepConfig()
                    .withJar("command-runner.jar")
                    .withArgs("spark-submit", "--packages", "org.apache.spark:spark-sql-kafka-0-10_2.11:2.4.4,org.apache.spark:spark-avro_2.11:2.4.4", "--class", DataPullTask.MAIN_CLASS, jarPath, String.format(DataPullTask.JSON_WITH_INPUT_FILE_PATH, this.jsonS3Path));
        }

        final StepConfig step = new StepConfig()
                .withName(this.taskId)
                .withHadoopJarStep(runExampleConfig).withActionOnFailure("CONTINUE");

        final AddJobFlowStepsRequest req = new AddJobFlowStepsRequest();
        req.withJobFlowId(id);
        req.withSteps(step);
        this.config.getEMRClient().addJobFlowSteps(req);
        if(terminateClusterAfterExecution){
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
            tag.setValue(value);
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

    public DataPullTask addBootStrapAction(final boolean hasBootStrapAction) {
        this.hasBootStrapAction = hasBootStrapAction;
        return this;
    }

    public DataPullTask withCustomJar(final String customJarPath) {
        s3JarPath = customJarPath;
        return this;
    }

    public DataPullTask addTags(final Map<String, String> tags) {
        tags.forEach((tagName,value) -> {
            this.addTag(tagName, value);
        });
        return this;
    }
}