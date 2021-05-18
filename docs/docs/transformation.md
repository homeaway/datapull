```json
{
  "useremailaddress": "your_email_address",
  "migrations": [
    {
      "source": {
        "platform": "elastic",
        "clustername":"cluster_name",
        "port": "9201",
        "login": "user_id",
        "password": "password",
        "index": "test",
        "version": "6.3.0",
        "alias":"U"
      },
      "sql": {
        "query": "SELECT id, name, DATE_FORMAT(current_date(), \"y-MM-dd HH:mm:ss.SSS\") as date FROM U"
      },
      "destination": {
        "platform": "mssql",
        "awsenv": "test",
        "server": "server_name",
        "database": "database",
        "table": "destination_table",
        "login": "user_id",
        "password": "password"
      }
    }
  ],
  "cluster": {
        "pipelinename": "emr_cluster_name",
        "awsenv": "dev",
        "portfolio": "portfolio_name",
        "product": "product_name",
        "ec2instanceprofile": "ec2_instance_profile",
        "terminateclusterafterexecution": "false",
        "ComponentInfo":"YOUR_Component-UUID_dominion"
  }
}
```

