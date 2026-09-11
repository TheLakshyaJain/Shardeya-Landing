-- notification.type_code has an FK to notification_type(code) (V3_008) --
-- missed this one when V4_008 seeded the rest of the lead/team catalogue.
-- M-12 §12: "Status changed to Deal Closed -> in-app to owner."
INSERT INTO notification_type (code, category, template_key_en, template_key_hi, description) VALUES
    ('LEAD_DEAL_CLOSED', 'LEAD', 'notification.dealClosed', 'notification.dealClosed', 'A lead''s deal was closed')
ON CONFLICT (code) DO NOTHING;
