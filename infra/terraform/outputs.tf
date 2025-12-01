output "alb_dns_name" {
  description = "ALB DNS name"
  value       = aws_lb.alb.dns_name
}

output "alb_arn" {
  description = "ALB ARN"
  value       = aws_lb.alb.arn
}

output "target_group_arn" {
  description = "Target group ARN"
  value       = aws_lb_target_group.tg.arn
}

output "alb_security_group_id" {
  description = "ALB security group id"
  value       = aws_security_group.alb_sg.id
}

output "backend_security_group_id" {
  description = "Backend security group id (to attach to instances)"
  value       = aws_security_group.backend_sg.id
}
