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

package logging


import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}

import com.amazonaws.services.logs.model.{DescribeLogStreamsRequest, InputLogEvent, PutLogEventsRequest}
import config.AppConfig
import core.DataFrameFromTo
import org.apache.spark.sql.SparkSession

case class tableJobHistory(MigrationId: String, JobId: String,SourceDestinationMappings: String, StartTime: String,EndTime : String,ElapsedTimeInMinutes: String,  Count: Long, Size: Long, Status: String)
case class tableMigrationHistory(_id:String,JobId: String, Portfolio: String,Product:String, MasterNode: String, Ec2Role: String,MigrationJSON : String, SubmissionTime: String, EndTime: String, ElapsedTimeInMinutes: String, Status: String, Errors: String)

class DataPullLog(appConfig: AppConfig, pipeline : String) extends Logger {

  /**
    * Inserts Job level metrics to s3
    * @param migrationId  : Unique job id
    * @param startTime : Job started at
    * @param endTime : Job Finished at
    * @param elapsed : Time taken for the job to complete
    * @param jsonString : core.Migration JSON
    * @param count : Number of records processed for each core.Migration job in a DataPull
    * @param size : Size of the data processed for a job
    * @param status : Status of the job and the value will be among Started/Completed/Failed.
    */
  def jobLog(migrationId: String,startTime: String, endTime: String,elapsed: Long,jsonString: String,  count: Long, size: Long,status: String,jobId: String, sparkSession: SparkSession): Unit={
    val jsonField = null
    var elapsed_time = elapsed/60000

    if(status != "Started" && elapsed_time == 0)
      elapsed_time =1
    else
      elapsed_time
    val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("UTC"))
    val prefix = "MigrationHistory/LogTime=" + DATE_TIME_FORMATTER.format(Instant.now().atZone(ZoneId.of("UTC")))  + "/JobId="+jobId+"/MigrationId="+migrationId

    val df = sparkSession.createDataFrame(Seq(tableJobHistory(migrationId,jobId,jsonString,startTime,endTime,elapsed_time.toString,count,size, status)))

        persistLog(df, prefix, sparkSession)
  }

  /**
    * Inserts Job level metrics to s3
    * @param jobId  : Unique job id
    * @param master : Master ip address of the spark cluster.
    * @param ec2Role : The role of with which emr cluster is running with.
    * @param portfolio : Portfolio information of the cluster/user.
    * @param product : Product information of the cluster/user.
    * @param migrationJson : User JSON which used for the DataPull.
    * @param stepSubmissionTime : This stores the timestamp of the job's start time.
    * @param endTime : This stores the timestamp of the job's end time.
    * @param elapsed : Time taken for the job to complete
    * @param status : Status of the job and the value will be among Started/Completed/Failed.
    * @param errors : It will store if there are any exceptions/errors in a DataPull.
    * @param sparkSession : Spark session object.
    *
    */
  def dataPullLogging(jobId: String, master: String, ec2Role: String,portfolio:String,product:String, migrationJson: String,stepSubmissionTime: String,endTime: String,elapsed: Long,status: String,errors: String,sparkSession: SparkSession): Unit={
    var elapsed_time= elapsed/60000
    if(status != "Started" && elapsed_time == 0)
      elapsed_time =1

    val df = sparkSession.createDataFrame(Seq(tableMigrationHistory(jobId,jobId,portfolio,product,master,ec2Role,migrationJson,stepSubmissionTime,endTime,elapsed_time.toString, status,errors)))
    val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("UTC"))
    val prefix = "DataPullHistory/LogTime=" + DATE_TIME_FORMATTER.format(Instant.now().atZone(ZoneId.of("UTC"))) + "/JobId="+jobId
    persistLog(df, prefix, sparkSession)
  }

  /**
    * Persists the log in the form of a dataframe , to disk. If running locally, this is the filesystem; else it is S3
    */
  def persistLog(df: org.apache.spark.sql.DataFrame, logSubFolder: String, sparkSession: SparkSession):Unit = {
    val dataframeFromTo = new DataFrameFromTo(appConfig, pipeline)
    val s3loggingfolder= appConfig.s3bucket+"/datapull-opensource/logs/"
    dataframeFromTo.dataFrameToFile(s3loggingfolder + logSubFolder, "json", "", "Append", df, !(sparkSession.sparkContext.isLocal), null, sparkSession, null, false, null, null, null, null, null, "false", null)

    if (sparkSession.sparkContext.isLocal == false) {
      logDataFrameToCloudWatch(appConfig.cloudWatchLogGroup, appConfig.cloudWatchLogStream, appConfig.cloudWatchLogRegion, appConfig.accessKeyForCloudWatchLog, appConfig.secretKeyForCloudWatchLog, df, sparkSession);
    }
  }

  def logDataFrameToCloudWatch(groupName : String, streamName : String, region : String, accessKey : String, secretKey : String, df : org.apache.spark.sql.DataFrame, sparkSession: SparkSession): Unit = {
    if(groupName == null || groupName.trim.isEmpty ||
      streamName == null || streamName.trim.isEmpty )
      return;

    val awsLogsClient = appConfig.getCloudWatchClient(region);
    val logEvents = new java.util.ArrayList[InputLogEvent]
    import sparkSession.implicits._
    val logStreamsRequest = new DescribeLogStreamsRequest().withLogGroupName(groupName)
    logStreamsRequest.withLimit(5)
    val logStreamList = awsLogsClient.describeLogStreams(logStreamsRequest).getLogStreams
    import scala.collection.JavaConversions._
    var token : String = null;
    for (logStream <- logStreamList) {
      if (logStream.getLogStreamName.equals(streamName)) token = logStream.getUploadSequenceToken
    }
    val dfData = df.map(x => x.mkString(",")+"  ").collect();
    for(i <- 0 until dfData.length){
      val log = new InputLogEvent
      log.setMessage(dfData(i))
      log.setTimestamp(System.currentTimeMillis())
      logEvents.add(log);
    }
    val putLogEventsRequest = new PutLogEventsRequest()
    putLogEventsRequest.setLogGroupName(groupName)
    putLogEventsRequest.setLogStreamName(streamName)
    putLogEventsRequest.setLogEvents(logEvents)
    putLogEventsRequest.setSequenceToken(token)
    val putLogEventsResult = awsLogsClient.putLogEvents(putLogEventsRequest)
  }
}
