package com.homeaway.utils.db;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class SqlServer {
    JdbcDb jdbcDb;
    private String driverName = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    private int port = 1433;

    public SqlServer(String hostName, String userId, String password, String database) {
        jdbcDb = new JdbcDb();
        try {
            jdbcDb.CreateConnection(driverName, port, hostName, userId, password, database);
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    public void checkSourceTableAndData() {
        jdbcDb.runScript("src/main/resources/test_data/SQL_Files/sqlserver-source-table-schema-and-data.sql");
    }

    public void checkDestinationTableSchema() {
        jdbcDb.runScript("src/main/resources/test_data/SQL_Files/sqlserver-destination-table-schema.sql");
    }

    public void createDataBase(String dataBaseToCreate)
    {
        String script =  String.format("if db_id('%1$s') is null \n" +
                                        "BEGIN\n" +
                                        "Create DATABASE %1$s\n" +
                                        "END;",dataBaseToCreate);
        jdbcDb.executeQuery(script);
    }

    public List<Map<String, Object>> executeSelectQuery(String selectQuery) {
        return jdbcDb.executeQuery(selectQuery);
    }

    public void closeConnection(){
        jdbcDb.closeConnection();
    }


}
