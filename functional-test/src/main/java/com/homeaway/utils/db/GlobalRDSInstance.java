package com.homeaway.utils.db;

import com.homeaway.utils.aws.RDSInstance;

public class GlobalRDSInstance {
    private static RDSInstance mySQLInstance = null;
    private static RDSInstance oracleInstance = null;
    private static RDSInstance postgresInstance = null;
    private static RDSInstance msSQLServerInstance = null;

    public static RDSInstance GetRDSInstance(String dbEngine, boolean creationRequired) {
        switch (dbEngine.toLowerCase().trim()) {
            case "mysql": {
                if (mySQLInstance == null && creationRequired) {
                    mySQLInstance = new RDSInstance(RDSInstance.Engine.MySQL, false);
                }
                return mySQLInstance;
            }
            case "oracle": {
                if (oracleInstance == null && creationRequired) {
                    oracleInstance = new RDSInstance(RDSInstance.Engine.Oracle, false);

                }
                return oracleInstance;
            }


            case "postgres": {
                if (postgresInstance == null && creationRequired)
                    postgresInstance = new RDSInstance(RDSInstance.Engine.PostgreSQL, false);


                return postgresInstance;
            }

            case "mssql": {
                if (msSQLServerInstance == null && creationRequired)
                    msSQLServerInstance = new RDSInstance(RDSInstance.Engine.MSSQLServer, false);


                return msSQLServerInstance;
            }

            default: {
                return null;
            }
        }
    }
}
