package com.homeaway.utils.aws;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homeaway.constants.Environment;
import com.homeaway.dto.InputJson;
import com.homeaway.dto.Parquet;
import com.homeaway.utils.ParquetReaderUtil;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.parquet.example.data.simple.SimpleGroup;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class AwsS3 {
    private String bucketName;
    private AmazonS3 s3;
    private String objectPrefix;

    public AwsS3() {
        this("");
    }

    public AwsS3(String s3Path) {
        if (BooleanUtils.isFalse(s3Path.equals(""))) {
            setBucketNameAndObjectPrefix(s3Path);
        }
        s3 = AmazonS3ClientBuilder.standard()
                .withRegion(Environment.awsRegion)
                .withCredentials(new AWSStaticCredentialsProvider(Environment.awsCredentials))
                .build();
    }

    public AwsS3(String s3Path, AWSCredentials credentials) {
        if (BooleanUtils.isFalse(s3Path.equals(""))) {
            setBucketNameAndObjectPrefix(s3Path);
        }
        s3 = AmazonS3ClientBuilder.standard()
                .withRegion(Environment.awsRegion)
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .build();
    }

    public String getObjectPrefix() {
        return objectPrefix;
    }

    public void setObjectPrefix(String prefix) {
        objectPrefix = prefix;
        if (!objectPrefix.endsWith("/")) {
            objectPrefix += "/";
        }
    }

    public void appendObjectPrefix(String prefix) {
        setObjectPrefix(objectPrefix + prefix);
    }

    public List<Map<String, Object>> getListOfCsvRecords() throws IOException {
        return parseCsvRecords(CSVFormat.EXCEL);
    }

    public List<Map<String, Object>> getListOfTabDelimitedRecords() throws IOException {
        return parseCsvRecords(CSVFormat.TDF);
    }

    public List<Map<String, Object>> getParquetFileData() throws IOException {
        List<Map<String, Object>> dataList = new ArrayList<>();
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest().withBucketName(bucketName)
                .withPrefix(objectPrefix).withDelimiter("/");
        ObjectListing objects = s3.listObjects(listObjectsRequest);
        for (S3ObjectSummary objectSummary : objects.getObjectSummaries()) {
            if (objectSummary.getSize() > 0) {
                File localFile = new File("target/main/resources/temp/parquet_file.parquet");
                s3.getObject(new GetObjectRequest(bucketName, objectSummary.getKey()), localFile);
                Parquet parquet = ParquetReaderUtil.getParquetData(localFile.getPath());
                List<SimpleGroup> simpleGroupList = parquet.getData();
                for (SimpleGroup simpleGroup : simpleGroupList) {
                    Map<String, Object> dataMap = new HashMap<>();
                    int fieldCount = simpleGroup.getType().getFieldCount();
                    for (int i = 0; i < fieldCount; i++) {
                        String fieldName = simpleGroup.getType().getType(i).getName();
                        String fieldValue = simpleGroup.getValueToString(i, 0);
                        dataMap.put(fieldName, fieldValue);
                    }
                    dataList.add(dataMap);
                    localFile.delete();
                }
            }
        }
        return dataList;
    }

    private List<Map<String, Object>> parseCsvRecords(CSVFormat format) throws IOException {
        List<Map<String, Object>> records = new ArrayList<>();
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest().withBucketName(bucketName)
                .withPrefix(objectPrefix).withDelimiter("/");
        ObjectListing objects = s3.listObjects(listObjectsRequest);
        for (S3ObjectSummary objectSummary : objects.getObjectSummaries()) {
            S3Object fullObject = s3.getObject(new GetObjectRequest(bucketName, objectSummary.getKey()));
            BufferedReader reader = new BufferedReader(new InputStreamReader(fullObject.getObjectContent()));
            List<CSVRecord> csvRecords = CSVParser.parse(reader, format).getRecords();
            for (int i = 1; i < csvRecords.size(); i++) {
                Iterator<String> headersIterator = csvRecords.get(0).iterator();
                Iterator<String> valuesIterator = csvRecords.get(i).iterator();
                Map<String, Object> record = new HashMap<>();
                while (valuesIterator.hasNext()) {
                    record.put(headersIterator.next(), valuesIterator.next());
                }
                records.add(record);
            }
        }

        return records;
    }

    public List<Map<String, Object>> getListOfRecords() throws IOException {
        List<Map<String, Object>> records = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest().withBucketName(bucketName)
                .withPrefix(objectPrefix).withDelimiter("/");
        ObjectListing objects = s3.listObjects(listObjectsRequest);
        for (S3ObjectSummary objectSummary : objects.getObjectSummaries()) {
            S3Object fullObject = s3.getObject(new GetObjectRequest(bucketName, objectSummary.getKey()));
            BufferedReader reader = new BufferedReader(new InputStreamReader(fullObject.getObjectContent()));
            String line = null;
            while ((line = reader.readLine()) != null) {
                Map<String, Object> recordMap = mapper.readValue(line, new TypeReference<Map<String, Object>>() {
                });
                records.add(recordMap);
            }
        }
        return records;
    }

    public List<String> getSubFolderList() {
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest().withBucketName(bucketName)
                .withPrefix(objectPrefix).withDelimiter("/");
        ObjectListing objects = s3.listObjects(listObjectsRequest);
        return objects.getCommonPrefixes().stream().map(e -> e.substring(objectPrefix.length(), e.lastIndexOf("/"))).collect(Collectors.toList());
    }


    public void setBucketNameAndObjectPrefix(String s3Path) {
        int indexOfFirstSlash = s3Path.indexOf("/");
        this.bucketName = s3Path.substring(0, indexOfFirstSlash);
        setObjectPrefix(s3Path.substring(indexOfFirstSlash + 1));
    }

    public void insertData() throws IOException {
        if (getListOfRecords().size() == 0) {
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, objectPrefix + "data-pull-test-data.json", new File("src/main/resources/test_data/s3-data.json"));
            s3.putObject(putObjectRequest);
        }
    }

    public void uploadTestReport(String s3ReportPath) {
        s3 = AmazonS3ClientBuilder.standard()
                .withRegion(Environment.awsRegion)
                .withCredentials(new AWSStaticCredentialsProvider(Environment.awsCredentials))
                .build();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-M-yyyy hh:mm:ss");
        s3ReportPath += simpleDateFormat.format(new Date()) + "/";
        setBucketNameAndObjectPrefix(s3ReportPath);
        s3.putObject(bucketName, objectPrefix + "extent-report.html", new File("report/extent-report.html"));
        s3.putObject(bucketName, objectPrefix + "logs.log", new File("report/logs.log"));
    }

    public void uploadInputJson(InputJson userInputJson, InputJson saveInputJson) throws IOException {
        s3 = AmazonS3ClientBuilder.standard()
                .withRegion(Environment.awsRegion)
                .withCredentials(new AWSStaticCredentialsProvider(Environment.awsCredentials))
                .build();

        ObjectMapper mapper = new ObjectMapper();
        String s3Path = userInputJson.getJsonInputFile().getS3Path();
        String bucketName = s3Path.substring(0, s3Path.indexOf("/"));
        String key = s3Path.substring(s3Path.indexOf("/") + 1);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        String inputJsonStr = mapper.writeValueAsString(saveInputJson);
        s3.putObject(bucketName, key, inputJsonStr);
    }

    public void addPartitionedData(String sourceS3Path) {
        setBucketNameAndObjectPrefix(sourceS3Path);
        addPartitionedData();
    }

    public void addPartitionedData() {
        if (getSubFolderList().size() < 2) {
            File[] directories = new File("src/main/resources/test_data/s3_partitioned_data/").listFiles(File::isDirectory);
            for (File directory : directories) {
                PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName,
                        objectPrefix + directory.getName() + "/" + directory.listFiles()[0].getName(),
                        directory.listFiles()[0]);
                s3.putObject(putObjectRequest);
            }

        }
    }

    public Map<String, List<Map<String, Object>>> getPartitionedCsvData() throws IOException {
        Map<String, List<Map<String, Object>>> data = new HashMap<>();
        List<String> subFolderList = getSubFolderList();
        String originalObjectPrefix = objectPrefix;
        for (String subFolder : subFolderList) {
            setObjectPrefix(originalObjectPrefix + subFolder);
            data.put(subFolder, getListOfCsvRecords());
        }
        objectPrefix = originalObjectPrefix;
        return data;
    }

    public void uploadTabDelimitedData() {
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest().withBucketName(bucketName)
                .withPrefix(objectPrefix).withDelimiter("/");
        ObjectListing objects = s3.listObjects(listObjectsRequest);
        if (objects.getObjectSummaries().size() < 2) {
            File file = new File("src/main/resources/test_data/s3_tab_delimited_data/tab_delimited_data.csv");
            s3.putObject(bucketName, objectPrefix + file.getName(), file);
        }
    }

    public void uploadParquetFilesHavingDifferentSchema() {
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest().withBucketName(bucketName)
                .withPrefix(objectPrefix).withDelimiter("/");
        ObjectListing objects = s3.listObjects(listObjectsRequest);
        if (objects.getObjectSummaries().size() < 2) {
            File folder = new File("src/main/resources/test_data/s3_parquet_file");
            for (File file : folder.listFiles()) {
                s3.putObject(bucketName, objectPrefix + file.getName(), file);
            }
        }
    }


}
