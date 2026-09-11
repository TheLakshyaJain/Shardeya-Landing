-- Exact FREE-tier values from 02-FOUNDATION-MODULES.md M-09 §23.1. PRO/PREMIUM
-- rows are deferred to whenever a plan row for them actually exists (see
-- V1_007's own note — same reasoning, undefined real-world pricing shouldn't
-- be guessed at). M2's exit criteria only requires FREE-plan enforcement
-- (project quota, plots-per-project quota) — this is deliberately narrower
-- than the full §23.1 matrix.
INSERT INTO plan_limit (plan_code, limit_key, limit_value) VALUES
    ('FREE', 'BROKER_PROPERTIES', '10'),
    ('FREE', 'BROKER_CUSTOMERS', '25'),
    ('FREE', 'BUILDER_PROJECTS', '1'),
    ('FREE', 'BUILDER_PLOTS_PER_PROJECT', '50'),
    ('FREE', 'BUILDER_TEAM_MEMBERS', '1'),
    ('FREE', 'BUILDER_BROKERS', '0'),
    ('FREE', 'EXPORT_ENABLED', '0'),
    ('FREE', 'BULK_UPLOAD_ENABLED', '0'),
    ('FREE', 'WHATSAPP_ENABLED', '0'),
    ('FREE', 'LEGAL_DOCS', 'NONE'),
    ('FREE', 'ANALYTICS', 'BASIC'),
    ('FREE', 'BACKUP_FREQUENCY', 'NONE')
ON CONFLICT (plan_code, limit_key) DO NOTHING;
