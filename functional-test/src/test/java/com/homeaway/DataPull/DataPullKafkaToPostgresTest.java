package com.homeaway.DataPull;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homeaway.BaseTest;
import com.homeaway.constants.FilePath;
import com.homeaway.dto.InputJson;
import com.homeaway.dto.migration.Destination;
import com.homeaway.dto.migration.Source;
import com.homeaway.utils.DataPull;
import com.homeaway.utils.db.KafkaConsumerCustom;
import com.homeaway.utils.db.KafkaProducerCustom;
import com.homeaway.utils.db.PostgreSQL;
import com.homeaway.validator.Validator;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
public class DataPullKafkaToPostgresTest extends BaseTest {

    private InputJson inputJson;
    private PostgreSQL postgreDb;
    private Destination destination;
    private Source source;

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        inputJson = new InputJson("Kafka-to-Postgres");
        destination = inputJson.getMigrations().get(0).getDestination();
        postgreDb = new PostgreSQL(destination.getServer(), destination.getLogin(), destination.getPassword(), destination.getDatabase());
        postgreDb.checkDestinationTableSchema(destination.getTable());
        List<Map<String, Object>> destDataList = postgreDb.executeQuery("Select * from " + destination.getTable());
        Assert.assertEquals(destDataList.size(), 0);
    }

    @Test(groups = {"kafka", "postgres"}, dataProvider = "getOffset")
    public void kafkaToPostgresTest(String offset) throws IOException {
        test.get().pass(String.format("Running test with offset - %s", offset));
        inputJson.getMigrations().get(0).getSource().setOffset(offset);
        source = inputJson.getMigrations().get(0).getSource();
        KafkaConsumerCustom consumer = new KafkaConsumerCustom();
        consumer.createConnection(source.getBootstrapServers(), source.getSchemaRegistries(), source.getGroupId());
        consumer.consumeMessage(source.getTopic());
        log.info("Consumed all the non-consumed messages");
        KafkaProducerCustom producer = new KafkaProducerCustom(source.getBootstrapServers(), source.getSchemaRegistries());
        producer.runProducer(source.getTopic());
        log.info("Pushed new messages in Kafka.");
        DataPull.start(inputJson);
        test.get().pass("DataPull Migration step is completed");
        File kafkaDataFile = new File(FilePath.KAFKA_DATA_FILE);
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> sourceDataList = mapper.readValue(kafkaDataFile, new TypeReference<List<Map<String, Object>>>() {
        });
        test.get().pass("Source data " + sourceDataList.toString());
        List<Map<String, Object>> destDataList = postgreDb.executeQuery("Select * from " + destination.getTable());
        test.get().pass("Destination data " + destDataList.toString());
        Validator.validateMigratedData(sourceDataList, destDataList);
        test.get().pass("Source and destination data are matching");
    }


    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        postgreDb.closeConnection();
    }

    @DataProvider
    public Object[][] getOffset() {
        Object[][] data = {
                {"latest"},
                {"earliest"},

        };
        return data;
    }
}
