package com.shardeya.builder.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ReceiptSequenceRepository extends JpaRepository<ReceiptSequence, ReceiptSequence.Id> {

    // B-05 §3: allocated under SELECT ... FOR UPDATE inside the same
    // transaction as the payment_record insert -- this lock is what makes
    // the numbering gapless under concurrent payment recording.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ReceiptSequence r WHERE r.id.orgId = :orgId AND r.id.fy = :fy")
    Optional<ReceiptSequence> findForUpdate(@Param("orgId") UUID orgId, @Param("fy") String fy);
}
