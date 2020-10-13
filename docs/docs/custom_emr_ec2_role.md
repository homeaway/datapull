# Custom EC2 Instance Profile/Role for EMR Clusters

By default, DataPull on EMR uses the IAM role `emr_ec2_datapull_role` as the [service role for cluster EC2 instances](https://docs.aws.amazon.com/emr/latest/ManagementGuide/emr-iam-role-for-ec2.html) (also called the EC2 instance profile for Amazon EMR).  This  IAM role `emr_ec2_datapull_role` has sufficient access to run DataPull jobs that involve non-AWS data stores. For instance, you can move data from a Cassandra cluster to a MongoDB cluster without having to read this document. However, if you want to have DataPull read/write data from an AWS data store like S3, the recommendation is that you create a custom EC2 Instance Profile/Role and grant that role access to the AWS datastore/resource that DataPull needs to access.  

Here is a
[sample terraform file](resources/sample_custom_emr_ec2_role.tf) for the creation of a custom IAM role `emr_ec2_datapull_custom_role` that can be used by DataPull on EMR as a custom EC2 Instance Profile/Role. The IAM Role thus used, can be specified for each DataPull job within its input JSON. The [sample terraform file](resources/sample_custom_emr_ec2_role.tf) has some required policy attachments; and also some optional policies that allow DataPull to interact with AWS Secrets Manager, your own S3 bucket, etc. 

> The IAM Role for the DataPull API `datapull_task_role` needs to have IAM:PassRole access over this custom EC2 instance profile Role `emr_ec2_datapull_custom_role`. By default, the role `datapull_task_role` has IAM:PassRole access over all IAM Roles matching the pattern `arn:aws:iam::*:role/emr_*datapull*`; therefore it is recommended that your custom IAM Role matches this pattern.

When creating a custom EC2 Role for DataPull, it is recommended that you remove the optional policies and their related policy attachments, if you do not need them. 

## Runbook to create custom EC2 Role for DataPull on EMR

> This runbook assumes that you have already installed DataPull, and you have access to the folder of the forked/mirrored repo used for the installation. If not, you will re-do the [Pre-install Steps](/install_on_aws/#pre-install-steps) with the same values for the variables, as was used in the Install.

1. In the folder that has the locally cloned forked/mirrored repo, copy the [sample terraform file](resources/sample_custom_emr_ec2_role.tf) that will create the custtom IAM role, from `docs/docs/resources/sample_custom_emr_ec2_role.tf` to `api/terraform/datapull_iam/`
1. Rename the copied file to the name of the custom IAM role, say `emr_ec2_datapull_custom_role.tf`
1. In the copied file, replace `<PLACEHOLDERS>` in the optional policies that you wish to use, with the values appropriate for your environment; remove the optional policies and attachments that you do not wish to use. 
1. If the ARN of the custom IAM Role does not match the pattern `arn:aws:iam::*:role/emr_*datapull*`, add the ARN to the array of resources for the action `iam:PassRole` of policies `datapull_iam_api_policy` and `datapull_emr_service_policy` in the file `api/terraform/datapull_iam/datapull_user_and_roles.tf`
1. Re-run the installation step [Create IAM User and Roles, with policies](../install_on_aws/#create-iam-user-and-roles-with-policies) so that the copied terraform file creates the custom IAM Role and updates the exsting Terraform state. 