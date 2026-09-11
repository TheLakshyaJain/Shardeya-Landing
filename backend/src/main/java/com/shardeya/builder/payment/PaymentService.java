package com.shardeya.builder.payment;

import com.shardeya.builder.broker.CommissionReleaseService;
import com.shardeya.builder.payment.dto.ChequeStatusRequest;
import com.shardeya.builder.payment.dto.PaymentCreateRequest;
import com.shardeya.builder.payment.dto.PaymentResponse;
import com.shardeya.builder.payment.dto.PaymentSummaryResponse;
import com.shardeya.builder.payment.dto.ReverseRequest;
import com.shardeya.builder.sale.PlotSale;
import com.shardeya.builder.sale.PlotSaleRepository;
import com.shardeya.platform.BadRequestException;
import com.shardeya.platform.OutboxService;
import com.shardeya.platform.ProjectAccessGuard;
import com.shardeya.platform.ResourceNotFoundException;
import com.shardeya.platform.TenantContextBinder;
import com.shardeya.shared.IndianTime;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRecordRepository recordRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final PlotSaleRepository saleRepository;
    private final PaymentAllocationService allocationService;
    private final ReceiptService receiptService;
    private final ProjectAccessGuard accessGuard;
    private final TenantContextBinder tenantContextBinder;
    private final OutboxService outboxService;
    private final EntityManager entityManager;
    private final ScheduleService scheduleService;
    private final CommissionReleaseService commissionReleaseService;

    public PaymentService(PaymentRecordRepository recordRepository, PaymentAllocationRepository allocationRepository,
                           PlotSaleRepository saleRepository, PaymentAllocationService allocationService,
                           ReceiptService receiptService, ProjectAccessGuard accessGuard,
                           TenantContextBinder tenantContextBinder, OutboxService outboxService, EntityManager entityManager,
                           ScheduleService scheduleService, CommissionReleaseService commissionReleaseService) {
        this.recordRepository = recordRepository;
        this.allocationRepository = allocationRepository;
        this.saleRepository = saleRepository;
        this.allocationService = allocationService;
        this.receiptService = receiptService;
        this.accessGuard = accessGuard;
        this.tenantContextBinder = tenantContextBinder;
        this.outboxService = outboxService;
        this.entityManager = entityManager;
        this.scheduleService = scheduleService;
        this.commissionReleaseService = commissionReleaseService;
    }

    @Transactional
    public PaymentResponse record(UUID saleId, PaymentCreateRequest req) {
        PlotSale sale = loadSale(saleId);

        if (req.paidOn().isAfter(IndianTime.today())) {
            throw new BadRequestException("paidOn", "PAID_ON_IN_FUTURE", "error.payment.paidOnInFuture");
        }
        if (req.paidOn().isBefore(sale.getPurchaseDate().minusDays(90))) {
            throw new BadRequestException("paidOn", "PAID_ON_TOO_EARLY", "error.payment.paidOnTooEarly");
        }
        if (req.mode() != PaymentRecord.Mode.CASH && (req.reference() == null || req.reference().isBlank())) {
            throw new BadRequestException("reference", "REFERENCE_REQUIRED", "error.payment.referenceRequired");
        }

        UUID orgId = sale.getOrgId();
        String receiptNo = receiptService.nextReceiptNumber(orgId, req.paidOn());
        UUID paymentId = UUID.randomUUID();

        PaymentRecord record = new PaymentRecord(paymentId, orgId, saleId, sale.getProjectId(), sale.getPlotId(),
                receiptNo, req.amount(), req.paidOn(), req.mode(), req.reference(), tenantContextBinder.current().userId());
        record.setRemarks(req.remarks());
        record = recordRepository.save(record);

        allocationService.allocate(orgId, saleId, paymentId, req.amount(), req.allocations());

        outboxService.enqueueNotification(orgId, "PAYMENT_RECORDED", "notification.paymentRecorded", null,
                Map.of("receiptNo", receiptNo, "amount", req.amount().toPlainString()), "plot_sale", saleId);
        // B-11 §17.2: "a payment receipt is generated automatically on
        // every payment_record insert via the outbox" -- wired only for
        // this, the normal payment-recording path. doReverse() (called from
        // both reverse() and the cheque-bounce path) deliberately does NOT
        // get the same auto-trigger this round -- a reversal receipt is a
        // real, if narrow, gap (see CLAUDE.md), still generatable manually
        // via POST /documents/generate against that payment_record's id.
        outboxService.enqueueDocumentGenerate(orgId, paymentId);

        entityManager.flush();
        entityManager.refresh(record);
        // 06-BROKER-NETWORK-ENGINE.md §8 -- the release fraction is driven
        // by the sale's own authoritative cumulative total_paid, which the
        // trigger fired by the payment_record insert above has already
        // updated in the DB but not yet in this JPA-managed `sale` --
        // same "Hibernate can't see a DB-trigger-computed change it didn't
        // itself make" root cause documented since M2. No-op for a
        // PERCENTAGE/FIXED-broker or no-broker sale (no booking_commission
        // rows exist to release against).
        entityManager.refresh(sale);
        commissionReleaseService.releaseForPayment(sale, record);
        scheduleService.syncAllInstalmentProjections(saleId);
        return toResponse(record);
    }

    public List<PaymentResponse> listForSale(UUID saleId) {
        loadSale(saleId);
        return recordRepository.findByPlotSaleIdOrderByPaidOnDescCreatedAtDesc(saleId).stream().map(this::toResponse).toList();
    }

    // entityManager.refresh() requires an active transaction even for a pure
    // read (unlike a plain query) -- found via a real curl call to this
    // endpoint, which 500'd with TransactionRequiredException since every
    // other read method in this class has no @Transactional and none of
    // them had needed refresh() before this one.
    @Transactional(readOnly = true)
    public PaymentSummaryResponse summary(UUID saleId) {
        PlotSale sale = loadSale(saleId);
        entityManager.refresh(sale);
        return new PaymentSummaryResponse(sale.getDealValue(), sale.getTotalPaid(), sale.getTotalWaived(), sale.getBalanceDue());
    }

    @Transactional
    public PaymentResponse reverse(UUID paymentId, ReverseRequest req) {
        PaymentRecord original = loadPayment(paymentId);
        PaymentRecord reversal = doReverse(original, req.reason());
        scheduleService.syncAllInstalmentProjections(original.getPlotSaleId());
        return toResponse(reversal);
    }

    // B-05 §7: cheque lifecycle. CLEARED is just a status flip; BOUNCED
    // auto-creates a reversal (a bounced cheque never really cleared, so its
    // provisional contribution to total_paid must come back out) and
    // notifies -- the one M3 notification type that fires from THIS path
    // rather than PlotSaleService/PaymentService.record().
    @Transactional
    public PaymentResponse updateChequeStatus(UUID paymentId, ChequeStatusRequest req) {
        PaymentRecord payment = loadPayment(paymentId);
        if (payment.getMode() != PaymentRecord.Mode.CHEQUE) {
            throw new BadRequestException("mode", "NOT_A_CHEQUE_PAYMENT", "error.payment.notChequePayment");
        }
        payment.setChequeStatus(req.status());
        recordRepository.save(payment);

        if (req.status() == PaymentRecord.ChequeStatus.BOUNCED) {
            // "remarks" is free-text shown directly in the payment history
            // table, not an i18n key -- unlike error messageKeys elsewhere,
            // this needs to actually be readable. (Caught by inspecting a
            // real payment history response: the raw messageKey string
            // "error.payment.chequeBouncedReversal" showed up verbatim as
            // the reversal's own remark.)
            doReverse(payment, "Reversal: cheque " + payment.getReceiptNo() + " bounced");
            outboxService.enqueueNotification(payment.getOrgId(), "CHEQUE_BOUNCED", "notification.chequeBounced", null,
                    Map.of("receiptNo", payment.getReceiptNo()), "plot_sale", payment.getPlotSaleId());
        }
        entityManager.flush();
        entityManager.refresh(payment);
        scheduleService.syncAllInstalmentProjections(payment.getPlotSaleId());
        return toResponse(payment);
    }

    private PaymentRecord doReverse(PaymentRecord original, String reason) {
        // Nothing stopped this from being called twice on the same original
        // payment -- neither PaymentService.reverse() nor the cheque-bounce
        // path in updateChequeStatus() checked for an existing reversal
        // first. A second reversal creates a second negative payment_record
        // plus a second set of negated allocations mirroring the SAME
        // original allocations, double-subtracting from
        // amount_allocated/total_paid rather than being a no-op or an
        // error -- confirmed by reversing the same payment twice and
        // watching the schedule's allocated amount go negative relative to
        // what one reversal should have produced. A payment can only ever
        // be reversed once (there is exactly one legitimate correction for
        // any given original), so guard on it here where both callers
        // (manual reverse and auto cheque-bounce reversal) funnel through.
        if (recordRepository.existsByReversesPaymentId(original.getId())) {
            throw new BadRequestException("paymentId", "ALREADY_REVERSED", "error.payment.alreadyReversed");
        }
        // A reversal record itself must never be reversible. It reuses the
        // same PaymentRecord constructor as any original payment, which
        // defaults chequeStatus to PENDING whenever mode=CHEQUE -- so a
        // reversal of a cheque payment looks, to the cheque-lifecycle
        // endpoint, exactly like a fresh actionable cheque. Calling
        // updateChequeStatus(reversalId, BOUNCED) on it would recurse into
        // doReverse() again with the REVERSAL as the "original," creating a
        // reversal-of-a-reversal: a brand new POSITIVE payment_record whose
        // allocations re-negate the (already negative) reversal allocations
        // -- silently re-crediting money that had legitimately been
        // reversed out, via a path this method's other guard above doesn't
        // catch (the reversal's own id has never itself been reversed).
        if (original.getReversesPaymentId() != null) {
            throw new BadRequestException("paymentId", "CANNOT_REVERSE_A_REVERSAL", "error.payment.cannotReverseReversal");
        }
        String receiptNo = receiptService.nextReceiptNumber(original.getOrgId(), IndianTime.today());
        UUID reversalId = UUID.randomUUID();
        PaymentRecord reversal = new PaymentRecord(reversalId, original.getOrgId(), original.getPlotSaleId(),
                original.getProjectId(), original.getPlotId(), receiptNo, original.getAmount().negate(),
                IndianTime.today(), original.getMode(), original.getReference(), tenantContextBinder.current().userId());
        reversal.setRemarks(reason);
        reversal.setReversesPaymentId(original.getId());
        reversal = recordRepository.save(reversal);

        // Mirror the original's own allocations with negated amounts, onto
        // the SAME schedules -- this is what actually pulls
        // amount_allocated (and therefore schedule status) back down; the
        // reversal's own total-paid contribution is handled by the
        // plot_sale trigger exactly like any other payment_record insert.
        for (PaymentAllocation original_ : allocationRepository.findByPaymentRecordIdAndDeletedAtIsNull(original.getId())) {
            allocationRepository.save(new PaymentAllocation(UUID.randomUUID(), original.getOrgId(), reversalId,
                    original_.getPaymentScheduleId(), original_.getAmount().negate()));
        }
        entityManager.flush();

        // 06-BROKER-NETWORK-ENGINE.md §8 -- a reversal is itself a new,
        // negative-amount customer payment (this method's own reversal
        // PaymentRecord above), so it must un-release the matching
        // proportional slice of every DESIGNATION-broker beneficiary's
        // frozen commission -- no special-case "reverse this release" logic
        // needed, the delta-against-cumulative-total math in
        // CommissionReleaseService handles it automatically once
        // sale.totalPaid reflects the reversal. Covers both callers of this
        // method (manual reverse() and the cheque-bounce auto-reversal in
        // updateChequeStatus()) for free.
        PlotSale sale = saleRepository.findByIdAndDeletedAtIsNull(original.getPlotSaleId()).orElse(null);
        if (sale != null) {
            entityManager.refresh(sale);
            commissionReleaseService.releaseForPayment(sale, reversal);
        }
        return reversal;
    }

    private PlotSale loadSale(UUID saleId) {
        PlotSale sale = saleRepository.findByIdAndDeletedAtIsNull(saleId).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        accessGuard.assertAccess(sale.getProjectId());
        return sale;
    }

    private PaymentRecord loadPayment(UUID paymentId) {
        PaymentRecord payment = recordRepository.findById(paymentId).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        loadSale(payment.getPlotSaleId()); // throws if this sale/project isn't in scope
        return payment;
    }

    private PaymentResponse toResponse(PaymentRecord p) {
        List<PaymentResponse.AllocationResponse> allocations = allocationRepository.findByPaymentRecordIdAndDeletedAtIsNull(p.getId())
                .stream().map(a -> new PaymentResponse.AllocationResponse(a.getPaymentScheduleId(), a.getAmount())).toList();
        return new PaymentResponse(p.getId(), p.getPlotSaleId(), p.getReceiptNo(), p.getAmount(), p.getPaidOn(),
                p.getMode().name(), p.getReference(), p.getChequeStatus() == null ? null : p.getChequeStatus().name(),
                p.getReceivedBy(), p.getRemarks(), p.getReversesPaymentId(), allocations, p.getCreatedAt());
    }
}
