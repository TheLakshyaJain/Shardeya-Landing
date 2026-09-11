package com.shardeya.foundation.calculator.dto;

import java.math.BigDecimal;

/**
 * M-08 §7. sharesSumMismatch is true when both shares are supplied and
 * don't add up to brokeragePct -- an informational note, not an error
 * (real deals charge each side independently per the spec's own text).
 */
public record BrokerageCalcResponse(
        BigDecimal total, BigDecimal ownerShare, BigDecimal buyerShare, BigDecimal gst, BigDecimal netPlusGst,
        boolean sharesSumMismatch
) {
}
