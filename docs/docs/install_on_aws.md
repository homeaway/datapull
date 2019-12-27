# Deploying DataPull on AWS Fargate and AWS EMR
This document helps you install DataPull on an Amazon AWS account, and run your first DataPull job of converting CSV data in AWS S3 to JSON data in AWS S3. 

In a nutshell, deploying DataPull to an AWS Account
- creates three IAM Roles
    - `datapull_task_role`, `datapull_task_execution_role` for running the DataPull REST API on AWS Fargate
    - `emr_ec2_datapull_role` for running ephemeral AWS EMR clusters
- creates an IAM User `datapull_user` temporarily for the purpose of installing the following DataPull components
    - an AWS Fargate service `datapull-web-api` with associated image in AWS ECR, and AWS Application Load Balacer (ALB)
    - an AWS CloudWatch Log Group `datapull_cloudwatch_log_group` and associated log stream
- stores DataPull JAR for EMR, job history, EMR logs in an existing AWS S3 bucket
> Note: `<xyz>` denotes a variable/attribute named `xyz` in the DataPull configuration file
## Pre-install steps
- Clone/download the master branch of this repo
- Have available, the AWS access key `<aws_admin_access_key>` and secret key `<aws_admin_secret_key>` of an IAM user that can create IAM users and IAM roles in your AWS account
    -   It is advisable that AWS access key `<aws_admin_access_key>` and secret key `<aws_admin_secret_key>` have admin access to the AWS account. Hence, typically these credetials will be available only to the team managing the AWS account; and hence the team deploying DataPull will need to coordinate with the team managing the AWS account.
- Have a S3 bucket `<s3_bucket_name>` (this bucket can be an existing bucket or a new bucket) in which the folder `datapull-opensource` will be used to store DataPull's artifacts and logs
- Have available a VPC with ID `<vpc_id>` in the AWS region `<region>`
- Have available, two AWS VPC subnet ids `<application_subnet_1>`,  `<application_subnet_2>` in the region VPC with ID `<vpc_id>` accessible to your clients. These subnets should ideally not accessible from the internet; but they should have access to the internet.
- Have available, security groups `<application_security_group>`, `<emr_security_group_master>`,  `<emr_security_group_slave>`, `<emr_security_group_service_access>` in the region VPC with ID `<vpc_id>` accessible to your clients, ideally not accessible from the internet; but has access to the internet. 
    - For `<emr_security_group_master>`,  `<emr_security_group_slave>`, `<emr_security_group_service_access>` , it is recommended that you use the managed security groups `ElasticMapReduce-Master-Private`, `ElasticMapReduce-Slave-Private`, `ElasticMapReduce-ServiceAccess` that are created by AWS automatically when an EMR cluster is created, ref https://docs.aws.amazon.com/emr/latest/ManagementGuide/emr-man-sec-groups.html
