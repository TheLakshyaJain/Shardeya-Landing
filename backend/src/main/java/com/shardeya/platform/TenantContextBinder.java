package com.shardeya.platform;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The sanctioned way for code outside {@code platform.*} to bind
 * {@link TenantContext} — needed for the one legitimate non-filter case:
 * signup creates a brand-new organization mid-transaction and must operate
 * under its RLS context for the rest of that same transaction (the INSERT
 * into {@code app_user}/{@code subscription} has to satisfy their WITH CHECK
 * clause). {@code TenantContext.set} itself stays package-private — this
 * class exists in {@code platform} so it's allowed to call it, while callers
 * elsewhere only ever touch this class, not {@code TenantContext} by name,
 * which is what the ArchUnit rule actually checks.
 *
 * <p><b>Must be called before the transaction even starts, in plain Java —
 * never inside an {@code @Transactional} method body.</b> The intuitive
 * assumption (and this class's own original javadoc) was that Hibernate
 * checks out the physical connection lazily, on first query, so binding
 * early in the method body would be enough. That's wrong for this app:
 * Spring's {@code JpaTransactionManager} is configured with a
 * {@code DataSource} (needed so {@code AuthLookupRepository}'s plain JDBC
 * calls can synchronize with JPA-managed transactions), and that makes
 * {@code doBegin()} eagerly check out the physical connection — before the
 * {@code @Transactional} method body runs at all. Confirmed by
 * instrumenting {@link TenantAwareDataSource}: {@code getConnection()} fired
 * with no tenant bound a millisecond before the method body's first line
 * executed, for every {@code @Transactional} method that tried to bind
 * inside itself (signup, login, OTP login, password reset). TenantAwareDataSource
 * then holds whatever GUC it set at that checkout for the rest of the
 * transaction, regardless of later TenantContext changes.
 *
 * <p>The fix used throughout {@code AuthService}: call this method in plain
 * Java first, then open the transaction explicitly via an injected
 * {@code TransactionTemplate} (propagation {@code REQUIRES_NEW}) — the same
 * pattern already proven in {@code RefreshTokenService}'s reuse-detection
 * path — so the checkout that fixes the GUC happens strictly after the bind.
 */
@Component
public final class TenantContextBinder {

    public void bindNewOrgContext(UUID orgId, UUID userId, String orgType, String role, Set<String> permissions) {
        TenantContext.set(new TenantContext.Tenant(orgId, userId, orgType, role, permissions, List.of(), true));
    }

    public void clear() {
        TenantContext.clear();
    }

    /** For authenticated endpoints (TenantContextFilter already bound context from the JWT) that need to read "who is this". */
    public TenantContext.Tenant current() {
        return TenantContext.current();
    }

    /** Convenience for the extremely common "which org is this request for" case (M2's builder/plot/media/import services). */
    public UUID currentOrgId() {
        return TenantContext.currentOrgId();
    }
}
