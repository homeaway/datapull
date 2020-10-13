# ----- SERVICE ACCOUNT QUESTIONS -----
# What functions is required for the IAM account? Jenkins will run as the IAM User, to spin up ECS Fargate containers. The ECS Fargate containers will assume the IAM Role, to spin up EMR clusters
# What permissions are allowed for the IAM account? IAM User: Access to install DataPull on AWS; IAM Roles: Access to run EMR, Access to S3 bucket ${var.datapull_s3_bucket}
# What AWS services is the IAM account touching? IAM User: ECS, IAM Role: EMR, S3, CloudWatch
# Is the IAM account apart of a role/group, is so, which role/group? No
# Does your account require shared access with a third party vendor? No
# Will your application be exposed to the internet? No

locals {
  common_tags = {
    brand = "Expedia"
    category = "EMRPipeline"
    tool = "datapull"
  }
}

#IAM User datapull_user used to install datapull. This user can be deleted once installation is done; or retained for use by the CI/CD pipeline to install DataPull

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
  tags = local.common_tags

}

#Policies for datapull_user...

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
                "s3:RestoreObject"
            ],
            "Resource": [
                "arn:aws:ecs:*:*:cluster/${var.docker_image_name}",
                "arn:aws:ecs:*:*:cluster/${var.ui_docker_image_name}",
                "arn:aws:iam::*:role/datapull_task_role",
                "arn:aws:iam::*:role/datapull_task_execution_role",
                "${aws_iam_role.emr_datapull_role.arn}",
                "arn:aws:ecr:*:*:repository/${var.docker_image_name}",
                "arn:aws:ecr:*:*:repository/${var.ui_docker_image_name}",
                "arn:aws:s3:::${var.datapull_s3_bucket}",
                "arn:aws:s3:::${var.datapull_s3_bucket}/*",
                "${aws_iam_role.emr_ec2_datapull_role.arn}",
                "arn:aws:ecr:*:*:repository/${var.docker_image_name}*",
                "arn:aws:ecs:*:*:cluster/${var.docker_image_name}*"
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
              "arn:aws:ecr:*:*:repository/${var.docker_image_name}*",
              "arn:aws:ecs:*:*:cluster/${var.docker_image_name}*"
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
                 "ecr:DescribeImages",
                 "ecs:StopTask"
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
            "Resource": "*",
            "Condition": {
                "StringLikeIfExists": {
                    "elasticmapreduce:ResourceTag/tool": "datapull"
                }
            }
        }
  ]
}
EOF
}

