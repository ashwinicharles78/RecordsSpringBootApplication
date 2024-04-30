package com.myorg;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.IpAddresses;
import software.amazon.awscdk.services.ec2.NatProvider;
import software.amazon.awscdk.services.ec2.SubnetConfiguration;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.VpcLookupOptions;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecs.AddCapacityOptions;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.DeploymentController;
import software.amazon.awscdk.services.ecs.DeploymentControllerType;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedEc2Service;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationProtocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

public class ConsumerStack extends Stack {
    private static final String RECORDS_REPO = "records-consumer";
    private static final String DEV = "dev";

    public ConsumerStack(final Construct scope, final String id) {
        this(scope, id, null);
    }
    public ConsumerStack(final Construct scope, final String id, final StackProps stackProps) {
        super(scope,id,stackProps);
        IVpc vpc = Vpc.fromLookup(this, id, VpcLookupOptions.builder()
                        .vpcName("vpc/"+DEV + "-vpc")
                        .ownerAccountId(stackProps.getEnv().getAccount())
                        .region(stackProps.getEnv().getRegion())
                .build());

        Cluster cluster = Cluster.Builder.create(this, RECORDS_REPO + "-ecs-cluster2")
                .clusterName(RECORDS_REPO + "-ecs-cluster2")
                .capacity(AddCapacityOptions.builder()
                        .vpcSubnets(SubnetSelection.builder()
                                .subnets(vpc.getPublicSubnets())
                                .build())
                        .instanceType(InstanceType.of(InstanceClass.T2, InstanceSize.MICRO))
                        .allowAllOutbound(true)
                        .desiredCapacity(1)
                        .canContainersAccessInstanceRole(true)
                        .build())
                .vpc(vpc)
                .build();

        ApplicationLoadBalancedEc2Service albes = ApplicationLoadBalancedEc2Service.Builder.create(this, "Service-"+RECORDS_REPO)
                .cluster(cluster)
                .protocol(ApplicationProtocol.HTTP)
                .cpu(256)
                .memoryLimitMiB(256)
                .desiredCount(1)
                .taskImageOptions(ApplicationLoadBalancedTaskImageOptions.builder()
                        .image(ContainerImage.fromEcrRepository(Repository.fromRepositoryName(this,
                                "build-repository-"+RECORDS_REPO, RECORDS_REPO)))
                        .containerPort(8082)
                        .containerName(id+"-container")
                        .environment(Map.of(
                                "KAFKA_ENDPOINT", this.getNode().tryGetContext("kafka-server").toString()
                        ))
                        .build())
                .desiredCount(1)
                .deploymentController(DeploymentController.builder().type(DeploymentControllerType.ECS).build())
                .publicLoadBalancer(true)
                .build();

        albes.getTargetGroup().configureHealthCheck(new HealthCheck.Builder()
                .path("/health")
                .healthyHttpCodes("200")
                .build());

    }

    private Vpc getVpc(String stackId) {

        return Vpc.Builder.create(this, stackId + "-vpc")
                .maxAzs(2)  // Default is all AZs in region
                .subnetConfiguration(List.of(
                        SubnetConfiguration.builder()
                                .name("Public")
                                .subnetType(SubnetType.PUBLIC)
                                .cidrMask(24)
                                .build(),
                        SubnetConfiguration.builder()
                                .name("Isolated")
                                .subnetType(SubnetType.PRIVATE_WITH_EGRESS)
                                .cidrMask(24)
                                .build()))
                .ipAddresses(IpAddresses.cidr("10.0.0.0/16"))
                .createInternetGateway(true)
                .natGateways(1)
                .natGatewayProvider(NatProvider.gateway())
                .build();
    }

}
