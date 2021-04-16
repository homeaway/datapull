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

import java.io.{PrintWriter, StringWriter}
import java.time.Instant
import java.util.concurrent.Executors

import com.amazonaws.services.simpleemail.model.{Body, Content, Destination}
import config.AppConfig
import javax.mail._
import helper._
import javax.mail.internet.{InternetAddress, MimeMessage}
import logging._
import org.codehaus.jettison.json.JSONArray
import security._

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.Duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.control.Breaks

class Controller(appConfig: AppConfig, pipeline: String) {

  val reportbodyHtml = StringBuilder.newBuilder
  var s3TempFolderDeletionError = StringBuilder.newBuilder
  var migrationErrors = ListBuffer[String]()
  val alerts = new Alert(appConfig)
  val dataPullLogs = new DataPullLog(appConfig, pipeline)
  var env: String = null
  val helper = new Helper(appConfig)

  def neatifyReportHtml(reportRowsHtml: String, reportCounts: Boolean, custom_retries: Boolean): String = {
    //make a table out of the body
    val reportTableHtml = "<table id=\"reportTable\"><thead><tr><th>Source Platform</th><th>Destination Platform</th><th>Start Time</th><th>End Time</th>" + (if (reportCounts) "<th>Records Processed</th>" else "") + "<th>Verification</th></tr></thead><tbody>" + reportRowsHtml + "</tbody></table>"

    var emailBody = "<style>table#reportTable {\n    font-size: 16px;\n    font-family: \"Trebuchet MS\", Arial, Helvetica, sans-serif;\n    border-collapse: collapse;\n    border-spacing: 0;\n    width: 100%;\nborder-color: grey;\n}\n#reportTable th {\n    padding-top: 11px;\n    padding-bottom: 11px;\n    background-color: #4CAF50;\n    color: white;\n}\n#reportTable td, #reportTable th {\n    border: 1px solid #ddd;\n   padding: 8px;\n}\n</style><table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" height=\"100%\" width=\"100%\" id=\"bodyTable\">\n    <tr>\n        <td align=\"center\" valign=\"top\">\n            <table border=\"0\" cellpadding=\"20\" cellspacing=\"0\" width=\"800\" id=\"emailContainer\">\n                <tr>\n                    <td align=\"center\" valign=\"top\">\n                        <table border=\"0\" cellpadding=\"20\" cellspacing=\"0\" width=\"100%\" id=\"emailHeader\">\n                            <tr>\n                                <td align=\"center\" valign=\"top\">\n                                    EMAILHEADERCONTENT\n                                </td>\n                            </tr>\n                        </table>\n                    </td>\n                </tr>\n                <tr>\n                    <td align=\"center\" valign=\"top\">\n                        <table border=\"0\" cellpadding=\"20\" cellspacing=\"0\" width=\"100%\" id=\"emailBody\">\n                            <tr>\n                                <td align=\"center\" valign=\"top\">\n                                    EMAILBODYCONTENT\n                                </td>\n                            </tr>\n                        </table>\n                    </td>\n                </tr>\n      S3FOLDERDELETIONERROR          <tr>\n                    <td align=\"center\" valign=\"top\">\n                        <table border=\"0\" cellpadding=\"20\" cellspacing=\"0\" width=\"100%\" id=\"emailFooter\">\n                            <tr>\n                                <td align=\"center\" valign=\"top\">\n                                    EMAILFOOTERCONTENT\n                                </td>\n                            </tr>\n                        </table>\n                    </td>\n                </tr>\n            </table>\n        </td>\n    </tr>\n</table><img src=\"https://www.google-analytics.com/collect?v=1&tid=UA-93072328-4&cid=" + math.abs(scala.util.Random.nextInt()).toString() + "." + math.abs(scala.util.Random.nextInt()).toString() + "&t=event&ec=email&ea=open&dp=/email/datamigrationreport&dt=DataPull Report\"/>"
    emailBody = emailBody.replaceFirst("EMAILHEADERCONTENT", "<h2>DataPull Report</h2>")
    emailBody = emailBody.replaceFirst("EMAILBODYCONTENT", java.util.regex.Matcher.quoteReplacement(reportTableHtml))
    emailBody = emailBody.replaceFirst("EMAILFOOTERCONTENT", "<h5>If you have questions or concerns, please contact the slack channel #des-coe-tools for assistance</h5>")
    if (s3TempFolderDeletionError.nonEmpty) {
      emailBody = emailBody.replaceFirst("S3FOLDERDELETIONERROR", "<tr>\n   <td align=\"center\" valign=\"top\">\n   <table border=\"0\" cellpadding=\"5\" cellspacing=\"0\" width=\"100%\" id=\"notification\">\n  <tr>\n  <td align=\"left\" valign=\"top\">\n      " + s3TempFolderDeletionError + "      \n    </td>\n   </tr>\n   </table>\n    </td>\n  </tr>\n")
    } else {
      emailBody = emailBody.replaceFirst("S3FOLDERDELETIONERROR", "")

    }
    emailBody
  }

