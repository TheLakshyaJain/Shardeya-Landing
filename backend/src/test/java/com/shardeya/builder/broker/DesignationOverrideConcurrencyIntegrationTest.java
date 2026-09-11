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
import com.shardeya.support.TestMobiles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
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
 * 06-BROKER-NETWORK-ENGINE.md build-order step 11's own concurrency
 * requirement, REVISED post-ship for the booking-vs-completion behaviour
 * change: "a manual-override racing a completion on the same broker" is
 * rewritten here as "a manual-override racing a BOOKING (create()) on the
 * same broker" -- see CLAUDE.md's own "Post-M6.5 Behaviour Change" note
 * for the full reasoning. Counting/promotion moved from complete() to
 * create(), so complete() no longer touches {@code broker_partner}'s
 * designation fields at all -- an override racing a completion would no
 * longer exercise any real contention (the original version of this test
 * would have started passing unconditionally, for the wrong reason, the
 * exact "don't just make failing tests pass blindly" trap this round was
 * warned about). The booking transaction is now where the real lock lives.
 *
 * <p>Protected end to end by the same two mechanisms as before, just at
 * the new trigger point: (1) {@code fn_broker_partner_designation_counts_trigger}'s
 * own {@code PERFORM ... FOR UPDATE} (step 7, moved to fire on booking by
 * V65_013) holds {@code broker_partner}'s row lock for the WHOLE booking
 * transaction, so {@code manuallyOverride()}'s later Hibernate
 * {@code UPDATE} (issued by a genuinely separate transaction) can never
 * interleave its effects with the booking's own write -- it either fully
 * precedes it, or blocks until the booking's transaction is entirely
 * done; and (2) {@code BrokerPartner}'s {@code @Version} column, plus
 * step 11's own broker-save-before-history-insert reorder in both
 * {@code manuallyOverride()} and {@code evaluateOne()}, which is what
 * keeps this from deadlocking the way the original (pre-step-11) code
 * would have. Confirmed here at the JUnit level, not just reasoned about.
 */
class DesignationOverrideConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PlotSaleService plotSaleService;
    @Autowired
    private DesignationPromotionService designationPromotionService;
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

    @Test
    void manualOverrideRacingABookingOnTheSameBrokerNeverLosesAnUpdateOrCorruptsState() throws Exception {
        UUID plotId = seedOrgProjectAndPlot(BigDecimal.valueOf(1000));
        UUID brokerId = seedDesignationBroker("Racer", null);
        DesignationSlab businessManager = designationSlabService.resolve(orgId, 3); // Business Manager, 3-5 team sales, 215

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        boolean[] overrideSucceeded = new boolean[1];
        Exception[] overrideFailure = new Exception[1];
        try {
            Callable<Void> overrideJob = () -> {
                TestTenantContext.bind(orgId, userId, "BUILDER", "BUILDER_ADMIN", Set.of());
                try {
                    barrier.await(15, TimeUnit.SECONDS);
                    designationPromotionService.manuallyOverride(brokerId, businessManager.getId(), "Racing a booking, step 11 concurrency test");
                    overrideSucceeded[0] = true;
                } catch (OptimisticLockingFailureException e) {
                    // A legitimate, expected outcome of losing the race --
                    // NOT rethrown, so the booking thread's own Future.get()
                    // below is never masked by this one.
                    overrideFailure[0] = e;
                } finally {
                    TestTenantContext.clear();
                }
                return null;
            };
            Callable<Void> bookJob = () -> {
                TestTenantContext.bind(orgId, userId, "BUILDER", "BUILDER_ADMIN", Set.of());
                try {
                    barrier.await(15, TimeUnit.SECONDS);
                    plotSaleService.create(plotId, saleRequest(BigDecimal.valueOf(400_000), brokerId));
                } finally {
                    TestTenantContext.clear();
                }
                return null;
            };

            Future<Void> f1 = executor.submit(overrideJob);
            Future<Void> f2 = executor.submit(bookJob);
            f1.get(30, TimeUnit.SECONDS);
            f2.get(30, TimeUnit.SECONDS); // the booking itself must never fail regardless of the race's outcome
        } finally {
            executor.shutdownNow();
        }

        entityManager.clear();

        // Sales counts are trigger-maintained independently of which side
        // of the Java-level race won -- always correctly 1 either way,
        // since the booking itself always succeeds.
        BrokerPartner broker = brokerRepository.findById(brokerId).orElseThrow();
        assertThat(broker.getPersonalSuccessfulBookings()).isEqualTo(1);
        assertThat(broker.getTeamSuccessfulBookings()).isEqualTo(1);

        List<DesignationHistory> history = historyRepository.findByOrgIdAndBrokerIdOrderByEffectiveAtDesc(orgId, brokerId);
        // Exactly one designation change ever took effect -- never zero
        // (something silently swallowed), never two (a lost-update-shaped
        // corruption where both "succeeded" against inconsistent state).
        assertThat(history).hasSize(1);

        if (overrideSucceeded[0]) {
            // The override won: it fully committed before the booking's
            // own evaluateAndPromote() ever read this broker, so the
            // booking correctly saw designationManuallyOverridden=true and
            // skipped its own promotion entirely (freezing the booking's
            // own commission tree at whatever rate was current at freeze
            // time -- unaffected either way, see the sibling
            // DesignationManualOverrideIntegrationTest for that assertion).
            assertThat(broker.isDesignationManuallyOverridden()).isTrue();
            assertThat(broker.getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(215));
            assertThat(history.get(0).getChangeType()).isEqualTo(DesignationHistory.ChangeType.MANUAL);
        } else {
            // The booking won: the override's blocked UPDATE, once
            // unblocked, found its @Version predicate no longer matched
            // (the booking had already bumped it) and cleanly failed --
            // its own designation_history insert rolled back with it, in
            // the SAME transaction, leaving no orphaned row behind.
            assertThat(overrideFailure[0]).isInstanceOf(OptimisticLockingFailureException.class);
            assertThat(broker.isDesignationManuallyOverridden()).isFalse();
            assertThat(broker.getCurrentCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(180)); // Senior Business Executive
            assertThat(history.get(0).getChangeType()).isEqualTo(DesignationHistory.ChangeType.AUTOMATIC);
        }
    }

    private SaleCreateRequest saleRequest(BigDecimal dealValue, UUID brokerId) {
        LocalDate today = IndianTime.today();
        return new SaleCreateRequest(null, "Rajesh Kumar", "9876543210", null, null, null, null,
                today, dealValue, brokerId, null, null, null, PlotSale.PaymentType.INSTALMENT,
                List.of(new ScheduleRowRequest("Booking", dealValue, today)), null);
    }

    private UUID seedDesignationBroker(String name, UUID uplineBrokerId) {
        UUID brokerId = UUID.randomUUID();
        BrokerPartner broker = new BrokerPartner(brokerId, orgId, name, TestMobiles.next(), BrokerPartner.CommissionType.DESIGNATION);
        broker.setUplineBrokerId(uplineBrokerId);
        DesignationSlab slab = designationSlabService.resolve(orgId, 0);
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

        Organization org = new Organization(orgId, Organization.Type.BUILDER, "Override Concurrency Test Org", "Jaipur");
        organizationRepository.saveAndFlush(org);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UUID roleId = (UUID) entityManager.createNativeQuery(
                            "SELECT id FROM role WHERE code = 'BUILDER_ADMIN' AND org_id IS NULL")
                    .getSingleResult();
            entityManager.createNativeQuery(
                            "INSERT INTO app_user (id, org_id, full_name, mobile, role_id, is_owner) "
                                    + "VALUES (:id, :orgId, 'Override Concurrency Test User', :mobile, :roleId, true)")
                    .setParameter("id", userId)
                    .setParameter("orgId", orgId)
                    .setParameter("mobile", TestMobiles.next())
                    .setParameter("roleId", roleId)
                    .executeUpdate();
        });

        Project project = new Project(projectId, orgId, "Override Concurrency Test Project", Project.Type.RESIDENTIAL_PLOT_COLONY,
                "1 Test Rd", "Locality", "Jaipur", "RJ", BigDecimal.TEN, "ACRE", BigDecimal.valueOf(435600), 100);
        projectRepository.saveAndFlush(project);

        Plot plot = new Plot(plotId, orgId, projectId, "A-1", areaSqft, "SQ_FT", areaSqft, BigDecimal.valueOf(4_200_000));
        plotRepository.saveAndFlush(plot);

        return plotId;
    }
}
