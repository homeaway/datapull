# Change Log

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)
and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html)

## [0.1.43] - 2021-09-22

-- adding optional forcerestart in datapull

### Changed
-  api/src/main/java/com/homeaway/datapullclient/config/EMRProperties.java
-  api/src/main/java/com/homeaway/datapullclient/input/ClusterProperties.java
-  api/src/main/java/com/homeaway/datapullclient/process/DataPullTask.java
-  core/src/main/resources/Samples/Input_Json_Specification.json

## [0.1.42] - 2021-08-03

- Fixed a bug which was mandating aws secret access key for every migration

### Changed

- core/src/main/scala/core/DataPull.scala

## [0.1.41] - 2021-07-29

- Added optional attributes to make it easier to process Kafka streams created by MongoDB kafka connect source
- Added support for sql queries (jdbc and spark sql queries) to passed in as sql files from filesystem/S3
- Fixed bug in AWS Secrets Manager support, and added support for `inlinesecret{{}}`
- Increased timeout for API health check
- Fix bug of occasionally expiring CloudWatch log tokens
- Fix bug of EMR clusters not being re-used occasionally

### Changed

- Added optional attributes to make it easier to process Kafka streams created by MongoDB kafka connect source
  - core/src/main/scala/core/DataFrameFromTo.scala
  - core/src/main/resources/Samples/Input_Json_Specification.json
- Added support for sql queries (jdbc and spark sql queries) to passed in as sql files from filesystem/S3
  - core/src/main/scala/core/DataPull.scala
  - core/src/main/scala/helper/Helper.scala
  - core/src/main/resources/Samples/Input_Json_Specification.json
  - core/src/main/scala/core/Migration.scala
- Fixed bug in AWS Secrets Manager support, and added support for `inlinesecret{{}}`
  - core/src/main/scala/core/Controller.scala
  - core/src/main/scala/helper/Helper.scala
  - core/src/main/scala/core/Migration.scala
  - core/src/main/scala/security/SecretService.scala
  - core/src/main/scala/security/SecretsManager.scala
  - core/src/main/scala/security/SecretStore.scala
  - core/src/main/scala/security/Vault.scala
  - docs/docs/access_secrets.md
  - docs/mkdocs.yml
- Increased timeout for API health check
  - api/terraform/datapull_task/datapull_ecs.tf
- Fix bug of expiring CloudWatch log tokens
  - core/src/main/scala/logging/DataPullLog.scala
- Fix bug of EMR clusters not being re-used occasionally
  - api/src/main/java/com/homeaway/datapullclient/process/DataPullTask.java

## [0.1.40] - 2021-06-30

- Bug fix to pick up streaming parameters
- Sequencefile write with long key and default/snappy/deflate codecs

### Changed

- Bug fix to pick up streaming parameters
  - core/src/main/scala/core/Migration.scala
- Sequencefile write with long key and snappy codec
  - core/src/main/scala/core/DataFrameFromTo.scala
  - core/docker_spark_server/Dockerfile
  - core/src/main/resources/Samples/Input_Json_Specification.json

## [0.1.39] - 2021-06-24

- Bug fix to pick up streaming parameters
- Support for inline Spark SQL expressions in input json and sequencefile format
- Support for Structured Streaming watermarks (for stream-to-stream left joins, etc.)

### Changed

- Bug fix to pick up streaming parameters 
  - core/src/main/scala/core/Migration.scala
- Support for inline Spark SQL expressions in input json and sequencefile format
  - core/src/main/scala/core/DataFrameFromTo.scala
  - core/src/main/scala/core/DataPull.scala
  - core/src/main/scala/helper/Helper.scala
  - manual-tests/filesystem_dataset_to_elasticsearch_to_filesystem/README.md
  - manual-tests/filesystem_dataset_to_elasticsearch_to_filesystem/datapull_input.json
  - core/src/main/resources/Samples/Input_Json_Specification.json
