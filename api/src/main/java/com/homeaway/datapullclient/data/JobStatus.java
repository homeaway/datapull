package com.homeaway.datapullclient.data;

import lombok.Data;

import java.util.Map;

@Data
public class JobStatus {

    Map<String,String> clusterStatus;
    Map<String,String> jobStatus;
}
