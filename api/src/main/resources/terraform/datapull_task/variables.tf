
variable "host_port" {}
variable "container_port" {}
variable "container_memory" {default = 2048}
variable "container_cpu" {default = 1024}
variable "subnetid_private" {}
variable "security_grp" {}
variable "aws_account_number" {}
variable "aws_repo_region" {default = "us-east-1"}
variable "load_balancer_subnet" {type="list"}
variable "docker_image_name" {}
variable "load_balancer_vpc" {}
variable "load_balancer_certificate_arn" {}

provider "aws" {

}
variable env  { default="dev"}