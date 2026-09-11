package com.shardeya.builder.tracker;

import com.shardeya.builder.payment.PaymentSchedule;
import com.shardeya.builder.payment.PaymentScheduleRepository;
import com.shardeya.builder.payment.PaymentService;
import com.shardeya.builder.payment.dto.AllocationRequest;
import com.shardeya.builder.payment.dto.PaymentCreateRequest;
import com.shardeya.builder.payment.dto.PaymentResponse;
import com.shardeya.builder.sale.PlotSale;
import com.shardeya.builder.sale.PlotSaleRepository;
import com.shardeya.builder.tracker.dto.CollectionRow;
import com.shardeya.builder.tracker.dto.FollowUpRow;
import com.shardeya.builder.tracker.dto.TrackerCounts;
import com.shardeya.foundation.customer.Interaction;
import com.shardeya.foundation.customer.InteractionService;
import com.shardeya.foundation.customer.dto.InteractionCreateRequest;
import com.shardeya.foundation.customer.dto.InteractionResponse;
import com.shardeya.foundation.notification.BuyerWhatsAppOptInService;
import com.shardeya.platform.BadRequestException;
import com.shardeya.platform.ConflictException;
import com.shardeya.platform.Cursor;
import com.shardeya.platform.CursorPage;
import com.shardeya.platform.OutboxService;
import com.shardeya.platform.ProjectAccessGuard;
import com.shardeya.platform.ResourceNotFoundException;
import com.shardeya.platform.TenantContext;
import com.shardeya.platform.TenantContextBinder;
import com.shardeya.shared.IndianTime;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * B-13 Follow-up & Collection Tracker -- "everything in it exists elsewhere
 * ... but a builder should not have to visit three modules." True to that,
 * every ACTION here (log/reschedule/mark-done/record-payment) is a thin
 * delegation to the module that actually owns the write (InteractionService,
 * PaymentService) rather than reimplementing their business logic -- only
 * the two READ queries (followUps/collections) are genuinely tracker-owned,
 * since they're action-oriented views with no single owning module.
 */
@Service
public class TrackerService {

    private final NamedParameterJdbcTemplate jdbc;
    private final TenantContextBinder tenantContextBinder;
    private final ProjectAccessGuard accessGuard;
    private final InteractionService interactionService;
    private final PaymentService paymentService;
    private final PaymentScheduleRepository scheduleRepository;
    private final PlotSaleRepository saleRepository;
    private final OutboxService outboxService;
    private final BuyerWhatsAppOptInService buyerWhatsAppOptInService;

    public TrackerService(NamedParameterJdbcTemplate jdbc, TenantContextBinder tenantContextBinder,
                           ProjectAccessGuard accessGuard, InteractionService interactionService,
                           PaymentService paymentService, PaymentScheduleRepository scheduleRepository,
                           PlotSaleRepository saleRepository, OutboxService outboxService,
                           BuyerWhatsAppOptInService buyerWhatsAppOptInService) {
        this.jdbc = jdbc;
        this.tenantContextBinder = tenantContextBinder;
        this.accessGuard = accessGuard;
        this.interactionService = interactionService;
        this.paymentService = paymentService;
        this.scheduleRepository = scheduleRepository;
        this.saleRepository = saleRepository;
        this.outboxService = outboxService;
        this.buyerWhatsAppOptInService = buyerWhatsAppOptInService;
    }

