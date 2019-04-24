package com.homeaway.utils.db;

import org.neo4j.driver.v1.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Neo4j {
    private Session session;
    private Driver driver;

    public Neo4j(String host, String user, String password) {
        driver = GraphDatabase.driver("bolt://" + host + ":7687", AuthTokens.basic(user, password));
        session = driver.session();
    }

    public List<Map<String, Object>> executeQuery(String query) {
        List<Map<String, Object>> records = new ArrayList<>();
        StatementResult rs = session.run(query);
        while (rs.hasNext()) {
            Record record = rs.next();
            records.add(record.get(0).asMap());
        }

        return records;
    }

    public void closeConnection() {
        if(session != null && driver != null){
            session.close();
            driver.close();
        }

    }


}
