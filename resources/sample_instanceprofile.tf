locals {
  common_tags = {
    brand = "Expedia"
    category = "EMRPipeline"
    tool = "datapull"
  }
}

resource "aws_iam_role" "custom_datapull_role" {
  name = "custom_datapull_role"
  tags = local.common_tags

  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": "sts:AssumeRole",
      "Principal": {
        "Service": ["elasticmapreduce.amazonaws.com","ec2.amazonaws.com"]
      },
      "Effect": "Allow",
      "Sid": ""
    }
  ]
}
EOF
}


resource "aws_iam_role_policy_attachment" "custom_role_emr_policy" {
  role = aws_iam_role.custom_datapull_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonElasticMapReduceFullAccess"
}

resource "aws_iam_policy" "custom_s3_policy" {
  name = "datapull_s3_policy"

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

resource "aws_iam_policy" "datapull_secrets_manager_policy" {
  name = "datapull_secrets_manager_policy"

  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": [
        "secretsmanager:DescribeSecret",
                "secretsmanager:PutSecretValue",
                "secretsmanager:CreateSecret",
                "secretsmanager:Get*",
                "secretsmanager:TagResource",
                "secretsmanager:UpdateSecret"
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

resource "aws_iam_role_policy_attachment" "datapull_secrets_manager_attachment" {
  role = aws_iam_role.custom_datapull_role.name
  policy_arn = aws_iam_policy.datapull_secrets_manager_policy.arn
}

resource "aws_iam_role_policy_attachment" "custom_s3_policy_attachment" {
  role = aws_iam_role.custom_datapull_role.name
  policy_arn = aws_iam_policy.custom_s3_policy.arn

}

resource "aws_iam_role_policy_attachment" "datapull_s3_attachment" {
  role = aws_iam_role.custom_datapull_role.name
  policy_arn = aws_iam_policy.datapull_s3_policy.arn
}

resource "aws_iam_role_policy_attachment" "datapull_emr_ec2_attachment" {
  role = aws_iam_role.custom_datapull_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonElasticMapReduceforEC2Role"
}

resource "aws_iam_role_policy_attachment" "datapull_emr_cloudwatch_attachment" {
  role = aws_iam_role.custom_datapull_role.name
  policy_arn = aws_iam_policy.datapull_cloudwatch_logs_policy.arn
}

resource "aws_iam_instance_profile" "datapull_default_instance_profile" {
  name = "custom_datapull_role"
  role = aws_iam_role.custom_datapull_role.name

}
