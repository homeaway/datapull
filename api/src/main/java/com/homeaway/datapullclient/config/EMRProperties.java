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

package com.homeaway.datapullclient.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "datapull.emr")
@EnableConfigurationProperties
@Data


public class EMRProperties {

    @Value("${ec2_key_name:}")
    private String ec2KeyName;

    @Value("${emr_security_group_master:}")
    private String emrSecurityGroupMaster;

    @Value("${emr_security_group_slave:}")
    private String emrSecurityGroupSlave;

    @Value("${emr_security_group_service_access:}")
    private String emrSecurityGroupServiceAccess;

    @Value("${instance_count:6}")
    private int instanceCount;

    @Value("${master_type:m4.large}")
    private String masterType;

    @Value("${slave_type:m4.large}")
    private String slaveType;

    @Value("${service_role:emr_datapull_role}")
    private String serviceRole;

    @Value("${job_flow_role:emr_ec2_datapull_role}")
    private String jobFlowRole;

    @Value("${emr_release:emr-5.31.0}")
    private String emrRelease;

    @Value( "${bootstrap_folder_path:}" )
    private String bootstrapFolderPath;

    private Map<String, String> tags = new HashMap<String, String>();
}