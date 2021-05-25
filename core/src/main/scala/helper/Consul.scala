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

class Consul(dnsName: String, config: AppConfig) {
  //primary constructor
  private var dataCenter = ""
  private val consulPattern = "\\b([^ ]+)\\.service\\.([^ ]+)\\.consul\\b".r
  var serviceName = ""
  var ipAddresses = List.empty[String]

  private val dataCenters = config.consul_datacenters

  if (IsConsulDNSName()) {
    val consulPattern(rServiceName, rDataCenter) = dnsName
    this.serviceName = rServiceName
    this.dataCenter = rDataCenter
    GetIpAddresses
  }

  private def ApiUrl(): String = {
    if (config.consul_url != null && (!config.consul_url.isEmpty) && (config.consul_url != "")) {
      config.consul_url
    } else {
      config.consul_datacenters(this.dataCenter)
    }
  }

  def IsConsulDNSName(): Boolean = {
    dnsName.matches(consulPattern.regex) && (
      (config.consul_url != null && (!config.consul_url.isEmpty) && (config.consul_url != ""))
      ||
        (config.consul_datacenters != null && config.consul_datacenters.size > 0)
      )
  }

  private def GetIpAddresses(): Unit = {
    val helper = new Helper(config)
    val httpResponse = helper.getHttpResponse(ApiUrl() + "/v1/catalog/service/" + serviceName + "?passing", 100000, 10000, "GET")
    if (httpResponse.ResponseCode == 200) {
      val ipAddressBuffer = ListBuffer.empty[String]
      val jsonResponse = new JSONArray(httpResponse.ResponseBody)
      for (i <- 0 to jsonResponse.length() - 1) {
        ipAddressBuffer.append(jsonResponse.getJSONObject(i).getString("Address"))
      }
      ipAddresses = ipAddressBuffer.toList
    }
  }
}