resource "aws_iam_policy" "datapull_cloudwatch_logs_policy" {
  name = "datapull_cloudwatch_logs_policy"

  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [ 
    {
      "Effect": "Allow",
      "Action": [
        "logs:DescribeLog*"
      ],
      "Resource": [
          "arn:aws:logs:*:*:log-group:/ecs/${var.docker_image_name}*:*",
          "arn:aws:logs:*:*:log-group:/ecs/${var.docker_image_name}*",
          "arn:aws:logs:*:*:log-group::log-stream*",
          "arn:aws:logs:*:*:log-group::log-stream*:*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "logs:PutRetentionPolicy",
        "logs:TagLogGroup",
        "logs:UntagLogGroup",
        "logs:DeleteLogGroup",
        "logs:ListTagsLogGroup",
        "logs:CreateLogStream",
        "logs:CreateLogGroup"
      ],
      "Resource": [
          "arn:aws:logs:*:*:log-group:/ecs/${var.docker_image_name}*:*",
          "arn:aws:logs:*:*:log-group:/ecs/${var.docker_image_name}*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "logs:PutLogEvents",
        "logs:GetLogEvents",
        "logs:DeleteLogStream"
      ],
      "Resource": [
        "arn:aws:logs:*:*:log-group:/ecs/${var.docker_image_name}:log-stream:*"
      ]
    }
  ]
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

resource "aws_iam_user_policy_attachment" "datapull_emr_policy" {
  user = aws_iam_user.datapull_user.name
  policy_arn = aws_iam_policy.datapull_emr_policy.arn
}

resource "aws_iam_user_policy_attachment" "datapull_user_infra_policy_split1" {
  user = aws_iam_user.datapull_user.name
  policy_arn = aws_iam_policy.datapull_user_infra_policy_split1.arn
}

resource "aws_iam_user_policy_attachment" "datapull_user_infra_policy_split2" {
  user = aws_iam_user.datapull_user.name
  policy_arn = aws_iam_policy.datapull_user_infra_policy_split2.arn
}

resource "aws_iam_user_policy_attachment" "datapull_loadbalancer_policy_attachment" {
  user = aws_iam_user.datapull_user.name
  policy_arn = aws_iam_policy.datapull_loadbalancer_logs_policy.arn
}

resource "aws_iam_user_policy_attachment" "datapull_cloudwatch_logs_policy_attachment" {
  user = aws_iam_user.datapull_user.name
  policy_arn = aws_iam_policy.datapull_cloudwatch_logs_policy.arn
}

resource "aws_iam_user_policy_attachment" "datapull_ServiceLinkedRole_policy_attachment" {
  user = aws_iam_user.datapull_user.name
  policy_arn = aws_iam_policy.datapull_ServiceLinkedRole_policy.arn
}

# IAM role for datapullapi ecs task
resource "aws_iam_role" "datapull_task_role" {
  name = "datapull_task_role"
  tags = local.common_tags

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

resource "aws_iam_instance_profile" "datapull_instance_profile" {
  name = "datapull_task_role"
  role = aws_iam_role.datapull_task_role.name
}

# policies for datapull_task_role

/*the S3 buckets in this policy will be overwritten by create_user_and_role.sh*/
resource "aws_iam_policy" "datapull_s3_api_policy" {
  name = "datapull_s3_api_policy"
  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": [
        "s3:ListBucket*"
      ],
      "Effect": "Allow",
      "Resource": [
           "arn:aws:s3:::${var.datapull_s3_bucket}"
      ],
      "Condition": {
          "StringLike": {
              "s3:prefix": [
                  "datapull-opensource/*"
              ]
          }
      }
    },
    {
      "Action": [
        "s3:GetObject*"
      ],
      "Effect": "Allow",
      "Resource": [
           "arn:aws:s3:::${var.datapull_s3_bucket}/datapull-opensource/*"
      ]
    },
    {
      "Action": [
        "s3:PutObject*",
        "s3:DeleteObject*"
      ],
      "Effect": "Allow",
      "Resource": [
           "arn:aws:s3:::${var.datapull_s3_bucket}/datapull-opensource/history/*"
      ]
    }
  ]
}
EOF
}

resource "aws_iam_policy" "datapull_cloudwatch_logs_api_emr_ec2_policy" {
  name = "datapull_cloudwatch_logs_api_emr_ec2_policy"

  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [ 
    
    {
      "Effect": "Allow",
      "Action": [
        "logs:ListTagsLogGroup",
        "logs:CreateLogStream",
        "logs:DescribeLog*"
      ],
      "Resource": [
          "arn:aws:logs:*:*:log-group:/ecs/${var.docker_image_name}*:*",
          "arn:aws:logs:*:*:log-group:/ecs/${var.docker_image_name}*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "logs:PutLogEvents",
        "logs:GetLogEvents"
      ],
      "Resource": [
        "arn:aws:logs:*:*:log-group:/ecs/${var.docker_image_name}:log-stream:*"
      ]
    }
  ]
}
EOF

}

# policy that replaces AmazonElasticMapReduceFullAccess, needed to spin up EMR 
resource "aws_iam_policy" "datapull_emr_api_policy" {
  name = "datapull_emr_api_policy"

  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [ 
    {
      "Effect": "Allow",
      "Action": [
        "elasticmapreduce:List*",
        "elasticmapreduce:GetBlockPublicAccessConfiguration",
        "elasticmapreduce:AddTags",
        "elasticmapreduce:RemoveTags"
      ],
      "Resource": [
          "*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
         "elasticmapreduce:Describe*",
         "elasticmapreduce:GetManagedScalingPolicy",
         "elasticmapreduce:AddInstance*",
         "elasticmapreduce:AddJobFlowSteps",
         "elasticmapreduce:CancelSteps",
         "elasticmapreduce:Modify*",
         "elasticmapreduce:Put*",
         "elasticmapreduce:Remove*",
         "elasticmapreduce:SetTerminationProtection",
         "elasticmapreduce:TerminateJobFlows"
      ],
      "Resource": [
          "*"
      ],
      "Condition": {
        "StringEquals": {
          "elasticmapreduce:ResourceTag/tool": "datapull"
        }
      }
    },
    {
      "Effect": "Allow",
      "Action": [
         "elasticmapreduce:RunJobFlow*"
      ],
      "Resource": [
          "*"
      ],
      "Condition": {
        "StringEquals": {
          "elasticmapreduce:RequestTag/tool": "datapull"
        }
      }
    }
  ]
}
EOF
}

