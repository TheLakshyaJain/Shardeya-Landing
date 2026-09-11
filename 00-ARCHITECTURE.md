# Shardeya — System Architecture

**Version 1.0 · Derived from Shardeya PRD v1.0 (March 2026)**
**Status: Architecture baseline — pre-implementation**

---

## 1. Architectural Position

### 1.1 What we are actually building

Shardeya is **not** two products. It is one multi-tenant SaaS with two tenant *profiles* (Broker, Builder) sharing ~55% of their domain surface (customers, follow-ups, calendar, deals archive, reports, notifications, subscriptions, calculators, i18n, import/export) and diverging on inventory:

| Concern | Broker | Builder |
|---|---|---|
| Inventory unit | `Property` (heterogeneous, owner-owned, brokered) | `Plot` (homogeneous, owned by tenant, sold) |
| Money in | Brokerage commission received | Sale price + instalments collected |
| Money out | — | Broker commission paid |
| Org shape | Single user | Owner + staff with RBAC |
| Channel | Direct | Own staff + external broker network |

**Architectural consequence:** build a shared **Party/Lead/Interaction/Deal** core and specialise inventory + ledger per profile. Do not fork the codebase.

### 1.2 Style: Modular Monolith → selective extraction

We start as a **modular monolith** (Spring Boot, single deployable, module-per-bounded-context with enforced package boundaries), not microservices.

**Why:**
- Team size at launch is small; cross-service transactions on money would be the dominant failure mode.
- Nearly every write path crosses 2–4 modules (record payment → update plot balance → close deal → accrue broker commission → fire notification → bump tier). In microservices this becomes a saga on day one for no benefit.
- Tenant-scoped read models are trivially joinable in one Postgres instance at the data volumes implied (a large builder = ~50 projects × ~500 plots = 25k rows).

**Extraction candidates, in order, when load demands:** ① Notification Dispatcher (already async via outbox), ② Document/PDF Generation (CPU-bound, bursty), ③ Import/Export Worker (memory-bound), ④ Reporting/Analytics read-side. All four are already designed as queue-driven workers behind interfaces, so extraction is a deployment change, not a rewrite.

### 1.3 Recommended stack

| Layer | Choice | Rationale |
|---|---|---|
| Backend | **Java 21 + Spring Boot 3.3** | Team's core strength. Virtual threads for I/O-heavy WhatsApp/S3 fan-out. Mature scheduling, validation, security. |
| API style | REST + JSON, OpenAPI 3.1 generated | Simple clients; no GraphQL complexity needed for fixed dashboards. |
| DB | **PostgreSQL 16** | JSONB for flexible per-type property attributes, `tsvector` for search, partial indexes for `deleted_at IS NULL`, RLS for tenant isolation defence-in-depth, window functions for ledgers. |
| Cache / queue | **Redis 7** | Session/refresh-token store, OTP store with TTL, rate limiting, dashboard aggregate cache, and a Streams-based job queue (avoids introducing Kafka at this scale). |
| Object storage | **S3-compatible** (AWS S3 / Cloudflare R2) | Photos, documents, generated PDFs, export files. Pre-signed URLs — bytes never traverse the app server. |
| Frontend | **React 18 + TypeScript + Vite** | Mobile-first SPA. |
| UI kit | **Tailwind + shadcn/ui + Radix** | Fast to build a consistent design system; accessible primitives satisfy §24.4. |
| State/data | **TanStack Query** + Zustand (UI-only state) | Server-state caching, optimistic updates, instant filter response (§24.3). |
| Forms | **react-hook-form + Zod** | Zod schemas shared conceptually with backend Bean Validation; field-level Hindi error messages. |
| Charts | **Recharts** | Covers every chart in §7.4 and §21.1. |
| i18n | **i18next + react-i18next** | Namespace-per-module JSON, lazy-loaded, instant switch without reload (§24.5). |
| Grid virtualisation | **@tanstack/react-virtual** | Plot grid of 500–5000 cells on a 360px Android device. |
| PDF generation | **OpenPDF / Flying Saucer (HTML→PDF)** server-side | Legal docs must be deterministic, templated, and auditable — never client-generated. |
| Excel | **Apache POI (SXSSF streaming)** | Streaming write for exports of 100k rows without OOM. |
| Auth | Self-issued **JWT access (15 min) + opaque refresh (30 d, Redis)** | "Remember Me" = 30-day refresh (§2.2). |
| Observability | OpenTelemetry → Grafana/Tempo/Loki; Micrometer | Trace the money paths. |
| Migrations | **Flyway** | Versioned, reviewable SQL. |

