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
import core.DataFrameFromTo
import core.DataPull.{jsonObjectPropertiesToMap, setAWSCredentials}
import javax.net.ssl.{HostnameVerifier, SSLSession, X509TrustManager}
import org.apache.spark.sql.{DataFrame, Row, SparkSession}
import org.codehaus.jettison.json.JSONObject
import security.SecretService
import za.co.absa.abris.avro.parsing.utils.AvroSchemaUtils
import za.co.absa.abris.avro.read.confluent.SchemaManagerFactory
import za.co.absa.abris.avro.registry.SchemaSubject
import za.co.absa.abris.config._

import scala.collection.mutable.StringBuilder
import scala.util.control.Breaks.breakable

class Helper(appConfig: AppConfig) {

  val secretStoreDefaultValue: String = "vault"

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

  def GetEC2Role(): String = {
    var role = getHttpResponse("http://169.254.169.254/latest/meta-data/iam/security-credentials/", 100000, 10000, "GET").ResponseBody
    role
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

  def GetToAvroConfig(topic: String, schemaRegistryUrl: String, df: DataFrame, columnName: String, schemaVersion: Option[Int] = None, isKey: Boolean = false, subjectNamingStrategy: String = "TopicNameStrategy" /*other options are RecordNameStrategy, TopicRecordNameStrategy*/ , subjectRecordName: Option[String] = None, subjectRecordNamespace: Option[String] = None, sslSettings: Map[String, String]): ToAvroConfig = {
    //get the specified schema version
    //if not specified, then get the latest schema from Schema Registry
    //if the topic does not have a schema then create and register the schema
    //applies to both key and value
    val subject = if (subjectNamingStrategy.equalsIgnoreCase("TopicRecordNameStrategy")) SchemaSubject.usingTopicRecordNameStrategy(topicName = topic, recordName = subjectRecordName.getOrElse(""), recordNamespace = subjectRecordNamespace.getOrElse("")) else if (subjectNamingStrategy.equalsIgnoreCase("RecordNameStrategy")) SchemaSubject.usingRecordNameStrategy(recordName = subjectRecordName.getOrElse(""), recordNamespace = subjectRecordNamespace.getOrElse("")) else SchemaSubject.usingTopicNameStrategy(topicName = topic, isKey = isKey) // Use isKey=true for the key schema and isKey=false for the value schema
    val schemaRegistryClientConfig = Map(AbrisConfig.SCHEMA_REGISTRY_URL -> schemaRegistryUrl) ++ sslSettings
    val schemaManager = SchemaManagerFactory.create(schemaRegistryClientConfig)
    val dataSchema = AvroSchemaUtils.toAvroSchema(df, columnName)
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
      val schemasWithMetadata = schemaManager.getAllSchemasWithMetadata(subject)
      val schemaRegistrySchema = schemasWithMetadata.find(p => p.getVersion == schemaVersion.getOrElse(schemasWithMetadata.map(_.getVersion).max))
      println((if (isKey) "key" else "value") + " avro schema expected by schema registry  = " + (if (schemaRegistrySchema.isEmpty) "No schema matching version" else schemaRegistrySchema.get.getSchema))
    }
    else {
      val schemaId = schemaManager.register(subject, dataSchema)
      toAvroConfig = AbrisConfig
        .toConfluentAvro
        .downloadSchemaById(schemaId)
        .usingSchemaRegistry(schemaRegistryUrl)
    }
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
    if (schemaManager.exists(subject)) {
      val schemasWithMetadata = schemaManager.getAllSchemasWithMetadata(subject)
      val schemaRegistrySchema = schemasWithMetadata.find(p => p.getVersion == schemaVersion.getOrElse(schemasWithMetadata.map(_.getVersion).max))
      println((if (isKey) "key" else "value") + " avro schema in schema registry  = " + (if (schemaRegistrySchema.isEmpty) "No schema matching version" else schemaRegistrySchema.get.getSchema))
    }
    fromAvroConfig
  }

