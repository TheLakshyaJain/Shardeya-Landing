-- 06-BROKER-NETWORK-ENGINE.md §8a -- BrokerCommissionPaymentService.record()
-- enqueues a BROKER_COMMISSION_PAID notification on every payout;
-- notification_type.code has a real FK to this table
-- (NotificationPreferenceService's own bugfix, documented in CLAUDE.md,
-- established that an unseeded code is a genuine insert-time constraint
-- violation, not a soft failure), so this row must exist before that
-- outbox event is ever dispatched -- new migration, never editing an
-- already-applied one (V6_013), same convention every prior notification-
-- type addition in this codebase has followed.
INSERT INTO notification_type (code, category, template_key_en, template_key_hi, description) VALUES
    ('BROKER_COMMISSION_PAID', 'BROKER', 'notification.brokerCommissionPaid', 'notification.brokerCommissionPaid', 'A commission payout was recorded for a network (designation) broker')
ON CONFLICT (code) DO NOTHING;
