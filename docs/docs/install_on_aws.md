# Deploying DataPull on AWS ECS Fargate and EMR

The following steps describe how to deploy the DataPull app in a fresh AWS account. In a nutshell, these steps do the following
- Creates an IAM user that has enough permissions to create a ECS Fargate app, and upload DataPull JARs to S3
- Creates an IAM role that has enough permissions to create EMR clusters, and read/write to S3

## DataPull App Structure
```DataPull``` application is divided into two components i.e. ```api``` and ```core```. Core contains the actual spark logic that moves data across databases. It is the component that actually does the data transfer. Api contains code that allows accessing DataPull services. It exposes the REST endpoint that accepts the input json. The API internally schedules the job, saves DataPull configuration into S3 and manages EMR lifecycle for spark job.

## Pre-requisites

- Clone/download the master branch of this repo
- Have an AWS account
- Have available, the AWS access key ```<aws_admin_access_key>``` and secret key ```<aws_admin_secret_key>``` of an IAM user that can create IAM users and IAM roles in your AWS account
    -   It is advisable that AWS access key ```<aws_admin_access_key>``` and secret key ```<aws_admin_secret_key>``` have admin access to the AWS account. Hence, this step would be typically done by the team managing the AWS account.
- Have a S3 bucket ```<datapull_s3_bucket>``` (this bucket can be an existing bucket or a new bucket) in which the folder ```datapull-opensource``` will be used to store DataPull's artifacts and logs
- Know a valid AWS VPC subnet id ```<aws_subnetid_private>``` in the region ```<aws_region>``` accessible to your clients, ideally not accessible from the internet; but has access to the internet
- Know a valid AWS security group ```<aws_security_grp>``` in the region ```<aws_region>``` accessible to your clients, ideally not accessible from the internet; but has access to the internet
- Have Docker installed on the machine used for deployment

## Configure DataPull
### master_application_config.yml(present in project root directory) configuration parameters
  
- <b>datapull.application.region(Optional)</b> : This specifies the regions supported by AWS. If you specify this value, you don't have to separately specify region for s3 buckets and emr cluster. This parameter is <b>mandatory</b> if you don't specify emr region and s3 region in api config.

- <b>datapull.logger.s3.loggingfolder</b> : This parameter is used to store spark logs in s3. Give a valid S3 bucket path to store logs. For example {bucket_name}/datapull/logs/ is a valid s3 path. As of now this is a mandatory.

- <b>datapull.logger.cloudwatch(Optional)</b> : Required only if you want DataPull core to put log events into cloudwatch. DataPull api uses ```api/src/main/resources/logback-spring.xml``` for configuring logging. For DataPull api component to put logs into cloudwatch, please enable cloudwatch logging in ```api/src/main/resources/logback-spring.xml```. ```datapull.logger.cloudwatch.accessKey``` and ```datapull.logger.cloudwatch.secretKey``` needs to be specified only if the machine running the application(core and api part) doesn't have the desired IAM roles.

- <b>datapull.logger.mssql(Optional)</b> : Required only you want to log events into mssql. In case you want to use this feature please specify all the paramters required within this node.

- <b>ddatapull.email.smtp(Optional)</b> : This is required if you want to specify task completion mail from DataPull. If you want to use SMTP server to send you emails, values of all the properties within this node has to be specified. You can also configure AWS SES to send emails.

- <b>datapull.email.ses(Optional)</b> : This property configures aws ses to send DataPull task completion email. ```email``` property within this node is the AWS SES verified email. ```datapull.email.ses.accessKey``` and ```datapull.email.ses.secretKey``` needs to be specified only if the machine running the application(core and api part) doesn't have the desired IAM roles.

> Note: from the above ```datapull.email.smtp``` and ```datapull.email.ses``` properties, one of the properties is required to be configured. If both of these properties are not configured, there is no way for DataPull to send process completion mail. 

### DataPull API configuration parameters (api/src/main/resources/application-${env}.yml)

