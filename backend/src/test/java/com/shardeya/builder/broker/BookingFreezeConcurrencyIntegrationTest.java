package com.shardeya.builder.broker;

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
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 06-BROKER-NETWORK-ENGINE.md build-order step 11 -- the sweep's own
 * explicit minimum requirement: "two simultaneous bookings under a shared
 * upline." U &lt;- {S1, S2}; S1 and S2 each sell a different plot in two
 * genuinely parallel {@code plotSaleService.create()} calls (the exact
 * same {@link CyclicBarrier}-synchronized, thread-local-{@code TenantContext}
 * pattern {@code DesignationPromotionConcurrencyIntegrationTest} already
 * established for completions).
 *
 * <p>Unlike the counts trigger (step 7) or the release trigger (this
 * step's own fix), {@link BookingCommissionService#freezeForSale} has no
 * aggregate-recompute-on-a-shared-row shape to race in the first place --
 * each sale's freeze only ever INSERTs brand-new rows scoped to its own
 * {@code plot_sale_id} (booking_commission's own unique constraint is
 * {@code (plot_sale_id, beneficiary_broker_id, commission_type)}, so U
 * correctly ends up with two independent rows, one per sale, never one
 * row two callers fight over), and it only ever READS each broker's
 * current rate (§1: "using each broker's CURRENT rate at THIS instant" --
 * a snapshot read, not an aggregate that needs to reflect a concurrent
 * peer's write). This test exists to PROVE that reasoning holds under real
 * contention, not to fix anything -- see CLAUDE.md's own step 11 write-up
 * for why this is a valid "no gap found, and here is why" result.
 */
class BookingFreezeConcurrencyIntegrationTest extends AbstractIntegrationTest {

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
    private BookingCommissionRepository bookingCommissionRepository;
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
    void twoBookingsUnderASharedUplineInParallelBothFreezeCorrectlyWithNoLostRowOrDeadlock() throws Exception {
        UUID plot1 = seedOrgProjectAndPlot(BigDecimal.valueOf(1000));
        UUID plot2 = seedSecondPlot();
        UUID uplineId = seedDesignationBroker("Upline", 200, null);
        UUID s1Id = seedDesignationBroker("Seller1", 160, uplineId);
        UUID s2Id = seedDesignationBroker("Seller2", 160, uplineId);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        UUID[] sale1Id = new UUID[1];
        UUID[] sale2Id = new UUID[1];
        try {
            Callable<Void> createSale1 = () -> {
                TestTenantContext.bind(orgId, userId, "BUILDER", "BUILDER_ADMIN", Set.of());
                try {
                    barrier.await(15, TimeUnit.SECONDS);
                    sale1Id[0] = plotSaleService.create(plot1, saleRequest(BigDecimal.valueOf(200_000), s1Id)).id();
                } finally {
                    TestTenantContext.clear();
                }
                return null;
            };
            Callable<Void> createSale2 = () -> {
                TestTenantContext.bind(orgId, userId, "BUILDER", "BUILDER_ADMIN", Set.of());
                try {
                    barrier.await(15, TimeUnit.SECONDS);
                    sale2Id[0] = plotSaleService.create(plot2, saleRequest(BigDecimal.valueOf(200_000), s2Id)).id();
                } finally {
                    TestTenantContext.clear();
                }
                return null;
            };

            Future<Void> f1 = executor.submit(createSale1);
            Future<Void> f2 = executor.submit(createSale2);
            f1.get(30, TimeUnit.SECONDS);
            f2.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        entityManager.clear();

        // Each seller has exactly their own SELLING_BROKER row, correct amount.
        BookingCommission s1Row = onlyRowFor(sale1Id[0], s1Id);
        assertThat(s1Row.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(160_000));
        BookingCommission s2Row = onlyRowFor(sale2Id[0], s2Id);
        assertThat(s2Row.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(160_000));

        // The upline gets TWO independent rows (one per sale, not one row
        // clobbered by the other) -- the actual property this test proves:
        // no lost INSERT under real concurrent contention.
        List<BookingCommission> uplineRows = bookingCommissionRepository.findByOrgIdAndBeneficiaryBrokerIdOrderByCreatedAtDesc(orgId, uplineId);
        assertThat(uplineRows).hasSize(2);
        assertThat(uplineRows).allSatisfy(row -> assertThat(row.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(40_000)));
        assertThat(uplineRows).extracting(BookingCommission::getPlotSaleId).containsExactlyInAnyOrder(sale1Id[0], sale2Id[0]);
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
        BrokerPartner broker = new BrokerPartner(brokerId, orgId, name, TestMobiles.next(), BrokerPartner.CommissionType.DESIGNATION);
        broker.setUplineBrokerId(uplineBrokerId);
        DesignationSlab slab = designationSlabService.resolve(orgId, ratePerSqft == 160 ? 0 : ratePerSqft == 180 ? 1 : 2);
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

        Organization org = new Organization(orgId, Organization.Type.BUILDER, "Booking Freeze Concurrency Test Org", "Jaipur");
        organizationRepository.saveAndFlush(org);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UUID roleId = (UUID) entityManager.createNativeQuery(
                            "SELECT id FROM role WHERE code = 'BUILDER_ADMIN' AND org_id IS NULL")
                    .getSingleResult();
            entityManager.createNativeQuery(
                            "INSERT INTO app_user (id, org_id, full_name, mobile, role_id, is_owner) "
                                    + "VALUES (:id, :orgId, 'Booking Freeze Concurrency Test User', :mobile, :roleId, true)")
                    .setParameter("id", userId)
                    .setParameter("orgId", orgId)
                    .setParameter("mobile", TestMobiles.next())
                    .setParameter("roleId", roleId)
                    .executeUpdate();
        });

        Project project = new Project(projectId, orgId, "Booking Freeze Concurrency Test Project", Project.Type.RESIDENTIAL_PLOT_COLONY,
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
