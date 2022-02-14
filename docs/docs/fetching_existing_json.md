# Fetching Existing Json For Sample
The existing input configuration json for the jobs which are getting executed can be fetched by the user. These jobs might be created by other users but can be fetched based on the source and destination platform.
check few of the example below.

###Single Source 
Here we have single source as elasticsearch.
##### GET /api/v1/getSampleInputJson?source=elastic
```json
{
  "status": 200,
  "message": [
    {
      "useremailaddress": "<email address>",
      "migrations": [
        {
          "singleSource": false,
          "sources": [
            {
              "platform": "elastic",
              "password": "2a9593bc72ce5153c18e8c0720cef047977bd8e9",
              "login": "booking_connectivity_datapull",
              "clustername": "eps-elasticsearch.us-west-2.prod.lodgingpartner.expedia.com",
              "index": "eps_booking_wk_2021-08-*",
              "alias": "es",
              "type": "_doc",
              "version": "6.4.0",
              "esoptions": {
                "es.port": "443",
                "es.net.ssl": "true",
                "es.net.ssl.cert.allow.self.signed": "true",
                "es.nodes.wan.only": "true"
              }
            }
          ],
          "destination": {
            "platform": "filesystem",
            "fileformat": "sequencefile",
            "path": "hdfs://chwxedwhdm002.datawarehouse.expecn.com:8020/raw/LOGGING/lpie-sync-book-message-logging-prod-temp-13/inlineexpr{{select date_format(date_sub(CAST(now() as DATE), 1), 'yyyy/MM/dd')}}"
          },
          "sql_bn": {
            "query": "SELECT\ncontent as content,\ntimestamp as timestamp,\nserviceId as serviceId,\nurl as url,\ntransactionId as transactionId,\ncorrelationId as correlationId,\nproperties.messageType as messageType,\nstruct(\n    properties.bookingItemId as bookingItemId,\n    properties.checkInDate as checkInDate,\n    properties.checkOutDate as checkOutDate,\n    properties.compensationModel as compensationModel,\n    properties.creationDate as creationDate,\n    properties.errorCode as errorCode,\n    properties.errorDescription as errorDescription,\n    properties.errorLevel as errorLevel,\n    properties.errorMessage as errorMessage,\n    properties.expediaHotelId as expediaHotelId,\n    properties.expirationDate as expirationDate,\n    properties.httpStatus as httpStatus,\n    properties.method as method,\n    properties.notificationMethod as notificationMethod,\n    properties.partnerConfirmationNumber as partnerConfirmationNumber,\n    properties.paymentType as paymentType,\n    properties.ratePlanCode as ratePlanCode,\n    properties.responderId as responderId,\n    properties.roomTypeCode as roomTypeCode,\n    properties.sourceApp as sourceApp,\n    properties.status as status,\n    properties.supplierBrandCode as supplierBrandCode,\n    properties.supplierChainCode as supplierChainCode,\n    properties.supplierHotelCode as supplierHotelCode,\n    type as type\n) as properties,\nstruct(\n    metadata.acceptedTimestamp as accepted_timestamp,\n    metadata.clientIP as client_ip,\n    metadata.consumptionHostName as consumption_hostname,\n    metadata.consumptionTimestamp as consumption_timestamp,\n    metadata.consumptionTopic as consumption_topic\n) as metadata\nFROM es\nWHERE serviceId = 'eps-booking-v2'\nlimit 30"
          },
          "destination_bn": {
            "platform": "filesystem",
            "path": "hdfs://chwxedwhdm002.datawarehouse.expecn.com:8020/raw/LOGGING/eps-booking-bn-message-logging-temp/inlineexpr{{select date_format(date_sub(CAST(now() as DATE), 5), 'yyyy/MM/dd')}}",
            "fileformat": "json"
          },
          "destination_local": {
            "platform": "filesystem",
            "path": "target/classes/raw/LOGGING/lpie-sync-book-message-logging-prod-temp/inlineexpr{{select date_format(date_sub(CAST(now() as DATE), 1), 'yyyy/MM/dd')}}",
            "fileformat": "json"
          },
          "destination_ignore_kafka": {
            "platform": "kafka",
            "bootstrapservers": "kafka-1f-us-east-1.egdp-test.aws.away.black:9093",
            "schemaregistries": "http://kafka-1f-us-east-1.egdp-test.aws.away.black:8081/",
            "topic": "lodging_connectivity_eg_delivery_data_bridge_es",
            "keyformat": "string",
            "valueformat": "string",
            "alias": "ka",
            "keystorepath": "manualtestfolder/async.booking (1).jks",
            "truststorepath": "manualtestfolder/async.booking (1).jks",
            "keystorepassword": "aq346AE$W^%$A#^",
            "truststorepassword": "aq346AE$W^%$A#&",
            "keypassword": "aq346AE$W^%$A#^"
          },
          "sql_br": {
            "query": "<SQL Query>"
          },
          "destination_sb": {
            "platform": "filesystem",
            "path": "hdfs://chwxedwhdm002.datawarehouse.expecn.com:8020/raw/LOGGING/lpie-sync-book-message-logging-prod-temp/inlineexpr{{select date_format(date_sub(CAST(now() as DATE), 2), 'yyyy/MM/dd')}}",
            "fileformat": "json"
          },
          "sql_timestamp": {
            "query": "<SQL Query>"
          },
          "sql": {
            "query": "<SQL Query>"
          }
        }
      ],
      "cluster": {
        "terminateClusterAfterExecution": "true",
        "emrInstanceCount": "18",
        "componentInfo": "00000000-0000-0000-0000-00000000000000",
        "portfolio": "Lodging Supply - Connectivity",
        "product": "Connectivity Reporting",
        "tags": {
          "Creator": "ibhuiyan@expediagroup.com",
          "Product": "Connectivity Reporting",
          "CostCenter": "80326",
          "Team": "LBT Armored-Puffins",
          "Application": "lpie-sync-book-reporting-bridge"
        },
        "pipelinename": "hdfs_full_set_test",
        "awsenv": "test",
        "sparksubmitparams": "--conf spark.executorEnv.HADOOP_USER_NAME=kafka --conf spark.appMasterEnv.HADOOP_USER_NAME=kafka --conf spark.yarn.appMasterEnv.HADOOP_USER_NAME=kafka --conf spark.yarn.executorEnv.HADOOP_USER_NAME=kafka --conf spark.sql.caseSensitive=true --conf spark.default.parallelism=3 --conf spark.storage.blockManagerSlaveTimeoutMs=1200s --conf spark.executor.heartbeatInterval=900s --packages org.apache.spark:spark-sql-kafka-0-10_2.11:2.4.4,org.apache.spark:spark-avro_2.11:2.4.4 --deploy-mode client --class core.DataPull s3://egdp-test-data-datatools-us-east-1/datapull-opensource/jars/DataMigrationFramework-1.0-SNAPSHOT-jar-with-dependencies.jar",
        "forcerestart": false
      }
    },
    {
      "useremailaddress": "<email address>",
      "migration_failure_threshold": "1",
      "precisecounts": true,
      "migrations": [
        {
          "singleSource": false,
          "sources": [
            {
              "platform": "elastic",
              "password": "YjFmMWIwZWY3YWQ2ZWEzYTM2Mzg3Y2Fm",
              "login": "epsKafkaES2",
              "jksfiles": [
                "s3://egdp-test-data-datatools-us-east-1/data/jksFiles/syncbook_hadoop_daily/escerts.jks"
              ],
              "clustername": "eps-elasticsearch2.test.expedia.com",
              "index": "eps_2021-*",
              "alias": "es",
              "type": "_doc",
              "version": "6.4.0",
              "esoptions": {
                "es.port": "443",
                "es.net.ssl": "true",
                "es.net.ssl.cert.allow.self.signed": "true",
                "es.nodes.wan.only": "true",
                "es.net.ssl.truststore.location": "file:///mnt/bootstrapfiles/escerts.jks",
                "es.net.ssl.keystore.location": "file:///mnt/bootstrapfiles/escerts.jks",
                "es.net.ssl.truststore.pass": "changeit",
                "es.net.ssl.keystore.pass": "changeit"
              }
            },
            {
              "platform": "s3",
              "s3path": "egdp-test-data-datatools-us-east-1/data/br_es_kafka/watermark",
              "fileformat": "json",
              "alias": "w"
            }
          ],
          "destination": {
            "platform": "kafka",
            "jksfiles": [
              "s3://egdp-test-data-datatools-us-east-1/data/jksFiles/syncbook_hadoop_daily/venafi_cert_test_1.jks"
            ],
            "keystorepassword": "&Wf543l%Hl2_g%:@-#|O)(#",
            "keyfield": "key",
            "keypassword": "&Wf543l%Hl2_g%:@-#|O)(#",
            "valuesubjectrecordnamespace": "com.expedia.booking.connectivity",
            "valuesubjectrecordname": "AsyncRetrievalMessageLog",
            "keystorepath": "/mnt/bootstrapfiles/venafi_cert_test_1.jks",
            "valueformat": "avro",
            "schemaregistries": "http://kafka-1b-us-east-1.egdp-test.aws.away.black:8081",
            "keyformat": "avro",
            "valuesubjectnamingstrategy": "TopicRecordNameStrategy",
            "truststorepassword": "&Wf543l%Hl2_g%:@-#|O)(#",
            "bootstrapservers": "kafka-1g-us-east-1.egdp-test.aws.away.black:9093",
            "topic": "supply_booking_connectivity_async_retrieval_messagelog_stream_v2",
            "truststorepath": "/mnt/bootstrapfiles/venafi_cert_test_1.jks"
          },
          "sql": {
            "query": "<SQL Query>"
          }
        },
        {
          "singleSource": false,
          "sources": [
            {
              "platform": "s3",
              "s3path": "egdp-test-data-datatools-us-east-1/data/br_es_kafka/watermark",
              "fileformat": "json",
              "alias": "w"
            }
          ],
          "destination": {
            "platform": "s3",
            "s3path": "egdp-test-data-datatools-us-east-1/data/br_es_kafka/watermark",
            "fileformat": "json",
            "savemode": "Overwrite"
          },
          "sql": {
            "query": "<SQL Query>"
          }
        }
      ],
      "cluster": {
        "terminateClusterAfterExecution": "false",
        "componentInfo": "00000000-0000-0000-0000-00000000000000",
        "portfolio": "Lodging Supply - Connectivity",
        "product": "Connectivity Reporting",
        "tags": {
          "Creator": "ibhuiyan@expediagroup.com",
          "Product": "Connectivity Reporting",
          "CostCenter": "80487",
          "Team": "LBT Armored-Puffins",
          "Application": "booking-connectivity-datapull-bridge"
        },
        "pipelinename": "br_es_kafka_test",
        "awsenv": "test",
        "cronexpression": "*/10 * * * *",
        "sparksubmitparams": "--conf spark.executorEnv.HADOOP_USER_NAME=kafka --conf spark.appMasterEnv.HADOOP_USER_NAME=kafka --conf spark.yarn.appMasterEnv.HADOOP_USER_NAME=kafka --conf spark.yarn.executorEnv.HADOOP_USER_NAME=kafka --conf spark.sql.caseSensitive=true --conf spark.default.parallelism=3 --conf spark.storage.blockManagerSlaveTimeoutMs=1200s --conf spark.executor.heartbeatInterval=900s --packages org.apache.spark:spark-sql-kafka-0-10_2.11:2.4.4,org.apache.spark:spark-avro_2.11:2.4.4 --deploy-mode client --class core.DataPull s3://egdp-test-data-datatools-us-east-1/datapull-opensource/jars/DataMigrationFramework-1.0-SNAPSHOT-jar-with-dependencies.jar",
        "forcerestart": false
      }
    }

  ]
}
```

