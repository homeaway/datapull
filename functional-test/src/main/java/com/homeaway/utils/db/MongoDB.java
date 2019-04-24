package com.homeaway.utils.db;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homeaway.dto.migration.PreMigrateCommand;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.mongodb.client.model.Projections.excludeId;

@Slf4j
public class MongoDB {
    private MongoClient mongoClient = null;
    private String dbName;

    public MongoDB(String hostName, String userId, String password, String authenticatingDb, String replicaSet) {
        createConnection(hostName, userId, password, authenticatingDb, replicaSet);
    }

    public void createConnection(String hostName, String userId, String password, String authenticatingDb, String replicaSet) {
        MongoClientURI uri = new MongoClientURI("mongodb://" + userId + ":" + password + "@" + hostName + "/?replicaSet=" + replicaSet + "&authSource=" + authenticatingDb);
        dbName = authenticatingDb;
        mongoClient = new MongoClient(uri);
        mongoClient.getConnectPoint();
    }

    public void createConnection(String hostName, String authenticatingDb) {
        MongoClientURI uri = new MongoClientURI("mongodb://@" + hostName + "/" + authenticatingDb);
        dbName = authenticatingDb;
        mongoClient = new MongoClient(uri);
        mongoClient.getConnectPoint();
    }


    public List<Map<String, Object>> getAllRecords(String collectionName) {
        List<Map<String, Object>> lisOfDocuments = new ArrayList<>();
        MongoDatabase db = mongoClient.getDatabase(dbName);
        MongoCollection collection = db.getCollection(collectionName);

        MongoCursor iterator = collection.find().projection(excludeId()).iterator();
        while (iterator.hasNext()) {
            Document document = (Document) iterator.next();
            Map<String, Object> record = new HashMap<>();
            for (Map.Entry<String, Object> e : document.entrySet()) {
                record.put(e.getKey(), e.getValue());
            }
            lisOfDocuments.add(record);
        }
        return lisOfDocuments;
    }

    public void deleteAllRecords(String collectionName) {
        MongoDatabase db = mongoClient.getDatabase(dbName);
        MongoCollection collection = db.getCollection(collectionName);
        collection.drop();
    }


    public void checkSourceCollectionAndRecords(String dbName, String collectionName) throws IOException {
        MongoDatabase db = mongoClient.getDatabase(dbName);
        MongoCollection collection = db.getCollection(collectionName);
        if (collection.count() == 0) {
            insertRecord(collection);
        }
    }

    private void insertRecord(MongoCollection collection) throws IOException {
        File elasticSearchData = new File("src/main/resources/test_data/MongoDB-Data.json");
        ObjectMapper mapper = new ObjectMapper();
        List<Document> recordList = mapper.readValue(elasticSearchData, new TypeReference<List<Document>>() {
        });
        collection.insertMany(recordList);
    }

    public List<PreMigrateCommand> getPreMigrateCommandList(String collection) throws IOException {
        List<PreMigrateCommand> preMigrateCommandList = new ArrayList<>();
        File mongoDbData = new File("src/main/resources/test_data/MongoDB-Data.json");
        ObjectMapper mapper = new ObjectMapper();
        List<Object> recordList = mapper.readValue(mongoDbData, new TypeReference<List<Object>>() {
        });
        for (Object record : recordList) {
            JsonNode node = mapper.convertValue(record, JsonNode.class);
            PreMigrateCommand preMigrateCommand = new PreMigrateCommand();
            preMigrateCommand.setQuery("{insert: \"" + collection + "\", documents: [" + node + "]}");
            preMigrateCommandList.add(preMigrateCommand);
        }
        return preMigrateCommandList;
    }

    public void closeConnection() {
        if (mongoClient != null)
            mongoClient.close();
    }
}
