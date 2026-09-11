package com.shardeya.builder.document;

import java.util.List;

/**
 * B-11 §7 entitlement table: Free = none; Pro = Basic (receipts + allotment
 * letters, system templates only, no customisation); Premium = Full (all
 * types + custom templates + bulk generation). Centralised here since both
 * {@link DocumentTemplateService} (customisation gate) and
 * {@link DocumentGenerationService} (per-doc-type + bulk gates) need the
 * identical tier ordering and minimums.
 */
final class LegalDocsTier {

    static final String KEY = "LEGAL_DOCS";
    static final List<String> ORDERED = List.of("NONE", "BASIC", "FULL");

    private LegalDocsTier() {
    }

    static String minimumFor(DocumentTemplate.DocType docType) {
        return switch (docType) {
            case ALLOTMENT_LETTER, PAYMENT_RECEIPT -> "BASIC";
            case DEMAND_LETTER, BOOKING_CONFIRMATION -> "FULL";
        };
    }
}
