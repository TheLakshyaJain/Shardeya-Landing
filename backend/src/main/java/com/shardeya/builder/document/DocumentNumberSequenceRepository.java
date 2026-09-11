package com.shardeya.builder.document;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DocumentNumberSequenceRepository extends JpaRepository<DocumentNumberSequence, DocumentNumberSequence.Id> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM DocumentNumberSequence s WHERE s.id.orgId = :orgId AND s.id.docType = :docType AND s.id.fy = :fy")
    Optional<DocumentNumberSequence> findForUpdate(@Param("orgId") UUID orgId, @Param("docType") DocumentTemplate.DocType docType, @Param("fy") String fy);
}
