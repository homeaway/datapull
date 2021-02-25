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

import java.io.{File, PrintWriter, StringWriter}
import java.nio.charset.StandardCharsets
import java.sql.{Connection, DriverManager, Statement, Timestamp}
import java.text.SimpleDateFormat
import java.time.Instant
import java.util
import java.util.{Calendar, Properties, UUID}

import com.amazonaws.services.logs.model.{DescribeLogStreamsRequest, InputLogEvent, PutLogEventsRequest}
import com.amazonaws.services.s3.model._
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.datastax.driver.core.exceptions.TruncateException
import com.datastax.spark.connector.cql.CassandraConnector
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.mongodb.client.{MongoCollection, MongoCursor, MongoDatabase}
import com.mongodb.spark.MongoSpark
import com.mongodb.spark.config.{ReadConfig, WriteConfig}
import com.mongodb.spark.sql.toSparkSessionFunctions
import com.mongodb.{MongoClient, MongoClientURI, MongoCredential, ServerAddress}
import config.AppConfig
import core.DataPull.jsonObjectPropertiesToMap
import helper._
import javax.mail.internet.{InternetAddress, MimeMessage}
import javax.mail.{Message, Session, Transport}
import net.snowflake.spark.snowflake.Utils.SNOWFLAKE_SOURCE_NAME
import org.apache.avro.generic.GenericData
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FSDataOutputStream, FileSystem, Path}
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.jdbc.JdbcDialects
import org.apache.spark.sql.{DataFrame, Row, SaveMode, SparkSession}
import org.bson.Document
import org.codehaus.jettison.json.JSONObject
import org.elasticsearch.spark.sql._
import org.influxdb.InfluxDBFactory
import org.influxdb.dto.Query
import security._
import za.co.absa.abris.avro.functions.to_avro

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.collection.immutable.List
import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, ListBuffer, StringBuilder}
import scala.util.control.Breaks._

class DataFrameFromTo(appConfig: AppConfig, pipeline: String) extends Serializable {
  val helper = new Helper(appConfig)
  def fileToDataFrame(filePath: String, fileFormat: String, delimiter: String, charset: String, mergeSchema: String, sparkSession: org.apache.spark.sql.SparkSession, isS3: Boolean, secretstore: String, isSFTP: Boolean, login: String, host: String, password: String, pemFilePath: String, awsEnv: String, vaultEnv: String): org.apache.spark.sql.DataFrame = {

    if (filePath == null && fileFormat == null && delimiter == null && charset == null && mergeSchema == null && sparkSession == null && login == null && host == null && password == null) {
      throw new Exception("Platform cannot have null values")
    }

    if (filePath == null && fileFormat == null && delimiter == null && charset == null && mergeSchema == null && sparkSession == null && login == null && host == null && password == null) {
      throw new Exception("Platform cannot have empty values")
    }

    var vaultPassword = password
    var vaultLogin = login
    if (vaultPassword == "" && awsEnv != "false" && vaultEnv != "false") {
      val secretService = new SecretService(secretstore, appConfig)
      val vaultCreds = secretService.getSecret(awsEnv, "sftp-" + host, login, vaultEnv)
      vaultLogin = vaultCreds("username")
      vaultPassword = vaultCreds("password")
    }

    var filePrefix = ""

    if (isS3) {
      val s3Prefix = if (sparkSession.sparkContext.master == "local[*]") "s3a" else "s3"
      filePrefix = s3Prefix + "://"
      sparkSession.conf.set("fs." + s3Prefix + ".connection.maximum", 100)
    }

    def createOrReplaceTempViewOnDF(df: org.apache.spark.sql.DataFrame): org.apache.spark.sql.DataFrame = {
      df
    }

    if (isSFTP) {

      createOrReplaceTempViewOnDF(sparkSession.read.
        format("com.springml.spark.sftp").
        option("host", host).
        option("username", login).
        option( if (pemFilePath == "")  "password" else "pem", if (pemFilePath == "")  password else pemFilePath).
        option("fileType", fileFormat).
        load(filePath))

    }

    else {
      if (fileFormat == "json") {
        createOrReplaceTempViewOnDF(sparkSession.read.json(s"$filePrefix$filePath"))
      } else if (fileFormat == "csv") {
        createOrReplaceTempViewOnDF(sparkSession.read.format("csv")
          .option("delimiter", delimiter)
          .option("mode", "DROPMALFORMED")
          .option("header", "true") //reading the headers
          .option("charset", charset)
          .csv(s"$filePrefix$filePath"))

      } else if (fileFormat == "avro") {
        createOrReplaceTempViewOnDF(sparkSession.read.format("avro").load(s"$filePrefix$filePath"))
      } else if (fileFormat == "orc") {
        createOrReplaceTempViewOnDF(sparkSession.read.format("orc").load(s"$filePrefix$filePath"))
      } else {
        //parquet
        createOrReplaceTempViewOnDF(sparkSession.read.option("mergeSchema", mergeSchema).parquet(s"$filePrefix$filePath"))
      }
    }
  }


  /*
* Get ngram transformation
*/

  implicit class RichDF(val ds: org.apache.spark.sql.DataFrame) {
    def showHTML(limit: Int = 100, truncate: Int = 100): String = {
      import xml.Utility.escape
      val data = ds.take(limit)
      val header = ds.schema.fieldNames.toSeq
      val rows: Seq[Seq[String]] = data.map { row =>
        row.toSeq.map { cell =>
          val str = cell match {
            case null => "null"
            case binary: Array[Byte] => binary.map("%02X".format(_)).mkString("[", " ", "]")
            case array: Array[_] => array.mkString("[", ", ", "]")
            case seq: Seq[_] => seq.mkString("[", ", ", "]")
            case _ => cell.toString
          }
          if (truncate > 0 && str.length > truncate) {
            // do not show ellipses for strings shorter than 4 characters.
            if (truncate < 4) str.substring(0, truncate)
            else str.substring(0, truncate - 3) + "..."
          } else {
            str
          }
        }: Seq[String]
      }
      var bodyHtml = StringBuilder.newBuilder
      bodyHtml = bodyHtml.append(
        s"""<style>table, th, td {border: 1px solid black;}</style> <table>
                <tr>
                 ${header.map(h => s"<th>${escape(h)}</th>").mkString}
                </tr>
                ${
          rows.map { row =>
            s"<tr>${row.map { c => s"<td>${escape(c)}</td>" }.mkString}</tr>"
          }.mkString
        }
            </table>
        """)
      bodyHtml.toString()

    }
  }

  def dataFrameToEmail(to: String, subject: String, df: org.apache.spark.sql.DataFrame, limit: String, truncate: String): Unit = {
    if (df == null) {
      throw new Exception("Platform cannot have null values")
    }

    val bodyHtml = df.showHTML(limit.toInt, truncate.toInt)
    //DataMigrationFramework.SendEmail(to,bodyHtml,"","",subject)
    val yamlMapper = new ObjectMapper(new YAMLFactory());
    val inputStream = this.getClass().getClassLoader().getResourceAsStream("application-dev.yml");
    val applicationConf = yamlMapper.readTree(inputStream)
    val config = new AppConfig(applicationConf)
    val EmailAddress = to;
    val htmlContent = bodyHtml;

    // Set up the mail object
    if (EmailAddress != "") {
      val properties = System.getProperties
      properties.put("mail.smtp.host", config.smtpServerAddress)
      val session = Session.getDefaultInstance(properties)
      val message = new MimeMessage(session)
      var subject1: String = subject
      // Set the from, to, subject, body text
      message.setFrom(new InternetAddress(config.dataToolsEmailAddress))
      message.setRecipients(Message.RecipientType.TO, "" + EmailAddress)
      message.setRecipients(Message.RecipientType.BCC, "" + config.dataToolsEmailAddress)
      message.setSubject(subject1)
      message.setContent(htmlContent, "text/html; charset=utf-8")
      // And send it
      Transport.send(message)
    }

  }

