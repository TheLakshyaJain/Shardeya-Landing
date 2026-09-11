package com.shardeya.builder.broker;

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
 * 06-BROKER-NETWORK-ENGINE.md §9/§41, build-order step 9 -- cancellation
 * with recovery, the "locked Option 1: RECOVER" decision. REVISED
 * post-ship: counting/promotion moved from COMPLETED to BOOKED (see
 * CLAUDE.md's own "Post-M6.5 Behaviour Change" note), which makes
 * cancellation the ONLY thing that ever reverses a count now -- every
 * booking counts the instant it's created, whether or not it ever reaches
 * COMPLETED. This file was rewritten against that rule, not patched.
 * Concurrent cancel-vs-booking coverage lives in the sibling class
 * DesignationCancellationConcurrencyIntegrationTest.
 */
class DesignationCancellationIntegrationTest extends AbstractIntegrationTest {

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
    private BrokerNetworkService brokerNetworkService;
    @Autowired
    private DesignationSlabService designationSlabService;
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

    // REVISED, renamed from ...NeverTouchesCounts: under the old rule, a
    // booking that never reached COMPLETED never counted in the first
    // place, so cancelling it was a no-op for counts. Under the new rule
    // this is now the OPPOSITE and load-bearing case -- the booking
    // counted (and promoted) the instant it was created, so cancelling it
    // -- even though it never got anywhere near COMPLETED -- must reverse
    // that count and demotion exactly as fully as cancelling a fully-paid
    // one would.
    @Test
    void cancellingAPartiallyReleasedStillActiveBookingRecoversEveryBeneficiaryAndReversesTheCountItSetAtBooking() {
        UUID plotId = seedOrgProjectAndPlot(BigDecimal.valueOf(1000));
        UUID meId = seedDesignationBroker("Me", 160, null);
        UUID aId = seedDesignationBroker("A", 160, meId);
        UUID bId = seedDesignationBroker("B", 160, aId);

        SaleResponse sale = plotSaleService.create(plotId, saleRequest(BigDecimal.valueOf(400_000), bId));
        // Counted and promoted immediately at booking -- long before any payment.
        entityManager.clear();
        assertThat(brokerRepository.findById(bId).orElseThrow().getTeamSuccessfulBookings()).isEqualTo(1);
        assertThat(brokerRepository.findById(bId).orElseThrow().getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(180));

        payInFull(sale.id(), BigDecimal.valueOf(100_000)); // 25% -- never reaches COMPLETED

        plotSaleService.cancel(sale.id(), new CancelSaleRequest("Buyer backed out", null));

        entityManager.clear();
        List<BookingCommission> rows = bookingCommissionRepository.findByPlotSaleId(sale.id());
        assertThat(rows).hasSize(3);
        BookingCommission bRow = rowFor(rows, bId);
        assertThat(bRow.getStatus()).isEqualTo(BookingCommission.Status.CANCELLED);
        assertThat(bRow.getReleasedAmount()).isEqualByComparingTo(BigDecimal.valueOf(40_000)); // 25% of 160,000, unchanged

        BookingCommission aRow = rowFor(rows, aId);
        assertThat(aRow.getStatus()).isEqualTo(BookingCommission.Status.CANCELLED);
        // §7's revised same-slab formula: A and Me share B's 160 slab, next
        // slab up is 180, so the frozen same-slab bonus is (180-160)*1000 =
        // 20,000 (not the old flat 10,000) -- 25% released = 5,000.
        assertThat(aRow.getReleasedAmount()).isEqualByComparingTo(BigDecimal.valueOf(5_000)); // 25% of the 20,000 same-slab bonus

        // Never completed, but it WAS booked -- cancelling it must fully
        // reverse the count and demotion that booking caused.
        BrokerPartner b = brokerRepository.findById(bId).orElseThrow();
        assertThat(b.getTeamSuccessfulBookings()).isZero();
        assertThat(b.getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(160));
        BrokerPartner a = brokerRepository.findById(aId).orElseThrow();
        assertThat(a.getTeamSuccessfulBookings()).isZero();
        assertThat(a.getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(160));
        BrokerPartner me = brokerRepository.findById(meId).orElseThrow();
        assertThat(me.getTeamSuccessfulBookings()).isZero();
        assertThat(me.getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(160));

        for (UUID brokerId : List.of(bId, aId, meId)) {
            List<DesignationHistory> history = historyRepository.findByOrgIdAndBrokerIdOrderByEffectiveAtDesc(orgId, brokerId);
            assertThat(history).hasSize(2); // 160->180 (AUTOMATIC, from create()), then 180->160 (CANCELLATION_REVERSAL, from cancel())
            assertThat(history.get(0).getChangeType()).isEqualTo(DesignationHistory.ChangeType.CANCELLATION_REVERSAL);
        }
    }

    @Test
    void cancellingAnAlreadyCompletedBookingReversesCountsAndDemotesTheWholeChain() {
        UUID plotId = seedOrgProjectAndPlot(BigDecimal.valueOf(1000));
        UUID meId = seedDesignationBroker("Me", 160, null);
        UUID aId = seedDesignationBroker("A", 160, meId);
        UUID bId = seedDesignationBroker("B", 160, aId);

        // Promotes B, A, Me from 160 to 180 immediately -- at create(), not
        // at complete() any more (REVISED post-ship). payInFull()/complete()
        // below only bring the sale to COMPLETED status; they no longer
        // have any bearing on counts or designation themselves.
        SaleResponse sale = plotSaleService.create(plotId, saleRequest(BigDecimal.valueOf(400_000), bId));
        entityManager.clear();
        assertThat(brokerRepository.findById(bId).orElseThrow().getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(180));

        payInFull(sale.id(), BigDecimal.valueOf(400_000));
        plotSaleService.complete(sale.id());

        entityManager.clear();
        assertThat(brokerRepository.findById(bId).orElseThrow().getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(180)); // unchanged by complete()

        plotSaleService.cancel(sale.id(), new CancelSaleRequest("Buyer defaulted after full payment", null));

        entityManager.clear();
        List<BookingCommission> rows = bookingCommissionRepository.findByPlotSaleId(sale.id());
        BookingCommission bRow = rowFor(rows, bId);
        assertThat(bRow.getStatus()).isEqualTo(BookingCommission.Status.CANCELLED);
        assertThat(bRow.getReleasedAmount()).isEqualByComparingTo(BigDecimal.valueOf(160_000)); // fully released before cancellation

        BrokerPartner b = brokerRepository.findById(bId).orElseThrow();
        assertThat(b.getPersonalSuccessfulBookings()).isZero();
        assertThat(b.getTeamSuccessfulBookings()).isZero();
        assertThat(b.getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(160)); // demoted back

        BrokerPartner a = brokerRepository.findById(aId).orElseThrow();
        assertThat(a.getTeamSuccessfulBookings()).isZero();
        assertThat(a.getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(160));

        BrokerPartner me = brokerRepository.findById(meId).orElseThrow();
        assertThat(me.getTeamSuccessfulBookings()).isZero();
        assertThat(me.getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(160));

        for (UUID brokerId : List.of(bId, aId, meId)) {
            List<DesignationHistory> history = historyRepository.findByOrgIdAndBrokerIdOrderByEffectiveAtDesc(orgId, brokerId);
            assertThat(history).hasSize(2); // 160->180 (AUTOMATIC, from create()), then 180->160 (CANCELLATION_REVERSAL, from cancel())
            DesignationHistory mostRecent = history.get(0);
            assertThat(mostRecent.getChangeType()).isEqualTo(DesignationHistory.ChangeType.CANCELLATION_REVERSAL);
            assertThat(mostRecent.getPreviousRate()).isEqualByComparingTo(BigDecimal.valueOf(180));
            assertThat(mostRecent.getNewRate()).isEqualByComparingTo(BigDecimal.valueOf(160));
        }
    }

    // §11/§24's timing rule extended to demotion: "a demotion only affects
    // FUTURE bookings -- it must NOT rewrite any other still-active
    // booking's already-frozen commission tree." REVISED: under the old
    // rule, sale2's own creation never counted toward anything (only
    // completion did), so cancelling sale1 always demoted cleanly back to
    // the original 160. Under the new rule, sale2's OWN creation ALSO
    // counts and promotes B (and A, Me) a second time -- so cancelling
    // sale1 now only demotes by the ONE booking sale1 itself contributed,
    // landing back at 180 (still counting sale2), never all the way back
    // to 160. This is a MORE faithful version of the same underlying
    // property: two real, independently-counted bookings, and cancelling
    // one must never disturb the other's already-frozen tree.
    @Test
    void aCancellationDrivenDemotionDoesNotAlterAnotherActiveBookingSFrozenCommission() {
        UUID plotId1 = seedOrgProjectAndPlot(BigDecimal.valueOf(1000));
        UUID plotId2 = seedSecondPlot();
        UUID meId = seedDesignationBroker("Me", 160, null);
        UUID aId = seedDesignationBroker("A", 160, meId);
        UUID bId = seedDesignationBroker("B", 160, aId);

        // sale1: B's first booking -- B/A/Me all promoted 160 -> 180.
        SaleResponse sale1 = plotSaleService.create(plotId1, saleRequest(BigDecimal.valueOf(400_000), bId));
        entityManager.clear();
        assertThat(brokerRepository.findById(bId).orElseThrow().getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(180));

        // sale2: B's SECOND booking -- also counts immediately, promoting
        // B/A/Me again, 180 -> 200. sale2's own freeze happens BEFORE that
        // promotion, so it correctly uses B's rate as of THIS instant (180,
        // the rate sale1 left B at), never the 200 this same call goes on
        // to produce.
        SaleResponse sale2 = plotSaleService.create(plotId2, saleRequest(BigDecimal.valueOf(400_000), bId));
        entityManager.clear();
        assertThat(brokerRepository.findById(bId).orElseThrow().getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(200));
        assertThat(brokerRepository.findById(bId).orElseThrow().getTeamSuccessfulBookings()).isEqualTo(2);

        BookingCommission sale2BRowBefore = onlyRowFor(sale2.id(), bId);
        assertThat(sale2BRowBefore.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(180_000));
        assertThat(sale2BRowBefore.getCommissionPerSqft()).isEqualByComparingTo(BigDecimal.valueOf(180));

        // Cancelling sale1 removes exactly ONE booking from B's count
        // (2 -> 1) -- sale2 still counts, so B lands back at Senior
        // Business Executive/180, NOT all the way back down to 160.
        plotSaleService.cancel(sale1.id(), new CancelSaleRequest("Buyer defaulted", null));
        entityManager.clear();
        assertThat(brokerRepository.findById(bId).orElseThrow().getTeamSuccessfulBookings()).isEqualTo(1);
        assertThat(brokerRepository.findById(bId).orElseThrow().getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(180));

        // sale2's own frozen tree -- still ACTIVE, never touched by sale1's cancellation -- must be completely unchanged.
        BookingCommission sale2BRowAfter = onlyRowFor(sale2.id(), bId);
        assertThat(sale2BRowAfter.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(180_000));
        assertThat(sale2BRowAfter.getCommissionPerSqft()).isEqualByComparingTo(BigDecimal.valueOf(180));
        assertThat(sale2BRowAfter.getStatus()).isEqualTo(BookingCommission.Status.PENDING); // still a live, uncancelled row
        assertThat(sale2BRowAfter.getId()).isEqualTo(sale2BRowBefore.getId());
    }

    private BookingCommission rowFor(List<BookingCommission> rows, UUID brokerId) {
        return rows.stream().filter(r -> r.getBeneficiaryBrokerId().equals(brokerId)).findFirst().orElseThrow();
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
        brokerNetworkService.attachNewBroker(orgId, brokerId, uplineBrokerId);
        return brokerId;
    }

    private UUID seedOrgProjectAndPlot(BigDecimal areaSqft) {
        orgId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        TestTenantContext.bind(orgId, userId, "BUILDER", "BUILDER_ADMIN", Set.of());

        Organization org = new Organization(orgId, Organization.Type.BUILDER, "Designation Cancellation Test Org", "Jaipur");
        organizationRepository.saveAndFlush(org);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UUID roleId = (UUID) entityManager.createNativeQuery(
                            "SELECT id FROM role WHERE code = 'BUILDER_ADMIN' AND org_id IS NULL")
                    .getSingleResult();
            entityManager.createNativeQuery(
                            "INSERT INTO app_user (id, org_id, full_name, mobile, role_id, is_owner) "
                                    + "VALUES (:id, :orgId, 'Designation Cancellation Test User', :mobile, :roleId, true)")
                    .setParameter("id", userId)
                    .setParameter("orgId", orgId)
                    .setParameter("mobile", TestMobiles.next())
                    .setParameter("roleId", roleId)
                    .executeUpdate();
        });

        Project project = new Project(projectId, orgId, "Designation Cancellation Test Project", Project.Type.RESIDENTIAL_PLOT_COLONY,
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
