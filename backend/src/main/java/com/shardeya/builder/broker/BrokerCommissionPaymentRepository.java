package com.shardeya.builder.broker;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BrokerCommissionPaymentRepository extends JpaRepository<BrokerCommissionPayment, UUID> {

    Optional<BrokerCommissionPayment> findByIdAndOrgId(UUID id, UUID orgId);

    List<BrokerCommissionPayment> findByOrgIdAndBeneficiaryBrokerIdOrderByCreatedAtDesc(UUID orgId, UUID beneficiaryBrokerId);

    boolean existsByReversesPaymentId(UUID reversesPaymentId);
}
