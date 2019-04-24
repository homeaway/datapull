package com.homeaway.DataPull;

import com.homeaway.BaseTest;
import com.homeaway.dto.InputJson;
import com.homeaway.dto.Migration;
import com.homeaway.utils.DataPull;
import com.homeaway.utils.aws.AwsS3;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
public class MigrationThresholdTest extends BaseTest {

    private InputJson inputJson;

    @BeforeMethod(alwaysRun = true)
    public void createDBConnection() throws Exception {
        inputJson = new InputJson("S3-to-s3-migration-threshold");
        List<Migration> migrations = inputJson.getMigrations();
        migrations.forEach(x -> {
            try {
                AwsS3 s3 = new AwsS3(x.getSource().getS3path());
                s3.insertData();
            } catch (Exception e) {
                //ignoring exception
            }
        });
    }


    @Test(dataProvider = "migrationThresholdProvider", groups = "s3")
    public void verifyS3CopyOperationWithFailureThreshold(int failureThreshold) throws IOException {
        test.get().pass("Running test with migration threshold " + failureThreshold);
        log.info("verifyS3CopyOperationWithFailureThreshold -> failureThreshold = " + failureThreshold);
        inputJson.setMigrationThreshold(Integer.toString(failureThreshold));
        DataPull.start(inputJson);
        List<Migration> migrations = inputJson.getMigrations();
        AwsS3 firstDestination = new AwsS3(migrations.get(0).getDestination().getS3path());
        Assert.assertTrue(!firstDestination.getListOfRecords().isEmpty(), "Record inserted in first destination");
        AwsS3 lastDestination = new AwsS3(migrations.get(4).getDestination().getS3path());
        if (failureThreshold == 3) {
            List<Map<String, Object>> records = lastDestination.getListOfRecords();
            log.info("verifyS3CopyOperationWithFailureThreshold -> records = " + records);
            Assert.assertTrue(records.isEmpty(), "NO records inserted in last destination");
            test.get().pass("Migration is skipped because of failure threshold limit.");
        } else if (failureThreshold == 4) {
            List<Map<String, Object>> records = lastDestination.getListOfRecords();
            log.info("verifyS3CopyOperationWithFailureThreshold -> records = " + records);
            Assert.assertTrue(!records.isEmpty(), "Records inserted in last destination");
            test.get().pass("Last migration is executed  because of failure threshold limit is greater then the actual failures.");
        }
        log.info("verifyS3CopyOperationWithFailureThreshold <- return");
    }

    @DataProvider
    public Object[][] migrationThresholdProvider() {
        Object[][] data = {
                {3},
                {4}
        };
        return data;
    }
}
