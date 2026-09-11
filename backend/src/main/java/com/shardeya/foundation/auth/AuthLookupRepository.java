package com.shardeya.foundation.auth;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AuthLookupRepository {

    private static final String SELECT = """
            SELECT au.id, au.org_id, au.mobile, au.email, au.password_hash,
                   au.failed_login_count, au.locked_until, au.status, au.token_version,
                   au.full_name, au.language, au.is_owner, au.role_id, r.code AS role_code,
                   au.project_access_mode,
                   (SELECT array_agg(rp.permission_code) FROM role_permission rp WHERE rp.role_id = au.role_id) AS permissions,
                   (SELECT array_agg(upa.project_id) FROM user_project_access upa WHERE upa.user_id = au.id) AS project_scope
            FROM app_user au
            JOIN role r ON r.id = au.role_id
            WHERE au.deleted_at IS NULL
            """;

    private static final RowMapper<AuthLookupUser> MAPPER = (rs, rowNum) -> new AuthLookupUser(
            UUID.fromString(rs.getString("id")),
            UUID.fromString(rs.getString("org_id")),
            rs.getString("mobile"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getShort("failed_login_count"),
            toInstant(rs.getTimestamp("locked_until")),
            rs.getString("status"),
            rs.getShort("token_version"),
            rs.getString("full_name"),
            rs.getString("language"),
            rs.getBoolean("is_owner"),
            UUID.fromString(rs.getString("role_id")),
            rs.getString("role_code"),
            toStringList(rs.getArray("permissions")),
            "ALL".equals(rs.getString("project_access_mode")),
            toUuidList(rs.getArray("project_scope")));

    private final JdbcTemplate authLookupJdbcTemplate;

    // @Qualifier is now load-bearing here, not just matching-by-convention --
    // M5 added a @Primary "jdbcTemplate" bean (AuthLookupDataSourceConfig,
    // for the main shardeya_app datasource, needed once FinancialService
    // became the first caller to inject a plain JdbcTemplate anywhere in the
    // app) which made this previously-safe parameter-name match resolve to
    // the WRONG datasource: any ambiguous JdbcTemplate injection point now
    // prefers @Primary over name-matching, exactly the class of bug this
    // file's own AuthLookupDataSourceConfig comments already documented
    // twice for DataSource/DataSourceProperties. Without this, every login
    // silently failed with "invalid credentials" -- AuthLookupRepository ran
    // against the RLS-enforced datasource with no tenant context bound yet,
    // so every SELECT matched zero rows, the exact original M1 bug's exact
    // symptom, reintroduced by a completely different bean four milestones
    // later.
    public AuthLookupRepository(@Qualifier("authLookupJdbcTemplate") JdbcTemplate authLookupJdbcTemplate) {
        this.authLookupJdbcTemplate = authLookupJdbcTemplate;
    }

    public Optional<AuthLookupUser> findByMobile(String mobile) {
        return querySingle(SELECT + " AND au.mobile = ?", mobile);
    }

    /**
     * Refresh-token rotation has the exact same problem signup/login do: a
     * {@code refresh_token} row carries only {@code user_id} (no org_id — see
     * V1_002's own comment), so minting a fresh access token needs org/role
     * info before there's any tenant context to bind. Same BYPASSRLS
     * connection, same justification.
     */
    public Optional<AuthLookupUser> findById(UUID id) {
        return querySingle(SELECT + " AND au.id = ?::uuid", id.toString());
    }

    public Optional<AuthLookupUser> findByEmail(String email) {
        return querySingle(SELECT + " AND lower(au.email) = lower(?)", email);
    }

    public boolean existsByMobile(String mobile) {
        Integer count = authLookupJdbcTemplate.queryForObject(
                "SELECT count(*) FROM app_user WHERE deleted_at IS NULL AND mobile = ?", Integer.class, mobile);
        return count != null && count > 0;
    }

    public boolean existsByEmail(String email) {
        Integer count = authLookupJdbcTemplate.queryForObject(
                "SELECT count(*) FROM app_user WHERE deleted_at IS NULL AND lower(email) = lower(?)",
                Integer.class, email);
        return count != null && count > 0;
    }

    private Optional<AuthLookupUser> querySingle(String sql, String param) {
        try {
            return Optional.ofNullable(authLookupJdbcTemplate.queryForObject(sql, MAPPER, param));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private static java.time.Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static List<String> toStringList(Array sqlArray) {
        if (sqlArray == null) {
            return List.of();
        }
        try {
            Object[] elements = (Object[]) sqlArray.getArray();
            return Arrays.stream(elements).map(String.class::cast).toList();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<UUID> toUuidList(Array sqlArray) {
        if (sqlArray == null) {
            return List.of();
        }
        try {
            Object[] elements = (Object[]) sqlArray.getArray();
            return Arrays.stream(elements).map(o -> UUID.fromString(o.toString())).toList();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
