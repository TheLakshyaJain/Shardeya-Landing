package com.shardeya.platform;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    // A JPQL enum *literal* (e.g. OutboxEvent.Status.PENDING inlined in the
    // query string) makes Hibernate generate a SQL cast using the Java enum's
    // simple class name — "'PENDING'::Status" — instead of the actual
    // Postgres native enum type ("outbox_status"), which fails at runtime
    // with "type Status does not exist" (only surfaces once the scheduled
    // poller actually runs; a plain compile/entity-mapping check doesn't
    // catch it). Binding it as a real parameter routes through the mapped
    // @JdbcTypeCode(SqlTypes.NAMED_ENUM) type instead, which casts correctly.
    @Query("select e from OutboxEvent e where e.status = :status "
            + "and e.availableAt <= :now order by e.availableAt asc")
    List<OutboxEvent> findReadyToDispatch(@Param("status") OutboxEvent.Status status, @Param("now") Instant now);
}