- Support for Structured Streaming watermarks (for stream-to-stream left joins, etc.)
  - core/src/main/scala/core/DataFrameFromTo.scala
  - core/src/main/resources/Samples/Input_Json_Specification.json

## [0.1.38] - 2021-06-14

- Update jettison dependency

### Changed

- core/pom.xml

## [0.1.37] - 2021-06-14

- Upgraded ABRiS library
- Support for S3 streaming destination

### Changed

- core/pom.xml
- core/src/main/scala/helper/Helper.scala
- core/src/main/scala/core/DataFrameFromTo.scala
- core/src/main/scala/core/Migration.scala

## [0.1.36] - 2021-06-08

- Re-added AWS bom for dependency management
- Fixed bug with SES config
- Added common options to Snowflake example
- Better error message when sql attribute in input json provided without query attribute
- Added required additional dependencies for jackson v 2.12.2
- Update Spark 2.4.7 local spark clusters to 2.4.8
- Updated documentation to fix embedded html issues
- Support for Kafka topics that aren't registered with Schema Registry

### Changed

- core/src/main/scala/config/AppConfig.scala
- core/pom.xml
- core/src/main/resources/Samples/Input_Sample_snowflake-to-filesystem-to-snowflake.json
- core/src/main/scala/core/Migration.scala
- api/pom.xml
- Update Spark 2.4.7 local spark clusters to 2.4.8
  - **/README.md
  - core/src/main/resources/Samples/Input_Json_Specification.json
  - core/Docker_Spark_Server/Dockerfile
- docs/docs/install_on_aws.md
- core/src/main/scala/core/DataFrameFromTo.scala

## [0.1.35] - 2021-05-25

- Made Consul datacenter Urls, AWS tags config-driven
- Added tags to ECR repo
- Upgraded API docker base image from non-supported version
- Removed extra leading slash for vault url prefix

### Changed

- Made Consul datacenter Urls, cost center tag config-driven
    - core/src/main/scala/config/AppConfig.scala
    - api/src/main/java/com/homeaway/datapullclient/input/ClusterProperties.java
    - core/src/main/scala/helper/Consul.scala
    - api/terraform/datapull_task/datapull_ecs.tf
    - api/src/main/java/com/homeaway/datapullclient/process/DataPullRequestProcessor.java
    - api/src/main/java/com/homeaway/datapullclient/process/DataPullTask.java
    - api/terraform/datapull_task/ecs_deploy.sh
    - api/terraform/datapull_task/ecs_deploy_uninstall.sh
    - core/src/main/scala/core/Migration.scala
    - api/terraform/datapull_task/variables.tf
- Added tags to ECR repo
    - api/terraform/datapull_iam/datapull_user_and_roles.tf
- Upgraded API docker base image from non-supported version
    - api/Dockerfile
- Removed extra leading slash for vault url prefix
    - core/src/main/scala/security/Vault.scala

## [0.1.34] - 2021-05-18

Upgraded jackson and aws dependencies; standardised iswindowsauthenticated attribute 

### Changed

- Upgraded jackson and aws dependencies
    - **/pom.xml
- standardised iswindowsauthenticated attribute
    - core/src/main/scala/core/DataPull.scala 
    - core/src/main/scala/core/Migration.scala
    - core/src/main/resources/Samples/Input_Sample_teradata-to-filesystem.json

## [0.1.33] - 2021-05-18

Removed env var that clashes with fargate, fixed support for s3 service endpoints by adding support for fileprefix and schema for filesystem-like stores, updated manual test to cover explicitly specified schema

### Changed

- Remove env var that clashes with fargate
    - api/terraform/datapull_task/datapull_ecs.tf
- fixed support for s3 service endpoints by adding support for fileprefix and schema for filesystem-like stores
    - core/src/main/scala/core/DataFrameFromTo.scala
    - core/src/main/scala/logging/DataPullLog.scala
    - core/src/main/scala/core/Migration.scala
- updated manual test to cover explicitly specified schema
    - manual-tests/filesystem_stream_to_kafka/datapull_input.json
    - manual-tests/filesystem_stream_to_kafka/README.md

