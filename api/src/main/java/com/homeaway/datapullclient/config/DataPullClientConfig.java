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

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduce;
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduceClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.homeaway.datapullclient.process.DataPullTask;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

import java.util.List;

@Slf4j
@Data
@Configuration("dataPullConfig")
@PropertySources(@PropertySource("classpath:application.yml"))
public class DataPullClientConfig {

    @Autowired
    private Environment env;

    @Value("${datapull.application.region:}")
    private String appRegion;

    @Value("${datapull.application.okta_url:}")
    private String oktaUrl;

    @Bean
    @Scope("prototype")
    public DataPullTask getTask(String taskId, String json, String jksFile, List<String> subnets) {
        return new DataPullTask(taskId, json, jksFile,subnets);
    }

    @Bean
    public AmazonS3 getS3Client(){
        AWSCredentialsProvider credentialsProvider = new DefaultAWSCredentialsProviderChain();
        return (AmazonS3ClientBuilder.standard()
                .withRegion(Regions.fromName((appRegion)))
                .withCredentials(credentialsProvider)
                .build());
    }

    @Bean
    public DataPullProperties getDataPullProperties(){
        return new DataPullProperties();
    }

    @Bean
    public EMRProperties getEmrProperties(){
        return new EMRProperties();
    }

    @Bean
    public AmazonElasticMapReduce getEMRClient(){
        AWSCredentialsProvider credentialsProvider = new DefaultAWSCredentialsProviderChain();
        return (AmazonElasticMapReduceClientBuilder.standard()
                .withRegion(Regions.fromName(appRegion))
                .withCredentials(credentialsProvider)
                .build());
    }
}