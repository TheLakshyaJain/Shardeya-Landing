package com.shardeya.builder.broker;

import com.shardeya.builder.broker.dto.BrokerTierCreateRequest;
import com.shardeya.builder.broker.dto.BrokerTierResponse;
import com.shardeya.builder.broker.dto.TierOverrideRequest;
import com.shardeya.platform.BadRequestException;
import com.shardeya.platform.ForbiddenException;
import com.shardeya.platform.OutboxService;
import com.shardeya.platform.ResourceNotFoundException;
import com.shardeya.platform.TenantContextBinder;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * B-14 §20.4 -- tier CRUD (Admin only, §9), overlap validation (a friendly
 * pre-flight check before the DB's own EXCLUDE constraint would reject it),
 * and the auto-evaluation itself: upgrade-only, writes broker_tier_history,
 * notifies. Manual override freezes auto-evaluation
 * (tier_manually_overridden = true) until... nothing currently clears it --
 * the B-14 API surface has no "clear override" endpoint, only
 * POST .../tier-override to set one; documented as a known gap in
 * CLAUDE.md rather than inventing an unspecified endpoint.
 */
@Service
public class BrokerTierService {

    private final BrokerTierRepository tierRepository;
    private final BrokerPartnerRepository brokerRepository;
    private final BrokerTierHistoryRepository historyRepository;
    private final DesignationSlabRepository designationSlabRepository;
    private final TenantContextBinder tenantContextBinder;
    private final OutboxService outboxService;
    private final EntityManager entityManager;

    public BrokerTierService(BrokerTierRepository tierRepository, BrokerPartnerRepository brokerRepository,
                              BrokerTierHistoryRepository historyRepository, DesignationSlabRepository designationSlabRepository,
                              TenantContextBinder tenantContextBinder, OutboxService outboxService, EntityManager entityManager) {
        this.tierRepository = tierRepository;
        this.brokerRepository = brokerRepository;
        this.historyRepository = historyRepository;
        this.designationSlabRepository = designationSlabRepository;
        this.tenantContextBinder = tenantContextBinder;
        this.outboxService = outboxService;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<BrokerTierResponse> list() {
        UUID orgId = tenantContextBinder.currentOrgId();
        return tierRepository.findByOrgIdAndDeletedAtIsNullOrderBySortOrderAsc(orgId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public BrokerTierResponse create(BrokerTierCreateRequest req) {
        requireAdmin();
        UUID orgId = tenantContextBinder.currentOrgId();
        if (tierRepository.existsByOrgIdAndNameIgnoreCase(orgId, req.name())) {
            throw new BadRequestException("name", "TIER_NAME_DUPLICATE", "error.brokerTier.nameDuplicate");
        }
        assertNoOverlap(orgId, null, req.minDeals(), req.maxDeals());

        UUID id = UUID.randomUUID();
        short sortOrder = (short) (tierRepository.findByOrgIdAndDeletedAtIsNullOrderBySortOrderAsc(orgId).size());
        BrokerTier tier = new BrokerTier(id, orgId, req.name(), req.nameHi(), req.minDeals(), req.maxDeals(), sortOrder);
        if (req.bonusType() != null) tier.setBonusType(req.bonusType());
        if (req.bonusValue() != null) tier.setBonusValue(req.bonusValue());
        tier.setPerksDescription(req.perksDescription());
        tierRepository.save(tier);
        if (tier.getBonusType() == BrokerTier.BonusType.RATE_PER_SQFT && tier.getBonusValue() != null) {
            syncDesignationSlabRate(orgId, tier.getName(), tier.getBonusValue());
        }
        return toResponse(tier);
    }

    @Transactional
    public BrokerTierResponse update(UUID id, BrokerTierCreateRequest req) {
        requireAdmin();
        UUID orgId = tenantContextBinder.currentOrgId();
        BrokerTier tier = tierRepository.findByIdAndOrgIdAndDeletedAtIsNull(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        assertNoOverlap(orgId, id, req.minDeals(), req.maxDeals());

        tier.setName(req.name());
        tier.setNameHi(req.nameHi());
        tier.setMinDeals(req.minDeals());
        tier.setMaxDeals(req.maxDeals());
        if (req.bonusType() != null) tier.setBonusType(req.bonusType());
        if (req.bonusValue() != null) tier.setBonusValue(req.bonusValue());
        tier.setPerksDescription(req.perksDescription());
        tierRepository.save(tier);
        if (tier.getBonusType() == BrokerTier.BonusType.RATE_PER_SQFT && tier.getBonusValue() != null) {
            syncDesignationSlabRate(orgId, tier.getName(), tier.getBonusValue());
        }
        // B-14 §10: "Tier threshold edited so a broker no longer qualifies
        // -> they keep their tier (no automatic demotion)" -- editing a
        // tier's own range never re-evaluates brokers currently assigned to
        // it or any other tier; only a new sale completing (evaluateAndUpgrade)
        // or an explicit /recalculate call ever moves a broker's tier_id.
        return toResponse(tier);
    }

    @Transactional
    public void delete(UUID id) {
        requireAdmin();
        UUID orgId = tenantContextBinder.currentOrgId();
        BrokerTier tier = tierRepository.findByIdAndOrgIdAndDeletedAtIsNull(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        tier.setDeletedAt(Instant.now());
        tier.setDeletedBy(tenantContextBinder.current().userId());
        tierRepository.save(tier);
    }

    @Transactional
    public void override(UUID brokerId, TierOverrideRequest req) {
        requireAdmin();
        UUID orgId = tenantContextBinder.currentOrgId();
        BrokerPartner broker = brokerRepository.findByIdAndOrgIdAndDeletedAtIsNull(brokerId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        BrokerTier newTier = tierRepository.findByIdAndOrgIdAndDeletedAtIsNull(req.tierId(), orgId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));

        UUID oldTierId = broker.getTierId();
        broker.setTierId(newTier.getId());
        broker.setTierManuallyOverridden(true);
        broker.setTierAssignedAt(Instant.now());
        brokerRepository.save(broker);

        UUID actorId = tenantContextBinder.current().userId();
        historyRepository.save(new BrokerTierHistory(UUID.randomUUID(), orgId, brokerId, oldTierId, newTier.getId(),
                broker.getDealsClosedCount(), actorId, true, req.reason()));
    }

    /**
     * B-14 §7: called from PlotSaleService right after a sale reaches
     * COMPLETED (the DB trigger, V6_015, has already bumped
     * broker_partner.deals_closed_count by the time this runs within the
     * same transaction -- same "Hibernate can't see a DB-trigger-computed
     * change it didn't itself make" root cause every prior milestone's
     * notes already document; entityManager.refresh() is what makes the
     * bumped count visible here).
     */
    @Transactional
    public void evaluateAndUpgrade(UUID brokerId) {
        UUID orgId = tenantContextBinder.currentOrgId();
        BrokerPartner broker = brokerRepository.findByIdAndOrgIdAndDeletedAtIsNull(brokerId, orgId).orElse(null);
        if (broker == null || broker.isTierManuallyOverridden()) {
            return;
        }
        entityManager.refresh(broker);

        List<BrokerTier> matching = tierRepository.findMatchingTiers(orgId, broker.getDealsClosedCount());
        if (matching.isEmpty()) {
            return;
        }
        BrokerTier candidate = matching.get(0); // findMatchingTiers orders by minDeals DESC -- highest applicable first
        BrokerTier currentTier = broker.getTierId() == null ? null : tierRepository.findById(broker.getTierId()).orElse(null);

        // Auto-upgrade only, never auto-downgrade (B-14 §7's single most
        // important tier rule, right after commission-immutability) --
        // ranked by minDeals since a higher-starting-threshold tier is
        // always the "better" one in this org's own ordering.
        if (currentTier != null && candidate.getMinDeals() <= currentTier.getMinDeals()) {
            return;
        }

        UUID oldTierId = broker.getTierId();
        broker.setTierId(candidate.getId());
        broker.setTierAssignedAt(Instant.now());
        brokerRepository.save(broker);

        historyRepository.save(new BrokerTierHistory(UUID.randomUUID(), orgId, brokerId, oldTierId, candidate.getId(),
                broker.getDealsClosedCount(), null, false, null));

        outboxService.enqueueNotification(orgId, "BROKER_TIER_UPGRADED", "notification.brokerTierUpgraded",
                "notification.brokerTierUpgradedBody", Map.of("brokerName", broker.getFullName(), "tierName", candidate.getName()),
                "broker_partner", brokerId);
    }

    /** B-14 §4 "POST /broker-tiers/recalculate -> admin re-evaluation of all brokers" -- e.g. after a bulk-imported historical-deals catch-up. */
    @Transactional
    public int recalculateAll() {
        requireAdmin();
        UUID orgId = tenantContextBinder.currentOrgId();
        List<BrokerPartner> brokers = brokerRepository.findAll((root, query, cb) ->
                cb.and(cb.equal(root.get("orgId"), orgId), cb.isNull(root.get("deletedAt"))));
        int evaluated = 0;
        for (BrokerPartner b : brokers) {
            evaluateAndUpgrade(b.getId());
            evaluated++;
        }
        return evaluated;
    }

    private void assertNoOverlap(UUID orgId, UUID excludingId, int minDeals, Integer maxDeals) {
        for (BrokerTier existing : tierRepository.findByOrgIdAndDeletedAtIsNullOrderBySortOrderAsc(orgId)) {
            if (existing.getId().equals(excludingId)) continue;
            if (existing.overlaps(minDeals, maxDeals)) {
                throw new BadRequestException("minDeals", "TIER_RANGE_OVERLAP", "error.brokerTier.rangeOverlap");
            }
        }
    }

    private void requireAdmin() {
        if (!"BUILDER_ADMIN".equals(tenantContextBinder.current().role())) {
            throw new ForbiddenException("error.brokerTier.adminOnly");
        }
    }

    private void syncDesignationSlabRate(UUID orgId, String tierName, java.math.BigDecimal rate) {
        List<DesignationSlab> slabs = designationSlabRepository.findByNameForOrg(orgId, tierName);
        for (DesignationSlab slab : slabs) {
            slab.setRatePerSqft(rate);
            designationSlabRepository.save(slab);
        }
    }

    private BrokerTierResponse toResponse(BrokerTier t) {
        return new BrokerTierResponse(t.getId(), t.getName(), t.getNameHi(), t.getMinDeals(), t.getMaxDeals(),
                t.getBonusType().name(), t.getBonusValue(), t.getPerksDescription(), t.getSortOrder(), t.isActive());
    }
}
