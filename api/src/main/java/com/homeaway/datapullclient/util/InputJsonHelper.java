package com.homeaway.datapullclient.util;


import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.util.IOUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homeaway.datapullclient.input.Destination;
import com.homeaway.datapullclient.input.InputConfiguration;
import com.homeaway.datapullclient.input.Source;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class InputJsonHelper {

    public static void addToMap(Map<String, Set<InputConfiguration>> map, String key, InputConfiguration json){
        map.putIfAbsent(key, new HashSet<>());
        map.get(key).add(json);
    }

    public static String getMapKey(Source src, Destination dest){
       String key = null;
       if(src!=null && dest!=null) key = src.getPlatform().concat(dest.getPlatform());
       else if(src!=null) key = src.getPlatform();
       else key = dest.getPlatform();
       return key;
    }

    public static void process(ObjectMapper objectMapper, String json, Map<String, Set<InputConfiguration>> source, Map<String, Set<InputConfiguration>> destination, Map<String, Set<InputConfiguration>> srcDestMap) throws JsonProcessingException {
        InputConfiguration inputConfiguration = objectMapper.readValue(json, InputConfiguration.class);
        inputConfiguration.getMigrations().stream().parallel().filter(migration->migration!=null).forEach(migration -> {
            if(migration.getDestination()!=null && migration.getDestination().getPlatform()!=null)addToMap(destination, getMapKey(null, migration.getDestination()), inputConfiguration);
            if(migration.getSource()!=null){
                if(migration.getDestination()!=null && migration.getDestination().getPlatform()!=null && migration.getSource().getPlatform()!=null)addToMap(srcDestMap, getMapKey(migration.getSource(), migration.getDestination()), inputConfiguration);
                if(migration.getSource().getPlatform()!=null)addToMap(source, getMapKey(migration.getSource(), null), inputConfiguration);
            }else if(migration.getSources()!=null){
                migration.getSources().stream().filter(s->s!=null).forEach(src->{
                    if(migration.getDestination()!=null && migration.getDestination().getPlatform()!=null && src.getPlatform()!=null) addToMap(srcDestMap, getMapKey(src, migration.getDestination()), inputConfiguration);
                    if(src.getPlatform()!=null)addToMap(source, getMapKey(src, null), inputConfiguration);
                });
            }

        });
    }

  public static List<String> fetchAllInputConf(
      AmazonS3 amazonS3, String bucketName, String directory) {

        return fetchAllKey(amazonS3, bucketName, directory).parallelStream().map(key -> getDataAsString(amazonS3, bucketName, key)).collect(Collectors.toList());
  }

    public static List<String> fetchAllKey(AmazonS3 amazonS3, String bucketName, String directory){
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest().withBucketName(bucketName).withPrefix(directory.concat("/"));
        List<String> keys = new ArrayList<>();
        ObjectListing objects = amazonS3.listObjects(listObjectsRequest);
        for (;;) {
            List<S3ObjectSummary> summaries = objects.getObjectSummaries();
            if (summaries.size() < 1) {
                break;
            }
            summaries.forEach(s -> keys.add(s.getKey()));
            objects = amazonS3.listNextBatchOfObjects(objects);
        }
        return keys;
    }

    public static  String getDataAsString(AmazonS3 amazonS3, String bucketName, String key) {
        S3Object object = amazonS3.getObject(new GetObjectRequest(bucketName, key));
        String json ;
        try {
             json = IOUtils.toString(object.getObjectContent());
        } catch (IOException e) {
            log.error("Error while processing input stream to string.", e);
            throw new RuntimeException("Error while processing input stream to string.");
        }
        return json;
    }
}
