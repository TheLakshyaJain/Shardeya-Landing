package com.shardeya.platform;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * 00-ARCHITECTURE.md §2.2 Layer 3: "PostgreSQL Row-Level Security... keyed on
 * {@code current_setting('app.current_org')}, set per-connection by the
 * transaction interceptor." This is that interceptor — implemented as a
 * DataSource wrapper because HikariCP physically reuses connections across
 * requests, so the GUC must be (re)set on every checkout, never assumed to
 * still hold whatever the previous borrower set. Only wraps the RLS-enforced
 * {@code shardeya_app} datasource; the BYPASSRLS auth-lookup one doesn't need
 * it (BYPASSRLS ignores the GUC either way).
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    public TenantAwareDataSource(DataSource targetDataSource) {
        super(targetDataSource);
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection connection = super.getConnection();
        applyTenant(connection);
        return connection;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection connection = super.getConnection(username, password);
        applyTenant(connection);
        return connection;
    }

    private void applyTenant(Connection connection) throws SQLException {
        TenantContext.Tenant tenant = TenantContext.currentOrNull();
        try (Statement statement = connection.createStatement()) {
            if (tenant == null) {
                statement.execute("RESET app.current_org");
            } else {
                // UUID#toString() output is always [0-9a-f-]{36} — safe to inline,
                // Postgres SET does not accept bind parameters for GUC values.
                statement.execute("SET app.current_org = '" + validated(tenant.orgId()) + "'");
            }
        }
    }

    private static String validated(UUID orgId) {
        return UUID.fromString(orgId.toString()).toString();
    }
}
