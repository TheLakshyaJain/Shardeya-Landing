package com.shardeya.builder.team;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shardeya.builder.team.dto.TeamMemberCreateRequest;
import com.shardeya.foundation.auth.AppUser;
import com.shardeya.foundation.auth.Organization;
import com.shardeya.foundation.auth.OrganizationRepository;
import com.shardeya.foundation.rbac.Role;
import com.shardeya.foundation.rbac.RoleRepository;
import com.shardeya.foundation.subscription.Subscription;
import com.shardeya.foundation.subscription.SubscriptionRepository;
import com.shardeya.platform.EmailPayload;
import com.shardeya.platform.OutboxEvent;
import com.shardeya.platform.OutboxEventRepository;
import com.shardeya.platform.TestTenantContext;
import com.shardeya.support.AbstractIntegrationTest;
import com.shardeya.support.TestMobiles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This service had zero test coverage of any kind before this class.
 * Written specifically to regression-guard the "invite link was a bare
 * relative path with no real host at all" bug -- see CLAUDE.md's
 * "Email/SMS invite and reset links were never real URLs" writeup.
 */
class TeamServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TeamService teamService;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private UUID orgId;
    private UUID ownerId;

    @AfterEach
    void clearTenant() {
        TestTenantContext.clear();
    }

    @Test
    void creatingATeamMemberWithSendInviteEnqueuesARealClickableLinkNotABarePath() throws Exception {
        seedOrgAndOwner();

        String invitedEmail = "invited-staffer-" + UUID.randomUUID() + "@example.com";
        TeamMemberCreateRequest req = new TeamMemberCreateRequest(
                "Invited Staffer", TestMobiles.next(), invitedEmail, "SALES_EXECUTIVE",
                null, true);
        teamService.create(req);

        // OutboxEvent has no getAggregateId()/getEventType() accessor pairing
        // convenient enough to filter by aggregate directly -- the invited
        // email's own uniqueness (fresh UUID per run) is what disambiguates
        // this event from any other EMAIL-type outbox row in the table.
        List<EmailPayload> emailsToInvitee = outboxEventRepository.findAll().stream()
                .filter(e -> "EMAIL".equals(e.getEventType()))
                .map(e -> readEmailPayload(e.getPayload()))
                .filter(p -> invitedEmail.equals(p.to()))
                .toList();
        assertThat(emailsToInvitee).as("exactly one invite email enqueued for the new staff member").hasSize(1);

        EmailPayload payload = emailsToInvitee.get(0);
        // The actual bug: this used to be the bare, hostless
        // "/accept-invite?token=..." with no scheme/host at all -- asserting
        // a real, absolute, clickable URL is what proves the fix, not just
        // "a token exists somewhere in the message."
        assertThat(payload.body()).containsPattern("https?://[^\\s]+/accept-invite\\?token=[A-Za-z0-9_-]+");
        assertThat(payload.body()).doesNotContain("added to Shardeya. Set your password: /accept-invite");
    }

    private EmailPayload readEmailPayload(String json) {
        try {
            return objectMapper.readValue(json, EmailPayload.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void seedOrgAndOwner() {
        orgId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        TestTenantContext.bind(orgId, ownerId, "BUILDER", "BUILDER_ADMIN", Set.of());

        Organization org = new Organization(orgId, Organization.Type.BUILDER, "Team Invite Link Test Org", "Jaipur");
        organizationRepository.saveAndFlush(org);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Role role = roleRepository.findByCodeAndOrgIdIsNull("BUILDER_ADMIN").orElseThrow();
            AppUser owner = new AppUser(ownerId, orgId, "Org Owner", TestMobiles.next(), role, true);
            owner.setStatus(AppUser.Status.ACTIVE);
            entityManager.persist(owner);

            // FREE's real BUILDER_TEAM_MEMBERS limit is 1, already consumed by
            // the owner itself (the usage trigger fires on this INSERT above
            // just like it would for a real signup) -- a throwaway,
            // high-limit plan for this test only, never touching the shared
            // FREE row, matching this project's own established precedent
            // for unblocking quota-limited test setup.
            entityManager.createNativeQuery(
                            "INSERT INTO plan (code, name_en, name_hi, price_monthly, price_yearly, sort_order, is_active) "
                                    + "VALUES ('TEAMTEST', 'Team Test', 'Team Test', 0, 0, 99, true) ON CONFLICT DO NOTHING")
                    .executeUpdate();
            entityManager.createNativeQuery(
                            "INSERT INTO plan_limit (plan_code, limit_key, limit_value) "
                                    + "VALUES ('TEAMTEST', 'BUILDER_TEAM_MEMBERS', '100') ON CONFLICT DO NOTHING")
                    .executeUpdate();

            Subscription subscription = new Subscription(UUID.randomUUID(), orgId, "TEAMTEST",
                    Subscription.Status.ACTIVE, Instant.now().plus(36500, ChronoUnit.DAYS));
            entityManager.persist(subscription);
        });
    }
}
