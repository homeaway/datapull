/* Copyright (c) 2019 Expedia Group.
 * All rights reserved.  http://www.homeaway.com
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package core

import java.io.{FileNotFoundException, PrintWriter, StringWriter}
import java.time.Instant
import java.util.UUID
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials, DefaultAWSCredentialsProviderChain}
import com.amazonaws.regions.{DefaultAwsRegionProviderChain, Regions}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import config.AppConfig
import core.DataPull.{jsonObjectPropertiesToMap, setAWSCredentials}
import helper._
import logging._
import org.apache.spark.scheduler.{SparkListener, SparkListenerStageCompleted}
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.StructType
import org.apache.spark.util.SizeEstimator
import org.codehaus.jettison.json.{JSONArray, JSONObject}
import security.SecretsManager

import scala.collection.immutable.List
import scala.collection.mutable.ListBuffer

//this singleton object handles everything to do with a single core.Migration i.e. getting a single source to a destination
class Migration extends SparkListener {
  //do a migration dammit!

  var appConfig: AppConfig = null;

  def migrate(migrationJSONString: String, reportEmailAddress: String, migrationId: String, verifymigration: Boolean, reportCounts: Boolean, no_of_retries: Int, custom_retries: Boolean, migrationLogId: String, isLocal: Boolean, preciseCounts: Boolean, appConfig: AppConfig, pipeline: String): Map[String, String] = {
    val s3TempFolderDeletionError = StringBuilder.newBuilder
    val reportRowHtml = StringBuilder.newBuilder
    val migration: JSONObject = new JSONObject(migrationJSONString)
    val dataPullLogs = new DataPullLog(appConfig, pipeline)
    this.appConfig = appConfig;

    var sparkSession: SparkSession = null

    if (isLocal) {
      sparkSession = SparkSession.builder.master("local[*]")
        .config("spark.scheduler.mode", "FAIR")
        .appName("DataPull - local")
        .config("spark.network.timeout", "10000s")
        .config("spark.executor.heartbeatInterval", "1000s")
        .config("spark.sql.broadcastTimeout", 36000)
        // .config("spark.driver.bindAddress","127.0.0.1")
        .getOrCreate()
    } else {
      sparkSession = SparkSession.builder //.master("local[*]")
        .config("spark.scheduler.mode", "FAIR")
        .appName("DataPull")
        .config("spark.network.timeout", "10000s")
        .config("spark.executor.heartbeatInterval", "1000s")
        .config("spark.sql.broadcastTimeout", 36000)
        .config("spark.task.maxFailures", no_of_retries)
        .config("fs.s3a.multiobjectdelete.enable", true)
        .config("spark.sql.hive.metastore.version", "1.2.1")
        .config("spark.sql.hive.metastore.jars", "builtin")
        .config("spark.sql.hive.caseSensitiveInferenceMode", "INFER_ONLY")
        .enableHiveSupport()
        .getOrCreate()
    }

    var sources = new JSONArray()
    var destination = new JSONObject()

    var tableslist: List[String] = null
    var tables = new ListBuffer[String]()
    var platformslist: List[String] = null
    var platforms = new ListBuffer[String]()
    var aliaseslist: List[String] = null
    var aliases = new ListBuffer[String]()
    var processedTableCount: Long = 0
    val migrationStartTime = Instant.now()
    var verified = "NA"
    var migrationException: String = null

    var size_of_the_records: Long = 0
    var processedTableCount_precise: Long = 0

    val jobId = UUID.randomUUID().toString
    var hasExceptions: Boolean = false
    val startTime_in_milli = System.currentTimeMillis()


    dataPullLogs.jobLog(migrationLogId, migrationStartTime.toString, null, 0, migrationJSONString, 0, 0, "Started", jobId, sparkSession)

    if (preciseCounts == false) {
      sparkSession.sparkContext.addSparkListener(new SparkListener() {
        override def onStageCompleted(stageCompleted: SparkListenerStageCompleted) {
          processedTableCount += stageCompleted.stageInfo.taskMetrics.inputMetrics.recordsRead
          size_of_the_records += stageCompleted.stageInfo.taskMetrics.inputMetrics.bytesRead
          println("size on stage :" + size_of_the_records)
        }
      })
    }

    try {

      var overrideSql = ""
      if (migration.has("sql")) {

        val sql = migration.getJSONObject("sql")
        overrideSql = sql.getString("query")
      }

      if (migration.has("sources")) {
        sources = migration.getJSONArray("sources")
      }
      if (migration.has("source")) {
        sources.put(migration.getJSONObject("source"));
      }

      destination = migration.getJSONObject("destination")

      var df = sparkSession.emptyDataFrame
      // for loop execution with a range
      for (a <- 0 to sources.length() - 1) {
        var selectedSource = sources.getJSONObject(a)
        var platform = selectedSource.getString("platform")

        //run the pre-migration command for the source, if any
        jsonSourceDestinationRunPrePostMigrationCommand(selectedSource, true, reportRowHtml, sparkSession, pipeline)

        df = jsonSourceDestinationToDataFrame(sparkSession, selectedSource, migrationLogId, jobId, s3TempFolderDeletionError, pipeline)

        if (platform == "mssql") {
          size_of_the_records += SizeEstimator.estimate(df)
        }

        var store: String = "table" + migrationId + "_" + a.toString();
        tables += store;

        if (migration.has("sql")) {

          val tmpAlias = selectedSource.getString("alias")

          overrideSql = overrideSql.replaceAll("(?<!\\.)\\b" + tmpAlias + "\\b", s"$store")
        }
        aliases += (if (selectedSource.has("alias")) selectedSource.getString("alias") else "")

        df.createOrReplaceTempView(store)

        platforms += platform
      }
      tableslist = tables.toList
      platformslist = platforms.toList
      aliaseslist = aliases.toList

      //run the pre-migration command for the destination, if any
      jsonSourceDestinationRunPrePostMigrationCommand(destination, true, reportRowHtml, sparkSession, pipeline)

      //do the transforms
      var destinationMap = jsonObjectPropertiesToMap(destination)

      //add optional keysp explicitely else the map will complain they don't exist later down
      destinationMap = destinationMap ++ jsonObjectPropertiesToMap(optionalJsonPropertiesList(), destination)

      if (destinationMap.getOrElse("secretstore", "").equals("aws_secrets_manager")) {
        destinationMap = extractCredentialsFromSecretManager(destinationMap)
      }

      var dft = sparkSession.emptyDataFrame

      if (migration.has("sql")) {
        dft = sparkSession.sql(overrideSql)
      } else {
        dft = df
      }

      if (preciseCounts == true) {
        dft.persist()
        processedTableCount_precise = dft.count()
      }

      val dataframeFromTo = new DataFrameFromTo(appConfig, pipeline)

      if (destinationMap("platform") == "cassandra") {
        destinationMap = destinationMap ++ deriveClusterIPFromConsul(destinationMap)
        dataframeFromTo.dataFrameToCassandra(destinationMap("awsenv"), destinationMap("cluster"), destinationMap("keyspace"), destinationMap("table"), destinationMap("login"), destinationMap("password"), destinationMap("local_dc"), destination.optJSONObject("sparkoptions"), dft, reportRowHtml, destinationMap("vaultenv"), destinationMap.getOrElse("secretstore", "vault"))
      }
      else if (destinationMap("platform") == "Email") {
        dataframeFromTo.dataFrameToEmail(destinationMap.getOrElse("to", reportEmailAddress), destinationMap.getOrElse("subject", "Datapull Result"), dft, destinationMap.getOrElse("limit", "100"), destinationMap.getOrElse("truncate", "100"))
      }
      else if (destinationMap("platform") == "mssql" || destinationMap("platform") == "mysql" || destinationMap("platform") == "postgres" || destinationMap("platform") == "oracle" || destinationMap("platform") == "teradata") {
        dataframeFromTo.dataFrameToRdbms(
          platform = destinationMap("platform"),
          awsEnv = destinationMap("awsenv"),
          server = destinationMap("server"),
          database = destinationMap("database"),
          table = destinationMap("table"),
          login = destinationMap("login"),
          password = destinationMap("password"),
          df = dft,
          vaultEnv = destinationMap("vaultenv"),
          secretStore = destinationMap.getOrElse("secretstore", "vault"),
          sslEnabled = destinationMap.getOrElse("sslenabled", "false").toBoolean,
          port = destinationMap.getOrElse("port", null),
          addlJdbcOptions = destination.optJSONObject("jdbcoptions"),
          savemode = destinationMap.getOrElse("savemode", "Append"),
          isWindowsAuthenticated = destinationMap.getOrElse("iswindowsauthenticated", "false").toBoolean,
          domainName = destinationMap.getOrElse("domain", null),
          typeForTeradata = destinationMap.get("typeforteradata")
        )
      } else if (destinationMap("platform") == "s3") {
        setAWSCredentials(sparkSession, destinationMap)
        sparkSession.sparkContext.hadoopConfiguration.set("mapreduce.input.fileinputformat.‌​input.dir.recursive", "true")
        dataframeFromTo.dataFrameToFile(
          filePath = destinationMap("s3path"),
          fileFormat = destinationMap("fileformat"),
          groupByFields = destinationMap("groupbyfields"),
          s3SaveMode = destinationMap.getOrElse("savemode", "Append"),
          df = dft,
          isS3 = true,
          secretstore = destinationMap.getOrElse("secretstore", "vault"),
          sparkSession = sparkSession,
          coalescefilecount = destinationMap.getOrElse("coalescefilecount", null).asInstanceOf[Integer],
          isSFTP = false,
          login = destinationMap.getOrElse("login", "false"),
          host = destinationMap.getOrElse("host", "false"),
          password = destinationMap.getOrElse("password", "false"),
          pemFilePath = destinationMap.getOrElse("pemfilepath", ""),
          awsEnv = destinationMap.getOrElse("awsEnv", "false"),
          vaultEnv = destinationMap.getOrElse("vaultEnv", "false"),
          rowFromJsonString = destinationMap.getOrElse("rowfromjsonstring", "false").toBoolean,
          filePrefix = (if (destinationMap.contains("fileprefix")) destinationMap.get("fileprefix") else (if (destinationMap.contains("s3_service_endpoint")) Some("s3a://") else None))
        )
      } else if (destinationMap("platform") == "filesystem") {
        sparkSession.sparkContext.hadoopConfiguration.set("mapreduce.input.fileinputformat.‌​input.dir.recursive", "true")
        dataframeFromTo.dataFrameToFile(destinationMap("path"), destinationMap("fileformat"), destinationMap("groupbyfields"), destinationMap.getOrElse("savemode", "Append"), dft, false, destinationMap.getOrElse("secretstore", "vault"), sparkSession, destinationMap.getOrElse("coalescefilecount", null).asInstanceOf[Integer], false, destinationMap.getOrElse("login", "false"), destinationMap.getOrElse("host", "false"), destinationMap.getOrElse("password", "false"), destinationMap.getOrElse("pemfilepath", ""), destinationMap.getOrElse("awsEnv", "false"), destinationMap.getOrElse("vaultEnv", "false"), destinationMap.getOrElse("rowfromjsonstring", "false").toBoolean, destinationMap.get("fileprefix"))
      }
      else if (destinationMap("platform") == "sftp") {

        dataframeFromTo.dataFrameToFile(destinationMap("path"), destinationMap("fileformat"), destinationMap("groupbyfields"), destinationMap.getOrElse("savemode", "Append"), dft, false, destinationMap.getOrElse("secretstore", "vault"), sparkSession, destinationMap.getOrElse("coalescefilecount", null).asInstanceOf[Integer], true, destinationMap.getOrElse("login", "false"), destinationMap.getOrElse("host", "false"), destinationMap.getOrElse("password", "false"), destinationMap.getOrElse("pemfilepath", ""), destinationMap.getOrElse("awsEnv", "false"), destinationMap.getOrElse("vaultEnv", "false"), destinationMap.getOrElse("rowfromjsonstring", "false").toBoolean, destinationMap.get("fileprefix"))
      }
      else if (destinationMap("platform") == "console") {
        val numRows: Int = destinationMap.getOrElse("numrows", "20").toInt
        val truncateData: Boolean = destinationMap.getOrElse("truncate", "true").toBoolean
        if (df.isStreaming) {
          val query = dft.writeStream
            .outputMode(destinationMap.getOrElse("outputmode", "append"))
            .format("console")
            .option("numRows", numRows.toString)
            .option("truncate", truncateData.toString)
            .start()
        }
        else {
          df.show(numRows = numRows, truncate = truncateData)
        }
      }
      else if (destinationMap("platform") == "hive") {
        dataframeFromTo.dataFrameToHive(destinationMap("table"), destinationMap.getOrElse("savemode", "Append"), dft)
      } else if (destinationMap("platform") == "mongodb") {
        dataframeFromTo.dataFrameToMongodb(destinationMap("awsenv"), destinationMap("cluster"), destinationMap("database"), destinationMap("authenticationdatabase"), destinationMap("collection"), destinationMap("login"), destinationMap("password"), destinationMap.getOrElse("replicaset", null), destinationMap.getOrElse("replacedocuments", "true"), destinationMap.getOrElse("orderedrecords", "false"), dft, sparkSession, destinationMap.getOrElse("documentfromjsonfield", "false"), destinationMap.getOrElse("jsonfield", "jsonfield"), destinationMap("vaultenv"), destinationMap.getOrElse("secretstore", "vault"), destination.optJSONObject("sparkoptions"), destinationMap.get("maxBatchSize").getOrElse(null), destinationMap.getOrElse("authenticationenabled", true).asInstanceOf[Boolean], destinationMap.getOrElse("sslenabled", "false"))
      } else if (destinationMap("platform") == "kafka") {
        dataframeFromTo.dataFrameToKafka(
          spark = sparkSession,
          df = dft,
          valueField = destinationMap.getOrElse("valuefield", "value"),
          topic = destinationMap("topic"),
          kafkaBroker = destinationMap("bootstrapservers"),
          schemaRegistryUrl = destinationMap("schemaregistries"),
          valueSchemaVersion = (if (destinationMap.get("valueschemaversion") == None) None else Some(destinationMap.getOrElse("valueschemaversion", "1").toInt)),
          valueSubjectNamingStrategy = destinationMap.getOrElse("valuesubjectnamingstrategy", "TopicNameStrategy"),
          valueSubjectRecordName = destinationMap.get("valuesubjectrecordname"),
          valueSubjectRecordNamespace = destinationMap.get("valuesubjectrecordnamespace"),
          keyField = destinationMap.get("keyfield"),
          keySchemaVersion = (if (destinationMap.get("keyschemaversion") == None) None else Some(destinationMap.getOrElse("keyschemaversion", "1").toInt)),
          keySubjectNamingStrategy = destinationMap.getOrElse("keysubjectnamingstrategy", "TopicNameStrategy"),
          keySubjectRecordName = destinationMap.get("keysubjectrecordname"),
          keySubjectRecordNamespace = destinationMap.get("keysubjectrecordnamespace"),
          headerField = destinationMap.get("headerfield"),
          keyStorePath = destinationMap.get("keystorepath"),
          trustStorePath = destinationMap.get("truststorepath"),
          keyStorePassword = destinationMap.get("keystorepassword"),
          trustStorePassword = destinationMap.get("truststorepassword"),
          keyPassword = destinationMap.get("keypassword"),
          isStream = df.isStreaming,
          keyFormat = destinationMap.getOrElse("keyformat", "avro"),
          valueFormat = destinationMap.getOrElse("valueformat", "avro"),
          addlSparkOptions = (if (destination.optJSONObject("sparkoptions") == null) None else Some(destination.optJSONObject("sparkoptions")))
        )
      } else if (destinationMap("platform") == "elastic") {
        dataframeFromTo.dataFrameToElastic(destinationMap("awsenv"), destinationMap("clustername"), destinationMap("port"), destinationMap("index"), destinationMap("type"), destinationMap("version"), destinationMap("login"), destinationMap("password"), destinationMap("local_dc"), destination.optJSONObject("sparkoptions"), dft, reportRowHtml, destinationMap("vaultenv"), destinationMap.getOrElse("savemode", "index"), destinationMap.getOrElse("mappingid", null), destinationMap.getOrElse("flag", "false"), destinationMap.getOrElse("secretstore", "vault"), sparkSession)
      }
      else if (destinationMap("platform") == "cloudwatch") {
        dataframeFromTo.dataFrameToCloudWatch(destinationMap("groupname"), destinationMap("streamname"), destinationMap.getOrElse("region", ""), destinationMap.getOrElse("awsaccesskeyid", ""), destinationMap.getOrElse("awssecretaccesskey", ""), destinationMap.getOrElse("timestamp_column", ""), destinationMap.getOrElse("timestamp_format", ""), dft, sparkSession)
      } else if (destinationMap("platform") == "snowflake") {
        dataframeFromTo.dataFrameToSnowflake(
          destinationMap("url"),
          destinationMap("user"),
          destinationMap("password"),
          destinationMap("database"),
          destinationMap("schema"),
          destinationMap("table"),
          destinationMap.getOrElse("savemode", "Append"),
          destination.optJSONObject("options"),
          destinationMap("awsenv"),
          destinationMap("vaultenv"),
          destinationMap.getOrElse("secretstore", "vault"),
          dft,
          sparkSession
        )
      }

      if (dft.isStreaming) {
        sparkSession.streams.awaitAnyTermination()
      }

      def prePostFortmpS3(datastore: JSONObject): Unit = {
        var customized_object = new JSONObject()
        var post_migrate_command = new JSONObject()
        var tmp_file_location: String = null

        if (
          (datastore.get("platform") == "mongodb" && datastore.has("overrideconnector"))
            || (datastore.get("platform") == "kafka" && (
            datastore.has("s3region") || datastore.has("tmpfilelocation")
            ))
        ) {
          var region: String = ""
          if (datastore.has("tmpfilelocation")) {
            tmp_file_location = datastore.get("tmpfilelocation").toString
          } else {
            tmp_file_location = datastore.get("s3location").toString
          }

          if (datastore.has("s3region")) {
            region = datastore.getString("s3region")
          }
          else {
            region = new DefaultAwsRegionProviderChain().getRegion
          }
          customized_object.put("platform", "s3".toString).put("s3region", region)
          post_migrate_command.put("operation", "delete").put("s3path", tmp_file_location)
          customized_object.put("post_migrate_command", post_migrate_command)
          jsonSourceDestinationRunPrePostMigrationCommand(customized_object, false, reportRowHtml, sparkSession, pipeline)
        }
      }
      //run the post-migration command for the sources, if any.
      for (a <- 0 to sources.length() - 1) {
        val selectedSource = sources.getJSONObject(a)
        //run the post-migration command for the source, if any
        jsonSourceDestinationRunPrePostMigrationCommand(selectedSource, false, reportRowHtml, sparkSession, pipeline)
        prePostFortmpS3(selectedSource)
      }

      jsonSourceDestinationRunPrePostMigrationCommand(destination, false, reportRowHtml, sparkSession, pipeline)
      prePostFortmpS3(destination)

      if (reportEmailAddress != "") {
        sparkSession.sparkContext.setJobDescription("Count destination " + migrationId)
        if (verifymigration) {
          var dfd = jsonSourceDestinationToDataFrame(sparkSession, destination, migrationLogId, jobId, s3TempFolderDeletionError, pipeline)
          verified = verifymigrations(df, dfd, sparkSession)

        }
      }
      if (preciseCounts == true) {
        dft.unpersist()
        processedTableCount = processedTableCount_precise
      }

      hasExceptions = false
    } catch {
      case ex: FileNotFoundException => {
        val sw = new StringWriter
        ex.printStackTrace()
        ex.printStackTrace(new PrintWriter(sw))
        migrationException = sw.toString()
        hasExceptions = true
      }
      case ex: Exception => {
        val sw = new StringWriter
        ex.printStackTrace()
        ex.printStackTrace(new PrintWriter(sw))
        migrationException = sw.toString()
        hasExceptions = true
      }
    } finally {

      val migrationEndTime = Instant.now().toString

      if (hasExceptions)
      //in case of exceptions, the job will be logged in as failed.
        dataPullLogs.jobLog(migrationLogId, migrationStartTime.toString, Instant.now().toString, System.currentTimeMillis() - startTime_in_milli, migrationJSONString, processedTableCount, size_of_the_records, "Failed", jobId, sparkSession)
      else
      //in case of no exceptions here the job will be logged as completed.
        dataPullLogs.jobLog(migrationLogId, migrationStartTime.toString, Instant.now().toString, System.currentTimeMillis() - startTime_in_milli, migrationJSONString, processedTableCount, size_of_the_records, "Completed", jobId, sparkSession)

      reportRowHtml.append("<tr><td>")
      for (a <- 0 to sources.length() - 1) {
        var selectedSource = sources.getJSONObject(a)
        var platform = selectedSource.getString("platform")
        reportRowHtml.append(printableSourceTargetInfo(selectedSource))
      }
      reportRowHtml.append("</td><td>")
      reportRowHtml.append(printableSourceTargetInfo(destination) + "</td><td>" + migrationStartTime.toString() + "</td><td>" + migrationEndTime + "</td>")
      reportRowHtml.append((if (migrationException != null) "</td><td colspan=\"" + (if (reportCounts) "2" else "1") + "\"\">" + migrationException + "</td></tr>" else
        (if (reportCounts) "<td>" + processedTableCount.toString() + "</td>" else "")
          + "<td>" + verified + "</td></tr>")
      )
    }
    Map("reportRowHtml" -> reportRowHtml.toString(), "migrationError" -> migrationException, "deletionError" -> s3TempFolderDeletionError.toString())
  }

  def jsonArrayToList(jsonArray: JSONArray): List[String] = {
    if (jsonArray != null) {
      val arrayLen = jsonArray.length()
      val retVal = new Array[String](arrayLen)
      for (i <- 0 to arrayLen - 1) {
        retVal(i) = jsonArray.getString(i)
      }
      retVal.toList
    } else {
      List.empty[String]
    }
  }

  def jsonSourceDestinationToDataFrame(sparkSession: org.apache.spark.sql.SparkSession, platformObject: JSONObject, migrationId: String, jobId: String, s3TempFolderDeletionError: StringBuilder, pipeline: String): org.apache.spark.sql.DataFrame = {
    var propertiesMap = jsonObjectPropertiesToMap(platformObject)
    //add optional keysp explicitely else the map will complain they don't exist later down
    propertiesMap = propertiesMap ++ jsonObjectPropertiesToMap(optionalJsonPropertiesList(), platformObject)

    if (propertiesMap.getOrElse("secretstore", "").equals("aws_secrets_manager")) {
      propertiesMap = extractCredentialsFromSecretManager(propertiesMap)
    }


    var platform = propertiesMap("platform")
    var dataframeFromTo = new DataFrameFromTo(appConfig, pipeline)


    if (platform == "mssql" || platform == "mysql" || platform == "oracle" || platform == "postgres" || platform == "teradata") {
      val sqlQuery = mssqlPlatformQueryFromS3File(sparkSession, platformObject)
      dataframeFromTo.rdbmsToDataFrame(
        platform = platform,
        awsEnv = propertiesMap("awsenv"),
        server = propertiesMap("server"),
        database = propertiesMap("database"),
        table = if (sqlQuery == "") {
          propertiesMap("table")
        } else {
          "(" + sqlQuery + ") S"
        },
        login = propertiesMap("login"),
        password = propertiesMap("password"),
        sparkSession = sparkSession,
        primarykey = propertiesMap("primarykey"),
        lowerbound = propertiesMap("lowerBound"),
        upperbound = propertiesMap("upperBound"),
        numofpartitions = propertiesMap("numPartitions"),
        vaultEnv = propertiesMap("vaultenv"),
        secretStore = propertiesMap.getOrElse("secretstore", "vault"),
        sslEnabled = propertiesMap.getOrElse("sslenabled", "false").toBoolean,
        port = propertiesMap.getOrElse("port", null),
        addlJdbcOptions = platformObject.optJSONObject("jdbcoptions"),
        isWindowsAuthenticated = propertiesMap.getOrElse("isWindowsAuthenticated", "false").toBoolean,
        domainName = propertiesMap.getOrElse("domain", null),
        typeForTeradata = propertiesMap.get("typeforteradata")
      )
    } else if (platform == "cassandra") {
      //DO NOT bring in the pre-migrate command in here, else it might run when getting the final counts
      propertiesMap = propertiesMap ++ deriveClusterIPFromConsul(jsonObjectPropertiesToMap(List("cluster", "cluster_key", "consul_dc"), platformObject))
      dataframeFromTo.cassandraToDataFrame(propertiesMap("awsenv"), propertiesMap("cluster"), propertiesMap("keyspace"), propertiesMap("table"), propertiesMap("login"), propertiesMap("password"), propertiesMap("local_dc"), platformObject.optJSONObject("sparkoptions"), sparkSession, propertiesMap("vaultenv"), propertiesMap.getOrElse("secretstore", "vault"))
    } else if (platform == "s3") {
      sparkSession.sparkContext.hadoopConfiguration.set("mapreduce.input.fileinputformat.‌​input.dir.recursive", "true")
      setAWSCredentials(sparkSession, propertiesMap)
      dataframeFromTo.fileToDataFrame(
        filePath = propertiesMap("s3path"),
        fileFormat = propertiesMap("fileformat"),
        delimiter = propertiesMap.getOrElse("delimiter", ","),
        charset = propertiesMap.getOrElse("charset", "utf-8"),
        mergeSchema = propertiesMap.getOrElse("mergeschema", "false").toBoolean,
        sparkSession = sparkSession,
        isS3 = true,
        secretstore = propertiesMap.getOrElse("secretstore", "vault"),
        login = propertiesMap.getOrElse("login", "false"),
        host = propertiesMap.getOrElse("host", "false"),
        password = propertiesMap.getOrElse("password", "false"),
        pemFilePath = propertiesMap.getOrElse("pemfilepath", ""),
        awsEnv = propertiesMap.getOrElse("awsEnv", "false"),
        vaultEnv = propertiesMap.getOrElse("vaultEnv", "false"),
        filePrefix = (if (propertiesMap.contains("fileprefix")) propertiesMap.get("fileprefix") else (if (propertiesMap.contains("s3_service_endpoint")) Some("s3a://") else None)),
        schema = (if (propertiesMap.contains("schema")) Some(StructType.fromDDL( propertiesMap.getOrElse("schema", ""))) else  None)
      )
    } else if (platform == "filesystem") {
      sparkSession.sparkContext.hadoopConfiguration.set("mapreduce.input.fileinputformat.‌​input.dir.recursive", "true")
      dataframeFromTo.fileToDataFrame(
        filePath = propertiesMap("path"),
        fileFormat = propertiesMap("fileformat"),
        delimiter = propertiesMap.getOrElse("delimiter", ","),
        charset = propertiesMap.getOrElse("charset", "utf-8"),
        mergeSchema = propertiesMap.getOrElse("mergeschema", "false").toBoolean,
        sparkSession = sparkSession,
        secretstore = propertiesMap.getOrElse("secretstore", "vault"),
        login = propertiesMap.getOrElse("login", "false"),
        host = propertiesMap.getOrElse("host", "false"),
        password = propertiesMap.getOrElse("password", "false"),
        pemFilePath = propertiesMap.getOrElse("pemfilepath", ""),
        awsEnv = propertiesMap.getOrElse("awsEnv", "false"),
        vaultEnv = propertiesMap.getOrElse("vaultEnv", "false"),
        isStream = propertiesMap.getOrElse("isstream", "false").toBoolean,
        addlSparkOptions = (if (platformObject.optJSONObject("sparkoptions") == null) None else Some(platformObject.optJSONObject("sparkoptions"))),
        filePrefix = propertiesMap.get("fileprefix"),
        schema = (if (propertiesMap.contains("schema")) Some(StructType.fromDDL( propertiesMap.getOrElse("schema", ""))) else  None)
      )
    }

    else if (platform == "sftp") {
      dataframeFromTo.fileToDataFrame(
        filePath = propertiesMap("path"),
        fileFormat = propertiesMap("fileformat"),
        delimiter = propertiesMap.getOrElse("delimiter", ","),
        charset = propertiesMap.getOrElse("charset", "utf-8"),
        mergeSchema = propertiesMap.getOrElse("mergeschema", "false").toBoolean,
        sparkSession = sparkSession,
        secretstore = propertiesMap.getOrElse("secretstore", "vault"),
        isSFTP = true,
        login = propertiesMap.getOrElse("login", "false"),
        host = propertiesMap.getOrElse("host", "false"),
        password = propertiesMap.getOrElse("password", "false"),
        pemFilePath = propertiesMap.getOrElse("pemfilepath", ""),
        awsEnv = propertiesMap.getOrElse("awsEnv", "false"),
        vaultEnv = propertiesMap.getOrElse("vaultEnv", "false"),
        filePrefix = propertiesMap.get("fileprefix"),
        schema = (if (propertiesMap.contains("schema")) Some(StructType.fromDDL( propertiesMap.getOrElse("schema", ""))) else  None)
      )
    }
    else if (platform == "hive") {
      dataframeFromTo.hiveToDataFrame(propertiesMap("url"), sparkSession, propertiesMap("dbtable"), propertiesMap.getOrElse("username", ""), propertiesMap.getOrElse("fetchsize", ""))
    } else if (platform == "mongodb") {
      dataframeFromTo.mongodbToDataFrame(propertiesMap("awsenv"), propertiesMap("cluster"), propertiesMap.getOrElse("overrideconnector", "false"), propertiesMap("database"), propertiesMap("authenticationdatabase"), propertiesMap("collection"), propertiesMap("login"), propertiesMap("password"), sparkSession, propertiesMap("vaultenv"), platformObject.optJSONObject("sparkoptions"), propertiesMap.getOrElse("secretstore", "vault"), propertiesMap.getOrElse("authenticationenabled", "true"), propertiesMap.getOrElse("tmpfilelocation", null), propertiesMap.getOrElse("samplesize", null), propertiesMap.getOrElse("sslenabled", "false"))
    }
    else if (platform == "kafka") {
      dataframeFromTo.kafkaToDataFrame(
        spark = sparkSession,
        kafkaBroker = propertiesMap("bootstrapservers"),
        schemaRegistryUrl = propertiesMap("schemaregistries"),
        topic = propertiesMap("topic"),
        valueSchemaVersion = (if (propertiesMap.get("valueschemaversion") == None) None else Some(propertiesMap.getOrElse("valueschemaversion", "1").toInt)),
        valueSubjectNamingStrategy = propertiesMap.getOrElse("valuesubjectnamingstrategy", "TopicNameStrategy"),
        valueSubjectRecordName = propertiesMap.get("valuesubjectrecordname"),
        valueSubjectRecordNamespace = propertiesMap.get("valuesubjectrecordnamespace"),
        keySchemaVersion = (if (propertiesMap.get("keyschemaversion") == None) None else Some(propertiesMap.getOrElse("keyschemaversion", "1").toInt)),
        keySubjectNamingStrategy = propertiesMap.getOrElse("keysubjectnamingstrategy", "TopicNameStrategy"),
        keySubjectRecordName = propertiesMap.get("keysubjectrecordname"),
        keySubjectRecordNamespace = propertiesMap.get("keysubjectrecordnamespace"),
        keyStorePath = propertiesMap.get("keystorepath"),
        trustStorePath = propertiesMap.get("truststorepath"),
        keyStorePassword = propertiesMap.get("keystorepassword"),
        trustStorePassword = propertiesMap.get("truststorepassword"),
        keyPassword = propertiesMap.get("keypassword"),
        keyFormat = propertiesMap.getOrElse("keyformat", "avro"),
        valueFormat = propertiesMap.getOrElse("valueformat", "avro"),
        addlSparkOptions = (if (platformObject.optJSONObject("sparkoptions") == null) None else Some(platformObject.optJSONObject("sparkoptions"))),
        isStream = propertiesMap.getOrElse("isstream", "false").toBoolean)
    }
    else if (platform == "elastic") {
      dataframeFromTo.ElasticToDataframe(propertiesMap("awsenv"), propertiesMap("clustername"), propertiesMap("port"), propertiesMap("index"), propertiesMap("type"), propertiesMap("version"), propertiesMap("login"), propertiesMap("password"), propertiesMap("vaultenv"), propertiesMap.getOrElse("secretstore", "vault"), sparkSession)
    }
    else if (platform == "influxdb") {
      dataframeFromTo.InfluxdbToDataframe(propertiesMap("awsenv"), propertiesMap("clustername"), propertiesMap("database"), propertiesMap("measurementname"), propertiesMap("login"), propertiesMap("password"), propertiesMap("vaultenv"), propertiesMap.getOrElse("secretstore", "vault"), sparkSession)
    }
    else if (platform == "snowflake") {
      dataframeFromTo.snowflakeToDataFrame(
        propertiesMap("url"),
        propertiesMap("user"),
        propertiesMap("password"),
        propertiesMap("database"),
        propertiesMap("schema"),
        propertiesMap("table"),
        platformObject.optJSONObject("options"),
        propertiesMap("awsenv"),
        propertiesMap("vaultenv"),
        propertiesMap.getOrElse("secretstore", "vault"),
        sparkSession)
    }
    else {
      sparkSession.emptyDataFrame
    }
  }

  /*this function takes in a mssql platform source json and returns the SQL query stored in a provided SQL file in s3 if it exists; else returns an empty string*/
  def mssqlPlatformQueryFromS3File(sparkSession: org.apache.spark.sql.SparkSession, platformObject: JSONObject): String = {
    var sqlQuery = ""
    if (platformObject.has("querys3sqlfile")) {
      val jsonMap = jsonObjectPropertiesToMap(List("s3path", "awsaccesskeyid", "awssecretaccesskey"), platformObject.getJSONObject("querys3sqlfile"))
      setAWSCredentials(sparkSession, jsonMap)
      sqlQuery = sparkSession.sparkContext.wholeTextFiles("s3n://" + jsonMap("s3path")).first()._2
    }
    sqlQuery
  }

  def extractCredentialsFromSecretManager(destinationMap: Map[String, String]): Map[String, String] = {
    val secretManager = new SecretsManager(appConfig)
    val secretName = destinationMap.getOrElse("secret_name", "");
    val mm = collection.mutable.Map[String, String]() ++= destinationMap
    val key_name: String = destinationMap.getOrElse("secret_key_name", null)
    if (secretName != null && !secretName.isEmpty) {
      val secretCredentials = secretManager.getSecret(secretName, key_name)
      mm.put("password", secretCredentials)
    }
    mm.toMap
  }

  def jsonSourceDestinationRunPrePostMigrationCommand(platformObject: JSONObject, runPreMigrationCommand: Boolean, reportbodyHtml: StringBuilder, sparkSession: SparkSession, pipeline: String): Unit = {
    var propertiesMap = jsonObjectPropertiesToMap(platformObject)
    //add optional keys explicitly else the map will complain they don't exist later down
    propertiesMap = propertiesMap ++ jsonObjectPropertiesToMap(optionalJsonPropertiesList(), platformObject)
    if (propertiesMap.getOrElse("secretstore", "").equals("aws_secrets_manager")) {
      propertiesMap = extractCredentialsFromSecretManager(propertiesMap)
    }
    var platform = propertiesMap("platform")
    var pre_migrate_commands = new JSONArray()
    if (platformObject.has("pre_migrate_commands")) {

      pre_migrate_commands = platformObject.optJSONArray("pre_migrate_commands")
    }
    if (platformObject.has("pre_migrate_command")) {

      if (platform == "s3") {
        pre_migrate_commands.put(platformObject.get("pre_migrate_command"))
      } else {
        val tmpJsonObject = new JSONObject()

        tmpJsonObject.put("query", platformObject.get("pre_migrate_command"))

        pre_migrate_commands.put(tmpJsonObject)
      }
    }
    var post_migrate_commands = new JSONArray()

    if (platformObject.has("post_migrate_commands")) {
      post_migrate_commands = platformObject.optJSONArray("post_migrate_commands")
    }
    if (platformObject.has("post_migrate_command")) {

      if (platform == "s3") {
        post_migrate_commands.put(platformObject.get("post_migrate_command"))

      } else {

        val tmpJsonObject = new JSONObject()

        tmpJsonObject.put("query", platformObject.get("post_migrate_command"))
        post_migrate_commands.put(tmpJsonObject)
      }

    }


    if ((runPreMigrationCommand && (pre_migrate_commands.length() > 0)) || ((!runPreMigrationCommand) && (post_migrate_commands.length() > 0))) {
      var s3Client: AmazonS3 = null
      if (platform == "s3") {
        s3Client = s3ClientBuilder(platformObject, sparkSession)
      }
      var lengthOfArray: Int = 0

      var command = new JSONArray()

      command = if (runPreMigrationCommand) pre_migrate_commands else post_migrate_commands

      lengthOfArray = command.length()

      for (i <- 0 to lengthOfArray - 1) {

        val dataframeFromTo = new DataFrameFromTo(appConfig, pipeline)
        if (platform == "mssql" || platform == "mysql" || platform == "oracle" || platform == "postgres" || platform == "teradata") {
          dataframeFromTo.rdbmsRunCommand(
            platform = platform,
            awsEnv = propertiesMap("awsenv"),
            server = propertiesMap("server"),
            port = propertiesMap.getOrElse("port", null),
            sslEnabled = propertiesMap.getOrElse("sslenabled", "false").toBoolean,
            database = propertiesMap("database"),
            sql_command = command.getJSONObject(i).getString("query"),
            login = propertiesMap("login"),
            password = propertiesMap("password"),
            vaultEnv = propertiesMap("vaultenv"),
            secretStore = propertiesMap.getOrElse("secretstore", "vault"),
            isWindowsAuthenticated = propertiesMap.getOrElse("iswindowsauthenticated", "false").toBoolean,
            domainName = propertiesMap.getOrElse("domain", null),
            typeForTeradata = propertiesMap.get("typeforteradata")
          )
        }
        else if (platform == "cassandra") {
          propertiesMap = propertiesMap ++ deriveClusterIPFromConsul(jsonObjectPropertiesToMap(List("cluster", "cluster_key", "consul_dc"), platformObject))
          dataframeFromTo.cassandraRunCommand(propertiesMap("awsenv"), propertiesMap("cluster"), propertiesMap("keyspace"), propertiesMap("login"), propertiesMap("password"), propertiesMap("local_dc"), platformObject.optJSONObject("sparkoptions"), command.getJSONObject(i).getString("query"), reportbodyHtml, propertiesMap("vaultenv"), propertiesMap.getOrElse("secretstore", "vault"))
        } else if (platform == "mongodb") {
          propertiesMap = propertiesMap ++ deriveClusterIPFromConsul(jsonObjectPropertiesToMap(List("clustername", "cluster_key", "consul_dc"), platformObject))
          dataframeFromTo.mongoRunCommand(propertiesMap("awsenv"), propertiesMap("cluster"), propertiesMap("database"), propertiesMap("authenticationdatabase"), propertiesMap("collection"), propertiesMap("login"), propertiesMap("password"), propertiesMap("vaultenv"), platformObject.optJSONObject("sparkoptions"), command.getJSONObject(i).getString("query"), propertiesMap.getOrElse("secretstore", "vault"), propertiesMap.getOrElse("authenticationenabled", true).asInstanceOf[Boolean], propertiesMap.getOrElse("sslenabled", "false"))
        } else if (platform == "elastic") {
          propertiesMap = propertiesMap ++ deriveClusterIPFromConsul(jsonObjectPropertiesToMap(List("clustername", "cluster_key", "consul_dc"), platformObject))
          dataframeFromTo.elasticRunCommand(propertiesMap("awsenv"), propertiesMap("clustername"), propertiesMap("port"), propertiesMap("index"), propertiesMap("login"), propertiesMap("password"), propertiesMap("local_dc"), platformObject.optJSONObject("sparkoptions"), command.getJSONObject(i).getString("shell"), reportbodyHtml, propertiesMap("vaultenv"), propertiesMap.getOrElse("secretstore", "vault"))
        } else if (platform == "s3") {
          val s3QueryJson = command.optJSONObject(i)
          val operation = s3QueryJson.getString("operation")
          if (operation == "copy") {
            dataframeFromTo.s3CopyDirectory(s3QueryJson.getString("sources3path"), s3QueryJson.getString("destinations3path"), s3QueryJson.getBoolean("overwrite"), s3QueryJson.getBoolean("removesource"), s3QueryJson.getBoolean("partitioned"), s3Client, sparkSession)
          }
          if (operation == "delete") {
            dataframeFromTo.s3RemoveDirectory(s3QueryJson.getString("s3path"), s3Client, sparkSession)
          }
        }
      }
    }

  }

  def optionalJsonPropertiesList(): List[String] = {
    List("awsenv", "local_dc", "pre_migrate_command", "post_migrate_command", "groupbyfields", "primarykey", "lowerBound", "upperBound", "numPartitions", "s3path", "awsaccesskeyid", "awssecretaccesskey", "password", "vaultenv", "secret_store")
  }

  def s3ClientBuilder(platformObject: JSONObject, sparkSession: SparkSession): AmazonS3 = {
    var accessKey = ""
    var secretKey = ""
    var s3Region = ""
    if (platformObject.has("awsaccesskeyid") && platformObject.has("awssecretaccesskey")) {
      setAWSCredentials(sparkSession, jsonObjectPropertiesToMap(platformObject))
      accessKey = platformObject.getString("awsaccesskeyid")
      secretKey = platformObject.getString("awssecretaccesskey")
    }

    if (platformObject.has("s3region")) {
      s3Region = platformObject.getString("s3region")
    }
    else {
      s3Region = new DefaultAwsRegionProviderChain().getRegion
    }

    val credentialsProvider = if (accessKey != null && !accessKey.isEmpty && secretKey != null && !secretKey.isEmpty) new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey))
    else new DefaultAWSCredentialsProviderChain


    val s3Client = AmazonS3ClientBuilder.standard.withRegion(Regions.fromName(s3Region)).withCredentials(credentialsProvider).build

    s3Client
  }

  def deriveClusterIPFromConsul(clusterMap: Map[String, String]): Map[String, String] = {
    var hostMap: Map[String, String] = Map()
    if (!clusterMap.contains("cluster") || "".equals(clusterMap("cluster"))) {
      //derive cluster using "cluster_key", "consul_dc"
      val ipAddress: Option[String] = readIpAddressFromConsul(clusterMap("cluster_key"), clusterMap("consul_dc"))
      if (ipAddress.isDefined) {
        hostMap += "cluster" -> ipAddress.get
      }
    }
    return hostMap
  }

  def readIpAddressFromConsul(clusterKey: String, consulDc: String): Option[String] = {
    if (!"".equals(clusterKey) && !"".equals(consulDc)) {
      val consulUrl = "" + appConfig.consul_url + s"$clusterKey?passing&dc=$consulDc"
      val helper = new Helper(appConfig)
      val responseString = helper.get(consulUrl)
      val jSONArray = new JSONArray(responseString)
      if (jSONArray.length() > 0 && jSONArray.getJSONObject(0).has("Node") && jSONArray.getJSONObject(0).getJSONObject("Node").has("Address")) {
        return Option(jSONArray.getJSONObject(0).getJSONObject("Node").getString("Address"))
      }
    }
    return Option.empty
  }


  def printableSourceTargetInfo(platformObject: JSONObject): String = {
    var htmlString = StringBuilder.newBuilder
    val platform = platformObject.getString("platform")
    var propertiesMap = Map("platform" -> platform)
    if (platform == "mssql") {
      propertiesMap = propertiesMap ++ jsonObjectPropertiesToMap(List("server", "database", "table"), platformObject)
    } else if (platform == "cassandra") {
      propertiesMap = propertiesMap ++ jsonObjectPropertiesToMap(List("cluster", "keyspace", "table", "local_dc"), platformObject)
      propertiesMap = propertiesMap ++ deriveClusterIPFromConsul(jsonObjectPropertiesToMap(List("cluster", "cluster_key", "consul_dc"), platformObject))
    } else if (platform == "s3") {
      propertiesMap = propertiesMap ++ jsonObjectPropertiesToMap(List("s3path", "fileformat", "awsaccesskeyid", "awssecretaccesskey"), platformObject)
    } else if (platform == "filesystem") {
      propertiesMap = propertiesMap ++ jsonObjectPropertiesToMap(List("path", "fileformat"), platformObject)
    } else if (platform == "hive") {
      propertiesMap = propertiesMap ++ jsonObjectPropertiesToMap(List("cluster", "clustertype", "database", "table"), platformObject)
    } else if (platform == "mongodb") {
      propertiesMap = propertiesMap ++ jsonObjectPropertiesToMap(List("cluster", "database", "authenticationdatabase", "collection", "login", "password"), platformObject)
    } else if (platform == "kafka") {
      propertiesMap = propertiesMap ++ jsonObjectPropertiesToMap(List("bootstrapServers", "schemaRegistries", "topic", "keyField", "keyFormat"), platformObject)
    }
    htmlString.append("<dl>")
    propertiesMap.filter((t) => t._2 != "" && t._1 != "login" && t._1 != "password" && t._1 != "awsaccesskeyid" && t._1 != "awssecretaccesskey").foreach(i => htmlString.append("<dt>" + i._1 + "</dt><dd>" + i._2 + "</dd>"))
    htmlString.append("</dl>")
    htmlString.toString()
  }

  def verifymigrations(source_df: org.apache.spark.sql.DataFrame, destination_df: org.apache.spark.sql.DataFrame, sparkSession: org.apache.spark.sql.SparkSession): String = {
    source_df.createTempView("source_df")
    destination_df.createTempView("destination_df")
    val a = source_df.count()
    val b = destination_df.count()

    val union = sparkSession.sql("Select * FROM source_df s UNION Select * FROM destination_df s ")
    val c = union.count()


    if ((a == b) && (b == c)) {
      return "succeeded"
    }
    else {
      return "failed"
    }
  }

}
