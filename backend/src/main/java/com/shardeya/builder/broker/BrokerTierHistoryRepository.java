package com.shardeya.builder.broker;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BrokerTierHistoryRepository extends JpaRepository<BrokerTierHistory, UUID> {

    List<BrokerTierHistory> findByOrgIdAndBrokerPartnerIdOrderByChangedAtDesc(UUID orgId, UUID brokerPartnerId);
}
