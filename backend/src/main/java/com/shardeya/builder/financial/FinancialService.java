package com.shardeya.builder.financial;

import com.shardeya.builder.financial.dto.FinancialPaymentRow;
import com.shardeya.builder.financial.dto.FinancialSummaryResponse;
import com.shardeya.builder.financial.dto.PendingInstalmentRow;
import com.shardeya.builder.financial.dto.RevenueTrendPoint;
import com.shardeya.platform.BadRequestException;
import com.shardeya.platform.Cursor;
import com.shardeya.platform.CursorPage;
import com.shardeya.platform.ProjectAccessGuard;
import com.shardeya.platform.TenantContext;
import com.shardeya.platform.TenantContextBinder;
import com.shardeya.shared.IndianTime;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * B-08 Financials / Account Manager -- a read/aggregation module over
 * payment_record/payment_schedule/plot_sale, no owned tables of its own.
 * Uses live queries throughout (not org_metrics, which is B-01's dashboard-
 * specific aggregate, or mv_org_revenue_monthly beyond the trend chart it's
 * shaped for) -- see CLAUDE.md's Milestone 5 notes for why summary cards are
 * computed live here rather than from the materialised view: correctness
 * over the view's 15-minute staleness, given this milestone's realistic
 * data volumes.
 */
@Service
public class FinancialService {

    private final NamedParameterJdbcTemplate jdbc;
    private final TenantContextBinder tenantContextBinder;
    private final ProjectAccessGuard accessGuard;

    public FinancialService(NamedParameterJdbcTemplate jdbc, TenantContextBinder tenantContextBinder,
                             ProjectAccessGuard accessGuard) {
        this.jdbc = jdbc;
        this.tenantContextBinder = tenantContextBinder;
        this.accessGuard = accessGuard;
    }

