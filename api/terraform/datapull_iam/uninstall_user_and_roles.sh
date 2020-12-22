#!/usr/bin/env bash

# this script takes the inputs AWS_PROFILE s3_bucket_name region
export AWS_DEFAULT_REGION=$3 #this is not required once https://github.com/terraform-providers/terraform-provider-aws/issues/7750 is resolved

api_docker_image_name="datatools-datapull-api"
ui_docker_image_name="datatools-datapull-ui"
bucket_name=$2
export AWS_PROFILE=$1

echo "Initializing Terraform ===>"
docker run --rm -v "$(pwd)":/workdir  -v "${HOME}/.aws":"/root/.aws" -w /workdir -e AWS_DEFAULT_REGION -e AWS_PROFILE -e TF_VAR_datapull_s3_bucket=${bucket_name} -e TF_VAR_docker_image_name=${api_docker_image_name} -e TF_VAR_ui_docker_image_name=${ui_docker_image_name} hashicorp/terraform init   -backend-config "bucket=${bucket_name}" -backend-config "profile=${AWS_PROFILE}"

echo "creating plan ===>"
docker run --rm  -v "$(pwd)":/workdir -v "${HOME}/.aws":"/root/.aws" -w /workdir -e AWS_DEFAULT_REGION -e AWS_PROFILE -e TF_VAR_datapull_s3_bucket=${bucket_name} -e TF_VAR_docker_image_name=${api_docker_image_name} -e TF_VAR_ui_docker_image_name=${ui_docker_image_name} hashicorp/terraform plan -destroy -out the_plan.tfplan

echo "applying plan ===> "
docker run --rm -v "$(pwd)":/workdir -v "${HOME}/.aws":"/root/.aws" -w /workdir -e AWS_DEFAULT_REGION -e AWS_PROFILE -e TF_VAR_datapull_s3_bucket=${bucket_name} -e TF_VAR_docker_image_name=${api_docker_image_name} -e TF_VAR_ui_docker_image_name=${ui_docker_image_name} hashicorp/terraform apply the_plan.tfplan