- <b> datapull.api.s3_bucket_name</b>: Specify DataPull S3 bucket name here. This is the bucket where DataPull input jsons and logs are stored. 

- <b> datapull.api.s3_bucket_region(Optional)</b>: DataPull S3 bucket region. Needs to be specified only if datapull.application.region is not specified in master_application_config.yml or S3 region is different from datapull.application.region specified in master_application_config.yml.

- <b> datapull.api.s3_jar_path</b>: This is the path of DataPull core jar in S3. You can keep it in any S3 bucket provided your app has access to this bucket. By default you can keep s3 path as s3://<datapull_s3_bucket>/datapull-opensource/jars/DataMigrationFramework-1.0-SNAPSHOT-jar-with-dependencies.jar

- <b> datapull.api.application_subnet</b>: subnet for your application. The fargate app will be deployed within this subnet. If emr_subnet is not provided for EMR cluster, the emr cluster will also be created within this subnet.

- <b> datapull.api.application_security_group</b>: security group for fargate app. This security group can also be used for emr clusters, but as of now security group for emr are created by EMR sdk API.

> Note: from the above  properties, ```datapull.api.s3_bucket_region``` needs to be specified only if ```datapull.application.region``` is not specified in ```master_application_config.yml``` or S3 region is different from ```datapull.application.region``` specified in ```master_application_config.yml```. 

- <b> datapull.emr.emr_security_group_master(Optional)</b>: Security group for EMR master node.

- <b> datapull.emr.emr_security_group_slave(Optional)</b>: Security group for EMR slave node.

- <b> datapull.emr.emr_security_group_service_access(Optional)</b>: Service access security group for EMR. This security group allows accessing spark UI through web browser.
- <b> datapull.emr.emr_region(Optional)</b>: DataPull EMR region. Needs to be specified only if ```datapull.application.region``` is not specified in ```master_application_config.yml``` or EMR region is different from ```datapull.application.region``` specified in ```master_application_config.yml```.
- <b> datapull.emr.tags(Optional)</b>: Tag for emr cluster. 

> Note: from the above properties properties, ```datapull.emr.emr_region``` needs to be specified only if ```datapull.application.region``` is not specified in ```master_application_config.yml``` or emr region is different from ```datapull.application.region``` specified in ```master_application_config.yml```. 

