# Test writing dataset in filesystem to Kafka

## What is tested?
1. Data can be read from a CSV file in the filesystem
1. Data can be written to a Confluent Kafka topic
1. Default (TopicNameStrategy) and specifc (RecordNameStrategy) subject naming strategies work for schema
1. Schema is registered to schema registry, if not present
1. Topic is created, if not present
1. Kafka event key and value can be written in avro and string formats


## Pre-requisites

1. Confirm DataPull can run locally, by following the instructions under "Confirm DataPull can run locally" in the [parent folder's README](../README.md)

## Steps

1. In terminal, get to this test's folder
    ```shell
    cd ../manual-tests/filesystem_dataset_to_kafka/
    ```
1. Stand up a dockerised kafka cluster by running
    ```shell
    CONFLUENT_VERSION="5.5.2" docker-compose -f docker_kafka_server/docker-compose.yml up -d --build
    ```
1. Open the url http://localhost:9021/clusters on your host machine, to open Confluent Control Center for the dockerised Kafka environment. Using the left nav of Confluent Control Center, navigate to `CO Cluster 1 > Topics` and conform that there are 3 topics named `docker-connect-*`
   
1. Run DataPull to copy data from a sample dataset in the filesystem, to a topic `hello_world` in the dockerised kafka cluster
    ```shell
    docker run --network docker_kafka_server_default -v $(pwd)/../../core/:/core -v $(pwd):/core/manualtestfolder -w /core -it --rm expedia/spark2.4.7-scala2.11-hadoop2.10.1 spark-submit --packages org.apache.spark:spark-sql_2.11:2.4.7,org.apache.spark:spark-avro_2.11:2.4.7,org.apache.spark:spark-sql-kafka-0-10_2.11:2.4.7 --deploy-mode client --class core.DataPull target/DataMigrationFramework-1.0-SNAPSHOT-jar-with-dependencies.jar manualtestfolder/datapull_input.json local
    ```
1. Run the previous step again, to test writes to an existing topic with defined schema

1. Open the url http://localhost:9021/clusters on your host machine, to open Confluent Control Center for the dockerised Kafka environment. Using the left nav of Confluent Control Center, navigate to `CO Cluster 1 > Topics` and conform that 
   1. there is a topic named `hello_world`
   1. the topic has 10 events
   1. each event has an string key and an avro value
   1. the schema for the key is shown (the fact that the value's schema is not shown seems to be a limitation of the Kafka Control Center)
   1. the value's schema can be queried using Schema Registry API, by opening http://localhost:8081/subjects/somevaluerecordnamespace.somevaluerecordname/versions/1

### Cleanup

- Clean up dockerised kafka cluster by running
```shell script
CONFLUENT_VERSION="5.5.2" docker-compose -f docker_kafka_server/docker-compose.yml down
```