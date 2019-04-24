package com.homeaway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.homeaway.constants.Environment;
import com.homeaway.constants.FilePath;
import com.homeaway.utils.GlobalVariables;
import com.homeaway.utils.aws.RDSInstance;
import com.homeaway.utils.db.GlobalRDSInstance;
import com.homeaway.utils.db.SqlServer;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.*;

@Slf4j
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class InputJson {

    public InputJson() {

    }


    public InputJson(String fileName) {
        FrameInputJson(fileName);
        if(Environment.parallelExecution) {
            if (GlobalVariables.threadIds == null)
                GlobalVariables.threadIds = new HashSet<>();
            long threadId = Thread.currentThread().getId();
            GlobalVariables.threadIds.add(threadId);
            cluster.setPipelinename(cluster.getPipelinename() + "-pe-" + threadId);
        }
    }

    public InputJson(String fileName, long threadId) {
        FrameInputJson(fileName);
        if(threadId > 0) {
            cluster.setPipelinename(cluster.getPipelinename() + "-pe-" + threadId);
        }
    }


    @JsonProperty("useremailaddress")
    private String useremailaddress;
    @JsonProperty("migrations")
    private List<Migration> migrations = null;
    @JsonProperty("cluster")
    private Cluster cluster;
    @JsonProperty("parallelmigrations")
    private Boolean parallelmigrations;
    @JsonProperty("jsoninputfile")
    private JsonInputFile jsonInputFile;
    @JsonProperty("sparkjarfile")
    private String sparkJarFile;
    @JsonProperty("migration_failure_threshold")
    private String migrationThreshold;

    private void FrameInputJson(String fileName) {

        File inputJsonFile = new File(FilePath.INPUT_JSON_FILE_PATH + fileName + ".json");
        File globalConfig = new File(FilePath.GLOBAL_CONFIG_FILE);

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);

        JsonNode updatedNode = UpdateClusterDetails(inputJsonFile, globalConfig);
        InputJson updatedJson = UpdateDBDetails(updatedNode, globalConfig);

        useremailaddress = Environment.userEmailAddress;
        migrations = updatedJson.migrations;
        cluster = updatedJson.cluster;
        parallelmigrations = updatedJson.parallelmigrations;
        jsonInputFile = updatedJson.jsonInputFile;
        sparkJarFile = updatedJson.sparkJarFile;
    }


    private JsonNode UpdateClusterDetails(File inputFile, File detailsToUpdate) {
        ObjectNode inputNode = null;
        ObjectMapper mapper = new ObjectMapper();
        try {
            inputNode = (ObjectNode) mapper.readTree(inputFile);
            ObjectNode emr = (ObjectNode) mapper.readTree(detailsToUpdate).get("cluster");
            inputNode.set("cluster", emr);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return inputNode;
    }

    private InputJson UpdateDBDetails(JsonNode InputNode, File DBDetailsFile) {
        InputJson finalJsonToReturn = null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
            if (InputNode.has("migrations")) {
                Iterator<JsonNode> migrationNodes = InputNode.findValue("migrations").iterator();
                while (migrationNodes.hasNext()) {
                    ObjectNode migrationsNode = (ObjectNode) migrationNodes.next();
                    Iterator<JsonNode> childNodes = migrationsNode.iterator();
                    while (childNodes.hasNext()) {
                        JsonNode currentNode = childNodes.next();
                        if(currentNode.isArray()) {
                            for (JsonNode node : currentNode) {
                                updateNode(node, DBDetailsFile);
                            }
                        } else {
                            updateNode(currentNode,DBDetailsFile);
                        }
                    }
                }
            }
            finalJsonToReturn = mapper.readValue(InputNode.toString(), InputJson.class);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return finalJsonToReturn;
    }

    private void updateNode(JsonNode node, File DBDetailsFile) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
            if (node.has("platform")) {
                ObjectNode dbDetailToChange;
                String platform = node.get("platform").asText().toLowerCase().trim();
                dbDetailToChange = (ObjectNode) mapper.readTree(DBDetailsFile).get(platform);
                if (dbDetailToChange != null)
                    ((ObjectNode) node).setAll(dbDetailToChange);
                else
                    System.out.println("Platform" + platform + "not present in the GlobalConfig.json file");
                if (Arrays.stream(Environment.rdsInstanceRequired).anyMatch(x -> x.toLowerCase().trim().equals(platform))) {
                    Map<String, String> details = new HashMap<>();
                    RDSInstance rdsInstance = GlobalRDSInstance.GetRDSInstance(platform, true);
                    if (rdsInstance != null) {
                        rdsInstance.waitTillRDSAvailability();
                        details.put("server", rdsInstance.rdsEndPoint);
                        details.put("login", rdsInstance.rdsSpec.username);
                        details.put("password", rdsInstance.rdsSpec.password);
                        if (platform.equals("mssql")) {
                            SqlServer connection = new SqlServer(details.get("server"), details.get("login"), details.get("password"), "");
                            String dataBase = node.get("database").asText();
                            connection.createDataBase(dataBase);
                            details.put("database", dataBase);
                            connection.closeConnection();
                        } else {
                            details.put("database", rdsInstance.rdsSpec.dbName);
                        }
                        ObjectNode serverNode = mapper.valueToTree(details);
                        ((ObjectNode) node).setAll(serverNode);
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public String toString() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        String strJson = null;
        try {
            strJson = mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return strJson;
    }
}
