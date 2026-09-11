package com.shardeya.builder.broker;

import com.shardeya.builder.broker.dto.DesignationHistoryResponse;
import com.shardeya.foundation.auth.AppUser;
import com.shardeya.foundation.auth.AppUserRepository;
import com.shardeya.platform.ForbiddenException;
import com.shardeya.platform.ResourceNotFoundException;
import com.shardeya.platform.TenantContextBinder;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 06-BROKER-NETWORK-ENGINE.md §6/§9/§34/§35, build-order steps 7+8+9 --
 * evaluates and applies designation changes for a booking's selling broker
 * and every one of its uplines, and (step 8) manual overrides. Called from
 * PlotSaleService.complete() (step 7, promotion only, §35: "one downline
 * completion promotes multiple uplines at once") AFTER the
 * plot_sale.status=COMPLETED save has been flushed, and from
 * PlotSaleService.cancel() (step 9, promotion OR demotion, only when the
 * cancelled sale had previously reached COMPLETED) AFTER the
 * plot_sale.status=CANCELLED save has been flushed. Either way, by that
 * point V65_010's trigger has already recomputed every affected broker's
 * personal_successful_bookings/team_successful_bookings, and (critically
 * for §43 concurrency) already holds each of their row locks for the rest
 * of this transaction, so nothing here needs its own explicit locking --
 * see the trigger migration's own comment for the full reasoning. That
 * same trigger fires generically on ANY plot_sale.status change (not just
 * the COMPLETED transition), so it needed zero changes to also correctly
 * exclude a just-cancelled booking from every affected broker's count --
 * step 9 only had to teach the JAVA side to act on the resulting decrease.
 *
 * <p>Deliberately does NOT touch booking_commission/commission_release at
 * all (§11/§24: "promotion applies only after the booking; the triggering
 * booking uses the old rate" -- and, by the identical construction, "a
 * demotion only affects future bookings", and step 8's own identical
 * "changes designation + rate going forward only") -- the frozen tree from
 * step 5 is simply never read or written here, which is what makes all
 * three timing rules true by construction rather than by a check.
 *
 * <p>Step 8 also deliberately does NOT touch personal_successful_bookings/
 * team_successful_bookings at all -- §34's own "does NOT change sales
 * counts, does NOT create fake bookings" is likewise true by construction:
 * manuallyOverride()/clearOverride() only ever read broker_partner.*_successful_bookings,
 * never write them (those columns stay exclusively trigger-maintained,
 * V65_010, from real plot_sale completions).
 */
@Service
public class DesignationPromotionService {

    /**
     * Which caller is asking for a re-evaluation, and what that implies:
     * whether a DEMOTION is acceptable, and how the resulting
     * designation_history row should be labelled.
     */
    private enum Mode {
        // step 7: a completion can only ever push counts up.
        PROMOTION_ONLY(false, DesignationHistory.ChangeType.AUTOMATIC, "Automatic promotion: team sales reached "),
        // step 9: a cancellation can push counts down (or, in principle, leave them
        // unchanged for an ACTIVE-never-completed sale, which never calls this at all).
        CANCELLATION_REVERSAL(true, DesignationHistory.ChangeType.CANCELLATION_REVERSAL, "Cancellation-driven reversal: team sales corrected to "),
        // step 8: clearing a manual override resumes automatic evaluation from
        // whatever the broker's real, unaffected team count already is --
        // could reveal either direction depending on where the override left them.
        RESUME_AUTOMATIC(true, DesignationHistory.ChangeType.AUTOMATIC, "Automatic re-evaluation after override cleared: team sales at ");

        final boolean allowDemotion;
        final DesignationHistory.ChangeType changeType;
        final String reasonPrefix;

        Mode(boolean allowDemotion, DesignationHistory.ChangeType changeType, String reasonPrefix) {
            this.allowDemotion = allowDemotion;
            this.changeType = changeType;
            this.reasonPrefix = reasonPrefix;
        }
    }

    private final BrokerPartnerRepository brokerRepository;
    private final BrokerNetworkService brokerNetworkService;
    private final DesignationSlabService designationSlabService;
    private final DesignationSlabRepository designationSlabRepository;
    private final DesignationHistoryRepository historyRepository;
    private final AppUserRepository appUserRepository;
    private final TenantContextBinder tenantContextBinder;
    private final EntityManager entityManager;

    public DesignationPromotionService(BrokerPartnerRepository brokerRepository, BrokerNetworkService brokerNetworkService,
                                        DesignationSlabService designationSlabService, DesignationSlabRepository designationSlabRepository,
                                        DesignationHistoryRepository historyRepository, AppUserRepository appUserRepository,
                                        TenantContextBinder tenantContextBinder, EntityManager entityManager) {
        this.brokerRepository = brokerRepository;
        this.brokerNetworkService = brokerNetworkService;
        this.designationSlabService = designationSlabService;
        this.designationSlabRepository = designationSlabRepository;
        this.historyRepository = historyRepository;
        this.appUserRepository = appUserRepository;
        this.tenantContextBinder = tenantContextBinder;
        this.entityManager = entityManager;
    }

    /**
     * §4: counts are recomputed for the selling broker (personal + team,
     * depth 0) and every ancestor (team only, depth 1..N) by the DB
     * trigger already; this method only re-evaluates each of those same
     * brokers' designation against their now-current team count.
     * Promotion only, matching BrokerTierService.evaluateAndUpgrade()'s
     * own posture -- a completion can never demote anyone.
     */
    @Transactional
    public void evaluateAndPromote(UUID sellingBrokerId) {
        evaluateAffected(sellingBrokerId, Mode.PROMOTION_ONLY);
    }

    /**
     * §9/§41 -- called only when the sale being cancelled had already
     * reached COMPLETED (i.e. it genuinely counted toward sales before
     * this cancellation). Unlike evaluateAndPromote(), this allows
     * DEMOTION: a cancellation is the first and only event in this engine
     * that can move a designation down, since it's the first event that
     * can make a team-sales count go DOWN.
     */
    @Transactional
    public void reevaluateAfterCancellation(UUID sellingBrokerId) {
        evaluateAffected(sellingBrokerId, Mode.CANCELLATION_REVERSAL);
    }

    /**
     * §34, build-order step 8 -- sets a broker's designation/rate directly,
     * for FUTURE bookings only (never touches booking_commission, per this
     * class's own top-level javadoc), and freezes automatic evaluation for
     * this broker (designation_manually_overridden=true) until explicitly
     * cleared. Admin-only, matching CommissionConfigService/BrokerTierService's
     * identical posture for money-shaping actions.
     */
    @Transactional
    public void manuallyOverride(UUID brokerId, UUID newDesignationId, String reason) {
        requireAdmin();
        UUID orgId = tenantContextBinder.currentOrgId();
        BrokerPartner broker = brokerRepository.findByIdAndOrgIdAndDeletedAtIsNull(brokerId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        DesignationSlab newSlab = loadVisibleSlab(orgId, newDesignationId);

        UUID previousDesignationId = broker.getCurrentDesignationId();
        BigDecimal previousRate = broker.getCurrentCommissionRate();

        // Build-order step 11's concurrency sweep -- same reorder, same
        // reason, as evaluateOne()'s own fix just above: the
        // designation_history INSERT's FK to broker_partner takes a FOR
        // KEY SHARE lock on this broker's row, which must never be
        // acquired BEFORE the broker_partner UPDATE itself (a stronger
        // lock) when this transaction isn't already guaranteed to hold
        // that row's lock going in -- manuallyOverride() never goes
        // through a plot_sale status change, so unlike a
        // completion/cancellation it has no such guarantee. See
        // evaluateOne()'s own comment for the full empirically-confirmed
        // deadlock mechanism this reorder avoids.
        broker.setCurrentDesignationId(newSlab.getId());
        broker.setCurrentCommissionRate(newSlab.getRatePerSqft());
        broker.setDesignationManuallyOverridden(true);
        brokerRepository.save(broker);

        DesignationHistory history = new DesignationHistory(UUID.randomUUID(), orgId, brokerId, previousDesignationId,
                newSlab.getId(), previousRate, newSlab.getRatePerSqft(), DesignationHistory.ChangeType.MANUAL, reason);
        history.setChangedBy(tenantContextBinder.current().userId());
        historyRepository.save(history);
    }

    /**
     * §34's own "one decision to surface, not assume" -- the spec never
     * says whether an override can be lifted; B-14's own identical
     * tier-override (M6) left this as a documented gap ("no clear-override
     * endpoint"). Built here anyway, per this round's explicit instruction:
     * clears the flag and immediately re-evaluates this ONE broker (never
     * their uplines/downlines -- clearing an override changes only this
     * broker's own designation/rate, never any sales count, so there is
     * nothing for anyone else's evaluation to react to) from their real,
     * currently-true team_successful_bookings, which could reveal either a
     * promotion or a demotion depending on where the override had left them
     * relative to their actual count.
     */
    @Transactional
    public void clearOverride(UUID brokerId) {
        requireAdmin();
        UUID orgId = tenantContextBinder.currentOrgId();
        BrokerPartner broker = brokerRepository.findByIdAndOrgIdAndDeletedAtIsNull(brokerId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        broker.setDesignationManuallyOverridden(false);
        brokerRepository.save(broker);
        entityManager.flush();
        evaluateOne(orgId, brokerId, Mode.RESUME_AUTOMATIC);
    }

    /** §26/§27 -- one broker's own promotion/demotion/override history, newest first. */
    @Transactional(readOnly = true)
    public List<DesignationHistoryResponse> history(UUID brokerId) {
        UUID orgId = tenantContextBinder.currentOrgId();
        brokerRepository.findByIdAndOrgIdAndDeletedAtIsNull(brokerId, orgId).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        return historyRepository.findByOrgIdAndBrokerIdOrderByEffectiveAtDesc(orgId, brokerId).stream().map(this::toHistoryResponse).toList();
    }

    /** §29's builder/admin overview -- every designation change across the whole network, newest first. */
    @Transactional(readOnly = true)
    public List<DesignationHistoryResponse> networkHistory() {
        UUID orgId = tenantContextBinder.currentOrgId();
        return historyRepository.findByOrgIdOrderByEffectiveAtDesc(orgId).stream().map(this::toHistoryResponse).toList();
    }

    private DesignationHistoryResponse toHistoryResponse(DesignationHistory h) {
        DesignationSlab previous = h.getPreviousDesignationId() == null ? null : designationSlabRepository.findById(h.getPreviousDesignationId()).orElse(null);
        DesignationSlab next = designationSlabRepository.findById(h.getNewDesignationId()).orElse(null);
        String brokerName = brokerRepository.findById(h.getBrokerId()).map(BrokerPartner::getFullName).orElse(null);
        String changedByName = h.getChangedBy() == null ? null : appUserRepository.findById(h.getChangedBy()).map(AppUser::getFullName).orElse(null);
        return new DesignationHistoryResponse(h.getId(), h.getBrokerId(), brokerName,
                previous == null ? null : previous.getName(), previous == null ? null : previous.getNameHi(), h.getPreviousRate(),
                next == null ? null : next.getName(), next == null ? null : next.getNameHi(), h.getNewRate(),
                h.getChangeType().name(), h.getReason(), h.getEffectiveAt(), changedByName);
    }

    private void evaluateAffected(UUID sellingBrokerId, Mode mode) {
        UUID orgId = tenantContextBinder.currentOrgId();

        List<UUID> affected = new ArrayList<>();
        affected.add(sellingBrokerId);
        for (BrokerNetwork ancestor : brokerNetworkService.ancestorsExcludingSelf(orgId, sellingBrokerId)) {
            affected.add(ancestor.getAncestorBrokerId());
        }

        for (UUID brokerId : affected) {
            evaluateOne(orgId, brokerId, mode);
        }
    }

    private void evaluateOne(UUID orgId, UUID brokerId, Mode mode) {
        BrokerPartner broker = brokerRepository.findById(brokerId).orElse(null);
        if (broker == null || broker.isDesignationManuallyOverridden()) {
            // §13/§34: an overridden broker is frozen against ANY automatic
            // evaluation (completion-driven promotion, cancellation-driven
            // demotion) until explicitly cleared -- clearOverride() itself
            // reaches this method only after already flipping the flag to
            // false, so it's never blocked by its own guard.
            return;
        }
        // The trigger already wrote this broker's fresh count inside THIS
        // same transaction (before this method is ever called) -- refresh
        // forces Hibernate to see it rather than a possibly-stale
        // first-level-cache copy (this broker may already have been loaded
        // once earlier in the same transaction, e.g. PlotSaleService's own
        // commissionType lookup).
        entityManager.refresh(broker);

        DesignationSlab candidate = designationSlabService.resolve(orgId, broker.getTeamSuccessfulBookings());
        if (candidate.getId().equals(broker.getCurrentDesignationId())) {
            return;
        }

        BigDecimal currentRate = broker.getCurrentCommissionRate() == null ? BigDecimal.ZERO : broker.getCurrentCommissionRate();
        DesignationTransitionCalculator.Direction direction = DesignationTransitionCalculator.compare(currentRate, candidate.getRatePerSqft());
        if (direction == DesignationTransitionCalculator.Direction.NO_CHANGE) {
            return;
        }
        if (direction == DesignationTransitionCalculator.Direction.DEMOTION && !mode.allowDemotion) {
            // evaluateAndPromote()'s own caller (a completion) can never
            // legitimately produce a demotion candidate anyway (counts only
            // go up), but this is the same defensive backstop
            // evaluateAndPromote() always had -- now expressed via the
            // shared pure comparison instead of an inline rate check.
            return;
        }

        // §11, build-order step 11's own concurrency sweep -- a REAL
        // deadlock found empirically (not by reasoning alone) via
        // DesignationOverrideConcurrencyIntegrationTest, in exactly the one
        // shape this method's own callers can reach that ISN'T already
        // protected by the counts trigger's PERFORM ... FOR UPDATE (step
        // 7): manuallyOverride()/clearOverride() never go through a
        // plot_sale status change at all, so a transaction calling this
        // method via Mode.RESUME_AUTOMATIC is NOT guaranteed to already
        // hold broker_partner's row lock the way a completion/cancellation
        // always is. The ORIGINAL order here -- INSERT designation_history
        // (whose broker_id column is a real FK to broker_partner, so
        // Postgres takes a FOR KEY SHARE lock on the referenced row as a
        // side effect) BEFORE the broker_partner UPDATE (which needs a
        // full lock) -- creates a classic Postgres lock-upgrade deadlock
        // whenever a SECOND transaction is concurrently queued for a
        // stronger lock on the SAME row in between: this transaction holds
        // the weak FOR KEY SHARE, the other transaction is blocked waiting
        // for FOR UPDATE, and this transaction's own later attempt to
        // upgrade to the same strong lock then has to queue BEHIND the
        // other transaction's already-waiting request -- a genuine mutual
        // wait, not merely a block. Reproduced directly: a manual override
        // racing a completion for the same broker (the completion's own
        // trigger takes FOR UPDATE first) deadlocked every time, confirmed
        // via the real Postgres error ("deadlock detected... while locking
        // tuple... in relation broker_partner"), before this reorder.
        // FIX: acquire the STRONG lock first (the broker_partner UPDATE),
        // matching the exact "strong lock as this transaction's very first
        // touch to the contended row" discipline the counts trigger itself
        // already established -- the designation_history INSERT's FK-driven
        // FOR KEY SHARE request is then trivially satisfied by a lock this
        // transaction already holds a stronger version of, never needing to
        // queue behind anyone. previousDesignationId/previousRate are
        // captured into locals BEFORE the broker's own fields are mutated,
        // so this reorder has zero effect on what the history row records.
        UUID previousDesignationId = broker.getCurrentDesignationId();
        BigDecimal previousRate = broker.getCurrentCommissionRate();
        int teamSalesAtChange = broker.getTeamSuccessfulBookings();

        broker.setCurrentDesignationId(candidate.getId());
        broker.setCurrentCommissionRate(candidate.getRatePerSqft());
        brokerRepository.save(broker);

        DesignationHistory history = new DesignationHistory(UUID.randomUUID(), orgId, brokerId,
                previousDesignationId, candidate.getId(), previousRate,
                candidate.getRatePerSqft(), mode.changeType, mode.reasonPrefix + teamSalesAtChange);
        history.setChangedBy(tenantContextBinder.current().userId());
        historyRepository.save(history);
    }

    private DesignationSlab loadVisibleSlab(UUID orgId, UUID designationId) {
        DesignationSlab slab = designationSlabRepository.findById(designationId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        if (slab.getOrgId() != null && !slab.getOrgId().equals(orgId)) {
            // A future per-org designation_slab override (none exist yet,
            // see V65_001's own comment) belonging to a DIFFERENT org --
            // treated as not found, same "foreign-tenant resource is 404,
            // never 403" rule as everywhere else in this codebase.
            throw new ResourceNotFoundException("error.notFound");
        }
        return slab;
    }

    private void requireAdmin() {
        if (!"BUILDER_ADMIN".equals(tenantContextBinder.current().role())) {
            throw new ForbiddenException("error.designation.adminOnly");
        }
    }
}
