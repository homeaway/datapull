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
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import config.AppConfig
import core.DataPull.{jsonObjectPropertiesToMap, setAWSCredentials}
import helper._
import logging._
import org.apache.spark.scheduler.{SparkListener, SparkListenerStageCompleted}
import org.apache.spark.sql.SparkSession
import org.apache.spark.util.SizeEstimator
import org.codehaus.jettison.json.{JSONArray, JSONObject}
import security.SecretsManager

import scala.collection.immutable.List
import scala.collection.mutable.ListBuffer

//this singleton object handles everything to do with a single core.Migration i.e. getting a single source to a destination
class Migration extends  SparkListener {
  //do a migration dammit!

  var appConfig : AppConfig = null;

  def migrate(migrationJSONString: String, reportEmailAddress: String, migrationId: String, verifymigration: Boolean, reportCounts: Boolean, no_of_retries: Int, custom_retries: Boolean, migrationLogId: String, isLocal: Boolean, preciseCounts: Boolean, appConfig: AppConfig, pipeline : String): Map[String, String] = {
    var s3TempFolderDeletionError = StringBuilder.newBuilder
    val reportRowHtml = StringBuilder.newBuilder
    val migration = new JSONObject(migrationJSONString)
    var dataPullLogs = new DataPullLog(appConfig, pipeline)
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
        .config("spark.task.maxFailures",no_of_retries)
        .config("fs.s3a.multiobjectdelete.enable",false)
        .getOrCreate()
    }

    var sourceCount = 0L
    var sql: String = null
    var sources = new JSONArray()
    var destination = new JSONObject()
    var relationships = new JSONArray()

    var tableslist: List[String] = null
    var tables = new ListBuffer[String]()
    var platformslist: List[String] = null
    var platforms = new ListBuffer[String]()
    var aliaseslist: List[String] = null
    var aliases = new ListBuffer[String]()
    var processedTableCount: Long = 0
    var mappings = new JSONArray()
    val migrationStartTime = Instant.now()
    var verified = "NA"
    var migrationException: String = null

    var size_of_the_records : Long = 0
    var processedTableCount_precise: Long = 0

    var endTime_in_milli: Long = 0
    var jobId = UUID.randomUUID().toString
    var hasExceptions: Boolean = false
    var startTime_in_milli = System.currentTimeMillis()
    var size : Long =0

    dataPullLogs.jobLog(migrationLogId,migrationStartTime.toString,null,0,migrationJSONString,0,0,"Started",jobId,sparkSession)

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

      var overrideSql  = ""
      if(migration.has("sql")){

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

        if(platform == "mssql"){
          size_of_the_records += SizeEstimator.estimate(df)
        }

        var store:String = "table" + migrationId + "_" + a.toString();
        tables += store;

        if(migration.has("sql")){

          val tmpAlias = selectedSource.getString("alias")

          overrideSql =overrideSql.replaceAll("(?<!\\.)\\b"+tmpAlias+"\\b",s"$store")
        }
        aliases += (if (selectedSource.has("alias")) selectedSource.getString("alias") else "")

        df.createOrReplaceTempView(store)

        platforms += platform
      }
      tableslist = tables.toList
      platformslist = platforms.toList
      aliaseslist = aliases.toList

      //run the pre-migration command for the destination, if any
      jsonSourceDestinationRunPrePostMigrationCommand(destination, true, reportRowHtml,sparkSession, pipeline)

      //do the transforms
      var destinationMap = jsonObjectPropertiesToMap(destination)

      //add optional keysp explicitely else the map will complain they don't exist later down
      destinationMap = destinationMap ++ jsonObjectPropertiesToMap(optionalJsonPropertiesList(), destination)

      if(destinationMap.getOrElse("secretstore", "").equals("secret_manager")){
        destinationMap = extractCredentialsFromSecretManager(destinationMap)
      }

      var dft = sparkSession.emptyDataFrame

