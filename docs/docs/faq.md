#FAQs

#### Can I use datapull to bulk delete rows in my Cassandra database?

The SQL used for migrating Cassandra to Cassandra cannot be used to issue `DELETE`s, but it can update the TTL on rows using a spark option. To run a bulk delete, just set both the source and destination to the same table, select only the rows that you want to delete, and set a small TTL on them using a nonzero value in the `spark.cassandra.output.ttl` field in your `sparkoptions` block.  (This is technically only an update, but it will have the desired effect of deleting the selected rows when the TTL runs out.)  

Example: 
In a table with columns `foo`, `bar`, and `baz`, the following block in your input JSON could be used to delete all rows where `baz` is set to `true`.

```json
{
  "migrations": [
    {
      "source": {
        ...
        "table": "my_foo_table",
        "alias": "source_table"
      },
      "sql":{
        "query": "SELECT foo, bar, baz FROM source_table WHERE baz = true"
      },
      "destination": {
        ...
        "table": "my_foo_table",
        "sparkoptions" : {
          "spark.cassandra.output.ttl" : "1"
        }
      }
    }
  ]
  }
```


!!! note
    For Elastic Search as a platform, it is recommended not to use _id or id as field names of mappingid as they are unique keyword in Elastic Search.

#### How can I submit a DataPull job which should run exactly once?

To make the DataPull job to run only once, we can remove the `cronexpression` parameter from the json, then it will be executed only once.

#### How to replace or upsert documents in MongoDB using DataPull?

We can use replace documents by setting up the option `replacedocuments` to `true`(By default it is true) which will replace the whole document with the recent one which is having the same _id.

If we want to upsert new columns to the existing documents we can the `replacedocuments` to `false` and we have to explicitly select use the same _id along with the new columns and in case if we don't have the same _id on the source side then we can read destination mongo collection as one of the source and joining that with the other source/s then pushing it to the destination will upsert the documents existing.

#### How does DataPull behave when reading data from MongoDB collection which is having more than one schema?

DataPull uses dataframes to move data from source/s to destination. Dataframes are schema bound i.e it expects a single schema across the whole dataset. To find the schema of a Mongo Collection, DataPull will sample a few documents in the Collection (the sample size is configurable). To conclude Datapull can't reliably migrate a collection/dataset which has multiple schemas in it.

#### How do I convert an Array of Doubles/Strings to a string  using Spark SQL?
     
For example - "categories":["geoAdmin:continent","meta:highLevelRegion"] then we can use `CONCAT('[',concat_ws(',',categories),']') as categories`.
    
    
#### How do I convert Array of Jsons to a String?  
For example - "localizedNames":[{"lcid":1025,"value":"أنتاركتيكا","extendedValue":"أنتاركتيكا"},{"lcid":1028,"value":"南極洲","extendedValue":"南極洲"}] then we can use `to_json(localizedNames) as localizedNames`

#### How do I convert String UUID to Type4 UUID?  
 For moving data to MongoDB with custom _id or any UUID's as UUID than as a String, we wrote a custom function.
 we can use `uuidToBinary(uuid_colum) as _id` in the sql column of the json. and can use `binaryToUUID(_id) as column_name` to do the vice versa.

#### How do I convert String to Parallax Hash?  
 For converting email address or any other PII data to parallax Hash, we wrote a custom function.
 we can use `stringToParallaxHash(string) as _hash` in the sql column of the json.

For any Spark SQL functions please refer to [https://spark.apache.org/docs/2.2.0/api/java/org/apache/spark/sql/functions.html](https://spark.apache.org/docs/2.2.0/api/java/org/apache/spark/sql/functions.html) and If you don't find any, feel free to let us know we are happy to write the custom function or you can contribute back to the tool.

#### How to I create a complex document structure with nested arrays and subdocuments, using Spark SQL?
Let's assume you want to create an array within a document within an array within a document, like this ...
```json
{
    "unitUuid": "00000000-0000-0000-0000-000000000000",
    "unitInfo": [
        {
            "UnitID": "3000523",
            "Y": [
                "204785"
            ]
        }
    ]
}
```
Here's the Spark SQL statement that will produce the above document.
```sql
select 
UA.unitUuid
, UA.cibEnabled 
, ( 
    select 
    collect_set ( 
        named_struct( 
            'UnitID', U.unitId 
            , 'Y', ( 
                select collect_set(P.listingNumber)  
                from P 
                where U.propertyEntityId = P._id 
            )
        ) 
    ) 
    from U 
    where UA.unitEntityId = U._id 
) as unitInfo 
from UA
```

## Common errors and their fixes
### EMR Pipeline errors
#### Symptom
You submit a DataPull job either through the UI or through the REST API endpoint and the EMR cluster isn't created. 
#### Fixes
* Check if IAM role or secret and access key given to API service is having permission to create EMR cluster.
* If this is a scheduled job (i.e you have specified a cron expression in your JSON input) the pipeline will not start building until the scheduled time (which is in UTC) becomes current. 
* If this is an ad-hoc job (i.e. you haven't specified a cron expression in your JSON input) the system automatically schedules the pipeline to run approximately 5 minutes from the time you submitted the JSON through the UI/REST api endpoint.


#### Symptom
You are unable to move data from an S3 bucket in Dev environment to an InfluxDB cluster in Production environment
#### Fixes

This is a known limitation that affects InfluxDB alone; and there is an easy workaround
- Do a DataPull from the S3 bucket in the Dev environment to an S3 bucket in the Production environment; using a Spark cluster in the Production environment. 
    - You will need to provide the AWS access key and secret key for the Dev environment, in the input json. 
    - You need not provide the AWS access key and secret key for the Production environment, in the input json since the IAM role that the Spark cluster runs on would/should usually have access to the S3 buckets in the same environment. 
- Do a DataPull from the S3 bucket in the Production environment to the InfluxDB cluster in the production environment; using a Spark cluster in the Production environment. 

!!! note "Please note that"
    - this limitation does not affect any other source-destination pair as of 2019-01-08 i.e. you can move data from a dev S3 bucket to a production MongoDB cluster; you can move data from a dev Cassandra cluster to a production InfluxDb cluster etc.
    - if Production Isolation is fully enforced i.e. once there is no network access between the production and non-production environments, no tool including DataPull will be able to move data between production and non-Production environments without approval from the Security team and an exemption from the Network team.

### Vault errors
#### Symptom
You get the following error in the email report

```java
java.io.IOException: Server returned HTTP response code: 403 for URL: https://vault-url/clustername/login at 
```

#### Fixes
The issue here is that Vault was unable to find the password for your clustername and loginname. Please check the following

* The clustername and login name are case-sensitive. 
* The clustername in Vault does not match the name you provided in Vault. 

#### Getting error when reading from a cassandra table from the local environment?
```
Error: INFO FileFormatWriter: Job null committed.
Exception in thread “main” java.lang.NoClassDefFoundError: org/apache/commons/configuration/ConfigurationException
    at org.apache.spark.sql.cassandra.DefaultSource$.<init>(DefaultSource.scala:135)
    at org.apache.spark.sql.cassandra.DefaultSource$.<clinit>(DefaultSource.scala)
        
```

####Fixes 
Please add the below dependency to the core pom and re run the job.

```xml
    <dependency>
        <groupId>commons-configuration</groupId>
        <artifactId>commons-configuration</artifactId>
        <version>1.10</version>
    </dependency>
```