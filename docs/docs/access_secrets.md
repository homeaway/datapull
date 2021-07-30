# Access secrets securely

DataPull supports the ability to read secrets (database credentials, access keys, client certificates, etc.) securely without exposing them in plain-text in the inpout JSON, if the secrets are stored in a secret store that can take AWS IAM Principals for authentication. 

## Client Certificates

Client certificates can be stored as JKS files in an SSE-S3 encrypted S3 bucket, and DataPull can access them if DataPull is installed in AWS. 

### Store certificates in S3 bucket
Please store the client certificates as JKS files in an S3 bucket that is accessible by the `emr_ec2_datapull_role` (or [custom emr ec2 role](custom_emr_ec2_role.md)) IAM role that was created while [installing DataPull on AWS](install_on_aws.md). It is recommended to store the JKS files in an  SSE-S3 encrypted S3 bucket, and use an [EMR Security Configuration](https://docs.aws.amazon.com/emr/latest/ManagementGuide/emr-create-security-configuration.html) that has access to the S3 bucket.

### Copy certificates to EMR using jksfiles input json array
Most Spark Connectors that use SSL client authentication, require the JKS Certificate files to be available on all the executors of the EMR Spark cluster. DataPull makes this possible, using the `jksfiles` array attribute in the input json. For example, if a Kafka topic requires a JKS certificate file for authentication, then it can be specified as shown in the example below...
```json
{
...
  "destination": {
    "platform": "kafka",
    "bootstrapservers": "broker_server_host_name:broker_port",
    "schemaregistries": "schema_registry_url:schema_registry_port",
    "topic": "some_topic_name",
    "keystorepath": "/mnt/bootstrapfiles/some_keystore_file.jks",
    "truststorepath": "/mnt/bootstrapfiles/som_truststore_file.jks",
    "keystorepassword": "inlinesecret{{\"secretstore\": \"aws_secrets_manager\", \"secretname\": \"some_secret_path\", \"secretkeyname\": \"some_secret_key_name\"}}",
    "truststorepassword": "inlinesecret{{\"secretstore\": \"aws_secrets_manager\", \"secretname\": \"some_secret_path\", \"secretkeyname\": \"some_secret_key_name\"}}",
    "keypassword": "inlinesecret{{\"secretstore\": \"aws_secrets_manager\", \"secretname\": \"some_secret_path\", \"secretkeyname\": \"some_secret_key_name\"}}",
    "jksfiles": [
      "s3://some_bucket_name/some_path/some_keystore_file.jks",
      "s3://some_bucket_name/some_path/some_truststore_file.jks"
    ]
  }
...        
}
```

## Other secrets

### Retrieve secrets from AWS Secrets Manager

First, please store the secret in a secret name/path in AWS Secrets Manager that is accessible by DataPull's `emr_ec2_datapull_role` (or [custom emr ec2 role](custom_emr_ec2_role.md)) IAM role that was created while [installing DataPull on AWS](install_on_aws.md). Secrets in AWS Secrets Manager are usually stored either as a JSON object or as a binary key/value array. In such cases, please specify the key of the secret object. 

Next, use the `inlinesecret{{}}` syntax anywhere in the input json where you want the secret to be substituted. Examples of this syntax can be found in the partial input json above. 