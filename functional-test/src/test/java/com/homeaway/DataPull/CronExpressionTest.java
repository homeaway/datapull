package com.homeaway.DataPull;

import com.homeaway.BaseTest;
import com.homeaway.dto.InputJson;
import com.homeaway.utils.DataPull;
import com.homeaway.utils.aws.DataPullApi;
import com.homeaway.utils.aws.EmrCluster;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronExpression;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.text.ParseException;
import java.util.Date;

@Slf4j
public class CronExpressionTest extends BaseTest {

    @Test
    public void cronExpressionTest() throws ParseException, InterruptedException {
        InputJson inputJson = new InputJson("Close-EMR-Cluster");

        EmrCluster emrCluster = new EmrCluster(inputJson);
        if (emrCluster.getClusterId() == null) {
            DataPull.start(inputJson);
        }
        log.info("Cron Expression " + testDataMap.get("cron_expression"));
        inputJson.getCluster().setCronExpression(testDataMap.get("cron_expression") + "*");
        CronExpression cronExpression = new CronExpression("0 " + testDataMap.get("cron_expression") + "?");
        long nextValidTime = cronExpression.getNextValidTimeAfter(new Date()).getTime();
        long timeDiff = nextValidTime - new Date().getTime();
        log.info("Time Diff " + timeDiff);
        DataPullApi api = new DataPullApi();
        int responseCode = api.triggerDataPull(inputJson);
        Assert.assertEquals(responseCode, 202);
        emrCluster.getClusterId();
        Thread.sleep(timeDiff);
        Assert.assertTrue(emrCluster.waitForClusterToStartRunning());
        test.get().pass("Cluster started again as per the cron expression.");
        Assert.assertTrue(emrCluster.waitForCustomJarToComplete());

    }

    @AfterMethod
    public void removeCronExpression() {
        InputJson inputJson = new InputJson("Close-EMR-Cluster");
        DataPull.start(inputJson);
    }
}
