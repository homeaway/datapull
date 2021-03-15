FROM cnfldemos/cp-server-connect-datagen:0.3.2-5.5.0
# FROM confluentinc/cp-kafka-connect-base:latest
RUN   confluent-hub install --no-prompt hpgrahsl/kafka-connect-mongodb:1.1.0 && \
    confluent-hub install --no-prompt confluentinc/kafka-connect-s3-source:1.3.2 && \
    confluent-hub install --no-prompt debezium/debezium-connector-mysql:1.2.2