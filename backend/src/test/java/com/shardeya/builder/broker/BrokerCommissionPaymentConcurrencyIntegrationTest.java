package com.shardeya.builder.broker;

import com.shardeya.builder.broker.dto.BrokerCommissionPaymentCreateRequest;
import com.shardeya.builder.plot.Plot;
import com.shardeya.builder.plot.PlotRepository;
import com.shardeya.builder.project.Project;
import com.shardeya.builder.project.ProjectRepository;
import com.shardeya.builder.sale.PlotSale;
import com.shardeya.builder.sale.PlotSaleService;
import com.shardeya.builder.sale.dto.SaleCreateRequest;
import com.shardeya.builder.sale.dto.SaleResponse;
import com.shardeya.builder.sale.dto.ScheduleRowRequest;
import com.shardeya.builder.payment.PaymentRecord;
import com.shardeya.builder.payment.PaymentService;
import com.shardeya.builder.payment.dto.PaymentCreateRequest;
import com.shardeya.foundation.auth.Organization;
import com.shardeya.foundation.auth.OrganizationRepository;
import com.shardeya.platform.BadRequestException;
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
 * 06-BROKER-NETWORK-ENGINE.md §8a/§14 -- "two simultaneous payouts against
 * the same broker must not double-pay past Due," this feature's own
 * explicit concurrency requirement, mirroring
 * DesignationPromotionConcurrencyIntegrationTest's exact threading
 * discipline (each worker thread gets its own DB connection and its own
 * independent {@code @Transactional} transaction; a {@link CyclicBarrier}
 * lines both up to start at essentially the same instant; {@code TenantContext}
 * is a plain non-inheritable ThreadLocal, so each worker binds its own
 * copy and clears it afterward).
 *
 * <p>The two requested amounts are deliberately chosen so that at most ONE
 * can ever succeed regardless of which thread the DB happens to serialize
 * first: a single booking frozen at 100,000 due, two threads each
 * requesting 60,000 -- whichever runs first succeeds and leaves exactly
 * 40,000 due, so the second's own 60,000 request always exceeds the
 * (correctly reduced) remaining due and is rejected. This is what proves
 * {@link BookingCommissionRepository#lockForPayoutOldestFirst}'s
 * PESSIMISTIC_WRITE lock genuinely serializes the two attempts rather than
 * letting both read a stale, pre-payout "100,000 due" and both succeed --
 * the exact double-pay-past-Due bug this test exists to rule out.
 */
class BrokerCommissionPaymentConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PlotSaleService plotSaleService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private BrokerCommissionPaymentService brokerCommissionPaymentService;
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
    private BrokerCommissionPaymentRepository paymentRepository;
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
    void twoSimultaneousPayoutsAgainstTheSameBrokerNeverDoublePayPastDue() throws Exception {
        UUID plotId = seedOrgProjectAndPlot(BigDecimal.valueOf(625)); // 625 sqft x 160/sqft = 100,000 frozen
        UUID bId = seedDesignationBroker("B");
        SaleResponse sale = plotSaleService.create(plotId, saleRequest(BigDecimal.valueOf(200_000), bId));
        paymentService.record(sale.id(), new PaymentCreateRequest(BigDecimal.valueOf(200_000), IndianTime.today(),
                PaymentRecord.Mode.BANK_TRANSFER, "NEFT-FULL", null, null)); // fully released: 100,000 due

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        boolean[] succeeded = new boolean[2];
        Exception[] failures = new Exception[2];
        try {
            Future<Void> f1 = executor.submit(payoutJob(barrier, bId, 0, succeeded, failures));
            Future<Void> f2 = executor.submit(payoutJob(barrier, bId, 1, succeeded, failures));
            f1.get(30, TimeUnit.SECONDS);
            f2.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        // Exactly one of the two genuinely succeeded; the other was
        // correctly rejected as exceeding the (by-then-reduced) Due --
        // never both succeeding (which would mean 120,000 paid against a
        // 100,000 total, a real double-pay-past-Due), and never both
        // failing (which would mean the lock never let either through).
        assertThat(succeeded[0] ^ succeeded[1]).isTrue();
        int winner = succeeded[0] ? 0 : 1;
        int loser = 1 - winner;
        assertThat(failures[winner]).isNull();
        assertThat(failures[loser]).isInstanceOf(BadRequestException.class);

        entityManager.clear();
        BookingCommission row = bookingCommissionRepository.findByPlotSaleId(sale.id()).get(0);
        assertThat(row.getPaidAmount()).isEqualByComparingTo(BigDecimal.valueOf(60_000)); // exactly one payout's worth, never 120,000
        assertThat(row.getPendingAmount()).isEqualByComparingTo(BigDecimal.valueOf(40_000));

        // Exactly one broker_commission_payment row was ever actually
        // persisted -- the loser's rejected attempt never wrote anything.
        assertThat(paymentRepository.findByOrgIdAndBeneficiaryBrokerIdOrderByCreatedAtDesc(orgId, bId)).hasSize(1);
    }

    private Callable<Void> payoutJob(CyclicBarrier barrier, UUID brokerId, int slot, boolean[] succeeded, Exception[] failures) {
        return () -> {
            TestTenantContext.bind(orgId, userId, "BUILDER", "BUILDER_ADMIN", Set.of());
            try {
                barrier.await(15, TimeUnit.SECONDS);
                brokerCommissionPaymentService.record(brokerId, new BrokerCommissionPaymentCreateRequest(
                        BigDecimal.valueOf(60_000), IndianTime.today(), BrokerCommissionPayment.Mode.CASH, null,
                        "Concurrent payout attempt " + slot));
                succeeded[slot] = true;
            } catch (Exception e) {
                failures[slot] = e;
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

    private UUID seedDesignationBroker(String name) {
        UUID brokerId = UUID.randomUUID();
        BrokerPartner broker = new BrokerPartner(brokerId, orgId, name, TestMobiles.next(), BrokerPartner.CommissionType.DESIGNATION);
        DesignationSlab slab = designationSlabService.resolve(orgId, 0); // Business Executive, 160/sqft
        broker.setCurrentDesignationId(slab.getId());
        broker.setCurrentCommissionRate(slab.getRatePerSqft());
        brokerRepository.saveAndFlush(broker);
        brokerNetworkService.attachNewBroker(orgId, brokerId, null);
        return brokerId;
    }

    private UUID seedOrgProjectAndPlot(BigDecimal areaSqft) {
        orgId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
        userId = UUID.randomUUID();

        TestTenantContext.bind(orgId, userId, "BUILDER", "BUILDER_ADMIN", Set.of());

        Organization org = new Organization(orgId, Organization.Type.BUILDER, "Broker Payout Concurrency Test Org", "Jaipur");
        organizationRepository.saveAndFlush(org);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UUID roleId = (UUID) entityManager.createNativeQuery(
                            "SELECT id FROM role WHERE code = 'BUILDER_ADMIN' AND org_id IS NULL")
                    .getSingleResult();
            entityManager.createNativeQuery(
                            "INSERT INTO app_user (id, org_id, full_name, mobile, role_id, is_owner) "
                                    + "VALUES (:id, :orgId, 'Broker Payout Concurrency Test User', :mobile, :roleId, true)")
                    .setParameter("id", userId)
                    .setParameter("orgId", orgId)
                    .setParameter("mobile", TestMobiles.next())
                    .setParameter("roleId", roleId)
                    .executeUpdate();
        });

        Project project = new Project(projectId, orgId, "Broker Payout Concurrency Test Project", Project.Type.RESIDENTIAL_PLOT_COLONY,
                "1 Test Rd", "Locality", "Jaipur", "RJ", BigDecimal.TEN, "ACRE", BigDecimal.valueOf(435600), 100);
        projectRepository.saveAndFlush(project);

        Plot plot = new Plot(plotId, orgId, projectId, "A-1", areaSqft, "SQ_FT", areaSqft, BigDecimal.valueOf(4_200_000));
        plotRepository.saveAndFlush(plot);

        return plotId;
    }
}
