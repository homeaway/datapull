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


@Configuration
@ConfigurationProperties(prefix="datapull.api")
@EnableConfigurationProperties
@Data
public class DataPullProperties {

    @Value("${s3_bucket_name:}")
    private String s3BucketName;

    @Value("${application_security_group:}")
    private String applicationSecurityGroup;

    @Value( "${log_file_path:}" )
    private String logFilePath;

    @Value( "${history_folder:}" )
    private String applicationHistoryFolder;

    @Value("${application_subnet_1:}")
    private String applicationSubnet1;

    @Value("${application_subnet_2:}")
    private String applicationSubnet2;

    @Value("${application_subnet_3:}")
    private String applicationSubnet3;

}