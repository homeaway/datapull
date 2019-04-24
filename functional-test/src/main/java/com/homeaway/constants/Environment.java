package com.homeaway.constants;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.homeaway.utils.aws.FargateApp;

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
        }catch (IOException ex){
            System.out.println(ex.getMessage());
        }
    }

    public static final String envName = properties.getProperty("environment");
    private static final String awsAccessKey = properties.getProperty("awsAccessKey");
    private static final String awsSecretKey = properties.getProperty("awsSecretKey");
    private static final String devAWSAccessKey = properties.getProperty("dev.awsAccessKey");
    private static final String devAWSSecretKey = properties.getProperty("dev.awsSecretKey");
    private static final String testAWSAccessKey = properties.getProperty("test.awsAccessKey");
    private static final String testAWSSecretKey = properties.getProperty("test.awsSecretKey");

    public static final String awsRegion = properties.getProperty("awsRegion");
    public static final AWSCredentials credentials =  new BasicAWSCredentials(awsAccessKey, awsSecretKey);

    public static final AWSCredentials devCredentials =  new BasicAWSCredentials(devAWSAccessKey, devAWSSecretKey);
    public static final AWSCredentials testCredentials = new BasicAWSCredentials(testAWSAccessKey, testAWSSecretKey);
    public static final String dataPullUri = properties.getProperty("dataPullUri");
    public static final String version = properties.getProperty("version");
    public static final String ecsClusterName = properties.getProperty("ecsClusterName");

    public static final String dataPullOpenSourceUriInFile = properties.getProperty("dataPullOpenSourceUri");
    public static final String dataPullOpenSourceUri = FargateApp.getDataPullUri();
    public static final String userEmailAddress = properties.getProperty("userEmailAddress");
    public static final String[] rdsInstanceRequired = properties.getProperty("rdsInstancesRequired").split(",");
    public static final boolean parallelExecution = Boolean.parseBoolean(properties.getProperty("parallelExecution"));
}