### Multiple Source
Here we have two sources elasticsearch and kafka
##### GET /getSampleInputJson?sources=elastic,kafka
```json
{
  "status": 200,
  "message": [
    {
      "useremailaddress": "<email address>",
      "migrations": [
        {
          "singleSource": true,
          "source": {
            "platform": "kafka",
            "offset": "earliest",
            "bootstrapServers": "kafka.us-east-1.test.kafka.away.black:9092",
            "schemaRegistries": "http://kafka-schema-registry.us-east-1.test.kafka.away.black:8081",
            "topic": "review-content-bylisting-datasync",
            "alias": "k",
            "s3location": "s3a://ha-test-data-datamigration-lodging-rates-us-east-1/kafkaReviews_20190823_temp",
            "deSerializer": "io.confluent.kafka.serializers.KafkaAvroDeserializer"
          },
          "destination": {
            "platform": "s3",
            "s3path": "ha-test-data-datatools-us-east-1/nishah3/",
            "fileformat": "json"
          }
        }
      ],
      "cluster": {
        "terminateClusterAfterExecution": "true",
        "instanceProfile": "ekg-loader",
        "componentInfo": "6f6de438-cd41-4cf3-a96e-c131d880050a",
        "portfolio": "Reviews",
        "product": "Reviews - Review Platform",
        "tags": {},
        "pipelinename": "dptest3",
        "awsenv": "test",
        "forcerestart": false
      }
    },
    {
      "useremailaddress": "<email address>",
      "migrations": [
        {
          "singleSource": false,
          "sources": [
            {
              "platform": "elastic",
              "password": "2a9593bc72ce5153c18e8c0720cef047977bd8e9",
              "login": "booking_connectivity_datapull",
              "clustername": "eps-elasticsearch.us-west-2.prod.lodgingpartner.expedia.com",
              "index": "eps_booking_wk_2021-08-*",
              "alias": "es",
              "type": "_doc",
              "version": "6.4.0",
              "esoptions": {
                "es.port": "443",
                "es.net.ssl": "true",
                "es.net.ssl.cert.allow.self.signed": "true",
                "es.nodes.wan.only": "true"
              }
            }
          ],
          "destination": {
            "platform": "filesystem",
            "fileformat": "sequencefile",
            "path": "hdfs://chwxedwhdm002.datawarehouse.expecn.com:8020/raw/LOGGING/lpie-sync-book-message-logging-prod-temp-13/inlineexpr{{select date_format(date_sub(CAST(now() as DATE), 1), 'yyyy/MM/dd')}}"
          },
          "sql_bn": {
            "query": "SELECT\ncontent as content,\ntimestamp as timestamp,\nserviceId as serviceId,\nurl as url,\ntransactionId as transactionId,\ncorrelationId as correlationId,\nproperties.messageType as messageType,\nstruct(\n    properties.bookingItemId as bookingItemId,\n    properties.checkInDate as checkInDate,\n    properties.checkOutDate as checkOutDate,\n    properties.compensationModel as compensationModel,\n    properties.creationDate as creationDate,\n    properties.errorCode as errorCode,\n    properties.errorDescription as errorDescription,\n    properties.errorLevel as errorLevel,\n    properties.errorMessage as errorMessage,\n    properties.expediaHotelId as expediaHotelId,\n    properties.expirationDate as expirationDate,\n    properties.httpStatus as httpStatus,\n    properties.method as method,\n    properties.notificationMethod as notificationMethod,\n    properties.partnerConfirmationNumber as partnerConfirmationNumber,\n    properties.paymentType as paymentType,\n    properties.ratePlanCode as ratePlanCode,\n    properties.responderId as responderId,\n    properties.roomTypeCode as roomTypeCode,\n    properties.sourceApp as sourceApp,\n    properties.status as status,\n    properties.supplierBrandCode as supplierBrandCode,\n    properties.supplierChainCode as supplierChainCode,\n    properties.supplierHotelCode as supplierHotelCode,\n    type as type\n) as properties,\nstruct(\n    metadata.acceptedTimestamp as accepted_timestamp,\n    metadata.clientIP as client_ip,\n    metadata.consumptionHostName as consumption_hostname,\n    metadata.consumptionTimestamp as consumption_timestamp,\n    metadata.consumptionTopic as consumption_topic\n) as metadata\nFROM es\nWHERE serviceId = 'eps-booking-v2'\nlimit 30"
          },
          "destination_bn": {
            "platform": "filesystem",
            "path": "hdfs://chwxedwhdm002.datawarehouse.expecn.com:8020/raw/LOGGING/eps-booking-bn-message-logging-temp/inlineexpr{{select date_format(date_sub(CAST(now() as DATE), 5), 'yyyy/MM/dd')}}",
            "fileformat": "json"
          },
          "destination_local": {
            "platform": "filesystem",
            "path": "target/classes/raw/LOGGING/lpie-sync-book-message-logging-prod-temp/inlineexpr{{select date_format(date_sub(CAST(now() as DATE), 1), 'yyyy/MM/dd')}}",
            "fileformat": "json"
          },
          "destination_ignore_kafka": {
            "platform": "kafka",
            "bootstrapservers": "kafka-1f-us-east-1.egdp-test.aws.away.black:9093",
            "schemaregistries": "http://kafka-1f-us-east-1.egdp-test.aws.away.black:8081/",
            "topic": "lodging_connectivity_eg_delivery_data_bridge_es",
            "keyformat": "string",
            "valueformat": "string",
            "alias": "ka",
            "keystorepath": "manualtestfolder/async.booking (1).jks",
            "truststorepath": "manualtestfolder/async.booking (1).jks",
            "keystorepassword": "aq346AE$W^%$A#^",
            "truststorepassword": "aq346AE$W^%$A#&",
            "keypassword": "aq346AE$W^%$A#^"
          },
          "sql_br": {
            "query": "<SQL Query>"
          },
          "destination_sb": {
            "platform": "filesystem",
            "path": "hdfs://chwxedwhdm002.datawarehouse.expecn.com:8020/raw/LOGGING/lpie-sync-book-message-logging-prod-temp/inlineexpr{{select date_format(date_sub(CAST(now() as DATE), 2), 'yyyy/MM/dd')}}",
            "fileformat": "json"
          },
          "sql_timestamp": {
            "query": "<SQL Query>"
          },
          "sql": {
            "query": "<SQL Query>"
          }
        }
      ],
      "cluster": {
        "terminateClusterAfterExecution": "true",
        "emrInstanceCount": "18",
        "componentInfo": "00000000-0000-0000-0000-00000000000000",
        "portfolio": "Lodging Supply - Connectivity",
        "product": "Connectivity Reporting",
        "tags": {
          "Creator": "ibhuiyan@expediagroup.com",
          "Product": "Connectivity Reporting",
          "CostCenter": "80326",
          "Team": "LBT Armored-Puffins",
          "Application": "lpie-sync-book-reporting-bridge"
        },
        "pipelinename": "hdfs_full_set_test",
        "awsenv": "test",
        "sparksubmitparams": "--conf spark.executorEnv.HADOOP_USER_NAME=kafka --conf spark.appMasterEnv.HADOOP_USER_NAME=kafka --conf spark.yarn.appMasterEnv.HADOOP_USER_NAME=kafka --conf spark.yarn.executorEnv.HADOOP_USER_NAME=kafka --conf spark.sql.caseSensitive=true --conf spark.default.parallelism=3 --conf spark.storage.blockManagerSlaveTimeoutMs=1200s --conf spark.executor.heartbeatInterval=900s --packages org.apache.spark:spark-sql-kafka-0-10_2.11:2.4.4,org.apache.spark:spark-avro_2.11:2.4.4 --deploy-mode client --class core.DataPull s3://egdp-test-data-datatools-us-east-1/datapull-opensource/jars/DataMigrationFramework-1.0-SNAPSHOT-jar-with-dependencies.jar",
        "forcerestart": false
      }
    }
  ]
}
```
### Single Source &  Destination
Here the source is kafka and destination as s3 
##### GET /getSampleInputJson?sources=kafka&destination=s3
```json
{
  "status": 200,
  "message": [
    {
      "useremailaddress": "<Email Address>",
      "migrations": [
        {
          "singleSource": true,
          "source": {
            "platform": "kafka",
            "offset": "earliest",
            "bootstrapServers": "kafka.us-east-1.test.kafka.away.black:9092",
            "schemaRegistries": "http://kafka-schema-registry.us-east-1.test.kafka.away.black:8081",
            "topic": "review-content-bylisting-datasync",
            "alias": "k",
            "s3location": "s3a://ha-test-data-datamigration-lodging-rates-us-east-1/kafkaReviews_20190823_temp",
            "deSerializer": "io.confluent.kafka.serializers.KafkaAvroDeserializer"
          },
          "destination": {
            "platform": "s3",
            "s3path": "ha-test-data-datatools-us-east-1/nishah3/",
            "fileformat": "json"
          }
        }
      ],
      "cluster": {
        "terminateClusterAfterExecution": "true",
        "instanceProfile": "ekg-loader",
        "componentInfo": "6f6de438-cd41-4cf3-a96e-c131d880050a",
        "portfolio": "Reviews",
        "product": "Reviews - Review Platform",
        "tags": {},
        "pipelinename": "dptest3",
        "awsenv": "test",
        "forcerestart": false
      }
    }
  ]
}
```
### By Destination
Here we have cassandra as destination.
##### GET /getSampleInputJson?destination=cassandra
```json
{
  "status": 200,
  "message": [
    {
      "useremailaddress": "<Email Address>",
      "migrations": [
        {
          "singleSource": true,
          "source": {
            "platform": "s3",
            "s3path": "apiary-592318062153-us-east-1-supply/supply/attributes/lodging_profile_eg/snapshots/snapshot_date_id=20210201",
            "fileformat": "parquet",
            "alias": "lodging_profile_eg"
          },
          "destination": {
            "platform": "cassandra",
            "cluster": "test-cassandra-egattributestore-01",
            "keyspace": "supply",
            "table": "lodging_profile_eg_test",
            "login": "supply_appuser",
            "awsenv": "test",
            "vaultenv": "test"
          },
          "sql": {
            "query": "<SQL Query>"
          }
        }
      ],
      "cluster": {
        "instanceProfile": "emr-profile-supply-us-east-1",
        "emrServiceRole": "emr-service-supply-us-east-1",
        "componentInfo": "cf881911-f834-4b0e-a059-7967b33fe04a",
        "portfolio": "Data Engineering Services",
        "product": "Lodging Profile Attributes Loader",
        "tags": {},
        "pipelinename": "lodging-profile-eg-to-cassandra",
        "awsenv": "test",
        "emr_security_configuration": "emrsc-supply",
        "forcerestart": false
      }
    },
    {
      "useremailaddress": "<Email Address>",
      "migrations": [
        {
          "singleSource": true,
          "source": {
            "platform": "s3",
            "s3path": "apiary-592318062153-us-east-1-supply/supply/attributes/lodging_profile_eg/snapshots/snapshot_date_id=20210201",
            "fileformat": "parquet",
            "alias": "lodging_profile_eg"
          },
          "destination": {
            "platform": "cassandra",
            "cluster": "test-cassandra-egattributestore-01.service.us-east-1-vpc-0618333437f727d62.consul",
            "keyspace": "supply",
            "table": "lodging_profile_eg_test",
            "login": "supply_appuser",
            "sparkoptions": {
              "spark.cassandra.connection.ssl.enabled": null,
              "comment_truststore_path": null,
              "spark.cassandra.connection.ssl.trustStore.path": null,
              "spark.cassandra.connection.ssl.trustStore.password": null
            },
            "awsenv": "egdp-test",
            "vaultenv": "egdp-test"
          },
          "sql": {
            "query": "<SQL Query>"
          }
        }
      ],
      "cluster": {
        "instanceProfile": "emr-profile-supply-us-east-1",
        "emrServiceRole": "emr-service-supply-us-east-1",
        "componentInfo": "cf881911-f834-4b0e-a059-7967b33fe04a",
        "portfolio": "Data Engineering Services",
        "product": "Lodging Profile Attributes Loader",
        "tags": {},
        "pipelinename": "lodging-profile-eg-to-cassandra",
        "awsenv": "egdp-test",
        "emr_security_configuration": "emrsc-supply",
        "forcerestart": false
      }
    }
  ]
}
```

