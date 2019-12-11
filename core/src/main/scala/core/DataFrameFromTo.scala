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
import java.net.URLEncoder
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
import com.mongodb.client.{MongoCollection, MongoCursor, MongoDatabase}
import com.mongodb.spark.MongoSpark
import com.mongodb.spark.config.{ReadConfig, WriteConfig}
import com.mongodb.spark.sql.toSparkSessionFunctions
import com.mongodb.{MongoClient, MongoClientURI}
import config.AppConfig
import core.DataPull.jsonObjectPropertiesToMap
import helper._
import org.apache.avro.Schema
import org.apache.avro.generic.{GenericData, GenericRecord}
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Row, SaveMode, SparkSession}
import org.bson.Document
import org.codehaus.jettison.json.JSONObject
import org.elasticsearch.spark.sql._
import org.influxdb.InfluxDBFactory
import org.influxdb.dto.Query
import org.json.simple.parser.JSONParser
import security._

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.collection.immutable.List
import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.util.control.Breaks._

class DataFrameFromTo(appConfig: AppConfig, pipeline : String) extends Serializable {



  def fileToDataFrame(filePath: String, fileFormat: String, delimiter: String, charset: String,mergeSchema: String, sparkSession: org.apache.spark.sql.SparkSession, isS3: Boolean, secretstore: String,isSFTP: Boolean,login:String,host:String,password:String,awsEnv:String , vaultEnv:String): org.apache.spark.sql.DataFrame = {

    if( filePath == null && fileFormat == null && delimiter == null && charset == null && mergeSchema == null && sparkSession == null && login == null && host == null && password == null )
    {
      throw new Exception("Platform cannot have null values")
    }

    if( filePath == null && fileFormat == null && delimiter == null && charset == null && mergeSchema == null && sparkSession == null && login == null && host == null && password == null )
    {
      throw new Exception("Platform cannot have empty values")
    }

    var vaultPassword = password
    var vaultLogin = login
    if (vaultPassword == "" && awsEnv != "false" && vaultEnv != "false") {
      val secretService = new SecretService(secretstore,appConfig)
      val vaultCreds = secretService.getSecret(awsEnv,  "sftp-"+host, login, vaultEnv)
      vaultLogin = vaultCreds("username")
      vaultPassword = vaultCreds("password")
    }

    var filePrefix = ""

    if (isS3) {
      filePrefix = "s3a://"
    }
    def createOrReplaceTempViewOnDF(df: org.apache.spark.sql.DataFrame): org.apache.spark.sql.DataFrame = {
      df
    }

    if(isSFTP)
    {

      createOrReplaceTempViewOnDF(sparkSession.read.
        format("com.springml.spark.sftp").
        option("host", host).
        option("username", login).
        option("password", password).
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
        createOrReplaceTempViewOnDF(sparkSession.read.format("com.databricks.spark.avro").load(s"$filePrefix$filePath"))
      } else {
        //parquet
        createOrReplaceTempViewOnDF(sparkSession.read.option("mergeSchema", mergeSchema).parquet(s"$filePrefix$filePath"))
      }
    }
  }

