package com.shardeya.foundation.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Real WhatsApp Business Platform (Meta Cloud API) adapter. Chosen over a
 * BSP (Gupshup/Interakt) per 02-FOUNDATION-MODULES.md M-06 §2's own listed
 * options -- direct-to-Meta avoids a second vendor relationship/markup on
 * top of Meta's own per-conversation pricing, and Meta's Cloud API is free
 * to integrate against (no BSP platform fee), which matters more than a
 * BSP's nicer dashboard at this stage. See CLAUDE.md for the full reasoning.
 *
 * <p><b>Not exercised by anything in this codebase or its tests</b> -- there
 * is no real WhatsApp Business Account in this environment to send against.
 * Written to the real, documented Cloud API request/response shape (Graph
 * API {@code POST /{phone-number-id}/messages}, Bearer token auth) so that
 * activating it later really is the one-property flip
 * {@code shardeya.whatsapp.provider=meta} promises, not "write this class
 * first" -- but it has never made a live call and must be smoke-tested
 * against a real account before being trusted in production.
 *
 * <p>To actually go live, you need, from Meta's WhatsApp Business Platform
 * (business.facebook.com &gt; WhatsApp Manager):
 * <ol>
 *   <li>A verified WhatsApp Business Account (WABA) and a registered phone
 *       number (or the free test number Meta issues during setup).</li>
 *   <li>{@code shardeya.whatsapp.meta.phone-number-id} -- the numeric Phone
 *       Number ID shown in WhatsApp Manager for that number.</li>
 *   <li>{@code shardeya.whatsapp.meta.access-token} -- a permanent System
 *       User access token (Business Settings &gt; System Users), not a
 *       24-hour temporary token from the quickstart page.</li>
 *   <li>Every template used for §22.4 buyer messages (payment received,
 *       instalment due, booking confirmation) submitted through WhatsApp
 *       Manager and approved by Meta -- template messages are the ONLY
 *       thing that can be sent outside a customer-initiated 24-hour
 *       session window, which every §22.4 send is by definition (the
 *       builder is initiating, not replying).</li>
 *   <li>A webhook endpoint (not built here -- see CLAUDE.md's "not built"
 *       list) subscribed to delivery-status callbacks, needed to update
 *       {@code message_delivery.status} past QUEUED/SENT.</li>
 * </ol>
 */
@Component
@ConditionalOnProperty(prefix = "shardeya.whatsapp", name = "provider", havingValue = "meta")
public class MetaCloudApiWhatsAppGateway implements WhatsAppGateway {

    private static final Logger log = LoggerFactory.getLogger(MetaCloudApiWhatsAppGateway.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final String phoneNumberId;
    private final String accessToken;
    private final String apiVersion;

    public MetaCloudApiWhatsAppGateway(
            @Value("${shardeya.whatsapp.meta.phone-number-id:}") String phoneNumberId,
            @Value("${shardeya.whatsapp.meta.access-token:}") String accessToken,
            @Value("${shardeya.whatsapp.meta.api-version:v19.0}") String apiVersion) {
        this.phoneNumberId = phoneNumberId;
        this.accessToken = accessToken;
        this.apiVersion = apiVersion;
        if (phoneNumberId.isBlank() || accessToken.isBlank()) {
            log.warn("shardeya.whatsapp.provider=meta but phone-number-id/access-token are not configured -- "
                    + "every send will fail until both are set. See MetaCloudApiWhatsAppGateway's own javadoc.");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public SendResult sendText(String mobile, String text, String purpose) {
        String url = "https://graph.facebook.com/%s/%s/messages".formatted(apiVersion, phoneNumberId);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", mobile,
                "type", "text",
                "text", Map.of("body", text));
        try {
            var response = restTemplate.postForObject(url, new HttpEntity<>(body, headers), Map.class);
            String messageId = null;
            if (response != null && response.get("messages") instanceof java.util.List<?> messages && !messages.isEmpty()
                    && messages.get(0) instanceof Map<?, ?> first) {
                messageId = (String) first.get("id");
            }
            log.info("[WHATSAPP META] purpose={} mobile={} messageId={}", purpose, mobile, messageId);
            return new SendResult(messageId);
        } catch (Exception e) {
            log.warn("WhatsApp Meta Cloud API send failed for mobile={} purpose={}", mobile, purpose, e);
            throw e;
        }
    }
}
