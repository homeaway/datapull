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

package com.homeaway.datapullclient.input;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Destination {
    @JsonProperty("platform")
    private String platform;

    @JsonProperty("cluster")
    private String cluster;

    @JsonProperty("keyspace")
    private String keyspace;

    @JsonProperty("table")
    private String table;

    @JsonProperty("login")
    private String commentCluster;

    @JsonProperty("password")
    private String password;

    @JsonProperty("s3path")
    private String s3Path;

    @JsonProperty("fileformat")
    private String fileformat;

    @JsonProperty("sparkoptions")
    private SparkOptions sparkoptions;

    @JsonProperty("server")
    private String server;

    @JsonProperty("database")
    private String database;

    @JsonProperty("jksfiles")
    private String[] jksfiles;

    @Override
    public String toString() {
        return "Destination{" +
                "platform='" + platform + '\'' +
                ", cluster='" + cluster + '\'' +
                ", keyspace='" + keyspace + '\'' +
                ", table='" + table + '\'' +
                ", commentCluster='" + commentCluster + '\'' +
                ", s3Path='" + s3Path + '\'' +
                ", fileformat='" + fileformat + '\'' +
                ", sparkoptions=" + sparkoptions +
                ", server='" + server + '\'' +
                ", database='" + database + '\'' +
                ", jksfilepath='" + jksfiles + '\'' +
                '}';
    }
}
