package com.shardeya.builder.broker;

import com.shardeya.builder.broker.dto.CommissionConfigCreateRequest;
import com.shardeya.builder.broker.dto.CommissionPaymentCreateRequest;
import com.shardeya.builder.broker.dto.CommissionPaymentResponse;
import com.shardeya.builder.broker.dto.CommissionPaymentReverseRequest;
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
import com.shardeya.platform.BadRequestException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** B-14 §20.5/§11 -- CommissionPaymentService's own validation branches: balance-exceeded confirmation, cancelled-entry block, and the reverse/double-reverse/reverse-of-reversal guards PaymentService already established for buyer payments. */
class CommissionPaymentServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PlotSaleService plotSaleService;
    @Autowired
    private CommissionPaymentService commissionPaymentService;
    @Autowired
    private CommissionConfigService commissionConfigService;
    @Autowired
    private CommissionLedgerEntryRepository ledgerRepository;
    @Autowired
    private BrokerPartnerRepository brokerRepository;
    @Autowired
    private PlotRepository plotRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
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
    void recordingAPartialThenFinalPaymentSettlesTheLedgerEntry() {
        UUID entryId = seedSaleWithLedgerEntry(BigDecimal.valueOf(100_000)); // 2% of 5,000,000

        CommissionPaymentResponse first = commissionPaymentService.record(entryId, new CommissionPaymentCreateRequest(
                BigDecimal.valueOf(40_000), IndianTime.today(), CommissionPayment.Mode.UPI, "UTR1", null, false));
        assertThat(first.amount()).isEqualByComparingTo(BigDecimal.valueOf(40_000));

        CommissionLedgerEntry afterFirst = ledgerRepository.findById(entryId).orElseThrow();
        assertThat(afterFirst.getStatus()).isEqualTo(CommissionLedgerEntry.Status.PARTIALLY_PAID);
        assertThat(afterFirst.getBalanceDue()).isEqualByComparingTo(BigDecimal.valueOf(60_000));

        commissionPaymentService.record(entryId, new CommissionPaymentCreateRequest(
                BigDecimal.valueOf(60_000), IndianTime.today(), CommissionPayment.Mode.BANK_TRANSFER, "NEFT2", null, false));

        CommissionLedgerEntry afterSecond = ledgerRepository.findById(entryId).orElseThrow();
        assertThat(afterSecond.getStatus()).isEqualTo(CommissionLedgerEntry.Status.PAID);
        assertThat(afterSecond.getBalanceDue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void aPaymentExceedingBalanceDueIsBlockedUnlessExplicitlyConfirmed() {
        UUID entryId = seedSaleWithLedgerEntry(BigDecimal.valueOf(100_000));

        assertThatThrownBy(() -> commissionPaymentService.record(entryId, new CommissionPaymentCreateRequest(
                BigDecimal.valueOf(150_000), IndianTime.today(), CommissionPayment.Mode.CASH, null, null, false)))
                .isInstanceOf(BadRequestException.class);

        CommissionLedgerEntry stillUnpaid = ledgerRepository.findById(entryId).orElseThrow();
        assertThat(stillUnpaid.getAmountPaid()).isEqualByComparingTo(BigDecimal.ZERO); // rejected attempt left no trace

        // Confirmed overpayment goes through.
        CommissionPaymentResponse confirmed = commissionPaymentService.record(entryId, new CommissionPaymentCreateRequest(
                BigDecimal.valueOf(150_000), IndianTime.today(), CommissionPayment.Mode.CASH, null, "Bonus included", true));
        assertThat(confirmed.amount()).isEqualByComparingTo(BigDecimal.valueOf(150_000));
    }

    @Test
    void reversingAPaymentCannotBeDoneTwiceAndAReversalCannotItselfBeReversed() {
        UUID entryId = seedSaleWithLedgerEntry(BigDecimal.valueOf(100_000));
        CommissionPaymentResponse payment = commissionPaymentService.record(entryId, new CommissionPaymentCreateRequest(
                BigDecimal.valueOf(50_000), IndianTime.today(), CommissionPayment.Mode.UPI, "UTR1", null, false));

        CommissionPaymentResponse reversal = commissionPaymentService.reverse(payment.id(), new CommissionPaymentReverseRequest("Recorded against the wrong broker"));
        assertThat(reversal.amount()).isEqualByComparingTo(BigDecimal.valueOf(-50_000));

        CommissionLedgerEntry afterReversal = ledgerRepository.findById(entryId).orElseThrow();
        assertThat(afterReversal.getAmountPaid()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(afterReversal.getStatus()).isEqualTo(CommissionLedgerEntry.Status.PENDING);

        assertThatThrownBy(() -> commissionPaymentService.reverse(payment.id(), new CommissionPaymentReverseRequest("Trying again")))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> commissionPaymentService.reverse(reversal.id(), new CommissionPaymentReverseRequest("Reversing the reversal")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void paymentsCannotBeRecordedAgainstACancelledLedgerEntry() {
        UUID plotId = seedOrgProjectAndPlot();
        UUID brokerId = seedBroker(BigDecimal.valueOf(2));
        commissionConfigService.create(brokerId, new CommissionConfigCreateRequest(
                BrokerCommissionConfig.Scope.GLOBAL, null, null, BrokerPartner.CommissionType.PERCENTAGE,
                BigDecimal.valueOf(2), LocalDate.now().minusDays(10), null));

        LocalDate today = IndianTime.today();
        SaleResponse sale = plotSaleService.create(plotId, new SaleCreateRequest(null, "Rajesh Kumar", "9876543210",
                null, null, null, null, today, BigDecimal.valueOf(5_000_000), brokerId, null, null, null,
                PlotSale.PaymentType.LUMP_SUM, List.of(new ScheduleRowRequest("Full payment", BigDecimal.valueOf(5_000_000), today)), null));
        UUID entryId = ledgerRepository.findByPlotSaleIdAndDeletedAtIsNull(sale.id()).orElseThrow().getId();

        plotSaleService.cancel(sale.id(), new com.shardeya.builder.sale.dto.CancelSaleRequest("Buyer backed out", null));

        assertThatThrownBy(() -> commissionPaymentService.record(entryId, new CommissionPaymentCreateRequest(
                BigDecimal.valueOf(1_000), IndianTime.today(), CommissionPayment.Mode.CASH, null, null, false)))
                .isInstanceOf(BadRequestException.class);
    }

    private UUID seedSaleWithLedgerEntry(BigDecimal expectedCommission) {
        UUID plotId = seedOrgProjectAndPlot();
        UUID brokerId = seedBroker(BigDecimal.valueOf(2));
        commissionConfigService.create(brokerId, new CommissionConfigCreateRequest(
                BrokerCommissionConfig.Scope.GLOBAL, null, null, BrokerPartner.CommissionType.PERCENTAGE,
                BigDecimal.valueOf(2), LocalDate.now().minusDays(10), null));

        LocalDate today = IndianTime.today();
        SaleResponse sale = plotSaleService.create(plotId, new SaleCreateRequest(null, "Rajesh Kumar", "9876543210",
                null, null, null, null, today, BigDecimal.valueOf(5_000_000), brokerId, null, null, null,
                PlotSale.PaymentType.LUMP_SUM, List.of(new ScheduleRowRequest("Full payment", BigDecimal.valueOf(5_000_000), today)), null));

        CommissionLedgerEntry entry = ledgerRepository.findByPlotSaleIdAndDeletedAtIsNull(sale.id()).orElseThrow();
        assertThat(entry.getBaseCommission()).isEqualByComparingTo(expectedCommission);
        return entry.getId();
    }

    private UUID seedBroker(BigDecimal defaultPct) {
        UUID brokerId = UUID.randomUUID();
        BrokerPartner broker = new BrokerPartner(brokerId, orgId, "Suresh Broker", "9988776655", BrokerPartner.CommissionType.PERCENTAGE);
        broker.setCommissionPct(defaultPct);
        brokerRepository.saveAndFlush(broker);
        return brokerId;
    }

    private UUID seedOrgProjectAndPlot() {
        orgId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        TestTenantContext.bind(orgId, userId, "BUILDER", "BUILDER_ADMIN", Set.of());

        Organization org = new Organization(orgId, Organization.Type.BUILDER, "Commission Payment Test Org", "Jaipur");
        organizationRepository.saveAndFlush(org);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UUID roleId = (UUID) entityManager.createNativeQuery(
                            "SELECT id FROM role WHERE code = 'BUILDER_ADMIN' AND org_id IS NULL")
                    .getSingleResult();
            entityManager.createNativeQuery(
                            "INSERT INTO app_user (id, org_id, full_name, mobile, role_id, is_owner) "
                                    + "VALUES (:id, :orgId, 'Commission Payment Test User', :mobile, :roleId, true)")
                    .setParameter("id", userId)
                    .setParameter("orgId", orgId)
                    .setParameter("mobile", "9" + String.valueOf(System.nanoTime()).substring(0, 9))
                    .setParameter("roleId", roleId)
                    .executeUpdate();
        });

        Project project = new Project(projectId, orgId, "Commission Payment Test Project", Project.Type.RESIDENTIAL_PLOT_COLONY,
                "1 Test Rd", "Locality", "Jaipur", "RJ", BigDecimal.TEN, "ACRE", BigDecimal.valueOf(435600), 100);
        projectRepository.saveAndFlush(project);

        Plot plot = new Plot(plotId, orgId, projectId, "A-1", BigDecimal.valueOf(1200), "SQ_FT",
                BigDecimal.valueOf(1200), BigDecimal.valueOf(5_000_000));
        plotRepository.saveAndFlush(plot);

        return plotId;
    }
}
