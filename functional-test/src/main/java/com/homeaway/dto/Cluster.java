package com.homeaway.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.homeaway.constants.Environment;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Cluster {
    @JsonProperty("pipelinename")
    private String pipelinename;
    @JsonProperty("portfolio")
    private String portfolio;
    @JsonProperty("product")
    private String product;
    @JsonProperty("awsenv")
    private String awsenv;
    @JsonProperty("terminateclusterafterexecution")
    private String terminateclusterafterexecution;
    @JsonProperty("ComponentInfo")
    private String componentInfo;
    @JsonProperty("ec2instanceprofile")
    private String ec2instanceprofile;
    @JsonProperty("sparksubmitparams")
    private String sparkSubmitParams;
    @JsonProperty("cronexpression")
    private String cronExpression;

    @JsonIgnore
    public String getEmrClusterName(){
        return Environment.envName.trim().toLowerCase() + "-emr-" + pipelinename + "-pipeline";
    }
}