    @Transactional(readOnly = true)
    public CursorPage<FollowUpRow> followUps(String range, UUID assignedTo, UUID projectId, String cursorRaw, int limit) {
        TenantContext.Tenant tenant = tenantContextBinder.current();
        // B-13 §9: DATA_VIEW_ALL or DATA_VIEW_OWN -- neither is expressible
        // via the single-permission @RequiresPermission annotation, so (same
        // pattern as CustomerService.assertCanViewAnyLeads for the identical
        // problem) this is an explicit service-layer check. A role with
        // neither (Accounts Staff -- B-07 §9 "no lead access at all") is
        // rejected outright, not silently scoped to an always-empty result.
        if (!tenant.permissions().contains("DATA_VIEW_ALL") && !tenant.permissions().contains("DATA_VIEW_OWN")) {
            throw new com.shardeya.platform.ForbiddenException("error.tracker.noLeadAccess");
        }
        if (projectId != null) {
            accessGuard.assertAccess(projectId);
        }
        Cursor cursor = Cursor.decode(cursorRaw);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orgId", tenant.orgId()).addValue("today", IndianTime.today());

        StringBuilder sql = new StringBuilder("""
                SELECT c.id AS customer_id, c.follow_up_date, c.full_name AS customer_name, c.mobile AS customer_mobile,
                       proj.name AS project_name, c.status, c.assigned_to, u.full_name AS assigned_to_name,
                       (SELECT i.remarks FROM interaction i WHERE i.customer_id = c.id ORDER BY i.occurred_on DESC, i.created_at DESC LIMIT 1) AS last_remark
                FROM customer c
                LEFT JOIN project proj ON proj.id = c.interested_project_id
                LEFT JOIN app_user u ON u.id = c.assigned_to
                WHERE c.org_id = :orgId AND c.deleted_at IS NULL AND c.no_further_follow_up = false
                  AND c.status NOT IN ('DEAL_CLOSED','LOST') AND c.follow_up_date IS NOT NULL
                """);
        // §9: DATA_VIEW_ALL sees everyone (optionally filtered to one
        // assignee); DATA_VIEW_OWN is hard-scoped to the caller regardless
        // of what assignedTo asks for -- the same "server ignores an
        // out-of-scope filter rather than trusting it" rule CustomerService
        // already establishes for the main lead list.
        if (tenant.permissions().contains("DATA_VIEW_ALL")) {
            if (assignedTo != null) {
                sql.append(" AND c.assigned_to = :assignedTo");
                params.addValue("assignedTo", assignedTo);
            }
        } else {
            sql.append(" AND c.assigned_to = :selfId");
            params.addValue("selfId", tenant.userId());
        }
        if (projectId != null) {
            sql.append(" AND c.interested_project_id = :projectId");
            params.addValue("projectId", projectId);
        } else if (!tenant.allProjects()) {
            sql.append(" AND (c.interested_project_id IS NULL OR c.interested_project_id = ANY(:scopeIds))");
            params.addValue("scopeIds", tenant.projectScope().toArray(new UUID[0]));
        }
        applyRange(sql, params, "c.follow_up_date", range);
        if (cursor != null) {
            sql.append(" AND (c.follow_up_date > :cursorDate OR (c.follow_up_date = :cursorDate AND c.id > :cursorId))");
            params.addValue("cursorDate", cursor.createdAt().atZone(IndianTime.ZONE).toLocalDate())
                    .addValue("cursorId", cursor.id());
        }
        sql.append(" ORDER BY c.follow_up_date ASC, c.id ASC LIMIT :limit");
        params.addValue("limit", limit + 1);

        List<FollowUpRow> rows = jdbc.query(sql.toString(), params, (rs, i) -> {
            LocalDate dueDate = rs.getDate("follow_up_date").toLocalDate();
            long daysOverdue = Math.max(0, ChronoUnit.DAYS.between(dueDate, IndianTime.today()));
            return new FollowUpRow((UUID) rs.getObject("customer_id"), dueDate, rs.getString("customer_name"),
                    rs.getString("customer_mobile"), rs.getString("project_name"), rs.getString("status"),
                    (UUID) rs.getObject("assigned_to"), rs.getString("assigned_to_name"), rs.getString("last_remark"), daysOverdue);
        });
        return CursorPage.of(rows, limit, r -> new Cursor(r.followUpDate().atStartOfDay(IndianTime.ZONE).toInstant(), r.customerId()));
    }

