package com.homeaway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonInputFile {
    @JsonProperty("s3path")
    private String s3Path;
    @JsonProperty("awsaccesskeyid")
    private String awsAccessKeyId;
    @JsonProperty("awssecretaccesskey")
    private String awsSecretAccessKey;
}