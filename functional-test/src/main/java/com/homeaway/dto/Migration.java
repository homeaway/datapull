package com.homeaway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.homeaway.dto.migration.Destination;
import com.homeaway.dto.migration.Source;
import com.homeaway.dto.migration.Sql;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Migration {
    @JsonProperty("sources")
    private List<Source> sources;
    @JsonProperty("source")
    private Source source;
    @JsonProperty("sql")
    private Sql sql;
    @JsonProperty("destination")
    private Destination destination;
}
