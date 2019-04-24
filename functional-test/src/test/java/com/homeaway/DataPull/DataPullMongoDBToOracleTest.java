package com.homeaway.DataPull;

import com.homeaway.BaseTest;
import com.homeaway.dto.InputJson;
import com.homeaway.dto.migration.Destination;
import com.homeaway.dto.migration.Source;
import com.homeaway.utils.DataPull;
import com.homeaway.utils.DataTypeHandler;
import com.homeaway.utils.db.MongoDB;
import com.homeaway.utils.db.Oracle;
import com.homeaway.validator.Validator;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
public class DataPullMongoDBToOracleTest extends BaseTest {

    private Oracle oracleDb;
    private InputJson inputJson;
    private Source source;
    private Destination destination;
    private MongoDB mongoDB;
    private List<Map<String, Object>> sourceDataList;

    @BeforeMethod
    public void createOracleRDSInstance() throws IOException {
        inputJson = new InputJson("MongoDB-to-Oracle");
        source = inputJson.getMigrations().get(0).getSource();
        mongoDB = new MongoDB(source.getCluster(), source.getLogin(), source.getPassword(), source.getDatabase(), source.getReplicaset());
        destination = inputJson.getMigrations().get(0).getDestination();
        oracleDb = new Oracle(destination.getServer(),destination.getLogin(),destination.getPassword(),destination.getDatabase());
        oracleDb.TruncateTable(destination.getTable());
        mongoDB.checkSourceCollectionAndRecords(source.getDatabase(), source.getCollection());
    }

    @Test(groups = {"mongodb", "oracle"})
    public void mongoDBToOracleTest() {
        sourceDataList = mongoDB.getAllRecords(source.getCollection());
        DataPull.start(inputJson);
        verifyMigratedData();
        sourceDataList = mongoDB.getAllRecords(source.getCollection());
        Assert.assertTrue(sourceDataList.size() == 0, "Post migrate command to delete the collection datapull_test didn't work");
    }

    @Test(groups = {"mongodb", "oracle"})
    public void mongoDbPreMigrateCommandTest() throws IOException {
        source.setPreMigrateCommands(mongoDB.getPreMigrateCommandList(source.getCollection()));
        source.setPostMigrateCommands(null);
        mongoDB.deleteAllRecords(source.getCollection());
        Assert.assertEquals(0, mongoDB.getAllRecords(source.getCollection()).size());
        DataPull.start(inputJson);
        sourceDataList = mongoDB.getAllRecords(source.getCollection());
        verifyMigratedData();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        mongoDB.closeConnection();
        oracleDb.closeConnection();
    }

    private void verifyMigratedData() {
        test.get().pass("DataPull Migration step is completed");
        log.info("Verifying source and destination data.");
        test.get().pass("Source data " + sourceDataList.toString());
        List<Map<String, Object>> destDataList = oracleDb.executeQuery("Select * from " + destination.getTable());
        test.get().pass("Destination data " + destDataList.toString());
        DataTypeHandler.convertValueToBoolean(destDataList, "PasswordResetFlag");
        log.info("Converted dest data {} ", destDataList);
        Validator.validateMigratedData(sourceDataList, destDataList);
        test.get().pass("Source and destination data are matching");

    }


}