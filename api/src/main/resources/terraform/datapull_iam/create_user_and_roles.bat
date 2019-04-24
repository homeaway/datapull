@echo off
setlocal ENABLEDELAYEDEXPANSION
cls & Color 0A

::this script takes the inputs aws_access_key aws_secret_access_key aws_region datapull_s3_bucket

set AWS_ACCESS_KEY_ID=%1
set AWS_SECRET_ACCESS_KEY=%2
set AWS_DEFAULT_REGION=%3

echo "creating EMR default roles ===> "
docker run -e AWS_ACCESS_KEY_ID=%1 -e AWS_SECRET_ACCESS_KEY=%2 -e AWS_DEFAULT_REGION=%3 garland/aws-cli-docker aws emr create-default-roles
echo "Initializing Terraform ===>"
docker run --rm -v %cd%:/workdir -w /workdir -e AWS_ACCESS_KEY_ID=%1 -e AWS_SECRET_ACCESS_KEY=%2 -e AWS_DEFAULT_REGION=%3 -e TF_VAR_datapull_s3_bucket=%4 -e TF_VAR_docker_image_name=%5 hashicorp/terraform:light init
echo "creating plan ===>"
docker run --rm  -v %cd%:/workdir -w /workdir -e AWS_ACCESS_KEY_ID=%1 -e AWS_SECRET_ACCESS_KEY=%2 -e AWS_DEFAULT_REGION=%3 -e TF_VAR_datapull_s3_bucket=%4 -e TF_VAR_docker_image_name=%5 hashicorp/terraform:light plan -out the_plan.tfplan
echo "applying plan ===> "
docker run --rm -v %cd%:/workdir -w /workdir -e AWS_ACCESS_KEY_ID=%1 -e AWS_SECRET_ACCESS_KEY=%2 -e AWS_DEFAULT_REGION=%3 -e TF_VAR_datapull_s3_bucket=%4 -e TF_VAR_docker_image_name=%5 hashicorp/terraform:light apply the_plan.tfplan

endlocal