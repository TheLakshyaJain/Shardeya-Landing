package com.shardeya.builder.broker;

import com.shardeya.builder.payment.PaymentRecord;
import com.shardeya.builder.payment.PaymentSchedule;
import com.shardeya.builder.payment.PaymentScheduleRepository;
import com.shardeya.builder.payment.PaymentService;
import com.shardeya.builder.payment.ScheduleService;
import com.shardeya.builder.payment.dto.PaymentCreateRequest;
import com.shardeya.builder.payment.dto.WaiveRequest;
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
 * 06-BROKER-NETWORK-ENGINE.md §1/§4/§6/§35 -- REVISED post-ship behaviour
 * change: counting toward personal/team sales, and promotion evaluation,
 * now happen at BOOKED (plot_sale creation), not COMPLETED. This class
 * was originally written against the old COMPLETED-triggered rule; it has
 * been rewritten from scratch against the new one, not patched to make
 * old assertions pass again. See CLAUDE.md's own "Post-M6.5 Behaviour
 * Change" note for the full before/after and why.
 *
 * <p>A genuinely important thing this rewrite surfaced: 3 of this file's
 * 4 original tests were STILL PASSING under the old code before it was
 * even touched here, purely by coincidence -- each one called create()
 * (which now promotes immediately) followed later by payInFull()/complete()
 * (which no longer does anything for designation state at all), and since
 * the test's own assertions only checked FINAL state after complete()
 * returned, the already-moved-earlier promotion satisfied them anyway.
 * The test bodies were "correct" by the numbers but their own names and
 * comments described a mechanism (completion-triggered promotion) that no
 * longer exists. This is exactly the "don't just make failing tests pass
 * blindly" trap this round was explicitly warned about -- every test
 * below asserts promotion state immediately after create(), never after a
 * payment or completion, to make the new timing impossible to miss if it
 * ever regresses.
 */
class DesignationPromotionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PlotSaleService plotSaleService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private ScheduleService scheduleService;
    @Autowired
    private PlotRepository plotRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private BrokerPartnerRepository brokerRepository;
    @Autowired
    private BrokerNetworkService brokerNetworkService;
    @Autowired
    private DesignationSlabService designationSlabService;
    @Autowired
    private BookingCommissionRepository bookingCommissionRepository;
    @Autowired
    private DesignationHistoryRepository historyRepository;
    @Autowired
    private PaymentScheduleRepository scheduleRepository;
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
    void bookingItsFirstSalePromotesFromBusinessExecutiveToSeniorBusinessExecutiveImmediately() {
        UUID plotId = seedOrgProjectAndPlot(BigDecimal.valueOf(1000));
        UUID bId = seedDesignationBroker("B", 160, null);

        // No payment, no completion -- create() alone must be enough.
        plotSaleService.create(plotId, saleRequest(BigDecimal.valueOf(400_000), bId));

        entityManager.clear();
        BrokerPartner b = brokerRepository.findById(bId).orElseThrow();
        assertThat(b.getPersonalSuccessfulBookings()).isEqualTo(1);
        assertThat(b.getTeamSuccessfulBookings()).isEqualTo(1);
        assertThat(b.getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(180)); // Senior Business Executive

        List<DesignationHistory> history = historyRepository.findByOrgIdAndBrokerIdOrderByEffectiveAtDesc(orgId, bId);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getChangeType()).isEqualTo(DesignationHistory.ChangeType.AUTOMATIC);
        assertThat(history.get(0).getPreviousRate()).isEqualByComparingTo(BigDecimal.valueOf(160));
        assertThat(history.get(0).getNewRate()).isEqualByComparingTo(BigDecimal.valueOf(180));
    }

    // §35: "one downline booking promotes multiple uplines at once" --
    // no payment or completion needed anywhere in this test any more,
    // since neither has any bearing on counting/promotion under the
    // revised rule.
    @Test
    void oneLeafBookingPromotesTheEntireUplineChainAtOnce() {
        UUID plotId1 = seedOrgProjectAndPlot(BigDecimal.valueOf(1000));
        UUID plotId2 = seedSecondPlot();
        UUID meId = seedDesignationBroker("Me", 160, null);
        UUID aId = seedDesignationBroker("A", 160, meId);
        UUID b1Id = seedDesignationBroker("B1", 160, aId);
        UUID b2Id = seedDesignationBroker("B2", 160, aId);

        // B1 books its first sale -- B1, A, and Me ALL sit at team sales =
        // 1 immediately afterward (B1 personal=1/team=1; A team=1 via B1;
        // Me team=1 via B1 through A) -- one booking promotes all three
        // from Business Executive(160) to Senior Business Executive(180)
        // simultaneously.
        plotSaleService.create(plotId1, saleRequest(BigDecimal.valueOf(400_000), b1Id));

        entityManager.clear();
        assertThat(brokerRepository.findById(b1Id).orElseThrow().getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(180));
        assertThat(brokerRepository.findById(aId).orElseThrow().getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(180));
        assertThat(brokerRepository.findById(meId).orElseThrow().getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(180));
        assertThat(brokerRepository.findById(aId).orElseThrow().getTeamSuccessfulBookings()).isEqualTo(1);

        // B2 (a sibling of B1 under the same upline A) books its own first
        // sale -- A's team sales become 2 (B1's + B2's), pushing A AND Me
        // from Senior Business Executive(180) straight to Business
        // Development Officer(200) in the SAME booking that also promotes
        // B2 itself from 160 to 180 -- three more promotions from one
        // booking, none of them B2's own slab.
        plotSaleService.create(plotId2, saleRequest(BigDecimal.valueOf(400_000), b2Id));

        entityManager.clear();
        assertThat(brokerRepository.findById(b2Id).orElseThrow().getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(180));
        assertThat(brokerRepository.findById(aId).orElseThrow().getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(200));
        assertThat(brokerRepository.findById(aId).orElseThrow().getTeamSuccessfulBookings()).isEqualTo(2);
        assertThat(brokerRepository.findById(meId).orElseThrow().getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(200));
        assertThat(brokerRepository.findById(meId).orElseThrow().getTeamSuccessfulBookings()).isEqualTo(2);
        // B1 itself untouched by B2's booking -- still exactly where its own booking left it.
        assertThat(brokerRepository.findById(b1Id).orElseThrow().getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(180));

        assertThat(historyRepository.findByOrgIdAndBrokerIdOrderByEffectiveAtDesc(orgId, aId)).hasSize(2); // 160->180, then 180->200
        assertThat(historyRepository.findByOrgIdAndBrokerIdOrderByEffectiveAtDesc(orgId, meId)).hasSize(2);
    }

    // §1/§4, REVISED: "waiver is now moot for counting" -- a booking
    // counts the instant it's created, regardless of how (or whether) it
    // is ever paid off. This replaces the old
    // aWaivedToZeroBookingDoesNotCountTowardSalesOrPromoteAnyone test,
    // whose entire premise (a waived remainder exempts a booking from
    // counting) no longer exists now that counting happens before any
    // payment could even occur.
    @Test
    void countingHappensAtBookingRegardlessOfLaterPaymentWaiverOrCompletion() {
        UUID plotId = seedOrgProjectAndPlot(BigDecimal.valueOf(1000));
        UUID bId = seedDesignationBroker("B", 160, null);

        LocalDate today = IndianTime.today();
        SaleCreateRequest req = new SaleCreateRequest(null, "Waived Buyer", "9876500099", null, null, null, null,
                today, BigDecimal.valueOf(400_000), bId, null, null, null, PlotSale.PaymentType.INSTALMENT,
                List.of(new ScheduleRowRequest("Booking", BigDecimal.valueOf(100_000), today),
                        new ScheduleRowRequest("Balance", BigDecimal.valueOf(300_000), today.plusMonths(2))),
                null);
        SaleResponse sale = plotSaleService.create(plotId, req);

        // Counted immediately -- BEFORE a single rupee has been paid.
        entityManager.clear();
        BrokerPartner bRightAfterBooking = brokerRepository.findById(bId).orElseThrow();
        assertThat(bRightAfterBooking.getPersonalSuccessfulBookings()).isEqualTo(1);
        assertThat(bRightAfterBooking.getTeamSuccessfulBookings()).isEqualTo(1);
        assertThat(bRightAfterBooking.getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(180)); // already promoted

        List<PaymentSchedule> rows = scheduleRepository.findByPlotSaleIdAndDeletedAtIsNullOrderBySequenceNoAsc(sale.id());
        paymentService.record(sale.id(), new PaymentCreateRequest(BigDecimal.valueOf(100_000), today,
                PaymentRecord.Mode.BANK_TRANSFER, "NEFT-PARTIAL", null, null));
        scheduleService.waive(rows.get(1).getId(), new WaiveRequest("Builder is forgiving the balance for this buyer"));
        SaleResponse completed = plotSaleService.complete(sale.id()); // reaches COMPLETED via a waived balance -- allowed, unchanged M5 rule
        assertThat(completed.status()).isEqualTo("COMPLETED");

        // Unaffected either way -- the payment, the waiver, and the
        // completion itself all have zero effect on counts or designation.
        entityManager.clear();
        BrokerPartner bAfterCompletion = brokerRepository.findById(bId).orElseThrow();
        assertThat(bAfterCompletion.getPersonalSuccessfulBookings()).isEqualTo(1);
        assertThat(bAfterCompletion.getTeamSuccessfulBookings()).isEqualTo(1);
        assertThat(bAfterCompletion.getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(180));
        // Still exactly the one AUTOMATIC row from booking -- completion never adds a second.
        assertThat(historyRepository.findByOrgIdAndBrokerIdOrderByEffectiveAtDesc(orgId, bId)).hasSize(1);
    }

    // §11/§24: "promotion applies only after the booking's own freeze; the
    // triggering booking uses the old rate." No payment or completion
    // needed to demonstrate this any more -- the whole rule now plays out
    // synchronously inside create() itself.
    @Test
    void theBookingSOwnFrozenCommissionIsUnchangedByThePromotionItTriggers() {
        UUID plotId1 = seedOrgProjectAndPlot(BigDecimal.valueOf(1000));
        UUID plotId2 = seedSecondPlot();
        UUID meId = seedDesignationBroker("Me", 160, null);
        UUID aId = seedDesignationBroker("A", 160, meId);
        UUID bId = seedDesignationBroker("B", 160, aId);

        SaleResponse sale = plotSaleService.create(plotId1, saleRequest(BigDecimal.valueOf(400_000), bId));
        // This exact call promoted B (and A, Me) from 160 to 180 -- but its
        // OWN frozen tree must still read exactly as it did at the instant
        // it was frozen (before the promotion it itself triggered).
        entityManager.clear();
        assertThat(brokerRepository.findById(bId).orElseThrow().getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(180));

        BookingCommission bRow = onlyRowFor(sale.id(), bId);
        assertThat(bRow.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(160_000));
        assertThat(bRow.getCommissionPerSqft()).isEqualByComparingTo(BigDecimal.valueOf(160));

        // A brand-new booking, created AFTER the promotion, correctly uses the NEW rate -- "future bookings only" (§6).
        SaleResponse sale2 = plotSaleService.create(plotId2, saleRequest(BigDecimal.valueOf(400_000), bId));
        BookingCommission bRow2 = onlyRowFor(sale2.id(), bId);
        assertThat(bRow2.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(180_000));
        assertThat(bRow2.getCommissionPerSqft()).isEqualByComparingTo(BigDecimal.valueOf(180));
    }

    private BookingCommission onlyRowFor(UUID saleId, UUID brokerId) {
        return bookingCommissionRepository.findByPlotSaleId(saleId).stream()
                .filter(r -> r.getBeneficiaryBrokerId().equals(brokerId)).findFirst().orElseThrow();
    }

    private SaleCreateRequest saleRequest(BigDecimal dealValue, UUID brokerId) {
        LocalDate today = IndianTime.today();
        return new SaleCreateRequest(null, "Rajesh Kumar", "9876543210", null, null, null, null,
                today, dealValue, brokerId, null, null, null, PlotSale.PaymentType.INSTALMENT,
                List.of(new ScheduleRowRequest("Booking", dealValue, today)), null);
    }

    private UUID seedDesignationBroker(String name, int ratePerSqft, UUID uplineBrokerId) {
        UUID brokerId = UUID.randomUUID();
        BrokerPartner broker = new BrokerPartner(brokerId, orgId, name, com.shardeya.support.TestMobiles.next(),
                BrokerPartner.CommissionType.DESIGNATION);
        broker.setUplineBrokerId(uplineBrokerId);
        DesignationSlab slab = designationSlabService.resolve(orgId, ratePerSqftToTeamSales(ratePerSqft));
        broker.setCurrentDesignationId(slab.getId());
        broker.setCurrentCommissionRate(slab.getRatePerSqft());
        brokerRepository.saveAndFlush(broker);
        brokerNetworkService.attachNewBroker(orgId, brokerId, uplineBrokerId);
        return brokerId;
    }

    private int ratePerSqftToTeamSales(int ratePerSqft) {
        return switch (ratePerSqft) {
            case 160 -> 0;
            case 180 -> 1;
            case 200 -> 2;
            default -> throw new IllegalArgumentException("No fixture slab for rate " + ratePerSqft);
        };
    }

    private UUID seedOrgProjectAndPlot(BigDecimal areaSqft) {
        orgId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        TestTenantContext.bind(orgId, userId, "BUILDER", "BUILDER_ADMIN", Set.of());

        Organization org = new Organization(orgId, Organization.Type.BUILDER, "Designation Promotion Test Org", "Jaipur");
        organizationRepository.saveAndFlush(org);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UUID roleId = (UUID) entityManager.createNativeQuery(
                            "SELECT id FROM role WHERE code = 'BUILDER_ADMIN' AND org_id IS NULL")
                    .getSingleResult();
            entityManager.createNativeQuery(
                            "INSERT INTO app_user (id, org_id, full_name, mobile, role_id, is_owner) "
                                    + "VALUES (:id, :orgId, 'Designation Promotion Test User', :mobile, :roleId, true)")
                    .setParameter("id", userId)
                    .setParameter("orgId", orgId)
                    .setParameter("mobile", com.shardeya.support.TestMobiles.next())
                    .setParameter("roleId", roleId)
                    .executeUpdate();
        });

        Project project = new Project(projectId, orgId, "Designation Promotion Test Project", Project.Type.RESIDENTIAL_PLOT_COLONY,
                "1 Test Rd", "Locality", "Jaipur", "RJ", BigDecimal.TEN, "ACRE", BigDecimal.valueOf(435600), 100);
        projectRepository.saveAndFlush(project);

        Plot plot = new Plot(plotId, orgId, projectId, "A-1", areaSqft, "SQ_FT", areaSqft, BigDecimal.valueOf(4_200_000));
        plotRepository.saveAndFlush(plot);

        return plotId;
    }

    private UUID seedSecondPlot() {
        UUID plotId = UUID.randomUUID();
        Plot plot = new Plot(plotId, orgId, projectId, "A-2", BigDecimal.valueOf(1000), "SQ_FT",
                BigDecimal.valueOf(1000), BigDecimal.valueOf(4_200_000));
        plotRepository.saveAndFlush(plot);
        return plotId;
    }
}
