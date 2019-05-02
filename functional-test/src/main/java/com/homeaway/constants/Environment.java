package com.homeaway.constants;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Environment {
    public static Properties properties;

    static {
        try {
            FileInputStream inputStream = new FileInputStream("src/main/resources/environment.properties");
            properties = new Properties();
            properties.load(inputStream);
        } catch (IOException ex) {
            System.out.println(ex.getMessage());
        }
    }

    public static final String envName = properties.getProperty("environment");
    public static final AWSCredentials awsCredentials = new BasicAWSCredentials(properties.getProperty("awsAccessKey"), properties.getProperty("awsSecretKey"));
    public static final String awsRegion = properties.getProperty("awsRegion");
    public static final String dataPullUri = properties.getProperty("dataPullUri");
    public static final String userEmailAddress = properties.getProperty("userEmailAddress");
    public static final String[] rdsInstanceRequired = properties.getProperty("rdsInstancesRequired", "").split(",");
    public static final boolean parallelExecution = Boolean.parseBoolean(properties.getProperty("parallelExecution"));


}
