package com.shardeya.builder.broker;

import com.shardeya.builder.broker.dto.CommissionPaymentCreateRequest;
import com.shardeya.builder.broker.dto.CommissionPaymentResponse;
import com.shardeya.builder.broker.dto.CommissionPaymentReverseRequest;
import com.shardeya.platform.ApiError;
import com.shardeya.platform.BadRequestException;
import com.shardeya.platform.OutboxService;
import com.shardeya.platform.ResourceNotFoundException;
import com.shardeya.platform.TenantContextBinder;
import com.shardeya.shared.IndianTime;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * B-14 §20.5/§11 -- commission_payment is immutable (V6_008's RULE blocks
 * DELETE outright); corrections are reversals, the exact same shape
 * builder.payment.PaymentService already established for buyer payments.
 * Deliberately a separate service from CommissionLedgerService, which only
 * ever creates/cancels a ledger entry from within PlotSaleService's own sale
 * transaction -- this one is reached directly from the ledger UI, never
 * from a sale write.
 */
@Service
public class CommissionPaymentService {

    private final CommissionPaymentRepository paymentRepository;
    private final CommissionLedgerEntryRepository ledgerRepository;
    private final TenantContextBinder tenantContextBinder;
    private final OutboxService outboxService;
    private final EntityManager entityManager;

    public CommissionPaymentService(CommissionPaymentRepository paymentRepository, CommissionLedgerEntryRepository ledgerRepository,
                                     TenantContextBinder tenantContextBinder, OutboxService outboxService, EntityManager entityManager) {
        this.paymentRepository = paymentRepository;
        this.ledgerRepository = ledgerRepository;
        this.tenantContextBinder = tenantContextBinder;
        this.outboxService = outboxService;
        this.entityManager = entityManager;
    }

    @Transactional
    public CommissionPaymentResponse record(UUID ledgerEntryId, CommissionPaymentCreateRequest req) {
        UUID orgId = tenantContextBinder.currentOrgId();
        CommissionLedgerEntry entry = ledgerRepository.findByIdAndOrgIdAndDeletedAtIsNull(ledgerEntryId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        // balance_due is DB GENERATED (base_commission + tier_bonus - amount_paid)
        // -- Hibernate's first-level cache won't see a fresher value than
        // whatever this entry looked like when it was loaded, same
        // "refresh before reading a trigger/GENERATED column" rule as every
        // prior milestone's pricePerUnit/org_usage/balance_due bugs.
        entityManager.refresh(entry);

        if (req.paidOn().isAfter(IndianTime.today())) {
            throw new BadRequestException("paidOn", "PAID_ON_IN_FUTURE", "error.commissionPayment.paidOnInFuture");
        }
        if (entry.getStatus() == CommissionLedgerEntry.Status.CANCELLED) {
            throw new BadRequestException("commissionLedgerEntryId", "LEDGER_ENTRY_CANCELLED", "error.commissionPayment.ledgerEntryCancelled");
        }
        if (req.amount().compareTo(entry.getBalanceDue()) > 0 && !req.confirmOverpayment()) {
            throw new BadRequestException(java.util.List.of(new ApiError("amount", "EXCEEDS_BALANCE_DUE",
                    "error.commissionPayment.exceedsBalance", Map.of("balanceDue", entry.getBalanceDue().toPlainString()))));
        }

        UUID paymentId = UUID.randomUUID();
        CommissionPayment payment = new CommissionPayment(paymentId, orgId, ledgerEntryId, req.amount(), req.paidOn(),
                req.mode(), req.reference(), tenantContextBinder.current().userId(), req.remarks(), null);
        payment = paymentRepository.save(payment);

        entityManager.flush();
        entityManager.refresh(payment);
        entityManager.refresh(entry); // amount_paid/status just moved via V6_014's trigger

        outboxService.enqueueNotification(orgId, "COMMISSION_PAYMENT_RECORDED", "notification.commissionPaymentRecorded", null,
                Map.of("amount", req.amount().toPlainString(), "balanceDue", entry.getBalanceDue().toPlainString()),
                "commission_ledger_entry", ledgerEntryId);

        return toResponse(payment);
    }

    // B-14 §5's own reverse endpoint is scoped to /commission-payments/{id}/reverse,
    // not the ledger entry -- mirrors PaymentService.reverse()'s exact shape:
    // block reversing an already-reversed payment, and block reversing a
    // reversal itself (no reversal-of-a-reversal).
    @Transactional
    public CommissionPaymentResponse reverse(UUID paymentId, CommissionPaymentReverseRequest req) {
        UUID orgId = tenantContextBinder.currentOrgId();
        CommissionPayment original = paymentRepository.findByIdAndOrgId(paymentId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        if (original.getReversesPaymentId() != null) {
            throw new BadRequestException("paymentId", "CANNOT_REVERSE_A_REVERSAL", "error.commissionPayment.cannotReverseReversal");
        }
        if (paymentRepository.existsByReversesPaymentId(paymentId)) {
            throw new BadRequestException("paymentId", "ALREADY_REVERSED", "error.commissionPayment.alreadyReversed");
        }

        UUID reversalId = UUID.randomUUID();
        CommissionPayment reversal = new CommissionPayment(reversalId, orgId, original.getCommissionLedgerEntryId(),
                original.getAmount().negate(), IndianTime.today(), original.getMode(), original.getReference(),
                tenantContextBinder.current().userId(), req.reason(), original.getId());
        reversal = paymentRepository.save(reversal);

        entityManager.flush();
        entityManager.refresh(reversal);
        return toResponse(reversal);
    }

    private CommissionPaymentResponse toResponse(CommissionPayment p) {
        return new CommissionPaymentResponse(p.getId(), p.getCommissionLedgerEntryId(), p.getAmount(), p.getPaidOn(),
                p.getMode().name(), p.getReference(), p.getRemarks(), p.getReversesPaymentId(), p.getCreatedAt());
    }
}
