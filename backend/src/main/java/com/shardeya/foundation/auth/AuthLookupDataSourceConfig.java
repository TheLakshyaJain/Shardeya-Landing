package com.shardeya.foundation.auth;

import com.shardeya.platform.TenantAwareDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

/**
 * Wires the BYPASSRLS {@code shardeya_authlookup} role into its own connection
 * pool, entirely separate from the main (RLS-enforced, {@code shardeya_app})
 * datasource that Hibernate/JPA uses everywhere else. See
 * V1_009__create_auth_lookup_role.sql for why this role exists at all — it
 * must never be reused for anything beyond {@link AuthLookupRepository}.
 *
 * <p>Both datasources are declared explicitly here (Spring Boot's documented
 * "configure two datasources" recipe: bind {@link DataSourceProperties} first,
 * then build via {@code initializeDataSourceBuilder()}) rather than leaning on
 * {@code DataSourceAutoConfiguration} for the main one. Two things bit us
 * getting here: (1) a second, unqualified {@code DataSource} bean makes JPA's
 * own internal autowiring ambiguous — it silently picked the wrong one once,
 * producing a bewildering "Unable to determine Dialect" failure with no
 * obvious link to the real cause; {@code @Primary} on the main bean fixes
 * that. (2) Binding {@code @ConfigurationProperties} directly onto a raw
 * {@code DataSourceBuilder.create().build()} result does NOT correctly map
 * {@code url} to Hikari's actual {@code jdbcUrl} setter — it fails at runtime
 * with "dataSource or dataSourceClassName or jdbcUrl is required" instead of
 * a bind-time error. Going through {@code DataSourceProperties} avoids that
 * translation gap entirely.
 */
@Configuration
public class AuthLookupDataSourceConfig {

    @Primary
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean
    public DataSource dataSource(DataSourceProperties dataSourceProperties) {
        // Wrapped so app.current_org is (re)applied on every physical connection
        // checkout — see TenantAwareDataSource for why this can't just be set
        // once. This is the shardeya_app (RLS-enforced) datasource.
        return new TenantAwareDataSource(dataSourceProperties.initializeDataSourceBuilder().build());
    }

    @Bean
    @ConfigurationProperties(prefix = "shardeya-auth-lookup-datasource")
    public DataSourceProperties authLookupDataSourceProperties() {
        return new DataSourceProperties();
    }

    // @Qualifier here is load-bearing for the exact same reason as on
    // authLookupJdbcTemplate below: @Primary on dataSourceProperties() above
    // means any ambiguous DataSourceProperties-typed injection point
    // — including this one — silently resolves to it instead of matching by
    // parameter name. Without this, the "auth lookup" pool was actually built
    // from spring.datasource (shardeya_app, RLS-enforced) rather than
    // shardeya-auth-lookup-datasource (shardeya_authlookup, BYPASSRLS): same
    // host/db so it connected fine, but every query saw RLS filter it down to
    // zero rows since no tenant context is ever bound before these lookups
    // run. Two bugs stacked on the same @Primary side effect, at two
    // different injection points — the first (on authLookupJdbcTemplate)
    // wasn't sufficient on its own because the DataSource it was correctly
    // selecting had already been built with the wrong credentials one layer
    // down.
    @Bean
    public DataSource authLookupDataSource(
            @Qualifier("authLookupDataSourceProperties") DataSourceProperties authLookupDataSourceProperties) {
        return authLookupDataSourceProperties.initializeDataSourceBuilder().build();
    }

    // @Qualifier here is load-bearing, not decorative: @Primary on the main
    // dataSource() bean above means Spring resolves ANY ambiguous
    // DataSource-typed injection point to it FIRST, before ever falling back
    // to matching the parameter name against candidate bean names — matching
    // this parameter's name to the "authLookupDataSource" bean by convention
    // was not enough and silently wired the RLS-enforced main datasource in
    // here instead. That meant every AuthLookupRepository query ran with no
    // RLS context bound (since binding context only happens *after* a lookup
    // resolves org/role), so its WHERE clause matched zero rows every time —
    // findByMobile/findByEmail/findById always "not found", and
    // existsByMobile/existsByEmail always "not registered" (silently
    // disabling the duplicate-signup check). Caught by testing login with
    // real HTTP calls against a live docker-compose stack: signup worked,
    // but logging in with the exact credentials just created returned 401
    // every time.
    @Bean
    public JdbcTemplate authLookupJdbcTemplate(@Qualifier("authLookupDataSource") DataSource authLookupDataSource) {
        return new JdbcTemplate(authLookupDataSource);
    }

    // M5: a THIRD variant of the same @Primary-adjacent bug class this file's
    // other comments already document twice, discovered when FinancialService
    // became the first caller in this codebase to ever inject a plain
    // JdbcTemplate/NamedParameterJdbcTemplate outside this class. Spring
    // Boot's JdbcTemplateAutoConfiguration only creates its OWN default
    // JdbcTemplate bean via @ConditionalOnMissingBean(JdbcOperations.class) --
    // but authLookupJdbcTemplate above already satisfies that condition (it's
    // a JdbcTemplate, regardless of its bean name), so Spring Boot's
    // auto-configured default never fires at all. Its NamedParameterJdbcTemplate
    // auto-configuration then wraps the only JdbcTemplate bean that DOES
    // exist -- authLookupJdbcTemplate, the BYPASSRLS one -- so any plain,
    // unqualified `JdbcTemplate`/`NamedParameterJdbcTemplate` constructor
    // injection anywhere in the app silently ran queries as
    // shardeya_authlookup instead of shardeya_app. Confirmed live: querying
    // payment_record (a table shardeya_authlookup has zero grants on, by
    // design -- see V4_009's narrow grant list) failed with a genuine
    // Postgres "permission denied for table payment_record", not an RLS
    // empty-result -- a real grant-level error, which is what gave this one
    // away as different from the RLS-context bugs already documented above.
    // Fixed the same way those were: explicit beans, @Primary, so any future
    // plain JdbcTemplate/NamedParameterJdbcTemplate injection anywhere in the
    // app resolves to the RLS-enforced shardeya_app datasource by default.
    @Primary
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Primary
    @Bean
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(JdbcTemplate jdbcTemplate) {
        return new NamedParameterJdbcTemplate(jdbcTemplate);
    }
}
