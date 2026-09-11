package com.shardeya.builder.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, UUID> {

    List<PaymentAllocation> findByPaymentScheduleIdAndDeletedAtIsNull(UUID paymentScheduleId);

    List<PaymentAllocation> findByPaymentRecordIdAndDeletedAtIsNull(UUID paymentRecordId);
}
