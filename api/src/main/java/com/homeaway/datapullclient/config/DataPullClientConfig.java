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

import com.amazonaws.auth.*;
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

@Slf4j
@Data
@Configuration("dataPullConfig")
@PropertySources({
        @PropertySource("classpath:application.yml"),
        @PropertySource("classpath:application-${env}.yml")
})
public class DataPullClientConfig {

    @Autowired
    private Environment env;

    @Value("${datapull.application.region:}")
    private String appRegion;

    @Value("${datapull.application.okta_url:}")
    private String oktaUrl;

    @Bean
    @Scope("prototype")
    public DataPullTask getTask(String taskId, String json){
        return new DataPullTask(taskId, json);
    }

    @Bean
    public AmazonS3 getS3Client(){
        AmazonS3 s3Client = null;

        DataPullProperties properties = getDataPullProperties();
        String s3BucketRegion = properties.getS3BucketRegion();
        String accessKey = properties.getAccessKey();
        String secretKey = properties.getSecretKey();
        String s3Region   = s3BucketRegion == null || s3BucketRegion.equals("") ? appRegion : s3BucketRegion;
        AWSCredentialsProvider credentialsProvider = accessKey != null && !accessKey.isEmpty() && secretKey != null && !secretKey.isEmpty() ?
                new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey)) : new DefaultAWSCredentialsProviderChain();

        s3Client = AmazonS3ClientBuilder.standard()
                .withRegion(Regions.fromName((s3Region)))
                .withCredentials(credentialsProvider)
                .build();

        return s3Client;
    }

    @Bean
    public DataPullProperties getDataPullProperties(){
        return new DataPullProperties();
    }

    @Bean
    public EMRProperties getEmrProperties(){
        return new EMRProperties();
    }

    public AmazonS3 getS3Client(String userAccessKey , String userSecretkey){
        AmazonS3 s3Client = null;
        DataPullProperties properties = getDataPullProperties();
        String s3BukcetRegion = properties.getS3BucketName();
        String s3Region   = s3BukcetRegion == null || s3BukcetRegion.equals("") ? appRegion : s3BukcetRegion;
        AWSCredentialsProvider credentialsProvider = null;
        if(userAccessKey != null && !userAccessKey.isEmpty() && userSecretkey != null && !userSecretkey.isEmpty()){
            credentialsProvider = new AWSStaticCredentialsProvider(new BasicAWSCredentials(userAccessKey, userSecretkey));
        }else {
            return getS3Client();
        }

        s3Client = AmazonS3ClientBuilder.standard()
                .withRegion(Regions.fromName((s3Region)))
                .withCredentials(credentialsProvider)
                .build();

        return s3Client;
    }

    @Bean
    public AmazonElasticMapReduce getEMRClient(){
        AmazonElasticMapReduce emrClient = null;
        String emrRegion = getEmrProperties().getEmrRegion();
        DataPullProperties properties = getDataPullProperties();
        String accessKey = properties.getAccessKey();
        String secretKey = properties.getSecretKey();
        String region   = emrRegion == null || emrRegion.equals("") ? appRegion : emrRegion;

        AWSCredentialsProvider credentialsProvider = accessKey != null && !accessKey.isEmpty() && secretKey != null && !secretKey.isEmpty() ?
                new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey)) : new DefaultAWSCredentialsProviderChain();

        emrClient =  AmazonElasticMapReduceClientBuilder.standard()
                .withRegion(Regions.fromName(region))
                .withCredentials(credentialsProvider)
                .build();

        return emrClient;
    }

//    @Bean
//    public CloudWatchLog getCloudWatchLogConfiguration(){
//        if(cloudWatchGrpName.isEmpty() || cloudWatchStreamName.isEmpty())
//            return null;
//
//        CloudWatchLog log = new CloudWatchLog(cloudWatchGrpName, cloudWatchStreamName, cloudWatchRegion == null ? appRegion : cloudWatchRegion);
//        log.setAccessKey(accessKey);
//        log.setSecretKey(secretKey);
//        return log;
//    }
}