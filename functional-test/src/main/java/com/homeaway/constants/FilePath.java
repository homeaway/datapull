package com.homeaway.constants;

public class FilePath {
    public static final String INPUT_JSON_FILE_PATH = "src/main/resources/input_files/";
    public static final String GLOBAL_CONFIG_FILE = "src/main/resources/" + Environment.envName.toLowerCase() + "_global-config.json";
    public static final String ORACLE_SCHEMA_SQL_FILE = "src/main/resources/test_data/SQL_Files/oracle-schema.sql";
    public static final String POSTGRES_SCHEMA_SQL_FILE = "src/main/resources/test_data/SQL_Files/postgres-destination-table-schema.sql";
    public static final String ORACLE_DATA_SQL_FILE = "src/main/resources/test_data/SQL_Files/oracle-data.sql";
    public static final String POSTGRES_DATA_SQL_FILE = "src/main/resources/test_data/SQL_Files/postgres-source-table-schema-and-data.sql";
    public static final String KAFKA_DATA_FILE = "src/main/resources/test_data/KafkaData.json";
    public static final String POSTGRES_SOURCE_UPDATE = "src/main/resources/test_data/SQL_Files/postgres-source-table-update.sql";
    public static final String POSTGRES_SOURCE_INSERT_UPDATE = "src/main/resources/test_data/SQL_Files/postgres-source-table-insert-update.sql";
    public static final String CASSANDRA_SOURCE_TABLE_DATA_FILE = "src/main/resources/test_data/cassandra_cql_files/cassandra_source_table_data.cql";

}
