package com.shardeya.builder.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate, UUID> {

    @Query("SELECT t FROM DocumentTemplate t WHERE t.id = :id AND t.deletedAt IS NULL")
    Optional<DocumentTemplate> findByIdAndDeletedAtIsNull(@Param("id") UUID id);

    // org-owned + system-default rows together, for the template list screen.
    @Query("SELECT t FROM DocumentTemplate t WHERE (t.orgId = :orgId OR t.orgId IS NULL) AND t.deletedAt IS NULL ORDER BY t.docType, t.language, t.name")
    List<DocumentTemplate> findAllVisibleToOrg(@Param("orgId") UUID orgId);

    // Resolution order for generation: org's own active template first, else the system default.
    @Query("SELECT t FROM DocumentTemplate t WHERE t.orgId = :orgId AND t.docType = :docType AND t.language = :language AND t.active = true AND t.deletedAt IS NULL")
    Optional<DocumentTemplate> findActiveOrgTemplate(@Param("orgId") UUID orgId, @Param("docType") DocumentTemplate.DocType docType, @Param("language") String language);

    @Query("SELECT t FROM DocumentTemplate t WHERE t.orgId IS NULL AND t.docType = :docType AND t.language = :language AND t.active = true AND t.deletedAt IS NULL")
    Optional<DocumentTemplate> findActiveSystemTemplate(@Param("docType") DocumentTemplate.DocType docType, @Param("language") String language);

    @Query("SELECT t FROM DocumentTemplate t WHERE (t.orgId = :orgId OR t.orgId IS NULL) AND t.docType = :docType AND t.language = :language AND t.active = true AND t.deletedAt IS NULL ORDER BY CASE WHEN t.orgId IS NULL THEN 1 ELSE 0 END")
    List<DocumentTemplate> findActiveCandidates(@Param("orgId") UUID orgId, @Param("docType") DocumentTemplate.DocType docType, @Param("language") String language);

    // Deactivate every other active template in the same (org, doc_type, language) slot before activating a new one.
    @Query("SELECT t FROM DocumentTemplate t WHERE t.orgId = :orgId AND t.docType = :docType AND t.language = :language AND t.active = true AND t.deletedAt IS NULL")
    List<DocumentTemplate> findActiveInSlot(@Param("orgId") UUID orgId, @Param("docType") DocumentTemplate.DocType docType, @Param("language") String language);
}
