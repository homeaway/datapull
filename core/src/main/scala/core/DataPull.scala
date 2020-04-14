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

import java.io.File
import java.nio.ByteBuffer
import java.time._
import java.util.{Scanner, UUID}
import javax.net.ssl._

import com.datastax.driver.core.utils.UUIDs
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.mongodb.spark.sql.fieldTypes.Binary
import config.AppConfig
import helper._
import logging._
import org.apache.commons.codec.binary.{Base64, Hex}
import org.apache.spark.sql.SparkSession
import org.bson.codecs.{EncoderContext, UuidCodec}
import org.bson.{BsonDocument, BsonDocumentWriter, UuidRepresentation}
import org.codehaus.jettison.json.{JSONArray, JSONObject}
import security._

import scala.collection.mutable.ListBuffer
import scala.util.control.Breaks.breakable

// Main class
object DataPull {

  def main(args: Array[String]): Unit = {

    val yamlMapper = new ObjectMapper(new YAMLFactory());
    val inputStream = this.getClass().getClassLoader().getResourceAsStream("application.yml");
    val applicationConf = yamlMapper.readTree(inputStream)
    val config = new AppConfig(applicationConf)
    val alert = new Alert(config)
    val helper = new Helper(config)

    val sslContext = SSLContext.getInstance("SSL")
    sslContext.init(null, Array(helper.TrustAll), new java.security.SecureRandom())
    HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory)
    HttpsURLConnection.setDefaultHostnameVerifier(helper.VerifiesAllHostNames)

    /*-------------------JSON INPUT-------------------------------------------------------------------------------------------------*/
    var jsonString = ""
    var isLocal:Boolean = true

    if (args.length == 0) {

      val dp: String = DataPull.getFile("Samples/" + config.inputjson)
      jsonString = dp
    }
    else if (args.length == 2 && args(1) == "local") {
      val dp: String = DataPull.getFile(args(0), false)
      jsonString = dp
    } else {
      jsonString = args(0)
      isLocal = false
    }



    var reportEmailAddress = ""
    var authenticatedUser = ""
    var precisecounts = false

    var minexecutiontime = ""
    var maxexecutiontime = ""
    var failureEmailAddress = ""

    var failureThreshold = 10000
    var verifymigration = false
    var applicationId = ""
    val reportbodyHtml = StringBuilder.newBuilder //ideally this would be constructed out of json data but we're taking shortcuts here
    var reportCounts = true
    var no_of_retries = config.no_of_retries
    var custom_retries = false
    var migrationErrors = ListBuffer[String]()
    var stepSubmissionTime: String = null
    var jobId: String = null
    var masterNode: String = null
    var sparkSession: SparkSession = null
    var portfolio: String = null
    var product: String = null
    var start_time_in_milli: Long = 0
    var ec2Role: String = "local"
    var pipelineName: String = null
    var awsenv: String = null
    var isScheduled: Boolean = false
    var pagerdutyEmailAddress = config.pagerDutyEmail


    if (jsonString == "") {
      throw new helper.CustomListOfExceptions("No json input available to DataPull")
    }

    if (isLocal) {
      sparkSession = SparkSession.builder.master("local[*]")
        .config("" + config.scheduler, "" + config.mode)
        .appName("DataPull")
        .config("" + config.network, "" + config.timeout)
        .config("" + config.broadcasttimeout, "" + config.btimeout)
        .config("" + config.executor, config.interval)
        .getOrCreate()
    } else {
      sparkSession = SparkSession.builder //.master("local[*]")
        .config("" + config.scheduler, "" + config.mode)
        .appName("DataPull")
        .config("" + config.network, "" + config.timeout)
        .config("" + config.broadcasttimeout, "" + config.btimeout)
        .config("" + config.executor, config.interval)
        .config("" + config.failures, no_of_retries)
        .getOrCreate()

      val helper = new Helper(config)
      ec2Role = helper.GetEC2Role()
    }
    applicationId = sparkSession.sparkContext.applicationId

    sparkSession.udf.register("validateUUID", validateUUID _)
    sparkSession.udf.register("uuidToBinary", uuidToBinary _)
    sparkSession.udf.register("binaryToUUID", binaryToUUID _)
    sparkSession.udf.register("uuid", uuid _)
    sparkSession.udf.register("binaryToJUUID", binaryToJUUID _)

    stepSubmissionTime = Instant.now().toString

    start_time_in_milli = System.currentTimeMillis()
    jobId = UUID.randomUUID().toString
    masterNode = sparkSession.conf.get("spark.driver.host")

    var json = new JSONObject(jsonString)

    val listOfS3Path = new ListBuffer[String]()

    assignVariablesFromJson(json)

