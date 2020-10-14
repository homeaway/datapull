**Custom Role Creation for EMR Clusters**

This is to help the customers who wants to use the custom role than the default `emr_ec2_datapull_role` role. We have created a ![sample terraform file](resources/sample_instanceprofile.tf) for creation of the role.

We have two policies, one for granting required permissions for accessing the s3 bucket and the other is for retrieving secrets from AWS secrets manager.
We can remove any of the policies if not needed along with the policy attachment statements.  

We should add the  name of the policy to `datapull_passrole_policy` under the `Resource` section of the policy. such that the api will have access to spin up the EC2 instances with the custom role. 

***Policy for accessing data from any s3 bucket***

If the datapull job needs access from any of the s3 buckets which is different from the bucket used for datapull installation, then the role should have necessary permissions to access the s3 bucket.
We should replace the `<BUCKET_NAME>` with the bucket name.

***Policy for retrieving secrets from secrets manager*** 

If the secret has to be retrieved from Secrets Manger then the instance profile on which EMR's EC2 instance is running should have necessary permissions attached to it. 
So we have included a policy `datapull_secrets_manager_attachment` in the sample terraform  which has all required permissions to access the secrets in the AWS secrets manager and attached the policy to the role.
Replace the `<SECRET_ARN>` with the full ARN secret ARN.

****NOTE: Remove the policy and the policy attachment from the terraform file if you don't want to use the secrets manager as the secrets store****

 After making the necessary changes, please follow the below steps:
 - Clone/download the master branch of the datapull repo if it doesn't exists.
 - make sure the file is under this path `/api/terraform/datapull_iam` 
 - Have available, the [AWS Profile](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-profiles.html) `<aws_admin_profile>` of an IAM user/role that can create IAM users and IAM roles in your AWS account
     -   It is advisable this IAM user/role have admin access to the AWS account. Typically these credentials will be available only to the team managing the AWS account; hence the team deploying DataPull will need to coordinate with the team managing the AWS account. 
****NOTE: If you have installed datapull using your machine, then make sure the admin profile credentials exists and is not expired****
- Then please follow the below steps:

> We recommend team managing the AWS account run this script from a terminal that has the AWS administrator credentials available as a profile `<aws_admin_profile>` in the [Credential Profile Chain](https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html) i.e. you should have the AWS credentials set in
<ul>
    <li>either your `~/.aws/credentials` file if you're on a Mac
    <li>or in your environment variables, 
    <li>or you are running this script from an AWS instance with an IAM Role that has administrator privileges 
</ul>

Make sure you have the below required information before running the below commands:
 <aws_admin_profile>  - The name of the admin aws profile which has saved in your credentials file
 <s3_bucket_name>  - this is the bucket used during datapull installation
 <region> - the region in which the datapull has been installed

- From the terminal at the root of the repo, run 
    - `cd api/terraform/datapull_iam/`
    - `chmod +x create_user_and_roles.sh`
    - `./create_user_and_roles.sh <aws_admin_profile> <s3_bucket_name> <region>`