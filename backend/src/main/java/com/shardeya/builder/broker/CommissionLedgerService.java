package com.shardeya.builder.broker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shardeya.builder.broker.dto.CommissionLedgerEntryResponse;
import com.shardeya.builder.plot.PlotRepository;
import com.shardeya.builder.project.ProjectRepository;
import com.shardeya.builder.sale.PlotSaleRepository;
import com.shardeya.platform.BadRequestException;
import com.shardeya.platform.ResourceNotFoundException;
import com.shardeya.platform.TenantContextBinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * B-14 §7/§10 -- the piece PlotSaleService's own sale transaction calls
 * directly (never the other way around) to create/cancel the one
 * commission_ledger_entry a sale can have. Kept as its own service, separate
 * from CommissionConfigService (which only computes numbers, stateless) and
 * from the not-yet-written CommissionPaymentService (which records money
 * actually paid out against an entry) -- PlotSaleService needs exactly this
 * one entry point and shouldn't have to know about broker-payment recording
 * at all.
 */
@Service
public class CommissionLedgerService {

    private final CommissionLedgerEntryRepository ledgerRepository;
    private final CommissionConfigService commissionConfigService;
    private final BrokerPartnerRepository brokerRepository;
    private final ProjectRepository projectRepository;
    private final PlotRepository plotRepository;
    private final PlotSaleRepository saleRepository;
    private final TenantContextBinder tenantContextBinder;
    private final ObjectMapper objectMapper;

    public CommissionLedgerService(CommissionLedgerEntryRepository ledgerRepository, CommissionConfigService commissionConfigService,
                                    BrokerPartnerRepository brokerRepository, ProjectRepository projectRepository,
                                    PlotRepository plotRepository, PlotSaleRepository saleRepository,
                                    TenantContextBinder tenantContextBinder, ObjectMapper objectMapper) {
        this.ledgerRepository = ledgerRepository;
        this.commissionConfigService = commissionConfigService;
        this.brokerRepository = brokerRepository;
        this.projectRepository = projectRepository;
        this.plotRepository = plotRepository;
        this.saleRepository = saleRepository;
        this.tenantContextBinder = tenantContextBinder;
        this.objectMapper = objectMapper;
    }

    /**
     * B-14 §10: "Broker with no commission configuration at sale time ->
     * falls back to their default; if that's also unset, the sale form
     * REQUIRES a manual commission amount rather than silently recording
     * zero." resolve() succeeding always wins over a manually-typed amount
     * on the sale form (the configured policy is authoritative once one
     * exists) -- manualCommissionAmount is only ever actually used as the
     * ledger's own commission figure on the fallback path, when resolve()
     * comes back empty. Throws BadRequestException if neither is available.
     * No-op (nothing created) when brokerId is null -- an external,
     * not-in-system broker (free-text name/mobile on the sale row) never
     * gets ledger automation, per B-14 §10's own scope note.
     */
    @Transactional
    public void createForSale(UUID brokerId, UUID saleId, UUID projectId, UUID plotId, LocalDate dealDate,
                               BigDecimal dealValue, BigDecimal manualCommissionAmount) {
        if (brokerId == null) {
            return;
        }
        UUID orgId = tenantContextBinder.currentOrgId();
        Optional<CommissionResolution> resolved = commissionConfigService.resolve(brokerId, projectId, plotId, dealValue, dealDate);

        BigDecimal base;
        BigDecimal bonus;
        String snapshot;
        if (resolved.isPresent()) {
            CommissionResolution r = resolved.get();
            base = r.baseCommission();
            bonus = r.tierBonus();
            snapshot = r.configSnapshotJson();
        } else {
            if (manualCommissionAmount == null) {
                throw new BadRequestException("brokerCommissionAmount", "COMMISSION_AMOUNT_REQUIRED", "error.sale.commissionAmountRequired");
            }
            base = manualCommissionAmount.setScale(2, java.math.RoundingMode.HALF_UP);
            bonus = BigDecimal.ZERO;
            snapshot = buildManualSnapshot(base);
        }

        UUID id = UUID.randomUUID();
        CommissionLedgerEntry entry = new CommissionLedgerEntry(id, orgId, brokerId, saleId, projectId, plotId,
                dealDate, dealValue, base, bonus, snapshot);
        entry.setCreatedBy(tenantContextBinder.current().userId());
        ledgerRepository.save(entry);
    }

