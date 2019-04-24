package com.homeaway.dto.migration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PreMigrateCommand {
    @JsonProperty("operation")
    private String operation;
    @JsonProperty("s3path")
    private String s3path;
    @JsonProperty("sources3path")
    private String sourceS3Path;
    @JsonProperty("destinations3path")
    private String destinationS3Path;
    @JsonProperty("removesource")
    private Boolean removeSource;
    @JsonProperty("overwrite")
    private Boolean overwrite;
    @JsonProperty("partitioned")
    private Boolean partitioned;
    @JsonProperty("query")
    private String query;
    @JsonProperty("shell")
    private String shell;


}
