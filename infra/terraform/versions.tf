terraform {
  required_version = ">= 1.10.0"

  backend "s3" {}

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project       = var.project
      Environment   = var.ambiente
      Microsservico = var.nome
      ManagedBy     = "terraform"
    }
  }
}
