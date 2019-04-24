package com.homeaway.validator;

import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;

import java.util.List;
import java.util.Map;

@Slf4j
public class Validator {

    public static void validateMigratedData(List<Map<String, Object>> sourceDataList, List<Map<String, Object>> destDataList) {
        log.info("Source data {}", sourceDataList);
        log.info("Destination data {}", destDataList);
        Assert.assertTrue(destDataList.size() > 0, "No record migrated");
        Assert.assertEquals(destDataList.size(), sourceDataList.size(), "number of transferred records are not matching");
        log.info("Total number of records moved is matching.");
        for (Map<String, Object> sourceDataMap : sourceDataList) {
            String userID = sourceDataMap.get("UserId").toString();
            Map<String, Object> matchedDestRecord = null;
            for (Map<String, Object> destData : destDataList) {
                if (destData.getOrDefault("UserId", -1).toString().equalsIgnoreCase(userID)) {
                    matchedDestRecord = destData;
                    break;
                }
            }
            Assert.assertNotNull(matchedDestRecord, "Didn't find any matching record in destination Database for UserID " + userID);
            for (Map.Entry<String, Object> columnValueEntry : sourceDataMap.entrySet()) {
                Object sourceDataValue = columnValueEntry.getValue();
                if (sourceDataValue != null) {
                    Assert.assertEquals(matchedDestRecord.get(columnValueEntry.getKey()).toString(), sourceDataValue.toString());
                }
            }
        }
        log.info("Key value pair of the moved records are also matching from the source database.");
    }
}
