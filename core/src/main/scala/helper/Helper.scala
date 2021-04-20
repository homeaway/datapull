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

package helper

import java.io.{PrintWriter, StringWriter}
import java.net.URLEncoder
import java.security.cert.X509Certificate
import config.AppConfig

import javax.net.ssl.{HostnameVerifier, SSLSession, X509TrustManager}
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.common.config.SslConfigs
import org.apache.spark.sql.Column
import org.apache.spark.sql.avro.SchemaConverters
import za.co.absa.abris.avro.read.confluent.SchemaManagerFactory
import za.co.absa.abris.avro.registry.SchemaSubject
import za.co.absa.abris.config.{AbrisConfig, FromAvroConfig, FromStrategyConfigFragment, ToAvroConfig, ToSchemaDownloadingConfigFragment, ToStrategyConfigFragment}

class Helper(appConfig: AppConfig) {

  /**
   * Returns the text (content) from a REST URL as a String.
   *
   * @param url            The full URL to connect to.
   * @param connectTimeout Sets a specified timeout value, in milliseconds,
   *                       to be used when opening a communications link to the resource referenced
   *                       by this URLConnection. If the timeout expires before the connection can
   *                       be established, a java.net.SocketTimeoutException
   *                       is raised. A timeout of zero is interpreted as an infinite timeout.
   *                       Defaults to 10000 ms.
   * @param readTimeout    If the timeout expires before there is data available
   *                       for read, a java.net.SocketTimeoutException is raised. A timeout of zero
   *                       is interpreted as an infinite timeout. Defaults to 10000 ms.
   * @param requestMethod  Defaults to "GET". (Other methods have not been tested.)
   *
   */
  @throws(classOf[java.io.IOException])
  @throws(classOf[java.net.SocketTimeoutException])
  def get(url: String,
          connectTimeout: Int = appConfig.http_timeout,
          readTimeout: Int = appConfig.http_timeout,
          requestMethod: String = "GET") = {
    import java.net.{HttpURLConnection, URL}
    val connection = (new URL(url)).openConnection.asInstanceOf[HttpURLConnection]
    connection.setConnectTimeout(connectTimeout)
    connection.setReadTimeout(readTimeout)
    connection.setRequestMethod(requestMethod)
    val inputStream = connection.getInputStream
    val content = scala.io.Source.fromInputStream(inputStream).mkString
    if (inputStream != null) inputStream.close
    content
  }

  def GetEC2pkcs7(): String = {
    var pkcs7 = getHttpResponse("http://169.254.169.254/latest/dynamic/instance-identity/pkcs7", 100000, 10000, "GET").ResponseBody
    pkcs7 = pkcs7.split('\n').mkString
    pkcs7
  }

  def GetEC2Role(): String = {
    var role = getHttpResponse("http://169.254.169.254/latest/meta-data/iam/security-credentials/", 100000, 10000, "GET").ResponseBody
    role
  }

