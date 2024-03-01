package com.myorg;

import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedEc2Service;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationProtocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import util.Tuple;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.*;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ecr.IRepository;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.rds.*;
import software.constructs.Construct;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DevAppStack extends Stack {
    public DevAppStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public DevAppStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

//         The code that defines your stack goes here

//         example resource
        IRepository ecrRepo = Repository.fromRepositoryName(this, "MyECRRepo", "records-appliaction-for-testing");

//        Cluster cluster = Cluster.Builder.create(this, "MyCluster").build();

        Vpc vpc = getVpc("Records");
        Tuple<DatabaseInstance, SecurityGroup> databaseAndEcsSecurityGroup = getDatabaseInstanceAndEcsSecurityGroup(vpc);
//        ApplicationLoadBalancedFargateService applicationLoadBalancedFargateService = getEcs(vpc, databaseAndEcsSecurityGroup);
        ApplicationLoadBalancedEc2Service applicationLoadBalancedEc2Service = getEc2(vpc, databaseAndEcsSecurityGroup);
        new CfnOutput(this, "Records app DNS Name", CfnOutputProps.builder().value(applicationLoadBalancedEc2Service.getLoadBalancer().getLoadBalancerDnsName()).build());

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
                                .subnetType(SubnetType.PRIVATE_ISOLATED)
                                .cidrMask(24)
                                .build()))
                .ipAddresses(IpAddresses.cidr("10.0.0.0/16"))
                .createInternetGateway(true)
                .build();
