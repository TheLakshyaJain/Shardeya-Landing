package com.shardeya.foundation.notification;

/**
 * Provider-agnostic (same reasoning as SmsGateway's own javadoc):
 * {@code shardeya.whatsapp.provider} selects the implementation bean.
 * {@code stub} (default, talks to infra/whatsapp-stub) and {@code meta}
 * (real Meta Cloud API, see MetaCloudApiWhatsAppGateway's own javadoc for
 * exactly what real credentials this needs) both exist -- swapping from
 * one to the other in a real deployment is a single property flip, never
 * a code change.
 */
public interface WhatsAppGateway {

    SendResult sendText(String mobile, String text, String purpose);

    /** {@code providerMessageId} is null when the provider genuinely gives nothing back to record. */
    record SendResult(String providerMessageId) {
    }
}
