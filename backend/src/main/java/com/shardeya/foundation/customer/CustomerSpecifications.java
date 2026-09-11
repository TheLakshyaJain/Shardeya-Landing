package com.shardeya.foundation.customer;

import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Dynamic list/filter predicates (B-07 §4 list query) -- a Specification per optional filter, combined with `and`, same convention as PlotSpecifications. */
public final class CustomerSpecifications {

    private CustomerSpecifications() {
    }

    public static Specification<Customer> orgId(UUID orgId) {
        return (root, query, cb) -> cb.equal(root.get("orgId"), orgId);
    }

    public static Specification<Customer> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<Customer> projectId(UUID projectId) {
        return (root, query, cb) -> projectId == null ? null : cb.equal(root.get("interestedProjectId"), projectId);
    }

    public static Specification<Customer> plotId(UUID plotId) {
        return (root, query, cb) -> plotId == null ? null : cb.equal(root.get("interestedPlotId"), plotId);
    }

    public static Specification<Customer> assignedTo(UUID userId) {
        return (root, query, cb) -> userId == null ? null : cb.equal(root.get("assignedTo"), userId);
    }

    public static Specification<Customer> source(Customer.Source source) {
        return (root, query, cb) -> source == null ? null : cb.equal(root.get("source"), source);
    }

    public static Specification<Customer> sourceBrokerId(UUID brokerId) {
        return (root, query, cb) -> brokerId == null ? null : cb.equal(root.get("sourceBrokerId"), brokerId);
    }

    public static Specification<Customer> status(Customer.Status status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Customer> important(Boolean important) {
        return (root, query, cb) -> important == null ? null : cb.equal(root.get("important"), important);
    }

    public static Specification<Customer> unassigned() {
        return (root, query, cb) -> cb.isNull(root.get("assignedTo"));
    }

    /** "Own leads only" (M-02 §7 ownership predicate) for a Sales Executive who lacks DATA_VIEW_ALL. */
    public static Specification<Customer> ownedBy(UUID userId) {
        return (root, query, cb) -> cb.or(
                cb.equal(root.get("assignedTo"), userId),
                cb.equal(root.get("createdBy"), userId));
    }

    /** Project-scoped staff (M-02 §7 project scope predicate) -- restricts to leads whose interested project is in scope, or leads with no project at all (early-stage enquiries, B-07 §10). */
    public static Specification<Customer> inProjectScope(List<UUID> scope) {
        return (root, query, cb) -> cb.or(
                root.get("interestedProjectId").in(scope),
                cb.isNull(root.get("interestedProjectId")));
    }

    public static Specification<Customer> followUpRange(String range, LocalDate today) {
        if (range == null) {
            return null;
        }
        return switch (range) {
            case "today" -> (root, query, cb) -> cb.equal(root.get("followUpDate"), today);
            case "week" -> (root, query, cb) -> cb.between(root.get("followUpDate"), today, today.plusDays(7));
            case "overdue" -> (root, query, cb) -> cb.lessThan(root.get("followUpDate"), today);
            default -> null;
        };
    }

    // Only ever applied when a real cursor exists (not the "first page, no
    // cursor yet" case) -- same reason ProjectRepository splits its own
    // first-page/after-cursor queries instead of one "(:x IS NULL OR ...)"
    // query: a null param used only in an IS NULL comparison gives
    // Postgres's JDBC driver no type to infer and fails outright.
    public static Specification<Customer> afterCursor(Instant cursorCreatedAt, UUID cursorId) {
        return (root, query, cb) -> cb.or(
                cb.lessThan(root.get("createdAt"), cursorCreatedAt),
                cb.and(cb.equal(root.get("createdAt"), cursorCreatedAt), cb.lessThan(root.get("id"), cursorId)));
    }

    // ILIKE with a leading wildcard across name/mobile/remarks -- same
    // pattern PlotSpecifications.search() already uses for plot_number
    // (ix_cust_mobile_trgm covers the mobile side; ix_cust_search's tsvector
    // GIN index exists for a future proper full-text query, not consulted
    // by this simple predicate yet).
    public static Specification<Customer> searchText(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String pattern = "%" + search.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("fullName")), pattern),
                cb.like(root.get("mobile"), pattern),
                cb.like(cb.lower(cb.coalesce(root.get("remarks"), "")), pattern));
    }
}
