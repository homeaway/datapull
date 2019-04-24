package com.homeaway.utils.aws;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.AmazonECSClientBuilder;
import com.amazonaws.services.ecs.model.*;
import com.homeaway.constants.Environment;
import lombok.extern.slf4j.Slf4j;

import static io.restassured.RestAssured.get;

@Slf4j
public class FargateApp {
    private static String ip = null;
    private static String uri = null;

    public static String getDataPullUri() {
        if (ip == null && Environment.version.toLowerCase().equals("open-source")) {
            String storedUri = Environment.dataPullOpenSourceUriInFile;
            String storedIp = storedUri.substring(storedUri.indexOf("//") + 2, storedUri.lastIndexOf(":"));
            int statusCode = healthCheckUp(storedIp);
            if (statusCode != 200) {
                getIpAddress();
                uri = "http://" + ip.toString() + ":8080/api/v1/DataPullPipeline";
            } else {
                ip = storedIp;
                uri = storedUri;
            }
        }
        return uri;
    }

    private static void getIpAddress() {
        try {
            log.info("Fetching latest IP from ECS fargate app.");
            AmazonECS client = AmazonECSClientBuilder.standard().withCredentials(new AWSStaticCredentialsProvider(Environment.credentials)).build();
            ListTasksResult listTasksResult = client.listTasks(new ListTasksRequest().withCluster(Environment.ecsClusterName));
            if (listTasksResult.getTaskArns().size() > 0) {
                String arn = listTasksResult.getTaskArns().get(0);
                String taskId = arn.substring(arn.lastIndexOf("/") + 1);
                log.info("Task Id - {}", taskId);
                DescribeTasksRequest describeTasksRequest = new DescribeTasksRequest();
                DescribeTasksResult describeTasksResult = client.describeTasks(describeTasksRequest.withCluster(Environment.ecsClusterName).withTasks(taskId));
                Task task = describeTasksResult.getTasks().get(0);
                ip = task.getContainers().get(0).getNetworkInterfaces().get(0).getPrivateIpv4Address();
                int statusCode = healthCheckUp(ip);
                if (!task.getLastStatus().equals("RUNNING") && statusCode != 200) {
                    log.error("ECS Fargate App is not up and running.");
                }

            } else {
                log.error("No task is found for {} ECS cluster", Environment.ecsClusterName);
            }
            log.info("Fargate app ip address - {}", ip);
        } catch (Exception ex) {
            ip = null;
            log.error(ex.getMessage());
        }
    }

    private static int healthCheckUp(String ipAddress) {
        int statusCode = 500;
        try {
            statusCode = get("http://" + ipAddress + ":8080/").getStatusCode();
        } catch (Exception ex) {
            log.info("Health checkup is failing for IP - {}", ipAddress);
        }
        return statusCode;
    }

}
