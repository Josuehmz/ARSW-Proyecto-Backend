Terraform module for ALB + Target Group + Security Groups

Usage
-----

1. Update `variables.tf` values via a `terraform.tfvars` file or provide `-var` flags.

Required variables:
- `vpc_id` - the VPC where your EC2 instances and subnets exist
- `public_subnet_ids` - list of 2+ public subnet ids (for ALB)
- `instance_ids` - list of EC2 instance ids to register in the target group

Example `terraform.tfvars`:

```hcl
aws_region = "us-east-1"
vpc_id = "vpc-0123456789abcdef0"
public_subnet_ids = ["subnet-aaa","subnet-bbb"]
instance_ids = ["i-0123456789abcdef0","i-0fedcba9876543210"]
name_prefix = "balatro"
```

Notes
-----
- This module will create:
  - an Internet-facing ALB in the provided public subnets
  - an ALB security group (allow HTTP 80 from internet)
  - a Backend security group (allow TCP 8080 from the ALB SG)
  - a Target Group with health check and optional stickiness
  - a Listener on port 80 forwarding to the target group
  - Attachments of the provided instance IDs to the target group

- After `terraform apply`, take the `backend_security_group_id` output and attach it to your EC2 instances (or update the EC2 launch configuration) so the ALB can reach them on port 8080.

- Ensure the provided EC2 instances are in the same VPC and have the necessary service (docker-compose, app listening on 8080, health endpoint).

- This is a basic template: if you need HTTPS, ACM certificate integration, or autoscaling groups, I can extend it.
