package com.myorg;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.certificatemanager.Certificate;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecs.AddCapacityOptions;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.MachineImageType;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedEc2Service;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.*;
import software.amazon.awscdk.services.rds.*;
import software.amazon.awscdk.services.route53.HostedZone;
import software.amazon.awscdk.services.route53.HostedZoneAttributes;
import software.amazon.awscdk.services.route53.IHostedZone;
import software.constructs.Construct;
import util.Tuple;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class TestAppStack extends Stack {
    public TestAppStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public TestAppStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        Vpc vpc = getVpc(String.valueOf(scope.getNode().tryGetContext(id)));
        Tuple<DatabaseInstance, SecurityGroup> databaseAndEcsSecurityGroup = getDatabaseInstanceAndEcsSecurityGroup(vpc, scope,id);
         ApplicationLoadBalancedEc2Service applicationLoadBalancedEc2Service = getEc2(scope, vpc, databaseAndEcsSecurityGroup,id);
        new CfnOutput(this, "Records app DNS Name", CfnOutputProps.builder().value(applicationLoadBalancedEc2Service.getLoadBalancer().getLoadBalancerDnsName()).build());

    }
    private Vpc getVpc(String stackId) {

        return Vpc.Builder.create(this, stackId + "-vpc")
                .maxAzs(2)
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
    }

    private Tuple<DatabaseInstance, SecurityGroup> getDatabaseInstanceAndEcsSecurityGroup(Vpc vpc, final Construct scope, String id) {

        final SecurityGroup ecsSecurityGroup = new SecurityGroup(this,  scope.getNode().tryGetContext(id) + "-ecs-ec2-security-group",  SecurityGroupProps.builder()
                .vpc(vpc)
                .description("ECS Security Group")
                .build());
        ecsSecurityGroup.addEgressRule(Peer.anyIpv4(), Port.tcp(3306), "Egress rule to DB port");

        final SecurityGroup databaseSecurityGroup =  new SecurityGroup(this, scope.getNode().tryGetContext(id) + "-database-security-group", SecurityGroupProps.builder()
                .vpc(vpc)
                .description("Database Security Group")
                .build());

        databaseSecurityGroup.addIngressRule(Peer.anyIpv4(), Port.tcp(3306), "Ingress rule to DB port");


        final IInstanceEngine instanceEngine = DatabaseInstanceEngine.mysql(
                MySqlInstanceEngineProps.builder()
                        .version(MysqlEngineVersion.VER_8_0_35)
                        .build()
        );

        DatabaseInstance databaseInstance = DatabaseInstance.Builder.create(this, (String) scope.getNode().tryGetContext(id))
                .vpc(vpc)
                .databaseName("test")
                .securityGroups((Collections.singletonList(databaseSecurityGroup)))
                .vpcSubnets(SubnetSelection.builder().subnets(vpc.getIsolatedSubnets()).build())
                .instanceType(InstanceType.of(InstanceClass.T2, InstanceSize.MICRO))
                .engine(instanceEngine)
                .credentials(Credentials.fromPassword("admin", SecretValue.unsafePlainText("adminadmin")))
                .instanceIdentifier(scope.getNode().tryGetContext(id) + "-database-mysql")
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        new CfnOutput(this, "Database Address", CfnOutputProps.builder().value(databaseInstance.getDbInstanceEndpointAddress()).build());
        new CfnOutput(this, "Database Port", CfnOutputProps.builder().value(databaseInstance.getDbInstanceEndpointPort()).build());

        return new Tuple<>(databaseInstance, ecsSecurityGroup);
    }

    private ApplicationLoadBalancedEc2Service getEc2(Construct scope, Vpc vpc, Tuple<DatabaseInstance, SecurityGroup> databaseAndEcsSecurityGroup, String id) {
        String datasourceUrl = "jdbc:mysql://" + databaseAndEcsSecurityGroup.getVar1().getDbInstanceEndpointAddress() + ":" + databaseAndEcsSecurityGroup.getVar1().getDbInstanceEndpointPort() + "/" + "test";
        Cluster cluster = Cluster.Builder.create(this, scope.getNode().tryGetContext(id)+ "-ecs-cluster")
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

        ApplicationLoadBalancedEc2Service albes;
        if(scope.getNode().tryGetContext(id).toString().contains("test"))
            albes = this.getECSSeviceForHttps(databaseAndEcsSecurityGroup, datasourceUrl, cluster,id);
        else
            albes = this.getECSSeviceForNonHttps(databaseAndEcsSecurityGroup, datasourceUrl, cluster,id);

        albes.getTargetGroup().configureHealthCheck(new HealthCheck.Builder()
                .path("/")
                .healthyHttpCodes("200")
                .build());

        return albes;

    }

    private ApplicationLoadBalancedEc2Service getECSSeviceForNonHttps(Tuple<DatabaseInstance, SecurityGroup> databaseAndEcsSecurityGroup, String datasourceUrl, Cluster cluster, String id) {

        ApplicationLoadBalancedEc2Service albes = ApplicationLoadBalancedEc2Service.Builder.create(this, "Service")
                .cluster(cluster)
                .protocol(ApplicationProtocol.HTTP)
                .cpu(256)
                .memoryLimitMiB(512)
                .taskImageOptions(ApplicationLoadBalancedTaskImageOptions.builder()
                        .image(ContainerImage.fromEcrRepository(Repository.fromRepositoryName(this,"Image-"+ id ,"records"), "latest"))
                        .containerPort(8080)
                        .environment(Map.of(
                                "SPRING_DATASOURCE_URL", datasourceUrl,
                                "SPRING_DATASOURCE_USERNAME", "admin",
                                "SPRING_DATASOURCE_PASSWORD", "adminadmin"
                        ))
                        .build())
                .desiredCount(1)
                .publicLoadBalancer(true)
                .build();

        return albes;
    }

    private ApplicationLoadBalancedEc2Service getECSSeviceForHttps(Tuple<DatabaseInstance, SecurityGroup> databaseAndEcsSecurityGroup, String datasourceUrl, Cluster cluster, String id) {

        IHostedZone zone = HostedZone.fromHostedZoneAttributes(this, "HostedZoneForService", HostedZoneAttributes.builder()
                .hostedZoneId("Z08392861QFV8Q4Z51RTB")
                .zoneName("ashwinicharles.info")
                .build());

        return ApplicationLoadBalancedEc2Service.Builder.create(this, "Service")
                .cluster(cluster)
                .cpu(256)
                .memoryLimitMiB(512)
                .taskImageOptions(ApplicationLoadBalancedTaskImageOptions.builder()
                        .image(ContainerImage.fromEcrRepository(Repository.fromRepositoryName(this,"Image"+ this.getNode().tryGetContext(id) ,"records")))
                        .containerPort(8080)
                        .environment(Map.of(
                                "SPRING_DATASOURCE_URL", datasourceUrl,
                                "SPRING_DATASOURCE_USERNAME", "admin",
                                "SPRING_DATASOURCE_PASSWORD", "adminadmin"
                        ))
                        .build())
                .desiredCount(1)
                .certificate(Certificate.fromCertificateArn(this, "certificate","arn:aws:acm:us-east-1:654654602872:certificate/a80378e0-9feb-4aee-9563-0b7994cbdf5d"))
                .protocol(ApplicationProtocol.HTTPS)
                .domainName("ashwinicharles.info")
                .domainZone(zone)
                .redirectHttp(true)
                .publicLoadBalancer(true)
                .build();
    }

}
