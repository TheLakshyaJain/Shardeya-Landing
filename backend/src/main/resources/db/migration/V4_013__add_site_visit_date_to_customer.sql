-- Site Visit calendar events (CalendarEvent.EventType.SITE_VISIT) were declared,
-- styled, and given an i18n label but never wired to anything -- there was no
-- date field on customer to source the projection from at all. This adds the
-- same shape followUpDate already has, so CustomerService can auto-project a
-- SITE_VISIT calendar event the same way it already does for FOLLOW_UP.
ALTER TABLE customer ADD COLUMN site_visit_date DATE;