  def performmigration(migrations: JSONArray, parallelmigrationflag: Boolean, reportEmailAddress: String, verifymigration: Boolean, reportCounts: Boolean, no_of_retries: Int, custom_retries: Boolean, jobId: String, sparkSession: org.apache.spark.sql.SparkSession, masterNode: String, ec2Role: String, portfolio: String, product: String, jsonString: String, stepSubmissionTime: String, minexecutiontime: String, maxexecutiontime: String, start_time_in_milli: Long, applicationId: String, pipelineName: String, awsenv: String, preciseCounts: Boolean, failureThreshold: Int, authenticatedUser: String, failureEmailAddress: String): Unit = {
    try {
      env = awsenv
      if (parallelmigrationflag) {
        var migrationJsonStringArray = List.empty[String]
        for (i <- 0 to migrations.length() - 1) {
          migrationJsonStringArray = migrationJsonStringArray :+ migrations.getJSONObject(i).toString()
        }

        implicit val ec = new ExecutionContext {
          val threadPool = Executors.newFixedThreadPool(1500)

          def execute(runnable: Runnable) {
            threadPool.submit(runnable)
          }

          def reportFailure(t: Throwable): Unit = {
            println("Unable to create the requested thread pool")
          }
        }

        var pool = 0

        //TODO check if this threadsafe
        // this is to have different pools per job, you can wrap it to limit the no. of pools
        def poolId = {
          pool = pool + 1
          pool.toString
        }

        val migration = new Migration()

        def runner(migrationJsonString: String) = Future {
          migration.migrate(migrationJsonString, reportEmailAddress, poolId, verifymigration, reportCounts, no_of_retries, custom_retries, jobId, sparkSession.sparkContext.isLocal, preciseCounts, appConfig, pipelineName)
        }

        try {
          val futures = migrationJsonStringArray map (i => runner(i))

          futures foreach (i => {
            val migrationResult: Map[String, String] = Await.result(i, Inf)
            reportbodyHtml.append(migrationResult("reportRowHtml"))
            if (migrationResult("migrationError") != null)
              migrationErrors += migrationResult("migrationError")
            if (migrationResult("deletionError") != null) {
              s3TempFolderDeletionError.append(migrationResult("deletionError")).append("<br>")
            }
          })
          ec.threadPool.shutdown()
        }
        finally {
          if (!ec.threadPool.isShutdown) {
            ec.threadPool.shutdownNow()
          }
        }

      }
      else {
        val breakableLoop = new Breaks;
        breakableLoop.breakable {
          for (i <- 0 to migrations.length() - 1) {
            var migration = new Migration()
            val migrationResult: Map[String, String] = migration.migrate(migrations.getJSONObject(i).toString(), reportEmailAddress, i.toString, verifymigration, reportCounts, no_of_retries, custom_retries, jobId, sparkSession.sparkContext.isLocal, preciseCounts, appConfig, pipelineName)
            reportbodyHtml.append(migrationResult("reportRowHtml"))
            if (migrationResult("migrationError") != null)
              migrationErrors += migrationResult("migrationError")
            if (migrationResult("deletionError") != null) {
              s3TempFolderDeletionError.append(migrationResult("deletionError")).append("<br>")

            }
            if (!migrationErrors.isEmpty) {
              if (migrationErrors.size >= failureThreshold) {
                reportAbortForAllPendingMigrations(i + 1, migrations)
                breakableLoop.break();
              }
            }
          }
        }
      }
    }

    catch {
      case e: Exception => {
        val sw = new StringWriter
        e.printStackTrace()
        e.printStackTrace(new PrintWriter(sw))
        reportbodyHtml.append("<tr><td><h3>Errors!</h3></td><td>" + Instant.now().toString() + "</td><td colspan=\"5\">" + sw.toString() + "</td></tr>")
        migrationErrors += sw.toString()
      }
    }
    finally {
      val updatedBodyHtml = neatifyReportHtml(reportbodyHtml.toString(), reportCounts, custom_retries)
      var elapsedtime: Long = 0
      //send email if provided
      if (reportEmailAddress != "") {
        val reportBody = reportbodyHtml.toString();
        SendEmail(reportEmailAddress, updatedBodyHtml, applicationId, pipelineName, env, null, authenticatedUser)
        dataPullLogs.dataPullLogging(jobId, masterNode, ec2Role, portfolio, product, jsonString, stepSubmissionTime, Instant.now().toString, System.currentTimeMillis() - start_time_in_milli, "Succeeded", reportBody, sparkSession)
      }
      if (migrationErrors.nonEmpty) {
        dataPullLogs.dataPullLogging(jobId, masterNode, ec2Role, portfolio, product, jsonString, stepSubmissionTime, Instant.now().toString, System.currentTimeMillis() - start_time_in_milli, "Failed", migrationErrors.toString(), sparkSession)
        if (minexecutiontime != "" && maxexecutiontime != "") {
          elapsedtime = System.currentTimeMillis() - start_time_in_milli
          alerts.AlertLog(jobId, masterNode, ec2Role, portfolio, product, elapsedtime / 1000, minexecutiontime.toLong, maxexecutiontime.toLong, sparkSession, "Failed", reportEmailAddress, pipelineName, awsenv, appConfig.dataToolsEmailAddress)

          if (failureEmailAddress != "") {
            SendEmail(failureEmailAddress, updatedBodyHtml, applicationId, pipelineName, env, "Data Pull job failed for the Pipeline:" + awsenv + "- " + pipelineName + "-Pipeline (" + applicationId + ")", authenticatedUser)
          }
        }
        throw new helper.CustomListOfExceptions(migrationErrors.mkString("\n"))
      }
      if (migrationErrors.isEmpty) {
        dataPullLogs.dataPullLogging(jobId, masterNode, ec2Role, portfolio, product, jsonString, stepSubmissionTime, Instant.now().toString, System.currentTimeMillis() - start_time_in_milli, "Completed", null, sparkSession)
        if (minexecutiontime != "" && maxexecutiontime != "") {
          //This will log in case of no exceptions with completed status.
          elapsedtime = System.currentTimeMillis() - start_time_in_milli

          alerts.AlertLog(jobId, masterNode, ec2Role, portfolio, product, elapsedtime / 1000, minexecutiontime.toLong, maxexecutiontime.toLong, sparkSession, "Completed", reportEmailAddress, pipelineName, awsenv, appConfig.dataToolsEmailAddress)

        }
      }
    }
  }

