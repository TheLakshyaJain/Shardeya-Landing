package com.shardeya.builder.broker;

import com.shardeya.builder.broker.dto.DesignationOverrideRequest;
import com.shardeya.builder.payment.PaymentRecord;
import com.shardeya.builder.payment.PaymentService;
import com.shardeya.builder.payment.dto.PaymentCreateRequest;
import com.shardeya.builder.plot.Plot;
import com.shardeya.builder.plot.PlotRepository;
import com.shardeya.builder.project.Project;
import com.shardeya.builder.project.ProjectRepository;
import com.shardeya.builder.sale.PlotSale;
import com.shardeya.builder.sale.PlotSaleService;
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
 * 06-BROKER-NETWORK-ENGINE.md §34, build-order step 8 -- manual promotion.
 * Deliberately built and verified AFTER steps 7/9 (the automatic
 * promotion/cancellation-reversal machinery this reuses), since §34's own
 * rules ("changes designation + rate going forward only", "does NOT
 * change sales counts", "freezes auto-evaluation") are all expressed as
 * interactions with that already-hardened machinery, not new mechanism.
 */
class DesignationManualOverrideIntegrationTest extends AbstractIntegrationTest {

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
    private DesignationSlabRepository designationSlabRepository;
    @Autowired
    private BrokerNetworkService brokerNetworkService;
    @Autowired
    private DesignationPromotionService designationPromotionService;
    @Autowired
    private BookingCommissionRepository bookingCommissionRepository;
    @Autowired
    private DesignationHistoryRepository historyRepository;
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
    void manualOverrideSetsDesignationAndFreezesAutomaticEvaluationAgainstACompletion() {
        seedOrgAndProject();
        UUID bId = seedDesignationBroker("B", 160, null);
        DesignationSlab businessManager = findSlabNamed("Business Manager"); // min_team_sales 3-5, rate 215

        designationPromotionService.manuallyOverride(bId, businessManager.getId(), "Recognising B's offline referral network");

        entityManager.clear();
        BrokerPartner b = brokerRepository.findById(bId).orElseThrow();
        assertThat(b.isDesignationManuallyOverridden()).isTrue();
        assertThat(b.getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(215));
        assertThat(b.getCurrentDesignationId()).isEqualTo(businessManager.getId());

        List<DesignationHistory> historyAfterOverride = historyRepository.findByOrgIdAndBrokerIdOrderByEffectiveAtDesc(orgId, bId);
        assertThat(historyAfterOverride).hasSize(1);
        assertThat(historyAfterOverride.get(0).getChangeType()).isEqualTo(DesignationHistory.ChangeType.MANUAL);
        assertThat(historyAfterOverride.get(0).getPreviousRate()).isEqualByComparingTo(BigDecimal.valueOf(160));
        assertThat(historyAfterOverride.get(0).getNewRate()).isEqualByComparingTo(BigDecimal.valueOf(215));
        assertThat(historyAfterOverride.get(0).getReason()).isEqualTo("Recognising B's offline referral network");

        // REVISED post-ship: counting/promotion now fires at create()
        // (BOOKED), not complete() -- this booking would normally (team=1)
        // auto-resolve B to Senior Business Executive/180 -- LOWER than
        // the manually-set 215 -- the INSTANT it's created. Being
        // overridden must freeze B against this entirely, not just
        // against upward moves.
        UUID plotId = seedPlot("A-1");
        SaleResponse sale = plotSaleService.create(plotId, saleRequest(BigDecimal.valueOf(400_000), bId));

        entityManager.clear();
        BrokerPartner bRightAfterBooking = brokerRepository.findById(bId).orElseThrow();
        assertThat(bRightAfterBooking.getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(215)); // untouched by the booking's own attempted evaluation
        assertThat(bRightAfterBooking.isDesignationManuallyOverridden()).isTrue(); // still frozen
        assertThat(bRightAfterBooking.getTeamSuccessfulBookings()).isEqualTo(1); // counts themselves are NOT frozen, only designation/rate
        // No new history row from the booking's own (guarded-off) evaluation -- still exactly the one MANUAL row.
        assertThat(historyRepository.findByOrgIdAndBrokerIdOrderByEffectiveAtDesc(orgId, bId)).hasSize(1);

        // Paying it off and completing it afterward changes nothing further --
        // completion has zero bearing on counts/designation under the revised rule.
        payInFull(sale.id(), BigDecimal.valueOf(400_000));
        plotSaleService.complete(sale.id());

        entityManager.clear();
        BrokerPartner bAfterCompletion = brokerRepository.findById(bId).orElseThrow();
        assertThat(bAfterCompletion.getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(215));
        assertThat(bAfterCompletion.isDesignationManuallyOverridden()).isTrue();
        assertThat(historyRepository.findByOrgIdAndBrokerIdOrderByEffectiveAtDesc(orgId, bId)).hasSize(1);
    }

    @Test
    void clearingTheOverrideResumesAutomaticEvaluationAndCanEvenDemoteFromTheOverriddenValue() {
        seedOrgAndProject();
        UUID bId = seedDesignationBroker("B", 160, null);
        DesignationSlab salesDirector = findSlabNamed("Sales Director"); // min_team_sales 10-14, rate 235

        designationPromotionService.manuallyOverride(bId, salesDirector.getId(), "Founding broker courtesy designation");

        // REVISED post-ship: team becomes 1 immediately at create() (BOOKED),
        // not at complete() -- designation stays frozen at 235 (Sales
        // Director) throughout; payInFull()/complete() below are just
        // bringing the sale to COMPLETED status, with zero further effect.
        UUID plotId = seedPlot("A-1");
        SaleResponse sale = plotSaleService.create(plotId, saleRequest(BigDecimal.valueOf(400_000), bId));
        entityManager.clear();
        assertThat(brokerRepository.findById(bId).orElseThrow().getTeamSuccessfulBookings()).isEqualTo(1);
        assertThat(brokerRepository.findById(bId).orElseThrow().getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(235));

        payInFull(sale.id(), BigDecimal.valueOf(400_000));
        plotSaleService.complete(sale.id());

        entityManager.clear();
        assertThat(brokerRepository.findById(bId).orElseThrow().getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(235));

        designationPromotionService.clearOverride(bId);

        entityManager.clear();
        BrokerPartner b = brokerRepository.findById(bId).orElseThrow();
        assertThat(b.isDesignationManuallyOverridden()).isFalse();
        // Real team count is 1 -> Senior Business Executive/180 -- a genuine
        // DEMOTION from the overridden 235, immediately upon clearing.
        assertThat(b.getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(180));

        List<DesignationHistory> history = historyRepository.findByOrgIdAndBrokerIdOrderByEffectiveAtDesc(orgId, bId);
        assertThat(history).hasSize(2); // MANUAL (160->235), then AUTOMATIC (235->180) from clearing
        assertThat(history.get(0).getChangeType()).isEqualTo(DesignationHistory.ChangeType.AUTOMATIC);
        assertThat(history.get(0).getPreviousRate()).isEqualByComparingTo(BigDecimal.valueOf(235));
        assertThat(history.get(0).getNewRate()).isEqualByComparingTo(BigDecimal.valueOf(180));

        // Automatic evaluation is genuinely resumed -- a SECOND booking now
        // promotes B normally again, no override in the way. Team becomes
        // 2 -> Business Development Officer/200 immediately at create().
        UUID plotId2 = seedPlot("A-2");
        plotSaleService.create(plotId2, saleRequest(BigDecimal.valueOf(400_000), bId));

        entityManager.clear();
        assertThat(brokerRepository.findById(bId).orElseThrow().getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(200));
    }

    // §34: "does NOT alter any historical/frozen booking commission" -- a
    // booking frozen BEFORE the override keeps its old rate; one created
    // AFTER uses the new one. Same timing-rule construction already proved
    // for step 7 promotion and step 9 demotion, now proved for step 8 too.
    @Test
    void manualOverrideNeverRewritesAnyExistingFrozenBookingCommission() {
        seedOrgAndProject();
        UUID bId = seedDesignationBroker("B", 160, null);

        UUID plotBefore = seedPlot("A-1");
        SaleResponse saleBefore = plotSaleService.create(plotBefore, saleRequest(BigDecimal.valueOf(400_000), bId));
        BookingCommission rowBefore = onlyRowFor(saleBefore.id(), bId);
        assertThat(rowBefore.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(160_000));

        DesignationSlab businessManager = findSlabNamed("Business Manager");
        designationPromotionService.manuallyOverride(bId, businessManager.getId(), "Manual bump ahead of a big launch");

        entityManager.clear();
        // The BEFORE booking's frozen tree must be byte-for-byte unchanged.
        BookingCommission rowBeforeAfterOverride = onlyRowFor(saleBefore.id(), bId);
        assertThat(rowBeforeAfterOverride.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(160_000));
        assertThat(rowBeforeAfterOverride.getCommissionPerSqft()).isEqualByComparingTo(BigDecimal.valueOf(160));
        assertThat(rowBeforeAfterOverride.getId()).isEqualTo(rowBefore.getId());

        // A brand-new booking created AFTER the override correctly uses the new 215 rate.
        UUID plotAfter = seedPlot("A-2");
        SaleResponse saleAfter = plotSaleService.create(plotAfter, saleRequest(BigDecimal.valueOf(400_000), bId));
        BookingCommission rowAfter = onlyRowFor(saleAfter.id(), bId);
        assertThat(rowAfter.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(215_000));
        assertThat(rowAfter.getCommissionPerSqft()).isEqualByComparingTo(BigDecimal.valueOf(215));
    }

    private DesignationSlab findSlabNamed(String name) {
        return designationSlabRepository.findAllForOrg(orgId).stream()
                .filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    private BookingCommission onlyRowFor(UUID saleId, UUID brokerId) {
        return bookingCommissionRepository.findByPlotSaleId(saleId).stream()
                .filter(r -> r.getBeneficiaryBrokerId().equals(brokerId)).findFirst().orElseThrow();
    }

    private void payInFull(UUID saleId, BigDecimal amount) {
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
        DesignationSlab slab = designationSlabService.resolve(orgId, ratePerSqft == 160 ? 0 : 1);
        broker.setCurrentDesignationId(slab.getId());
        broker.setCurrentCommissionRate(slab.getRatePerSqft());
        brokerRepository.saveAndFlush(broker);
        // Even a top-level broker needs its own depth-0 self-row in the
        // closure table -- V65_010's counts trigger reads via
        // broker_network, and with NO row at all (not even self) it finds
        // nothing to update, silently leaving personal/team_successful_bookings
        // at 0 forever. Caught before ever running this test, by re-checking
        // against the exact same seeding helper every other step 7/8/9 test
        // class already uses.
        brokerNetworkService.attachNewBroker(orgId, brokerId, uplineBrokerId);
        return brokerId;
    }

    private void seedOrgAndProject() {
        orgId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        TestTenantContext.bind(orgId, userId, "BUILDER", "BUILDER_ADMIN", Set.of());

        Organization org = new Organization(orgId, Organization.Type.BUILDER, "Manual Override Test Org", "Jaipur");
        organizationRepository.saveAndFlush(org);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UUID roleId = (UUID) entityManager.createNativeQuery(
                            "SELECT id FROM role WHERE code = 'BUILDER_ADMIN' AND org_id IS NULL")
                    .getSingleResult();
            entityManager.createNativeQuery(
                            "INSERT INTO app_user (id, org_id, full_name, mobile, role_id, is_owner) "
                                    + "VALUES (:id, :orgId, 'Manual Override Test User', :mobile, :roleId, true)")
                    .setParameter("id", userId)
                    .setParameter("orgId", orgId)
                    .setParameter("mobile", TestMobiles.next())
                    .setParameter("roleId", roleId)
                    .executeUpdate();
        });

        Project project = new Project(projectId, orgId, "Manual Override Test Project", Project.Type.RESIDENTIAL_PLOT_COLONY,
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
