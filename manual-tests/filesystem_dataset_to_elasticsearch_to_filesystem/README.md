# Test writing dataset in filesystem to Elasticsearch, and back

## What is tested?
1. Data can be read from a CSV file in the filesystem
1. Data can be upserted to a new Elasticsearch Index
1. Data can be read from an Elasticsearch index
1. Pre- and post-migrate commands for Elasticsearch work
1. In-line Expressions work

## Pre-requisites

1. Confirm DataPull can run locally, by following the instructions under "Confirm DataPull can run locally" in the [parent folder's README](../README.md)

## Steps

1. In terminal, get to this test's folder
    ```shell
    cd ../manual-tests/filesystem_dataset_to_elasticsearch_to_filesystem/
    ```
   
1. Stand up a dockerised elasticsearch cluster by running
    ```shell
    ELASTIC_VERSION=7.1.0 ELASTIC_SECURITY=true ELASTIC_PASSWORD=changeme docker-compose -f docker_elasticsearch_server/docker-compose.yml up -d
    ```
   
1. Run DataPull to copy data from a sample dataset in the filesystem, to a topic `hello_world` in the dockerised kafka cluster
    ```shell
    docker run --network docker_elasticsearch_server_default -v $(pwd)/../../core/:/core -v $(pwd):/core/manualtestfolder -w /core -it --rm expedia/spark2.4.8-scala2.11-hadoop2.10.1 spark-submit --packages org.apache.spark:spark-sql_2.11:2.4.8,org.apache.spark:spark-avro_2.11:2.4.8,org.apache.spark:spark-sql-kafka-0-10_2.11:2.4.8 --deploy-mode client --class core.DataPull target/DataMigrationFramework-1.0-SNAPSHOT-jar-with-dependencies.jar manualtestfolder/datapull_input.json local
    ```

1. Open the url http://localhost:5601/app/kibana#/dev_tools/console?_g=() on your host machine, to open Kibana Console Center for the dockerised Elasticsearch environment. In the Console, run the command `GET testindex1/_search?pretty=true` and conform that there are 3 documents in the index `testindex1`

1. Open the folder `core/target/classes/SampleData_Csv/{{today's date in the format yyyy-MM-dd}}` and confirm that there is a csv file with 3 records.

### Cleanup

- Clean up dockerised Elasticsearch cluster by running
```shell script
ELASTIC_VERSION=7.1.0 ELASTIC_SECURITY=true ELASTIC_PASSWORD=changeme docker-compose -f docker_elasticsearch_server/docker-compose.yml down
```