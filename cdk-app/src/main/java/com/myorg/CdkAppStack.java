package com.myorg;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ecr.IRepository;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.constructs.Construct;

import java.util.List;

public class CdkAppStack extends Stack {
    public CdkAppStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public CdkAppStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

//         The code that defines your stack goes here

//         example resource
        IRepository ecrRepo = Repository.fromRepositoryName(this, "MyECRRepo", "records-appliaction-for-testing");

        Cluster cluster = Cluster.Builder.create(this, "MyCluster").build();

        // Create a new ECS task definition
        FargateTaskDefinition taskDefinition = FargateTaskDefinition.Builder.create(this, "MyTaskDefinition").build();

        // Add a container to the task definition
        ContainerDefinition container = taskDefinition.addContainer("MyContainer", ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromEcrRepository(ecrRepo, "latest"))
                .memoryLimitMiB(512)
                .cpu(256)
                .portMappings(List.of(PortMapping.builder().containerPort(8080).build()))
                .build());

        // Create a service using Fargate service pattern
        ApplicationLoadBalancedFargateService service = ApplicationLoadBalancedFargateService.Builder.create(this, "MyService")
                .cluster(cluster)
                .taskDefinition(taskDefinition)
                .desiredCount(1)
                .build();

//
//        // Step 1: Create Docker image asset
//        DockerImageAsset dockerImage = DockerImageAsset.Builder.create(this, "MyDockerImage")
//                .directory("path/to/your/docker-compose-directory")
//                .build();
//
//        // Step 2: Define your infrastructure
//        ApplicationLoadBalancedFargateService fargateService = ApplicationLoadBalancedFargateService.Builder.create(this, "MyFargateService")
//                .taskImageOptions(
//                        ApplicationLoadBalancedTaskImageOptions.builder()
//                                .containerName("my-container")
//                                .image(ContainerImage.fromDockerImageAsset(dockerImage))
//                                // Other container options...
//                                .build())
//                // Other configuration options...
//                .build();

    }
}