  def dataFrameToFile(filePath: String, fileFormat: String, groupByFields: String, s3SaveMode: String, df: org.apache.spark.sql.DataFrame, isS3: Boolean, secretstore: String, sparkSession: SparkSession, coalescefilecount: Integer, isSFTP: Boolean, login: String, host: String, password: String, pemFilePath: String, awsEnv: String, vaultEnv: String, rowFromJsonString: String, jsonFieldName: String): Unit = {

    if (filePath == null && fileFormat == null && groupByFields == null && s3SaveMode == null && login == null && SparkSession == null) {
      throw new Exception("Platform cannot have null values")
    }

    if (filePath.isEmpty() && fileFormat.isEmpty() && groupByFields.isEmpty() && s3SaveMode.isEmpty() && login.isEmpty() && sparkSession == null) {
      throw new Exception("Platform cannot have empty values")
    }

    //if password isn't set, attempt to get from Vault
    var vaultPassword = password
    var vaultLogin = login
    if (vaultPassword == "" && awsEnv != "false" && vaultEnv != "false") {
      val secretService = new SecretService(secretstore, appConfig)
      val vaultCreds = secretService.getSecret(awsEnv, "sftp-" + host, login, vaultEnv)
      vaultLogin = vaultCreds("username")
      vaultPassword = vaultCreds("password")
    }
    sparkSession.sparkContext.hadoopConfiguration.set("spark.shuffle.service.enabled", "true")
    var groupByFieldsArray = groupByFields.split(",")
    var filePrefix = ""

    var dft = sparkSession.emptyDataFrame

    if (coalescefilecount == null) {
      if (fileFormat == "csv") {
        dft = df.coalesce(1)
      }
      else {
        dft = df
      }
    }
    else {
      if (coalescefilecount < df.rdd.partitions.size)
        dft = df.coalesce(coalescefilecount)
      else if (coalescefilecount > df.rdd.partitions.size)
        dft = df.repartition(coalescefilecount)
    }

    if (isS3) {
      val s3Prefix = if (sparkSession.sparkContext.master == "local[*]") "s3a" else "s3"
      filePrefix = s3Prefix + "://"
      sparkSession.conf.set("fs." + s3Prefix + ".connection.maximum", 100)
    }

    if (isSFTP) {

      df.write.
        format("com.springml.spark.sftp").
        option("host", host).
        option("username", login).
        option( if (pemFilePath == "")  "password" else "pem", if (pemFilePath == "")  password else pemFilePath).
        option("fileType", fileFormat).
        save(filePath)

    } else if (rowFromJsonString.toBoolean) {

      df.foreachPartition((partition: Iterator[Row]) => {

        val partitionList = new util.ArrayList[String]()
        partition.foreach(Row =>
          partitionList.add(Row.apply(0).toString)
        )
        if (!partitionList.isEmpty) {
          val conf: Configuration = new Configuration
          val path_string = filePrefix + filePath + "/" + UUID.randomUUID().toString + ".json"
          val dest: Path = new Path(path_string)
          val fs: FileSystem = dest.getFileSystem(conf)
          val out: FSDataOutputStream = fs.create(dest, true)
          out.write(partitionList.mkString("\n").getBytes(StandardCharsets.UTF_8))
          out.close()
        }
        partitionList.clear()
      })
    }
    else {
      if (fileFormat == "json") {
        if (groupByFields == "") {

          dft
            .write
            .format("json")
            .mode(SaveMode.valueOf(s3SaveMode)).json(s"$filePrefix$filePath")
        } else {
          dft
            .write
            .format("json")
            .partitionBy(groupByFieldsArray: _*)
            .mode(SaveMode.valueOf(s3SaveMode)).json(s"$filePrefix$filePath")
        }
      } else if (fileFormat == "csv") {
        if (groupByFields == "") {
          dft
            .write
            .option("header", "true")
            .mode(SaveMode.valueOf(s3SaveMode))
            .csv(s"$filePrefix$filePath")
        } else {
          dft
            .write
            .partitionBy(groupByFieldsArray: _*)
            .option("header", "true")
            .mode(SaveMode.valueOf(s3SaveMode))
            .csv(s"$filePrefix$filePath")
        }
      } else if (fileFormat == "avro") {
        if (groupByFields == "") {
          dft
            .write
            .format("avro")
            .option("header", "true")
            .mode(SaveMode.valueOf(s3SaveMode))
            .save(s"$filePrefix$filePath")
        } else {
          dft
            .write
            .format("avro")
            .partitionBy(groupByFieldsArray: _*)
            .option("header", "true")
            .mode(SaveMode.valueOf(s3SaveMode))
            .save(s"$filePrefix$filePath")
        }
      } else if (fileFormat == "orc") {
        if (groupByFields == "") {
          dft
            .write
            .option("header", "true")
            .mode(SaveMode.valueOf(s3SaveMode))
            .orc(s"$filePrefix$filePath")
        } else {
          dft
            .write
            .partitionBy(groupByFieldsArray: _*)
            .option("header", "true")
            .mode(SaveMode.valueOf(s3SaveMode))
            .orc(s"$filePrefix$filePath")
        }
      }
      else {
        //parquet
        if (groupByFields == "") {
          Option(dft
            .write
            .mode(SaveMode.valueOf(s3SaveMode)).parquet(s"$filePrefix$filePath"))
        } else {
          Option(dft
            .write
            .partitionBy(groupByFieldsArray: _*)
            .mode(SaveMode.valueOf(s3SaveMode)).parquet(s"$filePrefix$filePath"))
        }
      }

    }
  }

  /**
   * pre/Post migration command for s3
   *
   * @param source       :  source details for s3
   * @param destination  : destination details for s3
   * @param overwrite    : Boolean value if destination has to be overwriten.
   * @param removeSource : True if we have to remove the source
   * @param sparkSession : Spark session object
   */
  def s3CopyDirectory(source: String, destination: String, overwrite: Boolean, removeSource: Boolean, partitioned: Boolean, s3Client: AmazonS3, sparkSession: SparkSession): Unit = {

    val sourceS3Bucket = source.split("/")(0)
    val destS3Bucket = destination.split("/")(0)
    val sourcePrefix = source.substring(sourceS3Bucket.length + 1, source.length)
    val destPrefix = destination.substring(destS3Bucket.length + 1, destination.length)


    if (partitioned) {

      val sourcePathLength = source.split("/").length - 1
      val destPathLength = destination.split("/").length - 1

      val sourceObjects = new ListObjectsV2Request().withBucketName(sourceS3Bucket).withPrefix(sourcePrefix)
      var result = new ListObjectsV2Result
      val sourceArray = new ArrayBuffer[String]()

      do {
        result = s3Client.listObjectsV2(sourceObjects)
        sourceArray ++= result.getObjectSummaries.map(_.getKey)
        sourceObjects.setContinuationToken(result.getNextContinuationToken)
      } while (result.isTruncated)

      val sourceParts = sourceArray.map(_.split("/")).filter(x => x.length > sourcePathLength && !x.contains("_SUCCESS")).map(_ (sourcePathLength)).distinct.toArray

      val destObjects = new ListObjectsV2Request().withBucketName(destS3Bucket).withPrefix(destPrefix)
      //var result = new ListObjectsV2Result
      result = new ListObjectsV2Result
      val destArray = new ArrayBuffer[String]()

      do {
        result = s3Client.listObjectsV2(destObjects)
        destArray ++= result.getObjectSummaries.map(_.getKey)
        destObjects.setContinuationToken(result.getNextContinuationToken)
      } while (result.isTruncated)

      val destParts = destArray.map(_.split("/")).filter(x => x.length > destPathLength && !x.contains("_SUCCESS")).map(_ (destPathLength)).distinct.toArray

      sourceParts.foreach { x =>
        if (destParts contains x) {
          val deleteKeys = new ArrayBuffer[String]()
          val reqDest = new ListObjectsV2Request().withBucketName(destS3Bucket).withPrefix(destPrefix + "/" + x)
          result = new ListObjectsV2Result

          do {
            result = s3Client.listObjectsV2(reqDest)
            deleteKeys ++= result.getObjectSummaries.map(x => x.getKey)
            reqDest.setContinuationToken(result.getNextContinuationToken)
          } while (result.isTruncated)

          deleteKeys.foreach(println)
          val multiObjectDeleteRequest = new DeleteObjectsRequest(destS3Bucket).withKeys(deleteKeys: _*)
          s3Client.deleteObjects(multiObjectDeleteRequest)
        }

        println(s"""TEST Source:${sourcePrefix + "/" + x}""")
        val reqSource = new ListObjectsV2Request().withBucketName(sourceS3Bucket).withPrefix(sourcePrefix + "/" + x)
        result = new ListObjectsV2Result

        do {
          result = s3Client.listObjectsV2(reqSource)
          val copyKeys = result.getObjectSummaries.map(_.getKey)
          copyKeys.foreach { key =>
            val src = key
            val dest = destPrefix + "/" + x + "/" + src.split("/").last
            println(s"Copying $src to $dest")
            val cReq = new CopyObjectRequest(sourceS3Bucket, src, destS3Bucket, dest)
            s3Client.copyObject(cReq)
          }
          reqSource.setContinuationToken(result.getNextContinuationToken)
        } while (result.isTruncated)
      }
      if (removeSource) {
        s3RemoveDirectory(source, s3Client, sparkSession)
      }
    }
    else {
      val sourceObjects = new ListObjectsV2Request().withBucketName(sourceS3Bucket).withPrefix(sourcePrefix)
      var result = new ListObjectsV2Result
      val sourceArray = new ArrayBuffer[String]()

      result = s3Client.listObjectsV2(sourceObjects)
      val copyKeys = result.getObjectSummaries.map(_.getKey)
      copyKeys.foreach { key =>
        val src = key
        val dest = destPrefix + (if (destPrefix.charAt(destPrefix.length - 1).toString == "/") "" else "/") + src.split("/").last
        val cReq = new CopyObjectRequest(sourceS3Bucket, src, destS3Bucket, dest)
        s3Client.copyObject(cReq)
      }
    }
  }