      if (migration.has("sql")) {
        dft = sparkSession.sql(overrideSql)
      }else {
        dft = df
      }

      if (preciseCounts == true) {
        dft.persist()
        processedTableCount_precise = dft.count()
      }

      var dataframeFromTo = new DataFrameFromTo(appConfig, pipeline)

      if (destinationMap("platform") == "cassandra") {
        destinationMap = destinationMap ++ deriveClusterIPFromConsul(destinationMap)
        dataframeFromTo.dataFrameToCassandra(destinationMap("awsenv"), destinationMap("cluster"), destinationMap("keyspace"), destinationMap("table"), destinationMap("login"), destinationMap("password"), destinationMap("local_dc"), destination.optJSONObject("sparkoptions"), dft, reportRowHtml, destinationMap("vaultenv"),destinationMap.getOrElse("secretstore","vault"))
      } else if (destinationMap("platform") == "mssql" || destinationMap("platform") == "mysql" || destinationMap("platform") == "postgres" || destinationMap("platform") == "oracle" || destinationMap("platform") == "teradata") {
        dataframeFromTo.dataFrameToRdbms(destinationMap("platform"), destinationMap("awsenv"), destinationMap("server"), destinationMap("database"), destinationMap("table"), destinationMap("login"), destinationMap("password"), dft, destinationMap("vaultenv"), destinationMap.getOrElse("secretstore", "vault"), destinationMap.getOrElse("sslenabled", "false"), destinationMap.getOrElse("vault", null), destination.optJSONObject("jdbcoptions"))
      } else if (destinationMap("platform") == "s3") {
        setAWSCredentials(sparkSession, destinationMap)
        sparkSession.sparkContext.hadoopConfiguration.set("mapreduce.input.fileinputformat.‌​input.dir.recursive", "true")
        dataframeFromTo.dataFrameToFile(destinationMap("s3path"), destinationMap("fileformat"), destinationMap("groupbyfields"),destinationMap.getOrElse("savemode","Append"), dft, true,destinationMap.getOrElse("secretstore","vault"),sparkSession,false, destinationMap.getOrElse("login", "false"), destinationMap.getOrElse("host", "false"), destinationMap.getOrElse("password", "false"), destinationMap.getOrElse("awsEnv", "false"), destinationMap.getOrElse("vaultEnv", "false"))
      } else if (destinationMap("platform") == "filesystem") {
        sparkSession.sparkContext.hadoopConfiguration.set("mapreduce.input.fileinputformat.‌​input.dir.recursive", "true")
        dataframeFromTo.dataFrameToFile(destinationMap("path"), destinationMap("fileformat"), destinationMap("groupbyfields"),destinationMap.getOrElse("savemode","Append"), dft, false,destinationMap.getOrElse("secretstore","vault"),sparkSession,false, destinationMap.getOrElse("login", "false"), destinationMap.getOrElse("host", "false"), destinationMap.getOrElse("password", "false"), destinationMap.getOrElse("awsEnv", "false"), destinationMap.getOrElse("vaultEnv", "false"))
      }
      else if (destinationMap("platform") == "sftp") {

        dataframeFromTo.dataFrameToFile(destinationMap("path"), destinationMap("fileformat"), destinationMap("groupbyfields"), destinationMap.getOrElse("savemode", "Append"), dft, false,destinationMap.getOrElse("secretstore","vault") ,sparkSession,true, destinationMap.getOrElse("login", "false"), destinationMap.getOrElse("host", "false"), destinationMap.getOrElse("password", "false"), destinationMap.getOrElse("awsEnv", "false"), destinationMap.getOrElse("vaultEnv", "false"))
      }
      else if (destinationMap("platform") == "hive") {
        dataframeFromTo.dataFrameToHive(destinationMap("cluster"), destinationMap("clustertype"), destinationMap("database"), destinationMap("table"), dft)
      } else if (destinationMap("platform") == "mongodb") {
        dataframeFromTo.dataFrameToMongodb(destinationMap("awsenv"), destinationMap("cluster"), destinationMap("database"), destinationMap("authenticationdatabase"), destinationMap("collection"), destinationMap("login"), destinationMap("password"), destinationMap.getOrElse("replicaset", null), destinationMap.getOrElse("replacedocuments", "true"), destinationMap.getOrElse("orderedrecords", "false"), dft, sparkSession, destinationMap.getOrElse("documentfromjsonfield", "false"), destinationMap.getOrElse("jsonfield", "jsonfield"), destinationMap("vaultenv"), destinationMap.getOrElse("secretstore", "vault"), destination.optJSONObject("sparkoptions"), destinationMap.get("maxBatchSize").getOrElse(null), destinationMap.getOrElse("authenticationenabled", true).asInstanceOf[Boolean])
      } else if (destinationMap("platform") == "kafka") {
        dataframeFromTo.dataFrameToKafka(destinationMap("bootstrapServers"), destinationMap("schemaRegistries"), destinationMap("topic"), destinationMap("keyField"), destinationMap("Serializer"), dft)
      } else if (destinationMap("platform") == "elastic") {
        dataframeFromTo.dataFrameToElastic(destinationMap("awsenv"), destinationMap("clustername"), destinationMap("port"), destinationMap("index"), destinationMap("type"), destinationMap("version"), destinationMap("login"), destinationMap("password"), destinationMap("local_dc"), destination.optJSONObject("sparkoptions"), dft, reportRowHtml, destinationMap("vaultenv"),destinationMap.getOrElse("savemode","index"),destinationMap.getOrElse("mappingid",null),destinationMap.getOrElse("flag","false"),destinationMap.getOrElse("secretstore","vault"), sparkSession)
      }
      else if (destinationMap("platform") == "cloudwatch") {
        dataframeFromTo.dataFrameToCloudWatch(destinationMap("groupname"), destinationMap("streamname"), destinationMap.getOrElse("region", ""), destinationMap.getOrElse("awsaccesskeyid", ""), destinationMap.getOrElse("awssecretaccesskey", ""), destinationMap.getOrElse("timestamp_column",""), destinationMap.getOrElse("timestamp_format",""), dft, sparkSession)
      }

