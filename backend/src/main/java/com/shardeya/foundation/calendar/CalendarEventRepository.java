package com.shardeya.foundation.calendar;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, UUID>, JpaSpecificationExecutor<CalendarEvent> {

    Optional<CalendarEvent> findByIdAndOrgIdAndDeletedAtIsNull(UUID id, UUID orgId);

    /** The lookup half of the idempotent-projection-upsert contract -- keyed exactly on the DB's own unique partial index (V4_005). */
    Optional<CalendarEvent> findBySourceEntityTypeAndSourceEntityIdAndEventTypeAndDeletedAtIsNull(
            CalendarEvent.SourceEntityType sourceEntityType, UUID sourceEntityId, CalendarEvent.EventType eventType);
}
