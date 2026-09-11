package com.shardeya.builder.broker;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface CommissionLedgerEntryRepository extends JpaRepository<CommissionLedgerEntry, UUID>, JpaSpecificationExecutor<CommissionLedgerEntry> {

    Optional<CommissionLedgerEntry> findByIdAndOrgIdAndDeletedAtIsNull(UUID id, UUID orgId);

    Optional<CommissionLedgerEntry> findByPlotSaleIdAndDeletedAtIsNull(UUID plotSaleId);
}