    @Transactional(readOnly = true)
    public CursorPage<CollectionRow> collections(String range, UUID projectId, String cursorRaw, int limit) {
        TenantContext.Tenant tenant = tenantContextBinder.current();
        if (projectId != null) {
            accessGuard.assertAccess(projectId);
        }
        Cursor cursor = Cursor.decode(cursorRaw);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orgId", tenant.orgId()).addValue("today", IndianTime.today());

        StringBuilder sql = new StringBuilder("""
                SELECT sch.id AS schedule_id, sch.plot_sale_id, sch.due_date, proj.name AS project_name,
                       pl.plot_number, sale.buyer_name, sale.buyer_mobile,
                       (sch.expected_amount - sch.amount_allocated) AS amount_due, sale.balance_due, sch.reminder_enabled
                FROM payment_schedule sch
                JOIN plot_sale sale ON sale.id = sch.plot_sale_id
                JOIN plot pl ON pl.id = sale.plot_id
                JOIN project proj ON proj.id = sale.project_id
                WHERE sch.org_id = :orgId AND sch.deleted_at IS NULL
                  AND sch.status IN ('PENDING','PARTIALLY_PAID','OVERDUE')
                """);
        if (projectId != null) {
            sql.append(" AND sale.project_id = :projectId");
            params.addValue("projectId", projectId);
        } else if (!tenant.allProjects()) {
            sql.append(" AND sale.project_id = ANY(:scopeIds)");
            params.addValue("scopeIds", tenant.projectScope().toArray(new UUID[0]));
        }
        applyRange(sql, params, "sch.due_date", range);
        if (cursor != null) {
            sql.append(" AND (sch.due_date > :cursorDate OR (sch.due_date = :cursorDate AND sch.id > :cursorId))");
            params.addValue("cursorDate", cursor.createdAt().atZone(IndianTime.ZONE).toLocalDate())
                    .addValue("cursorId", cursor.id());
        }
        sql.append(" ORDER BY sch.due_date ASC, sch.id ASC LIMIT :limit");
        params.addValue("limit", limit + 1);

        // One extra lookup per row (N+1) to resolve buyerOptedIn -- accepted
        // at this page's realistic row counts, same "not a hot path" shape
        // already established for InstalmentReminderParams' own per-row
        // buyer/plot enrichment.
        List<CollectionRow> rows = jdbc.query(sql.toString(), params, (rs, i) -> {
            LocalDate dueDate = rs.getDate("due_date").toLocalDate();
            long daysOverdue = Math.max(0, ChronoUnit.DAYS.between(dueDate, IndianTime.today()));
            boolean buyerOptedIn = buyerWhatsAppOptInService.isOptedIn(tenant.orgId(), rs.getString("buyer_mobile"));
            return new CollectionRow((UUID) rs.getObject("schedule_id"), (UUID) rs.getObject("plot_sale_id"), dueDate,
                    rs.getString("project_name"), rs.getString("plot_number"), rs.getString("buyer_name"),
                    rs.getString("buyer_mobile"), rs.getBigDecimal("amount_due"), daysOverdue,
                    rs.getBigDecimal("balance_due"), rs.getBoolean("reminder_enabled"), buyerOptedIn);
        });
        return CursorPage.of(rows, limit, r -> new Cursor(r.dueDate().atStartOfDay(IndianTime.ZONE).toInstant(), r.scheduleId()));
    }

    @Transactional(readOnly = true)
    public TrackerCounts counts() {
        TenantContext.Tenant tenant = tenantContextBinder.current();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orgId", tenant.orgId()).addValue("today", IndianTime.today());

        StringBuilder followUpSql = new StringBuilder("""
                SELECT COUNT(*) FROM customer c
                WHERE c.org_id = :orgId AND c.deleted_at IS NULL AND c.no_further_follow_up = false
                  AND c.status NOT IN ('DEAL_CLOSED','LOST') AND c.follow_up_date IS NOT NULL AND c.follow_up_date <= :today
                """);
        if (tenant.permissions().contains("DATA_VIEW_ALL")) {
            if (!tenant.allProjects()) {
                followUpSql.append(" AND (c.interested_project_id IS NULL OR c.interested_project_id = ANY(:scopeIds))");
                params.addValue("scopeIds", tenant.projectScope().toArray(new UUID[0]));
            }
        } else if (tenant.permissions().contains("DATA_VIEW_OWN")) {
            followUpSql.append(" AND c.assigned_to = :selfId");
            params.addValue("selfId", tenant.userId());
        } else {
            // Accounts Staff etc. have no lead access at all (B-07 §9) -- 0, not an error, since this is just a badge count.
            followUpSql = new StringBuilder("SELECT 0");
        }
        long followUpCount = jdbc.queryForObject(followUpSql.toString(), params, Long.class);

        long collectionCount = 0;
        if (tenant.permissions().contains("FINANCIAL_VIEW")) {
            StringBuilder collectionSql = new StringBuilder("""
                    SELECT COUNT(*) FROM payment_schedule sch JOIN plot_sale sale ON sale.id = sch.plot_sale_id
                    WHERE sch.org_id = :orgId AND sch.deleted_at IS NULL AND sch.status IN ('PENDING','PARTIALLY_PAID','OVERDUE')
                      AND sch.due_date <= :today
                    """);
            if (!tenant.allProjects()) {
                collectionSql.append(" AND sale.project_id = ANY(:scopeIds2)");
                params.addValue("scopeIds2", tenant.projectScope().toArray(new UUID[0]));
            }
            collectionCount = jdbc.queryForObject(collectionSql.toString(), params, Long.class);
        }
        return new TrackerCounts(followUpCount, collectionCount);
    }

