FROM openjdk:8-jdk-slim
VOLUME /tmp
ARG JAR_FILE=target/datapullclient-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} datapullclient.jar
EXPOSE 8080
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/datapullclient.jar"]