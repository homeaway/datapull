package com.homeaway.dto.migration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Destination {
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
    @JsonProperty("alias")
    private String alias;
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
    public Sparkoptions sparkoptions;
    @JsonProperty("s3path")
    private String s3path;
    @JsonProperty("fileformat")
    private String fileformat;
    @JsonProperty("groupbyfields")
    private String groupByFields;
    @JsonProperty("post_migrate_command")
    private PostMigrateCommand postMigrateCommand;
    @JsonProperty("pre_migrate_command")
    private PreMigrateCommand preMigrateCommand;
    @JsonProperty("pre_migrate_commands")
    private List<PreMigrateCommand> preMigrateCommands;
    @JsonProperty("mappingid")
    private String mappingId;
    @JsonProperty("replacedocuments")
    private String replacedocuments;
    @JsonProperty("savemode")
    private String savemode;
    @JsonProperty("node1")
    private Node1 node1;
    @JsonProperty("node2")
    private Node1 node2;
    @JsonProperty("relation")
    private Relation relation;

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
    @JsonProperty("awsaccesskeyid")
    private String accessKey;
    @JsonProperty("awssecretaccesskey")
    private String secretKey;
}
