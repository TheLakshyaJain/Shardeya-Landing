package com.shardeya.builder.stats;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Wires the BYPASSRLS {@code shardeya_statsrefresh} role into its own
 * connection pool, entirely separate from the main (RLS-enforced,
 * {@code shardeya_app}) datasource. See
 * V7_013__create_stats_refresh_role.sql for why this role exists: {@link
 * StatsRefreshJob} is a cross-org scheduled job with no tenant context to
 * bind, and the four materialised views' own defining queries read
 * FORCE-RLS-protected source tables (plot_sale, customer, interaction,
 * payment_record, broker_partner) -- running the refresh as the
 * RLS-enforced shardeya_app silently produced zero rows for every org,
 * always, exactly mirroring the class of bug
 * AuthLookupDataSourceConfig already documents for the pre-auth lookup
 * path. Never reuse this role/datasource for anything beyond {@link
 * StatsRefreshJob}.
 *
 * <p>Deliberately NOT {@code @Primary} anywhere in this class (unlike
 * AuthLookupDataSourceConfig's main-datasource beans) -- the default
 * shardeya_app JdbcTemplate/DataSource/NamedParameterJdbcTemplate beans
 * are already established as {@code @Primary} elsewhere in this codebase;
 * this class only adds one more explicitly-qualified, non-primary bean
 * pair alongside them.
 */
@Configuration
public class StatsRefreshDataSourceConfig {

    @Bean
    @ConfigurationProperties(prefix = "shardeya-stats-refresh-datasource")
    public DataSourceProperties statsRefreshDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource statsRefreshDataSource(
            @Qualifier("statsRefreshDataSourceProperties") DataSourceProperties statsRefreshDataSourceProperties) {
        return statsRefreshDataSourceProperties.initializeDataSourceBuilder().build();
    }

    @Bean
    public JdbcTemplate statsRefreshJdbcTemplate(@Qualifier("statsRefreshDataSource") DataSource statsRefreshDataSource) {
        return new JdbcTemplate(statsRefreshDataSource);
    }
}
