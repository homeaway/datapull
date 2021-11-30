package com.homeaway.datapullclient.input;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Data
@EqualsAndHashCode
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class InputConfiguration {
    @JsonProperty("useremailaddress")
    private String userEmailAddress;
    @JsonProperty("failureemailaddress")
    private String failureEmailAddress;
    @JsonProperty("migration_failure_threshold")
    private String migrationFailureThreshold;
    @JsonProperty("parallelmigrations")
    private Boolean parallelMigrations;
    @JsonProperty("no_of_retries")
    private String noOfRetries;
    @JsonProperty("precisecounts")
    private Boolean preciseCounts;
    @JsonProperty("migrations")
    private Set<Migration> migrations;
    @JsonProperty("cluster")
    private ClusterProperties clusterProperties;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<>();
}
