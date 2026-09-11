package com.shardeya.builder.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * B-11 §17.2 / 01-DATA-MODEL.md §9. {@code orgId == null} means a system
 * default, visible to every tenant (mirrors {@code role.org_id}'s own
 * nullable-for-system-rows shape from M0). {@code version} is JPA's own
 * {@code @Version} optimistic lock AND the business "template_version"
 * snapshotted onto {@link GeneratedDocument#getTemplateVersion()} at
 * generation time -- see the migration's own comment for why one column
 * can safely serve both purposes here.
 */
@Entity
@Table(name = "document_template")
public class DocumentTemplate {

    public enum DocType { ALLOTMENT_LETTER, PAYMENT_RECEIPT, DEMAND_LETTER, BOOKING_CONFIRMATION }

    @Id
    private UUID id;

    @Column(name = "org_id")
    private UUID orgId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "doc_type", nullable = false)
    private DocType docType;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 2)
    private String language;

    @Column(name = "body_html", nullable = false)
    private String bodyHtml;

    @Column(name = "header_html")
    private String headerHtml;

    @Column(name = "footer_html")
    private String footerHtml;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String variables = "[]";

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    protected DocumentTemplate() {
    }

    public DocumentTemplate(UUID id, UUID orgId, DocType docType, String name, String language,
                             String bodyHtml, UUID createdBy) {
        this.id = id;
        this.orgId = orgId;
        this.docType = docType;
        this.name = name;
        this.language = language;
        this.bodyHtml = bodyHtml;
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public DocType getDocType() {
        return docType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLanguage() {
        return language;
    }

    public String getBodyHtml() {
        return bodyHtml;
    }

    public void setBodyHtml(String bodyHtml) {
        this.bodyHtml = bodyHtml;
    }

    public String getHeaderHtml() {
        return headerHtml;
    }

    public void setHeaderHtml(String headerHtml) {
        this.headerHtml = headerHtml;
    }

    public String getFooterHtml() {
        return footerHtml;
    }

    public void setFooterHtml(String footerHtml) {
        this.footerHtml = footerHtml;
    }

    public String getVariables() {
        return variables;
    }

    public void setVariables(String variables) {
        this.variables = variables;
    }

    public long getVersion() {
        return version;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public void setDeletedBy(UUID deletedBy) {
        this.deletedBy = deletedBy;
    }
}
