package com.shardeya.foundation.customer;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InteractionRepository extends JpaRepository<Interaction, UUID> {

    Optional<Interaction> findByIdAndOrgId(UUID id, UUID orgId);

    List<Interaction> findByCustomerIdOrderByOccurredOnDescCreatedAtDesc(UUID customerId, Pageable pageable);
}
