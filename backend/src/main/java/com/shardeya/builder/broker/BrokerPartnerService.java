package com.shardeya.builder.broker;

import com.shardeya.builder.broker.dto.BankDetailsResponse;
import com.shardeya.builder.broker.dto.BrokerCreateRequest;
import com.shardeya.builder.broker.dto.BrokerDealResponse;
import com.shardeya.builder.broker.dto.BrokerNetworkNodeResponse;
import com.shardeya.builder.broker.dto.BrokerPerformanceResponse;
import com.shardeya.builder.broker.dto.BrokerResponse;
import com.shardeya.builder.broker.dto.BrokerUpdateRequest;
import com.shardeya.builder.project.ProjectRepository;
import com.shardeya.builder.plot.PlotRepository;
import com.shardeya.builder.sale.PlotSale;
import com.shardeya.builder.sale.PlotSaleRepository;
import com.shardeya.foundation.subscription.EntitlementService;
import com.shardeya.platform.BadRequestException;
import com.shardeya.platform.ConflictException;
import com.shardeya.platform.ForbiddenException;
import com.shardeya.platform.GovIdCipher;
import com.shardeya.platform.OutboxService;
import com.shardeya.platform.ResourceNotFoundException;
import com.shardeya.platform.SensitiveAccessLogService;
import com.shardeya.platform.TenantContext;
import com.shardeya.platform.TenantContextBinder;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * B-14 §20.1/§20.2 -- broker CRUD, deactivate/reactivate/block, bank
 * details reveal (SENSITIVE_VIEW, audited -- same pattern
 * PlotSaleService.revealGovId already established for buyer gov-IDs).
 */
@Service
public class BrokerPartnerService {

    private final BrokerPartnerRepository repository;
    private final BrokerTierRepository tierRepository;
    private final TenantContextBinder tenantContextBinder;
    private final EntitlementService entitlementService;
    private final GovIdCipher cipher;
    private final SensitiveAccessLogService sensitiveAccessLogService;
    private final OutboxService outboxService;
    private final EntityManager entityManager;
    private final PlotSaleRepository saleRepository;
    private final ProjectRepository projectRepository;
    private final PlotRepository plotRepository;
    private final CommissionLedgerEntryRepository ledgerRepository;
    private final DesignationSlabService designationSlabService;
    private final DesignationSlabRepository designationSlabRepository;
    private final BrokerNetworkService brokerNetworkService;
    private final BookingCommissionRepository bookingCommissionRepository;

    public BrokerPartnerService(BrokerPartnerRepository repository, BrokerTierRepository tierRepository,
                                 TenantContextBinder tenantContextBinder, EntitlementService entitlementService,
                                 GovIdCipher cipher, SensitiveAccessLogService sensitiveAccessLogService,
                                 OutboxService outboxService, EntityManager entityManager,
                                 PlotSaleRepository saleRepository, ProjectRepository projectRepository, PlotRepository plotRepository,
                                 CommissionLedgerEntryRepository ledgerRepository, DesignationSlabService designationSlabService,
                                 DesignationSlabRepository designationSlabRepository, BrokerNetworkService brokerNetworkService,
                                 BookingCommissionRepository bookingCommissionRepository) {
        this.repository = repository;
        this.tierRepository = tierRepository;
        this.tenantContextBinder = tenantContextBinder;
        this.entitlementService = entitlementService;
        this.cipher = cipher;
        this.sensitiveAccessLogService = sensitiveAccessLogService;
        this.outboxService = outboxService;
        this.entityManager = entityManager;
        this.saleRepository = saleRepository;
        this.projectRepository = projectRepository;
        this.plotRepository = plotRepository;
        this.ledgerRepository = ledgerRepository;
        this.designationSlabService = designationSlabService;
        this.designationSlabRepository = designationSlabRepository;
        this.brokerNetworkService = brokerNetworkService;
        this.bookingCommissionRepository = bookingCommissionRepository;
    }

