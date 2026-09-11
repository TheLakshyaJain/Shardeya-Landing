package com.shardeya.builder.payment;

import com.shardeya.builder.payment.dto.ChequeStatusRequest;
import com.shardeya.builder.payment.dto.PaymentCreateRequest;
import com.shardeya.builder.payment.dto.PaymentResponse;
import com.shardeya.builder.payment.dto.PaymentSummaryResponse;
import com.shardeya.builder.payment.dto.ReverseRequest;
import com.shardeya.platform.IdempotencyService;
import com.shardeya.platform.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class PaymentController {

    private final PaymentService service;
    private final IdempotencyService idempotencyService;

    public PaymentController(PaymentService service, IdempotencyService idempotencyService) {
        this.service = service;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping("/api/v1/sales/{id}/payments")
    @RequiresPermission("FINANCIAL_RECORD_PAYMENT")
    public ResponseEntity<PaymentResponse> record(@PathVariable UUID id, @Valid @RequestBody PaymentCreateRequest request,
                                                   @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        PaymentResponse response = idempotencyService.withIdempotency("payment-record", idempotencyKey, PaymentResponse.class,
                () -> service.record(id, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // M4: previously ungated -- see PlotSaleController's identical note.
    // "Sales Executive sees no payment data at all" (B-05 §9) depends on this.
    @GetMapping("/api/v1/sales/{id}/payments")
    @RequiresPermission("FINANCIAL_VIEW")
    public List<PaymentResponse> listForSale(@PathVariable UUID id) {
        return service.listForSale(id);
    }

    @GetMapping("/api/v1/sales/{id}/payments/summary")
    @RequiresPermission("FINANCIAL_VIEW")
    public PaymentSummaryResponse summary(@PathVariable UUID id) {
        return service.summary(id);
    }

    @PostMapping("/api/v1/payments/{id}/reverse")
    @RequiresPermission("FINANCIAL_EDIT")
    public PaymentResponse reverse(@PathVariable UUID id, @Valid @RequestBody ReverseRequest request) {
        return service.reverse(id, request);
    }

    @PatchMapping("/api/v1/payments/{id}/cheque-status")
    @RequiresPermission("FINANCIAL_EDIT")
    public PaymentResponse updateChequeStatus(@PathVariable UUID id, @Valid @RequestBody ChequeStatusRequest request) {
        return service.updateChequeStatus(id, request);
    }
}
