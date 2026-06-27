package com.fooddelivery.notification.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.*;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class AwsSesEmailService {

    private SesV2Client sesV2Client;

    @Value("${aws.ses.configuration-set-name:}")
    private String configurationSetName;

    @PostConstruct
    public void initializeClient() {
        // Initializes using the DefaultCredentialsProvider chain (e.g., IAM roles, Env Vars)
        this.sesV2Client = SesV2Client.builder()
                .region(Region.AP_SOUTH_1) // Region matching the core infrastructure
                .build();
    }

    @PreDestroy
    public void cleanup() {
        if (sesV2Client != null) {
            sesV2Client.close();
        }
    }

    public String sendHtmlEmail(String senderAddress, String recipientAddress, String subject, String htmlBody) {
        Destination destination = Destination.builder()
                .toAddresses(recipientAddress)
                .build();

        Message message = Message.builder()
                .subject(Content.builder().data(subject).build())
                .body(Body.builder().html(Content.builder().data(htmlBody).build()).build())
                .build();

        EmailContent emailContent = EmailContent.builder()
                .simple(message)
                .build();

        SendEmailRequest.Builder emailRequestBuilder = SendEmailRequest.builder()
                .fromEmailAddress(senderAddress)
                .destination(destination)
                .content(emailContent);
                
        if (configurationSetName != null && !configurationSetName.isEmpty()) {
            emailRequestBuilder.configurationSetName(configurationSetName); // Required for tracking bounces/complaints
        }

        try {
            SendEmailResponse response = sesV2Client.sendEmail(emailRequestBuilder.build());
            return response.messageId();
        } catch (SesV2Exception e) {
            if (e.statusCode() == 400 || e.statusCode() == 404) {
                throw new com.fooddelivery.notification.exception.RecipientUnreachableException("AWS SES Client Error (400/404): " + e.awsErrorDetails().errorMessage());
            } else if (e.statusCode() >= 400 && e.statusCode() < 500) {
                throw new com.fooddelivery.notification.exception.InvalidPayloadException("AWS SES Client Error (4xx): " + e.awsErrorDetails().errorMessage());
            } else if (e.statusCode() == 504 || e.statusCode() == 503) {
                throw new com.fooddelivery.notification.exception.ProviderGatewayTimeoutException("AWS SES Gateway Timeout: " + e.awsErrorDetails().errorMessage());
            }
            throw new RuntimeException("AWS SES exception: " + e.awsErrorDetails().errorMessage());
        }
    }
}
