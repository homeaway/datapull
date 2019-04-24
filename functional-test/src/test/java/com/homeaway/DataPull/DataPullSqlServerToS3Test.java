package com.homeaway.DataPull;

import com.homeaway.BaseTest;
import com.homeaway.dto.InputJson;
import com.homeaway.dto.migration.Destination;
import com.homeaway.dto.migration.QueryS3SqlFile;
import com.homeaway.dto.migration.Source;
import com.homeaway.utils.DataPull;
import com.homeaway.utils.aws.AwsS3;
import com.homeaway.utils.db.SqlServer;
import com.homeaway.validator.Validator;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class DataPullSqlServerToS3Test extends BaseTest {

    InputJson inputJson;
    Destination destination;
    Source source;
    SqlServer sqlServer;
    AwsS3 s3;

    @BeforeMethod(alwaysRun = true)
    public void createDBConnection() {
        inputJson = new InputJson("SQLServer-to-S3");
        source = inputJson.getMigrations().get(0).getSource();
        sqlServer = new SqlServer(source.getServer(), source.getLogin(), source.getPassword(), source.getDatabase());
        sqlServer.checkSourceTableAndData();
        destination = inputJson.getMigrations().get(0).getDestination();
    }


    @Test(groups = {"mssql", "s3", "smoke"}, priority = 0)
    public void sqlServerToS3Test() throws IOException {
        s3 = new AwsS3(destination.getS3path());
        DataPull.start(inputJson);
        test.get().pass("DataPull Migration step is completed");
        log.info("Verifying source and destination data.");
        List<Map<String, Object>> sourceDataList = sqlServer.executeSelectQuery("Select * from " + source.getTable());
        test.get().pass("Source data " + sourceDataList.toString());
        List<Map<String, Object>> destDataList = s3.getListOfRecords();
        test.get().pass("Destination data " + destDataList.toString());
        Validator.validateMigratedData(destDataList, sourceDataList);
        test.get().pass("Source and destination data are matching");

    }

    @Test(groups = {"mssql", "s3"})
    public void verifyGroupbyfieldsAndCsvFileFormat() throws IOException {
        inputJson = new InputJson("SQLServer-to-S3-CSV-FileFormat");
        destination = inputJson.getMigrations().get(0).getDestination();
        s3 = new AwsS3(destination.getS3path());
        DataPull.start(inputJson);
        test.get().pass("DataPull Migration step is completed");
        log.info("Verifying source and destination data.");
        List<Map<String, Object>> groupByFieldsMap = sqlServer.executeSelectQuery("Select DISTINCT " + destination.getGroupByFields() + " from " + source.getTable());
        List<String> groupByFields = groupByFieldsMap.stream().map(e -> e.get(destination.getGroupByFields()).toString()).collect(Collectors.toList());
        List<String> subFolderList = s3.getSubFolderList();
        for (String field : groupByFields) {
            Assert.assertTrue(subFolderList.contains(destination.getGroupByFields() + "=" + field));
        }
        test.get().pass("S3 is having required sub folder");
        String s3ObjectPrefix = s3.getObjectPrefix();
        for (String folder : subFolderList) {
            s3.setObjectPrefix(s3ObjectPrefix + folder + "/");
            List<Map<String, Object>> destDataList = s3.getListOfCsvRecords();
            List<Map<String, Object>> sourceDataList = sqlServer.executeSelectQuery("Select * from " + source.getTable() + " where " + destination.getGroupByFields() + " = '" + folder.split("=")[1] + "'");
            Validator.validateMigratedData(destDataList, sourceDataList);

        }
        test.get().pass("Source and destination data are matching");
    }


    @Test(groups = {"mssql", "s3"}, priority = 1)
    public void verifySaveModeFunctionality() throws IOException {
        inputJson.getMigrations().get(0).getDestination().setSavemode("Append");
        s3 = new AwsS3(destination.getS3path());
        List<Map<String, Object>> recordsBeforeMigration = s3.getListOfRecords();
        DataPull.start(inputJson);
        test.get().pass("DataPull Migration step is completed");
        log.info("Verifying source and destination data.");
        List<Map<String, Object>> sourceDataList = sqlServer.executeSelectQuery("Select * from " + source.getTable());
        test.get().pass("Source data " + sourceDataList.toString());
        List<Map<String, Object>> recordsAfterMigration = s3.getListOfRecords();
        test.get().pass("Destination data " + recordsAfterMigration.toString());
        Assert.assertEquals(recordsAfterMigration.size(), recordsBeforeMigration.size() + sourceDataList.size());
        test.get().pass("records got appended in s3.");

    }

    @Test(groups = {"mssql", "s3"}, priority = 3)
    public void queryS3FileTest() throws IOException {
        QueryS3SqlFile queryS3SqlFile = new QueryS3SqlFile();
        queryS3SqlFile.setS3path(testDataMap.get("s3_sql_file"));
        source.setQueryS3SqlFile(queryS3SqlFile);
        DataPull.start(inputJson);
        test.get().pass("DataPull Migration step is completed");
        log.info("Verifying source and destination data.");
        List<Map<String, Object>> sourceDataList = sqlServer.executeSelectQuery("Select * from " + source.getTable());
        test.get().pass("Source data " + sourceDataList.toString());
        s3 = new AwsS3(destination.getS3path());
        List<Map<String, Object>> destinationData = s3.getListOfRecords();
        test.get().pass("Destination data " + destinationData.toString());
        Assert.assertEquals(destinationData.size(), 1, "Size of destination data");
        Assert.assertEquals(destinationData.get(0).get("UserId"), 1, "Size of destination data");
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        sqlServer.closeConnection();
    }


}