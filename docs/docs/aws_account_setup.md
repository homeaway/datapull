# Setup VPC etc. in AWS Account for DataPull install
The instructions for [deploying DataPull on AWS Fargate and AWS EMR](install_on_aws) assume that you already have available an S3 bucket, a VPC, subnets, etc. If you do not have these, or if you want to install DataPull in a new VPC dedicated to DataPull, please follow the following instructions. 

> It is generally recommended to use your existing VPC, subnets, etc. since they are most likely already setup to access the data you want DatPull to work on, have access to other services like S3, etc. 

When creating a VPC for DataPull, you need at least 2 subnets in different availablity zones, since AWS Application Load Balancer requires a minimum of two availability zones. You can create a VPC with two public subnets (easier approach modelled on https://docs.aws.amazon.com/vpc/latest/userguide/VPC_Scenario1.html) or a VPC with two private subnets and a public subnet (modelled on https://docs.aws.amazon.com/vpc/latest/userguide/VPC_Scenario2.html)

## VPC with two public subnets
- create VPC `datatools` with 2 IPv4 ranges `10.0.0.0/24`
    - this creates a security group named `default` for this VPC
    - this also creates a main route table with no name
- add a rule to security group `default`: to allow inbound traffic to tcp port 22 (SSH) from your client
    - This assumes that you will have a Bastion node or equivalent to access DataPull's API and Spark UI. If you trust your client network's public IP to not change or be spoofed, you can allow all inbound traffic from your client, and thus obviate the need for a Bastion node or equivalent
- this also creates a main route table with no name
- create two subnets in the VPC, each one in a different availability zone in your region.
    - subnet `datatools-external-1` with IPv4 range `10.0.0.0/25`
    - subnet `datatools-external-2` with IPv4 range `10.0.0.128/25`
- create internet gateway `datatools` for vpc `datatools`
- create route for destination `0.0.0.0/0` to internet gateway `datatools` for default the route table for the VPC

## VPC with two private subnets and a public subnet
- create VPC `datatools` with 2 IPv4 ranges `10.0.0.0/24`, `10.1.0.0/24`
    - this creates a security group named `default` for this VPC
    - this also creates a main route table with no name
- add a rule to security group `default`: to allow inbound traffic to tcp port 22 (SSH) from your client network (or from your public IP as a last resort)
- create three subnets in the VPC, each one in a different availability zone in your region.
    - subnet `datatools-internal-1` with IPv4 range `10.0.0.0/25`
    - subnet `datatools-internal-2` with IPv4 range `10.0.0.128/25`
    - subnet `datatools-external-1` with IPv4 range `10.1.0.0/24`
- create internet gateway `datatools` for vpc `datatools`
- create route table `datatools-external` in vpc `datatools`
- create route for detination `0.0.0.0/0` to internet gateway `datatools` for route table `datatools-external`
- associate subnet `datatools-external-1` to route table `datatools-external`
    - subnets `datatools-internal-[1-2]` will remain associated to the main/default route table (not route table `datatools-external`) for the VPC `datatools`
- create NAT gateway in the subnet `datatools-external-1` and with a new/existing elastic IP. 
- add an entry to the default route table with destination as `0.0.0.0/0`  and Target as the NAT gateway

## Additional Steps (common to both VPC types above)
- create S3 bucket `datatools-datapull` with SSE-S3 encryption in the same region as the VPC
- create gateway endpoint for S3, associated with VPC `datatools` and the default route table (not route table `datatools-external`). The policy should be `Full Access`
- create a bastion host by following https://aws.amazon.com/blogs/security/how-to-record-ssh-sessions-established-through-a-bastion-host/ with
    - existing keypair, else no SSH possible
    - security groups `default` (that allows SSH connections from your client network) and `ElasticMapReduce-Master-Private` (to allow bastion node to connect to EMR master)
    > For simplicity (at the cost of auditing and security features like 2FA), you can spin up a `t2.micro` EC2 instance with AWS Linux 2 AMI as an alternative to a bastion host; it will allow ssh tunneling. 
    > If you are using a VPC with only pulic subnets, and if you trust your client network's public IP to not change or be spoofed, you can allow all inbound traffic from your client, and thus obviate the need for a Bastion node or equivalent