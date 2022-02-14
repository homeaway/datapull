package com.homeaway.datapullclient.config;

import com.amazonaws.services.s3.AmazonS3;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homeaway.datapullclient.exception.InputException;
import com.homeaway.datapullclient.input.InputConfiguration;
import com.homeaway.datapullclient.util.InputJsonHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;

import javax.annotation.PostConstruct;
import java.util.*;

@Slf4j
@Configuration
public class StoredInputProviderConfig {
    private Map<String, Set<InputConfiguration>> source;
    private Map<String, Set<InputConfiguration>> destination;
    private Map<String, Set<InputConfiguration>> srcDestMap;
    private final DataPullProperties dataPullProperties;
    private final AmazonS3 amazonS3;
    private final String s3Bucket;
    private static final String DATAPULL_HISTORY_FOLDER = "datapull-opensource/history";
    private final ObjectMapper mapper;

    @Autowired
    public StoredInputProviderConfig(
            ObjectMapper mapper, DataPullProperties dataPullProperties, AmazonS3 amazonS3) {
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper can't be null.");
        this.dataPullProperties =
                Objects.requireNonNull(dataPullProperties, "dataPullProperties can't be null.");
        this.amazonS3 = Objects.requireNonNull(amazonS3, "amazonS3 can't be null.");
        this.s3Bucket = dataPullProperties.getS3BucketName();
        this.source = new HashMap<>();
        this.destination = new HashMap<>();
        this.srcDestMap = new HashMap<>();
    }

    @PostConstruct
    public void initMaps() {
        log.info("Existing input getting cache.");
        populateMaps(InputJsonHelper.fetchAllInputConf(amazonS3, s3Bucket, DATAPULL_HISTORY_FOLDER));
    }

    public Set<InputConfiguration> getSample(String inputKeyType, String key) {
        switch (inputKeyType) {
            case "srcdest":
                return srcDestMap.getOrDefault(key, new HashSet<>());
            case "src":
                return source.getOrDefault(key, new HashSet<>());
            case "dest":
                return destination.getOrDefault(key, new HashSet<>());
            default:
                throw new InputException("Asked key or key type not found.");
        }
    }

    public static boolean isJSONValid(ObjectMapper mapper, String json) {
        try {
            mapper.readTree(json);
            return true;
        } catch (JsonMappingException e) {
            log.error("Error : Invalid json :\n{}", json);
            log.error("Error : Invalid json :", e);
            return false;
        } catch (JsonProcessingException e) {
            log.error("Error : Invalid json :\n{}", json);
            log.error("Error : Invalid json :", e);
            return false;
        }
    }

    private void populateMaps(List<String> jsons) {
        jsons.stream()
                .parallel()
                .filter(j -> isJSONValid(mapper, j))
                .forEach(e -> processCacheStore(e));
    }

    @Async
    public void processCacheStore(String json) {
        try {
            InputJsonHelper.process(mapper, json, source, destination, srcDestMap);
        } catch (JsonProcessingException e) {
            log.error("Error json:: {}", json);
            log.error("Error while creating index. ", e);
            throw new RuntimeException("error while creating index. ");
        }
    }
}
