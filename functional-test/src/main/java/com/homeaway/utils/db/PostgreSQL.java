package com.homeaway.utils.db;

import com.homeaway.constants.FilePath;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@Slf4j
public class PostgreSQL {
    private String driverName = "org.postgresql.Driver";
    private int port = 5432;
    JdbcDb jdbcDb;

    public PostgreSQL(String host, String userName, String password, String db) {
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

    private void truncateTable(String tableName) {
        executeQuery("TRUNCATE TABLE "+tableName);
    }

    public void checkSourceTableAndData() {
        jdbcDb.runScript(FilePath.POSTGRES_DATA_SQL_FILE);
        log.info("Created table and inserted data.");
    }

    public void checkDestinationTableSchema(String tableName) {
        jdbcDb.runScript(FilePath.POSTGRES_SCHEMA_SQL_FILE);
        log.info("Destination table schema is created.");
        truncateTable(tableName);
    }

    public void closeConnection(){
        jdbcDb.closeConnection();
    }

    public void runScript(String scriptLocationToRun){
        jdbcDb.runScript(scriptLocationToRun);
    }
}
