package com.shardeya.builder.broker;

import com.shardeya.builder.broker.dto.BrokerCommissionPaymentCreateRequest;
import com.shardeya.builder.broker.dto.BrokerCommissionPaymentResponse;
import com.shardeya.builder.broker.dto.BrokerCommissionPaymentReverseRequest;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 06-BROKER-NETWORK-ENGINE.md §8a -- the third money state, Paid, and the
 * "Record Payment" action that creates it. Three states now exist for a
 * DESIGNATION broker's frozen commission:
 * <pre>
 * Earned    -- frozen at BOOKED (booking_commission.total_amount)
 * Released  -- unlocked as the CUSTOMER pays (booking_commission.released_amount, CommissionReleaseService)
 * Paid      -- the BUILDER actually paying the broker (booking_commission.paid_amount, this class -- new)
 * </pre>
 * Commission Due = Released - Paid (booking_commission.pending_amount, a
 * GENERATED column) -- the number the builder acts on and the hard cap
 * this service enforces: a payout can never exceed a broker's Due, because
 * Due is itself capped at Released, which is capped by real customer
 * collection. This is the entire point (ties payout to collection,
 * protects builder cash flow) -- unlike a buyer's payment
 * (PaymentAllocationService), which allows an "advance" beyond what's
 * currently due, a broker payout beyond Due is always a flat, hard reject,
 * never something a confirmation flag can override.
 *
 * <p>Reuses B-05's exact oldest-first auto-allocation shape
 * (PaymentAllocationService.allocateAuto()) and M6/B-05's exact
 * immutable-payment-plus-reversal discipline
 * (CommissionPaymentService/PaymentService.doReverse()) -- no new
 * allocation or reversal machinery invented, applied to a new table.
 *
 * <p>Concurrency: {@link BookingCommissionRepository#lockForPayoutOldestFirst}
 * acquires a PESSIMISTIC_WRITE lock on every affected booking_commission
 * row as this transaction's first touch to them, BEFORE the hard-cap sum
 * is computed -- a second, concurrent payout attempt against the same
 * broker blocks on that lock until this transaction fully commits
 * (including the paid_amount trigger's own write, V65_017), then sees the
 * correctly-reduced Due figures. This is what makes the hard cap itself
 * safe under real concurrency, not just the aggregate column (the
 * trigger's own separate lock-then-recompute discipline handles that
 * half, matching V65_012's already-learned lesson from day one).
 */
@Service
public class BrokerCommissionPaymentService {

    private final BrokerCommissionPaymentRepository paymentRepository;
    private final BrokerCommissionPaymentAllocationRepository allocationRepository;
    private final BookingCommissionRepository bookingCommissionRepository;
    private final BrokerPartnerRepository brokerRepository;
    private final TenantContextBinder tenantContextBinder;
    private final OutboxService outboxService;
    private final EntityManager entityManager;

    public BrokerCommissionPaymentService(BrokerCommissionPaymentRepository paymentRepository,
                                           BrokerCommissionPaymentAllocationRepository allocationRepository,
                                           BookingCommissionRepository bookingCommissionRepository,
                                           BrokerPartnerRepository brokerRepository, TenantContextBinder tenantContextBinder,
                                           OutboxService outboxService, EntityManager entityManager) {
        this.paymentRepository = paymentRepository;
        this.allocationRepository = allocationRepository;
        this.bookingCommissionRepository = bookingCommissionRepository;
        this.brokerRepository = brokerRepository;
        this.tenantContextBinder = tenantContextBinder;
        this.outboxService = outboxService;
        this.entityManager = entityManager;
    }

    @Transactional
    public BrokerCommissionPaymentResponse record(UUID brokerId, BrokerCommissionPaymentCreateRequest req) {
        UUID orgId = tenantContextBinder.currentOrgId();
        BrokerPartner broker = brokerRepository.findByIdAndOrgIdAndDeletedAtIsNull(brokerId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        if (broker.getCommissionType() != BrokerPartner.CommissionType.DESIGNATION) {
            // §8a only exists for DESIGNATION brokers -- PERCENTAGE/FIXED
            // brokers already have their own, separate payout mechanism
            // (CommissionPaymentService, against a single commission_ledger_entry).
            throw new BadRequestException("brokerId", "NOT_A_DESIGNATION_BROKER", "error.brokerCommissionPayment.notDesignationBroker");
        }
        if (req.paidOn().isAfter(IndianTime.today())) {
            throw new BadRequestException("paidOn", "PAID_ON_IN_FUTURE", "error.brokerCommissionPayment.paidOnInFuture");
        }

        // First touch to these rows in this transaction -- see this
        // class's own javadoc for why that ordering is what makes both the
        // hard cap and the aggregate column concurrency-safe.
        List<BookingCommission> oldestFirst = bookingCommissionRepository.lockForPayoutOldestFirst(brokerId, BookingCommission.Status.CANCELLED);

        BigDecimal totalDue = BigDecimal.ZERO;
        for (BookingCommission row : oldestFirst) {
            totalDue = totalDue.add(row.getPendingAmount());
        }
        if (req.amount().compareTo(totalDue) > 0) {
            // Hard reject, never a confirm-to-proceed flag -- paying past
            // Due would mean paying against commission not yet released by
            // customer collection, which is exactly what Due exists to
            // prevent.
            throw new BadRequestException(List.of(new ApiError("amount", "EXCEEDS_COMMISSION_DUE",
                    "error.brokerCommissionPayment.exceedsDue", Map.of(
                            "due", totalDue.toPlainString(), "requested", req.amount().toPlainString()))));
        }

        UUID paymentId = UUID.randomUUID();
        BrokerCommissionPayment payment = new BrokerCommissionPayment(paymentId, orgId, brokerId, req.amount(),
                req.paidOn(), req.mode(), req.reference(), req.remarks(), null);
        payment.setCreatedBy(tenantContextBinder.current().userId());
        payment = paymentRepository.save(payment);

        allocateOldestFirst(orgId, paymentId, req.amount(), oldestFirst);

        entityManager.flush();
        entityManager.refresh(payment);

        outboxService.enqueueNotification(orgId, "BROKER_COMMISSION_PAID", "notification.brokerCommissionPaid", null,
                Map.of("name", broker.getFullName(), "amount", req.amount().toPlainString()), "broker_partner", brokerId);

        return toResponse(payment);
    }

    // B-05's exact oldest-first shape (PaymentAllocationService.allocateAuto()),
    // applied to booking_commission rows instead of payment_schedule rows,
    // and against each row's own pendingAmount (=due) rather than an
    // expected-minus-allocated figure. Unlike that method, there is no
    // "unallocated advance" case here -- the hard-cap check above already
    // guarantees requestedAmount never exceeds the sum of every row's due,
    // so this loop always fully exhausts the payment amount across the
    // rows it touches (remaining reaches exactly zero, never leaves a
    // remainder).
    private void allocateOldestFirst(UUID orgId, UUID paymentId, BigDecimal paymentAmount, List<BookingCommission> oldestFirst) {
        BigDecimal remaining = paymentAmount;
        List<BrokerCommissionPaymentAllocation> toSave = new ArrayList<>();
        for (BookingCommission row : oldestFirst) {
            if (remaining.signum() <= 0) break;
            BigDecimal due = row.getPendingAmount();
            if (due.signum() <= 0) continue;

            BigDecimal toAllocate = remaining.min(due);
            toSave.add(new BrokerCommissionPaymentAllocation(UUID.randomUUID(), orgId, paymentId, row.getId(), toAllocate));
            remaining = remaining.subtract(toAllocate);
        }
        allocationRepository.saveAll(toSave);
    }

    @Transactional(readOnly = true)
    public List<BrokerCommissionPaymentResponse> listForBroker(UUID brokerId) {
        UUID orgId = tenantContextBinder.currentOrgId();
        brokerRepository.findByIdAndOrgIdAndDeletedAtIsNull(brokerId, orgId).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        return paymentRepository.findByOrgIdAndBeneficiaryBrokerIdOrderByCreatedAtDesc(orgId, brokerId).stream().map(this::toResponse).toList();
    }

    // Mirrors PaymentService.doReverse()'s exact two guards and mirrored-allocation
    // shape -- a payment can only ever be reversed once, and a reversal itself
    // can never be reversed (both prevent the identical double-reversal/
    // reversal-of-a-reversal corruption classes documented on that method).
    @Transactional
    public BrokerCommissionPaymentResponse reverse(UUID paymentId, BrokerCommissionPaymentReverseRequest req) {
        UUID orgId = tenantContextBinder.currentOrgId();
        BrokerCommissionPayment original = paymentRepository.findByIdAndOrgId(paymentId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        if (original.getReversesPaymentId() != null) {
            throw new BadRequestException("paymentId", "CANNOT_REVERSE_A_REVERSAL", "error.brokerCommissionPayment.cannotReverseReversal");
        }
        if (paymentRepository.existsByReversesPaymentId(paymentId)) {
            throw new BadRequestException("paymentId", "ALREADY_REVERSED", "error.brokerCommissionPayment.alreadyReversed");
        }

        UUID reversalId = UUID.randomUUID();
        BrokerCommissionPayment reversal = new BrokerCommissionPayment(reversalId, orgId, original.getBeneficiaryBrokerId(),
                original.getAmount().negate(), IndianTime.today(), original.getMode(), original.getReference(),
                req.reason(), original.getId());
        reversal.setCreatedBy(tenantContextBinder.current().userId());
        reversal = paymentRepository.save(reversal);

        // Mirror the original's own allocations with negated amounts, onto
        // the SAME booking_commission rows -- this is what actually pulls
        // paid_amount (and therefore pending_amount/Due) back up via the
        // trigger, exactly like PaymentService.doReverse() pulls
        // amount_allocated back down.
        for (BrokerCommissionPaymentAllocation original_ : allocationRepository.findByBrokerCommissionPaymentId(original.getId())) {
            allocationRepository.save(new BrokerCommissionPaymentAllocation(UUID.randomUUID(), orgId, reversalId,
                    original_.getBookingCommissionId(), original_.getAmount().negate()));
        }

        entityManager.flush();
        entityManager.refresh(reversal);
        return toResponse(reversal);
    }

    private BrokerCommissionPaymentResponse toResponse(BrokerCommissionPayment p) {
        return new BrokerCommissionPaymentResponse(p.getId(), p.getBeneficiaryBrokerId(), p.getAmount(), p.getPaidOn(),
                p.getMode().name(), p.getReference(), p.getRemarks(), p.getReversesPaymentId(), p.getCreatedAt());
    }
}
