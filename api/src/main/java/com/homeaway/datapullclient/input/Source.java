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
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class Source {

    @JsonProperty("awsenv")
    private String env;

    @JsonProperty("server")
    private String server;

    @JsonProperty("database")
    private String database;

    @JsonProperty("comment_vaultenv")
    private String commentVaaultEnv;

    @JsonProperty("vaultenv")
    private String vaultEnv;

    @JsonProperty("platform")
    private String platform;

    @JsonProperty("password")
    private String password;

    @JsonProperty("comment_cluster")
    private String commentCluster;

    @JsonProperty("cluster")
    private String cluster;

    @JsonProperty("keyspace")
    private String keyspace;

    @JsonProperty("table")
    private String table;

    @JsonProperty("login")
    private String login;

    @JsonProperty("s3path")
    private String s3Path;

    @JsonProperty("fileformat")
    private String fileformat;

    @JsonProperty("jksfiles")
    private String[] jksfiles;

    @JsonProperty("sparkoptions")
    private SparkOptions sparkoptions;

    /*@Override
    public String toString() {
        return "Source{" +
                "env='" + env + '\'' +
                ", server='" + server + '\'' +
                ", database='" + database + '\'' +
                ", commentVaaultEnv='" + commentVaaultEnv + '\'' +
                ", vaultEnv='" + vaultEnv + '\'' +
                ", platform='" + platform + '\'' +
                ", commentCluster='" + commentCluster + '\'' +
                ", cluster='" + cluster + '\'' +
                ", keyspace='" + keyspace + '\'' +
                ", table='" + table + '\'' +
                ", login='" + login + '\'' +
                ", s3Path='" + s3Path + '\'' +
                ", fileformat='" + fileformat + '\'' +
                ", jksfilepath='" + jksfiles + '\'' +
                ", sparkoptions=" + sparkoptions +
                '}';
    }*/
}
