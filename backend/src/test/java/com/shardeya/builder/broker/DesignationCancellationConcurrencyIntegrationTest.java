package com.shardeya.builder.broker;

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
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 06-BROKER-NETWORK-ENGINE.md §9/§14(§43), build-order step 9's own
 * explicit instruction, REVISED post-ship for the booking-vs-completion
 * behaviour change: "cancellation touches the exact same count/rollup/
 * designation machinery step 7 just hardened... add a concurrent test
 * covering cancel-vs-complete on a shared upline." Under the OLD rule,
 * completing a booking pushed a shared upline's count UP while cancelling
 * one pushed it DOWN, so racing those two was the natural way to prove
 * neither direction loses an update. Under the REVISED rule, completion no
 * longer touches counts at all -- the only thing that can push a count UP
 * any more is a NEW BOOKING (create()), so THAT is now the correct
 * opposite-direction counterpart to race against a cancellation. See
 * CLAUDE.md's own "Post-M6.5 Behaviour Change" note for the full
 * before/after and why this test was redesigned, not just patched --
 * the original version's own pre-race setup assertion (line 127 in the
 * old file) started failing the moment counting moved to booking time,
 * because the "not yet completed" booking it relied on to stay
 * uncounted until the race began was, itself, already counted the
 * instant it was created.
 *
 * <p>No new locking mechanism was needed for this to be correct --
 * V65_013's counts trigger already fires generically on plot_sale INSERT
 * (a booking) OR UPDATE OF status (a cancellation), and
 * DesignationPromotionService's evaluateOne() already reads via
 * entityManager.refresh() after an explicit flush, the exact same pattern
 * step 7 already proved safe. This test exists to CONFIRM that holds when
 * the two racing transactions are pulling the SAME shared upline's count
 * in OPPOSITE directions at once, not just introduce something new.
 */
class DesignationCancellationConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PlotSaleService plotSaleService;
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
    private DesignationHistoryRepository historyRepository;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID orgId;
    private UUID userId;
    private UUID projectId;

    @AfterEach
    void clearTenant() {
        TestTenantContext.clear();
    }

    // Me <- A <- {B1, B2}. Setup (sequential, no payment/completion needed
    // any more -- create() alone counts and promotes): B1 books X1 (B1/A/Me
    // all promote to Senior Business Executive/180); B2 books X2 (B2
    // promotes to 180; A/Me promote again, to Business Development
    // Officer/200 -- team=2 now). RACE (genuinely parallel): B1 books a
    // brand-new X3 (wants A/Me's team to go 2->3) while, at the same time,
    // X2 (B2's booking) is cancelled (wants A/Me's team to go 2->1, and
    // B2's own count/designation to reverse entirely). Neither thread's
    // effect may be lost regardless of which the DB happens to serialize
    // first -- the guaranteed-correct FINAL state (never which thread
    // "won") is: A/Me end up back at team=2/BDO(200) (a genuine net wash,
    // but only if BOTH racing updates actually landed), B1 ends promoted
    // to BDO(200) (personal/team=2), B2 ends fully reversed back to
    // Business Executive/160 (personal/team=0).
    @Test
    void newBookingVsCancellationRacingOnASharedUplineBothLandCorrectlyWithNoLostCountOrWrongDesignation() throws Exception {
        UUID plotX1 = seedOrgProjectAndPlot(BigDecimal.valueOf(1000));
        UUID plotX2 = seedPlot("A-2");
        UUID plotX3 = seedPlot("A-3");
        UUID meId = seedDesignationBroker("Me", 160, null);
        UUID aId = seedDesignationBroker("A", 160, meId);
        UUID b1Id = seedDesignationBroker("B1", 160, aId);
        UUID b2Id = seedDesignationBroker("B2", 160, aId);

        SaleResponse saleX1 = plotSaleService.create(plotX1, saleRequest(BigDecimal.valueOf(400_000), b1Id)); // B1/A/Me -> 180
        SaleResponse saleX2 = plotSaleService.create(plotX2, saleRequest(BigDecimal.valueOf(400_000), b2Id)); // B2 -> 180; A/Me -> 200 (team=2)

        entityManager.clear();
        assertThat(brokerRepository.findById(aId).orElseThrow().getTeamSuccessfulBookings()).isEqualTo(2);
        int aHistoryBefore = historyRepository.findByOrgIdAndBrokerIdOrderByEffectiveAtDesc(orgId, aId).size();
        int meHistoryBefore = historyRepository.findByOrgIdAndBrokerIdOrderByEffectiveAtDesc(orgId, meId).size();
        int b1HistoryBefore = historyRepository.findByOrgIdAndBrokerIdOrderByEffectiveAtDesc(orgId, b1Id).size();
        int b2HistoryBefore = historyRepository.findByOrgIdAndBrokerIdOrderByEffectiveAtDesc(orgId, b2Id).size();

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Void> bookX3 = executor.submit(job(barrier, () -> plotSaleService.create(plotX3, saleRequest(BigDecimal.valueOf(400_000), b1Id))));
            Future<Void> cancelX2 = executor.submit(job(barrier, () -> plotSaleService.cancel(saleX2.id(), new CancelSaleRequest("Buyer defaulted", null))));
            bookX3.get(30, TimeUnit.SECONDS);
            cancelX2.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        entityManager.clear();

        BrokerPartner a = brokerRepository.findById(aId).orElseThrow();
        assertThat(a.getTeamSuccessfulBookings()).isEqualTo(2); // X1 + X3, X2 removed -- net wash only if BOTH racing updates landed
        assertThat(a.getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(200));

        BrokerPartner me = brokerRepository.findById(meId).orElseThrow();
        assertThat(me.getTeamSuccessfulBookings()).isEqualTo(2);
        assertThat(me.getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(200));

        BrokerPartner b1 = brokerRepository.findById(b1Id).orElseThrow();
        assertThat(b1.getPersonalSuccessfulBookings()).isEqualTo(2); // X1 + X3
        assertThat(b1.getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(200));

        BrokerPartner b2 = brokerRepository.findById(b2Id).orElseThrow();
        assertThat(b2.getPersonalSuccessfulBookings()).isZero(); // X2 fully reversed
        assertThat(b2.getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(160));

        // No lost update, no double-promotion: exactly the right NUMBER of
        // new transitions landed during the race, on top of whatever
        // history setup already produced.
        assertThat(historyRepository.findByOrgIdAndBrokerIdOrderByEffectiveAtDesc(orgId, aId)).hasSize(aHistoryBefore + 2); // one demotion, one promotion, in either order
        assertThat(historyRepository.findByOrgIdAndBrokerIdOrderByEffectiveAtDesc(orgId, meId)).hasSize(meHistoryBefore + 2);
        assertThat(historyRepository.findByOrgIdAndBrokerIdOrderByEffectiveAtDesc(orgId, b1Id)).hasSize(b1HistoryBefore + 1); // 180 -> 200
        assertThat(historyRepository.findByOrgIdAndBrokerIdOrderByEffectiveAtDesc(orgId, b2Id)).hasSize(b2HistoryBefore + 1); // 180 -> 160, CANCELLATION_REVERSAL
    }

    private Callable<Void> job(CyclicBarrier barrier, Runnable action) {
        return () -> {
            TestTenantContext.bind(orgId, userId, "BUILDER", "BUILDER_ADMIN", Set.of());
            try {
                barrier.await(15, TimeUnit.SECONDS);
                action.run();
            } finally {
                TestTenantContext.clear();
            }
            return null;
        };
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
        userId = UUID.randomUUID();

        TestTenantContext.bind(orgId, userId, "BUILDER", "BUILDER_ADMIN", Set.of());

        Organization org = new Organization(orgId, Organization.Type.BUILDER, "Designation Cancellation Concurrency Test Org", "Jaipur");
        organizationRepository.saveAndFlush(org);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UUID roleId = (UUID) entityManager.createNativeQuery(
                            "SELECT id FROM role WHERE code = 'BUILDER_ADMIN' AND org_id IS NULL")
                    .getSingleResult();
            entityManager.createNativeQuery(
                            "INSERT INTO app_user (id, org_id, full_name, mobile, role_id, is_owner) "
                                    + "VALUES (:id, :orgId, 'Designation Cancellation Concurrency Test User', :mobile, :roleId, true)")
                    .setParameter("id", userId)
                    .setParameter("orgId", orgId)
                    .setParameter("mobile", TestMobiles.next())
                    .setParameter("roleId", roleId)
                    .executeUpdate();
        });

        Project project = new Project(projectId, orgId, "Designation Cancellation Concurrency Test Project", Project.Type.RESIDENTIAL_PLOT_COLONY,
                "1 Test Rd", "Locality", "Jaipur", "RJ", BigDecimal.TEN, "ACRE", BigDecimal.valueOf(435600), 100);
        projectRepository.saveAndFlush(project);

        Plot plot = new Plot(plotId, orgId, projectId, "A-1", areaSqft, "SQ_FT", areaSqft, BigDecimal.valueOf(4_200_000));
        plotRepository.saveAndFlush(plot);

        return plotId;
    }

    private UUID seedPlot(String plotNumber) {
        UUID plotId = UUID.randomUUID();
        Plot plot = new Plot(plotId, orgId, projectId, plotNumber, BigDecimal.valueOf(1000), "SQ_FT",
                BigDecimal.valueOf(1000), BigDecimal.valueOf(4_200_000));
        plotRepository.saveAndFlush(plot);
        return plotId;
    }
}
