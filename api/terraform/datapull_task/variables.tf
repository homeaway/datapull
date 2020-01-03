#Variables
variable "vpc_id" {}
variable "application_subnet_1" {}
variable "application_subnet_2" {}
variable container_port {}
variable "host_port" {}
variable "load_balancer_certificate_arn" {}
variable "security_grp" {}
variable "docker_image_name" {}
variable "container_memory" {default = 512}
variable "container_cpu" {default = 256}
variable "aws_account_number" {}
variable "application_region" {}

provider "aws" {}
variable env  { default="dev"}

