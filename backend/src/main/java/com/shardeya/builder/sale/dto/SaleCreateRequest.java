package com.shardeya.builder.sale.dto;

import com.shardeya.builder.sale.PlotSale;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Field validations mirror 03-BUILDER-MODULES.md B-04 §11. */
public record SaleCreateRequest(
        UUID customerId,

        @NotBlank(message = "error.sale.buyerNameRequired")
        @Size(min = 2, max = 120, message = "error.sale.buyerNameInvalid")
        String buyerName,

        @NotBlank(message = "error.sale.buyerMobileRequired")
        @Pattern(regexp = "^[6-9]\\d{9}$", message = "error.sale.buyerMobileInvalid")
        String buyerMobile,

        String buyerEmail,

        PlotSale.GovIdType buyerGovIdType,
        // Plaintext in the request only -- PlotSaleService encrypts before
        // persisting (GovIdCipher) and only the last 4 digits are ever
        // stored/returned unencrypted.
        String buyerGovIdNumber,
        UUID buyerGovIdMediaId,

        // Range-checked in PlotSaleService against IST's "today" (CLAUDE.md
        // rule #11), not a Bean Validation annotation -- @PastOrPresent/
        // @Past validate against the JVM's default zone, not guaranteed IST.
        @NotNull(message = "error.sale.purchaseDateRequired")
        LocalDate purchaseDate,

        @NotNull(message = "error.sale.dealValueRequired")
        @DecimalMin(value = "0.01", message = "error.sale.dealValueInvalid")
        BigDecimal dealValue,

        UUID brokerPartnerId,
        String externalBrokerName,
        String externalBrokerMobile,
        BigDecimal brokerCommissionAmount,

        @NotNull(message = "error.sale.paymentTypeRequired")
        PlotSale.PaymentType paymentType,

        @NotNull(message = "error.sale.scheduleRequired")
        List<@Valid ScheduleRowRequest> schedule,

        UUID handledBy,

        // Buyer WhatsApp consent, captured on their behalf by whichever
        // staff/admin user is running the wizard -- see
        // BuyerWhatsAppOptInService's own javadoc for why this is a
        // separate concept from a user's own OTP-confirmed opt-in. Defaults
        // to false (no consent captured) when the client omits it entirely
        // (Jackson's record-aware deserializer treats a missing JSON field
        // as the type's default, same as every other primitive here).
        boolean buyerWhatsappOptIn
) {
    // Non-canonical, test-convenience overload predating this field --
    // every existing test constructing this record directly would
    // otherwise need updating for one new trailing boolean it doesn't care
    // about. Jackson's record module deserializes via the CANONICAL
    // (all-components) constructor specifically, so this extra constructor
    // has no effect on real request parsing.
    public SaleCreateRequest(UUID customerId, String buyerName, String buyerMobile, String buyerEmail,
                              PlotSale.GovIdType buyerGovIdType, String buyerGovIdNumber, UUID buyerGovIdMediaId,
                              LocalDate purchaseDate, BigDecimal dealValue, UUID brokerPartnerId,
                              String externalBrokerName, String externalBrokerMobile, BigDecimal brokerCommissionAmount,
                              PlotSale.PaymentType paymentType, List<ScheduleRowRequest> schedule, UUID handledBy) {
        this(customerId, buyerName, buyerMobile, buyerEmail, buyerGovIdType, buyerGovIdNumber, buyerGovIdMediaId,
                purchaseDate, dealValue, brokerPartnerId, externalBrokerName, externalBrokerMobile, brokerCommissionAmount,
                paymentType, schedule, handledBy, false);
    }
}
