# Run DataPull Api Locally

## DataPull api can be run locally in the following two ways :-
1)	With valid run configuration in any IDE. It is a spring boot app which can be run using any IDE. (Note - Spring boot dependencies required)
2)	As a Docker app.

### Running the api using an IDE –
1) In the application-{env}.yml, add access key and secret key as stated below :-
   datapull.api.accessKey: <your aws account access key> 
   datapull.api.secretKey: <your aws account secret key>
   datapull.application.region: <your aws region>. If you want to use separate region for S3 and EMR use properties datapull.api.s3_bucket_region and datapull.emr.emr_region instead. 
   This step ensures that you have necessary permissions required to run DataPull           api.
2) In addition to the access key and secret key, you have to configure following properties as well:-
   datapull.api.s3_bucket_name=s3_bucket_for_app.
   datapull.api.s3_jar_path=DataPull_Core_Jar_Path 
   datapull.api.application_subnet=subnet_emr_cluster.
   datapull.api.application_security_group=security_groups_for_emr.
3) Create run configuration for the app. Simplest run configuration contains just the name of the main class. For DataPull API, the main class is com.homeaway.datapullclient.start.DatapullclientApplication. For the run configuration, also provide environment variable with name ```env```. Based on your environment variable ```env``` desired application-{env}.yml will be picked. In addition to main class name you can also provide additional configurations(such as xms and xmx parameters) for better control over the app.
Click on Run in the main menu and select the above created configuration. The app would start on your local and can be accessed through http://localhost:8080/swagger-ui.html by default.

### Running the api as a Docker App –
1)	In the application-{env}.yml, add access key and secret key as stated below :-
    datapull.api.accessKey: <your aws account access key> 
    datapull.api.secretKey: <your aws account secret key>
    datapull.application.region: <your aws region>. If you want to use separate region for S3 and EMR use properties datapull.api.s3_bucket_region and datapull.emr.emr_region instead. 
    This step ensures that you have necessary permissions required to run DataPull           api.
2)	In addition to the access key and secret key, you have to configure following properties as well:-
    datapull.api.s3_bucket_name=s3_bucket_for_app.
    datapull.api.s3_jar_path=DataPull_Core_Jar_Path 
    datapull.api.application_subnet=subnet_emr_cluster.
    datapull.api.application_security_group=security_groups_for_emr.
3)	Run mvn clean install from within /api folder to build the jar for our docker app.
4)	Run docker build -t image_name from within /api folder to build the docker image for our app.
5)	Run docker image using the following command :-
docker run -p8080:8080 -e env=dev -it image_name.
-e sets the environment variable based on which the respective application-{env}.yml file is picked.
  6)  App can be accessed through http://localhost:8080/swagger-ui.html

## Debugging the DataPull Api:-

### Debugging the api using an IDE –
1)	In the application-{env}.yml, add access key and secret key as stated below :-
    datapull.api.accessKey: <your aws account access key> 
    datapull.api.secretKey: <your aws account secret key>
    datapull.application.region: <your aws region>. If you want to use separate region for S3 and EMR use properties datapull.api.s3_bucket_region and datapull.emr.emr_region instead. 
    This step ensures that you have necessary permissions required to run DataPull           api.
2)	In addition to the access key and secret key, you have to configure following properties as well:-
    datapull.api.s3_bucket_name=s3_bucket_for_app.
    datapull.api.s3_jar_path=DataPull_Core_Jar_Path 
    datapull.api.application_subnet=subnet_emr_cluster.
    datapull.api.application_security_group=security_groups_for_emr.
3)	Create Debug configuration for the app. Simplest Debug configuration contains just the name of the main class. For DataPull API, the main class is com.homeaway.datapullclient.start.DatapullclientApplication. Click on Debug in the main menu and select the above created configuration. The app would start 
on your local and can be accessed through http://localhost:8080/swagger-ui.html by default.
4)	Debug points can now be configured and input json can be submitted through the swagger ui.
