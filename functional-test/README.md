## DataPull-Functional-Automation-Test
#### Pre-Requiste 
  [maven](https://maven.apache.org/install.html) is installed 
#### How to run this functional test?
##### Update environment.properties file 
In order to run, [environment.properties](../functional-test/src/main/resources/environment.properties) file must be updated.
* _environment_ - supported values are dev and test where, EMR cluster will be launched by the DataPull API. New environments require corresponding creation/edits of _environment_ global-config.json
* _awsAccessKey / awsSecretKey_ - AWS access key and secret key of the environment set above. Running test cases which uses S3 either as a source or destination database require read only access of AWS EMR cluster and read and write access of AWS s3 bucket.  
* _rdsInstancesRequired_ - If we don't have database like - oracle, mysql, mssql, postgres. But we still want to test functionality related to these databases. We can create AWS RDS instances of these databases on the fly and it will get terminated after test suite completion. For this we have to pass AccessKey and SecretKey in dev.awsAccessKey and  dev.awsSecretKey respectively which is having necessary permission to create AWS RDS instance.
* _test.awsAccessKey / test.awsSecretKey_ - This keys are mainly used to test that AWS S3 can be accessed by providing access key and secret key in DataPull input json. If we are running test - s3ToCassandraTestWithKey then it is required.
* _awsRegion_ - AWS region where EMR cluster is running and S3 bucket is located.
* _ecsClusterName_ - We are using this key to find the IP address at run time of FARGATE app hosting the DataPull api. This is same as ECS cluster name where we have deployed the DataPull api. If provided dataPullUri is up and running, value of this key is not required.
* _dataPullUri_ - DataPull uri. If DataPull api is running in local, URI will be `http://localhost:8080/api/v1/DataPullPipeline`
* _userEmailAddress_ - This email address will be used in the input json of the DataPull and email will be triggered on this email address after data pull job completion.
* _parallelExecution_ - We can set this to true if we want to run test in parallel and set the thread-count more than 1 in testng.xml file.
##### Update database and cluster details 
Database and cluster details can be edited in following file if env is Dev , file will be [Dev Env Database and Cluster Details config](../functional-test/src/main/resources/dev_global-config.json) and for Test env, file will be [Test Env Database and Cluster Details config](../functional-test/src/main/resources/dev_global-config.json)
##### Run test cases 
In order run the all the test cases you can use the command ``` mvn clean test ```
If you like to run only a specific set of test cases based on particular type of platform use the command ```mvn clean -Dgroups = {groups},config test```

#### How to check report after execution?
We are generating extent report and logs under report folder. It is also uploaded to s3 bucket if configured.


