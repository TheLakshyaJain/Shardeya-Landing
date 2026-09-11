package com.shardeya.builder.document;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.EnumType;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Mirrors ReceiptSequence (B-05 §3) exactly, with a doc_type dimension added. */
@Entity
@Table(name = "document_number_sequence")
public class DocumentNumberSequence {

    @EmbeddedId
    private Id id;

    @Column(name = "next_value", nullable = false)
    private long nextValue = 1;

    protected DocumentNumberSequence() {
    }

    public DocumentNumberSequence(UUID orgId, DocumentTemplate.DocType docType, String fy) {
        this.id = new Id(orgId, docType, fy);
    }

    public Id getId() {
        return id;
    }

    public long getNextValue() {
        return nextValue;
    }

    public void setNextValue(long nextValue) {
        this.nextValue = nextValue;
    }

    @Embeddable
    public static class Id implements Serializable {
        @Column(name = "org_id")
        private UUID orgId;

        @Enumerated(EnumType.STRING)
        @JdbcTypeCode(SqlTypes.NAMED_ENUM)
        @Column(name = "doc_type")
        private DocumentTemplate.DocType docType;

        @Column(name = "fy")
        private String fy;

        protected Id() {
        }

        public Id(UUID orgId, DocumentTemplate.DocType docType, String fy) {
            this.orgId = orgId;
            this.docType = docType;
            this.fy = fy;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Id id)) return false;
            return Objects.equals(orgId, id.orgId) && docType == id.docType && Objects.equals(fy, id.fy);
        }

        @Override
        public int hashCode() {
            return Objects.hash(orgId, docType, fy);
        }
    }
}
