package com.homeaway.utils.db;

import com.datastax.driver.core.*;
import com.homeaway.constants.FilePath;
import com.homeaway.dto.migration.PreMigrateCommand;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.*;

@Slf4j
public class Cassandra {
    private Session session = null;

    public Cassandra(String hostName, String userId, String password) {
        createConnection(hostName, userId, password);
    }

    public void createConnection(String hostName, String userId, String password) {
        try {
            session = Cluster.builder().addContactPoints(hostName).withSSL(getSSLOption())
                    .withCredentials(userId, password)
                    .build().connect();

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

    private SSLOptions getSSLOption() throws NoSuchAlgorithmException, KeyStoreException, IOException, CertificateException, KeyManagementException {
        SSLContext sc = SSLContext.getInstance("TLS");
        KeyStore trustStore = KeyStore.getInstance("JKS");
        InputStream inputStream = Cassandra.class.getResourceAsStream("/client-server.jks");
        trustStore.load(inputStream, "clientpw".toCharArray());
        TrustManagerFactory factory = TrustManagerFactory.getInstance("PKIX");
        factory.init(trustStore);
        sc.init(null, factory.getTrustManagers(), new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier((string, ssls) -> true);
        return RemoteEndpointAwareJdkSSLOptions.builder().withSSLContext(sc).build();
    }

    public List<Map<String, Object>> executeQuery(String query) {
        List<Map<String, Object>> resultList = new ArrayList<>();
        ResultSet resultSet = session.execute(query);
        Iterator<Row> rowIterator = resultSet.iterator();
        while (rowIterator.hasNext()) {
            Map<String, Object> columnValueMap = new HashMap<String, Object>();
            Row row = resultSet.iterator().next();
            int size = row.getColumnDefinitions().size();
            for (int i = 0; i < size; i++) {
                columnValueMap.put(row.getColumnDefinitions().getName(i), row.getObject(i));
            }
            resultList.add(columnValueMap);
        }
        return resultList;
    }


    public boolean checkSourceTableAndRecords(String keySpace, String tableName) throws IOException {
        KeyspaceMetadata keyspaceMetadata = session.getCluster().getMetadata().getKeyspace(keySpace);
        if (keyspaceMetadata == null) {
            log.info("Keyspace - {} does not exist.", keySpace);
            return false;
        } else {
            TableMetadata tableMetadata = keyspaceMetadata.getTable(tableName);
            if (tableMetadata == null) {
                log.info("table - {} does not exist.", tableName);
                createSourceTableSchema(keySpace, tableName);
            }
        }
        insertData(keySpace, tableName);
        return true;
    }

    public List<PreMigrateCommand> preMigrateCommandList = new ArrayList<>();

    private void insertData(String keySpace, String tableName) throws IOException {
        String dataFilePath = FilePath.CASSANDRA_SOURCE_TABLE_DATA_FILE;
        runScript(dataFilePath, keySpace, tableName, false);
    }

    public void checkDestinationTableSchema(String keySpace, String tableName) throws Exception {
        String schemaSql = new String(Files.readAllBytes(Paths.get("src/main/resources/test_data/cassandra_cql_files/cassandra_destination_table_schema.cql")));
        KeyspaceMetadata keyspaceMetadata = session.getCluster().getMetadata().getKeyspace(keySpace);
        if (keyspaceMetadata == null) {
            log.info("Keyspace - {} does not exist.", keySpace);
            throw new Exception("Keyspace does not exists.");
        } else {
            TableMetadata tableMetadata = keyspaceMetadata.getTable(tableName);
            if (tableMetadata == null) {
                log.info("table - {} does not exist.", tableName);
                schemaSql = schemaSql.replace("${keyspace}", keySpace);
                schemaSql = schemaSql.replace("${table}", tableName);
                session.execute(schemaSql);
            } else {
                truncateTable(keySpace, tableName);
            }
        }

    }

    public void truncateTable(String keySpace, String tableName) {
        log.info("truncating table {}", tableName);
        session.execute("Truncate table " + keySpace + "." + tableName);
    }

    public void closeConnection() {
        try {
            session.close();
        } catch (Exception e) { /* ignored */ }
    }

    public void createSourceTableSchema(String keySpace, String tableName) throws IOException {
        String schemaSql = new String(Files.readAllBytes(Paths.get("src/main/resources/test_data/cassandra_cql_files/cassandra_source_table_schema.cql")));
        schemaSql = schemaSql.replace("${keyspace}", keySpace);
        schemaSql = schemaSql.replace("${table}", tableName);
        session.execute(schemaSql);
    }

    public void runScript(String file, String keySpace, String tableName, boolean storeCommands) throws IOException {
        Reader reader = new BufferedReader(new FileReader(file));
        String insertSql = "";
        StringBuffer command = null;
        try {
            LineNumberReader lineReader = new LineNumberReader(reader);
            String line;
            while ((line = lineReader.readLine()) != null) {
                if (command == null) {
                    command = new StringBuffer();
                }
                String trimmedLine = line.trim();
                if (trimmedLine.length() < 1
                        || trimmedLine.startsWith("//")
                        || trimmedLine.startsWith("--")) {
                    // Do nothing
                } else if (trimmedLine.endsWith(";")
                        || trimmedLine.equals(";")) {

                    command.append(line.substring(0, line
                            .lastIndexOf(";")));
                    command.append(" ");
                    insertSql = command.toString();
                    insertSql = insertSql.replace("${keyspace}", keySpace);
                    insertSql = insertSql.replace("${table}", tableName);
                    runOrStoreCommand(storeCommands, insertSql);
                    command = null;
                } else {
                    command.append(line);
                    command.append("\n");
                }
            }
            if (command != null) {
                insertSql = command.toString();
                insertSql = insertSql.replace("${keyspace}", keySpace);
                insertSql = insertSql.replace("${table}", tableName);
                runOrStoreCommand(storeCommands, insertSql);
            }

        } catch (IOException e) {
            throw new IOException(String.format("Error executing '%s': %s", insertSql, e.getMessage()), e);
        }
    }

    private void runOrStoreCommand(boolean storeCommands, String command) {
        command = command.replaceAll("\\n", " ");
        if (storeCommands) {
            PreMigrateCommand preMigrateCommand = new PreMigrateCommand();
            preMigrateCommand.setQuery(command);
            preMigrateCommandList.add(preMigrateCommand);
        } else
            session.execute(command);
    }
}
