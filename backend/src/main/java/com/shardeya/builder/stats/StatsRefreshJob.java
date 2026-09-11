package com.shardeya.builder.stats;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * B-15 §3 "refreshed every 15 minutes" + §7 "data freshness shown
 * explicitly ('as of 09:15') -- a builder making a decision needs to know
 * how current the number is". CONCURRENTLY (every view has a unique index
 * for exactly this) so readers are never blocked mid-refresh.
 *
 * <p>{@code lastRefreshedAt} is in-memory only (an {@link AtomicReference},
 * not a DB column) -- lost on restart, same class of simplification as
 * every other "good enough for a single-instance deployment" choice in this
 * codebase; a real multi-instance deployment would need this in a shared
 * store. {@link #refreshNow()} is called directly by tests/manual
 * verification rather than sleep-polling for the real 15-minute tick, the
 * same pattern OutboxPoller.dispatchReady() already established.
 *
 * <p><b>Real bug, found on a live account:</b> this job originally injected
 * the plain (RLS-enforced, shardeya_app) {@code JdbcTemplate} -- the same
 * one every other service uses. But REFRESH MATERIALIZED VIEW re-executes
 * each view's defining query against its FORCE-RLS-protected source tables
 * (plot_sale, customer, interaction, payment_record, broker_partner), and
 * this job has no tenant context to bind (it refreshes every org's data in
 * one cross-tenant sweep, by design). With no {@code app.current_org} set,
 * RLS silently matched zero rows on every single refresh -- confirmed
 * empirically: {@code REFRESH MATERIALIZED VIEW} reported success every
 * time, but every one of the four views stayed permanently empty for every
 * org, indistinguishable from "no sales/leads/brokers exist yet" in the
 * actual charts. See V7_013__create_stats_refresh_role.sql /
 * StatsRefreshDataSourceConfig for the fix: a dedicated BYPASSRLS role,
 * used by this job alone.
 */
@Component
public class StatsRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(StatsRefreshJob.class);
    private static final String[] VIEWS = {"mv_monthly_sales", "mv_lead_funnel", "mv_broker_performance", "mv_staff_activity"};

    private final JdbcTemplate jdbc;
    private final AtomicReference<Instant> lastRefreshedAt = new AtomicReference<>();

    public StatsRefreshJob(@Qualifier("statsRefreshJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelay = 900_000)
    public void refreshOnSchedule() {
        try {
            refreshNow();
        } catch (Exception e) {
            // @Scheduled swallows an uncaught exception silently (just skips
            // this tick) -- exactly what let the RLS-empty-refresh bug this
            // class's javadoc documents go unnoticed: REFRESH reported
            // success, so there was nothing to even fail loudly. A genuine
            // failure (e.g. a permission or connectivity issue) must not be
            // this quiet a second time.
            log.error("Stats materialized view refresh failed", e);
        }
    }

    public void refreshNow() {
        for (String view : VIEWS) {
            jdbc.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY " + view);
        }
        lastRefreshedAt.set(Instant.now());
    }

    public Instant lastRefreshedAt() {
        return lastRefreshedAt.get();
    }
}
