-- M-06 Notification Engine (01-DATA-MODEL.md §8 notification_type) -- a
-- platform reference table, same shape as measurement_unit: no org_id, no
-- RLS, shared read-only catalogue across every tenant.
--
-- M3 scope is in-app bell only (05-MILESTONES.md M3: "M-06 Notification
-- Engine (in-app bell only -- WhatsApp/SMS in M7; outbox pattern set up
-- here)"). default_channels/is_mandatory are included for schema
-- completeness per the data model but aren't functionally consulted until
-- M7 adds real multi-channel delivery -- every M3 notification is in-app
-- only regardless of this column's value.
CREATE TABLE notification_type (
    code                VARCHAR(60) PRIMARY KEY,
    category            VARCHAR(60) NOT NULL,
    default_channels     VARCHAR(20)[] NOT NULL DEFAULT ARRAY['IN_APP'],
    is_mandatory          BOOLEAN NOT NULL DEFAULT true,
    template_key_en       VARCHAR(120) NOT NULL,
    template_key_hi       VARCHAR(120) NOT NULL,
    description           TEXT
);

-- Only the types M3 actually fires. Booking-confirmation WhatsApp,
-- broker-commission-due, and broker-notified-of-deal are all real B-04/B-05
-- notifications per spec, but they depend on WhatsApp delivery (M7) or the
-- broker network (B-14) which don't exist yet -- seeded here would be dead
-- catalogue rows with nothing to ever fire them. Add those when their
-- dependency milestone lands.
INSERT INTO notification_type (code, category, template_key_en, template_key_hi, description) VALUES
    ('PLOT_SOLD', 'SALE', 'notification.plotSold', 'notification.plotSold', 'A plot was marked sold'),
    ('SALE_CANCELLED', 'SALE', 'notification.saleCancelled', 'notification.saleCancelled', 'A sale was cancelled'),
    ('PAYMENT_RECORDED', 'PAYMENT', 'notification.paymentRecorded', 'notification.paymentRecorded', 'A payment was recorded'),
    ('CHEQUE_BOUNCED', 'PAYMENT', 'notification.chequeBounced', 'notification.chequeBounced', 'A cheque payment bounced'),
    ('INSTALMENT_OVERDUE', 'PAYMENT', 'notification.instalmentOverdue', 'notification.instalmentOverdue', 'An instalment became overdue')
ON CONFLICT (code) DO NOTHING;
