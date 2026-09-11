package com.shardeya.builder.sale;

import com.shardeya.builder.broker.BookingCommissionService;
import com.shardeya.builder.broker.BrokerPartner;
import com.shardeya.builder.broker.BrokerPartnerRepository;
import com.shardeya.builder.broker.BrokerTierService;
import com.shardeya.builder.broker.CommissionLedgerEntry;
import com.shardeya.builder.broker.CommissionLedgerEntryRepository;
import com.shardeya.builder.broker.CommissionLedgerService;
import com.shardeya.builder.broker.DesignationPromotionService;
import com.shardeya.builder.payment.PaymentSchedule;
import com.shardeya.builder.payment.PaymentScheduleRepository;
import com.shardeya.builder.plot.Plot;
import com.shardeya.builder.plot.PlotRepository;
import com.shardeya.builder.sale.dto.CancelSaleRequest;
import com.shardeya.builder.sale.dto.GovIdRevealResponse;
import com.shardeya.builder.sale.dto.SaleCreateRequest;
import com.shardeya.builder.sale.dto.SaleResponse;
import com.shardeya.builder.sale.dto.SaleUpdateRequest;
import com.shardeya.builder.sale.dto.ScheduleRowRequest;
import com.shardeya.foundation.media.MediaService;
import com.shardeya.foundation.notification.BuyerWhatsAppOptInService;
import com.shardeya.platform.BadRequestException;
import com.shardeya.platform.ConflictException;
import com.shardeya.platform.ForbiddenException;
import com.shardeya.platform.GovIdCipher;
import com.shardeya.platform.OutboxService;
import com.shardeya.platform.ProjectAccessGuard;
import com.shardeya.platform.ResourceNotFoundException;
import com.shardeya.platform.SensitiveAccessLogService;
import com.shardeya.platform.TenantContextBinder;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class PlotSaleService {

    private static final Pattern NON_ALNUM = Pattern.compile("[^A-Za-z0-9]");

    private final PlotSaleRepository repository;
    private final PlotRepository plotRepository;
    private final PaymentScheduleRepository scheduleRepository;
    private final ProjectAccessGuard accessGuard;
    private final TenantContextBinder tenantContextBinder;
    private final GovIdCipher govIdCipher;
    private final MediaService mediaService;
    private final OutboxService outboxService;
    private final SensitiveAccessLogService sensitiveAccessLogService;
    private final EntityManager entityManager;

    private final com.shardeya.builder.payment.ScheduleService scheduleService;
    private final CommissionLedgerService commissionLedgerService;
    private final BrokerTierService brokerTierService;
    private final BrokerPartnerRepository brokerPartnerRepository;
    private final CommissionLedgerEntryRepository commissionLedgerEntryRepository;
    private final BookingCommissionService bookingCommissionService;
    private final DesignationPromotionService designationPromotionService;
    private final BuyerWhatsAppOptInService buyerWhatsAppOptInService;

    public PlotSaleService(PlotSaleRepository repository, PlotRepository plotRepository,
                            PaymentScheduleRepository scheduleRepository, ProjectAccessGuard accessGuard,
                            TenantContextBinder tenantContextBinder, GovIdCipher govIdCipher, MediaService mediaService,
                            OutboxService outboxService, SensitiveAccessLogService sensitiveAccessLogService,
                            EntityManager entityManager, com.shardeya.builder.payment.ScheduleService scheduleService,
                            CommissionLedgerService commissionLedgerService, BrokerTierService brokerTierService,
                            BrokerPartnerRepository brokerPartnerRepository,
                            CommissionLedgerEntryRepository commissionLedgerEntryRepository,
                            BookingCommissionService bookingCommissionService,
                            DesignationPromotionService designationPromotionService,
                            BuyerWhatsAppOptInService buyerWhatsAppOptInService) {
        this.repository = repository;
        this.plotRepository = plotRepository;
        this.scheduleRepository = scheduleRepository;
        this.accessGuard = accessGuard;
        this.tenantContextBinder = tenantContextBinder;
        this.govIdCipher = govIdCipher;
        this.mediaService = mediaService;
        this.outboxService = outboxService;
        this.sensitiveAccessLogService = sensitiveAccessLogService;
        this.brokerPartnerRepository = brokerPartnerRepository;
        this.entityManager = entityManager;
        this.scheduleService = scheduleService;
        this.commissionLedgerService = commissionLedgerService;
        this.brokerTierService = brokerTierService;
        this.commissionLedgerEntryRepository = commissionLedgerEntryRepository;
        this.bookingCommissionService = bookingCommissionService;
        this.designationPromotionService = designationPromotionService;
        this.buyerWhatsAppOptInService = buyerWhatsAppOptInService;
    }

    // price_per_unit-style refresh -- total_paid/balance_due are DB
    // GENERATED/trigger-maintained columns Hibernate never populates on
    // save(), only on a fresh SELECT (same pattern as PlotService.refresh()).
    private void refresh(PlotSale sale) {
        entityManager.flush();
        entityManager.refresh(sale);
    }

    @Transactional
    public SaleResponse create(UUID plotId, SaleCreateRequest req) {
        Plot plot = plotRepository.findByIdAndDeletedAtIsNull(plotId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        accessGuard.assertAccess(plot.getProjectId());

        // ux_plot_active_sale is the real concurrency backstop (two staff
        // selling the same plot at once -- the loser's INSERT fails on the
        // unique index, B-04 §10); this pre-flight check just gives a
        // friendlier message for the ordinary sequential case.
        if (plot.getStatus() == Plot.Status.SOLD || repository.findActiveByPlotId(plotId, PlotSale.Status.CANCELLED).isPresent()) {
            throw new ConflictException("error.sale.plotAlreadySold");
        }

        // B-04 §11: purchase_date required, <= today, >= today - 10 years --
        // checked against IST's "today" (CLAUDE.md rule #11), not a Bean
        // Validation annotation (those validate against the JVM's default
        // zone, not guaranteed IST).
        var today = com.shardeya.shared.IndianTime.today();
        if (req.purchaseDate().isAfter(today) || req.purchaseDate().isBefore(today.minusYears(10))) {
            throw new BadRequestException("purchaseDate", "PURCHASE_DATE_OUT_OF_RANGE", "error.sale.purchaseDateInvalid");
        }

        BigDecimal scheduleSum = req.schedule().stream().map(ScheduleRowRequest::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (scheduleSum.compareTo(req.dealValue()) > 0) {
            throw new BadRequestException("schedule", "SCHEDULE_EXCEEDS_DEAL_VALUE", "error.sale.scheduleExceedsDealValue");
        }

        UUID orgId = tenantContextBinder.currentOrgId();
        UUID saleId = UUID.randomUUID();

        PlotSale sale = new PlotSale(saleId, orgId, plotId, plot.getProjectId(), req.buyerName(), req.buyerMobile(),
                req.purchaseDate(), req.dealValue(), req.paymentType());
        sale.setCustomerId(req.customerId());
        sale.setBuyerEmail(req.buyerEmail());
        applyGovId(sale, req.buyerGovIdType(), req.buyerGovIdNumber());
        sale.setBuyerGovIdMediaId(req.buyerGovIdMediaId());
        sale.setBrokerPartnerId(req.brokerPartnerId());
        sale.setExternalBrokerName(req.externalBrokerName());
        sale.setExternalBrokerMobile(req.externalBrokerMobile());
        sale.setBrokerCommissionAmount(req.brokerCommissionAmount());
        sale.setHandledBy(req.handledBy() != null ? req.handledBy() : tenantContextBinder.current().userId());

        sale = repository.save(sale);

        int seq = 1;
        for (ScheduleRowRequest row : req.schedule()) {
            scheduleRepository.save(new PaymentSchedule(UUID.randomUUID(), orgId, saleId, seq++, row.label(), row.amount(), row.dueDate()));
        }
        entityManager.flush();
        scheduleService.syncAllInstalmentProjections(saleId);

        // Single write path for plot.status/current_sale_id (01-DATA-MODEL.md
        // §13 denormalisation contract: "Application service (single write
        // path)") -- this IS that path; nothing else in the codebase may set
        // plot.status to SOLD directly (PlotService explicitly rejects it).
        plot.setStatus(Plot.Status.SOLD);
        plot.setCurrentSaleId(saleId);
        plotRepository.save(plot);

        // B-14 §7/§10 -- only for a real, in-system broker (brokerPartnerId);
        // an external/not-in-system broker (free-text name+commission on the
        // sale row) never gets ledger automation. Throws (rolling back this
        // whole transaction, same all-or-nothing atomicity as every other
        // piece of sale creation) if the broker has no resolvable commission
        // config AND no manual brokerCommissionAmount was supplied.
        //
        // 06-BROKER-NETWORK-ENGINE.md §1/§7, build-order step 5 -- a
        // DESIGNATION broker takes a completely different path here: no
        // commission_ledger_entry at all (CommissionConfigService.resolve()
        // would find no config AND no usable broker default for a
        // DESIGNATION broker, which is exactly the "manual amount required"
        // case CommissionLedgerService.createForSale() would otherwise throw
        // on -- SaleWizard never collects a manual amount for an in-system
        // broker post-M6, so that path is a dead end for this broker type).
        // Instead the full commission tree (seller + every upline
        // differential/bonus) is frozen as booking_commission rows, using
        // each broker's CURRENT rate at this instant (§1) -- PERCENTAGE/FIXED
        // brokers are completely unaffected by this branch and keep using
        // the exact M6 ledger path unchanged.
        if (req.brokerPartnerId() != null) {
            BrokerPartner broker = brokerPartnerRepository.findByIdAndOrgIdAndDeletedAtIsNull(req.brokerPartnerId(), orgId)
                    .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
            if (broker.getCommissionType() == BrokerPartner.CommissionType.DESIGNATION) {
                bookingCommissionService.freezeForSale(req.brokerPartnerId(), saleId, plot.getProjectId(), plotId, plot.getSizeSqft());
                // 06-BROKER-NETWORK-ENGINE.md §1/§4/§6, REVISED post-ship:
                // counting toward sales + promotion now happens at BOOKED,
                // not COMPLETED -- moved here from complete() below. Must
                // run AFTER freezeForSale() above, never before: the
                // freeze reads the broker's rate as it is right now, and
                // this call can promote that SAME broker (or an upline)
                // to a new rate -- the booking that earns the promotion
                // must itself stay frozen at the OLD rate (§6). The
                // trigger backing personal/team_successful_bookings
                // (V65_013) already fired as a side effect of the
                // plot_sale INSERT flushed earlier in this method (before
                // the schedule-sync call above), so evaluateAndPromote()'s
                // own entityManager.refresh() sees the fresh, already-
                // incremented counts here.
                designationPromotionService.evaluateAndPromote(req.brokerPartnerId());
            } else {
                commissionLedgerService.createForSale(req.brokerPartnerId(), saleId, plot.getProjectId(), plotId,
                        req.purchaseDate(), req.dealValue(), req.brokerCommissionAmount());
            }
        }

        outboxService.enqueueNotification(orgId, "PLOT_SOLD", "notification.plotSold", null,
                Map.of("plotNumber", plot.getPlotNumber(), "buyerName", req.buyerName()), "plot_sale", saleId);

        // Only a POSITIVE tick creates a row -- an unchecked box at sale
        // creation means "never asked," not an explicit opt-out, so no row
        // is written either way when false (BuyerWhatsAppOptInService's own
        // no-op-when-absent contract already gives the correct "not opted
        // in" answer with no row at all).
        if (req.buyerWhatsappOptIn()) {
            buyerWhatsAppOptInService.setOptIn(orgId, req.buyerMobile(), true, tenantContextBinder.current().userId());
        }

        refresh(sale);
        return toResponse(sale);
    }

    public SaleResponse get(UUID saleId) {
        PlotSale sale = loadOwn(saleId);
        return toResponse(sale);
    }

    public SaleResponse getByPlotId(UUID plotId) {
        PlotSale sale = repository.findActiveByPlotId(plotId, PlotSale.Status.CANCELLED).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        accessGuard.assertAccess(sale.getProjectId());
        return toResponse(sale);
    }

    @Transactional
    public SaleResponse update(UUID saleId, SaleUpdateRequest req) {
        PlotSale sale = loadOwn(saleId);
        if (req.buyerName() != null) sale.setBuyerName(req.buyerName());
        if (req.buyerMobile() != null) sale.setBuyerMobile(req.buyerMobile());
        if (req.buyerEmail() != null) sale.setBuyerEmail(req.buyerEmail());
        if (req.dealValue() != null) sale.setDealValue(req.dealValue());
        if (req.externalBrokerName() != null) sale.setExternalBrokerName(req.externalBrokerName());
        if (req.externalBrokerMobile() != null) sale.setExternalBrokerMobile(req.externalBrokerMobile());
        if (req.brokerCommissionAmount() != null) sale.setBrokerCommissionAmount(req.brokerCommissionAmount());
        repository.save(sale);
        refresh(sale);
        return toResponse(sale);
    }

    // A buyer may agree to (or later withdraw) WhatsApp reminders any time
    // after the sale itself was created -- not just the one moment the
    // wizard's checkbox was shown. Keyed by the sale's CURRENT buyerMobile,
    // so editing the buyer's mobile via update() above and then toggling
    // consent here correctly targets the new number; the old number's own
    // opt-in row (if any) is simply left as a historical fact for whichever
    // real person that number belonged to.
    @Transactional
    public SaleResponse setBuyerWhatsAppOptIn(UUID saleId, boolean optedIn) {
        PlotSale sale = loadOwn(saleId);
        buyerWhatsAppOptInService.setOptIn(sale.getOrgId(), sale.getBuyerMobile(), optedIn, tenantContextBinder.current().userId());
        return toResponse(sale);
    }

    // B-04 §7: sets CANCELLED, returns the plot to AVAILABLE, cancels unpaid
    // schedule rows. Payment records are retained (immutable, never
    // touched here) -- any refund is the builder's own manual bookkeeping,
    // never processed by the platform (§10).
    @Transactional
    public SaleResponse cancel(UUID saleId, CancelSaleRequest req) {
        PlotSale sale = loadOwn(saleId);
        if (sale.getStatus() == PlotSale.Status.CANCELLED) {
            throw new ConflictException("error.sale.alreadyCancelled");
        }
        sale.setStatus(PlotSale.Status.CANCELLED);
        sale.setCancelledAt(Instant.now());
        sale.setCancellationReason(req.reason() + (req.refundHandling() != null ? " | " + req.refundHandling() : ""));
        repository.save(sale);

        for (PaymentSchedule schedule : scheduleRepository.findByPlotSaleIdAndDeletedAtIsNullOrderBySequenceNoAsc(saleId)) {
            if (schedule.getStatus() != PaymentSchedule.Status.PAID) {
                schedule.setStatus(PaymentSchedule.Status.WAIVED);
                // Same class of bug as PaymentService's cheque-bounce reason
                // (fixed alongside this one): waiveReason is free text shown
                // directly to the user, not an i18n key.
                schedule.setWaiveReason("Waived automatically -- sale was cancelled");
                scheduleRepository.save(schedule);
            }
        }

        Plot plot = plotRepository.findByIdAndDeletedAtIsNull(sale.getPlotId())
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        plot.setStatus(Plot.Status.AVAILABLE);
        plot.setCurrentSaleId(null);
        plotRepository.save(plot);

        // Every schedule row above just moved out of "still due" (PAID stays
        // PAID, everything else became WAIVED) -- same removal B-09 §10
        // describes for a paid instalment applies equally to a waived one.
        scheduleService.syncAllInstalmentProjections(saleId);

        // B-14 §10: cancelling a sale marks its ledger entry CANCELLED too
        // (no-op if the sale never had a broker attributed). If commission
        // was already paid out (amount_paid > 0), the row is deliberately
        // left as-is rather than deleted or zeroed -- see
        // CommissionLedgerService.cancelForSale()'s own comment for why this
        // status+amount_paid combination alone is the "recovery" record.
        commissionLedgerService.cancelForSale(saleId);

        // 06-BROKER-NETWORK-ENGINE.md §9/§41, build-order step 9, REVISED
        // post-ship: structured exactly like the create()/complete()
        // branches, never mixed with the PERCENTAGE/FIXED path above.
        // Cancelling the frozen commission tree (booking_commission ->
        // CANCELLED, recovery derived at read time) always applies, same
        // as before. What changed: reversing sales counts and
        // re-evaluating designation (which can DEMOTE) now ALWAYS fires
        // for a DESIGNATION broker's cancelled sale, unconditionally --
        // no more "only if it had reached COMPLETED" gate. Under the
        // revised §1/§4, a booking counts the instant it's created, so a
        // sale that was booked, counted, and cancelled before ever
        // reaching COMPLETED (or even before a single rupee was paid)
        // still needs its count reversed exactly as fully as one that ran
        // all the way to COMPLETED first -- this is now the load-bearing
        // path for count correctness, not an edge case.
        if (sale.getBrokerPartnerId() != null) {
            BrokerPartner broker = brokerPartnerRepository.findByIdAndOrgIdAndDeletedAtIsNull(sale.getBrokerPartnerId(), sale.getOrgId())
                    .orElse(null);
            if (broker != null && broker.getCommissionType() == BrokerPartner.CommissionType.DESIGNATION) {
                bookingCommissionService.cancelForSale(saleId);
                // V65_013's counts trigger already fired (AFTER UPDATE OF
                // status ON plot_sale, on this exact CANCELLED transition)
                // as part of the save() above -- flush here so
                // reevaluateAfterCancellation()'s own entityManager.refresh()
                // sees the already-decremented counts within this same
                // transaction, same "trigger runs first, Java reads after
                // an explicit flush" pattern complete() (and now create())
                // already established.
                entityManager.flush();
                designationPromotionService.reevaluateAfterCancellation(sale.getBrokerPartnerId());
            }
        }

        outboxService.enqueueNotification(sale.getOrgId(), "SALE_CANCELLED", "notification.saleCancelled", null,
                Map.of("plotNumber", plot.getPlotNumber()), "plot_sale", saleId);

        refresh(sale);
        return toResponse(sale);
    }

    // B-04 §7: once nothing is left owing, marks COMPLETED -- moves the
    // sale into Deals History as completed (B-10) and would bump the
    // broker's deals_closed_count (B-14, not built yet). The downstream
    // broker effect is explicitly out of scope until that module lands;
    // this only flips plot_sale.status itself.
    //
    // Gate on balanceDue, not totalPaid >= dealValue directly -- a real
    // bug found on a live account: this check predates M5's total_waived
    // fix (see that section above) and never accounted for it, so a sale
    // with a waived (not literally paid) final instalment had
    // totalPaid < dealValue forever, permanently blocking "Mark Complete"
    // even though balanceDue was already correctly zero. balanceDue is
    // the one figure every other surface (Financials, Tracker, Deals
    // History, this sale's own Payment Summary panel) already treats as
    // the authoritative "anything still owed" answer -- reusing it here
    // instead of re-deriving the same fact a second, now-stale way.
    @Transactional
    public SaleResponse complete(UUID saleId) {
        PlotSale sale = loadOwn(saleId);
        refresh(sale);
        if (sale.getBalanceDue().compareTo(BigDecimal.ZERO) > 0) {
            throw new BadRequestException("status", "SALE_NOT_FULLY_PAID", "error.sale.notFullyPaid");
        }
        sale.setStatus(PlotSale.Status.COMPLETED);
        repository.save(sale);

        // B-14 §7 -- must run AFTER the save() above, not before: the
        // V6_015 trigger that bumps broker_partner.deals_closed_count fires
        // on this exact "status -> COMPLETED" UPDATE, and evaluateAndUpgrade()
        // relies on entityManager.refresh() seeing that already-applied bump
        // within this same transaction (same "Hibernate can't see a
        // DB-trigger-computed change it didn't itself make" root cause
        // documented since M2/M4). No-op if this sale had no broker.
        //
        // 06-BROKER-NETWORK-ENGINE.md §1/§4/§6, REVISED post-ship: a
        // DESIGNATION broker's own counting/promotion evaluation NO LONGER
        // happens here -- it moved to create() (booking time), see that
        // method's own comment for the full reasoning. Completing a sale
        // now has ZERO effect on a DESIGNATION broker's sales counts or
        // designation; this branch is deliberately gone. PERCENTAGE/FIXED
        // brokers are completely unaffected by any of this -- still
        // calling the exact, unmodified M6 brokerTierService.evaluateAndUpgrade().
        if (sale.getBrokerPartnerId() != null) {
            entityManager.flush();
            BrokerPartner broker = brokerPartnerRepository.findByIdAndOrgIdAndDeletedAtIsNull(sale.getBrokerPartnerId(), sale.getOrgId())
                    .orElse(null);
            if (broker != null && broker.getCommissionType() != BrokerPartner.CommissionType.DESIGNATION) {
                brokerTierService.evaluateAndUpgrade(sale.getBrokerPartnerId());
            }
            notifyCommissionDue(sale);
        }

        refresh(sale);
        return toResponse(sale);
    }

    // M-06 §22.3 "Broker commission due" -- In-app + Email, on deal close.
    // No-op if the sale somehow has a brokerPartnerId but no ledger entry
    // (an external/not-in-system broker never gets one, per
    // CommissionLedgerService.createForSale()'s own no-op-when-null-brokerId
    // contract -- can't actually happen here since this whole block is
    // already gated on brokerPartnerId != null, but ifPresent() is the
    // correct defensive shape regardless).
    private void notifyCommissionDue(PlotSale sale) {
        commissionLedgerEntryRepository.findByPlotSaleIdAndDeletedAtIsNull(sale.getId()).ifPresent(entry -> {
            BigDecimal amount = entry.getBaseCommission().add(entry.getTierBonus()).setScale(0, RoundingMode.HALF_UP);
            String brokerName = brokerPartnerRepository.findByIdAndOrgIdAndDeletedAtIsNull(sale.getBrokerPartnerId(), sale.getOrgId())
                    .map(BrokerPartner::getFullName).orElse("");
            Plot plot = plotRepository.findByIdAndDeletedAtIsNull(sale.getPlotId()).orElse(null);
            outboxService.enqueueNotification(sale.getOrgId(), "COMMISSION_DUE", "notification.commissionDue", null,
                    Map.of("brokerName", brokerName, "amount", amount.toPlainString(),
                            "plotNumber", plot == null ? "" : plot.getPlotNumber()),
                    "commission_ledger_entry", entry.getId());
        });
    }

    // B-04 §7/§9: gov-ID reveal is a separate, permissioned (SENSITIVE_VIEW,
    // enforced by @RequiresPermission on the controller), audited call --
    // never bundled into the plain sale GET.
    public GovIdRevealResponse revealGovId(UUID saleId, String reason) {
        PlotSale sale = loadOwn(saleId);
        sensitiveAccessLogService.record("plot_sale", saleId, "buyer_gov_id_number", reason);

        String number = sale.getBuyerGovIdNumberEnc() != null ? govIdCipher.decrypt(sale.getBuyerGovIdNumberEnc()) : null;
        String mediaUrl = sale.getBuyerGovIdMediaId() != null ? mediaService.presignSensitiveGet(sale.getBuyerGovIdMediaId()) : null;
        return new GovIdRevealResponse(sale.getBuyerGovIdType() == null ? null : sale.getBuyerGovIdType().name(), number, mediaUrl);
    }

    private void applyGovId(PlotSale sale, PlotSale.GovIdType type, String number) {
        sale.setBuyerGovIdType(type);
        if (number == null || number.isBlank()) {
            return;
        }
        String normalized = NON_ALNUM.matcher(number).replaceAll("").toUpperCase();
        sale.setBuyerGovIdNumberEnc(govIdCipher.encrypt(normalized));
        sale.setBuyerGovIdLast4(normalized.length() >= 4 ? normalized.substring(normalized.length() - 4) : normalized);
    }

    private PlotSale loadOwn(UUID saleId) {
        PlotSale sale = repository.findByIdAndDeletedAtIsNull(saleId).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        accessGuard.assertAccess(sale.getProjectId());
        return sale;
    }

    private SaleResponse toResponse(PlotSale s) {
        // Denormalised for display, same "don't make the frontend do an N+1
        // lookup" convention CommissionLedgerEntryResponse/BrokerDealResponse
        // already established -- a plain lookup by id, not a tenant-scoped
        // query, since RLS on broker_partner is already the backstop here
        // (this sale itself was already loaded through an org-scoped query).
        String brokerName = s.getBrokerPartnerId() == null ? null
                : brokerPartnerRepository.findById(s.getBrokerPartnerId()).map(b -> b.getFullName()).orElse(null);
        return new SaleResponse(s.getId(), s.getPlotId(), s.getProjectId(), s.getCustomerId(),
                s.getBuyerName(), s.getBuyerMobile(), s.getBuyerEmail(),
                s.getBuyerGovIdType() == null ? null : s.getBuyerGovIdType().name(), s.getBuyerGovIdLast4(), s.getBuyerGovIdMediaId(),
                s.getPurchaseDate(), s.getDealValue(),
                s.getBrokerPartnerId(), brokerName, s.getExternalBrokerName(), s.getExternalBrokerMobile(), s.getBrokerCommissionAmount(),
                s.getPaymentType().name(), s.getStatus().name(), s.getCancelledAt(), s.getCancellationReason(), s.getHandledBy(),
                s.getTotalPaid(), s.getBalanceDue(),
                buyerWhatsAppOptInService.isOptedIn(s.getOrgId(), s.getBuyerMobile()));
    }
}
