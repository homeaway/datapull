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

package com.homeaway.datapullclient.start;

import com.amazonaws.services.s3.AmazonS3Client;
import com.homeaway.datapullclient.config.DataPullClientConfig;
import com.homeaway.datapullclient.exception.ProcessingException;
import com.homeaway.datapullclient.process.DataPullRequestProcessor;
import com.homeaway.datapullclient.service.DataPullClientService;
import com.homeaway.datapullclient.service.SimpleDataPullClientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Arrays;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = "com.homeaway.datapullclient")
@Slf4j
public class DatapullclientApplication{

    public static void main(String[] args) throws ProcessingException{
        ConfigurableApplicationContext context = SpringApplication.run(DatapullclientApplication.class, args);
    }
}
