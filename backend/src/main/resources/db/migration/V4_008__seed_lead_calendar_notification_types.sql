-- New notification_type codes for B-07 (lead pipeline) and B-12 (team/staff)
-- events, in-app only -- same M3-established boundary (V3_007's own
-- comment): WhatsApp/SMS/email channels are M7 scope regardless of what
-- 03-BUILDER-MODULES.md's own §12 sections describe as the eventual
-- multi-channel behaviour. Calendar itself (M-11) fires no notification of
-- its own -- reminders are driven off the *source* record's own
-- notification (a follow-up reminder is really "FOLLOWUP_DUE" firing off the
-- customer, not a distinct calendar-level type).
INSERT INTO notification_type (code, category, template_key_en, template_key_hi, description) VALUES
    ('LEAD_CREATED', 'LEAD', 'notification.leadCreated', 'notification.leadCreated', 'A new lead was added'),
    ('LEAD_ASSIGNED', 'LEAD', 'notification.leadAssigned', 'notification.leadAssigned', 'A lead was assigned or reassigned'),
    ('FOLLOWUP_DUE', 'LEAD', 'notification.followupDue', 'notification.followupDue', 'A follow-up is due today'),
    ('LEAD_PLOT_SOLD', 'LEAD', 'notification.leadPlotSold', 'notification.leadPlotSold', 'A lead''s interested plot was sold to someone else'),
    ('STAFF_ADDED', 'TEAM', 'notification.staffAdded', 'notification.staffAdded', 'A staff member was added or their role changed'),
    ('STAFF_DEACTIVATED', 'TEAM', 'notification.staffDeactivated', 'notification.staffDeactivated', 'A staff member was deactivated'),
    ('INVITE_ACCEPTED', 'TEAM', 'notification.inviteAccepted', 'notification.inviteAccepted', 'A staff invite was accepted')
ON CONFLICT (code) DO NOTHING;
