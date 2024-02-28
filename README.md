Written code for the spring boot application
Written a docker compose and a docker file so that it can be run using docker.
Written CDK code to deploy changes in the project -
  1. Firstly since we are using a public docker image to deploy the project we first need to create a db instance to point to so created a RDS database first.
  2. Now we need to provide a network to it so created a VPC for it.
  3. Now we need a service to run the image and since we have AWS ECS, so used that to run the docker image.

