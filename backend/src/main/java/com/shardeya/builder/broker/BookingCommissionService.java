package com.shardeya.builder.broker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shardeya.builder.broker.dto.BookingCommissionResponse;
import com.shardeya.builder.broker.dto.BrokerCommissionSummaryResponse;
import com.shardeya.builder.broker.dto.NetworkCommissionSummaryResponse;
import com.shardeya.builder.plot.PlotRepository;
import com.shardeya.builder.project.ProjectRepository;
import com.shardeya.builder.sale.PlotSaleRepository;
import com.shardeya.platform.ResourceNotFoundException;
import com.shardeya.platform.TenantContextBinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 06-BROKER-NETWORK-ENGINE.md §7, build-order step 5 -- the piece
 * PlotSaleService's own sale transaction calls directly (mirroring
 * CommissionLedgerService's own role for PERCENTAGE/FIXED brokers, never
 * the other way around) to freeze the full commission tree for a
 * DESIGNATION broker's booking. Resolves each chain member's CURRENT rate,
 * hands the chain to the pure CommissionCalculationEngine, and persists
 * one booking_commission row per line item -- total_amount and every
 * snapshotted rate are set once here and never touched again by
 * application code.
 */
@Service
public class BookingCommissionService {

    private final BookingCommissionRepository repository;
    private final BrokerPartnerRepository brokerRepository;
    private final BrokerNetworkService brokerNetworkService;
    private final DesignationSlabRepository designationSlabRepository;
    private final DesignationSlabService designationSlabService;
    private final ProjectRepository projectRepository;
    private final PlotRepository plotRepository;
    private final PlotSaleRepository saleRepository;
    private final TenantContextBinder tenantContextBinder;
    private final ObjectMapper objectMapper;

    public BookingCommissionService(BookingCommissionRepository repository, BrokerPartnerRepository brokerRepository,
                                     BrokerNetworkService brokerNetworkService, DesignationSlabRepository designationSlabRepository,
                                     DesignationSlabService designationSlabService,
                                     ProjectRepository projectRepository, PlotRepository plotRepository, PlotSaleRepository saleRepository,
                                     TenantContextBinder tenantContextBinder, ObjectMapper objectMapper) {
        this.repository = repository;
        this.brokerRepository = brokerRepository;
        this.brokerNetworkService = brokerNetworkService;
        this.designationSlabRepository = designationSlabRepository;
        this.designationSlabService = designationSlabService;
        this.projectRepository = projectRepository;
        this.plotRepository = plotRepository;
        this.saleRepository = saleRepository;
        this.tenantContextBinder = tenantContextBinder;
        this.objectMapper = objectMapper;
    }

    /** B-14 §4's "GET /brokers/{id}/ledger" pattern, applied to a DESIGNATION broker's own booking_commission rows -- the Ledger-tab-equivalent read surface for this broker type. */
    @Transactional(readOnly = true)
    public List<BookingCommissionResponse> listForBeneficiary(UUID brokerId) {
        UUID orgId = tenantContextBinder.currentOrgId();
        brokerRepository.findByIdAndOrgIdAndDeletedAtIsNull(brokerId, orgId).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        return repository.findByOrgIdAndBeneficiaryBrokerIdOrderByCreatedAtDesc(orgId, brokerId).stream().map(this::toResponse).toList();
    }

    /**
     * §26/§27 -- the money roll-up for one DESIGNATION broker's own
     * commission tree: personal (their own SELLING_BROKER rows, uplineLevel
     * 0) vs team (every row they're a beneficiary of, at any level) earned,
     * broken down by the three commission types, plus released/pending.
     * CANCELLED rows excluded throughout (see BrokerCommissionSummaryResponse's
     * own javadoc).
     */
    @Transactional(readOnly = true)
    public BrokerCommissionSummaryResponse commissionSummary(UUID brokerId) {
        UUID orgId = tenantContextBinder.currentOrgId();
        brokerRepository.findByIdAndOrgIdAndDeletedAtIsNull(brokerId, orgId).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        BookingCommission.Status cancelled = BookingCommission.Status.CANCELLED;
        return new BrokerCommissionSummaryResponse(
                repository.sumPersonalEarnedFor(brokerId, cancelled),
                repository.sumTotalEarnedFor(brokerId, cancelled),
                repository.sumEarnedByTypeFor(brokerId, BookingCommission.LineType.SELLING_BROKER, cancelled),
                repository.sumEarnedByTypeFor(brokerId, BookingCommission.LineType.UPLINE_DIFFERENTIAL, cancelled),
                repository.sumEarnedByTypeFor(brokerId, BookingCommission.LineType.NETWORK_SAME_SLAB_BONUS, cancelled),
                repository.sumReleasedFor(brokerId, cancelled),
                repository.sumPendingFor(brokerId, cancelled));
    }