  def SendEmail(emailAddress: String, htmlContent: String, applicationId: String, pipelineName: String, env: String, subject: String, authenticatedUser: String): Unit = {
    // Set up the mail object

    var username: String = null
    var password: String = null
    var session: Session = null
    var subject_local: String = subject
    var toAddresses = emailAddress

    if (subject == null) {
      subject_local = "Data Pull Report - " + pipelineName + " (" + applicationId + ")"
    }

    if (authenticatedUser != null || authenticatedUser.nonEmpty) {
      toAddresses = toAddresses + "," + emailAddress
    }

    if (appConfig.smtpServerAddress != "SMTP_SERVER") {
      if (!isNullOrEmpty(emailAddress)) {
        if (!isNullOrEmpty(appConfig.smtpServerAddress)) {
          val properties = System.getProperties
          properties.put("mail.smtp.host", appConfig.smtpServerAddress)

          if (appConfig.smtpTlsEnable == "true") {
            properties.put("mail.smtp.starttls.enable", "true")
          }

          if (appConfig.smtpPort != "") {
            properties.put("mail.smtp.port", appConfig.smtpPort)
          }

          if (appConfig.smtpAuthEnable == "true") {

            properties.put("mail.smtp.auth", "true")

            username = appConfig.smtpUsername
            password = appConfig.smtpPassword

            if (password == "" || password == null) {

              val secretService = new SecretService(appConfig.smtpPasswordSecretStore, appConfig)
              password = secretService.getSecret(appConfig.smtpPasswordVaultPath, appConfig.smtpPasswordVaultKey, env)

            }
            session = Session.getDefaultInstance(properties, new MailAuthenticator(username, password))
          } else {
            session = Session.getDefaultInstance(properties)
          }

          val message = new MimeMessage(session)

          // Set the from, to, subject, body text
          message.setFrom(new InternetAddress(appConfig.dataToolsEmailAddress))
          message.setRecipients(Message.RecipientType.TO, toAddresses)
          message.setRecipients(Message.RecipientType.BCC, "" + appConfig.dataToolsEmailAddress)
          message.setSubject(subject_local)
          message.setContent(htmlContent, "text/html; charset=utf-8")
          // And send it
          Transport.send(message)
        }
        else if (!isNullOrEmpty(appConfig.sesFromEmail)) {
          import com.amazonaws.services.simpleemail.model.SendEmailRequest
          val emailFrom = appConfig.sesFromEmail
          val request = new SendEmailRequest().withSource(emailFrom)
            .withDestination(new Destination().withToAddresses(toAddresses.split(",|;"): _*))
            .withMessage(new com.amazonaws.services.simpleemail.model.Message().withBody(new Body().withHtml(new Content().withData(htmlContent).withCharset("UTF-8"))).withSubject(new Content().withData(subject_local)))

          request.setReturnPath(emailFrom);

          val result = appConfig.getSESClient().sendEmail(request);
        }
      }
    }
  }

