# Test writing dataset in filesystem to Kafka

## What is tested?
1. Pre-migrate and post-migrate commands of MSSQL
1. Data can be read from and to an MSSQL table
1. Pre-migrate and post-migrate commands of MySQL
1. Data can be read from and to an MySQL table
1. Both MySQL and MSSQL honor JDBC partitioned reads

## Pre-requisites

1. Confirm DataPull can run locally, by following the instructions under "Confirm DataPull can run locally" in the [parent folder's README](../README.md)

## Steps

1. In terminal, get to this test's folder
    ```shell
    cd ../manual-tests/mssql_dataset_to_mysql_to_mssql/
    ```
1. Stand up dockerised mssql and mysql instances by running
    ```shell
    docker-compose -f docker_rdbms_servers/docker-compose.yml up -d
    ```
   
1. Run DataPull to create a database and generate some data in mssql, and then copy the data to a mysql table; and then copy that data back to mssql
    ```shell
    docker run --network docker_rdbms_servers_default -v $(pwd)/../../core/:/core -v $(pwd):/core/manualtestfolder -w /core -it --rm expedia/spark2.4.7-scala2.11-hadoop2.10.1 spark-submit --packages org.apache.spark:spark-sql_2.11:2.4.7,org.apache.spark:spark-avro_2.11:2.4.7,org.apache.spark:spark-sql-kafka-0-10_2.11:2.4.7 --deploy-mode client --class core.DataPull target/DataMigrationFramework-1.0-SNAPSHOT-jar-with-dependencies.jar manualtestfolder/datapull_input.json local
    ```
1. Run the following command to confirm that the test worked. If the command prints 1000000, then the test was successful.
   ```shell
   docker-compose -f docker_rdbms_servers/docker-compose.yml exec mssql /opt/mssql-tools/bin/sqlcmd -S localhost -U SA -P "<YourStrong@Passw0rd>" -Q 'SELECT count(*) FROM mssqldb.dbo.tbl1'
   ```

### Cleanup

- Clean up dockerised instances by running
```shell script
docker-compose -f docker_rdbms_servers/docker-compose.yml down
```