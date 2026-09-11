package com.shardeya.builder.broker;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BrokerInteractionRepository extends JpaRepository<BrokerInteraction, UUID> {

    List<BrokerInteraction> findByOrgIdAndBrokerPartnerIdAndDeletedAtIsNullOrderByOccurredOnDesc(UUID orgId, UUID brokerPartnerId);
}
