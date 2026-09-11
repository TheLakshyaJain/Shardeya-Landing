package com.shardeya.builder.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GeneratedDocumentRepository extends JpaRepository<GeneratedDocument, UUID> {

    @Query("SELECT d FROM GeneratedDocument d WHERE d.orgId = :orgId AND d.entityType = :entityType AND d.entityId = :entityId ORDER BY d.generatedAt DESC")
    List<GeneratedDocument> findByEntity(@Param("orgId") UUID orgId, @Param("entityType") String entityType, @Param("entityId") UUID entityId);

    @Query("SELECT d FROM GeneratedDocument d WHERE d.orgId = :orgId AND d.docType = :docType AND d.entityType = :entityType AND d.entityId = :entityId ORDER BY d.generatedAt DESC LIMIT 1")
    Optional<GeneratedDocument> findLatestForEntity(@Param("orgId") UUID orgId, @Param("docType") DocumentTemplate.DocType docType,
                                                      @Param("entityType") String entityType, @Param("entityId") UUID entityId);
}
