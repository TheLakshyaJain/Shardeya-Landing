-- The four staff roles M1's seed deferred to M4 (see that migration's own
-- comment). Permission assignments follow 02-FOUNDATION-MODULES.md M-02 §3's
-- role matrix, refined against each module's own, more specific §9
-- Permissions section where the two disagree in granularity -- notably
-- FINANCIAL_EDIT (reverse a payment / edit a schedule row) is granted to
-- Admin + Accounts Staff only per 03-BUILDER-MODULES.md B-05 §9 ("Reverse:
-- FINANCIAL_EDIT (Admin, Accounts)"), even though M-02's coarse per-category
-- table shows Manager's "Financial access" column as a blanket checkmark
-- alongside Accounts'. Manager still gets FINANCIAL_VIEW and
-- FINANCIAL_RECORD_PAYMENT (B-05 explicitly includes Manager in "Record
-- payment"), just not the correction/edit action -- treated as the more
-- authoritative, action-level source over the summary table. B-05 §9 also
-- says "Waive: Admin only", but the catalogue has no separate waive
-- permission and ScheduleController already gates its one waive endpoint
-- with the same FINANCIAL_EDIT as reverse/edit-schedule (a pre-existing M3
-- simplification, not something this migration invents); Accounts Staff can
-- therefore waive too under current enforcement -- a known, narrow gap
-- documented in CLAUDE.md rather than silently reinterpreted here.
INSERT INTO role (id, org_id, code, name_en, name_hi, is_system) VALUES
    (gen_random_uuid(), NULL, 'MANAGER', 'Manager', 'मैनेजर', true),
    (gen_random_uuid(), NULL, 'SALES_EXECUTIVE', 'Sales Executive', 'सेल्स एग्जीक्यूटिव', true),
    (gen_random_uuid(), NULL, 'ACCOUNTS_STAFF', 'Accounts Staff', 'अकाउंट्स स्टाफ', true),
    (gen_random_uuid(), NULL, 'VIEW_ONLY', 'View Only', 'केवल देखने के लिए', true)
ON CONFLICT (code) WHERE org_id IS NULL DO NOTHING;

-- MANAGER: full data access, full financial visibility + payment recording
-- (not reverse/edit), no team management, no delete, no subscription.
INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, p.code
FROM role r
CROSS JOIN (VALUES
    ('DATA_VIEW_ALL'), ('DATA_CREATE'), ('DATA_EDIT_ALL'),
    ('FINANCIAL_VIEW'), ('FINANCIAL_RECORD_PAYMENT'),
    ('TEAM_VIEW'),
    ('PROJECT_CREATE'), ('PROJECT_EDIT'),
    ('PLOT_CREATE'), ('PLOT_EDIT'), ('PLOT_BULK_UPLOAD'),
    ('LEAD_ASSIGN'),
    ('BROKER_VIEW'), ('BROKER_MANAGE'), ('BROKER_COMMISSION_PAY'),
    ('REPORT_VIEW_ALL'), ('REPORT_FINANCIAL'),
    ('DOCUMENT_GENERATE'),
    ('SENSITIVE_VIEW'), ('EXPORT_DATA'), ('IMPORT_DATA')
) AS p(code)
WHERE r.code = 'MANAGER' AND r.org_id IS NULL
ON CONFLICT DO NOTHING;

-- SALES_EXECUTIVE: own leads only (DATA_VIEW_OWN/DATA_EDIT_OWN, never the
-- _ALL variants), no financial access whatsoever (B-04/B-05: "cannot create
-- a sale", "sees no payment data at all"), read-only on projects/plots
-- (view there needs no permission at all per B-02/B-03 -- see PlotController/
-- ProjectController: their GET endpoints carry no @RequiresPermission), own
-- reports only.
INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, p.code
FROM role r
CROSS JOIN (VALUES
    ('DATA_VIEW_OWN'), ('DATA_CREATE'), ('DATA_EDIT_OWN'),
    ('REPORT_VIEW_OWN')
) AS p(code)
WHERE r.code = 'SALES_EXECUTIVE' AND r.org_id IS NULL
ON CONFLICT DO NOTHING;

-- ACCOUNTS_STAFF: financials only, explicitly NO lead/customer access at all
-- (B-07 §9: "Accounts Staff: no lead access") -- deliberately no DATA_VIEW_*/
-- DATA_CREATE/DATA_EDIT_* granted, since those would otherwise open the lead
-- pipeline to them; BuilderCustomerService enforces "neither ALL nor OWN ->
-- reject" explicitly for this role rather than silently returning zero rows.
INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, p.code
FROM role r
CROSS JOIN (VALUES
    ('FINANCIAL_VIEW'), ('FINANCIAL_RECORD_PAYMENT'), ('FINANCIAL_EDIT'),
    ('BROKER_VIEW'), ('BROKER_COMMISSION_PAY'),
    ('REPORT_FINANCIAL'),
    ('DOCUMENT_GENERATE'),
    ('SENSITIVE_VIEW'), ('EXPORT_DATA')
) AS p(code)
WHERE r.code = 'ACCOUNTS_STAFF' AND r.org_id IS NULL
ON CONFLICT DO NOTHING;

-- VIEW_ONLY: read everything (M-02: "View all data ✅") except the carve-outs
-- the table marks ❌ for this role -- financials, broker management,
-- sensitive documents, subscription. No create/edit/delete of any kind.
INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, p.code
FROM role r
CROSS JOIN (VALUES
    ('DATA_VIEW_ALL'),
    ('REPORT_VIEW_ALL'),
    ('EXPORT_DATA')
) AS p(code)
WHERE r.code = 'VIEW_ONLY' AND r.org_id IS NULL
ON CONFLICT DO NOTHING;
