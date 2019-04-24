package com.homeaway.utils;

import java.util.List;
import java.util.Map;

public class DataTypeHandler {

    public static void convertValueToBoolean(List<Map<String, Object>> dataList, String keyNameOfBooleanDataType) {
        for (Map<String, Object> data : dataList) {
            if (data.get(keyNameOfBooleanDataType).toString().equals("1")) {
                data.put(keyNameOfBooleanDataType, true);
            } else
                data.put(keyNameOfBooleanDataType, false);
        }
    }
}
