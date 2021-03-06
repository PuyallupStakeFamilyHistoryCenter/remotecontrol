/*
 * Copyright (c) 2015, tibbitts
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.puyallupfamilyhistorycenter.service.utils;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient;
import com.amazonaws.services.simpleemail.model.Body;
import com.amazonaws.services.simpleemail.model.Content;
import com.amazonaws.services.simpleemail.model.Destination;
import com.amazonaws.services.simpleemail.model.Message;
import com.amazonaws.services.simpleemail.model.RawMessage;
import com.amazonaws.services.simpleemail.model.SendEmailRequest;
import com.amazonaws.services.simpleemail.model.SendRawEmailRequest;
import com.google.common.base.Verify;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.Address;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import org.apache.log4j.Logger;
import org.puyallupfamilyhistorycenter.service.ApplicationProperties;
import org.puyallupfamilyhistorycenter.service.models.PersonTemple;

/**
 *
 * @author tibbitts
 */
public class EmailUtils {
    private static final Logger logger = Logger.getLogger(EmailUtils.class);
    private static final AmazonSimpleEmailService ses;
    private static final Set<String> emailWhitelist;
    static {
        ClientConfiguration config = new ClientConfiguration();
        //config.set
        ses = new AmazonSimpleEmailServiceClient(new BasicAWSCredentials(ApplicationProperties.getEmailAWSAccessKey(), ApplicationProperties.getEmailAWSSecretKey()), config);
        ses.setRegion(Region.getRegion(Regions.US_WEST_2));
        
        emailWhitelist = ApplicationProperties.getEmailWhitelist();
    }
    
    public static void sendReferralEmail(String contactName, String contactEmail, String patronName, String patronEmail, String patronPhone, String patronWard, List<String> interests, String numAdults, String numChildren) {
        String emailBody = buildReferralEmailBody(contactName, patronName, patronName, patronEmail, patronPhone, patronWard, LocalDate.now(), interests, numAdults, numChildren);
        String subject = "Family history consultant referral for " + patronName;
        
        sendEmail(contactName, contactEmail, new String[] { "normanse@gmail.com" }, subject, emailBody);
    }
    
    public static void sendFinalEmail(String userName, String userEmail, Collection<String> imageFiles, Iterable<String> attachmentUrls) {
        String emailBody = buildFinalEmailBody(userName, null, attachmentUrls);
        String subject = ApplicationProperties.getEmailSubject();
        
        sendEmailWithAttachments(userName, userEmail, new String[] {}, subject, emailBody, imageFiles);

        logger.info("Sent final email to " + userEmail);
    }

    protected static void sendEmail(String recipientName, String recipientEmail, String[] ccList, String subjectString, String bodyString) {
        Content subject = new Content(subjectString);
        Body body = new Body();
        
        Content bodyContent = new Content(bodyString);
        body.setHtml(bodyContent);
        Message message = new Message(subject, body);
        
        SendEmailRequest request = new SendEmailRequest();
        request.setMessage(message);
        request.setSource("admin@puyallupfamilyhistorycenter.org");
        request.setDestination(new Destination(Arrays.asList("\"" + recipientName + "\" <" + recipientEmail + ">")).withCcAddresses(Arrays.asList(ccList)));
        ses.sendEmail(request);
    }
    
    protected static void sendEmailWithAttachments(String recipientName, String recipientEmail, String[] ccList, String subjectString, String bodyString, Collection<String> attachments) {
        try {
            Verify.verifyNotNull(recipientEmail, "Null email address");
            
            Session session = Session.getDefaultInstance(new Properties());
            MimeMessage message = new MimeMessage(session);
            message.setSubject(subjectString, "UTF-8");

            message.setFrom(new InternetAddress("admin@puyallupfamilyhistorycenter.org"));
            message.setReplyTo(new Address[]{new InternetAddress("admin@puyallupfamilyhistorycenter.org")});
            message.setRecipients(javax.mail.Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
            //TODO: Handle CC?

            // Cover wrap
            MimeBodyPart wrap = new MimeBodyPart();

            // Alternative TEXT/HTML content
            MimeMultipart cover = new MimeMultipart("alternative");
            MimeBodyPart html = new MimeBodyPart();
            cover.addBodyPart(html);

            wrap.setContent(cover);

            MimeMultipart content = new MimeMultipart("related");
            message.setContent(content);
            content.addBodyPart(wrap);

            // This is just for testing HTML embedding of different type of attachments.
            StringBuilder sb = new StringBuilder();

            if (attachments != null) {
                for (String attachmentFileName : attachments) {
                    String id = UUID.randomUUID().toString();
                    sb.append("<img src=\"cid:");
                    sb.append(id);
                    sb.append("\" alt=\"ATTACHMENT\"/>\n");

                    MimeBodyPart attachment = new MimeBodyPart();

                    DataSource fds = new FileDataSource(attachmentFileName);
                    attachment.setDataHandler(new DataHandler(fds));
                    attachment.setHeader("Content-ID", "<" + id + ">");
                    attachment.setFileName(fds.getName());

                    content.addBodyPart(attachment);
                }
            }

            html.setContent("<html><body><img style=\"width:100%\" src=\"http://www.puyallupfamilyhistorycenter.org/uploads/4/8/2/9/4829765/1433113473.png?\" alt=\"The Puyllup Family History Center\" />" + bodyString + "</body></html>", "text/html");

            // Send the email.
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            message.writeTo(outputStream);
            RawMessage rawMessage = new RawMessage(ByteBuffer.wrap(outputStream.toByteArray()));

            SendRawEmailRequest rawEmailRequest = new SendRawEmailRequest(rawMessage);
            ses.sendRawEmail(rawEmailRequest);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to send email to " + recipientEmail, ex);
        }
    }
    
    protected static final DateTimeFormatter dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG);
    protected static final List<String> defaultInterest = Arrays.asList("family history");
    