  def dataFrameToFile(filePath: String, fileFormat: String, groupByFields: String, s3SaveMode: String, df: org.apache.spark.sql.DataFrame, isS3: Boolean,secretstore:String, sparkSession: SparkSession,isSFTP: Boolean,login:String,host:String,password:String,awsEnv:String,vaultEnv:String): Unit = {

    if( filePath == null && fileFormat == null && groupByFields == null && s3SaveMode == null && login == null && isS3 == null && SparkSession == null )
    {
      throw new Exception("Platform cannot have null values")
    }

    if( filePath.isEmpty() == true && fileFormat.isEmpty() == true && groupByFields.isEmpty() == true && s3SaveMode.isEmpty() == true && login.isEmpty() == true  && sparkSession == null)
    {
      throw new Exception("Platform cannot have empty values")
    }

    //if password isn't set, attempt to get from Vault
    var vaultPassword = password
    var vaultLogin = login
    if (vaultPassword == "" && awsEnv != "false" && vaultEnv != "false") {
      val secretService = new SecretService(secretstore,appConfig)
      val vaultCreds = secretService.getSecret(awsEnv,  "sftp-"+host, login, vaultEnv)
      vaultLogin = vaultCreds("username")
      vaultPassword = vaultCreds("password")
    }

    var groupByFieldsArray = groupByFields.split(",")
    var filePrefix = ""
    if (isS3) {
      filePrefix = "s3a://"
    }
    if(isSFTP)
    {

      df.write.
        format("com.springml.spark.sftp").
        option("host", host).
        option("username", login).
        option("password", password).
        option("fileType", fileFormat).
        save(filePath)

      df.show()
    }
    else {
      if (fileFormat == "json") {
        if (groupByFields == "") {

          df
            .write
            .mode(SaveMode.valueOf(s3SaveMode)).json(s"$filePrefix$filePath")
        } else {
          df
            .write
            .partitionBy(groupByFieldsArray: _*)
            .mode(SaveMode.valueOf(s3SaveMode)).json(s"$filePrefix$filePath")
        }
      } else if (fileFormat == "csv") {
        if (groupByFields == "") {
          df.coalesce(1)
            .write
            .option("header", "true")
            .mode(SaveMode.valueOf(s3SaveMode))
            .csv(s"$filePrefix$filePath")
        } else {
          df.coalesce(1)
            .write
            .partitionBy(groupByFieldsArray: _*)
            .option("header", "true")
            .mode(SaveMode.valueOf(s3SaveMode))
            .csv(s"$filePrefix$filePath")
        }
      } else if (fileFormat == "avro") {
        if (groupByFields == "") {
          df.coalesce(1)
            .write
            .format("com.databricks.avro")
            .option("header", "true")
            .mode(SaveMode.valueOf(s3SaveMode))
            .save(s"$filePrefix$filePath")
        } else {
          df.coalesce(1)
            .write
            .format("com.databricks.avro")
            .partitionBy(groupByFieldsArray: _*)
            .option("header", "true")
            .mode(SaveMode.valueOf(s3SaveMode))
            .save(s"$filePrefix$filePath")
        }
      }
      else {
        //parquet
        if (groupByFields == "") {
          Option(df
            .write
            .mode(SaveMode.valueOf(s3SaveMode)).parquet(s"$filePrefix$filePath"))
        } else {
          Option(df
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
      destParts.foreach(x => println(s"TEST Dest Parts: $x"))

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
        val dest = destPrefix + (if (destPrefix.charAt(destPrefix.length - 1) == "/") "" else "/") + src.split("/").last
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
    val path = s3Location.substring(s3Location.indexOf(bucketName)+bucketName.length +1)
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
      val secretService = new SecretService(secretstore,appConfig)
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

  def dataFrameToCassandra(awsEnv: String, cluster: String, keyspace: String, table: String, login: String, password: String, local_dc: String, addlSparkOptions: JSONObject, df: org.apache.spark.sql.DataFrame, reportbodyHtml: StringBuilder, vaultEnv: String,secretStore:String): Unit = {

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
      val secretService = new SecretService(secretStore,appConfig)
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
                       relation_createOrMerge: String,secretStore:String, sparkSession: org.apache.spark.sql.SparkSession): Unit = {
    val consul = new Consul(cluster, appConfig)
    var clusterName = cluster
    if (consul.IsConsulDNSName()) {
      clusterName = consul.serviceName
    }
    //if password isn't set, attempt to get from security.Vault
    var vaultPassword = password
    var vaultLogin = login
    if (vaultPassword == "") {
      val secretService = new SecretService(secretStore,appConfig)
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

  def dataFrameToElastic(awsEnv: String, cluster: String, port: String, index: String, nodetype: String, version: String, login: String, password: String, local_dc: String, addlSparkOptions: JSONObject, df: org.apache.spark.sql.DataFrame, reportbodyHtml: StringBuilder, vaultEnv: String,saveMode:String,mappingId:String,flag: String,secretStore:String, sparkSession: org.apache.spark.sql.SparkSession): Unit = {


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
      val secretService = new SecretService(secretStore,appConfig)
      val vaultCreds = secretService.getSecret(awsEnv, clusterName, login, vaultEnv)
      vaultLogin = vaultCreds("username")
      vaultPassword = vaultCreds("password")
    }

    var config= Map("es.nodes"->clusterNodes,
      "es.port"->port,
      "es.clustername"->clusterName,
      "es.net.http.auth.user" -> vaultLogin,
      "es.net.http.auth.pass" -> vaultPassword,
      "es.write.operation" -> saveMode,
      "es.nodes.wan.only"-> "true",
      "es.resource" -> s"$index/$nodetype",
      "es.internal.es.version"-> version)


    if(mappingId !=null)
      config =config ++ Map("es.mapping.id" -> mappingId)

    if (flag == "false") {
      df.saveToEs(config)
    }

  }

  def ElasticToDataframe(awsEnv: String, cluster: String, port: String, index: String, nodetype: String, version: String, login: String, password: String, vaultEnv: String,secretStore: String, sparkSession: org.apache.spark.sql.SparkSession): org.apache.spark.sql.DataFrame = {


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
      val secretService = new SecretService(secretStore,appConfig)
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
        val secretService = new SecretService(secretStore,appConfig)
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

  def elasticRunCommand(awsEnv: String, cluster: String,port:String, index: String, login: String, password: String, local_dc: String, addlSparkOptions: JSONObject, curlcommand: String, reportbodyHtml: StringBuilder, vaultEnv: String,secretStore:String, ignoreTruncateException: Boolean = true): Unit = {

    //if password isn't set, attempt to get from Vault
    var vaultPassword = password
    var vaultLogin = login
    if (vaultPassword == "") {
      val secretService = new SecretService(secretStore,appConfig)
      val vaultCreds = secretService.getSecret(awsEnv, cluster, login, vaultEnv)
      vaultLogin = vaultCreds("username")
      vaultPassword = vaultCreds("password")
    }
    //logic
    var connection: Connection = null
    var statement: Statement = null

    try {

      val service = vaultPassword+":"+vaultLogin+"@"+cluster+":"+port+"/"
      var cmd_withcreds = curlcommand.replace("://","://"+login+":"+password+"@")//.replace("\"", "\\\"");

      val fileName = pipeline +".sh"
      import java.io.PrintWriter
      new PrintWriter(fileName) { write(cmd_withcreds); close }
      val file = new File(fileName)
      file.setReadable(true, false)
      file.setExecutable(true, false)
      file.setWritable(true, false)
      import sys.process._
      val result = "./"+fileName !!

      println("command result = "+result+" pipeline name = "+fileName+" pipeline = "+pipeline);

      new File(fileName).delete()

      val tokens : Array[String] = cmd_withcreds.split(" ");
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

  def mongodbToDataFrame(awsEnv: String, cluster: String, overrideconnector: String, database: String, authenticationDatabase: String, collection: String, login: String, password: String, sparkSession: org.apache.spark.sql.SparkSession, vaultEnv: String, addlSparkOptions: JSONObject, secretStore: String, authenticationEnabled: String): org.apache.spark.sql.DataFrame = {
    val consul = new Consul(cluster, appConfig)
    var clusterName = cluster
    var clusterNodes = cluster
    if (consul.IsConsulDNSName()) {
      clusterName = consul.serviceName
      clusterNodes = clusterNodes + "," + consul.ipAddresses.mkString(",") + ":27017"
    }
    var uri: String = null
    val authenticationEnabledLocal = authenticationEnabled
    //if password isn't set, attempt to get from security.Vault
    if (authenticationEnabledLocal == "true") {
      var vaultPassword = password
      var vaultLogin = login
      if (vaultPassword == "") {
        val secretService = new SecretService(secretStore, appConfig)
        val vaultCreds = secretService.getSecret(awsEnv, clusterName, login, vaultEnv)
        vaultLogin = vaultCreds("username")
        vaultPassword = vaultCreds("password")
      }

      uri = "mongodb://" + URLEncoder.encode(vaultLogin, "UTF-8") + ":" + URLEncoder.encode(vaultPassword, "UTF-8") + "@" + cluster + ":27017/" + database + "." + collection + "?authSource=" + (if (authenticationDatabase != "") authenticationDatabase else "admin")
    } else {
      uri = "mongodb://" + cluster + ":27017/" + database + "." + collection

    }
    if (overrideconnector.toBoolean)
    {

      var mongoClient: MongoClient = new MongoClient(new MongoClientURI(uri))
      var mdatabase: MongoDatabase = mongoClient.getDatabase("" + database);
      var col: MongoCollection[Document] = mdatabase.getCollection(collection);
      var cur: MongoCursor[Document] = col.find().iterator()
      var doc: org.bson.Document = null
      var jsondocs = new ListBuffer[String]()

      while (cur.hasNext()) {
        doc = cur.next();
        jsondocs += doc.toJson()
      }

      val finaljsondoc = jsondocs.toList

      import sparkSession.implicits._
      val df = finaljsondoc.toDF().withColumnRenamed("value","jsonfield")

      df
    }

    else {

      var sparkOptions = Map("uri" -> uri)

      if (addlSparkOptions != null) {
        sparkOptions = sparkOptions ++ jsonObjectPropertiesToMap(addlSparkOptions)
      }

      val df = sparkSession.loadFromMongoDB(ReadConfig(sparkOptions))
      df
    }
  }

  def dataFrameToMongodb(awsEnv: String, cluster: String, database: String, authenticationDatabase: String, collection: String, login: String, password: String, replicaset: String, replaceDocuments: String, ordered: String, df: org.apache.spark.sql.DataFrame, sparkSession: org.apache.spark.sql.SparkSession, documentfromjsonfield: String, jsonfield: String, vaultEnv: String, secretStore: String, addlSparkOptions: JSONObject, maxBatchSize: String, authenticationEnabled: String): Unit = {

    val consul = new Consul(cluster, appConfig)
    var clusterName = cluster
    if (consul.IsConsulDNSName()) {
      clusterName = consul.serviceName
    }
    var uri: String = null
    val authenticationEnabledLocal = authenticationEnabled
    //if password isn't set, attempt to get from security.Vault
    if (authenticationEnabledLocal == "true") {
      var vaultPassword = password
      var vaultLogin = login
      if (vaultPassword == "") {
        val secretService = new SecretService(secretStore, appConfig)
        val vaultCreds = secretService.getSecret(awsEnv, clusterName, login, vaultEnv)
        vaultLogin = vaultCreds("username")
        vaultPassword = vaultCreds("password")
      }
      uri = "mongodb://" + URLEncoder.encode(vaultLogin, "UTF-8") + ":" + URLEncoder.encode(vaultPassword, "UTF-8") + "@" + cluster + ":27017/" + database + "." + collection + "?authSource=" + (if (authenticationDatabase != "") authenticationDatabase else "admin") + (if (replicaset == null) "" else "&replicaSet=" + replicaset)

    } else {
      uri = "mongodb://" + cluster + ":27017/" + database + "." + collection + (if (replicaset == null) "" else "&replicaSet=" + replicaset)

    }



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

  def mongoRunCommand(awsEnv: String, cluster: String, database: String, authenticationDatabase: String, collection: String, login: String, password: String, vaultEnv: String, addlSparkOptions: JSONObject, runCommand: String, secretStore: String, authenticationEnabled: String): Unit = {


    val consul = new Consul(cluster, appConfig)
    var clusterName = cluster
    var clusterNodes = cluster
    if (consul.IsConsulDNSName()) {
      clusterName = consul.serviceName
      clusterNodes = clusterNodes + "," + consul.ipAddresses.mkString(",")
    }
    val authenticationEnabledLocal = authenticationEnabled
    var uri: MongoClientURI = null
    //if password isn't set, attempt to get from security.Vault
    if (authenticationEnabledLocal == "true") {
      //if password isn't set, attempt to get from Vault
      var vaultPassword = password
      var vaultLogin = login
      if (vaultPassword == "") {
        val secretService = new SecretService(secretStore, appConfig)
        val vaultCreds = secretService.getSecret(awsEnv, clusterName, login, vaultEnv)
        vaultLogin = vaultCreds("username")
        vaultPassword = vaultCreds("password")
      }
      uri = new MongoClientURI(s"mongodb://$vaultLogin:$vaultPassword@$clusterNodes/?authSource=$authenticationDatabase&authMechanism=SCRAM-SHA-1")
    } else {
      uri = new MongoClientURI(s"mongodb://$clusterNodes:27017/")
    }

    val mongoClient = new MongoClient(uri)
    var data = mongoClient.getDatabase(database)

    val response = data.runCommand(org.bson.Document.parse(runCommand))

  }

  def kafkaToDataFrame(bootstrapServers: String, topic: String, offset: String, schemaRegistries: String, deSerializer: String, s3Location: String, groupId: String, migrationId: String, jobId: String, sparkSession: org.apache.spark.sql.SparkSession, s3TempFolderDeletionError:mutable.StringBuilder): org.apache.spark.sql.DataFrame = {

    var groupId_temp= groupId

    if(groupId_temp==null)
      groupId_temp= UUID.randomUUID().toString + "_" + topic

    println(groupId_temp)

    val props = new Properties()
    props.put("bootstrap.servers", bootstrapServers)
    props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    props.put("value.deserializer", deSerializer)
    props.put("group.id", groupId_temp)
    props.put("auto.offset.reset", offset)
    props.put("schema.registry.url", schemaRegistries)
    props.put("max.poll.records", "500")
    props.put("session.timeout.ms", "120000")

    val consumer = new KafkaConsumer[String, GenericData.Record](props)

    consumer.subscribe(util.Collections.singletonList(topic))
    var list = new ListBuffer[String]()

    var tmp_s3: String = null

    var poll = 0
    var df_temp: org.apache.spark.sql.DataFrame = null
    var df: org.apache.spark.sql.DataFrame = null
    var count =0

    if (s3Location == null) {
      tmp_s3 = "s3a://ha-dev-datamigration/test-data/" + topic + "_" + UUID.randomUUID().toString
    }
    else{
      tmp_s3 = s3Location + "/migrationId=" + migrationId + "/jobId=" + jobId
    }

    val currentTime_Unix = System.currentTimeMillis()

    breakable {
      while (true) {

        val consumerRecords = consumer.poll(200)

        val count_tmp = consumerRecords.count()

        count+=count_tmp

        if (count_tmp == 0) {

          println("didn't find any records, waiting...")

          Thread.sleep(10000)

          if (poll >= 400) {
            df_temp = sparkSession.read.json(sparkSession.sparkContext.parallelize(list))
            df_temp.write.mode(SaveMode.Append).json(tmp_s3)
            list.clear()

            println("breaking out of the loop after required retries at:"+Instant.now())

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

              println("breaking out of the loop after reading all the messages till the job started and ending at:"+Instant.now())

              break()
            }

            list += consumerRecord.value().get("body").toString

            if (list.length >= 80000) {
              df_temp = sparkSession.read.json(sparkSession.sparkContext.parallelize(list))
              println("writing to s3 after reaching the block size and time now is:"+Instant.now())
              df_temp.write.mode(SaveMode.Append).json(tmp_s3)
              list.clear()
              count=0
            }

          }

        }
      }
    }

    df = sparkSession.read.json(tmp_s3)
    try{
      s3RemoveDirectoryUsingS3Client(tmp_s3)
      println(s"Deleted temporary S3 folder - $tmp_s3")
    }catch{
      case e: Throwable => e.printStackTrace
        println(s"Unable to delete temporary S3 folder - $tmp_s3")
        s3TempFolderDeletionError.append(s"<b>*</b> Unable to delete S3 folder - $tmp_s3")
    }

    df
  }


  def manOf[T: Manifest](t: T): Manifest[T] = manifest[T]

  def dataFrameToKafka(bootstrapServers: String, schemaRegistries: String, topic: String, keyField: String, Serializer: String, df: org.apache.spark.sql.DataFrame): Unit = {

    val props = new util.HashMap[String, Object]()

    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, Serializer)
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
    props.put("schema.registry.url", schemaRegistries)

    df.toJSON.foreachPartition((partition: Iterator[String]) => {
      val userSchema = "{\"type\":\"record\"," +
        "\"name\":\"dataFrame\"," +
        "\"fields\":[{\"name\":\"body\",\"type\":\"string\"},{\"name\":\"header\",\"type\":\"string\"}]}"

      val parser = new Schema.Parser()
      val schema = parser.parse(userSchema)

      val producer = new KafkaProducer[Object, GenericRecord](props)
      val jsonParser = new JSONParser()

      partition.foreach((item: String) => {
        try {
          val jsonObject = jsonParser.parse(item).asInstanceOf[org.json.simple.JSONObject]
          val key = jsonObject.get(keyField).toString()

          val avroRecord = new GenericData.Record(schema)

          //header == time, threadId, env, service, requestMarker, server

          // val specificData = new SpecificData.SchemaConstructable {schema}

          avroRecord.put("body", item)

          //  println(avroRecord)

          val message = new ProducerRecord[Object, GenericRecord](topic, key, avroRecord)
          producer.send(message)

        } catch {
          case ex: Exception => {

            ex.printStackTrace()
          }
        }
      })
      producer.flush()
      producer.close()
    })
    break()
  }

  def rdbmsToDataFrame(platform: String, awsEnv: String, server: String, database: String, table: String, login: String, password: String, sparkSession: org.apache.spark.sql.SparkSession, primarykey: String, lowerbound: String, upperbound: String, numofpartitions: String, vaultEnv: String, secretStore: String, sslEnabled: String, port: String, addlJdbcOptions: JSONObject): org.apache.spark.sql.DataFrame = {

    var driver: String = null
    var url: String = null

    if (platform == "mssql") {
      driver = "com.microsoft.sqlserver.jdbc.SQLServerDriver"
      url = "jdbc:sqlserver://" + server + ":" + (if (port == null) "1433" else port) + ";database=" + database
    }
    else if (platform == "mysql") {
      driver = "com.mysql.jdbc.Driver"
      url = "jdbc:mysql://" + server + ":" + (if (port == null) "3306" else port) + "/" + database + "?rewriteBatchedStatements=true&cachePrepStmts=true"
    }

    else if (platform == "postgres") {
      driver = "org.postgresql.Driver"
      url = "jdbc:postgresql://" + server + ":" + (if (port == null) "5432" else port) + "/" + database + (if (sslEnabled == "true") "?sslmode=require" else "")
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
      val secretService = new SecretService(secretStore,appConfig)
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

  def dataFrameToRdbms(platform: String, awsEnv: String, server: String, database: String, table: String, login: String, password: String, df: org.apache.spark.sql.DataFrame, vaultEnv: String, secretStore: String, sslEnabled: String, port: String, addlJdbcOptions: JSONObject): Unit = {

    var driver: String = null
    var url: String = null

    if (platform == "mssql") {
      driver = "com.microsoft.sqlserver.jdbc.SQLServerDriver"
      url = "jdbc:sqlserver://" + server + ":" + (if (port == null) "1433" else port) + ";database=" + database
    }
    else if (platform == "mysql") {
      driver = "com.mysql.jdbc.Driver"
      url = "jdbc:mysql://" + server + ":" + (if (port == null) "3306" else port) + "/" + database + "?rewriteBatchedStatements=true&cachePrepStmts=true"
    } else if (platform == "postgres") {
      driver = "org.postgresql.Driver"
      url = "jdbc:postgresql://" + server + ":" + (if (port == null) "5432" else port) + "/" + database + (if (sslEnabled == "true") "?sslmode=require" else "")
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
      val secretService = new SecretService(secretStore,appConfig)
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

    df.write.mode("append").options(jdbcOptions).jdbc(url, table, connectionProperties)
  }

  def hiveToDataFrame(cluster: String, clusterType: String, database: String, table: String): org.apache.spark.sql.DataFrame = {
    var sparkSession = SparkSession.builder //.master("local")
      .config("dfs.ha.namenodes." + clusterType, "nn1")
      .config("dfs.nameservices", clusterType)
      .config("dfs.namenode.rpc-address." + clusterType + ".nn1", cluster + ":8020")
      .config("dfs.client.failover.proxy.provider." + clusterType, "dfs.client.failover.proxy.provider." + clusterType)
      .config("hive.metastore.uris", "thrift://" + cluster + ":9083")
      .appName("data migration")
      .enableHiveSupport()
      .getOrCreate()
    val df = sparkSession.sql("Select * FROM " + database + "." + table)
    df
  }

  def dataFrameToHive(cluster: String, clusterType: String, database: String, table: String, df: org.apache.spark.sql.DataFrame): Unit = {
    var sparkSession = SparkSession.builder //.master("local")
      .config("dfs.ha.namenodes." + clusterType, "nn1")
      .config("dfs.nameservices", clusterType)
      .config("dfs.namenode.rpc-address." + clusterType + ".nn1", cluster + ":8020")
      .config("dfs.client.failover.proxy.provider." + clusterType, "dfs.client.failover.proxy.provider." + clusterType)
      .config("hive.metastore.uris", "thrift://" + cluster + ":9083")
      .appName("data migration")
      .enableHiveSupport()
      .getOrCreate()

    df.write
      .mode(SaveMode.Append)
      .format("orc")
      .saveAsTable(database + "." + table)
  }

  def rdbmsRunCommand(platform:String,awsEnv: String, server: String,port :String, sslEnabled: String, database: String, sql_command: String, login: String, password: String, vaultEnv: String,secretStore:String): Unit = {
    if (sql_command != "") {

      var driver:String = null;
      var url:String = null;
      if (platform == "mssql" )
      {
        driver = "com.microsoft.sqlserver.jdbc.SQLServerDriver"
        url = "jdbc:sqlserver://" + server + ":" + (if (port == null) "1433" else port) + ";database=" + database
      }
      else if (platform == "postgresql" )
      {
        driver = "org.postgresql.Driver"
        url = "jdbc:postgresql://" + server + ":" + (if (port == null) "5432" else port) + "/" + database + (if (sslEnabled == "true") "?sslmode=require" else "")
      }

      else if (platform == "mysql") {
        driver = "com.mysql.jdbc.Driver"
        url = "jdbc:mysql://" + server + ":" + (if (port == null) "3306" else port) + "/" + database + "?rewriteBatchedStatements=true&cachePrepStmts=true"
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
        val secretService = new SecretService(secretStore,appConfig)
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

  def dataFrameToCloudWatch(groupName : String, streamName : String, region : String, accessKey : String, secretKey : String, timeStampColumn : String, timestampFormat : String, df : org.apache.spark.sql.DataFrame, sparkSession: SparkSession): Unit = {
    if(groupName == null || groupName.trim.isEmpty ||
      streamName == null || streamName.trim.isEmpty )
      return;

    val awsLogsClient  = appConfig.getCloudWatchClient(accessKey, secretKey, region);
    val calendar = Calendar.getInstance
    val logStreamsRequest = new DescribeLogStreamsRequest().withLogGroupName(groupName).withLimit(5)
    val logStreamList = awsLogsClient.describeLogStreams(logStreamsRequest).getLogStreams
    val retriveTimeStamp = if(timeStampColumn ==  null || timeStampColumn.isEmpty) false else true;
    val dateFormat = new SimpleDateFormat(timestampFormat)
    val rows = df.collect();
    var token : String = null;
    rows.foreach(x => {
      val rowRDD: RDD[Row] = sparkSession.sparkContext.makeRDD(x :: Nil)
      val df3 = sparkSession.sqlContext.createDataFrame(rowRDD, x.schema)
      val json = df3.toJSON.first
      val timeStamps = if(retriveTimeStamp) new Timestamp(dateFormat.parse(x.getString(x.fieldIndex(timeStampColumn))).getTime) else new Timestamp(System.currentTimeMillis());
      val log = new InputLogEvent
      log.setMessage(json)
      log.setTimestamp(timeStamps.getTime)
      if(token == null){
        for (logStream <- logStreamList) {
          if (logStream.getLogStreamName.equals(streamName)){
            token = logStream.getUploadSequenceToken
          }
        }
      }

      val putLogEventsRequest = new PutLogEventsRequest()
      if(token != null){
        putLogEventsRequest.setSequenceToken(token)
      }
      putLogEventsRequest.setLogGroupName(groupName)
      putLogEventsRequest.setLogStreamName(streamName)
      putLogEventsRequest.setLogEvents(Seq(log))
      val putLogEventsResult = awsLogsClient.putLogEvents(putLogEventsRequest)
      token = putLogEventsResult.getNextSequenceToken
    })
  }

}