    @Transactional
    public InteractionResponse logFollowUp(UUID customerId, Interaction.Type type, String remarks,
                                            LocalDate nextDate, Interaction.Result result) {
        return interactionService.create(customerId,
                new InteractionCreateRequest(IndianTime.today(), type, remarks, nextDate, result, null, null, null));
    }

    @Transactional
    public InteractionResponse reschedule(UUID customerId, LocalDate newDate, String reason) {
        return interactionService.create(customerId,
                new InteractionCreateRequest(IndianTime.today(), Interaction.Type.NOTE,
                        "Rescheduled: " + reason, newDate, null, null, null, null));
    }

    @Transactional
    public InteractionResponse markDone(UUID customerId, String remarks) {
        // Interaction.Result.NO_FURTHER is exactly what InteractionService.create()
        // already reads to set customer.noFurtherFollowUp=true (M-12's own
        // logic) -- reused here, not reimplemented, per this class's own
        // "delegate, don't duplicate" rule.
        return interactionService.create(customerId,
                new InteractionCreateRequest(IndianTime.today(), Interaction.Type.NOTE, remarks, null,
                        Interaction.Result.NO_FURTHER, null, null, null));
    }

    @Transactional
    public PaymentResponse recordPayment(UUID scheduleId, PaymentCreateRequest req) {
        PaymentSchedule schedule = loadOwnSchedule(scheduleId);
        // If the caller didn't specify allocations, target THIS schedule row
        // explicitly rather than falling through to PaymentService's own
        // auto-allocate-oldest-due-first -- a quick payment clicked from a
        // specific overdue row must apply to THAT row, which isn't
        // necessarily the oldest-due one across the whole sale.
        PaymentCreateRequest effective = (req.allocations() == null || req.allocations().isEmpty())
                ? new PaymentCreateRequest(req.amount(), req.paidOn(), req.mode(), req.reference(), req.remarks(),
                        List.of(new AllocationRequest(scheduleId, req.amount())))
                : req;
        return paymentService.record(schedule.getPlotSaleId(), effective);
    }

    @Transactional
    public void remind(UUID scheduleId, String channel) {
        remindInternal(loadOwnSchedule(scheduleId));
    }

