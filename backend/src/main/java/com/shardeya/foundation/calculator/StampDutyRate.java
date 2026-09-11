package com.shardeya.foundation.calculator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * M-08 §3/§7 (01-DATA-MODEL.md §142) -- a platform reference table, no
 * org_id/RLS/soft-delete, same shape as MeasurementUnit/NotificationType.
 * Admin-editable (M-14 basic per 05-MILESTONES.md M6 exit criteria).
 */
@Entity
@Table(name = "stamp_duty_rate")
public class StampDutyRate {

    public enum PropertyType { RESIDENTIAL, COMMERCIAL, AGRICULTURAL }

    public enum TransactionType { SALE, GIFT, MORTGAGE }

    public enum BuyerGender { MALE, FEMALE, JOINT, ANY }

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "state_code", nullable = false, length = 2)
    private String stateCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "property_type", nullable = false)
    private PropertyType propertyType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "buyer_gender", nullable = false)
    private BuyerGender buyerGender;

    @Column(name = "stamp_duty_pct", nullable = false, precision = 6, scale = 3)
    private BigDecimal stampDutyPct;

    @Column(name = "registration_pct", precision = 6, scale = 3)
    private BigDecimal registrationPct;

    @Column(name = "registration_flat", precision = 19, scale = 2)
    private BigDecimal registrationFlat;

    @Column(name = "registration_cap", precision = 19, scale = 2)
    private BigDecimal registrationCap;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "source_note", columnDefinition = "text")
    private String sourceNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected StampDutyRate() {
    }

    public UUID getId() {
        return id;
    }

    public String getStateCode() {
        return stateCode;
    }

    public PropertyType getPropertyType() {
        return propertyType;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public BuyerGender getBuyerGender() {
        return buyerGender;
    }

    public BigDecimal getStampDutyPct() {
        return stampDutyPct;
    }

    public BigDecimal getRegistrationPct() {
        return registrationPct;
    }

    public BigDecimal getRegistrationFlat() {
        return registrationFlat;
    }

    public BigDecimal getRegistrationCap() {
        return registrationCap;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public String getSourceNote() {
        return sourceNote;
    }
}