> **Note on the UI reference:** the PRD's Propwise/Lovable reference is explicitly out of scope per direction. We define our own design system in `05-MILESTONES.md` M0. All *functional* UI requirements (§24.2 responsiveness, §24.3 performance, §24.4 accessibility, §24.5 language) remain binding.

---

## 2. Multi-Tenancy Model

### 2.1 Tenant = Organization

Every row that holds business data carries `org_id`. This is non-negotiable and is the single most important invariant in the system (§25.1: *"All data entered by a user belongs only to that user — no cross-visibility"*).

```
organization (id, type = BROKER | BUILDER, ...)
   ├── app_user (owner + staff)          [Builder: many; Broker: one]
   ├── property / project → plot         [profile-specific inventory]
   ├── customer, interaction, deal
   ├── payments, ledgers
   └── subscription
```

A **Broker** org is an org of exactly one user with `type = BROKER`. Modelling it as an org (rather than a bare user) means brokerage firms with multiple agents become a pure data change later, not a migration.

### 2.2 Three-layer isolation (defence in depth)

1. **Layer 1 — Request context.** A `TenantContext` (ThreadLocal / Scoped Value) is populated by a servlet filter from the validated JWT (`org_id`, `user_id`, `role`, `project_scope[]`). Cleared in a `finally`.
2. **Layer 2 — Persistence.** Hibernate `@FilterDef`/`@Filter` on `org_id` enabled globally per session, plus a repository base class that refuses any query without a tenant predicate. Every service-layer read goes through it.
3. **Layer 3 — Database.** PostgreSQL **Row-Level Security** on all tenant tables, keyed on `current_setting('app.current_org')`, set per-connection by the transaction interceptor. This is the backstop: a forgotten `WHERE org_id = ?` returns zero rows instead of leaking a competitor's pipeline.

**Additionally, for Builder staff:** `project_scope` (§18.2 "Project Access") is applied as a *second* predicate on all project-derived entities. A Sales Executive's `own leads only` restriction (§18.3) is a *third* predicate (`assigned_to = :userId`). These compose:

```
org_id = :org  AND  (project_id IN :scope OR :scopeIsAll)  AND  (assigned_to = :me OR :canSeeAllLeads)
```

### 2.3 Platform Admin

Shardeya staff (§1.2, §25.1) operate in a **separate application** (`/admin`) with its own auth, its own audit stream, and explicit **break-glass access**: viewing a tenant's data requires selecting a reason code, is time-boxed to 60 minutes, writes an immutable `platform_access_log` row, and (Milestone 9) notifies the tenant owner. Platform admins never get a tenant JWT.

---

## 3. Module Map

