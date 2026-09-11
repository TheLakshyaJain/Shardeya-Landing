package com.shardeya.builder.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, UUID> {

    Optional<PaymentRecord> findById(UUID id);

    List<PaymentRecord> findByPlotSaleIdOrderByPaidOnDescCreatedAtDesc(UUID plotSaleId);

    boolean existsByReversesPaymentId(UUID reversesPaymentId);
}