      else if (destinationMap("platform") == "neo4j") {
        var node1 = destination.optJSONObject("node1")
        var node2 = destination.optJSONObject("node2")
        var relation = destination.optJSONObject("relation")
        var node1_label: String = null
        var node1_keys : List[String] = null
        var node1_nonKeys: List[String] = null
        var node1_createOrMerge: String = "MERGE"
        var node1_createNodeKeyConstraint: Boolean = true
        var node2_label: String = null
        var node2_keys : List[String] = null
        var node2_nonKeys: List[String] = null
        var node2_createOrMerge: String = "MERGE"
        var node2_createNodeKeyConstraint: Boolean = true
        var relation_label: String = null
        var relation_createOrMerge: String = "MERGE"
        if (node1 != null) {
          node1_label = node1.optString("label")
          node1_keys = jsonArrayToList(node1.optJSONArray("properties_key"))
          node1_nonKeys=jsonArrayToList(node1.optJSONArray("properties_nonkey"))
          if (node1.has("createormerge")) {
            node1_createOrMerge = node1.getString("createormerge")
          }
          if (node1.has("createnodekeyconstraint")) {
            node1_createNodeKeyConstraint = node1.getBoolean("createnodekeyconstraint")
          }
          if (node1.has("property_key")){
            node1_keys = node1.getString("property_key") :: node1_keys
          }
          if (node1.has("property_nonkey")){
            node1_nonKeys = node1.getString("property_nonkey") :: node1_nonKeys
          }
        }
        if (node2 != null) {
          node2_label = node2.optString("label")
          node2_keys = jsonArrayToList(node2.optJSONArray("properties_key"))
          node2_nonKeys=jsonArrayToList(node2.optJSONArray("properties_nonkey"))
          if (node2.has("createormerge")) {
            node2_createOrMerge = node2.getString("createormerge")
          }
          if (node2.has("createnodekeyconstraint")) {
            node2_createNodeKeyConstraint = node2.getBoolean("createnodekeyconstraint")
          }
          if (node2.has("property_key")){
            node2_keys = node2.getString("property_key") :: node2_keys
          }
          if (node2.has("property_nonkey")){
            node2_nonKeys = node2.getString("property_nonkey") :: node2_nonKeys
          }
        }
        if (relation != null) {
          relation_label = relation.optString("label")
          if (relation.has("createormerge")) {
            relation_createOrMerge = relation.getString("createormerge")
          }
        }

        dataframeFromTo.dataFrameToNeo4j(dft, destinationMap("cluster"),  destinationMap("login"), destinationMap.getOrElse("password", ""),destinationMap("awsenv"), destinationMap("vaultenv"), node1_label, node1_keys, node1_nonKeys,node2_label,node2_keys ,node2_nonKeys ,relation_label, destinationMap.getOrElse("batchsize", "10000").toInt, node1_createOrMerge, node1_createNodeKeyConstraint, node2_createOrMerge, node2_createNodeKeyConstraint, relation_createOrMerge ,destinationMap.getOrElse("secretstore","vault"), sparkSession)
      }

