package com.shardeya.builder.broker;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommissionPaymentRepository extends JpaRepository<CommissionPayment, UUID> {

    Optional<CommissionPayment> findByIdAndOrgId(UUID id, UUID orgId);

    List<CommissionPayment> findByCommissionLedgerEntryIdOrderByCreatedAtDesc(UUID commissionLedgerEntryId);

    boolean existsByReversesPaymentId(UUID reversesPaymentId);
}
