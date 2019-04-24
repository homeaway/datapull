@echo off
setlocal ENABLEDELAYEDEXPANSION
cls & Color 0A
:: replace variables if present, with parameters

echo "Replace config params"
set AWS_ACCESS_KEY_ID=%1
set AWS_SECRET_ACCESS_KEY=%2
set docker_image_name=%3
set env=%4
set file="..\..\application_%env%.properties"
echo %file%
echo "env ========================%env%"
set bucket_name=""
set jar_file_path=""
set subnetid_private=""
set security_grp=""
set server_port=8080
set container_port=8080
set s3_repo_region=""
set aws_repo_region=us-east-1
IF EXIST %file% (
  echo "file exist"
  echo test
  for /f "tokens=1,2 delims==" %%a in ('type %file%') do (
    if /i "%%a"=="server.port" (
        set "server_port=%%b"
        echo "server port found :!server_port! "
    )
    if /i "%%a"=="APPLICATION_S3_REPOSITORY_BUCKET_NAME" (
        set "bucket_name=%%b"
        echo  "bucket name found:!bucket_name!"
    )
    IF /i "%%a"=="S3_JAR_PATH" (
        set "jar_file_path=%%b"
        echo "s3 jar file path found: !jar_file_path!"
    )
    IF /i "%%a"=="APP_REGION" (
        set "aws_repo_region=%%b"
        echo "app region found: !aws_repo_region!"
    )
    IF /i "%%a"=="S3_BUCKET_REGION" (
        set "s3_repo_region=%%b"
        echo "s3 bucket region found: !s3_repo_region!"
    )
    IF /i "%%a"=="APPLICATION_SUBNET" (
        set "subnetid_private=%%b"
        echo "application subnet found: !subnetid_private!"
    )
    IF /i "%%a"=="APPLICATION_SECURITY_GROUP" (
        set "security_grp=%%b"
        echo "security group found: !security_grp!"
    )
  )
) else (
  echo "file not found."
)
echo "docker_image_name = %docker_image_name%"
set AWS_DEFAULT_REGION=%aws_repo_region%
if /i "%s3_repo_region%"=="" (
    set "s3_repo_region=%aws_repo_region%"
        echo "s3 region was null so replaced it with app region !s3_repo_region!"
)
IF /i !jar_file_path!=="" (
    set "jar_file_path=s3://%bucket_name%/datapull-opensource/jars/DataMigrationFramework-1.0-SNAPSHOT-jar-with-dependencies.jar"
    echo "updated jar file path : !jar_file_path!"
)
docker run --env AWS_ACCESS_KEY_ID=%1 --env AWS_SECRET_ACCESS_KEY=%2 --env AWS_DEFAULT_REGION=%aws_repo_region% garland/aws-cli-docker aws sts get-caller-identity --output text > temp.txt
set /p var=<temp.txt
for /f "tokens=1" %%a IN ("%var%") do ( set aws_account_number=%%a )
CALL :TRIM %aws_account_number% aws_account_number
echo "account number =================%aws_account_number%"
del temp.txt

echo "Building core Jar"
cd ../../../../../../core
docker run -e MAVEN_OPTS="-Xmx1024M -Xss128M -XX:MetaspaceSize=512M -XX:MaxMetaspaceSize=1024M -XX:+CMSClassUnloadingEnabled" -it --rm -v %cd%:/workdir  -v %USERPROFILE%/.m2/:/root/.m2/ -w /workdir ^
maven:alpine mvn install:install-file -Dfile=src/main/resources/ojdbc-jar/ojdbc6.jar -DgroupId=com.oracle -DartifactId=ojdbc6 -Dversion=11.2.0.3 -Dpackaging=jar
docker run -e MAVEN_OPTS="-Xmx1024M -Xss128M -XX:MetaspaceSize=512M -XX:MaxMetaspaceSize=1024M -XX:+CMSClassUnloadingEnabled" -it --rm -v %cd%:/workdir  -v %USERPROFILE%/.m2/:/root/.m2/ -w /workdir  maven:alpine mvn clean install
echo "Uploading core Jar to s3"
echo "S3 repo region = --- %aws_repo_region%"
docker run -e AWS_ACCESS_KEY_ID=%1 -e AWS_SECRET_ACCESS_KEY=%2 -e AWS_DEFAULT_REGION=%aws_repo_region% -v %cd%:/data garland/aws-cli-docker aws s3 cp /data/target/DataMigrationFramework-1.0-SNAPSHOT-jar-with-dependencies.jar %jar_file_path%
cd ../api/
echo "Uploading API docker image to ECR"
docker run -e MAVEN_OPTS="-Xmx1024M -Xss128M -XX:MetaspaceSize=512M -XX:MaxMetaspaceSize=1024M -XX:+CMSClassUnloadingEnabled" -it --rm -v %cd%:/workdir  -v %USERPROFILE%/.m2/:/root/.m2/ -w /workdir  maven:alpine mvn clean install
docker build -t %docker_image_name% .
cd src/main/resources/terraform/datapull_task
echo "deleting repo ========================================="
docker run -e AWS_ACCESS_KEY_ID=%1 -e AWS_SECRET_ACCESS_KEY=%2 -e AWS_DEFAULT_REGION=%aws_repo_region% garland/aws-cli-docker aws ecr delete-repository --repository-name %docker_image_name%
echo "creating repo =============================     ============"

