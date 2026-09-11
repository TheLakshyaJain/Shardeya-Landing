package com.shardeya.builder.payment;

import com.shardeya.builder.payment.dto.AllocationRequest;
import com.shardeya.platform.BadRequestException;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * B-05 §7: "Auto-allocation, oldest-due-first, shown as a preview before
 * save and manually overridable." One receipt may settle part of one
 * instalment or span several -- payment_allocation is what makes that
 * representable at all (see its own migration comment).
 */
@Service
public class PaymentAllocationService {

    private final PaymentScheduleRepository scheduleRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final EntityManager entityManager;

    public PaymentAllocationService(PaymentScheduleRepository scheduleRepository,
                                     PaymentAllocationRepository allocationRepository, EntityManager entityManager) {
        this.scheduleRepository = scheduleRepository;
        this.allocationRepository = allocationRepository;
        this.entityManager = entityManager;
    }

    /**
     * Computes and persists allocations for a payment. If {@code manual} is
     * non-empty, it's validated and used as-is (each schedule must belong to
     * this sale, sum must not exceed the payment amount -- B-05 §11). If
     * empty, auto-allocates oldest-due-first: outstanding-amount rows are
     * filled in due-date order until the payment amount is exhausted. Any
     * remainder beyond all outstanding schedules is left unallocated (an
     * implicit "advance" -- see this class's own note on what that means
     * for total_paid vs balance_due).
     */
    public void allocate(UUID orgId, UUID plotSaleId, UUID paymentRecordId, BigDecimal paymentAmount, List<AllocationRequest> manual) {
        if (manual != null && !manual.isEmpty()) {
            allocateManual(orgId, plotSaleId, paymentRecordId, paymentAmount, manual);
        } else {
            allocateAuto(orgId, plotSaleId, paymentRecordId, paymentAmount);
        }
    }

    private void allocateManual(UUID orgId, UUID plotSaleId, UUID paymentRecordId, BigDecimal paymentAmount, List<AllocationRequest> manual) {
        BigDecimal sum = manual.stream().map(AllocationRequest::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(paymentAmount) > 0) {
            throw new BadRequestException("allocations", "ALLOCATIONS_EXCEED_PAYMENT", "error.payment.allocationsExceedAmount");
        }
        List<PaymentSchedule> schedules = scheduleRepository.findByPlotSaleIdAndDeletedAtIsNullOrderBySequenceNoAsc(plotSaleId);
        Map<UUID, PaymentSchedule> byId = schedules.stream().collect(Collectors.toMap(PaymentSchedule::getId, s -> s));
        for (AllocationRequest row : manual) {
            if (!byId.containsKey(row.scheduleId())) {
                throw new BadRequestException("allocations", "SCHEDULE_NOT_IN_SALE", "error.payment.scheduleNotInSale");
            }
            allocationRepository.save(new PaymentAllocation(UUID.randomUUID(), orgId, paymentRecordId, row.scheduleId(), row.amount()));
        }
        // Triggers (V3_010) fire per-row on insert and recompute each
        // touched schedule's own amount_allocated/status independently, so
        // no further action is needed here even across multiple schedules.
        entityManager.flush();
    }

    private void allocateAuto(UUID orgId, UUID plotSaleId, UUID paymentRecordId, BigDecimal paymentAmount) {
        List<PaymentSchedule> oldestFirst = scheduleRepository.findByPlotSaleIdAndDeletedAtIsNullOrderBySequenceNoAsc(plotSaleId).stream()
                .filter(s -> s.getStatus() != PaymentSchedule.Status.WAIVED)
                .sorted(Comparator.comparing(PaymentSchedule::getDueDate))
                .toList();

        BigDecimal remaining = paymentAmount;
        List<PaymentAllocation> toSave = new ArrayList<>();
        for (PaymentSchedule schedule : oldestFirst) {
            if (remaining.signum() <= 0) break;
            BigDecimal allocatedSoFar = schedule.getAmountAllocated() == null ? BigDecimal.ZERO : schedule.getAmountAllocated();
            BigDecimal outstanding = schedule.getExpectedAmount().subtract(allocatedSoFar);
            if (outstanding.signum() <= 0) continue;

            BigDecimal toAllocate = remaining.min(outstanding);
            toSave.add(new PaymentAllocation(UUID.randomUUID(), orgId, paymentRecordId, schedule.getId(), toAllocate));
            remaining = remaining.subtract(toAllocate);
        }
        allocationRepository.saveAll(toSave);
        entityManager.flush();
        // Any remaining amount is an unallocated advance -- CLAUDE.md/B-05
        // §7: "consumed by future instalments automatically." Auto-consuming
        // it against a LATER newly-added schedule row is not implemented
        // this milestone (a deliberate scope simplification, documented in
        // CLAUDE.md) -- the advance is still correctly reflected in
        // plot_sale.total_paid/balance_due (SUM(payment_record.amount)
        // includes the full payment regardless of allocation), just not
        // auto-applied to a schedule row created after the fact.
    }
}
