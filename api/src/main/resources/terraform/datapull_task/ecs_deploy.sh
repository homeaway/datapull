#!/usr/bin/env bash -x

set -x

exitAfterFailure(){
   if [[ "$?" -ne 0 ]] ; then
      echo 'PROCESS FAILED'; exit $rc
   fi
}

# replace variables if present, with parameters
echo "Replace config params"

export AWS_ACCESS_KEY_ID=$1
export AWS_SECRET_ACCESS_KEY=$2
export docker_image_name=$3

env=$4

cd ../../../../../../

pwd

echo "env ============================= $env"

docker run -e MAVEN_OPTS="-Xmx1024M -Xss128M -XX:MetaspaceSize=512M -XX:MaxMetaspaceSize=1024M -XX:+CMSClassUnloadingEnabled" --rm -v $(pwd):/workdir -v $HOME/.m2/:/root/.m2/ -w /workdir  lolhens/ammonite amm api/src/main/resources/overwrite_config.sc $env

echo "env variables written"

cd api/src/main/resources

pwd

echo "reading properties ===="

docker run -e MAVEN_OPTS="-Xmx1024M -Xss128M -XX:MetaspaceSize=512M -XX:MaxMetaspaceSize=1024M -XX:+CMSClassUnloadingEnabled" --rm -v $(pwd):/workdir -v $HOME/.m2/:/root/.m2/ -w /workdir  lolhens/ammonite amm read_application_config.sc $env

echo "new ====================="
pwd

file="application.properties"

bucket_name=''
jar_file_path=''
subnetid_private=''
security_grp=''
server_port=8080
container_port=8080
s3_repo_region=''
aws_repo_region=us-east-1
load_balancer_subnet=''
load_balancer_vpc=''
load_balancer_certificate_arn=''

server_port=${server_port}

echo "server ====== port ${server_port}"
if [ -f "$file" ]
then
  echo "$file found."

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
    elif [[ ${key} == datapull.api.s3_bucket_region ]]
    then
       s3_repo_region=${value}
       echo "s3 region ========= = ${s3_repo_region}"
    elif [[ ${key} == datapull.api.application_subnet ]]
    then
       subnetid_private=${value}
       echo "subnet id   ========= = ${subnetid_private}"
    elif [[ ${key} == datapull.api.application_security_group ]]
    then
       security_grp=${value}
       echo "secuirty group id   ========= = ${security_grp}"
    elif [[ ${key} == datapull.api.load_balancer_subnet ]]
    then
       load_balancer_subnet=${value}
       echo "load balancer subnet found ${load_balancer_subnet}"
    elif [[ ${key} == datapull.api.load_balancer_certificate_arn ]]
    then
       load_balancer_certificate_arn=${value}
       echo "secuirty group id   ========= = ${load_balancer_certificate_arn}"
    elif [[ ${key} == datapull.api.load_balancer_vpc ]]
    then
       load_balancer_vpc=${value}
       echo "load balancer vpc found ${load_balancer_vpc}"
    fi
  done < "$file"
else
  echo "$file not found."
fi


echo "docker_image_name = $docker_image_name"
export AWS_DEFAULT_REGION=$aws_repo_region

echo "jar_file_path $jar_file_path ================="

echo "server port ========================${server_port}"

if [ "$s3_repo_region" == "" ];
then
    s3_repo_region=$aws_repo_region
    echo "s3 region was null so replaced it with app region $s3_repo_region"
fi

if [ "$jar_file_path" == "" ];
then
    jar_file_path=s3://${bucket_name}/datapull-opensource/jars/DataMigrationFramework-1.0-SNAPSHOT-jar-with-dependencies.jar
    echo "$jar_file_path is not null 1st"
fi

echo "Removing created properties file"

rm -r application.properties

echo "Switching to core dir"

aws_account_number="$(docker run --env AWS_ACCESS_KEY_ID=$1 --env AWS_SECRET_ACCESS_KEY=$2 --env AWS_DEFAULT_REGION=$aws_repo_region garland/aws-cli-docker aws sts get-caller-identity --output text --query 'Account')"

cd ../../../../core/src/main/resources/

echo "Replacing the application.yml with the env specific yml file"

cp application-$env.yml application.yml

cd ../../../

echo "Building core Jar"

echo " conf file path"

pwd

docker run -e MAVEN_OPTS="-Xmx1024M -Xss128M -XX:MetaspaceSize=512M -XX:MaxMetaspaceSize=1024M -XX:+CMSClassUnloadingEnabled" --rm -v "$PWD":/usr/src/mymaven -v "$PWD/src/main/resources/m2":/root/.m2 -w /usr/src/mymaven maven:alpine mvn clean install
exitAfterFailure

echo "Uploading core Jar to s3"

docker run -e AWS_ACCESS_KEY_ID=$1 -e AWS_SECRET_ACCESS_KEY=$2 -e AWS_DEFAULT_REGION=$s3_repo_region -v $(pwd):/data garland/aws-cli-docker aws s3 cp /data/target/DataMigrationFramework-1.0-SNAPSHOT-jar-with-dependencies.jar ${jar_file_path}

