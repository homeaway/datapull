@echo off
setlocal ENABLEDELAYEDEXPANSION
cls & Color 0A

::this script takes the inputs AWS_PROFILE s3_bucket_name region 

::this is not required once https://github.com/terraform-providers/terraform-provider-aws/issues/7750 is resolved
set AWS_DEFAULT_REGION=%3

SET api_docker_image_name="datatools-datapull-api"
SET ui_docker_image_name="datatools-datapull-ui"
SET bucket_name=%2
SET AWS_PROFILE=%1

echo "creating EMR default roles ===> "
docker run --rm -v "%USERPROFILE%/.aws":"/root/.aws" amazon/aws-cli --profile "%AWS_PROFILE%" emr create-default-roles

echo "Initializing Terraform ===>"
docker run --rm -v "%CD%":"/workdir"  -v "%USERPROFILE%/.aws":"/root/.aws" -w /workdir -e AWS_DEFAULT_REGION -e AWS_PROFILE -e TF_VAR_datapull_s3_bucket=%bucket_name% -e TF_VAR_docker_image_name=%api_docker_image_name% -e TF_VAR_ui_docker_image_name=%ui_docker_image_name% hashicorp/terraform init   -backend-config "bucket=%bucket_name%" -backend-config "profile=%AWS_PROFILE%"

echo "creating plan ===>"
docker run --rm  -v "%CD%":"/workdir" -v "%USERPROFILE%/.aws":"/root/.aws" -w /workdir -e AWS_DEFAULT_REGION -e AWS_PROFILE -e TF_VAR_datapull_s3_bucket=%bucket_name% -e TF_VAR_docker_image_name=%api_docker_image_name% -e TF_VAR_ui_docker_image_name=%ui_docker_image_name% hashicorp/terraform plan -out the_plan.tfplan

echo "applying plan ===> "
docker run --rm -v "%CD%":"/workdir" -v "%USERPROFILE%/.aws":"/root/.aws" -w /workdir -e AWS_DEFAULT_REGION -e AWS_PROFILE -e TF_VAR_datapull_s3_bucket=%bucket_name% -e TF_VAR_docker_image_name=%api_docker_image_name% -e TF_VAR_ui_docker_image_name=%ui_docker_image_name% hashicorp/terraform apply the_plan.tfplan

echo "store datapull_user credentials to AWS profile datapull_user ===> "
for /F "usebackq delims=" %A in (`docker run --rm -v "%CD%":/workdir -v "%USERPROFILE%/.aws":"/root/.aws" -w /workdir -e AWS_DEFAULT_REGION -e AWS_PROFILE -e TF_VAR_datapull_s3_bucket=%bucket_name% -e TF_VAR_docker_image_name=%api_docker_image_name% -e TF_VAR_ui_docker_image_name=%ui_docker_image_name% hashicorp/terraform output -raw datapull_user_access_key`) do docker run --rm -v "%USERPROFILE%/.aws":"/root/.aws" amazon/aws-cli configure --profile datapull_user set aws_access_key_id %A
for /F "usebackq delims=" %A in (`docker run --rm -v "%CD%":/workdir -v "%USERPROFILE%/.aws":"/root/.aws" -w /workdir -e AWS_DEFAULT_REGION -e AWS_PROFILE -e TF_VAR_datapull_s3_bucket=%bucket_name% -e TF_VAR_docker_image_name=%api_docker_image_name% -e TF_VAR_ui_docker_image_name=%ui_docker_image_name% hashicorp/terraform output -raw datapull_user_secret_key`) do docker run --rm -v "%USERPROFILE%/.aws":"/root/.aws" amazon/aws-cli configure --profile datapull_user set aws_secret_access_key %A
docker run --rm -v "%USERPROFILE%/.aws":"/root/.aws" amazon/aws-cli configure --profile datapull_user set region %AWS_DEFAULT_REGION%

endlocal