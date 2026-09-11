package com.shardeya.builder.broker;

import com.shardeya.builder.payment.PaymentRecord;
import com.shardeya.builder.payment.PaymentRecordRepository;
import com.shardeya.builder.plot.Plot;
import com.shardeya.builder.plot.PlotRepository;
import com.shardeya.builder.project.Project;
import com.shardeya.builder.project.ProjectRepository;
import com.shardeya.builder.sale.PlotSale;
import com.shardeya.builder.sale.PlotSaleRepository;
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
 * explicit minimum requirement: "two simultaneous releases on the same
 * booking." This is the regression test for a REAL bug this step's own
 * audit found and fixed (V65_012): {@code fn_booking_commission_release_trigger}
 * (V65_008) computed its {@code released_amount} recompute via a variable
 * assigned in one statement and applied via a separate, later UPDATE --
 * the EXACT class of lost-update bug step 7's counts trigger already hit
 * once, just never checked for here. Confirmed empirically first via a
 * raw two-session {@code psql} reproduction (matching step 7's own
 * discipline) before writing this test or touching the trigger: two
 * concurrent {@code commission_release} inserts against the same
 * {@code booking_commission_id} left {@code released_amount} at only the
 * SECOND transaction's own (stale) computed value, not the true sum of
 * both.
 *
 * <p>Deliberately bypasses {@code PaymentService.record()} for the
 * concurrent half of this test -- a real payment's own {@code plot_sale}
 * row-lock chain (the {@code total_paid} trigger) already, incidentally,
 * serializes two concurrent payments on the SAME sale end-to-end (see
 * CLAUDE.md's own step 11 write-up for the full reasoning), which would
 * mask exactly the race this test needs to exercise. Instead, two
 * {@code payment_record} rows are inserted directly (still correctly
 * driving {@code plot_sale.total_paid} via that same, unrelated,
 * already-safe trigger) and {@link CommissionReleaseService#releaseForPayment}
 * is called directly, twice, from two genuinely parallel transactions --
 * isolating the fix to exactly the mechanism it protects, not relying on
 * an unrelated table's lock to accidentally prevent the race from ever
 * occurring in the first place.
 */
class CommissionReleaseConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PlotSaleService plotSaleService;
    @Autowired
    private CommissionReleaseService commissionReleaseService;
    @Autowired
    private PlotSaleRepository plotSaleRepository;
    @Autowired
    private PaymentRecordRepository paymentRecordRepository;
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
    private CommissionReleaseRepository releaseRepository;
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
    void twoSimultaneousReleasesOnTheSameBookingConvergeToTheCorrectTotalWithNoLostUpdate() throws Exception {
        UUID plotId = seedOrgProjectAndPlot(BigDecimal.valueOf(1000));
        UUID sellerId = seedDesignationBroker("Seller", 160, null);

        SaleResponse sale = plotSaleService.create(plotId, saleRequest(BigDecimal.valueOf(160_000), sellerId));
        // total_amount for the one booking_commission row is 160*1000=160,000.

        // Bring the sale to fully paid via TWO direct payment_record inserts
        // (bypassing PaymentService.record() -- see this class's own
        // javadoc for why) so plot_sale.total_paid correctly reaches
        // 160,000 (its own trigger already proven safe, see CLAUDE.md),
        // while booking_commission.released_amount stays at 0 -- nothing
        // has released anything yet.
        UUID payment1Id = insertRawPayment(sale.id(), BigDecimal.valueOf(80_000));
        UUID payment2Id = insertRawPayment(sale.id(), BigDecimal.valueOf(80_000));

        entityManager.clear();
        PlotSale fullyPaidSale = plotSaleRepository.findByIdAndDeletedAtIsNull(sale.id()).orElseThrow();
        assertThat(fullyPaidSale.getTotalPaid()).isEqualByComparingTo(BigDecimal.valueOf(160_000));

        BookingCommission row = bookingCommissionRepository.findByPlotSaleId(sale.id()).get(0);
        assertThat(row.getReleasedAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        // Two genuinely parallel transactions, each independently computing
        // "the fraction paid is 100%, so the full 160,000 should be
        // released" and racing to insert that release -- exactly the shape
        // that lost an update before the V65_012 fix.
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Void> release1 = releaseJob(barrier, sale.id(), payment1Id);
            Callable<Void> release2 = releaseJob(barrier, sale.id(), payment2Id);

            Future<Void> f1 = executor.submit(release1);
            Future<Void> f2 = executor.submit(release2);
            f1.get(30, TimeUnit.SECONDS);
            f2.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        entityManager.clear();

        BookingCommission afterRace = bookingCommissionRepository.findByPlotSaleId(sale.id()).get(0);
        // The property that actually proves no lost update: released_amount
        // must equal the TRUE sum of every commission_release row for this
        // booking, and both must equal the correct target (160,000) -- not
        // whichever thread happened to commit last.
        BigDecimal trueSum = releaseRepository.sumReleasedFor(row.getId());
        assertThat(trueSum).isEqualByComparingTo(BigDecimal.valueOf(160_000));
        assertThat(afterRace.getReleasedAmount()).isEqualByComparingTo(BigDecimal.valueOf(160_000));
        assertThat(afterRace.getStatus()).isEqualTo(BookingCommission.Status.FULLY_RELEASED);
    }

    private Callable<Void> releaseJob(CyclicBarrier barrier, UUID saleId, UUID paymentId) {
        return () -> {
            TestTenantContext.bind(orgId, userId, "BUILDER", "BUILDER_ADMIN", Set.of());
            try {
                barrier.await(15, TimeUnit.SECONDS);
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    PlotSale sale = plotSaleRepository.findByIdAndDeletedAtIsNull(saleId).orElseThrow();
                    PaymentRecord payment = paymentRecordRepository.findById(paymentId).orElseThrow();
                    commissionReleaseService.releaseForPayment(sale, payment);
                });
            } finally {
                TestTenantContext.clear();
            }
            return null;
        };
    }

    private UUID insertRawPayment(UUID saleId, BigDecimal amount) {
        PlotSale sale = plotSaleRepository.findByIdAndDeletedAtIsNull(saleId).orElseThrow();
        UUID paymentId = UUID.randomUUID();
        PaymentRecord payment = new PaymentRecord(paymentId, orgId, saleId, sale.getProjectId(), sale.getPlotId(),
                "RAWTEST-" + paymentId.toString().substring(0, 20), amount, IndianTime.today(), PaymentRecord.Mode.CASH, null, userId);
        paymentRecordRepository.saveAndFlush(payment);
        return paymentId;
    }

    private SaleCreateRequest saleRequest(BigDecimal dealValue, UUID brokerId) {
        LocalDate today = IndianTime.today();
        return new SaleCreateRequest(null, "Rajesh Kumar", "9876543210", null, null, null, null,
                today, dealValue, brokerId, null, null, null, PlotSale.PaymentType.LUMP_SUM,
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

        Organization org = new Organization(orgId, Organization.Type.BUILDER, "Release Concurrency Test Org", "Jaipur");
        organizationRepository.saveAndFlush(org);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UUID roleId = (UUID) entityManager.createNativeQuery(
                            "SELECT id FROM role WHERE code = 'BUILDER_ADMIN' AND org_id IS NULL")
                    .getSingleResult();
            entityManager.createNativeQuery(
                            "INSERT INTO app_user (id, org_id, full_name, mobile, role_id, is_owner) "
                                    + "VALUES (:id, :orgId, 'Release Concurrency Test User', :mobile, :roleId, true)")
                    .setParameter("id", userId)
                    .setParameter("orgId", orgId)
                    .setParameter("mobile", TestMobiles.next())
                    .setParameter("roleId", roleId)
                    .executeUpdate();
        });

        Project project = new Project(projectId, orgId, "Release Concurrency Test Project", Project.Type.RESIDENTIAL_PLOT_COLONY,
                "1 Test Rd", "Locality", "Jaipur", "RJ", BigDecimal.TEN, "ACRE", BigDecimal.valueOf(435600), 100);
        projectRepository.saveAndFlush(project);

        Plot plot = new Plot(plotId, orgId, projectId, "A-1", areaSqft, "SQ_FT", areaSqft, BigDecimal.valueOf(4_200_000));
        plotRepository.saveAndFlush(plot);

        return plotId;
    }
}