  def s3RemoveDirectory(s3Location: String, s3Client: AmazonS3, sparkSession: SparkSession): Unit = {

    val bucketName = s3Location.split("/")(0)
    val prefix = s3Location.substring(bucketName.length + 1, s3Location.length)

    val objects = new ListObjectsV2Request().withBucketName(bucketName).withPrefix(prefix)
    var result = new ListObjectsV2Result
    result = s3Client.listObjectsV2(objects)
    val delKeys = result.getObjectSummaries.map(_.getKey)

    delKeys.foreach { key =>
      s3Client.deleteObject(bucketName, key)
    }

  }

  def s3RemoveDirectoryUsingS3Client(s3Location: String): Unit = {
    val s3 = AmazonS3ClientBuilder.defaultClient()
    val bucketName = s3Location.split("/")(2)
    val path = s3Location.substring(s3Location.indexOf(bucketName) + bucketName.length + 1)
    s3.deleteObject(bucketName, path)
  }

  def cassandraToDataFrame(awsEnv: String, cluster: String, keyspace: String, table: String, login: String, password: String, local_dc: String, addlSparkOptions: JSONObject, sparkSession: org.apache.spark.sql.SparkSession, vaultEnv: String, secretstore: String): org.apache.spark.sql.DataFrame = {

    val consul = new Consul(cluster, appConfig)
    var clusterName = cluster
    var clusterNodes = cluster
    if (consul.IsConsulDNSName()) {
      clusterName = consul.serviceName
      clusterNodes = clusterNodes + "," + consul.ipAddresses.mkString(",")
    }
    //if password isn't set, attempt to get from security.Vault
    var vaultPassword = password
    var vaultLogin = login
    if (vaultPassword == "") {
      val secretService = new SecretService(secretstore, appConfig)
      val vaultCreds = secretService.getSecret(awsEnv, clusterName, login, vaultEnv)
      vaultLogin = vaultCreds("username")
      vaultPassword = vaultCreds("password")
    }
    var sparkOptions = Map("keyspace" -> keyspace, "table" -> table, "spark.cassandra.connection.host" -> clusterNodes, "spark.cassandra.auth.username" -> vaultLogin, "spark.cassandra.auth.password" -> vaultPassword, "spark.cassandra.input.consistency.level" -> "LOCAL_QUORUM")
    if (local_dc != "") {
      sparkOptions = sparkOptions ++ Map("spark.cassandra.connection.local_dc" -> local_dc)
    }

    if (addlSparkOptions != null) {
      sparkOptions = sparkOptions ++ jsonObjectPropertiesToMap(addlSparkOptions)
    }

    val df = sparkSession
      .read.format("org.apache.spark.sql.cassandra")
      .options(sparkOptions)
      .load()
    df
  }

  def dataFrameToCassandra(awsEnv: String, cluster: String, keyspace: String, table: String, login: String, password: String, local_dc: String, addlSparkOptions: JSONObject, df: org.apache.spark.sql.DataFrame, reportbodyHtml: StringBuilder, vaultEnv: String, secretStore: String): Unit = {

    val consul = new Consul(cluster, appConfig)
    var clusterName = cluster
    var clusterNodes = cluster
    if (consul.IsConsulDNSName()) {
      clusterName = consul.serviceName
      clusterNodes = clusterNodes + "," + consul.ipAddresses.mkString(",")
    }
    //if password isn't set, attempt to get from security.Vault
    var vaultPassword = password
    var vaultLogin = login
    if (vaultPassword == "") {
      val secretService = new SecretService(secretStore, appConfig)
      val vaultCreds = secretService.getSecret(awsEnv, clusterName, login, vaultEnv)
      vaultLogin = vaultCreds("username")
      vaultPassword = vaultCreds("password")
    }

    var sparkOptions = Map("keyspace" -> keyspace, "table" -> table, "spark.cassandra.connection.host" -> clusterNodes, "spark.cassandra.auth.username" -> vaultLogin, "spark.cassandra.auth.password" -> vaultPassword, "spark.cassandra.output.consistency.level" -> "LOCAL_QUORUM", "spark.cassandra.output.batch.size.bytes" -> "1024", "spark.cassandra.output.batch.grouping.buffer.size" -> "1000", "spark.cassandra.output.concurrent.writes" -> "100", "spark.cassandra.output.batch.grouping.key" -> "none", "spark.cassandra.output.ignoreNulls" -> "true")

    if (local_dc != "") {
      sparkOptions = sparkOptions ++ Map("spark.cassandra.connection.local_dc" -> local_dc)
    }

    if (addlSparkOptions != null) {
      sparkOptions = sparkOptions ++ jsonObjectPropertiesToMap(addlSparkOptions)
    }

    df.write
      .format("org.apache.spark.sql.cassandra")
      .mode(SaveMode.Append)
      .options(sparkOptions)
      .save()
  }

  def dataFrameToNeo4j(df: org.apache.spark.sql.DataFrame, cluster: String, login: String, password: String, awsEnv: String, vaultEnv: String, node1_label: String, node1_keys: List[String], node1_nonKeys: List[String], node2_label: String, node2_keys: List[String], node2_nonKeys: List[String], relation_label: String, batchSize: Int,
                       node1_createOrMerge: String,
                       node1_createNodeKeyConstraint: Boolean,
                       node2_createOrMerge: String,
                       node2_createNodeKeyConstraint: Boolean,
                       relation_createOrMerge: String, secretStore: String, sparkSession: org.apache.spark.sql.SparkSession): Unit = {
    val consul = new Consul(cluster, appConfig)
    var clusterName = cluster
    if (consul.IsConsulDNSName()) {
      clusterName = consul.serviceName
    }
    //if password isn't set, attempt to get from security.Vault
    var vaultPassword = password
    var vaultLogin = login
    if (vaultPassword == "") {
      val secretService = new SecretService(secretStore, appConfig)
      val vaultCreds = secretService.getSecret(awsEnv, clusterName, login, vaultEnv)
      vaultLogin = vaultCreds("username")
      vaultPassword = vaultCreds("password")
    }

    if (node1_label != "" && (!node1_keys.isEmpty)) {
      val neo4j = new Neo4j()
      neo4j.writeNode(df, cluster, vaultLogin, vaultPassword, node1_label, node1_keys, node1_nonKeys, sparkSession, batchSize, node1_createNodeKeyConstraint, node1_createOrMerge)

      if (node2_label != null && node2_label != "" && (!node2_keys.isEmpty)) {
        val neo4j = new Neo4j()
        neo4j.writeNode(df, cluster, vaultLogin, vaultPassword, node2_label, node2_keys, node2_nonKeys, sparkSession, batchSize, node2_createNodeKeyConstraint, node2_createOrMerge)

        if (relation_label != null) {
          neo4j.writeRelation(df, cluster, vaultLogin, vaultPassword, node1_label, node1_keys, node2_label, node2_keys, relation_label, sparkSession, batchSize, relation_createOrMerge)
        }
      }
    }
  }

