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
import helper.Helper
import org.codehaus.jettison.json.JSONObject

class Vault(appConfig: AppConfig) extends SecretStore (appConfig) {

  val helper = new Helper(appConfig)

  override def getSecret(secretName: String, env: Option[String]): String = {
    val token = GetVaultToken(env)
    val url = GetVaultUrl(env) + secretName
    val credsString = getCredsString(url, token)
    val credJSON = new JSONObject(credsString)
    credJSON.getJSONObject("data").toString
  }

  private def getCredsString(url: String, token: String): String = {
    return (helper.getHttpResponse(url, 10000, 10000, "GET", Map("Content-Type" -> "application/x-www-form-urlencoded", "X-Vault-Token" -> token)).ResponseBody)
  }

  private def GetVaultToken(vaultEnv: Option[String]): String = {
    var jsonBody = new JSONObject()
    jsonBody.put("role", helper.GetEC2Role())
    jsonBody.put("pkcs7", helper.GetEC2pkcs7())
    jsonBody.put("nonce", appConfig.vault_nonce)
    val tokenJsonString = helper.getHttpResponse(GetVaultUrl(vaultEnv) + appConfig.vault_path_login, 10000, 10000, "POST", Map("Content-Type" -> "application/x-www-form-urlencoded"), jsonBody.toString).ResponseBody
    val tokenJson = new JSONObject(tokenJsonString)
    val token = tokenJson.getJSONObject("auth").getString("client_token")
    token
  }

  private def GetVaultUrl(vaultEnv: Option[String]): String = {
    if (vaultEnv.isEmpty) {
      appConfig.vault_url
    } else {
      appConfig.vault_url.replaceAll("VAULTENV", vaultEnv.get)
    }
  }
}
