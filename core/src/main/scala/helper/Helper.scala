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

class Helper(appConfig: AppConfig) {

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

  def buildSecureKafkaProperties(bootstrapServers: String,
                                 schemaRegistries: String,
                                 keyStorePath: String,
                                 trustStorePath: String,
                                 keyStorePassword: String,
                                 trustStorePassword: String,
                                 keyPassword: String): Map[String, String] = {

    var props = Map("bootstrap.servers" -> bootstrapServers, "schema.registry.url" -> schemaRegistries)

    if (keyStorePath != "null" || trustStorePath != "null") {
      props += (CommonClientConfigs.SECURITY_PROTOCOL_CONFIG -> "SSL")
      if (keyStorePath != null)
        props += (SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG -> keyStorePath)
      if (trustStorePath != null)
        props += (SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG -> trustStorePath)
      if (keyStorePassword != null)
        props += (SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG -> keyStorePassword)
      if (trustStorePassword != null)
        props += (SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG -> trustStorePassword)
      if (keyPassword != null)
        props += (SslConfigs.SSL_KEY_PASSWORD_CONFIG -> keyPassword)
      props += (SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG -> "")
    }

    props

  }

  def buildMongoURI(login: String, password: String, cluster: String, replicaSet: String, autheticationDatabase: String, database: String, collection: String, authenticationEnabled: Boolean, sslEnabled: String): String = {
    if (authenticationEnabled) {
      "mongodb://" + URLEncoder.encode(login, "UTF-8") + ":" + URLEncoder.encode(password, "UTF-8") + "@" + cluster + ":27017/" + database + "." + collection + "?authSource=" + (if (autheticationDatabase != "") autheticationDatabase else "admin") + (if (replicaSet == null) "" else "&replicaSet=" + replicaSet) + (if (sslEnabled == "true") "&ssl=true&sslInvalidHostNameAllowed=true" else "")
    } else {
      "mongodb://" + cluster + ":27017/" + database + "." + collection + (if (replicaSet == null) "" else "&replicaSet=" + replicaSet)
    }
  }

  def buildTeradataURI(server: String, database: String, port: Option[Int]): String = {
    "jdbc:teradata://" + server + "/TYPE=RAW,DATABASE=" + database + ",TMODE=TERA,DBS_PORT=" + port.getOrElse(1025).toString
  }
}

case class HttpResponse(ResponseCode: Int, ResponseBody: String)


