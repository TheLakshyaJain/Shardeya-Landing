package com.shardeya.builder.broker;

import com.shardeya.builder.broker.dto.CommissionLedgerEntryResponse;
import com.shardeya.builder.broker.dto.CommissionPaymentCreateRequest;
import com.shardeya.builder.broker.dto.CommissionPaymentResponse;
import com.shardeya.builder.broker.dto.CommissionPaymentReverseRequest;
import com.shardeya.platform.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** B-14 §20.5 -- the commission ledger's own read/payment surface, separate from broker CRUD. */
@RestController
public class CommissionLedgerController {

    private final CommissionLedgerService ledgerService;
    private final CommissionPaymentService paymentService;

    public CommissionLedgerController(CommissionLedgerService ledgerService, CommissionPaymentService paymentService) {
        this.ledgerService = ledgerService;
        this.paymentService = paymentService;
    }

    @GetMapping("/api/v1/brokers/{id}/ledger")
    @RequiresPermission("BROKER_VIEW")
    public List<CommissionLedgerEntryResponse> forBroker(@PathVariable UUID id, @RequestParam(required = false) CommissionLedgerEntry.Status status,
                                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ledgerService.listForBroker(id, status, from, to);
    }

    @GetMapping("/api/v1/commission-ledger")
    @RequiresPermission("BROKER_VIEW")
    public List<CommissionLedgerEntryResponse> orgWide(@RequestParam(required = false) UUID brokerId, @RequestParam(required = false) UUID projectId,
                                                         @RequestParam(required = false) CommissionLedgerEntry.Status status,
                                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ledgerService.listOrgWide(brokerId, projectId, status, from, to);
    }

    @GetMapping("/api/v1/commission-ledger/{id}")
    @RequiresPermission("BROKER_VIEW")
    public CommissionLedgerEntryResponse get(@PathVariable UUID id) {
        return ledgerService.get(id);
    }

    @PostMapping("/api/v1/commission-ledger/{id}/payments")
    @RequiresPermission("BROKER_COMMISSION_PAY")
    public CommissionPaymentResponse recordPayment(@PathVariable UUID id, @Valid @RequestBody CommissionPaymentCreateRequest req) {
        return paymentService.record(id, req);
    }

    @PostMapping("/api/v1/commission-payments/{id}/reverse")
    @RequiresPermission("BROKER_COMMISSION_PAY")
    public CommissionPaymentResponse reversePayment(@PathVariable UUID id, @Valid @RequestBody CommissionPaymentReverseRequest req) {
        return paymentService.reverse(id, req);
    }
}