exitAfterFailure

cd ../api/

echo "Uploading API docker image to ECR $docker_image_name"

docker run -e MAVEN_OPTS="-Xmx1024M -Xss128M -XX:MetaspaceSize=512M -XX:MaxMetaspaceSize=1024M -XX:+CMSClassUnloadingEnabled" --rm -v "$PWD":/usr/src/mymaven -v "$PWD/src/main/resources/m2":/root/.m2 -w /usr/src/mymaven maven:alpine mvn clean install

exitAfterFailure

docker build -t ${docker_image_name} .

cd src/main/resources/terraform/datapull_task

echo "deleting repo =>"

docker run -e AWS_ACCESS_KEY_ID=$1 -e AWS_SECRET_ACCESS_KEY=$2 -e AWS_DEFAULT_REGION=$aws_repo_region garland/aws-cli-docker aws ecr delete-repository --repository-name ${docker_image_name}

echo "creating repo =>"

docker run -e AWS_ACCESS_KEY_ID=$1 -e AWS_SECRET_ACCESS_KEY=$2 -e AWS_DEFAULT_REGION=$aws_repo_region garland/aws-cli-docker aws ecr create-repository --repository-name ${docker_image_name}

echo "login into repo =>"

$(docker run -e AWS_ACCESS_KEY_ID=$1 -e AWS_SECRET_ACCESS_KEY=$2 -e AWS_DEFAULT_REGION=$aws_repo_region garland/aws-cli-docker aws ecr get-login --no-include-email --region $aws_repo_region)

echo "tagging image  =>"

docker tag ${docker_image_name}:latest ${aws_account_number}.dkr.ecr.${aws_repo_region}.amazonaws.com/${docker_image_name}:latest

echo "pushing image into repo =>"

docker push ${aws_account_number}.dkr.ecr.${aws_repo_region}.amazonaws.com/${docker_image_name}:latest

exitAfterFailure

#rm -rf ./.terraform/

echo "Initializing ===>  $bucket_name"
docker run --rm -v $(pwd):/workdir -w /workdir -e AWS_ACCESS_KEY_ID=$1 -e AWS_SECRET_ACCESS_KEY=$2 -e AWS_DEFAULT_REGION=$aws_repo_region -e TF_VAR_subnetid_private=$subnetid_private -e TF_VAR_security_grp=$security_grp -e TF_VAR_aws_account_number=$aws_account_number -e TF_VAR_aws_repo_region=$aws_repo_region -e TF_VAR_host_port=$server_port -e TF_VAR_container_port=$server_port -e TF_VAR_docker_image_name=$docker_image_name -e TF_VAR_load_balancer_subnet=$load_balancer_subnet -e TF_VAR_env=$env -e TF_VAR_load_balancer_vpc=$load_balancer_vpc -e TF_VAR_load_balancer_certificate_arn=$load_balancer_certificate_arn hashicorp/terraform:0.11.10 init -backend-config "bucket=$bucket_name" -backend-config "region=$aws_repo_region"

echo "creating plan ===>"
docker run --rm  -v $(pwd):/workdir -w /workdir -e AWS_ACCESS_KEY_ID=$1 -e AWS_SECRET_ACCESS_KEY=$2 -e AWS_DEFAULT_REGION=$aws_repo_region -e TF_VAR_subnetid_private=$subnetid_private -e TF_VAR_security_grp=$security_grp -e TF_VAR_aws_account_number=$aws_account_number -e TF_VAR_aws_repo_region=$aws_repo_region -e TF_VAR_host_port=$server_port -e TF_VAR_container_port=$server_port -e TF_VAR_docker_image_name=$docker_image_name -e TF_VAR_load_balancer_subnet=$load_balancer_subnet -e TF_VAR_env=$env -e TF_VAR_load_balancer_vpc=$load_balancer_vpc -e TF_VAR_load_balancer_certificate_arn=$load_balancer_certificate_arn hashicorp/terraform:0.11.10 plan -out the_plan.tfplan
echo "applying plan ===> "
docker run --rm -v $(pwd):/workdir -w /workdir -e AWS_ACCESS_KEY_ID=$1 -e AWS_SECRET_ACCESS_KEY=$2 -e AWS_DEFAULT_REGION=$aws_repo_region -e TF_VAR_subnetid_private=$subnetid_private -e TF_VAR_security_grp=$security_grp -e TF_VAR_aws_account_number=$aws_account_number -e TF_VAR_aws_repo_region=$aws_repo_region -e TF_VAR_host_port=$server_port -e TF_VAR_container_port=$server_port -e TF_VAR_docker_image_name=$docker_image_name -e TF_VAR_load_balancer_subnet=$load_balancer_subnet -e TF_VAR_env=$env -e TF_VAR_load_balancer_vpc=$load_balancer_vpc -e TF_VAR_load_balancer_certificate_arn=$load_balancer_certificate_arn hashicorp/terraform:0.11.10 apply the_plan.tfplan

