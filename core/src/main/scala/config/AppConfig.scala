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

package config

import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials, DefaultAWSCredentialsProviderChain}
import com.amazonaws.regions.Regions
import com.amazonaws.services.logs.{AWSLogs, AWSLogsClientBuilder}
import com.amazonaws.services.secretsmanager.{AWSSecretsManager, AWSSecretsManagerClientBuilder}
import com.amazonaws.services.simpleemail.{AmazonSimpleEmailService, AmazonSimpleEmailServiceClientBuilder}
import com.fasterxml.jackson.databind.JsonNode

case class AppConfig(smtpServerAddress: String, dataToolsEmailAddress: String, smtpUsername: String, smtpPassword: String, smtpPasswordSecretStore: String, smtpPasswordVaultPath: String, smtpPasswordVaultKey: String, smtpAuthEnable: String, smtpPort: String, smtpTlsEnable: String, vault_url: String,static_secret_path_prefix:String, vault_nonce: String, pagerDutyEmail: String,
                     s3bucket:String, server:String, database:String, login:String, password:String, table:String, driver:String,
                     stageAccountId : String, testAccountId : String, prodAccountId : String, inputjson:String, network:String, timeout:String, executor:String, interval:String, broadcasttimeout:String, btimeout:String
                     , scheduler:String, mode:String, failures:String, no_of_retries:Int, http_timeout:Int, consul_url:String, sleeptimeout:Int,  vault_path_login:String,
                     cloudWatchLogGroup:String, cloudWatchLogStream:String, cloudWatchLogRegion:String, accessKeyForCloudWatchLog : String, secretKeyForCloudWatchLog : String, secretsManagerRegion : String,
                     secretsManagerAccessKey : String, secretsManagerSecretKey : String, sesRegion : String, sesFromEmail : String, sesAccessKey : String, sesSecretKey : String) {


  def this(config: JsonNode) {
    this(
      smtpServerAddress = config.at("/datapull/email/smtp/smtpserveraddress").asText(""),
      dataToolsEmailAddress = config.at("/datapull/email/smtp/emailaddress").asText(""),
      smtpUsername = config.at("/datapull/email/smtp/smtpusername").asText(""),
      smtpPassword = config.at("/datapull/email/smtp/smtppassword").asText(""),
      smtpPasswordSecretStore = config.at("/datapull/email/smtp/smtppasswordsecretstore").asText(""),
      smtpPasswordVaultPath = config.at("/datapull/email/smtp/smtppasswordvaultpath").asText(""),
      smtpPasswordVaultKey = config.at("/datapull/email/smtp/smtppasswordvaultkey").asText(""),
      smtpAuthEnable = config.at("/datapull/email/smtp/smtpauthenable").asText(""),
      smtpPort = config.at("/datapull/email/smtp/smtpport").asText(""),
      smtpTlsEnable = config.at("/datapull/email/smtp/smtptlsenable").asText(""),
      vault_url = config.at("/datapull/secretstore/vault/vault_url").asText(""),
      static_secret_path_prefix = config.at("/datapull/secretstore/vault/static_secret_path_prefix").asText(""),
      vault_nonce = config.at("/datapull/secretstore/vault/vault_nonce").asText(""),
      pagerDutyEmail = config.at("/datapull/email/smtp/pagerdutyemail").asText(""),
      vault_path_login = config.at("/datapull/secretstore/vault/vault_path_login").asText(""),
      s3bucket = config.at("/datapull/api/s3_bucket_name").asText(""),
      server = config.at("/datapull/logger/mssql/server").asText(""),
      database = config.at("/datapull/logger/mssql/database").asText(""),
      login = config.at("/datapull/logger/mssql/login").asText(""),
      password = config.at("/datapull/logger/mssql/password").asText(""),
      table = config.at("/datapull/logger/mssql/table").asText(""),
      driver = config.at("/datapull/logger/mssql/driver").asText(""),
      stageAccountId = config.at("/datapull/aws/stage_acc_id").asText(""),
      testAccountId = config.at("/datapull/aws/test_acc_id").asText(""),
      prodAccountId = config.at("/datapull/aws/prod_acc_id").asText(""),
      inputjson = config.at("/datapull/json/inputjson").asText(""),
      network  =config.at("/datapull/spark/network").asText(""),
      timeout  =config.at("/datapull/spark/timeout").asText(""),
      executor =config.at("/datapull/spark/executor").asText(""),
      interval =config.at("/datapull/spark/interval").asText(""),
      broadcasttimeout = config.at("/datapull/spark/broadcasttimeout").asText(""),
      btimeout = config.at("/datapull/spark/btimeout").asText(""),
      scheduler = config.at("/datapull/spark/scheduler").asText(""),
      mode = config.at("/datapull/spark/mode").asText(""),
      failures = config.at("/datapull/spark/failures").asText(""),
      no_of_retries = config.at("/datapull/miscellaneous/no_of_retries").asInt(),
      http_timeout = config.at("/datapull/miscellaneous/timeout").asInt(),
      consul_url = config.at("/datapull/miscellaneous/consul_url").asText(""),
      sleeptimeout = config.at("/datapull/miscellaneous/sleeptimeout").asInt(),
      cloudWatchLogGroup = config.at("/datapull/logger/cloudwatch/groupName").asText(""),
      cloudWatchLogStream = config.at("/datapull/logger/cloudwatch/streamName").asText(""),
      cloudWatchLogRegion = config.at("/datapull/application/region").asText(""),
      accessKeyForCloudWatchLog = config.at("/datapull/logger/cloudwatch/awsaccesskeyid").asText(""),
      secretKeyForCloudWatchLog = config.at("/datapull/logger/cloudwatch/awssecretaccesskey").asText(""),
      secretsManagerRegion = config.at("/datapull/application/region").asText(""),
      secretsManagerAccessKey = config.at("/datapull/secretstore/secrets_manager/access_key").asText(""),
      secretsManagerSecretKey = config.at("/datapull/secretstore/secrets_manager/secret_key").asText(""),
      sesRegion = config.at("/datapull/application/region").asText(""),
      sesFromEmail = config.at("/datapull/email/ses/email").asText(""),
      sesAccessKey = config.at("/datapull/email/ses/access_key").asText(""),
      sesSecretKey = config.at("/datapull/email/ses/secret_key").asText("")
    )
  }

  def getCloudWatchClient(region: String): AWSLogs = {
    val credentialsProvider = new DefaultAWSCredentialsProviderChain();
    return (AWSLogsClientBuilder.standard()
      .withRegion(Regions.fromName((region)))
      .withCredentials(credentialsProvider)
      .build());
  }

  def getSecretsManagerClient(): AWSSecretsManager ={
    val credentialsProvider = if(secretsManagerAccessKey != null && !secretsManagerAccessKey.trim.isEmpty()) new AWSStaticCredentialsProvider(new BasicAWSCredentials(secretsManagerAccessKey, secretsManagerSecretKey))
    else new DefaultAWSCredentialsProviderChain();

    val client = AWSSecretsManagerClientBuilder.standard()
      .withRegion(Regions.fromName((secretsManagerRegion))).withCredentials(credentialsProvider)
      .build();
    return client;
  }

  def getSESClient(): AmazonSimpleEmailService ={
    val credentialsProvider = if(sesAccessKey != null && !sesAccessKey.trim.isEmpty()) new AWSStaticCredentialsProvider(new BasicAWSCredentials(sesAccessKey, sesSecretKey))
    else new DefaultAWSCredentialsProviderChain();

    val client =
      AmazonSimpleEmailServiceClientBuilder.standard()
        .withRegion(sesRegion).withCredentials(credentialsProvider)build();

    return client;
  }

  override def toString(): String = {
    return "smtpServerAddress = "+smtpServerAddress+"  smtpServerAddress = "+smtpServerAddress +"database = "+database;
  }
}
