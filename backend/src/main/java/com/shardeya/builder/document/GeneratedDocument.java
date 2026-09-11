package com.shardeya.builder.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * B-11 §17.2 / 01-DATA-MODEL.md §9. Immutable historical record -- no
 * soft-delete, no version, matching payment_record's own "never edited in
 * place" shape. {@code renderedSnapshot} is what makes reprinting years
 * later reproduce the original figures regardless of later template edits
 * or sale amendments.
 */
@Entity
@Table(name = "generated_document")
public class GeneratedDocument {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "doc_type", nullable = false)
    private DocumentTemplate.DocType docType;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "template_version", nullable = false)
    private long templateVersion;

    @Column(name = "entity_type", nullable = false, length = 30)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "media_id", nullable = false)
    private UUID mediaId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rendered_snapshot", nullable = false)
    private String renderedSnapshot;

    @Column(name = "document_number", nullable = false, length = 40)
    private String documentNumber;

    @Column(nullable = false, length = 2)
    private String language;

    @Column(name = "generated_by", nullable = false)
    private UUID generatedBy;

    @CreationTimestamp
    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    protected GeneratedDocument() {
    }

    public GeneratedDocument(UUID id, UUID orgId, DocumentTemplate.DocType docType, UUID templateId, long templateVersion,
                              String entityType, UUID entityId, UUID mediaId, String renderedSnapshot,
                              String documentNumber, String language, UUID generatedBy) {
        this.id = id;
        this.orgId = orgId;
        this.docType = docType;
        this.templateId = templateId;
        this.templateVersion = templateVersion;
        this.entityType = entityType;
        this.entityId = entityId;
        this.mediaId = mediaId;
        this.renderedSnapshot = renderedSnapshot;
        this.documentNumber = documentNumber;
        this.language = language;
        this.generatedBy = generatedBy;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public DocumentTemplate.DocType getDocType() {
        return docType;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public long getTemplateVersion() {
        return templateVersion;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public UUID getMediaId() {
        return mediaId;
    }

    public String getRenderedSnapshot() {
        return renderedSnapshot;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public String getLanguage() {
        return language;
    }

    public UUID getGeneratedBy() {
        return generatedBy;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }
}
