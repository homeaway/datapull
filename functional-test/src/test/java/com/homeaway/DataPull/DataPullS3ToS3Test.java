package com.homeaway.DataPull;

import com.homeaway.dto.InputJson;
import com.homeaway.dto.migration.Destination;
import com.homeaway.dto.migration.Source;
import com.homeaway.utils.DataPull;
import com.homeaway.utils.aws.AwsS3;
import com.homeaway.validator.Validator;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class DataPullS3ToS3Test {

    @Test
    public void tabDelimitedToParquetFormatTest() throws IOException {
        InputJson inputJson = new InputJson("tab-delimited-csv-to-parquet-format");
        Source source = inputJson.getMigrations().get(0).getSource();
        AwsS3 sourceS3 = new AwsS3(source.getS3path());
        sourceS3.uploadTabDelimitedData();
        DataPull.start(inputJson);
        List<Map<String, Object>> sourceData = sourceS3.getListOfTabDelimitedRecords();
        Destination destination = inputJson.getMigrations().get(0).getDestination();
        AwsS3 destinationS3 = new AwsS3(destination.getS3path());
        List<Map<String, Object>> destinationData = destinationS3.getParquetFileData();
        Validator.validateMigratedData(sourceData, destinationData);
    }

    @Test
    public void parquetToCsvFormatTest() throws IOException {
        InputJson inputJson = new InputJson("parquet-to-csv-format");
        Source source = inputJson.getMigrations().get(0).getSource();
        AwsS3 sourceS3 = new AwsS3(source.getS3path());
        sourceS3.uploadParquetFilesHavingDifferentSchema();
        DataPull.start(inputJson);
        List<Map<String, Object>> sourceData = sourceS3.getParquetFileData();
        Destination destination = inputJson.getMigrations().get(0).getDestination();
        AwsS3 destinationS3 = new AwsS3(destination.getS3path());
        List<Map<String, Object>> destinationData = destinationS3.getListOfCsvRecords();
        Validator.validateMigratedData(sourceData, destinationData);


    }
}