docker run -e AWS_ACCESS_KEY_ID=%1 -e AWS_SECRET_ACCESS_KEY=%2 -e AWS_DEFAULT_REGION=%aws_repo_region% garland/aws-cli-docker aws ecr create-repository --repository-name %docker_image_name%
echo "login into repo =============================     ============"
docker run -e AWS_ACCESS_KEY_ID=%1 -e AWS_SECRET_ACCESS_KEY=%2 -e AWS_DEFAULT_REGION=%aws_repo_region% garland/aws-cli-docker aws ecr get-login --no-include-email --region us-east-1>temp.txt
for /f "delims=" %%x in (temp.txt) do set loginCommand=%%x
del temp.txt
%loginCommand%
echo "tagging image  =============================     ============"
docker tag %docker_image_name%:latest %aws_account_number%.dkr.ecr.%aws_repo_region%.amazonaws.com/%docker_image_name%:latest
echo "pushing image into repo  =============================     ============"
docker push %aws_account_number%.dkr.ecr.%aws_repo_region%.amazonaws.com/%docker_image_name%:latest
echo "Initializing ===>"
docker run --rm -v %cd%:/workdir -w /workdir -e AWS_ACCESS_KEY_ID=%1 -e AWS_SECRET_ACCESS_KEY=%2 -e AWS_DEFAULT_REGION=%aws_repo_region% -e TF_VAR_subnetid_private=%subnetid_private% -e TF_VAR_security_grp=%security_grp% -e TF_VAR_aws_account_number=%aws_account_number% -e TF_VAR_aws_repo_region=%aws_repo_region% -e TF_VAR_host_port=%server_port% -e TF_VAR_container_port=%server_port% -e TF_VAR_docker_image_name=%docker_image_name% hashicorp/terraform:0.11.10 init -backend-config "bucket=%bucket_name%" ^
     -backend-config "region=%aws_repo_region%"
echo "creating plan ===>"
docker run --rm  -v %cd%:/workdir -w /workdir -e AWS_ACCESS_KEY_ID=%1 -e AWS_SECRET_ACCESS_KEY=%2 -e AWS_DEFAULT_REGION=%aws_repo_region% -e TF_VAR_subnetid_private=%subnetid_private% -e TF_VAR_security_grp=%security_grp% -e TF_VAR_aws_account_number=%aws_account_number% -e TF_VAR_aws_repo_region=%aws_repo_region% -e TF_VAR_host_port=%server_port% -e TF_VAR_container_port=%server_port% -e TF_VAR_docker_image_name=%docker_image_name% hashicorp/terraform:0.11.10 plan -out the_plan.tfplan
echo "applying plan ===> "
docker run --rm -v %cd%:/workdir -w /workdir -e AWS_ACCESS_KEY_ID=%1 -e AWS_SECRET_ACCESS_KEY=%2 -e AWS_DEFAULT_REGION=%aws_repo_region% -e TF_VAR_subnetid_private=%subnetid_private% -e TF_VAR_security_grp=%security_grp% -e TF_VAR_aws_account_number=%aws_account_number% -e TF_VAR_aws_repo_region=%aws_repo_region% -e TF_VAR_host_port=%server_port% -e TF_VAR_container_port=%server_port% -e TF_VAR_docker_image_name=%docker_image_name% hashicorp/terraform:0.11.10 apply the_plan.tfplan

endlocal

:TRIM
SET %2=%1
GOTO :EOF