    /** §29's builder/admin overview -- the same roll-up, summed across every DESIGNATION broker in the org. */
    @Transactional(readOnly = true)
    public NetworkCommissionSummaryResponse networkCommissionSummary() {
        UUID orgId = tenantContextBinder.currentOrgId();
        BookingCommission.Status cancelled = BookingCommission.Status.CANCELLED;
        return new NetworkCommissionSummaryResponse(
                repository.countDistinctBeneficiariesForOrg(orgId),
                repository.sumTotalEarnedForOrg(orgId, cancelled),
                repository.sumEarnedByTypeForOrg(orgId, BookingCommission.LineType.SELLING_BROKER, cancelled),
                repository.sumEarnedByTypeForOrg(orgId, BookingCommission.LineType.UPLINE_DIFFERENTIAL, cancelled),
                repository.sumEarnedByTypeForOrg(orgId, BookingCommission.LineType.NETWORK_SAME_SLAB_BONUS, cancelled),
                repository.sumReleasedForOrg(orgId, cancelled),
                repository.sumPendingForOrg(orgId, cancelled));
    }

    private BookingCommissionResponse toResponse(BookingCommission c) {
        String projectName = projectRepository.findByIdAndDeletedAtIsNull(c.getProjectId()).map(p -> p.getName()).orElse(null);
        String plotNumber = plotRepository.findByIdAndDeletedAtIsNull(c.getPlotId()).map(p -> p.getPlotNumber()).orElse(null);
        String buyerName = saleRepository.findByIdAndDeletedAtIsNull(c.getPlotSaleId()).map(s -> s.getBuyerName()).orElse(null);
        String sellingBrokerName = brokerRepository.findById(c.getSellingBrokerId()).map(BrokerPartner::getFullName).orElse(null);

        // §9/§8a: "amount actually PAID on a CANCELLED entry is exactly how
        // much needs recovering" -- gated on paidAmount, not releasedAmount
        // (see CommissionReversalCalculator's own javadoc: a
        // released-but-never-paid slice never left the builder's hand, so
        // it needs no recovery at all, just a void). Derived here, never a
        // stored flag or a second reversal row.
        boolean needsRecovery = c.getStatus() == BookingCommission.Status.CANCELLED && c.getPaidAmount().signum() > 0;
        BigDecimal recoveryAmount = needsRecovery
                ? CommissionReversalCalculator.reverseOne(new CommissionReversalCalculator.BeneficiarySnapshot(
                        c.getBeneficiaryBrokerId(), c.getTotalAmount(), c.getReleasedAmount(), c.getPaidAmount())).recoveryAmount()
                : null;

        return new BookingCommissionResponse(c.getId(), c.getPlotSaleId(), projectName, plotNumber, buyerName,
                c.getSellingBrokerId(), sellingBrokerName, c.getUplineLevel(), c.getCommissionType().name(),
                c.getPlotAreaSqft(), c.getCommissionPerSqft(), c.getTotalAmount(), c.getReleasedAmount(),
                c.getPaidAmount(), c.getPendingAmount(), c.getTotalAmount().subtract(c.getReleasedAmount()), c.getStatus().name(),
                c.getCreatedAt(), needsRecovery, recoveryAmount);
    }

    /**
     * §9/§41 -- cancels every beneficiary's frozen commission row for this
     * booking. Never deletes or re-zeroes released_amount (an append-only
     * historical fact, exactly like commission_ledger_entry.amount_paid) --
     * flipping status alone is enough: V65_008's own release trigger
     * already refuses to overwrite a CANCELLED status, so released_amount
     * is permanently frozen at whatever it was the instant this runs, and
     * needsRecovery/recoveryAmount derive correctly from it forever after
     * (see toResponse()). No-op if this sale never had a DESIGNATION
     * broker attributed (no rows exist) -- mirrors
     * CommissionLedgerService.cancelForSale()'s own no-op contract exactly.
     */
    @Transactional
    public void cancelForSale(UUID saleId) {
        for (BookingCommission row : repository.findByPlotSaleId(saleId)) {
            if (row.getStatus() != BookingCommission.Status.CANCELLED) {
                row.setStatus(BookingCommission.Status.CANCELLED);
                repository.save(row);
            }
        }
    }

