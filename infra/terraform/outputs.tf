output "ecr_repository_url" {
  value = aws_ecr_repository.this.repository_url
}

output "ecr_repository_name" {
  value = aws_ecr_repository.this.name
}

output "ssm_parameter_ecr_url" {
  value = aws_ssm_parameter.ecr_url.name
}
