package com.shardeya.foundation.auth;

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
 * Real SMS adapter, MSG91 (per 05-MILESTONES.md Milestone 7's own named
 * choice -- the standard India-DLT-compliant provider most Indian SaaS
 * products already integrate). {@code shardeya.sms.provider=msg91} selects
 * this over the default {@code stub}.
 *
 * <p><b>Not exercised by anything in this codebase or its tests</b> -- same
 * caveat as {@link com.shardeya.foundation.notification.MetaCloudApiWhatsAppGateway}:
 * written to MSG91's documented Flow API shape but never smoke-tested
 * against a real account.
 *
 * <p>To go live, you need, from MSG91 (control.msg91.com):
 * <ol>
 *   <li>An MSG91 account + {@code shardeya.sms.msg91.auth-key} (Settings &gt;
 *       API keys).</li>
 *   <li><b>DLT registration is mandatory and is a manual, offline step this
 *       code cannot do for you</b> -- India's TRAI regulations require every
 *       SMS sender ID and every message template to be pre-registered on a
 *       DLT (Distributed Ledger Technology) platform (MSG91 provides one,
 *       or use your existing telecom-operator DLT registration) before a
 *       single message can send; an unregistered template is silently
 *       dropped by the carrier, not rejected by MSG91's API, which makes
 *       this easy to misdiagnose as a code bug if skipped. Budget real
 *       calendar time (registration review can take days) before this
 *       adapter can send anything to a real Indian phone number.</li>
 *   <li>{@code shardeya.sms.msg91.sender-id} (your registered 6-character
 *       DLT sender ID) and {@code shardeya.sms.msg91.template-id} per
 *       message template actually used -- this class currently sends the
 *       OTP template ID for {@link #sendOtp} and a generic transactional
 *       template ID for {@link #sendText}; add more template IDs here as
 *       more DLT-approved templates exist.</li>
 * </ol>
 */
@Component
@ConditionalOnProperty(prefix = "shardeya.sms", name = "provider", havingValue = "msg91")
public class Msg91SmsGateway implements SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(Msg91SmsGateway.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final String authKey;
    private final String senderId;
    private final String otpTemplateId;
    private final String textTemplateId;

    public Msg91SmsGateway(
            @Value("${shardeya.sms.msg91.auth-key:}") String authKey,
            @Value("${shardeya.sms.msg91.sender-id:}") String senderId,
            @Value("${shardeya.sms.msg91.otp-template-id:}") String otpTemplateId,
            @Value("${shardeya.sms.msg91.text-template-id:}") String textTemplateId) {
        this.authKey = authKey;
        this.senderId = senderId;
        this.otpTemplateId = otpTemplateId;
        this.textTemplateId = textTemplateId;
        if (authKey.isBlank() || senderId.isBlank()) {
            log.warn("shardeya.sms.provider=msg91 but auth-key/sender-id are not configured, and DLT template "
                    + "registration must already be complete -- every send will fail until all three are done. "
                    + "See Msg91SmsGateway's own javadoc.");
        }
    }

    @Override
    public void sendOtp(String mobile, String code, String purpose) {
        send(mobile, otpTemplateId, Map.of("var", code), purpose);
    }

    @Override
    public void sendText(String mobile, String text, String purpose) {
        send(mobile, textTemplateId, Map.of("var", text), purpose);
    }

    private void send(String mobile, String templateId, Map<String, String> vars, String purpose) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("authkey", authKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of(
                "template_id", templateId,
                "sender", senderId,
                "recipients", java.util.List.of(Map.of("mobiles", "91" + mobile, "var", vars.getOrDefault("var", ""))));
        try {
            var response = restTemplate.postForObject("https://control.msg91.com/api/v5/flow", new HttpEntity<>(body, headers), Map.class);
            log.info("[SMS MSG91] purpose={} mobile={} response={}", purpose, mobile, response);
        } catch (Exception e) {
            log.warn("MSG91 SMS send failed for mobile={} purpose={}", mobile, purpose, e);
            throw e;
        }
    }
}
