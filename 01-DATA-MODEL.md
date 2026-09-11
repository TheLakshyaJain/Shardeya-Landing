# Shardeya — Complete Data Model

PostgreSQL 16. All tables use `id UUID PRIMARY KEY DEFAULT gen_random_uuid()` unless noted.

**Universal columns** on every tenant-scoped table (abbreviated as `[STD]` below):

```sql
org_id       UUID NOT NULL REFERENCES organization(id),
created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
created_by   UUID REFERENCES app_user(id),
updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
updated_by   UUID REFERENCES app_user(id),
deleted_at   TIMESTAMPTZ,
deleted_by   UUID REFERENCES app_user(id),
version      BIGINT NOT NULL DEFAULT 0        -- optimistic locking
```

**Universal index** on every tenant table: `CREATE INDEX ix_<t>_org ON <t>(org_id) WHERE deleted_at IS NULL;`

**RLS on every tenant table:**
```sql
ALTER TABLE <t> ENABLE ROW LEVEL SECURITY;
ALTER TABLE <t> FORCE ROW LEVEL SECURITY;  -- see note below — do not omit
CREATE POLICY tenant_isolation ON <t>
  USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
```

> **`FORCE ROW LEVEL SECURITY` is mandatory, not optional.** PostgreSQL exempts a
> table's *owner* from its own RLS policies unless `FORCE` is also set. Since the
> application's runtime DB role is the same role that owns the tables (it runs the
> Flyway migrations), omitting `FORCE` makes every policy a silent no-op — verified
> empirically while building M0 (org B could read org A's rows with `ENABLE` alone).

> **The `true` (missing_ok) argument to `current_setting`, and the outer
> `NULLIF`, are both mandatory, not optional.** Two separate bugs stack here:
> (1) Without `missing_ok=true`, `current_setting('app.current_org')` *throws*
> `unrecognized configuration parameter` — not "returns NULL" — on any
> connection where the GUC was never SET at all in that session. That's a real
> path, not a theoretical one: looking up a system role (`role.org_id IS NULL`)
> during signup, before any organization exists and before there's any tenant
> context to set, hits it directly. (2) `missing_ok=true` alone still isn't
> enough: the app's connection wrapper `RESET`s this GUC for unauthenticated
> requests (rather than leaving it merely unset), and `RESET` on a custom,
> undeclared GUC sets it to an **empty string**, not NULL — so
> `current_setting(..., true)` can legitimately return `''`, and `''::uuid`
> fails with "invalid input syntax for type uuid". `NULLIF(..., '')` before the
> cast collapses both "never set" and "explicitly reset" to the same NULL,
> which is what the policy actually wants either way. Found while building
> M1's signup flow (`V1_010`). This and the ENABLE-vs-FORCE bug from M0 are the
> same shape of mistake — the RLS template looks right at a glance and only
> breaks on a code path you haven't written yet. Verify new tables against an
> actually-unset-context query, not just a cross-tenant one.
> `FORCE` is what makes RLS an actual backstop instead of a false sense of one.

---

## 1. Identity & Tenancy

### `organization`
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| type | `org_type` ENUM | `BROKER`, `BUILDER` |
| name | VARCHAR(150) NOT NULL | Firm/colony company name; for broker = person's name |
| city | VARCHAR(100) NOT NULL | §2.1 |
| state_code | CHAR(2) | For stamp duty defaults |
| logo_media_id | UUID | Used on generated legal docs (§17.2) |
| default_language | CHAR(2) NOT NULL DEFAULT 'en' | `en` \| `hi` |
| timezone | VARCHAR(40) NOT NULL DEFAULT 'Asia/Kolkata' | |
| notification_hour | SMALLINT NOT NULL DEFAULT 9 | Local hour for "morning of" sends |
| status | ENUM | `ACTIVE`, `SUSPENDED`, `PENDING_DELETION` |
| deletion_requested_at | TIMESTAMPTZ | §25.4 |
| created_at / updated_at | TIMESTAMPTZ | |

### `app_user`
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| org_id | UUID NOT NULL FK | |
| full_name | VARCHAR(100) NOT NULL | §2.1 max 100 |
| mobile | VARCHAR(10) NOT NULL | 10-digit Indian |
| mobile_verified_at | TIMESTAMPTZ | Set after OTP |
| email | VARCHAR(255) | |
| email_verified_at | TIMESTAMPTZ | |
| password_hash | TEXT | NULL for staff who haven't accepted invite |
| role_id | UUID NOT NULL FK → `role` | |
| is_owner | BOOLEAN NOT NULL DEFAULT false | Exactly one per org |
| project_access_mode | ENUM NOT NULL DEFAULT 'ALL' | `ALL` \| `SCOPED` (§18.2) |
| language | CHAR(2) NOT NULL DEFAULT 'en' | Overrides org default (§3.3) |
| status | ENUM | `INVITED`, `ACTIVE`, `INACTIVE`, `REMOVED` |
| last_login_at | TIMESTAMPTZ | Feeds `Last Active` |
| failed_login_count | SMALLINT NOT NULL DEFAULT 0 | |
| locked_until | TIMESTAMPTZ | |
| `[STD]` | | |

```sql
CREATE UNIQUE INDEX ux_user_mobile ON app_user(mobile) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_user_email  ON app_user(lower(email)) WHERE email IS NOT NULL AND deleted_at IS NULL;
CREATE UNIQUE INDEX ux_org_one_owner ON app_user(org_id) WHERE is_owner AND deleted_at IS NULL;
```
> **Decision:** mobile/email are globally unique, not per-org. A person is one login. Supporting the same mobile as a broker *and* a builder staff member would require an account-switcher; deferred to post-launch (`user_org_membership` join table is the migration path, designed for but not built).

### `user_project_access`
`(user_id, project_id)` — rows only exist when `project_access_mode = 'SCOPED'` (§18.2).

### `role`
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| org_id | UUID NULL | NULL = system role, non-NULL = tenant custom role (future) |
| code | VARCHAR(40) NOT NULL | `BUILDER_ADMIN`, `MANAGER`, `SALES_EXECUTIVE`, `ACCOUNTS_STAFF`, `VIEW_ONLY`, `BROKER_OWNER`, `PLATFORM_ADMIN` |
| name_en / name_hi | VARCHAR(60) | |
| is_system | BOOLEAN NOT NULL | System roles are immutable |

### `role_permission`
`(role_id, permission_code)` — see M-02 for the full permission catalogue.

### `refresh_token`
`id, user_id, token_hash, family_id, issued_at, expires_at, revoked_at, replaced_by, user_agent, ip` — reuse detection revokes the whole `family_id`.

### `otp_challenge`
`id, purpose (SIGNUP|LOGIN|RESET|STAFF_INVITE), mobile, code_hash, attempts, max_attempts, expires_at, consumed_at, created_ip` — Redis is primary; this table exists for audit/forensics only.

---

## 2. Reference Data (platform-managed)

### `measurement_unit`
| code | name_en | name_hi | to_sqft_factor | state_code | is_active |
|---|---|---|---|---|---|
| SQ_FT | Square Feet | वर्ग फुट | 1.0 | NULL | true |
| SQ_M | Square Metre | वर्ग मीटर | 10.7639 | NULL | true |
| SQ_YD | Square Yard (Gaj) | गज | 9.0 | NULL | true |
| ACRE | Acre | एकड़ | 43560.0 | NULL | true |
| BIGHA | Bigha | बीघा | 27000.0 | UP | true |
| BIGHA | Bigha | बीघा | 27225.0 | RJ | true |
| GUNTA | Gunta | गुंठा | 1089.0 | NULL | true |
| DISMIL | Dismil | डिसमिल | 435.6 | NULL | true |

PK `(code, coalesce(state_code,'--'))`. §3.4.1 and §4.2 of the architecture depend on this.

### `stamp_duty_rate` (§3.4.3 — *"updatable from admin panel"*)
| Column | Type |
|---|---|
| id | UUID PK |
| state_code | CHAR(2) NOT NULL |
| property_type | ENUM `RESIDENTIAL`,`COMMERCIAL`,`AGRICULTURAL` |
| transaction_type | ENUM `SALE`,`GIFT`,`MORTGAGE` |
| buyer_gender | ENUM `MALE`,`FEMALE`,`JOINT`,`ANY` |
| stamp_duty_pct | NUMERIC(6,3) NOT NULL |
| registration_pct | NUMERIC(6,3) |
| registration_flat | NUMERIC(19,2) |
| registration_cap | NUMERIC(19,2) |
| effective_from | DATE NOT NULL |
| effective_to | DATE |
| source_note | TEXT |

`UNIQUE(state_code, property_type, transaction_type, buyer_gender, effective_from)`. Lookup picks the row where `effective_from <= today` and (`effective_to IS NULL OR effective_to >= today`), falling back `buyer_gender=ANY`.

### `locality` (optional autocomplete)
`id, city, state_code, name, pincode` — seeded, tenant-extendable via `org_locality`.

---

## 3. Customer / Lead Core (M-12) — shared by both profiles

### `customer`
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| full_name | VARCHAR(120) NOT NULL | §6.2 / §13.2 |
| mobile | VARCHAR(15) NOT NULL | |
| alternate_mobile | VARCHAR(15) | |
| email | VARCHAR(255) | |
| budget_min | NUMERIC(19,2) NOT NULL | |
| budget_max | NUMERIC(19,2) NOT NULL | `CHECK (budget_max >= budget_min)` |
| preferred_property_type | ENUM `PLOT`,`FLAT`,`HOUSE` | Broker (§6.2) |
| preferred_locality | VARCHAR(150) | Broker |
| size_requirement | VARCHAR(80) | Free text, e.g. "2BHK", "1000–1200 sqft" |
| source | ENUM | `REFERRAL`,`FACEBOOK`,`INSTAGRAM`,`WALK_IN`,`COLD_CALL`,`WEBSITE`,`BROKER`,`EXHIBITION`,`SOCIAL_MEDIA`,`OTHER` |
| source_broker_id | UUID FK → `broker_partner` | Builder only (§13.2) |
| status | `lead_status` ENUM NOT NULL | `INTERESTED`,`SITE_VISIT_SCHEDULED`,`SITE_VISIT_DONE`,`FOLLOWING_UP`,`DEAL_CLOSED`,`LOST` |
| interested_project_id | UUID FK → `project` | Builder (§13.2) |
| interested_plot_id | UUID FK → `plot` | Builder |
| assigned_to | UUID FK → `app_user` | Builder (§13.1 "Assigned To") |
| follow_up_date | DATE | Next scheduled follow-up |
| no_further_follow_up | BOOLEAN NOT NULL DEFAULT false | §6.2 — suppresses reminders |
| is_important | BOOLEAN NOT NULL DEFAULT false | §6.4 gold star |
| remarks | TEXT | Latest summary |
| last_interaction_at | TIMESTAMPTZ | Denormalised for list sort |
| closed_at | DATE | Set when status → DEAL_CLOSED/LOST |
| search_vector | TSVECTOR GENERATED | name + mobile + remarks |
| `[STD]` | | |

```sql
CREATE INDEX ix_cust_followup   ON customer(org_id, follow_up_date)
  WHERE deleted_at IS NULL AND no_further_follow_up = false
    AND status NOT IN ('DEAL_CLOSED','LOST');
CREATE INDEX ix_cust_status     ON customer(org_id, status) WHERE deleted_at IS NULL;
CREATE INDEX ix_cust_assigned   ON customer(org_id, assigned_to) WHERE deleted_at IS NULL;
CREATE INDEX ix_cust_project    ON customer(org_id, interested_project_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_cust_important  ON customer(org_id) WHERE is_important AND deleted_at IS NULL;
CREATE INDEX ix_cust_search     ON customer USING GIN(search_vector);
CREATE INDEX ix_cust_mobile_trgm ON customer USING GIN(mobile gin_trgm_ops);
```

### `customer_property_interest` (Broker, §6.2 multi-select / §5.2.3)
`id, customer_id, property_id, [STD]` — many-to-many. `UNIQUE(customer_id, property_id) WHERE deleted_at IS NULL`.

### `interaction` — the universal follow-up log (§5.2.4, §6.5, §13.3)
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| customer_id | UUID NOT NULL FK | |
| property_id | UUID FK | Broker: which property this follow-up concerned (§5.2.4) |
| project_id / plot_id | UUID FK | Builder context |
| deal_id | UUID FK → `deal` | |
| occurred_on | DATE NOT NULL | "Follow-up Date" of the entry |
| type | ENUM NOT NULL | `CALL`,`VISIT`,`WHATSAPP`,`MEETING`,`EMAIL`,`SMS`,`NOTE` |
| remarks | TEXT NOT NULL | |
| next_follow_up_date | DATE | Writes back to `customer.follow_up_date` |
| result | ENUM | `POSITIVE`,`NEUTRAL`,`NEGATIVE`,`NOT_INTERESTED`,`NEXT_SCHEDULED`,`NO_FURTHER` |
| conducted_by | UUID FK → `app_user` | §13.3 "Conducted By" |
| amended_at / amended_by / original_remarks | | 15-min grace amendment trail |
| `[STD]` (no `deleted_at` usage — deletion blocked by trigger) | | |

```sql
CREATE INDEX ix_interaction_cust ON interaction(customer_id, occurred_on DESC);
CREATE RULE no_delete_interaction AS ON DELETE TO interaction DO INSTEAD NOTHING;
```

---

## 4. Builder Domain

### `project` (§12.2.1)
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| name | VARCHAR(150) NOT NULL | |
| project_type | ENUM NOT NULL | `RESIDENTIAL_PLOT_COLONY`,`APARTMENT`,`VILLA`,`COMMERCIAL`,`MIXED_USE` |
| status | ENUM NOT NULL | `UPCOMING`,`ACTIVE`,`COMPLETED` |
| address | TEXT NOT NULL | |
| locality | VARCHAR(150) NOT NULL | |
| city | VARCHAR(100) NOT NULL | |
| state_code | CHAR(2) NOT NULL | |
| pincode | CHAR(6) | |
| google_maps_url | TEXT | |
| total_area_value | NUMERIC(14,4) NOT NULL | |
| total_area_unit | VARCHAR(16) NOT NULL | |
| total_area_sqft | NUMERIC(14,4) NOT NULL | Derived |
| declared_plot_count | INTEGER NOT NULL | §12.2.1 "Total Number of Plots" — target |
| launch_date | DATE | |
| expected_completion_date | DATE | |
| description | TEXT | |
| approvals | JSONB | Array of `{label, note, doc_media_id}` (§12.2.1 tags) |
| rera_number | VARCHAR(60) | |
| cover_media_id | UUID FK → `media_asset` | |
| layout_media_id | UUID FK → `media_asset` | Master layout/map |
| brochure_media_id | UUID FK → `media_asset` | |
| grid_rows / grid_cols | INTEGER | §12.2.2 grid configuration |
| grid_layout | JSONB | Sparse `{ "r3c7": plot_id }` map + blocked cells (roads/parks) |
| `[STD]` | | |

### `plot` (§12.3.1)
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| project_id | UUID NOT NULL FK | |
| plot_number | VARCHAR(30) NOT NULL | e.g. `A-12` |
| plot_number_norm | VARCHAR(30) GENERATED | `upper(regexp_replace(plot_number,'[^A-Za-z0-9]',''))` — for dedupe |
| status | `plot_status` ENUM NOT NULL DEFAULT 'AVAILABLE' | `AVAILABLE`,`RESERVED`,`SOLD` |
| reserved_for | VARCHAR(150) | §12.3.1 |
| reserved_until | DATE | *Added:* prevents indefinite phantom reservations (edge case) |
| size_value | NUMERIC(14,4) NOT NULL | |
| size_unit | VARCHAR(16) NOT NULL | |
| size_sqft | NUMERIC(14,4) NOT NULL | Derived — all filters/sorts use this |
| facing | ENUM | `N`,`S`,`E`,`W`,`NE`,`NW`,`SE`,`SW` |
| price | NUMERIC(19,2) NOT NULL | Total asking price |
| price_per_unit | NUMERIC(19,2) GENERATED | `price / NULLIF(size_value,0)` |
| is_garden | BOOLEAN NOT NULL DEFAULT false | |
| is_corner | BOOLEAN NOT NULL DEFAULT false | |
| is_hot | BOOLEAN NOT NULL DEFAULT false | Fire badge on grid |
| remarks | TEXT | |
| grid_row / grid_col | INTEGER | Cell position; NULL = unplaced (shown in an "unplaced" tray) |
| current_sale_id | UUID FK → `plot_sale` | Denormalised pointer to the active sale |
| `[STD]` | | |

```sql
CREATE UNIQUE INDEX ux_plot_number ON plot(project_id, plot_number_norm) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_plot_cell   ON plot(project_id, grid_row, grid_col)
  WHERE deleted_at IS NULL AND grid_row IS NOT NULL;
CREATE INDEX ix_plot_status ON plot(project_id, status) WHERE deleted_at IS NULL;
CREATE INDEX ix_plot_size   ON plot(project_id, size_sqft) WHERE deleted_at IS NULL;
CREATE INDEX ix_plot_num_trgm ON plot USING GIN(plot_number gin_trgm_ops);
```

### `plot_sale` (§12.3.2) — the booking/sale aggregate
Separating this from `plot` is essential: a plot can be sold, cancelled, and resold. `plot` holds *inventory*; `plot_sale` holds *the transaction*.

| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| plot_id | UUID NOT NULL FK | |
| project_id | UUID NOT NULL FK | Denormalised for reporting |
| customer_id | UUID FK → `customer` | If the buyer came through the lead pipeline |
| buyer_name | VARCHAR(120) NOT NULL | §12.3.2 |
| buyer_mobile | VARCHAR(15) NOT NULL | |
| buyer_email | VARCHAR(255) | |
| buyer_gov_id_type | ENUM `AADHAAR`,`PAN`,`PASSPORT`,`VOTER_ID`,`DL` | |
| buyer_gov_id_number_enc | BYTEA | **Encrypted at rest (pgcrypto/KMS)**, last-4 stored separately for display |
| buyer_gov_id_last4 | CHAR(4) | |
| buyer_gov_id_media_id | UUID FK | Scan — sensitive bucket |
| purchase_date | DATE NOT NULL | |
| deal_value | NUMERIC(19,2) NOT NULL | Agreed sale price (may differ from `plot.price`) |
| broker_partner_id | UUID FK → `broker_partner` | §12.3.2 |
| external_broker_name | VARCHAR(120) | If not in system |
| external_broker_mobile | VARCHAR(15) | |
| broker_commission_amount | NUMERIC(19,2) | Snapshot at sale time |
| payment_type | ENUM NOT NULL | `LUMP_SUM`,`INSTALMENT` |
| status | ENUM NOT NULL | `BOOKED`,`ACTIVE`,`COMPLETED`,`CANCELLED` |
| cancelled_at / cancellation_reason | | §16 cancelled deals |
| handled_by | UUID FK → `app_user` | §16 "Staff Handled By" |
| total_paid | NUMERIC(19,2) NOT NULL DEFAULT 0 | **Maintained by trigger** from `payment_record` |
| balance_due | NUMERIC(19,2) GENERATED | `deal_value - total_paid` |
| `[STD]` | | |

```sql
-- Only one non-cancelled sale per plot at a time
CREATE UNIQUE INDEX ux_plot_active_sale ON plot_sale(plot_id)
  WHERE status <> 'CANCELLED' AND deleted_at IS NULL;
```

### `payment_schedule` (§12.3.4 "Pending Instalment Tracker")
Expected/planned instalments — distinct from actual receipts.

| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| plot_sale_id | UUID NOT NULL FK | |
| sequence_no | SMALLINT NOT NULL | 1,2,3… |
| label | VARCHAR(80) | "Booking amount", "On registry" |
| expected_amount | NUMERIC(19,2) NOT NULL | |
| due_date | DATE NOT NULL | |
| status | ENUM NOT NULL DEFAULT 'PENDING' | `PENDING`,`PARTIALLY_PAID`,`PAID`,`OVERDUE`,`WAIVED` |
| amount_allocated | NUMERIC(19,2) NOT NULL DEFAULT 0 | Sum of allocations from receipts |
| reminder_enabled | BOOLEAN NOT NULL DEFAULT true | §12.3.4 toggle |
| last_reminder_sent_at | TIMESTAMPTZ | Debounce |
| `[STD]` | | |

`UNIQUE(plot_sale_id, sequence_no)`. `ix_sched_due ON payment_schedule(org_id, due_date, status)`.

### `payment_record` (§12.3.4 "Instalment History", §14.2)
Actual money received. **Immutable** — corrections are reversals.

| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| plot_sale_id | UUID NOT NULL FK | |
| project_id / plot_id | UUID | Denormalised for §14.2 table & reports |
| receipt_no | VARCHAR(30) NOT NULL | Generated: `{ORG_PREFIX}/{FY}/{seq}` — gapless per org per FY |
| amount | NUMERIC(19,2) NOT NULL CHECK (amount <> 0) | Negative = reversal |
| paid_on | DATE NOT NULL | |
| mode | ENUM NOT NULL | `CASH`,`CHEQUE`,`BANK_TRANSFER`,`UPI`,`DD` |
| reference | VARCHAR(120) | Cheque no / UTR / receipt note |
| cheque_status | ENUM | `PENDING`,`CLEARED`,`BOUNCED` — only for CHEQUE |
| received_by | UUID NOT NULL FK → `app_user` | §12.3.4 "Received By" |
| remarks | TEXT | |
| reverses_payment_id | UUID FK → `payment_record` | Contra entry |
| receipt_document_id | UUID FK → `generated_document` | |
| `[STD]` (no delete) | | |

`UNIQUE(org_id, receipt_no)`.

### `payment_allocation`
`id, payment_record_id, payment_schedule_id, amount` — a single receipt may settle part of one instalment or span several. This table is what makes "Amount Paid So Far", "Balance", and "Days Overdue" correct when a buyer pays ₹3L against two ₹2L instalments.

### `plot_document` (§12.3.3)
| Column | Type |
|---|---|
| id, plot_id, plot_sale_id | |
| doc_type | ENUM `SALE_AGREEMENT`,`REGISTRY_DEED`,`PLOT_MAP`,`BUYER_ID_PROOF`,`OTHER` |
| label | VARCHAR(120) — required when `OTHER` |
| media_id | UUID FK → `media_asset` |
| is_sensitive | BOOLEAN — true for `BUYER_ID_PROOF` → KMS bucket |
| `[STD]` | |

---

## 5. Broker Network (§20)

### `broker_partner`
| Column | Type | Notes |
|---|---|---|
| id, org_id | | Owned by the **builder** org |
| full_name | VARCHAR(120) NOT NULL | |
| mobile | VARCHAR(15) NOT NULL | `UNIQUE(org_id, mobile)` |
| email | VARCHAR(255) | |
| city_area | VARCHAR(150) | |
| rera_number | VARCHAR(60) | |
| firm_name | VARCHAR(150) | |
| commission_type | ENUM NOT NULL | `PERCENTAGE`,`FIXED` |
| commission_pct | NUMERIC(6,3) | Required if PERCENTAGE |
| commission_fixed | NUMERIC(19,2) | Required if FIXED |
| per_project_rates_enabled | BOOLEAN NOT NULL DEFAULT false | §20.2 toggle |
| bank_account_name | VARCHAR(150) | |
| bank_account_number_enc | BYTEA | Encrypted; last4 shown |
| bank_account_last4 | CHAR(4) | |
| ifsc | CHAR(11) | |
| upi_id | VARCHAR(80) | |
| notes | TEXT | |
| tier_id | UUID FK → `broker_tier` | Current tier |
| tier_manually_overridden | BOOLEAN NOT NULL DEFAULT false | §20.4 manual override |
| tier_assigned_at | TIMESTAMPTZ | |
| deals_closed_count | INTEGER NOT NULL DEFAULT 0 | Maintained by trigger |
| total_commission_earned | NUMERIC(19,2) NOT NULL DEFAULT 0 | Maintained |
| total_commission_paid | NUMERIC(19,2) NOT NULL DEFAULT 0 | Maintained |
| last_active_at | TIMESTAMPTZ | |
| status | ENUM | `ACTIVE`,`INACTIVE`,`BLOCKED` |
| linked_user_id | UUID FK → `app_user` | *Future:* broker logs in to see their own ledger |
| `[STD]` | | |

### `broker_commission_config` (§20.3)
| Column | Type | Notes |
|---|---|---|
| id, org_id | | |
| broker_partner_id | UUID NOT NULL FK | |
| scope | ENUM NOT NULL | `GLOBAL`,`PROJECT`,`PLOT` |
| project_id / plot_id | UUID FK | Per scope |
| commission_type | ENUM NOT NULL | `PERCENTAGE`,`FIXED` |
| rate_value | NUMERIC(19,3) NOT NULL | % or INR |
| effective_from | DATE NOT NULL | |
| effective_to | DATE | |
| `[STD]` | | |

Resolution precedence at sale time: **PLOT → PROJECT → GLOBAL → broker default**, filtered by `effective_from <= purchase_date`. The resolved value is *snapshotted* onto `commission_ledger_entry` so later rate changes never rewrite history.

### `broker_tier` (§20.4)
`id, org_id, name, name_hi, min_deals, max_deals, bonus_type (PCT|FIXED|NONE), bonus_value, badge_media_id, perks_description, sort_order, is_active`

Seeded per builder org on first use with Bronze 0–2, Silver 3–9, Gold 10–24, Platinum 25+. `CHECK (max_deals IS NULL OR max_deals >= min_deals)` and an exclusion constraint preventing overlapping ranges within an org.

### `commission_ledger_entry` (§20.5)
| Column | Type | Notes |
|---|---|---|
| id, org_id | | |
| broker_partner_id | UUID NOT NULL FK | |
| plot_sale_id | UUID NOT NULL FK | |
| project_id / plot_id | UUID | Denormalised |
| deal_date | DATE NOT NULL | |
| deal_value | NUMERIC(19,2) NOT NULL | Snapshot |
| base_commission | NUMERIC(19,2) NOT NULL | Resolved from config |
| tier_bonus | NUMERIC(19,2) NOT NULL DEFAULT 0 | From tier at deal time |
| total_commission | NUMERIC(19,2) GENERATED | base + bonus |
| amount_paid | NUMERIC(19,2) NOT NULL DEFAULT 0 | Maintained |
| balance_due | NUMERIC(19,2) GENERATED | total − paid |
| status | ENUM | `PENDING`,`PARTIALLY_PAID`,`PAID`,`CANCELLED` |
| config_snapshot | JSONB | The exact rule applied — audit |
| `[STD]` | | |

### `commission_payment`
`id, org_id, commission_ledger_entry_id, amount, paid_on, mode, reference, paid_by (user), remarks, [STD]` — immutable.

---

## 6. Broker Domain (§5)

### `property`
| Column | Type | Notes |
|---|---|---|
| id, org_id | | |
| title | VARCHAR(150) NOT NULL | §5.2.1 |
| address_line1 | VARCHAR(255) NOT NULL | |
| address_line2 | VARCHAR(255) | |
| locality | VARCHAR(150) NOT NULL | Powers area-wise filter |
| city | VARCHAR(100) NOT NULL | |
| pincode | CHAR(6) | |
| property_type | ENUM NOT NULL | `PLOT`,`FLAT`,`HOUSE` |
| transaction_type | ENUM NOT NULL | `SELL`,`RENT` |
| size_value / size_unit / size_sqft | | As per §4.2 |
| facing | ENUM | 8 directions |
| price | NUMERIC(19,2) NOT NULL | Asking price or monthly rent |
| is_price_negotiable | BOOLEAN NOT NULL DEFAULT false | |
| property_updated_date | DATE NOT NULL | §5.2.1 — physical verification date, drives the 30-day stale report |
| is_hot | BOOLEAN NOT NULL DEFAULT false | |
| is_garden_facing | BOOLEAN | |
| is_corner | BOOLEAN | |
| vastu_status | ENUM | `YES`,`NO`,`NOT_CHECKED` |
| owner_name | VARCHAR(120) NOT NULL | |
| owner_mobile | VARCHAR(15) NOT NULL | |
| description | TEXT | |
| total_floors | SMALLINT | Flat/House |
| floor_number | SMALLINT | `CHECK (floor_number <= total_floors)` |
| bedrooms / bathrooms | SMALLINT | |
| parking | ENUM | `NONE`,`COVERED`,`OPEN`,`YES` |
| age_bracket | ENUM | `NEW`,`UNDER_5`,`Y5_10`,`Y10_PLUS` |
| furnishing | ENUM | `UNFURNISHED`,`SEMI_FURNISHED`,`FULLY_FURNISHED` |
| video_url / virtual_tour_url | TEXT | §5.2.2 — validated against an allowlist (YouTube, Drive, Matterport, Vimeo) |
| video_media_id / brochure_media_id | UUID FK | |
| cover_media_id | UUID FK | First photo |
| deal_state | ENUM NOT NULL DEFAULT 'ACTIVE' | `ACTIVE`,`CLOSED`,`CANCELLED` — §5.1 filter |
| search_vector | TSVECTOR GENERATED | |
| `[STD]` | | |

```sql
CREATE INDEX ix_prop_stale ON property(org_id, property_updated_date) WHERE deleted_at IS NULL;
CREATE INDEX ix_prop_hot   ON property(org_id) WHERE is_hot AND deleted_at IS NULL;
CREATE INDEX ix_prop_filter ON property(org_id, property_type, transaction_type, deal_state) WHERE deleted_at IS NULL;
```

### `property_media`
`id, property_id, media_id, sort_order, media_role (PHOTO|VIDEO|BROCHURE), [STD]` — `sort_order = 0` is the cover (§5.2.2 drag reorder).

### `deal` (§5.2.5) — one per (property, customer) pairing
| Column | Type | Notes |
|---|---|---|
| id, org_id | | |
| property_id | UUID NOT NULL FK | |
| customer_id | UUID NOT NULL FK | |
| stage | `broker_deal_stage` ENUM NOT NULL | `INTERESTED`,`CALL_DONE`,`SITE_VISIT_DONE`,`FOLLOW_UPS_IN_PROGRESS`,`DEAL_CANCELLED`,`DEAL_FINALISED`,`AGREEMENT_REGISTRY_DONE`,`BROKERAGE_RECEIVED` |
| deal_value | NUMERIC(19,2) | Final agreed price |
| deal_type | ENUM | `SALE`,`RENT` — copied from property at finalisation |
| finalised_on / cancelled_on | DATE | |
| cancellation_reason | TEXT | |
| is_archived | BOOLEAN NOT NULL DEFAULT false | true once CANCELLED or BROKERAGE_RECEIVED (§5.2.5) |
| `[STD]` | | |

`UNIQUE(property_id, customer_id) WHERE deleted_at IS NULL`.

### `deal_stage_history`
`id, deal_id, from_stage, to_stage, changed_at, changed_by, note` — append-only.

### `brokerage_receipt` (§5.2.6, §7.2)
`id, org_id, deal_id, amount, gst_amount, received_from (OWNER|BUYER|BOTH), payment_date, payment_mode, receipt_note, [STD]` — a deal may have multiple partial receipts; §7.1 "Pending Brokerage" = deals at `DEAL_FINALISED`/`AGREEMENT_REGISTRY_DONE` with `sum(amount) < expected`.

---

## 7. Calendar (M-11, §8, §15)

### `calendar_event`
| Column | Type | Notes |
|---|---|---|
| id, org_id | | |
| title | VARCHAR(150) NOT NULL | |
| event_date | DATE NOT NULL | |
| event_time | TIME | Optional (§8.3) |
| duration_minutes | SMALLINT | |
| event_type | ENUM NOT NULL | `FOLLOW_UP`,`SITE_VISIT`,`INSTALMENT_DUE`,`MANUAL_MEETING`,`IMPORTANT_DATE` |
| source | ENUM NOT NULL | `MANUAL`,`AUTO` |
| source_entity_type / source_entity_id | | `CUSTOMER`,`PAYMENT_SCHEDULE`,`DEAL` — auto events are *projections*, regenerated on source change |
| customer_id / property_id / project_id / plot_id | UUID FK | Links (§8.3) |
| assigned_to | UUID FK → `app_user` | §15 staff filter |
| notes | TEXT | |
| reminder_enabled | BOOLEAN NOT NULL DEFAULT true | |
| reminder_offsets | INT[] DEFAULT '{0,1}' | Days before: 0 = same day, 1 = day before |
| status | ENUM | `SCHEDULED`,`DONE`,`CANCELLED` |
| `[STD]` | | |

`CREATE INDEX ix_cal_date ON calendar_event(org_id, event_date) WHERE deleted_at IS NULL;`
`UNIQUE(source_entity_type, source_entity_id, event_type) WHERE source='AUTO'` — idempotent projection.

---

## 8. Notifications (M-06, §22)

### `notification`
`id, org_id, recipient_user_id, type_code, title_key, body_key, params JSONB, entity_type, entity_id, priority, read_at, created_at` — `title_key`/`body_key` are i18n keys so the bell renders in the user's current language, not the language at send time.

`CREATE INDEX ix_notif_unread ON notification(recipient_user_id, created_at DESC) WHERE read_at IS NULL;`

### `notification_preference`
`id, org_id, user_id, type_code, in_app BOOLEAN, whatsapp BOOLEAN, sms BOOLEAN, email BOOLEAN` — defaults from a `notification_type` catalogue; user overrides stored sparsely.

### `notification_type` (platform reference)
`code, category, default_channels, is_mandatory, template_key_en, template_key_hi, description` — the catalogue in §22.2/§22.3/§22.4.

### `outbox_event`
`id, aggregate_type, aggregate_id, event_type, payload JSONB, available_at, attempts, status (PENDING|PROCESSING|DONE|FAILED), last_error, created_at`
`CREATE INDEX ix_outbox_ready ON outbox_event(available_at) WHERE status='PENDING';`

### `message_delivery`
`id, org_id, channel (WHATSAPP|SMS|EMAIL), recipient_masked, template_code, provider, provider_message_id, status (QUEUED|SENT|DELIVERED|READ|FAILED), error_code, cost_paise, sent_at, delivered_at, notification_id` — audit + billing + the §14.3 "Send WhatsApp Reminder" receipt.

### `whatsapp_optin`
`id, org_id, mobile, opted_in_at, opted_out_at, source` — §22.1 requires explicit opt-in. Buyers (§22.4) are third parties: we record consent captured by the builder and honour STOP replies.

---

## 9. Media & Documents (M-05)

### `media_asset`
`id, org_id, storage_key, bucket_class (STANDARD|SENSITIVE), original_filename, mime_type, size_bytes, checksum_sha256, width, height, duration_seconds, status (PENDING|SCANNING|READY|REJECTED), reject_reason, uploaded_by, derivatives JSONB, [STD]`

### `document_template` (§17.2 *"customisable from Admin Panel"*)
`id, org_id (NULL = system default), doc_type (ALLOTMENT_LETTER|PAYMENT_RECEIPT|DEMAND_LETTER|BOOKING_CONFIRMATION), name, language, body_html, header_html, footer_html, variables JSONB, version, is_active, [STD]`

Templates are **sandboxed**: a restricted expression language (allowlisted variables only, no arbitrary code), rendered server-side, output HTML-escaped.

### `generated_document`
`id, org_id, doc_type, template_id, template_version, entity_type, entity_id, media_id, rendered_snapshot JSONB, generated_by, generated_at, document_number` — the snapshot means a receipt reprinted in 2029 shows the 2026 figures.

---

## 10. Subscription (M-09, §23)

### `plan`
`code (FREE|PRO|PREMIUM), name_en, name_hi, price_monthly, price_yearly, sort_order, is_active`

### `plan_limit`
`plan_code, limit_key, limit_value` where `limit_value = -1` means unlimited.
Keys: `BROKER_PROPERTIES`, `BROKER_CUSTOMERS`, `BUILDER_PROJECTS`, `BUILDER_PLOTS_PER_PROJECT`, `BUILDER_TEAM_MEMBERS`, `BUILDER_BROKERS`, `EXPORT_ENABLED`, `BULK_UPLOAD_ENABLED`, `WHATSAPP_ENABLED`, `LEGAL_DOCS` (`NONE|BASIC|FULL`), `ANALYTICS` (`BASIC|ADVANCED|FULL`), `BACKUP_FREQUENCY` (`NONE|WEEKLY|DAILY`), `SUPPORT_TIER`.

Exact values from §23.1:

| limit_key | FREE | PRO | PREMIUM |
|---|---|---|---|
| BROKER_PROPERTIES | 10 | 100 | −1 |
| BROKER_CUSTOMERS | 25 | 500 | −1 |
| BUILDER_PROJECTS | 1 | 5 | −1 |
| BUILDER_PLOTS_PER_PROJECT | 50 | 500 | −1 |
| BUILDER_TEAM_MEMBERS | 1 (owner) | 3 | 15 |
| BUILDER_BROKERS | 0 | 10 | −1 |
| EXPORT_ENABLED | 0 | 1 | 1 |
| BULK_UPLOAD_ENABLED | 0 | 1 | 1 |
| WHATSAPP_ENABLED | 0 | 1 | 1 |
| LEGAL_DOCS | NONE | BASIC | FULL |
| ANALYTICS | BASIC | ADVANCED | FULL |
| BACKUP_FREQUENCY | NONE | WEEKLY | DAILY |

### `subscription`
`id, org_id, plan_code, status (TRIAL|ACTIVE|PAST_DUE|CANCELLED|EXPIRED), started_at, current_period_start, current_period_end, auto_renew, cancelled_at, cancel_at_period_end, grace_until, [STD]`

### `subscription_payment`
`id, org_id, subscription_id, amount, currency, gateway, gateway_payment_id, gateway_order_id, status, invoice_number, invoice_media_id, paid_at`

### `org_usage` (materialised counters)
`org_id, limit_key, current_value, updated_at` — incrementally maintained by triggers. Entitlement checks read this, never `COUNT(*)`.

---

## 11. Import / Export (M-07, M-10)

### `import_job`
`id, org_id, entity_type (PROPERTY|PLOT|CUSTOMER), target_project_id, source_media_id, template_version, status (UPLOADED|VALIDATING|PREVIEW_READY|IMPORTING|COMPLETED|FAILED|CANCELLED), total_rows, valid_rows, invalid_rows, imported_rows, started_by, started_at, completed_at, error_report_media_id, [STD]`

### `import_row`
`id, import_job_id, row_number, raw_data JSONB, normalised_data JSONB, status (VALID|INVALID|IMPORTED|SKIPPED), errors JSONB (array of {column, code, message_key, params}), created_entity_id`

### `export_job`
`id, org_id, entity_type, format (XLSX|CSV|PDF), filter_snapshot JSONB, scope (ALL|FILTERED), status, row_count, result_media_id, expires_at, requested_by, [STD]` — result files expire after 24h.

### `report_definition` (§10.1, §17.1)
`code, profile (BROKER|BUILDER), name_en, name_hi, description_key, supported_filters JSONB, supported_formats, required_permission, min_analytics_tier`

---

## 12. Audit & Privacy (M-13, §25)

### `audit_log`
`id, org_id, actor_user_id, actor_ip, entity_type, entity_id, action (CREATE|UPDATE|DELETE|RESTORE|EXPORT|VIEW_SENSITIVE|LOGIN|PERMISSION_CHANGE), changes JSONB, request_id, occurred_at`
Partitioned monthly by `occurred_at`. Retained 24 months.

### `sensitive_access_log`
`id, org_id, actor_user_id, entity_type, entity_id, field, reason, occurred_at` — every Aadhaar/PAN/bank-detail read.

### `platform_access_log`
`id, platform_admin_id, org_id, reason_code, justification, granted_at, expires_at, revoked_at` — §25.1 break-glass.

### `data_export_request` / `account_deletion_request`
`id, org_id, requested_by, requested_at, status, fulfilled_at, result_media_id, expires_at` (§25.3: within 7 days) and `id, org_id, requested_by, requested_at, scheduled_purge_at (= +30d), status, cancelled_at` (§25.4).

---

## 13. Denormalisation & Trigger Contracts

These are the only places we deliberately duplicate state. Each has a **reconciliation job** (nightly) that recomputes from source and alerts on drift — this is how we catch a missed trigger before a customer does.

| Denormalised field | Source of truth | Maintained by |
|---|---|---|
| `plot_sale.total_paid` | `SUM(payment_record.amount)` | AFTER INSERT trigger |
| `payment_schedule.amount_allocated` / `status` | `SUM(payment_allocation.amount)` | AFTER INSERT/UPDATE trigger |
| `plot.status` | `plot_sale.status` | Application service (single write path) |
| `plot.current_sale_id` | active `plot_sale` | Application service |
| `broker_partner.deals_closed_count` | `COUNT(plot_sale WHERE status='COMPLETED')` | AFTER UPDATE trigger → fires tier evaluation |
| `broker_partner.total_commission_earned/paid` | ledger sums | Trigger |
| `customer.last_interaction_at` | `MAX(interaction.occurred_on)` | Trigger |
| `customer.follow_up_date` | latest `interaction.next_follow_up_date` | Application service |
| `org_usage.current_value` | entity counts | Triggers on insert/soft-delete |
| `org_metrics.*` (dashboard cards) | various | Write-through + 5-min Redis TTL |

---

## 14. Entity Relationship Summary

```
organization ──┬── app_user ──── role ──── role_permission
               │        └── user_project_access ──┐
               ├── subscription ── subscription_payment
               ├── org_usage
               │
        BUILDER│                                   │
               ├── project ◄───────────────────────┘
               │     └── plot ── plot_sale ──┬── payment_schedule ──┐
               │            │                │                      │
               │            │                ├── payment_record ────┴ payment_allocation
               │            │                ├── plot_document
               │            │                └── commission_ledger_entry ── commission_payment
               │            └── plot_document
               ├── broker_partner ──┬── broker_commission_config
               │                    ├── broker_tier
               │                    └── commission_ledger_entry
        BROKER │
               ├── property ──┬── property_media
               │              ├── customer_property_interest
               │              └── deal ──┬── deal_stage_history
               │                         └── brokerage_receipt
        SHARED │
               ├── customer ──── interaction
               ├── calendar_event
               ├── notification ── notification_preference
               ├── media_asset ── generated_document ── document_template
               ├── import_job ── import_row
               ├── export_job
               └── audit_log
```
