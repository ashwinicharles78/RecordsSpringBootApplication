package com.myorg;

import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.CfnOutputProps;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.SecretValue;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.certificatemanager.Certificate;
import software.amazon.awscdk.services.codebuild.BuildEnvironment;
import software.amazon.awscdk.services.codebuild.BuildEnvironmentVariable;
import software.amazon.awscdk.services.codebuild.GitHubSourceProps;
import software.amazon.awscdk.services.codebuild.Project;
import software.amazon.awscdk.services.codebuild.Source;
import software.amazon.awscdk.services.codepipeline.Artifact;
import software.amazon.awscdk.services.codepipeline.IStage;
import software.amazon.awscdk.services.codepipeline.Pipeline;
import software.amazon.awscdk.services.codepipeline.PipelineType;
import software.amazon.awscdk.services.codepipeline.StageOptions;
import software.amazon.awscdk.services.codepipeline.actions.CodeBuildAction;
import software.amazon.awscdk.services.codepipeline.actions.EcsDeployAction;
import software.amazon.awscdk.services.codepipeline.actions.GitHubSourceAction;
import software.amazon.awscdk.services.msk.alpha.*;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.IpAddresses;
import software.amazon.awscdk.services.ec2.NatProvider;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SecurityGroupProps;
import software.amazon.awscdk.services.ec2.SubnetConfiguration;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecs.AddCapacityOptions;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedEc2Service;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationProtocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.rds.Credentials;
import software.amazon.awscdk.services.rds.DatabaseInstance;
import software.amazon.awscdk.services.rds.DatabaseInstanceBase;
import software.amazon.awscdk.services.rds.DatabaseInstanceEngine;
import software.amazon.awscdk.services.rds.DatabaseInstanceFromSnapshot;
import software.amazon.awscdk.services.rds.MySqlInstanceEngineProps;
import software.amazon.awscdk.services.rds.MysqlEngineVersion;
import software.amazon.awscdk.services.rds.SnapshotCredentials;
import software.amazon.awscdk.services.rds.StorageType;
import software.amazon.awscdk.services.route53.HostedZone;
import software.amazon.awscdk.services.route53.HostedZoneAttributes;
import software.amazon.awscdk.services.route53.IHostedZone;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static software.amazon.awscdk.services.codebuild.LinuxBuildImage.AMAZON_LINUX_2_5;

public class MyPipelineStack extends Stack {
    private static final String GITHUB_TOKEN = "github-token";
    private static final String CERTIFICATE_ARN = "certificate-arn";
    private static final String RECORDS = "records";

    public MyPipelineStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public MyPipelineStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        Artifact sourceActionOutput = new Artifact();
        Artifact buildOutput = new Artifact();

        @NotNull SecretValue secret = SecretValue.secretsManager(GITHUB_TOKEN);

        // CodePipeline
        Pipeline pipeline = Pipeline.Builder.create(this, "MyPipeline").pipelineType(PipelineType.V2).build();

        Vpc vpc = this.getVpc(id);

        SecurityGroup codebuildSG = new SecurityGroup(this, "Records" + "-CodeBuild-security-group", SecurityGroupProps.builder()
                .vpc(vpc)
                .description("CodeBuild Security Group")
                .build());
        codebuildSG.addEgressRule(Peer.anyIpv4(), Port.tcp(3306));
        codebuildSG.addIngressRule(Peer.anyIpv4(), Port.tcp(3306));
        codebuildSG.addIngressRule(Peer.anyIpv4(), Port.allTraffic());
        codebuildSG.addEgressRule(Peer.anyIpv4(), Port.allTraffic());

        DatabaseInstanceBase rdsDb = this.createRDSdb(id, vpc);

        software.amazon.awscdk.services.msk.alpha.Cluster kafkaCluster = this.createMKSCluster(vpc, id);

