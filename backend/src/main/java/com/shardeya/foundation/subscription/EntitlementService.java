package com.shardeya.foundation.subscription;

import com.shardeya.platform.FeatureNotEnabledException;
import com.shardeya.platform.QuotaExceededException;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * M2 scope was quota-type gates only (BUILDER_PROJECTS, BUILDER_PLOTS_PER_PROJECT
 * — the two named in 05-MILESTONES.md M2's exit criteria). Boolean/tier gates
 * (BULK_UPLOAD_ENABLED, EXPORT_ENABLED, WHATSAPP_ENABLED) were seeded as
 * correct reference data (V2_009) but deliberately left unenforced then:
 * §23.1 blocks bulk upload on the Free plan, but M2's own exit criteria
 * required testing a 500-plot bulk upload, and real subscription payment
 * (the only way to actually reach a paid plan) is explicitly M8 scope —
 * enforcing the gate then would have made that milestone's own required test
 * permanently unreachable.
 *
 * <p>{@code assertFeatureEnabled} (added alongside B-06 Path A / Quick Range
 * Create) is the "revisit" this class's own comment already anticipated —
 * needed now because Quick Create's whole point is a Free-plan-available
 * contrast against Path B (Excel import), which only means something once
 * Path B is actually gated. No PRO/PREMIUM plan_limit rows exist yet either
 * (still M8 scope), so today this can only ever demonstrate "blocked on
 * Free" — not a real upgrade unlocking it — same caveat M2/M3 already noted
 * for the numeric quota gates.</p>
 */
@Service
public class EntitlementService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanLimitRepository planLimitRepository;
    private final OrgUsageRepository orgUsageRepository;
    private final EntityManager entityManager;

    public EntitlementService(SubscriptionRepository subscriptionRepository, PlanLimitRepository planLimitRepository,
                               OrgUsageRepository orgUsageRepository, EntityManager entityManager) {
        this.subscriptionRepository = subscriptionRepository;
        this.planLimitRepository = planLimitRepository;
        this.orgUsageRepository = orgUsageRepository;
        this.entityManager = entityManager;
    }

    /**
     * Pre-flight check + reservation lock. Must be called inside the same
     * transaction as the entity creation whose trigger will bump
     * {@code org_usage} — the advisory lock only serializes concurrent
     * callers for the duration of that transaction.
     */
    public void assertWithinQuota(UUID orgId, String limitKey, UUID scopeId, String messageKeyIfExceeded) {
        String planCode = subscriptionRepository.findByOrgId(orgId).map(Subscription::getPlanCode).orElse("FREE");
        PlanLimit limit = planLimitRepository.findByPlanCodeAndLimitKey(planCode, limitKey).orElse(null);
        if (limit == null || limit.isUnlimited()) {
            return;
        }
        int max = limit.asInt();

        orgUsageRepository.advisoryLock(orgId + ":" + limitKey + ":" + scopeId);
        int used = currentUsage(orgId, limitKey, scopeId);
        if (used >= max) {
            throw new QuotaExceededException(messageKeyIfExceeded, limitKey, used, max);
        }
    }

    // entityManager.refresh() is load-bearing, not optional, whenever this is
    // called more than once for the same (org, limitKey, scopeId) within a
    // single transaction (exactly what a chunked bulk-import commit does,
    // once per row): find()'s JPQL SELECT genuinely re-executes the SQL each
    // call, but Hibernate's first-level cache then discards those fresh
    // column values and returns the ALREADY-MANAGED OrgUsage instance from
    // this row's first load in the transaction -- so getCurrentValue() kept
    // reading whatever it was BEFORE this transaction started, never
    // reflecting the org_usage trigger's own UPDATEs from the plot inserts
    // this very loop was doing. Confirmed empirically: bulk-importing 500
    // plots against a 50-plot Free-plan limit created 200 plots (an entire
    // COMMIT_CHUNK_SIZE) before the check finally caught up on the NEXT
    // chunk's fresh transaction/persistence context -- not visible from a
    // small-batch test (fewer rows than one chunk would never expose it) or
    // from reading the code, only from an actual over-limit bulk import.
    private int currentUsage(UUID orgId, String limitKey, UUID scopeId) {
        return orgUsageRepository.find(orgId, limitKey, scopeId)
                .map(u -> {
                    entityManager.refresh(u);
                    return u.getCurrentValue();
                })
                .orElse(0);
    }

    /**
     * Boolean/tier gate: throws unless the plan's {@code limit_value} for
     * this key is truthy. A plan with no row at all for this key (there are
     * none for PRO/PREMIUM yet -- see class javadoc) is treated as
     * disabled, not unlimited -- the opposite default from
     * {@link #assertWithinQuota}, since a missing numeric quota row means
     * "no cap," but a missing boolean-feature row can't mean "feature
     * enabled" without a plan ever having actually granted it.
     */
    public void assertFeatureEnabled(UUID orgId, String featureKey, String messageKeyIfDisabled) {
        String planCode = subscriptionRepository.findByOrgId(orgId).map(Subscription::getPlanCode).orElse("FREE");
        boolean enabled = planLimitRepository.findByPlanCodeAndLimitKey(planCode, featureKey)
                .map(PlanLimit::asInt)
                .map(v -> v != 0)
                .orElse(false);
        if (!enabled) {
            throw new FeatureNotEnabledException(messageKeyIfDisabled, featureKey);
        }
    }

    /**
     * M7: string-tier gate for the two keys whose {@code limit_value} is a
     * tier name, not a count/boolean (LEGAL_DOCS: NONE|BASIC|FULL,
     * ANALYTICS: BASIC|ADVANCED|FULL, per 01-DATA-MODEL.md §10) --
     * {@link #assertFeatureEnabled} only understands a truthy/falsy
     * {@code asInt()}, which doesn't apply to a three-value tier string.
     * A plan with no row at all is treated as the LOWEST tier (same
     * "missing means nothing granted" default {@code assertFeatureEnabled}
     * already uses for booleans), not unlimited.
     */
    public void assertTierAtLeast(UUID orgId, String tierKey, List<String> orderedTiers, String requiredTier, String messageKeyIfBelow) {
        String planCode = subscriptionRepository.findByOrgId(orgId).map(Subscription::getPlanCode).orElse("FREE");
        String actualTier = planLimitRepository.findByPlanCodeAndLimitKey(planCode, tierKey).map(PlanLimit::getLimitValue).orElse(orderedTiers.get(0));
        int actualIndex = orderedTiers.indexOf(actualTier);
        int requiredIndex = orderedTiers.indexOf(requiredTier);
        if (actualIndex < requiredIndex) {
            throw new FeatureNotEnabledException(messageKeyIfBelow, tierKey);
        }
    }

    public String tierOf(UUID orgId, String tierKey, List<String> orderedTiers) {
        String planCode = subscriptionRepository.findByOrgId(orgId).map(Subscription::getPlanCode).orElse("FREE");
        return planLimitRepository.findByPlanCodeAndLimitKey(planCode, tierKey).map(PlanLimit::getLimitValue).orElse(orderedTiers.get(0));
    }

    public UsageSnapshot usage(UUID orgId, String limitKey, UUID scopeId) {
        String planCode = subscriptionRepository.findByOrgId(orgId).map(Subscription::getPlanCode).orElse("FREE");
        PlanLimit limit = planLimitRepository.findByPlanCodeAndLimitKey(planCode, limitKey).orElse(null);
        int used = currentUsage(orgId, limitKey, scopeId);
        // A plan with no row at all for this key must mean "no cap," not
        // "cap of zero" -- matching assertWithinQuota()'s own convention
        // exactly (a null PlanLimit there short-circuits as unlimited). A
        // real bug: this used to hardcode 0 here, which disagreed with the
        // actual enforcement and made a preview show "0/0, exceeds quota"
        // for any plan genuinely missing a plan_limit row for this key --
        // even though committing would have gone through fine server-side.
        int max = (limit == null) ? -1 : limit.asInt();
        return new UsageSnapshot(limitKey, used, max);
    }

    public record UsageSnapshot(String limitKey, int used, int limit) {
        public boolean unlimited() {
            return limit < 0;
        }
    }
}