- Have Docker installed on the machine used for deployment
- If the DataPull API is to be served over HTTPS (say, at https://datapull.yourdomain.com), have available the ARN `<load_balancer_certificate_arn>` of an existing Certificate stored in AWS Certificate Manager, that is valid for `datapull.yourdomain.com`
- Choose which environment this deployment is for. Valid environments are 
    - `dev`
    - `test`
    - `stage`
    - `prod`
## Installation Steps
### Edit master_application_config-<env>.yml
This file is present in the root directory of the repo.
#### Required attributes (excluding those with defaulted values)
- datapull.application.region: Put `<region>` here
- datapull.api.s3_bucket_name: Put `<s3_bucket_name>` here
- datapull.api.application_subnet_1: Put `<application_subnet_1>` here
- datapull.api.application_subnet_2: Put `<application_subnet_2>` here
- datapull.api.application_security_group: Put `<application_security_group>` here
- datapull.api.vpc_id: Put `<vpc_id>` here
- datapull.emr.emr_security_group_master: Put `<emr_security_group_master>` here
- datapull.emr.emr_security_group_slave: Put `<emr_security_group_slave>` here
- datapull.emr.emr_security_group_service_access: Put `<emr_security_group_service_access>` here
#### Optional attributes
The following attributes need to be specified if you need DataPull to retrieve credentials from Hashicorp Vault to connect to your data stores. 
> For this functionality to work, you need 
> - DataPull to run on AWS EMR (this is the default behaviour), or on a Spark cluster whose node(s) run on AWS EC2.
> - a Hashicorp Vault cluster whose API is accessible at <vault_url> (e.g. https://myvault.mydomain.com:8200)
> - credentials for the database cluster <cluster> in the format `{ "username": "<username>", "password": "<password>" }` stored as a static secret at the location `<vault_url>/<static_secret_path_prefix>/<cluster>/<username>` . For example, if you need DataPull to retrieve the credentials for the user `mydbuser` of the MySql cluster `mycluster` from the Vault cluster static secret path https://myvaultcluster:8200/v1/secret/mycluster/mydbuser , you will need to store the credentials using the cUrl command `curl -X POST https://myvaultcluster:8200/v1/secret/mycluster/mydbuser -H 'X-Vault-Token: <valid Vault token>' -d '{ "username": "mydbuser", "password": "<password>" }'`
> - [AWS EC2 Auth method](https://www.vaultproject.io/docs/auth/aws.html#ec2-auth-method) set up on the Vault cluster, and the IAM Role of the Spark cluster mapped to a Vault policy that allows it to read the secret at the location `<vault_url>/<static_secret_path_prefix>/<cluster>/<username>`

- datapull.secretstore.vault.vault_url: Put `<vault_url>` here
- datapull.secretstore.vault.vault_nonce: Put a [Client nonce](https://www.vaultproject.io/docs/auth/aws.html#client-nonce) here
- datapull.secretstore.vault.static_secret_path_prefix: Put `<static_secret_path_prefix>` here
- datapull.secretstore.vault.vault_path_login: Put the path to the login url for the AWS EC2 Auth endpoint. e.g. `/v1/auth/<AWS Auth endpoint name>/login`

The following attribute needs to set if the DataPull API needs to be served over HTTPS.
- datapull.api.load_balancer_certificate_arn: Put `<load_balancer_certificate_arn>` here

The following attributes need to be set if you need DataPull to alert you if a job runs for too little time or if it runs too long. 
> For this functionality, DataPull needs a SQL Server table `<table>` in the database `<database>` on the SQL Server instance `<server>`; and an SQL Server login `<login>` with password `<password>` that can write to this table. If this table does not exist, please create this table with the following schema
> ```
> CREATE TABLE [<table>](
>    [JobId] [nvarchar](max) NULL,
>    [Portfolio] [nvarchar](max) NULL,
>    [Product] [nvarchar](max) NULL,
>    [MasterNode] [nvarchar](max) NULL,
>    [Ec2Role] [nvarchar](max) NULL,
>    [elapsedtime] [float] NOT NULL,
>    [minexecutiontime] [bigint] NOT NULL,
>    [maxexecutiontime] [bigint] NOT NULL,
>    [status] [nvarchar](max) NULL,
>    [InstantNow] [nvarchar](max) NULL,
>    [processedflag] [bigint] NOT NULL,
>    [EmailAddress] [nvarchar](max) NULL,
>    [pipelineName] [nvarchar](max) NULL,
>    [awsenv] [nvarchar](max) NULL,
>    [BccEmailAddress] [nvarchar](max) NULL
>)
> ```
- datapull.logger.mssql.server: Put `<server>` here
- datapull.logger.mssql.database: Put `<database>` here
- datapull.logger.mssql.login: Put `<login>` here
- datapull.logger.mssql.password: Put `<password>` here
- datapull.logger.mssql.table: Put `<table>` here

The following attributes need to be set if you need DataPull to send an email report once each DataPull job completes, from the email address `<emailaddress>` through the SMTP server/relay `<smtpserveraddress>`
- datapull.logger.smtp.emailaddress: Put `<emailaddress>` here
- datapull.logger.smtp.smtpserveraddress: Put `<smtpserveraddress>` here

The following attributes need to be set if you need DataPull to send an email report once each DataPull job completes, from the email address `<email>`  using an existing SES instance that is accessiable using the AWS credentials `<access_key>`/`<secret_key>`
- datapull.logger.ses.email: Put `<email>` here
- datapull.logger.ses.access_key: Put `<access_key>` here
- datapull.logger.ses.secret_key: Put `<secret_key>` here

## Oracle and Teradata support
For performing Data migration on Oracle and Teradata, the respective jars needs to be manually downloaded from their Company's repos and have to add the dependency in pom.xml file which is present in the Core section of the DataPull. 

### Steps to download ojdbc jar
- Go to the url https://www.oracle.com/technetwork/apps-tech/jdbc-112010-090769.html
- Accept the license agreement by clicking of the radio button in front of the 'Accept License Agreement'.
- Oracle would ask you to create an account if you don't have one already. If you already have an account you can simply login to your account.
- Click on ojdbc6.jar which will start the download.
  
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
### Create IAM User and Roles, with policies

- From the terminal at the root of the repo, run ```cd api/terraform/datapull_iam/```
- ```chmod +x create_user_and_roles.sh```
- Run ```./create_user_and_roles.sh <aws_admin_access_key> <aws_admin_secret_key> <aws_region> <datapull_s3_bucket> <docker_image_name>``` . The script will produce a ton of stdout data, that ends with 
```
Outputs:

datapull_user_access_key = <datapull_user_access_key>
datapull_user_secret_key = <datapull_user_secret_key>
```
- Record ```<datapull_user_access_key>``` and ```<datapull_user_secret_key>``` for use in the next section

#### Changes needed if the API has to be served with HTTPS
- From the terminal, run ```cd ../datapull_task/```
- Open the file `datapull_ecs.tf` in an editor or run this command ```vi datapull_ecs.tf```
-  Search for the block with `datapull-web-api-targetgroup` or `resource "aws_alb_target_group" "datapull-web-api-targetgroup"` and change the following:
    - Change the protocol from `HTTP` to `HTTPS`
    - Change the port from `8080` to `443`

### Create AWS Fargate API App and other AWS resources 
- From the terminal (assuming you are using the same terminal session as the previous steps), run ```cd ../datapull_task/```
    - If you're at the root of the repo, run ```cd api/terraform/datapull_task/```
- ```chmod +x ecs_deploy.sh```
- ```./ecs_deploy.sh <datapull_user_access_key> <datapull_user_secret_key> <docker_image_name> <env>```

### Browse to the DataPull API swagger endpoint

- On AWS Console, navigate to ```Services > Elastic Container Service > Clusters > datapull-web-api > Tasks > <id_of_task>```
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