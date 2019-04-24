package com.homeaway.utils.db;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homeaway.dto.migration.PreMigrateCommand;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Slf4j
public class ElasticSearch {
    private String hostName;
    private String port;
    private RestHighLevelClient client;
    private int searchSize = 10;

    public ElasticSearch(String hostName, String port, String userName, String password) throws IOException {
        this.hostName = hostName;
        this.port = port;
        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials(userName, password));

        RestClientBuilder builder = RestClient.builder(new HttpHost(hostName, Integer.parseInt(port)))
                .setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
                    @Override
                    public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {
                        return httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                    }
                });
        client = new RestHighLevelClient(builder);
        client.info();
    }


    public List<Map<String, Object>> getAllRecords(String index, String type) {
        List<Map<String, Object>> recordList = new ArrayList<>();
        Iterator<SearchHit> searchHitIterator = getHitIterator(index, type);
        if (searchHitIterator != null) {
            while (searchHitIterator.hasNext()) {
                SearchHit hit = searchHitIterator.next();
                recordList.add(hit.getSourceAsMap());
            }
        }
        return recordList;
    }

    public void insertRecords(String index, String type) throws IOException {
        List<Map<String, Object>> data = getAllRecords(index, type);
        if (data == null || data.size() == 0) {
            File elasticSearchData = new File("src/main/resources/test_data/ElasticSearch-Data.json");
            ObjectMapper mapper = new ObjectMapper();
            List<Object> recordList = mapper.readValue(elasticSearchData, new TypeReference<List<Object>>() {
            });
            IndexRequest indexRequest = new IndexRequest();
            indexRequest.index(index);
            indexRequest.type(type);
            for (Object jsonRecord : recordList) {
                JsonNode node = mapper.convertValue(jsonRecord, JsonNode.class);
                indexRequest.id(node.path("UserId").asText());
                indexRequest.source(mapper.writeValueAsString(node), XContentType.JSON);
                log.info(client.index(indexRequest).status().name());
            }
        }
    }

    public void deleteAllRecords(String index, String type) {
        DeleteRequest deleteRequest = new DeleteRequest();
        deleteRequest.index(index);
        deleteRequest.type(type);
        Iterator<SearchHit> searchHitIterator = getHitIterator(index, type);
        if (searchHitIterator != null) {
            while (searchHitIterator.hasNext()) {
                SearchHit hit = searchHitIterator.next();
                deleteRequest.id(hit.getId());
                try {
                    client.delete(deleteRequest);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void setSearchSize(int size) {
        searchSize = size;
    }

    private Iterator<SearchHit> getHitIterator(String index, String type) {
        SearchRequest searchRequest = new SearchRequest(index);
        searchRequest.types(type);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.matchAllQuery());
        searchSourceBuilder.size(searchSize);
        searchRequest.source(searchSourceBuilder);
        Iterator<SearchHit> searchHitIterator = null;
        for (int i = 0; i < 3 && searchHitIterator == null; i++)
            try {
                searchHitIterator = client.search(searchRequest).getHits().iterator();
            } catch (Exception e) {

            }
        return searchHitIterator;
    }

    public void closeConnection() {
        if (client != null) {
            try {
                client.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public List<PreMigrateCommand> getPreMigrateCommandList(String index, String type) throws IOException {
        List<PreMigrateCommand> preMigrateCommandList = new ArrayList<>();
        File elasticSearchData = new File("src/main/resources/test_data/ElasticSearch-Data.json");
        ObjectMapper mapper = new ObjectMapper();
        List<Object> recordList = mapper.readValue(elasticSearchData, new TypeReference<List<Object>>() {
        });
        for (Object record : recordList) {
            JsonNode node = mapper.convertValue(record, JsonNode.class);
            String uri = "http://" + hostName + ":" + port + "/" + index + "/" + type + "/" + node.path("UserId").asText();
            String curlCommand = "curl -X PUT -H 'content-type: application/json' " + uri + " -d ' " + node + "'";
            PreMigrateCommand preMigrateCommand = new PreMigrateCommand();
            preMigrateCommand.setShell(curlCommand);
            preMigrateCommandList.add(preMigrateCommand);

        }
        return preMigrateCommandList;
    }
}
