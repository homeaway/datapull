# DataPull #
<p align="center">
  <img width="222" height="207" src="docs/docs/logo.png">
</p>
DataPull is an ETL tool to join and transform data from heterogeneous datastores. It provides users an easy and consistent way to move data from one datastore to another. Supported datastores include, but are not limited to, SQLServer, MySql, Postgres, Cassandra, MongoDB and Kafka.

## Build and run DataPull locally
### Pre-requisites ###
* IntelliJ with Scala plugin configured. Check out this [Help page](https://www.jetbrains.com/help/idea/managing-plugins.html) if you don't have this plugin
### Steps to run a Debug execution ###
* Clone this repo locally and checkout the master branch
* Open the folder [core](core) in IntelliJ IDE
* By default, this source code is designed to execute a sample JSON input file [Input_Sample_filesystem-to-filesystem.json](core/src/main/resources/Input_Sample_filesystem-to-filesystem.json) that moves data from a CSV file [HelloWorld.csv](core/src/main/resources/SampleData/HelloWorld.csv) to a folder of json files named SampleData_Json. 
* Go to File > Project Structure... , and choose 1.8 (java version) as the Project SDK
* Go to Run > Edit Configurations... , and do the following
    * Create an Application configuration (use the + sign on the top left corner of the modal window)
    * Set the Name to Debug
    * Set the Main Class as DataMigrationFramework
    * Use classpath of module DataMigrationFramework
    * Set JRE to 1.8
    * Click Apply and then OK
* Click Run > Debug 'Debug' to start the debug execution
* Open the relative path target/classes/SampleData_Json to find the result of your data migration i.e. the data from target/classes/SampleData/HelloWorld.csv transformed into JSON.

## Deploy DataPull to Amazon AWS
Deploying DataPull on Amazon AWS, involves
- installing the DataPull API and Spark JAR in AWS Fargate, using [this runbook](https://homeaway.github.io/datapull/installation_emr/)
- running DataPulls in AWS EMR, using [this runbook](https://homeaway.github.io/datapull/emr_runbook/)

## Bugs/Feature Requests
Please create an issue in this git repo, using the bug report or feature request templates.