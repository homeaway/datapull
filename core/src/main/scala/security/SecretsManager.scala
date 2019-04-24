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

import java.util.Base64

import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest
import config.AppConfig
import org.json4s.jackson.JsonMethods._
case class SecretsManager(appConfig: AppConfig){

  def getSecret(secretName : String): Map[String, String] = {
    var decodedBinarySecret = null
    val getSecretValueRequest = new GetSecretValueRequest().withSecretId(secretName)
    val client  = appConfig.getSecretsManagerClient();
    val  getSecretValueResult = client.getSecretValue(getSecretValueRequest)

    val secretString  = getSecretValueResult.getSecretString
    val secret = if(secretString != null) getSecretValueResult.getSecretString else new String(Base64.getDecoder.decode(getSecretValueResult.getSecretBinary).array)
    val result = parse(secret.toString).values.asInstanceOf[Map[String, String]]
    //val result  = parse(secret.toString).extract[Map[String, String]]
    return result
  }
}
