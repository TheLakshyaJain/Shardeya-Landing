package com.shardeya.builder.document;

import com.shardeya.foundation.auth.Organization;
import com.shardeya.foundation.auth.OrganizationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * B-11 §3: gapless per org, per doc type, per financial year -- mirrors
 * ReceiptService (B-05) exactly. Deliberately NOT used for PAYMENT_RECEIPT:
 * that doc type reuses payment_record's own already-gapless receipt_no
 * directly (DocumentGenerationService), rather than drawing a second,
 * independent number for the same receipt from this table.
 */
@Service
public class DocumentNumberService {

    private static final Pattern NON_ALNUM = Pattern.compile("[^A-Za-z0-9]");

    private final DocumentNumberSequenceRepository sequenceRepository;
    private final OrganizationRepository organizationRepository;

    public DocumentNumberService(DocumentNumberSequenceRepository sequenceRepository, OrganizationRepository organizationRepository) {
        this.sequenceRepository = sequenceRepository;
        this.organizationRepository = organizationRepository;
    }

    /** Must be called inside the same transaction as the generated_document insert it numbers. */
    public String nextDocumentNumber(UUID orgId, DocumentTemplate.DocType docType, LocalDate asOf) {
        String fy = financialYear(asOf);
        DocumentNumberSequence seq = sequenceRepository.findForUpdate(orgId, docType, fy)
                .orElseGet(() -> new DocumentNumberSequence(orgId, docType, fy));
        long value = seq.getNextValue();
        seq.setNextValue(value + 1);
        sequenceRepository.save(seq);

        return "%s/%s/%s/%d".formatted(orgPrefix(orgId), docTypePrefix(docType), fy, value);
    }

    private String docTypePrefix(DocumentTemplate.DocType docType) {
        return switch (docType) {
            case ALLOTMENT_LETTER -> "ALT";
            case PAYMENT_RECEIPT -> "RCP";
            case DEMAND_LETTER -> "DMD";
            case BOOKING_CONFIRMATION -> "BKG";
        };
    }

    private String financialYear(LocalDate date) {
        int startYear = date.getMonth().getValue() >= Month.APRIL.getValue() ? date.getYear() : date.getYear() - 1;
        int endYearShort = (startYear + 1) % 100;
        return "FY%02d-%02d".formatted(startYear % 100, endYearShort);
    }

    private String orgPrefix(UUID orgId) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalStateException("Organization not found: " + orgId));
        String alnum = NON_ALNUM.matcher(org.getName()).replaceAll("").toUpperCase();
        return alnum.length() >= 4 ? alnum.substring(0, 4) : (alnum + "XXXX").substring(0, 4);
    }
}
