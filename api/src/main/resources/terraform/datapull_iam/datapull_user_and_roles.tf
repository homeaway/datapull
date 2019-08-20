# ----- SERVICE ACCOUNT QUESTIONS -----
# What functions is required for the IAM account? Jenkins will run as the IAM User, to spin up ECS Fargate containers. The ECS Fargate containers will assume the IAM Role, to spin up EMR clusters
# What permissions are allowed for the IAM account? IAM User: AmazonECS_FullAccess; IAM Role: AmazonElasticMapReduceFullAccess, Access to S3 bucket ${var.datapull_s3_bucket}
# What AWS services is the IAM account touching? IAM User: ECS, IAM Role: EMR, S3
# Is the IAM account apart of a role/group, is so, which role/group? No
# Does your account require shared access with a third party vendor? No
# Will your application be exposed to the internet? No

#IAM User datapull_user

locals {
  common_tags = {
    brand = "HomeAway"
    category = "EMRPipeline"
    tool = "datapull"
  }
}

resource "aws_iam_user" "datapull_user" {
  name = "datapull_user"

  # Formatting note about paths
  # - Path format = "/PORTFOLIO/PRODUCT/SERVICE/"
  # - Should be lower case
  # - Must have special characters and whitespace removed
  # - At the moment the Mindle Product list includes the Portfolio already, you don't need to include this in the path
  # Example:
  # Portfolio - technical operations
  # Product - technical operations - iam
  # Service - sso
  # Resulting Path - /technicaloperations/iam/sso/
  path = "/dataengineeringservices/datatools/datapull/"
  tags = "${local.common_tags}"

}

# Attach policy to enable creation of ECS fargate containers
/*the S3 buckets in this policy will be overwritten by create_user_and_role.sh*/
resource "aws_iam_policy" "datapull_user_infra_policy_split1" {
  name = "datapull_user_infra_policy_split1"

  policy = <<EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "VisualEditor0",
            "Effect": "Allow",
            "Action": [
                "ecs:UpdateContainerInstancesState",
                "ecs:StartTask",
                "ecs:RegisterContainerInstance",
                "ecr:UploadLayerPart",
                "ecr:BatchDeleteImage",
                "ecr:DeleteRepository",
                "iam:PassRole",
                "ecs:RunTask",
                "ecr:CompleteLayerUpload",
                "ecs:SubmitContainerStateChange",
                "ecs:StopTask",
                "ecs:DescribeContainerInstances",
                "ecs:DeregisterContainerInstance",
                "ecs:DescribeTasks",
                "ecr:DeleteRepositoryPolicy",
                "ecr:BatchCheckLayerAvailability",
                "ecs:SubmitTaskStateChange",
                "ecr:GetDownloadUrlForLayer",
                "ecs:DeleteCluster",
                "ecs:DescribeClusters",
                "ecr:PutImage",
                "ecr:SetRepositoryPolicy",
                "ecs:UpdateContainerAgent",
                "ecr:BatchGetImage",
                "ecr:DescribeImages",
                "ecr:InitiateLayerUpload",
                "ecr:GetRepositoryPolicy",
                "s3:AbortMultipartUpload",
                "s3:DeleteObject",
                "s3:DeleteObjectTagging",
                "s3:DeleteObjectVersion",
                "s3:DeleteObjectVersionTagging",
                "s3:GetObject",
                "s3:GetObjectAcl",
                "s3:GetObjectTagging",
                "s3:GetObjectTorrent",
                "s3:GetObjectVersion",
                "s3:GetObjectVersionAcl",
                "s3:GetObjectVersionTorrent",
                "s3:ListMultipartUploadParts",
                "s3:PutObject",
                "s3:ListBucket",
                "s3:PutObjectAcl",
                "s3:PutObjectVersionAcl",
                "s3:PutObjectVersionTagging",
                "s3:RestoreObject",
                "iam:PassRole"
            ],
            "Resource": [
                "arn:aws:ecs:*:*:cluster/${var.docker_image_name}",
                "arn:aws:ecs:*:*:cluster/${var.ui_docker_image_name}",
                "arn:aws:iam::*:role/datapull_task_role",
                "arn:aws:iam::*:role/datapull_task_execution_role",
                "arn:aws:iam::*:role/EMR_DefaultRole",
                "arn:aws:ecr:*:*:repository/${var.docker_image_name}",
                "arn:aws:ecr:*:*:repository/${var.ui_docker_image_name}",
                "arn:aws:s3:::${var.datapull_s3_bucket}",
                "arn:aws:s3:::${var.datapull_s3_bucket}/*",
                "arn:aws:iam::*:role/emr_ec2_datapull_role",
                "arn:aws:ecr:*:*:repository/*datapull*",
                "arn:aws:ecs:*:*:cluster/*datapull*"
            ]
        }
    ]
}
EOF

}

