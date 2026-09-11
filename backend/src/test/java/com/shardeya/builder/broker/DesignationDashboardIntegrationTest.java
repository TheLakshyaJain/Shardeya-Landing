package com.shardeya.builder.broker;

import com.shardeya.builder.broker.dto.BrokerCommissionSummaryResponse;
import com.shardeya.builder.broker.dto.DesignationHistoryResponse;
import com.shardeya.builder.broker.dto.NetworkCommissionSummaryResponse;
import com.shardeya.builder.payment.PaymentRecord;
import com.shardeya.builder.payment.PaymentService;
import com.shardeya.builder.payment.dto.PaymentCreateRequest;
import com.shardeya.builder.plot.Plot;
import com.shardeya.builder.plot.PlotRepository;
import com.shardeya.builder.project.Project;
import com.shardeya.builder.project.ProjectRepository;
import com.shardeya.builder.sale.PlotSale;
import com.shardeya.builder.sale.PlotSaleService;
import com.shardeya.builder.sale.dto.CancelSaleRequest;
import com.shardeya.builder.sale.dto.SaleCreateRequest;
import com.shardeya.builder.sale.dto.SaleResponse;
import com.shardeya.builder.sale.dto.ScheduleRowRequest;
import com.shardeya.foundation.auth.Organization;
import com.shardeya.foundation.auth.OrganizationRepository;
import com.shardeya.platform.TestTenantContext;
import com.shardeya.shared.IndianTime;
import com.shardeya.support.AbstractIntegrationTest;
import com.shardeya.support.TestMobiles;
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

/**
 * 06-BROKER-NETWORK-ENGINE.md §26/§27/§29, build-order step 10 -- the
 * dashboard aggregate endpoints (commission-summary, network-wide
 * commission-summary, designation-history). Verifies the aggregates
 * against a real multi-level chain with genuinely mixed
 * released/pending/cancelled commission, per this round's own explicit
 * verification instruction, not just single-row happy-path math.
 */
class DesignationDashboardIntegrationTest extends AbstractIntegrationTest {

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
    private DesignationSlabService designationSlabService;
    @Autowired
    private BrokerNetworkService brokerNetworkService;
    @Autowired
    private DesignationPromotionService designationPromotionService;
    @Autowired
    private BookingCommissionService bookingCommissionService;
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