    // Mirrors ScheduleService's own loadOwn()/loadSale() exactly (same
    // package family, identical tenant-isolation shape): PaymentSchedule has
    // no getOrgId() accessor, and the established pattern here relies on RLS
    // as the primary mechanism for the schedule lookup itself, then uses the
    // loaded PlotSale's project for the REAL enforcement layer --
    // ProjectAccessGuard, which TrackerService's action methods were
    // otherwise missing entirely (they had no project-scope check at all
    // before this).
    private PaymentSchedule loadOwnSchedule(UUID scheduleId) {
        PaymentSchedule schedule = scheduleRepository.findByIdAndDeletedAtIsNull(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        PlotSale sale = saleRepository.findByIdAndDeletedAtIsNull(schedule.getPlotSaleId())
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        accessGuard.assertAccess(sale.getProjectId());
        return schedule;
    }

    @Transactional
    public BulkRemindResult bulkRemind(List<UUID> scheduleIds) {
        int sent = 0;
        int skipped = 0;
        for (UUID scheduleId : scheduleIds) {
            PaymentSchedule schedule;
            try {
                schedule = loadOwnSchedule(scheduleId);
            } catch (ResourceNotFoundException e) {
                skipped++;
                continue;
            }
            if (!schedule.isReminderEnabled()) {
                skipped++;
                continue;
            }
            // Dedupe: max one reminder per buyer per 24h (B-08/B-13 §7) --
            // approximated at the schedule-row level via lastReminderSentAt,
            // since payment_schedule has no direct link to a single
            // "buyer" identity beyond its own sale; a buyer with several
            // overdue rows could in principle get more than one reminder in
            // a 24h window under this approximation. Documented simplification,
            // not a silent gap -- see CLAUDE.md's Milestone 5 notes.
            if (schedule.getLastReminderSentAt() != null
                    && schedule.getLastReminderSentAt().isAfter(Instant.now().minusSeconds(86400))) {
                skipped++;
                continue;
            }
            // ConflictException here covers all three guards remindInternal
            // itself throws (disabled, already-sent-today, and -- new --
            // not-opted-in): one bad row in a batch of many should reduce
            // to a skip, not abort the whole sweep, matching this method's
            // own "sent vs skipped" purpose. The first two are already
            // pre-checked above (defense in depth, not the only guard); the
            // opt-in check has no pre-check here since it needs the sale
            // loaded, which remindInternal already does itself.
            try {
                remindInternal(schedule);
                sent++;
            } catch (ConflictException e) {
                skipped++;
            }
        }
        return new BulkRemindResult(sent, skipped);
    }

    private void remindInternal(PaymentSchedule schedule) {
        if (!schedule.isReminderEnabled()) {
            throw new ConflictException("error.tracker.reminderDisabled", java.util.Map.of());
        }
        if (schedule.getLastReminderSentAt() != null
                && schedule.getLastReminderSentAt().isAfter(Instant.now().minusSeconds(86400))) {
            throw new ConflictException("error.tracker.reminderAlreadySentToday", java.util.Map.of());
        }
        PlotSale sale = saleRepository.findByIdAndDeletedAtIsNull(schedule.getPlotSaleId())
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        // Unlike every automated (dispatch-service) WhatsApp send, this one
        // messages a genuine third party (the buyer) directly with no
        // per-user preference layer to lean on -- an active, explicit
        // whatsapp_optin is the ONLY gate. No opt-in row -> not opted in ->
        // hard block, never a silent no-op (the frontend disables this
        // button proactively via CollectionRow.buyerOptedIn, but this check
        // is the real, server-side enforcement CLAUDE.md rule #5 requires).
        if (!buyerWhatsAppOptInService.isOptedIn(sale.getOrgId(), sale.getBuyerMobile())) {
            throw new ConflictException("error.tracker.buyerNotOptedIn", java.util.Map.of());
        }
        BigDecimal amountDue = schedule.getExpectedAmount().subtract(schedule.getAmountAllocated());
        String message = "Hi " + sale.getBuyerName() + ", a payment of INR " + amountDue.toPlainString()
                + " was due on " + schedule.getDueDate() + ". Please arrange payment at your earliest convenience.";
        // Quiet hours (never before 08:00 / after 21:00 IST) already applies
        // here for free -- enqueueWhatsApp stamps available_at centrally for
        // every caller (OutboxService's own javadoc), so this manual,
        // buyer-facing send gets the identical treatment the automated
        // sends already get, with no separate check needed.
        outboxService.enqueueWhatsApp(sale.getOrgId(), "payment_schedule", schedule.getId(), sale.getBuyerMobile(), message, "INSTALMENT_REMINDER");
        schedule.setLastReminderSentAt(Instant.now());
        scheduleRepository.save(schedule);
    }

    private void applyRange(StringBuilder sql, MapSqlParameterSource params, String column, String range) {
        String effectiveRange = range == null ? "all" : range;
        switch (effectiveRange) {
            case "today" -> sql.append(" AND ").append(column).append(" <= :today");
            case "week" -> {
                sql.append(" AND ").append(column).append(" <= :weekEnd");
                params.addValue("weekEnd", IndianTime.today().plusDays(6));
            }
            case "overdue" -> sql.append(" AND ").append(column).append(" < :today");
            case "all" -> {
                // no additional filter
            }
            default -> throw new BadRequestException("range", "RANGE_INVALID", "error.tracker.rangeInvalid");
        }
    }

    public record BulkRemindResult(int sent, int skipped) {
    }
}