    @Transactional(readOnly = true)
    public List<BrokerResponse> list(String status, UUID tierId, String search) {
        UUID orgId = tenantContextBinder.currentOrgId();
        return repository.findAll((root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("orgId"), orgId));
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), BrokerPartner.Status.valueOf(status)));
            }
            if (tierId != null) {
                predicates.add(cb.equal(root.get("tierId"), tierId));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")), like),
                        cb.like(root.get("mobile"), like)));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        }).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public BrokerResponse get(UUID id) {
        return toResponse(loadOwn(id));
    }

    /**
     * 06-BROKER-NETWORK-ENGINE.md §28 network tree -- every DESIGNATION
     * broker in the org, flat, with {@code uplineBrokerId} pointers for
     * the frontend to assemble into a tree. team_successful_bookings is
     * always 0 here today (the closure-table rollup trigger is
     * build-order step 7, not yet wired) -- an honest, documented
     * limitation of this step, not a display bug.
     */
    @Transactional(readOnly = true)
    public List<BrokerNetworkNodeResponse> listNetwork() {
        UUID orgId = tenantContextBinder.currentOrgId();
        return repository.findByOrgIdAndCommissionTypeAndDeletedAtIsNull(orgId, BrokerPartner.CommissionType.DESIGNATION)
                .stream()
                .map(b -> {
                    DesignationSlab designation = b.getCurrentDesignationId() == null ? null
                            : designationSlabRepository.findById(b.getCurrentDesignationId()).orElse(null);
                    return new BrokerNetworkNodeResponse(
                            b.getId(), b.getFullName(), b.getUplineBrokerId(),
                            designation == null ? null : designation.getName(), designation == null ? null : designation.getNameHi(),
                            b.getCurrentCommissionRate(), b.getPersonalSuccessfulBookings(), b.getTeamSuccessfulBookings(),
                            b.getStatus().name());
                })
                .toList();
    }

    @Transactional
    public BrokerResponse create(BrokerCreateRequest req) {
        UUID orgId = tenantContextBinder.currentOrgId();
        // 06-BROKER-NETWORK-ENGINE.md §0: FIXED is retired for NEW brokers
        // only -- an existing FIXED broker (M6 data) is untouched and its
        // routine edits still go through update(), which deliberately does
        // NOT carry this same restriction (see that method's own comment).
        if (req.commissionType() == BrokerPartner.CommissionType.FIXED) {
            throw new BadRequestException("commissionType", "COMMISSION_TYPE_FIXED_RETIRED", "error.broker.commissionTypeFixedRetired");
        }
        validateCommissionFields(req.commissionType(), req.commissionPct(), req.commissionFixed());
        // §0: PERCENTAGE brokers never participate in the hierarchy -- an
        // upline selected for one is a client error, not silently dropped.
        if (req.commissionType() == BrokerPartner.CommissionType.PERCENTAGE && req.uplineBrokerId() != null) {
            throw new BadRequestException("uplineBrokerId", "BROKER_UPLINE_NOT_ALLOWED", "error.broker.uplineNotAllowedForPercentage");
        }
        if (req.commissionType() == BrokerPartner.CommissionType.DESIGNATION) {
            // §31: upline is explicitly selected here, never inferred from
            // the adding user's own upline -- there is no such inference
            // anywhere in this method; the caller must pass it (or null).
            brokerNetworkService.assertCanSetUpline(orgId, null, req.uplineBrokerId());
        }

        // B-14 §21.1 "Broker quota enforced (Pro = 10)" -- BUILDER_BROKERS
        // was already seeded FREE=0 back in M2's V2_009 (which is what
        // "Free = N/A, module hidden" actually resolves to: a plan with a
        // 0 quota blocks every single add, same as a hidden module would,
        // without needing a separate boolean feature gate the way
        // BULK_UPLOAD_ENABLED needed one -- BUILDER_BROKERS was already a
        // numeric quota key, not a boolean one).
        entitlementService.assertWithinQuota(orgId, "BUILDER_BROKERS", null, "error.broker.quotaExceeded");

        if (repository.existsByOrgIdAndMobileAndDeletedAtIsNull(orgId, req.mobile())) {
            BrokerPartner existing = repository.findFirstByOrgIdAndMobileAndDeletedAtIsNull(orgId, req.mobile()).orElseThrow();
            throw new ConflictException("error.broker.duplicateMobile", Map.of(
                    "name", existing.getFullName(), "id", existing.getId().toString()));
        }

        UUID id = UUID.randomUUID();
        BrokerPartner broker = new BrokerPartner(id, orgId, req.fullName(), req.mobile(), req.commissionType());
        applyCreateFields(broker, req);

        if (req.commissionType() == BrokerPartner.CommissionType.DESIGNATION) {
            // §6: "new broker starts Business Executive / ₹160 / 0 / 0" --
            // resolved via the config table (0 team sales), never hardcoded,
            // same reasoning M6 already established for the zero-deal tier
            // lookup just below for PERCENTAGE/FIXED brokers.
            DesignationSlab startingSlab = designationSlabService.resolve(orgId, 0);
            broker.setCurrentDesignationId(startingSlab.getId());
            broker.setCurrentCommissionRate(startingSlab.getRatePerSqft());
            broker.setUplineBrokerId(req.uplineBrokerId());
        } else {
            // New broker always starts at whichever tier matches 0 deals
            // (Bronze) -- resolved the same way any tier lookup is, not
            // hardcoded to "the first tier", since a builder may have edited
            // their tier thresholds before adding their first broker.
            // PERCENTAGE/FIXED only -- DESIGNATION brokers don't use the
            // flat-tier system at all (§0: it's replaced by designations
            // for them).
            List<BrokerTier> zeroDealTiers = tierRepository.findMatchingTiers(orgId, 0);
            if (!zeroDealTiers.isEmpty()) {
                broker.setTierId(zeroDealTiers.get(0).getId());
                broker.setTierAssignedAt(java.time.Instant.now());
            }
        }

        broker = repository.save(broker);
        entityManager.flush();
        entityManager.refresh(broker);

        if (req.commissionType() == BrokerPartner.CommissionType.DESIGNATION) {
            brokerNetworkService.attachNewBroker(orgId, id, req.uplineBrokerId());
        }

        outboxService.enqueueNotification(orgId, "BROKER_ADDED", "notification.brokerAdded",
                "notification.brokerAddedBody", Map.of("name", req.fullName()), "broker_partner", id);

        return toResponse(broker);
    }

    @Transactional
    public BrokerResponse update(UUID id, BrokerUpdateRequest req) {
        BrokerPartner broker = loadOwn(id);
        if (req.fullName() != null) broker.setFullName(req.fullName());
        if (req.mobile() != null && !req.mobile().equals(broker.getMobile())) {
            UUID orgId = tenantContextBinder.currentOrgId();
            if (repository.existsByOrgIdAndMobileAndDeletedAtIsNull(orgId, req.mobile())) {
                throw new ConflictException("error.broker.duplicateMobile", Map.of());
            }
            broker.setMobile(req.mobile());
        }
        if (req.email() != null) broker.setEmail(req.email());
        if (req.cityArea() != null) broker.setCityArea(req.cityArea());
        if (req.reraNumber() != null) broker.setReraNumber(req.reraNumber());
        if (req.firmName() != null) broker.setFirmName(req.firmName());
        if (req.commissionType() != null) {
            validateCommissionFields(req.commissionType(), req.commissionPct(), req.commissionFixed());
            broker.setCommissionType(req.commissionType());
            broker.setCommissionPct(req.commissionPct());
            broker.setCommissionFixed(req.commissionFixed());
        }
        if (req.perProjectRatesEnabled() != null) broker.setPerProjectRatesEnabled(req.perProjectRatesEnabled());
        applyBankDetails(broker, req.bankAccountName(), req.bankAccountNumber(), req.ifsc(), req.upiId());
        if (req.notes() != null) broker.setNotes(req.notes());

        repository.save(broker);
        return toResponse(broker);
    }

    @Transactional
    public void deactivate(UUID id) {
        BrokerPartner broker = loadOwn(id);
        broker.setStatus(BrokerPartner.Status.INACTIVE);
        repository.save(broker);
    }

    @Transactional
    public void reactivate(UUID id) {
        BrokerPartner broker = loadOwn(id);
        broker.setStatus(BrokerPartner.Status.ACTIVE);
        repository.save(broker);
    }

    @Transactional
    public void block(UUID id) {
        // Admin only (B-14 §9) -- delete/block is stricter than the
        // BROKER_MANAGE permission this would otherwise share with
        // Manager, same "explicit role check, no dedicated permission
        // code" pattern ScheduleService.waive() already established.
        requireAdmin();
        BrokerPartner broker = loadOwn(id);
        broker.setStatus(BrokerPartner.Status.BLOCKED);
        repository.save(broker);
    }

    @Transactional
    public void delete(UUID id) {
        requireAdmin();
        BrokerPartner broker = loadOwn(id);
        if (broker.getTotalCommissionEarned() != null
                && broker.getTotalCommissionEarned().subtract(
                        broker.getTotalCommissionPaid() == null ? java.math.BigDecimal.ZERO : broker.getTotalCommissionPaid())
                .compareTo(java.math.BigDecimal.ZERO) > 0) {
            // B-14 §10: "Broker deleted with an outstanding ledger balance
            // -> blocked: 'Ramesh Sharma has Rs X in unpaid commission.
            // Settle or write off first.'"
            throw new ConflictException("error.broker.outstandingBalance", Map.of(
                    "name", broker.getFullName()));
        }
        broker.setDeletedAt(java.time.Instant.now());
        broker.setDeletedBy(tenantContextBinder.current().userId());
        repository.save(broker);
    }

    @Transactional(readOnly = true)
    public BankDetailsResponse bankDetails(UUID id, String reason) {
        BrokerPartner broker = loadOwn(id);
        sensitiveAccessLogService.record("broker_partner", id, "bank_account_number", reason);
        String number = broker.getBankAccountNumberEnc() != null ? cipher.decrypt(broker.getBankAccountNumberEnc()) : null;
        return new BankDetailsResponse(broker.getBankAccountName(), number, broker.getIfsc(), broker.getUpiId());
    }

    // B-14 §20.6 BrokerPerformancePanel: deals, revenue generated, commission
    // earned/pending, conversion rate. Computed from plot_sale directly
    // (not from commission_ledger_entry) so it still reports something
    // sensible even for a sale created before this broker ever had a
    // resolvable commission config (a manual-ledger sale, or -- pre-M6 data
    // that will never exist in this app's own history, but the query
    // doesn't assume a ledger entry exists for every attributed sale).
    @Transactional(readOnly = true)
    public BrokerPerformanceResponse performance(UUID id) {
        BrokerPartner broker = loadOwn(id);
        UUID orgId = tenantContextBinder.currentOrgId();
        List<PlotSale> sales = saleRepository.findByOrgIdAndBrokerPartnerIdAndDeletedAtIsNullOrderByPurchaseDateDesc(orgId, id);

        int completed = 0;
        int active = 0;
        int cancelled = 0;
        java.math.BigDecimal revenue = java.math.BigDecimal.ZERO;
        for (PlotSale s : sales) {
            switch (s.getStatus()) {
                case COMPLETED -> completed++;
                case ACTIVE -> active++;
                case CANCELLED -> cancelled++;
            }
            if (s.getStatus() != PlotSale.Status.CANCELLED) {
                revenue = revenue.add(s.getDealValue());
            }
        }
        java.math.BigDecimal[] earnedPaidDueReleased = commissionFiguresWithReleased(broker);
        java.math.BigDecimal earned = earnedPaidDueReleased[0];
        java.math.BigDecimal paid = earnedPaidDueReleased[1];
        java.math.BigDecimal due = earnedPaidDueReleased[2];
        java.math.BigDecimal released = earnedPaidDueReleased[3];
        // Conversion rate: closed-out (completed or cancelled) deals that
        // actually completed -- ACTIVE deals are still in flight and
        // shouldn't count against the broker either way yet.
        int decided = completed + cancelled;
        java.math.BigDecimal conversionRate = decided == 0 ? java.math.BigDecimal.ZERO
                : java.math.BigDecimal.valueOf(completed).multiply(java.math.BigDecimal.valueOf(100))
                        .divide(java.math.BigDecimal.valueOf(decided), 1, java.math.RoundingMode.HALF_UP);

        return new BrokerPerformanceResponse(sales.size(), completed, active, cancelled, revenue, earned, paid,
                released, due, conversionRate);
    }

    // B-14 §20.6 BrokerDealsTable. commissionEarned/commissionStatus come
    // from the sale's own commission_ledger_entry (the authoritative,
    // resolved-at-sale-time figure), NOT plot_sale.broker_commission_amount
    // -- that field is only ever populated for the external/not-in-system
    // broker path (M3) or the "no resolvable config" manual fallback (M6);
    // for the normal resolved-config path it stays null on the sale row
    // itself even though a real commission was computed and ledgered.
    @Transactional(readOnly = true)
    public List<BrokerDealResponse> deals(UUID id) {
        loadOwn(id);
        UUID orgId = tenantContextBinder.currentOrgId();
        return saleRepository.findByOrgIdAndBrokerPartnerIdAndDeletedAtIsNullOrderByPurchaseDateDesc(orgId, id).stream()
                .map(s -> {
                    var ledgerEntry = ledgerRepository.findByPlotSaleIdAndDeletedAtIsNull(s.getId()).orElse(null);
                    return new BrokerDealResponse(s.getId(),
                            projectRepository.findByIdAndDeletedAtIsNull(s.getProjectId()).map(p -> p.getName()).orElse(null),
                            plotRepository.findByIdAndDeletedAtIsNull(s.getPlotId()).map(p -> p.getPlotNumber()).orElse(null),
                            s.getBuyerName(), s.getPurchaseDate(), s.getDealValue(), s.getStatus().name(),
                            ledgerEntry == null ? null : ledgerEntry.getTotalCommission(),
                            ledgerEntry == null ? null : ledgerEntry.getStatus().name());
                })
                .toList();
    }

    BrokerPartner loadOwn(UUID id) {
        UUID orgId = tenantContextBinder.currentOrgId();
        return repository.findByIdAndOrgIdAndDeletedAtIsNull(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
    }

    private void requireAdmin() {
        if (!"BUILDER_ADMIN".equals(tenantContextBinder.current().role())) {
            throw new ForbiddenException("error.broker.adminOnly");
        }
    }

    private void validateCommissionFields(BrokerPartner.CommissionType type, java.math.BigDecimal pct, java.math.BigDecimal fixed) {
        if (type == BrokerPartner.CommissionType.PERCENTAGE && pct == null) {
            throw new BadRequestException("commissionPct", "COMMISSION_PCT_REQUIRED", "error.broker.commissionPctRequired");
        }
        if (type == BrokerPartner.CommissionType.FIXED && fixed == null) {
            throw new BadRequestException("commissionFixed", "COMMISSION_FIXED_REQUIRED", "error.broker.commissionFixedRequired");
        }
    }

    private void applyCreateFields(BrokerPartner broker, BrokerCreateRequest req) {
        broker.setEmail(req.email());
        broker.setCityArea(req.cityArea());
        broker.setReraNumber(req.reraNumber());
        broker.setFirmName(req.firmName());
        broker.setCommissionPct(req.commissionPct());
        broker.setCommissionFixed(req.commissionFixed());
        broker.setPerProjectRatesEnabled(req.perProjectRatesEnabled());
        applyBankDetails(broker, req.bankAccountName(), req.bankAccountNumber(), req.ifsc(), req.upiId());
        broker.setNotes(req.notes());
    }

    private void applyBankDetails(BrokerPartner broker, String name, String number, String ifsc, String upi) {
        if (name != null) broker.setBankAccountName(name);
        if (number != null && !number.isBlank()) {
            broker.setBankAccountNumberEnc(cipher.encrypt(number));
            broker.setBankAccountLast4(number.length() >= 4 ? number.substring(number.length() - 4) : number);
        }
        if (ifsc != null) broker.setIfsc(ifsc);
        if (upi != null) broker.setUpiId(upi);
    }

    private BrokerResponse toResponse(BrokerPartner b) {
        String tierName = null;
        if (b.getTierId() != null) {
            tierName = tierRepository.findById(b.getTierId()).map(BrokerTier::getName).orElse(null);
        }
        DesignationSlab designation = b.getCurrentDesignationId() == null ? null
                : designationSlabRepository.findById(b.getCurrentDesignationId()).orElse(null);
        java.math.BigDecimal[] earnedPaidDue = commissionFigures(b);
        java.math.BigDecimal earned = earnedPaidDue[0];
        java.math.BigDecimal paid = earnedPaidDue[1];
        return new BrokerResponse(b.getId(), b.getFullName(), b.getMobile(), b.getEmail(), b.getCityArea(), b.getFirmName(),
                b.getCommissionType().name(), b.getCommissionPct(), b.getCommissionFixed(), b.isPerProjectRatesEnabled(),
                b.getTierId(), tierName, b.getDealsClosedCount(), earned, paid, earnedPaidDue[2],
                b.getLastActiveAt(), b.getStatus().name(),
                b.getUplineBrokerId(), b.getCurrentDesignationId(), designation == null ? null : designation.getName(),
                designation == null ? null : designation.getNameHi(), b.getCurrentCommissionRate(),
                b.getPersonalSuccessfulBookings(), b.getTeamSuccessfulBookings(), b.isDesignationManuallyOverridden());
    }

    // broker_partner.total_commission_earned/total_commission_paid are only
    // ever trigger-maintained off commission_ledger_entry (V6_015) -- the
    // M6 PERCENTAGE/FIXED path. A DESIGNATION broker never writes a single
    // commission_ledger_entry row (M6.5 routes them through
    // booking_commission/commission_release/broker_commission_payment
    // instead), so those two raw columns stay permanently 0 for every
    // DESIGNATION broker even though real money is being tracked correctly
    // underneath -- exactly the same "top-of-page stat cards never rewired
    // to the new DESIGNATION tables" gap already disclosed in CLAUDE.md's
    // own "Milestone 6.5, steps 8 & 10" section. For a DESIGNATION broker:
    // "earned" is the frozen total across every booking_commission row
    // they're a beneficiary of (own sales as SELLING_BROKER, plus any
    // UPLINE_DIFFERENTIAL/NETWORK_SAME_SLAB_BONUS rows from downline
    // sales) -- the same "team" figure BookingCommissionService.commissionSummary()
    // already exposes on this broker's own Ledger tab, so the two figures
    // never disagree.
    //
    // §8a: "paid" and "due" are now REAL for a DESIGNATION broker, not
    // approximations -- BrokerCommissionPaymentService finally gives
    // paid_amount a write path, so "due" here is genuinely
    // Released - Paid (sumDueFor, which sums the same GENERATED
    // pending_amount column BrokerCommissionPaymentService's own hard-cap
    // check reads directly off the locked entities it holds -- the two can
    // never disagree). Before this round, "paid" was hardcoded ZERO and
    // "due" was just "released" (the best approximation possible with no
    // real payout tracking) -- see CLAUDE.md's own "Post-M6.5 Bug Fix"
    // section for that interim state and why it was later relabelled
    // "Commission Released" rather than "Due" on the frontend; now that
    // paid is real, "Due" is genuinely meaningful again and the frontend
    // label reverts accordingly (see BrokerDetailPage.tsx).
    // Returns {earned, paid, due} -- see commissionFiguresWithReleased()
    // for the 4-element form (adds "released") used by performance(),
    // where the detail page's stat cards need both Released and Due
    // visible per §8a's own "keep Released visible too" instruction.
    private java.math.BigDecimal[] commissionFigures(BrokerPartner b) {
        java.math.BigDecimal[] full = commissionFiguresWithReleased(b);
        return new java.math.BigDecimal[]{full[0], full[1], full[2]};
    }

    // {earned, paid, due, released}. For a DESIGNATION broker, "paid" isn't
    // itself a stored aggregate anywhere on broker_partner (unlike earned/
    // due/released, all derivable from booking_commission sums) -- it's
    // simply released minus due, since released = paid + due by
    // construction (booking_commission.pending_amount IS
    // released_amount - paid_amount). Deriving it this way, rather than
    // adding yet another repository sum query, keeps all four figures
    // trivially consistent with each other by construction.
    private java.math.BigDecimal[] commissionFiguresWithReleased(BrokerPartner b) {
        if (b.getCommissionType() == BrokerPartner.CommissionType.DESIGNATION) {
            BookingCommission.Status cancelled = BookingCommission.Status.CANCELLED;
            java.math.BigDecimal earned = bookingCommissionRepository.sumTotalEarnedFor(b.getId(), cancelled);
            java.math.BigDecimal released = bookingCommissionRepository.sumReleasedFor(b.getId(), cancelled);
            java.math.BigDecimal due = clampToZero(bookingCommissionRepository.sumDueFor(b.getId(), cancelled));
            java.math.BigDecimal paid = clampToZero(released.subtract(due));
            return new java.math.BigDecimal[]{earned, paid, due, released};
        }
        java.math.BigDecimal earned = b.getTotalCommissionEarned() == null ? java.math.BigDecimal.ZERO : b.getTotalCommissionEarned();
        java.math.BigDecimal paid = b.getTotalCommissionPaid() == null ? java.math.BigDecimal.ZERO : b.getTotalCommissionPaid();
        // No release concept for PERCENTAGE/FIXED brokers -- null, not
        // zero, so the frontend can tell "not applicable" apart from
        // "applicable and genuinely zero."
        return new java.math.BigDecimal[]{earned, paid, clampToZero(earned.subtract(paid)), null};
    }

    // "Commission due" can legitimately go slightly negative -- a broker
    // can end up paid marginally more than earned, since
    // CommissionPaymentService/the frontend's own RecordPaymentDialog both
    // deliberately let a payment go a few paise over the exact balance
    // when it matches what formatIndianCurrency's whole-rupee rounding
    // shows the user (see the ledger-payment rounding-mismatch fix above).
    // Displaying that as a raw negative figure renders as a confusing
    // "-₹0" once formatIndianCurrency rounds a small negative like -0.38
    // to negative zero -- "due" isn't a meaningful negative concept in
    // this UI (an overpaid broker owes nothing, not a negative amount), so
    // clamp at zero for display rather than surface the paise-level
    // overpayment artifact as if it were a real balance.
    private static java.math.BigDecimal clampToZero(java.math.BigDecimal value) {
        return value.signum() < 0 ? java.math.BigDecimal.ZERO.setScale(value.scale()) : value;
    }
}