resource "aws_iam_policy" "datapull_iam_api_policy" {
  name = "datapull_iam_api_policy"

  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [ 
    {
      "Effect": "Allow",
      "Action": [
        "iam:PassRole"
      ],
      "Resource": [
        "arn:aws:iam::*:role/emr_*datapull*",
        "arn:aws:iam::*:role/emr_*datatech*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": "iam:ListRoles",
      "Resource": [
          "*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "iam:GetPolicy",
        "iam:GetPolicyVersion"
      ],
      "Resource": [
          "arn:aws:iam:::policy/datapull_*"
      ]
    }  
  ]
}
EOF
}

resource "aws_iam_role_policy_attachment" "datapull_emr_api_policy_attachment" {
  role = aws_iam_role.datapull_task_role.name
  policy_arn = aws_iam_policy.datapull_emr_api_policy.arn
}

resource "aws_iam_role_policy_attachment" "datapull_iam_api_policy_attachment" {
  role = aws_iam_role.datapull_task_role.name
  policy_arn = aws_iam_policy.datapull_iam_api_policy.arn
}

resource "aws_iam_role_policy_attachment" "datapull_logs_api_policy_attachment" {
  role = aws_iam_role.datapull_task_role.name
  policy_arn = aws_iam_policy.datapull_cloudwatch_logs_api_emr_ec2_policy.arn
}

resource "aws_iam_role_policy_attachment" "datapull_s3_api_policy_attachment" {
  role = aws_iam_role.datapull_task_role.name
  policy_arn = aws_iam_policy.datapull_s3_api_policy.arn
}

