package com.homeaway.utils.db;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.util.Arrays;
import java.util.Properties;

@Slf4j
public class KafkaConsumerCustom {

    private KafkaConsumer<String, String> consumer;

    public void createConnection(String bootstrapServer, String schemaRegistryUrl, String groupId){
        Properties props = getKafkaProperties(bootstrapServer, schemaRegistryUrl,groupId);
        consumer = new KafkaConsumer<>(props);
    }

    public Properties getKafkaProperties(String bootstrapServer, String schemaRegistryUrl, String groupId){
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServer);
        props.put("group.id", groupId);
        props.put("schema.registry.url", schemaRegistryUrl);
        props.put("enable.auto.commit", "true");
        props.put("specific.avro.reader", false);
        props.put("session.timeout.ms", 40000);
        props.put("max.poll.records", 100);
        props.put("auto.offset.reset", "latest");
        //Deserializer for open source external - KafkaAvroDeserializer.class
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
        return props;
    }

    public void consumeMessage(String topic){
        consumer.subscribe(Arrays.asList(topic));
        ConsumerRecords<String, String> records = consumer.poll(1*60*1000);
        log.info("Number of consumed messages {}" , records.count() );
        closeConnection();

    }

    public void closeConnection(){
        if(consumer != null)
            consumer.close();
    }
}
