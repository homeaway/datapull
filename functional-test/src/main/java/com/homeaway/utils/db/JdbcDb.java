package com.homeaway.utils.db;

import com.homeaway.utils.SQLScriptRunner;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JdbcDb {
    private Connection connection = null;
    private Statement statement = null;

    public void CreateConnection(String driverName, int port, String host, String userName, String password, String dbName) throws SQLException {
        try {
            Class.forName(driverName);
            String connectionUrl = "";
            String dbType = "";
            if (driverName.contains("mysql"))
                connectionUrl = "jdbc:mysql://" + host + ":" + port + "/" + dbName;
            else if (driverName.contains("sqlserver"))
                connectionUrl = "jdbc:sqlserver://" + host + ":" + port + ";databaseName=" + dbName;
            else if(driverName.contains("oracle"))
                connectionUrl = "jdbc:oracle:thin:@//" + host + ":" + port + "/" + dbName;
            else if(driverName.contains("postgresql"))
                connectionUrl = "jdbc:postgresql://" + host + ":" + port + "/" + dbName;
            connection = DriverManager.getConnection(connectionUrl, userName, password);
            statement = connection.createStatement();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

    }

    public List<Map<String, Object>> executeQuery(String query) {
        List<Map<String, Object>> resultList = new ArrayList<>();
        ResultSet resultSet = null;
        try {
            if (query.toLowerCase().startsWith("truncate")) {
                statement.execute(query);
                return null;
            } else
                resultSet = statement.executeQuery(query);
            int columnCount = resultSet.getMetaData().getColumnCount();
            while (resultSet.next()) {
                Map<String, Object> columnValueMap = new HashMap<String, Object>();
                for (int i = 1; i <= columnCount; i++) {
                    columnValueMap.put(resultSet.getMetaData().getColumnLabel(i), resultSet.getObject(i));
                }
                resultList.add(columnValueMap);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return resultList;

    }


    public void closeConnection() {
        try {
            if(statement != null && connection != null){
                statement.close();
                connection.close();
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    protected boolean runScript(String file){
        SQLScriptRunner runner = new SQLScriptRunner(connection, false, false);
        try {
            runner.runScript(new BufferedReader(new FileReader(file)));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SQLException e) {
           return false;
        }
        return true;
    }
}