    /**
     * §1: "the entire commission tree is calculated and FROZEN... using
     * each broker's CURRENT rate at this instant." Only ever called for a
     * DESIGNATION-type selling broker (PlotSaleService gates on
     * commissionType before calling this vs. CommissionLedgerService.createForSale) --
     * a PERCENTAGE/FIXED broker never gets a booking_commission row, and a
     * sale with no broker at all never calls this method.
     */
    @Transactional
    public void freezeForSale(UUID sellingBrokerId, UUID saleId, UUID projectId, UUID plotId, BigDecimal plotAreaSqft) {
        if (sellingBrokerId == null) {
            return;
        }
        UUID orgId = tenantContextBinder.currentOrgId();
        BrokerPartner seller = brokerRepository.findByIdAndOrgIdAndDeletedAtIsNull(sellingBrokerId, orgId)
                .orElseThrow(() -> new IllegalStateException("Selling broker " + sellingBrokerId + " not found for org " + orgId));

        List<CommissionCalculationEngine.ChainMember> chain = new ArrayList<>();
        Map<UUID, BrokerPartner> brokersById = new LinkedHashMap<>();
        // §7's revised same-slab formula needs each member's own next-slab
        // rate resolved up front -- the pure engine never looks anything
        // up itself (by design, see its own class javadoc), so this is the
        // one place that reads designation_slab config for it. Resolved
        // for the seller too even though it's only ever read when that
        // member later turns out to be an UPLINE on some OTHER booking's
        // chain -- never for this booking's own SELLING_BROKER row.
        chain.add(new CommissionCalculationEngine.ChainMember(seller.getId(), seller.getCurrentCommissionRate(),
                designationSlabService.resolveNextRate(orgId, seller.getCurrentDesignationId())));
        brokersById.put(seller.getId(), seller);

        for (BrokerNetwork ancestorRow : brokerNetworkService.ancestorsExcludingSelf(orgId, sellingBrokerId)) {
            UUID ancestorId = ancestorRow.getAncestorBrokerId();
            BrokerPartner ancestor = brokerRepository.findById(ancestorId)
                    .orElseThrow(() -> new IllegalStateException("Upline broker " + ancestorId + " referenced by broker_network but not found"));
            chain.add(new CommissionCalculationEngine.ChainMember(ancestor.getId(), ancestor.getCurrentCommissionRate(),
                    designationSlabService.resolveNextRate(orgId, ancestor.getCurrentDesignationId())));
            brokersById.put(ancestor.getId(), ancestor);
        }

        List<CommissionCalculationEngine.CommissionLineItem> lineItems = CommissionCalculationEngine.calculate(chain, plotAreaSqft);

        for (CommissionCalculationEngine.CommissionLineItem item : lineItems) {
            BrokerPartner beneficiary = brokersById.get(item.beneficiaryBrokerId());
            BrokerPartner directDownline = item.uplineLevel() == 0 ? null : brokersById.get(chain.get(item.uplineLevel() - 1).brokerId());

            BigDecimal commissionPerSqft = item.amount().divide(plotAreaSqft, 2, RoundingMode.HALF_UP);
            String snapshot = buildRateSnapshot(beneficiary, item.beneficiaryRatePerSqft(), directDownline, item.directDownlineRatePerSqft());

            BookingCommission row = new BookingCommission(UUID.randomUUID(), orgId, saleId, projectId, plotId,
                    sellingBrokerId, item.beneficiaryBrokerId(), (short) item.uplineLevel(),
                    BookingCommission.LineType.valueOf(item.type().name()), snapshot, plotAreaSqft, commissionPerSqft, item.amount());
            row.setCreatedBy(tenantContextBinder.current().userId());
            repository.save(row);
        }
    }

    private String buildRateSnapshot(BrokerPartner beneficiary, BigDecimal beneficiaryRate, BrokerPartner directDownline, BigDecimal directDownlineRate) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        DesignationSlab beneficiarySlab = beneficiary.getCurrentDesignationId() == null ? null
                : designationSlabRepository.findById(beneficiary.getCurrentDesignationId()).orElse(null);
        snapshot.put("beneficiaryDesignationId", beneficiary.getCurrentDesignationId());
        snapshot.put("beneficiaryDesignationName", beneficiarySlab == null ? null : beneficiarySlab.getName());
        snapshot.put("beneficiaryDesignationNameHi", beneficiarySlab == null ? null : beneficiarySlab.getNameHi());
        snapshot.put("beneficiaryRatePerSqft", beneficiaryRate);

        if (directDownline != null) {
            DesignationSlab downlineSlab = directDownline.getCurrentDesignationId() == null ? null
                    : designationSlabRepository.findById(directDownline.getCurrentDesignationId()).orElse(null);
            snapshot.put("directDownlineDesignationId", directDownline.getCurrentDesignationId());
            snapshot.put("directDownlineDesignationName", downlineSlab == null ? null : downlineSlab.getName());
            snapshot.put("directDownlineDesignationNameHi", downlineSlab == null ? null : downlineSlab.getNameHi());
            snapshot.put("directDownlineRatePerSqft", directDownlineRate);
        }

        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialise booking commission rate snapshot", e);
        }
    }
}
