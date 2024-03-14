package com.myorg;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

public class CdkAppApp {
    public static void main(final String[] args) {
        App app = new App();

        Environment env = Environment.builder()
                .account("654654602872")
                .region("us-east-1")
                .build();

        new TestAppStack(app, "dev", StackProps.builder()
                .env(env).build());
        new TestAppStack(app, "test",StackProps.builder()
                .env(env).build());

        app.synth();
    }
}

