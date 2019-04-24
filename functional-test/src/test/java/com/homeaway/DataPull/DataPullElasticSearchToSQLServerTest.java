package com.homeaway.DataPull;

import com.homeaway.BaseTest;
import com.homeaway.dto.InputJson;
import com.homeaway.dto.migration.Destination;
import com.homeaway.dto.migration.PostMigrateCommand;
import com.homeaway.dto.migration.PreMigrateCommand;
import com.homeaway.dto.migration.Source;
import com.homeaway.utils.DataPull;
import com.homeaway.utils.db.ElasticSearch;
import com.homeaway.utils.db.SqlServer;
import com.homeaway.validator.Validator;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j

public class DataPullElasticSearchToSQLServerTest extends BaseTest {

    InputJson inputJson;
    ElasticSearch elasticSearch;
    SqlServer sqlServer;
    Destination destination;
    Source source;

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws IOException {
        inputJson = new InputJson("ElasticSearch-to-SQLServer");
        source = inputJson.getMigrations().get(0).getSource();
        elasticSearch = new ElasticSearch(source.getNode(), source.getPort(), source.getLogin(), source.getPassword());
        List<PreMigrateCommand> preMigrateCommandList = elasticSearch.getPreMigrateCommandList(source.getIndex(), source.getType());
        source.setPreMigrateCommands(preMigrateCommandList);
        destination = inputJson.getMigrations().get(0).getDestination();
        sqlServer = new SqlServer(destination.getServer(), destination.getLogin(), destination.getPassword(), destination.getDatabase());
        sqlServer.checkDestinationTableSchema();
        sqlServer.executeSelectQuery("TRUNCATE TABLE " + destination.getTable());
    }

    @Test(groups = {"elastic", "mssql"})
    public void elasticSearchToSqlServerTest() throws IOException {
        String pmCommand = "curl -XDELETE http://" + source.getClusterName() + ":" + source.getPort() + "/" + source.getIndex();
        PostMigrateCommand postMigrateCommand = new PostMigrateCommand();
        postMigrateCommand.setShell(pmCommand);
        List<PostMigrateCommand> postMigrateCommands = new ArrayList<>();
        postMigrateCommands.add(postMigrateCommand);
        elasticSearch.insertRecords(source.getIndex(), source.getType());
        inputJson.getMigrations().get(0).getSource().setPostMigrateCommands(postMigrateCommands);
        List<Map<String, Object>> sourceDataList = elasticSearch.getAllRecords(source.getIndex(), source.getType());
        DataPull.start(inputJson);
        test.get().pass("DataPull Migration step is completed");
        sqlServer = new SqlServer(destination.getServer(), destination.getLogin(), destination.getPassword(), destination.getDatabase());
        elasticSearch = new ElasticSearch(source.getNode(), source.getPort(), source.getLogin(), source.getPassword());
        test.get().pass("Source data " + sourceDataList.toString());
        List<Map<String, Object>> destDataList = sqlServer.executeSelectQuery("Select * from " + destination.getTable());
        test.get().pass("Destination data " + destDataList.toString());
        Validator.validateMigratedData(sourceDataList, destDataList);
        test.get().pass("Source and destination data are matching");
        sourceDataList = elasticSearch.getAllRecords(source.getIndex(), source.getType());
        Assert.assertTrue(sourceDataList.size() == 0,"Post migrate command to delete the index datatools2 didn't work");
    }

    @Test(groups = {"elastic", "mssql"})
    public void elasticSearchPreMigrateCommandTes() throws IOException {
        List<PreMigrateCommand> preMigrateCommandList = elasticSearch.getPreMigrateCommandList(source.getIndex(), source.getType());
        source.setPreMigrateCommands(preMigrateCommandList);
        DataPull.start(inputJson);
        List<Map<String, Object>> sourceDataList = elasticSearch.getAllRecords(source.getIndex(), source.getType());
        test.get().pass("Source data " + sourceDataList.toString());
        List<Map<String, Object>> destDataList = sqlServer.executeSelectQuery("Select * from " + destination.getTable());
        test.get().pass("Destination data " + destDataList.toString());
        Validator.validateMigratedData(sourceDataList, destDataList);
        test.get().pass("Source and destination data are matching");
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        sqlServer.closeConnection();
        elasticSearch.closeConnection();
    }


}
