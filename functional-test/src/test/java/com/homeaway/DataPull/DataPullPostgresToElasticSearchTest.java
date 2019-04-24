package com.homeaway.DataPull;

import com.homeaway.BaseTest;
import com.homeaway.constants.FilePath;
import com.homeaway.dto.InputJson;
import com.homeaway.dto.migration.Destination;
import com.homeaway.dto.migration.Source;
import com.homeaway.utils.DataPull;
import com.homeaway.utils.db.ElasticSearch;
import com.homeaway.utils.db.PostgreSQL;
import com.homeaway.validator.Validator;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

@Slf4j
public class DataPullPostgresToElasticSearchTest extends BaseTest {

    InputJson inputJson;
    ElasticSearch elasticSearch;
    PostgreSQL postgreDb;
    Destination destination;
    Source source;

    @BeforeMethod(alwaysRun = true)
    public void createPostgresRDSInstance(Method method) throws IOException {
        if (method.getName().equals("postgresToElasticSearchTest")) {
            inputJson = new InputJson("Postgres-to-ElasticSearch");
        } else if (method.getName().equals("elasticSearchMappingIdAndSaveModeTest")) {
            inputJson = new InputJson("ElasticSearchMappingIdAndSaveMode");
        }
        source = inputJson.getMigrations().get(0).getSource();
        postgreDb = new PostgreSQL(source.getServer(),source.getLogin(),source.getPassword(),source.getDatabase());
        postgreDb.checkSourceTableAndData();
        List<Map<String, Object>> sourceDataList = postgreDb.executeQuery("Select * from " + source.getTable());
        Assert.assertTrue(sourceDataList.size() > 0, "Either table or sample data does not exist.");
        destination = inputJson.getMigrations().get(0).getDestination();
        elasticSearch = new ElasticSearch(destination.getNode(), destination.getPort(), destination.getLogin(), destination.getPassword());
        if (method.getName().equals("elasticSearchMappingIdAndSaveModeTest")) {
            elasticSearch.setSearchSize(20);
        }
        elasticSearch.deleteAllRecords(destination.getIndex(), destination.getType());
        log.info("Deleting data at destination side");
    }

    @Test(groups = {"postgres", "elastic"})
    public void postgresToElasticSearchTest() throws IOException {
        DataPull.start(inputJson);
        test.get().pass("DataPull Migration step is completed");
        verifyMigration();
    }

    @Test(groups = {"postgres", "elastic"})
    public void elasticSearchMappingIdAndSaveModeTest() {
        DataPull.start(inputJson);
        test.get().pass("DataPull Migration step is completed for savemode : create");
        verifyMigration();
        postgreDb.runScript(FilePath.POSTGRES_SOURCE_UPDATE);
        inputJson.getMigrations().get(0).getDestination().setSavemode("update");
        DataPull.start(inputJson);
        verifyMigration();
        test.get().pass("DataPull Migration step is completed for savemode : update");
        postgreDb.runScript(FilePath.POSTGRES_SOURCE_INSERT_UPDATE);
        inputJson.getMigrations().get(0).getDestination().setSavemode("upsert");
        DataPull.start(inputJson);
        test.get().pass("DataPull Migration step is completed for savemode : upsert");
        verifyMigration();
    }

    @AfterMethod(alwaysRun = true)
    public void closeDBConnections(Method method) {
        if (method.getName().equals("elasticSearchMappingIdAndSaveModeTest")) {
            postgreDb.executeQuery("delete from " + source.getTable());
            elasticSearch.deleteAllRecords(destination.getIndex(),destination.getType());
        }
        postgreDb.closeConnection();
        elasticSearch.closeConnection();
    }

    private void verifyMigration(){
        List<Map<String,Object>> sourceDataList = postgreDb.executeQuery("Select * from " +source.getTable());
        test.get().pass("Source data " + sourceDataList.toString());
        List<Map<String,Object>> destDataList = elasticSearch.getAllRecords(destination.getIndex(), destination.getType());
        test.get().pass("Destination data " + destDataList.toString());
        Validator.validateMigratedData(sourceDataList, destDataList);
        test.get().pass("Source and destination data are matching");
    }

}
