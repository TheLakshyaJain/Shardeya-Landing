-- B-05 Payment Tracker (01-DATA-MODEL.md §4 payment_schedule / §12.3.4).
-- "Expected" ledger -- what should be paid. Kept entirely separate from
-- payment_record ("actual" ledger) per B-05 §7's own explicit warning
-- against merging them.
CREATE TYPE payment_schedule_status AS ENUM ('PENDING', 'PARTIALLY_PAID', 'PAID', 'OVERDUE', 'WAIVED');

CREATE TABLE payment_schedule (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                  UUID NOT NULL REFERENCES organization(id),
    plot_sale_id            UUID NOT NULL REFERENCES plot_sale(id),
    sequence_no             SMALLINT NOT NULL,
    label                   VARCHAR(80),
    expected_amount         NUMERIC(19,2) NOT NULL,
    due_date                DATE NOT NULL,
    status                  payment_schedule_status NOT NULL DEFAULT 'PENDING',
    -- Maintained by a trigger from payment_allocation (V3_009) -- never
    -- written directly by application code.
    amount_allocated        NUMERIC(19,2) NOT NULL DEFAULT 0,
    reminder_enabled        BOOLEAN NOT NULL DEFAULT true,
    last_reminder_sent_at   TIMESTAMPTZ,
    waive_reason            TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by              UUID REFERENCES app_user(id),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by              UUID REFERENCES app_user(id),
    deleted_at               TIMESTAMPTZ,
    deleted_by               UUID REFERENCES app_user(id),
    version                  BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_payment_schedule_org ON payment_schedule(org_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_schedule_seq ON payment_schedule(plot_sale_id, sequence_no) WHERE deleted_at IS NULL;
CREATE INDEX ix_sched_due ON payment_schedule(org_id, due_date, status) WHERE deleted_at IS NULL;

ALTER TABLE payment_schedule ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment_schedule FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON payment_schedule
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
