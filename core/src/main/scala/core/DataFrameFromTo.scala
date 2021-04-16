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
import java.util.{Calendar, UUID}
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
import com.mongodb.{MongoClient, MongoClientURI}
import config.AppConfig
import core.DataPull.jsonObjectPropertiesToMap
import helper._
import javax.mail.internet.{InternetAddress, MimeMessage}
import javax.mail.{Message, Session, Transport}
import net.snowflake.spark.snowflake.Utils.SNOWFLAKE_SOURCE_NAME
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FSDataOutputStream, FileSystem, Path}
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
import za.co.absa.abris.avro.functions.{from_avro, to_avro}
import scala.collection.JavaConversions._
import scala.collection.immutable.List
import scala.collection.mutable.{ArrayBuffer, ListBuffer, StringBuilder}

class DataFrameFromTo(appConfig: AppConfig, pipeline: String) extends Serializable {
  val helper = new Helper(appConfig)

  def fileToDataFrame(filePath: String, fileFormat: String, delimiter: String, charset: String, mergeSchema: Boolean = false, sparkSession: org.apache.spark.sql.SparkSession, isS3: Boolean = false, secretstore: String, isSFTP: Boolean = false, login: String, host: String, password: String, pemFilePath: String, awsEnv: String, vaultEnv: String, isStream: Boolean = false, addlSparkOptions: Option[JSONObject] = None): org.apache.spark.sql.DataFrame = {

    if (filePath == null && fileFormat == null && delimiter == null && charset == null && sparkSession == null && login == null && host == null && password == null) {
      throw new Exception("Platform cannot have null values")
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

    var sparkOptions: Map[String, String] = Map.empty[String, String]

    //add options by type
    if (isSFTP) {
      sparkOptions = Map(
        "host" -> host,
        "username" -> vaultLogin,
        (if (pemFilePath == "") "password" else "pem") -> (if (pemFilePath == "") vaultPassword else pemFilePath),
        "fileType" -> fileFormat
      )
    }
    if (fileFormat == "csv") {
      sparkOptions = sparkOptions ++ Map(
        "delimiter" -> delimiter,
        "mode" -> "DROPMALFORMED",
        "header" -> "true",
        "charset" -> charset
      )
    } else if (fileFormat == "parquet") {
      sparkOptions = sparkOptions ++ Map(
        "mergeSchema" -> mergeSchema.toString.toLowerCase
      )
    }

    if (!addlSparkOptions.isEmpty) {
      sparkOptions = sparkOptions ++ jsonObjectPropertiesToMap(addlSparkOptions.get)
    }

    if (isSFTP) {
      createOrReplaceTempViewOnDF(sparkSession.read
        .format("com.springml.spark.sftp")
        .options(sparkOptions)
        .load(filePath))
    }
    else {
      if (isStream) {
        createOrReplaceTempViewOnDF(
          sparkSession.readStream
            .schema(fileToDataFrame(
              filePath = filePath,
              fileFormat = fileFormat,
              delimiter = delimiter,
              charset = charset,
              mergeSchema = mergeSchema,
              sparkSession = sparkSession,
              isS3 = isS3,
              secretstore = secretstore,
              login = login,
              host = host,
              password = password,
              pemFilePath = pemFilePath,
              awsEnv = awsEnv,
              vaultEnv = vaultEnv,
              isStream = false
            ).schema)
            .format(fileFormat)
            .options(sparkOptions)
            .load(s"$filePrefix$filePath")
        )
      }
      else {
        createOrReplaceTempViewOnDF(
          sparkSession
            .read
            .format(fileFormat)
            .options(sparkOptions)
            .load(s"$filePrefix$filePath")
        )
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
      val hadoopConf = sparkSession.sparkContext.hadoopConfiguration
      hadoopConf.set("fs." + s3Prefix + ".fast.upload", "true")
      hadoopConf.set("fs." + s3Prefix + ".canned.acl", "BucketOwnerFullControl")
      hadoopConf.set("fs." + s3Prefix + ".acl.default", "BucketOwnerFullControl")
    }

    if (isSFTP) {

      df.write.
        format("com.springml.spark.sftp").
        option("host", host).
        option("username", login).
        option(if (pemFilePath == "") "password" else "pem", if (pemFilePath == "") password else pemFilePath).
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
      "org.elasticsearch.hadoop.rest.commonshttp" -> "TRACE",
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


    val uri = new MongoClientURI(helper.buildMongoURI(vaultLogin, vaultPassword, cluster, null, authenticationDatabase, database, collection, authenticationEnabled, sslEnabled))
    val mongoClient = new MongoClient(uri)
    val data = mongoClient.getDatabase(database)

    val response = data.runCommand(org.bson.Document.parse(runCommand))
  }

  def kafkaToDataFrame(spark: SparkSession,
                       kafkaBroker: String,
                       topic: String,
                       schemaRegistryUrl: String,
                       valueSchemaVersion: Option[Int] = None,
                       valueSubjectNamingStrategy: String = "TopicNameStrategy" /*other options are RecordNameStrategy, TopicRecordNameStrategy*/ ,
                       valueSubjectRecordName: Option[String] = None,
                       valueSubjectRecordNamespace: Option[String] = None,
                       keySchemaVersion: Option[Int] = None,
                       keySubjectNamingStrategy: String = "TopicNameStrategy" /*other options are RecordNameStrategy, TopicRecordNameStrategy*/ ,
                       keySubjectRecordName: Option[String] = None,
                       keySubjectRecordNamespace: Option[String] = None,
                       keyStorePath: Option[String] = None,
                       trustStorePath: Option[String] = None,
                       keyStorePassword: Option[String] = None,
                       trustStorePassword: Option[String] = None,
                       keyPassword: Option[String] = None,
                       keyFormat: String = "string",
                       valueFormat: String = "avro",
                       addlSparkOptions: Option[JSONObject] = None,
                       isStream: Boolean = false): DataFrame = {
    var sparkOptions: Map[String, String] = helper.buildSecureKafkaProperties(keyStorePath = keyStorePath, trustStorePath = trustStorePath, keyStorePassword = keyStorePassword, trustStorePassword = trustStorePassword, keyPassword = keyPassword)

    sparkOptions = sparkOptions ++ Map("kafka.bootstrap.servers" -> kafkaBroker, "subscribe" -> topic, "schema.registry.url" -> schemaRegistryUrl, "max.poll.records" -> "500", "session.timeout.ms" -> "120000")

    if (!addlSparkOptions.isEmpty) {
      sparkOptions = sparkOptions ++ jsonObjectPropertiesToMap(addlSparkOptions.get)
    }
    var df = spark.emptyDataFrame
    if (isStream) {
      df = spark
        .readStream
        .format("kafka")
        .options(sparkOptions)
        .load()
    } else {
      df = spark
        .read
        .format("kafka")
        .options(sparkOptions)
        .load()
    }
    df.createOrReplaceTempView("df")
    val fromValueAvroConfig = helper.GetFromAvroConfig(
      topic = topic,
      schemaRegistryUrl = schemaRegistryUrl,
      schemaVersion = valueSchemaVersion,
      isKey = false,
      subjectNamingStrategy = valueSubjectNamingStrategy,
      subjectRecordName = valueSubjectRecordName,
      subjectRecordNamespace = valueSubjectRecordNamespace,
      sslSettings = sparkOptions
    )
    val fromKeyAvroConfig = helper.GetFromAvroConfig(
      topic = topic,
      schemaRegistryUrl = schemaRegistryUrl,
      schemaVersion = keySchemaVersion,
      isKey = true,
      subjectNamingStrategy = keySubjectNamingStrategy,
      subjectRecordName = keySubjectRecordName,
      subjectRecordNamespace = keySubjectRecordNamespace,
      sslSettings = sparkOptions
    )

    var dft = df
      .withColumnRenamed("key", "keyBinary")
      .withColumnRenamed("value", "valueBinary")
    dft = dft
      .withColumn("key", keyFormat match {
        case "avro" => from_avro(dft.col("keyBinary"), fromKeyAvroConfig)
        case _ => dft.col("keyBinary").cast("String")
      })
      .withColumn("value", valueFormat match {
        case "avro" => from_avro(dft.col("valueBinary"), fromValueAvroConfig)
        case _ => dft.col("valueBinary").cast("String")
      })
    dft = dft
      .drop("keyBinary")
      .drop("valueBinary")
    dft.printSchema()
    dft
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
                       keyPassword: Option[String] = None,
                       keyFormat: String,
                       valueFormat: String,
                       isStream: Boolean = false,
                       addlSparkOptions: Option[JSONObject] = None
                      ): Unit = {

    var dfavro = spark.emptyDataFrame

    var sparkOptions: Map[String, String] = helper.buildSecureKafkaProperties(keyStorePath = keyStorePath, trustStorePath = trustStorePath, keyStorePassword = keyStorePassword, trustStorePassword = trustStorePassword, keyPassword = keyPassword)

    sparkOptions = sparkOptions ++ Map("kafka.bootstrap.servers" -> kafkaBroker, "topic" -> topic, "includeHeaders" -> (!headerField.isEmpty).toString)

    if (!addlSparkOptions.isEmpty) {
      sparkOptions = sparkOptions ++ jsonObjectPropertiesToMap(addlSparkOptions.get)
    }

    val valueFieldCol = df.col(valueField)
    val valueAvroConfig = helper.GetToAvroConfig(topic = topic, schemaRegistryUrl = schemaRegistryUrl, dfColumn = valueFieldCol, schemaVersion = valueSchemaVersion, isKey = false, subjectNamingStrategy = valueSubjectNamingStrategy, subjectRecordName = valueSubjectRecordName, subjectRecordNamespace = valueSubjectRecordNamespace, sslSettings = sparkOptions)
    var columnsToSelect = Seq((valueFormat match {
      case "avro" => to_avro(valueFieldCol, valueAvroConfig)
      case _ => valueFieldCol
    }) as 'value)
    if (!keyField.isEmpty) {
      val keyFieldCol = df.col(keyField.get)
      val keyAvroConfig = helper.GetToAvroConfig(topic = topic, schemaRegistryUrl = schemaRegistryUrl, dfColumn = keyFieldCol, schemaVersion = keySchemaVersion, isKey = true, subjectNamingStrategy = keySubjectNamingStrategy, subjectRecordName = keySubjectRecordName, subjectRecordNamespace = keySubjectRecordNamespace, sslSettings = sparkOptions)
      columnsToSelect = columnsToSelect ++ Seq((keyFormat match {
        case "avro" => to_avro(keyFieldCol, keyAvroConfig)
        case _ => keyFieldCol
      }) as 'key)
    }
    if (!headerField.isEmpty) {
      columnsToSelect = columnsToSelect ++ Seq(df.col(headerField.get) as 'header)
    }

    dfavro = df.select(columnsToSelect: _*)
    dfavro.printSchema()
    if (isStream) {
      dfavro.writeStream
        .options(sparkOptions)
        .format("kafka")
        .start()
    }
    else {
      dfavro.write
        .options(sparkOptions)
        .format("kafka")
        .save()
    }
  }

  def rdbmsToDataFrame(platform: String, awsEnv: String, server: String, database: String, table: String, login: String, password: String, sparkSession: org.apache.spark.sql.SparkSession, primarykey: String, lowerbound: String, upperbound: String, numofpartitions: String, vaultEnv: String, secretStore: String, sslEnabled: Boolean, port: String, addlJdbcOptions: JSONObject, isWindowsAuthenticated: Boolean, domainName: String, typeForTeradata: Option[String]): org.apache.spark.sql.DataFrame = {
    val configMap = helper.buildRdbmsURI(platform, server, port, database, isWindowsAuthenticated, domainName, typeForTeradata, sslEnabled)
    val driver: String = configMap("driver")
    val url: String = configMap("url")

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

  def dataFrameToRdbms(platform: String, awsEnv: String, server: String, database: String, table: String, login: String, password: String, df: org.apache.spark.sql.DataFrame, vaultEnv: String, secretStore: String, sslEnabled: Boolean = false, port: String, addlJdbcOptions: JSONObject, savemode: String, isWindowsAuthenticated: Boolean, domainName: String, typeForTeradata: Option[String] = None): Unit = {
    val configMap = helper.buildRdbmsURI(platform, server, port, database, isWindowsAuthenticated, domainName, typeForTeradata, sslEnabled)
    val driver: String = configMap("driver")
    val url: String = configMap("url")

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
    var df_temp = df
    if (platform == "teradata") {
      df_temp = df.coalesce(1)
    }
    df_temp.write.mode(savemode).options(jdbcOptions).jdbc(url, table, connectionProperties)
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

  def rdbmsRunCommand(platform: String, awsEnv: String, server: String, port: String, sslEnabled: Boolean, database: String, sql_command: String, login: String, password: String, vaultEnv: String, secretStore: String, isWindowsAuthenticated: Boolean, domainName: String, typeForTeradata: Option[String]): Unit = {
    if (sql_command != "") {

      val configMap = helper.buildRdbmsURI(platform, server, port, database, isWindowsAuthenticated, domainName, typeForTeradata, sslEnabled)
      val driver: String = configMap("driver")
      val url: String = configMap("url")

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

