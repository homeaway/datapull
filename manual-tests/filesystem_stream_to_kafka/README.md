# Test streaming dataset in filesystem to Kafka

## What is tested?
1. Data can be read as a batch from a folder in the filesystem, with CSV files
2. Data can be written to a Confluent Kafka topic, as a batch
3. Data can be read from a Confluent Kafka topic, as a batch
1. Data can be written as a batch to a folder in the filesystem, as JSON files
1. Data can be read as a stream from a folder in the filesystem, with JSON files
2. Data can be written to a Confluent Kafka topic, as a stream

## Pre-requisites

1. Confirm DataPull can run locally, by following the instructions under "Confirm DataPull can run locally" in the [parent folder's README](../README.md)

## Steps

1. In terminal, get to this test's folder
    ```shell
    cd ../manual-tests/filesystem_stream_to_kafka/
    ```
1. Stand up a dockerised kafka cluster by running
    ```shell
    CONFLUENT_VERSION="5.5.2" docker-compose -f docker_kafka_server/docker-compose.yml up -d --build
    ```
1. Open the url http://localhost:9021/clusters on your host machine, to open Confluent Control Center for the dockerised Kafka environment. Using the left nav of Confluent Control Center, navigate to `CO Cluster 1 > Topics` and conform that there are 3 topics named `docker-connect-*`

1. Run DataPull 
    ```shell
    docker run --network docker_kafka_server_default -v $(pwd)/../../core/:/core -v $(pwd):/core/manualtestfolder -w /core -it --rm expedia/spark2.4.7-scala2.11-hadoop2.10.1 spark-submit --packages org.apache.spark:spark-sql_2.11:2.4.7,org.apache.spark:spark-avro_2.11:2.4.7,org.apache.spark:spark-sql-kafka-0-10_2.11:2.4.7 --deploy-mode client --class core.DataPull target/DataMigrationFramework-1.0-SNAPSHOT-jar-with-dependencies.jar manualtestfolder/datapull_input.json local
    ```
1. Open the url http://localhost:9021/clusters on your host machine, to open Confluent Control Center for the dockerised Kafka environment. Using the left nav of Confluent Control Center, navigate to `CO Cluster 1 > Topics` and conform that
   1. there are 2 topics named `hello_world` and `hello_world2`
   1. each topic has 5 events
   1. each event has an avro key and an avro value
   1. the schema for the key is shown (the fact that the value's schema is not shown seems to be a limitation of the Kafka Control Center)
   1. the value's schema can be queried using Schema Registry API, by opening http://localhost:8081/subjects/somevaluerecordnamespace.somevaluerecordname/versions/1   
   
1. Open the folder core/target/classes/SampleData_Json/HelloWorld and confirm that there is at least one JSON file with the following fields
   1. string field `HelloField`
   1. string field `WorldField`
   1. integer field `IntField`
   
1. On a different terminal but with the same path, run `sudo chown -R $(whoami):$(whoami) .`. If you don't do this, you will get permission error when attempting the next step.

1. Clone one of the JSON files in core/target/classes/SampleData_Json/HelloWorld, into the same folder as a new JSON file. 
   
1. Check in the Confluent Control Center to confirm that the topic `hello_world2` now contains more than 5 events. 

### Cleanup
- Stop the spark app by pressing Ctrl+C at the terminal
- Clean up dockerised kafka cluster by running
   ```shell script
   CONFLUENT_VERSION="5.5.2" docker-compose -f docker_kafka_server/docker-compose.yml down
   ```