    /**
     * B-14 §10: "Deal cancelled after commission paid -> recovery entry
     * with a clear 'Recover Rs X from Ramesh Sharma' flag; never a silent
     * deletion." Deliberately not a second ledger row -- CANCELLED plus the
     * existing amount_paid (already trigger-maintained, never deleted) IS
     * the full recovery fact: amount_paid on a CANCELLED entry is exactly
     * "how much needs recovering," so a second, parallel "negative entry"
     * concept would just be two records answering the same question.
     * CommissionLedgerEntryResponse derives needsRecovery/recoveryAmount
     * from this same status+amountPaid combination at read time.
     * No-op if the sale never had a broker attributed (no entry exists).
     */
    @Transactional
    public void cancelForSale(UUID saleId) {
        UUID orgId = tenantContextBinder.currentOrgId();
        ledgerRepository.findByPlotSaleIdAndDeletedAtIsNull(saleId).ifPresent(entry -> {
            if (!entry.getOrgId().equals(orgId)) {
                return;
            }
            entry.setStatus(CommissionLedgerEntry.Status.CANCELLED);
            entry.setUpdatedBy(tenantContextBinder.current().userId());
            ledgerRepository.save(entry);
        });
    }

    @Transactional(readOnly = true)
    public CommissionLedgerEntryResponse get(UUID id) {
        UUID orgId = tenantContextBinder.currentOrgId();
        CommissionLedgerEntry entry = ledgerRepository.findByIdAndOrgIdAndDeletedAtIsNull(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        return toResponse(entry);
    }

    // B-14 §4 "GET /brokers/{id}/ledger?status=&from=&to=" -- a broker's own ledger.
    @Transactional(readOnly = true)
    public List<CommissionLedgerEntryResponse> listForBroker(UUID brokerId, CommissionLedgerEntry.Status status, LocalDate from, LocalDate to) {
        UUID orgId = tenantContextBinder.currentOrgId();
        brokerRepository.findByIdAndOrgIdAndDeletedAtIsNull(brokerId, orgId).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        return search(orgId, brokerId, null, status, from, to);
    }

    // B-14 §4 "GET /commission-ledger?brokerId=&projectId=&status=&from=&to=" -- org-wide view.
    @Transactional(readOnly = true)
    public List<CommissionLedgerEntryResponse> listOrgWide(UUID brokerId, UUID projectId, CommissionLedgerEntry.Status status, LocalDate from, LocalDate to) {
        UUID orgId = tenantContextBinder.currentOrgId();
        return search(orgId, brokerId, projectId, status, from, to);
    }

    private List<CommissionLedgerEntryResponse> search(UUID orgId, UUID brokerId, UUID projectId, CommissionLedgerEntry.Status status,
                                                         LocalDate from, LocalDate to) {
        return ledgerRepository.findAll((root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("orgId"), orgId));
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (brokerId != null) predicates.add(cb.equal(root.get("brokerPartnerId"), brokerId));
            if (projectId != null) predicates.add(cb.equal(root.get("projectId"), projectId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("dealDate"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("dealDate"), to));
            query.orderBy(cb.desc(root.get("dealDate")));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        }).stream().map(this::toResponse).toList();
    }

    private CommissionLedgerEntryResponse toResponse(CommissionLedgerEntry e) {
        String brokerName = brokerRepository.findById(e.getBrokerPartnerId()).map(BrokerPartner::getFullName).orElse(null);
        String projectName = projectRepository.findByIdAndDeletedAtIsNull(e.getProjectId()).map(p -> p.getName()).orElse(null);
        String plotNumber = plotRepository.findByIdAndDeletedAtIsNull(e.getPlotId()).map(p -> p.getPlotNumber()).orElse(null);
        String buyerName = saleRepository.findByIdAndDeletedAtIsNull(e.getPlotSaleId()).map(s -> s.getBuyerName()).orElse(null);

        // B-14 §10's "recovery entry" for a cancelled deal whose commission
        // was already paid -- derived here, not a stored flag (see
        // CommissionLedgerService.cancelForSale()'s own comment).
        boolean needsRecovery = e.getStatus() == CommissionLedgerEntry.Status.CANCELLED
                && e.getAmountPaid() != null && e.getAmountPaid().compareTo(BigDecimal.ZERO) > 0;
        BigDecimal recoveryAmount = needsRecovery ? e.getAmountPaid() : null;

        return new CommissionLedgerEntryResponse(e.getId(), e.getBrokerPartnerId(), brokerName, e.getPlotSaleId(),
                projectName, plotNumber, buyerName, e.getDealDate(), e.getDealValue(), e.getBaseCommission(),
                e.getTierBonus(), e.getTotalCommission(), e.getAmountPaid(), e.getBalanceDue(), e.getStatus().name(),
                needsRecovery, recoveryAmount);
    }

    private String buildManualSnapshot(BigDecimal amount) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("configId", null);
        snapshot.put("scope", "MANUAL");
        snapshot.put("commissionType", "FIXED");
        snapshot.put("rateValue", amount);
        snapshot.put("tierId", null);
        snapshot.put("tierName", null);
        snapshot.put("tierBonusType", null);
        snapshot.put("tierBonusValue", null);
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialise manual commission snapshot", e);
        }
    }
}