        String datasourceUrl = "jdbc:mysql://" + rdsDb.getDbInstanceEndpointAddress() + ":" + rdsDb.getDbInstanceEndpointPort() + "/" + "test";
        // CodeBuild Project
        Project codeBuildProject = Project.Builder.create(this, "MyCodeBuildProject")
                .projectName(this.getNode().tryGetContext(id)+"-build-project")
                .vpc(vpc)
                .subnetSelection(SubnetSelection.builder().subnets(vpc.getPrivateSubnets()).build())
                .securityGroups(List.of(
                    codebuildSG
                ))
                .source(Source.gitHub(GitHubSourceProps.builder()
                                .owner("ashwinicharle78")
                                .repo("RecordsSpringBootApplication")
                                .branchOrRef("main").build()))
                .role(Role.fromRoleArn(this, "currentRole", "arn:aws:iam::654654602872:role/service-role/codebuild-testing-build-again-service-role"))
                .environment(BuildEnvironment.builder()
                        .buildImage(AMAZON_LINUX_2_5)
                        .privileged(true)
                        .environmentVariables(Map.of("IMAGE_REPO_NAME", BuildEnvironmentVariable.builder()
                                        .value(RECORDS)
                                        .build(),
                                "AWS_DEFAULT_REGION", BuildEnvironmentVariable.builder()
                                        .value(Objects.requireNonNull(props.getEnv()).getRegion())
                                        .build(),
                                "AWS_ACCOUNT_ID", BuildEnvironmentVariable.builder()
                                        .value(props.getEnv().getAccount())
                                        .build(),
                                "IMAGE_TAG", BuildEnvironmentVariable.builder()
                                        .value("latest")
                                        .build(),
                                "JDBC_CONNECTION", BuildEnvironmentVariable.builder()
                                        .value(datasourceUrl)
                                        .build(),
                                "KAFKA_ENDPOINT",BuildEnvironmentVariable.builder()
                                        .value(kafkaCluster.getBootstrapBrokers())
                                        .build()
                                ))
                        .build())
                .build();

        ApplicationLoadBalancedEc2Service ecsService = this.getEc2(vpc, id, rdsDb, kafkaCluster);

        // Add source stage
        IStage sourceStage = pipeline.addStage(StageOptions.builder()
                .stageName("Source")
                .actions(List.of(
                        GitHubSourceAction.Builder.create()
                                .actionName("GitHub_Source")
                                .owner("ashwinicharles78")
                                .repo("RecordsSpringBootApplication")
                                .branch("main")
                                .oauthToken(secret)
                                .output(sourceActionOutput)
                                .runOrder(1)
                                .build()
                ))
                .build());

        // Add build stage
        IStage buildStage = pipeline.addStage(StageOptions.builder()
                .stageName("Build")
                .actions(List.of(
                        CodeBuildAction.Builder.create()
                                .actionName("CodeBuild")
                                .input(sourceActionOutput)
                                .outputs(List.of(buildOutput))
                                .project(codeBuildProject)
                                .runOrder(2)
                                .build()
                ))
                .build());

