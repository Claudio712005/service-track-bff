variable "ambiente" {
  type = string

  validation {
    condition     = contains(["hml", "prd"], var.ambiente)
    error_message = "ambiente deve ser hml ou prd."
  }
}

variable "project" {
  type    = string
  default = "servicetrack"
}

variable "nome" {
  type    = string
  default = "bff"
}

variable "region" {
  type    = string
  default = "us-east-1"
}

variable "max_image_count" {
  type    = number
  default = 10
}

variable "untagged_expire_days" {
  type    = number
  default = 7
}
