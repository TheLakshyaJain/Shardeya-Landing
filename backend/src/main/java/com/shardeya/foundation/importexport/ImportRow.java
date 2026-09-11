package com.shardeya.foundation.importexport;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/** No org_id/RLS of its own — scoped transitively through import_job_id (same pattern as role_permission). */
@Entity
@Table(name = "import_row")
public class ImportRow {

    public enum Status { VALID, INVALID, IMPORTED, SKIPPED }

    @Id
    private UUID id;

    @Column(name = "import_job_id", nullable = false)
    private UUID importJobId;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data", nullable = false)
    private String rawData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "normalised_data")
    private String normalisedData;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private Status status = Status.INVALID;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String errors = "[]";

    @Column(name = "created_entity_id")
    private UUID createdEntityId;

    protected ImportRow() {
    }

    public ImportRow(UUID id, UUID importJobId, int rowNumber, String rawData) {
        this.id = id;
        this.importJobId = importJobId;
        this.rowNumber = rowNumber;
        this.rawData = rawData;
    }

    public UUID getId() {
        return id;
    }

    public UUID getImportJobId() {
        return importJobId;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public String getRawData() {
        return rawData;
    }

    public void setRawData(String rawData) {
        this.rawData = rawData;
    }

    public String getNormalisedData() {
        return normalisedData;
    }

    public void setNormalisedData(String normalisedData) {
        this.normalisedData = normalisedData;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getErrors() {
        return errors;
    }

    public void setErrors(String errors) {
        this.errors = errors;
    }

    public UUID getCreatedEntityId() {
        return createdEntityId;
    }

    public void setCreatedEntityId(UUID createdEntityId) {
        this.createdEntityId = createdEntityId;
    }
}
