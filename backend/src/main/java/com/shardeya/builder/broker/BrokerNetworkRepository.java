package com.shardeya.builder.broker;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BrokerNetworkRepository extends JpaRepository<BrokerNetwork, BrokerNetwork.Key> {

    /** All ancestors of {@code brokerId} (including its own depth-0 self-row), ordered nearest-first -- the exact chain the commission engine walks up (§7). */
    @Query("SELECT n FROM BrokerNetwork n WHERE n.orgId = :orgId AND n.id.descendantBrokerId = :brokerId ORDER BY n.depth ASC")
    List<BrokerNetwork> findAncestorsOf(@Param("orgId") UUID orgId, @Param("brokerId") UUID brokerId);

    /** All descendants of {@code brokerId} (including its own depth-0 self-row) -- the recursive team-sales rollup set (§4), not used until build-order step 7. */
    @Query("SELECT n FROM BrokerNetwork n WHERE n.orgId = :orgId AND n.id.ancestorBrokerId = :brokerId")
    List<BrokerNetwork> findDescendantsOf(@Param("orgId") UUID orgId, @Param("brokerId") UUID brokerId);

    /** The core cycle-check: true iff {@code candidateUplineId} is already a descendant of {@code brokerId} -- making it {@code brokerId}'s upline would close a cycle (§10). */
    boolean existsById_AncestorBrokerIdAndId_DescendantBrokerId(UUID ancestorBrokerId, UUID descendantBrokerId);
}
