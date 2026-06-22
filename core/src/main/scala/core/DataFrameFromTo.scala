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
import java.sql._
import java.text.SimpleDateFormat
import java.time.Instant
import java.util
import java.util.{Calendar, UUID}

import com.amazonaws.services.logs.model.{DescribeLogStreamsRequest, InputLogEvent, PutLogEventsRequest}
import com.amazonaws.services.s3.model._
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.datastax.spark.connector.cql.CassandraConnector
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.apache.spark.sql.functions.{col, monotonically_increasing_id}
import org.apache.spark.sql.types.IntegerType
import org.apache.spark.sql.Encoders

import scala.collection.mutable
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
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.{DataFrame, Row, SaveMode, SparkSession}
import org.bson.Document
import org.codehaus.jettison.json.JSONObject
import org.elasticsearch.spark.sql._
import org.influxdb.InfluxDBFactory
import org.influxdb.dto.Query
import security._
import za.co.absa.abris.avro.functions.{from_avro, to_avro}
import za.co.absa.abris.config.FromAvroConfig

import scala.collection.JavaConversions._
import scala.collection.mutable.{ArrayBuffer, ListBuffer, StringBuilder}

class DataFrameFromTo(appConfig: AppConfig, pipeline: String) extends Serializable {
  val helper = new Helper(appConfig)