    // T (160) -> A (180) -> B (200, sells). §45-shaped differential chain:
    // B keeps 200*1000=200,000; A gets (200-180)*1000=20,000 differential;
    // T gets (200-160)*1000=40,000 differential. Then B is paid HALF
    // (100,000 of 200,000 deal value) so every beneficiary sits at exactly
    // 50% released/50% pending -- a genuinely mixed state, not all-or-nothing.
    @Test
    void personalVsTeamCommissionSplitsCorrectlyAndReleasedPendingReflectAPartialPayment() {
        seedOrgAndProject();
        // Rates must INCREASE going up the hierarchy for a differential to
        // be nonzero (§7: an upline rated LOWER than its direct downline
        // gets an explicit zero-amount row instead -- see
        // CommissionCalculationEngine's own javadoc). T is the most
        // senior/highest-rated, at the top with no upline; B, the most
        // junior, is the one who actually sells.
        UUID tId = seedDesignationBroker("T", 200, null);
        UUID aId = seedDesignationBroker("A", 180, tId);
        UUID bId = seedDesignationBroker("B", 160, aId);

        UUID plotId = seedPlot("A-1"); // fixed 1000 sqft, see seedPlot()
        SaleResponse sale = plotSaleService.create(plotId, saleRequest(BigDecimal.valueOf(200_000), bId));
        // B: 160*1000=160,000 (personal). A: (180-160)*1000=20,000 differential. T: (200-180)*1000=20,000 differential.
        payPartial(sale.id(), BigDecimal.valueOf(100_000)); // exactly 50% of the 200,000 deal value

        entityManager.clear();
        BrokerCommissionSummaryResponse bSummary = bookingCommissionService.commissionSummary(bId);
        assertThat(bSummary.personalCommissionEarned()).isEqualByComparingTo(BigDecimal.valueOf(160_000));
        assertThat(bSummary.teamCommissionEarned()).isEqualByComparingTo(BigDecimal.valueOf(160_000)); // B has no downline
        assertThat(bSummary.sellingBrokerEarned()).isEqualByComparingTo(BigDecimal.valueOf(160_000));
        assertThat(bSummary.uplineDifferentialEarned()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(bSummary.commissionReleased()).isEqualByComparingTo(BigDecimal.valueOf(80_000)); // 50% of 160,000
        assertThat(bSummary.commissionPending()).isEqualByComparingTo(BigDecimal.valueOf(80_000));

        BrokerCommissionSummaryResponse aSummary = bookingCommissionService.commissionSummary(aId);
        assertThat(aSummary.personalCommissionEarned()).isEqualByComparingTo(BigDecimal.ZERO); // A never sold anything themselves
        assertThat(aSummary.teamCommissionEarned()).isEqualByComparingTo(BigDecimal.valueOf(20_000));
        assertThat(aSummary.uplineDifferentialEarned()).isEqualByComparingTo(BigDecimal.valueOf(20_000));
        assertThat(aSummary.commissionReleased()).isEqualByComparingTo(BigDecimal.valueOf(10_000));
        assertThat(aSummary.commissionPending()).isEqualByComparingTo(BigDecimal.valueOf(10_000));

        BrokerCommissionSummaryResponse tSummary = bookingCommissionService.commissionSummary(tId);
        assertThat(tSummary.personalCommissionEarned()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(tSummary.teamCommissionEarned()).isEqualByComparingTo(BigDecimal.valueOf(20_000));
        assertThat(tSummary.commissionReleased()).isEqualByComparingTo(BigDecimal.valueOf(10_000));
        assertThat(tSummary.commissionPending()).isEqualByComparingTo(BigDecimal.valueOf(10_000));

        // Paying the rest brings every beneficiary to fully released, zero pending.
        payPartial(sale.id(), BigDecimal.valueOf(100_000));
        entityManager.clear();
        assertThat(bookingCommissionService.commissionSummary(bId).commissionPending()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(bookingCommissionService.commissionSummary(aId).commissionPending()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(bookingCommissionService.commissionSummary(tId).commissionPending()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // Same-slab bonus shape (§46-like): two brokers at the identical rate,
    // plus a SECOND, independent booking from a different top-level seller,
    // to prove the org-wide summary genuinely sums ACROSS bookings and
    // brokers rather than only ever reflecting the single most-recent one.
    //
    // §7's revised same-slab formula: A and B share the ₹160 (Business
    // Executive) slab, whose next slab up is ₹180 (Senior Business
    // Executive) -- incentive = (180 - 160) = ₹20/sq.ft., NOT the old flat
    // ₹10. Re-derived by hand against the real lookup table, not adjusted
    // until green.
    @Test
    void orgWideSummaryAggregatesAcrossMultipleBookingsAndDistinctBrokersCorrectly() {
        seedOrgAndProject();
        UUID aId = seedDesignationBroker("A", 160, null);
        UUID bId = seedDesignationBroker("B", 160, aId); // same slab as A -> NETWORK_SAME_SLAB_BONUS for A
        UUID cId = seedDesignationBroker("C", 160, null); // a second, independent top-level seller

        UUID plot1 = seedPlot("A-1");
        plotSaleService.create(plot1, saleRequest(BigDecimal.valueOf(160_000), bId)); // B sells: B=160,000, A bonus=(180-160)*1000=20,000
        UUID plot2 = seedPlot("A-2");
        plotSaleService.create(plot2, saleRequest(BigDecimal.valueOf(160_000), cId)); // C sells solo: C=160,000

        entityManager.clear();
        NetworkCommissionSummaryResponse orgSummary = bookingCommissionService.networkCommissionSummary();
        assertThat(orgSummary.totalDesignationBrokers()).isEqualTo(3); // A, B, C all appear as beneficiaries
        assertThat(orgSummary.totalCommissionEarned()).isEqualByComparingTo(BigDecimal.valueOf(340_000)); // 160k + 20k + 160k
        assertThat(orgSummary.sellingBrokerEarned()).isEqualByComparingTo(BigDecimal.valueOf(320_000)); // B's 160k + C's 160k
        assertThat(orgSummary.sameSlabBonusEarned()).isEqualByComparingTo(BigDecimal.valueOf(20_000)); // A's bonus, (180-160) per sq.ft, not the old flat 10,000
        assertThat(orgSummary.uplineDifferentialEarned()).isEqualByComparingTo(BigDecimal.ZERO);
        // Nothing paid yet -- everything pending, nothing released.
        assertThat(orgSummary.totalCommissionReleased()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(orgSummary.totalCommissionPending()).isEqualByComparingTo(BigDecimal.valueOf(340_000));
    }

    // §9's cancellation-recovery interaction with the step-10 aggregates:
    // a cancelled booking's rows are frozen, never deleted, but must be
    // excluded from every earned/released/pending figure going forward --
    // otherwise a builder's dashboard would overstate real outstanding
    // commission for a deal that's dead.
    @Test
    void cancelledBookingsAreExcludedFromEveryAggregateFigureButAnUnrelatedBookingIsUnaffected() {
        seedOrgAndProject();
        UUID aId = seedDesignationBroker("A", 160, null);

        UUID cancelledPlot = seedPlot("A-1");
        SaleResponse cancelledSale = plotSaleService.create(cancelledPlot, saleRequest(BigDecimal.valueOf(160_000), aId));
        payPartial(cancelledSale.id(), BigDecimal.valueOf(80_000)); // half released before cancellation
        plotSaleService.cancel(cancelledSale.id(), new CancelSaleRequest("Buyer backed out", null));

        UUID keptPlot = seedPlot("A-2");
        plotSaleService.create(keptPlot, saleRequest(BigDecimal.valueOf(160_000), aId));

        entityManager.clear();
        BrokerCommissionSummaryResponse aSummary = bookingCommissionService.commissionSummary(aId);
        // Only the KEPT booking's 160,000 counts -- the cancelled one's
        // 160,000 (and its 80,000 already-released amount, a real recovery
        // situation) is excluded entirely from "earned"/"released"/"pending".
        assertThat(aSummary.personalCommissionEarned()).isEqualByComparingTo(BigDecimal.valueOf(160_000));
        assertThat(aSummary.commissionReleased()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(aSummary.commissionPending()).isEqualByComparingTo(BigDecimal.valueOf(160_000));

        NetworkCommissionSummaryResponse orgSummary = bookingCommissionService.networkCommissionSummary();
        assertThat(orgSummary.totalCommissionEarned()).isEqualByComparingTo(BigDecimal.valueOf(160_000));
    }

    // §26/§27/§29's "promotion history" -- both the per-broker and the
    // org-wide views, newest first, with every name resolved (designation
    // names, broker name, changed-by name) rather than raw ids.
    @Test
    void designationHistoryReturnsResolvedNamesNewestFirstBothPerBrokerAndOrgWide() {
        seedOrgAndProject();
        UUID aId = seedDesignationBroker("A", 160, null);
        DesignationSlab businessManager = businessManagerSlab();

        designationPromotionService.manuallyOverride(aId, businessManager.getId(), "Recognising a large offline deal pipeline");

        entityManager.clear();
        List<DesignationHistoryResponse> perBroker = designationPromotionService.history(aId);
        assertThat(perBroker).hasSize(1);
        DesignationHistoryResponse row = perBroker.get(0);
        assertThat(row.brokerName()).isEqualTo("A");
        assertThat(row.previousDesignationName()).isEqualTo("Business Executive");
        assertThat(row.newDesignationName()).isEqualTo("Business Manager");
        assertThat(row.newRate()).isEqualByComparingTo(BigDecimal.valueOf(215));
        assertThat(row.changeType()).isEqualTo("MANUAL");
        assertThat(row.changedByName()).isNotBlank();

        List<DesignationHistoryResponse> orgWide = designationPromotionService.networkHistory();
        assertThat(orgWide).hasSize(1);
        assertThat(orgWide.get(0).brokerName()).isEqualTo("A");
    }

    private DesignationSlab businessManagerSlab() {
        // 3rd slab up from Business Executive (index 0) -- matches every
        // other step 7/8/9 test class's own "find by name" helper, inlined
        // here rather than re-added as a field since this class only needs
        // it once.
        return designationSlabService.resolve(orgId, 3);
    }

    private void payPartial(UUID saleId, BigDecimal amount) {
        paymentService.record(saleId, new PaymentCreateRequest(amount, IndianTime.today(),
                PaymentRecord.Mode.BANK_TRANSFER, "NEFT-" + UUID.randomUUID(), null, null));
    }

    private SaleCreateRequest saleRequest(BigDecimal dealValue, UUID brokerId) {
        LocalDate today = IndianTime.today();
        return new SaleCreateRequest(null, "Rajesh Kumar", "9876543210", null, null, null, null,
                today, dealValue, brokerId, null, null, null, PlotSale.PaymentType.INSTALMENT,
                List.of(new ScheduleRowRequest("Booking", dealValue, today)), null);
    }

    private UUID seedDesignationBroker(String name, int ratePerSqft, UUID uplineBrokerId) {
        UUID brokerId = UUID.randomUUID();
        BrokerPartner broker = new BrokerPartner(brokerId, orgId, name, TestMobiles.next(), BrokerPartner.CommissionType.DESIGNATION);
        broker.setUplineBrokerId(uplineBrokerId);
        DesignationSlab slab = designationSlabService.resolve(orgId, ratePerSqft == 160 ? 0 : ratePerSqft == 180 ? 1 : 2);
        broker.setCurrentDesignationId(slab.getId());
        broker.setCurrentCommissionRate(slab.getRatePerSqft());
        brokerRepository.saveAndFlush(broker);
        // Even a top-level broker needs its own depth-0 self-row in the
        // closure table (see DesignationManualOverrideIntegrationTest's
        // own comment on this exact point).
        brokerNetworkService.attachNewBroker(orgId, brokerId, uplineBrokerId);
        return brokerId;
    }

    private void seedOrgAndProject() {
        orgId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        TestTenantContext.bind(orgId, userId, "BUILDER", "BUILDER_ADMIN", Set.of());

        Organization org = new Organization(orgId, Organization.Type.BUILDER, "Dashboard Test Org", "Jaipur");
        organizationRepository.saveAndFlush(org);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UUID roleId = (UUID) entityManager.createNativeQuery(
                            "SELECT id FROM role WHERE code = 'BUILDER_ADMIN' AND org_id IS NULL")
                    .getSingleResult();
            entityManager.createNativeQuery(
                            "INSERT INTO app_user (id, org_id, full_name, mobile, role_id, is_owner) "
                                    + "VALUES (:id, :orgId, 'Dashboard Test User', :mobile, :roleId, true)")
                    .setParameter("id", userId)
                    .setParameter("orgId", orgId)
                    .setParameter("mobile", TestMobiles.next())
                    .setParameter("roleId", roleId)
                    .executeUpdate();
        });

        Project project = new Project(projectId, orgId, "Dashboard Test Project", Project.Type.RESIDENTIAL_PLOT_COLONY,
                "1 Test Rd", "Locality", "Jaipur", "RJ", BigDecimal.TEN, "ACRE", BigDecimal.valueOf(435600), 100);
        projectRepository.saveAndFlush(project);
    }

    private UUID seedPlot(String plotNumber) {
        UUID plotId = UUID.randomUUID();
        Plot plot = new Plot(plotId, orgId, projectId, plotNumber, BigDecimal.valueOf(1000), "SQ_FT",
                BigDecimal.valueOf(1000), BigDecimal.valueOf(4_200_000));
        plotRepository.saveAndFlush(plot);
        return plotId;
    }
}
