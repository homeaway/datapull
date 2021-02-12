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

import config.AppConfig
import org.codehaus.jettison.json.JSONArray

import scala.collection.mutable.ListBuffer

class Consul (dnsName: String, config : AppConfig) {
  //primary constructor
  private var dataCenter = ""
  var serviceName = ""
  var ipAddresses = List.empty[String]

  private val dataCenters = Map("us-aus-1-prod" -> "prod.vxe",
    "us-aus-1-dts" -> "dts.vxe",
    "us-aus-1-itdev" -> "dts.vxe")

  if (IsConsulDNSName()) {
    val dnsParts = dnsName.split("\\.")
    dataCenter = dnsParts(2).toLowerCase
    serviceName = dnsParts(0)
    GetIpAddresses
  }

  private def ApiUrl (): String = {
    "http://" + dataCenter + ".consul." + dataCenters(dataCenter) + ".away.black:8500"
  }

  def IsConsulDNSName(): Boolean = {
    dnsName.matches("\\b[a-zA-Z0-9-_]+.service.[a-zA-Z0-9-_]+.consul\\b")
  }

  private def GetIpAddresses(): Unit = {
    val helper = new Helper(config)
    val httpResponse = helper.getHttpResponse(ApiUrl() + "/v1/catalog/service/" + serviceName + "?passing", 100000, 10000, "GET")
    if (httpResponse.ResponseCode == 200) {
      val ipAddressBuffer = ListBuffer.empty[String]
      val jsonResponse = new JSONArray(httpResponse.ResponseBody)
      for (i <- 0 to jsonResponse.length()-1) {
        ipAddressBuffer.append(jsonResponse.getJSONObject(i).getString("Address"))
      }
      ipAddresses = ipAddressBuffer.toList
    }
  }
}
