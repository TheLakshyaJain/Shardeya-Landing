package com.shardeya.builder.stats;

import com.shardeya.builder.stats.dto.BreakdownSlice;
import com.shardeya.builder.stats.dto.BrokerRanking;
import com.shardeya.builder.stats.dto.CollectionVsTargetPoint;
import com.shardeya.builder.stats.dto.FunnelStage;
import com.shardeya.builder.stats.dto.MonthPoint;
import com.shardeya.builder.stats.dto.StaffPerformanceRow;
import com.shardeya.builder.stats.dto.StatsOverviewResponse;
import com.shardeya.foundation.subscription.EntitlementService;
import com.shardeya.platform.BadRequestException;
import com.shardeya.platform.ProjectAccessGuard;
import com.shardeya.platform.TenantContext;
import com.shardeya.platform.TenantContextBinder;
import com.shardeya.shared.IndianTime;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * B-15 §7. Four charts read straight from the 15-min-refreshed materialised
 * views exactly as designed (monthly-sales, revenue-by-project, top-brokers/
 * leads-by-source when unfiltered, staff-performance); plot-status-breakdown,
 * conversion-funnel (cohort, never current-status), collection-vs-target,
 * and overdue-trend are always live -- see CLAUDE.md for the reasoning
 * behind each choice, particularly why the funnel can never be a status
 * snapshot (B-15 §7's own explicit warning).
 */
@Service
public class StatsService {

    private final NamedParameterJdbcTemplate jdbc;
    private final TenantContextBinder tenantContextBinder;
    private final ProjectAccessGuard accessGuard;
    private final EntitlementService entitlementService;
    private final StatsRefreshJob refreshJob;

    public StatsService(NamedParameterJdbcTemplate jdbc, TenantContextBinder tenantContextBinder, ProjectAccessGuard accessGuard,
                         EntitlementService entitlementService, StatsRefreshJob refreshJob) {
        this.jdbc = jdbc;
        this.tenantContextBinder = tenantContextBinder;
        this.accessGuard = accessGuard;
        this.entitlementService = entitlementService;
        this.refreshJob = refreshJob;
    }

    @Transactional(readOnly = true)
    public StatsOverviewResponse overview(UUID projectId, LocalDate from, LocalDate to) {
        TenantContext.Tenant tenant = tenant(projectId);
        LocalDate effTo = to != null ? to : IndianTime.today();
        LocalDate effFrom = from != null ? from : effTo.minusYears(5);

        MapSqlParameterSource params = params(tenant.orgId(), projectId, effFrom, effTo);
        String saleScope = "s.org_id = :orgId AND s.deleted_at IS NULL AND s.purchase_date BETWEEN :from AND :to"
                + (projectId != null ? " AND s.project_id = :projectId" : "");

        String leadProjectFilter = projectId != null ? " AND interested_project_id = :projectId" : "";
        BigDecimal conversionRate = jdbc.queryForObject("""
                SELECT CASE WHEN COUNT(*) = 0 THEN NULL ELSE
                    ROUND(100.0 * COUNT(*) FILTER (WHERE status = 'DEAL_CLOSED') / COUNT(*), 1) END
                FROM customer WHERE org_id = :orgId AND deleted_at IS NULL AND created_at::date BETWEEN :from AND :to %s
                """.formatted(leadProjectFilter), params, BigDecimal.class);

        BigDecimal avgDealValue = jdbc.queryForObject(
                "SELECT ROUND(AVG(s.deal_value), 2) FROM plot_sale s WHERE " + saleScope + " AND s.status <> 'CANCELLED'",
                params, BigDecimal.class);

        // Plain date - date subtraction (an integer day count) rather than
        // date - timestamptz, which would otherwise need EXTRACT(DAY FROM
        // interval) and depend on how Postgres breaks the resulting
        // interval into days vs months -- simpler and unambiguous.
        //
        // %s-and-.formatted() throughout this class, deliberately, not
        // `""" + var + """` splicing across a line boundary: Java text
        // blocks strip trailing whitespace from every line independently
        // per segment, so a continuation relying on a single leading/
        // trailing space to separate it from the next segment can silently
        // lose that space if it happens to be the shortest-indented line in
        // its segment. Caught live here first ("WHERE " -> "WHERE", glued
        // straight onto "s.org_id" as "WHEREs.org_id", a genuine
        // BadSqlGrammarException no amount of reading the code would have
        // caught) then found repeated three more times in
        // collectionVsTarget() once the pattern was recognized and checked
        // for elsewhere in this same class.
        Double avgDaysToClose = jdbc.queryForObject("""
                SELECT AVG(s.purchase_date - c.created_at::date)
                FROM plot_sale s JOIN customer c ON c.id = s.customer_id
                WHERE %s AND s.status <> 'CANCELLED'
                """.formatted(saleScope), params, Double.class);

        String saleProjectFilter = projectId != null ? " AND s.project_id = :projectId" : "";
        BigDecimal collectionEfficiency = jdbc.queryForObject("""
                SELECT CASE WHEN SUM(sch.expected_amount) = 0 OR SUM(sch.expected_amount) IS NULL THEN NULL ELSE
                    ROUND(100.0 * SUM(sch.amount_allocated) / SUM(sch.expected_amount), 1) END
                FROM payment_schedule sch JOIN plot_sale s ON s.id = sch.plot_sale_id
                WHERE s.org_id = :orgId AND sch.deleted_at IS NULL AND s.deleted_at IS NULL
                  AND sch.due_date BETWEEN :from AND :to %s
                """.formatted(saleProjectFilter),
                params, BigDecimal.class);

        return new StatsOverviewResponse(conversionRate, avgDealValue, avgDaysToClose, collectionEfficiency, refreshJob.lastRefreshedAt());
    }

    @Transactional(readOnly = true)
    public List<MonthPoint> monthlySales(UUID projectId, int months) {
        assertTier("monthly-sales");
        TenantContext.Tenant tenant = tenant(projectId);
        MapSqlParameterSource params = new MapSqlParameterSource("orgId", tenant.orgId())
                .addValue("since", YearMonth.now().minusMonths(months - 1L).atDay(1));
        String where = "org_id = :orgId AND month >= :since" + (projectId != null ? " AND project_id = :projectId" : "");
        if (projectId != null) params.addValue("projectId", projectId);
        String sql = "SELECT to_char(month, 'YYYY-MM') AS m, COALESCE(SUM(sale_value),0) AS v, COALESCE(SUM(plots_sold),0) AS c "
                + "FROM mv_monthly_sales WHERE " + where + " GROUP BY month ORDER BY month";
        return fillMonthGaps(jdbc.query(sql, params, (rs, i) -> new MonthPoint(rs.getString("m"), rs.getBigDecimal("v"), rs.getLong("c"))), months);
    }

    @Transactional(readOnly = true)
    public List<MonthPoint> monthlyRevenue(UUID projectId, int months) {
        assertTier("monthly-revenue");
        TenantContext.Tenant tenant = tenant(projectId);
        MapSqlParameterSource params = new MapSqlParameterSource("orgId", tenant.orgId())
                .addValue("since", YearMonth.now().minusMonths(months - 1L).atDay(1));
        String where = "org_id = :orgId AND paid_on >= :since" + (projectId != null ? " AND project_id = :projectId" : "");
        if (projectId != null) params.addValue("projectId", projectId);
        // Live, not a matview -- "revenue" here means actual cash collected
        // (SUM(payment_record.amount), reversals net out naturally since
        // they're already negative rows), a genuinely different figure from
        // mv_monthly_sales' deal_value at time of sale.
        String sql = "SELECT to_char(date_trunc('month', paid_on), 'YYYY-MM') AS m, COALESCE(SUM(amount),0) AS v, COUNT(*) AS c "
                + "FROM payment_record WHERE " + where + " GROUP BY 1 ORDER BY 1";
        return fillMonthGaps(jdbc.query(sql, params, (rs, i) -> new MonthPoint(rs.getString("m"), rs.getBigDecimal("v"), rs.getLong("c"))), months);
    }

    @Transactional(readOnly = true)
    public List<BreakdownSlice> plotStatusBreakdown(UUID projectId) {
        assertTier("plot-status-breakdown");
        TenantContext.Tenant tenant = tenant(projectId);
        MapSqlParameterSource params = new MapSqlParameterSource("orgId", tenant.orgId());
        String where = "org_id = :orgId AND deleted_at IS NULL" + (projectId != null ? " AND project_id = :projectId" : "");
        if (projectId != null) params.addValue("projectId", projectId);
        String sql = "SELECT status, COUNT(*) AS c FROM plot WHERE " + where + " GROUP BY status";
        return jdbc.query(sql, params, (rs, i) -> new BreakdownSlice(rs.getString("status"), rs.getLong("c"), null));
    }

    @Transactional(readOnly = true)
    public List<BreakdownSlice> leadsBySource(UUID projectId, LocalDate from, LocalDate to) {
        assertTier("leads-by-source");
        TenantContext.Tenant tenant = tenant(projectId);
        MapSqlParameterSource params = new MapSqlParameterSource("orgId", tenant.orgId());
        if (projectId != null) params.addValue("projectId", projectId);
        if (from == null && to == null) {
            // Fast path: the 15-min-refreshed snapshot, exactly as designed.
            String where = "org_id = :orgId" + (projectId != null ? " AND project_id = :projectId" : "");
            String sql = "SELECT source, SUM(lead_count) AS c FROM mv_lead_funnel WHERE " + where + " GROUP BY source";
            return jdbc.query(sql, params, (rs, i) -> new BreakdownSlice(rs.getString("source"), rs.getLong("c"), null));
        }
        // A date range was asked for -- mv_lead_funnel has no date
        // dimension at all, so this falls back to a live query rather than
        // silently ignoring the filter.
        LocalDate effFrom = from != null ? from : LocalDate.of(2000, 1, 1);
        LocalDate effTo = to != null ? to : IndianTime.today();
        params.addValue("from", effFrom).addValue("to", effTo);
        String where = "org_id = :orgId AND deleted_at IS NULL AND created_at::date BETWEEN :from AND :to"
                + (projectId != null ? " AND interested_project_id = :projectId" : "");
        String sql = "SELECT source, COUNT(*) AS c FROM customer WHERE " + where + " GROUP BY source";
        return jdbc.query(sql, params, (rs, i) -> new BreakdownSlice(rs.getString("source"), rs.getLong("c"), null));
    }

    @Transactional(readOnly = true)
    public List<FunnelStage> conversionFunnel(UUID projectId, LocalDate from, LocalDate to) {
        assertTier("conversion-funnel");
        TenantContext.Tenant tenant = tenant(projectId);
        LocalDate effFrom = from != null ? from : IndianTime.today().minusYears(5);
        LocalDate effTo = to != null ? to : IndianTime.today();
        MapSqlParameterSource params = new MapSqlParameterSource("orgId", tenant.orgId()).addValue("from", effFrom).addValue("to", effTo);
        if (projectId != null) params.addValue("projectId", projectId);
        String projectFilter = projectId != null ? " AND interested_project_id = :projectId" : "";

        // Cohort, not current-status counting (B-15 §7's own explicit
        // warning) -- "reached site visit" is answered by a real interaction
        // row (type=VISIT) ever existing for that lead, not by the lead's
        // current status field, which has no history and could have moved
        // past or away from that stage since.
        long interested = count(params, "SELECT COUNT(*) FROM customer WHERE org_id=:orgId AND deleted_at IS NULL "
                + "AND created_at::date BETWEEN :from AND :to" + projectFilter);
        long siteVisit = count(params, "SELECT COUNT(DISTINCT c.id) FROM customer c JOIN interaction i ON i.customer_id = c.id "
                + "WHERE c.org_id=:orgId AND c.deleted_at IS NULL AND c.created_at::date BETWEEN :from AND :to"
                + projectFilter + " AND i.type = 'VISIT'");
        long closed = count(params, "SELECT COUNT(*) FROM customer WHERE org_id=:orgId AND deleted_at IS NULL "
                + "AND created_at::date BETWEEN :from AND :to AND status = 'DEAL_CLOSED'" + projectFilter);

        List<FunnelStage> stages = new ArrayList<>();
        stages.add(new FunnelStage("INTERESTED", interested, interested == 0 ? null : 100.0));
        stages.add(new FunnelStage("SITE_VISIT", siteVisit, interested == 0 ? null : pct(siteVisit, interested)));
        stages.add(new FunnelStage("DEAL_CLOSED", closed, interested == 0 ? null : pct(closed, interested)));
        return stages;
    }

    @Transactional(readOnly = true)
    public List<BrokerRanking> topBrokers(int limit, LocalDate from, LocalDate to) {
        assertTier("top-brokers");
        TenantContext.Tenant tenant = tenant(null);
        MapSqlParameterSource params = new MapSqlParameterSource("orgId", tenant.orgId()).addValue("limit", limit);
        if (from == null && to == null) {
            String sql = """
                    SELECT b.id, b.full_name, SUM(m.deals_closed) AS deals, SUM(m.revenue_generated) AS revenue
                    FROM mv_broker_performance m JOIN broker_partner b ON b.id = m.broker_partner_id
                    WHERE m.org_id = :orgId GROUP BY b.id, b.full_name ORDER BY revenue DESC NULLS LAST LIMIT :limit
                    """;
            return jdbc.query(sql, params, (rs, i) -> new BrokerRanking(java.util.UUID.fromString(rs.getString(1)),
                    rs.getString(2), rs.getLong(3), rs.getBigDecimal(4)));
        }
        LocalDate effFrom = from != null ? from : LocalDate.of(2000, 1, 1);
        LocalDate effTo = to != null ? to : IndianTime.today();
        params.addValue("from", effFrom).addValue("to", effTo);
        String sql = """
                SELECT b.id, b.full_name, COUNT(*) FILTER (WHERE s.status = 'COMPLETED') AS deals,
                       COALESCE(SUM(s.deal_value) FILTER (WHERE s.status <> 'CANCELLED'), 0) AS revenue
                FROM plot_sale s JOIN broker_partner b ON b.id = s.broker_partner_id
                WHERE s.org_id = :orgId AND s.deleted_at IS NULL AND s.purchase_date BETWEEN :from AND :to
                GROUP BY b.id, b.full_name ORDER BY revenue DESC NULLS LAST LIMIT :limit
                """;
        return jdbc.query(sql, params, (rs, i) -> new BrokerRanking(java.util.UUID.fromString(rs.getString(1)),
                rs.getString(2), rs.getLong(3), rs.getBigDecimal(4)));
    }

    @Transactional(readOnly = true)
    public List<BreakdownSlice> revenueByProject(LocalDate from, LocalDate to) {
        assertTier("revenue-by-project");
        TenantContext.Tenant tenant = tenant(null);
        MapSqlParameterSource params = new MapSqlParameterSource("orgId", tenant.orgId());
        LocalDate effFrom = from != null ? from : IndianTime.today().minusYears(5);
        LocalDate effTo = to != null ? to : IndianTime.today();
        params.addValue("from", effFrom).addValue("to", effTo);
        // Real bug: m.month always stores the FIRST of a month, but :from/
        // :to are whatever raw dates the date-range picker sent (e.g. a
        // real fortnight like "Aug 5 - Aug 20"). A plain `m.month BETWEEN
        // :from AND :to` then compares '2026-08-01' against a range that
        // starts on the 5th -> false -> that entire month's real sales
        // silently vanish from the chart, even though sales genuinely
        // happened within the picked range. date_trunc()'ing both bounds
        // to month-start makes "picked any day in that month" correctly
        // include the whole month, matching how a month-bucketed chart's
        // date filter should behave (same normalisation monthlySales()/
        // overdueTrend() already get for free from their own `:since`
        // being computed as a month-start YearMonth, not a raw picked date).
        String sql = """
                SELECT p.name, COALESCE(SUM(m.sale_value), 0) AS v, COALESCE(SUM(m.plots_sold), 0) AS c
                FROM project p LEFT JOIN mv_monthly_sales m ON m.project_id = p.id
                  AND m.month BETWEEN date_trunc('month', CAST(:from AS date)) AND date_trunc('month', CAST(:to AS date))
                WHERE p.org_id = :orgId AND p.deleted_at IS NULL
                GROUP BY p.id, p.name ORDER BY v DESC
                """;
        return jdbc.query(sql, params, (rs, i) -> new BreakdownSlice(rs.getString(1), rs.getLong(3), rs.getBigDecimal(2)));
    }

    @Transactional(readOnly = true)
    public List<CollectionVsTargetPoint> collectionVsTarget(UUID projectId, int months) {
        assertTier("collection-vs-target");
        TenantContext.Tenant tenant = tenant(projectId);
        MapSqlParameterSource params = new MapSqlParameterSource("orgId", tenant.orgId())
                .addValue("since", YearMonth.now().minusMonths(months - 1L).atDay(1));
        if (projectId != null) params.addValue("projectId", projectId);
        String projectFilterA = projectId != null ? " AND r.project_id = :projectId" : "";
        String projectFilterS = projectId != null ? " AND s.project_id = :projectId" : "";
        String projectFilterT = projectId != null ? " AND t.project_id = :projectId" : "";

        // %s placeholders inside ONE text block, filled via .formatted() --
        // NOT `""" + var + """` splicing across line boundaries. That
        // pattern is a real footgun: Java text blocks strip trailing
        // whitespace from every line independently per-segment, so a
        // continuation line meant to start with a leading space (e.g.
        // " GROUP BY 1)," relying on that one space to separate it from
        // the previous segment's concatenated content) can lose it
        // entirely if it happens to be the line with the least
        // indentation in that segment -- confirmed live: this exact shape
        // produced "...projectIdGROUP BY 1)" with no space, a
        // BadSqlGrammarException only a real call caught (see
        // StatsService.overview()'s own avgDaysToClose fix for the
        // simpler, one-off version of the identical mistake).
        String sql = """
                WITH months AS (SELECT generate_series(date_trunc('month', CAST(:since AS date)), date_trunc('month', CURRENT_DATE), interval '1 month')::date AS m),
                actual AS (SELECT date_trunc('month', r.paid_on)::date AS m, SUM(r.amount) AS actual
                           FROM payment_record r WHERE r.org_id = :orgId AND r.paid_on >= CAST(:since AS date) %s
                           GROUP BY 1),
                configured_target AS (SELECT t.month::date AS m, SUM(t.target_amount) AS target
                           FROM collection_target t WHERE t.org_id = :orgId AND t.month >= CAST(:since AS date) %s
                           GROUP BY 1),
                fallback_target AS (SELECT date_trunc('month', sch.due_date)::date AS m, SUM(sch.expected_amount) AS target
                           FROM payment_schedule sch JOIN plot_sale s ON s.id = sch.plot_sale_id
                           WHERE s.org_id = :orgId AND sch.deleted_at IS NULL AND s.deleted_at IS NULL
                             AND sch.due_date >= CAST(:since AS date) %s
                           GROUP BY 1)
                SELECT to_char(months.m, 'YYYY-MM') AS month, COALESCE(actual.actual, 0) AS actual,
                       COALESCE(configured_target.target, fallback_target.target, 0) AS target
                FROM months
                LEFT JOIN actual ON actual.m = months.m
                LEFT JOIN configured_target ON configured_target.m = months.m
                LEFT JOIN fallback_target ON fallback_target.m = months.m
                ORDER BY months.m
                """.formatted(projectFilterA, projectFilterT, projectFilterS);
        return jdbc.query(sql, params, (rs, i) -> new CollectionVsTargetPoint(rs.getString("month"), rs.getBigDecimal("actual"), rs.getBigDecimal("target")));
    }

    @Transactional(readOnly = true)
    public List<MonthPoint> overdueTrend(UUID projectId, int months) {
        assertTier("overdue-trend");
        TenantContext.Tenant tenant = tenant(projectId);
        MapSqlParameterSource params = new MapSqlParameterSource("orgId", tenant.orgId())
                .addValue("since", YearMonth.now().minusMonths(months - 1L).atDay(1))
                .addValue("today", IndianTime.today());
        if (projectId != null) params.addValue("projectId", projectId);
        String projectFilter = projectId != null ? " AND s.project_id = :projectId" : "";
        // Live off due_date, matching CLAUDE.md's own established rule
        // (M5): overdue must never gate on the once-daily-swept status
        // column, always due_date < today, computed fresh every call.
        String sql = """
                SELECT to_char(date_trunc('month', sch.due_date), 'YYYY-MM') AS m,
                       COALESCE(SUM(sch.expected_amount - sch.amount_allocated), 0) AS v, COUNT(*) AS c
                FROM payment_schedule sch JOIN plot_sale s ON s.id = sch.plot_sale_id
                WHERE s.org_id = :orgId AND sch.deleted_at IS NULL AND s.deleted_at IS NULL
                  AND sch.due_date >= CAST(:since AS date) AND sch.due_date < :today
                  AND sch.status IN ('PENDING','PARTIALLY_PAID','OVERDUE') %s
                GROUP BY 1 ORDER BY 1
                """.formatted(projectFilter);
        return fillMonthGaps(jdbc.query(sql, params, (rs, i) -> new MonthPoint(rs.getString("m"), rs.getBigDecimal("v"), rs.getLong("c"))), months);
    }

    @Transactional(readOnly = true)
    public List<StaffPerformanceRow> staffPerformance(LocalDate from, LocalDate to) {
        assertTier("staff-performance");
        TenantContext.Tenant tenant = tenant(null);
        LocalDate effFrom = from != null ? from : IndianTime.today().minusMonths(11);
        LocalDate effTo = to != null ? to : IndianTime.today();
        MapSqlParameterSource params = new MapSqlParameterSource("orgId", tenant.orgId())
                .addValue("from", effFrom.withDayOfMonth(1)).addValue("to", effTo);
        String sql = """
                SELECT u.id, u.full_name, SUM(a.leads_handled) AS leads, SUM(a.deals_closed) AS deals,
                       SUM(a.follow_ups_logged) AS followups, SUM(a.payments_recorded) AS payments
                FROM mv_staff_activity a JOIN app_user u ON u.id = a.user_id
                WHERE a.org_id = :orgId AND a.month BETWEEN :from AND :to
                GROUP BY u.id, u.full_name ORDER BY u.full_name
                """;
        return jdbc.query(sql, params, (rs, i) -> new StaffPerformanceRow(java.util.UUID.fromString(rs.getString(1)),
                rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getLong(5), rs.getLong(6)));
    }

    // --- helpers -----------------------------------------------------------

    private TenantContext.Tenant tenant(UUID projectId) {
        TenantContext.Tenant tenant = tenantContextBinder.current();
        if (projectId != null) accessGuard.assertAccess(projectId);
        return tenant;
    }

    private void assertTier(String chartKey) {
        TenantContext.Tenant tenant = tenantContextBinder.current();
        entitlementService.assertTierAtLeast(tenant.orgId(), AnalyticsTier.KEY, AnalyticsTier.ORDERED,
                AnalyticsTier.minimumFor(chartKey), "error.stats.analyticsTierRequired");
    }

    private MapSqlParameterSource params(UUID orgId, UUID projectId, LocalDate from, LocalDate to) {
        MapSqlParameterSource p = new MapSqlParameterSource("orgId", orgId).addValue("from", from).addValue("to", to);
        if (projectId != null) p.addValue("projectId", projectId);
        return p;
    }

    private long count(MapSqlParameterSource params, String sql) {
        Long v = jdbc.queryForObject(sql, params, Long.class);
        return v == null ? 0 : v;
    }

    private Double pct(long part, long whole) {
        if (whole == 0) return null;
        return BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP).doubleValue();
    }

    // B-15 §10 "very wide date range -> auto-bucket" is trimmed to a
    // months-count cap (1-60, Bean-Validated at the controller) rather than
    // implementing quarter/year auto-bucketing -- a real, deliberate scope
    // trim (see CLAUDE.md). Zero-activity months are filled with 0 rather
    // than omitted, so a chart never silently skips a month.
    private List<MonthPoint> fillMonthGaps(List<MonthPoint> sparse, int months) {
        java.util.Map<String, MonthPoint> byMonth = new java.util.HashMap<>();
        for (MonthPoint p : sparse) byMonth.put(p.month(), p);
        List<MonthPoint> out = new ArrayList<>();
        YearMonth cursor = YearMonth.now().minusMonths(months - 1L);
        for (int i = 0; i < months; i++) {
            String key = cursor.toString();
            out.add(byMonth.getOrDefault(key, new MonthPoint(key, BigDecimal.ZERO, 0)));
            cursor = cursor.plusMonths(1);
        }
        return out;
    }
}