  /**
   * Returns the text (content) and response code from a REST URL as a String and int.
   *
   * @param url            The full URL to connect to.
   * @param connectTimeout Sets a specified timeout value, in milliseconds,
   *                       to be used when opening a communications link to the resource referenced
   *                       by this URLConnection. If the timeout expires before the connection can
   *                       be established, a java.net.SocketTimeoutException
   *                       is raised. A timeout of zero is interpreted as an infinite timeout.
   *                       Defaults to 10000 ms.
   * @param readTimeout    If the timeout expires before there is data available
   *                       for read, a java.net.SocketTimeoutException is raised. A timeout of zero
   *                       is interpreted as an infinite timeout. Defaults to 10000 ms.
   * @param requestMethod  Defaults to "GET". (Other methods have not been tested.)
   *
   */
  @throws(classOf[java.io.IOException])
  @throws(classOf[java.net.SocketTimeoutException])
  def getHttpResponse(url: String,
                      connectTimeout: Int = appConfig.http_timeout,
                      readTimeout: Int = appConfig.http_timeout,
                      requestMethod: String = "GET",
                      httpHeaders: Map[String, String] = Map.empty[String, String],
                      jsonBody: String = ""): HttpResponse = {
    import java.net.{HttpURLConnection, URL}

    var responseCode: Int = 0
    var content: String = null
    retry()

    def retry(): Unit = {

      var retry_count = 1
      val no_of_retries = appConfig.no_of_retries
      var vault_exception = StringBuilder.newBuilder

      val sleepTimeout = appConfig.sleeptimeout

      var connection: HttpURLConnection = null
      var exceptions: Boolean = false

      while (!exceptions) {
        try {
          connection = (new URL(url)).openConnection.asInstanceOf[HttpURLConnection]
          connection.setConnectTimeout(connectTimeout)
          connection.setReadTimeout(readTimeout)
          connection.setRequestMethod(requestMethod)
          httpHeaders.foreach(h => connection.setRequestProperty(h._1, h._2))

          if (jsonBody != "") {

            connection.setRequestMethod("POST")
            connection.setDoOutput(true)

            import java.io.{BufferedWriter, OutputStreamWriter}
            val out = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream))
            out.write(jsonBody)
            out.flush()
            out.close()
          }
          responseCode = connection.getResponseCode
          val inputStream = connection.getInputStream
          content = scala.io.Source.fromInputStream(inputStream).mkString
          if (inputStream != null) inputStream.close
          exceptions = true

        }
        catch {
          case ex: Exception => {
            println("Exception found is:" + ex)

            val sw = new StringWriter
            ex.printStackTrace()
            ex.printStackTrace(new PrintWriter(sw))

            val tmp_string = "Retry:" + retry_count + "-" + sw.toString

            vault_exception.append(tmp_string)

            if (retry_count == no_of_retries) {
              exceptions = true
              throw ex
            }
            Thread.sleep(sleepTimeout)
            retry_count += 1
          }
        } finally {
          if (connection != null) {
            connection.disconnect()
          }
        }
      }

    }

    HttpResponse(responseCode, content)
  }

  def buildSecureKafkaProperties(keyStorePath: Option[String],
                                 trustStorePath: Option[String],
                                 keyStorePassword: Option[String],
                                 trustStorePassword: Option[String],
                                 keyPassword: Option[String]): Map[String, String] = {

    var props = Map[String, String]()

    if ((!keyStorePath.isEmpty) || (!trustStorePath.isEmpty)) {
      props += ("kafka.security.protocol" -> "SSL")
      props += ("kafka.ssl.endpoint.identification.algorithm" -> "")
      if (!keyStorePath.isEmpty)
        props += ("kafka.ssl.keystore.location" -> keyStorePath.get)
      if (!trustStorePath.isEmpty)
        props += ("kafka.ssl.truststore.location" -> trustStorePath.get)
      if (!keyStorePassword.isEmpty)
        props += ("kafka.ssl.keystore.password" -> keyStorePassword.get)
      if (!trustStorePassword.isEmpty)
        props += ("kafka.ssl.truststore.password" -> trustStorePassword.get)
      if (!keyPassword.isEmpty)
        props += ("kafka.ssl.key.password" -> keyPassword.get)
    }
    props
  }

  def GetToAvroConfig(topic: String, schemaRegistryUrl: String, dfColumn: Column, schemaVersion: Option[Int] = None, isKey: Boolean = false, subjectNamingStrategy: String = "TopicNameStrategy" /*other options are RecordNameStrategy, TopicRecordNameStrategy*/ , subjectRecordName: Option[String] = None, subjectRecordNamespace: Option[String] = None, sslSettings: Map[String, String]): ToAvroConfig = {
    //get the specified schema version
    //if not specified, then get the latest schema from Schema Registry
    //if the topic does not have a schema then create and register the schema
    //applies to both key and value
    val subject = if (subjectNamingStrategy.equalsIgnoreCase("TopicRecordNameStrategy")) SchemaSubject.usingTopicRecordNameStrategy(topicName = topic, recordName = subjectRecordName.getOrElse(""), recordNamespace = subjectRecordNamespace.getOrElse("")) else if (subjectNamingStrategy.equalsIgnoreCase("RecordNameStrategy")) SchemaSubject.usingRecordNameStrategy(recordName = subjectRecordName.getOrElse(""), recordNamespace = subjectRecordNamespace.getOrElse("")) else SchemaSubject.usingTopicNameStrategy(topicName = topic, isKey = isKey) // Use isKey=true for the key schema and isKey=false for the value schema
    val schemaRegistryClientConfig = Map(AbrisConfig.SCHEMA_REGISTRY_URL -> schemaRegistryUrl) ++ sslSettings
    val schemaManager = SchemaManagerFactory.create(schemaRegistryClientConfig)
    val expression = dfColumn.expr
    val dataSchema = SchemaConverters.toAvroType(expression.dataType, expression.nullable)
    println((if (isKey) "key" else "value") + " subject = " + subject.asString)
    println((if (isKey) "key" else "value") + " avro schema inferred from data  = " + dataSchema.toString())
    var toAvroConfig: ToAvroConfig = null
    if (schemaManager.exists(subject)) {
      val avroConfigFragment = AbrisConfig
        .toConfluentAvro
      val toStrategyConfigFragment: ToStrategyConfigFragment = if (schemaVersion.isEmpty) avroConfigFragment.downloadSchemaByLatestVersion
      else avroConfigFragment.downloadSchemaByVersion(schemaVersion.get)
      val schemaDownloadingConfigFragment: ToSchemaDownloadingConfigFragment = if (subjectNamingStrategy.equalsIgnoreCase("TopicRecordNameStrategy")) toStrategyConfigFragment.andTopicRecordNameStrategy(topicName = topic, recordName = subjectRecordName.getOrElse(""), recordNamespace = subjectRecordNamespace.getOrElse("")) else if (subjectNamingStrategy.equalsIgnoreCase("RecordNameStrategy")) toStrategyConfigFragment.andRecordNameStrategy(recordName = subjectRecordName.getOrElse(""), recordNamespace = subjectRecordNamespace.getOrElse("")) else toStrategyConfigFragment.andTopicNameStrategy(topic, isKey = isKey)
      toAvroConfig = schemaDownloadingConfigFragment
        .usingSchemaRegistry(schemaRegistryUrl)
    }
    else {
      val schemaId = schemaManager.register(subject, dataSchema)
      toAvroConfig = AbrisConfig
        .toConfluentAvro
        .downloadSchemaById(schemaId)
        .usingSchemaRegistry(schemaRegistryUrl)
    }
    println((if (isKey) "key" else "value") + " avro schema expected by schema registry  = " + toAvroConfig.schemaString)
    toAvroConfig
  }

  def GetFromAvroConfig(topic: String, schemaRegistryUrl: String, schemaVersion: Option[Int] = None, isKey: Boolean = false, subjectNamingStrategy: String = "TopicNameStrategy" /*other options are RecordNameStrategy, TopicRecordNameStrategy*/ , subjectRecordName: Option[String] = None, subjectRecordNamespace: Option[String] = None, sslSettings: Map[String, String]): FromAvroConfig = {
    //get the specified schema version
    //if not specified, then get the latest schema from Schema Registry
    //if the topic does not have a schema then create and register the schema
    //applies to both key and value
    val subject = if (subjectNamingStrategy.equalsIgnoreCase("TopicRecordNameStrategy")) SchemaSubject.usingTopicRecordNameStrategy(topicName = topic, recordName = subjectRecordName.getOrElse(""), recordNamespace = subjectRecordNamespace.getOrElse("")) else if (subjectNamingStrategy.equalsIgnoreCase("RecordNameStrategy")) SchemaSubject.usingRecordNameStrategy(recordName = subjectRecordName.getOrElse(""), recordNamespace = subjectRecordNamespace.getOrElse("")) else SchemaSubject.usingTopicNameStrategy(topicName = topic, isKey = isKey) // Use isKey=true for the key schema and isKey=false for the value schema
    val schemaRegistryClientConfig = Map(AbrisConfig.SCHEMA_REGISTRY_URL -> schemaRegistryUrl) ++ sslSettings
    val schemaManager = SchemaManagerFactory.create(schemaRegistryClientConfig)
    println((if (isKey) "key" else "value") + " subject = " + subject.asString)
    var fromAvroConfig: FromAvroConfig = null
    val avroConfigFragment = AbrisConfig
      .fromConfluentAvro
    var fromStrategyConfigFragment: FromStrategyConfigFragment = null
    if (schemaVersion.isEmpty) {
      fromStrategyConfigFragment = avroConfigFragment.downloadReaderSchemaByLatestVersion
    }
    else {
      fromStrategyConfigFragment = avroConfigFragment.downloadReaderSchemaByVersion(schemaVersion.get)
    }

    val fromSchemaDownloadingConfigFragment = if (subjectNamingStrategy.equalsIgnoreCase("TopicRecordNameStrategy")) fromStrategyConfigFragment.andTopicRecordNameStrategy(topicName = topic, recordName = subjectRecordName.getOrElse(""), recordNamespace = subjectRecordNamespace.getOrElse("")) else if (subjectNamingStrategy.equalsIgnoreCase("RecordNameStrategy")) fromStrategyConfigFragment.andRecordNameStrategy(recordName = subjectRecordName.getOrElse(""), recordNamespace = subjectRecordNamespace.getOrElse("")) else fromStrategyConfigFragment.andTopicNameStrategy(topicName = topic, isKey = isKey)

    fromAvroConfig = fromSchemaDownloadingConfigFragment
      .usingSchemaRegistry(schemaRegistryUrl)

    println((if (isKey) "key" else "value") + " avro schema expected by schema registry  = " + fromAvroConfig.schemaString)
    fromAvroConfig
  }

  def buildRdbmsURI(platform: String, server: String, port: String, database: String, isWindowsAuthenticated: Boolean, domainName: String, typeForTeradata: Option[String], sslEnabled: Boolean): Map[String, String] = {

    var driver: String = null
    var url: String = null

    if (platform == "mssql") {
      if (isWindowsAuthenticated) {
        driver = "net.sourceforge.jtds.jdbc.Driver"
        url = "jdbc:jtds:sqlserver://" + server + ":" + (if (port == null) "1433" else port) + "/" + database + ";domain=" + domainName + ";useNTLMv2=true"
      }
      else {
        driver = "com.microsoft.sqlserver.jdbc.SQLServerDriver"
        url = "jdbc:sqlserver://" + server + ":" + (if (port == null) "1433" else port) + ";database=" + database
      }
    }
    else if (platform == "oracle") {
      driver = "oracle.jdbc.driver.OracleDriver"
      url = "jdbc:oracle:thin:@//" + server + ":" + (if (port == null) "1521" else port) + "/" + database
    }
    else if (platform == "teradata") {
      driver = "com.teradata.jdbc.TeraDriver"
      url = buildTeradataURI(server, database, if (port == null) None else Some(port.toInt), isWindowsAuthenticated, typeForTeradata = typeForTeradata)
    }
    else if (platform == "mysql") {
      driver = "com.mysql.jdbc.Driver"
      url = "jdbc:mysql://" + server + ":" + (if (port == null) "3306" else port) + "/" + database + "?rewriteBatchedStatements=true&cachePrepStmts=true"
    }

    else if (platform == "postgres") {
      driver = "org.postgresql.Driver"
      url = "jdbc:postgresql://" + server + ":" + (if (port == null) "5432" else port) + "/" + database + (if (sslEnabled == true) "?sslmode=require" else "")
    }
    Map("driver" -> driver, "url" -> url)
  }

  def buildTeradataURI(server: String, database: String, port: Option[Int], isWindowsAuthenticated: Boolean, typeForTeradata: Option[String]): String = {
    "jdbc:teradata://" + server + "/" + (if (isWindowsAuthenticated) "LOGMECH=LDAP," else "") + "TYPE=" + typeForTeradata.getOrElse("DEFAULT") + ",DATABASE=" + database + ",TMODE=TERA,DBS_PORT=" + port.getOrElse(1025).toString
  }

  def buildMongoURI(login: String, password: String, cluster: String, replicaSet: String, autheticationDatabase: String, database: String, collection: String, authenticationEnabled: Boolean, sslEnabled: String): String = {
    if (authenticationEnabled) {
      "mongodb://" + URLEncoder.encode(login, "UTF-8") + ":" + URLEncoder.encode(password, "UTF-8") + "@" + cluster + ":27017/" + database + "." + collection + "?authSource=" + (if (autheticationDatabase != "") autheticationDatabase else "admin") + (if (replicaSet == null) "" else "&replicaSet=" + replicaSet) + (if (sslEnabled == "true") "&ssl=true&sslInvalidHostNameAllowed=true" else "")
    } else {
      "mongodb://" + cluster + ":27017/" + database + "." + collection + (if (replicaSet == null) "" else "&replicaSet=" + replicaSet)
    }
  }

  class CustomListOfExceptions(message: String) extends Exception(message) {

    def this(message: String, cause: Throwable) {
      this(message)
      initCause(cause)
    }

    def this(cause: Throwable) {
      this(Option(cause).map(_.toString).orNull, cause)
    }

    def this() {
      this(null: String)
    }
  }

  // Bypasses both client and server validation.
  object TrustAll extends X509TrustManager {
    val getAcceptedIssuers = null

    def checkClientTrusted(x509Certificates: Array[X509Certificate], s: String) = {}

    def checkServerTrusted(x509Certificates: Array[X509Certificate], s: String) = {}
  }

  // Verifies all host names by simply returning true.
  object VerifiesAllHostNames extends HostnameVerifier {
    def verify(s: String, sslSession: SSLSession) = true
  }

}

case class HttpResponse(ResponseCode: Int, ResponseBody: String)