  def fileToDataFrame(filePath: String, fileFormat: String, delimiter: String, charset: String, mergeSchema: Boolean = false, sparkSession: org.apache.spark.sql.SparkSession, isS3: Boolean = false, secretstore: String, isSFTP: Boolean = false, login: String, host: String, password: String, pemFilePath: String, awsEnv: String, vaultEnv: String, isStream: Boolean = false, addlSparkOptions: Option[JSONObject] = None, filePrefix: Option[String] = None, schema: Option[StructType] = None): org.apache.spark.sql.DataFrame = {

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

    var filePrefixString = filePrefix.getOrElse("")

    if (isS3) {
      val s3Prefix = if (sparkSession.sparkContext.master == "local[*]") "s3a" else "s3"
      filePrefixString = filePrefix.getOrElse(s3Prefix + "://")
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
    if (fileFormat.equalsIgnoreCase("csv")) {
      sparkOptions = sparkOptions ++ Map(
        "delimiter" -> delimiter,
        "mode" -> "DROPMALFORMED",
        "header" -> "true",
        "charset" -> charset
      )
    } else if (fileFormat.equalsIgnoreCase("parquet")) {
      sparkOptions = sparkOptions ++ Map(
        "mergeSchema" -> mergeSchema.toString.toLowerCase
      )
    }

    if (!addlSparkOptions.isEmpty) {
      sparkOptions = sparkOptions ++ jsonObjectPropertiesToMap(addlSparkOptions.get)
    }

    if (isSFTP) {
      val localTmpDir = System.getProperty("java.io.tmpdir") + "/datapull-sftp-" + UUID.randomUUID()
      new java.io.File(localTmpDir).mkdirs()
      val port = sparkOptions.getOrElse("port", "22").toInt
      // SFTPClient(identity, passPhrase, username, password, host, port, runCrypto, secretKey)
      val sftpClient = if (pemFilePath != "")
        new com.springml.sftp.client.SFTPClient(pemFilePath, null, vaultLogin, null, host, port, false, null)
      else
        new com.springml.sftp.client.SFTPClient(null, null, vaultLogin, vaultPassword, host, port, false, null)
      val localFile = sftpClient.copy(filePath, localTmpDir)
      createOrReplaceTempViewOnDF(sparkSession.read.options(sparkOptions - "host" - "username" - "password" - "pem" - "fileType" - "port").format(fileFormat).load(localFile))
    }
    else {
      if (isStream) {
        createOrReplaceTempViewOnDF(
          sparkSession.readStream
            .schema(schema.getOrElse(fileToDataFrame(
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
              isStream = false,
              filePrefix = filePrefix
            ).schema))
            .format(fileFormat)
            .options(sparkOptions)
            .load(s"$filePrefixString$filePath")
        )
      }
      else {
        var dfReader = sparkSession
          .read
          .format(fileFormat)

        if (!schema.isEmpty) {
          dfReader = dfReader.schema(schema.get)
        }

        createOrReplaceTempViewOnDF(
          dfReader
            .options(sparkOptions)
            .load(s"$filePrefixString$filePath")
        )
      }
    }
  }


  /*
* Get ngram transformation
*/

  def dataFrameToEmail(to: String, subject: String, df: org.apache.spark.sql.DataFrame, limit: String, truncate: String): Unit = {
    if (df == null) {
      throw new Exception("Platform cannot have null values")
    }

    val bodyHtml = helper.showHTML(df, limit.toInt, truncate.toInt)
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

  private def hasEmptyOrNestedEmptySchema(schema: org.apache.spark.sql.types.StructType): Boolean = {
    if (schema.fields.isEmpty) return true
    schema.fields.exists {
      case org.apache.spark.sql.types.StructField(_, s: org.apache.spark.sql.types.StructType, _, _) => hasEmptyOrNestedEmptySchema(s)
      case _ => false
    }
  }

  def dataFrameToFile(filePath: String, fileFormat: String, groupByFields: String, s3SaveMode: String, df: org.apache.spark.sql.DataFrame, isS3: Boolean, secretstore: String, sparkSession: SparkSession, coalescefilecount: String, isSFTP: Boolean, login: String, host: String, password: String, pemFilePath: String, awsEnv: String, vaultEnv: String, rowFromJsonString: Boolean, filePrefix: Option[String] = None, addlSparkOptions: Option[JSONObject] = None): Unit = {

    if (filePath == null && fileFormat == null && groupByFields == null && s3SaveMode == null && login == null && SparkSession == null) {
      throw new Exception("Platform cannot have null values")
    }

    if (filePath.isEmpty() && fileFormat.isEmpty() && groupByFields.isEmpty() && s3SaveMode.isEmpty() && login.isEmpty() && sparkSession == null) {
      throw new Exception("Platform cannot have empty values")
    }

    // Spark 3.5 rejects writing a DataFrame with an empty or nested-empty schema.
    // Spark 2.x silently wrote an empty file. Guard here to preserve the old behaviour.
    if (hasEmptyOrNestedEmptySchema(df.schema)) {
      println(s"Warning: DataFrame has empty or nested-empty schema, skipping write to $filePath")
      return
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

    var dft = sparkSession.emptyDataFrame

    if (coalescefilecount == null) {
      if (fileFormat.equalsIgnoreCase("csv")) {
        dft = df.coalesce(1)
      }
      else {
        dft = df
      }
    }
    else {
      val coalsceFileCount_tmp = coalescefilecount.toInt
      if (coalsceFileCount_tmp < df.rdd.partitions.size)
        dft = df.coalesce(coalsceFileCount_tmp)
      else if (coalsceFileCount_tmp > df.rdd.partitions.size)
        dft = df.repartition(coalsceFileCount_tmp)
    }
    var filePrefixString = filePrefix.getOrElse("")
    if (isS3) {
      val s3Prefix = if (sparkSession.sparkContext.master == "local[*]") "s3a" else "s3"
      filePrefixString = filePrefix.getOrElse(s3Prefix + "://")
      sparkSession.conf.set("fs." + s3Prefix + ".connection.maximum", 100)
      val hadoopConf = sparkSession.sparkContext.hadoopConfiguration
      hadoopConf.set("fs." + s3Prefix + ".fast.upload", "true")
    }

    var sparkOptions: Map[String, String] = Map.empty[String, String]

    if (isSFTP) {
      val writeOptions = if (!addlSparkOptions.isEmpty) jsonObjectPropertiesToMap(addlSparkOptions.get) else Map.empty[String, String]
      val port = writeOptions.getOrElse("port", "22").toInt
      val localTmpDir = System.getProperty("java.io.tmpdir") + "/datapull-sftp-" + UUID.randomUUID()
      new java.io.File(localTmpDir).mkdirs()
      val localOutPath = localTmpDir + "/" + new java.io.File(filePath).getName
      df.write.format(fileFormat).options(writeOptions - "port").save(localOutPath)
      // SFTPClient(identity, passPhrase, username, password, host, port, runCrypto, secretKey)
      val sftpClient = if (pemFilePath != "")
        new com.springml.sftp.client.SFTPClient(pemFilePath, null, login, null, host, port, false, null)
      else
        new com.springml.sftp.client.SFTPClient(null, null, login, password, host, port, false, null)
      sftpClient.copyToFTP(localOutPath, filePath)

    } else if (rowFromJsonString) {

      df.foreachPartition((partition: Iterator[Row]) => {

        val partitionList = new util.ArrayList[String]()
        partition.foreach(Row =>
          partitionList.add(Row.apply(0).toString)
        )
        if (!partitionList.isEmpty) {
          val conf: Configuration = new Configuration
          val path_string = filePrefixString + filePath + "/" + UUID.randomUUID().toString + ".json"
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
      if (!filePath.startsWith("/datapull-opensource/logs/"))
        dft.printSchema()
      sparkOptions = sparkOptions ++ Map(
        "header" -> "true",
        "path" -> s"$filePrefixString$filePath"
      )
      if (!addlSparkOptions.isEmpty) {
        sparkOptions = sparkOptions ++ jsonObjectPropertiesToMap(addlSparkOptions.get)
      }
      if (dft.isStreaming) {
        var dfWriter = dft.writeStream
          .format(fileFormat)
          .options(sparkOptions)
        if (groupByFields != "") {
          dfWriter = dfWriter.partitionBy(groupByFieldsArray: _*)
        }

        dfWriter
          .start()
      } else {
        var dfWriter = dft.write
          .mode(SaveMode.valueOf(s3SaveMode))
          .format(fileFormat)
          .options(sparkOptions)
        if (groupByFields != "") {
          dfWriter = dfWriter.partitionBy(groupByFieldsArray: _*)
        }
        val normalizedFormat = fileFormat.toLowerCase
        if (normalizedFormat == "json" | normalizedFormat == "csv" | normalizedFormat == "avro"
          | normalizedFormat == "orc" | normalizedFormat == "parquet") {
          dfWriter.format(normalizedFormat).option("path",s"$filePrefixString$filePath").save()
        } else if (normalizedFormat == "sequencefile") {
          dft.toJSON.rdd.zipWithIndex.map { case (v, i) => (i, v) }.saveAsSequenceFile(s"$filePrefixString$filePath", Some(classOf[org.apache.hadoop.io.compress.DefaultCodec]))
        } else if (normalizedFormat == "sequencefilesnappy") {
          dft.toJSON.rdd.zipWithIndex.map { case (v, i) => (i, v) }.saveAsSequenceFile(s"$filePrefixString$filePath", Some(classOf[org.apache.hadoop.io.compress.SnappyCodec]))
        } else if (normalizedFormat == "sequencefiledeflate") {
          dft.toJSON.rdd.zipWithIndex.map { case (v, i) => (i, v) }.saveAsSequenceFile(s"$filePrefixString$filePath", Some(classOf[org.apache.hadoop.io.compress.DeflateCodec]))
        } else {
          throw new UnsupportedOperationException(s"Unsupported file format: $fileFormat")
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

    if (sparkOptions.getOrElse("spark.cassandra.connection.ssl.enabled", "false") == "true") {
      // Java 17 disables TLSv1/1.1 and enforces CA flag on trust anchors; set at runtime
      // to cover executor JVMs not patched via bootstrap
      java.security.Security.setProperty("jdk.tls.client.protocols", "TLSv1.2")
      java.security.Security.setProperty("jdk.security.allowNonCaAnchor", "true")
      if (!sparkOptions.contains("spark.cassandra.connection.ssl.enabledAlgorithms")) {
        sparkOptions = sparkOptions ++ Map("spark.cassandra.connection.ssl.enabledAlgorithms" ->
          "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256")
      }
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

    if (sparkOptions.getOrElse("spark.cassandra.connection.ssl.enabled", "false") == "true") {
      java.security.Security.setProperty("jdk.tls.client.protocols", "TLSv1.2")
      java.security.Security.setProperty("jdk.security.allowNonCaAnchor", "true")
      if (!sparkOptions.contains("spark.cassandra.connection.ssl.enabledAlgorithms")) {
        sparkOptions = sparkOptions ++ Map("spark.cassandra.connection.ssl.enabledAlgorithms" ->
          "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256")
      }
    }

    df.write
      .format("org.apache.spark.sql.cassandra")
      .mode(SaveMode.Append)
      .options(sparkOptions)
      .save()
  }


  def dataFrameToElastic(awsEnv: String, cluster: String, port: Int, index: String, nodetype: Option[String] = None, version: String, login: String, password: String, local_dc: String, addlESOptions: Option[JSONObject] = None, df: org.apache.spark.sql.DataFrame, vaultEnv: String, saveMode: String, mappingId: String, flag: String, secretStore: String, sparkSession: org.apache.spark.sql.SparkSession): Unit = {

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

    val esWritePort = if (port == 9300) 9200 else port
    var esOptions = Map("es.nodes" -> clusterNodes,
      "es.port" -> esWritePort.toString,
      "es.clustername" -> clusterName,
      "es.net.http.auth.user" -> vaultLogin,
      "es.net.http.auth.pass" -> vaultPassword,
      "es.write.operation" -> saveMode,
      "es.nodes.wan.only" -> "true",
      "es.resource" -> s"$index/${nodetype.getOrElse("")}",
      "es.internal.es.version" -> version)

    if (mappingId != null)
      esOptions = esOptions ++ Map("es.mapping.id" -> mappingId)

    if (!addlESOptions.isEmpty) {
      esOptions = esOptions ++ jsonObjectPropertiesToMap(addlESOptions.get)
    }

    if (flag == "false") {
      df.saveToEs(esOptions)
    }

  }

  def ElasticToDataframe(awsEnv: String, cluster: String, port: Int, index: String, nodetype: Option[String] = None, version: String, login: String, password: String, vaultEnv: String, secretStore: String, sparkSession: org.apache.spark.sql.SparkSession, addlESOptions: Option[JSONObject] = None): org.apache.spark.sql.DataFrame = {


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

    val esReadPort = if (port == 9300) 9200 else port
    var esOptions = Map("es.nodes" -> clusterNodes,
      "es.port" -> esReadPort.toString,
      "es.index.auto.create" -> "true",
      "es.nodes.wan.only" -> "true",
      "es.clustername" -> clusterName,
      "es.net.http.auth.user" -> vaultLogin,
      "es.net.http.auth.pass" -> vaultPassword,
      "es.internal.es.version" -> version,
      "es.resource" -> s"$index/${nodetype.getOrElse("")}"
    )

    if (!addlESOptions.isEmpty) {
      esOptions = esOptions ++ jsonObjectPropertiesToMap(addlESOptions.get)
    }

    val df = sparkSession
      .read.format("org.elasticsearch.spark.sql")
      .options(esOptions)
      .load(index + "/" + nodetype.getOrElse(""))

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
        case e: Exception =>
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

  def elasticRunCommand(awsEnv: String, cluster: String, port: Int, login: String, password: String, curlcommand: String, vaultEnv: String, secretStore: String): Unit = {

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
      val cmd_withcreds = curlcommand.replace("://", "://" + vaultLogin + ":" + vaultPassword + "@")

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
      var jsonResult = new JSONObject(result)
      if (jsonResult.has("error")) {
        throw new Exception(result)
      }
    } catch {
      case e: Throwable => e.printStackTrace
        throw (e)
    } finally {
      if (connection != null) {
        if (!connection.isClosed) {
          connection.close()
        }
      }
    }
  }

  def mongodbToDataFrame(awsEnv: String, cluster: String, overrideconnector: String, database: String, authenticationDatabase: String, collection: String, login: String, password: String, sparkSession: org.apache.spark.sql.SparkSession, vaultEnv: String, addlSparkOptions: JSONObject, secretStore: String, authenticationEnabled: String, tmpFileLocation: String, sampleSize: String, sslEnabled: String, replicaset: String = null, readPreference: String = null): org.apache.spark.sql.DataFrame = {
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
    // Backward-compat guard: replica-set topology mode is only needed when readPreference routing is
    // requested. Several existing pipelines carry a `replicaset` value that is a cluster/Consul name,
    // NOT a real replica-set name; honoring it unconditionally would force replica-set mode and break
    // their connection (setName mismatch). So only pass replicaSet into the URI when readPreference is
    // explicitly set. With no readpreference, behavior is identical to before (single-host mode).
    val effectiveReplicaSet = if (readPreference != null && !readPreference.isEmpty) replicaset else null
    uri = helper.buildMongoURI(vaultLogin, vaultPassword, cluster, effectiveReplicaSet, authenticationDatabase, database, collection, authenticationEnabled.toBoolean, sslEnabled, readPreference)
    if (overrideconnector.toBoolean) {
      // MongoDB Read using MongoDB Spark Connector
      val readConfig = Map(
          "spark.mongodb.read.connection.uri" -> uri,
          "database" -> database,
          "collection" -> collection
      )
      val dfMongo = sparkSession.read.format("mongodb").options(readConfig).load()

      // Convert each row to a whole-document JSON string (matching old behavior)
      import org.apache.spark.sql.functions.{struct, to_json}
      val dfAsJson = dfMongo.withColumn("jsonfield", to_json(struct(dfMongo.columns.map(col): _*)))
        .select("jsonfield")

      // Write to temporary JSON files with 20K-row partitioning
      val tmpLocation = if (tmpFileLocation.startsWith("s3://") || tmpFileLocation.startsWith("s3a://")) tmpFileLocation else "s3a://" + tmpFileLocation
      val partitionedDF = dfAsJson.withColumn("partition", (monotonically_increasing_id() / 20000).cast(IntegerType))

      partitionedDF.write
        .mode(SaveMode.Overwrite)
        .partitionBy("partition")
        .json(tmpLocation)

      // Read back the data
      val dfBig = sparkSession.read
        .json(tmpLocation)
        .select("jsonfield")

      dfBig

    } else {
      // Additional Spark MongoDB options
      var sparkOptions = Map(
        "spark.mongodb.read.connection.uri" -> uri,
        "database" -> database,
        "collection" -> collection,
        // mongo-spark-connector 10.x (Spark 3.x) changed schema inference: without sampling it
        // returns empty StructType for complex/nested fields. Force document sampling to match
        // the old connector behaviour and produce a proper schema.
        "spark.mongodb.read.inferSchema.sampleSize" -> "1000"
      )
      if (addlSparkOptions != null) {
        sparkOptions = sparkOptions ++ jsonObjectPropertiesToMap(addlSparkOptions)
      }
      if (sampleSize != null) {
        sparkOptions = sparkOptions ++ Map(
          "sampleSize" -> sampleSize,
          "spark.mongodb.input.sample.sampleSize" -> sampleSize,
          "spark.mongodb.read.inferSchema.sampleSize" -> sampleSize
        )
      }

      // Load data directly from MongoDB
      val df = sparkSession.read.format("mongodb").options(sparkOptions).load()
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

    var sparkOptions = Map("spark.mongodb.write.connection.uri" -> uri, "replaceDocument" -> replaceDocuments.toString, "ordered" -> ordered.toString)
    if (maxBatchSize != null)
      sparkOptions = sparkOptions ++ Map("maxBatchSize" -> maxBatchSize)

    if (addlSparkOptions != null) {
      sparkOptions = sparkOptions ++ jsonObjectPropertiesToMap(addlSparkOptions)
    }

    val writeConfig = sparkOptions

    if (documentfromjsonfield.toBoolean) {
      // Parse JSON strings from the specified field into a proper DataFrame with schema
      // The MongoDB Spark Connector v10.x expects a DataFrame with typed columns, not Kryo-encoded BSON
      val jsonRDD = df.select(jsonfield).rdd.map(row => row.getString(0))
      val parsedDF = sparkSession.read.json(sparkSession.createDataset(jsonRDD)(Encoders.STRING))

      // Write the parsed DataFrame to MongoDB
      parsedDF.write
        .format("mongodb")
        .options(writeConfig)
        .mode("append")
        .save()

    } else {
      // Save the DataFrame directly to MongoDB
      df.write
        .format("mongodb")
        .options(writeConfig)
        .mode("append")
        .save()
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


    import com.mongodb.client.{MongoClient, MongoClients, MongoDatabase}
    import org.bson.Document

    val uri = helper.buildMongoURI(vaultLogin, vaultPassword, cluster, null, authenticationDatabase, database, collection, authenticationEnabled, sslEnabled)
    val mongoClient: MongoClient = MongoClients.create(uri)
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
                       isStream: Boolean = false,
                       streamWatermarkField: String = "timestamp",
                       streamWatermarkDelay: Option[String] = None,
                       kafkaConnectMongodbOptions: Option[JSONObject] = None
                      ): DataFrame = {
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
    var fromValueAvroConfig: FromAvroConfig = null
    if (valueFormat == "avro") {
      fromValueAvroConfig = helper.GetFromAvroConfig(
        topic = topic,
        schemaRegistryUrl = schemaRegistryUrl,
        schemaVersion = valueSchemaVersion,
        isKey = false,
        subjectNamingStrategy = valueSubjectNamingStrategy,
        subjectRecordName = valueSubjectRecordName,
        subjectRecordNamespace = valueSubjectRecordNamespace,
        sslSettings = sparkOptions
      )
    }
    var fromKeyAvroConfig: FromAvroConfig = null
    if (keyFormat == "avro") {
      fromKeyAvroConfig = helper.GetFromAvroConfig(
        topic = topic,
        schemaRegistryUrl = schemaRegistryUrl,
        schemaVersion = keySchemaVersion,
        isKey = true,
        subjectNamingStrategy = keySubjectNamingStrategy,
        subjectRecordName = keySubjectRecordName,
        subjectRecordNamespace = keySubjectRecordNamespace,
        sslSettings = sparkOptions
      )
    }
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
    dft = ProcessKafkaConnectMongodbOptions(
      spark = spark,
      df = dft,
      kafkaConnectMongodbOptions = kafkaConnectMongodbOptions
    )
    dft.printSchema()
    if (isStream && !streamWatermarkDelay.isEmpty) {
      dft = dft.withWatermark(streamWatermarkField, streamWatermarkDelay.get)
    }
    dft
  }

  private def ProcessKafkaConnectMongodbOptions(spark: SparkSession, df: DataFrame, kafkaConnectMongodbOptions: Option[JSONObject] = None): DataFrame = {
    var outputDf = df
    if (!kafkaConnectMongodbOptions.isEmpty) {
      if (kafkaConnectMongodbOptions.get.has("documentschema")) {
        val documentSchema = kafkaConnectMongodbOptions.get.optString("documentschema")
        outputDf.createOrReplaceTempView("df")
        outputDf = spark.sql("SELECT \n        df2.topic, df2.partition, df2.offset, ifnull(to_timestamp(from_unixtime(df2.payload.clusterTime.`$timestamp`.t)), df2.timestamp) as timestamp, df2.payload.fullDocument._id as key, struct(df2.payload.fullDocument.*) as value \nFROM  (\n        select df.topic, df.partition, df.offset, df.timestamp,\n                from_json (\n                        from_json (\n                                value,\n                                'struct<payload:string,schema:struct<optional:boolean,type:string>>'\n                        ).payload , \n                        'struct<\n                                operationType:string,\n                                clusterTime:struct<`$timestamp`:struct<t:long,i:long>>,\n                                fullDocument:struct<" + documentSchema + ">,\n                                ns:struct<coll:string,db:string>\n                        >'\n                ) as payload from df\n) df2")
      }
    }
    outputDf
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

    // Spark 3.5 KafkaWriter.validateQuery() requires topic as a UTF8String Literal, not a plain
    // String option. Passing "topic" in options causes IllegalArgumentException in Spark 3.5.
    // Fix: remove "topic" from options and inject it as a literal column in the DataFrame instead.
    sparkOptions = sparkOptions ++ Map("kafka.bootstrap.servers" -> kafkaBroker, "includeHeaders" -> (!headerField.isEmpty).toString)

    if (!addlSparkOptions.isEmpty) {
      sparkOptions = sparkOptions ++ jsonObjectPropertiesToMap(addlSparkOptions.get)
    }

    val valueFieldCol = df.col(valueField)
    val valueAvroConfig = helper.GetToAvroConfig(topic = topic, schemaRegistryUrl = schemaRegistryUrl, df = df, columnName = valueField, schemaVersion = valueSchemaVersion, isKey = false, subjectNamingStrategy = valueSubjectNamingStrategy, subjectRecordName = valueSubjectRecordName, subjectRecordNamespace = valueSubjectRecordNamespace, sslSettings = sparkOptions)
    var columnsToSelect = Seq((valueFormat match {
      case "avro" => to_avro(valueFieldCol, valueAvroConfig)
      case _ => valueFieldCol
    }) as 'value)
    if (!keyField.isEmpty) {
      val keyFieldCol = df.col(keyField.get)
      val keyAvroConfig = helper.GetToAvroConfig(topic = topic, schemaRegistryUrl = schemaRegistryUrl, df = df, columnName = keyField.get, schemaVersion = keySchemaVersion, isKey = true, subjectNamingStrategy = keySubjectNamingStrategy, subjectRecordName = keySubjectRecordName, subjectRecordNamespace = keySubjectRecordNamespace, sslSettings = sparkOptions)
      columnsToSelect = columnsToSelect ++ Seq((keyFormat match {
        case "avro" => to_avro(keyFieldCol, keyAvroConfig)
        case _ => keyFieldCol
      }) as 'key)
    }
    if (!headerField.isEmpty) {
      columnsToSelect = columnsToSelect ++ Seq(df.col(headerField.get) as 'header)
    }
    // Add topic as a literal column so Spark 3.5 KafkaWriter resolves it as UTF8String
    columnsToSelect = columnsToSelect ++ Seq(org.apache.spark.sql.functions.lit(topic) as 'topic)

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

  def rdbmsToDataFrame(platform: String, url:String, awsEnv: String, server: String, database: String, table: String, login: String, password: String, sparkSession: org.apache.spark.sql.SparkSession, primarykey: String, lowerbound: String, upperbound: String, numofpartitions: String, vaultEnv: String, secretStore: String, sslEnabled: Boolean, port: String, addlJdbcOptions: JSONObject, isWindowsAuthenticated: Boolean, domainName: String, typeForTeradata: Option[String]): org.apache.spark.sql.DataFrame = {
    val configMap = helper.buildRdbmsURI(platform, server, port, database, isWindowsAuthenticated, domainName, typeForTeradata, sslEnabled, addlJdbcOptions: JSONObject)
    val driver: String = configMap("driver")
    val db_url: String = Option(url).filter(_.nonEmpty).getOrElse(configMap.getOrElse("url", ""))
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
      jdbcOptions = jdbcOptions ++ Map("url" -> db_url,
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


      val df = sparkSession.read.options(jdbcOptions).jdbc(url = db_url,

        table = table,
        columnName = primarykey,
        lowerBound = lowerbound.toLong,
        upperBound = upperbound.toLong,
        numPartitions = numofpartitions.toInt,
        connectionProperties = connectionProperties)

      df
    }
  }

  def dataFrameToRdbms(platform: String, url:String, awsEnv: String, server: String, database: String, table: String, login: String, password: String, df: org.apache.spark.sql.DataFrame, vaultEnv: String, secretStore: String, sslEnabled: Boolean = false, port: String, addlJdbcOptions: JSONObject, savemode: String, isWindowsAuthenticated: Boolean, domainName: String, typeForTeradata: Option[String] = None): Unit = {
    val configMap = helper.buildRdbmsURI(platform, server, port, database, isWindowsAuthenticated, domainName, typeForTeradata, sslEnabled, addlJdbcOptions: JSONObject)
    val driver: String = configMap("driver")
    val db_url: String = Option(url).filter(_.nonEmpty).getOrElse(configMap.getOrElse("url", ""))

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
    df_temp.write.mode(savemode).options(jdbcOptions).jdbc(db_url, table, connectionProperties)
  }

  def hiveToDataFrame(sparkSession: SparkSession, query: String, properties: Option[JSONObject] = None): DataFrame = {

    println("Properties Passed:" + properties)

    val defaultConfigs = Map(
      "spark.sql.hive.caseSensitiveInferenceMode" -> "NEVER_INFER"
      // spark.sql.hive.metastore.jars is a static config in Spark 3.5 — cannot be set post-session.
      // It is already set to "builtin" during SparkSession.builder in DataPull.scala.
    )
    val parsedProperties = properties.map { jsonObj =>
      import scala.collection.JavaConverters._
      val propertyMap = scala.collection.mutable.Map[String, String]()
      val iter = jsonObj.keys()

      while (iter.hasNext) {
        val key = iter.next().toString
        propertyMap(key) = jsonObj.getString(key)
      }
      propertyMap.toMap
    }.getOrElse(Map.empty[String, String])

    val finalConfigs = defaultConfigs ++ parsedProperties

    finalConfigs.foreach { case (key, value) =>
      sparkSession.sqlContext.setConf(key, value)
    }

    println("Configurations applied:")
    finalConfigs.foreach { case (key, value) =>
      println(s"$key -> ${sparkSession.sqlContext.getConf(key)}")
    }

    sparkSession.sql(query)
  }

  def dataFrameToHive(sparkSession: SparkSession, df: org.apache.spark.sql.DataFrame, table: String, database: String, format: String, saveMode: String, partitions: Boolean): Unit = {
    sparkSession.sql("use " + database)

    if (!partitions) {
      df.write
        .format(format)
        .mode(saveMode)
        .insertInto(table)
    } else {
      sparkSession.sqlContext.setConf("hive.exec.dynamic.partition", "true")
      sparkSession.sqlContext.setConf("hive.exec.dynamic.partition.mode", "nonstrict")

      df.write
        .format(format)
        .mode(saveMode)
        .insertInto(table)
    }
  }

  def rdbmsRunCommand(platform: String, url:String, awsEnv: String, server: String, port: String, sslEnabled: Boolean, database: String, sql_command: String, login: String, password: String, vaultEnv: String, secretStore: String, isWindowsAuthenticated: Boolean, domainName: String, typeForTeradata: Option[String], colType: Option[String]): Option[String] = {

    if (sql_command == "") return None

    val configMap = helper.buildRdbmsURI(platform, server, port, database, isWindowsAuthenticated, domainName, typeForTeradata, sslEnabled, null)
    val driver: String = configMap("driver")
    val db_url: String = Option(url).filter(_.nonEmpty).getOrElse(configMap.getOrElse("url", ""))

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
      Class.forName(driver)
      connection = DriverManager.getConnection(db_url, vaultLogin, vaultPassword)
      val statement = connection.createStatement()

      if (colType.isDefined) {
        val rs = statement.executeQuery(sql_command)
        try {
          if (rs.next()) {
            Some(colType.toString match {
              case "Some(int)"    => String.valueOf(rs.getInt(1))
              case "Some(string)" => rs.getString(1)
              case "Some(float)"  => String.valueOf(rs.getFloat(1))
              case "Some(date)"   => String.valueOf(rs.getDate(1))
              case "Some(long)"   => String.valueOf(rs.getLong(1))
              case _              => String.valueOf(rs.getInt(1))
            })
          } else None
        } finally {
          rs.close()
          statement.close()
        }
      } else {
        statement.execute(sql_command)
        statement.close()
        None
      }

    } catch {
      case e: Throwable => e.printStackTrace; throw e
    } finally {
      if (connection != null && !connection.isClosed())
        connection.close()
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

  import scala.jdk.CollectionConverters._

  def jsonObjectToMap(jsonObject: JSONObject): Map[String, String] = {
    jsonObject.keys.asScala.map(key => key.toString -> jsonObject.getString(key.toString)).toMap
  }

  def dataFrameToIceberg(sparkSession: SparkSession, df: org.apache.spark.sql.DataFrame,
                         table: String, database: String, saveMode: String, isMergeInto: Boolean,
                         mergeIntoSQL: String, sparkoptions: Option[JSONObject]): Unit = {

    IcebergUtils.configureIcebergCatalog(sparkSession)

    sparkSession.sql("use " + database)

    /*
    allow to insert the data to target table only if these conditions met
    case-1: source dataframe columns <= target dataframe columns
    case-2: source dataframe columns and datatypes == target dataframe columns and datatypes
     */
    val targetDf = sparkSession.sql(s"SELECT * FROM $table limit 100")
    val srcDf = df
    validateIcebergSchema(srcDf, targetDf)

    if (isMergeInto) {
      //validating MergeIntoQuery
      if (mergeIntoSQL.trim.toLowerCase.startsWith("merge into")) {
        //get the source table from User given SQL string
        val srcTable = mergeIntoSQL.split(" ").take(6).last

        df.createOrReplaceTempView(srcTable)
        sparkSession.sql(mergeIntoSQL)
      }else {
        throw new IllegalArgumentException("Validation Error: mergeinto sql query is not valid, should start with 'MERGE INTO'. Expected format: " +
          "MERGE INTO <target-table> target USING <source-table> source ON target.<key> = source.<key> WHEN MATCHED THEN UPDATE SET * WHEN NOT MATCHED THEN INSERT *")
      }
    } else{
      var extraConfigOptions= Map.empty[String, String]
      sparkoptions match {
        case Some(jsonObj) if jsonObj != null =>
          extraConfigOptions = jsonObjectPropertiesToMap(sparkoptions.get)
        case _ =>
          extraConfigOptions = extraConfigOptions ++ Map("overwrite-mode" -> "dynamic")
      }

      if (saveMode.toLowerCase == "overwrite") {
        df.write
          .format("iceberg")
          .options(extraConfigOptions)
          .mode(saveMode)
          .insertInto(table)
      } else {
        df.write
          .format("iceberg")
          .mode(saveMode)
          .insertInto(table)
      }
    }
  }

  import scala.collection.mutable.Map

  def icebergToDataFrame(sparkSession: org.apache.spark.sql.SparkSession,
                         query: String): org.apache.spark.sql.DataFrame = {
    IcebergUtils.configureIcebergCatalog(sparkSession)

    sparkSession.sql(query)
  }

  import org.apache.spark.sql.types._

  def validateIcebergSchema(srcDf: org.apache.spark.sql.DataFrame, tgtDf: org.apache.spark.sql.DataFrame): Unit = {

    val srcSchema = srcDf.schema
    val tgtSchema = tgtDf.schema

    val srcFields = srcSchema.fields.map(f => f.name.toLowerCase -> f.dataType).toMap
    val tgtFields = tgtSchema.fields.map(f => f.name.toLowerCase -> f.dataType).toMap

    // Check if src has more columns than tgt (which is not allowed)
    if (srcFields.size > tgtFields.size) {
      throw new IllegalArgumentException("Source DataFrame has more columns than target DataFrame")
    }

    // Check if all src columns exist in tgt and types match
    srcFields.foreach { case (srcCol, srcType) =>
      tgtFields.get(srcCol) match {
        case Some(tgtType) if tgtType == srcType => // All good
        case Some(tgtType) =>
          throw new IllegalArgumentException(s"Data type mismatch for column '$srcCol'. Src: $srcType, Tgt: $tgtType")
        case None =>
          throw new IllegalArgumentException(s"Column '$srcCol' is missing in target schema")
      }
    }
  }

}

