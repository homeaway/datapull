<p align="center">
  <img width="222" height="207" src="./media/logo.png">
</p>

# DataPull

DataPull is a self-service tool provided by HomeAway's Data Tools team to migrate data across heterogeneous datastores effortlessly. When deployed to Amazon AWS, DataPull spins up EMR Spark infrastructure, does the data movement and terminates the infrastructure once the job is complete; to minimize costs. 
Multiple data migrations can be done ither serially or in parallel within a DataPull job. There also exists built-in integration with [Hashicorp Vault](https://www.vaultproject.io/) so that datastore credentials are never exposed. DataPull also has a built-in scheduler for daily/recurring jobs; or its REST API endpoints can be invoked by a third-party scheduler.

## Platforms supported

DataPull supports the following datastores as sources and destinations.

| Platform | Source | Destination |
|:---: |:---: |:---: |
| SQL Server | ✔ | ✔ |
| Cassandra | ✔ | ✔ |
| Mongodb | ✔ | ✔ |
| S3 | ✔ | ✔ |
| FileSystem | ✔ | ✔ |
| SFTP | ✔ | ✔ |
| Elasticsearch | ✔ | ✔ |
| Kafka | ✔ |**X**|
| Neo4j |**X** | ✔ |
| MySql | ✔ | ✔ |
| Postgres | ✔ | ✔ | 
| InfluxDB | ✔ | ✔ | 
| Hive     | ✔ |**X**|

## How to use DataPull

###  Steps common to all environments (Dev/Test/Stage/Prod)

* Create a json file/string that has the source/s, destination/s and the portfolio information for tagging the ephemeral infrastructure needed to do the DataPull
  * Here are some sample JSON files for some common use cases: https://github.com/homeaway/datapull/blob/master/core/src/main/resources/Samples
  * Here is JSON specification document which has all the possible options supported by DataPull: https://github.com/homeaway/datapull/blob/master/core/src/main/resources/Samples/Input_Json_Specification.json
* Please provide an email address for the element "useremailaddress" in the JSON input. Once your DataPull completes, an email will be sent with all the migration details along with the source, destination details of the migrations, the time taken for the migrations to complete and the number of records processed, etc. 
* For DataPull to get access to the source and destination data platforms, you need to either provide the login and password within the JSON (not recommended for Stage and Production environments); or provide the login and an IAM Role which is mapped to the credentials in Vault (you also need to add the ```"awsenv"``` and ```"vaultenv"``` elements to the source(s) and destination in the json input).
* Navigate to your swagger api URL and invoke this REST API with the json as input.


## More information about the Input JSON

The input JSON mainly divided into Three sections.

1. Basic Information about the Job
1. Migrations
1. Cluster

The basic information section is there for allowing the users to put their email address, enabling the jobs to run in parallel or serial etc.

Migrations section is an array and primarily covers source and the destination details of a migration. It can have multiple migration objects. And enabling the any options/features is very easy, we just have to add the element as specified in the Input_specifications document which is available in the resources folder. If you want to migrate only set of columns of the source data, DataPull supports this by using Mappings array inside a migration object and Please check the sample JSONs available for a example. 

Cluster section is mostly related to the infrastructure of the migration job which has details about the portfolio, product, Cron expression and environment in which the job will be running etc. The cluster will be spun up based on the awsenv(aws environment) element in this section among dev/test/stage/prod. 


[This sample JSON input](https://github.com/homeaway/datapull/blob/master/core/src/main/resources/Samples/Input_Sample_MySql_to_S3.json) moves data from MySql to AWS S3


## How does DataPull work?

Spark is the main engine which drives the DataPull. So technically it will run on any spark environment. But we are biased to Amazon's Elastic Map Reduce(EMR) to have minimum dependencies(AWS versus AWS+Qubole) and our app does use EMR to spin up the spark clusters. But after all as it is a spark application it will run on any spark cluster whether it is EMR or Qubole or any other spark clusters.

DataPull expects the Input JSON as an argument and irrespective of any spark environment we have to pass the Input JSON as an argument to make the DataPull work.   

### How do I do Delta Moves for DataPull?
Here is an example
```
{
  "useremailaddress": "your_id@expediagroup.com",
  "migrations": [
    {
      "sources": [{
        "platform": "cassandra",
        "cluster": "cassandracluster",
        "keyspace": "terms_conditions",
        "table": "your_table",
        "login": "your_appuser",
        "awsenv": "prod",
        "vaultenv": "prod",
        "alias":"source"
      },
      {
        "platform": "s3",
        "s3path": "bucketpath",
        "fileformat": "json",
        "alias":"destination"
      }
    ],
      "destination": {
        "platform": "s3",
        "s3path": "bucketpath",
        "fileformat": "json"
      },
      "sql":{
        "query":"select source.* from source LEFT JOIN destination on source.id= destination.id where destination.id=null"
      }
    }
  ],
  "cluster": {
    "pipelinename": "yourpipeline",
    "awsenv": "prod",
    "portfolio": "payment-services",
    "product": "payment-onboarding",
    "ec2instanceprofile": "instanceprofile",
    "cronexpression": "0/15 * * * *",
    "terminateclusterafterexecution": "false",
    "ComponentInfo": "someuuid"
  }
}
```
## Run your DataPull on a spark cluster
### If you are starting from scratch...

* Prepare the Input JSON as explained in the above section with all the required information.
* Then please go to swagger API URL and submit the JSON in the _inputJson_ field provided and click _Try it out!_

### For Elastic Search In the field of mappingid it is recommended to use _id or id as fieldnames as they are a unique keyword in Elastic Search.
  
### How to use delta DataPulls?
So lets say you did a DataPull, however in the second datapull you do not want to repopulate everything but only need the deltas of new records. Here is what is needed.

> Pre-req: CDC needs to enabled on the database

*  Setup CDC for your specified tables.
*  It creates a watermarking table which essentially allows you to calculate the deltas.
*  Next is you create your json but with Pre-migrate and Post migrate steps.


In your pre-migrate step you populate your watermarking table with defaults and you update the watermark_from with watermark to field. And as part of post migration field you reset the watermark_to field with now date so you are ready for the future datapull

Once this is complete you can revert back to the original of how to use DataPull again.

### How to schedule a DataPull ?

Please add an element _cronexpression_ in the cluster section of the Input Json. For example, `"cronexpression": "0 21 * * *"` executes DataPull every day at 9 PM UTC.

## Contributors to DataPull

The current list of contributors are tracked by Github at https://github.com/homeaway/datapull/graphs/contributors . Prior to being opensourced, DataPull was an innersourced project at Vrbo, that evolved with contributions from

* Arturo Artigas
* Nirav N Shah
* Pratik Shah
* Ranjith Peddi
* Rohith Mark Varghese
* Sandeep Nautiyal
* Satish Behra
* Selvanathan Ragunathan
* Srinivas Rao Gajjala
* Virendra Chaudhary
