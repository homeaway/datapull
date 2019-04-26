# Functional Tests
> Pre-Requisite : [maven](https://maven.apache.org/install.html) is installed 
## Runbook to execute functional tests
### Update environment.properties file 
The [environment.properties](../functional-test/src/main/resources/environment.properties) file must be updated for the following keys...
* _environment_ - supported values are dev and test where, EMR cluster will be launched by the DataPull API. New environments require corresponding creation/edits of _environment_ global-config.json
* _awsAccessKey / awsSecretKey_ - AWS access key and secret key of the environment set above. Running test cases which uses S3 either as a source or destination database require read only access of AWS EMR cluster and read and write access of AWS s3 bucket.  
* _rdsInstancesRequired_ -  The following databases are required for some functional test cases: mysql, mssql, postgres. If these databases are unavailable, they can be created as Amazon AWS RDS instances; and they will be terminated after the test suite completes. If this _rdsInstancesRequired_ flag is set to true, then an AWS AccessKey and SecretKey that has permission to create AWS RDS instances, must be set in dev.awsAccessKey and  dev.awsSecretKey respectively.
* _test.awsAccessKey / test.awsSecretKey_ - These keys are mainly used to test that AWS S3 can be accessed by providing access key and secret key in DataPull input json. These keys are required to run the test _s3ToCassandraTestWithKey_.
* _awsRegion_ - AWS region where EMR cluster is running and S3 bucket is located.
* _ecsClusterName_ - This is the ECS cluster name that hosts the DataPull api. This key is used to find the IP address of FARGATE app hosting the DataPull API. If the website at _dataPullUri_ is healthy, this key is not required.
* _dataPullUri_ - Url of the DataPull API. If DataPull API is running locally, this uri should be http://localhost:8080/api/v1/DataPullPipeline
* _userEmailAddress_ - This email address will be used in the input json of the DataPull. An email will be sent to this email address after the DataPull completes.
* _parallelExecution_ - This shold be set to true, and the thread-count set to more than 1 in testng.xml file; to run the tests in parallel. 
### Update database and cluster details 
Database and cluster details can be edited in following file if env is Dev , file will be [Dev Env Database and Cluster Details config](../functional-test/src/main/resources/dev_global-config.json) and for Test env, file will be [Test Env Database and Cluster Details config](../functional-test/src/main/resources/dev_global-config.json)
### Run test cases 
* Use the command ```mvn clean test``` to run all test cases.
* Use the command ```mvn clean -Dgroups = {platforms},config test``` to run a subset of test cases for the specified platforms.
### Check report after execution
Extent report and logs are generated to the report folder. If configured, these documents can be uploaded to an s3 bucket.