    static String buildReferralEmailBody(String contactName, String patronFullName, String patronShortName, String patronEmail, String patronPhone, String patronWard, LocalDate visitDate, List<String> interests, String numAdults, String numChildren) {
        String uuid = UUID.nameUUIDFromBytes(patronEmail.getBytes()).toString();
        StringBuilder builder = new StringBuilder("<html><head></head><body>");
        builder.append("<img style=\"width:100%\" src=\"http://www.puyallupfamilyhistorycenter.org/uploads/4/8/2/9/4829765/1433113473.png?").append(uuid).append("\" alt=\"The Puyllup Family History Center\" />");
        builder.append("<h3>Dear ").append(contactName).append(",</h3>");
        builder.append("<p><strong>").append(patronFullName).append("</strong> from the ").append(patronWard)
                .append(" visited the Discovery room at the Puyallup Stake Family History Center on ")
                .append(dateFormat.format(visitDate)).append(" with a party of ")
                .append(numAdults).append(" adults and ")
                .append(numChildren).append(" children.</p>");
        if (interests == null || interests.isEmpty()) {
            interests = defaultInterest;
        }
        builder.append("<p>").append(patronShortName).append(" expressed interest in learning more about");
        for (int i = 0; i < interests.size(); i++) {
            if (i == interests.size() - 1 && interests.size() > 1) {
                builder.append(" and");
            } else if (i != 0 && interests.size() > 2) {
                builder.append(",");
            }
            builder.append(" <strong>").append(interests.get(i)).append("</strong>");
        }
        builder.append(".</p>");
        builder.append("<p>Please schedule a time for them to meet with a family history consultant ")
                .append("so they can learn more about how they can be involved in family history work.</p>");
        builder.append("<dt>Email:</dt><dd><a href=\"mailto:").append(patronEmail).append("\">").append(patronEmail).append("</a></dd></dl>");
        builder.append("<dt>Phone:</dt><dd><a href=\"tel:").append(patronPhone).append("\">").append(patronPhone).append("</a></dd></dl>");
        builder.append("<p>Thank you for your assistance; we appreciate it.</p>");
        builder.append("<p>The staff at the Puyallup Stake Family History Center</p>");
        builder.append("</body></html>");
        
        return builder.toString();
    }
    
    protected static String buildFinalEmailBody(String personName, Iterable<PersonTemple> prospects, Iterable<String> attachmentUrls) {
        StringBuilder builder = new StringBuilder();
        //TODO: Make this configurable
        builder.append("<p>")
                .append(ApplicationProperties.getEmailSalutation().replaceAll("\n", "</p><p>").replaceAll("\\$\\{personName\\}", personName))
                .append("</p>")
                .append("<p>")
                .append(ApplicationProperties.getEmailBody().replaceAll("\n", "</p><p>"))
                .append("</p>");
        
        if (prospects != null) {
            boolean firstProspect = true;
            
            for (PersonTemple prospect : prospects) {
                if (firstProspect) {
                    builder.append("<p>")
                            .append(ApplicationProperties.getEmailProspectsExplanation().replaceAll("\n", "</p><p>"))
                            .append("</p><ul>");
                    firstProspect = false;
                }
                builder.append("<li><a href='https://familysearch.org/tree/#view=ancestor&person=")
                        .append(prospect.id)
                        .append("'>")
                        .append(prospect.name)
                        .append("</a></li>");
            }
            
            if (!firstProspect) {
                builder.append("</ul>");
            }
        }
        
        builder.append("<p>")
                .append(ApplicationProperties.getEmailSignature().replaceAll("\n", "</p><p>"))
                .append("</p>");
        
        attachmentUrls.spliterator().forEachRemaining(url -> {
            builder.append("<a href='").append(url).append("'><img style='padding: 5px; max-width: 200px' alt='Attachment' src='").append(url).append("' /></a>");
        });
        
        return builder.toString();
    }
}
