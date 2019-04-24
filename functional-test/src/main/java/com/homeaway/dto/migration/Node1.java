package com.homeaway.dto.migration;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class Node1 {
    @JsonProperty("label")
    private String label;
    @JsonProperty("property_key")
    private String propertyKey;
    @JsonProperty("properties_nonkey")
    private List<String> propertiesNonkey = null;
    @JsonProperty("createormerge")
    private String createormerge;
    @JsonProperty("createnodekeyconstraint")
    private Boolean createnodekeyconstraint;
}
