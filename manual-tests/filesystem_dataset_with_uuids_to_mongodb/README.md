# Test writing dataset with UUID in filesystem to MongoDB

## What is tested?
1. Data can be read from a CSV file in the filesystem
1. Data can be written to a MongoDB cluster with authentication
1. DataPull will create the MongoDB collection if it doesn't exist already
1. DataPull will write UUIDs to MongoDB in MongoDB's UUID datatype , if DataPull's uuidToBinary() Spark SQL function is used

## Pre-requisites

1. Docker Desktop
1. Nothing (usually MongoDB) running on the host machine's port 27017
1. Robo3T installed on the host machine

## Steps

1. Open a terminal pointing to the root folder of this repo
1. Please run DataPull locally in a Dockerised environment, by following all the steps defined in the section "Build and execute within a Dockerised Spark environment" of the [README file in the repo's root folder](../../README.md). 
1. Confirm the previous step ran successfully, and that you are in the correct folder, by running the following command in the terminal. It should return at least one json file.
    ```shell
    ls target/classes/SampleData_Json/IntField\=1/
    ```
1. Delete the results of the previous local run by running the following commands in terminal
    ```shell
    sudo chown -R $(whoami):$(whoami) .
    rm -rf target/classes/SampleData_Json/
    ```
1. In terminal, get to this test's folder
    ```shell
    cd ../manual-tests/filesystem_dataset_with_uuids_to_mongodb/
    ```
1. Start a dockerised mongodb server
    ```shell
    docker network create manual-test-network
    docker run -p 27017:27017 --rm -d --network manual-test-network --name mongoserver \
        -e MONGO_INITDB_ROOT_USERNAME=mongoadmin \
        -e MONGO_INITDB_ROOT_PASSWORD=secret \
        mongo:4.0
    ```
1. Run DataPull to copy data from a sample dataset in the filesystem, to a collection `testcollection` in the database `testdb` in the dockerised mongodb server
    ```shell
    docker run --network manual-test-network -v $(pwd)/../../core/:/core -v $(pwd):/core/manualtestfolder -w /core -it --rm expedia/spark2.4.7-scala2.11-hadoop2.10.1 spark-submit --packages org.apache.spark:spark-sql_2.11:2.4.7,org.apache.spark:spark-avro_2.11:2.4.7 --deploy-mode client --class core.DataPull target/DataMigrationFramework-1.0-SNAPSHOT-jar-with-dependencies.jar manualtestfolder/datapull_input.json local
    ```
1. Open Robo3T, and connect to the mongodb server by creating a new connection with
    1. Address: localhost:27017
    1. Authentication
        1. User Name: mongoadmin
        1. Password: secret
1. Using Robo3T, assert that
    1. There exists a database named `testdb`
    1. There exists a collection named `testcollection` within `testdb`
    1. The collection `testcollection` has documents that include a UUID field `uuidfield` with valid UUID values
1. If the previous step is asserted, then this test is successful; else the test has failed. On failure, please report the failure to the DataPull project team. 

### Cleanup
In terminal, run 
```shell
docker stop mongoserver
docker network rm manual-test-network
```