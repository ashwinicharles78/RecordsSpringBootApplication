package com.myorg;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.certificatemanager.Certificate;
import software.amazon.awscdk.services.codedeploy.LoadBalancer;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ecr.IRepository;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecs.AddCapacityOptions;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.MachineImageType;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedEc2Service;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.*;
import software.amazon.awscdk.services.globalaccelerator.ListenerProps;
import software.amazon.awscdk.services.rds.*;
import software.amazon.awscdk.services.route53.ARecord;
import software.amazon.awscdk.services.route53.HostedZone;
import software.amazon.awscdk.services.route53.HostedZoneAttributes;
import software.amazon.awscdk.services.route53.IHostedZone;
import software.amazon.awscdk.services.route53.RecordTarget;
import software.amazon.awscdk.services.route53.targets.LoadBalancerTarget;
import software.constructs.Construct;
import util.Tuple;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TestAppStack extends Stack {
    public TestAppStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public TestAppStack(final Construct scope, final String id, final StackProps props) {
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

        DatabaseInstance databaseInstance = DatabaseInstance.Builder.create(this, "Records" + "-database-mysql")
                .vpc(vpc)
                .databaseName("test")
                .securityGroups((Collections.singletonList(databaseSecurityGroup)))
                .vpcSubnets(SubnetSelection.builder().subnets(vpc.getIsolatedSubnets()).build())
                .instanceType(InstanceType.of(InstanceClass.T2, InstanceSize.MICRO))
                .engine(instanceEngine)
                .credentials(Credentials.fromPassword("ashwini", SecretValue.unsafePlainText("ashwini_pw")))
                .instanceIdentifier("Records" + "-database-mysql")
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

/*        ApplicationLoadBalancer loadBalancer = ApplicationLoadBalancer.Builder.create(this, "loadBalancer")
                .vpc(vpc)
                .internetFacing(true)
                .securityGroup(databaseAndEcsSecurityGroup.getVar2())
                .build();
        RedirectOptions redirectOptions = RedirectOptions.builder()
                .protocol("HTTPS")
                .port("443")
                .build();

        loadBalancer.addListener("listener", BaseApplicationListenerProps.builder()
                .defaultAction(ListenerAction.redirect(redirectOptions))
                .port(80)
                .protocol(ApplicationProtocol.HTTP)
                .build());*/

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

        IHostedZone zone = HostedZone.fromHostedZoneAttributes(this, "HostedZoneForService", HostedZoneAttributes.builder()
                .hostedZoneId("Z08392861QFV8Q4Z51RTB")
                .zoneName("ashwinicharles.info")
                .build());

        ApplicationLoadBalancedEc2Service albes = ApplicationLoadBalancedEc2Service.Builder.create(this, "Service")
                .cluster(cluster)
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
                .certificate(Certificate.fromCertificateArn(this, "certificate","arn:aws:acm:us-east-1:654654602872:certificate/a80378e0-9feb-4aee-9563-0b7994cbdf5d"))
                .protocol(ApplicationProtocol.HTTPS)
                .domainName("ashwinicharles.info")
                .domainZone(zone)
                .redirectHttp(true)
                .publicLoadBalancer(true)
                .build();

//        createRoute53Record(albes);
//        configureListenersforHttps(albes);


        albes.getTargetGroup().configureHealthCheck(new HealthCheck.Builder()
                .path("/records")
                .healthyHttpCodes("200")
                .build());

        return albes;

    }

    private void createRoute53Record(ApplicationLoadBalancedEc2Service albes) {

        IHostedZone zone = HostedZone.fromHostedZoneAttributes(this, "HostedZone", HostedZoneAttributes.builder()
                .hostedZoneId("Z08392861QFV8Q4Z51RTB")
                .zoneName("ashwinicharles.info")
                .build());

        ARecord.Builder.create(this, "ARecord")
                .zone(zone)
                .target(RecordTarget.fromAlias(new LoadBalancerTarget(albes.getLoadBalancer())))
                .build();

    }

    private void configureListenersforHttps(ApplicationLoadBalancedEc2Service albes) {
        IListenerCertificate certificate = ListenerCertificate.fromArn("arn:aws:acm:us-east-1:654654602872:certificate/a80378e0-9feb-4aee-9563-0b7994cbdf5d");

//        ApplicationListener listener = albes.getLoadBalancer().getListeners().get(0);
//        listener.addAction("Redirect", AddApplicationActionProps.builder().build());

        albes.getLoadBalancer().addListener("test",BaseApplicationListenerProps.builder()
                .certificates(List.of(certificate))
                .defaultTargetGroups(List.of(albes.getTargetGroup()))
                .protocol(ApplicationProtocol.HTTPS).build());

/*        albes.getLoadBalancer().addRedirect(ApplicationLoadBalancerRedirectConfig.builder()
                        .sourceProtocol(ApplicationProtocol.HTTP)
                        .targetProtocol(ApplicationProtocol.HTTPS)
                        .open(true)
                .build());*/
    }
}
