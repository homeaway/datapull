package com.homeaway.datapullclient.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix="datapull.email.smtp")
@EnableConfigurationProperties
@Data

public class SMTPProperties {

    @Value("${emailaddress:}")
    private String emailaddress;

    @Value("${smtpserveraddress:}")
    private String smtpserveraddress;

    @Value("${port:}")
    private String smtpport;

    @Value("${starttls:}")
    private String smtpstarttls;


}
