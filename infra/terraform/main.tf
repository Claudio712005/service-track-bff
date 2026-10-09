locals {
  nome_completo = "${var.project}-${var.ambiente}-${var.nome}"
}

resource "aws_ecr_repository" "this" {
  name                 = local.nome_completo
  image_tag_mutability = "IMMUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "this" {
  repository = aws_ecr_repository.this.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Expira imagens sem tag apos ${var.untagged_expire_days} dias"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = var.untagged_expire_days
        }
        action = { type = "expire" }
      },
      {
        rulePriority = 2
        description  = "Mantem as ultimas ${var.max_image_count} imagens"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = var.max_image_count
        }
        action = { type = "expire" }
      },
    ]
  })
}

resource "aws_ssm_parameter" "ecr_url" {
  name  = "/${var.project}/${var.ambiente}/${var.nome}/ecr-url"
  type  = "String"
  value = aws_ecr_repository.this.repository_url
}
