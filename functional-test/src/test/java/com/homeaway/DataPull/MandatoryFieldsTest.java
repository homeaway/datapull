package com.homeaway.DataPull;

import com.homeaway.dto.InputJson;
import com.homeaway.utils.aws.DataPullApi;
import org.testng.Assert;
import org.testng.annotations.Test;

public class MandatoryFieldsTest {

    @Test
    public void emailMandatoryFieldTest() {
        InputJson inputJson = new InputJson("Close-EMR-Cluster");
        inputJson.setUseremailaddress(null);
        DataPullApi api = new DataPullApi();
        int responseCode = api.triggerDataPull(inputJson);
        Assert.assertEquals(responseCode, 400, "Response code");
    }

    @Test
    public void clusterMandatoryFieldTest() {
        InputJson inputJson = new InputJson("Close-EMR-Cluster");
        inputJson.setCluster(null);
        DataPullApi api = new DataPullApi();
        int responseCode = api.triggerDataPull(inputJson);
        Assert.assertEquals(responseCode, 400, "Response code");
    }

    @Test
    public void migrationsMandatoryFieldTest() {
        InputJson inputJson = new InputJson("Close-EMR-Cluster");
        inputJson.setMigrations(null);
        DataPullApi api = new DataPullApi();
        int responseCode = api.triggerDataPull(inputJson);
        Assert.assertEquals(responseCode, 400, "Response code");
    }
}
