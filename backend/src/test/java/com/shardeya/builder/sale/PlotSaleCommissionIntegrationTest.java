package com.shardeya.builder.sale;

import com.shardeya.builder.broker.BrokerCommissionConfig;
import com.shardeya.builder.broker.BrokerCommissionConfigRepository;
import com.shardeya.builder.broker.BrokerPartner;
import com.shardeya.builder.broker.BrokerPartnerRepository;
import com.shardeya.builder.broker.BrokerTier;
import com.shardeya.builder.broker.BrokerTierRepository;
import com.shardeya.builder.broker.CommissionLedgerEntry;
import com.shardeya.builder.broker.CommissionLedgerEntryRepository;
import com.shardeya.builder.broker.dto.CommissionConfigCreateRequest;
import com.shardeya.builder.broker.CommissionConfigService;
import com.shardeya.builder.payment.PaymentRecord;
import com.shardeya.builder.payment.PaymentService;
import com.shardeya.builder.payment.dto.PaymentCreateRequest;
import com.shardeya.builder.plot.Plot;
import com.shardeya.builder.plot.PlotRepository;
import com.shardeya.builder.project.Project;
import com.shardeya.builder.project.ProjectRepository;
import com.shardeya.builder.sale.dto.CancelSaleRequest;
import com.shardeya.builder.sale.dto.SaleCreateRequest;
import com.shardeya.builder.sale.dto.SaleResponse;
import com.shardeya.builder.sale.dto.ScheduleRowRequest;
import com.shardeya.foundation.auth.Organization;
import com.shardeya.foundation.auth.OrganizationRepository;
import com.shardeya.platform.TestTenantContext;
import com.shardeya.shared.IndianTime;
import com.shardeya.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * B-14 §7/§10 -- covers exactly the scenarios the M6 kickoff explicitly
 * called out: a per-plot commission override winning over a broker's global
 * rate, a broker auto-upgrading tiers on sale completion, a rate change
 * AFTER a sale leaving that sale's own commission untouched, and the
 * commission-ledger wiring not breaking the existing M3/M5 sale lifecycle
 * (create -> payment -> waive -> complete -> cancel). Brokers/tiers/configs
 * are seeded directly via their repositories (not BrokerPartnerService/
 * CommissionConfigService's own create() calls) for the same reason
 * PlotSaleIntegrationTest seeds org/project/plot directly -- a fresh test
 * org has no subscription row, which resolves to the FREE plan, whose
 * BUILDER_BROKERS quota is 0 (see BrokerPartnerService's own comment); going
 * through the quota-gated service here would only be testing the quota
 * gate, not the commission wiring this class exists to cover.
 */
class PlotSaleCommissionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PlotSaleService plotSaleService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private PlotRepository plotRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private BrokerPartnerRepository brokerRepository;
    @Autowired
    private BrokerCommissionConfigRepository configRepository;
    @Autowired
    private BrokerTierRepository tierRepository;
    @Autowired
    private CommissionLedgerEntryRepository ledgerRepository;
    @Autowired
    private CommissionConfigService commissionConfigService;
    @Autowired
    private com.shardeya.platform.OutboxEventRepository outboxEventRepository;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID orgId;
    private UUID projectId;

    @AfterEach
    void clearTenant() {
        TestTenantContext.clear();
    }

    @Test
    void saleWithAPlotLevelOverrideUsesThatRateNotTheBrokerSGlobalRate() {
        UUID plotId = seedOrgProjectAndPlot();
        UUID brokerId = seedBroker(BigDecimal.valueOf(2)); // 2% default (used only if no config resolves)

        commissionConfigService.create(brokerId, new CommissionConfigCreateRequest(
                BrokerCommissionConfig.Scope.GLOBAL, null, null, BrokerPartner.CommissionType.PERCENTAGE,
                BigDecimal.valueOf(3), LocalDate.now().minusDays(30), null));
        commissionConfigService.create(brokerId, new CommissionConfigCreateRequest(
                BrokerCommissionConfig.Scope.PLOT, null, plotId, BrokerPartner.CommissionType.PERCENTAGE,
                BigDecimal.valueOf(5), LocalDate.now().minusDays(30), null));

        SaleResponse sale = plotSaleService.create(plotId, saleRequestWithBroker(BigDecimal.valueOf(4_200_000), brokerId, null));

        CommissionLedgerEntry entry = ledgerRepository.findByPlotSaleIdAndDeletedAtIsNull(sale.id()).orElseThrow();
        // 5% (PLOT) of 4,200,000 = 210,000 -- NOT 3% (GLOBAL) = 126,000, NOT 2% (broker default) = 84,000.
        assertThat(entry.getBaseCommission()).isEqualByComparingTo(BigDecimal.valueOf(210_000));
        assertThat(entry.getStatus()).isEqualTo(CommissionLedgerEntry.Status.PENDING);
    }

    @Test
    void saleFallsBackToTheGlobalConfigWhenNoPlotOrProjectOverrideExists() {
        UUID plotId = seedOrgProjectAndPlot();
        UUID brokerId = seedBroker(BigDecimal.valueOf(2));

        commissionConfigService.create(brokerId, new CommissionConfigCreateRequest(
                BrokerCommissionConfig.Scope.GLOBAL, null, null, BrokerPartner.CommissionType.PERCENTAGE,
                BigDecimal.valueOf(3), LocalDate.now().minusDays(30), null));

        SaleResponse sale = plotSaleService.create(plotId, saleRequestWithBroker(BigDecimal.valueOf(4_200_000), brokerId, null));

        CommissionLedgerEntry entry = ledgerRepository.findByPlotSaleIdAndDeletedAtIsNull(sale.id()).orElseThrow();
        assertThat(entry.getBaseCommission()).isEqualByComparingTo(BigDecimal.valueOf(126_000)); // 3% of 4,200,000
    }

    @Test
    void changingTheBrokerSRateAfterASaleLeavesThatSaleSCommissionUnchanged() {
        UUID plotId1 = seedOrgProjectAndPlot();
        UUID plotId2 = seedSecondPlot();
        UUID brokerId = seedBroker(BigDecimal.valueOf(2));

        commissionConfigService.create(brokerId, new CommissionConfigCreateRequest(
                BrokerCommissionConfig.Scope.GLOBAL, null, null, BrokerPartner.CommissionType.PERCENTAGE,
                BigDecimal.valueOf(3), LocalDate.now().minusDays(30), null));

        SaleResponse firstSale = plotSaleService.create(plotId1, saleRequestWithBroker(BigDecimal.valueOf(4_200_000), brokerId, null));
        CommissionLedgerEntry firstEntry = ledgerRepository.findByPlotSaleIdAndDeletedAtIsNull(firstSale.id()).orElseThrow();
        assertThat(firstEntry.getBaseCommission()).isEqualByComparingTo(BigDecimal.valueOf(126_000)); // 3%

        // Builder edits the broker's global rate upward, well after the first sale closed.
        var globalConfig = configRepository.findByOrgIdAndBrokerPartnerIdAndDeletedAtIsNullOrderByEffectiveFromDesc(orgId, brokerId).get(0);
        commissionConfigService.update(globalConfig.getId(), new CommissionConfigCreateRequest(
                BrokerCommissionConfig.Scope.GLOBAL, null, null, BrokerPartner.CommissionType.PERCENTAGE,
                BigDecimal.valueOf(6), globalConfig.getEffectiveFrom(), null));

        // The first sale's ledger entry, re-read from the DB, must be untouched --
        // config_snapshot is a self-contained copy taken at sale time, never a live reference.
        CommissionLedgerEntry firstEntryReread = ledgerRepository.findByIdAndOrgIdAndDeletedAtIsNull(firstEntry.getId(), orgId).orElseThrow();
        assertThat(firstEntryReread.getBaseCommission()).isEqualByComparingTo(BigDecimal.valueOf(126_000));

        // A brand new sale, created after the rate change, correctly picks up the new rate.
        SaleResponse secondSale = plotSaleService.create(plotId2, saleRequestWithBroker(BigDecimal.valueOf(1_000_000), brokerId, null));
        CommissionLedgerEntry secondEntry = ledgerRepository.findByPlotSaleIdAndDeletedAtIsNull(secondSale.id()).orElseThrow();
        assertThat(secondEntry.getBaseCommission()).isEqualByComparingTo(BigDecimal.valueOf(60_000)); // 6% of 1,000,000
    }

    @Test
    void aBrokerCrossingATierThresholdAutoUpgradesWhenTheSaleThatCrossesItCompletes() {
        UUID plotId = seedOrgProjectAndPlot();
        UUID brokerId = seedBroker(BigDecimal.valueOf(2));

        BrokerTier bronze = seedTier("Bronze", 0, 0, (short) 0);
        BrokerTier silver = seedTier("Silver", 1, null, (short) 1);
        assignTier(brokerId, bronze.getId());

        commissionConfigService.create(brokerId, new CommissionConfigCreateRequest(
                BrokerCommissionConfig.Scope.GLOBAL, null, null, BrokerPartner.CommissionType.PERCENTAGE,
                BigDecimal.valueOf(2), LocalDate.now().minusDays(30), null));

        SaleResponse sale = plotSaleService.create(plotId, saleRequestLumpSum(BigDecimal.valueOf(1_000_000), brokerId));
        paymentService.record(sale.id(), new PaymentCreateRequest(BigDecimal.valueOf(1_000_000), IndianTime.today(),
                PaymentRecord.Mode.BANK_TRANSFER, "NEFT-1", null, null));

        BrokerPartner beforeComplete = brokerRepository.findById(brokerId).orElseThrow();
        assertThat(beforeComplete.getTierId()).isEqualTo(bronze.getId());

        plotSaleService.complete(sale.id());

        entityManager.clear(); // force a fresh read, not the same first-level-cached instance
        BrokerPartner afterComplete = brokerRepository.findById(brokerId).orElseThrow();
        assertThat(afterComplete.getDealsClosedCount()).isEqualTo(1);
        assertThat(afterComplete.getTierId()).isEqualTo(silver.getId()); // auto-upgraded, no manual action taken
    }

    // M-06 second half, §22.3 "Broker commission due" -- on deal close.
    @Test
    void completingASaleWithABrokerEnqueuesACommissionDueNotification() {
        UUID plotId = seedOrgProjectAndPlot();
        UUID brokerId = seedBroker(BigDecimal.valueOf(2));

        commissionConfigService.create(brokerId, new CommissionConfigCreateRequest(
                BrokerCommissionConfig.Scope.GLOBAL, null, null, BrokerPartner.CommissionType.PERCENTAGE,
                BigDecimal.valueOf(3), LocalDate.now().minusDays(30), null));

        SaleResponse sale = plotSaleService.create(plotId, saleRequestLumpSum(BigDecimal.valueOf(1_000_000), brokerId));
        paymentService.record(sale.id(), new PaymentCreateRequest(BigDecimal.valueOf(1_000_000), IndianTime.today(),
                PaymentRecord.Mode.BANK_TRANSFER, "NEFT-CD-1", null, null));

        plotSaleService.complete(sale.id());

        // 3% of 1,000,000 = 30,000.
        assertThat(outboxEventRepository.findAll()).anyMatch(e -> e.getEventType().equals("NOTIFICATION")
                && e.getPayload().contains("COMMISSION_DUE") && e.getPayload().contains("30000"));
    }

    @Test
    void aManualCommissionAmountIsUsedVerbatimWhenSuppliedAlongsideAResolvableDefault() {
        // B-14 §10's "no config, no default -> REQUIRE a manual amount"
        // branch turns out to be unreachable through the real creation
        // path: ck_broker_partner_commission_pct/ck_broker_partner_commission_fixed
        // (V6_003) are DB CHECK constraints, not just BrokerPartnerService's
        // own Bean Validation -- a broker row can never exist with a
        // PERCENTAGE type and a null commissionPct (attempted directly via
        // BrokerPartnerRepository.saveAndFlush() here, bypassing every
        // application-layer check, and it STILL 400s at the DB itself).
        // So CommissionConfigService.resolve() can, in this schema, never
        // actually return empty -- the broker's own default is always a
        // valid fallback. CommissionLedgerService.createForSale()'s
        // "require manual amount" throw is kept as defensive code (the
        // Optional<CommissionResolution> return type still models the
        // theoretical case honestly) but is not exercised by any reachable
        // state today; documented in CLAUDE.md rather than deleted.
        UUID plotId = seedOrgProjectAndPlot();
        UUID brokerId = UUID.randomUUID();
        assertThatThrownBy(() -> {
            BrokerPartner broker = new BrokerPartner(brokerId, orgId, "No-Config Broker", "9123456780", BrokerPartner.CommissionType.PERCENTAGE);
            brokerRepository.saveAndFlush(broker);
        }).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        entityManager.clear();

        // A broker with a real default (2%, no configs at any level) still
        // resolves fine on its own -- confirming the fallback-to-default
        // half of §10 works, independent of the unreachable "require
        // manual" half tested above.
        UUID brokerWithDefaultId = seedBroker(BigDecimal.valueOf(2));
        SaleResponse sale = plotSaleService.create(plotId, saleRequestWithBroker(BigDecimal.valueOf(4_200_000), brokerWithDefaultId, null));
        CommissionLedgerEntry entry = ledgerRepository.findByPlotSaleIdAndDeletedAtIsNull(sale.id()).orElseThrow();
        assertThat(entry.getBaseCommission()).isEqualByComparingTo(BigDecimal.valueOf(84_000)); // 2% of 4,200,000, broker's own default
    }

    @Test
    void cancellingASaleWithABrokerCancelsItsCommissionLedgerEntryToo() {
        UUID plotId = seedOrgProjectAndPlot();
        UUID brokerId = seedBroker(BigDecimal.valueOf(2));
        commissionConfigService.create(brokerId, new CommissionConfigCreateRequest(
                BrokerCommissionConfig.Scope.GLOBAL, null, null, BrokerPartner.CommissionType.PERCENTAGE,
                BigDecimal.valueOf(2), LocalDate.now().minusDays(30), null));

        SaleResponse sale = plotSaleService.create(plotId, saleRequestWithBroker(BigDecimal.valueOf(4_200_000), brokerId, null));
        assertThat(ledgerRepository.findByPlotSaleIdAndDeletedAtIsNull(sale.id()).orElseThrow().getStatus())
                .isEqualTo(CommissionLedgerEntry.Status.PENDING);

        plotSaleService.cancel(sale.id(), new CancelSaleRequest("Buyer backed out", null));

        CommissionLedgerEntry entry = ledgerRepository.findByPlotSaleIdAndDeletedAtIsNull(sale.id()).orElseThrow();
        assertThat(entry.getStatus()).isEqualTo(CommissionLedgerEntry.Status.CANCELLED);
    }

    @Test
    void aSaleWithNoBrokerAtAllCreatesNoLedgerEntryAndTheFullM3M5LifecycleStillWorks() {
        // The actual regression the kickoff instruction asked for: with
        // commission-ledger creation now wired into create()/complete()/
        // cancel(), the ordinary no-broker path (still the overwhelming
        // majority of sales) must behave exactly as M3/M5 left it.
        UUID plotId = seedOrgProjectAndPlot();
        SaleResponse sale = plotSaleService.create(plotId, saleRequestNoBroker(BigDecimal.valueOf(4_200_000)));
        assertThat(ledgerRepository.findByPlotSaleIdAndDeletedAtIsNull(sale.id())).isEmpty();

        paymentService.record(sale.id(), new PaymentCreateRequest(BigDecimal.valueOf(4_200_000), IndianTime.today(),
                PaymentRecord.Mode.BANK_TRANSFER, "NEFT-FULL", null, null));
        var summary = paymentService.summary(sale.id());
        assertThat(summary.balanceDue()).isEqualByComparingTo(BigDecimal.ZERO);

        SaleResponse completed = plotSaleService.complete(sale.id());
        assertThat(completed.status()).isEqualTo("COMPLETED");

        // Cancelling a COMPLETED sale isn't a supported transition in this
        // app (B-04 only documents cancelling an ACTIVE sale) -- so the
        // lifecycle regression instead confirms a *second*, separate sale's
        // cancel path still works exactly as M3 left it.
        UUID plotId2 = seedSecondPlot();
        SaleResponse sale2 = plotSaleService.create(plotId2, saleRequestNoBroker(BigDecimal.valueOf(1_000_000)));
        plotSaleService.cancel(sale2.id(), new CancelSaleRequest("Buyer backed out", null));
        assertThat(plotRepository.findByIdAndDeletedAtIsNull(plotId2).orElseThrow().getStatus()).isEqualTo(Plot.Status.AVAILABLE);
        assertThat(ledgerRepository.findByPlotSaleIdAndDeletedAtIsNull(sale2.id())).isEmpty(); // still nothing, no broker was ever involved
    }

    private SaleCreateRequest saleRequestWithBroker(BigDecimal dealValue, UUID brokerId, BigDecimal manualCommissionAmount) {
        LocalDate today = IndianTime.today();
        return new SaleCreateRequest(null, "Rajesh Kumar", "9876543210", null, null, null, null,
                today, dealValue, brokerId, null, null, manualCommissionAmount,
                PlotSale.PaymentType.INSTALMENT,
                List.of(new ScheduleRowRequest("Booking", BigDecimal.valueOf(500_000).min(dealValue), today),
                        new ScheduleRowRequest("Balance", dealValue.subtract(BigDecimal.valueOf(500_000).min(dealValue)), today.plusMonths(2))),
                null);
    }

    private SaleCreateRequest saleRequestLumpSum(BigDecimal dealValue, UUID brokerId) {
        LocalDate today = IndianTime.today();
        return new SaleCreateRequest(null, "Rajesh Kumar", "9876543210", null, null, null, null,
                today, dealValue, brokerId, null, null, null,
                PlotSale.PaymentType.LUMP_SUM,
                List.of(new ScheduleRowRequest("Full payment", dealValue, today)),
                null);
    }

    private SaleCreateRequest saleRequestNoBroker(BigDecimal dealValue) {
        LocalDate today = IndianTime.today();
        return new SaleCreateRequest(null, "Anita Sharma", "9876543211", null, null, null, null,
                today, dealValue, null, null, null, null,
                PlotSale.PaymentType.LUMP_SUM,
                List.of(new ScheduleRowRequest("Full payment", dealValue, today)),
                null);
    }

    private UUID seedBroker(BigDecimal defaultPct) {
        UUID brokerId = UUID.randomUUID();
        BrokerPartner broker = new BrokerPartner(brokerId, orgId, "Suresh Broker", "9988776655", BrokerPartner.CommissionType.PERCENTAGE);
        broker.setCommissionPct(defaultPct);
        brokerRepository.saveAndFlush(broker);
        return brokerId;
    }

    private BrokerTier seedTier(String name, int minDeals, Integer maxDeals, short sortOrder) {
        BrokerTier tier = new BrokerTier(UUID.randomUUID(), orgId, name, name, minDeals, maxDeals, sortOrder);
        tierRepository.saveAndFlush(tier);
        return tier;
    }

    private void assignTier(UUID brokerId, UUID tierId) {
        BrokerPartner broker = brokerRepository.findById(brokerId).orElseThrow();
        broker.setTierId(tierId);
        brokerRepository.saveAndFlush(broker);
    }

    private UUID seedOrgProjectAndPlot() {
        orgId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        TestTenantContext.bind(orgId, userId, "BUILDER", "BUILDER_ADMIN", Set.of());

        Organization org = new Organization(orgId, Organization.Type.BUILDER, "Commission Test Org", "Jaipur");
        organizationRepository.saveAndFlush(org);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UUID roleId = (UUID) entityManager.createNativeQuery(
                            "SELECT id FROM role WHERE code = 'BUILDER_ADMIN' AND org_id IS NULL")
                    .getSingleResult();
            entityManager.createNativeQuery(
                            "INSERT INTO app_user (id, org_id, full_name, mobile, role_id, is_owner) "
                                    + "VALUES (:id, :orgId, 'Commission Test User', :mobile, :roleId, true)")
                    .setParameter("id", userId)
                    .setParameter("orgId", orgId)
                    .setParameter("mobile", "9" + String.valueOf(System.nanoTime()).substring(0, 9))
                    .setParameter("roleId", roleId)
                    .executeUpdate();
        });

        Project project = new Project(projectId, orgId, "Commission Test Project", Project.Type.RESIDENTIAL_PLOT_COLONY,
                "1 Test Rd", "Locality", "Jaipur", "RJ", BigDecimal.TEN, "ACRE", BigDecimal.valueOf(435600), 100);
        projectRepository.saveAndFlush(project);

        Plot plot = new Plot(plotId, orgId, projectId, "A-1", BigDecimal.valueOf(1200), "SQ_FT",
                BigDecimal.valueOf(1200), BigDecimal.valueOf(4_200_000));
        plotRepository.saveAndFlush(plot);

        return plotId;
    }

    private UUID seedSecondPlot() {
        UUID plotId = UUID.randomUUID();
        Plot plot = new Plot(plotId, orgId, projectId, "A-2", BigDecimal.valueOf(1200), "SQ_FT",
                BigDecimal.valueOf(1200), BigDecimal.valueOf(1_000_000));
        plotRepository.saveAndFlush(plot);
        return plotId;
    }
}