resource "aws_iam_policy" "datapull_user_infra_policy_split2" {
  name = "datapull_user_infra_policy_split2"

  policy = <<EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "VisualEditor1",
            "Effect": "Allow",
            "Action": [
                "ecs:DiscoverPollEndpoint",
                "ecs:DeleteService",
                "ecs:DescribeTaskDefinition",
                "ecs:DeregisterTaskDefinition",
                "ecs:Poll",
                "ecs:UpdateService",
                "ecs:StartTelemetrySession",
                "ecs:DescribeServices"
            ],
            "Resource": [
              "arn:aws:ecr:*:*:repository/*datapull*",
              "arn:aws:ecs:*:*:cluster/*datapull*"
            ]
        },
        {
            "Sid": "VisualEditor2",
            "Effect": "Allow",
            "Action": [
                "ecr:CreateRepository",
                "ecr:GetAuthorizationToken",
                "ecs:StartTelemetrySession",
                "ecs:CreateCluster",
                "ecs:CreateService",
                "ecs:RegisterTaskDefinition",
                "elasticloadbalancing:Describe*",
                "elasticloadbalancing:CreateLoadBalancer*",
                "elasticloadbalancing:CreateListener",
                "ecs:Describe*",
                "ecs:DeregisterTaskDefinition",
                "ecs:UpdateService",
                "ecs:DeleteService",
                 "ecr:BatchDeletImage",
                 "ecr:ListImages",
                 "ecr:DescribeImages"
            ],
            "Resource": "*"
        }
    ]
}
EOF

}

resource "aws_iam_policy" "datapull_emr_policy" {
  name = "datapull_emr_policy"

  policy = <<EOF
{
    "Version": "2012-10-17",
    "Statement": [

        {
            "Sid": "VisualEditor5",
            "Effect": "Allow",
            "Action": [
                "elasticmapreduce:Describe*",
                "elasticmapreduce:List*",
                "elasticmapreduce:ViewEventsFromAllClustersInConsole",
                "elasticmapreduce:SetTerminationProtection",
                "elasticmapreduce:TerminateJobFlows",
                "elasticmapreduce:RunJobFlow",
                "elasticmapreduce:AddTags",
                "elasticmapreduce:AddJobFlowSteps"
            ],
            "Resource": "*"
        }
  ]
}
EOF
}

resource "aws_iam_policy" "datapull_passrole_policy" {
  name = "datapull_passrole_policy"

  policy = <<EOF
{
    "Version": "2012-10-17",
    "Statement": [ {
        "Effect": "Allow",
        "Action": "iam:PassRole",
        "Resource": [
            "arn:aws:iam::*:role/EMR_DefaultRole",
            "arn:aws:iam::*:role/user_iam_role"
        ]
    } ]
}
EOF
}

resource "aws_iam_user_policy_attachment" "datapull_passrole_policy" {
  user = "${aws_iam_user.datapull_user.name}"
  policy_arn = "${aws_iam_policy.datapull_passrole_policy.arn}"
}

resource "aws_iam_user_policy_attachment" "datapull_emr_policy" {
  user = "${aws_iam_user.datapull_user.name}"
  policy_arn = "${aws_iam_policy.datapull_emr_policy.arn}"
}

