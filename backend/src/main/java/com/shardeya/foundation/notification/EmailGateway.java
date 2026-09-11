package com.shardeya.foundation.notification;

/**
 * Unlike WhatsApp/SMS, email doesn't need a provider-specific adapter class
 * at all to be "real-provider-ready": SMTP is already the wire protocol
 * every mainstream provider (AWS SES, SendGrid, Postmark) exposes, so
 * swapping MailHog for a real one is a {@code spring.mail.*} config change,
 * never a code change. This interface exists purely so OutboxPoller depends
 * on an abstraction (matching WhatsAppGateway/SmsGateway's shape) rather
 * than constructing a {@code SimpleMailMessage} inline -- the actual
 * "provider swap" lever is application.yml, not a {@code provider} property
 * here.
 */
public interface EmailGateway {

    void send(String to, String subject, String body);
}
