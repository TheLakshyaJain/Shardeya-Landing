package com.shardeya.foundation.importexport;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * M-10 §3/§7 "a report definition is data" -- global reference table (no
 * org_id, no RLS), same shape as MeasurementUnit/StampDutyRate. The actual
 * query + column shape for each code lives in Java
 * ({@link com.shardeya.foundation.importexport.reports.ReportRegistry}) --
 * this row is what drives the catalogue/filter-panel/permission gate.
 */
@Entity
@Table(name = "report_definition")
public class ReportDefinition {

    @Id
    private String code;

    private String profile;

    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "name_hi")
    private String nameHi;

    @Column(name = "description_key")
    private String descriptionKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "supported_filters")
    private String supportedFilters;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "supported_formats")
    private String supportedFormats;

    @Column(name = "required_permission")
    private String requiredPermission;

    @Column(name = "min_analytics_tier")
    private String minAnalyticsTier;

    @Column(name = "sort_order")
    private int sortOrder;

    protected ReportDefinition() {
    }

    public String getCode() {
        return code;
    }

    public String getProfile() {
        return profile;
    }

    public String getNameEn() {
        return nameEn;
    }

    public String getNameHi() {
        return nameHi;
    }

    public String getDescriptionKey() {
        return descriptionKey;
    }

    public String getSupportedFilters() {
        return supportedFilters;
    }

    public String getSupportedFormats() {
        return supportedFormats;
    }

    public String getRequiredPermission() {
        return requiredPermission;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