  def dataFrameToElastic(awsEnv: String, cluster: String, port: String, index: String, nodetype: String, version: String, login: String, password: String, local_dc: String, addlSparkOptions: JSONObject, df: org.apache.spark.sql.DataFrame, reportbodyHtml: StringBuilder, vaultEnv: String, saveMode: String, mappingId: String, flag: String, secretStore: String, sparkSession: org.apache.spark.sql.SparkSession): Unit = {


    val consul = new Consul(cluster, appConfig)
    var clusterName = cluster
    var clusterNodes = cluster
    if (consul.IsConsulDNSName()) {
      clusterName = consul.serviceName
      clusterNodes = clusterNodes + "," + consul.ipAddresses.mkString(",")
    }
    //if password isn't set, attempt to get from security.Vault
    var vaultPassword = password
    var vaultLogin = login
    if (vaultPassword == "") {
      val secretService = new SecretService(secretStore, appConfig)
      val vaultCreds = secretService.getSecret(awsEnv, clusterName, login, vaultEnv)
      vaultLogin = vaultCreds("username")
      vaultPassword = vaultCreds("password")
    }

    var config = Map("es.nodes" -> clusterNodes,
      "es.port" -> port,
      "es.clustername" -> clusterName,
      "es.net.http.auth.user" -> vaultLogin,
      "es.net.http.auth.pass" -> vaultPassword,
      "es.write.operation" -> saveMode,
      "es.nodes.wan.only" -> "true",
      "es.resource" -> s"$index/$nodetype",
      "es.internal.es.version" -> version)


    if (mappingId != null)
      config = config ++ Map("es.mapping.id" -> mappingId)

    if (flag == "false") {
      df.saveToEs(config)
    }

  }

  def ElasticToDataframe(awsEnv: String, cluster: String, port: String, index: String, nodetype: String, version: String, login: String, password: String, vaultEnv: String, secretStore: String, sparkSession: org.apache.spark.sql.SparkSession): org.apache.spark.sql.DataFrame = {


    val consul = new Consul(cluster, appConfig)
    var clusterName = cluster
    var clusterNodes = cluster
    if (consul.IsConsulDNSName()) {
      clusterName = consul.serviceName
      clusterNodes = clusterNodes + "," + consul.ipAddresses.mkString(",")
    }
    //if password isn't set, attempt to get from security.Vault
    var vaultPassword = password
    var vaultLogin = login
    if (vaultPassword == "") {
      val secretService = new SecretService(secretStore, appConfig)
      val vaultCreds = secretService.getSecret(awsEnv, clusterName, login, vaultEnv)
      vaultLogin = vaultCreds("username")
      vaultPassword = vaultCreds("password")
    }

    val df = sparkSession
      .read.format("org.elasticsearch.spark.sql")
      .option("es.nodes", clusterNodes)
      .option("es.port", port)
      .option("es.index.auto.create", "true")
      .option("es.nodes.wan.only", "true")
      .option("es.clustername", clusterName)
      .option("es.net.http.auth.user", vaultLogin)
      .option("es.net.http.auth.pass", vaultPassword)
      .option("es.internal.es.version", version)
      .load("" + index + "/" + nodetype)

    df
  }

  def InfluxdbToDataframe(awsEnv: String, clustername: String, database: String, measurementname: String, login: String, password: String, vaultEnv: String, secretStore: String, sparkSession: org.apache.spark.sql.SparkSession): org.apache.spark.sql.DataFrame = {

    if (awsEnv == null && clustername == null && database == null && measurementname == null && login == null && password == null && vaultEnv == null && sparkSession == null) {
      throw new Exception("Platform cannot have null values")
    }

    if (awsEnv.isEmpty() == true && clustername.isEmpty() == true && database.isEmpty() == true && measurementname.isEmpty() == true && login.isEmpty() == true && password.isEmpty() == true && vaultEnv.isEmpty() == true && sparkSession == null) {
      throw new Exception("Platform cannot have empty values")
    }

    val consul = new Consul(clustername, appConfig)
    var clusterName = clustername
    var clusterNodes = clustername
    if (consul.IsConsulDNSName()) {
      clusterName = consul.serviceName
      clusterNodes = clusterNodes + "," + consul.ipAddresses.mkString(",")
    }
    //if password isn't set, attempt to get from security.Vault
    var vaultPassword = password
    var vaultLogin = login
    if (vaultPassword == "") {
      val secretService = new SecretService(secretStore, appConfig)
      val vaultCreds = secretService.getSecret(awsEnv, clusterName, login, vaultEnv)
      vaultLogin = vaultCreds("username")
      vaultPassword = vaultCreds("password")
    }

    val influxDB = InfluxDBFactory.connect("http://" + clustername + ":8086", login, password);
    val query = new Query("select * from " + measurementname, database);

    val queryresult = influxDB.query(query);
    val result = queryresult.getResults();
    val size = result.size()
    val rawColumnNames = result.get(size - 1).getSeries().get(size - 1).getColumns();
    val rawColumnValues = result.get(size - 1).getSeries().get(size - 1).getValues();

    val icolumns = rawColumnNames.iterator();
    val ivalues = rawColumnValues.iterator();

    var influxdata = new ListBuffer[String]()
    var influxcolumnvalues = new ListBuffer[String]()

    while (ivalues.hasNext()) {
      influxdata += ivalues.next().toString().dropRight(1).drop(1);
    }

    while (icolumns.hasNext()) {
      influxcolumnvalues += icolumns.next().toString();
    }

    val columnlist = influxcolumnvalues.toList

    val finalcolumnlist = columnlist.mkString(",")

    val finalinfluxdata = influxdata.toList

    import sparkSession.implicits._
    val df = finalinfluxdata.toDF().withColumnRenamed("value", finalcolumnlist)
    df
  }

  def cassandraRunCommand(awsEnv: String, cluster: String, keyspace: String, login: String, password: String, local_dc: String, addlSparkOptions: JSONObject, cql_command: String, reportbodyHtml: StringBuilder, vaultEnv: String, secretStore: String, ignoreTruncateException: Boolean = true): Unit = {
    if (cql_command != "") {
      val consul = new Consul(cluster, appConfig)
      var clusterName = cluster
      var clusterNodes = cluster
      if (consul.IsConsulDNSName()) {
        clusterName = consul.serviceName
        clusterNodes = clusterNodes + "," + consul.ipAddresses.mkString(",")
      }
      //if password isn't set, attempt to get from security.Vault
      var vaultPassword = password
      var vaultLogin = login
      if (vaultPassword == "") {
        val secretService = new SecretService(secretStore, appConfig)
        val vaultCreds = secretService.getSecret(awsEnv, clusterName, login, vaultEnv)
        vaultLogin = vaultCreds("username")
        vaultPassword = vaultCreds("password")
      }

      var sparkOptions = Map("keyspace" -> keyspace, "spark.cassandra.connection.host" -> clusterNodes, "spark.cassandra.auth.username" -> vaultLogin, "spark.cassandra.auth.password" -> vaultPassword, "spark.cassandra.output.consistency.level" -> "LOCAL_ONE")

      if (addlSparkOptions != null) {
        sparkOptions = sparkOptions ++ jsonObjectPropertiesToMap(addlSparkOptions)
      }

      try {
        val conf = new SparkConf(true)
          .setAll(sparkOptions)
        val cConnect = CassandraConnector(conf)
        cConnect.withSessionDo(session => session.execute(cql_command))
      } catch {
        case e: TruncateException =>
          val sw = new StringWriter
          e.printStackTrace()
          e.printStackTrace(new PrintWriter(sw))
          println(s"TruncateException during truncate of $cql_command. Suppressing the error and proceeding with data migration.")
          reportbodyHtml.append(s"<tr><td><h4>Warning, Truncate Table failed for command $cql_command!</h4></td><td>" + Instant.now().toString() + "</td><td colspan=\"4\">" + sw.toString() + "</td></tr>")
          if (!ignoreTruncateException) {
            throw (e)
          }
      } finally {
      }
    }
  }

