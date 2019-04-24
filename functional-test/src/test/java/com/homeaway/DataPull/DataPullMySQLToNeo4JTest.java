package com.homeaway.DataPull;

import com.homeaway.BaseTest;
import com.homeaway.dto.InputJson;
import com.homeaway.dto.migration.Destination;
import com.homeaway.dto.migration.Source;
import com.homeaway.utils.DataPull;
import com.homeaway.utils.db.MySQL;
import com.homeaway.utils.db.Neo4j;
import com.homeaway.validator.Validator;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

@Slf4j
public class DataPullMySQLToNeo4JTest extends BaseTest {
    InputJson inputJson;
    Neo4j neo4j;
    MySQL mySql;
    String nodeLabel;
    Source source1, source2;
    Destination destination;
    String node1Label;
    String node2Label;
    String relationShipLabel;


    @BeforeMethod(alwaysRun = true)
    public void setUp(Method method) {
        if (method.getName().equals("neo4jRelationsTest")) {
            inputJson = new InputJson("Neo4jRelations");
            source1 = inputJson.getMigrations().get(0).getSources().get(0);
            source2 = inputJson.getMigrations().get(0).getSources().get(1);
            destination = inputJson.getMigrations().get(0).getDestination();
            neo4j = new Neo4j(destination.getCluster(), destination.getLogin(), destination.getPassword());
            node1Label = destination.getNode1().getLabel();
            node2Label = destination.getNode2().getLabel();
            relationShipLabel = destination.getRelation().getLabel();
            neo4j.executeQuery("MATCH (:" + node1Label + ")-[r:" + relationShipLabel + "]-(:" + node2Label + ")  delete r");
            neo4j.executeQuery("MATCH (n:" + node2Label + ") DELETE n");
            neo4j.executeQuery("MATCH (n:" + node1Label + ") DELETE n");
        } else if (method.getName().equals("mySqlToNeo4jTest")) {
            inputJson = new InputJson("MySQL-to-Neo4j");
            destination = inputJson.getMigrations().get(0).getDestination();
            nodeLabel = destination.getNode1().getLabel();
            neo4j = new Neo4j(destination.getCluster(), destination.getLogin(), destination.getPassword());
            neo4j.executeQuery("MATCH (n:" + nodeLabel + ") DELETE n");
            List<Map<String, Object>> destDataList = neo4j.executeQuery("MATCH (n:" + nodeLabel + ") RETURN n");
            Assert.assertEquals(destDataList.size(), 0);
            source1 = inputJson.getMigrations().get(0).getSource();
        }
        mySql = new MySQL(source1.getServer(), source1.getLogin(), source1.getPassword(), source1.getDatabase());
        mySql.checkSourceTableAndData();

    }


    @Test(groups = {"mysql", "neo4j"}, priority = 0)
    public void mySqlToNeo4jTest() {
        DataPull.start(inputJson);
        test.get().pass("DataPull Migration step is completed");
        log.info("Verifying source and destination data.");
        List<Map<String, Object>> sourceDataList = mySql.executeQuery("Select * from " + source1.getTable());
        test.get().pass("Source data " + sourceDataList.toString());
        List<Map<String, Object>> destDataList = neo4j.executeQuery("MATCH (n:" + nodeLabel + ") RETURN n");
        test.get().pass("Destination data " + destDataList.toString());
        Validator.validateMigratedData(sourceDataList, destDataList);
        test.get().pass("Source and destination data are matching");
    }

    @Test(groups = {"mysql", "neo4j"}, priority = 1)
    public void neo4jRelationsTest() {
        DataPull.start(inputJson);
        test.get().pass("DataPull Migration step is completed");
        log.info("Verifying source and destination data.");
        List<Map<String, Object>> sourceDataList = mySql.executeQuery("Select * from " + source1.getTable());
        test.get().pass("Source1 data " + sourceDataList.toString());
        List<Map<String, Object>> destDataList = neo4j.executeQuery("MATCH (n:" + node1Label + ") RETURN n");
        test.get().pass("Destination data of label1 " + destDataList.toString());
        Validator.validateMigratedData(sourceDataList, destDataList);
        test.get().pass("Source1 and destination node1 data are matching");
        List<Map<String, Object>> relationShipList = neo4j.executeQuery("match (n:" + node1Label + ") - [r:" + relationShipLabel + "]->(n1:" + node2Label + ") return r");
        log.info("Relationship records size" + relationShipList.size());
        Assert.assertTrue(!relationShipList.isEmpty() && relationShipList.size() == sourceDataList.size());
        sourceDataList = mySql.executeQuery("Select * from " + source2.getTable());
        test.get().pass("Source2 data " + sourceDataList.toString());
        destDataList = neo4j.executeQuery("MATCH (n:" + node2Label + ") RETURN n");
        test.get().pass("Destination data of label2 " + destDataList.toString());
        Validator.validateMigratedData(sourceDataList, destDataList);
        test.get().pass("Source2 and destination node 2 data are matching");
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        neo4j.closeConnection();
        mySql.closeConnection();
    }
}
