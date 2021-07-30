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


class SecretService(secretStoreType: String, appConfig: AppConfig) {
  var secretStore: SecretStore = null
  if (secretStoreType == "vault") {
    secretStore = new Vault(appConfig)
  }
  else if (secretStoreType == "aws_secrets_manager") {
    secretStore = new SecretsManager(appConfig)
  }

  def getSecret(env: String, clusterName: String, userName: String, keyStoreEnv: String): Map[String, String] = {
    secretStore.getSecret(env, clusterName, userName, keyStoreEnv)
  }

  def getSecret(secretName: String, secretKeyName: Option[String], env: Option[String]): String = {
    secretStore.getSecret(secretName, secretKeyName, env)
  }
}