  def elasticRunCommand(awsEnv: String, cluster: String, port: String, index: String, login: String, password: String, local_dc: String, addlSparkOptions: JSONObject, curlcommand: String, reportbodyHtml: StringBuilder, vaultEnv: String, secretStore: String, ignoreTruncateException: Boolean = true): Unit = {

    //if password isn't set, attempt to get from Vault
    var vaultPassword = password
    var vaultLogin = login
    if (vaultPassword == "") {
      val secretService = new SecretService(secretStore, appConfig)
      val vaultCreds = secretService.getSecret(awsEnv, cluster, login, vaultEnv)
      vaultLogin = vaultCreds("username")
      vaultPassword = vaultCreds("password")
    }
    //logic
    var connection: Connection = null
    var statement: Statement = null

    try {

      val service = vaultPassword + ":" + vaultLogin + "@" + cluster + ":" + port + "/"
      var cmd_withcreds = curlcommand.replace("://", "://" + login + ":" + password + "@") //.replace("\"", "\\\"");

      val fileName = pipeline + ".sh"
      import java.io.PrintWriter
      new PrintWriter(fileName) {
        write(cmd_withcreds);
        close
      }
      val file = new File(fileName)
      file.setReadable(true, false)
      file.setExecutable(true, false)
      file.setWritable(true, false)
      import sys.process._
      val result = "./" + fileName !!

      println("command result = " + result + " pipeline name = " + fileName + " pipeline = " + pipeline);

      new File(fileName).delete()

      val tokens: Array[String] = cmd_withcreds.split(" ");
    } catch {
      case e: Throwable => e.printStackTrace
        throw (e)
    } finally {
      if (connection != null) {
        if (!connection.isClosed()) {
          connection.close()
        }
      }
    }
  }

  def mongodbToDataFrame(awsEnv: String, cluster: String, overrideconnector: String, database: String, authenticationDatabase: String, collection: String, login: String, password: String, sparkSession: org.apache.spark.sql.SparkSession, vaultEnv: String, addlSparkOptions: JSONObject, secretStore: String, authenticationEnabled: String, tmpFileLocation: String, sampleSize: String, sslEnabled: String): org.apache.spark.sql.DataFrame = {
    val consul = new Consul(cluster, appConfig)
    var clusterName = cluster
    var clusterNodes = cluster
    if (consul.IsConsulDNSName()) {
      clusterName = consul.serviceName
      clusterNodes = clusterNodes + "," + consul.ipAddresses.mkString(",") + ":27017"
    }
    var uri: String = null
    val helper = new Helper(appConfig)
    var vaultLogin: String = null
    var vaultPassword: String = null
    sparkSession.sparkContext.hadoopConfiguration.set("spark.shuffle.service.enabled", "true")
    //if password isn't set, attempt to get from security.Vault
    if (authenticationEnabled.toBoolean) {
      vaultPassword = password
      vaultLogin = login
      if (vaultPassword == "") {
        val secretService = new SecretService(secretStore, appConfig)
        val vaultCreds = secretService.getSecret(awsEnv, clusterName, login, vaultEnv)
        vaultLogin = vaultCreds("username")
        vaultPassword = vaultCreds("password")
      }
    }
    uri = helper.buildMongoURI(vaultLogin, vaultPassword, cluster, null, authenticationDatabase, database, collection, authenticationEnabled.toBoolean, sslEnabled)
    if (overrideconnector.toBoolean) {
      var mongoClient: MongoClient = new MongoClient(new MongoClientURI(uri))
      var mdatabase: MongoDatabase = mongoClient.getDatabase("" + database);
      var col: MongoCollection[Document] = mdatabase.getCollection(collection);
      var cur: MongoCursor[Document] = col.find().iterator()
      var doc: org.bson.Document = null
      val list = new ListBuffer[String]()
      val tmp_location = tmpFileLocation
      var df_temp = sparkSession.emptyDataFrame
      var df_big = sparkSession.emptyDataFrame

      import sparkSession.implicits._
      while (cur.hasNext()) {
        doc = cur.next();
        list += (doc.toJson)
        if (list.length >= 20000) {
          df_temp = list.toList.toDF("jsonfield")
          df_temp.write.mode(SaveMode.Append).json(tmp_location)
          list.clear()
        }
      }
      df_temp = list.toList.toDF("jsonfield")
      df_temp.write.mode(SaveMode.Append).json(tmp_location)
      list.clear()
      df_big = sparkSession.read.json(tmp_location).withColumnRenamed("value", "jsonfield")
      return df_big
    }
    else {
      var sparkOptions = Map("uri" -> uri)
      if (addlSparkOptions != null) {
        sparkOptions = sparkOptions ++ jsonObjectPropertiesToMap(addlSparkOptions)
      }
      if (sampleSize != null) {
        sparkOptions = sparkOptions ++ Map("spark.mongodb.input.sample.sampleSize" -> sampleSize, "sampleSize" -> sampleSize)
      }
      val df = sparkSession.loadFromMongoDB(ReadConfig(sparkOptions))
      return df
    }
  }

  def dataFrameToMongodb(awsEnv: String, cluster: String, database: String, authenticationDatabase: String, collection: String, login: String, password: String, replicaset: String, replaceDocuments: String, ordered: String, df: org.apache.spark.sql.DataFrame, sparkSession: org.apache.spark.sql.SparkSession, documentfromjsonfield: String, jsonfield: String, vaultEnv: String, secretStore: String, addlSparkOptions: JSONObject, maxBatchSize: String, authenticationEnabled: Boolean, sslEnabled: String): Unit = {

    val consul = new Consul(cluster, appConfig)
    var clusterName = cluster
    if (consul.IsConsulDNSName()) {
      clusterName = consul.serviceName
    }
    var uri: String = null

    var vaultLogin: String = null
    var vaultPassword: String = null
    //if password isn't set, attempt to get from security.Vault
    if (authenticationEnabled) {
      vaultPassword = password
      vaultLogin = login
      if (vaultPassword == "") {
        val secretService = new SecretService(secretStore, appConfig)
        val vaultCreds = secretService.getSecret(awsEnv, clusterName, login, vaultEnv)
        vaultLogin = vaultCreds("username")
        vaultPassword = vaultCreds("password")
      }
    }
    uri = helper.buildMongoURI(vaultLogin, vaultPassword, cluster, replicaset, authenticationDatabase, database, collection, authenticationEnabled, sslEnabled)

    var sparkOptions = Map("uri" -> uri, "replaceDocument" -> replaceDocuments.toString, "ordered" -> ordered.toString)
    if (maxBatchSize != null)
      sparkOptions = sparkOptions ++ Map("maxBatchSize" -> maxBatchSize)

    if (addlSparkOptions != null) {
      sparkOptions = sparkOptions ++ jsonObjectPropertiesToMap(addlSparkOptions)
    }

    val writeConfig = WriteConfig(sparkOptions)
    if (documentfromjsonfield.toBoolean) {

      import com.mongodb.spark._
      import org.bson.Document
      import sparkSession.implicits._
      val rdd = df.select(jsonfield).map(r => r.getString(0)).rdd
      rdd.map(Document.parse).saveToMongoDB(writeConfig)
    }
    else {
      MongoSpark.save(df, writeConfig)

    }
  }

  def mongoRunCommand(awsEnv: String, cluster: String, database: String, authenticationDatabase: String, collection: String, login: String, password: String, vaultEnv: String, addlSparkOptions: JSONObject, runCommand: String, secretStore: String, authenticationEnabled: Boolean, sslEnabled: String): Unit = {

    val consul = new Consul(cluster, appConfig)
    var clusterName = cluster
    var clusterNodes = cluster
    if (consul.IsConsulDNSName()) {
      clusterName = consul.serviceName
      clusterNodes = clusterNodes + "," + consul.ipAddresses.mkString(",")
    }

    var vaultLogin: String = null
    var vaultPassword: String = null
    //if password isn't set, attempt to get from security.Vault
    if (authenticationEnabled) {
      vaultPassword = password
      vaultLogin = login
      if (vaultPassword == "") {
        val secretService = new SecretService(secretStore, appConfig)
        val vaultCreds = secretService.getSecret(awsEnv, clusterName, login, vaultEnv)
        vaultLogin = vaultCreds("username")
        vaultPassword = vaultCreds("password")
      }
    }

    val mongoCredential = {
      MongoCredential.createPlainCredential(authenticationDatabase, login, vaultPassword.toCharArray)
    }

    var authList = new util.ArrayList[MongoCredential]()
    authList.add(mongoCredential)

    var connectionString = new ServerAddress(cluster, 27017)

    val uri = new MongoClientURI(s"mongodb://$vaultLogin:$vaultPassword@$clusterNodes/?authSource=$authenticationDatabase&authMechanism=SCRAM-SHA-1")
    val mongoClient = new MongoClient(uri)
    var data = mongoClient.getDatabase(database)

    val response = data.runCommand(org.bson.Document.parse(runCommand))
  }

