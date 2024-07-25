package com.homeaway.datapullclient.data;

import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduce;
import com.amazonaws.services.elasticmapreduce.model.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ClusterStepCollection {

    public Map<String,List<DescribeStepRequest>> getClusterResult(AmazonElasticMapReduce emr, int MAX_RETRY) {
        Map<String,List<DescribeStepRequest>> clusterStepMap =  new HashMap<>();
        ListClustersRequest listClustersRequest = new ListClustersRequest();
        //Only get clusters that are in usable state
        listClustersRequest.setClusterStates(Arrays.asList(ClusterState.RUNNING.toString(), ClusterState.WAITING.toString(), ClusterState.STARTING.toString()));

        ListClustersResult listClustersResult = retryListClusters(emr, MAX_RETRY, listClustersRequest);

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -2);
        listClustersRequest.setCreatedBefore(cal.getTime());
        listClustersRequest.setClusterStates(Arrays.asList(ClusterState.RUNNING.toString(), ClusterState.WAITING.toString(), ClusterState.STARTING.toString()));
        while (true) {
            for (ClusterSummary cluster : listClustersResult.getClusters()) {
                List<DescribeStepRequest> dsList = new ArrayList<>();
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
                            DescribeStepRequest ds = new DescribeStepRequest();
                            ds.setClusterId(cluster.getId());
                            ds.setStepId(step.getId());
                            dsList.add(ds);
                        }
                        if (steps.getMarker() != null) {
                            listSteps.setMarker(steps.getMarker());
                            steps = retryListSteps(emr, MAX_RETRY, listSteps);
                        } else {
                            break;
                        }
                    }
                    clusterStepMap.put(cluster.getName(),dsList);
                }
            }
            if (listClustersResult.getMarker() != null) {
                listClustersRequest.setMarker(listClustersResult.getMarker());
                listClustersResult = retryListClusters(emr, MAX_RETRY, listClustersRequest);
            } else {
                break;
            }
        }
        return clusterStepMap;
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
            }
        }
        return listClustersResult;
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
            }
        }
        return steps;
    }
}
