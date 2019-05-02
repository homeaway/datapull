package com.homeaway.DataPull;

import com.homeaway.BaseTest;
import com.homeaway.dto.InputJson;
import com.homeaway.dto.Migration;
import com.homeaway.dto.migration.Destination;
import com.homeaway.dto.migration.PostMigrateCommand;
import com.homeaway.dto.migration.Source;
import com.homeaway.utils.DataPull;
import com.homeaway.utils.aws.AwsS3;
import com.homeaway.utils.db.Cassandra;
import com.homeaway.validator.Validator;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

@Slf4j
public class DataPullS3ToCassandraTest extends BaseTest {

    private PostMigrateCommand postMigrateCommand;
    private InputJson inputJson;
    private Destination destination;
    private Source source;
    private Cassandra cassandra;
    private AwsS3 s3;
    private List<Map<String, Object>> sourceDataList;

    @BeforeMethod(alwaysRun = true)
    public void createDBConnection(Method method) throws Exception {
        if (method.getName().equals("s3ToCassandraTest")) {
            inputJson = new InputJson("S3-to-Cassandra");
        } else if (method.getName().equals("verifyS3CopyOperation")) {
            inputJson = new InputJson("S3-to-Cassandra-with-post-migrate-command");
        }

        Migration firstMigration = inputJson.getMigrations().get(0);
        source = inputJson.getMigrations().get(0).getSource();
        if (firstMigration.getSource().getPostMigrateCommand() != null) {
            firstMigration.getSource().getPostMigrateCommand().setS3path(source.getS3path());
        }
        destination = inputJson.getMigrations().get(0).getDestination();
        s3 = new AwsS3(source.getS3path());
        s3.insertData();
        sourceDataList = s3.getListOfRecords();
        postMigrateCommand = source.getPostMigrateCommand();
        if (method.getName().equals("verifyS3CopyOperation")) {
            AwsS3 s3Temp = new AwsS3(postMigrateCommand.getSourceS3Path());
            s3Temp.addPartitionedData();
            Assert.assertTrue(s3Temp.getSubFolderList().size() > 1);
        }
        Assert.assertTrue(sourceDataList.size() > 0, "Data is not available in source S3.");
        cassandra = new Cassandra(destination.getCluster(), destination.getLogin(), destination.getPassword());
        cassandra.checkDestinationTableSchema(destination.getKeyspace(), destination.getTable());
        List<Map<String, Object>> destDataList = cassandra.executeQuery("Select * from " + destination.getKeyspace() + "." + destination.getTable());
        Assert.assertEquals(0, destDataList.size());
    }


    @Test(groups = {"s3", "cassandra"})
    public void s3ToCassandraTest() throws IOException {
        DataPull.start(inputJson);
        test.get().pass("DataPull Migration step is completed");
        verifySourceAndDestinationData();
        //get s3 data after migration
        sourceDataList = s3.getListOfRecords();
        //data is delete as delete operation is performed after migration
        Assert.assertTrue(sourceDataList.size() == 0, "Post migrate command of delete operation didn't work.");
        test.get().pass("Post migrate command of delete operation executed successfully.");
    }

    @Test(groups = {"s3", "cassandra"}, dataProvider = "getRemoveSourceData")
    public void verifyS3CopyOperation(boolean removeSource) throws IOException {
        test.get().pass("Running test with removeSource flag as - " + removeSource);
        s3.setBucketNameAndObjectPrefix(postMigrateCommand.getSourceS3Path());
        Map<String, List<Map<String, Object>>> partitionedSourceData = s3.getPartitionedCsvData();
        inputJson.getMigrations().get(0).getSource().getPostMigrateCommand().setRemoveSource(removeSource);
        DataPull.start(inputJson);
        verifySourceAndDestinationData();
        AwsS3 destinationS3 = new AwsS3(postMigrateCommand.getDestinationS3Path());
        String destinationObjectPrefix = destinationS3.getObjectPrefix();
        for (Map.Entry<String, List<Map<String, Object>>> singlePartitionData : partitionedSourceData.entrySet()) {
            List<Map<String, Object>> sourceData = singlePartitionData.getValue();
            destinationS3.setObjectPrefix(destinationObjectPrefix + singlePartitionData.getKey());
            List<Map<String, Object>> destinationData = destinationS3.getListOfCsvRecords();
            Validator.validateMigratedData(sourceData, destinationData);
        }
        test.get().pass("Partitioned data is moved to destination s3 path.");
        if (removeSource) {
            Assert.assertEquals(0, s3.getPartitionedCsvData().size());
            test.get().pass("Source data has been removed.");
        } else {
            Assert.assertEquals(partitionedSourceData.size(), s3.getPartitionedCsvData().size());
        }

    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        cassandra.closeConnection();
    }

    @DataProvider
    public Object[][] getRemoveSourceData() {
        Object[][] data = {
                {false},
                {true}
        };
        return data;
    }

    private void verifySourceAndDestinationData() {
        log.info("Verifying source and destination data.");
        test.get().pass("Source data " + sourceDataList.toString());
        List<Map<String, Object>> destDataList = cassandra.executeQuery("Select * from " + destination.getKeyspace() + "." + destination.getTable());
        test.get().pass("Destination data " + destDataList.toString());
        Validator.validateMigratedData(sourceDataList, destDataList);
        test.get().pass("Source and destination data are matching");
    }


}
