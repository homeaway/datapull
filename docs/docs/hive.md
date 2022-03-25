```json
{
  "useremailaddress": "YOUR_ID@DOMAIN",
  "migrations": [
    {
      "source": {
        "platform": "hive",
        "url": "your_jdbc_hive_url",
        "dbtable":"your_dbtable",
        "username":""
      },
      "destination": {
        "platform": "filesystem",
        "path": "target/classes/SampleHiveData_Json",
        "fileformat": "json"
      }
    }
  ],
  "cluster": {
    "pipelinename": "ekg",
    "awsenv": "dev",
    "portfolio": "Data Engineering Services",
    "product": "Data Engineering - COE",
    "ec2instanceprofile": "Iam role",
    "cronexpression":"21 * * * *"
  }
}
```

```json
{
  "useremailaddress": "YOUR_ID@DOMAIN.com",
  "migrations": [
    {
      "source": {
        "platform": "s3",
        "s3path": "BUCKET_NAME/FOLDER_PATH",
        "fileformat": "json",
        "enable_server_side_encryption": "true",
        "alias":"test32"
      },

      "destination": {
        "platform": "hive",
        "table": "TABLE_NAME",
        "database": "DATABASE_NAME",
        "format": "parquet",
        "partitions": true
      }
    }
  ],
  "cluster": {
    "pipelinename": "sample_pipeline",
    "awsenv": "dev",
    "portfolio": "Data Engineering Services",
    "product": "Data Engineering - COE",
    "ec2instanceprofile": "Iam role",
    "cronexpression":"21 * * * *",
    "ComponentInfo":"YOUR_Component-UUID_dominion"
  }
}
```

