package com.shardeya.foundation.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Moved out of OutboxPoller (M-06 second half) so email gets the same
 * real-gateway shape WhatsApp/SMS already had. {@code shardeya.mail.from}
 * defaults to the MailHog-era placeholder -- a real provider (GoDaddy
 * Workspace Email, SES, SendGrid, ...) typically requires the From address
 * to match the authenticated mailbox/domain, so this needs to be set to a
 * real address alongside {@code spring.mail.username/password} once a real
 * SMTP_HOST is configured.
 */
@Component
public class SmtpEmailGateway implements EmailGateway {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailGateway(JavaMailSender mailSender,
                             @Value("${shardeya.mail.from:noreply@shardeya.local}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
