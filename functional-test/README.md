# Functional Tests
> Pre-Requisite : [maven](https://maven.apache.org/install.html) is installed 
## Runbook to execute functional tests
### Update environment.properties file 
The [environment.properties](../functional-test/src/main/resources/environment.properties) file must be updated for the following keys...
* _environment_ - supported values are dev and test where EMR cluster will be launched by the DataPull API and this value will also be used to select corresponding environment global-config.json .
* _awsAccessKey / awsSecretKey_ - AWS access key and secret key of the environment set above. This should have read-only access of EMR cluster and read and write access of S3 bucket.  
* _rdsInstancesRequired_ -  The following databases are required for some functional test cases: MySQL, mssql, postgres, and Oracle. If these databases are unavailable, it can be created as  AWS RDS instances and that will be terminated after the test suite execution completes. If we are expecting script to create RDS instance on the fly, AWS secret key and access key must have permission to do the same.
* _awsRegion_ - AWS region where EMR cluster is running and S3 bucket is located.
* _dataPullUri_ - Url of the DataPull API. If DataPull API is running locally, this uri will be http://localhost:8080/api/v1/DataPullPipeline
* _userEmailAddress_ - This email address will be used in the input json of the DataPull. An email will be sent to this email address after the DataPull job completes.
* _parallelExecution_ - If we want to run tests in parallel. This flag should be set to true and the thread-count set to more than 1 in testng.xml file.
### Update database and cluster details 
Database and cluster details can be edited in following file if env is Dev , file will be [Dev Env Database and Cluster Details config](../functional-test/src/main/resources/dev_global-config.json) and for Test env, file will be [Test Env Database and Cluster Details config](../functional-test/src/main/resources/dev_global-config.json)
### How to update database details
Suppose I am interested in running test cases related to Cassandra as a source and MongoDB as a destination. I will find there is a test class - `DataPullCassandraToMongoDbTest` matching these criteria. After reading a couple of lines, I will find that `Cassandra-to-MongoDB.json` file is being read in the test class. This file is available under resources/input_files folder. If I read this file, I will find that there are no connection details of Cassandra and MongoDB cluster. Actually, we are using this file as a template and actual values will be fetched from dev_global-config.json or test_global-config.json depending on environment entered in environment.properties file. Mapping is like the value of the platform key in Cassandra-to-MongoDB.json file is a key in the dev/test_global-config.json file. So, the value of the cassandra and mongodb in dev/test_global-config.json file will be updated in the source and destination object of Cassandra-to-MongoDB.json file respectively. So, place to update any test data or database connection details is dev/test_global-config.json file. 

<img href= "updating_test_data.gif" title= "updating_test_data.gif"/>

### Run test cases 
* Use the command ```mvn clean test``` to run all test cases.
* Use the command ```mvn clean -Dgroups = {platforms},config test``` to run a subset of test cases for the specified platforms.
* If we are interested in running test cases related to few class files, we can update the testng.xml . Remove the class tags which we do not want to run and execute the command ```mvn clean test```.
### Check report after execution
Extent report and logs are generated in the report folder. If configured, these documents can be uploaded to a s3 bucket.