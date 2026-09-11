package com.shardeya.foundation.notification;

import com.shardeya.foundation.auth.Organization;
import com.shardeya.foundation.auth.OrganizationRepository;
import com.shardeya.foundation.notification.dto.NotificationPreferenceRow;
import com.shardeya.foundation.notification.dto.NotificationPreferenceUpdateRequest;
import com.shardeya.platform.BadRequestException;
import com.shardeya.platform.TestTenantContext;
import com.shardeya.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression coverage for a real bug found live during the "Post-M7 --
 * Minimal Ops Visibility" verification pass: {@code update()} never
 * validated {@code typeCode} against the real {@code notification_type}
 * catalogue before saving, so an unknown code hit
 * {@code notification_preference}'s own FK constraint (V7_014) at INSERT
 * time -- an unhandled {@code DataIntegrityViolationException}, a genuine
 * 500, first caught by actually looking at the new /admin/errors view
 * against a live server, not by any pre-existing test (this service had
 * none at all before this).
 */
class NotificationPreferenceServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private NotificationPreferenceService service;
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

    // notification_preference.user_id has a real FK to app_user(id) (V7_014)
    // -- unlike AdminOpsServiceIntegrationTest's tables, a random UUID here
    // isn't enough; this needs the same real-app_user seed every other
    // notification-table test in this suite already uses.
    private UUID seedOrgWithUser() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TestTenantContext.bind(orgId, userId, "BUILDER", "BUILDER_ADMIN", Set.of("SETTINGS_MANAGE"));

        organizationRepository.saveAndFlush(new Organization(orgId, Organization.Type.BUILDER, "Notif Pref Test Org", "Jaipur"));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UUID roleId = (UUID) entityManager.createNativeQuery(
                            "SELECT id FROM role WHERE code = 'BUILDER_ADMIN' AND org_id IS NULL")
                    .getSingleResult();
            entityManager.createNativeQuery(
                            "INSERT INTO app_user (id, org_id, full_name, mobile, role_id, is_owner) "
                                    + "VALUES (:id, :orgId, 'Notif Pref Test User', :mobile, :roleId, true)")
                    .setParameter("id", userId)
                    .setParameter("orgId", orgId)
                    .setParameter("mobile", "9" + String.valueOf(System.nanoTime()).substring(0, 9))
                    .setParameter("roleId", roleId)
                    .executeUpdate();
        });
        return orgId;
    }

    @Test
    void updateWithAnUnknownTypeCodeThrowsACleanBadRequestInsteadOf500ing() {
        seedOrgWithUser();

        assertThatThrownBy(() -> service.update(List.of(
                new NotificationPreferenceUpdateRequest("THIS_TYPE_CODE_DOES_NOT_EXIST", true, false, false, false))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateWithARealTypeCodePersistsAndIsReflectedInTheMatrix() {
        seedOrgWithUser();

        service.update(List.of(new NotificationPreferenceUpdateRequest("FOLLOWUP_DUE", true, false, false, false)));

        List<NotificationPreferenceRow> rows = service.matrix();
        assertThat(rows).anyMatch(r -> r.typeCode().equals("FOLLOWUP_DUE") && !r.whatsapp());
    }
}
