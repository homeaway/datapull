package com.homeaway.DataPull;

import com.homeaway.BaseTest;
import com.homeaway.dto.InputJson;
import com.homeaway.dto.migration.Destination;
import com.homeaway.dto.migration.Source;
import com.homeaway.utils.DataPull;
import com.homeaway.utils.DataTypeHandler;
import com.homeaway.utils.db.MySQL;
import com.homeaway.utils.db.Oracle;
import com.homeaway.validator.Validator;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

@Slf4j
public class DataPullOracleToMySQLTest extends BaseTest {
    boolean successFlag = false;
    private Oracle oracleDb;
    MySQL mySQL;
    InputJson inputJson;
    Destination destination;
    Source source;

    @BeforeMethod(alwaysRun = true)
    public void createOracleRDSInstance() {
        inputJson = new InputJson("Oracle-to-MySQL");
        source = inputJson.getMigrations().get(0).getSource();
        oracleDb = new Oracle(source.getServer(),source.getLogin(),source.getPassword(),source.getDatabase());
        oracleDb.createSchema();
        log.info("Schema creation is submitted.");
        successFlag = oracleDb.insertData();
        Assert.assertTrue(successFlag, "Error in inserting data.");
        log.info("Inserted Data into oracle database.");
        destination = inputJson.getMigrations().get(0).getDestination();
        mySQL = new MySQL(destination.getServer(), destination.getLogin(), destination.getPassword(), destination.getDatabase());
        mySQL.checkDestinationTableSchema();
        mySQL.executeQuery("TRUNCATE TABLE "+destination.getTable());
    }

    @Test(groups = {"oracle", "mysql"})
    public void oracleToMySQLTest() {
        DataPull.start(inputJson);
        test.get().pass("DataPull Migration step is completed");
        log.info("Verifying source and destination data.");
        List<Map<String, Object>> sourceDataList = oracleDb.executeQuery("Select * from "+ source.getTable());
        DataTypeHandler.convertValueToBoolean(sourceDataList, "PasswordResetFlag");
        log.info("Source data " + sourceDataList.toString());
        test.get().pass("Source data " + sourceDataList.toString());
        List<Map<String, Object>> destDataList = mySQL.executeQuery("Select * from " + destination.getTable());
        test.get().pass("Destination data " + destDataList.toString());
        log.info("Destination data " + destDataList.toString());
        Validator.validateMigratedData(sourceDataList, destDataList);
        test.get().pass("Source and destination data are matching");
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(){
        oracleDb.closeConnection();
        mySQL.closeConnection();
    }

}
