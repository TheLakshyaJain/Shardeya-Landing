package com.shardeya.builder.broker;

import com.shardeya.builder.plot.Plot;
import com.shardeya.builder.plot.PlotRepository;
import com.shardeya.builder.project.Project;
import com.shardeya.builder.project.ProjectRepository;
import com.shardeya.builder.sale.PlotSale;
import com.shardeya.builder.sale.PlotSaleService;
import com.shardeya.builder.sale.dto.SaleCreateRequest;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 06-BROKER-NETWORK-ENGINE.md §14/§43, build-order step 7's own explicit
 * "single most bug-prone area" requirement, REVISED post-ship for the
 * booking-vs-completion behaviour change: "two bookings completing
 * simultaneously for the same broker/downline must not lose a count,
 * double-promote, assign a wrong designation, or snapshot a wrong rate."
 * Counting/promotion now fires at BOOKED (create()), not COMPLETED -- so
 * the genuinely parallel operation this test races is now two {@code create()}
 * calls, not two {@code complete()} calls. See CLAUDE.md's own "Post-M6.5
 * Behaviour Change" note for the full before/after and why this test was
 * rewritten, not just patched: under the old code, this test's own two
 * complete() calls were what actually exercised the counts trigger's
 * concurrency protection; under the new code, complete() no longer touches
 * designation state at all, so racing two of them would no longer test
 * anything real for this concern -- exactly the "don't just make failing
 * tests pass blindly" trap this round was warned about, caught here before
 * it could happen by tracing what the race would ACTUALLY exercise, not
 * just checking whether the old test still happened to pass.
 *
 * <p>Each worker thread gets its own DB connection (a real Testcontainers
 * Postgres, real HikariCP pool) and its own independent
 * {@code @Transactional} transaction (Spring's transaction binding is
 * thread-local); a {@link CyclicBarrier} lines both threads up to start
 * their {@code create()} call at essentially the same instant, maximising
 * the chance of genuine row-lock contention on the shared upline rows
 * rather than accidentally-sequential execution.
 *
 * <p>{@code TenantContext} is a plain (non-inheritable) {@code ThreadLocal}
 * (see {@code TenantContext}'s own javadoc) -- the main test thread's
 * {@link TestTenantContext#bind} does NOT propagate to the executor's
 * worker threads. Each worker binds its own copy of the same tenant facts
 * before calling into {@code plotSaleService}, and clears it afterward so a
 * pooled thread never leaks tenant state into some future test.
 */
class DesignationPromotionConcurrencyIntegrationTest extends AbstractIntegrationTest {

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

    // §43: "two bookings sharing an upline, created simultaneously, must
    // not lose a count, double-promote, assign a wrong designation, or
    // snapshot a wrong rate." Me <- A <- {B1, B2}: B1 and B2 book their
    // first sales in two genuinely parallel transactions -- no payment, no
    // completion needed at all any more, since neither has any bearing on
    // counting/promotion under the revised rule. Regardless of which of
    // them the DB happens to serialize first (never asserted on -- only
    // the guaranteed-correct FINAL state is), the end state must be
    // exactly: B1 and B2 each at personal=1/team=1/Senior Business
    // Executive(180); A and Me each at team=2/Business Development
    // Officer(200) (2 bookings, one from each leaf); a designation_history
    // row for every individual slab transition that actually happened, no
    // more, no fewer.
    @Test
    void twoBookingsSharingAnUplineInParallelBothCountCorrectlyWithNoLostUpdateOrDoublePromotion() throws Exception {
        UUID plotId1 = seedOrgProjectAndPlot(BigDecimal.valueOf(1000));
        UUID plotId2 = seedSecondPlot();
        UUID meId = seedDesignationBroker("Me", 160, null);
        UUID aId = seedDesignationBroker("A", 160, meId);
        UUID b1Id = seedDesignationBroker("B1", 160, aId);
        UUID b2Id = seedDesignationBroker("B2", 160, aId);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Void> bookSale1 = bookingJob(barrier, plotId1, b1Id);
            Callable<Void> bookSale2 = bookingJob(barrier, plotId2, b2Id);

            Future<Void> f1 = executor.submit(bookSale1);
            Future<Void> f2 = executor.submit(bookSale2);
            f1.get(30, TimeUnit.SECONDS);
            f2.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        entityManager.clear();

        BrokerPartner b1 = brokerRepository.findById(b1Id).orElseThrow();
        assertThat(b1.getPersonalSuccessfulBookings()).isEqualTo(1);
        assertThat(b1.getTeamSuccessfulBookings()).isEqualTo(1);
        assertThat(b1.getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(180));

        BrokerPartner b2 = brokerRepository.findById(b2Id).orElseThrow();
        assertThat(b2.getPersonalSuccessfulBookings()).isEqualTo(1);
        assertThat(b2.getTeamSuccessfulBookings()).isEqualTo(1);
        assertThat(b2.getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(180));

        // The property that actually proves no lost update: BOTH bookings
        // must be reflected in A/Me's counts, not just whichever
        // transaction happened to run last.
        BrokerPartner a = brokerRepository.findById(aId).orElseThrow();
        assertThat(a.getTeamSuccessfulBookings()).isEqualTo(2);
        assertThat(a.getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(200)); // Business Development Officer

        BrokerPartner me = brokerRepository.findById(meId).orElseThrow();
        assertThat(me.getTeamSuccessfulBookings()).isEqualTo(2);
        assertThat(me.getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(200));

        // No double-promotion: exactly one history row per real transition
        // that happened, not two of the same transition racing each other
        // in, and not a duplicate for the same broker from both threads.
        assertThat(historyRepository.findByOrgIdAndBrokerIdOrderByEffectiveAtDesc(orgId, b1Id)).hasSize(1);
        assertThat(historyRepository.findByOrgIdAndBrokerIdOrderByEffectiveAtDesc(orgId, b2Id)).hasSize(1);
        assertThat(historyRepository.findByOrgIdAndBrokerIdOrderByEffectiveAtDesc(orgId, aId)).hasSize(2); // 160->180, 180->200
        assertThat(historyRepository.findByOrgIdAndBrokerIdOrderByEffectiveAtDesc(orgId, meId)).hasSize(2);
    }

    private Callable<Void> bookingJob(CyclicBarrier barrier, UUID plotId, UUID brokerId) {
        return () -> {
            TestTenantContext.bind(orgId, userId, "BUILDER", "BUILDER_ADMIN", Set.of());
            try {
                barrier.await(15, TimeUnit.SECONDS);
                plotSaleService.create(plotId, saleRequest(BigDecimal.valueOf(400_000), brokerId));
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
        BrokerPartner broker = new BrokerPartner(brokerId, orgId, name, com.shardeya.support.TestMobiles.next(),
                BrokerPartner.CommissionType.DESIGNATION);
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

        Organization org = new Organization(orgId, Organization.Type.BUILDER, "Designation Concurrency Test Org", "Jaipur");
        organizationRepository.saveAndFlush(org);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UUID roleId = (UUID) entityManager.createNativeQuery(
                            "SELECT id FROM role WHERE code = 'BUILDER_ADMIN' AND org_id IS NULL")
                    .getSingleResult();
            entityManager.createNativeQuery(
                            "INSERT INTO app_user (id, org_id, full_name, mobile, role_id, is_owner) "
                                    + "VALUES (:id, :orgId, 'Designation Concurrency Test User', :mobile, :roleId, true)")
                    .setParameter("id", userId)
                    .setParameter("orgId", orgId)
                    .setParameter("mobile", com.shardeya.support.TestMobiles.next())
                    .setParameter("roleId", roleId)
                    .executeUpdate();
        });

        Project project = new Project(projectId, orgId, "Designation Concurrency Test Project", Project.Type.RESIDENTIAL_PLOT_COLONY,
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
