disqus:
One of the popular use cases is of join operation. Please note this example is join of filesystem but it can be of any other sources.
```json
{
  "useremailaddress": "your_email_address",
  "migrations": [
    {
      "sources": [
        {
          "platform": "filesystem",
          "path": "/Users/Documents/Code/DataMigrationFramework/src/main/resources/SampleData/HelloWorld.csv",
          "fileformat": "csv",
          "alias":"A"
        },
        {
          "platform": "filesystem",
          "path": "/Users/Documents/Code/DataMigrationFramework/src/main/resources/SampleData/HelloWorld.csv",
          "fileformat": "csv",
          "alias":"B"
        },
        {
          "platform": "filesystem",
          "path": "/Users/Documents/Code/DataMigrationFramework/src/main/resources/SampleData/HelloWorld.csv",
          "fileformat": "csv",
          "alias":"C"
        }
      ],
      "destination": {
        "platform": "filesystem",
        "path": "/Users/Documents/Code/DataMigrationFramework/src/main/resources/SampleData_Json",
        "fileformat": "json",
        "groupbyfields": "IntField"
      },
      "relationships":[
        {
          "relation":"A.IntField = B.IntField"
        },
        {
          "relation":"B.IntField = C.IntField"
        }
      ],
      "mappings": [
        {
          "sources": [
            "HelloField", "WorldField"
          ],
          "destinations": [
            {
              "column": "HelloWorldField",
              "transform": "CONCAT(A.HelloField, ' ' ,B.WorldField)",
              "isrequired": false
            }
          ]
        },
        {
          "sources": [
            "IntField"
          ],
          "destinations": [
            {
              "column": "IntField",
              "transform": "C.IntField",
              "isrequired": false
            }
          ]
        }
      ]
    }
  ],
  "cluster": {
    "pipelinename": "datapull",
    "awsenv": "dev",
    "portfolio": "Data Engineering Services",
    "product": "Data Engineering - COE",
    "ec2instanceprofile": "Iam role",
    "cronexpression":"21 * * * *",
    "ComponentInfo":"YOUR_Component-UUID_dominion"
  }
}
```

   