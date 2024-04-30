package com.myorg;

import software.amazon.awscdk.SecretValue;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.pipelines.CodeBuildOptions;
import software.amazon.awscdk.pipelines.CodePipeline;
import software.amazon.awscdk.services.codebuild.BuildEnvironment;
import software.amazon.awscdk.services.codebuild.BuildEnvironmentVariable;
import software.amazon.awscdk.services.codebuild.GitHubSourceProps;
import software.amazon.awscdk.services.codebuild.Project;
import software.amazon.awscdk.services.codebuild.Source;
import software.amazon.awscdk.services.codepipeline.Artifact;
import software.amazon.awscdk.services.codepipeline.IStage;
import software.amazon.awscdk.services.codepipeline.Pipeline;
import software.amazon.awscdk.services.codepipeline.StageOptions;
import software.amazon.awscdk.services.codepipeline.actions.CodeBuildAction;
import software.amazon.awscdk.services.codepipeline.actions.EcsDeployAction;
import software.amazon.awscdk.services.codepipeline.actions.GitHubSourceAction;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SecurityGroupProps;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.VpcLookupOptions;
import software.amazon.awscdk.services.events.targets.CodeBuildProject;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static software.amazon.awscdk.services.codebuild.LinuxBuildImage.AMAZON_LINUX_2_5;

public class PipelineStack extends Stack {

    private static final String GITHUB_TOKEN = "github-token";

    private static final String RECORDS_GIT = "RecordsSpringBootApplication";
    private static final String RECORDS_REPO = "records-producer";

    public PipelineStack(final Construct scope, final String id) {
        this(scope, id , null);
    }

    public PipelineStack(final Construct scope, final String id, final StackProps stackProps) {
        super(scope, id, stackProps);

        IVpc vpc = Vpc.fromLookup(this, id, VpcLookupOptions.builder()
                .vpcName("vpc/dev-vpc")
                .ownerAccountId(stackProps.getEnv().getAccount())
                .region(stackProps.getEnv().getRegion())
                .build());

        SecretValue secret = SecretValue.secretsManager(GITHUB_TOKEN);
//         CodePipeline
        Artifact sourceActionOutput = new Artifact();
        Artifact buildOutput = new Artifact();
        Pipeline pipeline = Pipeline.Builder.create(this, "MyPipeline").build();

//         Add source stage
        IStage sourceStage = pipeline.addStage(StageOptions.builder()
                .stageName("Source")
                .actions(List.of(
                        GitHubSourceAction.Builder.create()
                                .actionName("GitHub_Source")
                                .owner("ashwinicharles78")
                                .repo(RECORDS_GIT)
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
                                .service(ecsService.getService())
                                .input(buildOutput)
                                .runOrder(3)
                                .build()
                ))
                .build());
    }
}
