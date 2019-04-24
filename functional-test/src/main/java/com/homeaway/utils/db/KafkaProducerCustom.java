package com.homeaway.utils.db;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homeaway.constants.FilePath;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.producer.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

@Slf4j
public class KafkaProducerCustom {

    private  String bootstrapServers;
    private String schemaRegistryUrl;

    public KafkaProducerCustom(String bootstrapServers, String schemaRegistryUrl){
        this.bootstrapServers = bootstrapServers;
        this.schemaRegistryUrl = schemaRegistryUrl;
    }

    private Producer<Object, Object> createProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "Users_DataPull_Producer");
        // Serializer for open source external - KafkaAvroSerializer.class
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        props.put("schema.registry.url", schemaRegistryUrl);
        return new KafkaProducer(props);
    }

    public void runProducer(String topic) {
        final Producer<Object, Object> producer = createProducer();

        try {
            File kafkaData = new File(FilePath.KAFKA_DATA_FILE);
            ObjectMapper mapper = new ObjectMapper();
            List<Object> recordList = mapper.readValue(kafkaData, new TypeReference<List<Object>>() {
            });

            String userSchema = "{\"type\":\"record\",\"name\":\"usersdata\",\"fields\":[{\"name\":\"body\",\"type\":{\"name\":\"body_details\",\"type\":\"record\",\"fields\":[{\"name\":\"UserId\",\"type\":\"int\"},{\"name\":\"UserGuid\",\"type\":\"string\"},{\"name\":\"PasswordSalt\",\"type\":\"string\"},{\"name\":\"Password\",\"type\":\"string\"},{\"name\":\"PasswordEncryption\",\"type\":\"string\"},{\"name\":\"PasswordResetFlag\",\"type\":\"boolean\"}]}}]}";
            String bodySchemaString = "{\"name\":\"body_details12\",\"type\":\"record\",\"fields\":[{\"name\":\"UserId\",\"type\":\"int\"},{\"name\":\"UserGuid\",\"type\":\"string\"},{\"name\":\"PasswordSalt\",\"type\":\"string\"},{\"name\":\"Password\",\"type\":\"string\"},{\"name\":\"PasswordEncryption\",\"type\":\"string\"},{\"name\":\"PasswordResetFlag\",\"type\":\"boolean\"}]}";
            Schema.Parser parser = new Schema.Parser();
            Schema schema = parser.parse(userSchema);
            Schema bodySchema = parser.parse(bodySchemaString);
            for (Object jsonRecord : recordList) {
                long time = System.currentTimeMillis();
                JsonNode node = mapper.convertValue(jsonRecord, JsonNode.class);
                GenericRecord avroRecord = new GenericData.Record(schema);
                GenericRecord innerRecord = new GenericData.Record(bodySchema);
                Map<String, Object> nodeMap = mapper.readValue(mapper.writeValueAsString(node), new TypeReference<Map<String, Object>>() {
                });
                for (Map.Entry<String, Object> e : nodeMap.entrySet()) {
                    innerRecord.put(e.getKey(), e.getValue());
                }
                avroRecord.put("body", innerRecord);
                final ProducerRecord<Object, Object> record = new ProducerRecord<Object, Object>(topic, String.valueOf(time), avroRecord);
                RecordMetadata metadata = producer.send(record).get();
                long elapsedTime = System.currentTimeMillis() - time;
                log.info("sent innerRecord(key={} value={}) " +
                                "meta(partition={}, offset={}) time={}\n",
                        record.key(), record.value(), metadata.partition(),
                        metadata.offset(), elapsedTime);

            }
        } catch (IOException | InterruptedException | ExecutionException e) {
            e.printStackTrace();
        } finally {
            producer.flush();
            producer.close();
        }
    }

}
