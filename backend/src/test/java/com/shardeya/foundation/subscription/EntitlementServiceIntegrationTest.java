package com.shardeya.foundation.subscription;

import com.shardeya.builder.project.Project;
import com.shardeya.builder.project.ProjectRepository;
import com.shardeya.foundation.auth.Organization;
import com.shardeya.foundation.auth.OrganizationRepository;
import com.shardeya.platform.QuotaExceededException;
import com.shardeya.platform.TestTenantContext;
import com.shardeya.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression coverage for a real bug found via manual bulk-import
 * verification (see CLAUDE.md "Milestone 2" notes): {@link EntitlementService}
 * read an org_usage row once per transaction and, because Hibernate's
 * first-level cache returns that same managed entity on every subsequent
 * JPQL {@code find()} call within the transaction, never saw the
 * org_usage-maintaining DB trigger's own out-of-band updates from the plot
 * inserts this exact test performs. Result: 200 plots (a full bulk-import
 * chunk) could be created against a 50-plot quota before the check ever
 * caught up. This test creates plots one at a time in a SINGLE transaction
 * -- the same shape as ImportService's chunked commit -- and asserts the
 * quota is enforced at exactly the limit, not later.
 */
class EntitlementServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private EntitlementService entitlementService;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void clearTenant() {
        TestTenantContext.clear();
    }

    @Test
    void quotaIsEnforcedAtTheLimitEvenAcrossManyChecksInOneTransaction() {
        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        // TenantContext MUST be bound in plain Java BEFORE any transaction
        // opens -- TenantAwareDataSource fixes app.current_org on the
        // connection at CHECKOUT time (when the transaction begins), not
        // whenever TestTenantContext.bind() happens to run. A @Transactional
        // test method starts its transaction (and checks out the connection)
        // before the method body executes, so binding tenant context inside
        // it is too late -- exactly the same root cause M1's own notes
        // documented for AuthService. Hence: bind first, open the
        // transaction explicitly via TransactionTemplate second.
        TestTenantContext.bind(orgId, UUID.randomUUID(), "BUILDER", "BUILDER_ADMIN", Set.of());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Organization org = new Organization(orgId, Organization.Type.BUILDER, "Quota Test Org", "Jaipur");
            organizationRepository.saveAndFlush(org);

            Project project = new Project(projectId, orgId, "Quota Test Project", Project.Type.RESIDENTIAL_PLOT_COLONY,
                    "1 Test Rd", "Locality", "Jaipur", "RJ", BigDecimal.TEN, "ACRE", BigDecimal.valueOf(435600), 100);
            projectRepository.saveAndFlush(project);

            // FREE plan's BUILDER_PLOTS_PER_PROJECT is seeded at 50 (V2_009)
            // -- exercised directly rather than re-seeding a custom limit,
            // so this test also breaks loudly if that seed value ever changes.
            int limit = 50;
            for (int i = 0; i < limit; i++) {
                entitlementService.assertWithinQuota(orgId, "BUILDER_PLOTS_PER_PROJECT", projectId, "error.plot.quotaExceeded");
                insertPlot(orgId, projectId, "P-" + i);
            }

            assertThatThrownBy(() -> entitlementService.assertWithinQuota(orgId, "BUILDER_PLOTS_PER_PROJECT", projectId, "error.plot.quotaExceeded"))
                    .isInstanceOf(QuotaExceededException.class);

            Number count = (Number) entityManager
                    .createNativeQuery("SELECT COUNT(*) FROM plot WHERE project_id = :projectId AND deleted_at IS NULL")
                    .setParameter("projectId", projectId)
                    .getSingleResult();
            assertThat(count.intValue()).isEqualTo(50);
        });
    }

    @Test
    void bulkUploadIsDisabledOnTheFreePlanButRangesAreNot() {
        // BULK_UPLOAD_ENABLED is seeded '0' for FREE (V2_009) -- Path B
        // (Excel import) is gated on it; Path A (Quick Range Create) has no
        // such call anywhere in its code path at all, which this test can't
        // directly assert (there's nothing to catch), but is exercised by
        // PlotQuickCreateServiceIntegrationTest committing successfully on
        // this exact same FREE plan with no feature check involved.
        UUID orgId = UUID.randomUUID();
        TestTenantContext.bind(orgId, UUID.randomUUID(), "BUILDER", "BUILDER_ADMIN", Set.of());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Organization org = new Organization(orgId, Organization.Type.BUILDER, "Feature Gate Test Org", "Jaipur");
            organizationRepository.saveAndFlush(org);
        });

        assertThatThrownBy(() -> entitlementService.assertFeatureEnabled(orgId, "BULK_UPLOAD_ENABLED", "error.import.bulkUploadRequiresUpgrade"))
                .isInstanceOf(com.shardeya.platform.FeatureNotEnabledException.class);
    }

    @Test
    void assertFeatureEnabledTreatsAMissingPlanLimitRowAsDisabledNotUnlimited() {
        // No PRO/PREMIUM plan_limit rows exist yet (still M8 scope) -- a
        // plan code with literally no row for this key must NOT be treated
        // as "enabled by default," unlike assertWithinQuota's own
        // missing-row-means-unlimited default for numeric quotas.
        UUID orgId = UUID.randomUUID();
        TestTenantContext.bind(orgId, UUID.randomUUID(), "BUILDER", "BUILDER_ADMIN", Set.of());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Organization org = new Organization(orgId, Organization.Type.BUILDER, "No Plan Row Test Org", "Jaipur");
            organizationRepository.saveAndFlush(org);
        });

        assertThatThrownBy(() -> entitlementService.assertFeatureEnabled(orgId, "SOME_FUTURE_FEATURE_KEY", "error.import.bulkUploadRequiresUpgrade"))
                .isInstanceOf(com.shardeya.platform.FeatureNotEnabledException.class);
    }

    @Test
    void usageTreatsAMissingPlanLimitRowAsUnlimitedNotZero() {
        // Real bug found on a live account: that org's subscription had
        // been left pointing at a throwaway test plan (from earlier manual
        // RBAC verification) which had no plan_limit row at all for
        // BUILDER_PLOTS_PER_PROJECT. usage() used to hardcode limit=0 for a
        // missing row, disagreeing with assertWithinQuota()'s own
        // null-means-unlimited convention -- so Quick Create's preview
        // showed "0/0, exceeds your plan's limit" and blocked the UI even
        // though the real server-side check (assertWithinQuota) would have
        // let the commit through with no cap at all.
        UUID orgId = UUID.randomUUID();
        TestTenantContext.bind(orgId, UUID.randomUUID(), "BUILDER", "BUILDER_ADMIN", Set.of());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Organization org = new Organization(orgId, Organization.Type.BUILDER, "No Plot Limit Row Test Org", "Jaipur");
            organizationRepository.saveAndFlush(org);
            entityManager.createNativeQuery(
                            "INSERT INTO plan (code, name_en, name_hi, price_monthly, price_yearly, sort_order) " +
                                    "VALUES ('TESTPLAN', 'Test Plan', 'Test Plan', 0, 0, 99)")
                    .executeUpdate();
            entityManager.createNativeQuery(
                            "INSERT INTO subscription (id, org_id, plan_code, status, started_at, current_period_start, current_period_end) " +
                                    "VALUES (:id, :orgId, 'TESTPLAN', 'ACTIVE', now(), now(), now() + interval '100 years')")
                    .setParameter("id", UUID.randomUUID())
                    .setParameter("orgId", orgId)
                    .executeUpdate();
        });

        var snapshot = entitlementService.usage(orgId, "BUILDER_PLOTS_PER_PROJECT", UUID.randomUUID());
        assertThat(snapshot.unlimited()).isTrue();
    }

    // Raw native-SQL insert (not PlotRepository/JPA) -- this test only needs
    // a real row to exist so the org_usage AFTER INSERT trigger fires; going
    // through the full Plot entity/generated-column machinery would be
    // needless setup for what this test actually checks. Uses the same
    // EntityManager as everything else in this test (not a bare JdbcTemplate
    // bean, which resolves ambiguously against this project's multiple
    // DataSources -- see CLAUDE.md's own @Primary/@Qualifier gotcha from M1).
    private void insertPlot(UUID orgId, UUID projectId, String plotNumber) {
        entityManager.createNativeQuery(
                        "INSERT INTO plot (org_id, project_id, plot_number, size_value, size_unit, size_sqft, price) " +
                                "VALUES (:orgId, :projectId, :plotNumber, 1200, 'SQ_FT', 1200, 4200000)")
                .setParameter("orgId", orgId)
                .setParameter("projectId", projectId)
                .setParameter("plotNumber", plotNumber)
                .executeUpdate();
    }
}
