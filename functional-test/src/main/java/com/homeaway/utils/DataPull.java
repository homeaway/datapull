package com.homeaway.utils;

import com.homeaway.dto.InputJson;
import com.homeaway.utils.aws.DataPullApi;
import com.homeaway.utils.aws.EmrCluster;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
@Slf4j
public class DataPull {

    public static void start(InputJson inputJson){
        DataPullApi api = new DataPullApi();
        int responseCode = api.triggerDataPull(inputJson);
        Assert.assertEquals(responseCode, 202);
        log.info("Triggered the Data Pull API successfully.");
        EmrCluster emrCluster = new EmrCluster(inputJson);
        String clusterID = emrCluster.waitForClusterToStart();
        log.info("Cluster created with ID - " + clusterID);
        boolean isClusterRunning = emrCluster.waitForClusterToStartRunning();
        Assert.assertTrue(isClusterRunning, "EMR Cluster is not in running state.");
        log.info("cluster started running.");
        boolean isMigrationComplete = emrCluster.waitForCustomJarToComplete();
        Assert.assertTrue(isMigrationComplete,"Data migration is not completed.");
        log.info("Data pull is complete.");
    }


}
