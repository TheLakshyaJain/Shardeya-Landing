package com.shardeya.builder.broker;

import com.shardeya.builder.broker.dto.BookingCommissionResponse;
import com.shardeya.builder.broker.dto.BrokerCommissionPaymentCreateRequest;
import com.shardeya.builder.broker.dto.BrokerCommissionPaymentResponse;
import com.shardeya.builder.broker.dto.BrokerCommissionPaymentReverseRequest;
import com.shardeya.builder.broker.dto.BrokerCommissionSummaryResponse;
import com.shardeya.builder.broker.dto.DesignationHistoryResponse;
import com.shardeya.builder.broker.dto.NetworkCommissionSummaryResponse;
import com.shardeya.platform.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** 06-BROKER-NETWORK-ENGINE.md §7/§8/§8a/§26/§27/§29 -- the read surface for a DESIGNATION broker's frozen/releasing/paid commission tree, its "Record Payment" action, and the step-10 dashboard aggregates, separate from CommissionLedgerController's PERCENTAGE/FIXED-only ledger. */
@RestController
public class BookingCommissionController {

    private final BookingCommissionService bookingCommissionService;
    private final DesignationPromotionService designationPromotionService;
    private final BrokerCommissionPaymentService brokerCommissionPaymentService;

    public BookingCommissionController(BookingCommissionService bookingCommissionService, DesignationPromotionService designationPromotionService,
                                        BrokerCommissionPaymentService brokerCommissionPaymentService) {
        this.bookingCommissionService = bookingCommissionService;
        this.designationPromotionService = designationPromotionService;
        this.brokerCommissionPaymentService = brokerCommissionPaymentService;
    }

    // §8a: pays against the broker's whole Due balance, auto-allocated
    // oldest-first -- see BrokerCommissionPaymentService's own class
    // javadoc for the full model and the hard-cap reasoning.
    @PostMapping("/api/v1/brokers/{id}/commission-payments")
    @RequiresPermission("BROKER_COMMISSION_PAY")
    public BrokerCommissionPaymentResponse recordCommissionPayment(@PathVariable UUID id, @Valid @RequestBody BrokerCommissionPaymentCreateRequest req) {
        return brokerCommissionPaymentService.record(id, req);
    }

    @GetMapping("/api/v1/brokers/{id}/commission-payments")
    @RequiresPermission("BROKER_VIEW")
    public List<BrokerCommissionPaymentResponse> commissionPayments(@PathVariable UUID id) {
        return brokerCommissionPaymentService.listForBroker(id);
    }

    @PostMapping("/api/v1/broker-commission-payments/{id}/reverse")
    @RequiresPermission("BROKER_COMMISSION_PAY")
    public BrokerCommissionPaymentResponse reverseCommissionPayment(@PathVariable UUID id, @Valid @RequestBody BrokerCommissionPaymentReverseRequest req) {
        return brokerCommissionPaymentService.reverse(id, req);
    }

    @GetMapping("/api/v1/brokers/{id}/booking-commissions")
    @RequiresPermission("BROKER_VIEW")
    public List<BookingCommissionResponse> forBroker(@PathVariable UUID id) {
        return bookingCommissionService.listForBeneficiary(id);
    }

    @GetMapping("/api/v1/brokers/{id}/commission-summary")
    @RequiresPermission("BROKER_VIEW")
    public BrokerCommissionSummaryResponse commissionSummary(@PathVariable UUID id) {
        return bookingCommissionService.commissionSummary(id);
    }

    @GetMapping("/api/v1/brokers/{id}/designation-history")
    @RequiresPermission("BROKER_VIEW")
    public List<DesignationHistoryResponse> designationHistory(@PathVariable UUID id) {
        return designationPromotionService.history(id);
    }

    // Literal path segments ("network") always win over {id} in Spring's
    // matching regardless of declaration order -- same fact
    // BrokerPartnerController.network() already documents.
    @GetMapping("/api/v1/brokers/network/commission-summary")
    @RequiresPermission("BROKER_VIEW")
    public NetworkCommissionSummaryResponse networkCommissionSummary() {
        return bookingCommissionService.networkCommissionSummary();
    }

    @GetMapping("/api/v1/brokers/network/designation-history")
    @RequiresPermission("BROKER_VIEW")
    public List<DesignationHistoryResponse> networkDesignationHistory() {
        return designationPromotionService.networkHistory();
    }
}