  def kafkaToDataFrame(bootstrapServers: String,
                       topic: String,
                       offset: String,
                       schemaRegistries: String,
                       keyDeserializer: String,
                       deSerializer: String,
                       s3Location: String,
                       groupId: String,
                       requiredOnlyValue: String,
                       payloadColumnName: String,
                       migrationId: String,
                       jobId: String,
                       sparkSession: org.apache.spark.sql.SparkSession,
                       s3TempFolderDeletionError: mutable.StringBuilder,
                       keyStorePath: Option[String] = None,
                       trustStorePath: Option[String] = None,
                       keyStorePassword: Option[String] = None,
                       trustStorePassword: Option[String] = None,
                       keyPassword: Option[String] = None): org.apache.spark.sql.DataFrame = {

    var groupId_temp = groupId

    if (groupId_temp == null)
      groupId_temp = UUID.randomUUID().toString + "_" + topic

    println(groupId_temp)

    val props = new Properties()

    props.put("key.deserializer", keyDeserializer)
    props.put("value.deserializer", deSerializer)
    props.put("group.id", groupId_temp)
    props.put("auto.offset.reset", offset)
    props.put("max.poll.records", "500")
    props.put("session.timeout.ms", "120000")

    props.putAll(helper.buildSecureKafkaProperties(keyStorePath = keyStorePath,
      trustStorePath = trustStorePath,
      keyStorePassword = keyStorePassword,
      trustStorePassword = trustStorePassword,
      keyPassword = keyPassword)
    )

    val consumer = new KafkaConsumer[String, GenericData.Record](props)

    consumer.subscribe(util.Collections.singletonList(topic))
    var list = new ListBuffer[String]()

    var tmp_s3: String = null

    var poll = 0
    var df_temp: org.apache.spark.sql.DataFrame = null
    var df: org.apache.spark.sql.DataFrame = null
    var count = 0

    tmp_s3 = s3Location + "/migrationId=" + migrationId + "/jobId=" + jobId

    val currentTime_Unix = System.currentTimeMillis()

    breakable {
      while (true) {

        val consumerRecords = consumer.poll(200)

        val count_tmp = consumerRecords.count()

        count += count_tmp

        if (count_tmp == 0) {

          println("didn't find any records, waiting...")

          Thread.sleep(10000)

          if (poll >= 400) {
            df_temp = sparkSession.read.json(sparkSession.sparkContext.parallelize(list))
            df_temp.write.mode(SaveMode.Append).json(tmp_s3)
            list.clear()

            println("breaking out of the loop after required retries at:" + Instant.now())

            break()
          }

          poll += 1
        }

        if (count > 0) {

          for (consumerRecord <- consumerRecords.asScala) {

            if (consumerRecord.timestamp() > currentTime_Unix) {
              df_temp = sparkSession.read.json(sparkSession.sparkContext.parallelize(list))
              df_temp.write.mode(SaveMode.Append).json(tmp_s3)
              list.clear()

              println("breaking out of the loop after reading all the messages till the job started and ending at:" + Instant.now())

              break()
            }


            if (requiredOnlyValue == "true") {

              list += consumerRecord.value().get(payloadColumnName).toString

            }
            else {

              val fullJSONObject: JSONObject = new JSONObject()
              var key: JSONObject = null
              var value: JSONObject = null
              var valueString: String = null

              var keyString: String = null

              valueString = consumerRecord.value().toString
              keyString = consumerRecord.key().toString

              if (isJSONString(keyString)) {
                key = new JSONObject(keyString)
                fullJSONObject.put("key", key)
              }
              else {
                fullJSONObject.put("key", consumerRecord.key())
              }

              value = new JSONObject(valueString)
              if (value != null && value.toString != "") {

                fullJSONObject.put("value", value)
              }
              list += fullJSONObject.toString()

            }

            if (list.length >= 80000) {
              df_temp = sparkSession.read.json(sparkSession.sparkContext.parallelize(list))
              println("writing to s3 after reaching the block size and time now is:" + Instant.now())
              df_temp.write.mode(SaveMode.Append).json(tmp_s3)
              list.clear()
              count = 0
            }

          }

        }
      }
    }
    df = sparkSession.read.json(tmp_s3)
    try {
      s3RemoveDirectoryUsingS3Client(tmp_s3)
      println(s"Deleted temporary S3 folder - $tmp_s3")
    } catch {
      case e: Throwable => e.printStackTrace
        println(s"Unable to delete temporary S3 folder - $tmp_s3")
        s3TempFolderDeletionError.append(s"<b>*</b> Unable to delete S3 folder - $tmp_s3")
    }

    df
  }

  def isJSONString(jsonString: String): Boolean = {

    try {
      val json = new JSONObject(jsonString)
    } catch {
      case e: Exception => {
        return false
      }
    }
    return true
  }

  def manOf[T: Manifest](t: T): Manifest[T] = manifest[T]