  def buildRdbmsURI(platform: String, server: String, port: String, database: String, isWindowsAuthenticated: Boolean, domainName: String, typeForTeradata: Option[String], sslEnabled: Boolean, addlJdbcOptions: JSONObject): Map[String, String] = {

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
      url = buildTeradataURI(server, database, if (port == null) None else Some(port.toInt), isWindowsAuthenticated, typeForTeradata = typeForTeradata, addlJdbcOptions = addlJdbcOptions)
    }
    else if (platform == "mysql") {
      driver = "com.mysql.jdbc.Driver"
      url = "jdbc:mysql://" + server + ":" + (if (port == null) "3306" else port) + "/" + database + "?rewriteBatchedStatements=true&cachePrepStmts=true"
    }

    else if (platform == "postgres") {
      driver = "org.postgresql.Driver"
      url = "jdbc:postgresql://" + server + ":" + (if (port == null) "5432" else port) + "/" + database + (if (sslEnabled == true) "?sslmode=require" else "")
    }
    println("logging the URI: " + url)
    Map("driver" -> driver, "url" -> url)
  }

  def buildTeradataURI(server: String, database: String, port: Option[Int], isWindowsAuthenticated: Boolean, typeForTeradata: Option[String], addlJdbcOptions: JSONObject): String = {
    var URI: String = null
    if (addlJdbcOptions != null && addlJdbcOptions.has("logging_level"))
      "jdbc:teradata://" + server + "/" + (if (isWindowsAuthenticated) "LOGMECH=LDAP," else "") + "TYPE=" + typeForTeradata.getOrElse("DEFAULT") + ",DATABASE=" + database + ",TMODE=TERA,DBS_PORT=" + port.getOrElse(1025).toString + ",LOG=" + jsonObjectPropertiesToMap(addlJdbcOptions).get("logging_level").get
    else
      "jdbc:teradata://" + server + "/" + (if (isWindowsAuthenticated) "LOGMECH=LDAP," else "") + "TYPE=" + typeForTeradata.getOrElse("DEFAULT") + ",DATABASE=" + database + ",TMODE=TERA,DBS_PORT=" + port.getOrElse(1025).toString
  }

  def buildMongoURI(login: String, password: String, cluster: String, replicaSet: String, autheticationDatabase: String, database: String, collection: String, authenticationEnabled: Boolean, sslEnabled: String): String = {
    if (authenticationEnabled) {
      "mongodb://" + URLEncoder.encode(login, "UTF-8") + ":" + URLEncoder.encode(password, "UTF-8") + "@" + cluster + ":27017/" + database + "." + collection + "?authSource=" + (if (autheticationDatabase != "") autheticationDatabase else "admin") + (if (replicaSet == null) "" else "&replicaSet=" + replicaSet) + (if (sslEnabled == "true") "&ssl=true&sslInvalidHostNameAllowed=true" else "")
    } else {
      "mongodb://" + cluster + ":27017/" + database + "." + collection + (if (replicaSet == null) "" else "&replicaSet=" + replicaSet)
    }
  }

  def showHTML(ds: org.apache.spark.sql.DataFrame, limit: Int = 100, truncate: Int = 100): String = {
    import xml.Utility.escape
    val data = ds.take(limit)
    val header = ds.schema.fieldNames.toSeq
    val rows: Seq[Seq[String]] = data.map { row =>
      rowToStrings(row, truncate)
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
    bodyHtml.toString

  }

  def ReplaceInlineExpressions(inputString: String, sparkSession: org.apache.spark.sql.SparkSession): String = {
    val RegexForInlineExpr = """inlineexpr\{\{(.*)}}""".r
    val returnVal = RegexForInlineExpr.replaceAllIn(inputString, _ match { case RegexForInlineExpr(inlineExprr) => println(inlineExprr);
      val df = sparkSession.sql(inlineExprr);
      val rows = df.take(1);
      if (rows.length == 1) {
        this.rowToStrings(rows(0), 0).mkString(",")
      } else ""
    })

    val RegexForInlineSecret = """inlinesecret\{\{(.*)}}""".r
    RegexForInlineSecret.replaceAllIn(returnVal, _ match { case RegexForInlineSecret(inlineExprr) => println(inlineExprr);
      val inlineExprrAsJson = new JSONObject(new JSONObject("{\"data\": \"{" + inlineExprr + "}\"}").getString("data"))
      if (inlineExprrAsJson.has("secretstore") && inlineExprrAsJson.has("secretname")) {
        val secretStore = inlineExprrAsJson.getString("secretstore")
        val secretName = inlineExprrAsJson.getString("secretname")
        var secretKeyName: Option[String] = None
        if (inlineExprrAsJson.has("secretkeyname")) {
          secretKeyName = Some(inlineExprrAsJson.getString("secretkeyname"))
        }
        val secretService = new SecretService(secretStore.toLowerCase(), appConfig)
        secretService.getSecret(secretName, secretKeyName, None)
      } else {
        ""
      }
    })

  }

  def ReplaceInlineExpressions(platformObject: JSONObject, optionalJsonPropertiesList:List[String]): JSONObject ={
    val RegexForJDBCInlineExpr = """inlineexprforjdbc\{\{(.*?)}}""".r
    println(platformObject)
    val returnVal=  RegexForJDBCInlineExpr.replaceAllIn(platformObject.toString, _ match { case RegexForJDBCInlineExpr(inlineExprr) => println(inlineExprr);
      val inlineexprforjdbcasJson=  new JSONObject(new JSONObject("{\"data\": \"{" + inlineExprr + "}\"}").getString("data"))
      val propertiesMap = jsonObjectPropertiesToMap(jsonObject = platformObject ) ++ jsonObjectPropertiesToMap(optionalJsonPropertiesList, platformObject)
      println(propertiesMap)
      val dataframeFromTo = new DataFrameFromTo(appConfig, "test")
      val colType=  jsonObjectPropertiesToMap(inlineexprforjdbcasJson).get("coltype")
      val rs=  dataframeFromTo.rdbmsRunCommand(
        platform = platformObject.getString("platform"),
        awsEnv = propertiesMap("awsenv"),
        server = propertiesMap("server"),
        port = propertiesMap.getOrElse("port", null),
        sslEnabled = propertiesMap.getOrElse("sslenabled", "false").toBoolean,
        database = propertiesMap("database"),
        sql_command = inlineexprforjdbcasJson.getString("sql"),
        login = propertiesMap("login"),
        password = propertiesMap("password"),
        vaultEnv = propertiesMap("vaultenv"),
        secretStore = propertiesMap.getOrElse("secretstore", secretStoreDefaultValue),
        isWindowsAuthenticated = propertiesMap.getOrElse("iswindowsauthenticated", propertiesMap.getOrElse("isWindowsAuthenticated", "false")).toBoolean,
        domainName = propertiesMap.getOrElse("domain", null),
        typeForTeradata = propertiesMap.get("typeforteradata"),
        colType = colType
      )
      var returnString: String= null
      while (rs.next()) {
        if (colType.toString.equals("Some(int)")) {
          returnString=  String.valueOf(rs.getInt(1))
        } else if (colType.toString.equals("Some(string)")) {
          returnString= rs.getString(1)
        } else if (colType.toString.equals("Some(float)")) {
          returnString= String.valueOf(rs.getFloat(1))
        } else if (colType.toString.equals("Some(date)")) {
          returnString= String.valueOf(rs.getDate(1))
        } else if (colType.toString.equals("Some(long)")) {
          returnString= String.valueOf(rs.getLong(1))
        } else {
          returnString= String.valueOf(rs.getInt(1))
        }
      }
      println(returnString)
      returnString
    })
    println(returnVal)
    new JSONObject(returnVal)
  }

  private def rowToStrings(row: Row, truncate: Int): Seq[String] = {
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

  //Get string from input file specificed in json
  def InputFileJsonToString(sparkSession: SparkSession, jsonObject: JSONObject, inputFileObjectKey: String = "inputfile"): Option[String] = {
    var retVal: Option[String] = None
    if (jsonObject.has(inputFileObjectKey)) {
      breakable {
        val jsonMap = jsonObjectPropertiesToMap(jsonObject.getJSONObject(inputFileObjectKey))
        var inputFile = ""
        if (jsonMap.contains("s3path")) {
          val s3Prefix = if (sparkSession.sparkContext.master == "local[*]") "s3a" else "s3"
          setAWSCredentials(sparkSession, jsonMap)
          inputFile = s3Prefix + "://" + jsonMap("s3path")
        }
        else {
          inputFile = jsonMap("path")
        }
        val rddjson = sparkSession.sparkContext.wholeTextFiles(inputFile)
        retVal = Some(rddjson.first()._2)
      }
    }
    retVal
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