    breakable {
      while (json.has("jsoninputfile")) {
        val jsonMap = jsonObjectPropertiesToMap(List("s3path", "awsaccesskeyid", "awssecretaccesskey"), json.getJSONObject("jsoninputfile"))
        if (listOfS3Path.contains(jsonMap("s3path"))) {
          throw new Exception("New json is pointing to same json.")
        }
        listOfS3Path += jsonMap("s3path")
        setAWSCredentials(sparkSession, jsonMap)
        val rddjson = sparkSession.sparkContext.wholeTextFiles("s3a://" + jsonMap("s3path"))
        json = new JSONObject(rddjson.first()._2)
      }
    }

    assignVariablesFromJson(json)

    def assignVariablesFromJson(json: JSONObject): Unit = {

      if (json.has("useremailaddress")) {
        reportEmailAddress = json.getString("useremailaddress")
      }

      if (json.has("failureemailaddress")) {
        failureEmailAddress = json.getString("failureemailaddress")
      }

      if (json.has("authenticated_user")) {
        authenticatedUser = json.getString("authenticated_user")
      }

      if (json.has("precisecounts")) {
        precisecounts = json.getBoolean("precisecounts")

      }
      if (json.has("minexecutiontime")) {
        minexecutiontime = json.getString("minexecutiontime")
      }
      if (json.has("maxexecutiontime")) {
        maxexecutiontime = json.getString("maxexecutiontime")
      }
      if (json.has("migration_failure_threshold")) {
        failureThreshold = json.getString("migration_failure_threshold").toInt
      }
      if (json.has("no_of_retries")) {
        no_of_retries = config.no_of_retries
        custom_retries = true
      }
      if (json.has("reportcounts")) {
        reportCounts = json.getBoolean("reportcounts")
      }
      if (json.has("verifymigration")) {
        verifymigration = (json.getString("verifymigration") == "true")
      }
    }
    if (json.has("cluster")) {

      val cluster = json.getJSONObject("cluster")
      var dataPullLog = new DataPullLog(config, pipelineName)
      dataPullLog.dataPullLogging(jobId, masterNode, ec2Role, portfolio, product, jsonString, stepSubmissionTime, null, 0, "Started", null, sparkSession)

      portfolio = cluster.getString("portfolio")

      product = cluster.getString("product")

      pipelineName = cluster.getString("pipelinename")
      awsenv = cluster.getString("awsenv")
      isScheduled = cluster.has("cronexpression")
    }
    if (minexecutiontime != "" && maxexecutiontime != "") {
      val elapsedtime = System.currentTimeMillis() - start_time_in_milli
      alert.AlertLog(jobId, masterNode, ec2Role, portfolio, product, elapsedtime / 1000, minexecutiontime.toLong, maxexecutiontime.toLong, sparkSession, "Started", reportEmailAddress, pipelineName, awsenv, config.dataToolsEmailAddress)
    }
    if (json.has("useremailaddress")) {
      reportEmailAddress = json.getString("useremailaddress")
    }

    if (awsenv == "prod") {

      if (isScheduled) {
        if (failureEmailAddress != "") {
          failureEmailAddress = failureEmailAddress + ";" + pagerdutyEmailAddress
        }
        else
          failureEmailAddress = pagerdutyEmailAddress
      }
    }

