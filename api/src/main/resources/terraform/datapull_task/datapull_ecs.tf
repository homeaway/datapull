data "aws_caller_identity" "current" {}

resource "aws_ecs_task_definition" "datapull-web-api_backend_container" {
  family                = "${var.docker_image_name}_backend_container"
  container_definitions = <<EOF
[
    {
      "dnsSearchDomains": null,
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/${var.docker_image_name}",
          "awslogs-region": "${var.aws_repo_region}",
          "awslogs-stream-prefix": "ecs"
        }
      },
      "entryPoint": null,
      "portMappings": [
        {
          "hostPort": ${var.host_port},
          "protocol": "tcp",
          "containerPort": ${var.container_port}
        }
      ],
      "linuxParameters": null,
      "environment": [
        {
          "name": "env",
          "value": "${var.env}"
        },
        {
          "name": "AWS_CONTAINER_CREDENTIALS_RELATIVE_URI",
          "value": "true"
        },
        {
          "name": "PORT",
          "value": "${var.container_port}"
        },
        {
          "name": "spring.profiles.active",
          "value": "${var.env}"
        }
      ],
      "ulimits": null,
      "dnsServers": null,
      "networkMode": "awsvpc",
      "mountPoints": [],
      "workingDirectory": null,
      "dockerSecurityOptions": null,
      "volumesFrom": [],
      "image": "${var.aws_account_number}.dkr.ecr.${var.aws_repo_region}.amazonaws.com/${var.docker_image_name}:latest",
      "disableNetworking": null,
      "healthCheck": null,
      "essential": true,
      "links": null,
      "hostname": null,
      "extraHosts": null,
      "user": null,
      "requires_compatibilities":["FARGATE"],
      "readonlyRootFilesystem": null,
      "dockerLabels": null,
      "privileged": null,
      "name": "${var.docker_image_name}-dockercontainer"
    }
  ]
EOF

  network_mode = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  execution_role_arn = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/datapull_task_execution_role"
  task_role_arn = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/datapull_task_role"
  cpu = "${var.container_cpu}"
  memory = "${var.container_memory}"
}

resource "aws_ecs_cluster" "datapull-web-api" {
  name = "${var.docker_image_name}"
}

resource "aws_alb" "datapull-web-api-lb" {
  name            = "${var.docker_image_name}-alb"
  subnets = "${var.load_balancer_subnet}"
  security_groups = ["${var.security_grp}"]
  internal = true
  depends_on = ["aws_alb_target_group.datapull-web-api-targetgroup"]
}

resource "aws_alb_target_group" "datapull-web-api-targetgroup" {
  name        = "${var.docker_image_name}-tg"
  port        = "8080"
  protocol    = "HTTP"
  vpc_id = "${var.load_balancer_vpc}"
  target_type = "ip",
  health_check {
    healthy_threshold   = 3
    unhealthy_threshold = 10
    timeout             = 5
    interval            = 10
    path                = "/api/v1/healthCheck"
    port                = "8080"
  }
}

# Redirect all traffic from the ALB to the target group
resource "aws_alb_listener" "datapull-web-apilb-listener" {
  load_balancer_arn = "${aws_alb.datapull-web-api-lb.arn}"
  port              = "443"
  protocol          = "HTTPS"
  certificate_arn = "${var.load_balancer_certificate_arn}"
  default_action {
    target_group_arn = "${aws_alb_target_group.datapull-web-api-targetgroup.arn}"
    type             = "forward"
  }
}

resource "aws_ecs_service" "datapull-web-api_service" {
  name                               = "${var.docker_image_name}_ecs_service"
  cluster = "${aws_ecs_cluster.datapull-web-api.id}"
  task_definition = "${aws_ecs_task_definition.datapull-web-api_backend_container.arn}"
  desired_count                      = "1"
  deployment_minimum_healthy_percent = 50
  deployment_maximum_percent         = 200
  scheduling_strategy                = "REPLICA"
  launch_type                        = "FARGATE"

  network_configuration {
    security_groups = ["${var.security_grp}"]
    subnets         = ["${var.subnetid_private}"]
    assign_public_ip = "false"
  }

  load_balancer {
    target_group_arn = "${aws_alb_target_group.datapull-web-api-targetgroup.id}"
    container_name   = "${var.docker_image_name}-dockercontainer"
    container_port   = "${var.container_port}"
  }

  depends_on = [
    "aws_alb_listener.datapull-web-apilb-listener",
  ]
}

resource "aws_cloudwatch_log_group" "datapull_cloudwatch_log_group" {
  name = "/ecs/${var.docker_image_name}"
  retention_in_days = 1
  tags {
    env = "${var.env}"
    application = "${var.docker_image_name}"
  }
}

resource "aws_cloudwatch_log_stream" "datapull-stream" {
  name = "datapull-stream"
  log_group_name = "${aws_cloudwatch_log_group.datapull_cloudwatch_log_group.name}"
}

terraform {
  backend "s3" {
    key    = "datapull-opensource/terraform-state/ecs_deploy.tfstate"
  }
}
