#!/usr/bin/env bash

set -x

exitAfterFailure(){
   if [[ "$?" -ne 0 ]] ; then
      echo 'PROCESS FAILED'; exit $rc
   fi
}

# replace variables if present, with parameters
echo "Replace config params"
export docker_image_name="datatools-datapull-api"
env=$1
export AWS_PROFILE="egdptest"

cd ../../../

echo "env ============================= ${env}"

echo "Deleting api's application.yml if already exists"
rm -rf api/src/main/resources/application.yml

echo "deleting core application.yml file if already existing"
rm -rf core/src/main/resources/application.yml

docker run -e MAVEN_OPTS="-Xmx1024M -Xss128M -XX:MetaspaceSize=512M -XX:MaxMetaspaceSize=1024M -XX:+CMSClassUnloadingEnabled" --rm -v ${PWD}:/workdir -v "${HOME}"/.m2/:/root/.m2/ -w /workdir  lolhens/ammonite amm api/src/main/resources/overwrite_config.sc ${env}

echo "env variables written"

cd api/src/main/resources

pwd


echo "reading properties ===="

docker run -e MAVEN_OPTS="-Xmx1024M -Xss128M -XX:MetaspaceSize=512M -XX:MaxMetaspaceSize=1024M -XX:+CMSClassUnloadingEnabled" --rm -v $(pwd):/workdir -v ${HOME}/.m2/:/root/.m2/ -w /workdir  lolhens/ammonite amm read_application_config.sc ${env}

echo "new ====================="
pwd

file="application.properties"

bucket_name=''
jar_file_path=''
application_subnet_1=''
application_subnet_2=''
security_grp=''
server_port=8080
container_port=8080
aws_repo_region=us-east-1
vpc_id=''
load_balancer_certificate_arn=''

server_port=${server_port}

echo "server ====== port ${server_port}"
if [ -f "${file}" ]
then
  echo "${file} found."

  while IFS='=' read -r key value
  do
    echo "${key}===============================${value}"
    if [[ ${key} == server.port ]]
    then
       server_port=${value}
       echo "server port found ${server_port}"
    elif [[ ${key} == datapull.api.s3_bucket_name ]]
    then
       bucket_name=${value}
       echo "bucket name found = ${bucket_name}"
    elif [[ ${key} == datapull.api.s3_jar_path ]]
    then
       jar_file_path=${value}
       echo "jar path name found ${jar_file_path}"
    elif [[ ${key} == datapull.application.region ]]
    then
       aws_repo_region=${value}
       echo "aws region ========= = ${aws_repo_region}"
    elif [[ ${key} == datapull.api.application_subnet_1 ]]
    then
       application_subnet_1=${value}
       echo "subnet id   ========= = ${application_subnet_1}"
        elif [[ ${key} == datapull.api.application_subnet_2 ]]
    then
       application_subnet_2=${value}
       echo "subnet id   ========= = ${application_subnet_2}"
    elif [[ ${key} == datapull.api.application_security_group ]]
    then
       security_grp=${value}
       echo "security group id   ========= = ${security_grp}"
       elif [[ ${key} == datapull.api.vpc_id ]]
    then
       vpc_id=${value}
       echo "security group id   ========= = ${vpc_id}"
    elif [[ ${key} == datapull.api.load_balancer_certificate_arn ]]
    then
       load_balancer_certificate_arn=${value}
       echo "security group id   ========= = ${load_balancer_certificate_arn}"
    fi
  done < "${file}"
else
  echo "${file} not found."
fi


echo "docker_image_name = $docker_image_name"
export AWS_DEFAULT_REGION=$aws_repo_region

echo "server port ========================${server_port}"

jar_file_path="s3://${bucket_name}/datapull-opensource/jars/DataMigrationFramework-1.0-SNAPSHOT-jar-with-dependencies.jar"

echo "jar_file_path $jar_file_path ================="

echo "Removing created properties file"

rm -rf application.properties

echo "Switching to core dir"

aws_account_number="$(docker run -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_DEFAULT_REGION -e AWS_PROFILE -v "${HOME}/.aws":"/root/.aws" garland/aws-cli-docker aws sts get-caller-identity --output text --query 'Account')"

cd ../../../../core/src/main/resources/

cd ../../../

echo "Building core Jar"

echo " conf file path"

pwd

docker run -e MAVEN_OPTS="-Xmx1024M -Xss128M -XX:MetaspaceSize=512M -XX:MaxMetaspaceSize=1024M -XX:+CMSClassUnloadingEnabled" --rm -v "${PWD}":/usr/src/mymaven -v "${HOME}/.m2":/root/.m2 -w /usr/src/mymaven maven:alpine mvn clean install
exitAfterFailure

echo "Uploading core Jar to s3"