- In the file [api/src/main/resources/terraform/datapull_task/datapull_ecs.tf](https://github.com/homeaway/datapull/blob/master/api/src/main/resources/terraform/datapull_task/datapull_ecs.tf) set
- the HCL resource attribute ```aws_ecs_service.datapullwebapi_service.network_configuration.assign_public_ip``` to false if the VPC subnet id ```<aws_subnetid_private>``` is accessible from your clients' network; else set this to true
- **Note:** Setting this value to has obvious security risks since this exposes the DataPull endpoint to the internet. Use this for demo purposes only.    

### DataPull Core configuration paramters(core/src/main/resources/application.yml)

- <b>datapull.secretstore.vault(Optional)</b>: Required only if application is using vault to access credentials. Configure the properties within this yml node to make DataPull work with vault.

## Oracle and Teradata support
For performing Data migration on Oracle and Teradata, the respective jar needs to be manually downloaded from their Company's website and add dependency in pom.xml file. 

### Steps to download ojdbc jar
- Go to the url https://www.oracle.com/technetwork/apps-tech/jdbc-112010-090769.html
- Accept the license agreement by clicking of the radio button in front of the 'Accept License Agreement'.
- Oracle would ask you to create an account if you don't have one already. If you already have an account you can simply login to your account.
- Click on ojdbc6.jar. Download will start in few seconds.
  
### Steps to download teradata jar
- Go to the url https://downloads.teradata.com/download/connectivity/jdbc-driver.
- For downloading teradata jar, account needs to be created on teradata website.
- Downloads will be available in either .tar or .zip format. Archive file name will be in the format TeraJDBC__indep_indep_{version}.zip. Download the archive file.
- The archive file with contain two jar files terajdbc4.jar and tdgssconfig.jar. Both jars are needed for teradata jdbc functionality to work.

### Steps to include Oracle into the project
- Run this command to include Oracle jar into maven repo. Run this command from folder where Oracle jars are present. Change the value of ```-Dversion=11.2.0.3``` in the command according to downloaded jar version.
- <b>Command</b>  :````docker run -e MAVEN_OPTS="-Xmx1024M -Xss128M -XX:MetaspaceSize=512M -XX:MaxMetaspaceSize=1024M -XX:+CMSClassUnloadingEnabled" --rm -v $(pwd):/workdir  -v $HOME/.m2/:/root/.m2/ -w /workdir maven:alpine mvn install:install-file -Dfile=ojdbc6.jar -DgroupId=com.oracle -DartifactId=ojdbc6 -Dversion=11.2.0.3 -Dpackaging=jar````
- Add below mentioned dependency to pom.xml(core/pom.xml) in DataPull core. Replace {version} with the downloaded jar version.
```
<dependency> 
    <groupId>com.oracle</groupId>
    <artifactId>ojdbc6</artifactId>
    <version>{version}</version>
</dependency>
```  
### Steps to include Teradata into the project
   - Run these commands to include Teradata jars into maven repo. Run this command from folder where Teradata jars are present. Change the value of ```-Dversion=16.20.00.10``` in the command according to downloaded jar version.
   - <b>Commands</b>  : ````docker run -e MAVEN_OPTS="-Xmx1024M -Xss128M -XX:MetaspaceSize=512M -XX:MaxMetaspaceSize=1024M -XX:+CMSClassUnloadingEnabled" --rm -v $(pwd):/workdir  -v $HOME/.m2/:/root/.m2/ -w /workdir maven:alpine mvn install:install-file -Dfile=terajdbc4.jar -DgroupId=com.teradata -DartifactId=terajdbc4 -Dversion=16.20.00.10 -Dpackaging=jar````
                         
     ````docker run -e MAVEN_OPTS="-Xmx1024M -Xss128M -XX:MetaspaceSize=512M -XX:MaxMetaspaceSize=1024M -XX:+CMSClassUnloadingEnabled" --rm -v $(pwd):/workdir  -v $HOME/.m2/:/root/.m2/ -w /workdir maven:alpine mvn install:install-file -Dfile=tdgssconfig.jar -DgroupId=com.teradata -DartifactId=tdgssconfig -Dversion=16.20.00.10 -Dpackaging=jar````
   
   - Add below mentioned dependency to pom.xml(core/pom.xml) in DataPull core. Replace {version} with the downloaded jar version.
``` 
<dependency>
    <groupId>com.teradata</groupId>
    <artifactId>terajdbc4</artifactId>
    <version>{version}</version>
</dependency>

<dependency>
    <groupId>com.teradata</groupId>
    <artifactId>tdgssconfig</artifactId>
    <version>{version}</version>
</dependency>
```      
## Create DataPull Infrastructure
- Have available, the AWS access key ```<aws_admin_access_key>``` and secret key ```<aws_admin_secret_key>``` of an IAM user that can create IAM users and IAM roles in your AWS account
    -   It is advisable that AWS access key ```<aws_admin_access_key>``` and secret key ```<aws_admin_secret_key>``` have admin access to the AWS account. Hence, this step would be typically done by the team managing the AWS account.
- Have a S3 bucket ```<datapull_s3_bucket>``` (this bucket can be an existing bucket or a new bucket) in which the folder ```datapull-opensource``` will be used to store DataPull's artifacts and logs
- Know a valid AWS VPC subnet id ```<aws_subnetid_private>``` in the region ```<aws_region>``` accessible to your clients, ideally not accessible from the internet; but has access to the internet
- Know a valid AWS security group ```<aws_security_grp>``` in the region ```<aws_region>``` accessible to your clients, ideally not accessible from the internet; but has access to the internet
- Have Docker installed on the machine used for deployment
- The Oracle ojdbc jar should be present in the resources folder of DataPull core component i.e. core/src/main/resources/ojdbc-jar/. The process of downloading ojdbc jar is explained at the end of this file.

### Create IAM User and Roles, with policies

- From the terminal at the root of the repo, run ```cd api/src/main/resources/terraform/datapull_iam/```
- ```chmod +x create_user_and_roles.sh```
- Run ```./create_user_and_roles.sh <aws_admin_access_key> <aws_admin_secret_key> <aws_region> <datapull_s3_bucket> <docker_image_name>``` . The script will produce a ton of stdout data, that ends with 
```
Outputs:

datapull_user_access_key = <datapull_user_access_key>
datapull_user_secret_key = <datapull_user_secret_key>
```
- Record ```<datapull_user_access_key>``` and ```<datapull_user_secret_key>``` for use in the next section

### Create AWS Fargate API App

- From the terminal (assuming you are using the same terminal session as the previous steps), run ```cd ./../datapull_task/```
    - If you're at the root of the repo, run ```cd api/src/main/resources/terraform/datapull_task/```
- ```chmod +x ecs_deploy.sh```
- ```./ecs_deploy.sh <datapull_user_access_key> <datapull_user_secret_key> <docker_image_name> <env>```

### Browse to the DataPull API swagger endpoint

- On AWS Console, navigate to ```Services > Elastic Container Service > Clusters > datapullwebapi > Tasks > <id_of_task>```
- If the HCL resource attribute ```aws_ecs_service.datapullwebapi_service.network_configuration.assign_public_ip``` was set to false, take the Private IP of the task as ```<datapull_api_ip>``` else take this as the Public IP
- Open the API endpoint url ```http://<datapull_api_ip>:8080/swagger-ui.html#!/data45pull45request45handler/startDataPullUsingPOST```

## Do your first DataPull
- Create a csv file at the S3 location ```s3://<datapull_s3_bucket>/datapull-opensource/data/firstdatapull/source/helloworld.csv``` with the following data
```
hellofield,worldfield
hello,world
```
- Post the following JSON input to the API endpoint url ```http://<datapull_api_ip>:8080/swagger-ui.html#!/data45pull45request45handler/startDataPullUsingPOST``` .
    - Please remember to replace ```<your_id@DOMAIN.com>``` and ```<datapull_s3_bucket>``` with valid data. 
```
{
    "useremailaddress": "<your_id@DOMAIN.com>",
    "precisecounts": true,
    "migrations": [
        {
            "sources": [
                {
                    "platform": "s3",
                    "s3path": "<datapull_s3_bucket>/datapull-opensource/data/firstdatapull/source",
                    "fileformat": "csv",
                    "savemode": "Overwrite"
                }
            ],
            "destination": {
                "platform": "s3",
                "s3path": "<datapull_s3_bucket>/datapull-opensource/data/firstdatapull/destination",
                "fileformat": "json",
                "savemode": "Overwrite"
            }
        }
    ],
    "cluster": {
        "pipelinename": "firstdatapull",
        "awsenv": "dev",
        "portfolio": "Data Engineering Services",
        "terminateclusterafterexecution": "true",
        "product": "Data Engineering Data Tools",
        "ComponentInfo": "00000000-0000-0000-0000-000000000000"
    }
}
```
- In approximately 8 minutes (to account for the time taken for the ephemeral EMR cluster to spin up), you should get the data from the CSV converted into JSON and written to the S3 folder ```s3://<datapull_s3_bucket>/datapull-opensource/data/firstdatapull/destination/```
- The logs for each DataPull invocation are available at ```s3://<datapull_s3_bucket>/datapull-opensource/logs/DataPullHistory```. The logs for each migration with DataPull invocations are available at ```s3://<datapull_s3_bucket>/datapull-opensource/logs/MigrationHistory```
- If you had configured the anonymous SMTP server for DataPull to send you email reports, you could get a report with the subject ```DataPull Report - firstdatapull (application_<random_numbers>)```