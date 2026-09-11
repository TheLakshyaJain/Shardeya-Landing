package com.shardeya.builder.broker;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BrokerPartnerRepository extends JpaRepository<BrokerPartner, UUID>, JpaSpecificationExecutor<BrokerPartner> {

    Optional<BrokerPartner> findByIdAndOrgIdAndDeletedAtIsNull(UUID id, UUID orgId);

    boolean existsByOrgIdAndMobileAndDeletedAtIsNull(UUID orgId, String mobile);

    Optional<BrokerPartner> findFirstByOrgIdAndMobileAndDeletedAtIsNull(UUID orgId, String mobile);

    long countByOrgIdAndDeletedAtIsNull(UUID orgId);

    /** 06-BROKER-NETWORK-ENGINE.md §28 network tree -- only DESIGNATION brokers participate in the hierarchy (§0). */
    List<BrokerPartner> findByOrgIdAndCommissionTypeAndDeletedAtIsNull(UUID orgId, BrokerPartner.CommissionType commissionType);
}
