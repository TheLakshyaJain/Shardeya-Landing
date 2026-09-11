package com.shardeya.platform;

import com.shardeya.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionAspectTest extends AbstractIntegrationTest {

    @Autowired
    private PermissionGuardedFixture guarded;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void allowsCallWhenPermissionPresent() {
        TenantContext.set(new TenantContext.Tenant(
                UUID.randomUUID(), UUID.randomUUID(), "BUILDER", "BUILDER_ADMIN",
                Set.of("TEST_PERMISSION"), List.of(), true));

        assertThat(guarded.protectedAction()).isEqualTo("ok");
    }

    @Test
    void rejectsCallWhenPermissionMissing() {
        TenantContext.set(new TenantContext.Tenant(
                UUID.randomUUID(), UUID.randomUUID(), "BROKER", "BROKER_OWNER",
                Set.of("SOME_OTHER_PERMISSION"), List.of(), true));

        assertThatThrownBy(() -> guarded.protectedAction())
                .isInstanceOf(ForbiddenException.class);
    }
}
