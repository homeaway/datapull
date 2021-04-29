locals {
  common_tags = {
    brand = "Expedia"
    category = "EMRPipeline"
    tool = "datapull" #a bunch of policies depend on this required tag; therefore please change only if you're ready to update the policies too
  }
}

resource "aws_iam_role" "emr_ec2_datapull_custom_role" {
  name = "emr_ec2_datapull_custom_role"
  tags = local.common_tags

  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": "sts:AssumeRole",
      "Principal": {
        "Service": ["ec2.amazonaws.com"]
      },
      "Effect": "Allow",
      "Sid": ""
    }
  ]
}
EOF
}

resource "aws_iam_instance_profile" "datapull_custom_emr_ec2_instance_profile" {
  name = aws_iam_role.emr_ec2_datapull_custom_role.name
  role = aws_iam_role.emr_ec2_datapull_custom_role.name
}

# Required policy attachments for custom role

resource "aws_iam_role_policy_attachment" "emr_ec2_datapull_custom_role_ses_policy_attachment" {
  role = aws_iam_role.emr_ec2_datapull_custom_role.name
  policy_arn = "arn:aws:iam:::policy/datapull_ses_emr_ec2_policy"
}

resource "aws_iam_role_policy_attachment" "emr_ec2_datapull_custom_role_s3_policy_attachment" {
  role = aws_iam_role.emr_ec2_datapull_custom_role.name
  policy_arn = "arn:aws:iam:::policy/datapull_s3_emr_ec2_policy"
}

resource "aws_iam_role_policy_attachment" "emr_ec2_datapull_custom_role_cloudwatch_policy_attachment" {
  role = aws_iam_role.emr_ec2_datapull_custom_role.name
  policy_arn = "arn:aws:iam:::policy/datapull_cloudwatch_logs_api_emr_ec2_policy"
}

# Optional policy and attachment for DataPull to access an S3 bucket <BUCKET_NAME>

resource "aws_iam_policy" "datapull_custom_s3_policy" {
  name = "datapull_custom_s3_policy"

  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": [
        "s3:*"
      ],
      "Effect": "Allow",
      "Resource": [
           "arn:aws:s3:::<BUCKET_NAME>",
           "arn:aws:s3:::<BUCKET_NAME>/*"
      ]
    }
  ]
}
EOF
}

resource "aws_iam_role_policy_attachment" "datapull_custom_s3_policy_attachment" {
  role = aws_iam_role.emr_ec2_datapull_custom_role.name
  policy_arn = aws_iam_policy.datapull_custom_s3_policy.arn
}

# Optional policy and attachment for DataPull to access a secret <SECRET_ARN> in AWS Secrets Manager

resource "aws_iam_policy" "datapull_custom_secrets_manager_policy" {
  name = "datapull_custom_secrets_manager_policy"

  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": [
        "secretsmanager:DescribeSecret",
        "secretsmanager:Get*"
      ],
      "Effect": "Allow",
      "Resource": [
          "<SECRET_ARN>"
      ]
    }
  ]
}
EOF
}

resource "aws_iam_role_policy_attachment" "datapull_custom_secrets_manager_policy_attachment" {
  role = aws_iam_role.emr_ec2_datapull_custom_role.name
  policy_arn = aws_iam_policy.datapull_custom_secrets_manager_policy.arn
}