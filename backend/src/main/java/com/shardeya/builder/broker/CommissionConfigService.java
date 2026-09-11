package com.shardeya.builder.broker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shardeya.builder.broker.dto.CommissionConfigCreateRequest;
import com.shardeya.builder.broker.dto.CommissionConfigResponse;
import com.shardeya.builder.broker.dto.CommissionPreviewRequest;
import com.shardeya.builder.broker.dto.CommissionPreviewResponse;
import com.shardeya.builder.plot.PlotRepository;
import com.shardeya.builder.project.ProjectRepository;
import com.shardeya.platform.BadRequestException;
import com.shardeya.platform.ForbiddenException;
import com.shardeya.platform.ResourceNotFoundException;
import com.shardeya.platform.TenantContextBinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * B-14 §20.3/§7 -- commission config CRUD plus the resolution algorithm
 * itself. Commission configuration is Admin-only (§9: "a Manager setting
 * commission rates is a fraud vector") -- enforced here via an explicit
 * role check, same pattern ScheduleService.waive() and
 * BrokerPartnerService's block/delete already use for the fixed M-02
 * catalogue's lack of a dedicated permission code.
 */
@Service
public class CommissionConfigService {

    private final BrokerCommissionConfigRepository configRepository;
    private final BrokerPartnerRepository brokerRepository;
    private final BrokerTierRepository tierRepository;
    private final ProjectRepository projectRepository;
    private final PlotRepository plotRepository;
    private final TenantContextBinder tenantContextBinder;
    private final ObjectMapper objectMapper;

    public CommissionConfigService(BrokerCommissionConfigRepository configRepository, BrokerPartnerRepository brokerRepository,
                                    BrokerTierRepository tierRepository, ProjectRepository projectRepository,
                                    PlotRepository plotRepository, TenantContextBinder tenantContextBinder, ObjectMapper objectMapper) {
        this.configRepository = configRepository;
        this.brokerRepository = brokerRepository;
        this.tierRepository = tierRepository;
        this.projectRepository = projectRepository;
        this.plotRepository = plotRepository;
        this.tenantContextBinder = tenantContextBinder;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<CommissionConfigResponse> list(UUID brokerId) {
        // B-14 §9: "Commission configuration and tier configuration: Admin
        // only" is a blanket statement covering the whole surface, not just
        // writes -- commission rates can reveal sensitive
        // per-broker/per-project deal economics a builder may not want a
        // Manager to see, unlike the general broker roster (BROKER_VIEW).
        requireAdmin();
        UUID orgId = tenantContextBinder.currentOrgId();
        loadBroker(brokerId, orgId);
        return configRepository.findByOrgIdAndBrokerPartnerIdAndDeletedAtIsNullOrderByEffectiveFromDesc(orgId, brokerId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public CommissionConfigResponse create(UUID brokerId, CommissionConfigCreateRequest req) {
        requireAdmin();
        UUID orgId = tenantContextBinder.currentOrgId();
        loadBroker(brokerId, orgId);
        validateScopeTarget(req.scope(), req.projectId(), req.plotId());
        if (req.effectiveTo() != null && req.effectiveTo().isBefore(req.effectiveFrom())) {
            throw new BadRequestException("effectiveTo", "DATE_RANGE_INVALID", "error.commissionConfig.dateRangeInvalid");
        }

        UUID id = UUID.randomUUID();
        BrokerCommissionConfig config = new BrokerCommissionConfig(id, orgId, brokerId, req.scope(), req.projectId(),
                req.plotId(), req.commissionType(), req.rateValue(), req.effectiveFrom());
        config.setEffectiveTo(req.effectiveTo());
        configRepository.save(config);
        return toResponse(config);
    }

    @Transactional
    public CommissionConfigResponse update(UUID id, CommissionConfigCreateRequest req) {
        requireAdmin();
        UUID orgId = tenantContextBinder.currentOrgId();
        BrokerCommissionConfig config = configRepository.findByIdAndOrgIdAndDeletedAtIsNull(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        // B-14 §10: "Rate changed with a backdated effective_from -> warning
        // that it will not alter existing ledger entries" -- true here by
        // construction, since commission_ledger_entry.config_snapshot is a
        // self-contained copy taken at sale time, never a live reference
        // back to this row. Editing a config only ever affects FUTURE
        // resolve() calls, never past ones -- no extra guard needed beyond
        // that fact already being structurally true.
        config.setCommissionType(req.commissionType());
        config.setRateValue(req.rateValue());
        config.setEffectiveFrom(req.effectiveFrom());
        config.setEffectiveTo(req.effectiveTo());
        configRepository.save(config);
        return toResponse(config);
    }

    @Transactional
    public void delete(UUID id) {
        requireAdmin();
        UUID orgId = tenantContextBinder.currentOrgId();
        BrokerCommissionConfig config = configRepository.findByIdAndOrgIdAndDeletedAtIsNull(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        config.setDeletedAt(java.time.Instant.now());
        config.setDeletedBy(tenantContextBinder.current().userId());
        configRepository.save(config);
    }

    @Transactional(readOnly = true)
    public CommissionPreviewResponse preview(UUID brokerId, CommissionPreviewRequest req) {
        LocalDate saleDate = req.saleDate() != null ? req.saleDate() : com.shardeya.shared.IndianTime.today();
        Optional<CommissionResolution> resolved = resolve(brokerId, req.projectId(), req.plotId(), req.dealValue(), saleDate);
        if (resolved.isEmpty()) {
            // B-14 §10: "Broker with no commission configuration at sale
            // time -> falls back to their default; if that's also unset,
            // the sale form REQUIRES a manual commission amount rather
            // than silently recording zero." The preview card surfaces
            // this exact situation rather than showing a misleading Rs 0.
            throw new BadRequestException("brokerId", "NO_COMMISSION_CONFIG", "error.commissionConfig.noneConfigured");
        }
        CommissionResolution r = resolved.get();
        return new CommissionPreviewResponse(r.baseCommission(), r.tierBonus(), r.totalCommission(),
                r.appliedScope(), r.appliedCommissionType(), r.appliedRateValue(), r.tierName(), r.usedBrokerDefault());
    }

    /**
     * B-14 §7's resolve() algorithm. Empty return means "no config at any
     * level AND no broker default" -- the caller (PlotSaleService, or this
     * class's own preview()) decides what to do about that (require a
     * manual override amount, or a friendly 400).
     */
    @Transactional(readOnly = true)
    public Optional<CommissionResolution> resolve(UUID brokerId, UUID projectId, UUID plotId, BigDecimal dealValue, LocalDate saleDate) {
        UUID orgId = tenantContextBinder.currentOrgId();
        BrokerPartner broker = loadBroker(brokerId, orgId);

        BrokerCommissionConfig matched = null;
        if (plotId != null) {
            List<BrokerCommissionConfig> plotConfigs = configRepository.findEffectiveConfigs(
                    orgId, brokerId, BrokerCommissionConfig.Scope.PLOT, null, plotId, saleDate);
            if (!plotConfigs.isEmpty()) matched = plotConfigs.get(0);
        }
        if (matched == null && projectId != null) {
            List<BrokerCommissionConfig> projectConfigs = configRepository.findEffectiveConfigs(
                    orgId, brokerId, BrokerCommissionConfig.Scope.PROJECT, projectId, null, saleDate);
            if (!projectConfigs.isEmpty()) matched = projectConfigs.get(0);
        }
        if (matched == null) {
            List<BrokerCommissionConfig> globalConfigs = configRepository.findEffectiveConfigs(
                    orgId, brokerId, BrokerCommissionConfig.Scope.GLOBAL, null, null, saleDate);
            if (!globalConfigs.isEmpty()) matched = globalConfigs.get(0);
        }

        BrokerPartner.CommissionType type;
        BigDecimal rate;
        String scope;
        boolean usedDefault;
        UUID matchedConfigId = null;

        if (matched != null) {
            type = matched.getCommissionType();
            rate = matched.getRateValue();
            scope = matched.getScope().name();
            matchedConfigId = matched.getId();
            usedDefault = false;
        } else {
            boolean brokerDefaultUsable = broker.getCommissionType() == BrokerPartner.CommissionType.PERCENTAGE
                    ? broker.getCommissionPct() != null
                    : broker.getCommissionFixed() != null;
            if (!brokerDefaultUsable) {
                return Optional.empty();
            }
            type = broker.getCommissionType();
            rate = type == BrokerPartner.CommissionType.PERCENTAGE ? broker.getCommissionPct() : broker.getCommissionFixed();
            scope = "DEFAULT";
            usedDefault = true;
        }

        BigDecimal base = type == BrokerPartner.CommissionType.PERCENTAGE
                ? dealValue.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : rate.setScale(2, RoundingMode.HALF_UP);

        BigDecimal tierBonus = BigDecimal.ZERO;
        String tierName = null;
        UUID tierIdForSnapshot = null;
        BrokerTier.BonusType tierBonusType = null;
        BigDecimal tierBonusValue = null;
        if (broker.getTierId() != null) {
            BrokerTier tier = tierRepository.findById(broker.getTierId()).orElse(null);
            if (tier != null) {
                tierName = tier.getName();
                tierIdForSnapshot = tier.getId();
                tierBonusType = tier.getBonusType();
                tierBonusValue = tier.getBonusValue();
                if (tier.getBonusType() == BrokerTier.BonusType.PCT) {
                    tierBonus = base.multiply(tier.getBonusValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                } else if (tier.getBonusType() == BrokerTier.BonusType.FIXED) {
                    tierBonus = tier.getBonusValue().setScale(2, RoundingMode.HALF_UP);
                }
            }
        }

        BigDecimal total = base.add(tierBonus);
        String snapshotJson = buildSnapshot(matchedConfigId, scope, type, rate, tierIdForSnapshot, tierName, tierBonusType, tierBonusValue);

        return Optional.of(new CommissionResolution(base, tierBonus, total, scope, type.name(), rate, tierName, usedDefault, snapshotJson));
    }

    private String buildSnapshot(UUID configId, String scope, BrokerPartner.CommissionType type, BigDecimal rate,
                                  UUID tierId, String tierName, BrokerTier.BonusType tierBonusType, BigDecimal tierBonusValue) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("configId", configId == null ? null : configId.toString());
        snapshot.put("scope", scope);
        snapshot.put("commissionType", type.name());
        snapshot.put("rateValue", rate);
        snapshot.put("tierId", tierId == null ? null : tierId.toString());
        snapshot.put("tierName", tierName);
        snapshot.put("tierBonusType", tierBonusType == null ? null : tierBonusType.name());
        snapshot.put("tierBonusValue", tierBonusValue);
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialise commission config snapshot", e);
        }
    }

    private void validateScopeTarget(BrokerCommissionConfig.Scope scope, UUID projectId, UUID plotId) {
        boolean valid = switch (scope) {
            case GLOBAL -> projectId == null && plotId == null;
            case PROJECT -> projectId != null && plotId == null;
            case PLOT -> plotId != null;
        };
        if (!valid) {
            throw new BadRequestException("scope", "SCOPE_TARGET_MISMATCH", "error.commissionConfig.scopeTargetMismatch");
        }
        if (projectId != null) {
            projectRepository.findByIdAndDeletedAtIsNull(projectId).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        }
        if (plotId != null) {
            plotRepository.findByIdAndDeletedAtIsNull(plotId).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        }
    }

    private BrokerPartner loadBroker(UUID brokerId, UUID orgId) {
        return brokerRepository.findByIdAndOrgIdAndDeletedAtIsNull(brokerId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
    }

    private void requireAdmin() {
        if (!"BUILDER_ADMIN".equals(tenantContextBinder.current().role())) {
            throw new ForbiddenException("error.commissionConfig.adminOnly");
        }
    }

    private CommissionConfigResponse toResponse(BrokerCommissionConfig c) {
        String projectName = c.getProjectId() == null ? null : projectRepository.findByIdAndDeletedAtIsNull(c.getProjectId()).map(p -> p.getName()).orElse(null);
        String plotNumber = c.getPlotId() == null ? null : plotRepository.findByIdAndDeletedAtIsNull(c.getPlotId()).map(p -> p.getPlotNumber()).orElse(null);
        return new CommissionConfigResponse(c.getId(), c.getBrokerPartnerId(), c.getScope().name(), c.getProjectId(), projectName,
                c.getPlotId(), plotNumber, c.getCommissionType().name(), c.getRateValue(), c.getEffectiveFrom(), c.getEffectiveTo());
    }
}