        // Add deploy stage
        IStage deployStage = pipeline.addStage(StageOptions.builder()
                .stageName("Deploy")
                .actions(List.of(
                        EcsDeployAction.Builder.create()
                                .actionName("DeployAction")
                                .deploymentTimeout(Duration.minutes(10))
                                .service(ecsService.getService())
                                .input(buildOutput)
                                .runOrder(3)
                                .build()
                ))
                .build());

    }

    private software.amazon.awscdk.services.msk.alpha.Cluster createMKSCluster(Vpc vpc, String id) {
//        Stream stream = Stream.Builder.create(this, "MyFirstStream")
//                .streamName("my-awesome-stream")
//                .shardCount(1)
//                .streamMode(StreamMode.ON_DEMAND)
//                .retentionPeriod(Duration.days(7))
//                .build();
//
//
//        CfnCluster.Builder.create(this,"MSKCluster-"+id)
//                .kafkaVersion()
//                .build()

        SecurityGroup kafkaSG = new SecurityGroup(this, "Records" + "-MSK-security-group", SecurityGroupProps.builder()
                .vpc(vpc)
                .description("Kafka Security Group")
                .build());

        kafkaSG.addIngressRule(Peer.anyIpv4(), Port.allTraffic());
        kafkaSG.addEgressRule(Peer.anyIpv4(), Port.allTraffic());

        return software.amazon.awscdk.services.msk.alpha.Cluster.Builder.create(this, "test-cluster-"+id)
                .clusterName("kafka-cluster-"+id)
                .kafkaVersion(KafkaVersion.V3_5_1)
                .instanceType(InstanceType.of(InstanceClass.T3, InstanceSize.SMALL))
                .vpc(vpc)
                .encryptionInTransit(EncryptionInTransitConfig.builder()
                        .clientBroker(ClientBrokerEncryption.PLAINTEXT)
                        .build())
//                .clientAuthentication(ClientAuthentication.sasl(SaslAuthProps.builder().iam(false).scram(false).build()))
                .vpcSubnets(SubnetSelection.builder().subnets(vpc.getPublicSubnets()).build())
                .ebsStorageInfo(EbsStorageInfo.builder().volumeSize(1).build())
                .numberOfBrokerNodes(1)
                .securityGroups(List.of(kafkaSG))
                .build();

//        CfnCluster cfnCluster = CfnCluster.Builder.create(this, "MyCfnCluster")
//                .brokerNodeGroupInfo(CfnCluster.BrokerNodeGroupInfoProperty.builder()
//                        .clientSubnets(List.of("clientSubnets"))
//                        .instanceType("instanceType")
//                        // the properties below are optional
//                        .brokerAzDistribution("brokerAzDistribution")
//                        .connectivityInfo(CfnCluster.ConnectivityInfoProperty.builder()
//                                .publicAccess(CfnCluster.PublicAccessProperty.builder()
//                                        .type("type")
//                                        .build())
//                                .vpcConnectivity(CfnCluster.VpcConnectivityProperty.builder()
//                                        .clientAuthentication(CfnCluster.VpcConnectivityClientAuthenticationProperty.builder()
//                                                .build())
//                                        .build())
//                                .build())
//                        .securityGroups(List.of("securityGroups"))
//                        .storageInfo(CfnCluster.StorageInfoProperty.builder()
//                                .ebsStorageInfo(CfnCluster.EBSStorageInfoProperty.builder()
//                                        .provisionedThroughput(CfnCluster.ProvisionedThroughputProperty.builder()
//                                                .enabled(false)
//                                                .volumeThroughput(1)
//                                                .build())
//                                        .volumeSize(1)
//                                        .build())
//                                .build())
//                        .build())
//                .clusterName("test-cluster"+id)
//                .kafkaVersion("3.5.1")
//                .numberOfBrokerNodes(1)
//                .build();
    }

    private DatabaseInstanceBase createRDSdb(String id, Vpc vpc) {
        final SecurityGroup databaseSecurityGroup =  new SecurityGroup(this, "Records" + "-database-security-group", SecurityGroupProps.builder()
                .vpc(vpc)
                .description("Database Security Group")
                .build());

        new CfnOutput(this, "Test", CfnOutputProps.builder().value(this.getStackId()).build());
        databaseSecurityGroup.addIngressRule(Peer.anyIpv4(), Port.tcp(3306), "Ingress rule to DB port");
        databaseSecurityGroup.addEgressRule(Peer.anyIpv4(), Port.allTraffic());
        Object snapshotARN = this.getNode().tryGetContext("recovery-snapshot-identifier-"+id);
        DatabaseInstanceBase db;
        if(snapshotARN != null) {
            db = DatabaseInstanceFromSnapshot.Builder.create(this, "instance-from-snapshot"+id)
                    .snapshotIdentifier(snapshotARN.toString())
                    .instanceIdentifier("db-instance-"+id)
                    .engine(DatabaseInstanceEngine.mysql(MySqlInstanceEngineProps.builder().version(MysqlEngineVersion.VER_8_0_35).build()))
                    .instanceType(InstanceType.of(InstanceClass.T2, InstanceSize.MICRO))
                    .credentials(SnapshotCredentials.fromPassword(SecretValue.unsafePlainText("adminadmin"))) // Optional - will default to 'admin' username and generated password
                    .allocatedStorage(20)
                    .port(3306)
                    .removalPolicy(RemovalPolicy.SNAPSHOT)
                    .multiAz(false)
                    .storageType(StorageType.GP2)
                    .vpc(vpc)
                    .vpcSubnets(SubnetSelection.builder().subnets(vpc.getPrivateSubnets()).build())
                    .securityGroups(List.of(databaseSecurityGroup))
                    .build();
        } else {
            db = DatabaseInstance.Builder.create(this, "Instance" + id)
                    .instanceIdentifier("db-instance-" + id)
                    .engine(DatabaseInstanceEngine.mysql(MySqlInstanceEngineProps.builder().version(MysqlEngineVersion.VER_8_0_35).build()))
                    .instanceType(InstanceType.of(InstanceClass.T2, InstanceSize.MICRO))
                    .credentials(Credentials.fromPassword("admin", SecretValue.unsafePlainText("adminadmin"))) // Optional - will default to 'admin' username and generated password
                    .allocatedStorage(20)
                    .port(3306)
                    .removalPolicy(RemovalPolicy.SNAPSHOT)
                    .multiAz(false)
                    .databaseName("test")
                    .storageType(StorageType.GP2)
                    .vpc(vpc)
                    .vpcSubnets(SubnetSelection.builder().subnets(vpc.getPrivateSubnets()).build())
                    .securityGroups(List.of(databaseSecurityGroup))
                    .build();
        }

        return db;

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

    private ApplicationLoadBalancedEc2Service getEc2(Vpc vpc, String id, DatabaseInstanceBase rdsDb, software.amazon.awscdk.services.msk.alpha.Cluster kafkaCluster) {
        String datasourceUrl = "jdbc:mysql://" + rdsDb.getDbInstanceEndpointAddress() + ":" + rdsDb.getDbInstanceEndpointPort() + "/" + "test";
        Cluster cluster = Cluster.Builder.create(this, RECORDS + "-ecs-cluster")
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

        ApplicationLoadBalancedEc2Service albes;
        if(this.getNode().tryGetContext(id).toString().contains("test"))
            albes = this.getECSSeviceForHttps(datasourceUrl, cluster,id);
        else
            albes = this.getECSSeviceForNonHttps(datasourceUrl, cluster,id, kafkaCluster);
        albes.getTargetGroup().configureHealthCheck(new HealthCheck.Builder()
                .path("/auth/health")
                .healthyHttpCodes("200")
                .build());

        return albes;
    }

    private ApplicationLoadBalancedEc2Service getECSSeviceForNonHttps(String datasourceUrl, Cluster cluster, String id, software.amazon.awscdk.services.msk.alpha.Cluster kafkaCluster) {
        return ApplicationLoadBalancedEc2Service.Builder.create(this, "Service")
                .cluster(cluster)
                .protocol(ApplicationProtocol.HTTP)
                .cpu(256)
                .memoryLimitMiB(256)
                .desiredCount(1)
                .taskImageOptions(ApplicationLoadBalancedTaskImageOptions.builder()
                        .image(ContainerImage.fromEcrRepository(Repository.fromRepositoryName(this,
                                "build-repository", RECORDS)))
                        .containerPort(8080)
                        .containerName(this.getNode().tryGetContext(id).toString()+"-container")
                        .environment(Map.of(
                                "SPRING_DATASOURCE_URL", datasourceUrl,
                                "SPRING_DATASOURCE_USERNAME", "admin",
                                "SPRING_DATASOURCE_PASSWORD", "adminadmin",
                                "KAFKA_ENDPOINT", kafkaCluster.getBootstrapBrokers()
                        ))
                        .build())
                .desiredCount(1)
                .publicLoadBalancer(true)
                .build();
    }

    private ApplicationLoadBalancedEc2Service getECSSeviceForHttps(String datasourceUrl, Cluster cluster, String id) {

        IHostedZone zone = HostedZone.fromHostedZoneAttributes(this, "HostedZoneForService", HostedZoneAttributes.builder()
                .hostedZoneId("Z08392861QFV8Q4Z51RTB")
                .zoneName("ashwinicharles.info")
                .build());

        return ApplicationLoadBalancedEc2Service.Builder.create(this, "Service")
                .cluster(cluster)
                .cpu(256)
                .memoryLimitMiB(256)
                .desiredCount(1)
                .taskImageOptions(ApplicationLoadBalancedTaskImageOptions.builder()
                        .image(ContainerImage.fromEcrRepository(Repository.fromRepositoryName(this,"Image"+ this.getNode().tryGetContext(id) ,RECORDS)))
                        .containerPort(8080)
                        .environment(Map.of(
                                "SPRING_DATASOURCE_URL", datasourceUrl,
                                "SPRING_DATASOURCE_USERNAME", "admin",
                                "SPRING_DATASOURCE_PASSWORD", "adminadmin"
                        ))
                        .build())
                .desiredCount(1)
                .certificate(Certificate.fromCertificateArn(this, "certificate",this.getNode().tryGetContext(CERTIFICATE_ARN).toString()))
                .protocol(ApplicationProtocol.HTTPS)
                .domainName("ashwinicharles.info")
                .domainZone(zone)
                .redirectHttp(true)
                .publicLoadBalancer(true)
                .build();
    }
}
