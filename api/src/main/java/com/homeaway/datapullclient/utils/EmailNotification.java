package com.homeaway.datapullclient.utils;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Objects;
import java.util.Properties;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.Body;
import com.amazonaws.services.simpleemail.model.Content;
import com.amazonaws.services.simpleemail.model.Destination;
import com.amazonaws.services.simpleemail.model.SendEmailRequest;
import com.homeaway.datapullclient.config.DataPullClientConfig;
import com.homeaway.datapullclient.config.SMTPProperties;
import com.homeaway.datapullclient.config.SESProperties;
import com.homeaway.datapullclient.exception.ProcessingException;
import org.apache.commons.lang3.StringUtils;

public class EmailNotification {

    DataPullClientConfig config;
    String subject;
    String messageBody1;
    String messageBody2;
    Boolean clusterStatus = true;
    String htmlContent;

    public EmailNotification(DataPullClientConfig config) {
        this.config = config;
    }

    public void sendEmail(String emailStatusCodeVal, String to, String jobFlowId, String taskId, String stackTrace,String ClusterId) throws ProcessingException {

        final SMTPProperties smtpProperties = config.getSMTPProperties();
        final SESProperties sesProperties = config.getSESProperties();

            if (StringUtils.equalsIgnoreCase(emailStatusCodeVal, "CLUSTER_CREATION_SUCCESS")) {
                subject = " Cluster created successfully for datapull pipeline \"" + taskId +"\"";
                messageBody1 = "Cluster ID for the datapull task \""  + taskId + "\" is \"" + jobFlowId + "\". Please monitor the EMR console for more details.";
                clusterStatus = true;
            } else if (StringUtils.equalsIgnoreCase(emailStatusCodeVal, "SPARK_EXEC_ON_EXISTING_CLUSTER")) {
                subject = "Datapull pipeline \"" + taskId + "\" successfully submitted to an existing cluster ";
                messageBody1 = "Cluster ID for the datapull job \"" + taskId + "\" is \"" + ClusterId + "\". Please monitor the EMR console for more details.";
                clusterStatus = true;
        } else {
            if (StringUtils.equalsIgnoreCase(emailStatusCodeVal, "CLUSTER_CREATION_FAILED")) {
                subject = "Cluster creation failed for datapull pipeline \"" + taskId +"\"";
                messageBody1 = "Datapull Cluster creation failed with below error.";
            } else if (StringUtils.equalsIgnoreCase(emailStatusCodeVal, "SPARK_EXEC_FAILED")) {
                subject = "Spark job execution failed for datapull pipeline \"" + taskId +"\"";
                messageBody1 = "Spark job execution failed with below error. ";
            } else if (StringUtils.equalsIgnoreCase(emailStatusCodeVal, "INVALID_PARAMS")) {
                subject = "Spark job execution failed for datapull pipeline \"" + taskId +"\"";
                messageBody1 = "Spark job execution due to invalid configs. ";
            } else if (StringUtils.equalsIgnoreCase(emailStatusCodeVal, "RUN_ON_EXISTING_CLUSTER_FAILED")) {
                subject = "Unable to run the datapull pipeline \"" + taskId + "\" on cluster \"" + jobFlowId+"\"";
                messageBody1 = "Unable to run the pipeline " + taskId;
            }
            messageBody2 = stackTrace;
            clusterStatus = false;
        }

        if (clusterStatus) {
            htmlContent = "<html><body><table border='1'><tr><th style=\"background-color: green; color: white;\"> Message</th></tr><tr><td><pre>" + messageBody1 + "</pre></td></tr></table></body></html>";
        } else {
            htmlContent = "<html><body><h2>" + messageBody1 + "</h2><table border='1'><tr><th style=\"background-color: orange; color: white;\">Error Message</th></tr><tr><td><pre>" + messageBody2 + "</pre></td></tr></table></body></html>";
        }

        if (StringUtils.isNotEmpty(smtpProperties.getEmailaddress()) && StringUtils.isNotEmpty(smtpProperties.getSmtpserveraddress())) {
            sendEmailViaSMTP(smtpProperties, to, subject, htmlContent);
        } else if (sesProperties.getEmail() != null && sesProperties.getSecretKey() != null && sesProperties.getAccessKey() != null) {
            sendEmailViaSES(sesProperties, to, subject, htmlContent);
            System.out.println("Test SES configs");
        } else {
            throw new ProcessingException("SMTP or SES properties are not configured properly");
        }
    }

    private void sendEmailViaSMTP(SMTPProperties smtpProperties, String to, String subject, String htmlContent) {

        Properties properties = new Properties();
        String from = smtpProperties.getEmailaddress().toString();
        properties.put("mail.smtp.host", smtpProperties.getSmtpserveraddress());
        properties.put("mail.smtp.port",  StringUtils.isNotEmpty(smtpProperties.getSmtpport()) ? smtpProperties.getSmtpport() : "587");
        properties.put("mail.smtp.starttls.enable",  StringUtils.isNotEmpty(smtpProperties.getSmtpstarttls()) ? smtpProperties.getSmtpstarttls() : "true");
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.ssl.protocols", "TLSv1.2");
        properties.put("mail.smtp.ssl.trust", "*");

        String password = "";

        Session session = Session.getInstance(properties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(from, password);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            message.setContent(htmlContent, "text/html");

            Transport.send(message);
            System.out.println("Email sent successfully!");
        } catch (MessagingException e) {
            e.printStackTrace();
            System.err.println("Error sending email: " + e.getMessage());
        }
    }

    private void sendEmailViaSES(SESProperties sesProperties, String to, String subject, String htmlContent) throws ProcessingException {
        BasicAWSCredentials awsCredentials = new BasicAWSCredentials(sesProperties.getAccessKey(), sesProperties.getSecretKey());
        AmazonSimpleEmailServiceClientBuilder builder = AmazonSimpleEmailServiceClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
                .withRegion(sesProperties.getRegion());
        AmazonSimpleEmailService client = builder.build();
        SendEmailRequest request = new SendEmailRequest()
                .withDestination(new Destination().withToAddresses(to))
                .withMessage(new com.amazonaws.services.simpleemail.model.Message()
                        .withBody(new Body()
                                .withHtml(new Content().withCharset("UTF-8").withData(htmlContent)))
                        .withSubject(new Content().withCharset("UTF-8").withData(subject)))
                .withSource(sesProperties.getEmail());
        try {
            client.sendEmail(request);
            System.out.println("Email sent successfully via SES!");
        } catch (AmazonServiceException e) {
            e.printStackTrace();
            System.err.println("Error sending email via SES: " + e.getMessage());
            throw new ProcessingException("Error sending email via SES", e);
        }
    }
}