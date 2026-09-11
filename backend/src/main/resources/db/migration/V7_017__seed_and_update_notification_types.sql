-- M-06 §22, second half. Two things:
--
-- 1. INSTALMENT_DUE_TODAY is a new, distinct catalogue code -- the §22.3
--    trigger table lists "Instalment due today" and "Instalment overdue
--    3+ days" as two separate rows with different timing, not one type at
--    two severities, so this needs its own code rather than overloading
--    the existing INSTALMENT_OVERDUE.
--
-- 2. Every notification_type seeded across V3_007/V4_008/V4_012/V6_013/
--    V7_009 left default_channels at the column default (ARRAY['IN_APP'])
--    and is_mandatory at true, regardless of what §22.2/§22.3's own tables
--    actually specify per type -- those columns existed since M3 for
--    schema completeness but were never functionally consulted until this
--    build (see V3_007's own comment). This is the migration that finally
--    makes them mean something: real per-type channel defaults, and
--    mandatory narrowed to genuinely payment/security-shaped events only
--    (matching M-06 §22 "security, subscription, payment confirmations
--    cannot be silenced" -- everything else is real is_mandatory=false so
--    the preference matrix can actually toggle it).
INSERT INTO notification_type (code, category, template_key_en, template_key_hi, description, default_channels, is_mandatory) VALUES
    ('INSTALMENT_DUE_TODAY', 'PAYMENT', 'notification.instalmentDueToday', 'notification.instalmentDueToday',
     'An instalment is due today', ARRAY['IN_APP','WHATSAPP'], true)
ON CONFLICT (code) DO UPDATE SET
    default_channels = EXCLUDED.default_channels,
    is_mandatory = EXCLUDED.is_mandatory;

UPDATE notification_type SET default_channels = ARRAY['IN_APP','WHATSAPP'], is_mandatory = true
    WHERE code = 'INSTALMENT_OVERDUE';
UPDATE notification_type SET default_channels = ARRAY['IN_APP','WHATSAPP'], is_mandatory = false
    WHERE code = 'FOLLOWUP_DUE';
UPDATE notification_type SET default_channels = ARRAY['IN_APP','EMAIL'], is_mandatory = false
    WHERE code IN ('COMMISSION_DUE', 'STAFF_ADDED');
UPDATE notification_type SET is_mandatory = true
    WHERE code IN ('PAYMENT_RECORDED', 'CHEQUE_BOUNCED', 'INVITE_ACCEPTED', 'STAFF_DEACTIVATED');
UPDATE notification_type SET is_mandatory = false
    WHERE code IN ('PLOT_SOLD', 'SALE_CANCELLED', 'LEAD_CREATED', 'LEAD_ASSIGNED', 'LEAD_PLOT_SOLD',
                    'LEAD_DEAL_CLOSED', 'BROKER_TIER_UPGRADED', 'COMMISSION_PAYMENT_RECORDED', 'BROKER_ADDED',
                    'COMMISSION_OVERDUE', 'DOCUMENT_GENERATED', 'BULK_GENERATION_COMPLETE',
                    'DOCUMENT_GENERATION_FAILED', 'TEMPLATE_ACTIVATED');