    var migrations = new JSONArray()
    if (json.has("migrations")) {
      migrations = json.optJSONArray("migrations")
    }
    else {
      migrations.put(json)
    }
    var parallelmigrations = false;
    if (json.has("parallelmigrations")) {
      parallelmigrations = json.getBoolean("parallelmigrations")
    }
    var migrationStartTime = Instant.now()
    var controllerinstance = new Controller(config, pipelineName)
    controllerinstance.performmigration(migrations, parallelmigrations, reportEmailAddress, verifymigration, reportCounts, config.no_of_retries.toInt, custom_retries, jobId, sparkSession, masterNode, ec2Role, portfolio, product, jsonString, stepSubmissionTime, minexecutiontime, maxexecutiontime, start_time_in_milli, applicationId, pipelineName, awsenv, precisecounts, failureThreshold, failureEmailAddress, authenticatedUser)
    if (migrationErrors.nonEmpty) {
      throw new helper.CustomListOfExceptions(migrationErrors.mkString("\n"))
    }
  }
  
  def getFile(fileName: String, relativeToClass:Boolean = true): String = {

    val result = new StringBuilder("")
    var file:File = null
    if (relativeToClass) {
      //Get file from resources folder
      val classLoader = getClass().getClassLoader();
      file = new File(classLoader.getResource(fileName).getFile());
    }
    else {
      file = new File(fileName);
    }
    val scanner = new Scanner(file)

    while (scanner.hasNextLine()) {
      val line = scanner.nextLine();
      result.append(line).append("\n");
    }

    scanner.close();

    return result.toString();

  }

  def setAWSCredentials(sparkSession: org.apache.spark.sql.SparkSession, sourceDestinationMap: Map[String, String]): Unit = {
    //sparkSession.sparkContext.hadoopConfiguration.set("fs.s3.impl", "com.amazon.ws.emr.hadoop.fs.EmrFileSystem")
    if (sourceDestinationMap("awssecretaccesskey") != "") {
      sparkSession.sparkContext.hadoopConfiguration.set("fs.s3a.access.key", sourceDestinationMap("awsaccesskeyid"))
      sparkSession.sparkContext.hadoopConfiguration.set("fs.s3a.secret.key", sourceDestinationMap("awssecretaccesskey"))
    }
  }

  def jsonObjectPropertiesToMap(properties: List[String], jsonObject: JSONObject): Map[String, String] = {
    properties map (property => property -> (if (jsonObject.has(property)) jsonObject.getString(property) else "")) toMap
  }

  def jsonArrayPropertiesToList(jsonStringArr: String) = {
    var returnList = List.empty[String]
    val jsonArray = new JSONArray(jsonStringArr)
    val length = jsonArray.length()
    for( i <- 0 to length-1){
      returnList = returnList :+ jsonArray.get(i).toString()
    }
    returnList
  }

  def uuid(): String = {

    UUIDs.timeBased().toString

  }

  def validateUUID(uuidString: String): Boolean = {
    try {

      val uuid: UUID = UUID.fromString(uuidString)

      import java.util.UUID
      if (uuid.equals(new UUID(0, 0))) return false
      return uuidString.equalsIgnoreCase(uuid.toString)
    } catch {
      case e: Exception => {
        false
      }
    }
  }

  def uuidToBinary(uuid_key: String): Binary = {
    if (uuid_key == null) null

    else {
      val uuid = UUID.fromString(uuid_key)
      val holder = new BsonDocument
      val writer = new BsonDocumentWriter(holder)
      writer.writeStartDocument()
      writer.writeName("uuid")
      new UuidCodec(UuidRepresentation.STANDARD).encode(writer, uuid, EncoderContext.builder().build())
      writer.writeEndDocument()
      val bsonBinary = holder.getBinary("uuid")
      val binaryUuid = new Binary(bsonBinary.getType(), bsonBinary.getData())
      return binaryUuid
    }
  }

  def binaryToUUID(byte: Array[Byte]): String = {

    if (byte == null) null

    else {
      val bb = ByteBuffer.wrap(byte)
      new UUID(bb.getLong, bb.getLong()).toString
    }
  }

  def jsonObjectPropertiesToMap(jsonObject: JSONObject): Map[String, String] = {
    var returnMap = Map.empty[String, String]
    var keys = jsonObject.keys()
    while (keys.hasNext()) {
      val key = keys.next().toString()
      val value = jsonObject.getString(key)
      returnMap = returnMap ++ Map(key -> value)
    }
    returnMap
  }
  /**
    * Binary data to JUUID String representation
    * Based on: https://github.com/mongodb/mongo-csharp-driver/blob/master/uuidhelpers.js
    * I used a StringBuilder instead of their liberal use of substrings. ~3-4x faster
    */
  def binaryToJUUID(bytes: Array[Byte]): String = {
    if (bytes == null ) null
    else{
      val sb: StringBuilder  = new StringBuilder;
      val base64Bytes: Array[Byte] = Base64.decodeBase64(bytes)
      val hexChars: Array[Char] = Hex.encodeHex(base64Bytes)
      sb.appendAll(hexChars, 14, 2)
      sb.appendAll(hexChars, 12, 2)
      sb.appendAll(hexChars, 10, 2)
      sb.appendAll(hexChars, 8, 2)
      sb.append("-")
      sb.appendAll(hexChars, 6, 2)
      sb.appendAll(hexChars, 4, 2)
      sb.append("-")
      sb.appendAll(hexChars, 2, 2)
      sb.appendAll(hexChars, 0, 2)
      sb.append("-")
      sb.appendAll(hexChars, 30, 2)
      sb.appendAll(hexChars, 28, 2)
      sb.append("-")
      sb.appendAll(hexChars, 26, 2)
      sb.appendAll(hexChars, 24, 2)
      sb.appendAll(hexChars, 22, 2)
      sb.appendAll(hexChars, 20, 2)
      sb.appendAll(hexChars, 18, 2)
      sb.appendAll(hexChars, 16, 2)
      sb.toString
    }
  }
}
