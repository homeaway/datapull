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

import config.AppConfig
import org.codehaus.jettison.json.JSONObject

import java.util.Base64

class SecretStore (appConfig: AppConfig) {

  def getSecret(secretName: String, env:Option[String]):String={
    null
  }

  def getSecret(awsEnv: String, clusterName: String, userName: String, vaultEnv: String): Map[String, String] = {
    var usernameAndPassword: Map[String, String] = Map()
    var vaultEnv_effective = awsEnv
    if (vaultEnv != null && !vaultEnv.isEmpty) {
      vaultEnv_effective = vaultEnv
    }
    val secretName = appConfig.static_secret_path_prefix + "/" + clusterName + "/" + userName
    val username = this.getSecret(secretName, Some("username"), Some(vaultEnv_effective))
    val password = this.getSecret(secretName, Some("password"), Some(vaultEnv_effective))
    usernameAndPassword = Map("username" -> username, "password" -> password)
    usernameAndPassword
  }

  def getSecret(secretName: String, secretKeyName: Option[String], env:Option[String]):String={
    var secret = this.getSecret(secretName, env)
    if (!(secretKeyName.getOrElse("")).isEmpty) {
      val secretAsJson = new JSONObject(secret)
      if (secretAsJson != null && secretAsJson.has(secretKeyName.get)) {
        secret = secretAsJson.optString(secretKeyName.get)
      }
    }
    secret
  }

}