```
┌─────────────────────────── FOUNDATION (shared) ────────────────────────────┐
│ M-01 Identity, Tenancy & Auth      M-08 Calculators                        │
│ M-02 RBAC & Permission Engine      M-09 Subscription & Entitlements        │
│ M-03 Localization (i18n)           M-10 Reporting & Export Engine          │
│ M-04 App Shell & Navigation        M-11 Calendar & Reminder Engine         │
│ M-05 Media & Document Storage      M-12 Customer / Lead Core               │
│ M-06 Notification Engine           M-13 Audit, Soft-Delete & Privacy       │
│ M-07 Import (Bulk Upload) Engine   M-14 Platform Admin Console             │
└────────────────────────────────────────────────────────────────────────────┘
            │                                              │
┌───────────▼──────────── BUILDER (core) ────────┐  ┌──────▼──── BROKER ─────────┐
│ B-01 Builder Dashboard                          │  │ BR-01 Broker Dashboard     │
│ B-02 Manage Projects                            │  │ BR-02 Property Manager     │
│ B-03 Plot Inventory & Interactive Grid          │  │ BR-03 Property↔Customer    │
│ B-04 Plot Sale, Buyer & Documents               │  │       Link + Deal Pipeline │
│ B-05 Payment Tracker & Instalment Schedules     │  │ BR-04 Customer Manager     │
│ B-06 Bulk Plot Upload                           │  │ BR-05 Brokerage Analysis   │
│ B-07 Builder Lead Manager                       │  │ BR-06 Broker Calendar      │
│ B-08 Financials / Accounts                      │  │ BR-07 Deals History        │
│ B-09 Builder Calendar                           │  │ BR-08 Reports & Stats      │
│ B-10 Builder Deals History                      │  └────────────────────────────┘
│ B-11 Reports & Legal Document Generation        │
│ B-12 Admin Panel (Team & Roles)                 │
│ B-13 Follow-up & Collection Tracker             │
│ B-14 Broker Management (commission, tiers)      │
│ B-15 Stats & Analysis                           │
└─────────────────────────────────────────────────┘
```

Package layout enforces this (ArchUnit tests in CI):

```
com.shardeya
├── platform/          # cross-cutting: tenancy, security, audit, outbox, storage
├── foundation/        # M-01..M-14, one package per module
├── builder/           # B-01..B-15
├── broker/            # BR-01..BR-08
└── shared/            # domain primitives: Money, AreaMeasure, PhoneNumber, DateRange
```

**Rule:** `builder.*` and `broker.*` may depend on `foundation.*` and `shared.*`, never on each other. Cross-profile needs are lifted into `foundation`.

---

## 4. Cross-Cutting Design Decisions

### 4.1 Money

Never `double`. Use `BigDecimal(19,2)` in Java, `NUMERIC(19,2)` in Postgres, stored in **paise-precision INR**. A `Money` value object owns rounding (HALF_UP) and formatting (Indian grouping: `₹1,23,45,678`). All percentage maths (brokerage, commission, GST, tier bonus) rounds only at the final display/persist step, never intermediately.

### 4.2 Area measurement

The PRD uses Sq Ft, Sq Yd, Sq M, Bigha, Gunta, Dismil, Acre — and Bigha **varies by region** (UP ≈ 27,000 sqft, Rajasthan ≈ 27,225, Bengal ≈ 14,400). Storing "3 bigha" is meaningless without context.

**Decision:** every area is persisted as **two columns**: `area_value NUMERIC(14,4)` + `area_unit VARCHAR(16)` (as entered, for display fidelity) **plus** a derived `area_sqft NUMERIC(14,4)` (canonical, for all comparison/sort/filter/aggregation). Conversion factors live in a `measurement_unit` reference table with an optional `state_code` override, admin-editable. This is the only way filters like "size range" and reports like "total project area" stay correct across a tenant with mixed units.

### 4.3 Soft delete

Every business table has `deleted_at TIMESTAMPTZ NULL`, `deleted_by UUID NULL`. All indexes on hot query paths are **partial**: `WHERE deleted_at IS NULL`. A nightly job hard-deletes rows past 30 days (§25.4). Restore endpoint exists for the 30-day window. Financial rows (`payment_record`, `commission_ledger_entry`) are **never** deletable — only reversible via a contra entry.

### 4.4 Auditability

