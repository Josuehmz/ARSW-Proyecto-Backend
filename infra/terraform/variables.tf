variable "aws_region" {
  description = "AWS region to deploy into"
  type        = string
  default     = "us-east-1"
}

variable "vpc_id" {
  description = "VPC id where ALB and instances exist"
  type        = string
}

variable "public_subnet_ids" {
  description = "List of public subnet ids (2 or more) for the ALB"
  type        = list(string)
}

variable "instance_ids" {
  description = "List of backend EC2 instance ids to register to the target group"
  type        = list(string)
}

variable "name_prefix" {
  description = "Name prefix used for resources"
  type        = string
  default     = "balatro"
}

variable "backend_port" {
  description = "Port on which backend listens (internal)"
  type        = number
  default     = 8080
}

variable "health_check_path" {
  description = "Health check path for the target group"
  type        = string
  default     = "/health"
}

variable "enable_stickiness" {
  description = "Enable ALB target group stickiness"
  type        = bool
  default     = true
}

variable "stickiness_duration" {
  description = "Stickiness cookie duration in seconds"
  type        = number
  default     = 300
}
