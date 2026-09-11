package com.shardeya.foundation.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

/**
 * Platform-wide reference catalogue (01-DATA-MODEL.md §8 notification_type)
 * -- no org_id, no RLS, shared read-only across every tenant, same shape as
 * measurement_unit. Existed as a table since M3 (V3_007) with no JPA entity
 * at all until now, since nothing in application code ever needed to read
 * default_channels/is_mandatory before this build -- see V7_017's own
 * migration comment.
 */
@Entity
@Table(name = "notification_type")
public class NotificationType {

    @Id
    private String code;

    @Column(nullable = false, length = 60)
    private String category;

    @Column(name = "default_channels", nullable = false)
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> defaultChannels;

    @Column(name = "is_mandatory", nullable = false)
    private boolean mandatory;

    @Column(name = "template_key_en", nullable = false, length = 120)
    private String templateKeyEn;

    @Column(name = "template_key_hi", nullable = false, length = 120)
    private String templateKeyHi;

    @Column
    private String description;

    protected NotificationType() {
    }

    public String getCode() {
        return code;
    }

    public String getCategory() {
        return category;
    }

    public List<String> getDefaultChannels() {
        return defaultChannels;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public String getTemplateKeyEn() {
        return templateKeyEn;
    }

    public String getTemplateKeyHi() {
        return templateKeyHi;
    }

    public String getDescription() {
        return description;
    }
}
