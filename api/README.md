# Run DataPull API Locally
In the file application-{env}.yml, set the following keys
```
datapull.api.accessKey: <aws account access key> 
datapull.api.secretKey: <aws account secret key>
datapull.application.region: <aws region> 
datapull.api.s3_bucket_name: <s3 bucket for app>
datapull.api.s3_jar_path: <DataPull Core Jar Path>
datapull.api.application_subnet: <subnet of emr cluster>
datapull.api.application_security_group: <security groups for emr>
```
To use separate regions for S3 and EMR,  set properties `datapull.api.s3_bucket_region` and `datapull.emr.emr_region` instead. 
## Using IDE
DataPull API is a spring boot app which can be run using any IDE that has the required Spring boot dependencies.
1. Create run configuration for the app. The simplest run configuration contains just the name of the main class, and an environment variable with name ```env```. For DataPull API, the main class is ```com.homeaway.datapullclient.start.DatapullclientApplication```. Based on the environment variable ```env``` the corresponding application-{env}.yml will be used. Additional configurations such as xms and xmx parameters can be provided for better control over the app.
1. Run the application using the IDE's `Run` or `Debug` with the configuration created earlier. The API can be accessed at http://localhost:8080/swagger-ui.html

## Using Docker
1. In Terminal, run
    ```
    cd api
    # build the API jar
    mvn clean install
    # build the docker image
    docker build -t datapull-web-api
    # run docker container. -e sets the environment variable based on which the respective application-{env}.yml file is picked
    docker run -p8080:8080 -e env=dev -it datapull-web-api
    ```
1. The API can be accessed at http://localhost:8080/swagger-ui.html