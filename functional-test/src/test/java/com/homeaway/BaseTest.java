package com.homeaway;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homeaway.constants.Environment;
import com.homeaway.constants.FilePath;
import com.homeaway.dto.InputJson;
import com.homeaway.utils.ExtentManager;
import com.homeaway.utils.GlobalVariables;
import com.homeaway.utils.aws.AwsS3;
import com.homeaway.utils.aws.DataPullApi;
import com.homeaway.utils.aws.RDSInstance;
import com.homeaway.utils.db.GlobalRDSInstance;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.ITestResult;
import org.testng.annotations.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Slf4j
public class BaseTest {

    public static ThreadLocal<ExtentTest> test = new ThreadLocal();
    protected static ExtentReports extent;
    private static ThreadLocal<ExtentTest> parentTest = new ThreadLocal();
    protected static Map<String, String> testDataMap;

    @BeforeSuite(groups = "config")
    public static void beforeSuite() throws IOException {
        log.info("Environment - {}", Environment.envName);
        log.info("Data Pull version - {}", Environment.version);

        testDataMap = getTestData();
        extent = ExtentManager.getInstance();
        initiateRDSCreation();
    }

    @AfterSuite(groups = "config")
    public void cleanUpAfterSuite() throws IOException {
        terminateEMRCluster();
        uploadTestReportInS3();
        deleteRDSInstances();
    }

    private static void initiateRDSCreation() {
        List<String> groupList = Arrays.asList(ExtentManager.getGroupsExecuted().split(","));
        if (System.getProperty("groups") != null) {
            for (String dbEngine : Environment.rdsInstanceRequired) {
                if (groupList.contains("all") || groupList.contains(dbEngine)) {
                    GlobalRDSInstance.GetRDSInstance(dbEngine, true);
                }
            }
        }

    }

    @BeforeClass(groups = "config")
    public void beforeClass() {
        ExtentTest parent = extent.createTest(getClass().getSimpleName());
        parentTest.set(parent);
    }

    @BeforeMethod(groups = "config")
    public void beforeMethod(Method method) {
        log.info("\n****************************************************************************************************\n*\n*    Executing -  {}  \n*\n****************************************************************************************************", method.getName());
        ExtentTest child = parentTest.get().createNode(method.getName());
        test.set(child);
    }

    private static Map<String, String> getTestData() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode globalConfigNode = mapper.readTree(new File(FilePath.GLOBAL_CONFIG_FILE));
        JsonNode testDataNode = globalConfigNode.get("test_data");
        return mapper.convertValue(testDataNode, Map.class);
    }

    //@Test
    private void terminateEMRCluster() {

        if (Environment.parallelExecution) {
            Iterator threadId = GlobalVariables.threadIds.iterator();
            while (threadId.hasNext()) {
                InputJson inputJson = new InputJson("Close-EMR-Cluster", (long) threadId.next());
                triggerDataPullToTerminateCluster(inputJson);
            }
        } else {
            InputJson inputJson = new InputJson("Close-EMR-Cluster");
            triggerDataPullToTerminateCluster(inputJson);
        }
    }

    private void triggerDataPullToTerminateCluster(InputJson inputJson) {

        inputJson.getCluster().setTerminateclusterafterexecution("true");
        DataPullApi api = new DataPullApi();
        int responseCode = api.triggerDataPull(inputJson);
        Assert.assertEquals(responseCode, 202);
        log.info("Request to terminate the cluster " + inputJson.getCluster().getPipelinename() + " is submitted.");

    }

    private void uploadTestReportInS3() throws IOException {
        AwsS3 s3 = new AwsS3();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(new File(FilePath.GLOBAL_CONFIG_FILE));
        if (node.has("s3FunctionalReport")) {
            try {
                String s3path = node.get("s3FunctionalReport").get("s3path").asText();
                s3.uploadTestReport(s3path);
                log.info("Functional Test report is uploaded to S3");
            } catch (Exception ex) {
                log.error(ex.getMessage());
            }
        }
    }

    private void deleteRDSInstances() {
        for (String dbEngine : Environment.rdsInstanceRequired) {
            RDSInstance rdsInstance = GlobalRDSInstance.GetRDSInstance(dbEngine, false);
            if (rdsInstance != null) {
                rdsInstance.deleteRDS();
            }
        }
    }

    @AfterMethod(groups = "config", alwaysRun = true)
    public void afterMethod(ITestResult result, Method method) {
        if (result.getStatus() == ITestResult.FAILURE) {
            test.get().fail(result.getThrowable());
            log.info("{} status - {}.", method.getName(), "failed");
            log.error(result.getThrowable().getMessage());
        } else if (result.getStatus() == ITestResult.SKIP || result.getStatus() == -1) {
            log.info("{} status - {}.", method.getName(), "skipped");
            if (result.getThrowable() != null) {
                test.get().skip(result.getThrowable());
                log.error(result.getThrowable().getMessage());
            } else {
                test.get().skip("Skipped");
            }

        } else {
            test.get().pass("Test is passed");
            log.info("{} status - {}.", method.getName(), "passed");
        }
        extent.flush();
    }

}
