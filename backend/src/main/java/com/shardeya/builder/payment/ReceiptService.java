package com.shardeya.builder.payment;

import com.shardeya.foundation.auth.Organization;
import com.shardeya.foundation.auth.OrganizationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * B-05 §3: gapless receipt numbering, {@code {ORG_PREFIX}/{FY}/{seq}}. The
 * PDF itself is a stub this milestone -- 05-MILESTONES.md M3: "PDF
 * generation stub — full PDF in M5" -- so this only ever produces the
 * number, never a rendered document.
 */
@Service
public class ReceiptService {

    private static final Pattern NON_ALNUM = Pattern.compile("[^A-Za-z0-9]");

    private final ReceiptSequenceRepository sequenceRepository;
    private final OrganizationRepository organizationRepository;

    public ReceiptService(ReceiptSequenceRepository sequenceRepository, OrganizationRepository organizationRepository) {
        this.sequenceRepository = sequenceRepository;
        this.organizationRepository = organizationRepository;
    }

    /**
     * Must be called inside the same transaction as the payment_record
     * insert it numbers -- the PESSIMISTIC_WRITE lock this acquires is only
     * held for the duration of that transaction, which is what makes the
     * sequence gapless under concurrent payments.
     */
    public String nextReceiptNumber(UUID orgId, LocalDate paidOn) {
        // FY from paid_on, never now() -- B-05 §10 explicitly calls out
        // "financial-year rollover mid-transaction" as an edge case this
        // must get right; the receipt's own date is what determines its FY,
        // not whenever the request happens to be processed.
        String fy = financialYear(paidOn);
        ReceiptSequence seq = sequenceRepository.findForUpdate(orgId, fy)
                .orElseGet(() -> new ReceiptSequence(orgId, fy));
        long value = seq.getNextValue();
        seq.setNextValue(value + 1);
        sequenceRepository.save(seq);

        return "%s/%s/%d".formatted(orgPrefix(orgId), fy, value);
    }

    // Indian financial year: April 1 - March 31, expressed as "FY24-25" for
    // a date between 2024-04-01 and 2025-03-31.
    private String financialYear(LocalDate date) {
        int startYear = date.getMonth().getValue() >= Month.APRIL.getValue() ? date.getYear() : date.getYear() - 1;
        int endYearShort = (startYear + 1) % 100;
        return "FY%02d-%02d".formatted(startYear % 100, endYearShort);
    }

    // No dedicated org "prefix" column exists -- derived deterministically
    // from the org name (first 4 uppercase alphanumeric characters, padded
    // with 'X' if the name is short) rather than adding a new field just for
    // this. Stable for a given org since the name rarely changes after signup.
    private String orgPrefix(UUID orgId) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalStateException("Organization not found: " + orgId));
        String alnum = NON_ALNUM.matcher(org.getName()).replaceAll("").toUpperCase();
        String prefix = alnum.length() >= 4 ? alnum.substring(0, 4) : (alnum + "XXXX").substring(0, 4);
        return prefix;
    }
}
