package com.shardeya.platform;

import com.shardeya.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for a JPQL-enum-literal bug: querying with the enum
 * inlined in the query string (rather than bound as a parameter) made
 * Hibernate cast to the Java enum's simple class name instead of the mapped
 * Postgres native enum type, failing at runtime with "type Status does not
 * exist" — only surfaced once the scheduled poller actually ran, not at
 * compile time or entity-mapping validation.
 */
class OutboxEventRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private OutboxEventRepository repository;

    @Test
    void findReadyToDispatchDoesNotThrowOnTheNativeEnumCast() {
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(), "test_aggregate", UUID.randomUUID(), "TEST_EVENT", "{}");
        repository.save(event);

        List<OutboxEvent> ready = repository.findReadyToDispatch(OutboxEvent.Status.PENDING, Instant.now());

        assertThat(ready).extracting(OutboxEvent::getId).contains(event.getId());
    }
}
