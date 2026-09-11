package com.shardeya.foundation.calendar;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CalendarEventSpecifications {

    private CalendarEventSpecifications() {
    }

    public static Specification<CalendarEvent> orgId(UUID orgId) {
        return (root, query, cb) -> cb.equal(root.get("orgId"), orgId);
    }

    public static Specification<CalendarEvent> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<CalendarEvent> dateBetween(LocalDate from, LocalDate to) {
        return (root, query, cb) -> cb.between(root.get("eventDate"), from, to);
    }

    public static Specification<CalendarEvent> assignedTo(UUID userId) {
        return (root, query, cb) -> userId == null ? null : cb.equal(root.get("assignedTo"), userId);
    }

    public static Specification<CalendarEvent> projectId(UUID projectId) {
        return (root, query, cb) -> projectId == null ? null : cb.equal(root.get("projectId"), projectId);
    }

    public static Specification<CalendarEvent> types(List<CalendarEvent.EventType> types) {
        return (root, query, cb) -> (types == null || types.isEmpty()) ? null : root.get("eventType").in(types);
    }

    /** Staff visibility (B-09 §9): a Sales Executive sees only events assigned to them or unassigned. */
    public static Specification<CalendarEvent> assignedToMeOrUnassigned(UUID userId) {
        return (root, query, cb) -> cb.or(cb.equal(root.get("assignedTo"), userId), cb.isNull(root.get("assignedTo")));
    }

    /** Project-scoped staff (M-02 §7): events outside the caller's project scope are invisible, not greyed out (B-09 §10). */
    public static Specification<CalendarEvent> inProjectScope(List<UUID> scope) {
        return (root, query, cb) -> cb.or(root.get("projectId").in(scope), cb.isNull(root.get("projectId")));
    }

    /** Instalment events require FINANCIAL_VIEW (B-09 §9) -- excluded entirely for a caller without it. */
    public static Specification<CalendarEvent> excludeInstalments() {
        return (root, query, cb) -> cb.notEqual(root.get("eventType"), CalendarEvent.EventType.INSTALMENT_DUE);
    }
}
