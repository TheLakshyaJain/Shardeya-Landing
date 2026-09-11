-- M1 seeds only the two "owner" roles (M-02's core slice per 05-MILESTONES.md
-- M1: "broker owner + builder admin roles; staff roles in M4"). MANAGER,
-- SALES_EXECUTIVE, ACCOUNTS_STAFF, VIEW_ONLY are deferred to M4 when the
-- Team/Staff module actually needs them.
INSERT INTO role (id, org_id, code, name_en, name_hi, is_system) VALUES
    (gen_random_uuid(), NULL, 'BUILDER_ADMIN', 'Admin', 'एडमिन', true),
    (gen_random_uuid(), NULL, 'BROKER_OWNER', 'Owner', 'मालिक', true)
ON CONFLICT (code) WHERE org_id IS NULL DO NOTHING;

-- BUILDER_ADMIN: every permission in the catalogue (02-FOUNDATION-MODULES.md
-- M-02 §3 table — the Admin/Owner column is checked for every group).
INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, p.code
FROM role r
CROSS JOIN (VALUES
    ('DATA_VIEW_ALL'), ('DATA_CREATE'), ('DATA_EDIT_ALL'), ('DATA_DELETE'),
    ('FINANCIAL_VIEW'), ('FINANCIAL_RECORD_PAYMENT'), ('FINANCIAL_EDIT'),
    ('TEAM_VIEW'), ('TEAM_MANAGE'),
    ('PROJECT_CREATE'), ('PROJECT_EDIT'), ('PROJECT_DELETE'),
    ('PLOT_CREATE'), ('PLOT_EDIT'), ('PLOT_DELETE'), ('PLOT_BULK_UPLOAD'),
    ('LEAD_ASSIGN'),
    ('BROKER_VIEW'), ('BROKER_MANAGE'), ('BROKER_COMMISSION_PAY'),
    ('REPORT_VIEW_ALL'), ('REPORT_FINANCIAL'),
    ('DOCUMENT_GENERATE'), ('DOCUMENT_TEMPLATE_EDIT'),
    ('SENSITIVE_VIEW'), ('EXPORT_DATA'), ('IMPORT_DATA'),
    ('SETTINGS_MANAGE'), ('SUBSCRIPTION_MANAGE')
) AS p(code)
WHERE r.code = 'BUILDER_ADMIN' AND r.org_id IS NULL
ON CONFLICT DO NOTHING;

-- BROKER_OWNER: every permission except the Builder-only inventory/broker-network
-- ones (02-FOUNDATION-MODULES.md M-01 §1: brokers don't have projects/plots, and
-- BROKER_* permissions govern a *builder's* external broker network, not a
-- broker's own account). "BROKER_OWNER gets every permission except the
-- Builder-only ones" (M-02 §3).
INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, p.code
FROM role r
CROSS JOIN (VALUES
    ('DATA_VIEW_ALL'), ('DATA_CREATE'), ('DATA_EDIT_ALL'), ('DATA_DELETE'),
    ('FINANCIAL_VIEW'), ('FINANCIAL_RECORD_PAYMENT'), ('FINANCIAL_EDIT'),
    ('TEAM_VIEW'), ('TEAM_MANAGE'),
    ('REPORT_VIEW_ALL'), ('REPORT_FINANCIAL'),
    ('DOCUMENT_GENERATE'), ('DOCUMENT_TEMPLATE_EDIT'),
    ('SENSITIVE_VIEW'), ('EXPORT_DATA'), ('IMPORT_DATA'),
    ('SETTINGS_MANAGE'), ('SUBSCRIPTION_MANAGE')
) AS p(code)
WHERE r.code = 'BROKER_OWNER' AND r.org_id IS NULL
ON CONFLICT DO NOTHING;
