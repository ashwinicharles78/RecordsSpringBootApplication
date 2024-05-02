package com.records.Records.service.impl;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.Body;
import com.amazonaws.services.simpleemail.model.Content;
import com.amazonaws.services.simpleemail.model.Destination;
import com.amazonaws.services.simpleemail.model.Message;
import com.amazonaws.services.simpleemail.model.SendEmailRequest;
import com.records.Records.model.KafkaUserData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.util.Objects;

@Service
public class MailService {

    @Autowired
    private Environment env;

    @Autowired
    private TemplateEngine templateEngine;

    // Replace sender@example.com with your "From" address.
    // This address must be verified with Amazon SES.
    static final String FROM = "mail@ashwinicharles.info";

    // Replace recipient@example.com with a "To" address. If your account
    // is still in the sandbox, this address must be verified.
    static final String TO = "ashwinicharles78@gmail.com";

    // The configuration set to use for this email. If you do not want to use a
    // configuration set, comment the following variable and the
    // .withConfigurationSetName(CONFIGSET); argument below.
    static final String CONFIGSET = "ConfigSet";

    // The subject line for the email.
    static final String SUBJECT = "Amazon SES test (AWS SDK for Java)";

    // The HTML body for the email.
    static final String HTMLBODY = "<h1>Customer Registration</h1>"
            + "<p>This email was sent with <a href='https://aws.amazon.com/ses/'>"
            + "Amazon SES</a> using the <a href='https://aws.amazon.com/sdk-for-java/'>"
            + "AWS SDK for Java</a>"
            + "<p>Customer with registration Id %s</p>"
            + "And Name - %s";

    // The email body for recipients with non-HTML email clients.
    static final String TEXTBODY = "This email was sent through Amazon SES "
            + "using the AWS SDK for Java.";
    private static final String SPACE = " ";

    public void sendEmail(KafkaUserData userData) throws IOException {
        BasicAWSCredentials awsCreds = new BasicAWSCredentials(
                Objects.requireNonNull(env.getProperty("aws.accessKeyId")),
                Objects.requireNonNull(env.getProperty("aws.administrativeKey")),
                "654654602872"
        );

        try {
            String processedString = "";
            AmazonSimpleEmailService client =
                    AmazonSimpleEmailServiceClientBuilder.standard()
                            .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
                            // Replace US_WEST_2 with the AWS Region you're using for
                            // Amazon SES.
                            .withRegion(Regions.US_EAST_1).build();
            if(!userData.getEmail().isEmpty()) {
                Context context = new Context();
            /*
            content is the variable defined in our HTML template within the div tag
            */
                context.setVariable("email", userData.getEmail());
                context.setVariable("name", userData.getFirstName()+SPACE+userData.getLastName());
                processedString = templateEngine.process("template", context);
            }
            SendEmailRequest request = new SendEmailRequest()
                    .withDestination(
                            new Destination().withToAddresses(TO))
                    .withMessage(new Message()
                            .withBody(new Body()
                                    .withHtml(new Content()
                                            .withCharset("UTF-8").withData(processedString))
                                    .withText(new Content()
                                            .withCharset("UTF-8").withData(TEXTBODY)))
                            .withSubject(new Content()
                                    .withCharset("UTF-8").withData(SUBJECT)))
                    .withSource(FROM);
            // Comment or remove the next line if you are not using a
            // configuration set
//                    .withConfigurationSetName(CONFIGSET);
            client.sendEmail(request);
            System.out.println("Email sent!");
        } catch (Exception ex) {
            System.out.println("The email was not sent. Error message: "
                    + ex.getMessage());
        }

    }
}