## [0.1.32] - 2021-05-15

- Upgraded Elasticsearch maven dependency to support ES 7.12.1, removed, unused dependencies
- Fixed https://github.com/homeaway/datapull/issues/81
- Added manual test for Elasticsearch sources and destinations
- Fixed Elasticsearch bugs, added support for additional optional ES Options and associated documentation
- Made Elasticsearch port, mapping, etc. optional with defaults

### Changed

- Upgraded Elasticsearch maven dependency to support ES 7.12.1, removed, unused dependencies
    - core/pom.xml
- Fixed https://github.com/homeaway/datapull/issues/81
    - core/src/main/resources/Samples/Input_Sample_*.json
    - functional-test/src/main/resources/dev_global-config.json
    - functional-test/src/main/resources/test_global-config.json
    - docs/docs/transformation.md
- Fixed Elasticsearch bugs, added support for additional optional ES Options and associated documentation
    - core/src/main/scala/core/DataFrameFromTo.scala
    - core/src/main/resources/Samples/Input_Json_Specification.json
- Made Elasticsearch port, mapping, etc. optional with defaults
    - core/src/main/scala/core/Migration.scala

### Added
- Added manual test for Elasticsearch sources and destinations
    - manual-tests/filesystem_dataset_to_elasticsearch_to_filesystem/*

## [0.1.31] - 2021-05-12

Remove bintray-bound Neo4j lib implementation until https://neo4j.com/product/connectors/apache-spark-connector/?ref=blog can be implemented

### Changed

- core/src/main/resources/Samples/Input_Json_Specification.json
- core/src/main/resources/Samples/Input_Sample_Oracle_PostPreMigration.json
- core/src/main/scala/core/DataFrameFromTo.scala
- core/src/main/scala/core/Migration.scala
- core/pom.xml
- docs/docs/index.md
- functional-test/src/main/resources/dev_global-config.json
- functional-test/src/main/resources/test_global-config.json
- functional-test/Jenkinsfile
- functional-test/pom.xml
- functional-test/testng.xml

### Deleted

- core/src/main/resources/Samples/Input_Sample_filesystem_to_Neo4j.json
- core/src/main/resources/Samples/Input_Sample_Neo4j.json
- core/src/main/scala/helper/Neo4j.scala
- functional-test/src/main/java/com/homeaway/utils/db/Neo4j.java
- functional-test/src/main/resources/input_files/MySQL-to-Neo4j.json
- functional-test/src/main/resources/input_files/Neo4jRelations.json
- functional-test/src/test/java/com/homeaway/DataPull/DataPullMySQLToNeo4JTest.java

## [0.1.30] - 2021-05-11

Fixing small bugs and making the brand, costcenter, application tags as optional; definition of dockerised spark server; installation instructions for Oracle and Teradata drivers

### Changed

- api/src/main/java/com/homeaway/datapullclient/input/ClusterProperties.java
- api/src/main/resources/input_json_schema.json
- core/docker_spark_server/Dockerfile
- docs/docs/oracle_teradata_support.md

## [0.1.29] - 2021-04-29
Added manual tests for mysql and mssql data stores, added support for secure Kafka schema registries, give ownership of files to bucket owners when writing to S3, reduced code redundancy, added permissions for bootstrap files and ECS tags, permission for default and custom EC2 EMR roles to send SES emails, support for custom AWS Service Endpoints like IBM Object Storage, made BucketOwnerFullControl default but removable

### Added
- manual-tests/mssql_dataset_to_mysql_to_mssql/* - Manual test for batch and pre/post-migrate commands on mysql and mssql data stores

### Changed
- DataFrameFromTo.scala - Added support for secure Kafka schema registries, give ownership of files to bucket owners when writing to S3, reduced code redundancy, made BucketOwnerFullControl default but removable
- Helper.scala - Added support for secure Kafka schema registries
- Input_Json_Specification.json - Added documentation for Teradata type (fastexport, fastload, etc), documentation for custom AWS Service Endpoints like IBM Object Storage, made BucketOwnerFullControl default but removable
- Migration.scala - Removed debug print that can cause credentials to leak to logs
- datapull_user_and_roles.tf - Added permissions for bootstrap files and ECS tags, and for sending SES emails
- Controller.scala - Set SES From email name to DataPull
- sample_custom_emr_ec2_role.tf - Added permissions for sending SES emails
- DataPull.scala - support for custom AWS Service Endpoints like IBM Object Storage, made BucketOwnerFullControl default but removable

## [0.1.28] - 2021-04-13
Converted Kafka source to use ABRiS and support spark streaming, support streaming filesystem source, add Console as a destination for batch and stream

### Added
- manual-tests/filesystem_stream_to_kafka/* - Manual test for streaming filesystems and Kafka sources

### Changed
- core/src/main/resources/Samples/Input_Json_Specification.json - Added documentation to support SSL Kafka sources and destinations,  plus update missing documentation for some filesystem actions
- Sample input json files - Updated to reflect change in sample CSV data file location
- core/src/main/scala/core/DataFrameFromTo.scala - Converted Kafka source to use ABRiS and support spark streaming, , support streaming filesystem source
- core/src/main/scala/core/Migration.scala - add Console as a destination for batch and stream, support streaming etc for file source and Kafka; refactored some function calls to use explicit parameter names
- core/src/main/scala/helper/Helper.scala - Added support for Kafka as a source, using ABRiS
- core/src/test/scala/DataFrameFromToFileSystem.scala - updated default value of mergeSchema option for parquet files, to false from null
- core/src/main/scala/core/DataPull.scala Removed unused code

## [0.1.27] - 2021-04-12
### Changed
- Controller.scala - Throw an exception to end the execution with exit code 1 when at least one migration has an error.

## [0.1.26] - 2021-04-12
### Changed
- api/src/main/java/com/homeaway/datapullclient/config/DataPullClientConfig.java
- api/src/main/java/com/homeaway/datapullclient/input/ClusterProperties.java
- api/src/main/java/com/homeaway/datapullclient/process/DataPullRequestProcessor.java
- api/src/main/java/com/homeaway/datapullclient/process/DataPullTask.java

## [0.1.25] - 2021-03-24
### changed
- Migration.scala - Fix port issue

## [0.1.24] - 2021-03-20
Added support for string event format for Kafka destinations
### Added
- .gitattributes - Force *.sh files to be handled with LF line endings regardless of OS, by git
### Changed
- core/src/main/scala/core/{DataFrameFromTo.scala, Migration.scala} - Added support for string event format for Kafka destinations
- core/src/main/resources/Samples/Input_Sample_s3_to_kafka.json - Updated example to use string format for kafka event key
- core/src/main/resources/Samples/Input_Json_Specification.json - Updated spec to support string event format for Kafka destinations
- manual-tests/filesystem_dataset_to_kafka/{README.md, datapull_input.json} - Updated manual test to test for string event format
- api/terraform/*/*.sh - changed line endings from CRLF to LF
- api/terraform/datapull_task/ecs_deploy.sh - removed `exit 0` from some previous debug accidentally committed