resource "aws_iam_user_policy_attachment" "datapull_user_infra_policy_split1" {
  user = "${aws_iam_user.datapull_user.name}"
  policy_arn = "${aws_iam_policy.datapull_user_infra_policy_split1.arn}"
}

resource "aws_iam_user_policy_attachment" "datapull_user_infra_policy_split2" {
  user = "${aws_iam_user.datapull_user.name}"
  policy_arn = "${aws_iam_policy.datapull_user_infra_policy_split2.arn}"
}

resource "aws_iam_policy" "datapull_cloudwatch_logs_policy" {
  name = "datapull_cloudwatch_logs_policy"

  policy = <<EOF
{
    "Version": "2012-10-17",
    "Statement": [ {
          "Effect": "Allow",
          "Action": [
            "logs:PutRetentionPolicy",
            "logs:ListTagsLogGroup",
            "logs:TagLogGroup",
            "logs:PutLogEvents",
            "logs:GetLogEvents"
          ],
          "Resource": [
             "arn:aws:logs:*:*:log-group:/ecs/datapull*:*",
             "arn:aws:logs:*:*:log-group:/ecs/datapull*:*",
              "arn:aws:logs:*:*:log-group:/ecs/datapull*",
             "arn:aws:logs:*:*:log-group:/ecs/datapull*"
          ]
        },
        {
          "Effect": "Allow",
          "Action": [
            "logs:CreateLogGroup",
            "logs:DescribeLog*",
            "ecs:DescribeTask*",
            "logs:CreateLogStream"
          ],
          "Resource": "*"
        } ]
}
EOF

}

resource "aws_iam_policy" "datapull_loadbalancer_logs_policy" {
  name = "datapull_loadbalancer_logs_policy"

  policy = <<EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "elasticloadbalancing:*"
            ],
            "Resource": [
                "arn:aws:elasticloadbalancing:*:*:loadbalancer/app/${var.docker_image_name}-alb/*",
                "arn:aws:elasticloadbalancing:*:*:listener/app/${var.docker_image_name}-alb/*/*",
                "arn:aws:elasticloadbalancing:*:*:listener-rule/app/${var.docker_image_name}-alb/*/*/*",
                "arn:aws:elasticloadbalancing:*:*:targetgroup/${var.docker_image_name}-tg/*",
                "arn:aws:elasticloadbalancing:*:*:loadbalancer/app/${var.ui_docker_image_name}-alb/*",
                "arn:aws:elasticloadbalancing:*:*:listener/app/${var.ui_docker_image_name}-alb/*/*",
                "arn:aws:elasticloadbalancing:*:*:listener-rule/app/${var.ui_docker_image_name}-alb/*/*/*",
                "arn:aws:elasticloadbalancing:*:*:targetgroup/${var.ui_docker_image_name}-tg/*"
            ]
        }
     ]
}
EOF

}

resource "aws_iam_user_policy_attachment" "datapull_loadbalancer_policy_attachment" {
  user = "${aws_iam_user.datapull_user.name}"
  policy_arn = "${aws_iam_policy.datapull_loadbalancer_logs_policy.arn}"
}

resource "aws_iam_user_policy_attachment" "datapull_cloudwatch_logs_policy_attachment" {
  user       = "${aws_iam_user.datapull_user.name}"
  policy_arn = "${aws_iam_policy.datapull_cloudwatch_logs_policy.arn}"
}

resource "aws_iam_policy" "datapull_ServiceLinkedRole_policy" {
  name = "datapull_ServiceLinkedRole_policy"

  policy = <<EOF
{
    "Version": "2012-10-17",
    "Statement": [ {
    "Effect": "Allow",
    "Action": [
        "iam:CreateServiceLinkedRole",
        "iam:PutRolePolicy"
    ],
    "Resource": "arn:aws:iam::*:role/aws-service-role/ecs.amazonaws.com/AWSServiceRoleForECS*",
    "Condition": {"StringLike": {"iam:AWSServiceName": "ecs.amazonaws.com"}}
} ]
}
EOF

}

