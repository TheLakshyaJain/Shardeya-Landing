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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "plot_sale")
public class PlotSale {

    public enum PaymentType { LUMP_SUM, INSTALMENT }

    public enum Status { BOOKED, ACTIVE, COMPLETED, CANCELLED }

    public enum GovIdType { AADHAAR, PAN, PASSPORT, VOTER_ID, DL }

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "plot_id", nullable = false)
    private UUID plotId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "buyer_name", nullable = false, length = 120)
    private String buyerName;

    @Column(name = "buyer_mobile", nullable = false, length = 15)
    private String buyerMobile;

    @Column(name = "buyer_email")
    private String buyerEmail;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "buyer_gov_id_type")
    private GovIdType buyerGovIdType;

    // AES-256-GCM ciphertext (nonce || ciphertext || tag) -- see GovIdCipher.
    // Never read/written except through PlotSaleService's encrypt/decrypt
    // calls; nothing else should ever touch this column directly.
    @Column(name = "buyer_gov_id_number_enc")
    private byte[] buyerGovIdNumberEnc;

    @Column(name = "buyer_gov_id_last4", length = 4)
    private String buyerGovIdLast4;

    @Column(name = "buyer_gov_id_media_id")
    private UUID buyerGovIdMediaId;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Column(name = "deal_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal dealValue;

    @Column(name = "broker_partner_id")
    private UUID brokerPartnerId;

    @Column(name = "external_broker_name")
    private String externalBrokerName;

    @Column(name = "external_broker_mobile")
    private String externalBrokerMobile;

    @Column(name = "broker_commission_amount", precision = 19, scale = 2)
    private BigDecimal brokerCommissionAmount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "payment_type", nullable = false)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", columnDefinition = "text")
    private String cancellationReason;

    @Column(name = "handled_by")
    private UUID handledBy;

    @Column(name = "total_paid", insertable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal totalPaid;

    // Trigger-maintained (V5_006) from WAIVED payment_schedule rows' never-
    // collected portion -- balance_due (below) already subtracts this, but
    // it's mapped separately too so callers can show "Waived: ₹X" explicitly
    // rather than have it silently vanish into a smaller balance figure.
    @Column(name = "total_waived", insertable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal totalWaived;

    @Column(name = "balance_due", insertable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal balanceDue;

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

    protected PlotSale() {
    }

    public PlotSale(UUID id, UUID orgId, UUID plotId, UUID projectId, String buyerName, String buyerMobile,
                     LocalDate purchaseDate, BigDecimal dealValue, PaymentType paymentType) {
        this.id = id;
        this.orgId = orgId;
        this.plotId = plotId;
        this.projectId = projectId;
        this.buyerName = buyerName;
        this.buyerMobile = buyerMobile;
        this.purchaseDate = purchaseDate;
        this.dealValue = dealValue;
        this.paymentType = paymentType;
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

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public String getBuyerName() {
        return buyerName;
    }

    public void setBuyerName(String buyerName) {
        this.buyerName = buyerName;
    }

    public String getBuyerMobile() {
        return buyerMobile;
    }

    public void setBuyerMobile(String buyerMobile) {
        this.buyerMobile = buyerMobile;
    }

    public String getBuyerEmail() {
        return buyerEmail;
    }

    public void setBuyerEmail(String buyerEmail) {
        this.buyerEmail = buyerEmail;
    }

    public GovIdType getBuyerGovIdType() {
        return buyerGovIdType;
    }

    public void setBuyerGovIdType(GovIdType buyerGovIdType) {
        this.buyerGovIdType = buyerGovIdType;
    }

    public byte[] getBuyerGovIdNumberEnc() {
        return buyerGovIdNumberEnc;
    }

    public void setBuyerGovIdNumberEnc(byte[] buyerGovIdNumberEnc) {
        this.buyerGovIdNumberEnc = buyerGovIdNumberEnc;
    }

    public String getBuyerGovIdLast4() {
        return buyerGovIdLast4;
    }

    public void setBuyerGovIdLast4(String buyerGovIdLast4) {
        this.buyerGovIdLast4 = buyerGovIdLast4;
    }

    public UUID getBuyerGovIdMediaId() {
        return buyerGovIdMediaId;
    }

    public void setBuyerGovIdMediaId(UUID buyerGovIdMediaId) {
        this.buyerGovIdMediaId = buyerGovIdMediaId;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(LocalDate purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public BigDecimal getDealValue() {
        return dealValue;
    }

    public void setDealValue(BigDecimal dealValue) {
        this.dealValue = dealValue;
    }

    public UUID getBrokerPartnerId() {
        return brokerPartnerId;
    }

    public void setBrokerPartnerId(UUID brokerPartnerId) {
        this.brokerPartnerId = brokerPartnerId;
    }

    public String getExternalBrokerName() {
        return externalBrokerName;
    }

    public void setExternalBrokerName(String externalBrokerName) {
        this.externalBrokerName = externalBrokerName;
    }

    public String getExternalBrokerMobile() {
        return externalBrokerMobile;
    }

    public void setExternalBrokerMobile(String externalBrokerMobile) {
        this.externalBrokerMobile = externalBrokerMobile;
    }

    public BigDecimal getBrokerCommissionAmount() {
        return brokerCommissionAmount;
    }

    public void setBrokerCommissionAmount(BigDecimal brokerCommissionAmount) {
        this.brokerCommissionAmount = brokerCommissionAmount;
    }

    public PaymentType getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(PaymentType paymentType) {
        this.paymentType = paymentType;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public UUID getHandledBy() {
        return handledBy;
    }

    public void setHandledBy(UUID handledBy) {
        this.handledBy = handledBy;
    }

    public BigDecimal getTotalPaid() {
        return totalPaid;
    }

    public BigDecimal getTotalWaived() {
        return totalWaived;
    }

    public BigDecimal getBalanceDue() {
        return balanceDue;
    }

    public Instant getCreatedAt() {
        return createdAt;
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
