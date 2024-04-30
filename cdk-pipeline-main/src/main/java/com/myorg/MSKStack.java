package com.myorg;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.CfnOutputProps;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.SecretValue;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SecurityGroupProps;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.VpcLookupOptions;
import software.amazon.awscdk.services.msk.alpha.ClientBrokerEncryption;
import software.amazon.awscdk.services.msk.alpha.Cluster;
import software.amazon.awscdk.services.msk.alpha.ClusterConfigurationInfo;
import software.amazon.awscdk.services.msk.alpha.EbsStorageInfo;
import software.amazon.awscdk.services.msk.alpha.EncryptionInTransitConfig;
import software.amazon.awscdk.services.msk.alpha.KafkaVersion;
import software.amazon.awscdk.services.rds.Credentials;
import software.amazon.awscdk.services.rds.DatabaseInstance;
import software.amazon.awscdk.services.rds.DatabaseInstanceBase;
import software.amazon.awscdk.services.rds.DatabaseInstanceEngine;
import software.amazon.awscdk.services.rds.DatabaseInstanceFromSnapshot;
import software.amazon.awscdk.services.rds.MySqlInstanceEngineProps;
import software.amazon.awscdk.services.rds.MysqlEngineVersion;
import software.amazon.awscdk.services.rds.SnapshotCredentials;
import software.amazon.awscdk.services.rds.StorageType;
import software.constructs.Construct;

import java.util.List;

public class MSKStack extends Stack {
    public MSKStack(final Construct scope, final String id) {
        this(scope, id , null);
    }

    public MSKStack(final Construct scope, final String id, final StackProps stackProps) {
        super(scope, id, stackProps);
        IVpc vpc = Vpc.fromLookup(this, id, VpcLookupOptions.builder()
                .vpcName("vpc/dev-vpc")
                .ownerAccountId(stackProps.getEnv().getAccount())
                .region(stackProps.getEnv().getRegion())
                .build());

        SecurityGroup kafkaSG = new SecurityGroup(this, "Records" + "-MSK-security-group", SecurityGroupProps.builder()
                .vpc(vpc)
                .description("Kafka Security Group")
                .build());

        kafkaSG.addIngressRule(Peer.anyIpv4(), Port.allTraffic());
        kafkaSG.addEgressRule(Peer.anyIpv4(), Port.allTraffic());

        Cluster.Builder.create(this, "test-cluster-"+id)
                .clusterName("kafka-cluster-"+id)
                .kafkaVersion(KafkaVersion.V3_5_1)
                .instanceType(InstanceType.of(InstanceClass.T3, InstanceSize.SMALL))
                .vpc(vpc)
                .encryptionInTransit(EncryptionInTransitConfig.builder()
                        .clientBroker(ClientBrokerEncryption.PLAINTEXT)
                        .build())
                .vpcSubnets(SubnetSelection.builder().subnets(vpc.getPublicSubnets()).build())
                .ebsStorageInfo(EbsStorageInfo.builder().volumeSize(1).build())
                .numberOfBrokerNodes(1)
                .configurationInfo(ClusterConfigurationInfo.builder().revision(2).arn("arn:aws:kafka:us-east-1:654654602872:configuration/config-test/d66b33c2-a63e-4989-b254-3de2b92a20ef-12").build())
                .securityGroups(List.of(kafkaSG))
                .build();

    }
}
