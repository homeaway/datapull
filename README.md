# DataPull #
<p align="center">
  <img width="222" height="207" src="./docs/docs/media/logo.png">
</p>
DataPull is a self-service Distributed ETL tool to join and transform data from heterogeneous datastores. It provides users an easy and consistent way to move data from one datastore to another. Supported datastores include, but are not limited to, SQLServer, MySql, Postgres, Cassandra, MongoDB, and Kafka.

## Features
1. JSON configuration-driven data movement - no Java/Scala knowledge needed
1. Join and transform data among heterogeneous datastores (including NoSQL datastores) using ANSI SQL
1. Deploys on Amazon AWS EMR and Fargate; but can run on any Spark cluster
1. Picks up datastore credentials stored in [Hashicorp Vault](https://www.vaultproject.io), [Amazon Secrets Manager](https://aws.amazon.com/secrets-manager/)
1. Execution logs and migration history configurable to Amazon AWS Cloudwatch, S3
1. Use built-in cron scheduler, or call REST API from external schedulers

... and many more features documented [here](https://github.com/homeaway/datapull/blob/master/core/src/main/resources/Samples/Input_Json_Specification.json)

## Run DataPull locally
> Note: DataPull consists of two services, an API written in Java Spring Boot, and a Spark app written in Scala. Although Scala apps can run on JDK 11, per [official docs](https://docs.scala-lang.org/overviews/jdk-compatibility/overview.html#jdk-11-compatibility-notes) it is recommended that Java 8 be used for compiling Scala code. The effort to upgrade to OpenJDK 11+ is tracked [here](https://github.com/homeaway/datapull/issues/2)

### Build and execute within a Dockerised Spark environment
> Pre-requisite: Docker Desktop
* Clone this repo locally and check out the master branch
  ```
  git clone git@github.com:homeaway/datapull.git
  ```
* build the Scala JAR from within the core folder
  ```
  cd datapull/core
  cp ./src/main/resources/application-dev.yml ./src/main/resources/application.yml
  docker run -e MAVEN_OPTS="-Xmx1024M -Xss128M -XX:MetaspaceSize=512M -XX:MaxMetaspaceSize=1024M -XX:+CMSClassUnloadingEnabled" --rm -v "${PWD}":/usr/src/mymaven -v "${PWD}/m2":/root/.m2 -w /usr/src/mymaven maven:3.6.3-jdk-8 mvn clean install
  ```
* Execute a sample JSON input file [Input_Sample_filesystem-to-filesystem.json](core/src/main/resources/Input_Sample_filesystem-to-filesystem.json) that moves data from a CSV file [HelloWorld.csv](core/src/main/resources/SampleData/HelloWorld.csv) to a folder of json files named SampleData_Json.  
  ```
  docker run -v $(pwd):/core -w /core -it --rm gettyimages/spark:2.2.1-hadoop-2.8 spark-submit --deploy-mode client --class core.DataPull target/DataMigrationFramework-1.0-SNAPSHOT-jar-with-dependencies.jar src/main/resources/Samples/Input_Sample_filesystem-to-filesystem.json local
  ```
* Open the relative path target/classes/SampleData_Json to find the result of the DataPull i.e. the data from target/classes/SampleData/HelloWorld.csv transformed into JSON.

## Deploy DataPull to Amazon AWS
Deploying DataPull to Amazon AWS, involves
- installing the DataPull API and Spark JAR in AWS Fargate, using [this runbook](https://homeaway.github.io/datapull/install_on_aws/)
- running DataPulls in AWS EMR, using [this runbook](https://homeaway.github.io/datapull/emr_runbook/)

## Contribute to this project
### Bugs/Feature Requests
Please create an issue in this git repo, using the bug report or feature request templates.
### Documentation
DataPull documentation is available at https://homeaway.github.io/datapull/ . To update this documentation, please do the following steps...
- Create a [Feature Request](https://github.com/homeaway/datapull/issues/new?template=feature_request.md) issue
  - Please fill in the title and the body of the issue. Our suggested title is "Documentation for `<what this documentation is for>`"
- Fork the [DataPull](https://github.com/homeaway/datapull) repo
- In terminal from the root of the repo, run 
```
docker run --rm -it -p 8000:8000 -v ${PWD}/docs:/docs squidfunk/mkdocs-material
```
- Open http://127.0.0.1/8000 to see a preview of the documentation site. You can edit the documentation by following https://www.mkdocs.org/#getting-started
- Once you're done updating the documentation, please commit and push your updates to your forked repo. 
- In terminal from the root of the forked repo, run 
```
docker run --rm -it -v ~/.ssh:/root/.ssh -v ${PWD}:/docs squidfunk/mkdocs-material gh-deploy --config-file /docs/docs/mkdocs.yml
```
- Create 2 PRs (one for forked repo branch that you updated, another for `gh-pages` branch) and we'll review and approve them. 
- Thanks again, for helping make DataPull better!