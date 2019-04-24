package com.homeaway.utils.db;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class MySQL {
    private String driverName = "com.mysql.jdbc.Driver";
    private int port = 3306;
    JdbcDb jdbcDb;

    public MySQL(String host, String userName, String password, String db) {
        jdbcDb = new JdbcDb();
        try {
            jdbcDb.CreateConnection(driverName, port, host, userName, password, db);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void checkSourceTableAndData() {
        jdbcDb.runScript("src/main/resources/test_data/SQL_Files/mysql-source-table-schema-and-data.sql");
    }

    public void checkDestinationTableSchema() {
        jdbcDb.runScript("src/main/resources/test_data/SQL_Files/mysql-destination-table-schema.sql");
    }

    public List<Map<String, Object>> executeQuery(String query) {
        return jdbcDb.executeQuery(query);
    }

    public void closeConnection(){
        jdbcDb.closeConnection();
    }


}
