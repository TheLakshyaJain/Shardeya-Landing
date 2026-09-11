package com.shardeya.builder.broker;

import com.shardeya.builder.broker.dto.BookingCommissionResponse;
import com.shardeya.builder.broker.dto.BrokerCommissionPaymentCreateRequest;
import com.shardeya.builder.broker.dto.BrokerCommissionPaymentResponse;
import com.shardeya.builder.broker.dto.BrokerCommissionPaymentReverseRequest;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 06-BROKER-NETWORK-ENGINE.md §8a -- Paid, the third money state, and the
 * "Record Payment" action that creates it. Reuses B-05's exact
 * oldest-first allocation shape and M6/B-05's exact immutable-payment
 * reversal discipline, applied to a new table
 * (broker_commission_payment/broker_commission_payment_allocation) --
 * these tests exercise the ONE genuinely new piece of logic this feature
 * adds: the hard payout cap and its interaction with multi-booking
 * oldest-first spreading. Seeding follows BookingCommissionIntegrationTest's
 * own exact precedent (seed org/project/plot/brokers directly via
 * repositories, not through the quota-gated services -- a fresh test org's
 * FREE plan has BUILDER_BROKERS=0).
 */
class BrokerCommissionPaymentServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PlotSaleService plotSaleService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private BrokerCommissionPaymentService brokerCommissionPaymentService;
    @Autowired
    private BookingCommissionService bookingCommissionService;
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
    private UUID projectId;

    @AfterEach
    void clearTenant() {
        TestTenantContext.clear();
    }

    // The scenario that's the whole reason this feature exists: a broker
    // beneficiary of TWO separate bookings, each fully released (customer
    // paid in full), pays a single amount that spans both -- must fill the
    // OLDER booking's due completely before touching the newer one.
    //
    // The two bookings are deliberately NOT the same size: booking sale1
    // itself promotes B from Business Executive (160/sqft, 0 team sales)
    // to Senior Business Executive (180/sqft, 1 team sale) the instant
    // it's created (the M6.5 booking-triggers-promotion behaviour), so
    // sale2 -- booked afterward -- correctly freezes at the NEW rate. This
    // is real, expected, correct behaviour (proven directly in
    // DesignationPromotionIntegrationTest), not an artifact of this test --
    // and it makes this a strictly more realistic proof that oldest-first
    // spreading works correctly across differently-sized frozen amounts,
    // not just identical ones.
    @Test
    void paymentSpanningTwoBookingsFillsTheOlderOneFirstThenSpillsIntoTheNewer() {
        seedOrgAndProject();
        UUID bId = seedDesignationBroker("B", null);

        UUID plot1 = seedPlot(BigDecimal.valueOf(500), "A-1");
        SaleResponse sale1 = plotSaleService.create(plot1, saleRequest(BigDecimal.valueOf(100_000), bId)); // 500 sqft x 160 = 80,000 frozen; this booking promotes B to 180/sqft
        payInFull(sale1.id(), BigDecimal.valueOf(100_000));

        UUID plot2 = seedPlot(BigDecimal.valueOf(500), "A-2");
        SaleResponse sale2 = plotSaleService.create(plot2, saleRequest(BigDecimal.valueOf(100_000), bId)); // 500 sqft x 180 (post-promotion) = 90,000 frozen
        payInFull(sale2.id(), BigDecimal.valueOf(100_000));

        BookingCommission row1 = onlyRowFor(sale1.id());
        BookingCommission row2 = onlyRowFor(sale2.id());
        assertThat(row1.getReleasedAmount()).isEqualByComparingTo(BigDecimal.valueOf(80_000));
        assertThat(row2.getReleasedAmount()).isEqualByComparingTo(BigDecimal.valueOf(90_000));
        // Total due across both: 170,000.

        BrokerCommissionPaymentResponse payment = brokerCommissionPaymentService.record(bId,
                new BrokerCommissionPaymentCreateRequest(BigDecimal.valueOf(100_000), IndianTime.today(),
                        BrokerCommissionPayment.Mode.CASH, null, "Partial payout spanning two bookings"));

        entityManager.clear();
        BookingCommission refreshed1 = bookingCommissionRepository.findById(row1.getId()).orElseThrow();
        BookingCommission refreshed2 = bookingCommissionRepository.findById(row2.getId()).orElseThrow();
        // The older booking (sale1) is filled completely first: its full
        // 80,000 due is paid off, leaving 0 due there.
        assertThat(refreshed1.getPaidAmount()).isEqualByComparingTo(BigDecimal.valueOf(80_000));
        assertThat(refreshed1.getPendingAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        // The remaining 20,000 spills into the newer booking (sale2),
        // leaving 70,000 still due there (90,000 - 20,000).
        assertThat(refreshed2.getPaidAmount()).isEqualByComparingTo(BigDecimal.valueOf(20_000));
        assertThat(refreshed2.getPendingAmount()).isEqualByComparingTo(BigDecimal.valueOf(70_000));

        assertThat(payment.amount()).isEqualByComparingTo(BigDecimal.valueOf(100_000));
    }

    @Test
    void payingMoreThanCommissionDueIsRejectedOutrightNoConfirmFlag() {
        seedOrgAndProject();
        UUID bId = seedDesignationBroker("B", null);
        UUID plot1 = seedPlot(BigDecimal.valueOf(500), "A-1");
        SaleResponse sale1 = plotSaleService.create(plot1, saleRequest(BigDecimal.valueOf(100_000), bId));
        payInFull(sale1.id(), BigDecimal.valueOf(100_000)); // 80,000 due, fully released

        assertThatThrownBy(() -> brokerCommissionPaymentService.record(bId,
                new BrokerCommissionPaymentCreateRequest(BigDecimal.valueOf(80_000.01).setScale(2, RoundingMode.HALF_UP),
                        IndianTime.today(), BrokerCommissionPayment.Mode.CASH, null, null)))
                .isInstanceOf(BadRequestException.class);

        // Nothing was paid or allocated by the rejected attempt.
        entityManager.clear();
        BookingCommission row = onlyRowFor(sale1.id());
        assertThat(row.getPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(paymentRepository.findByOrgIdAndBeneficiaryBrokerIdOrderByCreatedAtDesc(orgId, bId)).isEmpty();
    }

    @Test
    void reversingAPaymentRestoresCommissionDueExactly() {
        seedOrgAndProject();
        UUID bId = seedDesignationBroker("B", null);
        UUID plot1 = seedPlot(BigDecimal.valueOf(500), "A-1");
        SaleResponse sale1 = plotSaleService.create(plot1, saleRequest(BigDecimal.valueOf(100_000), bId));
        payInFull(sale1.id(), BigDecimal.valueOf(100_000)); // 80,000 due

        BrokerCommissionPaymentResponse payment = brokerCommissionPaymentService.record(bId,
                new BrokerCommissionPaymentCreateRequest(BigDecimal.valueOf(50_000), IndianTime.today(),
                        BrokerCommissionPayment.Mode.CASH, null, null));
        entityManager.clear();
        assertThat(onlyRowFor(sale1.id()).getPendingAmount()).isEqualByComparingTo(BigDecimal.valueOf(30_000));

        BrokerCommissionPaymentResponse reversal = brokerCommissionPaymentService.reverse(payment.id(),
                new BrokerCommissionPaymentReverseRequest("Recorded against the wrong broker by mistake"));
        assertThat(reversal.amount()).isEqualByComparingTo(BigDecimal.valueOf(-50_000));
        assertThat(reversal.reversesPaymentId()).isEqualTo(payment.id());

        entityManager.clear();
        BookingCommission row = onlyRowFor(sale1.id());
        assertThat(row.getPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(row.getPendingAmount()).isEqualByComparingTo(BigDecimal.valueOf(80_000)); // Due fully restored

        // Guards mirror PaymentService.doReverse() exactly: can't reverse twice, can't reverse a reversal.
        assertThatThrownBy(() -> brokerCommissionPaymentService.reverse(payment.id(),
                new BrokerCommissionPaymentReverseRequest("Trying to reverse the same payment again")))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> brokerCommissionPaymentService.reverse(reversal.id(),
                new BrokerCommissionPaymentReverseRequest("Trying to reverse a reversal")))
                .isInstanceOf(BadRequestException.class);
    }

    // §9/§8a's own "strongest recovery case": a booking that was actually
    // PAID OUT, then cancelled -- the recovery must track the real PAID
    // amount, not released-minus-paid (that formula, and the bug it had,
    // are covered directly in CommissionReversalCalculatorTest; this test
    // proves the whole stack wires it correctly end to end).
    @Test
    void cancellingAFullyPaidBookingTracksThePaidAmountAsRecoveryNotReleasedMinusPaid() {
        seedOrgAndProject();
        UUID bId = seedDesignationBroker("B", null);
        UUID plot1 = seedPlot(BigDecimal.valueOf(500), "A-1");
        SaleResponse sale1 = plotSaleService.create(plot1, saleRequest(BigDecimal.valueOf(100_000), bId));
        payInFull(sale1.id(), BigDecimal.valueOf(100_000)); // 80,000 released

        brokerCommissionPaymentService.record(bId, new BrokerCommissionPaymentCreateRequest(
                BigDecimal.valueOf(80_000), IndianTime.today(), BrokerCommissionPayment.Mode.CASH, null, null)); // fully paid out
        entityManager.clear();
        assertThat(onlyRowFor(sale1.id()).getPendingAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        plotSaleService.cancel(sale1.id(), new CancelSaleRequest("Buyer defaulted after the broker was already paid", null));

        entityManager.clear();
        BookingCommission cancelled = onlyRowFor(sale1.id());
        assertThat(cancelled.getStatus()).isEqualTo(BookingCommission.Status.CANCELLED);
        assertThat(cancelled.getPaidAmount()).isEqualByComparingTo(BigDecimal.valueOf(80_000)); // historical fact, never re-zeroed

        BookingCommissionResponse response = onlyResponseFor(bId);
        assertThat(response.needsRecovery()).isTrue();
        assertThat(response.recoveryAmount()).isEqualByComparingTo(BigDecimal.valueOf(80_000)); // exactly what was paid out, not released-minus-paid (which would be 0 here)
    }

    // The counterpart to the test above: released-but-never-paid needs no
    // recovery at all once cancelled -- nothing left the builder's hand.
    @Test
    void cancellingAReleasedButNeverPaidBookingNeedsNoRecovery() {
        seedOrgAndProject();
        UUID bId = seedDesignationBroker("B", null);
        UUID plot1 = seedPlot(BigDecimal.valueOf(500), "A-1");
        SaleResponse sale1 = plotSaleService.create(plot1, saleRequest(BigDecimal.valueOf(100_000), bId));
        payInFull(sale1.id(), BigDecimal.valueOf(100_000)); // 80,000 released, never paid out to the broker

        plotSaleService.cancel(sale1.id(), new CancelSaleRequest("Buyer backed out before any broker payout", null));

        BookingCommissionResponse response = onlyResponseFor(bId);
        assertThat(response.needsRecovery()).isFalse();
        assertThat(response.recoveryAmount()).isNull();
    }

    private void payInFull(UUID saleId, BigDecimal amount) {
        paymentService.record(saleId, new PaymentCreateRequest(amount, IndianTime.today(),
                PaymentRecord.Mode.BANK_TRANSFER, "NEFT-" + UUID.randomUUID(), null, null));
    }

    private BookingCommission onlyRowFor(UUID saleId) {
        List<BookingCommission> rows = bookingCommissionRepository.findByPlotSaleId(saleId);
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private BookingCommissionResponse onlyResponseFor(UUID brokerId) {
        List<BookingCommissionResponse> rows = bookingCommissionService.listForBeneficiary(brokerId);
        assertThat(rows).hasSize(1);
        return rows.get(0);
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
        DesignationSlab slab = designationSlabService.resolve(orgId, 0); // Business Executive, 160/sqft
        broker.setCurrentDesignationId(slab.getId());
        broker.setCurrentCommissionRate(slab.getRatePerSqft());
        brokerRepository.saveAndFlush(broker);
        brokerNetworkService.attachNewBroker(orgId, brokerId, uplineBrokerId);
        return brokerId;
    }

    private UUID seedPlot(BigDecimal areaSqft, String plotNumber) {
        UUID plotId = UUID.randomUUID();
        Plot plot = new Plot(plotId, orgId, projectId, plotNumber, areaSqft, "SQ_FT", areaSqft, BigDecimal.valueOf(4_200_000));
        plotRepository.saveAndFlush(plot);
        return plotId;
    }

    private void seedOrgAndProject() {
        orgId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        TestTenantContext.bind(orgId, userId, "BUILDER", "BUILDER_ADMIN", Set.of());

        Organization org = new Organization(orgId, Organization.Type.BUILDER, "Broker Commission Payment Test Org", "Jaipur");
        organizationRepository.saveAndFlush(org);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UUID roleId = (UUID) entityManager.createNativeQuery(
                            "SELECT id FROM role WHERE code = 'BUILDER_ADMIN' AND org_id IS NULL")
                    .getSingleResult();
            entityManager.createNativeQuery(
                            "INSERT INTO app_user (id, org_id, full_name, mobile, role_id, is_owner) "
                                    + "VALUES (:id, :orgId, 'Broker Commission Payment Test User', :mobile, :roleId, true)")
                    .setParameter("id", userId)
                    .setParameter("orgId", orgId)
                    .setParameter("mobile", TestMobiles.next())
                    .setParameter("roleId", roleId)
                    .executeUpdate();
        });

        Project project = new Project(projectId, orgId, "Broker Commission Payment Test Project", Project.Type.RESIDENTIAL_PLOT_COLONY,
                "1 Test Rd", "Locality", "Jaipur", "RJ", BigDecimal.TEN, "ACRE", BigDecimal.valueOf(435600), 100);
        projectRepository.saveAndFlush(project);
    }
}
