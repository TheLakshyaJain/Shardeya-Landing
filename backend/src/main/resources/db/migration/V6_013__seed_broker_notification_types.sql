-- B-14 §12/§20.4 -- the exact rows V3_007's own comment already
-- anticipated: "broker-commission-due, and broker-notified-of-deal are
-- all real B-04/B-05 notifications per spec, but they depend on...
-- the broker network (B-14) which don't exist yet... Add those when their
-- dependency milestone lands." This is that milestone.
INSERT INTO notification_type (code, category, template_key_en, template_key_hi, description) VALUES
    ('COMMISSION_DUE', 'BROKER', 'notification.commissionDue', 'notification.commissionDue', 'Broker commission became due after a deal closed'),
    ('BROKER_TIER_UPGRADED', 'BROKER', 'notification.brokerTierUpgraded', 'notification.brokerTierUpgraded', 'A broker was automatically upgraded to a higher tier'),
    ('COMMISSION_PAYMENT_RECORDED', 'BROKER', 'notification.commissionPaymentRecorded', 'notification.commissionPaymentRecorded', 'A commission payment was recorded'),
    ('BROKER_ADDED', 'BROKER', 'notification.brokerAdded', 'notification.brokerAdded', 'A new broker was added to the network'),
    ('COMMISSION_OVERDUE', 'BROKER', 'notification.commissionOverdue', 'notification.commissionOverdue', 'A commission payment has been pending for over 30 days')
ON CONFLICT (code) DO NOTHING;
