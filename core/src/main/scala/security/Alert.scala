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

package security

import java.time.Instant

import config.AppConfig
import logging.Logger
import org.apache.spark.sql.SparkSession

class Alert(appConfig: AppConfig) extends Logger{
  case class AlertDetail(JobId: String, Portfolio: String,Product:String, MasterNode: String, Ec2Role: String,  elapsedtime:Double,minexecutiontime:Long,maxexecutiontime:Long,status:String,InstantNow:String,processedflag:Long,EmailAddress:String,pipelineName:String,awsenv:String,BccEmailAddress:String)
  case class dbconfig (server: String , database:String,login:String,password:String,table:String)

  def AlertLog(jobId:String,MasterNode:String,ec2Role:String,portfolio:String,product:String,elapsedtime:Double,minexecutiontime:Long,maxexecutiontime:Long,sparkSession: SparkSession,status:String,EmailAddress:String,pipelineName:String,awsenv:String,BccEmailAddress:String): Unit = {

    var driver: String = null
    var url: String = null
    var platform:String = "mssql"


    if (platform == "mssql") {
      driver = appConfig.driver
      url = "jdbc:sqlserver://" + appConfig.server + ";database=" + appConfig.database
      val connectionProperties = new java.util.Properties()
      connectionProperties.setProperty("user", appConfig.login)
      connectionProperties.setProperty("password", appConfig.password)
      connectionProperties.setProperty("driver", driver)

      val df = sparkSession.createDataFrame(Seq(AlertDetail(jobId,portfolio,product,MasterNode,ec2Role,elapsedtime,minexecutiontime,maxexecutiontime,status,Instant.now().toString,0,EmailAddress,pipelineName,awsenv,BccEmailAddress)))
      df.write.mode("append").jdbc(url, appConfig.table, connectionProperties)
      //df.show()
    }
  }
}