    @Transactional(readOnly = true)
    public FinancialSummaryResponse summary(UUID projectId, LocalDate from, LocalDate to) {
        TenantContext.Tenant tenant = tenantContextBinder.current();
        if (projectId != null) {
            accessGuard.assertAccess(projectId);
        }
        LocalDate today = IndianTime.today();
        LocalDate effectiveTo = to != null ? to : today;
        LocalDate effectiveFrom = from != null ? from : effectiveTo.minusYears(5);
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new BadRequestException("from", "DATE_RANGE_INVALID", "error.financial.dateRangeInvalid");
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orgId", tenant.orgId())
                .addValue("today", today)
                .addValue("monthStart", today.withDayOfMonth(1))
                .addValue("yearStart", LocalDate.of(today.getYear(), 1, 1));
        String saleScope = scopeClause(tenant, projectId, params, "project_id");
        // payment_schedule has no project_id column of its own (only
        // plot_sale_id) -- project scoping here requires joining to
        // plot_sale, unlike payment_record/plot_sale which both have the
        // column directly. A real bug: this previously referenced the
        // nonexistent "ps.project_id", which only threw once someone
        // actually used the project filter (org-wide summary never hits
        // this branch, since scopeClause() returns "" with no projectId).
        String scheduleScope = scopeClause(tenant, projectId, params, "sale.project_id");
        String paymentScope = scopeClause(tenant, projectId, params, "project_id");

        BigDecimal totalRevenueAllTime = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount),0) FROM payment_record WHERE org_id=:orgId" + paymentScope,
                params, BigDecimal.class);
        BigDecimal revenueThisMonth = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount),0) FROM payment_record WHERE org_id=:orgId AND paid_on >= :monthStart AND paid_on <= :today" + paymentScope,
                params, BigDecimal.class);
        BigDecimal revenueThisYear = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount),0) FROM payment_record WHERE org_id=:orgId AND paid_on >= :yearStart AND paid_on <= :today" + paymentScope,
                params, BigDecimal.class);
        BigDecimal pendingCollections = jdbc.queryForObject(
                "SELECT COALESCE(SUM(balance_due),0) FROM plot_sale WHERE org_id=:orgId AND status <> 'CANCELLED' AND deleted_at IS NULL" + saleScope,
                params, BigDecimal.class);

        // Computed live off due_date, NOT ps.status='OVERDUE' -- that enum
        // value is only refreshed once daily by OverdueScheduleSweeper
        // (M3, 9am IST cron), so a schedule that crossed its due date since
        // the last run would be silently excluded from this figure for up
        // to 24h even though it's genuinely overdue. Tracker's own
        // Collection tab already computes overdue this same live way
        // (TrackerService.applyRange's "overdue" case); this mirrors it so
        // the two surfaces never disagree on the same underlying fact.
        var overdueRow = jdbc.queryForMap(
                "SELECT COALESCE(SUM(ps.expected_amount - ps.amount_allocated),0) AS amt, COUNT(*) AS cnt " +
                        "FROM payment_schedule ps JOIN plot_sale sale ON sale.id = ps.plot_sale_id " +
                        "WHERE ps.org_id=:orgId AND ps.status IN ('PENDING','PARTIALLY_PAID','OVERDUE') " +
                        "AND ps.due_date < :today AND ps.deleted_at IS NULL" + scheduleScope,
                params);
        FinancialSummaryResponse.OverdueSummary overdue = new FinancialSummaryResponse.OverdueSummary(
                (BigDecimal) overdueRow.get("amt"), ((Number) overdueRow.get("cnt")).longValue());

        List<FinancialSummaryResponse.ModeBreakdown> breakdown = jdbc.query(
                "SELECT mode, COALESCE(SUM(amount),0) AS amt, COUNT(*) AS cnt FROM payment_record " +
                        "WHERE org_id=:orgId AND paid_on >= :effectiveFrom AND paid_on <= :effectiveTo" + paymentScope + " GROUP BY mode",
                new MapSqlParameterSource(params.getValues())
                        .addValue("effectiveFrom", effectiveFrom).addValue("effectiveTo", effectiveTo),
                (rs, i) -> new FinancialSummaryResponse.ModeBreakdown(rs.getString("mode"), rs.getBigDecimal("amt"), rs.getLong("cnt")));

        int totalProjectCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM project WHERE org_id=:orgId AND deleted_at IS NULL", params, Integer.class);

        return new FinancialSummaryResponse(totalRevenueAllTime, revenueThisMonth, revenueThisYear, pendingCollections,
                overdue, BigDecimal.ZERO, breakdown, !tenant.allProjects(),
                tenant.allProjects() ? totalProjectCount : tenant.projectScope().size(), totalProjectCount);
    }

    @Transactional(readOnly = true)
    public CursorPage<FinancialPaymentRow> payments(UUID projectId, LocalDate from, LocalDate to, String mode,
                                                     String cursorRaw, int limit) {
        TenantContext.Tenant tenant = tenantContextBinder.current();
        if (projectId != null) {
            accessGuard.assertAccess(projectId);
        }
        Cursor cursor = Cursor.decode(cursorRaw);
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("orgId", tenant.orgId());
        String scope = scopeClause(tenant, projectId, params, "pr.project_id");

        StringBuilder sql = new StringBuilder("""
                SELECT pr.id, pr.paid_on, proj.name AS project_name, pl.plot_number, ps.buyer_name,
                       (SELECT COUNT(DISTINCT pa.payment_schedule_id) FROM payment_allocation pa WHERE pa.payment_record_id = pr.id AND pa.deleted_at IS NULL) AS schedule_count,
                       (SELECT sch.sequence_no FROM payment_allocation pa JOIN payment_schedule sch ON sch.id = pa.payment_schedule_id
                          WHERE pa.payment_record_id = pr.id AND pa.deleted_at IS NULL LIMIT 1) AS seq_no,
                       pr.amount, pr.mode, pr.reference, pr.cheque_status, u.full_name AS recorded_by_name,
                       pr.reverses_payment_id, pr.remarks, pr.created_at,
                       EXISTS (
                           SELECT 1 FROM payment_record orig
                           WHERE orig.id = pr.reverses_payment_id AND orig.mode = 'CHEQUE' AND orig.cheque_status = 'BOUNCED'
                       ) AS due_to_cheque_bounce
                FROM payment_record pr
                JOIN plot_sale ps ON ps.id = pr.plot_sale_id
                JOIN plot pl ON pl.id = pr.plot_id
                JOIN project proj ON proj.id = pr.project_id
                LEFT JOIN app_user u ON u.id = pr.received_by
                WHERE pr.org_id = :orgId
                """);
        sql.append(scope);
        if (from != null) {
            sql.append(" AND pr.paid_on >= :from");
            params.addValue("from", from);
        }
        if (to != null) {
            sql.append(" AND pr.paid_on <= :to");
            params.addValue("to", to);
        }
        if (mode != null) {
            // pr.mode is a Postgres enum (payment_mode); a bound
            // PreparedStatement parameter arrives typed as varchar, and
            // Postgres has no payment_mode = varchar operator without an
            // explicit cast (string literals embedded directly in SQL, like
            // the status IN ('PENDING', ...) lists elsewhere in this class,
            // don't hit this because an untyped literal can be cast
            // implicitly -- a bound parameter can't).
            sql.append(" AND pr.mode = CAST(:mode AS payment_mode)");
            params.addValue("mode", mode);
        }
        if (cursor != null) {
            sql.append(" AND (pr.created_at < :cursorCreatedAt OR (pr.created_at = :cursorCreatedAt AND pr.id < :cursorId))");
            params.addValue("cursorCreatedAt", cursor.createdAt()).addValue("cursorId", cursor.id());
        }
        sql.append(" ORDER BY pr.created_at DESC, pr.id DESC LIMIT :limit");
        params.addValue("limit", limit + 1);

        List<FinancialPaymentRow> rows = jdbc.query(sql.toString(), params, (rs, i) -> {
            long scheduleCount = rs.getLong("schedule_count");
            Object seqObj = rs.getObject("seq_no");
            Short seq = (scheduleCount == 1 && seqObj != null) ? rs.getShort("seq_no") : null;
            return new FinancialPaymentRow(
                    (UUID) rs.getObject("id"), rs.getDate("paid_on").toLocalDate(), rs.getString("project_name"),
                    rs.getString("plot_number"), rs.getString("buyer_name"), seq, rs.getBigDecimal("amount"),
                    rs.getString("mode"), rs.getString("reference"), rs.getString("cheque_status"),
                    rs.getString("recorded_by_name"), rs.getObject("reverses_payment_id") != null,
                    rs.getBoolean("due_to_cheque_bounce"), rs.getString("remarks"),
                    rs.getTimestamp("created_at").toInstant());
        });
        return CursorPage.of(rows, limit, r -> new Cursor(r.createdAt(), r.id()));
    }

    @Transactional(readOnly = true)
    public CursorPage<PendingInstalmentRow> pending(UUID projectId, boolean overdueOnly, String cursorRaw, int limit) {
        TenantContext.Tenant tenant = tenantContextBinder.current();
        if (projectId != null) {
            accessGuard.assertAccess(projectId);
        }
        Cursor cursor = Cursor.decode(cursorRaw);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orgId", tenant.orgId()).addValue("today", IndianTime.today());
        String scope = scopeClause(tenant, projectId, params, "sale.project_id");

        StringBuilder sql = new StringBuilder("""
                SELECT sch.id AS schedule_id, sch.plot_sale_id, sale.buyer_name, sale.buyer_mobile,
                       proj.name AS project_name, pl.plot_number,
                       (sch.expected_amount - sch.amount_allocated) AS amount_due, sch.due_date, sch.status,
                       sale.balance_due, sch.reminder_enabled
                FROM payment_schedule sch
                JOIN plot_sale sale ON sale.id = sch.plot_sale_id
                JOIN plot pl ON pl.id = sale.plot_id
                JOIN project proj ON proj.id = sale.project_id
                WHERE sch.org_id = :orgId AND sch.deleted_at IS NULL
                  AND sch.status IN ('PENDING','PARTIALLY_PAID','OVERDUE')
                """);
        sql.append(scope);
        // 03-BUILDER-MODULES.md B-08 §14.3, §7: "Pending vs Overdue are
        // distinct: pending = not yet due; overdue = past due date and
        // unpaid" -- these two calls must be mutually exclusive, not two
        // overlapping views of the same unpaid rows. Live due_date check,
        // not status='OVERDUE' -- see the identical comment on the
        // summary() overdue query above for why.
        sql.append(overdueOnly ? " AND sch.due_date < :today" : " AND sch.due_date >= :today");
        if (cursor != null) {
            sql.append(" AND (sch.due_date > :cursorDate OR (sch.due_date = :cursorDate AND sch.id > :cursorId))");
            params.addValue("cursorDate", cursor.createdAt().atZone(IndianTime.ZONE).toLocalDate())
                    .addValue("cursorId", cursor.id());
        }
        sql.append(" ORDER BY sch.due_date ASC, sch.id ASC LIMIT :limit");
        params.addValue("limit", limit + 1);

        List<PendingInstalmentRow> rows = jdbc.query(sql.toString(), params, (rs, i) -> {
            LocalDate dueDate = rs.getDate("due_date").toLocalDate();
            long daysOverdue = Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(dueDate, IndianTime.today()));
            return new PendingInstalmentRow(
                    (UUID) rs.getObject("schedule_id"), (UUID) rs.getObject("plot_sale_id"), rs.getString("buyer_name"),
                    rs.getString("buyer_mobile"), rs.getString("project_name"), rs.getString("plot_number"),
                    rs.getBigDecimal("amount_due"), dueDate, daysOverdue, rs.getString("status"),
                    rs.getBigDecimal("balance_due"), rs.getBoolean("reminder_enabled"));
        });
        // Cursor.createdAt is reused here to carry a LocalDate (via
        // atStartOfDay) since this list sorts by due_date, not created_at,
        // unlike every other cursor-paginated list in this codebase --
        // Cursor's shape (Instant, UUID) is generic enough to double for this
        // rather than inventing a parallel date-based cursor type.
        return CursorPage.of(rows, limit, r -> new Cursor(r.dueDate().atStartOfDay(IndianTime.ZONE).toInstant(), r.scheduleId()));
    }

    @Transactional(readOnly = true)
    public List<RevenueTrendPoint> revenueTrend(UUID projectId, int months) {
        TenantContext.Tenant tenant = tenantContextBinder.current();
        if (projectId != null) {
            accessGuard.assertAccess(projectId);
        }
        int clampedMonths = Math.min(Math.max(months, 1), 36);
        LocalDate start = IndianTime.today().minusMonths(clampedMonths - 1L).withDayOfMonth(1);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orgId", tenant.orgId()).addValue("start", start);
        // B-08 §14.4: "project, date range, payment mode, status -- cascades
        // to every card and table on the page." This chart was the one
        // exception -- it never accepted a projectId at all, so selecting a
        // project filtered the summary cards but left the trend chart
        // showing the org-wide total underneath, which looks (and was
        // reported) exactly like "the chart ignores my selection" even
        // though the numbers were individually correct.
        String scope = scopeClause(tenant, projectId, params, "project_id");
        // Live query, not mv_org_revenue_monthly directly -- the view can be
        // up to 15 minutes stale (B-08 §10), and a 12-month live aggregate
        // over payment_record is cheap enough at this milestone's data
        // volumes that the extra correctness isn't worth the staleness risk.
        // Revisit (read from the view + live-compute only the current month)
        // if this ever needs to scale past that.
        List<RevenueTrendPoint> rows = jdbc.query("""
                        SELECT to_char(date_trunc('month', paid_on), 'YYYY-MM') AS month,
                               COALESCE(SUM(amount),0) AS collected, COUNT(*) AS cnt
                        FROM payment_record
                        WHERE org_id = :orgId AND paid_on >= :start
                        """ + scope + " GROUP BY 1 ORDER BY 1",
                params, (rs, i) -> new RevenueTrendPoint(rs.getString("month"), rs.getBigDecimal("collected"), rs.getLong("cnt")));

        // Fill in months with zero activity so the 12-bar chart never has gaps.
        List<RevenueTrendPoint> filled = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        LocalDate cursorMonth = start;
        int idx = 0;
        while (!cursorMonth.isAfter(IndianTime.today())) {
            String key = cursorMonth.format(fmt);
            if (idx < rows.size() && rows.get(idx).month().equals(key)) {
                filled.add(rows.get(idx));
                idx++;
            } else {
                filled.add(new RevenueTrendPoint(key, BigDecimal.ZERO, 0));
            }
            cursorMonth = cursorMonth.plusMonths(1);
        }
        return filled;
    }

    private String scopeClause(TenantContext.Tenant tenant, UUID projectId, MapSqlParameterSource params, String projectColumn) {
        if (projectId != null) {
            params.addValue("scopeProjectId", projectId);
            return " AND " + projectColumn + " = :scopeProjectId";
        }
        if (!tenant.allProjects()) {
            params.addValue("scopeIds", tenant.projectScope().toArray(new UUID[0]));
            return " AND " + projectColumn + " = ANY(:scopeIds)";
        }
        return "";
    }
}
