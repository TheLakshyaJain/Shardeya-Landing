package com.shardeya.builder.document;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * B-11 §6 VariablePalette groups (Builder · Project · Plot · Buyer ·
 * Payment · Dates) as the single source of truth for both
 * {@link TemplateRenderer} activation-time validation and the frontend's
 * click-to-insert palette (served via DocumentTemplateController so the two
 * can never drift apart). Adding a variable anywhere means adding it here
 * first, then making {@link DocumentGenerationService#buildContext} supply
 * it -- the allowlist is the contract, not a byproduct of what the context
 * builder happens to populate.
 */
final class DocumentVariableAllowlist {

    private DocumentVariableAllowlist() {
    }

    private static final Set<String> COMMON = Set.of(
            "org.name", "org.address", "org.phone", "org.logoUrl",
            "project.name", "project.address",
            "plot.number", "plot.areaSqft", "plot.areaValue", "plot.areaUnit", "plot.facing",
            "buyer.name", "buyer.mobile", "buyer.email",
            "document.number", "document.generatedDate"
    );

    private static final Set<String> ALLOTMENT_EXTRA = Set.of(
            "sale.dealValue", "sale.dealValueWords", "sale.purchaseDate", "sale.paymentType"
    );

    private static final Set<String> RECEIPT_EXTRA = Set.of(
            "payment.receiptNo", "payment.amount", "payment.amountWords", "payment.paidOn",
            "payment.mode", "payment.reference", "payment.totalPaid", "payment.balanceDue"
    );

    private static final Set<String> DEMAND_EXTRA = Set.of(
            "demand.totalOverdueAmount", "demand.totalOverdueAmountWords"
    );

    private static final Set<String> SCHEDULE_ROW_FIELDS = Set.of("label", "amount", "dueDate");
    private static final Set<String> OVERDUE_ROW_FIELDS = Set.of("label", "amount", "dueDate", "daysOverdue");

    static Set<String> topLevelVariables(DocumentTemplate.DocType docType) {
        Set<String> set = new LinkedHashSet<>(COMMON);
        switch (docType) {
            case ALLOTMENT_LETTER -> set.addAll(ALLOTMENT_EXTRA);
            case PAYMENT_RECEIPT -> set.addAll(RECEIPT_EXTRA);
            case DEMAND_LETTER -> set.addAll(DEMAND_EXTRA);
            case BOOKING_CONFIRMATION -> { /* not built this round -- see CLAUDE.md */ }
        }
        return set;
    }

    static Map<String, Set<String>> collections(DocumentTemplate.DocType docType) {
        Map<String, Set<String>> map = new LinkedHashMap<>();
        if (docType == DocumentTemplate.DocType.ALLOTMENT_LETTER) {
            map.put("schedule", SCHEDULE_ROW_FIELDS);
        }
        if (docType == DocumentTemplate.DocType.DEMAND_LETTER) {
            map.put("overdue", OVERDUE_ROW_FIELDS);
        }
        return map;
    }
}
