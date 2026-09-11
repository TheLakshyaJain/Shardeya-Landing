package com.shardeya.builder.broker.dto;

/** B-14 §4 "GET /brokers/{id}/bank-details [SENSITIVE_VIEW, audited]". */
public record BankDetailsResponse(String bankAccountName, String bankAccountNumber, String ifsc, String upiId) {
}
