package com.shardeya.foundation.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Calls infra/whatsapp-stub's POST /v1/messages, which mimics the real
 * WhatsApp Business Cloud API's send-message response shape (see that
 * service's own server.js comment) -- unlike StubSmsGateway (which just logs
 * to a file), this genuinely round-trips over HTTP so a real dispatch
 * failure (stub down, bad payload) surfaces the same way a real provider
 * outage would, and GET /v1/messages on the stub gives a MailHog-equivalent
 * inspection point for verification.
 */
@Component
@ConditionalOnProperty(prefix = "shardeya.whatsapp", name = "provider", havingValue = "stub", matchIfMissing = true)
public class StubWhatsAppGateway implements WhatsAppGateway {

    private static final Logger log = LoggerFactory.getLogger(StubWhatsAppGateway.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl;

    public StubWhatsAppGateway(@Value("${shardeya.whatsapp.stub.url:http://localhost:4001}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    @SuppressWarnings("unchecked")
    public SendResult sendText(String mobile, String text, String purpose) {
        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", mobile,
                "type", "text",
                "text", Map.of("body", text));
        try {
            var response = restTemplate.postForObject(baseUrl + "/v1/messages", body, Map.class);
            log.info("[WHATSAPP STUB] purpose={} mobile={} response={}", purpose, mobile, response);
            String messageId = null;
            if (response != null && response.get("messages") instanceof java.util.List<?> messages && !messages.isEmpty()
                    && messages.get(0) instanceof Map<?, ?> first) {
                messageId = (String) first.get("id");
            }
            return new SendResult(messageId);
        } catch (Exception e) {
            log.warn("WhatsApp stub send failed for mobile={} purpose={}", mobile, purpose, e);
            throw e;
        }
    }
}
