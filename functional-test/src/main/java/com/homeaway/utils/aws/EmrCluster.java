package com.homeaway.utils.aws;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduce;
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduceClientBuilder;
import com.amazonaws.services.elasticmapreduce.model.*;
import com.homeaway.constants.Environment;
import com.homeaway.dto.InputJson;
import com.homeaway.dto.migration.Source;
import com.homeaway.utils.Wait;
import com.homeaway.utils.db.KafkaProducerCustom;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class EmrCluster {

    private String clusterId;
    private AmazonElasticMapReduce emr;
    private InputJson inputJson;

    public EmrCluster(InputJson inputJson) {
        this.inputJson = inputJson;
        emr = AmazonElasticMapReduceClientBuilder.
                standard().
                withRegion(Environment.awsRegion).
                withCredentials(new AWSStaticCredentialsProvider(Environment.credentials)).
                build();
        getClusterId();
    }

    public String getClusterId() {
        String id = null;
        String clusterName = inputJson.getCluster().getEmrClusterName();
        List<ClusterSummary> clusterList = emr.listClusters().getClusters();
        List<ClusterSummary> matchedClusterList = clusterList.stream().filter(e -> e.getName().equalsIgnoreCase(clusterName)).
                filter(e -> BooleanUtils.isFalse(e.getStatus().getState().toLowerCase().contains("terminat"))).
                collect(Collectors.toList());
        if (matchedClusterList.size() == 1) {
            id = matchedClusterList.get(0).getId();
        }
        clusterId = id;
        return id;
    }

    public String waitForClusterToStart() {
        if (clusterId == null) {
            log.info("Waiting for 8 minutes to finish the jenkins job of creating EMR cluster.");
            Wait.inMinutes(8);
        }
        clusterId = getClusterId();
        return clusterId;
    }


    public String getClusterStatus() {
        DescribeClusterRequest clusterRequest = new DescribeClusterRequest();
        clusterRequest.setClusterId(clusterId);
        DescribeClusterResult result = emr.describeCluster(clusterRequest);
        return result.getCluster().getStatus().getState().toLowerCase();

    }

    public String getCustomJarStepStatus() {
        String status = "";
        ListStepsResult stepsResult = emr.listSteps(new ListStepsRequest().withClusterId(clusterId));
        List<StepSummary> stepSummaryList = stepsResult.getSteps();
        String stepName = "";
        stepName = getStepName();
        for (StepSummary step : stepSummaryList) {
            if (step.getName().equalsIgnoreCase(stepName)) {
                status = step.getStatus().getState().toLowerCase();
                break;

            }
        }
        return status;
    }

    public String getCustomJarStepStatus(String StepId) {
        String status = "";
        ListStepsResult stepsResult = emr.listSteps(new ListStepsRequest().withClusterId(clusterId).withStepIds(StepId));
        List<StepSummary> stepSummaryList = stepsResult.getSteps();
        String stepName = "";
        stepName = getStepName();
        for (StepSummary step : stepSummaryList) {
            if (step.getName().equalsIgnoreCase(stepName)) {
                status = step.getStatus().getState().toLowerCase();
                break;

            }
        }
        return status;
    }

    private String getStepIdOfRunningStep() {
        String stepId = "";
        ListStepsResult stepsResult = emr.listSteps(new ListStepsRequest().withClusterId(clusterId).withStepStates(StepState.RUNNING));
        List<StepSummary> stepSummaryList = stepsResult.getSteps();
        String stepName = "";
        stepName = getStepName();
        for (StepSummary step : stepSummaryList) {
            if (step.getName().equalsIgnoreCase(stepName)) {
                stepId = step.getId();
                break;

            }
        }
        return stepId;
    }

    public List<String> getStepArguments() {
        List<String> arguments = new ArrayList<>();
        ListStepsResult stepsResult = emr.listSteps(new ListStepsRequest().withClusterId(clusterId));
        List<StepSummary> stepSummaryList = stepsResult.getSteps();
        String stepName = "";
        stepName = getStepName();
        for (StepSummary step : stepSummaryList) {
            if (step.getName().equalsIgnoreCase(stepName)) {
                arguments = step.getConfig().getArgs();
                break;

            }
        }
        return arguments;
    }

    private String getStepName() {
        String stepName = "";
        switch (Environment.version.trim().toLowerCase()) {
            case "ha-internal":
                stepName = "customJar";
                break;
            case "open-source":
                stepName = inputJson.getCluster().getEmrClusterName();
                break;
        }
        return stepName;
    }

    public boolean waitForClusterToStartRunning() {
        String clusterStatus = getClusterStatus();
        if (clusterStatus.contains("terminat")) {
            log.info("EMR cluster has been terminated.");
            return false;
        }
        int maxCount = 26;
        int i = 0;
        while (!clusterStatus.equalsIgnoreCase("running") && i < maxCount) {
            log.info("EMR cluster is still not in running state. Waiting for 30 seconds more.");
            Wait.inSeconds(30);
            clusterStatus = getClusterStatus();
            if (clusterStatus.contains("terminat"))
                return false;
            i++;
        }
        return clusterStatus.equalsIgnoreCase(ClusterState.RUNNING.toString());

    }

    public boolean waitForCustomJarToComplete() {
        boolean executed = false;
        String stepId = getStepIdOfRunningStep();
        String stepStatus = getCustomJarStepStatus(stepId);
        int maxCount = 14;
        int i = 0;
        while (!stepStatus.equalsIgnoreCase("completed") && i < maxCount) {
            log.info("Custom Jar step is not completed yet. Waiting for 30 seconds more.");
            Wait.inSeconds(30);
            if (inputJson.getMigrations() != null) {
                if (!executed &&
                        inputJson.getMigrations().size() > 0 &&
                        inputJson.getMigrations().get(0).getSources() == null &&
                        inputJson.getMigrations().get(0).getSource().getPlatform().equalsIgnoreCase("kafka")
                        && stepStatus.equalsIgnoreCase("running")
                        && i > 3) {
                    Source source = inputJson.getMigrations().get(0).getSource();
                    KafkaProducerCustom producer = new KafkaProducerCustom(source.getBootstrapServers(), source.getSchemaRegistries());
                    producer.runProducer(source.getTopic());
                    executed = true;
                }
            }
            stepStatus = getCustomJarStepStatus();
            if (stepStatus.equalsIgnoreCase(StepState.FAILED.toString()) ||
                    stepStatus.equalsIgnoreCase(StepState.CANCELLED.toString()) ||
                    stepStatus.equalsIgnoreCase(StepState.PENDING.toString())) {
                return false;
            }
            i++;
        }
        return stepStatus.equalsIgnoreCase("completed");
    }

}
