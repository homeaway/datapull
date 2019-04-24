package com.homeaway.utils.db;

import com.homeaway.constants.FilePath;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class Oracle {

    private String driverName = "oracle.jdbc.driver.OracleDriver";
    private int port = 1521;
    JdbcDb jdbcDb;

    public Oracle(String host, String userName, String password, String db) {
        jdbcDb = new JdbcDb();
        try {
            jdbcDb.CreateConnection(driverName, port, host, userName, password, db);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Map<String, Object>> executeQuery(String query) {
        return jdbcDb.executeQuery(query);
    }

    public boolean createSchema(){
        return jdbcDb.runScript(FilePath.ORACLE_SCHEMA_SQL_FILE);
    }

    public boolean insertData(){
        return jdbcDb.runScript(FilePath.ORACLE_DATA_SQL_FILE);
    }

    public void closeConnection(){
        jdbcDb.closeConnection();
    }

    public void TruncateTable(String tableName)
    {
        executeQuery("TRUNCATE TABLE "+tableName);
    }


}
