package com.homeaway.dto.migration;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;


public class Sparkoptions {
    @JsonProperty("spark.cassandra.connection.ssl.enabled")
    public String sparkCassandraConnectionSslEnabled;
    @JsonProperty("comment_truststore_path")
    public String commentTruststorePath;
    @JsonProperty("spark.cassandra.connection.ssl.trustStore.path")
    public String sparkCassandraConnectionSslTrustStorePath;
    @JsonProperty("spark.cassandra.connection.ssl.trustStore.password")
    public String sparkCassandraConnectionSslTrustStorePassword;
}