# IAM role for datapullapi ecs task executor
resource "aws_iam_role" "datapull_task_execution_role" {
  name = "datapull_task_execution_role"
  tags = local.common_tags

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

# policies for datapull_task_execution_role...

resource "aws_iam_role_policy_attachment" "datapull_task_execution_policy" {
  role = aws_iam_role.datapull_task_execution_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# IAM Role for EMR Service
resource "aws_iam_role" "emr_datapull_role" {
  name = "emr_datapull_role"
  tags = local.common_tags

  assume_role_policy = <<EOF
{
  "Version": "2008-10-17",
  "Statement": [
    {
      "Sid": "",
      "Effect": "Allow",
      "Principal": {
        "Service": [
          "spotfleet.amazonaws.com",
          "application-autoscaling.amazonaws.com",
          "spot.amazonaws.com",
          "elasticmapreduce.amazonaws.com"
        ]
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
EOF

}

# policies for emr_datapull_role...

resource "aws_iam_policy" "datapull_emr_service_policy" {
  name = "datapull_emr_service_policy"

  policy = <<EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Resource": "*",
            "Action": [
                "cloudwatch:PutMetricAlarm",
                "cloudwatch:DescribeAlarms",
                "cloudwatch:DeleteAlarms",
                "application-autoscaling:RegisterScalableTarget",
                "application-autoscaling:DeregisterScalableTarget",
                "application-autoscaling:PutScalingPolicy",
                "application-autoscaling:DeleteScalingPolicy",
                "application-autoscaling:Describe*"
            ]
        },
        {
            "Effect": "Allow",
            "Resource": "*",
            "Condition": {
                "StringLike": {
                    "ec2:ResourceTag/tool": "datapull"
                }
            },
            "Action": [
                "ec2:TerminateInstances",
                "ec2:DeleteTags",
                "ec2:DetachVolume",
                "ec2:DeleteVolume"                
            ]
        },
        {
            "Effect": "Allow",
            "Resource": "*",
            "Condition": {
                "StringLikeIfExists": {
                    "ec2:ResourceTag/tool": "datapull"
                }
            },
            "Action": [
                "ec2:AuthorizeSecurityGroupEgress",
                "ec2:AuthorizeSecurityGroupIngress",
                "ec2:CancelSpotInstanceRequests",
                "ec2:CreateFleet",
                "ec2:CreateLaunchTemplate",
                "ec2:CreateNetworkInterface",
                "ec2:CreateSecurityGroup",
                "ec2:CreateTags",
                "ec2:DeleteLaunchTemplate",
                "ec2:DeleteNetworkInterface",
                "ec2:DeleteSecurityGroup",
                "ec2:DescribeAccountAttributes",
                "ec2:DescribeAvailabilityZones",
                "ec2:DescribeDhcpOptions",
                "ec2:DescribeImages",
                "ec2:DescribeInstances",
                "ec2:DescribeInstanceStatus",
                "ec2:DescribeKeyPairs",
                "ec2:DescribeLaunchTemplates",
                "ec2:DescribeNetworkAcls",
                "ec2:DescribeNetworkInterfaces",
                "ec2:DescribePrefixLists",
                "ec2:DescribeRouteTables",
                "ec2:DescribeSecurityGroups",
                "ec2:DescribeSpotInstanceRequests",
                "ec2:DescribeSpotPriceHistory",
                "ec2:DescribeSubnets",
                "ec2:DescribeTags",
                "ec2:DescribeVolumes",
                "ec2:DescribeVolumeStatus",
                "ec2:DescribeVpcAttribute",
                "ec2:DescribeVpcEndpoints",
                "ec2:DescribeVpcEndpointServices",
                "ec2:DescribeVpcs",
                "ec2:DetachNetworkInterface",
                "ec2:ModifyImageAttribute",
                "ec2:ModifyInstanceAttribute",
                "ec2:RequestSpotInstances",
                "ec2:RevokeSecurityGroupEgress",
                "ec2:RunInstances"
            ]
        },
        {
            "Sid": "",
            "Effect": "Allow",
            "Action": [
                "iam:GetRole",
                "iam:GetRolePolicy",
                "iam:ListRolePolicies",
                "iam:PassRole"
            ],
            "Resource": [
                "arn:aws:iam::*:role/emr_*datapull*",
                "arn:aws:iam::*:role/emr_*datatech*"
            ]
        },
        {
            "Sid": "",
            "Effect": "Allow",
            "Action": [
                "iam:ListInstanceProfiles"
            ],
            "Resource": [
                "arn:aws:iam::*:instance-profile/emr_*datapull*",
                "arn:aws:iam::*:instance-profile/emr_*datatech*"
            ]
        }
    ]
}
EOF

}

resource "aws_iam_role_policy_attachment" "datapull_emr_service_policy" {
  role = aws_iam_role.emr_datapull_role.name
  policy_arn = aws_iam_policy.datapull_emr_service_policy.arn
}

# IAM Role for default EC2 instance profiles of EMR
resource "aws_iam_role" "emr_ec2_datapull_role" {
  name = "emr_ec2_datapull_role"
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

# policies for emr_ec2_datapull_role ...

/*the S3 buckets in this policy will be overwritten by create_user_and_role.sh*/
resource "aws_iam_policy" "datapull_s3_emr_ec2_policy" {
  name = "datapull_s3_emr_ec2_policy"
  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": [
        "s3:ListBucket*"
      ],
      "Effect": "Allow",
      "Resource": [
           "arn:aws:s3:::${var.datapull_s3_bucket}"
      ],
      "Condition": {
          "StringLike": {
              "s3:prefix": [
                  "datapull-opensource/*"
              ]
          }
      }
    },
    {
      "Action": [
        "s3:GetObject*"
      ],
      "Effect": "Allow",
      "Resource": [
           "arn:aws:s3:::${var.datapull_s3_bucket}/datapull-opensource/*"
      ]
    },
    {
      "Action": [
        "s3:PutObject*",
        "s3:DeleteObject*"
      ],
      "Effect": "Allow",
      "Resource": [
           "arn:aws:s3:::${var.datapull_s3_bucket}/datapull-opensource/data/*",
           "arn:aws:s3:::${var.datapull_s3_bucket}/datapull-opensource/logs/*"
      ]
    }
  ]
}
EOF
}

resource "aws_iam_role_policy_attachment" "datapull_s3_emr_ec2_attachment" {
  role = aws_iam_role.emr_ec2_datapull_role.name
  policy_arn = aws_iam_policy.datapull_s3_emr_ec2_policy.arn
}

resource "aws_iam_role_policy_attachment" "datapull_emr_ec2_cloudwatch_attachment" {
  role = aws_iam_role.emr_ec2_datapull_role.name
  policy_arn = aws_iam_policy.datapull_cloudwatch_logs_api_emr_ec2_policy.arn
}

resource "aws_iam_instance_profile" "datapull_default_instance_profile" {
  name = "emr_ec2_datapull_role"
  role = aws_iam_role.emr_ec2_datapull_role.name
}

resource "aws_iam_access_key" "datapull_iam_access_key" {
  user = aws_iam_user.datapull_user.name
}

output "datapull_user_access_key" {
  value = aws_iam_access_key.datapull_iam_access_key.id
  sensitive = true
}

output "datapull_user_secret_key" {
  value = aws_iam_access_key.datapull_iam_access_key.secret
  sensitive = true
}