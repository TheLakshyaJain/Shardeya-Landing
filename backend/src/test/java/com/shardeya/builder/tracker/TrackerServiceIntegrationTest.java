package com.shardeya.builder.tracker;

import com.shardeya.builder.payment.PaymentSchedule;
import com.shardeya.builder.payment.PaymentScheduleRepository;
import com.shardeya.builder.plot.Plot;
import com.shardeya.builder.plot.PlotRepository;
import com.shardeya.builder.project.Project;
import com.shardeya.builder.project.ProjectRepository;
import com.shardeya.builder.sale.PlotSale;
import com.shardeya.builder.sale.PlotSaleService;
import com.shardeya.builder.sale.dto.SaleCreateRequest;
import com.shardeya.builder.sale.dto.SaleResponse;
import com.shardeya.builder.sale.dto.ScheduleRowRequest;
import com.shardeya.builder.tracker.dto.CollectionRow;
import com.shardeya.foundation.auth.Organization;
import com.shardeya.foundation.auth.OrganizationRepository;
import com.shardeya.platform.ConflictException;
import com.shardeya.platform.CursorPage;
import com.shardeya.platform.OutboxEventRepository;
import com.shardeya.platform.TestTenantContext;
import com.shardeya.shared.IndianTime;
import com.shardeya.support.AbstractIntegrationTest;
import com.shardeya.support.TestMobiles;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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

/**
 * Regression coverage for the Tracker's own manual, buyer-facing "Send
 * Reminder" action being gated on an active WhatsApp opt-in -- this path
 * messages a genuine third party directly and never went through
 * NotificationDispatchService's own opt-in/allowlist machinery at all, so
 * it needed its own explicit guard (see TrackerService.remindInternal's own
 * comment). This is the first test coverage TrackerService has ever had.
 */
class TrackerServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TrackerService trackerService;
    @Autowired
    private PlotSaleService plotSaleService;
    @Autowired
    private PlotRepository plotRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private PaymentScheduleRepository scheduleRepository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;

    // outbox_event has no org_id column at all (a cross-tenant queue table,
    // never RLS-scoped) -- findAll() returns every event the whole suite has
    // ever created. A hardcoded buyer mobile shared across every test in
    // this class (or with PlotSaleIntegrationTest's own fixed "9876543210")
    // would make one test's real WhatsApp send look like it belongs to a
    // DIFFERENT test asserting "no send happened" -- each seed call below
    // gets its own, genuinely unique mobile as the only safe discriminator.
    private String buyerMobile;

    @AfterEach
    void clearTenant() {
        TestTenantContext.clear();
    }

    @Test
    void remindIsBlockedWithAClearConflictWhenTheBuyerHasNoActiveOptIn() {
        UUID scheduleId = seedSaleWithOneSchedule();

        assertThatThrownBy(() -> trackerService.remind(scheduleId, "WHATSAPP"))
                .isInstanceOf(ConflictException.class);

        assertThat(outboxEventRepository.findAll())
                .noneMatch(e -> e.getEventType().equals("WHATSAPP") && e.getPayload().contains(buyerMobile));
    }

    @Test
    void remindSucceedsOnceTheBuyerHasOptedIn() {
        UUID scheduleId = seedSaleWithOneSchedule();

        plotSaleService.setBuyerWhatsAppOptIn(saleIdFor(scheduleId), true);

        trackerService.remind(scheduleId, "WHATSAPP");

        assertThat(outboxEventRepository.findAll())
                .anyMatch(e -> e.getEventType().equals("WHATSAPP") && e.getPayload().contains(buyerMobile));
    }

    @Test
    void collectionsRowReflectsCurrentBuyerOptInStatus() {
        UUID scheduleId = seedSaleWithOneSchedule();

        CollectionRow before = onlyRow();
        assertThat(before.buyerOptedIn()).isFalse();

        plotSaleService.setBuyerWhatsAppOptIn(saleIdFor(scheduleId), true);

        CollectionRow after = onlyRow();
        assertThat(after.buyerOptedIn()).isTrue();
    }

    @Test
    void bulkRemindSkipsANotOptedInRowInsteadOfAbortingTheWholeBatch() {
        UUID scheduleId = seedSaleWithOneSchedule();

        TrackerService.BulkRemindResult result = trackerService.bulkRemind(List.of(scheduleId));
        assertThat(result.sent()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
    }

    private CollectionRow onlyRow() {
        CursorPage<CollectionRow> page = trackerService.collections(null, null, null, 25);
        assertThat(page.items()).hasSize(1);
        return page.items().get(0);
    }

    private UUID saleIdFor(UUID scheduleId) {
        return scheduleRepository.findByIdAndDeletedAtIsNull(scheduleId).orElseThrow().getPlotSaleId();
    }

    private UUID seedSaleWithOneSchedule() {
        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        TestTenantContext.bind(orgId, userId, "BUILDER", "BUILDER_ADMIN", Set.of("FINANCIAL_VIEW", "FINANCIAL_RECORD_PAYMENT"));

        organizationRepository.saveAndFlush(new Organization(orgId, Organization.Type.BUILDER, "Tracker Remind Test Org", "Jaipur"));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UUID roleId = (UUID) entityManager.createNativeQuery(
                            "SELECT id FROM role WHERE code = 'BUILDER_ADMIN' AND org_id IS NULL")
                    .getSingleResult();
            entityManager.createNativeQuery(
                            "INSERT INTO app_user (id, org_id, full_name, mobile, role_id, is_owner) "
                                    + "VALUES (:id, :orgId, 'Tracker Test User', :mobile, :roleId, true)")
                    .setParameter("id", userId).setParameter("orgId", orgId)
                    .setParameter("mobile", TestMobiles.next())
                    .setParameter("roleId", roleId)
                    .executeUpdate();
        });

        Project project = new Project(projectId, orgId, "Tracker Remind Test Project", Project.Type.RESIDENTIAL_PLOT_COLONY,
                "1 Test Rd", "Locality", "Jaipur", "RJ", BigDecimal.TEN, "ACRE", BigDecimal.valueOf(435600), 100);
        projectRepository.saveAndFlush(project);

        Plot plot = new Plot(plotId, orgId, projectId, "A-1", BigDecimal.valueOf(1200), "SQ_FT",
                BigDecimal.valueOf(1200), BigDecimal.valueOf(4_200_000));
        plotRepository.saveAndFlush(plot);

        buyerMobile = TestMobiles.next();
        LocalDate today = IndianTime.today();
        SaleResponse sale = plotSaleService.create(plotId, new SaleCreateRequest(null, "Rajesh Kumar", buyerMobile, null,
                null, null, null, today, BigDecimal.valueOf(4_200_000), null, null, null, null,
                PlotSale.PaymentType.LUMP_SUM,
                List.of(new ScheduleRowRequest("Full payment", BigDecimal.valueOf(4_200_000), today)),
                null));

        return scheduleRepository.findByPlotSaleIdAndDeletedAtIsNullOrderBySequenceNoAsc(sale.id()).get(0).getId();
    }
}
