package com.shardeya.builder.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shardeya.builder.dashboard.dto.DashboardCard;
import com.shardeya.builder.dashboard.dto.DashboardResponse;
import com.shardeya.foundation.notification.NotificationService;
import com.shardeya.foundation.notification.dto.NotificationResponse;
import com.shardeya.platform.TenantContext;
import com.shardeya.platform.TenantContextBinder;
import com.shardeya.shared.IndianTime;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * B-01 Builder Dashboard -- all ten §11.1 cards. The whole point of this
 * class, called out explicitly in CLAUDE.md's own pitfall list: the six
 * inventory/lead cards read {@link OrgMetrics} (trigger-maintained,
 * see V5_002), NEVER a live COUNT(*); only the genuinely time-windowed
 * figures (follow-ups today, instalments due this month, deals closed this
 * month, the overdue alert) run a live query, exactly as B-01 §3 itself
 * specifies ("time-window figures... change hourly and are cheap with the
 * right index"). Redis-cached 5 minutes per (org, user) -- user matters
 * because a Sales Executive's numbers are scoped to their own leads.
 */
@Service
public class DashboardService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final OrgMetricsRepository orgMetricsRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final TenantContextBinder tenantContextBinder;
    private final NotificationService notificationService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public DashboardService(OrgMetricsRepository orgMetricsRepository, NamedParameterJdbcTemplate jdbc,
                             TenantContextBinder tenantContextBinder, NotificationService notificationService,
                             StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.orgMetricsRepository = orgMetricsRepository;
        this.jdbc = jdbc;
        this.tenantContextBinder = tenantContextBinder;
        this.notificationService = notificationService;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        TenantContext.Tenant tenant = tenantContextBinder.current();
        String cacheKey = "dashboard:" + tenant.orgId() + ":" + tenant.userId();
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, DashboardResponse.class);
            } catch (Exception ignored) {
                // Fall through and recompute -- a bad cache entry should never break the dashboard.
            }
        }
        DashboardResponse response = compute(tenant);
        try {
            redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(response), CACHE_TTL);
        } catch (Exception ignored) {
            // Caching is an optimisation, not a correctness requirement -- never fail the request over it.
        }
        return response;
    }

    private DashboardResponse compute(TenantContext.Tenant tenant) {
        OrgMetrics metrics = orgMetricsRepository.findByOrgId(tenant.orgId()).orElse(null);
        boolean viewAll = tenant.permissions().contains("DATA_VIEW_ALL");
        boolean viewOwn = tenant.permissions().contains("DATA_VIEW_OWN");
        boolean financial = tenant.permissions().contains("FINANCIAL_VIEW");
        int totalProjects = metrics == null ? 0 : metrics.getTotalProjects();
        boolean isOnboarding = totalProjects == 0;

        Map<String, DashboardCard> cards = new LinkedHashMap<>();
        if (metrics != null) {
            cards.put("totalProjects", DashboardCard.of(metrics.getTotalProjects(), "/builder/projects"));
            cards.put("totalPlots", DashboardCard.of(metrics.getTotalPlots(), "/builder/projects"));
            cards.put("availablePlots", DashboardCard.of(metrics.getAvailablePlots(), "/builder/projects?plotStatus=AVAILABLE"));
            cards.put("soldPlots", DashboardCard.of(metrics.getSoldPlots(), "/builder/projects?plotStatus=SOLD"));
            cards.put("reservedPlots", DashboardCard.of(metrics.getReservedPlots(), "/builder/projects?plotStatus=RESERVED"));
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orgId", tenant.orgId()).addValue("today", IndianTime.today())
                .addValue("monthStart", IndianTime.today().withDayOfMonth(1))
                .addValue("monthEnd", IndianTime.today().withDayOfMonth(IndianTime.today().lengthOfMonth()));

        // activeLeads: DATA_VIEW_ALL reads the trigger-maintained org-wide
        // total (org_metrics); DATA_VIEW_OWN needs a per-user count instead,
        // which org_metrics doesn't carry -- a live query here is still safe
        // (not the pitfall the "never COUNT(*)" rule warns about) because
        // it's a single indexed lookup on ix_cust_assigned for exactly one
        // user, not an unindexed full-table scan repeated on every render.
        if (viewAll && metrics != null) {
            cards.put("activeLeads", DashboardCard.of(metrics.getActiveLeads(), "/builder/customers"));
        } else if (viewOwn) {
            long ownLeads = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM customer WHERE org_id=:orgId AND deleted_at IS NULL
                      AND assigned_to=:selfId AND status NOT IN ('DEAL_CLOSED','LOST')
                    """, params.addValue("selfId", tenant.userId()), Long.class);
            cards.put("activeLeads", DashboardCard.of(ownLeads, "/builder/customers?tab=mine"));
        }

        if (viewAll || viewOwn) {
            String followUpSql = "SELECT COUNT(*) FROM customer WHERE org_id=:orgId AND deleted_at IS NULL " +
                    "AND no_further_follow_up=false AND status NOT IN ('DEAL_CLOSED','LOST') " +
                    "AND follow_up_date IS NOT NULL AND follow_up_date <= :today" +
                    (viewAll ? "" : " AND assigned_to=:selfId");
            long followUpsToday = jdbc.queryForObject(followUpSql, params, Long.class);
            cards.put("followUpsToday", DashboardCard.of(followUpsToday, "/builder/tracker?tab=followups&range=today"));
        }

        if (financial) {
            var instalRow = jdbc.queryForMap("""
                    SELECT COUNT(*) AS cnt, COALESCE(SUM(expected_amount - amount_allocated),0) AS amt
                    FROM payment_schedule WHERE org_id=:orgId AND deleted_at IS NULL
                      AND status IN ('PENDING','PARTIALLY_PAID','OVERDUE') AND due_date BETWEEN :monthStart AND :monthEnd
                    """, params);
            cards.put("instalmentsDueMonth", DashboardCard.ofCountAndAmount(
                    ((Number) instalRow.get("cnt")).longValue(), (BigDecimal) instalRow.get("amt"),
                    "/builder/financials?tab=pending"));
            cards.put("totalRevenue", DashboardCard.ofAmount(
                    metrics == null ? BigDecimal.ZERO : metrics.getTotalRevenue(), "/builder/financials"));
        }

        String dealsSql = "SELECT COUNT(*) FROM plot_sale WHERE org_id=:orgId AND deleted_at IS NULL " +
                "AND status='COMPLETED' AND purchase_date BETWEEN :monthStart AND :monthEnd" +
                (viewAll ? "" : " AND handled_by=:selfId2");
        long dealsClosedMonth = jdbc.queryForObject(dealsSql,
                params.addValue("selfId2", tenant.userId()), Long.class);
        cards.put("dealsClosedMonth", DashboardCard.of(dealsClosedMonth, "/builder/deals?range=month"));

        List<DashboardResponse.DashboardAlert> alerts = new java.util.ArrayList<>();
        if (financial) {
            // Computed live off due_date, NOT status='OVERDUE' -- that enum
            // value only gets refreshed once daily by OverdueScheduleSweeper
            // (M3, 9am IST cron), so a schedule that crossed its due date
            // since the last run would be silently missing from this alert
            // for up to 24h. Mirrors FinancialService.summary()'s identical
            // fix and Tracker's own live due-date computation -- all three
            // surfaces must agree on the same underlying fact.
            var overdueRow = jdbc.queryForMap("""
                    SELECT COUNT(*) AS cnt, COALESCE(SUM(expected_amount - amount_allocated),0) AS amt
                    FROM payment_schedule WHERE org_id=:orgId AND deleted_at IS NULL
                      AND status IN ('PENDING','PARTIALLY_PAID','OVERDUE') AND due_date < :today
                    """, params);
            long overdueCount = ((Number) overdueRow.get("cnt")).longValue();
            if (overdueCount > 0) {
                alerts.add(new DashboardResponse.DashboardAlert("OVERDUE_INSTALMENTS", overdueCount, (BigDecimal) overdueRow.get("amt")));
            }
        }

        return new DashboardResponse(cards, alerts, Instant.now(), isOnboarding, !tenant.allProjects(),
                tenant.allProjects() ? totalProjects : tenant.projectScope().size(), totalProjects);
    }

    public List<NotificationResponse> activity(int limit) {
        // No dedicated audit/activity-log table exists yet (M-13, a later
        // milestone) -- the notification table is the closest real source
        // of "things that just happened" this app already has (every
        // business event that fires a notification also lands here), so
        // the activity feed reuses the caller's own notification history
        // rather than standing up a parallel audit trail early. Revisit once
        // M-13 exists; DATA_VIEW_ALL holders (Admin/Manager) already receive
        // a copy of every broadcast event via createForOrg, so this reads as
        // a reasonable org activity feed for them today, not just "my own inbox".
        return notificationService.listMine(Math.min(Math.max(limit, 1), 50));
    }
}