  def dataFrameToKafka(spark: SparkSession,
                       df: DataFrame,
                       valueField: String,
                       topic: String,
                       kafkaBroker: String,
                       schemaRegistryUrl: String,
                       valueSchemaVersion: Option[Int] = None,
                       valueSubjectNamingStrategy: String = "TopicNameStrategy" /*other options are RecordNameStrategy, TopicRecordNameStrategy*/ ,
                       valueSubjectRecordName: Option[String] = None,
                       valueSubjectRecordNamespace: Option[String] = None,
                       keyField: Option[String] = None,
                       keySchemaVersion: Option[Int] = None,
                       keySubjectNamingStrategy: String = "TopicNameStrategy" /*other options are RecordNameStrategy, TopicRecordNameStrategy*/ ,
                       keySubjectRecordName: Option[String] = None,
                       keySubjectRecordNamespace: Option[String] = None,
                       headerField: Option[String] = None,
                       keyStorePath: Option[String] = None,
                       trustStorePath: Option[String] = None,
                       keyStorePassword: Option[String] = None,
                       trustStorePassword: Option[String] = None,
                       keyPassword: Option[String] = None
                      ): Unit = {

    var dfavro = spark.emptyDataFrame
    var columnsToSelect = Seq(to_avro(df.col(valueField), helper.GetToAvroConfig(topic = topic, schemaRegistryUrl = schemaRegistryUrl, dfColumn = df.col(valueField), schemaVersion = valueSchemaVersion, isKey = false, subjectNamingStrategy = valueSubjectNamingStrategy, subjectRecordName = valueSubjectRecordName, subjectRecordNamespace = valueSubjectRecordNamespace)) as 'value)
    if (!keyField.isEmpty) {
      val keyFieldCol = df.col(keyField.get)
      columnsToSelect = columnsToSelect ++ Seq(to_avro(keyFieldCol, helper.GetToAvroConfig(topic = topic, schemaRegistryUrl = schemaRegistryUrl, dfColumn = keyFieldCol, schemaVersion = keySchemaVersion, isKey = true, subjectNamingStrategy = keySubjectNamingStrategy, subjectRecordName = keySubjectRecordName, subjectRecordNamespace = keySubjectRecordNamespace)) as 'key)
    }
    if (!headerField.isEmpty) {
      columnsToSelect = columnsToSelect ++ Seq(df.col(headerField.get) as 'header)
    }

    var options = helper.buildSecureKafkaProperties(keyStorePath = keyStorePath, trustStorePath = trustStorePath, keyStorePassword = keyStorePassword, trustStorePassword = trustStorePassword, keyPassword = keyPassword)

    dfavro = df.select(columnsToSelect: _*)
    dfavro.printSchema()
    dfavro.write
      .option("kafka.bootstrap.servers", kafkaBroker)
      .option("topic", topic)
      .options(options)
      .option("includeHeaders", (!headerField.isEmpty).toString)
      .format("kafka")
      .save()

  }

  def rdbmsToDataFrame(platform: String, awsEnv: String, server: String, database: String, table: String, login: String, password: String, sparkSession: org.apache.spark.sql.SparkSession, primarykey: String, lowerbound: String, upperbound: String, numofpartitions: String, vaultEnv: String, secretStore: String, sslEnabled: String, port: String, addlJdbcOptions: JSONObject, isWindowsAuthenticated: Boolean, domainName: String): org.apache.spark.sql.DataFrame = {

    var driver: String = null
    var url: String = null
    val helper = new Helper(appConfig)
    if (isWindowsAuthenticated) {
      if (platform == "mssql") {
        driver = "net.sourceforge.jtds.jdbc.Driver"
        url = "jdbc:jtds:sqlserver://" + server + ":" + (if (port == null) "1433" else port) + "/" + database + ";domain= " + domainName + ";useNTLMv2=true"
      }
      else if (platform == "teradata") {
        driver = "com.teradata.jdbc.TeraDriver"

        url = helper.buildTeradataURI(server, database, if (port == null) None else Some(port.toInt),isWindowsAuthenticated)
      }
    } else {
      if (platform == "mssql") {
        driver = "com.microsoft.sqlserver.jdbc.SQLServerDriver"
        url = "jdbc:sqlserver://" + server + ":" + (if (port == null) "1433" else port) + ";database=" + database
      }
      else if (platform == "oracle") {
        driver = "oracle.jdbc.driver.OracleDriver"
        url = "jdbc:oracle:thin:@//" + server + ":" + (if (port == null) "1521" else port) + "/" + database
      }
      else if (platform == "teradata") {
        driver = "com.teradata.jdbc.TeraDriver"

        url = helper.buildTeradataURI(server, database, if (port == null) None else Some(port.toInt),isWindowsAuthenticated)

      }

      else if (platform == "mysql") {
        driver = "com.mysql.jdbc.Driver"
        url = "jdbc:mysql://" + server + ":" + (if (port == null) "3306" else port) + "/" + database + "?rewriteBatchedStatements=true&cachePrepStmts=true"
      }

      else if (platform == "postgres") {
        driver = "org.postgresql.Driver"
        url = "jdbc:postgresql://" + server + ":" + (if (port == null) "5432" else port) + "/" + database + (if (sslEnabled == "true") "?sslmode=require" else "")
      }
    }
    val consul = new Consul(server, appConfig)
    var clusterName = server
    if (consul.IsConsulDNSName()) {
      clusterName = consul.serviceName
    }
    //if password isn't set, attempt to get from security.Vault
    var vaultPassword = password
    var vaultLogin = login
    if (vaultPassword == "") {
      val secretService = new SecretService(secretStore, appConfig)
      val vaultCreds = secretService.getSecret(awsEnv, clusterName, login, vaultEnv)
      vaultLogin = vaultCreds("username")
      vaultPassword = vaultCreds("password")
    }

    var jdbcOptions = Map("fetchsize" -> "72000")

    if (addlJdbcOptions != null) {
      jdbcOptions = jdbcOptions ++ jsonObjectPropertiesToMap(addlJdbcOptions)
    }

    //logic
    if (primarykey.isEmpty()) {
      jdbcOptions = jdbcOptions ++ Map("url" -> url,
        "user" -> vaultLogin,
        "password" -> vaultPassword,
        "driver" -> driver,
        "dbtable" -> table)

      val df = sparkSession.read.format("jdbc").options(jdbcOptions).load()
      df
    }
    else {

      val connectionProperties = new java.util.Properties()
      connectionProperties.setProperty("user", vaultLogin)
      connectionProperties.setProperty("password", vaultPassword)
      connectionProperties.setProperty("driver", driver)


      val df = sparkSession.read.options(jdbcOptions).jdbc(url = url,

        table = table,
        columnName = primarykey,
        lowerBound = lowerbound.toLong,
        upperBound = upperbound.toLong,
        numPartitions = numofpartitions.toInt,
        connectionProperties = connectionProperties)

      df
    }
  }

  def dataFrameToRdbms(platform: String, awsEnv: String, server: String, database: String, table: String, login: String, password: String, df: org.apache.spark.sql.DataFrame, vaultEnv: String, secretStore: String, sslEnabled: String, port: String, addlJdbcOptions: JSONObject, savemode: String, isWindowsAuthenticated: Boolean, domainName: String): Unit = {
    var driver: String = null
    var url: String = null
    var dflocal = df
    if (isWindowsAuthenticated) {
      if (platform == "mssql") {
        driver = "net.sourceforge.jtds.jdbc.Driver"
        url = "jdbc:jtds:sqlserver://" + server + ":" + (if (port == null) "1433" else port) + "/" + database + ";domain= " + domainName + ";useNTLMv2=true"
      }
    } else {
      if (platform == "mssql") {
        driver = "com.microsoft.sqlserver.jdbc.SQLServerDriver"
        url = "jdbc:sqlserver://" + server + ":" + (if (port == null) "1433" else port) + ";database=" + database
      }
      else if (platform == "oracle") {
        driver = "oracle.jdbc.driver.OracleDriver"
        url = "jdbc:oracle:thin:@//" + server + ":" + (if (port == null) "1521" else port) + "/" + database
      }
      else if (platform == "teradata") {
        driver = "com.teradata.jdbc.TeraDriver"

        val helper = new Helper(appConfig)
        url = helper.buildTeradataURI(server, database, if (port == null) None else Some(port.toInt),isWindowsAuthenticated)
        dflocal = dflocal.coalesce(1) //to prevent locking, by ensuring only there is one writer per table
      }

      else if (platform == "mysql") {
        driver = "com.mysql.jdbc.Driver"
        url = "jdbc:mysql://" + server + ":" + (if (port == null) "3306" else port) + "/" + database + "?rewriteBatchedStatements=true&cachePrepStmts=true"
      }

      else if (platform == "postgres") {
        driver = "org.postgresql.Driver"
        url = "jdbc:postgresql://" + server + ":" + (if (port == null) "5432" else port) + "/" + database + (if (sslEnabled == "true") "?sslmode=require" else "")
      }
    }

    val consul = new Consul(server, appConfig)
    var clusterName = server
    if (consul.IsConsulDNSName()) {
      clusterName = consul.serviceName
    }
    //if password isn't set, attempt to get from security.Vault
    var vaultPassword = password
    var vaultLogin = login
    if (vaultPassword == "") {
      val secretService = new SecretService(secretStore, appConfig)
      val vaultCreds = secretService.getSecret(awsEnv, clusterName, login, vaultEnv)
      vaultLogin = vaultCreds("username")
      vaultPassword = vaultCreds("password")
    }

    import scala.collection.mutable.Map
    var jdbcOptions = Map.empty[String, String]

    if (platform == "postgres") {
      jdbcOptions = jdbcOptions ++ Map("stringtype" -> "unspecified")
    }

    if (addlJdbcOptions != null) {
      jdbcOptions = jdbcOptions ++ jsonObjectPropertiesToMap(addlJdbcOptions)
    }

    val connectionProperties = new java.util.Properties()
    connectionProperties.setProperty("user", vaultLogin)
    connectionProperties.setProperty("password", vaultPassword)
    connectionProperties.setProperty("driver", driver)

    dflocal.write.mode(savemode).options(jdbcOptions).jdbc(url, table, connectionProperties)
  }

  def hiveToDataFrame(cluster: String, sparkSession: org.apache.spark.sql.SparkSession, dbtable: String, username: String, fetchsize: String): org.apache.spark.sql.DataFrame = {

    import org.apache.spark.sql.jdbc.JdbcDialect

    val HiveDialect = new JdbcDialect {
      override def canHandle(url: String): Boolean = url.startsWith("jdbc:hive2") || url.contains("hive2")

      override def quoteIdentifier(colName: String): String = {
        s"$colName"
      }

    }

    JdbcDialects.registerDialect(HiveDialect)

    val jdbcDF = sparkSession.read
      .format("jdbc")
      .option("url", cluster)
      .option("dbtable", dbtable)
      .option("username", username)
      // .option("fetchsize", fetchsize.toInt)
      .load()

    jdbcDF
  }

  def dataFrameToHive(table: String, saveMode: String, df: org.apache.spark.sql.DataFrame): Unit = {

    df.write
      .mode(SaveMode.valueOf(saveMode))
      .insertInto(table)
  }

  def rdbmsRunCommand(platform: String, awsEnv: String, server: String, port: String, sslEnabled: String, database: String, sql_command: String, login: String, password: String, vaultEnv: String, secretStore: String, isWindowsAuthenticated: Boolean, domainName: String): Unit = {
    if (sql_command != "") {

      var driver: String = null;
      var url: String = null;
      if (isWindowsAuthenticated) {
        if (platform == "mssql") {
          driver = "net.sourceforge.jtds.jdbc.Driver"
          url = "jdbc:jtds:sqlserver://" + server + ":" + (if (port == null) "1433" else port) + "/" + database + ";domain= " + domainName + ";useNTLMv2=true"
        }
      } else {
        if (platform == "mssql") {
          driver = "com.microsoft.sqlserver.jdbc.SQLServerDriver"
          url = "jdbc:sqlserver://" + server + ":" + (if (port == null) "1433" else port) + ";database=" + database
        }
        else if (platform == "oracle") {
          driver = "oracle.jdbc.driver.OracleDriver"
          url = "jdbc:oracle:thin:@//" + server + ":" + (if (port == null) "1521" else port) + "/" + database
        }
        else if (platform == "teradata") {
          driver = "com.teradata.jdbc.TeraDriver"

          val helper = new Helper(appConfig)
          url = helper.buildTeradataURI(server, database, if (port == null) None else Some(port.toInt),isWindowsAuthenticated )

        }

        else if (platform == "mysql") {
          driver = "com.mysql.jdbc.Driver"
          url = "jdbc:mysql://" + server + ":" + (if (port == null) "3306" else port) + "/" + database + "?rewriteBatchedStatements=true&cachePrepStmts=true"
        }

        else if (platform == "postgres") {
          driver = "org.postgresql.Driver"
          url = "jdbc:postgresql://" + server + ":" + (if (port == null) "5432" else port) + "/" + database + (if (sslEnabled == "true") "?sslmode=require" else "")
        }
      }

      val consul = new Consul(server, appConfig)
      var clusterName = server
      if (consul.IsConsulDNSName()) {
        clusterName = consul.serviceName
      }
      //if password isn't set, attempt to get from Vault
      var vaultPassword = password
      var vaultLogin = login
      if (vaultPassword == "") {
        val secretService = new SecretService(secretStore, appConfig)
        val vaultCreds = secretService.getSecret(awsEnv, clusterName, login, vaultEnv)
        vaultLogin = vaultCreds("username")
        vaultPassword = vaultCreds("password")
      }
      //logic
      var connection: Connection = null

      try {
        // make the connection
        Class.forName(driver)
        connection = DriverManager.getConnection(url, vaultLogin, vaultPassword)

        // create the statement, and run the command
        val statement = connection.createStatement()
        val isSuccess = statement.execute(sql_command)
      } catch {
        case e: Throwable => e.printStackTrace
          throw (e)
      } finally {
        if (connection != null) {
          if (!connection.isClosed()) {
            connection.close()
          }
        }
      }
    }
  }

  def dataFrameToCloudWatch(groupName: String, streamName: String, region: String, accessKey: String, secretKey: String, timeStampColumn: String, timestampFormat: String, df: org.apache.spark.sql.DataFrame, sparkSession: SparkSession): Unit = {
    if (groupName == null || groupName.trim.isEmpty ||
      streamName == null || streamName.trim.isEmpty)
      return;
    val awsLogsClient = appConfig.getCloudWatchClient(region);
    val calendar = Calendar.getInstance
    val logStreamsRequest = new DescribeLogStreamsRequest().withLogGroupName(groupName).withLimit(5)
    val logStreamList = awsLogsClient.describeLogStreams(logStreamsRequest).getLogStreams
    val retriveTimeStamp = if (timeStampColumn == null || timeStampColumn.isEmpty) false else true;
    val dateFormat = new SimpleDateFormat(timestampFormat)
    val rows = df.collect();
    var token: String = null;
    rows.foreach(x => {
      val rowRDD: RDD[Row] = sparkSession.sparkContext.makeRDD(x :: Nil)
      val df3 = sparkSession.sqlContext.createDataFrame(rowRDD, x.schema)
      val json = df3.toJSON.first
      val timeStamps = if (retriveTimeStamp) new Timestamp(dateFormat.parse(x.getString(x.fieldIndex(timeStampColumn))).getTime) else new Timestamp(System.currentTimeMillis());
      val log = new InputLogEvent
      log.setMessage(json)
      log.setTimestamp(timeStamps.getTime)
      if (token == null) {
        for (logStream <- logStreamList) {
          if (logStream.getLogStreamName.equals(streamName)) {
            token = logStream.getUploadSequenceToken
          }
        }
      }

      val putLogEventsRequest = new PutLogEventsRequest()
      if (token != null) {
        putLogEventsRequest.setSequenceToken(token)
      }
      putLogEventsRequest.setLogGroupName(groupName)
      putLogEventsRequest.setLogStreamName(streamName)
      putLogEventsRequest.setLogEvents(Seq(log))
      val putLogEventsResult = awsLogsClient.putLogEvents(putLogEventsRequest)
      token = putLogEventsResult.getNextSequenceToken
    })
  }

  def snowflakeToDataFrame(
                            sfUrl: String,
                            sfUser: String,
                            sfPassword: String,
                            sfDatabase: String,
                            sfSchema: String,
                            tableOrQuery: String,
                            options: JSONObject,
                            awsEnv: String,
                            vaultEnv: String,
                            secretStore: String,
                            sparkSession: org.apache.spark.sql.SparkSession): org.apache.spark.sql.DataFrame = {
    //if password isn't set, attempt to get from security.Vault
    var vaultPassword = sfPassword
    var vaultLogin = sfUser
    if (vaultPassword == "") {
      val clusterName = sfUrl + "/" + sfDatabase + "/" + sfSchema
      val secretService = new SecretService(secretStore, appConfig)
      val vaultCreds = secretService.getSecret(awsEnv, clusterName, vaultLogin, vaultEnv)
      vaultLogin = vaultCreds("username")
      vaultPassword = vaultCreds("password")
    }

    var sfOptions = Map(
      "sfUrl" -> sfUrl,
      "sfUser" -> vaultLogin,
      "sfPassword" -> vaultPassword,
      "sfDatabase" -> sfDatabase,
      "sfSchema" -> sfSchema
    )

    if (options != null) {
      sfOptions = sfOptions ++ jsonObjectPropertiesToMap(options)
    }

    val df = sparkSession
      .read
      .format(SNOWFLAKE_SOURCE_NAME)
      .options(sfOptions)
      .option("query", "SELECT * FROM " + tableOrQuery)
      .load()

    df
  }

  def dataFrameToSnowflake(
                            sfUrl: String,
                            sfUser: String,
                            sfPassword: String,
                            sfDatabase: String,
                            sfSchema: String,
                            table: String,
                            saveMode: String,
                            options: JSONObject,
                            awsEnv: String,
                            vaultEnv: String,
                            secretStore: String,
                            df: org.apache.spark.sql.DataFrame,
                            sparkSession: org.apache.spark.sql.SparkSession
                          ): Unit = {
    //if password isn't set, attempt to get from security.Vault
    var vaultPassword = sfPassword
    var vaultLogin = sfUser
    if (vaultPassword == "") {
      val clusterName = sfUrl + "/" + sfDatabase + "/" + sfSchema
      val secretService = new SecretService(secretStore, appConfig)
      val vaultCreds = secretService.getSecret(awsEnv, clusterName, vaultLogin, vaultEnv)
      vaultLogin = vaultCreds("username")
      vaultPassword = vaultCreds("password")
    }

    var sfOptions = Map(
      "sfUrl" -> sfUrl,
      "sfUser" -> vaultLogin,
      "sfPassword" -> vaultPassword,
      "sfDatabase" -> sfDatabase,
      "sfSchema" -> sfSchema
    )

    if (options != null) {
      sfOptions = sfOptions ++ jsonObjectPropertiesToMap(options)
    }

    df.write
      .format(SNOWFLAKE_SOURCE_NAME)
      .options(sfOptions)
      .option("dbtable", table)
      .mode(SaveMode.valueOf(saveMode))
      .save()

  }
}