## [0.1.23] - 2021-03-14
### Added
- manual-tests/filesyste_dataset_to_kafka* - Added manual test instructions to test data movement from filesystem to kafka topic
### Changed
- core/pom.xml - Removed redundant dependency that causes runtime conflict for kafka clients
- manual-tests/README.md - Centralised steps to confirm datapull works locally, prior to doing the manual tests
- README.md, manual-tests/filesystem_dataset_with_uuids_to_mongodb/README.md - Updated spark submit command with necessary kafka runtime dependency

## [0.1.22] - 2021-03-08
### Added
- manual-tests/* - Added manual test instructions and supporting material

### Changed
- core/docker_spark_server/Dockerfile - Updated the download mirrors and versions for Apache Spark and Hadoop binaries
- pom.xml - Updated hadoop version to commonly supported version
- README.md - Updated with referece to manual tests

## [0.1.21] - 2021-02-25
### Changed
- core/src/main/scala/core/DataFramFromTo.scala - No longer using asInstanceOf[MongoClientURI]

## [0.1.20] - 2021-02-24
- core/src/main/scala/core/DataFramFromTo.scala - Fixed few arguments w.r.t the building kafka properties
- core/src/main/scala/core/Migration.scala
- core/src/main/scala/helper/Helper.scala

## [0.1.19] - 2021-02-12
### Changed
- api/src/main/java/com/homeaway/datapullclient/config/EMRProperties.java: Upgraded EMR version to 5.31.0
- core/docker_spark_server/Dockerfile: Upgraded Spark version to 2.4.6, downgraded Hadoop version to 2.10.0
- core/src/main/resources/Samples/Input_Json_Specification.json: Upgraded Kafka destination spec to match abris 4.0.1 capabilities
- core/src/main/resources/Samples/Input_Sample_Join_Heterogeneous_Sources.json: Fixed file spelling
- core/src/main/resources/Samples/Input_Sample_s3_to_kafka.json: Upgraded example to match abris 4.0.1 capabilities
- core/src/main/scala/core/DataFramFromTo.scala: Removed unnecessary println's, Use s3a when running locally, s3 otherwise, upgraded ABRiS to 4.0.1
- core/src/main/scala/core/DataPull.scala: Use s3a when running locally, s3 otherwise
- core/src/main/scala/core/Migration.scala: Removed unnecessary println's, upgraded ABRiS to 4.0.1
- pom.xml: Upgraded Spark version to 2.4.6, downgraded Hadoop version to 2.10.0, removed shaded jar, upgraded ABRiS to 4.0.1
- README.md: Upgraded Spark version to 2.4.6, downgraded Hadoop version to 2.10.0


## [0.2.1] - 2021-01-26
### Changed
api/src/main/java/com/homeaway/datapullclient/process/DataPullTask.java: S3 Cross-Account Access Support.

## [0.2.0] - 2020-12-22
### Changed
- Fixed install script error, upgraded AWS CLI to v2
- Fixed the MS DOS batch script for IAM principals
- Removed unnecessary environment variables
### Removed
- Removed the MS DOS batch script for ECS Deploy


## [0.1.18] - 2020-11-05
### Changed
- Fixed issue of Teradata destination deadlocking

## [0.1.17] - 2020-10-19
### Changed
- core/pom.xml : Remove hive JDBC dependency that was causing Spark SQL to fail
- core/src/main/scala/core/DataFrameFromTo.scala, core/src/main/scala/core/DataPull.scala, core/src/main/scala/core/Migration.scala, core/src/main/scala/logging/DataPullLog.scala, core/src/test/scala/DataFrameFromToFileSystem.scala : Added support RSA key based auth for SFTP
- core/src/main/resources/Samples/Input_Sample_filesystem-to-sftp.json : Updated to show RSA Key based auth
- core/src/main/resources/Samples/Input_Json_Specification.json : Added documentation for using sftp with password or RSA key auth

## [0.1.16] - 2020-10-18
### Changed
- README.md : Minor path fixes for running DataPull in a local Docker container
- core/pom.xml, core/src/main/scala/core/DataFrameFromTo.scala, core/src/main/scala/core/Migration.scala : Added support for Snowflake
- core/src/main/resources/Samples/Input_Sample_sftp-to-filesystem.json : Removed duplicate json attribute
- core/src/main/resources/Samples/Input_Json_Specification.json : Added documentation for uuing Snowflake as a source/destination
### Added
- core/src/main/resources/Samples/Input_Sample_snowflake-to-filesystem-to-snowflake.json : Added sample input json to read and write from Snowflake

## [0.1.15] - 2020-10-13
- Added support for reading/writing to a filesystem in orc.
- Added support for accepting an optional emr service role from the user if needed.
- Added support for accepting optional value and header fileds when writing data to kafka.
- Added support for reading/writing data to server side encrypted s3 buckets with AES-256. 

### Changed
* api/src/main/java/com/homeaway/datapullclient/input/ClusterProperties.java
* api/src/main/java/com/homeaway/datapullclient/process/DataPullRequestProcessor.java
* api/src/main/java/com/homeaway/datapullclient/process/DataPullTask.java
* api/terraform/datapull_task/ecs_deploy.sh
* core/pom.xml
* core/src/main/scala/core/DataFrameFromTo.scala
* core/src/main/scala/core/DataPull.scala
* core/src/main/scala/core/Migration.scala

## [0.1.14] - 2020-09-23
- Added script to uninstall DataPull non-IAM components
- Reduced access of IAM Roles and Users used by DataPull
- Updated documentation for custom EMR profile role
- Added uninstall scripts and documentation for AWS deployments

### Added
* api/terraform/datapull_task/ecs_deploy_uninstall.sh: script to uninstall DataPull non-IAM components
* api/terraform/datapull_iam/uninstall_user_and_roles.sh: script to uninstall DataPull IAM components
* docs/docs/uninstall_on_aws.md: Documentation on how to uninstall DataPull in AWS

### Changed
* api/terraform/datapull_iam/datapull_user_and_roles.tfL Reduced access of IAM Roles and Users used by DataPull
* api/terraform/datapull_task/datapull_ecs.tf: Minro fix to spelling of AWS tags
* docs/docs/custom_emr_ec2_role.md: Updated documentation for custom EMR profile role
* docs/docs/install_on_aws.md: Updated documentation for custom EMR profile role
* docs/docs/index.md: Marked Kafka destination as supported datastore
* docs/mkdocs.yml: Updated documentation navigation 

## [0.1.13] - 2020-09-16
- Added Kafka as a destination
- Fixed bug related to JDBC port not being read
- Added support for reading and writing S3 data when running in dockerised spark
- Minor improvements on time to install
- Cleaned up and updated dependencies

### Added
* core/docker_spark_server/Dockerfile: Run dockersied spark with Spark 2.4.4, Scala 2.11, Hadoop 3.x
* core/docker_spark_server/LICENSE: MIT License attribution to gettyimages for docker container
* core/src/main/resources/Samples/Input_Sample_filesystem-to-filesystem-avro.json: Sample for Avro file format

### Changed
* .gitignore: Ignore local log folder created when debugging
* README.md: Support for S3 sources and destinations when running as docker container
* api/src/main/java/com/homeaway/datapullclient/config/EMRProperties.java: Upgraded EMR version to 5.29
* api/src/main/java/com/homeaway/datapullclient/process/DataPullTask.java: Support for Kafka and Spark Avro
* api/terraform/datapull_iam/datapull_user_and_roles.tf: Additional permissions to EMR service role to support EMR 6.0.0
* api/terraform/datapull_task/ecs_deploy.sh: Installation speed improvement
* core/pom.xml: Cleaned up and upgraded dependencies; support for Kafka streaming
* core/src/main/resources/Samples/Input_Json_Specification.json: Documentation for Kafka as destination, EMR nodes
* core/src/main/resources/Samples/Input_Sample_s3_to_kafka.json: Updated sample for Kafka as destination
* core/src/main/scala/core/DataFrameFromTo.scala: Added support for Kafka; support to use S3 platform locally
* core/src/main/scala/core/Migration.scala: Added support for Kafka; bug fixes for JDBC port
* core/src/main/scala/helper/Neo4j.scala: Upgrade syntax to support Scala 2.12
* functional-test/pom.xml: Upgraded dependencies to resolve security advisories

### Deleted
* core/hive-jdbc-3.1.2.jar: Replaced downloaded dependency with maven reference

## [0.1.12] - 2020-08-05
### Changed
* core/pom.xml
* core/src/main/scala/core/DataFrameFromTo.scala
* core/src/main/scala/core/Migration.scala

## [0.1.11] - 2020-07-28

### Added
* Added the jtds driver for supporting NTLM based windows authentication for sql server.
* Added a sample file [core/src/main/resources/Samples/Input_Sample_SQLServer_to_Cassandra_with_windows_authentication.json](Input_Sample_SQLServer_to_Cassandra_with_windows_authentication.json).
* Added the support for windows based based authentication for SQL Server.

### Changed
* Made necessary changes to the following files :
           * CHANGELOG.md
           * core/pom.xml
           *  core/src/main/resources/Samples/Input_Json_Specification.json
           *  core/src/main/scala/core/DataFrameFromTo.scala
           *  core/src/main/scala/core/Migration.scala

### Removed
* N/A

## [0.1.10] - 2020-07-07

### Added
* IAM Role emr_datapull_role and associated IAM Policies. This role is used as the default EMR service role, to fulfill Feature Issue #70

### Changed
* DataPull default install on AWS EMR and Fargate, spins up ephemeral EMR clusters of 6 m4.large instances instead of 3 m4.xlarge instances. This results in a spark clusters that cost the same but have 100% more usable CPU and memory
* DataPull default install on AWS EMR and Fargate, spins up ephemeral EMR clusters that use a EMR service IAM role emr_datapull_role with reduced permission needs , instead of using the default IAM Role EMR_DefaultRole that has very broad permissions. This fulfils Feature Issue #70
* Updated terraform for API's ALB listener with a commented line for the Certificate ARN; and fixed corresponding documentation that explains how to switch the API endpoint from HTTP to HTTPS
* DataPull default install on AWS EMR and Fargate, has the ECS Service with assign_public_ip flag as true instead of false; this is necessary for Fargate to fetch the ECR image (ref [AWS KnowledgeCenter](https://aws.amazon.com/premiumsupport/knowledge-center/ecs-pull-container-error/))
* Documentation to set up VPC etc in AWS Account for DataPull install fixes incorrect mention of 10.0.0.0/24 as "2 IPv4 ranges" 

### Removed
* Removed JDBC-based Hive data source functionality by reverting https://github.com/homeaway/datapull/commit/68478198b743aa4ebd26ed6151292fded7232b30 as a hotfix for Bug Issue #68
* In core/src/main/scala/logging/DataPullLog.scala, removed an unnecessary println

## [0.1.9] - 2020-07-06
### Changed
* api/terraform/datapull_task/ecs_deploy.sh - Fixed bug issue #67

## [0.1.8] - 2020-07-05
### Changed
* README.md - replaced instructions to install MkDocs with dockerised MkDocs commands; replaced need for JDK8 install for Dockerised execution,  with dockerised maven
* docs/docs/index.md - replaced instructions to install MkDocs with dockerised MkDocs commands

## [0.1.7] - 2020-06-11
### Added
* docs/docs/custom_emr_ec2_role.md
* docs/docs/resources/sample_custom_emr_ec2_role.tf

## [0.1.6] - 2020-05-22
### Changed
* core/src/main/resources/Samples/Input_Json_Specification.json
* core/src/main/scala/core/Controller.scala
* core/src/main/scala/core/DataFrameFromTo.scala
* core/src/main/scala/core/DataPull.scala
* core/src/main/scala/core/Migration.scala
* core/src/main/scala/logging/DataPullLog.scala

## [0.1.5] - 2020-05-21
### Changed
* CHANGELOG.md
* core/pom.xml
* core/src/main/resources/Samples/Input_Json_Specification.json
* core/src/main/scala/core/DataFrameFromTo.scala
* core/src/main/scala/core/Migration.scala

## [0.1.4] - 2020-05-15
### Changed
* core/pom.xml
* core/src/main/scala/core/Migration.scala

## [0.1.3] - 2020-05-04
### Changed
* core/src/main/scala/core/DataFrameFromTo.scala
* core/src/main/scala/core/DataPull.scala
* core/src/main/scala/core/Migration.scala
* core/src/main/scala/logging/DataPullLog.scala

## [0.1.2] - 2020-05-04
### Changed
* core/src/main/scala/core/Migration.scala
* core/src/main/scala/core/DataFrameFromTo.scala

## [0.1.1] - 2020-05-04
### Changed
* core/src/main/scala/core/Migration.scala

## [0.1.0] - 2020-01-16
### Changed
* core/src/main/scala/core/DataFrameFromTo.scala
* core/src/main/scala/core/Migration.scala
* core/src/main/scala/logging/DataPullLog.scala

## [0.0.9] - 2020-01-12
### Changed
* core/src/main/resources/Samples/Input_Json_Specification.json
* core/src/main/scala/core/DataFrameFromTo.scala
* core/src/main/scala/core/Migration.scala

## [0.0.8] - 2020-01-10
### Added
* core/src/main/resources/Samples/Input_Sample_s3_to_kafka.json
### Changed
* core/src/main/resources/Samples/Input_Json_Specification.json
* core/src/main/scala/core/DataFrameFromTo.scala
* core/src/main/scala/core/Migration.scala

## [0.0.7] - 2020-01-09
### Changed
* core/src/main/scala/core/DataFrameFromTo.scala
* core/src/main/scala/core/Migration.scala

## [0.0.6] - 2020-01-03
### Added
* docs/docs/media/datapull_installation.png - added installation diagram

### Changed
* README.md - updated documentation on how to document
* core/pom.xml - updated version to fix a vulnerability
* docs/docs/index.md - moved logo
* docs/docs/install_on_aws.md - fixed formatting
* docs/docs/monitor_spark_ui.md - removed SSH tunnel diagram in documentation

## [0.0.5] - 2019-12-11
### Changed
* core/src/main/scala/core/DataFrameFromTo.scala
* core/src/main/scala/core/Migration.scala
* core/src/main/scala/helper/Helper.scala

## [0.0.4] - 2019-12-10 
### Added
* docs/docs/oracle_teradata_support.md
* docs/docs/aws_account_setup.md

### Changed
* api/pom.xml
* api/src/main/java/com/homeaway/datapullclient/config/DataPullClientConfig.java
* api/src/main/java/com/homeaway/datapullclient/config/DataPullClientConfig.java
* api/src/main/java/com/homeaway/datapullclient/config/DataPullProperties.java
* api/src/main/java/com/homeaway/datapullclient/config/EMRProperties.java
* api/src/main/java/com/homeaway/datapullclient/process/DataPullRequestProcessor.java
* api/src/main/java/com/homeaway/datapullclient/process/DataPullTask.java
* api/src/main/java/com/homeaway/datapullclient/start/DatapullclientApplication.java
* api/src/main/resources/application-dev/test/stage/prod.yml
* api/src/main/resources/overwrite_config.sc
* api/src/main/resources/read_application_config.sc
* api/src/main/resources/terraform/datapull_task/datapull_ecs.tf → api/terraform/datapull_task/datapull_ecs.tf
* api/terraform/datapull_task/ecs_deploy.sh
* api/terraform/datapull_task/variables.tf
* core/pom.xml
* core/src/main/resources/application-dev/test/stage/prod.yml
* core/src/main/scala/config/AppConfig.scala
* core/src/main/scala/core/DataFrameFromTo.scala
* core/src/main/scala/logging/DataPullLog.scala
* core/src/main/scala/security/Vault.scala
* docs/docs/install_on_aws.md
* master_application_config-dev/test/stage/prod.yml
* .gitignore

### Deleted
* api/src/main/resources/application.yml
* master_application_config.yml

## [0.0.3] - 2019-11-15
### Added
-Added Documentation - "Added the documentation for Monitoring spark UI for the datapull jobs."

## [0.0.2] - 2019-08-20
### Fixed
- Fixed bug - "create_user_and_roles.sh script is not generating access and secret keys"

## [0.0.1] - 2019-06-26
### Fixed
- Fixed "No json input available to Data Pull" exception when running DataPull from EMR 
- Fixed create_user_and_roles.sh script
