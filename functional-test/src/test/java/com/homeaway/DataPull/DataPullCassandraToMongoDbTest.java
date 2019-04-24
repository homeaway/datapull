package com.homeaway.DataPull;

import com.homeaway.BaseTest;
import com.homeaway.constants.FilePath;
import com.homeaway.dto.InputJson;
import com.homeaway.dto.migration.Destination;
import com.homeaway.dto.migration.PostMigrateCommand;
import com.homeaway.dto.migration.PreMigrateCommand;
import com.homeaway.dto.migration.Source;
import com.homeaway.utils.DataPull;
import com.homeaway.utils.aws.AwsS3;
import com.homeaway.utils.db.Cassandra;
import com.homeaway.utils.db.MongoDB;
import com.homeaway.validator.Validator;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;


@Slf4j
public class DataPullCassandraToMongoDbTest extends BaseTest {

    InputJson inputJson;
    Destination destination;
    Source source;
    Cassandra cassandra;
    MongoDB mongoDB;

    @BeforeMethod(alwaysRun = true)
    public void createDBConnection() throws IOException {
        inputJson = new InputJson("Cassandra-to-MongoDB");
        source = inputJson.getMigrations().get(0).getSource();
        cassandra = new Cassandra(source.getCluster(), source.getLogin(), source.getPassword());
        cassandra.checkSourceTableAndRecords(source.getKeyspace(), source.getTable());
        destination = inputJson.getMigrations().get(0).getDestination();
        mongoDB = new MongoDB(destination.getCluster(), destination.getLogin(), destination.getPassword(), destination.getDatabase(), destination.getReplicaset());
        mongoDB.deleteAllRecords(destination.getCollection());
        Assert.assertEquals(0, mongoDB.getAllRecords(destination.getCollection()).size());
    }

    @Test(groups = {"cassandra", "mongodb"})
    public void cassandraToMongoDbTest() {
        log.info("cass to mongo execution datapull start");
        DataPull.start(inputJson);
        test.get().pass("DataPull Migration step is completed");
        verifyMigration();
    }

    @Test(groups = {"cassandra", "mongodb"})
    public void jsonPointingToAnotherJsonTest() throws IOException {
        InputJson inputJsonWithPointer = new InputJson("JsonPointingToAnotherInputJson");
        AwsS3 s3Client = new AwsS3();
        s3Client.uploadInputJson(inputJsonWithPointer, inputJson);
        DataPull.start(inputJsonWithPointer);
        test.get().pass("DataPull Migration step is completed");
        verifyMigration();
    }


    @Test(groups = {"cassandra", "mongodb"})
    public void cassandraPreAndPostMigrateCommandTest() throws IOException {
        cassandra.truncateTable(source.getKeyspace(), source.getTable());
        int dataAtSource = cassandra.executeQuery("Select * from " + source.getKeyspace() + "." + source.getTable()).size();
        Assert.assertEquals(0, dataAtSource);
        source.setPreMigrateCommands(getPreMigrateCommandList());
        PostMigrateCommand postMigrateCommand = new PostMigrateCommand();
        postMigrateCommand.setQuery("Truncate table " + source.getKeyspace() + "." + source.getTable());
        source.setPostMigrateCommands(Arrays.asList(postMigrateCommand));
        DataPull.start(inputJson);
        dataAtSource = cassandra.executeQuery("Select * from " + source.getKeyspace() + "." + source.getTable()).size();
        Assert.assertEquals(dataAtSource, 0);
        Assert.assertTrue(mongoDB.getAllRecords(destination.getCollection()).size() > 0);
        test.get().pass("Pre and Post migrate commands are working fine.");
    }


    private void verifyMigration() {
        log.info("Verifying source and destination data.");
        List<Map<String, Object>> sourceDataList = cassandra.executeQuery("Select * from " + source.getKeyspace() + "." + source.getTable());
        test.get().pass("Source data " + sourceDataList.toString());
        List<Map<String, Object>> destDataList = mongoDB.getAllRecords(destination.getCollection());
        test.get().pass("Destination data " + destDataList.toString());
        Validator.validateMigratedData(sourceDataList, destDataList);
        test.get().pass("Source and destination data are matching");
    }

    private List<PreMigrateCommand> getPreMigrateCommandList() throws IOException {
        String dataFilePath = FilePath.CASSANDRA_SOURCE_TABLE_DATA_FILE;
        cassandra.runScript(dataFilePath, source.getKeyspace(), source.getTable(), true);
        return cassandra.preMigrateCommandList;
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        cassandra.closeConnection();
        mongoDB.closeConnection();
    }
}
