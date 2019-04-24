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

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClusterProperties {

    @JsonProperty("pipelinename")
    private String pipelineName;

    @JsonProperty("awsenv")
    private String awsEnv;

    @JsonProperty("cronexpression")
    private String cronExpression;

    @JsonAlias({"terminateclusterafterexecution", "terminate_cluster_after_execution"})
    private String terminateClusterAfterExecution;

    @JsonAlias({"ec2_instance_profile", "ec2instanceprofile"})
    private String instanceProfile;

    @JsonAlias({"instance_type", "master_instance_type"})
    private String masterInstanceType;

    @JsonProperty("slave_instance_type")
    private String slaveInstanceType;

    @JsonProperty("master_security_group")
    private String masterSecurityGroup;

    @JsonProperty("service_access_security_group")
    private String serviceAccessSecurityGroup;

    @JsonProperty("slave_security_group")
    private String slaveSecurityGroup;

    @JsonProperty("ec2_key_name")
    private String ec2KeyName;

    @JsonProperty("sparksubmitparams")
    private String sparksubmitparams;

    @JsonProperty("subnet_id")
    private String subnetId;

    @JsonAlias({"emr_instance_count", "NodeCount"})
    private String emrInstanceCount;

    @JsonProperty("emr_release_version")
    private String emrReleaseVersion;

    @JsonAlias({"ComponentInfo", "component_info"})
    private String componentInfo;

    @JsonAlias({"Portfolio", "portfolio"})
    private String portfolio;

    @JsonAlias({"Product", "product"})
    private String product;

    @JsonAlias({"Team", "team"})
    private String team;

    @JsonAlias({"Tags", "tags"})
    private Map<String, String> tags = new HashMap<String, String>();


    private String env;

    @Override
    public String toString() {
        return "ClusterProperties{" +
                "pipelineName='" + pipelineName + '\'' +
                ", awsEnv='" + awsEnv + '\'' +
                ", cronExpression='" + cronExpression + '\'' +
                ", terminateClusterAfterExecution='" + terminateClusterAfterExecution + '\'' +
                ", instanceProfile='" + instanceProfile + '\'' +
                ", masterInstanceType='" + masterInstanceType + '\'' +
                ", slaveInstanceType='" + slaveInstanceType + '\'' +
                ", masterSecurityGroup='" + masterSecurityGroup + '\'' +
                ", slaveSecurityGroup='" + slaveSecurityGroup + '\'' +
                ", ec2KeyName='" + ec2KeyName + '\'' +
                ", subnetId='" + subnetId + '\'' +
                ", sparksubmitparams='" + sparksubmitparams + '\'' +
                ", emrInstanceCount='" + emrInstanceCount + '\'' +
                ", emrReleaseVersion='" + emrReleaseVersion + '\'' +
                ", componentInfo='" + componentInfo + '\'' +
                ", portfolio='" + portfolio + '\'' +
                ", product='" + product + '\'' +
                ", team='" + team + '\'' +
                ", env='" + env + '\'' +
                '}';
    }
}
