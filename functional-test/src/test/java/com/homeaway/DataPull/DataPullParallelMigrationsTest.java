package com.homeaway.DataPull;

import com.homeaway.BaseTest;
import com.homeaway.dto.InputJson;
import com.homeaway.dto.migration.Destination;
import com.homeaway.dto.migration.Source;
import com.homeaway.utils.DataPull;
import com.homeaway.utils.DataTypeHandler;
import com.homeaway.utils.Wait;
import com.homeaway.utils.db.ElasticSearch;
import com.homeaway.utils.db.MongoDB;
import com.homeaway.utils.db.Oracle;
import com.homeaway.utils.db.SqlServer;
import com.homeaway.validator.Validator;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
public class DataPullParallelMigrationsTest extends BaseTest {
    InputJson inputJson;
    Source source1, source2;
    Destination destination1, destination2;
    ElasticSearch elasticSearch;
    SqlServer sqlServer;
    MongoDB mongoDB;
    Oracle oracleDb;

    @DataProvider
    public static Object[][] getFlag() {
        Object[][] data = {
                {true}, {false}
        };
        return data;
    }

    @BeforeMethod
    public void createDBConnection() throws IOException {
        inputJson = new InputJson("ParallelMigrations");
        source1 = inputJson.getMigrations().get(0).getSource();
        elasticSearch = new ElasticSearch(source1.getNode(), source1.getPort(), source1.getLogin(), source1.getPassword());
        elasticSearch.insertRecords(source1.getIndex(), source1.getType());
        destination1 = inputJson.getMigrations().get(0).getDestination();
        sqlServer = new SqlServer(destination1.getServer(), destination1.getLogin(), destination1.getPassword(), destination1.getDatabase());
        sqlServer.checkDestinationTableSchema();
        sqlServer.executeSelectQuery("TRUNCATE TABLE " + destination1.getTable());
        source2 = inputJson.getMigrations().get(1).getSource();
        mongoDB = new MongoDB(source2.getCluster(), source2.getLogin(), source2.getPassword(), source2.getDatabase(), source2.getReplicaset());
        destination2 = inputJson.getMigrations().get(1).getDestination();
        oracleDb = new Oracle(destination2.getServer(), destination2.getLogin(), destination2.getPassword(), destination2.getDatabase());
        oracleDb.TruncateTable(destination2.getTable());
        mongoDB.checkSourceCollectionAndRecords(source2.getDatabase(), source2.getCollection());
    }

    @Test(dataProvider = "getFlag")
    public void parallelMigrations(boolean parallelMigration) throws IOException {
        test.get().pass(String.format("Running test with parallel migration flag - %s", parallelMigration));
        List<Map<String, Object>> sourceDataList = elasticSearch.getAllRecords(source1.getIndex(), source1.getType());
        inputJson.setParallelmigrations(parallelMigration);
        DataPull.start(inputJson);
        test.get().pass("DataPull Migration step is completed");
        sqlServer = new SqlServer(destination1.getServer(), destination1.getLogin(), destination1.getPassword(), destination1.getDatabase());
        elasticSearch = new ElasticSearch(source1.getNode(), source1.getPort(), source1.getLogin(), source1.getPassword());
        test.get().pass("Source1 data " + sourceDataList.toString());
        List<Map<String, Object>> destDataList = sqlServer.executeSelectQuery("Select * from " + destination1.getTable());
        test.get().pass("Destination1 data " + destDataList.toString());
        log.info("verification of Source1(elastic) and destination1(sqlserver) started");
        Validator.validateMigratedData(sourceDataList, destDataList);
        test.get().pass("Source1(elastic) and destination1(sqlserver) data are matching");
        log.info("Source1(elastic) and destination1(sqlserver) data are matching");
        Wait.inSeconds(2);
        log.info("verification of Source2(mongo) and destination2(oracle) started");
        sourceDataList = mongoDB.getAllRecords(source2.getCollection());
        test.get().pass("Source2 data " + sourceDataList.toString());
        oracleDb = new Oracle(destination2.getServer(), destination2.getLogin(), destination2.getPassword(), destination2.getDatabase());
        destDataList = oracleDb.executeQuery("Select * from " + destination2.getTable());
        test.get().pass("Destination2 data " + destDataList.toString());
        DataTypeHandler.convertValueToBoolean(destDataList, "PasswordResetFlag");
        log.info("Converted test data {} ", destDataList);
        Validator.validateMigratedData(sourceDataList, destDataList);
        test.get().pass("Source2(mongo) and destination2(oracle) data are matching");
        log.info("Source2(mongo) and destination2(oracle) data are matching");
    }

    @AfterMethod
    void cleanUp() {
        elasticSearch.closeConnection();
        oracleDb.closeConnection();
        sqlServer.closeConnection();
        mongoDB.closeConnection();

    }
}
