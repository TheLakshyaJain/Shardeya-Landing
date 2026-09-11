package com.shardeya.foundation.importexport;

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
@Table(name = "import_job")
public class ImportJob {

    public enum EntityType { PROPERTY, PLOT, CUSTOMER }

    public enum Status { UPLOADED, VALIDATING, PREVIEW_READY, IMPORTING, COMPLETED, FAILED, CANCELLED }

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "entity_type", nullable = false)
    private EntityType entityType;

    @Column(name = "target_project_id")
    private UUID targetProjectId;

    @Column(name = "source_media_id")
    private UUID sourceMediaId;

    @Column(name = "template_version", nullable = false, length = 20)
    private String templateVersion;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private Status status = Status.UPLOADED;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "valid_rows", nullable = false)
    private int validRows;

    @Column(name = "invalid_rows", nullable = false)
    private int invalidRows;

    @Column(name = "imported_rows", nullable = false)
    private int importedRows;

    @Column(name = "started_by")
    private UUID startedBy;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_report_media_id")
    private UUID errorReportMediaId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ImportJob() {
    }

    public ImportJob(UUID id, UUID orgId, EntityType entityType, UUID targetProjectId, UUID sourceMediaId,
                      String templateVersion, UUID startedBy) {
        this.id = id;
        this.orgId = orgId;
        this.entityType = entityType;
        this.targetProjectId = targetProjectId;
        this.sourceMediaId = sourceMediaId;
        this.templateVersion = templateVersion;
        this.startedBy = startedBy;
        this.startedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public UUID getTargetProjectId() {
        return targetProjectId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getValidRows() {
        return validRows;
    }

    public void setValidRows(int validRows) {
        this.validRows = validRows;
    }

    public int getInvalidRows() {
        return invalidRows;
    }

    public void setInvalidRows(int invalidRows) {
        this.invalidRows = invalidRows;
    }

    public int getImportedRows() {
        return importedRows;
    }

    public void setImportedRows(int importedRows) {
        this.importedRows = importedRows;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