docker run -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_DEFAULT_REGION -e AWS_PROFILE -v "${PWD}":/data -v "${HOME}/.aws":"/root/.aws" garland/aws-cli-docker aws s3 cp /data/target/DataMigrationFramework-1.0-SNAPSHOT-jar-with-dependencies.jar "$jar_file_path"

exitAfterFailure

cd ../api/

echo "Uploading API docker image to ECR $docker_image_name"

docker run -e MAVEN_OPTS="-Xmx1024M -Xss128M -XX:MetaspaceSize=512M -XX:MaxMetaspaceSize=1024M -XX:+CMSClassUnloadingEnabled" --rm -v "${PWD}":/usr/src/mymaven -v "${HOME}/.m2":/root/.m2 -w /usr/src/mymaven maven:alpine mvn clean install

exitAfterFailure
ENV TZ=America/Los_Angeles
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

docker build -t "${docker_image_name}" .

cd terraform/datapull_task

echo "deleting repo =>"
#
docker run -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_DEFAULT_REGION -e AWS_PROFILE -v "${HOME}/.aws":"/root/.aws" garland/aws-cli-docker aws ecr delete-repository --repository-name "$docker_image_name"
#
echo "creating repo =>"
#
docker run -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_DEFAULT_REGION -e AWS_PROFILE -v "${HOME}/.aws":"/root/.aws" garland/aws-cli-docker aws ecr create-repository --repository-name "$docker_image_name"
#
#
echo "login into repo =>"
#
$(docker run -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_DEFAULT_REGION -e AWS_PROFILE -v "${HOME}/.aws":"/root/.aws" garland/aws-cli-docker aws ecr get-login --no-include-email --region $aws_repo_region)

echo "tagging image  =>"

docker tag "${docker_image_name}":latest "${aws_account_number}".dkr.ecr."${aws_repo_region}".amazonaws.com/"${docker_image_name}":latest

echo "pushing image into repo =>"

docker push "${aws_account_number}".dkr.ecr."${aws_repo_region}".amazonaws.com/"${docker_image_name}":latest

exitAfterFailure

rm -rf ./.terraform/

echo "Initializing ===>  $bucket_name"
#
#echo $application_subnet

docker run --rm -v $(pwd):/workdir -v "${HOME}/.aws":"/root/.aws" -w /workdir -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_DEFAULT_REGION -e AWS_PROFILE -e TF_VAR_application_subnet_1="$application_subnet_1" -e TF_VAR_application_subnet_2="$application_subnet_2" -e TF_VAR_security_grp="$security_grp" -e TF_VAR_aws_account_number="$aws_account_number" -e TF_VAR_application_region="$aws_repo_region" -e TF_VAR_host_port="$server_port" -e TF_VAR_container_port="$container_port" -e TF_VAR_docker_image_name="$docker_image_name" -e TF_VAR_env="${env}" -e TF_VAR_vpc_id="$vpc_id" -e TF_VAR_load_balancer_certificate_arn="$load_balancer_certificate_arn" hashicorp/terraform init -backend-config "bucket=$bucket_name" -backend-config "region=$aws_repo_region"

echo "creating plan ===>"
docker run --rm  -v $(pwd):/workdir -v "${HOME}/.aws":"/root/.aws" -w /workdir -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_DEFAULT_REGION -e AWS_PROFILE -e TF_VAR_application_subnet_1="$application_subnet_1" -e TF_VAR_application_subnet_2="$application_subnet_2" -e TF_VAR_security_grp="$security_grp" -e TF_VAR_aws_account_number="$aws_account_number" -e TF_VAR_application_region="$aws_repo_region" -e TF_VAR_host_port="$server_port" -e TF_VAR_container_port="$server_port" -e TF_VAR_docker_image_name="$docker_image_name" -e TF_VAR_env="${env}" -e TF_VAR_vpc_id="$vpc_id" -e TF_VAR_load_balancer_certificate_arn="$load_balancer_certificate_arn" hashicorp/terraform plan -out the_plan.tfplan
echo "applying plan ===> "
docker run --rm -v $(pwd):/workdir -v "${HOME}/.aws":"/root/.aws" -w /workdir -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_DEFAULT_REGION -e AWS_PROFILE -e TF_VAR_application_subnet_1="$application_subnet_1" -e TF_VAR_application_subnet_2="$application_subnet_2" -e TF_VAR_security_grp="$security_grp" -e TF_VAR_aws_account_number="$aws_account_number" -e TF_VAR_application_region="$aws_repo_region" -e TF_VAR_host_port="$server_port" -e TF_VAR_container_port="$server_port" -e TF_VAR_docker_image_name="$docker_image_name" -e TF_VAR_env="${env}" -e TF_VAR_vpc_id="$vpc_id" -e TF_VAR_load_balancer_certificate_arn="$load_balancer_certificate_arn" hashicorp/terraform apply the_plan.tfplan