//        return Vpc.fromLookup(this, "DefaultVPC", VpcLookupOptions.builder().isDefault(true).build());
    }

    private Tuple<DatabaseInstance, SecurityGroup> getDatabaseInstanceAndEcsSecurityGroup (Vpc vpc) {

        final SecurityGroup ecsSecurityGroup = new SecurityGroup(this,  "Records" + "-ecs-fargate-security-group",  SecurityGroupProps.builder()
                .vpc(vpc)
                .description("ECS Security Group")
                .build());
        ecsSecurityGroup.addEgressRule(Peer.anyIpv4(), Port.tcp(3306), "Egress rule to DB port");

        final SecurityGroup databaseSecurityGroup =  new SecurityGroup(this, "Records" + "-database-security-group", SecurityGroupProps.builder()
                .vpc(vpc)
                .description("Database Security Group")
                .build());

        databaseSecurityGroup.addIngressRule(Peer.anyIpv4(), Port.tcp(3306), "Ingress rule to DB port");


        final IInstanceEngine instanceEngine = DatabaseInstanceEngine.mysql(
                MySqlInstanceEngineProps.builder()
                        .version(MysqlEngineVersion.VER_8_0_35)
                        .build()
        );

        DatabaseInstance databaseInstance = DatabaseInstance.Builder.create(this, "Records" + "-database-mysql-dev")
                .vpc(vpc)
                .databaseName("test")
                .securityGroups((Collections.singletonList(databaseSecurityGroup)))
                .vpcSubnets(SubnetSelection.builder().subnets(vpc.getIsolatedSubnets()).build())
                .instanceType(InstanceType.of(InstanceClass.T2, InstanceSize.MICRO))
                .engine(instanceEngine)
                .credentials(Credentials.fromPassword("ashwini", SecretValue.unsafePlainText("ashwini_pw")))
                .instanceIdentifier("Records" + "-database-mysql-dev")
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        new CfnOutput(this, "Database Address", CfnOutputProps.builder().value(databaseInstance.getDbInstanceEndpointAddress()).build());
        new CfnOutput(this, "Database Port", CfnOutputProps.builder().value(databaseInstance.getDbInstanceEndpointPort()).build());

        return new Tuple<>(databaseInstance, ecsSecurityGroup);
    }

    private ApplicationLoadBalancedFargateService getEcs(Vpc vpc, Tuple<DatabaseInstance, SecurityGroup> databaseAndEcsSecurityGroup) {
        Cluster cluster = Cluster.Builder.create(this, "Records" + "-ecs-cluster")
                .vpc(vpc)
                .build();

        String datasourceUrl = "jdbc:mysql://" + databaseAndEcsSecurityGroup.getVar1().getDbInstanceEndpointAddress() + ":" + databaseAndEcsSecurityGroup.getVar1().getDbInstanceEndpointPort() + "/" + "test";
        new CfnOutput(this, "datasourceUrl", CfnOutputProps.builder().value(datasourceUrl).build());

        // Create a load-balanced Fargate service and make it public
        var albfs = ApplicationLoadBalancedFargateService.Builder.create(this, "Records" + "-ecs-service")
                .cluster(cluster)
                .cpu(256)
                .desiredCount(1)
                .taskImageOptions(
                        ApplicationLoadBalancedTaskImageOptions.builder()
                                .image(ContainerImage.fromRegistry("public.ecr.aws/m1j0e2f8/records-appliaction-for-testing"))
                                .containerPort(8080)
                                .environment(Map.of(
                                        "SPRING_DATASOURCE_URL", datasourceUrl,
                                        "SPRING_DATASOURCE_USERNAME", "ashwini",
                                        "SPRING_DATASOURCE_PASSWORD", "ashwini_pw"
                                ))
                                .build())

                .memoryLimitMiB(512)
                .publicLoadBalancer(true) //public facing load balancer or not
                .assignPublicIp(true)   // Need to clear doubt in this
                .securityGroups(Collections.singletonList(databaseAndEcsSecurityGroup.getVar2()))
                .build();

        albfs.getTargetGroup().configureHealthCheck(new HealthCheck.Builder()
                .path("/records")
                .port(String.valueOf(8080))
                .healthyHttpCodes("200")
                .build());

        return albfs;
    }

    private ApplicationLoadBalancedEc2Service getEc2(Vpc vpc, Tuple<DatabaseInstance, SecurityGroup> databaseAndEcsSecurityGroup) {
        String datasourceUrl = "jdbc:mysql://" + databaseAndEcsSecurityGroup.getVar1().getDbInstanceEndpointAddress() + ":" + databaseAndEcsSecurityGroup.getVar1().getDbInstanceEndpointPort() + "/" + "test";
        Cluster cluster = Cluster.Builder.create(this, "Records" + "-ecs-cluster")
                .capacity(AddCapacityOptions.builder()
                        .vpcSubnets(SubnetSelection.builder()
                                .subnets(vpc.getPublicSubnets())
                                .build())
                        .instanceType(InstanceType.of(InstanceClass.T2, InstanceSize.MICRO))
                        .allowAllOutbound(true)
                        .desiredCapacity(1)
                        .machineImageType(MachineImageType.AMAZON_LINUX_2)
                        .build())
                .vpc(vpc)
                .build();

        ApplicationLoadBalancedEc2Service albes = ApplicationLoadBalancedEc2Service.Builder.create(this, "Service")
                .cluster(cluster)
                .protocol(ApplicationProtocol.HTTP)
                .cpu(256)
                .memoryLimitMiB(512)
                .taskImageOptions(ApplicationLoadBalancedTaskImageOptions.builder()
                        .image(ContainerImage.fromRegistry("public.ecr.aws/m1j0e2f8/records-appliaction-for-testing"))
                        .containerPort(8080)
                        .environment(Map.of(
                                "SPRING_DATASOURCE_URL", datasourceUrl,
                                "SPRING_DATASOURCE_USERNAME", "ashwini",
                                "SPRING_DATASOURCE_PASSWORD", "ashwini_pw"
                        ))
                        .build())
                .desiredCount(1)
                .publicLoadBalancer(true)
                .build();

        albes.getTargetGroup().configureHealthCheck(new HealthCheck.Builder()
                .path("/records")
                .healthyHttpCodes("200")
                .build());

        return albes;

    }
}
