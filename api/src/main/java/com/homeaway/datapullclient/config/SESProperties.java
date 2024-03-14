
package com.homeaway.datapullclient.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix="datapull.email.ses")
@EnableConfigurationProperties
@Data

public class SESProperties {

    @Value("${region:}")
    private String region;

    @Value("${email:}")
    private String email;

    @Value("${access_key:}")
    private String accessKey;

    @Value("${secret_key:}")
    private String secretKey;

}