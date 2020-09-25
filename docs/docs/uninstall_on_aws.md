# Uninstall DataPull deployed on AWS Fargate/ECS and AWS EMR

This document helps you uninstall DataPull on an Amazon AWS account

## Pre-requisites
- The fork/branch of the DataPull repo used for the installation should be available; since the script uses the same `master-application-conf-<env>.yml` data that was used for the install, for the uninstall too.
- The S3 bucket (which holds the `datapull-opensource/terraform-state` folder) should exist
- The IAM User `datapull_user` used for the purpose of installing DataPull, should exist
    - If this user has been deleted, you can follow the first part of DataPull installation to re-create this IAM user

## What does not get uninstalled by this process?
- AWS EMR default roles that are created by the CLI command `aws emr create-default-roles`
- DataPull's logs, sample data, terraform state, and history at `<s3_bucket_name>/datapull-opensource`
- Networking components created by the runbook [Setup VPC etc. in AWS Account for DataPull install](../aws_account_setup) 

## How to uninstall

### Uninstall non-IAM components

- From the terminal at the root of the repo, run 
    - `cd api/terraform/datapull_task/`
    - `chmod +x ecs_deploy_uninstall.sh`
    -  `./ecs_deploy_uninstall.sh <env>`

### Uninstall IAM components

- From the terminal (assuming you are using the same terminal session as the previous steps), run
    - `cd ../datapull_iam/`
        - If you are at the root of the repo, run `cd api/terraform/datapull_iam/`
    - `chmod +x uninstall_user_and_roles.sh`
    - `./uninstall_user_and_roles.sh <aws_admin_profile> <s3_bucket_name> <region>`
