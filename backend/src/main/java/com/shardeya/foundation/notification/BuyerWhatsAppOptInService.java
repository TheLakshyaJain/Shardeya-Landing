package com.shardeya.foundation.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Buyer-facing WhatsApp consent (§22.4) -- deliberately a SEPARATE service
 * from {@link WhatsAppOptInService}, whose own javadoc explicitly scopes
 * itself to "the CURRENT USER'S OWN registered mobile" and rejects an
 * arbitrary caller-supplied number on purpose. A buyer is not a user at
 * all (no login, no OTP flow reachable for them), so consent is captured
 * differently: the builder ticks a checkbox on the buyer's behalf in the
 * sale wizard (or toggles it later from the sale detail page), and that
 * builder attestation -- not an OTP round-trip -- is what creates the
 * {@code whatsapp_optin} row, with {@code source='BUILDER_CAPTURED'} and
 * {@code capturedBy} recording which staff/admin user did it. Both
 * services write the exact same table (one row per (org, mobile), per
 * V7_016's own design), so a mobile number that happens to belong to both
 * a staff member (self-service) AND appears as a buyer's number on some
 * sale would share one row -- an accepted, documented edge case, not a
 * bug, matching V7_016's own "one row per (org, mobile)" contract.
 */
@Service
public class BuyerWhatsAppOptInService {

    public static final String SOURCE_BUILDER_CAPTURED = "BUILDER_CAPTURED";

    private final WhatsAppOptinRepository repository;

    public BuyerWhatsAppOptInService(WhatsAppOptinRepository repository) {
        this.repository = repository;
    }

    /**
     * Sets the buyer's current consent state. {@code optedIn=false} with no
     * existing row is a no-op (a buyer who was never asked isn't "opted
     * out," they're simply not opted in -- {@link #isOptedIn} already
     * returns {@code false} for that case without a row existing at all).
     */
    @Transactional
    public void setOptIn(UUID orgId, String mobile, boolean optedIn, UUID capturedByUserId) {
        if (mobile == null || mobile.isBlank()) {
            return;
        }
        WhatsAppOptin optin = repository.findByOrgIdAndMobile(orgId, mobile).orElse(null);
        if (optedIn) {
            if (optin == null) {
                repository.save(new WhatsAppOptin(UUID.randomUUID(), orgId, mobile, SOURCE_BUILDER_CAPTURED, capturedByUserId));
            } else {
                optin.reOptIn();
                optin.recordCapturedBy(capturedByUserId);
                repository.save(optin);
            }
        } else if (optin != null) {
            optin.optOut();
            repository.save(optin);
        }
    }

    @Transactional(readOnly = true)
    public boolean isOptedIn(UUID orgId, String mobile) {
        if (mobile == null || mobile.isBlank()) {
            return false;
        }
        return repository.findByOrgIdAndMobile(orgId, mobile).map(WhatsAppOptin::isActive).orElse(false);
    }
}
