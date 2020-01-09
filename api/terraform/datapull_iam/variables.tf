provider "aws" {
}

variable "datapull_s3_bucket" {

}

variable "docker_image_name" {

}

variable "ui_docker_image_name" {

}

terraform {
  backend "s3" {
      key    = "datapull-opensource/terraform-state/datapull_users_and_roles.tfstate"
  }
}