- `audit_log` captures who/what/when/before/after for every mutation on tenant data, written by a Hibernate interceptor, JSONB diff.
- Interaction/follow-up entries are **append-only by contract** (§6.5, §13.3: *"cannot be deleted once added — full audit trail"*). Enforced with a DB trigger that rejects `DELETE` and rejects `UPDATE` of `remarks`/`occurred_on` after 15 minutes (grace window for typo fixes, logged as an amendment).
- Deal stage transitions are recorded in `deal_stage_history`, never overwritten.

### 4.5 Idempotency

Every POST that creates money or sends a message accepts an `Idempotency-Key` header. Keys stored in Redis (24h) mapped to the original response. Non-negotiable for: record payment, record commission payment, send WhatsApp reminder, bulk import commit, subscription purchase.

### 4.6 Asynchronous work — Transactional Outbox

Notifications, WhatsApp sends, PDF generation, export builds, and index updates are **never** done inline in a request transaction. Pattern:

```
BEGIN
  ... business write ...
  INSERT INTO outbox_event (aggregate, type, payload, available_at)
COMMIT
        ↓  (poller, 1s, FOR UPDATE SKIP LOCKED, batch 100)
   Dispatcher → Redis Stream → Worker (retry w/ exponential backoff, DLQ after 5)
```

This guarantees a payment is never recorded without its receipt notification being *scheduled*, and a WhatsApp outage never rolls back a payment.

### 4.7 Time & scheduling

- Store `TIMESTAMPTZ` (UTC). Render in **Asia/Kolkata**.
- "Business dates" (follow-up date, instalment due date, purchase date) are `DATE`, not timestamps — a follow-up is due *on a day*, not at an instant.
- "Morning of" notifications (§22.2, §22.3) = a scheduled job at **09:00 IST**, tenant-overridable in settings.
- "Overdue" is computed against IST calendar date, always. A dedicated `business_date()` SQL function prevents the classic UTC off-by-one where an instalment shows overdue at 05:30 IST.

### 4.8 Search

Per-tenant `tsvector` GIN indexes on the searchable concatenation (property title+address+owner+locality; customer name+phone+remarks; plot number). Trigram (`pg_trgm`) index additionally on phone and plot_number for partial/fuzzy match ("A-1" should find "A-12"). No Elasticsearch until a tenant exceeds ~200k searchable rows.

### 4.9 API conventions

- Base: `/api/v1`
- Auth: `Authorization: Bearer <jwt>`
- Tenancy: **never** taken from a request parameter. Always from the token.
- Lists: cursor pagination (`?cursor=&limit=`) for infinite scroll; offset pagination allowed only for report tables with page numbers.
- Errors: RFC 9457 Problem Details + `errors[]` array of `{field, code, messageKey, params}` so the client renders the message in the active language (§24.4 requires *specific* errors — the backend sends a key + params, the frontend renders "मोबाइल नंबर 10 अंकों का होना चाहिए").
- Every mutating endpoint returns the full updated resource, so the client can update cache without a refetch.

### 4.10 File handling

Upload flow (§24.3: max 2MB per photo):
1. Client requests `POST /api/v1/media/upload-intent` → server validates entitlement + quota, returns pre-signed PUT URL + `media_id`.
2. Client **compresses client-side** (browser-image-compression, target ≤ 2MB, max edge 2560px) then PUTs directly to S3.
3. Client calls `POST /api/v1/media/{id}/complete`.
4. Async worker: verifies MIME by magic bytes (not extension), strips EXIF GPS, generates 3 derivatives (thumb 200px, card 600px, full 1600px) in WebP + JPEG fallback, virus-scans (ClamAV), marks `READY`.

Sensitive documents (Aadhaar/PAN scans, §12.3.2, §25.2) go to a **separate bucket** with SSE-KMS, no public derivatives, 5-minute pre-signed reads, and every read logged to `sensitive_access_log`.

### 4.11 Rate limiting & abuse

Redis token bucket per `{ip}`, `{user}`, `{org}`. Hard caps: OTP send 3/hour/mobile + 10/day/IP; WhatsApp sends per org per day (also a cost control); export jobs 10/hour/org; bulk import 5/hour/org.