resource "aws_iam_user_policy_attachment" "datapull_ServiceLinkedRole_policy_attachment" {
  user       = "${aws_iam_user.datapull_user.name}"
  policy_arn = "${aws_iam_policy.datapull_ServiceLinkedRole_policy.arn}"
}

# IAM role for datapullapi ecs task
resource "aws_iam_role" "datapull_task_role" {
  name = "datapull_task_role"
  tags = "${local.common_tags}"

  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": "sts:AssumeRole",
      "Principal": {
        "Service": "ecs-tasks.amazonaws.com"
      },
      "Effect": "Allow",
      "Sid": ""
    }
  ]
}
EOF

}

resource "aws_iam_role_policy_attachment" "taskrole_emr_policy" {
  role = "${aws_iam_role.datapull_task_role.name}"
  policy_arn = "arn:aws:iam::aws:policy/AmazonElasticMapReduceFullAccess"

}

resource "aws_iam_role_policy_attachment" "emr_ec2_role_emr_policy" {
  role = "${aws_iam_role.emr_ec2_datapull_role.name}"
  policy_arn = "arn:aws:iam::aws:policy/AmazonElasticMapReduceFullAccess"

}

resource "aws_iam_role_policy_attachment" "datapull_logs_policy" {
  role = "${aws_iam_role.datapull_task_role.name}"
  policy_arn = "${aws_iam_policy.datapull_cloudwatch_logs_policy.arn}"

}

/*the S3 buckets in this policy will be overwritten by create_user_and_role.sh*/
resource "aws_iam_policy" "datapull_s3_policy" {
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
           "arn:aws:s3:::${var.datapull_s3_bucket}",
           "arn:aws:s3:::${var.datapull_s3_bucket}/*"
      ]
    }
  ]
}
EOF

}

resource "aws_iam_role_policy_attachment" "datapull_s3_policy_attachment" {
  role = "${aws_iam_role.datapull_task_role.name}"
  policy_arn = "${aws_iam_policy.datapull_s3_policy.arn}"

}

resource "aws_iam_instance_profile" "datapull_instance_profile" {
  name = "datapull_task_role"
  role = "${aws_iam_role.datapull_task_role.name}"

}

# IAM role for datapullapi ecs task
resource "aws_iam_role" "datapull_task_execution_role" {
  name = "datapull_task_execution_role"
  tags = "${local.common_tags}"

  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": "sts:AssumeRole",
      "Principal": {
        "Service": "ecs-tasks.amazonaws.com"
      },
      "Effect": "Allow",
      "Sid": ""
    }
  ]
}
EOF

}

resource "aws_iam_role" "emr_ec2_datapull_role" {
  name = "emr_ec2_datapull_role"
  tags = "${local.common_tags}"

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


resource "aws_iam_role_policy_attachment" "datapull_s3_attachment" {
  role = "${aws_iam_role.emr_ec2_datapull_role.name}"
  policy_arn = "${aws_iam_policy.datapull_s3_policy.arn}"

}

resource "aws_iam_role_policy_attachment" "datapull_emr_ec2_attachment" {
  role = "${aws_iam_role.emr_ec2_datapull_role.name}"
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonElasticMapReduceforEC2Role"
}

resource "aws_iam_role_policy_attachment" "datapull_emr_cloudwatch_attachment" {
  role = "${aws_iam_role.emr_ec2_datapull_role.name}"
  policy_arn = "${aws_iam_policy.datapull_cloudwatch_logs_policy.arn}"
}

resource "aws_iam_role_policy_attachment" "datapull_task_execution_policy" {
  role = "${aws_iam_role.datapull_task_execution_role.name}"
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_instance_profile" "datapull_default_instance_profile" {
  name = "emr_ec2_datapull_role"
  role = "${aws_iam_role.emr_ec2_datapull_role.name}"

}

resource "aws_iam_access_key" "datapull_iam_access_key" {
 user    = "${aws_iam_user.datapull_user.name}"
}

output "datapull_user_access_key" {
 value = "${aws_iam_access_key.datapull_iam_access_key.id}"
}

output "datapull_user_secret_key" {
 value = "${aws_iam_access_key.datapull_iam_access_key.secret}"
}