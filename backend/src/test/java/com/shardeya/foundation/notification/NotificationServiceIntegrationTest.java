package com.shardeya.foundation.notification;

import com.shardeya.foundation.auth.Organization;
import com.shardeya.foundation.auth.OrganizationRepository;
import com.shardeya.foundation.notification.dto.NotificationResponse;
import com.shardeya.platform.TestTenantContext;
import com.shardeya.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Regression coverage for a real bug reported by the user: the "Mark all
 * read" button on the notification bell silently did nothing.
 * NotificationService.markAllRead() called a repository @Modifying UPDATE
 * query with no @Transactional on the calling method -- Spring Data's
 * repository proxy defaults query methods to a read-only transaction unless
 * the caller already has a writable one open, so this failed every time with
 * InvalidDataAccessApiUsageException ("Executing an update/delete query").
 * RefreshTokenService.revokeFamilyOf() already established the correct
 * pattern for this exact class of bug elsewhere in the codebase.
 */
class NotificationServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private NotificationService notificationService;
    @Autowired
    private OrganizationRepository organizationRepository;
    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void clearTenant() {
        TestTenantContext.clear();
    }

    private UUID seedOrgWithUser() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TestTenantContext.bind(orgId, userId, "BUILDER", "BUILDER_ADMIN", Set.of());

        Organization org = new Organization(orgId, Organization.Type.BUILDER, "Notification Test Org", "Jaipur");
        organizationRepository.saveAndFlush(org);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UUID roleId = (UUID) entityManager.createNativeQuery(
                            "SELECT id FROM role WHERE code = 'BUILDER_ADMIN' AND org_id IS NULL")
                    .getSingleResult();
            entityManager.createNativeQuery(
                            "INSERT INTO app_user (id, org_id, full_name, mobile, role_id, is_owner) "
                                    + "VALUES (:id, :orgId, 'Notification Test User', :mobile, :roleId, true)")
                    .setParameter("id", userId)
                    .setParameter("orgId", orgId)
                    .setParameter("mobile", "9" + String.valueOf(System.nanoTime()).substring(0, 9))
                    .setParameter("roleId", roleId)
                    .executeUpdate();
        });
        return orgId;
    }

    @Test
    void markAllReadActuallyMarksEveryUnreadNotificationReadWithoutThrowing() {
        UUID orgId = seedOrgWithUser();
        notificationService.createForOrg(orgId, "PLOT_SOLD", "notification.plotSold", "notification.plotSoldBody",
                Map.of("plotNumber", "A-1"), "plot_sale", UUID.randomUUID());
        notificationService.createForOrg(orgId, "PAYMENT_RECORDED", "notification.paymentRecorded", "notification.paymentRecordedBody",
                Map.of("amount", "100000"), "payment_record", UUID.randomUUID());

        assertThat(notificationService.unreadCount()).isEqualTo(2);

        // This threw InvalidDataAccessApiUsageException before the fix --
        // the whole point of this test is that it must NOT throw.
        assertThatCode(() -> notificationService.markAllRead()).doesNotThrowAnyException();

        assertThat(notificationService.unreadCount()).isEqualTo(0);
        assertThat(notificationService.listMine(10)).allMatch(NotificationResponse::read);
    }

    @Test
    void markAllReadIsSafeToCallWithNoUnreadNotifications() {
        seedOrgWithUser();
        assertThat(notificationService.unreadCount()).isEqualTo(0);
        assertThatCode(() -> notificationService.markAllRead()).doesNotThrowAnyException();
    }
}
