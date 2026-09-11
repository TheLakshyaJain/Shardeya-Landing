package com.shardeya.foundation.customer;

import com.shardeya.builder.tracker.TrackerService;
import com.shardeya.foundation.auth.Organization;
import com.shardeya.foundation.auth.OrganizationRepository;
import com.shardeya.foundation.customer.dto.CustomerCreateRequest;
import com.shardeya.foundation.customer.dto.CustomerResponse;
import com.shardeya.platform.TestTenantContext;
import com.shardeya.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real bug, found on a live account ("Mark Done on the Tracker tab isn't
 * working"): {@link InteractionService#create} only ever flipped
 * {@code customer.noFurtherFollowUp} when {@code nextFollowUpDate != null}
 * -- but {@link TrackerService#markDone} sends {@code result=NO_FURTHER}
 * with {@code nextFollowUpDate=null} (there's no new date when a follow-up
 * is genuinely done). The interaction logged correctly every time; the
 * customer's own follow-up state just never changed, so the exact same
 * lead reappeared in the follow-ups list immediately after the mutation's
 * own refetch -- indistinguishable from the button silently doing nothing.
 */
class InteractionServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CustomerService customerService;
    @Autowired
    private TrackerService trackerService;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID userId;

    @AfterEach
    void clearTenant() {
        TestTenantContext.clear();
    }

    @Test
    void markDoneClearsNoFurtherFollowUpAndRemovesTheLeadFromTheFollowUpsList() {
        UUID orgId = seedOrgAndUser();
        CustomerResponse customer = customerService.create(new CustomerCreateRequest(
                "Mark Done Test Lead", "9876543219", null, null,
                BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(2_000_000), null, null, null,
                Customer.Source.WALK_IN, null, null, null, null,
                LocalDate.now().minusDays(1), null, null, userId, false));

        assertThat(trackerService.followUps("all", null, null, null, 50).items())
                .anyMatch(r -> r.customerId().equals(customer.id()));

        trackerService.markDone(customer.id(), "Not interested anymore");

        Customer reloaded = customerRepository.findById(customer.id()).orElseThrow();
        assertThat(reloaded.isNoFurtherFollowUp()).isTrue();

        assertThat(trackerService.followUps("all", null, null, null, 50).items())
                .noneMatch(r -> r.customerId().equals(customer.id()));
    }

    private UUID seedOrgAndUser() {
        UUID orgId = UUID.randomUUID();
        userId = UUID.randomUUID();
        TestTenantContext.bind(orgId, userId, "BUILDER", "BUILDER_ADMIN", Set.of("DATA_CREATE", "DATA_VIEW_ALL", "DATA_EDIT_ALL"));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Organization org = new Organization(orgId, Organization.Type.BUILDER, "Mark Done Test Org", "Jaipur");
            organizationRepository.saveAndFlush(org);

            UUID roleId = (UUID) entityManager.createNativeQuery(
                            "SELECT id FROM role WHERE code = 'BUILDER_ADMIN' AND org_id IS NULL")
                    .getSingleResult();
            entityManager.createNativeQuery(
                            "INSERT INTO app_user (id, org_id, full_name, mobile, role_id, is_owner) "
                                    + "VALUES (:id, :orgId, 'Mark Done Test User', :mobile, :roleId, true)")
                    .setParameter("id", userId).setParameter("orgId", orgId)
                    .setParameter("mobile", "9" + String.valueOf(System.nanoTime()).substring(0, 9))
                    .setParameter("roleId", roleId).executeUpdate();
        });
        return orgId;
    }
}