      //run the post-migration command for the sources, if any.
      for (a <- 0 to sources.length() - 1) {
        val selectedSource = sources.getJSONObject(a)
        //run the post-migration command for the source, if any
        jsonSourceDestinationRunPrePostMigrationCommand(selectedSource, false, reportRowHtml,sparkSession, pipeline)
      }

      jsonSourceDestinationRunPrePostMigrationCommand(destination, false, reportRowHtml,sparkSession, pipeline)

      if (reportEmailAddress != "") {
        sparkSession.sparkContext.setJobDescription("Count destination " + migrationId)
        if (verifymigration)
        {
          var dfd = jsonSourceDestinationToDataFrame(sparkSession, destination,migrationLogId,jobId,s3TempFolderDeletionError, pipeline)
          verified =verifymigrations(df, dfd,sparkSession)

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

      if(hasExceptions)
      //in case of exceptions, the job will be logged in as failed.
        dataPullLogs.jobLog(migrationLogId,migrationStartTime.toString,Instant.now().toString,System.currentTimeMillis()-startTime_in_milli,migrationJSONString,processedTableCount,size_of_the_records,"Failed",jobId,sparkSession)
      else
      //in case of no exceptions here the job will be logged as completed.
        dataPullLogs.jobLog(migrationLogId,migrationStartTime.toString,Instant.now().toString,System.currentTimeMillis()-startTime_in_milli,migrationJSONString,processedTableCount,size_of_the_records,"Completed",jobId,sparkSession)

      reportRowHtml.append("<tr><td>")
      for (a <- 0 to sources.length() - 1) {
        var selectedSource = sources.getJSONObject(a)
        var platform = selectedSource.getString("platform")
        reportRowHtml.append(printableSourceTargetInfo(selectedSource))
      }
      reportRowHtml.append("</td><td>")
      reportRowHtml.append(printableSourceTargetInfo(destination) + "</td><td>" + migrationStartTime.toString() + "</td><td>" + migrationEndTime + "</td>")
      reportRowHtml.append((if (migrationException != null)  "</td><td colspan=\"" + (if (reportCounts) "2" else "1") + "\"\">" + migrationException + "</td></tr>" else
        (if (reportCounts) "<td>" + processedTableCount.toString() + "</td>" else "")
          + "<td>" + verified + "</td></tr>")
      )
    }
    Map("reportRowHtml" -> reportRowHtml.toString(), "migrationError" -> migrationException, "deletionError" -> s3TempFolderDeletionError.toString())
  }

