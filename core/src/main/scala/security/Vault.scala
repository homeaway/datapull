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

class Vault(appConfig: AppConfig) extends SecretStore {

  val helper = new Helper(appConfig)

  override def getSecret(awsEnv: String, clusterName: String, userName: String, vaultEnv: String): Map[String, String] = {
    var usernameAndPassword: Map[String, String] = Map()
    var vaultEnv_effective = if (vaultEnv == "") awsEnv else vaultEnv
    val token = GetVaultToken(awsEnv, vaultEnv_effective)
    val url = appConfig.vault_url + "/" + appConfig.static_secret_path_prefix + "/" + clusterName + "/" + userName
    val credsString = getCredsString(url, token)
    val credJSON = new JSONObject(credsString)
    val username = credJSON.getJSONObject("data").getString("username")
    val password = credJSON.getJSONObject("data").getString("password")
    usernameAndPassword = Map("username" -> username, "password" -> password)
    usernameAndPassword
  }

  private def getCredsString(url: String, token: String): String = {
    return (helper.getHttpResponse(url, 10000, 10000, "GET", Map("Content-Type" -> "application/x-www-form-urlencoded", "X-Vault-Token" -> token)).ResponseBody)
  }

  override def getSecret(vaultPath: String, vaultKey: String,env:String):String={
    var usernameAndPassword: Map[String, String] = Map()
    var vaultEnv_effective = env
    val token = GetVaultToken(env, vaultEnv_effective)
    val url = appConfig.vault_url.replaceFirst("VAULTENV", vaultEnv_effective)+ ""+vaultPath
    val credsString = getCredsString(url, token)
    val credJSON = new JSONObject(credsString)
    credJSON.getJSONObject("data").getString(vaultKey)
  }

  def GetVaultToken(awsEnv: String, vaultEnv: String): String = {
    var jsonBody = new JSONObject()
     jsonBody.put("role", helper.GetEC2Role())
    jsonBody.put("pkcs7", helper.GetEC2pkcs7())
    jsonBody.put("nonce", appConfig.vault_nonce)
    val tokenJsonString = helper.getHttpResponse(appConfig.vault_url.replaceFirst("VAULTENV", vaultEnv) + appConfig.vault_path_login, 10000, 10000, "POST", Map("Content-Type" -> "application/x-www-form-urlencoded"), jsonBody.toString).ResponseBody
    val tokenJson = new JSONObject(tokenJsonString)
    val token = tokenJson.getJSONObject("auth").getString("client_token")
    token
  }
}
