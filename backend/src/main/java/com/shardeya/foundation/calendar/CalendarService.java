package com.shardeya.foundation.calendar;

import com.shardeya.foundation.calendar.dto.CalendarEventResponse;
import com.shardeya.foundation.calendar.dto.EventCreateRequest;
import com.shardeya.foundation.calendar.dto.EventUpdateRequest;
import com.shardeya.platform.BadRequestException;
import com.shardeya.platform.ForbiddenException;
import com.shardeya.platform.ProjectAccessGuard;
import com.shardeya.platform.ResourceNotFoundException;
import com.shardeya.platform.TenantContext;
import com.shardeya.platform.TenantContextBinder;
import jakarta.persistence.EntityManager;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * M-11 Calendar & Reminder Engine. The core contract this class exists to
 * guarantee: auto events are projections, upserted idempotently keyed on
 * (sourceEntityType, sourceEntityId, eventType) -- see {@link #upsertAutoEvent}.
 * Changing a customer's follow-up date, or a schedule row's due date, always
 * updates the SAME calendar_event row; it never creates a duplicate.
 */
@Service
public class CalendarService {

    private final CalendarEventRepository repository;
    private final TenantContextBinder tenantContextBinder;
    private final ProjectAccessGuard accessGuard;
    private final EntityManager entityManager;

    public CalendarService(CalendarEventRepository repository, TenantContextBinder tenantContextBinder,
                            ProjectAccessGuard accessGuard, EntityManager entityManager) {
        this.repository = repository;
        this.tenantContextBinder = tenantContextBinder;
        this.accessGuard = accessGuard;
        this.entityManager = entityManager;
    }

    // ------------------------------------------------------- auto projections

    /**
     * Upserts an AUTO-sourced calendar event. Called from CustomerService/
     * InteractionService (FOLLOW_UP, SITE_VISIT) and ScheduleService
     * (INSTALMENT_DUE) whenever the source record's relevant date changes --
     * never called directly from a controller.
     */
    @Transactional
    public void upsertAutoEvent(UUID orgId, CalendarEvent.SourceEntityType sourceType, UUID sourceId,
                                 CalendarEvent.EventType eventType, String title, LocalDate eventDate,
                                 UUID customerId, UUID projectId, UUID plotId, UUID assignedTo) {
        if (eventDate == null) {
            removeAutoEvent(sourceType, sourceId, eventType);
            return;
        }
        CalendarEvent event = repository
                .findBySourceEntityTypeAndSourceEntityIdAndEventTypeAndDeletedAtIsNull(sourceType, sourceId, eventType)
                .orElseGet(() -> new CalendarEvent(UUID.randomUUID(), orgId, title, eventDate, eventType, CalendarEvent.Source.AUTO));
        event.setTitle(title);
        event.setEventDate(eventDate);
        event.setSourceEntityType(sourceType);
        event.setSourceEntityId(sourceId);
        event.setCustomerId(customerId);
        event.setProjectId(projectId);
        event.setPlotId(plotId);
        event.setAssignedTo(assignedTo);
        event.setStatus(CalendarEvent.Status.SCHEDULED);
        repository.save(event);
    }

    /** Clearing the source date (or the source itself being deleted/paid off) soft-deletes the projection -- B-09 §10. */
    @Transactional
    public void removeAutoEvent(CalendarEvent.SourceEntityType sourceType, UUID sourceId, CalendarEvent.EventType eventType) {
        repository.findBySourceEntityTypeAndSourceEntityIdAndEventTypeAndDeletedAtIsNull(sourceType, sourceId, eventType)
                .ifPresent(event -> {
                    event.setDeletedAt(Instant.now());
                    repository.save(event);
                });
    }

    // ------------------------------------------------------------------ read

    public List<CalendarEventResponse> list(LocalDate from, LocalDate to, UUID assignedToFilter, UUID projectId,
                                             List<CalendarEvent.EventType> types) {
        TenantContext.Tenant tenant = tenantContextBinder.current();
        Specification<CalendarEvent> spec = Specification
                .where(CalendarEventSpecifications.orgId(tenant.orgId()))
                .and(CalendarEventSpecifications.notDeleted())
                .and(CalendarEventSpecifications.dateBetween(from, to))
                .and(CalendarEventSpecifications.assignedTo(assignedToFilter))
                .and(CalendarEventSpecifications.projectId(projectId))
                .and(CalendarEventSpecifications.types(types));

        boolean viewAll = tenant.permissions().contains("DATA_VIEW_ALL");
        if (!viewAll) {
            spec = spec.and(CalendarEventSpecifications.assignedToMeOrUnassigned(tenant.userId()));
        }
        if (!tenant.allProjects()) {
            spec = spec.and(CalendarEventSpecifications.inProjectScope(tenant.projectScope()));
        }
        // B-09 §9: instalment events additionally require FINANCIAL_VIEW -- a
        // Sales Executive's calendar shows follow-ups/visits, never money.
        if (!tenant.permissions().contains("FINANCIAL_VIEW")) {
            spec = spec.and(CalendarEventSpecifications.excludeInstalments());
        }

        return repository.findAll(spec).stream().map(CalendarEventResponse::from).toList();
    }

    public Map<LocalDate, Long> counts(LocalDate from, LocalDate to) {
        return list(from, to, null, null, null).stream()
                .collect(Collectors.groupingBy(CalendarEventResponse::eventDate, Collectors.counting()));
    }

    /** B-09 §4/§6 StaffWorkloadStrip: per-staff event counts for the visible range -- shows at a glance who is overloaded. */
    public Map<UUID, Long> staffSummary(LocalDate from, LocalDate to) {
        return list(from, to, null, null, null).stream()
                .filter(e -> e.assignedTo() != null)
                .collect(Collectors.groupingBy(CalendarEventResponse::assignedTo, Collectors.counting()));
    }

    // -------------------------------------------------------- manual events

    @Transactional
    public CalendarEventResponse createManual(EventCreateRequest req) {
        UUID orgId = tenantContextBinder.currentOrgId();
        if (req.projectId() != null) {
            accessGuard.assertAccess(req.projectId());
        }
        CalendarEvent.EventType type = req.importantDate() ? CalendarEvent.EventType.IMPORTANT_DATE : CalendarEvent.EventType.MANUAL_MEETING;
        CalendarEvent event = new CalendarEvent(UUID.randomUUID(), orgId, req.title(), req.eventDate(), type, CalendarEvent.Source.MANUAL);
        event.setEventTime(req.eventTime());
        event.setDurationMinutes(req.durationMinutes());
        event.setCustomerId(req.customerId());
        event.setPropertyId(req.propertyId());
        event.setProjectId(req.projectId());
        event.setPlotId(req.plotId());
        event.setAssignedTo(req.assignedTo());
        event.setNotes(req.notes());
        event.setReminderEnabled(req.reminderEnabled());
        event = repository.save(event);
        // CalendarEvent.id is assigned in Java (no @GeneratedValue) --
        // save() routes through merge(), and the returned reference's
        // @CreationTimestamp isn't reliably populated without an explicit
        // flush+refresh. Same bug class found (NPE-crashing) in
        // InteractionService and (silently) in CustomerService/TeamService.
        entityManager.flush();
        entityManager.refresh(event);
        return CalendarEventResponse.from(event);
    }

    @Transactional
    public CalendarEventResponse updateManual(UUID id, EventUpdateRequest req) {
        CalendarEvent event = requireManual(id);
        if (req.title() != null) event.setTitle(req.title());
        if (req.eventDate() != null) event.setEventDate(req.eventDate());
        if (req.eventTime() != null) event.setEventTime(req.eventTime());
        if (req.durationMinutes() != null) event.setDurationMinutes(req.durationMinutes());
        if (req.assignedTo() != null) event.setAssignedTo(req.assignedTo());
        if (req.notes() != null) event.setNotes(req.notes());
        if (req.reminderEnabled() != null) event.setReminderEnabled(req.reminderEnabled());
        repository.save(event);
        return CalendarEventResponse.from(event);
    }

    @Transactional
    public void deleteManual(UUID id) {
        CalendarEvent event = requireManual(id);
        event.setDeletedAt(Instant.now());
        repository.save(event);
    }

    @Transactional
    public CalendarEventResponse complete(UUID id) {
        CalendarEvent event = requireOwnedEvent(id);
        event.setStatus(CalendarEvent.Status.DONE);
        repository.save(event);
        return CalendarEventResponse.from(event);
    }

    // Auto events are not directly reschedulable -- M-11 §7's own contract:
    // "the UI routes Reschedule to the SOURCE record ... which is the correct
    // mental model and keeps one source of truth." Rescheduling the source
    // (a customer's follow-up date, a schedule row's due date) re-triggers
    // upsertAutoEvent on its own, through CustomerService/InteractionService/
    // ScheduleService -- never through this method.
    @Transactional
    public CalendarEventResponse reschedule(UUID id, LocalDate newDate, LocalTime newTime, String reason) {
        CalendarEvent event = requireManual(id);
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("reason", "RESCHEDULE_REASON_REQUIRED", "error.calendar.rescheduleReasonRequired");
        }
        event.setEventDate(newDate);
        event.setEventTime(newTime);
        repository.save(event);
        return CalendarEventResponse.from(event);
    }

    private CalendarEvent requireOwnedEvent(UUID id) {
        UUID orgId = tenantContextBinder.currentOrgId();
        return repository.findByIdAndOrgIdAndDeletedAtIsNull(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
    }

    private CalendarEvent requireManual(UUID id) {
        CalendarEvent event = requireOwnedEvent(id);
        if (event.getSource() != CalendarEvent.Source.MANUAL) {
            throw new ForbiddenException("error.calendar.autoEventNotEditable");
        }
        return event;
    }
}
