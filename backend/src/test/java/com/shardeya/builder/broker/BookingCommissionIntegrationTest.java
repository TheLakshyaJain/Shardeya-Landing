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
import com.shardeya.builder.sale.dto.CancelSaleRequest;
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
 * 06-BROKER-NETWORK-ENGINE.md §7/§8, build-order steps 5+6 -- the frozen
 * commission tree (booking_commission, created inside PlotSaleService.create())
 * and its proportional release on customer payments (commission_release,
 * created inside PaymentService.record()/doReverse()). Seeding follows the
 * exact same "seed brokers/org/project/plot directly via repositories, not
 * through the quota-gated services" precedent PlotSaleCommissionIntegrationTest
 * already established, for the same reason (a fresh test org's FREE plan
 * has BUILDER_BROKERS=0).
 */
class BookingCommissionIntegrationTest extends AbstractIntegrationTest {

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
    private CommissionReleaseRepository commissionReleaseRepository;
    @Autowired
    private CommissionLedgerEntryRepository ledgerRepository;
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

    // §16 fixture §45: 200/180/160 chain, 1000 sq.ft. -> B 160k, A 20k, Me 20k.
    @Test
    void threeLevelDesignationChainFreezesExactlyWhatThePureEngineComputes() {
        UUID plotId = seedOrgProjectAndPlot(BigDecimal.valueOf(1000));

        UUID meId = seedDesignationBroker("Me", 200, null);
        UUID aId = seedDesignationBroker("A", 180, meId);
        UUID bId = seedDesignationBroker("B", 160, aId);

        SaleResponse sale = plotSaleService.create(plotId, saleRequestWithBroker(BigDecimal.valueOf(4_000_000), bId));

        // A DESIGNATION broker never gets an M6 commission_ledger_entry --
        // the two commission worlds (value-based PERCENTAGE/FIXED vs
        // area-based DESIGNATION) never mix.
        assertThat(ledgerRepository.findByPlotSaleIdAndDeletedAtIsNull(sale.id())).isEmpty();

        List<BookingCommission> rows = bookingCommissionRepository.findByPlotSaleId(sale.id());
        assertThat(rows).hasSize(3);

        BookingCommission bRow = rowFor(rows, bId);
        assertThat(bRow.getUplineLevel()).isEqualTo((short) 0);
        assertThat(bRow.getCommissionType()).isEqualTo(BookingCommission.LineType.SELLING_BROKER);
        assertThat(bRow.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(160_000));

        BookingCommission aRow = rowFor(rows, aId);
        assertThat(aRow.getUplineLevel()).isEqualTo((short) 1);
        assertThat(aRow.getCommissionType()).isEqualTo(BookingCommission.LineType.UPLINE_DIFFERENTIAL);
        assertThat(aRow.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(20_000));

        BookingCommission meRow = rowFor(rows, meId);
        assertThat(meRow.getUplineLevel()).isEqualTo((short) 2);
        assertThat(meRow.getCommissionType()).isEqualTo(BookingCommission.LineType.UPLINE_DIFFERENTIAL);
        assertThat(meRow.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(20_000));

        // Nothing released yet -- no customer payment has happened.
        assertThat(rows).allSatisfy(r -> assertThat(r.getReleasedAmount()).isEqualByComparingTo(BigDecimal.ZERO));
        assertThat(rows).allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(BookingCommission.Status.PENDING));
    }

    @Test
    void releaseIsProportionalToCumulativePaymentAndReversalUnreleasesTheSameSlice() {
        UUID plotId = seedOrgProjectAndPlot(BigDecimal.valueOf(1000));
        UUID meId = seedDesignationBroker("Me", 200, null);
        UUID aId = seedDesignationBroker("A", 180, meId);
        UUID bId = seedDesignationBroker("B", 160, aId);

        SaleResponse sale = plotSaleService.create(plotId, saleRequestWithBroker(BigDecimal.valueOf(400_000), bId));
        // Totals frozen: B=160,000, A=20,000, Me=20,000.

        PaymentResponseHolder p1 = record(sale.id(), BigDecimal.valueOf(100_000)); // cumulative 25%
        assertReleased(sale.id(), bId, 40_000, aId, 5_000, meId, 5_000);

        record(sale.id(), BigDecimal.valueOf(100_000)); // cumulative 50%
        assertReleased(sale.id(), bId, 80_000, aId, 10_000, meId, 10_000);

        PaymentResponseHolder p3 = record(sale.id(), BigDecimal.valueOf(200_000)); // cumulative 100%
        assertReleased(sale.id(), bId, 160_000, aId, 20_000, meId, 20_000);
        List<BookingCommission> fullyReleased = bookingCommissionRepository.findByPlotSaleId(sale.id());
        assertThat(fullyReleased).allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(BookingCommission.Status.FULLY_RELEASED));

        // Reversing the last (200,000) payment must un-release the exact
        // matching slice -- no special-case reversal logic, just the delta
        // math reacting to sale.totalPaid dropping back to 200,000 (50%).
        paymentService.reverse(p3.paymentId(), new com.shardeya.builder.payment.dto.ReverseRequest("Buyer asked to undo this payment"));
        assertReleased(sale.id(), bId, 80_000, aId, 10_000, meId, 10_000);

        // Every release row (3 payments x 3 beneficiaries = 9 positive, plus
        // 3 negative reversal rows = 12) is linked to the real customer
        // payment_record that caused it.
        assertThat(commissionReleaseRepository.findAll()).hasSize(12);
        assertThat(commissionReleaseRepository.findAll()).noneMatch(r -> r.getAmount().signum() == 0);
        assertThat(commissionReleaseRepository.findAll()).anyMatch(r -> r.getPaymentRecordId().equals(p1.paymentId()));
    }

    @Test
    void waivedPortionOfADealIsNeverReleasedEvenThoughTheSaleCompletes() {
        UUID plotId = seedOrgProjectAndPlot(BigDecimal.valueOf(1000));
        UUID bId = seedDesignationBroker("B", 160, null); // top-level, no upline -- simplest shape for this scenario

        LocalDate today = IndianTime.today();
        SaleCreateRequest req = new SaleCreateRequest(null, "Waive Test Buyer", "9876500001", null, null, null, null,
                today, BigDecimal.valueOf(400_000), bId, null, null, null, PlotSale.PaymentType.INSTALMENT,
                List.of(new ScheduleRowRequest("Booking", BigDecimal.valueOf(100_000), today),
                        new ScheduleRowRequest("Balance", BigDecimal.valueOf(300_000), today.plusMonths(2))),
                null);
        SaleResponse sale = plotSaleService.create(plotId, req);
        // Total frozen for B (SELLING_BROKER, top-level so no uplines): 1000 x 160 = 160,000.

        List<PaymentSchedule> rows = scheduleRepository.findByPlotSaleIdAndDeletedAtIsNullOrderBySequenceNoAsc(sale.id());
        paymentService.record(sale.id(), new PaymentCreateRequest(BigDecimal.valueOf(100_000), today,
                PaymentRecord.Mode.BANK_TRANSFER, "NEFT-WAIVE-1", null, null)); // 25% of the deal actually paid
        scheduleService.waive(rows.get(1).getId(), new WaiveRequest("Builder is forgiving the balance for this buyer"));

        var summary = paymentService.summary(sale.id());
        assertThat(summary.balanceDue()).isEqualByComparingTo(BigDecimal.ZERO); // waiver zeroes the balance

        SaleResponse completed = plotSaleService.complete(sale.id()); // must not throw
        assertThat(completed.status()).isEqualTo("COMPLETED");

        // Only the money the customer actually paid (25%) was ever released
        // -- the waived 75% never generates a release, even though the sale
        // is COMPLETED and its balance reads zero.
        BookingCommission row = bookingCommissionRepository.findByPlotSaleId(sale.id()).get(0);
        assertThat(row.getReleasedAmount()).isEqualByComparingTo(BigDecimal.valueOf(40_000)); // 25% of 160,000
        assertThat(row.getStatus()).isEqualTo(BookingCommission.Status.PARTIALLY_RELEASED);
    }

    @Test
    void cancellingADesignationBrokerSaleRecoversTheReleasedAmountAndCancelsTheRow() {
        UUID plotId = seedOrgProjectAndPlot(BigDecimal.valueOf(1000));
        UUID bId = seedDesignationBroker("B", 160, null);

        SaleResponse sale = plotSaleService.create(plotId, saleRequestWithBroker(BigDecimal.valueOf(400_000), bId));
        record(sale.id(), BigDecimal.valueOf(100_000));
        assertReleased(sale.id(), bId, 40_000);

        // 06-BROKER-NETWORK-ENGINE.md §9, build-order step 9: cancelling a
        // DESIGNATION-broker sale now cancels its frozen booking_commission
        // rows and derives a real recovery figure from whatever had already
        // been released (never re-zeroed -- the exact M6
        // status+amount_paid recovery pattern, reused here against
        // released_amount). Full coverage of the sales-count-reversal/
        // designation-demotion half lives in DesignationPromotionIntegrationTest.
        SaleResponse cancelled = plotSaleService.cancel(sale.id(), new CancelSaleRequest("Buyer backed out", null));
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(plotRepository.findByIdAndDeletedAtIsNull(plotId).orElseThrow().getStatus()).isEqualTo(Plot.Status.AVAILABLE);

        BookingCommission row = bookingCommissionRepository.findByPlotSaleId(sale.id()).get(0);
        assertThat(row.getReleasedAmount()).isEqualByComparingTo(BigDecimal.valueOf(40_000)); // historical fact, never re-zeroed
        assertThat(row.getStatus()).isEqualTo(BookingCommission.Status.CANCELLED);
    }

    @Test
    void aPercentageBrokerSaleCreatesNoBookingCommissionRowsAndPaymentsCreateNoReleases() {
        UUID plotId = seedOrgProjectAndPlot(BigDecimal.valueOf(1000));
        UUID brokerId = UUID.randomUUID();
        BrokerPartner broker = new BrokerPartner(brokerId, orgId, "Percentage Broker", "9988877766", BrokerPartner.CommissionType.PERCENTAGE);
        broker.setCommissionPct(BigDecimal.valueOf(2));
        brokerRepository.saveAndFlush(broker);

        SaleResponse sale = plotSaleService.create(plotId, saleRequestWithBroker(BigDecimal.valueOf(400_000), brokerId));
        assertThat(bookingCommissionRepository.findByPlotSaleId(sale.id())).isEmpty();
        assertThat(ledgerRepository.findByPlotSaleIdAndDeletedAtIsNull(sale.id())).isPresent(); // the M6 path, unaffected

        record(sale.id(), BigDecimal.valueOf(400_000));
        assertThat(commissionReleaseRepository.findAll()).isEmpty(); // the release hook is a clean no-op with no booking_commission rows to release against
    }

    private record PaymentResponseHolder(UUID paymentId) {
    }

    private PaymentResponseHolder record(UUID saleId, BigDecimal amount) {
        var response = paymentService.record(saleId, new PaymentCreateRequest(amount, IndianTime.today(),
                PaymentRecord.Mode.BANK_TRANSFER, "NEFT-" + UUID.randomUUID(), null, null));
        return new PaymentResponseHolder(response.id());
    }

    private void assertReleased(UUID saleId, UUID onlyBrokerId, long expectedAmount) {
        entityManager.clear();
        BookingCommission row = bookingCommissionRepository.findByPlotSaleId(saleId).get(0);
        assertThat(row.getBeneficiaryBrokerId()).isEqualTo(onlyBrokerId);
        assertThat(row.getReleasedAmount()).isEqualByComparingTo(BigDecimal.valueOf(expectedAmount));
    }

    private void assertReleased(UUID saleId, UUID bId, long bAmount, UUID aId, long aAmount, UUID meId, long meAmount) {
        entityManager.clear(); // force a fresh read of the trigger-maintained released_amount, not a stale first-level-cached instance
        List<BookingCommission> rows = bookingCommissionRepository.findByPlotSaleId(saleId);
        assertThat(rowFor(rows, bId).getReleasedAmount()).isEqualByComparingTo(BigDecimal.valueOf(bAmount));
        assertThat(rowFor(rows, aId).getReleasedAmount()).isEqualByComparingTo(BigDecimal.valueOf(aAmount));
        assertThat(rowFor(rows, meId).getReleasedAmount()).isEqualByComparingTo(BigDecimal.valueOf(meAmount));
    }

    private BookingCommission rowFor(List<BookingCommission> rows, UUID brokerId) {
        return rows.stream().filter(r -> r.getBeneficiaryBrokerId().equals(brokerId)).findFirst().orElseThrow();
    }

    private SaleCreateRequest saleRequestWithBroker(BigDecimal dealValue, UUID brokerId) {
        LocalDate today = IndianTime.today();
        return new SaleCreateRequest(null, "Rajesh Kumar", "9876543210", null, null, null, null,
                today, dealValue, brokerId, null, null, null,
                PlotSale.PaymentType.INSTALMENT,
                List.of(new ScheduleRowRequest("Booking", dealValue, today)),
                null);
    }

    private UUID seedDesignationBroker(String name, int ratePerSqft, UUID uplineBrokerId) {
        UUID brokerId = UUID.randomUUID();
        BrokerPartner broker = new BrokerPartner(brokerId, orgId, name, com.shardeya.support.TestMobiles.next(),
                BrokerPartner.CommissionType.DESIGNATION);
        broker.setUplineBrokerId(uplineBrokerId);
        // Real seeded system-default slabs (V65_002) happen to match this
        // fixture's rates exactly (160/180/200 = Business Executive/Senior
        // Business Executive/Business Development Officer) -- resolved via
        // team-sales count 0/1/2 rather than hand-picking a slab id, so this
        // stays correct if the seed data ever changes shape.
        int teamSales = switch (ratePerSqft) {
            case 160 -> 0;
            case 180 -> 1;
            case 200 -> 2;
            default -> throw new IllegalArgumentException("No fixture slab for rate " + ratePerSqft);
        };
        DesignationSlab slab = designationSlabService.resolve(orgId, teamSales);
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

        Organization org = new Organization(orgId, Organization.Type.BUILDER, "Booking Commission Test Org", "Jaipur");
        organizationRepository.saveAndFlush(org);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UUID roleId = (UUID) entityManager.createNativeQuery(
                            "SELECT id FROM role WHERE code = 'BUILDER_ADMIN' AND org_id IS NULL")
                    .getSingleResult();
            entityManager.createNativeQuery(
                            "INSERT INTO app_user (id, org_id, full_name, mobile, role_id, is_owner) "
                                    + "VALUES (:id, :orgId, 'Booking Commission Test User', :mobile, :roleId, true)")
                    .setParameter("id", userId)
                    .setParameter("orgId", orgId)
                    .setParameter("mobile", com.shardeya.support.TestMobiles.next())
                    .setParameter("roleId", roleId)
                    .executeUpdate();
        });

        Project project = new Project(projectId, orgId, "Booking Commission Test Project", Project.Type.RESIDENTIAL_PLOT_COLONY,
                "1 Test Rd", "Locality", "Jaipur", "RJ", BigDecimal.TEN, "ACRE", BigDecimal.valueOf(435600), 100);
        projectRepository.saveAndFlush(project);

        Plot plot = new Plot(plotId, orgId, projectId, "A-1", areaSqft, "SQ_FT", areaSqft, BigDecimal.valueOf(4_200_000));
        plotRepository.saveAndFlush(plot);

        return plotId;
    }
}
