package com.shardeya.builder.broker;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BrokerCommissionPaymentAllocationRepository extends JpaRepository<BrokerCommissionPaymentAllocation, UUID> {

    /** A payment's own allocations -- what BrokerCommissionPaymentService.reverse() mirrors with negated amounts onto the same entries. */
    List<BrokerCommissionPaymentAllocation> findByBrokerCommissionPaymentId(UUID brokerCommissionPaymentId);
}
