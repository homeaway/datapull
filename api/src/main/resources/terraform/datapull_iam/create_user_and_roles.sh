#!/usr/bin/env bash

# this script takes the inputs aws_access_key aws_secret_access_key aws_region datapull_s3_bucket

export AWS_ACCESS_KEY_ID=$1
export AWS_SECRET_ACCESS_KEY=$2
export AWS_DEFAULT_REGION=$3

echo "creating EMR default roles ===> "
docker run -e AWS_ACCESS_KEY_ID=$1 -e AWS_SECRET_ACCESS_KEY=$2 -e AWS_DEFAULT_REGION=$3 garland/aws-cli-docker aws emr create-default-roles 
echo "Initializing Terraform ===>"
docker run --rm -v $(pwd):/workdir -w /workdir -e AWS_ACCESS_KEY_ID=$1 -e AWS_SECRET_ACCESS_KEY=$2 -e AWS_DEFAULT_REGION=$3 -e TF_VAR_datapull_s3_bucket=$4 -e TF_VAR_docker_image_name=$5 TF_VAR_ui_docker_image_name=$6 hashicorp/terraform:0.11.10 init -backend-config "bucket=$4" \
     -backend-config "region=$3"
echo "creating plan ===>"
docker run --rm  -v $(pwd):/workdir -w /workdir -e AWS_ACCESS_KEY_ID=$1 -e AWS_SECRET_ACCESS_KEY=$2 -e AWS_DEFAULT_REGION=$3 -e TF_VAR_datapull_s3_bucket=$4 -e TF_VAR_docker_image_name=$5 TF_VAR_ui_docker_image_name=$6 hashicorp/terraform:0.11.10 plan -out the_plan.tfplan
echo "applying plan ===> "
docker run --rm -v $(pwd):/workdir -w /workdir -e AWS_ACCESS_KEY_ID=$1 -e AWS_SECRET_ACCESS_KEY=$2 -e AWS_DEFAULT_REGION=$3 -e TF_VAR_datapull_s3_bucket=$4 -e TF_VAR_docker_image_name=$5  TF_VAR_ui_docker_image_name=$6 hashicorp/terraform:0.11.10 apply the_plan.tfplan
