package com.shardeya.foundation.media;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {

    // Enum bound as a @Param, not inlined in the JPQL string — an inlined
    // literal generated the wrong SQL enum cast in OutboxEventRepository
    // during M1 (see CLAUDE.md). Only ever surfaced via a background poller,
    // not a test assertion, so binding is the rule here regardless.
    @Query("SELECT m FROM MediaAsset m WHERE m.status = :status AND m.createdAt < :cutoff")
    List<MediaAsset> findStale(@Param("status") MediaAsset.Status status, @Param("cutoff") Instant cutoff);
}
