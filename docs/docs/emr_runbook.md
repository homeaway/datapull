disqus:
# Runbook for using DataPull deployed on AWS EMR #
## 1. Prepare the Pipeline JSON ##

1.1 Here's a sample JSON that joins Property information from SQL Server with units in cassandra and moves to s3. Please copy this to your favorite text editor

```json
{
    "useremailaddress": "<EMAIL_ADDRESS>",
    "migrations": [
        {
            "sources": [
                {
                    "platform": "mssql",
                    "server": "server_name",
                    "database": "db_name",
                    "table": "properties_table",
                    "login": "user_id",
                    "password": "password",
                    "alias": "properties"
                },
                {
                    "platform": "cassandra",
                    "cluster": "cassandra_server_name",
                    "keyspace": "keyspace_name",
                    "table": "units",
                    "login": "user_id",
                    "password": "password",
                    "alias": "units"
                }
            ],
            "destination": {
                "platform": "s3",
                "s3path": "bucket_name/path/",
                "fileformat": "csv"
            },
            "sql": {
                "query": "select properties.pro_id, properties.pro_city, units.unituuid from properties JOIN units ON properties.pro_id = units.propertyid limit 100"
            }
        }
    ],
    "cluster": {
        "pipelinename": "emr_cluster_name",
        "awsenv": "dev",
        "portfolio": "portfolio_name",
        "product": "product_name",
        "ec2instanceprofile": "ec2_instance_profile",
        "terminateclusterafterexecution": "false",
        "ComponentInfo":"YOUR_Component-UUID_dominion"
    }
}
```

1.2 In the JSON in your text editor, please replace

* `<EMAIL_ADDRESS>` with your email address.
* database  and cluster details with your database and cluster details.

## 2. Submit the JSON to the API ##

2.1 Copy the modified JSON from your text editor, and paste it into the swagger API inputJson.
 Swagger API can be accessed through URL - http://IP-Address:8080/swagger-ui.html#!/data45pull45request45handler/startDatapull . If api is deployed in ECS FARGATE, replace the IP-Address with IP address of task of FARGATE app. If it is deployed in local, replace IP-Address with localhost. 

2.2 Click the "Try it out!" button to queue the data pull.

## 3. Check status of the job

We can check the status of the job in spark UI. Spark UI runs on the master node of the EMR cluster. URL of the Spark UI will be http://master-node-IP:8088/cluster So, to find the master node IP address ,login into your AWS console, navigate to EMR service and find your cluster. If pipelinename is datapull and awsenv is dev, the cluster name will be dev-emr-datapull-pipeline. Find the IP address of the master node from hardware tab. 


## 4. Get the email confirmation ##

Wait for a few minutes, and check your email to get a confirmation when your data pull job is complete.

## 5. Check S3 for your data
* Log into your AWS console.
* Navigate to your S3 bucket.
* Check the data under s3 path provided in the input json.
