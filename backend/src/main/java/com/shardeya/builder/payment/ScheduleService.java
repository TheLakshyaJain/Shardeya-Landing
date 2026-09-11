package com.shardeya.builder.payment;

import com.shardeya.builder.payment.dto.ScheduleCreateRequest;
import com.shardeya.builder.payment.dto.ScheduleResponse;
import com.shardeya.builder.payment.dto.ScheduleUpdateRequest;
import com.shardeya.builder.payment.dto.WaiveRequest;
import com.shardeya.builder.sale.PlotSale;
import com.shardeya.builder.sale.PlotSaleRepository;
import com.shardeya.foundation.calendar.CalendarEvent;
import com.shardeya.foundation.calendar.CalendarService;
import com.shardeya.platform.BadRequestException;
import com.shardeya.platform.ConflictException;
import com.shardeya.platform.ForbiddenException;
import com.shardeya.platform.ProjectAccessGuard;
import com.shardeya.platform.ResourceNotFoundException;
import com.shardeya.platform.TenantContextBinder;
import com.shardeya.shared.IndianTime;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class ScheduleService {

    private final PaymentScheduleRepository repository;
    private final PlotSaleRepository saleRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final ProjectAccessGuard accessGuard;
    private final TenantContextBinder tenantContextBinder;
    private final CalendarService calendarService;
    private final EntityManager entityManager;

    public ScheduleService(PaymentScheduleRepository repository, PlotSaleRepository saleRepository,
                            PaymentAllocationRepository allocationRepository, ProjectAccessGuard accessGuard,
                            TenantContextBinder tenantContextBinder, CalendarService calendarService,
                            EntityManager entityManager) {
        this.repository = repository;
        this.saleRepository = saleRepository;
        this.allocationRepository = allocationRepository;
        this.accessGuard = accessGuard;
        this.tenantContextBinder = tenantContextBinder;
        this.calendarService = calendarService;
        this.entityManager = entityManager;
    }

    public List<ScheduleResponse> list(UUID saleId) {
        loadSale(saleId);
        return repository.findByPlotSaleIdAndDeletedAtIsNullOrderBySequenceNoAsc(saleId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ScheduleResponse add(UUID saleId, ScheduleCreateRequest req) {
        PlotSale sale = loadSale(saleId);
        int nextSeq = repository.findMaxSequenceNo(saleId) + 1;
        PaymentSchedule schedule = new PaymentSchedule(UUID.randomUUID(), sale.getOrgId(), saleId, nextSeq,
                req.label(), req.amount(), req.dueDate());
        repository.save(schedule);
        syncInstalmentProjection(sale, schedule);
        return toResponse(schedule);
    }

    @Transactional
    public ScheduleResponse update(UUID scheduleId, ScheduleUpdateRequest req) {
        PaymentSchedule schedule = loadOwn(scheduleId);
        if (req.label() != null) schedule.setLabel(req.label());
        if (req.dueDate() != null) schedule.setDueDate(req.dueDate());
        if (req.amount() != null) {
            schedule.setExpectedAmount(req.amount());
            // expected_amount changing doesn't fire the allocation trigger
            // (V3_010 only fires on payment_allocation changes), so status
            // needs the same recompute done explicitly here -- otherwise a
            // schedule whose amount increased past what's already allocated
            // would incorrectly stay PAID.
            recomputeStatus(schedule);
        }
        repository.save(schedule);
        syncInstalmentProjection(loadSale(schedule.getPlotSaleId()), schedule);
        return toResponse(schedule);
    }

    @Transactional
    public void delete(UUID scheduleId) {
        PaymentSchedule schedule = loadOwn(scheduleId);
        if (!allocationRepository.findByPaymentScheduleIdAndDeletedAtIsNull(scheduleId).isEmpty()) {
            throw new ConflictException("error.schedule.hasAllocations");
        }
        schedule.setDeletedAt(Instant.now());
        repository.save(schedule);
        calendarService.removeAutoEvent(CalendarEvent.SourceEntityType.PAYMENT_SCHEDULE, scheduleId, CalendarEvent.EventType.INSTALMENT_DUE);
    }

    @Transactional
    public ScheduleResponse waive(UUID scheduleId, WaiveRequest req) {
        // B-05 §9: "Waive: Admin only" -- stricter than the FINANCIAL_EDIT
        // permission this endpoint is otherwise gated on (which Accounts
        // Staff also holds, correctly, for reverse/edit-schedule). There is
        // no dedicated "waive" permission code in the fixed M-02 catalogue,
        // so this is an explicit role check rather than inventing one --
        // same pattern as CustomerService.assertCanViewAnyLeads for a rule
        // the declarative @RequiresPermission annotation can't express.
        // BUILDER_ADMIN is exclusively the org owner's role (TeamService
        // never allows inviting a staff member as BUILDER_ADMIN), so this
        // is equivalent to "owner only."
        if (!"BUILDER_ADMIN".equals(tenantContextBinder.current().role())) {
            throw new ForbiddenException("error.schedule.waiveAdminOnly");
        }
        PaymentSchedule schedule = loadOwn(scheduleId);
        schedule.setStatus(PaymentSchedule.Status.WAIVED);
        schedule.setWaiveReason(req.reason());
        repository.save(schedule);
        // Waived rows never appear on the calendar again -- same "no longer
        // actionable" reasoning as a paid instalment (B-09 §10).
        calendarService.removeAutoEvent(CalendarEvent.SourceEntityType.PAYMENT_SCHEDULE, scheduleId, CalendarEvent.EventType.INSTALMENT_DUE);
        return toResponse(schedule);
    }

    /**
     * Keeps the INSTALMENT_DUE calendar projection in sync with a schedule
     * row's actual due-ness (B-09 §7/§10: "Instalment paid -> the projection
     * is removed on payment, same day"). Called from every path that can
     * change a schedule's status or due date, including PaymentService after
     * recording/reversing a payment (a DB trigger flips status there, so
     * Java only finds out by re-reading the row afterward).
     */
    @Transactional
    public void syncInstalmentProjection(PlotSale sale, PaymentSchedule schedule) {
        boolean stillDue = schedule.getStatus() == PaymentSchedule.Status.PENDING
                || schedule.getStatus() == PaymentSchedule.Status.PARTIALLY_PAID
                || schedule.getStatus() == PaymentSchedule.Status.OVERDUE;
        if (!stillDue) {
            calendarService.removeAutoEvent(CalendarEvent.SourceEntityType.PAYMENT_SCHEDULE, schedule.getId(), CalendarEvent.EventType.INSTALMENT_DUE);
            return;
        }
        String title = "Instalment due: " + schedule.getExpectedAmount().toPlainString() + " (" + schedule.getLabel() + ")";
        calendarService.upsertAutoEvent(sale.getOrgId(), CalendarEvent.SourceEntityType.PAYMENT_SCHEDULE, schedule.getId(),
                CalendarEvent.EventType.INSTALMENT_DUE, title, schedule.getDueDate(), null, sale.getProjectId(),
                sale.getPlotId(), sale.getHandledBy());
    }

    /**
     * Re-syncs every schedule row on a sale -- called by PaymentService
     * after any operation that can flip a schedule's status via the DB
     * trigger (record, reverse, cheque-bounce).
     *
     * <p><b>Real bug, found live:</b> a fully-paid instalment kept showing
     * on the calendar after being recorded. Root cause was the same
     * "Hibernate can't see a DB-trigger-computed change it didn't itself
     * make" class documented since M2 (pricePerUnit/org_usage) -- but a
     * second-order case of it: {@link PaymentAllocationService#allocate}
     * (called earlier in the SAME transaction, from {@code PaymentService.record()})
     * already loads these exact {@link PaymentSchedule} rows via the
     * identical repository query before inserting {@code payment_allocation}
     * rows, whose trigger then updates {@code status}/{@code amount_allocated}
     * in the DATABASE only. Hibernate's persistence context still holds
     * those SAME entity instances by identity, so re-querying here (as this
     * method always has) returned the cached, pre-trigger objects rather
     * than fresh ones -- {@code status} still read PENDING/PARTIALLY_PAID
     * even though the row was genuinely PAID, so the calendar projection
     * was upserted instead of removed. Confirmed directly: paying an
     * instalment in full flipped {@code payment_schedule.status} to PAID in
     * the database immediately, but the {@code calendar_event} row for it
     * was never deleted. {@link EntityManager#refresh} forces each schedule
     * to be re-read from the DB before its (possibly stale) in-memory
     * status is used to decide whether to keep or remove the event.
     *
     * <p>An explicit {@link EntityManager#flush()} runs first -- some
     * callers (e.g. {@code PlotSaleService.cancel()}'s WAIVED loop) mutate
     * schedule rows in Java immediately before calling this method with no
     * flush of their own. JPA's default AUTO flush mode would flush those
     * pending changes automatically before the query below anyway, but this
     * codebase's own established convention for this exact "read-after-
     * trigger" pattern (see {@code PaymentService.record()}) is an explicit
     * flush before refresh, not reliance on implicit auto-flush timing.
     */
    @Transactional
    public void syncAllInstalmentProjections(UUID saleId) {
        entityManager.flush();
        PlotSale sale = saleRepository.findById(saleId).orElseThrow();
        for (PaymentSchedule schedule : repository.findByPlotSaleIdAndDeletedAtIsNullOrderBySequenceNoAsc(saleId)) {
            entityManager.refresh(schedule);
            syncInstalmentProjection(sale, schedule);
        }
    }

    @Transactional
    public ScheduleResponse setReminderEnabled(UUID scheduleId, boolean enabled) {
        PaymentSchedule schedule = loadOwn(scheduleId);
        schedule.setReminderEnabled(enabled);
        repository.save(schedule);
        return toResponse(schedule);
    }

    private void recomputeStatus(PaymentSchedule schedule) {
        if (schedule.getStatus() == PaymentSchedule.Status.WAIVED) return;
        BigDecimal allocated = schedule.getAmountAllocated() == null ? BigDecimal.ZERO : schedule.getAmountAllocated();
        if (allocated.compareTo(schedule.getExpectedAmount()) >= 0) {
            schedule.setStatus(PaymentSchedule.Status.PAID);
        } else if (allocated.signum() > 0) {
            schedule.setStatus(PaymentSchedule.Status.PARTIALLY_PAID);
        } else if (schedule.getDueDate().isBefore(IndianTime.today())) {
            schedule.setStatus(PaymentSchedule.Status.OVERDUE);
        } else {
            schedule.setStatus(PaymentSchedule.Status.PENDING);
        }
    }

    private PaymentSchedule loadOwn(UUID scheduleId) {
        PaymentSchedule schedule = repository.findByIdAndDeletedAtIsNull(scheduleId).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        loadSale(schedule.getPlotSaleId());
        return schedule;
    }

    private PlotSale loadSale(UUID saleId) {
        PlotSale sale = saleRepository.findByIdAndDeletedAtIsNull(saleId).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        accessGuard.assertAccess(sale.getProjectId());
        return sale;
    }

    private ScheduleResponse toResponse(PaymentSchedule s) {
        long daysOverdue = s.getStatus() == PaymentSchedule.Status.OVERDUE
                ? java.time.temporal.ChronoUnit.DAYS.between(s.getDueDate(), IndianTime.today())
                : 0;
        BigDecimal allocated = s.getAmountAllocated() == null ? BigDecimal.ZERO : s.getAmountAllocated();
        return new ScheduleResponse(s.getId(), s.getSequenceNo(), s.getLabel(), s.getExpectedAmount(), s.getDueDate(),
                s.getStatus().name(), allocated, s.isReminderEnabled(), daysOverdue);
    }
}
