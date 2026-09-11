package com.shardeya.foundation.auth;

import com.shardeya.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrganizationRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private OrganizationRepository organizationRepository;

    @Test
    @Transactional
    void savesAndReloadsWithNativePostgresEnums() {
        Organization org = new Organization(UUID.randomUUID(), Organization.Type.BUILDER, "Test Builders", "Pune");
        organizationRepository.saveAndFlush(org);

        Organization reloaded = organizationRepository.findById(org.getId()).orElseThrow();
        assertThat(reloaded.getType()).isEqualTo(Organization.Type.BUILDER);
        assertThat(reloaded.getStatus()).isEqualTo(Organization.Status.ACTIVE);
        assertThat(reloaded.getName()).isEqualTo("Test Builders");
    }
}
