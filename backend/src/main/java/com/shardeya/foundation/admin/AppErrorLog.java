package com.shardeya.foundation.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A deliberately scoped-down stand-in for the "recent errors" half of M-14
 * (Platform Admin Console) -- see CLAUDE.md's "Post-M7 -- Minimal Ops
 * Visibility" notes. One row per genuinely unhandled exception
 * ({@link com.shardeya.platform.GlobalExceptionHandler}'s catch-all only --
 * never the specific, expected exception types that already map to a real
 * 4xx status), never a validation/business-rule rejection. {@code orgId} is
 * nullable: some errors happen before any tenant context is ever bound.
 */
@Entity
@Table(name = "app_error_log")
public class AppErrorLog {

    @Id
    private UUID id;

    @Column(name = "org_id")
    private UUID orgId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;

    @Column(nullable = false, length = 500)
    private String path;

    @Column(name = "exception_class", nullable = false, length = 300)
    private String exceptionClass;

    @Column(columnDefinition = "text")
    private String message;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AppErrorLog() {
    }

    public AppErrorLog(UUID id, UUID orgId, UUID userId, String httpMethod, String path,
                        String exceptionClass, String message) {
        this.id = id;
        this.orgId = orgId;
        this.userId = userId;
        this.httpMethod = httpMethod;
        this.path = path;
        this.exceptionClass = exceptionClass;
        this.message = message;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getPath() {
        return path;
    }

    public String getExceptionClass() {
        return exceptionClass;
    }

    public String getMessage() {
        return message;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