---

## 5. Security Requirements (§25.2)

| Requirement | Implementation |
|---|---|
| Passwords encrypted, never plaintext | **Argon2id** (m=64MB, t=3, p=4). Never bcrypt-only. Password never logged, never in DTOs. |
| HTTPS everywhere | TLS 1.3, HSTS `max-age=63072000; includeSubDomains; preload`, no HTTP listener. |
| OTP login | 6-digit, Redis TTL 5 min, hashed at rest, 5 verify attempts then invalidate, 60s resend cooldown (§2.1), rate-limited per mobile. |
| Gov ID / financial docs restricted | Separate KMS-encrypted bucket; access gated on `FINANCIAL_ACCESS` permission; every read audited; never included in exports by default. |
| Cross-tenant isolation | Three-layer model §2.2, with an automated test suite that asserts every endpoint returns 404 for a foreign-org resource ID. |
| Session | Access JWT 15 min; refresh rotated on every use with reuse-detection (reuse ⇒ revoke whole family). |
| Secrets | No secrets in env files in prod; AWS Secrets Manager / Vault, rotated. |
| Uploads | Magic-byte MIME check, size cap, AV scan, `Content-Disposition: attachment`, served from a cookieless domain. |
| PII in logs | Structured logging with a masking serializer for phone/email/Aadhaar/PAN/account numbers. |

---

## 6. Performance Budget (§24.3)

Targets on a 4G Indian connection, mid-range Android (Moto G-class):

| Metric | Budget |
|---|---|
| First Contentful Paint | ≤ 1.8 s |
| Largest Contentful Paint | ≤ 2.5 s |
| Initial JS bundle (gzip) | ≤ 180 KB; each route chunk ≤ 90 KB |
| Dashboard API (p95) | ≤ 400 ms |
| List/filter API (p95) | ≤ 300 ms |
| Plot grid render, 500 plots | ≤ 250 ms to interactive |
| Search keystroke → results | ≤ 200 ms (300 ms debounce + cached) |

Techniques: route-level code splitting; dashboard aggregates served from a Redis-cached materialised summary refreshed on write-through + 5-min TTL; `LIMIT 51` cursor lists (fetch n+1 to know `hasMore` without `COUNT(*)`); counts served from incrementally maintained `org_metrics` rows rather than `COUNT(*)` scans; virtualised tables and grids; images `loading="lazy"` + `srcset`; brotli; HTTP/2.

---

## 7. Environments & Delivery

| Env | Purpose |
|---|---|
| `local` | Docker Compose: Postgres, Redis, MinIO, MailHog, WhatsApp stub |
| `dev` | Auto-deploy on merge to `main`; seeded demo tenants (1 broker, 1 builder w/ 3 projects × 200 plots) |
| `staging` | Production-shaped; anonymised data restore; where migrations are rehearsed |
| `prod` | Blue/green; Flyway runs pre-cutover; expand→migrate→contract for breaking schema changes |

**CI gates:** unit tests, ArchUnit boundary tests, Flyway migration dry-run, tenant-isolation test suite, OpenAPI diff check, Lighthouse mobile budget check, `npm audit`/OWASP dependency check.

---

## 8. Reading Order

| File | Contents |
|---|---|
| `00-ARCHITECTURE.md` | This file |
| `01-DATA-MODEL.md` | Complete schema, all tables, indexes, constraints, ERD |
| `02-FOUNDATION-MODULES.md` | M-01 … M-14, full 13-point spec each |
| `03-BUILDER-MODULES.md` | B-01 … B-15, full 13-point spec each *(core product)* |
| `04-BROKER-MODULES.md` | BR-01 … BR-08, full 13-point spec each |
| `05-MILESTONES.md` | 12 independently shippable milestones with exit criteria |
| `CLAUDE.md` | Working agreement for AI-assisted implementation |
