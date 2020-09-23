# Change Log
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)
and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html)

## [0.1.14] - 2020-09-23
- Added script to uninstall DataPull non-IAM components
- Reduced access of IAM Roles and Users used by DataPull
- Updated documentation for custom EMR profile role

### Added
* api/terraform/datapull_task/ecs_deploy_uninstall.sh: script to uninstall DataPull non-IAM components

### Changed
* api/terraform/datapull_iam/datapull_user_and_roles.tfL Reduced access of IAM Roles and Users used by DataPull
* api/terraform/datapull_task/datapull_ecs.tf: Minro fix to spelling of AWS tags
* docs/docs/custom_emr_ec2_role.md: Updated documentation for custom EMR profile role
* docs/docs/install_on_aws.md: Updated documentation for custom EMR profile role
* docs/docs/index.md: Marked Kafka destionation as supported datastore

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
