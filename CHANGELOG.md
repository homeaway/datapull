# Change Log
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)
and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html)

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
