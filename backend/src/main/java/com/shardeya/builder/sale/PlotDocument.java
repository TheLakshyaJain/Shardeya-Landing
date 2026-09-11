package com.shardeya.builder.sale;

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

@Entity
@Table(name = "plot_document")
public class PlotDocument {

    public enum DocType { SALE_AGREEMENT, REGISTRY_DEED, PLOT_MAP, BUYER_ID_PROOF, OTHER }

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "plot_id", nullable = false)
    private UUID plotId;

    @Column(name = "plot_sale_id", nullable = false)
    private UUID plotSaleId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "doc_type", nullable = false)
    private DocType docType;

    @Column(length = 120)
    private String label;

    @Column(name = "media_id", nullable = false)
    private UUID mediaId;

    @Column(name = "is_sensitive", nullable = false)
    private boolean sensitive;

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

    @Version
    @Column(nullable = false)
    private long version;

    protected PlotDocument() {
    }

    public PlotDocument(UUID id, UUID orgId, UUID plotId, UUID plotSaleId, DocType docType, String label,
                         UUID mediaId, boolean sensitive) {
        this.id = id;
        this.orgId = orgId;
        this.plotId = plotId;
        this.plotSaleId = plotSaleId;
        this.docType = docType;
        this.label = label;
        this.mediaId = mediaId;
        this.sensitive = sensitive;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getPlotId() {
        return plotId;
    }

    public UUID getPlotSaleId() {
        return plotSaleId;
    }

    public DocType getDocType() {
        return docType;
    }

    public String getLabel() {
        return label;
    }

    public UUID getMediaId() {
        return mediaId;
    }

    public boolean isSensitive() {
        return sensitive;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public long getVersion() {
        return version;
    }
}