  private def isNullOrEmpty(value: String): Boolean = {
    return value == null || value.isEmpty;
  }

  private class MailAuthenticator(userName: String, passWord: String) extends Authenticator {
    override protected def getPasswordAuthentication: PasswordAuthentication = new PasswordAuthentication(userName, passWord)
  }

  def reportAbortForAllPendingMigrations(startIndex: Int, migrations: JSONArray) = {
    val migration = new Migration();
    for (i <- startIndex to migrations.length() - 1) {
      val migrationJSON = migrations.getJSONObject(i)
      var sources = new JSONArray()
      if (migrationJSON.has("sources")) {
        sources = migrationJSON.getJSONArray("sources")
      }
      if (migrationJSON.has("source")) {
        sources.put(migrationJSON.getJSONObject("source"));
      }
      val reportRowHtml = StringBuilder.newBuilder
      reportRowHtml.append("<tr><td>")
      for (a <- 0 to sources.length() - 1) {
        var selectedSource = sources.getJSONObject(a)
        var platform = selectedSource.getString("platform")
        reportRowHtml.append(migration.printableSourceTargetInfo(selectedSource))
      }
      val destination = migrationJSON.getJSONObject("destination")
      reportRowHtml.append("</td><td>")
      reportRowHtml.append(migration.printableSourceTargetInfo(destination) + "</td>")
      reportRowHtml.append("</td><td colspan=\"" + ("4") + "\"\">" + "SKIPPED!!!" + "</td></tr>")
      reportbodyHtml.append(reportRowHtml)
    }
  }

}
