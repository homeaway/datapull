package com.homeaway.DataPull;

import com.homeaway.BaseTest;
import com.homeaway.dto.InputJson;
import com.homeaway.utils.DataPull;
import com.homeaway.utils.aws.EmrCluster;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

@Slf4j
public class DataPullCustomJarTest extends BaseTest {

    @Test
    public void sparkSubmitParamTest() {
        InputJson inputJson = new InputJson("Close-EMR-Cluster");
        log.info("test data - " + testDataMap.get("spark_submit_params"));
        inputJson.getCluster().setSparkSubmitParams(testDataMap.get("spark_submit_params"));
        DataPull.start(inputJson);
        EmrCluster emrCluster = new EmrCluster(inputJson);
        List<String> actualStepArguments = emrCluster.getStepArguments();
        String[] expectedStepArguments = testDataMap.get("spark_submit_params").split("\\s+");
        for (String args : expectedStepArguments) {
            Assert.assertTrue(actualStepArguments.contains(args));
        }
        test.get().pass("EMR cluster step started with custom arguments.");

    }

    @Test
    public void customJarFileTest() {
        InputJson inputJson = new InputJson("Close-EMR-Cluster");
        DataPull.start(inputJson);
        EmrCluster emrCluster = new EmrCluster(inputJson);
        List<String> expectedStepArguments = emrCluster.getStepArguments();
        log.info("test data - " + testDataMap.get("customJarPath"));
        inputJson.setSparkJarFile(testDataMap.get("customJarPath"));
        DataPull.start(inputJson);
        emrCluster = new EmrCluster(inputJson);
        List<String> actualStepArguments = emrCluster.getStepArguments();
        Assert.assertEquals(expectedStepArguments.size(), actualStepArguments.size());
        for (int i = 0; i < expectedStepArguments.size(); i++) {
            if (expectedStepArguments.get(i).contains("SNAPSHOT")) {
                Assert.assertEquals(actualStepArguments.get(i), (testDataMap.get("customJarPath")));
            } else {
                Assert.assertEquals(expectedStepArguments.get(i), actualStepArguments.get(i));
            }
        }
        test.get().pass("EMR cluster step started with custom jar.");
    }

}