  def jsonArrayToList (jsonArray: JSONArray): List[String] = {
    if (jsonArray != null ) {
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

  def extractCredentialsFromSecretManager(destinationMap : Map[String, String]) : Map[String, String] = {
    val secretManager = new SecretsManager(appConfig)
    val secretName = destinationMap.getOrElse("secret_name", "");
    val mm = collection.mutable.Map[String, String]() ++= destinationMap
    if (secretName != null && !secretName.isEmpty) {
      val secretCredentials = secretManager.getSecret(secretName)
      if (secretCredentials.contains(secretName)) {
        mm.put("password", secretCredentials.get(secretName).get)
      }
      else {
        mm.put("login", secretCredentials.get("username").get)
        mm.put("server", secretCredentials.get("host").get)
        mm.put("database", secretCredentials.get("dbname").get)
        mm.put("password", secretCredentials.get("password").get)
      }
    }
    return mm.toMap
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

  def jsonSourceDestinationToDataFrame(sparkSession: org.apache.spark.sql.SparkSession, platformObject: JSONObject,migrationId: String, jobId: String, s3TempFolderDeletionError:StringBuilder, pipeline : String): org.apache.spark.sql.DataFrame = {
    var propertiesMap = jsonObjectPropertiesToMap(platformObject)
    //add optional keysp explicitely else the map will complain they don't exist later down
    propertiesMap = propertiesMap ++ jsonObjectPropertiesToMap(optionalJsonPropertiesList(), platformObject)

    if(propertiesMap.getOrElse("secretstore", "").equals("secret_manager")){
        propertiesMap = extractCredentialsFromSecretManager(propertiesMap)
    }


    var platform = propertiesMap("platform")
    var dataframeFromTo = new DataFrameFromTo(appConfig, pipeline)

    if (platform == "mssql" || platform == "mysql" || platform == "oracle" || platform == "postgres" || platform == "teradata") {
      val sqlQuery = mssqlPlatformQueryFromS3File(sparkSession, platformObject)
      dataframeFromTo.rdbmsToDataFrame(platform,propertiesMap("awsenv"), propertiesMap("server"), propertiesMap("database"), if (sqlQuery == "") {
        propertiesMap("table")
      } else {
        "(" + sqlQuery + ") S"
      }, propertiesMap("login"), propertiesMap("password"), sparkSession, propertiesMap("primarykey"), propertiesMap("lowerBound"), propertiesMap("upperBound"), propertiesMap("numPartitions"), propertiesMap("vaultenv"), propertiesMap.getOrElse("secretstore", "vault"), propertiesMap.getOrElse("sslenabled", "false"), propertiesMap.getOrElse("vault", null), platformObject.optJSONObject("jdbcoptions"))
    } else if (platform == "cassandra") {
      //DO NOT bring in the pre-migrate command in here, else it might run when getting the final counts
      propertiesMap = propertiesMap ++ deriveClusterIPFromConsul(jsonObjectPropertiesToMap(List("cluster", "cluster_key", "consul_dc"), platformObject))
      dataframeFromTo.cassandraToDataFrame(propertiesMap("awsenv"), propertiesMap("cluster"), propertiesMap("keyspace"), propertiesMap("table"), propertiesMap("login"), propertiesMap("password"), propertiesMap("local_dc"), platformObject.optJSONObject("sparkoptions"), sparkSession, propertiesMap("vaultenv"),propertiesMap.getOrElse("secretstore","vault"))
    } else if (platform == "s3") {
      sparkSession.sparkContext.hadoopConfiguration.set("mapreduce.input.fileinputformat.‌​input.dir.recursive", "true")
      setAWSCredentials(sparkSession, propertiesMap)
      dataframeFromTo.fileToDataFrame(propertiesMap("s3path"), propertiesMap("fileformat"),propertiesMap.getOrElse("delimiter",","),propertiesMap.getOrElse("charset","utf-8"), propertiesMap.getOrElse("mergeschema", "false"), sparkSession, true,propertiesMap.getOrElse("secretstore","vault"),false, propertiesMap.getOrElse("login", "false"), propertiesMap.getOrElse("host", "false"), propertiesMap.getOrElse("password", "false"), propertiesMap.getOrElse("awsEnv", "false"), propertiesMap.getOrElse("vaultEnv", "false"))
    } else if (platform == "filesystem") {
      sparkSession.sparkContext.hadoopConfiguration.set("mapreduce.input.fileinputformat.‌​input.dir.recursive", "true")
      dataframeFromTo.fileToDataFrame(propertiesMap("path"), propertiesMap("fileformat"),propertiesMap.getOrElse("delimiter",","),propertiesMap.getOrElse("charset","utf-8"), propertiesMap.getOrElse("mergeschema", "false"), sparkSession, false,propertiesMap.getOrElse("secretstore","vault"),false, propertiesMap.getOrElse("login", "false"), propertiesMap.getOrElse("host", "false"), propertiesMap.getOrElse("password", "false"), propertiesMap.getOrElse("awsEnv", "false"), propertiesMap.getOrElse("vaultEnv", "false"))
    }

    else if (platform == "sftp") {
      dataframeFromTo.fileToDataFrame(propertiesMap("path"), propertiesMap("fileformat"), propertiesMap.getOrElse("delimiter", ","), propertiesMap.getOrElse("charset", "utf-8"), propertiesMap.getOrElse("mergeschema", "false"), sparkSession, false,propertiesMap.getOrElse("secretstore","vault"),true, propertiesMap.getOrElse("login", "false"), propertiesMap.getOrElse("host", "false"), propertiesMap.getOrElse("password", "false"), propertiesMap.getOrElse("awsEnv", "false"), propertiesMap.getOrElse("vaultEnv", "false"))
    }
    else if (platform == "hive") {
      dataframeFromTo.hiveToDataFrame(propertiesMap("cluster"), propertiesMap("clustertype"), propertiesMap("database"), propertiesMap("table"))
    } else if (platform == "mongodb") {
      dataframeFromTo.mongodbToDataFrame(propertiesMap("awsenv"), propertiesMap("cluster"), propertiesMap.getOrElse("overrideconnector", "false"), propertiesMap("database"), propertiesMap("authenticationdatabase"), propertiesMap("collection"), propertiesMap("login"), propertiesMap("password"), sparkSession, propertiesMap("vaultenv"), platformObject.optJSONObject("sparkoptions"), propertiesMap.getOrElse("secretstore", "vault"), propertiesMap.getOrElse("authenticationenabled", true).asInstanceOf[Boolean])
    }
    else if (platform == "kafka") {
      dataframeFromTo.kafkaToDataFrame(propertiesMap("bootstrapServers"), propertiesMap("topic"), propertiesMap("offset"), propertiesMap("schemaRegistries"), propertiesMap.getOrElse("keydeserializer", "org.apache.kafka.common.serialization.StringDeserializer"), propertiesMap("deSerializer"), propertiesMap.getOrElse("s3location", appConfig.s3bucket.toString + "/datapull-opensource/logs/"), propertiesMap.getOrElse("groupid", null), propertiesMap.getOrElse("requiredonlyvalue", "false"), propertiesMap.getOrElse("payloadcolumnname", "body"), migrationId, jobId, sparkSession, s3TempFolderDeletionError)
    }
    else if (platform == "elastic") {
      dataframeFromTo.ElasticToDataframe(propertiesMap("awsenv"), propertiesMap("clustername"), propertiesMap("port"), propertiesMap("index"), propertiesMap("type"), propertiesMap("version") ,propertiesMap("login"), propertiesMap("password"), propertiesMap("vaultenv"),propertiesMap.getOrElse("secretstore","vault"),  sparkSession)
    }
    else if (platform == "influxdb") {
      dataframeFromTo.InfluxdbToDataframe(propertiesMap("awsenv"), propertiesMap("clustername"), propertiesMap("database"), propertiesMap("measurementname"), propertiesMap("login"), propertiesMap("password"), propertiesMap("vaultenv"), propertiesMap.getOrElse("secretstore", "vault"), sparkSession)
    }

    else {
      sparkSession.emptyDataFrame
    }
  }

  def jsonSourceDestinationRunPrePostMigrationCommand(platformObject: JSONObject, runPreMigrationCommand: Boolean, reportbodyHtml: StringBuilder,sparkSession: SparkSession, pipeline : String): Unit = {
    var propertiesMap = jsonObjectPropertiesToMap(platformObject)

    if(platformObject.has("pre_migrate_command")){}

    //add optional keysp explicitely else the map will complain they don't exist later down
    propertiesMap = propertiesMap ++ jsonObjectPropertiesToMap(optionalJsonPropertiesList(), platformObject)
    if(propertiesMap.getOrElse("secretstore", "").equals("secret_manager")){
      propertiesMap = extractCredentialsFromSecretManager(propertiesMap)
    }
    var platform = propertiesMap("platform")
    var s3Client: AmazonS3 = null

    if (platform == "s3") {
      s3Client = AmazonS3ClientBuilder.defaultClient()
      s3Client = s3ClientBuilder(platformObject, sparkSession)
    }

    var pre_migrate_commands = new JSONArray()
    if (platformObject.has("pre_migrate_commands")) {

      pre_migrate_commands = platformObject.optJSONArray("pre_migrate_commands")
    }
    if(platformObject.has("pre_migrate_command")){

      if(platform =="s3"){
        pre_migrate_commands.put(platformObject.get("pre_migrate_command"))
      }else{
        val tmpJsonObject = new JSONObject()

        tmpJsonObject.put("query",platformObject.get("pre_migrate_command"))

        pre_migrate_commands.put(tmpJsonObject)
      }
    }
    var post_migrate_commands = new JSONArray()

    if (platformObject.has("post_migrate_commands")) {
      post_migrate_commands = platformObject.optJSONArray("post_migrate_commands")
    }
    if(platformObject.has("post_migrate_command")){

      if(platform == "s3"){
        post_migrate_commands.put(platformObject.get("post_migrate_command"))

      }else {

        val tmpJsonObject = new JSONObject()

        tmpJsonObject.put("query",platformObject.get("post_migrate_command"))
        post_migrate_commands.put(tmpJsonObject)
      }

    }


    if ((runPreMigrationCommand && (pre_migrate_commands.length()>0)) || ((!runPreMigrationCommand) && (post_migrate_commands.length()>0))) {

      var lengthOfArray: Int = 0

      var command = new JSONArray()

      command = if (runPreMigrationCommand) pre_migrate_commands else post_migrate_commands

      lengthOfArray = command.length()

      for (i <- 0 to lengthOfArray - 1) {

        var dataframeFromTo = new DataFrameFromTo(appConfig, pipeline)
        if (platform == "mssql" || platform == "mysql" || platform == "oracle" || platform == "postgres" || platform == "teradata") {
          dataframeFromTo.rdbmsRunCommand(platform,propertiesMap("awsenv"), propertiesMap("server"),propertiesMap.getOrElse("port", null), propertiesMap.getOrElse("sslenabled", null), propertiesMap("database"), command.getJSONObject(i).getString("query"), propertiesMap("login"), propertiesMap("password"), propertiesMap("vaultenv"),propertiesMap.getOrElse("secretstore","vault"))
        }
        else if (platform == "cassandra") {
          propertiesMap = propertiesMap ++ deriveClusterIPFromConsul(jsonObjectPropertiesToMap(List("cluster", "cluster_key", "consul_dc"), platformObject))
          dataframeFromTo.cassandraRunCommand(propertiesMap("awsenv"), propertiesMap("cluster"), propertiesMap("keyspace"), propertiesMap("login"), propertiesMap("password"), propertiesMap("local_dc"), platformObject.optJSONObject("sparkoptions"), command.getJSONObject(i).getString("query"), reportbodyHtml, propertiesMap("vaultenv"),propertiesMap.getOrElse("secretstore","vault"))
        } else if (platform == "mongodb") {
          propertiesMap = propertiesMap ++ deriveClusterIPFromConsul(jsonObjectPropertiesToMap(List("clustername", "cluster_key", "consul_dc"), platformObject))
          dataframeFromTo.mongoRunCommand(propertiesMap("awsenv"), propertiesMap("cluster"), propertiesMap("database"), propertiesMap("authenticationdatabase"), propertiesMap("collection"), propertiesMap("login"), propertiesMap("password"), propertiesMap("vaultenv"), platformObject.optJSONObject("sparkoptions"), command.getJSONObject(i).getString("query"), propertiesMap.getOrElse("secretstore", "vault"), propertiesMap.getOrElse("authenticationenabled", true).asInstanceOf[Boolean])
        }else if (platform == "elastic") {
          propertiesMap = propertiesMap ++ deriveClusterIPFromConsul(jsonObjectPropertiesToMap(List("clustername", "cluster_key", "consul_dc"), platformObject))
          dataframeFromTo.elasticRunCommand(propertiesMap("awsenv"), propertiesMap("clustername"), propertiesMap("port"), propertiesMap("index"), propertiesMap("login"), propertiesMap("password"), propertiesMap("local_dc"), platformObject.optJSONObject("sparkoptions"), command.getJSONObject(i).getString("shell"), reportbodyHtml, propertiesMap("vaultenv"), propertiesMap.getOrElse("secretstore", "vault"))
        } else if (platform == "s3") {
          val s3QueryJson = command.optJSONObject(i)
          val operation = s3QueryJson.getString("operation")
          if(operation == "copy"){
            dataframeFromTo.s3CopyDirectory(s3QueryJson.getString("sources3path"), s3QueryJson.getString("destinations3path"), s3QueryJson.getBoolean("overwrite"), s3QueryJson.getBoolean("removesource"), s3QueryJson.getBoolean("partitioned"), s3Client, sparkSession)
          }
          if(operation == "delete"){
            dataframeFromTo.s3RemoveDirectory(s3QueryJson.getString("s3path"), s3Client, sparkSession)
          }
        }
      }
    }

  }

  def optionalJsonPropertiesList(): List[String] = {
    List("awsenv", "local_dc", "pre_migrate_command", "post_migrate_command", "groupbyfields","primarykey", "lowerBound", "upperBound", "numPartitions", "s3path", "awsaccesskeyid", "awssecretaccesskey", "password", "vaultenv", "secret_store")
  }

  def s3ClientBuilder(platformObject: JSONObject, sparkSession: SparkSession): AmazonS3 = {

    var s3Client = AmazonS3ClientBuilder.defaultClient()
    var accessKey = ""
    var secretKey = ""
    var s3Region = "us-east-1"

    if (platformObject.has("awsaccesskeyid") && platformObject.has("awssecretaccesskey")) {
      setAWSCredentials(sparkSession, jsonObjectPropertiesToMap(platformObject))

      accessKey = platformObject.getString("awsaccesskeyid")
      secretKey = platformObject.getString("awssecretaccesskey")

    }

    if (platformObject.has("s3region")) {
      s3Region = platformObject.getString("s3region")
    }

    val credentialsProvider = if (accessKey != null && !accessKey.isEmpty && secretKey != null && !secretKey.isEmpty) new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey))
    else new DefaultAWSCredentialsProviderChain

    s3Client = AmazonS3ClientBuilder.standard.withRegion(Regions.fromName(s3Region)).withCredentials(credentialsProvider).build

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
      val consulUrl = ""+appConfig.consul_url+s"$clusterKey?passing&dc=$consulDc"
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
      propertiesMap = propertiesMap ++ jsonObjectPropertiesToMap(List("bootstrapServers", "schemaRegistries", "topic", "keyField", "Serializer"), platformObject)
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
