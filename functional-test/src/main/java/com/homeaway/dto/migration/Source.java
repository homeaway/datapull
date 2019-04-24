package com.homeaway.dto.migration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Source {
    @JsonProperty("platform")
    private String platform;
    @JsonProperty("awsenv")
    private String awsenv;
    @JsonProperty("server")
    private String server;
    @JsonProperty("database")
    private String database;
    @JsonProperty("table")
    private String table;
    @JsonProperty("login")
    private String login;
    @JsonProperty("vaultenv")
    private String vaultenv;
    @JsonProperty("password")
    private String password;
    @JsonProperty("cluster")
    private String cluster;
    @JsonProperty("collection")
    private String collection;
    @JsonProperty("keyspace")
    private String keyspace;
    @JsonProperty("authenticationdatabase")
    private String authenticationDatabase;
    @JsonProperty("replicaset")
    private String replicaset;
    @JsonProperty("sparkoptions")
    private Sparkoptions sparkoptions;
    @JsonProperty("s3path")
    private String s3path;
    @JsonProperty("fileformat")
    private String fileformat;
    @JsonProperty("post_migrate_command")
    private PostMigrateCommand postMigrateCommand;
    @JsonProperty("pre_migrate_command")
    private PreMigrateCommand preMigrateCommand;
    @JsonProperty("pre_migrate_commands")
    private List<PreMigrateCommand> preMigrateCommands;
    @JsonProperty("post_migrate_commands")
    private List<PostMigrateCommand> postMigrateCommands;
    @JsonProperty("awsaccesskeyid")
    private String accessKey;
    @JsonProperty("awssecretaccesskey")
    private String secretKey;

    @JsonProperty("alias")
    private String alias;
    @JsonProperty("bootstrapServers")
    private String bootstrapServers;
    @JsonProperty("schemaRegistries")
    private String schemaRegistries;
    @JsonProperty("topic")
    private String topic;
    @JsonProperty("offset")
    private String offset;
    @JsonProperty("deSerializer")
    private String deSerializer;
    @JsonProperty("s3location")
    private String s3location;
    @JsonProperty("s3location")
    public void setS3Location(String s3Location){
    this.s3location = s3Location + UUID.randomUUID();
    }
    @JsonProperty("groupid")
    private String groupId;

    //*******  elastic search related
    @JsonProperty("clustername")
    private String clusterName;
    @JsonProperty("node")
    private String node;
    @JsonProperty("port")
    private String port;
    @JsonProperty("index")
    private String index;
    @JsonProperty("type")
    private String type;
    @JsonProperty("version")
    private String version;

    // Cassandra related
    @JsonProperty("jksfilepath")
    private String jksFilePath;

    @JsonProperty("querys3sqlfile")
    private QueryS3SqlFile queryS3SqlFile;
    @JsonProperty("delimiter")
    private String delimiter;
    @JsonProperty("mergeschema")
    private Boolean mergeSchema;
}
