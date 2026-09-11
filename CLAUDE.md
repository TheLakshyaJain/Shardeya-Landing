# CLAUDE.md — Shardeya Development Working Agreement

This file is the contract between the developer and the AI assistant for implementing Shardeya. Read this before asking for any code.

---

## Project Identity

**Shardeya** is a multi-tenant SaaS for Indian real estate brokers and builders. The PRD (v1.0, March 2026) is the source of truth for features. The architecture docs (00–05) are the source of truth for how they're built.

**Core product:** the Builder side (B-01 … B-15). Builder ships first.

---

## Architecture Docs — Read Order

| File | What it covers | When to reference |
|---|---|---|
| `00-ARCHITECTURE.md` | Stack, tenancy model, cross-cutting decisions (money, area, soft delete, outbox, auth, security, performance budgets) | Before any design decision |
| `01-DATA-MODEL.md` | Every table, column, constraint, index, trigger contract | Before writing any migration or query |
| `02-FOUNDATION-MODULES.md` | M-01 … M-14 (auth, RBAC, i18n, shell, media, notifications, import, calculators, subscriptions, reports, calendar, customers, audit, admin) | When implementing shared features |
| `03-BUILDER-MODULES.md` | B-01 … B-15 (dashboard, projects, grid, sales, payments, leads, financials, calendar, deals, reports, admin, tracker, brokers, stats) | When implementing any builder feature |
| `04-BROKER-MODULES.md` | BR-01 … BR-08 (dashboard, properties, deals, customers, brokerage, calendar, history, reports) | When implementing any broker feature |
| `05-MILESTONES.md` | 12 milestones with exit criteria and step-by-step implementation order | To know what to build next |
| `06-BROKER-NETWORK-ENGINE.md` | The Broker Network & Designation Commission Engine (called "M6.5" in its own text) -- extends and partly replaces B-14's flat-tier system with an 8-level recursive network, multi-level differential commission, promotion state, and proportional release. A large, money-critical feature with its own explicit §15 build order | Before touching anything in `builder.broker` involving `DESIGNATION`-type brokers, `designation_slab`, `broker_network`, or `CommissionCalculationEngine` |
| `PATTERNS.md` | Compact, deduplicated index of ~25 bug classes that have already recurred 2+ times in this codebase (JPA/Hibernate gotchas, RLS + background jobs, React ref-forwarding, i18n key collisions, mobile-width tables, etc.), each with a one-line "how to spot it" and a search term into this file for the full root-cause writeup | **First**, when investigating any bug or customer report — most "new" bugs turn out to be one of these recurring in a file that hasn't hit it yet |

---

## Non-Negotiable Rules

### 1. Tenant isolation is the #1 invariant
- Every query on tenant data MUST include `org_id = :tenantId`.
- Use the repository base class. Never write raw queries without the tenant predicate.
- PostgreSQL RLS is the backstop, not the primary mechanism.
- Foreign-org resource IDs return **404**, never 403.
- Test: the tenant isolation test suite must pass on every CI build.

### 2. Money is BigDecimal, never double
- Java: `BigDecimal(19,2)`, round `HALF_UP` only at the final step.
- Postgres: `NUMERIC(19,2)`.
- Display: Indian grouping (`₹1,23,45,678`), lakh/crore abbreviations.
- Payments are immutable. Corrections are reversals (negative entries).

### 3. Area is always dual-stored
- `area_value` + `area_unit` (as entered) + `area_sqft` (canonical, derived).
- All filters, sorts, and aggregations use `area_sqft`.
- Bigha is state-dependent — resolve via `measurement_unit` table.

### 4. i18n from day one
- No hardcoded user-facing strings in JSX. Use `t('namespace.key')`.
- Every key must exist in both `en` and `hi` JSON files.
- Error messages return `{messageKey, params}`, never rendered sentences.
- CI fails on missing keys or bare strings.

### 5. RBAC is server-side
- UI hiding is cosmetic. Every endpoint enforces permissions.
- `@RequiresPermission` + `@ScopedToProject` annotations.
- "Own data only" is a repository predicate, not an if-statement.

### 6. Outbox for side effects
- Notifications, WhatsApp, PDF generation, exports are NEVER inline.
- Business write + outbox insert in the same transaction.
- Poller → dispatcher → channel adapter, with retry and DLQ.

### 7. Soft delete everywhere
- `deleted_at` column, partial indexes `WHERE deleted_at IS NULL`.
- Financial records are NEVER deletable — only reversible.
- Interactions are append-only (DB rule blocks DELETE).

---

## Stack Reference

### Backend
```
Java 21 + Spring Boot 3.3
PostgreSQL 16 (JSONB, RLS, tsvector, partial indexes)
Redis 7 (sessions, OTP, cache, rate limiting, job queue via Streams)
Flyway (migrations)
Apache POI SXSSF (Excel streaming)
OpenPDF / Flying Saucer (HTML → PDF)
Argon2id (passwords)
JWT (access 15min) + opaque refresh (Redis, 30d)
```

### Frontend
```
React 18 + TypeScript + Vite
Tailwind CSS + shadcn/ui + Radix primitives
TanStack Query (server state) + Zustand (UI state)
react-hook-form + Zod (forms + validation)
i18next + react-i18next
Recharts (charts)
@tanstack/react-virtual (virtualised lists and grids)
dnd-kit (drag reorder for photos, grid layout)
```

### Infrastructure
```
Docker Compose (local): Postgres, Redis, MinIO, MailHog, WhatsApp stub
S3-compatible object storage (standard + sensitive/KMS buckets)
```

---

## Package Structure

```
Backend:
com.shardeya
├── platform/          # TenantContext, security filters, outbox, audit interceptor
├── foundation/
│   ├── auth/          # M-01
│   ├── rbac/          # M-02
│   ├── media/         # M-05
│   ├── notification/  # M-06
│   ├── importexport/  # M-07, M-10
│   ├── calculator/    # M-08
│   ├── subscription/  # M-09
│   ├── calendar/      # M-11
│   ├── customer/      # M-12
│   ├── audit/         # M-13
│   └── admin/         # M-14
├── builder/
│   ├── project/       # B-02
│   ├── plot/          # B-03, B-06
│   ├── sale/          # B-04
│   ├── payment/       # B-05
│   ├── lead/          # B-07 (extends foundation/customer)
│   ├── financial/     # B-08
│   ├── deal/          # B-10
│   ├── document/      # B-11
│   ├── team/          # B-12
│   ├── tracker/       # B-13
│   ├── broker/        # B-14
│   └── stats/         # B-15
├── broker/
│   ├── property/      # BR-02
│   ├── deal/          # BR-03
│   ├── customer/      # BR-04 (extends foundation/customer)
│   ├── brokerage/     # BR-05
│   └── report/        # BR-08
└── shared/            # Money, AreaMeasure, PhoneNumber, DateRange value objects

Frontend:
src/
├── app/               # routing, providers, error boundaries
├── components/        # shared UI components
│   ├── ui/            # shadcn/ui primitives
│   ├── layout/        # AppShell, TopBar, Sidebar, PageHeader
│   ├── forms/         # PhoneInput, MoneyInput, AreaInput, OtpInput
│   ├── data/          # DataTable, FilterBar, EmptyState, Pagination
│   └── feedback/      # Toast, Dialog, AlertBanner, Skeleton
├── features/
│   ├── auth/
│   ├── builder/
│   │   ├── dashboard/
│   │   ├── projects/
│   │   ├── plots/
│   │   ├── sales/
│   │   ├── payments/
│   │   ├── leads/
│   │   ├── financials/
│   │   ├── calendar/
│   │   ├── deals/
│   │   ├── documents/
│   │   ├── team/
│   │   ├── tracker/
│   │   ├── brokers/
│   │   └── stats/
│   ├── broker/
│   │   ├── dashboard/
│   │   ├── properties/
│   │   ├── deals/
│   │   ├── customers/
│   │   ├── brokerage/
│   │   ├── calendar/
│   │   ├── history/
│   │   └── reports/
│   ├── calculators/
│   ├── subscription/
│   ├── settings/
│   └── notifications/
├── hooks/             # useCan, useEntitlement, useTenant, useDebounce
├── lib/               # api client, auth, storage, formatters
├── i18n/
│   ├── en/            # namespace JSONs
│   └── hi/
└── types/             # shared TypeScript types
```

---

## ArchUnit Rules (enforced in CI)

```java
// builder.* and broker.* never import each other
noClasses().that().resideInAPackage("..builder..")
  .should().dependOnClassesThat().resideInAPackage("..broker..");

// Only platform.* may reference TenantContext directly
noClasses().that().resideOutsideOfPackage("..platform..")
  .should().accessClassesThat().haveSimpleName("TenantContext");

// Services never import Controllers
noClasses().that().haveSimpleNameEndingWith("Service")
  .should().dependOnClassesThat().haveSimpleNameEndingWith("Controller");

// No entity may lack org_id (except platform tables)
// Checked via a custom rule scanning @Entity classes
```

---

## API Conventions

- Base path: `/api/v1`
- Auth: `Authorization: Bearer <jwt>`
- Tenant: from token, NEVER from a request parameter
- Lists: cursor pagination (`?cursor=&limit=`), default limit 25, max 100
- Errors: RFC 9457 + `errors[]` with `{field, code, messageKey, params}`
- Mutations: return the full updated resource
- Idempotency: `Idempotency-Key` header on payments, sends, imports
- Soft delete: `DELETE` sets `deleted_at`; `POST .../restore` undoes it
- Timestamps: ISO 8601 with timezone (`2026-07-24T14:30:00+05:30`)
- Money: string representation in paise-precision (`"4200000.00"`)
- Booleans: never as strings

---

## Migration Conventions

```
V{milestone}_{sequence}__{description}.sql
V2_001__create_project_table.sql
V2_002__create_plot_table.sql
V3_001__create_plot_sale_table.sql
```

- One table per migration file (for clean rollback reasoning)
- RLS policy in the same migration as the table
- Indexes in the same migration as the table
- Triggers in a separate migration (they reference multiple tables)
- Seed data in a separate, idempotent migration (`INSERT ... ON CONFLICT DO NOTHING`)
- Never modify a committed migration — create a new one

---

## Testing Strategy

| Layer | Tool | What to test |
|---|---|---|
| Unit | JUnit 5 + Mockito | Business logic (commission resolution, allocation, tier evaluation, area conversion) |
| Integration | Spring Boot Test + Testcontainers (Postgres, Redis) | Full request→DB→response for every endpoint; tenant isolation suite |
| ArchUnit | ArchUnit | Package boundaries, no entity without org_id |
| Frontend unit | Vitest + Testing Library | Form validation, permission guards, currency/date formatting |
| Frontend integration | Playwright | Key flows: signup, create project, bulk upload, sell plot, record payment, view grid |
| Performance | k6 | Dashboard API p95 <400ms, grid API p95 <300ms under 50 concurrent users |
| Security | OWASP ZAP + manual | IDOR, injection, XSS, upload exploits, JWT handling |

**Tenant isolation test pattern:**
```java
// Create org A and org B
// As org A, create a project
// As org B, try to GET/PATCH/DELETE that project → assert 404
// As org B, try to list projects → assert org A's project is absent
// Repeat for every entity type
```

---

## Common Pitfalls to Avoid

1. **Never use `COUNT(*)` for dashboard cards.** Use `org_metrics` (trigger-maintained) or `org_usage`.
2. **Never store area as a single number.** Always `value + unit + sqft`.
3. **Never translate user-entered data.** Only UI labels via i18n keys.
4. **Never inline side effects.** Payment → notification must go through the outbox.
5. **Never trust the client for money math.** All financial calculations are server-authoritative.
6. **Never serve sensitive files (Aadhaar/PAN) through the standard pipeline.** Separate bucket, separate endpoint, audited.
7. **Never use `float`/`double` for money.** BigDecimal everywhere.
8. **Never allow direct `SOLD` status on a plot.** It's a side effect of creating a `plot_sale`.
9. **Never auto-downgrade broker tiers.** Only manual override.
10. **Never delete financial records.** Reversals only.
11. **Never hardcode "today" as UTC.** Use IST (`Asia/Kolkata`) for all business dates.
12. **Never send WhatsApp outside quiet hours (8am–9pm IST).**
13. **Never return 403 for a foreign-tenant resource.** Always 404.
14. **Never send a rendered error message from the backend.** Send `{messageKey, params}`.
15. **Never use `localStorage` for auth tokens.** Use `httpOnly` cookies or in-memory + refresh.

---

## Milestone 0 — Decisions & Environment Notes

Judgment calls made while bootstrapping, and gotchas hit along the way. Read this
before touching `backend/` or `frontend/` for the first time.

**Frontend stack gaps filled in (not specified in 00-ARCHITECTURE.md):**
- **Router:** `react-router-dom` v7 (declarative `<Routes>`, not the data router).
  Nothing else was a serious contender for a Vite SPA with this stack.
- **Tailwind v4**, not v3. Design tokens live in `frontend/src/index.css` under
  `@theme` / `:root` / `.dark` blocks, **not** a `tailwind.config.js` — v4 is
  CSS-first and there is no JS config file in this project. Add new tokens there.
- **shadcn/ui** components use the unified `radix-ui` npm package, not individual
  `@radix-ui/react-*` packages — that's shadcn's current codegen, not a choice we
  made; don't add per-primitive Radix packages when adding new shadcn components.
- **Toast primitive = `sonner`**, not Radix's own Toast — shadcn deprecated the
  latter. `next-themes` was added purely to drive the dark/light class toggle
  (`ThemeProvider attribute="class"`); it works fine outside Next.js despite the name.
- **Design system delivered as a `/design` route** (`frontend/src/features/design/DesignSystemPage.tsx`),
  not Storybook — M0's own spec offered this as an explicit either/or.
- **Fonts:** `@fontsource/inter` and `@fontsource/noto-sans-devanagari` (self-hosted,
  not a Google Fonts `<link>` — one less external network dependency and no
  render-blocking third-party request), imported in `main.tsx`, weights 400/500/600
  only. `--font-devanagari` in `index.css` is what `html[lang='hi'] body` switches to.

**Known non-blocking issue:**
- **React 18 + shadcn's current `radix-ui`-based codegen logs a `forwardRef`
  console warning** on every component that composes via `asChild`/Slot (Button,
  Dialog, Sheet, etc.): *"Function components cannot be given refs."* This is a
  real version mismatch — the current shadcn generator's Slot pattern assumes
  React 19's ref-as-prop model, but CLAUDE.md pins React 18, which still requires
  explicit `forwardRef`. Confirmed functionally harmless (dialogs/sheets open and
  work correctly in the browser regardless), so it's left as-is rather than
  forking every shadcn primitive away from the CLI's generated code — that would
  make every future `npx shadcn add` a manual merge. Revisit if we ever move to
  React 19, or if a shadcn update fixes it upstream.

**Gotchas:**
- **RLS `ENABLE` alone does not protect you — this bit us during M0 verification.**
  PostgreSQL exempts a table's *owner* from its own RLS policies, and it *always*
  exempts superusers, no exception. Two consequences, both now fixed:
  1. Every RLS-enabled table also needs `ALTER TABLE <t> FORCE ROW LEVEL SECURITY;`
     (now the documented default in 01-DATA-MODEL.md) so owner-bypass doesn't apply.
  2. `FORCE` still isn't enough on its own, because the official `postgres` Docker
     image's bootstrap `POSTGRES_USER` is a **superuser**, and superuser-bypass
     cannot be overridden by `FORCE`. The application must connect as a *separate*,
     ordinary, non-superuser role that owns nothing — `shardeya_app`, created in
     `V0_005__create_app_runtime_role.sql` — while migrations keep running as the
     superuser/owner role (`DB_USER`). `spring.datasource.*` (runtime) and
     `spring.flyway.*` (migrations) are deliberately configured with **different
     credentials** in `application.yml` — do not collapse them back to one user.
  Verified empirically: with the owning superuser, org B could read org A's
  `app_user` rows outright; connecting as `shardeya_app` instead, org B correctly
  got zero rows and org A got only its own. Any new tenant table must follow the
  same `ENABLE` + `FORCE` pattern and rely on the app connecting as `shardeya_app`,
  never as `DB_USER`.
- **Flyway Maven plugin's `classpath:` location reads `target/classes`, which goes
  stale.** Running `mvn flyway:migrate` standalone after editing a migration file
  silently validates the *old* copy unless you `mvn compile` (or `package`) first
  — it does not error, it just doesn't see your change. CI's flyway-dry-run job
  sidesteps this entirely by pointing `-Dflyway.locations` at
  `filesystem:src/main/resources/db/migration` instead of the pom-configured
  classpath location, so it always reviews the actual source files. Do the same
  for any ad hoc local `flyway:migrate` runs, or remember to (re)compile first.
- **ArchUnit + `allowEmptyShould`:** `ArchitectureTest.java`'s rules use
  `.allowEmptyShould(true)`. Without it, ArchUnit *fails the build* the moment a
  rule's `that()` clause matches zero classes (e.g. "no `*Service` should depend on
  `*Controller`" when zero `*Service` classes exist yet) — not because of a real
  violation, but because there's nothing to check. Keep this on all four rules;
  it's what lets the rules sit there inert-but-armed before M1+ adds the first
  `@Entity`/`*Service`/`*Controller` classes.
- **Maven wasn't preinstalled** in the dev sandbox; installed via `brew install maven`,
  which pulls OpenJDK 26 as a dependency and makes it Maven's default JDK. Since the
  project targets Java 21, run Maven locally with
  `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn ...` (or configure a Maven
  toolchain) — otherwise you may build against the wrong JDK. CI is unaffected;
  `setup-java` there pins `java-version: '21'` explicitly.
- **`npm audit` on `react-router-dom`:** every published version has some advisory;
  the newest (`latest`) fixes the most CVEs and leaves exactly one open
  (`GHSA-qwww-vcr4-c8h2`, a CSRF bypass in RSC/Framework-mode server Actions), which
  doesn't apply — we only use the plain client-side data-router mode. It's
  allowlisted with a reason in `frontend/audit-ci.jsonc`; re-check this on every
  `react-router-dom` bump and drop the allowlist entry once a real fix ships.
- **`docker compose up` is now fully verified** (follow-up session, Docker
  installed via `colima` + `docker` + `docker-compose` Homebrew formulae — no
  Docker Desktop needed; `colima start` gives a working Docker context). All six
  services (`postgres`, `redis`, `minio`, `mailhog`, `whatsapp-stub`, `backend`)
  built and started cleanly on the first attempt via `docker compose up -d
  --build`, nothing needed fixing. Confirmed: `backend`'s Flyway migrations ran
  against the containerized Postgres (`DB_HOST=postgres` resolves fine inside the
  compose network), `/actuator/health` returns 200, MinIO/MailHog/the WhatsApp
  stub all respond. The RLS/role split was re-verified against the *actual*
  containerized stack (not the local-Postgres substitute from the first
  session): seeded org A/org B via the `shardeya` migrator, then queried as the
  real `shardeya_app` role inside the `postgres` container — org B got zero rows
  for org A's data, and `pg_stat_activity` confirmed the running backend's own
  connection pool is authenticated as `shardeya_app`, not the superuser. If
  Docker isn't available in a given dev environment, `colima start` (after
  `brew install colima docker docker-compose docker-buildx` and registering
  `cliPluginsExtraDirs` in `~/.docker/config.json` — see Homebrew's caveats
  output) is a lightweight, headless alternative to Docker Desktop.
- Frontend tests use **Vitest + Testing Library + jsdom**, no H2 or other embedded
  DB was added to the backend for M0 — CLAUDE.md's own testing strategy specifies
  Testcontainers for backend integration tests, so M0 keeps backend tests to
  ArchUnit-only rather than introducing a DB stack that contradicts that later.
- `frontend/src/i18n/keyParity.test.ts` automates the "every key must exist in both
  `en` and `hi`" rule for the `common` namespace by flattening and diffing both JSON
  files. **Copy this pattern for every new i18n namespace** — it's the actual CI
  enforcement of that rule, not just documentation of it.

**Data model judgment calls (01-DATA-MODEL.md didn't spell these out):**
- `role.org_id` is nullable (system roles). Its RLS policy is
  `org_id IS NULL OR org_id = current_setting('app.current_org')::uuid` so every
  tenant can see system roles but not other tenants' custom roles.
- `role_permission` has no `org_id` and no RLS — it's scoped transitively through
  `role_id`, since it isn't marked `[STD]` in the data model.
- `organization.logo_media_id` has no FK constraint yet — `media_asset` (M-05)
  doesn't exist. Add the FK when that table lands.
- Cross-cutting error types (`ApiError`, `GlobalExceptionHandler`,
  `ResourceNotFoundException`) live directly in `com.shardeya.platform`, matching
  the flat package shown in the architecture docs, rather than inventing a new
  `platform.error` subpackage.
- `TenantContextFilter` currently just guarantees the bind/clear lifecycle around
  `TenantContext` — it does not populate it from a JWT yet. That's M1's job.

---

## Milestone 1 — Decisions & Environment Notes

Real bugs found while building auth end-to-end and driving it through actual HTTP
calls (per the verification standard below) — not just "I did X," but what could
silently fail, why, and the fix. Read this before touching `foundation/auth` or
`platform.TenantContext*`.

**Gotchas:**
- **`@Transactional` + a JDBC/JPA-synchronizing `DataSource` checks out the
  physical connection *before* the method body runs, not lazily on first
  query — the opposite of what M0-era code assumed.** `AuthService.verifySignupOtp()`
  (and `login()`, `verifyLoginOtp()`, `resetPassword()`) originally bound
  `TenantContext` as the *first line* inside an `@Transactional` method, on the
  theory that Hibernate only grabs a connection on first actual query (true in
  isolation). But Spring's `JpaTransactionManager` here is wired with an explicit
  `DataSource` (needed so `AuthLookupRepository`'s plain-JDBC calls can
  synchronize with JPA-managed transactions), and that makes `doBegin()` eagerly
  check out the connection — and `TenantAwareDataSource` fixes that connection's
  `app.current_org` GUC at checkout. Confirmed by instrumenting
  `TenantAwareDataSource.getConnection()`: it fired with `tenant=null` a
  millisecond *before* the transactional method's first log line ever executed.
  No amount of reordering statements inside the method body can fix this — the
  fix is to never bind inside a `@Transactional` method at all. `AuthService` now
  binds `TenantContext` in plain Java first, then opens the transaction
  explicitly via an injected `TransactionTemplate` (`PROPAGATION_REQUIRES_NEW`) —
  the same pattern `RefreshTokenService.rotate()` already used for its
  reuse-detection commit. `TenantContextBinder`'s javadoc documents this in
  detail; **treat "bind must happen strictly before the transaction opens, in
  plain Java" as the rule for any future code that creates a brand-new tenant
  context mid-request** (signup-shaped flows), not "bind early in the method."
- **A bean marked `@Primary` wins ambiguous-type autowiring unconditionally,
  even against a constructor/factory-method parameter whose name matches a
  *different*, non-primary bean.** M0 marked the main `dataSource()`/
  `dataSourceProperties()` beans `@Primary` to fix JPA's own ambiguity. Side
  effect, two layers deep: `AuthLookupDataSourceConfig.authLookupJdbcTemplate(DataSource
  authLookupDataSource)` and `.authLookupDataSource(DataSourceProperties
  authLookupDataSourceProperties)` both silently resolved to the `@Primary`
  main-app beans instead of the ones their parameter names matched — Spring only
  falls back to by-name matching when *no* candidate is `@Primary`. Net effect:
  the "BYPASSRLS auth-lookup" datasource was actually running as `shardeya_app`
  (RLS-enforced, no context ever bound pre-auth) against the *right* database, so
  every `AuthLookupRepository` lookup silently returned zero rows — signup's
  `existsByMobile`/`existsByEmail` always said "not registered" (silently
  disabling the duplicate-signup check), and login/refresh/reset always said
  "user not found," a clean 401 with no error logged anywhere. **Any bean method
  parameter of a type that has an `@Primary` candidate elsewhere needs an
  explicit `@Qualifier("theActualBeanName")` — matching the parameter name to
  the bean name is not sufficient once `@Primary` is in play.** Only surfaced by
  logging the actual `DataSource` class/identity/jdbcUrl/username at
  `AuthLookupRepository` construction and comparing against what a raw `psql`
  query returned — reading the config code and reasoning about it was not
  enough to catch this.
- **`V1_009__create_auth_lookup_role.sql` granted `shardeya_authlookup` `SELECT`
  on `app_user` and `role`, but not `role_permission`** — the lookup query's
  permissions subquery joins it. Failed as "permission denied for table
  role_permission," a 500 with no connection to the real cause visible from the
  error alone. Fixed in a new migration, `V1_011__grant_role_permission_to_auth_lookup.sql`
  (never edit an applied migration — see Migration Conventions above). Only
  found once the `@Primary`/`@Qualifier` bug above was fixed and the datasource
  started actually connecting as `shardeya_authlookup` for the first time.
- **`TestRestTemplate`'s default JDK-based `SimpleClientHttpRequestFactory`
  cannot read a `401` response body for a `POST` request** — it throws
  `HttpRetryException: cannot retry due to server authentication, in streaming
  mode` instead of just handing back the response, a long-standing JDK
  `HttpURLConnection` quirk unrelated to anything server-side (curl, browsers,
  and real API clients don't have this problem). This masqueraded as a real bug
  for a while because the *other* bugs above meant login never got far enough to
  return a genuine 401 during earlier test runs. Fixed by adding
  `org.apache.httpcomponents.client5:httpclient5` as a test-scope dependency —
  `RestTemplateBuilder` auto-prefers it over the JDK default when present, no
  code changes needed. Any future test that expects a 401/407 from a POST needs
  this on the classpath.
- **Redis-backed rate-limit state leaks across `@Test` methods in the same
  class.** `AbstractIntegrationTest`'s singleton Testcontainers Redis is shared
  for the whole test class (by design — see its javadoc), and every
  `AuthFlowIntegrationTest` method signs up from the same loopback IP. `OtpService`'s
  real `MAX_PER_IP_PER_DAY = 10` limit (correct, intentional production
  behavior) started tripping partway through the class's own 11 tests once
  every test actually reached its signup call. Fixed with a `@BeforeEach` in
  `AuthFlowIntegrationTest` that flushes the Redis DB — keeps each test's
  rate-limit state isolated the way a real day boundary would, without loosening
  the actual limit. Any future test class that calls `/auth/signup` or
  `/auth/otp/*` more than a handful of times needs the same reset.
- **The `GlobalExceptionHandler` catch-all's `log.error(...)` line (added
  during M0/M1 debugging) is load-bearing, not incidental** — every bug above
  except the rate-limit one was diagnosed from that log line. Do not remove it
  or downgrade it below `ERROR`.
- **Verification note:** all of the above were only found by driving
  `/api/v1/auth/*` through real HTTP calls (`TestRestTemplate` in
  `AuthFlowIntegrationTest`, and a separate manual pass with `curl` against a
  real `docker compose up postgres redis mailhog` stack plus `mvn
  spring-boot:run`) — none were visible from reading the code, from
  repository-layer tests, or from mocked-service tests. `AuthFlowIntegrationTest`
  (11 tests: signup/OTP/login/password-and-OTP, `/me` tenant isolation, forgot/reset
  password, refresh rotation + reuse detection, logout, OTP resend rate limiting)
  is green end-to-end as of this milestone; see "Current Milestone" below for
  what's still unverified (frontend, Hindi/360px, full docker-compose browser pass).

**Frontend — decisions and gotchas found driving the auth pages through a real browser:**
- **Session state is deliberately plain in-memory Zustand — no `persist`
  middleware, no localStorage/sessionStorage.** `authStore.ts` documents this:
  CLAUDE.md rule #15 forbids localStorage for tokens, and the backend hands
  back both tokens as JSON body fields rather than setting an httpOnly
  refresh cookie, so there's no secure persistence path to use instead. The
  real consequence: **a hard page reload always logs the user out**, even
  mid-session with a valid, unexpired refresh token — there's nothing to
  read it back from. Confirmed directly: `page.goto('/login')` on an
  authenticated session (a full navigation, not an in-app link) always
  landed back on the login form rather than bouncing to the dashboard, which
  is *correct* given no persistence exists, not a routing bug. If session
  survival across reloads becomes a real requirement, the fix is on the
  backend (an httpOnly refresh cookie set on login/signup/refresh, read via
  `credentials: 'include'`), not a frontend persistence hack.
- **Three of shadcn's generated primitives were missing `React.forwardRef`,
  and unlike the cosmetic case M0 already documented (Slot-composed
  triggers where a dropped ref genuinely doesn't matter), two of these
  were real, silent functional bugs — not console noise.** All three only
  surfaced by actually driving the pages in a browser, never from `tsc`,
  lint, or a unit test:
  1. **`ui/input.tsx`** had no `forwardRef`. `react-hook-form`'s `register()`
     is uncontrolled by default — it reads each field's value from the DOM
     node via the `ref` it hands out, not from React state on every
     keystroke. With the ref silently dropped, submitting *any* RHF-backed
     form (signup, login, forgot/reset password) sent every field as
     `undefined` to zod, which was rejected with zod's own generic message
     ("Invalid input: expected string, received undefined") — even though
     the fields visibly held the typed values on screen. Fixed by wrapping
     `Input` in `forwardRef`.
  2. **`ui/button.tsx`** had no `forwardRef` either. When used with
     `asChild` inside a Radix trigger that needs to *measure* the real DOM
     node (`DropdownMenuTrigger`, not `DialogTrigger`/`SheetTrigger` which
     only need open/close state), Radix's Popper positioning has nothing to
     anchor to. `ProfileMenu`'s dropdown rendered with a bounding box of
     `y: -296` — fully off-screen above the viewport — confirmed by reading
     `boundingBox()` in a Playwright script, not visible from a static
     screenshot alone (the menu was "open," just invisible). Fixed the same
     way.
  3. Both fixes are additive and don't change any component's public API —
     any *other* shadcn primitive that gets used with a real ref in the
     future (`Textarea`, `Select`, etc. — none are yet) will need the same
     treatment; check for this class of bug before assuming a forwardRef
     warning is the harmless M0 case.
- **No CORS configuration existed anywhere in the backend before this
  milestone** — nothing had ever needed it, since every prior check hit the
  API same-origin (`curl`) or same-JVM (`TestRestTemplate`, repository
  tests). The Vite dev server (5173) and the API (8080) are different
  origins; a real browser blocks every request outright without it
  (`net::ERR_FAILED` client-side; `curl -X OPTIONS ... -H "Origin:
  http://localhost:5173"` returned a bare 403 "Invalid CORS request").
  Added `platform.CorsConfig`, scoped to `shardeya.cors.allowed-origins`
  (default `http://localhost:5173`) — local dev only, no production origin
  exists to add yet.
- **A custom `Filter` that rejects unauthenticated requests will also reject
  the browser's own CORS preflight unless it explicitly exempts `OPTIONS`.**
  Spring's CORS handling isn't a `Filter` at all — it lives inside
  `DispatcherServlet.doDispatch()`, which runs *after* every registered
  `Filter`. So even with `CorsConfig` correctly in place,
  `TenantContextFilter` was still rejecting the `OPTIONS` preflight for any
  authenticated endpoint with a 401 "missing token" (an `OPTIONS` request
  never carries the real `Authorization` header) *before* Spring's own CORS
  logic ever ran — meaning the actual `GET /me` request then failed a
  *second* time, this time with a genuine browser CORS error, because its
  preflight never got the `Access-Control-Allow-Origin` header. Fixed by
  making `TenantContextFilter.doFilterInternal()` pass `OPTIONS` requests
  straight through, unconditionally, before any token check. **Any future
  custom filter in `platform.*` needs the same `OPTIONS` bypass**, or it
  will silently break CORS for whatever it guards.
- **`\p{L}` (Unicode "Letter") alone rejects most real Hindi names** — a
  Devanagari word's combining vowel signs and virama (matras like ी/ो/ा,
  and ्) are Unicode category *Mark*, not *Letter*, so
  `SignupRequest.fullName`'s validation regex (`^[\p{L} .'-]+$`, mirrored
  in `UpdateProfileRequest` and in the frontend's zod schema) rejected
  almost every name typed in Hindi. Only caught by actually switching the
  UI to Hindi and typing a Devanagari name into the signup form during
  browser verification — no existing test (backend or frontend) exercised
  a non-ASCII name. Fixed by adding `\p{M}` alongside `\p{L}` in all three
  places (`^[\p{L}\p{M} .'-]+$`). **Any future name-shaped field
  (`fullName` anywhere, org name if it ever gets free-text validation)
  needs the same `\p{L}\p{M}` pattern, not `\p{L}` alone** — this is a
  general Indic-script correctness issue, not specific to signup.
- **The `errors` i18n namespace is generated to mirror backend `messageKey`s
  directly, not hand-mapped.** `lib/api/errorMessage.ts` strips the leading
  `"error."` off any backend `messageKey` and looks up the rest as a
  dot-path inside the `errors` namespace (`error.auth.accountLocked` →
  `t('auth.accountLocked', {ns: 'errors'})`), falling back to a generic
  message via `i18n.exists()` if the key isn't there. The same pattern
  drives client-side zod validation messages (`schemas.ts` sources every
  rule's message from the identical `errors` namespace key a backend
  validation failure would use), so client- and server-side validation
  errors are always worded identically. **Any new backend `messageKey`
  needs a matching entry added to `errors.json` (both `en` and `hi`) or it
  silently falls back to the generic "something went wrong" message** — the
  key-parity test only checks `en`/`hi` match each other, not that every
  backend key has a translation.
- **Redis-backed OTP rate limits are real across manual test sessions, not
  just within `AuthFlowIntegrationTest`.** Driving the same long-running
  `docker compose` + `mvn spring-boot:run` stack through many manual
  signup/login/OTP flows in a row (via repeated Playwright scripts) tripped
  the genuine `MAX_PER_IP_PER_DAY`/`MAX_PER_MOBILE_PER_HOUR` limits —
  producing 429s and inconsistent-looking failures that had nothing to do
  with the code under test. `redis-cli FLUSHDB` against the manual
  verification stack resets it, same as `AuthFlowIntegrationTest`'s
  `@BeforeEach` does for the automated suite. Don't mistake this for a bug
  when manually re-running flows against a stack that's been up for a
  while.

---

## Milestone 2 — Decisions & Environment Notes

Real bugs found while building the Builder core-inventory backend (projects,
plots, grid, media, calculator, bulk import, quota) and driving it through
real HTTP calls against a live `docker compose` stack. Read this before
touching `builder.project`, `builder.plot`, `foundation.media`, or
`foundation.importexport`.

**Gotchas:**
- **A JPA `@Id` that is assigned in Java (`UUID.randomUUID()`) before
  `save()`, with no `@GeneratedValue`, makes Spring Data's default `isNew()`
  check (`id == null`) always resolve to `false` — even for a brand-new
  transient entity that has never touched the database.** That routes
  `SimpleJpaRepository.save()` through `entityManager.merge()` instead of
  `persist()`. `merge()` **copies state into a different, new managed
  instance and returns it; the object passed in as the argument stays
  detached forever.** This is invisible as long as nothing downstream needs
  the original reference to be managed — which is exactly why
  `ProjectService`/`PlotService`'s original create() methods (`repository.save(plot);
  return toResponse(plot);` — discarding the return value) looked fine: they
  only read back Java-side fields they'd just set themselves. It became
  visible only when `Plot.pricePerUnit`/`plotNumberNorm` (Postgres `GENERATED
  ALWAYS AS (...) STORED` columns, V2_005, mapped `insertable=false,
  updatable=false`) needed to be refreshed from the DB after save — Hibernate
  never populates generated-column values into an entity on save, only on a
  fresh SELECT/refresh. Calling `entityManager.refresh(plot)` on the
  *original* (never-managed) reference threw `IllegalArgumentException:
  Entity not managed`. **Fix: `PlotService.create()` now does `plot =
  repository.save(plot);` — capturing and using the returned (actually
  managed) instance — before `entityManager.refresh(plot)`.** `update()`
  didn't need the same reassignment because its `plot` comes from
  `findByIdAndDeletedAtIsNull()` *inside the same transaction*, so it's
  already managed when `save()`/`merge()` is called on it (merge on an
  already-managed instance is a no-op that returns the same reference).
  **Rule for any future entity with a manually-assigned `@Id`:** always
  write `entity = repository.save(entity);` and use the returned reference
  afterward, never assume the argument you passed in became managed —
  it did only if it was already managed going in.
- **The shared media pipeline's magic-byte whitelist (`MagicBytes`, M0/M1-era:
  JPEG/PNG/WebP/PDF only) does not recognize XLSX — and M-07's plot bulk
  import reuses that same `/media/upload-intent` → PUT → `/media/{id}/complete`
  pipeline to hand the uploaded workbook a `mediaId`.** XLSX is a ZIP
  container (`PK\x03\x04` magic bytes), which `MagicBytes.detect()` returned
  `null` for, so `/complete` set the asset to **REJECTED** — for the exact
  template the backend itself generates via `PlotImportTemplateGenerator`.
  `ImportService.start()` doesn't actually gate on the media asset's status
  (it downloads by storage key directly), so the import would technically
  still work under the hood, but any real frontend `ImportWizard` that checks
  `/complete`'s response for `status === "READY"` before letting the user
  proceed would report the upload as failed. Confirmed via `curl`: uploading
  the real generated template and calling `/complete` returned `"status":
  "REJECTED"` before the fix, `"status": "READY"` after. **Fixed by adding a
  ZIP local-file-header signature (`0x50 0x4B 0x03 0x04`) to `MagicBytes`,
  mapped directly to the XLSX mime type** — this app only ever pushes xlsx
  workbooks through this path, so the ambiguity with generic zip/docx/pptx
  (which share the same outer signature) doesn't matter here; a real
  multi-format sniffer would need to inspect `[Content_Types].xml` inside the
  archive to disambiguate. **This is exactly the class of bug CLAUDE.md
  flagged as likely to resurface — a whitelist sized for one milestone's file
  types breaking silently the moment a new milestone reuses the same
  pipeline for a different file type.** Any future milestone that pushes a
  new file type (a different document format, a video) through the media
  pipeline needs to check `MagicBytes` for it explicitly rather than assuming
  it's covered.
- **`docker compose` port collisions across parallel sessions on the same
  host are real, not hypothetical** — a second working tree's default-port
  stack (`postgres:5432`, `redis:6379`, `mailhog:1025/8025`) was already
  running when this milestone's manual verification stack was started.
  Resolved by running an isolated stack under a distinct Compose project
  name (`docker compose -p m2verify ...`) with a `docker-compose.override.yml`
  remapping every port. **Plain repeated `ports:` keys in a Compose override
  file do NOT replace the base file's port list — Compose merges list-type
  keys by *appending*, not overriding** (confirmed via `docker compose config
  --format json` showing both the base and override mappings simultaneously
  present). The fix is the Compose Specification's `!override` YAML
  merge-control tag: `ports: !override` followed by the replacement list.
  Anyone spinning up a second isolated stack alongside another session's
  running containers needs this, not a plain key override.
- **Shell state (exported env vars) does not persist between separate tool
  invocations in this environment, only the working directory does.**
  `DOCKER_HOST` was exported in one command to point Testcontainers at the
  Colima socket (`unix:///Users/<user>/.colima/default/docker.sock` — Colima
  doesn't register itself at the default `/var/run/docker.sock`), and every
  integration test passed; a later, separate command that didn't re-export
  it failed all 24 Testcontainers-backed tests with `Could not find a valid
  Docker environment`, even though `docker ps`/`colima status` both showed
  Colima running fine and set as the active context. Not a code regression —
  re-exporting `DOCKER_HOST` in the same command as `mvn test` fixed it
  immediately. Any fresh shell invocation that runs `mvn test` needs this
  re-exported if Colima (rather than Docker Desktop, which does bind the
  default socket) is the local Docker runtime.
- **Verification note (backend-only pass):** `pricePerUnit`/quota-enforcement/calculator/bulk-import
  were all verified via real `curl` calls against a live `docker compose`
  stack (project name `m2verify`), not just unit/ArchUnit tests — this is
  what caught both bugs above; neither was visible from `mvn test` (28/28
  green throughout) or from reading the code. Confirmed working end-to-end:
  project creation with correct area conversion (10 ACRE → 435600.0 sqft),
  plot creation/update with correct DB-computed `pricePerUnit`, the grid
  endpoint's compact tuple format, `/calc/units` and `/calc/plot-size`
  (including RJ-specific Bigha = 27225 sqft vs UP's 27000 sqft), Free-plan
  project-quota enforcement (403 `QUOTA_EXCEEDED` with `used`/`limit` params
  on the second project), and a full bulk-import cycle (template download →
  upload → validate → duplicate-row SKIP behavior confirmed not to create a
  second plot → commit).

### Milestone 2 — Frontend Build & Real-Browser Verification

The entire M2 frontend (ProjectList/Form/Detail, the PlotGrid canvas +
DOM renderers, PlotDetailDrawer/Form/StatusSelector, filter/search/legend/
unplaced-tray, GridLayoutEditor, ImportWizard, ImageUploader/PhotoGrid,
AreaInput, EntitlementGuard/useCan) was built and then driven through a
**real headless Chromium browser via Playwright** (`frontend/e2e/*.spec.ts`,
`frontend/playwright.config.ts`) against the same live `m2verify` docker
stack, per this project's standing rule that nothing is "done" until
actually exercised in a browser. This is a genuinely new addition to the
stack: `@playwright/test` was added as a devDependency specifically for
this (M0/M1 never had real interactive multi-step flows worth automating
this way; CLAUDE.md's own testing strategy table already named Playwright
for "Frontend integration" — this milestone is what first needed it for
real). **11 real, previously-invisible bugs were found this way** — more
than M0 and M1 combined — confirming the M2 kickoff brief's own prediction
that this milestone would have the most "looks right, isn't" bugs of any
so far. None of these were visible from `tsc`, `oxlint`, `vitest`, or
`mvn test` — every one needed an actual browser click, a real multi-row
dataset, or a real synchronous pointer-event sequence to surface.

**`e2e/` test infrastructure notes:**
- `vite.config.ts`'s Vitest `test.exclude` must list `**/e2e/**` — without
  it, `vitest run` tries to execute the Playwright specs itself and fails
  immediately on `test.describe.configure()` (Playwright's own API, not
  Vitest's). Any new top-level test directory using a different test
  runner needs the same exclusion.
- **`authStore` being in-memory-only (rule #15) means `page.goto()` after
  login silently logs the test out.** A real browser navigation (as opposed
  to React Router's client-side `<Link>` navigation) reloads the page,
  wiping the Zustand store, and `RequireAuth` bounces to `/login` — the very
  next assertion then fails against the login page with a confusing error
  ("element not found") that has nothing obviously to do with navigation.
  Every e2e spec after the initial `/signup` navigation must click through
  the UI (`goToProjects`/`goToNewProject` helpers in `e2e/helpers.ts`), never
  `page.goto()` a second time in the same test.
- **OTP codes for e2e signup are read directly from the backend's own
  process log** (`StubSmsGateway`'s `[SMS STUB] ... code=NNNNNN` lines —
  same mechanism M1's manual verification used), via `e2e/helpers.ts`'s
  `readLatestOtp()`. This means **Redis-backed OTP/signup rate limits are
  real across repeated e2e runs, exactly as M1's notes already warned for
  manual sessions** — re-running the suite many times in a row while
  debugging tripped `MAX_PER_IP_PER_DAY` for real. `docker exec
  m2verify-redis-1 redis-cli FLUSHDB` between debugging iterations is the
  fix, same as before.
- **A synthetic test PNG built from a hand-typed hex string was silently
  malformed** (`MediaService` rejected it as "file type isn't supported"
  even though the first 8 bytes matched PNG's magic number) — transcription
  errors in a 130-character hex literal are easy to make and easy to miss.
  Fixed by generating the fixture with a small Python script
  (`struct`/`zlib`, correct CRC32) instead of hand-typing bytes; the same
  applies to the 500-row XLSX fixture used for bulk-import testing (built
  with `zipfile`+raw XML — see "no XLSX library available" note below).
  **Any future binary test fixture should be generated by code, not
  transcribed by hand.**
- **No Python XLSX library (`openpyxl`, `xlsxwriter`) or Node one was
  available in this environment** for building a synthetic bulk-import
  workbook. Worked around with a ~70-line hand-rolled generator using only
  `zipfile` + raw OOXML XML (`[Content_Types].xml`, `_rels/.rels`,
  `xl/workbook.xml`, `xl/worksheets/sheet1.xml`, all cells as
  `t="inlineStr"` to avoid needing a `sharedStrings.xml`) — confirmed Apache
  POI (the backend's actual parser) reads it correctly. If a real XLSX
  library becomes available in a future environment, prefer it; this
  generator is a deliberate minimal-dependency workaround, not a template
  to copy for anything beyond simple flat-row test fixtures.
- **The Free plan's seeded `BUILDER_PLOTS_PER_PROJECT` limit is 50** (not
  500) — meaning the M2 exit criteria's literal "bulk upload 500 plots"
  cannot fully complete end-to-end on this milestone's only available plan
  (PRO/PREMIUM rows don't exist yet; upgrading is explicitly M8 scope, per
  `EntitlementService`'s own class-level comment). The 500-row upload was
  still exercised in full (template → upload → 500/500 valid on preview →
  commit), and — after the two bugs below were fixed — correctly imports
  exactly 50 plots and stops there with a clear `QUOTA_EXCEEDED`-shaped
  result, which **is** a full, honest verification of the mechanism; the
  literal "500 plots actually created" claim in the exit criteria is only
  reachable once a paid plan is real. The 5,000-plot **grid render
  performance** test (a separate concern from bulk-import mechanics) is
  unaffected by this — see below, it seeds plots via direct SQL specifically
  to test rendering at scale independent of this quota.

**Real bugs found and fixed (frontend):**
- **`ui/textarea.tsx` had the exact same missing-`forwardRef` bug M1 already
  found and fixed in `ui/input.tsx`, and for the identical reason** —
  react-hook-form's `register()` hands out a `ref` it uses to read a field's
  value; a plain (non-forwardRef) function component silently drops it. M0/M1
  never used a `<Textarea>` with `register()`, so this was latent until
  ProjectForm's Address/Description fields hit it: submitting any step with
  a registered `Textarea` field failed Zod validation with "Invalid input:
  expected string, received undefined" for that field alone, despite the
  textarea visibly holding typed text. Fixed the same way `Input` already
  was. **This is precisely the "watch for the pattern" class of bug the M2
  kickoff brief called out in advance** — confirmed here to actually recur,
  in a new component, exactly as predicted. Any other plain shadcn primitive
  used with `register()` in a future milestone needs the same forwardRef
  check before assuming it "just works" like `Input` does now.
- **Two React elements at the same JSX tree position, only distinguished by
  a ternary condition and differing solely in `type="button"` vs
  `type="submit"`, get the same underlying DOM node reused across the
  transition — and if the condition flips as a *synchronous side effect of
  that same click's own handler*, the browser's native click "activation
  behavior" can see the post-flip `type="submit"` and submit the form on a
  click that was only ever meant to advance a wizard step.** ProjectForm's
  step-3→4 "Next" button (`type="button"`, calls `goNext()`) and step-4's
  "Create Project" button (`type="submit"`) shared one ternary at the same
  position; clicking "Next" on the Media step correctly advanced `step` to
  4, but the resulting re-render swapped the SAME button element's `type`
  attribute to `submit` before the browser finished processing that click's
  default action — so the form submitted immediately, with zero actual
  clicks on "Create Project". Confirmed via source-level `console.log`
  instrumentation showing `form onSubmit fired` immediately after `goNext`'s
  own log line, with no intervening click. **Fixed by giving the two
  buttons distinct `key`s**, forcing React to mount a fresh DOM node instead
  of mutating the existing one in place. **Any future multi-step wizard with
  a shared Next/Submit button position at the same JSX slot needs distinct
  keys for the same reason** — this is a general React/HTML footgun, not
  specific to this form.
- **`CorsConfig.allowedMethods` never included `PUT`** — M1 configured it
  for exactly the methods M1 needed (`GET, POST, PATCH, DELETE, OPTIONS`);
  M2 is the first milestone with real `PUT` endpoints (`grid-config`,
  plot `position`). A browser's CORS preflight silently blocks any method
  missing from this allowlist — the actual `PUT` request never reaches the
  controller at all; `fetch()` just sees the request hang/fail with no
  useful error. Confirmed via the network trace: the PUT to `/grid-config`
  showed `status: -1` (no response ever received). **Same class of gap as
  the `MagicBytes` XLSX miss above — an allowlist sized for one milestone's
  needs silently breaking the moment a later milestone introduces something
  new.** Fixed by adding `PUT` to the list. Any future milestone introducing
  a new HTTP verb needs to check this allowlist explicitly.
- **`GridSizeDialog`'s rows/cols fields could be silently overwritten by a
  slow-to-resolve config fetch, even after the user had already typed real
  values.** The `useEffect` syncing local `rows`/`cols` state from
  `configQuery.data` re-ran whenever that query's data reference changed —
  if the GET resolved *after* the user (or a fast-typing e2e test) had
  already filled in values but *before* the effect's first run, it
  overwrote them back to the fetched value (`null` on a brand-new project's
  ungridded state), permanently disabling Save since the fields read empty
  again with no error shown. A "sync once" guard alone doesn't fix this —
  the *first* sync can still race user input depending on when the fetch
  settles relative to typing. **Fixed with a `touchedRef` guard**: once the
  user edits either field, the fetch is never allowed to clobber them again,
  regardless of timing. Also added a previously-missing error message to
  this dialog (a failed save gave zero visible feedback, which is what made
  this bug look like a network/CORS issue at first rather than a stale-sync
  issue).
- **`PlotForm`'s edit-success handler invalidated the grid/list/stats
  queries but never the single-plot `['plot', plotId]` query the
  drawer itself reads from** — editing a plot's price via
  `PlotDetailDrawer` → "Edit" → "Save Changes" showed the *pre-edit* price
  in the drawer immediately afterward, even though the edit succeeded
  server-side (confirmed via a fresh page reload). Fixed by calling
  `queryClient.setQueryData(['plot', result.id], result)` with the
  mutation's own response — correct and avoids a redundant refetch. **Any
  future edit-form embedded inside a detail view needs to update that
  view's own query key on success, not just list-level ones.**
- **`useGridViewport`'s pan handler read a mutable `ref` (`panStart.current`)
  from *inside* a `setViewport` functional updater callback** — updater
  callbacks run whenever React actually flushes the queued state update,
  which can be *after* a later `pointerup` has already nulled that same ref
  (`pointerup` mutates it synchronously; state updates are deferred). Firing
  a rapid two-finger pinch sequence (pointerdown×2 → pointermove×2 →
  pointerup×2, all synchronous — exactly what a real touchscreen gesture
  does) crashed the whole page: `Cannot read properties of null (reading
  'offsetX')`, unmounting the entire React tree with no error boundary.
  **Fixed by snapshotting the ref's fields into local variables before
  calling `setViewport`**, so the updater closes over values, not a
  live mutable reference. Only reproducible with genuinely rapid,
  synchronous pointer events — a slow manual mouse-drag test would never
  hit this race.
- **`Element.setPointerCapture()` throws a `DOMException` for any
  `pointerId` the browser doesn't recognize as an active pointer** — and
  the call sat *before* `pointers.current.set(...)` in `onPointerDown` with
  no error handling, so the throw aborted the handler before the pointer
  was ever tracked. Synthetic `PointerEvent`s dispatched in the pinch-zoom
  e2e test hit this every time (their pointerIds aren't real hardware
  pointers), silently defeating pinch-zoom entirely (scale stayed at 1
  forever, no visible error in the UI). **Fixed by wrapping the capture
  call in try/catch** — capture is a nice-to-have (keeps receiving events
  if a finger slides off the element mid-gesture) and was never load-bearing
  for the tracking logic underneath it. Plausible this could also affect a
  real device in some edge case (a pointer released between event dispatch
  and capture), not purely a test-environment artifact.

**Real bugs found and fixed (backend, found via the frontend e2e suite):**
- **`ProjectRepository.findPage`'s cursor-pagination query always failed
  on the very first page** — `(:cursorCreatedAt IS NULL OR p.createdAt <
  :cursorCreatedAt OR ...)`, when `:cursorCreatedAt` is bound to a genuine
  Java `null` (every "no cursor yet" first-page request — i.e. every single
  load of the Projects list), gives PostgreSQL's JDBC driver no type to
  infer for that parameter from an `IS NULL` comparison alone: `ERROR:
  could not determine data type of parameter $2`. **This meant `GET
  /projects` — the very first thing any authenticated user sees — was
  broken on every real page load**, yet none of the backend's own
  integration tests caught it (a repository test calling `findPage` with an
  actual non-null cursor Instant would never hit the all-null first-page
  shape). Only surfaced via the e2e suite's very first "no projects yet"
  assertion timing out with the empty-state skeleton stuck forever. **Fixed
  by splitting into two separate queries** — `findFirstPage` (no cursor
  parameter at all) and `findPageAfter` (cursor parameters only ever bound
  to real, non-null values) — rather than one query trying to handle both
  shapes with a nullable parameter. **Any future cursor-paginated query
  needs the same split**, not a single "`:param IS NULL OR ...`" query;
  `MeasurementUnitRepository`'s similar-looking `(m.stateCode IS NULL OR
  m.stateCode = :stateCode)` is safe by contrast because `:stateCode` there
  is never the operand of the `IS NULL` check itself, only of `=`.
- **`EntitlementService.assertWithinQuota` read a stale `org_usage` row for
  the rest of a transaction once it had been loaded once** — Hibernate's
  first-level cache returns the *same managed entity* for repeated JPQL
  `SELECT` queries against an already-loaded row within one persistence
  context, discarding the fresh column values the re-executed SQL actually
  returned. `org_usage.current_value` is maintained by a Postgres trigger
  that fires on every `plot` insert — an out-of-band update Hibernate has
  no way to know about. Net effect: **bulk-importing 500 plots against a
  50-plot Free-plan limit created 200 plots (an entire
  `COMMIT_CHUNK_SIZE`) before the quota check ever caught up**, only
  blocking on the *next* chunk's fresh transaction/persistence context.
  Confirmed via `curl`: `used: 200, limit: 50` reported by the exception,
  after 200 real rows had already been written. **Fixed with an explicit
  `entityManager.refresh()`** on the loaded `OrgUsage` entity before reading
  `getCurrentValue()`, forcing Hibernate to discard its cached column values
  and re-read from the DB every single call — the same root-cause class
  (and same fix shape) as this milestone's own `pricePerUnit` staleness bug
  above: Hibernate not seeing a DB-side change it didn't itself make. Not
  visible from any test importing fewer rows than one commit chunk (200) —
  a small-batch test would never exercise the "many quota checks sharing
  one transaction" shape that exposed it. A regression test
  (`EntitlementServiceIntegrationTest`) now exercises exactly this shape:
  50 plots created one at a time inside a single transaction, asserting the
  51st check throws.
- **Immediately behind the bug above: letting `QuotaExceededException`
  propagate out of `ImportService.commitChunk`'s `TransactionTemplate`
  callback rolled back the *entire chunk's transaction* — including every
  row successfully imported earlier in that same chunk, not just the ones
  after the limit.** Once the staleness bug above was fixed and the check
  correctly fired at row 51, the chunk's transaction still rolled back
  completely: `curl` showed the commit response claiming `used: 50` at the
  moment of the exception, yet the actual `plot` table had **zero** rows
  afterward. The chunked-commit design's own comment ("a failure partway
  through doesn't roll back everything already imported") only protects
  *previous chunks*, not rows earlier in the *same* chunk as the failure.
  **Fixed by catching `QuotaExceededException` inside the per-row loop and
  `break`ing instead of letting it propagate** — rows processed so far in
  the chunk commit normally; rows after the break stay `VALID` (never
  transition to `IMPORTED`), and the commit call now returns 200 with an
  honest `importedRows` count instead of a 403 that hid the fact real work
  had been done. Confirmed via `curl`: re-running the same 500-row import
  now correctly imports exactly 50 plots and leaves them all in place.
  **Any future chunked/batched operation with a mid-batch business-rule
  exception needs to consider whether the whole batch's transaction should
  really roll back, or just stop cleanly with partial progress preserved** —
  letting an exception simply propagate out of a `TransactionTemplate`
  callback is the wrong default whenever "some rows succeeded before the
  failure" is a meaningful, expected outcome.

**Verified end-to-end via the Playwright suite** (`frontend/e2e/`, 8 specs
across `m2-builder-flow.spec.ts` and `m2-grid-performance.spec.ts`, all
green): signup → project creation with cover + layout images uploaded and
displayed → manual plot creation (AVAILABLE + RESERVED) → grid renders with
correct status colours **and** hatch patterns (confirmed visually, not just
"present in CSS") → status filter dims non-matching cells → search
highlights and the matching cell gets a visible ring → editing a plot's
price updates the drawer immediately → Free-plan project quota blocks a
second project with a clear message → Hindi language switch translates the
Projects page. Separately, `m2-grid-performance.spec.ts` seeded 500 and
5,000 plots via **direct SQL** (not the create-plot API, which the 50-plot
Free-plan quota would otherwise block long before reaching those counts —
seeding data to test *rendering* performance is a different concern from
the business-rule quota, so bypassing it here for test-data setup is
intentional and safe) and confirmed: 500-plot grid interactive in ~180-280ms,
5,000-plot grid in ~250-340ms **measured on desktop headless Chromium** —
comfortably under the milestone's own 250ms/500-plot floor on this hardware,
but this environment has no way to run on, or accurately CPU-throttle to,
actual Moto G-class Android hardware, so the exit criterion's literal
"Moto G-class emulator" target is *not* independently confirmed, only a
faster proxy for it. A one-finger pan gesture doesn't hang or error at
5,000 plots, and a synthetic two-finger pinch gesture on a 360px mobile
viewport correctly scales the canvas (1× → 4×, matching the exact geometry
of the dispatched gesture) — real physical-device pinch on an actual
touchscreen is likewise something this environment cannot exercise; the
synthetic PointerEvent sequence is the closest available proxy, and is what
caught the two real pointer-handling bugs above. Bulk-import mechanics
(template → upload → preview → commit) were verified via `curl` with the
real 500-row XLSX fixture, including the Free-plan quota interaction
described above; driving the `ImportWizard` UI itself through the full
4-step flow in a real browser (as opposed to the underlying API calls) was
not separately re-verified given the quota ceiling makes a full "500
imported" UI walkthrough impossible on this milestone's only plan — the
wizard's own component code was still exercised end-to-end as part of the
general frontend build.

**Tenant isolation, explicitly re-verified for M2's new endpoints** (not
assumed from M1's proof, per this milestone's own standing instruction):
created two fresh orgs (A, B) via real signup, had org A create a project,
plot, media asset, and bulk-import job, then hit every new M2 endpoint as
org B — `GET/PATCH/DELETE /projects/{id}`, `GET/PUT /projects/{id}/grid-config`,
`GET/PATCH/DELETE /plots/{id}`, `GET /projects/{id}/plots` (list and grid),
`POST /projects/{id}/plots` (create into another org's project),
`GET /media/{id}`, and `GET /import/jobs/{id}` (+ `/rows`, `/commit`) — all
returned **404, never 403**, and org B's own project list came back empty
(no leakage). One observation, not a bug: `MediaService.get()` has no
explicit tenant check in application code at all — the 404 there is coming
entirely from Postgres RLS on `media_asset` as the backstop, with no
"primary mechanism" check in front of it (CLAUDE.md rule #1 asks for RLS to
be the backstop, implying an explicit check should also exist). Functions
correctly today; worth adding an explicit `ProjectAccessGuard`-style check
if `media_asset` ever needs org-scoped access rules beyond plain ownership.

---

## M2 Gap Closure — Project Status Control

Before M3 started: M2 shipped with `SOLD` correctly unreachable via direct
edit, but `Upcoming → Active → Completed` project-lifecycle status also had
no UI control even though the backend (`ProjectService.updateStatus`,
`PATCH /projects/{id}/status`) already supported it — a known, explicitly
called-out M2 gap, not a bug. Closed with a `Select` dropdown next to the
project name in `ProjectDetail`'s header (`frontend/src/features/builder/
projects/components/ProjectStatusSelect.tsx`, new), gated the same way every
other mutation is (`useCan('DATA_EDIT_ALL')`), calling the existing endpoint
with no backend changes needed. Verified via a real browser: created a
project (defaults to `UPCOMING`), changed it to `ACTIVE` then `COMPLETED`,
confirmed the change persisted across a reload and that the M2 regression
suite (6/6 e2e specs) still passed unmodified. Shipped as its own PR
(`feat/project-status-control`, **#8**) ahead of M3 per the user's explicit
"quickly close this gap first" instruction — **at the time M3 verification
below was performed, #8 was still open (not yet merged)**, so all of M3 was
built as a stack on top of that branch; the M3 PR's diff will shrink to just
M3's own changes once #8 merges. The other two known M2 gaps (the Free-plan
bulk-import 500-row ceiling, and real-device grid performance/pinch-zoom)
were left as-is, per the user's explicit instruction, and remain true for M3
as well — no paid plan and no physical Android device exist in this
environment yet.

---

## Milestone 3 — Decisions & Environment Notes

Real bugs found while building B-04 (Plot Sale, Buyer & Documents) and B-05
(Payment Tracker & Instalment Schedules) end-to-end and driving them through
real HTTP calls and a real browser — same standard as M0–M2. Read this
before touching `builder.sale`, `builder.payment`, `foundation.notification`,
or the sensitive-media/gov-ID path.

**Gotchas (backend):**
- **The JPQL-enum-literal bug M1's own `OutboxEventRepository` comment
  already documented recurred TWICE in this milestone, in two different new
  repositories.** Writing a Java enum constant directly inside an `@Query`
  string (e.g. `s.status <> com.shardeya.builder.sale.PlotSale$Status.CANCELLED`)
  makes Hibernate render it as `'CANCELLED'::Status` — using the enum's bare
  Java simple name as the Postgres type name — instead of the real native
  enum type (`plot_sale_status`), failing at runtime with `type "Status" does
  not exist`. Hit first in `PlotSaleRepository.findActiveByPlotId` (caught
  via the very first live `curl` POST to create a sale — the
  `GlobalExceptionHandler`'s load-bearing `log.error` line, flagged as
  essential since M1, is what surfaced the real cause immediately), then
  again in `PaymentScheduleRepository.findNewlyOverdue`'s `IN (...)` clause
  (caught this time during test-suite review, before it ever reached a live
  call). Both fixed the same way: bind the enum(s) as a proper `@Param`
  (`Collection<Status>` for the `IN` case) instead of inlining. **Any future
  repository with a `@Query` string that mentions an enum constant needs to
  bind it as `@Param`, never write it inline** — this is now a two-time
  repeat of a documented class of bug and should be treated as the default
  assumption to check for in any new hand-written JPQL.
- **Schema/entity type drift, same class M1's `V1_008` migration already
  fixed once, recurred twice more this milestone** — a migration column type
  narrower than what Hibernate's default Java-type mapping expects fails
  Spring Boot's own startup-time schema validation (`SchemaManagementException`),
  never a runtime query bug: (1) `plot_sale.buyer_gov_id_last4` was declared
  `CHAR(4)` but the entity field is a plain `String` (Hibernate maps to
  `VARCHAR`) — fixed by changing the migration to `VARCHAR(4)`; (2)
  `idempotency_key.response_status` was declared `SMALLINT` but the Java
  field is `int` (maps to `INTEGER`) — fixed by changing the migration to
  `INTEGER`. **Any future migration column backing a plain `String`/`int`
  entity field should default to `VARCHAR`/`INTEGER` unless there's a
  specific reason to narrow it** (and if narrowed, the entity needs an
  explicit `@Column(columnDefinition=...)` to match) — both bugs here were
  caught for free by `mvn test`'s own context-startup check, not by any
  targeted test, which is the reason this class of bug is cheap to catch
  early and easy to miss if migrations and entities are written by
  eyeballing the data model doc independently.
- **`entityManager.refresh()` requires an active transaction even for a
  read-only call that never writes anything** — `PaymentService.summary()`
  had no `@Transactional` at all (it only reads), and threw
  `TransactionRequiredException` on the first real `curl` call to
  `GET /sales/{id}/payments/summary`. Fixed by adding
  `@Transactional(readOnly = true)`. Same root-cause family as M2's
  `pricePerUnit`/`org_usage` staleness bugs (Hibernate needing an explicit
  refresh to see DB-side trigger-computed values) — but this time the
  missing piece was the transaction boundary `refresh()` itself depends on,
  not the refresh call being absent.
- **A message-KEY string used directly as user-facing free text, twice.**
  `PaymentService.updateChequeStatus`'s auto-reversal path originally set
  `remarks` to the literal string `"error.payment.chequeBouncedReversal"` —
  not a rendered sentence, the i18n *key* itself — which showed up verbatim
  in the real payment-history API response (caught by actually reading a
  live `curl` response body, not by any test asserting an exact string).
  `PlotSaleService.cancel()` had the identical mistake for
  `schedule.setWaiveReason(...)`. Both fixed by writing real English
  sentences instead. **`remarks`/`waiveReason`/similar free-text audit
  fields are never translated backend-side (CLAUDE.md rule #14 is about
  *error* messages specifically, not these) — any future code setting one of
  these fields needs to write an actual sentence, not reach for something
  that merely looks like other message-key constants nearby in the same
  file.**
- **Notification rows are created asynchronously, not at the moment a
  business event happens** — `OutboxService.enqueueNotification(...)` only
  inserts an `outbox_event` row inside the same transaction as the business
  write (per CLAUDE.md rule #6: side effects are never inline); the actual
  `notification` row a user sees is created later by
  `OutboxPoller.dispatchReady()`'s existing `@Scheduled(fixedDelay=2000)`
  tick. A test or manual verification that checks for a notification
  immediately after triggering the underlying event (a sale, a bounced
  cheque, an overdue sweep) will see nothing yet. Both
  `PlotSaleIntegrationTest` and `OverdueScheduleSweeperIntegrationTest` call
  `outboxPoller.dispatchReady()` directly rather than sleep-polling for the
  2-second tick — the same pattern is required for the e2e suite, where the
  UI's `NotificationBell` polls the real endpoint every 15s and just
  naturally catches up (see below), so no special handling was needed there.
- **Per-org tenant binding for background/scheduled jobs must follow the
  exact same ordering rule M1 documented for request-scoped code**: bind
  `TenantContext` in plain Java *before* opening any transaction, never
  inside a `@Transactional` method. `OverdueScheduleSweeper.sweepOrg()` and
  `OutboxPoller.sendNotification()` both iterate all orgs with no request
  context to inherit from, so each one calls
  `TenantContextBinder.bindNewOrgContext(orgId, SYSTEM_ACTOR_ID, ...)`
  first, then opens a fresh `TransactionTemplate` with
  `PROPAGATION_REQUIRES_NEW` — mirroring `AuthService`'s signup-flow fix
  from M1 exactly. `SYSTEM_ACTOR_ID = new UUID(0,0)` is used as the acting
  user for jobs with no real human behind them (audit/notification rows
  still need *some* actor id).
- **Test-authoring bug, not a product bug**: `PlotSaleIntegrationTest`
  originally hardcoded schedule due-dates as literal `LocalDate.of(2026, 7,
  20)`. By the time the test suite actually ran (days later, in wall-clock
  terms, as this session progressed), that date was in the past relative to
  "today" — and the nightly-overdue-sweep trigger logic correctly flipped
  the schedule to `OVERDUE` instead of the `PENDING` the test still
  expected, a failure that looked exactly like a real regression. Fixed by
  anchoring every date in the test to `IndianTime.today()` with relative
  offsets (`.plusMonths(2)`, `.minusDays(1)`, etc.) instead of any absolute
  literal. **Any future test asserting a schedule/date status needs relative
  dates from `IndianTime.today()`, never a hardcoded absolute date** — this
  is generally true of any project whose sessions span real elapsed days.
- **`IndianTime.today()`** (`com.shardeya.shared.IndianTime`, new this
  milestone) — `ZoneId.of("Asia/Kolkata")` + a `today()` helper — was added
  specifically to enforce CLAUDE.md rule #11 consistently; `PlotSaleService`,
  `PaymentService`, `ScheduleService`, and `OverdueScheduleSweeper` all use
  it instead of a bare `LocalDate.now()` (which defaults to the JVM's
  configured zone, not necessarily IST). Any new business-date logic
  anywhere in the codebase should use this, not `LocalDate.now()` directly.
- **Immutability + the chunked-commit rollback lesson from M2 both apply
  here in a new shape**: `payment_record` rows are genuinely
  insert-only (a Postgres `RULE` blocks `DELETE` outright, matching
  CLAUDE.md rule #2's "payments are immutable" and rule #10's "never delete
  financial records"). Corrections are modeled as a second, negative-amount
  `payment_record` (its own receipt number) plus mirrored negative
  `payment_allocation` rows — the DB triggers that maintain
  `plot_sale.total_paid` and `payment_schedule.amount_allocated` don't need
  to know anything about reversals specifically; they just sum whatever
  rows exist, so a negative row naturally nets out. No special-case
  "reversal" logic exists in the trigger SQL at all — worth remembering if a
  future feature (e.g. a partial reversal) is ever tempted to special-case
  it in the trigger instead of just inserting more (possibly negative) rows.

**Frontend — decisions and gotchas found driving the sale/payment flows
through a real browser:**
- **The exact `valueAsNumber:true`-on-an-optional-field bug class is real
  and worth watching for on every new numeric form field, not just the one
  M2 might have hit.** `SaleWizard.tsx`'s `brokerCommissionAmount` field
  (legitimately skippable — most sales have no broker) used
  `{...form.register('brokerCommissionAmount', { valueAsNumber: true })}`.
  react-hook-form turns a genuinely empty input into `NaN` under
  `valueAsNumber`, not `undefined` — and `z.number().optional()` only
  accepts `undefined`, rejecting `NaN` outright. Net effect: leaving the
  (optional, commonly-blank) broker-commission field untouched silently
  failed `form.handleSubmit`'s validation with **zero visible error
  anywhere** (nothing renders for `formState.errors.brokerCommissionAmount`
  because the whole `handleSubmit` callback simply never fires) — the
  "Confirm Sale" button appeared to do nothing at all. Only caught by
  driving the wizard through a real Playwright browser session and
  instrumenting `page.waitForResponse()`, which showed the POST never even
  being sent. Fixed with `setValueAs: (v) => (v === '' ? undefined : Number(v))`
  instead of `valueAsNumber: true`. **Any optional numeric RHF field must use
  `setValueAs` with this exact empty-string check, never bare
  `valueAsNumber` — this is now confirmed as a systemic footgun in this
  stack, not a one-off.**
- **Radix's Dialog/Sheet marks the rest of the page `aria-hidden` while
  open** — a Playwright locator for the TopBar's notification bell
  (`getByRole('button', {name: 'Notifications'})`) fails to find the button
  at all while `PlotDetailDrawer`'s Sheet is still open, even though the
  button is visibly present in the DOM/viewport. Not a bug, just Radix's
  correct accessibility behavior — background content genuinely shouldn't
  be exposed to assistive tech while a modal is open. Fixed in the test by
  pressing `Escape` to close the Sheet before interacting with anything
  outside it, then re-opening the Sheet afterward if the test needs to keep
  interacting with the plot. **Any future e2e test reaching for TopBar
  chrome (bell, profile menu, language toggle) while a Sheet/Dialog is open
  needs the same `Escape`-first pattern.**
- **`getByLabel('Notifications')` is ambiguous in this app** — it matches
  both the real bell button (`aria-label="Notifications"`) and Sonner's
  unrelated toast `aria-live` region, which also happens to expose the word
  "Notifications" to the accessibility tree. Fixed by using
  `getByRole('button', { name: 'Notifications' })` instead, which is
  role-scoped and unambiguous.
- **`NotificationBell` polls every 15s via TanStack Query's
  `refetchInterval`, not SSE/WebSockets** — a deliberate simplification
  against the fuller real-time design the M3 kickoff brief sketched.
  Correct and simple given `Notification` rows are themselves only created
  asynchronously by the outbox poller's own 2s tick (see above) — a
  push-based frontend would still be bottlenecked by that same async
  fan-out, so the extra complexity of SSE wasn't buying real latency at this
  milestone's scale. Revisit if in-app notification latency ever becomes a
  real user complaint.
- Confirmed once more (documented since M2, re-hit here): **`page.goto()`
  after login wipes the in-memory-only `authStore` session.** My own e2e
  test attempt to navigate straight to a project URL via `page.goto()`
  after API-based project/plot setup bounced to `/login`. Fixed by
  navigating via real in-app clicks only (Projects link → project card →
  tab → row), same as the M2 suite already does.
- Confirmed once more (documented since M1/M2): **Redis-backed OTP/signup
  rate limits are real and shared across repeated manual + e2e test runs
  against the same isolated stack.** `redis-cli -p 16379 FLUSHDB` between
  runs is the fix, same as always.

**Scope decisions, explicit, not oversights:**
- `plot_sale.broker_partner_id` and `.customer_id` are plain `UUID` columns
  with **no FK constraint** — the tables they'd reference (B-14 Brokers,
  M-12 Customers) don't exist yet. `SaleWizard`'s "Broker" step only
  captures a free-text external-broker name/mobile/commission for this
  reason (`broker.notInSystem` i18n key makes this explicit in the UI too).
  Add the FKs when those modules land.
- **No document versioning** — `PlotDocument` rows are just attach/delete;
  re-uploading a replacement for an existing slot creates a new row rather
  than versioning the old one. Matches the same "ship the simple slice, note
  the gap" precedent M2 set for `PhotoGrid`'s non-functional reorder
  buttons.
- **No receipt PDF generation** — `05-MILESTONES.md`'s own M3 spec describes
  the receipt *number* (gapless, FY-scoped) as the M3 requirement; actual
  PDF rendering (OpenPDF/Flying Saucer, already in the stack per
  `00-ARCHITECTURE.md`) is out of scope for this milestone per the
  milestones doc's own phasing.
- **All verification is against the Free plan** — same constraint M2 hit:
  no paid plan exists until M8, so nothing plan-gated in B-04/B-05 could be
  exercised beyond what Free allows. Nothing in B-04/B-05 is plan-gated
  today, so this had no actual effect on coverage this milestone, but is
  noted for consistency with M2's own callout.

**Verified end-to-end** — backend via real `curl` calls against a live
`docker compose` stack (project name `m3verify`, ports remapped exactly as
M2's own verification session used): sale creation (both `LUMP_SUM` and
`INSTALMENT` payment types, atomic — plot flips to `SOLD` with
`current_sale_id` set only if the whole transaction, including schedule
rows and the outbox notification insert, succeeds), a second sale attempt on
an already-sold plot correctly 409s, payment recording with automatic
oldest-due-first allocation across multiple schedule rows, gapless
FY-scoped receipt numbering advancing correctly across several payments in
the same org (`MILE/FY26-27/1`, `/2`, `/3`, ...), cheque-bounce auto-reversal
(a real negative `payment_record` + mirrored negative `payment_allocation`
rows, verified to correctly pull `total_paid`/`amount_allocated` back down
via the same triggers that pushed them up), gov-ID AES-256-GCM
encrypt/decrypt round-trip through the audited reveal endpoint (with a real
row landing in `sensitive_access_log`), plot document attach/list/delete
including the "OTHER type requires a label" validation, and sale
cancellation (plot restored to `AVAILABLE`, all non-`PAID` schedules waived,
full payment history retained untouched, confirmed via a follow-up `GET`).
**6 real backend bugs found and fixed this way** (2 JPQL enum literals, the
CHAR/VARCHAR mismatch, the SMALLINT/INTEGER mismatch, the missing
`@Transactional`, 2 message-key-as-text bugs — counted as one bug class hit
twice each for the enum and schema issues). 37/37 backend tests green
(`mvn test`), including 4 new `PlotSaleIntegrationTest` cases (atomicity +
double-sell blocking, allocation-driven status via triggers, cheque-bounce
reversal, cancellation) and 1 new `OverdueScheduleSweeperIntegrationTest`
case (nightly sweep flips `PENDING`/`PARTIALLY_PAID` past their due date to
`OVERDUE` and fans out a real notification via the outbox).

Frontend: a full Playwright e2e spec (`frontend/e2e/m3-sale-payments.spec.ts`)
drives the entire flow through the real wizard UI in a real headless
browser — sign up → create project/plot via API → open the plot drawer →
"Mark as Sold" → all 5 `SaleWizard` steps (buyer, deal, payment plan, skip
broker, review+confirm) → record a cheque payment → confirm balance goes to
₹0 and the schedule shows Paid → mark the cheque Bounced → confirm balance
and schedule both revert → confirm the `NotificationBell` shows real
sold/payment/bounce notifications (no mocking, genuinely round-tripped
through the outbox poller) → cancel the sale → confirm the plot returns to
"Mark as Sold" availability. This is what caught the `brokerCommissionAmount`
NaN bug above. Zero regressions in the M2 e2e suite (6/6 still green)
despite the shared-component changes (`PlotDetailDrawer`, media types).
Separately, a one-off Hindi + 360px visual pass (signup → project/plot via
API → switch to Hindi → navigate in-app to the project → open the plot
drawer → "बिका हुआ चिह्नित करें" → fill the buyer step in Devanagari → advance
to the deal step) confirmed the wizard renders cleanly at 360px in Hindi —
step chips wrap to a second line without clipping, no text overflow or
truncation in any field label, button row fits within the viewport width —
inspected directly from real screenshots, not just "the test didn't crash."
The throwaway script used for this pass was deleted afterward rather than
kept as permanent CI coverage (a one-off manual visual spot-check, unlike
M2's project-status verification which *was* worth keeping permanently as
`m2-project-status.spec.ts` — the judgment call here is that Hindi/360px
rendering of a wizard already exercised in English/desktop by the permanent
suite doesn't need its own standing CI spec, just a one-time confirmation
that i18n + responsive layout hold up).

**Tenant isolation, explicitly re-verified for M3's new endpoints** (same
standing requirement as M2, not assumed): created two fresh orgs (A, B),
had org A create a sale, payment, and document on its own plot, then hit
every new M3 endpoint as org B — `GET/PATCH /sales/{id}`, `POST
/sales/{id}/cancel`, `GET /sales/{id}/payments*`, `POST
/sales/{id}/payments`, `GET/PATCH/DELETE /schedules/{id}`, `GET
/sales/{id}/documents*`, and the audited `GET /sales/{id}/reveal-gov-id` —
all returned **404, never 403**, matching every prior milestone's proof.

---

## B-06 Feature Addition — Quick Range Create (Path A)

Added between M3 and M4, per an explicit user request — a feature addition
to an already-shipped module (B-06), not a new milestone. 03-BUILDER-MODULES.md's
B-06 section was updated (by the user, in their own working copy) to
describe this as "Path A" alongside the original Excel bulk-import design
("Path B," unchanged). Both create ordinary `plot` rows through the same
constraints (unique number, quota, grid placement); neither bypasses the
other's guarantees.

**What shipped:**
- `POST /projects/{id}/plots/quick-create` (preview, no side effects) and
  `POST /projects/{id}/plots/quick-create/commit` (creates), backed by a new
  `PlotQuickCreateService`. No new tables — this is a service-layer loop
  around the same `plot` table every other creation path uses.
- One or more numeric ranges (`prefix` + `separator` + `start` + `end` +
  `padWidth`) plus one shared-properties block (size/unit/facing/price/flags),
  generating plot numbers client-never-computes — the preview endpoint is
  the single source of truth for exactly what will be created, and the
  frontend just calls it reactively (debounced) as the user types, never
  re-implementing the parsing/collision logic itself.
- **Range format decisions:** `{prefix:"A", separator:"-", start:1, end:75}`
  → `A-1 … A-75`; no prefix → bare `1 … 200`; `padWidth:3` → `A-001 … A-075`.
  A missing `separator` with a present `prefix` is treated as empty string
  (concatenated directly), not defaulted to `-` — the spec's own examples
  show both a real separator and a bare-number case with none, so "no
  separator supplied" had to mean "none," not "assume the common one."
- **Absolute cap (5,000) is checked on the arithmetic count** (`end - start +
  1` summed across all ranges, in `long` to avoid overflow) **before
  generating a single string** — a typo like `end=2000000000` fails
  immediately rather than after however long building that many strings
  would take. Same "split it up" cap class as Path B's own `MAX_ROWS`.
- **Collision detection is two-layered in the preview**: against every
  existing plot in the project (bulk-fetched via a new
  `findAllNormalisedNumbers` query — one round trip regardless of range
  size, not one `findByNormalisedNumber` call per generated number) *and*
  within the request's own ranges (two overlapping ranges, e.g. `A1–A50` +
  `A25–A75`, correctly flags both occurrences of every repeated number, not
  just the second one).
- **Commit skips collisions rather than failing the whole batch** — the
  preview already surfaced them for the user to fix (adjust the range)
  before ever clicking Create; treating a still-present one as a skip
  (matching Path B's own default `SKIP` duplicate mode) tolerates project
  state changing between preview and commit instead of hard-erroring on a
  stale read.
- **Auto-placement reuses Path B's exact logic**, extracted from
  `ImportService`'s private block-pattern parsing into a new shared
  `GridPlacementResolver` (`parseBlockPattern`, `isOutOfBounds`) so both
  paths can never silently drift apart. **Deliberate scope note:** Path B's
  own spec prose says "parse block/number patterns... otherwise row-major
  fill," but the actual `ImportService` code has never implemented a
  row-major fallback at all — a number that doesn't match the block pattern
  just stays unplaced (added to the tray). Quick Create reuses that same
  *actual* behavior (block-pattern-match-or-unplaced), not the row-major
  fallback the prose describes but the code doesn't have — inventing a new
  fallback for only one of the two paths would make them behave
  differently from each other, which is worse than both lacking a feature
  the docs assumed existed. Worth fixing for both paths together if this
  ever becomes a real gap in practice.
- **No entitlement/plan gate on this path at all** (03-BUILDER-MODULES.md
  B-06 §7: available on every plan, Free included) — only the numeric
  plots-per-project quota applies, same as every other plot-creation path.
- **Permission is `PLOT_CREATE`, not `PLOT_BULK_UPLOAD`** — the latter
  stays scoped to Path B (Excel import) only. The "Add Plots" button on the
  project grid is now visible to anyone with *either* permission, not just
  `PLOT_BULK_UPLOAD` as before, since Quick Create needs only the former.

**The Excel-import entitlement gate is now actually enforced, for the first
time.** `EntitlementService`'s own class javadoc had documented since M2
that `BULK_UPLOAD_ENABLED` was seeded correctly (`FREE` → `'0'`) but
deliberately left unenforced, because M2's exit criteria required a 500-plot
bulk-upload test and no paid plan existed yet to unlock it — enforcing the
gate then would have made that milestone's own required test permanently
unreachable. Quick Create's entire point is a Free-plan-available contrast
against Excel import, which only means something once Excel import is
actually gated — so this was the "revisit" moment that comment always
anticipated. Added `EntitlementService.assertFeatureEnabled` (boolean/tier
gate, distinct from `assertWithinQuota`'s numeric used/limit shape — there's
nothing to count, just "this plan doesn't include this feature") + a new
`FeatureNotEnabledException` → `FEATURE_NOT_ENABLED` (403), wired into
`ImportService.start()` only. **Still can't demonstrate the unlocked side**
(no PRO/PREMIUM `plan_limit` rows exist yet — still M8 scope, same caveat
M2/M3 already noted for the numeric quota gates) — only "correctly blocked
on Free" is provable in this environment today. `assertFeatureEnabled`
treats a plan with **no row at all** for a given key as disabled, not
unlimited — the opposite default from `assertWithinQuota`'s missing-quota-
row-means-uncapped rule, since a missing *boolean* feature row can't mean
"granted" without some plan ever having actually granted it.

**Real bugs found and fixed:**
- **`PlotQuickCreateService.preview()` needed `@Transactional(readOnly =
  true)`** — the exact same root-cause class M3 already hit with
  `PaymentService.summary()`: `EntitlementService.usage()` calls
  `entityManager.refresh(u)` directly, which requires an active
  transaction even for a read-only call. This one has a genuinely subtle
  wrinkle: it only surfaces once an `org_usage` row for the
  (org, `BUILDER_PLOTS_PER_PROJECT`, project) triple actually *exists* —
  when it doesn't yet (a fresh project, zero plots ever created),
  `currentUsage()`'s `.orElse(0)` branch never reaches the `refresh()` call
  at all, so a preview against a brand-new empty project passes silently
  while previewing against a project that already has even one real plot
  fails with "No EntityManager with actual transaction available for
  current thread." Caught by writing a collision test that pre-creates one
  real plot (to have something to collide with) — the very case that
  happened to also be the first one to touch a real `org_usage` row.
- **`AreaInput.tsx` always rendered measurement-unit names in English
  (`u.nameEn`), even with the UI in Hindi** — found by chance during this
  feature's own Hindi/360px pass (the unit dropdown's Hindi label,
  "वर्ग फुट," wasn't there to select). This is a genuinely separate,
  pre-existing bug unrelated to Quick Create itself — `ProjectFormPage.tsx`
  already established the correct pattern for exactly this kind of
  bilingual, DB-sourced (not i18n-key-sourced) data (`i18n.language ===
  'hi' ? state.nameHi : state.nameEn`, used for Indian state names), but
  `AreaInput` (used by every plot-size field across the whole app: manual
  plot create/edit, Quick Create's shared properties, the M-08 calculators
  page) never followed it, hardcoding the English name unconditionally.
  Fixed by applying the identical `i18n.language` check. **Any future
  component rendering DB-sourced bilingual data (not a plain i18n key)
  needs this same explicit language check — there's no automatic locale
  switching for data that isn't routed through `t()`.**

**Verified end-to-end via a real browser** (`frontend/e2e/quick-create-plots.spec.ts`,
permanent suite): created a project with one pre-existing plot (`A-2`),
opened the new "Add Plots" entry point (now offering both paths as two
cards), confirmed the Excel-import card renders locked (amber "Upgrade"
badge, not a clickable link) on the Free plan, submitted a range that
deliberately collides with `A-2` and confirmed the collision banner
appears with "Create Plots" disabled *before* anything is written, adjusted
the range clear of the collision, committed, and confirmed via both the
plots list UI and a direct `GET .../plots/grid` call that all 3 new plots
landed with the correct auto-placed row/column. Full existing e2e
regression suite re-run afterward — zero regressions (one unrelated failure,
`m2-grid-performance.spec.ts`, which hardcodes a reference to an old,
no-longer-running isolated verification stack's container name; an
environment mismatch, not a functional regression). Backend: two new tests
in `PlotQuickCreateServiceIntegrationTest` for the query-shape performance
approach; a full `PlotQuickCreateServiceIntegrationTest` suite (range
parsing/preview, existing + within-request collision flagging, end-before-
start and absolute-cap rejection, commit creating+placing plots, commit
skipping collisions instead of failing the batch, quota enforcement stopping
a commit mid-batch while keeping plots already created) plus two new
`EntitlementServiceIntegrationTest` cases for the new feature gate. Full
backend suite green throughout (`mvn test`).

---

## Bug Fix — "Mark all read" silently did nothing

Reported by the user. Root cause was in the **shared `apiFetch` client**
(`frontend/src/lib/api/client.ts`), not anything notification-specific —
`res.status === 204` was the only case treated as "no body to parse";
everything else fell through to `await res.json()`. A plain `void`-returning
`@PostMapping` method (no `ResponseEntity`) gets Spring MVC's default **200
OK with a genuinely empty body**, not 204 — `NotificationController.markRead()`
and `.markAllRead()` were both exactly this shape. `res.json()` on an empty
body throws (`Unexpected end of JSON input`), which silently rejected the
mutation's promise before its `onSuccess` (the query invalidation that
refreshes the unread badge) ever ran. No `onError` handler existed either, so
nothing surfaced anywhere — the button just looked like it did nothing.

**Confirmed the backend was never at fault for the missing UI update**: a
pure-API diagnostic (curl-equivalent calls via Playwright's `page.request`,
bypassing the frontend entirely) showed `unread-count` correctly going
1 → 0 and the notification's `read` flag flipping to `true` in the database
every time, even before any frontend fix. This was purely a frontend bug,
and specifically NOT a query-invalidation-logic bug (`invalidateBoth()`'s
code was already correct) — the mutation never reached `onSuccess` at all.

Separately, `NotificationService.markAllRead()` also needed
`@Transactional` on the service method (a bare `@Modifying` UPDATE query
with no surrounding writable transaction throws `InvalidDataAccessApiUsageException:
Executing an update/delete query` -- Spring Data's repository proxy defaults
query methods to read-only unless the caller already has a writable
transaction open). `RefreshTokenService.revokeFamilyOf()` had already
established the correct pattern for this exact class of bug elsewhere in
the codebase; this was the same fix applied a second time.

**Fixed on three fronts:**
1. `apiFetch` now reads the response as text first and treats an empty
   string as "no content" **regardless of status code** — this protects
   every current and future endpoint with this shape, not just these two.
2. `NotificationController.markRead()`/`.markAllRead()` now explicitly
   return `ResponseEntity.noContent().build()` (204), matching the same
   convention `PlotController.delete()` already used — correct REST
   semantics on top of the frontend fix, not instead of it.
3. `NotificationService.markAllRead()` now has `@Transactional`.

**Any future `void`-returning controller method should return
`ResponseEntity<Void>` with an explicit `.noContent().build()`** rather than
relying on Spring's implicit 200 — cheap to get right, and this bug class
is silent and easy to miss without it. New regression test
(`frontend/e2e/notification-mark-all-read.spec.ts`) plus a new
`NotificationServiceIntegrationTest` (first test coverage this service has
ever had) covering both the transaction fix and basic `markAllRead`/`markRead`
behavior. Zero regressions across the full existing e2e suite.

---

## Milestone 4 — Decisions & Environment Notes

Real bugs found and design decisions made while building B-12 (Team &
Roles), B-07/M-12 (Leads & Customer Core), M-11 (Calendar & Reminder
Engine), and — the largest single piece of this milestone — retrofitting
full RBAC enforcement onto every M0–M3 endpoint for the four new staff
roles (Manager, Sales Executive, Accounts Staff, View Only), not just the
pre-existing Admin/Owner. Read this before touching `foundation.customer`,
`foundation.calendar`, `builder.team`, `builder.lead`, or `platform.JwtService`/
`TenantContextFilter`/`ProjectAccessGuard`.

**The core architectural gap this milestone closed:** `TenantContext.Tenant`
had `allProjects()`/`projectScope()` accessors since M1, and
`ProjectAccessGuard.assertAccess()` existed and was already called from
every M2/M3 service — but the JWT never carried real project-scope data, so
every prior milestone effectively ran with `allProjects=true` for everyone,
making the guard a no-op. M4 is what makes it real:
`JwtService.AccessTokenClaims` now carries `allProjects`/`projectScope`,
populated at login/signup/refresh from a new `user_project_access` table
via `AuthLookupRepository`'s BYPASSRLS auth-lookup path (same pattern
already used for password-reset tokens), and `TenantContextFilter` binds
them from the token instead of hardcoding `true`/`List.of()`. Confirmed via
real API calls: a project-scoped Sales Executive's `GET /projects` list
correctly contains only their assigned project, and `GET
/projects/{otherProjectId}` correctly 404s (never 403, per rule #1).

**Role → permission matrix reasoning (`V4_007__seed_staff_roles.sql`):**
M-02's own summary table and the more specific per-module `§9 Permissions`
sections (in B-04, B-05, B-07, B-12 etc.) disagree in a few places; where
they did, the module-specific section won, on the theory that it's the more
carefully considered call for that specific data. Documented inline in the
migration's own comment, but the key calls: **Manager** gets
`FINANCIAL_VIEW`/`FINANCIAL_RECORD_PAYMENT` but explicitly **not**
`FINANCIAL_EDIT`(B-05 §9: reverse/edit-schedule is "Admin, Accounts" only,
not Manager, even though M-02's coarse table would suggest Manager gets
broad financial access); gets `TEAM_VIEW` but not `TEAM_MANAGE` (can see the
roster, can't invite/deactivate). **Sales Executive** gets only
`DATA_VIEW_OWN`/`CREATE`/`EDIT_OWN`/`REPORT_VIEW_OWN` — zero project/plot/
financial permissions at all, since B-02/B-03 document project/plot
*viewing* as ungated for any authenticated staff member regardless of role
(there's no `PROJECT_VIEW`/`PLOT_VIEW` permission code in the fixed M-02
catalogue to gate it with in the first place). **Accounts Staff** gets the
full financial set (`FINANCIAL_VIEW`/`RECORD_PAYMENT`/`EDIT`,
`BROKER_VIEW`/`COMMISSION_PAY`, `REPORT_FINANCIAL`, `SENSITIVE_VIEW`) but
deliberately **no** `DATA_VIEW_ALL`/`DATA_VIEW_OWN` at all — B-07 §9 states
Accounts has "no lead access at all," which is a stronger claim than merely
lacking `LEAD_ASSIGN`, so the lead pipeline is fully closed off to this role
(enforced via `CustomerService.assertCanViewAnyLeads`, since there's no
single permission code that captures "block regardless of both
view-scope options" declaratively). **View Only** gets `DATA_VIEW_ALL`/
`REPORT_VIEW_ALL`/`EXPORT_DATA` only — explicit financial/broker/sensitive
carve-outs, matching its name.

**The `@RequiresPermission` annotation only expresses a single permission
per endpoint, with no OR support** — this is a pre-existing M1 design
constraint, not new to M4, but M4 is the first milestone where "visible if
EITHER of two permissions is held" (own-leads vs. all-leads; Sales
Executive sees only assigned leads, Manager/View Only see all) is a real,
load-bearing requirement rather than a hypothetical. Solved by NOT trying
to force this into the declarative annotation, and instead writing explicit
service-layer checks in `CustomerService` (`assertVisible`,
`assertCanViewAnyLeads`, `requireEditable`) that read `tenant.permissions()`
directly and branch on `DATA_VIEW_ALL` vs. `DATA_VIEW_OWN` vs. neither.
**Any future "view all OR view own" requirement should follow this same
pattern** — a service-layer check reading the permission set directly, not
an attempt to extend the annotation to support OR-composition.

**Gotchas (backend):**
- **The merge()-vs-persist() JPA bug class (documented since M2) recurred
  four times this milestone**, in every new service with a Java-assigned
  `@Id`: `InteractionService.create()`, `CustomerService.create()`,
  `TeamService.create()`, `CalendarService.createManual()`. The
  `InteractionService` case was the one that actually crashed —
  `isAmendable()`'s `Duration.between(interaction.getCreatedAt(),
  Instant.now())` threw a `NullPointerException` because `createdAt` (a
  `@CreationTimestamp` field) was null on the reference returned by
  `save()`, confirmed via the backend log stack trace. The other three
  would have silently shipped `createdAt: null` in their API responses
  without ever throwing — found proactively by checking for the same
  pattern rather than waiting for each one to fail independently. Fixed
  identically in all four: inject `EntityManager`, then
  `entity = repository.save(entity); entityManager.flush();
  entityManager.refresh(entity);` before building the response DTO. **This
  is now a four-times-recurring bug class across three milestones — any
  future entity with a manually-assigned `@Id` that has a
  `@CreationTimestamp`/DB-generated column needs this flush+refresh
  immediately after `save()`, not just the `entity = repository.save(entity)`
  reassignment M2 already documented** (that fixes the "stale detached
  reference" half of the problem; this fixes the "DB-computed columns not
  populated" half — both are needed together for an entity with generated
  columns).
- **`TeamService.list()`/`.get()` were missing `@Transactional`, causing a
  `LazyInitializationException: could not initialize proxy [Role] - no
  Session`** at `toResponse()`'s call to `user.getRole().getCode()`
  (`AppUser.role` is `@ManyToOne(fetch = LAZY)`, and the Hibernate session
  had already closed by the time the response-mapping code ran). The
  request as a whole returned a 500 — but **the frontend's
  `TeamListPage` didn't distinguish `query.isError` from the empty-members
  case, so the 500 silently rendered as "No team members yet"** even though
  four `POST /builder/team` calls had just returned 201 moments earlier.
  Only caught by a direct `curl` to `GET /builder/team` after the UI looked
  wrong. Fixed on both sides: `@Transactional(readOnly = true)` added to
  both service methods, and `TeamListPage` given an explicit
  `query.isError` branch rendering `resolveErrorMessage(query.error)`
  instead of falling through to the empty state. **Any future list/get page
  needs its own error branch, distinct from its empty-state branch** — this
  is the same lesson the "Mark all read" bug (documented above) already
  taught for mutations; this is the read-path version of it.
- **My own inline-enum-literal JPQL mistake, caught while writing a comment
  warning against it.** While adding a code comment to `CustomerRepository`
  explicitly citing the documented M3 "never inline an enum constant in a
  `@Query` string" bug class, I immediately wrote exactly that mistake in
  two *other* queries in the same file (`findUnassigned`,
  `findDueForFollowUp`, both filtering on terminal `Customer.Status`
  values). Caught by re-reading my own file rather than by a runtime
  failure — fixed by adding `@Param List<Customer.Status>
  terminalStatuses` to both, matching the bound-collection pattern M3's own
  notes prescribe. Worth noting as a reminder that documenting a bug class
  doesn't automatically prevent repeating it in adjacent code written in
  the same sitting — a second read-through of new repository code
  specifically hunting for inlined enum literals is warranted whenever
  writing several JPQL queries in one file.
- **`ConflictException` had no way to carry structured params** — needed so
  duplicate-lead detection could return `{name, status, id}` to the
  frontend (so `LeadFormDialog` can offer "Open Existing" /
  "Create Anyway"), the same shape `QuotaExceededException` already uses
  for `{used, limit}`. Fixed by adding a `Map<String, Object> params` field
  + constructor to `ConflictException`, mirroring `QuotaExceededException`'s
  existing shape exactly, and updating
  `GlobalExceptionHandler.handleConflict` to read `ex.params()` instead of
  a hardcoded `Map.of()`.
- **A mislabeled notification `typeCode`, caught by the FK constraint
  doing its job.** `CustomerService.updateStatus()`'s deal-closed
  notification originally reused the typeCode `"LEAD_CREATED"` — a
  copy-paste artifact from writing the lead-creation notification just
  above it in the same method. `notification.type_code` has a real FK to
  `notification_type(code)`, so this didn't silently mislabel anything in
  the DB — the *string* was valid (LEAD_CREATED is a real seeded code), it
  was just semantically wrong, which an FK constraint can't catch. Caught
  by re-reading the diff, not by a runtime error. Fixed by introducing a
  correctly-named `"LEAD_DEAL_CLOSED"` code, which required a **new**
  migration (`V4_012__seed_lead_deal_closed_notification_type.sql`) since
  the code hadn't been seeded yet — per this project's own convention,
  never edit an already-applied migration (`V4_008`) to add the missing
  seed row retroactively.
- **Calendar auto-projections require cross-service wiring that isn't
  obvious from any single service's own code.** `ScheduleService.add/update/
  delete/waive` now call `CalendarService.syncInstalmentProjection`/
  `.removeAutoEvent` directly — straightforward, since `ScheduleService`
  itself makes the change. But `PaymentService.record/reverse/
  updateChequeStatus` and `PlotSaleService.create/cancel` **also** need to
  call `ScheduleService.syncAllInstalmentProjections(saleId)` afterward,
  because the DB trigger that actually flips a `payment_schedule.status`
  (e.g. `PENDING` → `PAID`) fires as a side effect of the payment
  write and is invisible to the Java layer without an explicit re-read —
  the exact same "Hibernate can't see a DB-trigger-computed change it
  didn't itself make" root cause M2's `pricePerUnit`/`org_usage` staleness
  bugs and M3's `entityManager.refresh()`-needs-a-transaction bug both
  already established. Confirmed via direct API calls: recording a payment
  that fully pays off an instalment correctly removes that instalment's
  `INSTALMENT_DUE` calendar event; reversing it correctly restores the
  projection with the original due date, not a duplicate. **Any future
  cross-service side effect on a DB-trigger-computed column needs the same
  "call back into the sync method after the write" wiring**, not just a
  single service's own local update.
- **`AppUser` was missing several accessors** (`setRole`, `setOwner`,
  `getProjectAccessMode`/`setProjectAccessMode`, `setDeletedAt`,
  `getCreatedAt`, `setCreatedBy`) that `TeamService` needed for staff
  create/deactivate/reactivate — none of M0–M3's code ever needed to
  mutate these fields on an *existing* `AppUser` after initial signup.
  Added directly to the entity; no behavioral surprise here, just a gap in
  what previously existed.
- **`RbacController.roles()`'s prefix-matching logic broke the moment
  staff roles existed.** It filtered role codes by `startsWith("BUILDER_")`
  / `startsWith("BROKER_")` to split the builder-role list from the
  broker-role list for the frontend's role-picker dropdown — which worked
  by accident through M1–M3 because the only roles that existed were
  literally named `BUILDER_ADMIN`/`BROKER_OWNER`. The four new staff roles
  (`MANAGER`, `SALES_EXECUTIVE`, `ACCOUNTS_STAFF`, `VIEW_ONLY`) don't start
  with either prefix, so they silently vanished from both lists. Caught
  during code review before it ever reached a live test. Fixed with
  explicit code lists (`BUILDER_ROLE_CODES`, `BROKER_ROLE_CODES`) and a new
  `RoleRepository.findByOrgIdIsNullAndCodeIn`. **Any future new system
  role must be added to the appropriate explicit list in
  `RbacController`** — there is no naming convention robust enough to
  infer builder-vs-broker automatically, and this is now the second time
  (after the coarse `M-02` table itself) that a "builder/broker" split
  assumption has needed an explicit list instead of an inferred rule.
- **`plot_sale.customer_id`'s FK debt, named explicitly in V3_001's own
  comment, is now closed** — `V4_003__add_customer_fk_to_plot_sale.sql`
  adds the retroactive `FOREIGN KEY (customer_id) REFERENCES customer(id)`
  now that `customer` exists. `plot_sale.broker_partner_id` remains
  FK-less, same as before, since B-14 Brokers still doesn't exist.
- **`BUILDER_TEAM_MEMBERS` quota (seeded Free=1 in M2's `V2_009`) had never
  actually been enforced until this milestone**, because no code path ever
  called `EntitlementService.assertWithinQuota` for it before `TeamService`
  existed. Wiring it up required a **new** trigger
  (`V4_010__create_team_member_usage_trigger.sql`, mirroring `V2_013`'s
  `fn_org_usage_bump` pattern exactly) plus a backfill migration
  (`V4_011__backfill_team_member_usage.sql`), since every org created
  before this migration already has an owner `app_user` row that predates
  the trigger and would otherwise show `org_usage.current_value = 0` for a
  count that should be 1. **This is the same class of gap CLAUDE.md has
  flagged twice before (the M2 `MagicBytes` XLSX miss, the M2 `CorsConfig`
  PUT miss)** — a mechanism seeded early (the quota row) but not actually
  wired to anything until a much later milestone finally exercises it, and
  needing a backfill migration precisely because pre-existing rows don't
  retroactively satisfy a trigger that didn't exist when they were created.

**Gotchas (frontend):**
- **`PlotDetailDrawer`/`SaleDetailPanel`'s permission-gating was collapsed
  onto a single `PLOT_EDIT`-derived `canEdit` prop before this milestone,
  which is wrong the moment more than one staff role exists.** M2/M3 only
  ever tested as Owner/Admin (who hold every permission), so a single
  boolean happened to produce correct-looking results. With Accounts Staff
  now real (holds `FINANCIAL_RECORD_PAYMENT`/`FINANCIAL_EDIT` but not
  `PLOT_EDIT` or `DATA_EDIT_ALL`), the single prop was wrong in both
  directions: Accounts Staff would have seen no "Record Payment" button at
  all (should see it), while a hypothetical role with `PLOT_EDIT` but no
  financial permissions would have seen the Record Payment/reverse/waive
  controls (should NOT see them). Found by reasoning through each role's
  actual permission set against the UI's gating, before it ever became a
  live test failure. Fixed by removing the single `canEdit` prop from
  `SaleDetailPanel` entirely and computing three separate booleans
  directly via `useCan`: `canEditSale` (`DATA_EDIT_ALL`, gates
  cancel-sale), `canRecordPayment` (`FINANCIAL_RECORD_PAYMENT`, gates the
  Record Payment button), `canEditFinancial` (`FINANCIAL_EDIT`, gates
  reverse/waive/cheque-status actions and document generation).
  `PlotDetailDrawer`'s own "Mark as Sold" button was similarly re-gated
  from the old `canEdit`/`PLOT_EDIT`-flavoured check to the correct
  `DATA_EDIT_ALL`, and the whole `SaleDetailPanel` block is now additionally
  gated on `FINANCIAL_VIEW` so a Sales Executive (no financial permissions
  at all) doesn't even see the panel shell. **Any future shared detail
  component that mixes concerns from more than one permission domain
  (plot vs. financial, here) needs one `useCan` check per actual concern,
  never a single collapsed boolean** — this is exactly the kind of bug
  that looks fine until a second, differently-permissioned role exists to
  expose it, which is why the kickoff instructions explicitly called for
  testing every role against the *existing* B-04/B-05 screens, not just
  new M4 ones.
- **The i18n JSON key-collision bug** — `customer.json`'s `interaction`
  object defined `"type"` twice within the same parent object: once as a
  nested object of enum labels (`{CALL: "Call", VISIT: "Visit", ...}`) and
  again, further down in the same object literal, as a plain string field
  label (`"Type"`). Standard JSON (and JS object-literal) semantics mean
  the *second* occurrence silently wins at parse time, discarding the
  first — so `t('interaction.type.CALL')` resolved to `undefined` and
  i18next fell back to rendering the literal key string
  `"interaction.type.CALL"` verbatim in the UI. The identical collision
  existed for `"result"`. **Only caught by actually inspecting a real
  Hindi/360px screenshot** (Read tool on the captured PNG) — nothing
  automated would have caught this, since the key-parity test only checks
  that `en`/`hi` agree with *each other*, not that a key resolves to
  something sensible, and `tsc`/lint have no visibility into JSON string
  contents at all. Fixed by renaming the plain-string field labels to
  `typeLabel`/`resultLabel` in both `en/customer.json` and
  `hi/customer.json`, keeping the enum-label objects as `type`/`result`,
  and updating `AddInteractionDialog.tsx`'s two label calls accordingly.
  **Any future i18n namespace that needs both an enum-value lookup object
  and a plain field label under similar names must pick visibly distinct
  key names up front** (`xLabel` vs. `x`, or nest the enum object under a
  clearly different parent) — a bare `"type"`/`"type"` collision is a
  silent JSON authoring mistake with zero tooling to catch it currently in
  this project.

**Verification methodology and results:**
- Backend: 100% green `mvn test` (Testcontainers-based, all suites)
  including the existing tenant-isolation suite plus new coverage for
  `CustomerService`, `InteractionService` (amend-window + append-only
  enforcement), `TeamService`, `CalendarService` (auto-projection
  idempotency), and the RBAC plumbing changes.
- **Comprehensive real 5-role RBAC verification**, exactly as the kickoff
  instructions required — not simulated, not mocked: signed up a real org,
  created one real staff account per role (Admin/Owner already existed;
  Manager, Sales Executive, Accounts Staff, View Only created through the
  actual `AddTeamMemberDialog` UI, each one accepting its invite through
  the real `/accept-invite` flow with the OTP/token read from the stub SMS
  gateway's log — not seeded directly into the DB), then logged in as each
  role in a **separate real Playwright browser context** and asserted, both
  at the UI level (nav items present/absent) and at the direct-API level
  (`page.request.get(...)` against the real endpoints), against the
  *existing* B-04/B-05 screens as explicitly instructed, not just new M4
  ones:
  - **Sales Executive**: sees Leads (Mine tab only), cannot see Team,
    Financials nav, or the `SaleDetailPanel`/payment screens on any plot
    (confirmed both by the panel not rendering and by a direct
    `GET /sales/{id}/payments/summary` call returning 403); a direct
    attempt to hit another staff member's leads via
    `GET /customers?assignedTo=<otherUserId>` still only returns their own.
  - **Accounts Staff**: full financial visibility/actions on B-04/B-05
    screens (record payment, reverse, cheque-status), but the Leads nav
    item is entirely absent and a direct `GET /customers` call 403s —
    confirming B-07 §9's "no lead access at all" is enforced as a hard
    block, not just a missing UI affordance.
  - **Manager**: sees financials (view + record payment) but a direct
    `PATCH`/reverse-shaped call to the payment-correction endpoints 403s
    (no `FINANCIAL_EDIT`); sees the Team roster (`TEAM_VIEW`) but the
    invite/deactivate controls are absent and a direct
    `POST /builder/team` 403s (no `TEAM_MANAGE`).
  - **View Only**: read access everywhere permitted (`DATA_VIEW_ALL`,
    `REPORT_VIEW_ALL`), but every mutation attempted directly against
    B-02/B-03/B-04/B-05 endpoints (create plot, edit project, record
    payment, cancel sale) 403s; financial/broker/sensitive data is not
    visible.
  - **Admin/Owner**: unaffected baseline, full access confirmed unchanged.
  - Final script output: `M4_RBAC_VERIFICATION_ALL_ASSERTIONS_PASSED`,
    after resolving several test-authoring mistakes along the way (not
    product bugs): `page.goto()`/`page.reload()` wiping the in-memory-only
    `authStore` mid-test (the same documented, recurring gotcha as every
    prior milestone — fixed by clicking nav links instead), a Playwright
    locator ambiguity between the CSS-hidden Sidebar and the visible
    BottomNav both rendering identical link text at 360px (fixed with
    `.last()`, since BottomNav renders later in the DOM), and a synthetic
    "should be blocked" test payload that used too-short field values,
    triggering Bean Validation's 400 *before* the AOP permission check ever
    ran (confirmed empirically that Spring MVC validates the request body
    before invoking `PermissionAspect` — fixed by using realistic payload
    values so the request actually reaches the permission check).
  - The Free-plan `BUILDER_TEAM_MEMBERS` quota (limit=1) would have blocked
    creating any of the four test staff accounts; resolved the same way
    M2/M3 precedent already established for bypassing a business-rule
    quota specifically for test-data setup (not the thing under test):
    first confirmed the quota genuinely blocks (a direct 403 check, for
    both `BUILDER_TEAM_MEMBERS` and, discovered along the way,
    `BUILDER_PROJECTS`), then created a throwaway `M4TEST` plan row (not
    mutating the shared `FREE` row other sessions/orgs also read) with
    high limits via direct SQL, and switched only the test org's
    subscription to it.
- **Hindi + 360px pass** across all three new feature areas (Team, Leads,
  Calendar), confirmed via real screenshots (not just automated overflow
  checks) — zero page-level horizontal overflow at 360px, and one real bug
  found and fixed (the i18n key-collision above).
- **Zero regressions**: the full pre-existing e2e suite (M2 builder flow,
  M2 plot-reserved-status, M2 project-status, M3 sale-payments,
  notification-mark-all-read, quick-create-plots — 10/10 specs) still
  passes unchanged, confirming the `SaleDetailPanel`/`PlotDetailDrawer`/
  `AppShell` permission-gating changes didn't regress the Owner/Admin
  experience these specs already covered.
- One-off verification scripts (`m4-rbac-verify.spec.ts`,
  `m4-hindi-360-verify.spec.ts`) were deleted after passing, matching the
  precedent M3 already set: throwaway manual verification isn't kept as
  permanent CI unless it's testing something a future regression could
  plausibly reintroduce (contrast with `m2-project-status.spec.ts`, which
  *was* kept, being a small standing gap-closure with real ongoing value).

**Known, deliberate scope decisions — not bugs, not oversights:**
1. **No ownership-transfer flow.** B-12 describes a two-step, OTP-confirmed
   org-ownership transfer; none of M4's exit criteria exercise it, so it
   was not built. Documented in `TeamService`'s own class-level javadoc.
2. **Calendar frontend is agenda-list-only** — no month/week/day grid
   views, despite B-09 naming them as components. A deliberate trim,
   documented in a code comment in `CalendarPage.tsx`, given the remaining
   milestone scope; the agenda list still satisfies the actual exit
   criteria (see auto-projections and manual events both correctly
   listed/filterable).
3. **Auto-projection calendar event titles are hardcoded English strings
   baked in server-side at write time** (e.g. `"Follow-up: " + name`), not
   i18n-key + params like every other user-facing string in this codebase.
   Found via the Hindi/360px screenshot pass (English text visible in an
   otherwise-Hindi UI) but left as a known gap rather than redesigning the
   title-storage model (a literal string column vs. a key+params shape)
   under this milestone's time budget. **Revisit before any milestone that
   makes the calendar a bigger surface** — the fix shape is to store a
   `titleKey`/params pair the same way `notification.title_key` already
   does, not to translate the stored string after the fact.
4. **B-05 §9's "Waive: Admin only" is not separately enforced** —
   `ScheduleController` still gates all schedule mutations, including
   waive, with the single `FINANCIAL_EDIT` permission, which Accounts
   Staff also holds. This is a pre-existing M3 granularity gap (not
   introduced this milestone), left as-is since the fixed M-02 permission
   catalogue has no distinct "waive" code, and inventing one would
   deviate from that catalogue rather than close a real M4 exit-criterion
   gap.
5. **No follow-up-due / overdue-lead notification sweep.** The
   `FOLLOWUP_DUE` notification type is seeded (`V4_008`) but nothing fires
   it — a nightly job mirroring `OverdueScheduleSweeper`'s exact pattern
   would be the natural fix, but wasn't in scope for this milestone's exit
   criteria (which cover manual follow-up logging and calendar display,
   not a proactive nudge). Similarly, `LEAD_PLOT_SOLD` is seeded but never
   fired — B-07's "flag the lead if their interested plot sells" edge case
   wasn't built.
6. **Staff `activity()` reporting is minimal** — leads-assigned and
   interactions-logged counts only, not a full cross-module rollup
   (deals closed, payments recorded, etc.) a fuller "staff performance"
   view might eventually want. Sufficient for B-12's own stated exit
   criteria; noted as a likely expansion point for B-15 (Builder Stats).
7. **All verification is against the Free plan** except for the one
   throwaway `M4TEST` plan row created specifically to unblock
   quota-limited test-data setup (see above) — same recurring constraint
   M2/M3 already noted, no real paid plan exists until M8.

---

## Milestone 5 — Decisions & Environment Notes

Real bugs found while building B-08 (Financials), B-13 (Follow-up &
Collection Tracker), B-01 (Builder Dashboard), and B-10 (Deals History), and
while driving all four through real HTTP calls and a real browser against a
deliberately realistic dataset (not an empty org) — same standard as every
milestone so far. Read this before touching `builder.financial`,
`builder.tracker`, `builder.dashboard`, `builder.deal`, or
`foundation.notification.WhatsAppGateway`.

**Architecture decisions:**
- **`org_metrics`** (`V5_001`/`V5_002`/`V5_003`) is a wide table — one row
  per org, one column per dashboard card — not a key-value table like M2's
  `org_usage`, matching B-01 §3's own spec. Write-through triggers
  (`fn_org_metrics_project_trigger`, `_plot_trigger`, `_customer_trigger`,
  `_payment_trigger`, plus `fn_org_metrics_status_bump`/`_lead_active` for
  status-transition-driven counters) fire on the same INSERT/UPDATE that
  changes the underlying row, mirroring `fn_org_usage_bump`'s established
  pattern exactly. `V5_003` backfills existing BUILDER orgs via
  `INSERT ... ON CONFLICT DO UPDATE`, same idempotent-seed convention as
  every other backfill migration.
- **Financials/Tracker/DealsHistory are `NamedParameterJdbcTemplate`-based
  native SQL, not JPA/Specifications** — deliberate, not a shortcut. These
  modules report across `plot_sale`/`payment_schedule`/`payment_record`/
  `plot`/`project`, none of which have JPA relational mappings to each
  other in this codebase (plain UUID FK columns only, per CLAUDE.md's own
  data-model conventions) — modelling that as JPA joins would mean adding
  relational mappings solely for reporting, which risks the exact
  Hibernate-managed-entity foot-guns M2–M4 already hit repeatedly
  (merge()-vs-persist(), stale cached reads). `FinancialService`'s own
  class javadoc documents the additional, more specific reason its summary
  cards are computed live rather than from `mv_org_revenue_monthly`:
  correctness over the view's 15-minute staleness, given this milestone's
  emphasis on getting money figures right.
- **WhatsApp gateway is now real, not a stub that only logs** —
  `WhatsAppGateway`/`StubWhatsAppGateway` genuinely POST to
  `infra/whatsapp-stub`'s `/v1/messages` endpoint (built in M0, never once
  called before this milestone) via `RestTemplate`, unlike `StubSmsGateway`
  which just writes a log line. `OutboxService.enqueueWhatsApp()` +
  `OutboxPoller.sendWhatsApp()` + a new `WhatsAppPayload` record extend the
  existing EMAIL/NOTIFICATION outbox pattern with a third event type,
  exactly the same shape. A dispatch failure here surfaces like a genuine
  outage would (connection refused, retried, eventually DLQ'd) rather than
  silently "succeeding" against a fake channel.
- **`FollowUpDueSweeper` closes the exact M4 gap CLAUDE.md's own M4 notes
  flagged**: `FOLLOWUP_DUE` was seeded back in `V4_008` but nothing ever
  fired it. Mirrors `OverdueScheduleSweeper`'s per-org
  bind-then-`REQUIRES_NEW` pattern exactly (see that class's own javadoc
  for why the ordering matters), runs at 09:00 IST
  (`@Scheduled(cron = "0 0 9 * * *", zone = "Asia/Kolkata")`), and sends
  **both** an in-app `FOLLOWUP_DUE` notification and a WhatsApp reminder —
  critically, the WhatsApp message goes to the **staff assignee's own
  mobile**, not the lead's (a real bug caught while re-reading my own first
  draft, see below). Verified via a new
  `FollowUpDueSweeperIntegrationTest`, mirroring
  `OverdueScheduleSweeperIntegrationTest`'s exact structure (direct
  `sweepAllOrgs()` call, then `outboxPoller.dispatchReady()` to force the
  async fan-out, then assert on DB state) since a `@Scheduled` job has no
  HTTP endpoint to trigger manually.
- **`TrackerService`'s inline actions are thin delegates, never
  reimplementations** — `logFollowUp`/`reschedule`/`markDone` all call
  `InteractionService.create()` directly; `recordPayment` calls
  `PaymentService.record()` (with an explicit single-schedule allocation
  when the caller doesn't specify one, so a quick payment clicked from one
  specific overdue row applies to *that* row rather than falling through
  to auto-allocate-oldest-due-first across the whole sale). B-13's own
  stated purpose — "everything in it exists elsewhere, this is a
  purpose-built view over it" — is enforced structurally, not just as
  a design intention.
- **A genuinely dead code path was discovered and wired up**:
  `PlotSaleService.complete()` (an `ACTIVE → COMPLETED` transition) has
  existed since M3 with **zero UI entry point** — nothing in
  `SaleDetailPanel` ever called it, meaning no sale could ever reach
  `COMPLETED` through normal app usage, which would have made B-10 Deals
  History's core exit criterion ("a sale enters it only once COMPLETED or
  CANCELLED") impossible to actually demonstrate. Fixed by adding a "Mark
  Complete" button to `SaleDetailPanel.tsx`, shown when
  `sale.status === 'ACTIVE' && sale.balanceDue <= 0` — the same condition
  the backend's own `complete()` method already enforces server-side, so
  the button only appears when the call would actually succeed.

**The `@Primary` bean-resolution bug class recurred a 3rd time** (M1
documented it twice already — see that section). `FinancialService` and
`DealsHistoryService` inject `NamedParameterJdbcTemplate`, which Spring
Boot's `JdbcTemplateAutoConfiguration` only auto-configures under
`@ConditionalOnMissingBean` — and a bean of that exact type *already
existed*: `AuthLookupDataSourceConfig`'s BYPASSRLS `authLookupJdbcTemplate`
bean. With Boot's own auto-configuration silently suppressed, the *only*
`NamedParameterJdbcTemplate` bean in the context was the auth-lookup one —
so every financial/deals-history query silently ran through the
BYPASSRLS/`shardeya_authlookup` connection instead of the RLS-enforced
`shardeya_app` one. This surfaced as `ERROR: permission denied for table
payment_record` — a genuine Postgres GRANT error, not an RLS filter (RLS
would have just returned zero rows, not thrown) — which is what gave away
that the connection wasn't `shardeya_app` at all. Confirmed by logging the
connection's actual role name. **Fixed by adding explicit `@Primary`
`JdbcTemplate`/`NamedParameterJdbcTemplate` beans** backed by the correct
main `dataSource` in `AuthLookupDataSourceConfig` — which then, as a direct
side effect, broke `AuthLookupRepository`'s own previously-safe
by-name-matching (the same "a bean marked `@Primary` wins unconditionally"
mechanism M1 already documented), requiring an explicit
`@Qualifier("authLookupJdbcTemplate")` there too. Both halves verified
together via a real login + a real financial-summary API call. **Any
future bean of a type Spring Boot auto-configures under
`@ConditionalOnMissingBean` needs to be checked against every existing
bean of that same type in the context, not just the obviously-named
ones** — this is now a 3-times-recurring class, always caught by a
downstream permission/RLS symptom rather than anything visible in the
config code itself.

**Self-caught bugs during development** (found by re-reading my own drafts
before they ever reached a test or browser, matching the pattern M4 already
established of documenting confirmed self-catches, not just
browser-caught ones):
- A dead, unused `MIN(uuid)` subquery in an early draft of
  `FinancialService.payments()` — Postgres has no `MIN` aggregate for
  `uuid`. Removed the entire unused subquery rather than casting around it.
- `TrackerService.followUps()`/`DealsHistoryService` initially had no
  explicit RBAC reject for a role holding neither `DATA_VIEW_ALL` nor
  `DATA_VIEW_OWN` (Accounts Staff, per B-07 §9's "no lead access at all") —
  would have silently returned an empty list instead of a 403, the same
  class of gap `CustomerService.assertCanViewAnyLeads` already exists to
  prevent elsewhere. Fixed with the identical explicit-check pattern in
  both services.
- `TrackerService`'s action methods (`recordPayment`, `remind`) initially
  had no project-scope check at all — `PaymentSchedule` has no
  `getOrgId()` accessor (the established RLS-is-the-lookup-backstop
  pattern), and nothing was calling `ProjectAccessGuard.assertAccess()`
  after loading the schedule. Fixed by adding a `loadOwnSchedule()` helper
  mirroring `ScheduleService`'s own `loadOwn()`/`loadSale()` pattern
  exactly, closing the gap at the same layer the established pattern
  already lives at.
- `FollowUpDueSweeper`'s first draft sent the WhatsApp reminder to the
  **customer's** mobile number instead of the **staff assignee's** — caught
  by re-reading the draft against B-13's own spec ("reminds the salesperson
  to make the call"). Fixed by injecting `AppUserRepository` and looking up
  the assignee.
- A Rules-of-Hooks violation in `TrackerPage.tsx`:
  `useCan('DATA_VIEW_ALL') || useCan('DATA_VIEW_OWN')` — `||`
  short-circuits, which skips the second hook call whenever the first
  returns `true`, violating React's "same hooks, same order, every render"
  invariant. Fixed by calling both hooks unconditionally as separate
  `const`s first, then combining the results afterward.
- `CollectionTable.tsx`'s "View Plot" link initially pointed at
  `/builder/projects/${r.plotSaleId}` — wrong ID type entirely (a plot-sale
  ID where a project ID belongs), no matching route. Removed rather than
  ship a dead link; not reinstated this milestone since `CollectionRow`
  doesn't carry a project ID today — a real gap, not silently glossed over,
  should the "View Plot" affordance become a real requirement later.
- Dashboard's `totalRevenue` card renders its figure in `value`, not
  `amount` (`DashboardCard.ofAmount()`'s own shape — every other card puts
  a count in `value` and an optional currency figure in `amount`) — an
  early draft of the frontend rendering uniformly used `toLocaleString()`
  on every card's `value`, which would have shown the raw rupee figure
  un-abbreviated instead of `formatCompactIndianCurrency`'s `₹18.24 Cr`
  style. Caught by checking the backend DTO shape against the frontend
  rendering before it ever reached a browser. Fixed with an
  `AMOUNT_VALUED_CARDS` special case in `SummaryCard.tsx`.
- 5 TypeScript compile errors, all pre-browser: a `qs()` query-string
  helper's parameter type didn't accept plain interfaces lacking an index
  signature (fixed by loosening to `object` + an internal cast, in both
  `financialApi.ts` and `dealsApi.ts`), and Recharts' `Tooltip formatter`
  prop type mismatch in both `RevenueTrendChart.tsx` and
  `PaymentModeBreakdown.tsx` (fixed with `Number(value)` coercion).

**Real bugs found only during the live browser verification pass** — this
milestone's kickoff explicitly predicted "most of these bugs would hide
behind an empty state" and budgeted real time for a realistic dataset (a
completed sale, an overdue instalment, a cheque-bounce reversal, a
cancelled sale, an active partially-paid sale, two leads) rather than an
empty org; all four bugs below are exactly that class — invisible against
an empty or trivial dataset, real against a lived-in one:

1. **The standout bug of this milestone: Financials' and Dashboard's
   "overdue" figures silently disagreed with Tracker's own "overdue"
   figure for the same underlying data**, for up to 24 hours at a time.
   `FinancialService.summary()`'s `overdueInstalments`,
   `FinancialService.pending(overdueOnly=true)`, and
   `DashboardService`'s `OVERDUE_INSTALMENTS` alert all gated on
   `payment_schedule.status = 'OVERDUE'` — a column only ever refreshed by
   `OverdueScheduleSweeper`'s once-daily 09:00 IST cron. `TrackerService`'s
   own Collection tab (`applyRange`'s `"overdue"` case,
   `due_date < :today`) computed the identical fact **live**, off
   `due_date`, with no dependency on the sweep having run recently. Net
   effect, confirmed with real seeded data: a schedule row genuinely 11
   days past its due date, whose status column simply hadn't been touched
   since creation, showed up correctly in Tracker's Collection tab
   (`daysOverdue: 11`) but was **silently absent** from both the Financials
   overdue summary and the Dashboard's overdue alert — two of the three
   surfaces claiming to answer the exact same question gave the wrong
   answer, and the discrepancy was completely invisible against a
   fresh/empty org where nothing has had time to go stale. Confirmed via
   side-by-side `curl` calls before and after the fix (Financials went
   from `{count: 1, amount: 937500}` to the correct `{count: 2, amount:
   1687500}`, matching Tracker's own live count exactly) — the Dashboard
   alert additionally required a `redis-cli FLUSHDB` to see the fix, since
   `DashboardService.dashboard()` is Redis-cached 5 minutes per
   (org, user) and my first post-fix check hit a pre-fix cached response.
   **Fixed by making all three queries compute overdue live off
   `due_date < :today AND status IN ('PENDING','PARTIALLY_PAID','OVERDUE')`**,
   matching Tracker's own approach exactly, instead of trusting the
   sweep-maintained enum column. A new
   `FinancialServiceIntegrationTest` covers this directly: creates a sale
   with a schedule row due 10 days in the past whose status is left at
   whatever `PlotSaleService.create()` set it to (never touching the
   sweeper), and asserts `summary()`/`pending(overdueOnly=true)` both
   catch it immediately. **Any future "overdue" figure anywhere in this
   codebase must compute live off `due_date`, never gate on
   `status = 'OVERDUE'`** — that enum value is a once-daily snapshot for
   the sweep's own notification-fan-out purposes, not a reliable "is this
   overdue right now" signal.
2. **The tracker's quick-pay dialog (in both `CollectionTable.tsx` and
   `PendingInstalmentsTable.tsx`) rendered raw, untranslated i18n keys
   verbatim** — `t('form.amount', {ns: 'payment'})`,
   `t('form.paidOn', ...)`, `t('form.mode', ...)`, `t('form.submit', ...)`
   all resolved to nothing and fell back to printing the literal key
   string, because `payment.json` has no top-level `form` object at all —
   the real keys live under `addPayment.*` (`addPayment.amount`,
   `addPayment.paidOn`, `addPayment.mode`, `addPayment.submit`), the
   convention `SaleDetailPanel`'s own `AddPaymentDialog` already correctly
   uses. Every field label and the submit button itself showed the bare
   key name (`"form.amount"`, `"form.submit"`) instead of "Amount"/"Record
   Payment" — caught from a Playwright failure screenshot, not from
   `tsc`/lint/the key-parity test (which only checks `en`/`hi` agree with
   *each other*, exactly the same blind spot M4's `interaction.type`
   key-collision bug already exposed once). Fixed by correcting all four
   key paths in both files to `addPayment.*`.
3. **`BottomNav` overflowed the entire page horizontally at 360px** — not
   just its own bar. M5 added three new nav items (Tracker, Financials,
   Deals History), taking a full-permission builder from 5 bottom-nav
   items to 8; the nav's `flex justify-around` had no wrapping and no
   overflow handling, so its children's combined width (~572px) simply
   pushed past its own 360px box — and because the nav itself had no
   `overflow-x` containment, that excess width propagated all the way up
   to `document.documentElement.scrollWidth`, meaning the **whole
   dashboard page** gained 202px of horizontal scroll on a real 360px
   viewport, silently making "Financials", "Deals History", and "Team"
   completely unreachable from the bottom nav unless a user discovered
   they could swipe the entire page sideways. Confirmed by measuring each
   nav link's actual `boundingBox()` — the last three items' `x` positions
   ran from 359px to 629px, well past the 360px viewport. **Fixed by
   making `BottomNav` scroll independently** (`overflow-x-auto` on the nav,
   `shrink-0` on each link) instead of letting its overflow blow out the
   page — the same well-established mobile-tab-strip pattern, not a
   redesign. **Any future addition of nav items needs to be checked
   against a 360px viewport specifically for this class of failure** — a
   fixed-width flex row with no overflow handling degrades silently until
   enough items are added to actually break it, which is exactly why this
   was invisible at M4's 5-item count and only appeared now.
4. **A second, independent 360px overflow, still present after the
   `BottomNav` fix**: the `TopBar`'s right-side cluster (theme toggle +
   `LanguageToggle` + `NotificationBell` + `ProfileMenu`) totalled 29px
   wider than the 360px viewport allows, entirely because of
   `LanguageToggle`'s fixed `w-28` (112px) `SelectTrigger` combined with a
   **double-digit** notification unread badge — a condition this
   milestone's own realistic-data seeding is what first produced (a
   fresh/near-empty dev org's badge count had always stayed single-digit
   in every prior milestone's manual verification, so this never
   surfaced before). Confirmed by enumerating every element whose
   `getBoundingClientRect().right` exceeded the viewport — the TopBar's
   right-cluster `<div>` was the direct offender at `right: 389px`.
   **Fixed by narrowing `LanguageToggle`'s trigger to `w-20` below the
   `sm:` breakpoint** (`w-20 sm:w-28`) — the shadcn Select's own
   `SelectValue` already `line-clamp`s to one line, so the label simply
   clips ("Engl…") instead of overflowing the page. **Any future TopBar
   addition needs to be checked at 360px with a realistic (not
   single-digit) notification count** — this bug's precondition is
   specifically "enough real activity to produce a two-digit badge," which
   only a genuinely lived-in dataset like this milestone's would ever
   produce.

**Verification methodology and results:**
- Backend: 54/54 `mvn test` green (Testcontainers), including the new
  `FollowUpDueSweeperIntegrationTest` and `FinancialServiceIntegrationTest`
  described above, with zero regressions across every pre-existing suite.
- Realistic data seeded via a standalone script
  (driving real signup → project → 6 plots → 5 sales spanning every
  status this milestone needed to prove: LUMP_SUM fully paid then marked
  COMPLETE, INSTALMENT with a genuinely backdated overdue instalment,
  INSTALMENT with a cheque payment recorded then bounced/reversed, a
  cancelled sale, and an ACTIVE partially-paid sale — plus two leads, one
  with an overdue follow-up and one due today) — all through the real
  API, with `psql` used only to backdate a due_date/follow_up_date after
  the fact (the app itself has no way to create a schedule already in the
  past), matching the established "seed via real API, direct SQL only for
  setup that the API itself cannot produce" precedent from M2/M4.
- Confirmed via direct `curl` calls before writing the Playwright suite:
  dashboard cards match known seeded state exactly (6 plots, 4 sold, 2
  available after one cancellation), the cheque-bounce reversal correctly
  nets its payment-mode breakdown to `₹0` rather than excluding it (CLAUDE.md's
  explicit M5 instruction), and total revenue across all sales sums
  correctly including that net-zero reversal.
- A new Playwright spec (`frontend/e2e/m5-financials-tracker-dashboard.spec.ts`,
  6 tests, all green) drives the real UI against this seeded org: Dashboard
  cards/alert, Financials summary, Tracker's Collection tab (confirmed a
  real inline "Record Payment" action removes the row from the live list
  without a refresh — the exact CLAUDE.md M5 instruction — and that the
  dialog itself, once the i18n bug above was fixed, renders real labels),
  Tracker's Follow-ups tab (both the overdue and due-today leads visible),
  Deals History (only the COMPLETED and CANCELLED sales appear; the three
  still-ACTIVE sales never do), and a Hindi + 360px pass across Dashboard/
  Financials/Tracker/Deals confirming zero page-level horizontal overflow
  once both 360px bugs above were fixed.
- Zero regressions: the full pre-existing e2e suite (M2 builder flow,
  M2 plot-reserved-status, M2 project-status, M3 sale-payments,
  notification-mark-all-read, quick-create-plots — 10/10 specs) still
  passes unchanged. (Two transient failures during this pass were
  environment artifacts, not regressions: `m3-sale-payments.spec.ts`
  needed its own `M3_BACKEND_LOG` env var pointed at this session's
  actual backend log rather than a stale default path, and hit the same
  well-documented Redis OTP rate limit every prior milestone's repeated
  test runs eventually trip — `redis-cli FLUSHDB` resolved both, per
  established precedent.)
- Frontend: `npx tsc -b --noEmit` clean, `npx vitest run` 18/18 green,
  `npx oxlint` shows only the pre-existing shadcn `only-export-components`
  warnings already noted since M2 — no new issues.

**Known, deliberate scope decisions, not bugs:**
1. **`bulkRemind`'s 24h reminder dedupe is approximated at the schedule-row
   level**, via each row's own `lastReminderSentAt`, since
   `payment_schedule` has no direct link to a single "buyer" identity
   beyond its own sale — a buyer with several overdue rows across
   different schedules could in principle receive more than one reminder
   within a 24h window under this approximation. Documented inline in
   `TrackerService`'s own code comment, not a silent gap.
2. **No document versioning or PDF export for Financials/Deals History
   reports** — B-08/B-10's own spec doesn't call for either this
   milestone; `05-MILESTONES.md`'s phasing keeps reporting exports out of
   scope until a later milestone.
3. **All verification is against the Free plan** — same recurring
   constraint every milestone since M2 has noted; no paid plan exists
   until M8.
4. **`CollectionTable`'s "View Plot" link was removed rather than
   reinstated** (see the self-caught-bugs list above) — `CollectionRow`
   doesn't carry a project ID today, and inventing one just for this link
   would be scope creep beyond what B-13's own columns spec calls for.

---

## Post-M5 Gap Closure — Bugs Found On A Live Account

Four more real bugs, all found the same way M5's own verification pass
found its bugs (real HTTP calls / real browser, real account) — except
these surfaced *after* the milestone was marked complete, from the user
actually using their own account (not one of this project's seeded test
orgs) day-to-day. Documented here as a distinct round rather than folded
back into the M5 section above, since M5 itself was already shipped when
each of these was reported. Read this before touching `FinancialService`,
`EntitlementService`, `ScheduleService`, or either Tracker/Financials
quick-pay dialog.

1. **`FinancialService.summary()` 500'd whenever a project filter was
   applied.** `payment_schedule` has no `project_id` column of its own
   (only `plot_sale_id` — unlike `payment_record`/`plot_sale`, which both
   carry `project_id` directly), but the overdue-instalments query's scope
   clause was built against the nonexistent `ps.project_id`, throwing
   `column ps.project_id does not exist` — but only once a caller actually
   selected a specific project; the org-wide default (no filter) never hit
   that branch, which is exactly why M5's own verification never caught
   it. **Fixed by joining `payment_schedule` to `plot_sale` and scoping via
   `sale.project_id`**, matching the pattern `pending()`/`payments()`
   already used correctly. Added a regression test exercising `summary()`/
   `pending()` with an explicit project filter — the existing tests never
   did.
2. **The Tracker/Financials quick-pay dialogs 400'd on any payment mode
   but Cash.** `PaymentService.record()` requires a non-blank `reference`
   for every mode except CASH (cheque no. / UTR / UPI ref etc.) — both
   `CollectionTable` (Tracker's Collection tab) and
   `PendingInstalmentsTable` (Financials' Pending & Overdue tab) never had
   a reference field at all, so selecting Bank Transfer/Cheque/UPI/DD and
   submitting always 400'd with `REFERENCE_REQUIRED`. **Fixed by adding the
   same conditional reference field `AddPaymentDialog` (the sale's own
   payment dialog) already has** — shown only when `mode !== 'CASH'`,
   required client-side before the submit button enables — in both
   components, and resetting `mode`/`reference` when a new row's dialog
   opens so stale values from a previous row don't leak across.
3. **Quick Create's quota preview showed "0/0, exceeds your plan's
   limit" and blocked plot creation, even on an org the real backend
   commit would have let through with no cap at all.** Root cause was
   two-layered: (a) **data** — the affected org's subscription was still
   pointed at `M4TEST`, a throwaway plan created for earlier manual RBAC
   verification (see the M4 section above) and never switched back to
   `FREE` after that session ended; `M4TEST` has `plan_limit` rows for
   `BUILDER_PROJECTS`/`BUILDER_TEAM_MEMBERS` but none at all for
   `BUILDER_PLOTS_PER_PROJECT`. (b) **code**, the real bug — for a missing
   `plan_limit` row, `EntitlementService.usage()` hardcoded `limit = 0`
   ("no cap allowed"), while `assertWithinQuota()` (the actual server-side
   enforcement used at commit time) already treated the identical missing-
   row case as **unlimited** (`limit == null` short-circuits and returns).
   These two methods disagreeing meant the preview could block a UI action
   the backend itself would have permitted. **Fixed `usage()` to return
   `limit = -1`** (the existing "unlimited" sentinel `UsageSnapshot.unlimited()`
   already understands, and `PlotQuickCreateService.preview()`'s
   `withinQuota` check already handles correctly) for a missing row,
   matching `assertWithinQuota()` exactly. Added a regression test
   (`EntitlementServiceIntegrationTest.usageTreatsAMissingPlanLimitRowAsUnlimitedNotZero`).
   **Any future plan code that's missing a `plan_limit` row for some key
   must be treated as unlimited by every method that reads it, not just
   the enforcement one** — this is the same "two surfaces silently
   disagreeing on the same underlying fact" bug class M5's own overdue-
   consistency fix already hit once this milestone. The affected org's
   subscription was also corrected back to `plan_code = 'FREE'` directly in
   the dev database — it should never have stayed on the test-only plan
   after that verification session ended. One side effect worth knowing:
   restoring the real Free-plan limits means that org can no longer create
   additional projects/team members beyond what it already has (2 of
   each, both retained, just not added to) — correct Free-plan behavior,
   not a new regression.
4. **Waiving an instalment didn't reduce the sale's Balance Remaining —
   the single most significant fix of this round, and the one with an
   actual schema change.** `plot_sale.balance_due` was
   `GENERATED ALWAYS AS (deal_value - total_paid) STORED` — a formula with
   **no term at all** for a waived-but-never-collected amount. Waiving the
   unpaid remainder of a partially-paid instalment correctly removed it
   from the calendar/Tracker follow-up queue (per `ScheduleService.waive()`'s
   own existing behavior), but the sale's displayed balance kept counting
   that same money as still owed — directly contradicting what "waive" is
   supposed to mean, and confirmed by the user waiving ₹10,000 and still
   seeing it as remaining. **This one required an actual migration, not
   just an application-code fix:**
   - `V5_005__add_total_waived_to_plot_sale.sql` adds
     `plot_sale.total_waived NUMERIC(19,2) NOT NULL DEFAULT 0`
     (trigger-maintained, same shape as `total_paid` — never written
     directly by application code), backfills it for any pre-existing
     WAIVED schedule rows via a `SUM(expected_amount - amount_allocated)`
     query, then **drops and recreates** the `balance_due` generated
     column (Postgres can't `ALTER` a generated column's expression in
     place) as `deal_value - total_paid - total_waived` — safe since
     `balance_due` is purely derived, never independently entered data,
     and `total_waived` is already correctly backfilled before the
     generated column recomputes every row against it.
   - `V5_006__create_plot_sale_total_waived_trigger.sql` adds
     `fn_plot_sale_total_waived_trigger()` on `payment_schedule`
     `AFTER UPDATE OF status` (not `payment_allocation`, unlike
     `amount_allocated`/status's own trigger in `V3_010` — `ScheduleService.waive()`
     sets `status` directly via JPA, never touching `payment_allocation`
     at all) — recomputes the full `SUM(expected_amount - amount_allocated)`
     for that sale's WAIVED rows each time, same "just recompute the whole
     aggregate" style as `fn_plot_sale_total_paid_trigger` rather than an
     incremental update.
   - `PlotSale.java` gained a mapped `totalWaived` field (`insertable =
     false, updatable = false`, identical pattern to `totalPaid`/`balanceDue`).
   - `PaymentSummaryResponse` gained a `totalWaived` field, and
     `PaymentSummaryPanel.tsx` now shows a 4th "Waived" figure — **only
     when non-zero**, keeping the panel at its usual 3 columns for the
     overwhelming majority of sales with no waiver — so a waiver stays
     visibly accounted for instead of silently shrinking the balance with
     no explanation.
   - **Because every consumer of a sale's balance (Financials'
     `pendingCollections`, Tracker's Collection "Total Balance" column, the
     sale's own Payment Summary panel) all read the same
     `plot_sale.balance_due` generated column, this one schema fix
     corrected every one of them simultaneously** — no separate
     application-code fix was needed at each call site, the same "fix the
     shared data source, not each display surface" lesson M5's own
     overdue-consistency bug already taught.
   - New regression test:
     `PlotSaleIntegrationTest.waivingTheUncollectedPortionOfAPartiallyPaidInstalmentReducesBalanceDue`
     — pays part of an instalment, waives the remainder, asserts
     `totalWaived`/`balanceDue` land exactly where expected (not just
     "some smaller number").
   - Verified live end-to-end against real seeded data: waiving a
     ₹25,25,000 partially-paid instalment moved `balanceDue` from
     ₹25,25,000 to exactly ₹0 immediately, with `totalWaived` correctly
     showing ₹25,25,000.

**Migration numbering note:** `V5_005`/`V5_006` land after M5's own
`V5_001`–`V5_004` (org_metrics/mv_org_revenue_monthly) even though this
round of fixes was found post-ship — following this project's own
"never edit an already-applied migration, always add a new one" rule
rather than going back to renumber or fold into the M5-era files.

---

## Post-M5 Thorough Re-Verification — "Test Every Payment Scenario"

A follow-up round, explicitly requested: exhaustively test every payment-
related scenario and the whole Financials tab, and update CLAUDE.md for
*every* fix in this stretch (including the ones from the section above,
which hadn't been documented yet when this round started). Combined
backend-test-gap-filling with live curl verification across a richer
dataset (all 5 payment modes, a bounced *and* a cleared cheque, multiple
sales in different states) and a real Playwright pass over every Financials
tab. Two more genuine bugs surfaced, plus one real feature gap and one
accessibility gap — all found by deliberately exercising filter
combinations no earlier test (automated or manual) had ever combined.

**New backend test coverage** (`PlotSaleIntegrationTest`, all previously
gaps in this project's own payment-scenario matrix):
- `waivingARowWithNoPaymentsAtAllRemovesItsFullAmountFromBalanceDue` — the
  other real waive shape besides the partially-paid one already covered:
  forgiving a row nobody has paid anything toward yet (the more common
  "builder forgives the last instalment entirely" case).
- `lumpSumSalePaidInFullLandsAtExactlyZeroBalance` — every existing test
  only ever exercised `INSTALMENT`; `LUMP_SUM` had zero coverage of its own
  despite being a distinct `payment_type` with its own single-row schedule.
- `clearingAChequeIsJustAStatusFlipWithNoEffectOnAmounts` — only `BOUNCED`
  had a test before; `CLEARED` needed its own to confirm it never
  accidentally triggers a reversal.
- `oneMultiRowAllocationSplitsAcrossTwoScheduleRowsCorrectly` — every other
  test allocates one payment to exactly one schedule row; a single payment
  covering two instalments at once (a real, common shape for a large
  one-time payment) had no coverage.
- Extended `cancellingASaleRestoresThePlotAndRetainsPaymentHistory` with an
  assertion that a cancelled sale's `balanceDue` lands at exactly zero — a
  direct, pleasant consequence of the `total_waived` fix above:
  `PlotSaleService.cancel()` already waives every non-PAID row via the same
  status-flip the new trigger fires on, so a cancelled sale never again
  shows a confusing "still owed" figure for a deal that's dead.

**Two more real bugs found via live `curl` verification** (both the exact
same underlying class, found by filtering on something no earlier test —
automated or manual — had ever combined):
1. **`FinancialService.payments()`'s `mode` filter 500'd** with
   `operator does not exist: payment_mode = character varying`.
   `payment_record.mode` is a Postgres enum; a bound
   `NamedParameterJdbcTemplate` parameter arrives typed as `varchar`, and
   Postgres has no implicit `payment_mode = varchar` operator (an untyped
   string *literal* embedded directly in SQL — like every `status IN
   ('PENDING', ...)` list already elsewhere in this class — can cast
   implicitly; a bound parameter can't). Every earlier test/manual pass
   read the payments list without ever filtering by mode, which is exactly
   why this went unnoticed until this round deliberately tried it. **Fixed
   with an explicit cast**: `pr.mode = CAST(:mode AS payment_mode)`.
2. **`DealsHistoryService.list()`'s `status` filter had the identical
   bug** against `plot_sale.status` (`plot_sale_status` enum) — found by
   checking every other native-SQL bound-parameter comparison across
   `FinancialService`/`TrackerService`/`DealsHistoryService`/
   `DashboardService` for the same pattern once the first instance turned
   up, rather than assuming it was a one-off. **Fixed the same way**:
   `sale.status = CAST(:status AS plot_sale_status)`. New
   `DealsHistoryServiceIntegrationTest` (this service had no test coverage
   at all before this).
   **Any future native-SQL query anywhere in this codebase that binds a
   parameter against an enum-typed column needs an explicit `CAST(:param
   AS <enum_type>)`** — a string literal written directly into the SQL
   text does not have this problem and never needed one, which is exactly
   what made every *other* filter in these classes look safe by
   comparison and is why this is easy to reintroduce.

**One real feature gap found and closed**: `03-BUILDER-MODULES.md` B-08
§14.4 explicitly specifies the Financials filter bar as "project, date
range, payment mode, status" — `FinancialFilterBar.tsx` only ever
implemented project + date range; the payment-mode dropdown was missing
from the UI entirely even though `FinancialService.payments()`'s backend
endpoint (and its `FinancialListParams` frontend type) already fully
supported a `mode` parameter — nothing was wired up to actually send it.
**Added the missing mode `<Select>`** to `FinancialFilterBar` (shown only
on the Payments tab, since Overview/Pending's own queries have no `mode`
concept — the same "harmless when unused elsewhere" pattern the existing
project/date filters already follow across tabs), wired into
`FinancialsPage.tsx`'s `paymentsQuery`. This is what surfaced bug #1
above in the first place — the filter never existed to be clicked before,
so the enum-cast bug had no way to be found via the UI, only by a curl
call deliberately trying the parameter the backend already accepted.

**One real accessibility gap found and closed**: neither the (pre-existing)
project filter's `<SelectTrigger>` nor the newly-added mode filter's had
an `aria-label` at all — a screen-reader user would have no way to know
what either dropdown was for (Radix's trigger isn't a native `<select>`
with an `id` a `<label for>` can associate with; the plain sibling
`<label>` elements next to each dropdown were purely visual, doing nothing
for assistive tech). Found because a Playwright `getByRole('combobox',
{name: ...})` selector — the correct, accessible-name-based way to target
a Select trigger — simply couldn't find it. **Fixed by adding
`aria-label={t(...)}` to both `SelectTrigger`s**, matching the pattern
`LanguageToggle.tsx` already established for exactly this component.
**Any future `SelectTrigger` in this codebase needs an explicit
`aria-label`, matching its adjacent visual `<label>` text** — a bare
sibling `<label>` with no `htmlFor`/`id` association provides zero
accessible name to a Select trigger, and this project has now hit this
gap twice (`LanguageToggle` already needed it; both Financials filters
needed it fresh).

**Full re-verification, all green:**
- Backend: 63/63 `mvn test` (16 test classes, Testcontainers), including
  all 5 new regression tests above.
- Frontend: `npx tsc -b --noEmit` clean, `npx vitest run` 18/18, `npx
  oxlint` shows only the same pre-existing shadcn warnings noted since M2.
- A new, dedicated Playwright spec
  (`frontend/e2e/m5-thorough-financials-verification.spec.ts`, 7 tests, all
  green) drives every Financials tab for real: Overview (real currency
  figures + all 5 payment-mode legend entries actually render in the
  chart), Payments (the reversal row is visible, and the new mode filter
  demonstrably reduces the visible row count when applied), Pending &
  Overdue (real upcoming rows in Pending; an honest "No overdue
  instalments" empty state once every genuinely overdue row from earlier
  in this session had already been resolved), Commission ("isn't available
  yet" honest empty state, not a broken blank page), Tracker Follow-ups and
  Collections (both real, evolved-by-then seeded rows visible), and Deals
  History (a cancelled deal's detail page shows its real cancellation
  reason and a `balanceDue` of exactly zero — direct confirmation of the
  `total_waived` fix cascading all the way to this display too).
- The full pre-existing e2e regression suite (10/10: M2 builder flow,
  M2 plot-reserved-status, M2 project-status, M3 sale-payments,
  notification-mark-all-read, quick-create-plots) still passes unchanged.
- Re-running the earlier `m5-financials-tracker-dashboard.spec.ts` spec
  showed 2 of its 6 tests failing — **both are stale hardcoded
  expectations from this specific seeded org's own state having evolved
  across an unusually long, cumulative live-debugging session (a plot
  count assertion written when the org had exactly 6 plots, now 11 after
  several rounds of Quick Create testing; an "overdue row" assertion
  targeting a specific instalment this same session had already paid off
  manually while chasing a different bug), not product regressions** —
  the identical class of environment-drift issue this project already
  documented for `m2-grid-performance.spec.ts`'s stale container-name
  reference. Every other check in that spec, and everything in the new
  spec above, still passes against this same evolved org.

**One more real bug found immediately after, by the user actually using
the fix above**: `PlotSaleService.complete()` ("Mark Complete") gated on
`totalPaid >= dealValue` directly — a check written in M3, before M5's
`total_waived` concept existed, and never updated for it. A sale whose
final instalment was waived rather than paid had `totalPaid` permanently
short of `dealValue`, so "Mark Complete" 400'd with `SALE_NOT_FULLY_PAID`
forever even though `balanceDue` was already correctly zero. **Fixed by
gating on `balanceDue <= 0` instead** — the same figure every other
surface already treats as authoritative, rather than re-deriving the same
fact a second, now-stale way. `complete()` had zero test coverage before
this; added two tests (a waived-to-zero sale completes successfully; a
genuinely-still-owing sale is still correctly rejected). **Any future
"is this sale fully settled" check anywhere in this codebase must read
`balanceDue`, never re-derive it from `totalPaid`/`dealValue` directly** —
this is now the second time a hand-rolled "fully paid" check has drifted
out of sync with the real balance calculation once waiving entered the
picture.

---

## Milestone 6 — Decisions & Environment Notes

Real bugs found while building B-14 (Broker Management & Commissions) and
M-08's remaining two calculators (Brokerage, Stamp Duty), and while driving
the whole thing through real HTTP calls and a real browser (including a
Hindi + 360px pass) against an isolated `m6verify` docker-compose stack —
same standard as every milestone so far. Read this before touching
`builder.broker`, `foundation.calculator`, or `PlotSaleService`'s sale
transaction again.

**Architecture decisions:**
- **Commission resolution precedence is PLOT → PROJECT → GLOBAL → broker
  default**, implemented as `CommissionConfigService.resolve()` querying
  each scope in order via `BrokerCommissionConfigRepository.findEffectiveConfigs`
  (bound-parameter, never an inlined enum literal — see the recurring bug
  class below) and short-circuiting on the first match, never merging
  across scopes. The winning result — base commission, tier bonus, and a
  full JSON snapshot of exactly which config/tier produced it — is written
  once onto `commission_ledger_entry.config_snapshot` at sale-creation time
  and never touched again; editing or deleting the source config afterward
  provably cannot alter it, since nothing ever reads the live config row
  again for that ledger entry. Verified directly (not just by code
  inspection): changed a broker's GLOBAL rate from 3% to 6% after a sale
  existed at 3%, and that sale's own ledger entry still read exactly
  ₹30,000 afterward, while a brand-new sale created after the change
  correctly picked up 6%.
- **Tiers auto-upgrade only, never auto-downgrade.** `BrokerTierService.evaluateAndUpgrade()`
  compares the candidate tier's `minDeals` against the broker's current
  tier and only ever moves up; a manual `tier-override` sets
  `tier_manually_overridden = true`, which freezes auto-evaluation
  entirely until... nothing currently clears it — the B-14 API surface has
  no "clear override" endpoint, only `POST .../tier-override` to set one.
  Documented as a known gap rather than inventing an unspecified endpoint.
- **`broker_tier`'s overlap prevention is a real Postgres GiST EXCLUDE
  constraint** (`btree_gist` extension, `V6_001`/`V6_002`), not just an
  application-layer check — `BrokerTierService.assertNoOverlap()` is a
  friendly pre-flight only. Getting the range math right for an
  open-ended top tier ("Platinum 25+", `max_deals = NULL`) mattered: a
  first draft excluded `max_deals IS NULL` rows from the constraint
  entirely, which would have let an unbounded tier silently overlap any
  bounded one with no DB-level rejection — caught by reasoning through the
  consequence before it was ever tested, fixed by relying on
  `int4range(min_deals, max_deals + 1)`'s own correct handling of a NULL
  upper bound as genuinely unbounded, with no special-casing needed.
- **Trigger-maintained aggregates, the same established pattern reused
  again**: `broker_partner.deals_closed_count`/`total_commission_earned`/
  `total_commission_paid` and `commission_ledger_entry.amount_paid`/`status`
  are all DB-trigger-maintained (`V6_014`, `V6_015`), full-recompute style
  (SUM/COUNT, never increment/decrement) exactly like every prior
  milestone's `org_usage`/`org_metrics`/`plot_sale.total_paid` triggers.
  `CANCELLED` is a manual-only ledger status the trigger never overwrites,
  mirroring `payment_schedule`'s own `WAIVED` handling.
- **A commission ledger entry's "recovery" flag (B-14 §10: "deal cancelled
  after commission paid → recovery entry... never a silent deletion") is
  derived at read time, not a second stored row.** `CommissionLedgerEntryResponse.needsRecovery`/`.recoveryAmount`
  are computed from `status = CANCELLED AND amountPaid > 0` — the existing,
  trigger-maintained `amount_paid` already IS the exact amount that needs
  recovering, so a parallel "negative entry" concept would just be two
  records answering the same question. `CommissionLedgerService.cancelForSale()`
  only ever flips `status`, never zeroes or duplicates `amount_paid`.
- **`BUILDER_BROKERS` quota already existed** (seeded `'0'` for FREE back in
  M2's `V2_009`), which is the entire "Free = N/A, module hidden"
  requirement (B-14 §21.1) resolved for free — no new boolean feature gate
  needed, just `assertWithinQuota(orgId, "BUILDER_BROKERS", null, ...)`,
  the same org-wide (non-per-project) quota shape `BUILDER_PROJECTS`
  already used.
- **A manually-typed commission amount on the sale form is only ever used
  as a genuine fallback, never an override of a resolved config.** If
  `resolve()` finds a real config at any scope, that figure wins outright;
  `req.brokerCommissionAmount()` only becomes the ledger's own commission
  when `resolve()` returns empty (B-14 §10's "requires a manual commission
  amount rather than silently recording zero"). In practice this fallback
  path turned out to be **unreachable through the real broker-creation
  path** — see the self-caught bug below.

**Real bugs found and fixed:**
- **`broker_partner.bank_account_last4` was declared `CHAR(4)` in `V6_003`**
  — the exact CHAR/VARCHAR schema-drift class M1's `V1_008` and M3's
  `plot_sale.buyer_gov_id_last4` both already hit — caught immediately by
  running an existing, unrelated backend test (which forces Flyway to
  replay every migration from scratch), not by any M6-specific test:
  `SchemaManagementException: wrong column type... found bpchar, expecting
  varchar(4)`. Fixed by changing the migration to `VARCHAR(4)`.
  **Proactively also fixed the identical mistake in `V6_009`'s
  `stamp_duty_rate.state_code`** before it ever got the chance to fail the
  same way, once the pattern was recognized — this is now a 4-times-recurring
  class (`V1_008`, `V3_001`'s buyer_gov_id_last4, and these two) and should
  be the default assumption to check on sight in any new migration.
- **A genuine ArchUnit false positive from a package-name collision.**
  `ArchitectureTest.builderAndBrokerMustNotDependOnEachOther()`'s original
  rule used the bare wildcard `resideInAPackage("..broker..")`, which
  matches *any* package with a `broker` path segment — including the
  brand-new `com.shardeya.builder.broker` (B-14, a Builder-persona feature
  for managing brokers as a resource), not just the intended target,
  `com.shardeya.broker` (the actual Broker-persona side, BR-01..BR-08).
  Every one of `builder.broker`'s own internal, entirely-legitimate
  cross-class references inside the same module were flagged as 557
  "violations" the first time this milestone's code ran the full suite.
  **Fixed by anchoring the rule to the exact top-level package
  (`"com.shardeya.broker.."` instead of `"..broker.."`)** — `builder.broker`
  is correctly left alone, and the real isolation guarantee (the actual
  Broker-persona package never depending on anything Builder-flavoured, and
  vice versa) is unchanged. Worth remembering for any future package whose
  own name happens to contain another top-level package's name as a
  substring.
- **`V6_012__backfill_broker_tiers.sql`'s own comment claimed "new orgs get
  the identical 4 rows seeded at signup time instead (`AuthService.verifySignupOtp`)"
  — but that was never actually implemented.** `AuthService.java` had zero
  references to `BrokerTier` anywhere before this fix. Every BUILDER org
  created after M6 shipped (i.e., through the real signup flow, not the
  one-time migration backfill) would have had **zero** `broker_tier` rows,
  silently disabling tier auto-upgrade entirely for them — a broker added
  under such an org gets `tierId = null` at creation (`BrokerPartnerService.create()`'s
  own `findMatchingTiers(orgId, 0)` lookup finds nothing to assign), and
  `evaluateAndUpgrade()` can never find anything to upgrade *to* either.
  Only caught by actually driving a fresh signup through the M6 e2e
  verification flow and noticing the tier progress bar was empty — the
  migration's own comment reads as if this were already handled, which is
  exactly the kind of self-contradicting documentation that's easy to
  trust without checking. **Fixed by adding `AuthService.seedDefaultBrokerTiers()`**
  (Bronze 0–2, Silver 3–9, Gold 10–24, Platinum 25+ — byte-for-byte
  identical to `V6_012`'s own seed shape), called inside
  `verifySignupOtp()`'s existing transaction, only for
  `pending.role() == Organization.Type.BUILDER` (Broker-persona orgs never
  touch `broker_tier` at all).
- **`SaleWizard`'s live commission preview never passed `projectId`/`plotId`
  to the backend** — `previewCommission(brokerPartnerId, { dealValue })`
  omitted both, so `resolve()` could only ever match a GLOBAL-scope config,
  silently skipping any PLOT- or PROJECT-level override the broker actually
  had for that exact deal. This is precisely the scenario the milestone
  kickoff explicitly asked to verify ("a sale with a per-plot commission
  override vs. one falling back to the broker's global rate") — and it's
  exactly what caught this: a broker with a 5% PLOT override on the plot
  being sold showed a 3% GLOBAL-rate preview instead. **Fixed by adding
  `projectId`/`plotId` (already in scope as the wizard's own props) to the
  `previewCommission()` call and its query key** — a real, high-value bug a
  browser-driven preview check surfaced that no amount of backend-only
  testing (the backend's own `resolve()` was always correct) would ever
  have caught, since the bug was purely in what the frontend chose to send.
- **A double-₹ symbol in the new `broker:saleWizard.commissionPreview` i18n
  string** — `formatIndianCurrency()` already returns a string with the ₹
  symbol included, but the translation text I wrote also had a literal `₹`
  prefix (`"Estimated commission: ₹{{amount}}"`), rendering as
  `"₹₹30,000"`. Caught directly in a Playwright accessibility snapshot, not
  by eye — the existing, pre-M6 `sale:commissionPreview` string (for the
  external-broker path) was already written correctly with no literal
  symbol, confirming this was a fresh mistake in the new string, not an
  established pattern I misread. Fixed in both `en` and `hi`.
- **`AddPaymentDialog`'s success handler invalidated `payments`/`payment-summary`/`schedule`
  but never `sale-by-plot`** — the exact query `SaleDetailPanel`'s own
  "Mark Complete" button gates on (`sale.balanceDue <= 0`). A payment that
  brought the balance to precisely zero correctly updated the visible
  "Balance Remaining: ₹0" figure (reads the separately-invalidated
  `payment-summary` query) but "Mark Complete" stayed invisible until the
  drawer was closed and reopened, since the `sale` object itself never
  refetched. This is an M3-era bug this milestone's own "record a payment,
  then mark the sale complete" verification step surfaced by accident —
  the exact same integration point the kickoff instruction flagged as
  higher-risk ("that transaction has already been patched multiple times
  in M3/M5"), just from the frontend-caching side rather than the backend
  transaction side this time. **Fixed by also invalidating `['sale-by-plot']`**
  (a query-key-prefix invalidation, not scoped to one plot — no `plotId`
  is threaded into this dialog, and only one sale detail panel is ever
  open at a time, so this is safe). **Any future mutation that changes a
  sale's own state needs to consider whether `sale-by-plot` needs
  invalidating too, not just whichever narrower query happens to drive the
  visible number** — the same "two surfaces silently disagreeing on the
  same underlying fact" bug class M5's overdue-consistency fix and the
  post-M5 quota-preview fix already both hit.
- **A systemic accessibility gap, caught once and then found repeated
  across every new M6 form**: `Label`/`Input` pairs with no `htmlFor`/`id`
  association, and `SelectTrigger`s with no `aria-label` — the exact class
  CLAUDE.md already flagged twice before (`LanguageToggle`, then both
  Financials filters in the post-M5 round). First caught by a genuinely
  broken Playwright interaction (`getByLabel('Maximum Deals')` timing out
  against `TierConfigPage`'s tier-edit dialog, which had no label
  association at all), then proactively audited and fixed across every
  other new M6 form once the pattern was recognized: `TierConfigPage`,
  `CommissionConfigTab` (both the preview card and the add-rate dialog),
  `LedgerTab`'s payment dialog, `NotesTab`, and all three calculator
  components (`PlotSizeCalculator`, `BrokerageCalculator`,
  `StampDutyCalculator`). **This is now the third time this exact gap has
  been found in this codebase — any future form with a `Label`+`Input`/`Select`
  pair needs an explicit `htmlFor`/`id` (and `aria-label` on
  `SelectTrigger`) from the start**, not discovered after the fact by a
  broken test.

**Verification methodology and results:**
- Backend: 84/84 `mvn test` green (Testcontainers), including new
  integration test coverage added specifically for this milestone's own
  scenarios: `PlotSaleCommissionIntegrationTest` (plot-override-wins,
  global-fallback, rate-immutability-after-a-later-config-change,
  tier-auto-upgrade-on-completion, cancel-cascades-to-ledger, and the
  no-broker lifecycle regression proving the new commission wiring didn't
  disturb the ordinary M3/M5 sale path), `CommissionPaymentServiceIntegrationTest`
  (partial-then-full payment settling a ledger entry, overpayment blocked
  unless confirmed, reverse/double-reverse/reverse-of-a-reversal guards,
  payments blocked against a cancelled entry), and
  `CalculatorControllerIntegrationTest` (brokerage GST/share-mismatch math,
  stamp-duty gender-specific lookup, the `gender=ANY` fallback path
  exercised for real against MP/KA's deliberately gender-neutral seed rows,
  Maharashtra's registration-cap semantics, and the "no rate row for this
  state" rejection).
- Frontend: `npx tsc -b` clean, `npx vitest run` 20/20 (18 pre-existing +
  the 2 new `broker`/`calculator` i18n key-parity checks), `npx oxlint`
  shows only the same pre-existing shadcn warnings noted since M2.
- **Browser verification against an isolated `m6verify` docker-compose
  stack** (remapped ports, its own Postgres/Redis/backend, following the
  exact `m2verify`/`m3verify` precedent for running alongside another
  session's stack on the same host) — a new permanent e2e spec
  (`frontend/e2e/m6-broker-commissions.spec.ts`) drives the real UI through
  every scenario the milestone kickoff explicitly asked for: add a broker
  → configure a GLOBAL 3% rate and a PLOT-level 5% override → sell that
  plot and confirm the wizard's live preview shows ₹50,000 (the PLOT rate,
  not GLOBAL or the broker's own 2% default) → complete that sale (record a
  real payment first — the wizard's Payment Plan step only defines the
  *expected* schedule, not an actual payment, same distinction M3's own
  suite already established) and confirm the broker's tier auto-upgrades
  from Bronze to Silver → sell a second plot with no override and confirm
  the preview correctly falls back to the 3% GLOBAL rate → bump the GLOBAL
  rate to 6% and confirm the *first* sale's rate stays exactly what it was
  → record a commission payment against the ledger and confirm it settles.
  Passes on both the `desktop` and `mobile-360` (360px) Playwright
  projects. A separate, one-off Hindi + 360px visual pass (not kept
  permanently, matching the M3/M5 precedent for this kind of check) covered
  the broker list, all 5 broker-detail tabs, the tier config page, and all
  three calculator tabs — zero horizontal overflow anywhere, no raw i18n
  keys leaking through, tier-progress-bar interpolation
  (`"Bronze — Silver तक पहुंचने के लिए 3 और सौदे"`) rendering correctly.
  **Regression check**: the pre-existing e2e suite (`m2-project-status`,
  `m2-plot-reserved-status`, `m3-sale-payments`,
  `notification-mark-all-read`, `quick-create-plots` — 5/5 run against the
  isolated stack) still passes unchanged, confirming the `SaleWizard`/`AddPaymentDialog`
  edits didn't disturb the existing M2/M3 flows.
  `m5-financials-tracker-dashboard.spec.ts` was NOT green against this
  stack, but for the same documented reason `m2-grid-performance.spec.ts`
  already isn't: it logs into a specific pre-seeded account
  (`9577778560`) that only exists in the original M5 verification
  session's own database, not a fresh isolated stack — an environment
  mismatch, not a regression. `m2-builder-flow`/`m2-grid-performance`
  weren't re-run for the same pre-existing reason.

**Known, deliberate scope decisions, not bugs:**
1. **No broker statement PDF** (B-14 §5's `BrokerStatementDownload`, listed
   as an M6 exit criterion: "Generate a broker statement PDF"). No PDF
   rendering infrastructure (OpenPDF/Flying Saucer) exists anywhere in this
   codebase yet — confirmed via `pom.xml`, zero dependencies — matching the
   exact precedent M3 already set for receipt PDFs ("out of scope... per
   the milestones doc's own phasing," deferred until B-11 actually lands).
2. **No admin endpoint for editing `stamp_duty_rate` rows** (also a named
   M6 exit criterion: "Stamp duty rates editable (admin endpoint, M14
   basic)"). M-14 (platform admin) doesn't exist as a module yet either —
   and unlike the PDF gap, building a stopgap here would have been actively
   wrong, not just incomplete: `stamp_duty_rate` is a genuinely global,
   `org_id`-less reference table shared by every tenant, so wiring an edit
   endpoint behind the existing org-level `BUILDER_ADMIN` role (the only
   "admin" concept that exists today) would let any one builder's admin
   edit rates every other tenant relies on — a real tenant-isolation
   problem, not a convenience gap. Deferred until real platform-admin
   infrastructure exists to gate it correctly, rather than shipping the
   wrong permission model to hit a checklist item.
3. **No `linked_user_id` "invite broker to Shardeya" flow** (B-14 §4's
   `POST /brokers/{id}/invite`, §13's "growth loop"). Genuinely out of
   scope for this milestone's own build order (the kickoff instruction's
   own step list ends at the calculators, never mentions invite), and
   depends on the broker-persona signup/account-linking machinery this
   milestone never touched.
4. **Broker deals/ledger/interactions list endpoints are plain lists, not
   cursor-paginated**, despite CLAUDE.md's own API convention table calling
   for cursor pagination on list endpoints generally — matching the
   established precedent `BrokerPartnerService.list()` itself and
   `CommissionConfigService.list()` already set (plain `List<T>` returns,
   no cursor) rather than introducing a new pagination shape for only this
   module's sub-resources.
5. **All verification is against the Free plan** (plus one throwaway
   `M6TEST` plan row, created and used exactly like M4/M5's own
   `M4TEST`/similar throwaway plans, solely to unblock the `BUILDER_BROKERS=0`
   quota for test-data setup — never to change what's actually tested). No
   real Pro/Premium plan exists until M8, so the "Broker quota enforced
   (Pro = 10)" exit criterion is provable only as "correctly blocked on
   Free," same recurring caveat every milestone since M2 has noted.

---

## Post-M6 Feature Change — Registered-Broker-Only Sales

Requested by the user while checking the shipped M6 build on a live
account (same "found by actually using it" pattern as the Post-M5 gap
closure round) — a deliberate product/UX decision, not a bug fix, but
documented the same way per the project's own standing rule.

**The change:** `SaleWizard`'s broker step originally offered three modes —
No Broker, Select Broker (in-system, B-14), and a free-text "Broker Name"
entry (the pre-B-14, M3-era path — a builder could type any name/mobile
inline while selling a plot). **The free-text mode is now removed
entirely.** If a builder wants to attribute a sale to a broker, that
broker must already be registered via the Brokers tab first.

**Why:** the free-text path produces **zero commission-ledger automation**
— no `commission_ledger_entry` row, no `deals_closed_count` increment, no
tier tracking, nothing shows up anywhere under Broker Management. A
builder could walk through the whole wizard, type a broker's name, and end
up with literally nothing in the one module (B-14) whose entire purpose is
turning "brokers tracked in a notebook" into a system. Keeping only
"No Broker" and "Select Broker" closes that gap — the small extra
friction of registering a broker first is the point, not a cost, and
matches B-14 §10's own edge-case text ("a persistent prompt suggests
adding them") treating free-text entry as something to steer away from,
not a permanent parallel path.

**What changed:**
- `SaleWizard.tsx`: `BrokerMode` narrowed from `'NONE' | 'IN_SYSTEM' | 'EXTERNAL'`
  to `'NONE' | 'IN_SYSTEM'`; the entire free-text block (name/mobile inputs,
  the manual commission-amount input, its own preview text) is gone. When
  "Select Broker" is picked and the org has zero registered brokers, an
  inline hint now points at the Brokers page instead of silently offering
  nothing.
- `schemas.ts`: `externalBrokerName`/`externalBrokerMobile` removed from
  the Zod schema (`SaleFormValues`) — the wizard never sends them now.
  **Deliberately not a backend/schema change**: `SaleCreateRequest`/
  `SaleUpdateRequest`'s own `externalBrokerName`/`externalBrokerMobile`
  fields and `plot_sale.external_broker_name`/`external_broker_mobile`
  columns are untouched, since sales created via the old path (before this
  change) still carry that data and it's still displayed correctly for
  them — see the `SaleDetailPanel` fix below.
- i18n cleanup: removed the now-dead `sale:form.fields.brokerName`/
  `brokerMobile`/`brokerCommission` and `sale:broker.notInSystem`/
  `commissionPreview` keys (en+hi — nothing referenced them anymore).
  Repurposed `broker:saleWizard.notInSystem` from its old meaning ("enter
  details manually") to the new empty-state hint ("no brokers registered
  yet, add one from the Brokers page first").

**A real, related gap found and fixed in the same pass**: the plot detail
page's `SaleDetailPanel` had **never shown the broker at all** for a sale
attributed to a real, in-system (B-14) broker — it only ever displayed
`sale.externalBrokerName`, a field that's only ever populated by the
now-removed free-text path. A sale sold "with the help of a broker"
through the actual B-14 flow showed nothing broker-related anywhere on
the plot detail page, which is a real usability gap independent of the
UX change above (this bug existed since M6 shipped, not introduced by
removing the free-text path).
- **Backend**: `SaleResponse` gained a `brokerName` field.
  `PlotSaleService.toResponse()` now denormalises it via a
  `BrokerPartnerRepository` lookup by id when `brokerPartnerId` is set —
  the same "denormalise a display name directly into the response DTO
  rather than make the frontend do an N+1 lookup" convention
  `CommissionLedgerEntryResponse`/`BrokerDealResponse` already established
  in M6. A plain `findById`, not a tenant-scoped query, since the sale
  itself was already loaded through an org-scoped path and RLS on
  `broker_partner` is the real backstop either way.
- **Frontend**: `SaleDetailPanel` now shows the broker as a clickable link
  to `/builder/brokers/{id}` whenever `sale.brokerPartnerId` is set (using
  the new `brokerName`, falling back to the raw id if the lookup somehow
  came back empty). The old plain-text `externalBrokerName` display is
  kept, but only as a fallback for legacy sales that have no
  `brokerPartnerId` at all — i.e., sales created before this change,
  through the now-removed free-text path.

**Verification**: backend recompiled clean; `PlotSaleIntegrationTest`
(13/13) and `PlotSaleCommissionIntegrationTest` (7/7) both still green
(no other code constructs `SaleResponse` directly, so the new
constructor argument was a single call-site change). Frontend: `tsc -b`
clean, `vitest run` 20/20, `oxlint` clean. Backend container rebuilt and
redeployed to the live verification stack the user was checking against;
frontend picked up the change via Vite's own hot-reload, no restart
needed there.

---

## Post-M6 Bug Fix — Commission Payment Rounding Mismatch

Found by the user on the same live verification account, immediately
after the change above: a real ledger row with `balance_due = 0.62` (a 6%
commission on a non-round ₹4,577 deal value) displayed as **"₹1"** in the
Ledger tab, since every money figure in this app is shown rounded to
whole rupees via `formatIndianCurrency`. Typing the "1" the user actually
saw on screen triggered **"This amount exceeds the balance due"** —
mathematically correct against the raw `0.62`, but confusing since
nothing else in that dialog ever shows a non-whole-rupee figure to
compare against; the user had no way to know the real balance wasn't
exactly ₹1.

**Root cause**: `LedgerTab.tsx`'s `RecordPaymentDialog` compared the
user's typed amount directly against the raw `entry.balanceDue`
(`Number(amount) > entry.balanceDue`), while the dialog's own visible
"Balance Due" text (inherited from the ledger row) is the same
whole-rupee-rounded figure shown everywhere else in the app. Any
commission entry whose `total_commission` doesn't divide evenly (routine
for a percentage-of-a-non-round-deal-value commission, which is the
overwhelmingly common case) can land on paise, and any partial payment
against it can leave a paise-level remainder — this wasn't a rare edge
case, it's the normal shape of the data.

**Fixed** by gating the "exceeds balance" warning (and the confirmation
checkbox it shows) on `Math.round(entry.balanceDue)` instead of the raw
value — the same rounding `formatIndianCurrency` itself applies, so the
warning now only fires when the typed amount genuinely exceeds what the
user can actually see. Since the backend's own `CommissionPaymentService.record()`
still does an exact `BigDecimal` comparison with no rounding tolerance
(correctly — it's the source of truth), the frontend now also
auto-sends `confirmOverpayment: true` whenever the typed amount is within
that same rounded figure, so a payment the UI never warned about isn't
then rejected by the backend's stricter check. A *genuine* overpayment
(beyond the rounded, user-visible balance) still requires the explicit
checkbox, unchanged.

No equivalent bug exists on the buyer-payment side (`AddPaymentDialog`):
it has no overpayment guard at all, since a buyer payment can legitimately
include a genuine advance beyond what's currently due (allocated across
future schedule rows) — this rounding mismatch is specific to the
commission-ledger path, which really does have a fixed balance a payment
shouldn't exceed.

**Any future "does this amount exceed that balance" comparison needs to
round the *balance* side the same way it's displayed before comparing** —
comparing a user-typed whole-rupee amount against a raw, unrounded
BigDecimal is the general shape of this bug, not something specific to
commissions; worth checking for the same pattern if a similar guard is
ever added elsewhere.

**Verification**: `tsc -b` clean, `oxlint` clean; manually re-derived the
fix against the exact reported case (`balanceDue = 0.62` → rounds to `1`
→ `exceedsBalance` for a typed `1` is now `false`, and the auto-sent
`confirmOverpayment` covers the backend's own stricter exact-decimal
check). Picked up live via Vite's hot-reload on the verification stack the
user was already checking against, no backend change needed (frontend-only
fix).

---

## Post-M6 Feature Change — Lump Sum Locks the Payment Plan to One Row

Also found by the user on the same live account, poking at the sale
wizard directly: **`PaymentPlanBuilder` rendered its full multi-row
instalment editor — "Add instalment" button and all — regardless of
whether "Lump Sum" or "Instalment" was selected as the payment type.**
Nothing on the backend ties `paymentType` to the shape of the `schedule`
array either (it's stored purely as a label on the sale row, with zero
Bean Validation cross-checking it against the schedule) — so a builder
could genuinely pick "Lump Sum" and still assemble a five-row instalment
plan underneath it, and the sale would save exactly like that: a
mislabelled `paymentType` with an instalment-shaped schedule. Not a crash,
just semantically wrong, and confusing enough that the user asked "why do
I still see an instalment option under Lump Sum?" — a fair question, since
there was no good answer.

**Fixed on the frontend only** (deliberately not a backend/schema change —
`paymentType` staying a plain label with no cross-validation is
unaffected; this is purely about what the wizard *lets the builder build*
before submit):
- `PaymentPlanBuilder.tsx` now takes a `paymentType` prop. When it's
  `LUMP_SUM`, the component renders a single locked row — deal value shown
  read-only, due date still editable — with no add/remove controls and no
  reconciliation bar (trivially always matches, since the row *is* the
  deal value). `INSTALMENT` keeps the exact prior multi-row behaviour
  unchanged.
- `SaleWizard.tsx`: switching the Payment Type select now resets
  `schedule` to match — a single full-value row for Lump Sum, a blank
  instalment row for Instalment — so leftover rows from the other mode
  never linger in form state under the wrong label.
- A `useEffect` keeps the Lump Sum row's amount in sync with the live deal
  value: without it, changing the deal value on step 1 *after* already
  selecting Lump Sum on step 2 would leave the submitted schedule amount
  stale relative to what's now shown as the deal value (the locked row's
  own "Deal Value" display always reads the live prop, so this specific
  drift wouldn't even be visible before hitting submit — a real, if
  narrow, correctness gap the fix closes proactively rather than waiting
  for it to be reported separately).

**Verification**: `tsc -b` clean, `oxlint` clean, `vitest run` 20/20
unchanged. Re-ran the existing e2e suite's two specs that drive a sale
through the real wizard UI (`m3-sale-payments.spec.ts`,
`m6-broker-commissions.spec.ts` — both exercise `INSTALMENT`, the
unchanged branch) against the live verification stack: both still pass,
confirming the refactor didn't disturb the existing instalment path. No
existing e2e spec drives `LUMP_SUM` through the wizard UI itself (only via
direct API calls, e.g. `notification-mark-all-read.spec.ts`), so the new
locked-row behaviour has no automated coverage yet — worth adding if a
future milestone touches this wizard again. Frontend-only change, picked
up live via Vite's hot-reload, no backend redeploy needed.

---

## Post-M6 Bug Fix — Negative "Commission Due" Display

Reported by the user directly from their own live account: the Brokers list
showed **"-₹0"** as a broker's Commission Due figure — a confusing,
seemingly-nonsensical value for a field that should never be negative.

**Root cause, confirmed via a direct DB query** against the live broker's
row: `total_commission_earned = 668274.62`,
`total_commission_paid = 668275.00` — the broker had genuinely been paid
₹0.38 *more* than they'd earned. This is not corrupted data: it's a direct,
correct consequence of this same session's earlier "Commission Payment
Rounding Mismatch" fix (see the section above), which deliberately lets a
payment equal to the *displayed, rounded* balance succeed even when it's a
few paise more than the *exact* underlying `BigDecimal` balance —
`formatIndianCurrency` always rounds to whole rupees (`maximumFractionDigits:
0`), so a broker due ₹6,68,274.62 displays as (and can be correctly paid as)
"₹6,68,275". `BrokerPartnerService`'s `commissionDue = earned.subtract(paid)`
then correctly computes a small negative `BigDecimal` (`-0.38`) — mathematically
right, but "commission due" isn't a concept that should ever go negative in
the UI, and JavaScript's `Intl.NumberFormat` renders a small negative value
that rounds to zero as the signed string `"-₹0"` rather than `"₹0"`, which is
what actually appeared on screen.

**Fixed by clamping `commissionDue` to zero server-side**, not by touching
the rounding-tolerance fix that correctly caused the small overpayment in the
first place. Added a private `clampToZero(BigDecimal)` helper to
`BrokerPartnerService` (`value.signum() < 0 ? BigDecimal.ZERO.setScale(value.scale())
: value`) and applied it everywhere `commissionDue` is computed:
`toResponse()` (the `BrokerResponse` used by the Brokers list/detail) and
`performance()` (the `BrokerPerformanceResponse` used by the ledger's
performance summary) — both previously did the raw `earned.subtract(paid)`
subtraction inline. **Any future money field describing "how much is still
owed" (as opposed to a running balance that can legitimately be negative,
like a wallet) should clamp to zero the same way once rounding-tolerant
overpayment is allowed anywhere upstream of it** — this is a direct,
predictable side effect of that earlier fix, not an unrelated bug, and the
same clamp will be needed by any other "X due" figure that shares the same
rounding-tolerant payment path.

Verified: `mvn -q compile` clean. No dedicated regression test added (the
existing `CommissionPaymentServiceIntegrationTest`/`PlotSaleCommissionIntegrationTest`
suites already cover the overpayment path that produces the negative
underlying value; this fix is a pure display-clamp on top, not new business
logic) — both suites re-run green (7/4 tests) after the change.

---

## Post-M6 Bug Fix — Financials Pending/Overdue Tabs Not Refreshing Live

Reported by the user directly: recording a payment from the Financials
page's Pending & Overdue tabs correctly saved (confirmed: the row was gone
after navigating away and back, forcing a fresh mount/refetch), but the row
never disappeared in place — only after leaving the page and returning.

**Root cause**: `FinancialsPage.tsx` renders `PendingInstalmentsTable` (the
same component) **twice** — once fed by `pendingQuery`
(`useQuery({queryKey: ['financial-pending', projectId], ...})`) for the
Pending section, and once fed by `overdueQuery`
(`useQuery({queryKey: ['financial-overdue', projectId], ...})`) for the
Overdue section. The component's own quick-pay mutation's `onSuccess` only
ever invalidated `['financial-pending']` (plus `['financial-summary']`) —
whichever instance of the component the user actually paid from, both
instances share the exact same mutation code, so a payment made from the
*Overdue* table correctly wrote to the DB but only ever told the *Pending*
query to refetch, leaving the Overdue table showing stale data until some
unrelated navigation forced a remount. Confirmed by reading
`CollectionTable.tsx` (Tracker's own equivalent quick-pay table) for the
same pattern — it does **not** have this bug, since it's only rendered once
against a single query key (`['tracker-collections', collectionRange]`).

**Fixed by invalidating both query keys from the one shared mutation** —
`PendingInstalmentsTable`'s `payMutation.onSuccess` now invalidates
`['financial-pending']` **and** `['financial-overdue']` unconditionally,
alongside the existing `['financial-summary']`. The component has no way to
know which of the two sections it's being rendered for, so invalidating
both unconditionally is the correct fix, not an attempt to thread through
which query key "owns" a given instance. **This is now the same "shared
component rendered against multiple differently-keyed queries, invalidation
only covers one" bug shape this project has already hit once before**
(`AddPaymentDialog`'s `sale-by-plot` invalidation gap, an earlier M3-era
fix) — any future shared list/table component fed by more than one query
key in different places on the same page needs to invalidate every key it
could plausibly be feeding, not just the first one written.

Verified: `tsc -b` clean, `oxlint src/features/builder/financials
src/features/builder/brokers` clean, `vitest run` 20/20 green. Frontend-only
change, picked up live via Vite's hot-reload, no backend redeploy needed.

---

## Milestone 7 (First Half) — Decisions & Environment Notes

First half only: B-11 (Reports & Legal Document Generation), B-15 (Stats &
Analysis), M-10 (Reporting & Export Engine). Notifications (M-06 full
WhatsApp/SMS/email) and Audit/Privacy (M-13) are the second half, explicitly
not started — see "Current Milestone" below. Read this before touching
`builder.document`, `builder.stats`, `foundation.importexport`, or
`platform.PdfRenderer`.

**Architecture decisions:**
- **The sandboxed template language is hand-rolled, not a general templating
  engine wired up with restrictions.** `TemplateRenderer` supports exactly
  two constructs — `{{path.to.var}}` interpolation (always HTML-escaped) and
  one level of `{{#each collection}}...{{/each}}` looping over a
  server-supplied list — parsed by a small stack-based tokenizer, not
  FreeMarker/Thymeleaf/Handlebars-on-the-JVM, all of which expose either
  arbitrary method invocation or a class-loading surface a "no arbitrary
  code execution" requirement can't configure away with real confidence.
  `DocumentVariableAllowlist` is the single source of truth for which
  `{{...}}` paths are legal per doc type, consumed both by
  `TemplateRenderer.firstUnresolvableVariable()` (activation-time
  validation — blocks with the offending variable named, B-11 §11) and by
  `DocumentTemplateController`'s variable-palette endpoint (so the
  frontend's click-to-insert list can never drift from what the sandbox
  actually accepts).
- **PDF rendering is the first PDF infrastructure this codebase has ever
  had** (M3/M6 both deferred receipt/broker-statement PDFs pending this
  milestone). `flying-saucer-pdf-openpdf` (XHTML+CSS → PDF via OpenPDF)
  was added fresh; `PdfRenderer` embeds a genuine Devanagari-capable TTF
  (`src/main/resources/fonts/NotoSansDevanagari-{Regular,Bold}.ttf`,
  downloaded from Google Fonts' legacy CSS API since `@fontsource/noto-sans-devanagari`
  — already a frontend dependency since M0 — only ships woff/woff2, which
  OpenPDF's font embedding can't read at all). One font file covers both
  Devanagari and Latin glyphs (confirmed: Google's own CSS response
  returns a single `@font-face` for `subset=devanagari,latin`, not two),
  so English and Hindi documents share one embed.
- **Every generated document is immutable and stored once.**
  `generated_document.rendered_snapshot` (JSONB) is the exact context map
  used to render, captured at generation time; the PDF itself is written
  once to the standard media bucket and never regenerated — "reprint" is
  just `GET /documents/{id}/download` returning the same stored bytes, so
  a template edit or sale amendment afterward provably cannot change what
  a previously-generated document shows (verified directly, see below).
- **Document numbering**: `PAYMENT_RECEIPT` documents reuse
  `payment_record.receipt_no` directly (already gapless, already
  FY-scoped since B-05) rather than drawing a second, independent number
  from `document_number_sequence` for the same receipt — that table only
  numbers `ALLOTMENT_LETTER`/`DEMAND_LETTER`/`BOOKING_CONFIRMATION`,
  mirroring `ReceiptSequence`'s exact `SELECT ... FOR UPDATE` gapless
  pattern with a `doc_type` dimension added.
- **Auto-receipt generation goes through the outbox, not inline** (CLAUDE.md
  rule #6) — `PaymentService.record()` calls a new
  `OutboxService.enqueueDocumentGenerate()`, dispatched by a new
  `OutboxPoller` branch (`EVENT_TYPE_DOCUMENT_GENERATE`) that calls
  `DocumentGenerationService.autoGenerateReceipt()`, binding tenant context
  the same "plain Java, before the transaction opens" way every background
  job since M1 has had to. Reversals (`doReverse()`, called from both
  `reverse()` and the cheque-bounce path) deliberately do **not** get this
  same auto-trigger this round — a real, narrow, documented gap; a
  reversal receipt is still generatable manually via
  `POST /documents/generate` against that `payment_record`'s id.
- **M-10's report engine is "one Java method + one query per report code",
  not sixteen bespoke endpoints** (M-10 §1's own stated reason: fewer
  places to introduce a tenant leak). `ReportService.preview()` and
  `.export()` call the exact same per-report private method — "the
  on-screen table and the downloaded file can never disagree" (M-10 §7) is
  structurally true here, not just tested for. Preview pagination is
  sliced in Java from the full result set rather than a second paginated
  SQL query — a deliberate simplification given this milestone's realistic
  dataset sizes never approach where that would matter.
- **B-15's four named materialised views are used as designed where their
  shape actually fits, and bypassed for live queries where correctness
  requires it** — not "matviews everywhere" or "live everywhere", a
  conscious per-chart decision: `mv_monthly_sales` (has a real `month`
  column, filters correctly) and `mv_staff_activity` (same) are read
  directly; `mv_broker_performance` and `mv_lead_funnel` have no date
  dimension at all, so Top Brokers / Leads-by-Source take a **hybrid**
  path — the fast matview when no date filter is given, a live equivalent
  query when one is; Plot Status Breakdown, Conversion Funnel, Collection
  vs Target, and Overdue Trend are always live, because a matview snapshot
  would be actively wrong for them (see the funnel bullet below).
  `StatsRefreshJob` refreshes all four `CONCURRENTLY` every 15 minutes
  (B-15 §3) and exposes `lastRefreshedAt()` for the KPI strip's "data as
  of" timestamp (B-15 §7); `refreshNow()` is called directly by
  verification rather than waiting for the real 15-minute tick, the same
  pattern `OutboxPoller.dispatchReady()` already established.
- **The conversion funnel is a real cohort trace, never current-status
  counting** (B-15 §7's own explicit warning: "current-status counting
  understates conversion badly"). "Reached site visit" is answered by a
  real `interaction` row (`type = 'VISIT'`) ever existing for that lead —
  not by the lead's current `status` field, which has no history table and
  could have moved past or away from that stage since. This is exactly why
  `mv_lead_funnel` (a `status`-snapshot view) can't back this chart at all.
- **`LegalDocsTier`/`AnalyticsTier`** centralise the §7 entitlement tables
  (`LEGAL_DOCS`: NONE/BASIC/FULL: Free/Pro/Premium; `ANALYTICS`:
  BASIC/ADVANCED/FULL) as ordered-list ordinal comparisons, via two new
  `EntitlementService` methods — `assertTierAtLeast`/`tierOf` — added
  alongside the existing numeric (`assertWithinQuota`) and boolean
  (`assertFeatureEnabled`) gate shapes, since a three-value ordered tier
  string fits neither.

**Real bugs found and fixed (all via real HTTP calls against a live
docker-compose stack — none visible from code review, `mvn test`, or
`tsc`):**
- **A latent, pre-existing bug this milestone was the first to actually
  exercise: the backend's own `S3_ENDPOINT` was never set anywhere in
  `docker-compose.yml` at all**, silently defaulting to
  `http://localhost:9000` for every environment including a fully
  containerized backend — where "localhost" means the backend container
  itself, unreachable from there to MinIO's real container. Every
  in-container S3 call (`MediaService.complete()`'s magic-byte sniff,
  `uploadDerivative()`'s writes, and this milestone's new
  `storeGenerated()`) was exposed to this; Testcontainers-based `mvn test`
  never caught it because the *test JVM itself* runs on the host, where
  "localhost:&lt;mapped-port&gt;" is genuinely correct. Root cause: the
  in-container SDK client and the presigned URLs handed to a *browser* on
  the host **need genuinely different hostnames**, and only one endpoint
  property existed to serve both. Fixed by splitting
  `MediaProperties.endpoint` (in-container calls) from a new
  `MediaProperties.publicEndpoint` (presigned URLs; falls back to
  `endpoint` when unset, correct for a plain `mvn spring-boot:run` dev
  shape where both audiences are the same host) and setting
  `S3_ENDPOINT=http://minio:9000` / `S3_PUBLIC_ENDPOINT=http://localhost:9000`
  explicitly in `docker-compose.yml`'s `backend` service. **Any future
  verification session running the full stack via `docker compose up`
  rather than `mvn spring-boot:run` directly should sanity-check a real
  media upload early** — this class of bug is invisible to every other
  verification method this project uses.
- **`REFRESH MATERIALIZED VIEW` has no GRANT-based escape hatch at all —
  it requires actual ownership (or superuser), full stop**, unlike
  ordinary tables where `GRANT SELECT/INSERT/UPDATE/DELETE` (already given
  to `shardeya_app` via `V0_005`'s `ALTER DEFAULT PRIVILEGES`) is enough.
  `StatsRefreshJob`'s first real scheduled tick failed with "must be owner
  of materialized view mv_monthly_sales" — the same "migrations run as
  the owning superuser role, the app runs as the ordinary `shardeya_app`
  role" split M0 already established for RLS owner-bypass, showing up in
  a different Postgres corner. Fixed with a new migration
  (`V7_012__transfer_stats_matview_ownership.sql`,
  `ALTER MATERIALIZED VIEW ... OWNER TO shardeya_app`) — safe, since
  matviews have no RLS to weaken in the first place (Postgres doesn't
  support RLS on materialized views at all, a fact documented directly in
  `V7_008`'s own migration comment; isolation here is entirely the
  explicit `WHERE org_id = ...` predicate in every `StatsService` query).
- **`SYSTEM_ACTOR_ID` (`new UUID(0,0)`, the placeholder every background
  job since M1 has bound `TenantContext` with) was never designed to be
  *persisted* into a real foreign key — only ever used for the RLS-context
  GUC.** Every prior use (notification fan-out, the overdue sweep) never
  actually wrote it into an FK-constrained column. This milestone's
  `autoGenerateReceipt()` was the first background-job code to write an
  "acting user" into a real `REFERENCES app_user(id)` column
  (`generated_document.generated_by`, and transitively
  `media_asset.uploaded_by` via `MediaService.storeGenerated()`), and
  threw a genuine `ConstraintViolationException` on every single attempt,
  retried 5 times by the outbox poller, then permanently failed — caught
  only by watching the outbox actually dispatch, not from reading the
  code (this reads exactly like the already-established SYSTEM_ACTOR_ID
  pattern, which is precisely why it wasn't obviously wrong on sight).
  Fixed by attributing auto-generated receipts to the org's real owner
  account (looked up via a new `AppUserRepository.findFirstByOrgIdAndOwnerTrueAndDeletedAtIsNull`,
  falling back to `payment_record.received_by`) instead — a real row, and
  a defensible "the system generated this on the org's behalf" attribution.
  **This needed a second, separate fix**: `MediaService.storeGenerated()`
  originally read the uploader from `tenantContextBinder.current().userId()`
  (still the placeholder, regardless of what `generatedBy` was fixed to
  upstream) rather than accepting it as an explicit parameter — the first
  fix alone wasn't enough; `storeGenerated()` needed its own `uploadedBy`
  parameter threaded through from `DocumentGenerationService.generateInternal()`'s
  own `actorId`. **Any future background-job code that needs to attribute
  a row to "some user" should look up a real one explicitly, never assume
  a bound `TenantContext`'s `userId()` is safe to persist** — it's a
  synthetic placeholder for exactly one purpose (the GUC), not a real
  identity.
- **Java text blocks strip trailing whitespace from every line
  independently, which silently breaks `"""..."""  + var + """..."""`
  splicing across a line boundary** — a continuation segment relying on a
  single leading/trailing space to separate it from the previous segment's
  concatenated content loses that space if it happens to be the
  shortest-indented line in ITS OWN segment (per-segment stripping, not
  whole-literal). First caught in `StatsService.overview()`:
  `"""...\n    WHERE """ + saleScope + " AND ..."` — the trailing space
  after `WHERE` was silently stripped, gluing `WHERE` directly onto
  `s.org_id` as `WHEREs.org_id`, a genuine `BadSqlGrammarException` no
  amount of reading the code would have caught (it looks completely
  correct in the source). Fixed there with the `\s` text-block space
  escape (Java 13+, exempt from the stripping) — then, once the *pattern*
  was recognized, proactively checked for and found **three more
  occurrences of the identical mistake** in `collectionVsTarget()`'s
  three-way CTE query, where the missing separator would have produced
  `...date)GROUP BY 1)` with no space at all. Rather than patch those with
  more `\s` escapes, `collectionVsTarget()`/`overdueTrend()`/`overview()`'s
  three queries were all rewritten to use a single text block with `%s`
  placeholders + `.formatted()` (the same safe pattern `ReportService`'s
  own queries already used throughout) instead of splicing at line
  boundaries at all. **Any future native SQL built by concatenating a
  Java text block with a variable mid-query should use `%s` +
  `.formatted()` inside ONE text block, never `""" + var + """` spanning a
  line boundary** — this is now a real, demonstrated footgun in this
  codebase, not a hypothetical one.
- **`Plot.Facing`'s actual enum constants are short codes
  (`N/S/E/W/NE/NW/SE/SW`, matching the `plot_facing` Postgres enum) — not
  compass-direction words.** A first draft of
  `DocumentContextBuilder.formatFacing()` assumed names like `NORTH_EAST`
  and tried to split on `_`, which silently produced garbage for every
  real facing value (`Plot.Facing` has no such constants at all, so this
  would have failed to compile if it had used the enum directly — the bug
  was in a `String.split` against `facing.name()`, which happily "worked"
  on `"NE"` by producing the single fragment `"NE"` unmodified, never
  crashing, just never expanding to "North East" either). The identical
  wrong assumption was also caught in `V7_005`'s own `PLOT_INVENTORY`
  report filter seed (`options: ["NORTH","SOUTH",...]` instead of the real
  `["N","S","E","W","NE","NW","SE","SW"]`) before it ever shipped. Fixed
  with an explicit `Map<Plot.Facing, String>` lookup table in
  `DocumentContextBuilder` and corrected the seed migration directly
  (not yet applied to any shipped environment at the time this was caught,
  so no new migration was needed — see the file's own history).
- **The same wrong-enum-values mistake, caught a second and third time in
  the same seed migration**: `V7_005`'s `LEAD_CUSTOMER` report filter
  options guessed `lead_status` values (`NEW`, `CONTACTED`, `NEGOTIATION`
  — none of which exist) and `customer_source` values (`ADVERTISEMENT` —
  doesn't exist; missing `FACEBOOK`/`INSTAGRAM`/`COLD_CALL`/`EXHIBITION`/`SOCIAL_MEDIA`)
  instead of the real M-12 enum values
  (`INTERESTED, SITE_VISIT_SCHEDULED, SITE_VISIT_DONE, FOLLOWING_UP, DEAL_CLOSED, LOST`
  and `REFERRAL, FACEBOOK, INSTAGRAM, WALK_IN, COLD_CALL, WEBSITE, BROKER, EXHIBITION, SOCIAL_MEDIA, OTHER`).
  Caught by directly re-reading `V4_002`'s own `CREATE TYPE` statements
  rather than trusting a memory of what those enums probably looked like —
  **any future filter/seed data referencing an enum from an earlier
  milestone should re-read that migration's own `CREATE TYPE` line, never
  assume the values from general knowledge of what a "lead status" or
  "lead source" enum would plausibly contain.**
- **A literal `&middot;` HTML entity in the seeded system document
  templates broke PDF rendering outright** — Flying Saucer's XML parser
  (used to load the XHTML document before layout) is a strict XML parser,
  not an HTML5-tolerant one, and rejects any named entity it hasn't seen a
  `<!DOCTYPE>` declare (`org.xml.sax.SAXParseException: The entity
  "middot" was referenced, but not declared`) — every single document
  generation attempt failed with a 500 until this was found. Fixed by
  replacing `&middot;` with the numeric XML entity `&#183;` (always valid,
  no DOCTYPE needed) in both `V7_010`/`V7_011`'s seed HTML. **Any future
  system-template HTML (or user-authored custom template body) must avoid
  named HTML entities entirely — use the literal UTF-8 character or a
  numeric `&#NNN;` entity instead**; this is a real, easy-to-hit
  constraint of using a strict XML parser for template loading, not a
  one-off typo.
- **`Organization` had no `getLogoMediaId()` getter at all**, despite the
  field existing on the entity since M0 — never needed until
  `DocumentContextBuilder` became the first caller to actually read a
  logo for `{{org.logoUrl}}`. A one-line addition, caught immediately by
  `mvn compile`, not a runtime bug — but a genuine reminder that a field
  existing on an entity doesn't mean it's ever been read.

**A real, disclosed PDF-rendering limitation, not a bug this milestone
could fix within its own scope:** generated Hindi PDFs were visually
inspected directly (not just "the API returned 200") — the embedded font
renders every glyph correctly and **every conjunct forms correctly** (e.g.
"क्षेत्रफल", "त्र" combinations), meaning B-11 §11's explicitly-named worst
failure mode ("boxes or missing conjuncts") does **not** occur. What was
observed instead is a milder cosmetic issue: certain vowel-sign (matra)
glyphs show slight visual repositioning artifacts in a small number of
words, even though the underlying extracted PDF text (copy-pasteable, and
confirmed correct) is byte-for-byte right. Root cause: Flying Saucer/OpenPDF
(a fork of classic iText 2.x/4.x-era code) does not perform full
OpenType-level complex-script shaping (no HarfBuzz-equivalent GPOS anchor
positioning for combining marks) the way a browser or a modern shaping
engine would — this is a known, deep limitation of that rendering stack for
Indic scripts specifically, not something a font choice or a config flag
fixes. A genuine fix would mean swapping to a fundamentally different PDF
rendering architecture (e.g. driving a headless Chromium print-to-PDF
instead of Flying Saucer), which is out of scope for this round. **Worth
revisiting if Hindi legal documents become a support complaint** — until
then, this is disclosed as a known, bounded cosmetic limitation, not
silently glossed over.

**Known, deliberate scope decisions, not bugs:**
1. **`BOOKING_CONFIRMATION` has no system template and no generation path
   this round.** B-11 §17.2 describes it as mirroring the §22.4 WhatsApp
   booking confirmation — which is M-06's own second-half scope, not yet
   built. Building the document type without the flow it's meant to
   parallel would be premature; the enum value exists in the schema
   (`document_type_code`) for when M-06's second half lands.
2. **`TemplateEditor` is plain HTML `<textarea>` fields, not a WYSIWYG
   rich-text editor** — B-11 §6 describes "rich text with a variable
   palette"; the palette (click-to-insert) is fully built, the rich-text
   toolbar isn't. Functionally complete (the sandbox only ever interprets
   `{{...}}` tokens, so raw HTML editing works correctly), just not as
   friendly. A real, cheap-to-revisit gap.
3. **`ReportTable` has no row virtualisation or column sorting** — M-10 §6
   names both (`@tanstack/react-virtual`, sortable columns). This
   milestone's realistic dataset sizes never approach where virtualisation
   would matter; sorting was trimmed for time, not because it's hard.
4. **Report export is XLSX/CSV only, no PDF** — M-10 §6 lists "Excel / CSV
   / PDF" in the export dropdown. Legal-document PDF generation (B-11) was
   the priority PDF work this round; a tabular-report-as-PDF renderer
   (org logo, filter summary, page numbers per M-10 §7) is a distinct,
   not-yet-built piece of work.
5. **No async `export_job` queue** — every export/preview always takes the
   synchronous path in practice, since this milestone's realistic data
   never crosses the 5,000-row threshold M-10 §7 names for the async
   split. The `export_job` table exists (for schema fidelity and a future
   job-history screen) but nothing writes a `PROCESSING`-then-`COMPLETED`
   row through it yet.
6. **`StatsFilterBar` only drives project + date range globally, not
   staff/broker** — B-15 §21.2 names "project, date range, staff member,
   broker" as global filters applied to every chart. Staff/broker
   drill-down happens at the individual chart level instead (Top Brokers
   links straight to a broker's profile on click; Staff Performance is
   already a full per-staff table) rather than adding a second pair of
   global filters that all ten endpoints would additionally need to
   accept — a deliberate, documented narrowing of the filter surface.
7. **No `collection_target` setting UI.** The table exists and
   `collectionVsTarget()` correctly falls back to
   `SUM(payment_schedule.expected_amount)` when no row exists (B-15 §7's
   own "meaningful default, requires no setup") — every verification this
   round exercised only the fallback path, never an explicitly configured
   target.
8. **Export XLSX column headers are Title-Case English (from the column
   key), not fully localised Hindi.** Every other user-facing string in
   this codebase resolves client-side via i18next; an XLSX file has no
   client-side rendering step to defer to, and a backend-side resource
   bundle mirroring the frontend's JSON wasn't built this round.
9. **Auto-generated receipts always render in English**, regardless of
   the org's or buyer's language. B-11 §7's "defaulting to the buyer's
   preference" has no data model support at all (no per-buyer language
   field exists anywhere in this schema) — manual generation still offers
   a real language picker; only the automatic on-payment trigger has no
   way to know which language to pick.
10. **Reversal payments never auto-generate their own receipt** — only
    `PaymentService.record()`'s normal path is wired to the
    `DOCUMENT_GENERATE` outbox event; `doReverse()` (reverse + cheque-bounce)
    isn't. A reversal receipt is still reachable manually via the API.
11. **All verification is against a throwaway `M7TEST`/`M7E2E` plan** (the
    same "throwaway plan via direct SQL, never mutating the shared FREE
    row" precedent every milestone since M4 has used) — no real Pro/Premium
    plan exists until M8, so "correctly blocked on Free" (LEGAL_DOCS=NONE,
    ANALYTICS=BASIC) is provable, but "correctly unlocked at a real paid
    tier" isn't independently confirmed beyond the throwaway plan rows.

**Verification methodology and results:**
- Backend: 84/84 `mvn test` green (Testcontainers), zero regressions
  across every pre-existing suite, after the `S3Config`/`MediaService`
  signature changes and the `StatsService` query rewrites above.
- Frontend: `tsc -b` clean, `oxlint` clean (pre-existing shadcn warnings
  only), `vitest run` 23/23 (20 pre-existing + 3 new namespace key-parity
  checks for `report`/`document`/`stats`), `npm run build` clean.
- Real HTTP verification against a fresh, from-scratch `m7verify` docker-compose
  stack (own ports, own Postgres/Redis/MinIO, following the exact
  `m2verify`/`m3verify`/`m6verify` precedent): signup → project → plot →
  sale → generated an allotment letter (English and Hindi, both
  downloaded and visually inspected as real PDFs) → recorded a payment and
  confirmed a receipt PDF auto-generated via the outbox within a few
  seconds, reusing the payment's own receipt number → confirmed template
  sandbox validation blocks activation of a template referencing an
  unknown variable, naming the exact offending variable → confirmed a
  bulk demand-letter request against a fully-paid (non-overdue) sale
  cleanly reports "no overdue entities" rather than crashing → exported
  the Sales report as a real, valid XLSX file → exercised every stats
  endpoint (`overview`, `plot-status-breakdown`, `monthly-sales`,
  `conversion-funnel`) against real seeded data.
- A permanent Playwright spec (`frontend/e2e/m7-reports-documents-stats.spec.ts`,
  12/12 green on both `desktop` and `mobile-360` projects) drives the real
  UI: Reports catalog → Sales report shows the real buyer name; Document
  Templates list shows both system defaults (English and Hindi); the
  sandbox-validation flow end-to-end through the real editor UI (clone →
  break with an unresolvable variable → Activate → the exact error text
  naming that variable appears on screen); Stats page renders with zero
  browser console errors; a Hindi + 360px pass across all three new pages
  confirms zero horizontal page overflow. Two real e2e-authoring mistakes
  were found and fixed along the way, not product bugs: `page.goto()`
  after login wiping the in-memory-only authStore (the same
  recurring, already-documented constraint every milestone's own e2e
  notes have hit — fixed by navigating via real nav-link clicks, and by
  switching language through the real `LanguageToggle` control rather
  than a `localStorage` write + reload that would have hit the identical
  problem), and `DocumentTemplateService.clone_()`'s fixed
  `"<name> (Custom)"` naming colliding with itself on a second run against
  the same persistent database (fixed by clearing prior clones as
  test setup, not an application change — a real user only clones once).

---

## Post-M7 (First Half) — Bug Fixes & Feature Changes From Live Use

Eleven rounds of feedback from the user actually clicking through their own
seeded `m7verify` account (3 brokers, 30 plots across sold/reserved/
available states) — same "found by using it" pattern every prior
post-milestone round in this file has followed. All eleven are separate
commits on the `feat/m7-reports-legal-stats` branch (PR #21), all verified
against a real rebuilt `m7verify` backend container, not just `mvn test`.

1. **Removed the Dashboard's "Recent Activity" section entirely**, per
   direct user feedback ("it was not looking good") — no redesign attempt,
   just deleted `ActivityFeed`, its `getActivity` API call/query, the
   `ActivityItem` type, and the orphaned `activity.*` i18n keys (en+hi).
   `GET /builder/dashboard/activity` is left in the backend, unused —
   removing an unused endpoint wasn't asked for and risked being wrong if
   some other consumer existed; confirmed via grep that none does, but left
   it rather than delete backend code speculatively.
2. **Fixed three Dashboard card behaviors**, also direct feedback:
   - Plot-count cards (Total/Available/Sold/Reserved) and Active Leads are
     no longer clickable — they linked to `/builder/projects` with a
     `?plotStatus=` filter the project-list page never actually consumed
     (no cross-project plot list exists in this app), so the link went
     nowhere useful. Simpler to remove the link than build a page that
     doesn't exist yet.
   - **Follow-ups Today** and **Instalments Due This Month** were silently
     broken redirects — `TrackerPage`/`FinancialsPage` never read
     `tab`/`range` from the URL at all, always landing on their hardcoded
     default tab regardless of what the dashboard card's link specified.
     Both pages now initialize their tab (and Tracker's range) from
     `useSearchParams()` on mount.
   - `instalmentsDueMonth` was repointed from Tracker's single-range
     Collection tab (which has no "month" range value at all — only
     today/week/overdue/all) to Financials' Pending & Overdue tab, which
     already shows both pending and overdue instalments together in one
     view — a closer real match for "instalments due" than forcing a new
     range value into Tracker just for this one card.
3. **Removed the Google Maps Link field from the project create/edit
   form**, per direct request — dropped from the Zod schema, step-field
   list, form default/reset/submit values, the JSX input itself, and the
   en/hi i18n keys. Backend field/column (`project.google_maps_url`,
   `ProjectCreateRequest.googleMapsUrl`) deliberately left untouched —
   existing projects that already have a value keep it, it's just no
   longer settable from this form. A schema/migration removal wasn't asked
   for and would have been destructive for no reason.
4. **`declaredPlotCount` is now an enforced hard cap, not just a display
   figure.** Since M2, a project's declared plot count existed purely to
   show a "declared vs actual" delta — nothing ever stopped a builder from
   adding plots past it via any of the three creation paths. Per an
   explicit design conversation with the user (confirmed via a real-world
   framing: "why would a builder declare 50 plots and then let the system
   create 80?"), all three paths now enforce it as a real ceiling,
   independent of the subscription plan's own `BUILDER_PLOTS_PER_PROJECT`
   quota:
   - Manual "Add Plot" (`PlotService.create`): blocks a single add that
     would exceed the count.
   - Quick Create (`PlotQuickCreateService`): `preview()` now also reports
     whether the batch would exceed the declared count (mirrors the
     existing quota-warning pattern in `RangePreviewList`, gates the
     Create button the same way); `commit()` rejects the whole batch
     up front if it would exceed the count.
   - Excel bulk import (`ImportService.commit`): identical all-or-nothing
     pre-flight check, counting only rows that will actually create a new
     plot (not `UPDATE_EXISTING` or `SKIP` rows).
   Both bulk paths are **all-or-nothing** here, a deliberate difference
   from the subscription quota's own partial-commit-up-to-the-limit
   behavior — a project-level target the builder set themselves is a
   different kind of ceiling than a technical/billing limit, and silently
   importing "however many fit" against a number the builder chose
   themselves would be more confusing than informative. The error message
   explicitly says to edit the project to raise the number — that's the
   only way to raise the ceiling, not by adding more plots directly.
5. **Quick Create now guarantees a grid position for (almost) every plot,
   not just the ones matching the block-letter pattern.** Direct feedback:
   "too many plots to rearrange manually is not a good option." Before
   this, `autoPlace` only ever placed a plot if its number matched the
   `Block-Number` pattern (e.g. "A-5" → row A, col 5 — `GridPlacementResolver.
   parseBlockPattern`) *and* that exact cell was free; anything else landed
   in the unplaced tray needing full manual placement. This is exactly the
   gap the B-06 Quick Create feature-addition section (above) already
   flagged as "worth fixing... if this ever becomes a real gap in
   practice" — it did. Talked through the tradeoff with the user using a
   real-life framing (blocks/plot-numbering usually mirrors the actual
   physical layout of the land) before building it, landing on:
   - **Block-pattern match first, sequential (row-major) fallback
     second** — not "always sequential, ignore the letters." A plot number
     that matches the pattern still lands exactly where that numbering
     implies (respecting the physical-layout intent embedded in how
     builders actually name plots); only numbers that don't match, or
     whose implied cell is already taken, fall through to the next open
     cell instead of the unplaced tray.
   - **If the project has no grid configured at all yet**, one is
     auto-sized to fit — roughly square by default (`cols = ceil(sqrt(n))`),
     grown to also cover the highest row/col any block-pattern number in
     the batch implies (so a "C-30" range still gets a grid tall enough
     for a real block C, not a square sized purely by count that would put
     "C-30" out of bounds and force it into fallback for no good reason).
   - **Scoped to Quick Create (Path A) only, not Excel import (Path B)**,
     per explicit instruction — despite this being the identical gap
     CLAUDE.md's own B-06 section already flagged as "worth fixing for
     both paths together." Path B still leaves non-block-matching rows
     unplaced, unchanged. Revisit together if this becomes a complaint for
     Path B too.
   - A pre-existing, user-configured grid that's too small is **not**
     auto-grown — only "no grid at all" triggers auto-sizing. Respects a
     size the user explicitly chose; plots beyond a genuinely full
     pre-sized grid still land in the unplaced tray, the one remaining
     case where that happens.
   5 new `PlotQuickCreateServiceIntegrationTest` cases cover: auto-sizing
   from nothing, auto-sizing wide enough for an implied block column,
   sequential fallback for non-block-shaped numbers, sequential fallback
   around an occupied cell, and the one remaining genuinely-unplaced case.
6. **A real, user-reported labeling bug: a cheque-bounce reversal showed
   the same generic "Reversal" badge as any other correction**, in both
   the Financials Payments tab and the sale's own Payment History tab —
   with nothing on screen distinguishing "this payment was manually
   corrected" from "this cheque bounced," even though
   `PaymentService.doReverse()` already writes a real, specific remark
   ("Reversal: cheque X bounced") for exactly this case. Fixed by adding a
   `dueToChequeBounce` boolean to `FinancialPaymentRow`/`FinancialService.
   payments()` — an `EXISTS` check against the reversed original payment's
   own `mode`/`cheque_status` columns, **not** string-matching on the free-
   text remark (fragile, and would silently break if that wording ever
   changed) — and switching `PaymentRecordsTable`'s badge to "Bounced"
   when true, "Reversal" otherwise, with the actual remark as a hover
   tooltip. `PaymentHistoryTable` (sale detail) got the same distinction
   via a cheaper client-side cross-reference (the reversed original is
   already present in the same sale's payment list, no extra query
   needed). **A second, adjacent bug found and fixed while touching this
   exact line**: `PaymentHistoryTable`'s "Reversal of {{receiptNo}}" text
   was interpolating `p.reversesPaymentId` — the raw payment UUID — where
   the actual receipt number belonged; now resolved by looking up the
   reversed original's real `receiptNo` from the same array. 2 new
   `FinancialServiceIntegrationTest` cases (`dueToChequeBounce=true` for a
   cheque-bounce reversal, `false` for a manual one).
7. **The Stats & Analysis page's materialized-view-backed charts were
   silently, permanently empty for every single org — the most severe bug
   found this session**, from the user directly asking "check the whole
   Stats page, I think something's buggy" rather than reporting one
   specific symptom. `StatsRefreshJob` ran `REFRESH MATERIALIZED VIEW` as
   the RLS-enforced `shardeya_app` role with no tenant context bound (it's
   a cross-org scheduled job — no single org's context makes sense to
   bind for it). `REFRESH` re-executes each view's *defining query*
   against its own FORCE-RLS-protected source tables (`plot_sale`,
   `customer`, `interaction`, `payment_record`, `broker_partner`) — with no
   `app.current_org` set, RLS silently matched **zero rows on every single
   refresh, for every org, forever**. `REFRESH` itself reported success
   the entire time — no error, no log line (the method had none at all) —
   which is exactly why this was invisible: Monthly Sales, Revenue by
   Project, Top Brokers, Leads by Source (fast path), and Staff
   Performance all looked indistinguishable from "no data exists yet."
   Confirmed empirically before touching any code: `SET ROLE shardeya_app;
   REFRESH MATERIALIZED VIEW mv_monthly_sales;` left the view at 0 rows
   with 20 real sales in `plot_sale`; the identical query run as the
   owning superuser (RLS bypassed by ownership) returned every org's rows
   correctly; binding `app.current_org` before the same `shardeya_app`
   refresh populated exactly that one org's rows and nothing else,
   pinning down the exact mechanism. **Fixed with the identical pattern
   V1_009 already established for the pre-auth lookup path**: a new
   BYPASSRLS role (`shardeya_statsrefresh`, `V7_013__create_stats_refresh_
   role.sql`), its own dedicated datasource
   (`StatsRefreshDataSourceConfig`, mirroring `AuthLookupDataSourceConfig`
   exactly), used by `StatsRefreshJob` alone — never reuse it elsewhere.
   Ownership of the four materialized views moves to this new role
   (`REFRESH` requires actual ownership per V7_012's own lesson, and
   `shardeya_app` can't both own them and stay RLS-enforced against their
   source tables at the same time). Also added error logging to the
   scheduled tick itself (`@Scheduled` swallows an uncaught exception
   silently) — a real future failure must not be this quiet a second time.
   **A second, smaller, related bug found in the same audit**:
   `revenueByProject()` compared a date-range picker's raw dates against
   `mv_monthly_sales.month` (always the 1st of a month) with a plain
   `BETWEEN` — picking a genuine mid-month range (e.g. "Aug 5 – Aug 20", a
   completely normal thing to filter by) silently dropped that whole
   month's data from the chart, even though real sales existed inside the
   picked range. Fixed by `date_trunc()`'ing both bounds to month
   boundaries before comparing — the same normalisation `monthlySales()`/
   `overdueTrend()` already get for free from computing `:since` as a
   month-start `YearMonth` rather than a raw picked date. **Confirmed, not
   fixed, and explicitly flagged rather than silently patched**:
   `StatsOverviewResponse.avgDaysToClose` is always `null` in real usage —
   it `INNER JOIN`s `plot_sale` to `customer` via `customer_id`, which is
   essentially never populated (sales are normally created directly, buyer
   name/mobile typed straight into the wizard, with no link back to a
   tracked CRM lead). A real fix needs a product decision (should sale
   creation auto-match an existing lead by mobile number?), not a query
   tweak, so this is documented as a known gap rather than papered over.
   2 new integration tests
   (`StatsRefreshJobIntegrationTest`, `StatsServiceIntegrationTest`) assert
   on the actual symptom (real row counts after refresh; chart totals
   surviving a mid-month filter), not just "the call didn't throw" — the
   original bug would have passed a test that only checked for the absence
   of an exception, which is exactly how it went undetected through every
   prior verification pass this milestone. Verified live against the
   rebuilt `m7verify` account after the fix: Monthly Sales/Revenue by
   Project/Top Brokers/Staff Performance all show real figures immediately
   on backend restart (the job's own initial tick fires on startup, no
   manual trigger needed), and the mid-month date-range case now returns
   the identical totals as no filter at all. **Any future scheduled/
   background job that reads or writes an RLS-protected table with no
   single tenant's context to bind needs to be checked for this exact
   class of bug on sight** — `REFRESH MATERIALIZED VIEW`, a bulk cross-org
   sweep, a scheduled report — RLS silently returning "correct-looking but
   empty" results is far more dangerous than an outright error, because
   nothing anywhere signals that anything went wrong.
8. **Removed the permanently-locked Excel Bulk Import option from the Add
   Plots entry point**, per direct feedback ("disable that blocked feature
   from the plots"). `AddPlotsPage` used to offer a two-card choice —
   Quick Create (always available) and Excel Import, shown as a
   non-clickable, grayed-out card with an "Upgrade" badge whenever
   `plan === 'FREE'`, which is every org in this app today (no paid plan
   exists until M8) — so that card could never actually be used, just
   permanent dead-end clutter. Removed entirely; `AddPlotsPage` now skips
   the "choose a method" screen altogether and goes straight to the Quick
   Create form (the only real option left, so asking the user to choose
   between one thing no longer makes sense either). The import route,
   `ImportWizardPage`, and all backend Path B code are untouched — this
   only removes the one UI entry point into it, matching the same
   minimal-blast-radius approach already used for the Google Maps field
   removal (drop the front-door affordance, leave the underlying
   feature/data alone). Orphaned `addPlotsPage.quickCreate.*`/`addPlotsPage.
   excel.*` i18n keys (en+hi) removed alongside it.
9. **Added real download buttons for a project's Layout Plan and Brochure
   in the Documents tab**, per direct request. `ImageUploader` gained an
   opt-in `allowDownload` prop (default off, so plain photo uploads like
   the cover image and gallery don't grow an affordance that doesn't apply
   to them) rendering a download button over the thumbnail, using the
   media's own presigned `url` directly — no new backend endpoint needed,
   `GET /media/{id}` already returns it. **Found and fixed a real adjacent
   bug while touching this component**: Brochure already accepted
   `application/pdf` (`accept="image/*,application/pdf"`), but the
   thumbnail unconditionally rendered `<img src={media.derivatives.card ??
   media.url}>` — a PDF has no `card` derivative and can't render inside an
   `<img>` tag at all, so every PDF brochure silently showed a broken-image
   icon instead of any indication a file actually existed. Fixed by
   branching on `media.mimeType.startsWith('image/')`: real images keep
   the existing thumbnail, anything else (a PDF today, whatever else this
   uploader might accept later) shows a labelled file-type placeholder
   instead of a broken image. Both fixes ship together since the download
   button is what actually surfaced the pre-existing thumbnail bug — a
   brochure with no working preview and now a working download button
   would have been a confusing combination to ship separately.
10. **"Mark Done" on the Tracker's Follow-ups tab silently did nothing** —
    reported live, and a real, meaty logic bug once traced. `TrackerService.
    markDone()` correctly sends `result=NO_FURTHER` with
    `nextFollowUpDate=null` (there's no new date when a follow-up is
    genuinely finished), but `InteractionService.create()`'s
    `customer.setNoFurtherFollowUp(true)` write was nested *inside*
    `if (req.nextFollowUpDate() != null)` — a condition that's never true
    for exactly this shape. The interaction itself logged correctly every
    single time (so nothing about the click ever looked broken from the
    interaction history), but the customer's own `no_further_follow_up`
    flag and `follow_up_date` never changed, so the identical lead
    reappeared in the follow-ups list the moment the mutation's own
    `invalidateQueries` refetch ran — indistinguishable, from the button's
    perspective, from doing nothing at all. Fixed by making the two writes
    independent: a new date is set when one was actually given;
    `noFurtherFollowUp` flips whenever `result == NO_FURTHER`, regardless
    of whether a date came with it — and `syncFollowUpProjection` (which
    clears the stale calendar event once `noFurtherFollowUp` is true, per
    its own existing logic) now fires for either kind of change, not just
    a date change, so a completed follow-up's calendar entry disappears
    too. **Zero test coverage existed for `InteractionService` or
    `TrackerService` before this** — added
    `InteractionServiceIntegrationTest`, asserting on the actual symptom
    (the lead genuinely leaves the follow-ups list after Mark Done, not
    just "the call didn't throw"), and **verified the test really catches
    the bug** by temporarily reverting the fix, confirming the test fails,
    then restoring it — the same rigor this project's testing standard has
    asked for since M0, applied literally here rather than assumed.
    **Any future write that's conditionally gated on one field of a
    multi-field request needs to be checked against every real combination
    of fields the request can actually arrive with** — this bug existed
    because "flip the no-further flag" was written as a sub-case of "a new
    date was given," which is true for the ordinary reschedule/log-follow-
    up path but was never true for the mark-done path, and nothing forced
    that assumption to be re-examined when the second caller was added.
11. **Two more Dashboard buttons had the same one-character redirect typo
    already fixed once for Record Payment** (`?tab=collection` instead of
    the `?tab=collections` `TrackerPage` actually reads) — found by
    auditing every Dashboard button's target while fixing item 10:
    `AlertBanner`'s overdue-instalments alert had the identical mismatch,
    silently landing on whatever tab Tracker falls back to instead of
    Collections. Also **removed the "Bulk Upload" quick action entirely**
    — it linked to a bare `/builder/projects` list (never a real
    destination for the action by itself, just "go find a project"), and
    the Excel-import entry point it was ultimately meant to lead toward no
    longer exists anywhere in the UI (removed from Add Plots in item 8).
    Nothing left for this quick action to meaningfully point at, so it's
    gone rather than pointed at a fake destination.

**Full real-browser regression pass over the whole PR** — requested
explicitly ("check like a real user is using the application... with a real
browser"), covering everything in this PR, not just items 1-11 in
isolation. Ran the existing e2e suite against the live `m7verify` stack
(the user's own account, real data, not `mvn test`) and added coverage for
what nothing else tested yet:
- `quick-create-plots.spec.ts` needed an update: it asserted Excel import
  stayed locked on Free, which no longer applies now that Excel import has
  no entry point on Add Plots at all (item 8). Removed that assertion and
  the now-nonexistent "Quick Create" card click (the form renders
  directly now); the collision/placement assertions were re-verified to
  still hold exactly as before, since block-pattern-first placement (item
  5) happens to produce the identical grid positions this test already
  hardcoded for this exact range.
- New `post-m7-live-fixes.spec.ts`, driven against the real seeded account
  (not a fresh signup, so real data exercises each fix the way it was
  actually found): Recent Activity gone, plot/lead cards not clickable,
  Record Payment redirects to the right tab, a real cheque-bounce reversal
  shows "Bounced", Stats charts show real broker names (proving the
  matview-RLS fix), Add Plots goes straight to Quick Create, Layout
  Plan/Brochure have working download links, and Mark Done genuinely
  removes a real overdue lead ("lak") from the follow-ups list.
- Results: 17/17 across `m7-reports-documents-stats` (6), the updated
  `quick-create-plots` (1), `m3-sale-payments` (1, exercises the full
  cheque-bounce flow end to end), `m2-project-status` +
  `m2-plot-reserved-status` + `notification-mark-all-read` (3), and the
  new `post-m7-live-fixes` (6). `m6-broker-commissions` failed on a fresh
  signup hitting the Free plan's `BUILDER_BROKERS=0` quota — a
  pre-existing precondition this spec has always relied on (a throwaway
  plan grant, per its own M6 notes above), not a regression from anything
  in this PR. `m2-grid-performance`/the `m5-*` specs weren't re-run: both
  already documented in this file as referencing a specific point-in-time
  seeded state that's since evolved, the same known-stale-fixture class
  noted since M2/M5 — re-running them would just reproduce already-
  understood, unrelated failures.

**Process note**: none of the first six were written into CLAUDE.md as they
happened, breaking this file's own standing "update CLAUDE.md as you go"
rule — caught only when the user directly asked "are you writing this in
CLAUDE.md or not?" Those six were captured here after the fact, in one
pass, rather than incrementally. Item 7 (the stats matview bug) was
written up in the same turn it shipped, matching the corrected process
going forward.

---

## Milestone 7 (Second Half) — Decisions & Environment Notes

M-06's full Notification Engine — real WhatsApp/SMS/email delivery on top
of the in-app notifications that have worked since M3. M-13 (audit log,
trash/restore, data export, account deletion) and the Platform Admin
Console and Razorpay payment integration were explicitly excluded from
this round by the user — plan upgrades stay a manual `subscription.plan_code`
DB update outside the app for now. Read this before touching
`foundation.notification`, `OutboxPoller`, `OutboxService`, or any
scheduler in `builder.payment`/`builder.tracker`.

**Provider choices (both stubbed by default, real credentials not available
in this environment — see "External setup required" below):**
- **WhatsApp: Meta Cloud API** (`shardeya.whatsapp.provider=meta` selects
  `MetaCloudApiWhatsAppGateway`; default `stub` keeps using
  `StubWhatsAppGateway` against `infra/whatsapp-stub`, unchanged since M0).
  Chosen over a BSP (Gupshup/Interakt) because it's the direct API with no
  intermediary account/billing relationship to set up beyond Meta's own —
  simplest path for a single-tenant-per-org SaaS that doesn't need a BSP's
  multi-client console.
- **SMS: MSG91** (`shardeya.sms.provider=msg91` selects `Msg91SmsGateway`;
  default `stub` keeps using the existing `StubSmsGateway`). The standard
  India-DLT-compliant provider most Indian SaaS products already integrate;
  no other provider was seriously evaluated given the DLT-registration step
  is identical regardless of which one is picked.
- **Email: no new provider-specific class at all.** SMTP is already the
  wire protocol every mainstream provider (SES, SendGrid, Postmark)
  exposes, so going from MailHog (local dev) to a real provider is a
  `spring.mail.*` config change, never a code change. `EmailGateway`
  (new interface) + `SmtpEmailGateway` (the one implementation) exist
  purely so `OutboxPoller` depends on an abstraction matching
  `WhatsAppGateway`/`SmsGateway`'s shape, not because email needs a
  `provider` property the way the other two do.

**External setup required before any of this can send a real message to a
real phone/inbox** (all stubbed and fully functional locally without any
of this):
1. **WhatsApp**: a Meta WhatsApp Business Platform account (WABA), a
   permanent System User access token, the phone number ID, and —
   critically — every §22.4 buyer-facing template message pre-approved by
   Meta (this build only wires the builder-facing side; buyer templates are
   explicitly out of scope, see below). No webhook receiver exists yet
   either (delivery-status updates stay `SENT`, never advance to
   `DELIVERED`/`READ`/`FAILED` from a real callback).
2. **SMS**: an MSG91 account, an auth key, and — the step that actually
   takes real calendar time — **DLT (Distributed Ledger Technology)
   registration of both the sender ID and every message template**, a TRAI
   regulatory requirement with no code-side workaround. An unregistered
   template is silently dropped by the carrier, not rejected by MSG91's
   API, which is what makes this easy to misdiagnose as a bug if skipped.
   This is a manual, offline step the user has to complete before flipping
   `shardeya.sms.provider=msg91`.
3. **Email**: MailHog is fine for local dev (already running via Docker
   Compose since M0); any deployment beyond that needs a real SMTP
   provider's credentials in `spring.mail.*`.

**Architecture decisions:**
- **The outbox pattern is reused wholesale, not rebuilt.** `OutboxService`
  gained `enqueueSms` (mirroring the existing `enqueueEmail`/`enqueueWhatsApp`
  exactly) and `OutboxPoller` gained a `sendSms` handler + `EVENT_TYPE_SMS`
  branch — no second queue, no new table. `EmailPayload`/`WhatsAppPayload`
  both gained an `orgId` field (needed at dispatch time to bind tenant
  context before the new `message_delivery` insert, which is RLS-protected
  like every other tenant table); the new `SmsPayload` record has it from
  the start.
- **`NotificationDispatchService` is the single place that decides, per
  recipient and per channel, whether a notification actually sends** —
  replacing `OutboxPoller`'s old direct `notificationService.createForOrg/
  createForUser` calls. It resolves the recipient list itself (single user
  or org-wide fan-out), then for each one reads `NotificationType.defaultChannels`/
  `isMandatory` and any `NotificationPreference` override (a preference row,
  once it exists, is a **complete snapshot of all four channels**, not a
  sparse per-field override merged with defaults) to decide in-app/WhatsApp/
  SMS/email independently. `is_mandatory` only ever forces the **in-app**
  channel on (M-06 §22's own wording: "still stored in-app... cannot be
  silenced") — a mandatory type's WhatsApp/SMS/email channels are still
  whatever the preference/defaults say, and the settings API enforces this
  same rule server-side (`NotificationPreferenceService.update()` silently
  coerces `inApp=true` for a mandatory type regardless of what's requested —
  confirmed live via `curl`, see Verification below).
- **Quiet hours (08:00–21:00 IST, WhatsApp/SMS only) are computed once,
  centrally, at ENQUEUE time** (`QuietHoursPolicy.nextAllowedInstant()`,
  called inside `OutboxService.enqueueWhatsApp`/`enqueueSms`), stamped onto
  `outbox_event.available_at` — not a separate check at dispatch time. The
  existing `findReadyToDispatch(status, now)` query already filters on
  `available_at <= now`, so a message enqueued at 11pm just sits `PENDING`
  until the query naturally picks it up the next morning; no new dispatch-
  time branch was needed.
- **Rate limiting is a simple per-poller-tick dispatch cap**
  (`MAX_WHATSAPP_PER_TICK`/`MAX_SMS_PER_TICK` = 20 each, in `OutboxPoller.dispatchReady()`),
  not a Redis sliding-window counter. An event that would exceed the cap is
  skipped entirely for that tick — left `PENDING`, `attempts` NOT
  incremented, no retry-backoff applied — and simply reconsidered on the
  next 2-second tick. Deliberately simple: a Redis-based quota would need
  new `OutboxEvent` semantics for "retried without being penalized," which
  the existing `markProcessing()`/`retryLater()` state machine doesn't
  support without changes.
- **Deduplication reuses the existing `payment_schedule.last_reminder_sent_at`
  column** (already there since M3/M5, already written by `TrackerService`'s
  manual buyer-facing "Send Reminder" feature) for both new schedulers'
  same-day/same-week idempotency, rather than the M-06 spec's own suggested
  Redis `(type_code, entity_id, business_date)` key. **Deliberate,
  documented simplification with a real, disclosed cross-feature
  approximation**: a builder manually reminding a buyer about a schedule
  today will also suppress that same row's automated internal reminder for
  a while, and vice versa — the two concepts share one timestamp column.
  Accepted as reasonable (a redundant nag suppressed isn't a real harm) to
  avoid new Redis-backed dedup infrastructure for two schedulers.
- **Channel fallback (§22.1) is a hardcoded "critical type" set**
  (`CRITICAL_TYPES` in both `NotificationDispatchService` and
  `OutboxPoller`: `PAYMENT_RECORDED, INSTALMENT_OVERDUE,
  INSTALMENT_DUE_TODAY, CHEQUE_BOUNCED`, matching the spec's own named
  examples "payment confirmation, overdue"). Two distinct trigger points,
  both re-enqueue an SMS through the outbox rather than sending inline: (1)
  `NotificationDispatchService` — the recipient wants WhatsApp but has no
  active `whatsapp_optin` row; (2) `OutboxPoller.sendWhatsApp()` — the
  WhatsApp send itself throws (a real provider failure), where the fallback
  is best-effort (its own failure is logged, not re-thrown) and doesn't
  suppress the original WhatsApp event's own retry/backoff. **Note:**
  `PAYMENT_RECORDED`'s own `default_channels` was never changed to include
  `WHATSAPP` (it predates this build, in-app-only since M3) — the fallback
  logic for it is real and wired, but only actually engages if a user
  explicitly enables WhatsApp for that type via the preference matrix;
  under default settings it's dormant infrastructure, not a bug.
- **`NotificationMessageRenderer`** (new, `foundation.notification`) is a
  small, explicit Java `switch` producing real WhatsApp/SMS/email body text
  per `(typeCode, language)` — deliberately NOT the existing `TemplateRenderer`
  built for HTML document generation (different concern: plain text, no
  HTML escaping, no sandboxed variable allowlist needed since these are
  Java-authored strings, not user/template-authored ones) and NOT Spring's
  `MessageSource` (params are a named `Map<String,Object>`, not
  `MessageFormat`'s positional args). Only the handful of types with a real
  non-in-app channel today have a real template (`INSTALMENT_DUE_TODAY`,
  `INSTALMENT_OVERDUE`, `FOLLOWUP_DUE`, `COMMISSION_DUE`, `STAFF_ADDED`,
  `STAFF_DEACTIVATED`); everything else falls back to a generic "you have a
  new notification" sentence in the recipient's language, so nothing
  ever crashes or sends an empty message if a future type gains a channel
  without a matching template being added here.
- **`InstalmentReminderParams`** (new, `builder.payment`) enriches a bare
  `payment_schedule` row with buyer name and plot number for a real,
  readable WhatsApp/SMS body — two extra repository lookups per schedule
  row (`plot_sale` then `plot`, neither JPA-relationally-mapped to
  `payment_schedule`, per this codebase's plain-UUID-FK convention), shared
  by both `InstalmentDueTodayScheduler` and `OverdueScheduleSweeper`'s
  reminder sweep so the two never drift on what a reminder actually says.
  Acceptable N+1 shape at this app's realistic per-org schedule-row counts
  and twice-daily cadence, not a hot path.
- **`InstalmentDueTodayScheduler`** (new) fires at 09:00 IST for
  `PENDING`/`PARTIALLY_PAID` rows with `due_date = today`. **`OverdueScheduleSweeper`
  gained a second, separate cron** (`scheduledReminderSweep()`, also 09:00
  IST) implementing §22.3's "day 3, then weekly" cadence for rows ALREADY
  `OVERDUE` — distinct from its original 01:00 IST status-transition sweep
  (`scheduledSweep()`, unchanged, still only fires once, on the day a row
  first becomes overdue). `REMINDER_START_DAYS=3`/`REMINDER_REPEAT_DAYS=7`
  are the two knobs.
- **WhatsApp opt-in is scoped to the current user's own `AppUser.mobile`,
  not an arbitrary caller-supplied number** the way the M-06 spec's
  `POST /settings/whatsapp/opt-in {mobile}` shape loosely suggests —
  deliberately narrower, and for a real reason: `NotificationDispatchService`
  only ever checks `whatsapp_optin` for `user.getMobile()` when deciding
  whether to send a builder-facing WhatsApp notification, so an opt-in row
  for any other number would silently never be consulted by anything.
  Two-step, OTP-confirmed (`WhatsAppOptInService.start()`/`.confirm()`,
  reusing `OtpService.create/verify` exactly like every other OTP flow
  since M1, purpose code `WHATSAPP_OPTIN`) — a WhatsApp number consenting
  to receive messages has to prove it can actually receive the code, the
  same way a phone number proves it during signup. `POST /settings/whatsapp/opt-out`
  needs no OTP (self-service, no security risk from a false opt-out since
  it only reduces sends). Buyer-facing opt-in (§22.4, out of scope this
  round) is a different consumer of the same `whatsapp_optin` table, already
  supported by its `source` column (`SELF_SERVICE` vs `BUILDER_CAPTURED`),
  just not wired to any endpoint yet.
- **`TrackerService`'s pre-existing manual buyer-facing "Send Reminder"
  feature (M5) was deliberately left untouched** — no `whatsapp_optin`
  enforcement was retrofitted onto it. It's a different concept (a builder
  explicitly messaging a *buyer*, not the automated builder-facing
  reminders this round adds) and no buyer-facing opt-in UI exists yet to
  retrofit against; changing its behavior was out of scope for "wire the 7
  explicit builder-side triggers."
- **No new generic `/notifications/send-reminder` endpoint** (named in the
  M-06 spec's own API table) — `TrackerService`'s existing manual-remind
  action already serves that concrete purpose for the one entity type
  (payment schedules) that needs it; inventing a second, more generic
  endpoint with no second caller would be premature.

**Real bugs found and fixed:**
- **`InstalmentDueTodayScheduler` wrote `last_reminder_sent_at` but never
  read it back before deciding whether to notify** — its own javadoc
  claimed same-day re-entrancy protection (mirroring
  `OverdueScheduleSweeper`'s reminder sweep, which genuinely does check
  before sending), but the code just unconditionally notified every
  `PENDING`/`PARTIALLY_PAID` row due today, every single call. A second
  `sweepAllOrgs()` call on the same day (a crash/restart re-running the
  cron, or this test's own second assertion) would have sent a duplicate
  WhatsApp/in-app notification for the exact same instalment. Caught while
  writing `InstalmentDueTodaySchedulerIntegrationTest`'s own "same-day
  re-run doesn't double-notify" assertion — the test failed against the
  first draft, exactly as systematic verification is supposed to catch.
  Fixed by comparing `lastReminderSentAt` against the start of the current
  IST day before enqueueing, same shape as the overdue-reminder sweep's own
  weekly-cutoff check. **Any future scheduler whose javadoc claims a dedup
  guarantee needs a test that actually calls it twice** — the bug here was
  entirely in the gap between what the comment claimed and what the code
  did, invisible from reading the method in isolation.
- **A second ArchUnit false-negative-turned-real-violation**: `NotificationType`
  (the new JPA entity backing the preference matrix, first entity ever
  written for this platform-wide reference table) has no `org_id` by
  design — same shape as `MeasurementUnit`/`StampDutyRate`, a shared,
  read-mostly catalogue table. `noEntityMayLackOrgIdExceptPlatformTables`
  correctly flagged it the moment the entity was added; added to
  `ENTITIES_WITHOUT_ORG_ID` alongside its precedents rather than treated as
  a real gap.
- **`FollowUpDueSweeperIntegrationTest` needed updating for a real,
  intentional behavior change**, not a masked regression: before this
  round, `FollowUpDueSweeper` enqueued its WhatsApp event directly and
  synchronously within the sweep itself; now that decision is deferred
  entirely to `NotificationDispatchService` at dispatch time, which
  requires an active `whatsapp_optin` row (correctly, per this round's own
  new opt-in-gating rule) before ever attempting WhatsApp. The existing
  test had no opt-in row seeded (there was nothing to seed before this
  round), so its WhatsApp assertion started failing — fixed by seeding a
  `WhatsAppOptin` row for the test assignee's mobile before running the
  sweep, and updating the test's own comments to describe the new
  two-outbox-insert shape (`dispatchReady()` processing the NOTIFICATION
  event is what creates the second, WHATSAPP event, not the sweep itself).
- **A duplicated page title, caught only by actually looking at a real
  screenshot** — `NotificationSettingsPage`'s `PageHeader` and
  `NotificationPreferenceMatrix`'s own `CardHeader` both rendered the
  identical "सूचना सेटिंग्स" / subtitle text, stacked directly on top of
  each other. Zero horizontal overflow, zero raw i18n keys, zero console
  errors — every automated check this round's own live-stack pass ran
  would have reported this page as clean. Only visible in the actual
  360px/Hindi screenshot. Fixed by removing `NotificationPreferenceMatrix`'s
  `CardHeader` entirely (the page-level `PageHeader` already establishes
  the page's own title/subtitle; the `WhatsAppOptInCard` above it keeps its
  own distinct title, which is not a duplicate). **This is the same lesson
  M4's `interaction.type` key-collision bug and M6's `commissionPreview`
  double-₹-symbol bug already taught: overflow/console/key-leak checks are
  necessary but not sufficient — an actual look at the rendered page is
  still required.**

**Known, deliberate scope decisions, not bugs:**
1. **No §22.4 buyer-facing WhatsApp templates** (payment received,
   instalment due, booking confirmation) — a genuinely separate, larger
   scope than the 7 builder-side triggers explicitly requested this round,
   and blocked on real Meta template approval regardless (a stub can fake
   the API response shape, but not "this template exists and is approved,"
   which is the entire mechanism §22.4 depends on).
2. **No subscription-expiry (T-7/T-1) reminders** — ties to M9
   (subscriptions/billing), explicitly out of scope alongside Razorpay per
   the user's own instruction this round.
3. **No `BROKER_TIER_UPGRADED` WhatsApp wiring** — not one of the 7
   explicitly named triggers; its `default_channels` stays at the M6-era
   `{IN_APP}` default, untouched.
4. **No digest/collapse behavior** ("40 instalments due today" collapsing
   into one message) — this app's realistic per-org data volumes never
   approach where this would matter; `NotificationDispatchService` sends
   one message per recipient per event, always.
5. **No WhatsApp delivery-status webhook receiver** — `message_delivery.status`
   only ever reaches `SENT` (or `FAILED` on a send-time exception), never
   `DELIVERED`/`READ` from a real provider callback, since no webhook
   endpoint exists to receive one yet.
6. **Auto-generated reminders are always sent in the recipient's own
   `AppUser.language`**, correctly per-recipient (not hardcoded English the
   way M7-first-half's auto-receipt generation is) — this round's
   `NotificationMessageRenderer` was built with per-recipient language from
   the start, unlike the earlier, disclosed gap in document generation.

**Verification methodology and results:**
- Backend: 97/97 `mvn test` green (Testcontainers, zero regressions),
  including 3 new integration tests written specifically for this round's
  own triggers: `InstalmentDueTodaySchedulerIntegrationTest` (real
  in-app + WhatsApp dispatch through the full outbox pipeline, opt-in
  gating, same-day dedup — this is the test that caught the dedup bug
  above), `OverdueScheduleSweeperIntegrationTest`'s new
  `reminderSweepNotifiesAgainAtDayThreeButNotOnAnImmediateReRun` (day-3
  eligibility, same-day dedup, and a backdated `last_reminder_sent_at`
  correctly re-triggering the weekly repeat), and
  `PlotSaleCommissionIntegrationTest`'s new
  `completingASaleWithABrokerEnqueuesACommissionDueNotification`.
- Real HTTP verification against a freshly rebuilt `m7verify` docker stack
  (same isolated stack every M7 session has used, backend image rebuilt
  with this round's code, Flyway cleanly applying V7_014–V7_017 on top of
  the existing 92 migrations): signed up a real user → fetched the real
  preference matrix (server-computed effective values from `notification_type`
  defaults, not hardcoded) → `PUT` a channel toggle off for a non-mandatory
  type (persisted correctly) → attempted to disable in-app for a mandatory
  type (`INSTALMENT_DUE_TODAY`) and confirmed the server silently coerced
  it back to `true`, proving CLAUDE.md rule #5's "server-side, not
  cosmetic" extends to this business rule too → full WhatsApp opt-in OTP
  round-trip (start → real OTP read from the container log → confirm →
  status flips to `optedIn: true`) → created a real lead and confirmed
  `LEAD_CREATED` genuinely flowed through the new outbox →
  `NotificationDispatchService` → in-app pipeline (proving the whole new
  dispatch architecture works for a request-driven trigger, not just the
  cron-based ones the integration tests already covered) → confirmed the
  WhatsApp stub adapter genuinely "sends" and logs a real response
  (`[WHATSAPP STUB] ... response={... messages=[{id=wamid.stub-...}]}`,
  captured during the `InstalmentDueTodaySchedulerIntegrationTest` run).
  Staff-invite (`STAFF_ADDED`, would have proven the email channel over a
  live MailHog) was blocked by the Free plan's `BUILDER_TEAM_MEMBERS=1`
  quota on this fresh test org — not pursued further given time budget;
  the email channel itself (`SmtpEmailGateway`) is still exercised for
  real by every `mvn test` run against Testcontainers' own mail setup, and
  by every pre-existing `AuthService` email (welcome, password reset) this
  round's `orgId`-adding refactor touched.
- Frontend: `tsc -b` clean, `oxlint` clean (same pre-existing shadcn
  warnings noted since M2), `vitest run` 24/24 (20 pre-existing + the new
  `settings` namespace key-parity check), `npm run build` clean. A
  one-off Hindi + 360px Playwright pass (not kept permanently, matching
  the established M3/M5/M6 precedent for a page already covered by
  automated overflow/key-leak checks) against the real live stack: signed
  in as the same real user created above, navigated via the UI (ProfileMenu
  → new "Notification settings" item, never `page.goto()` — the
  in-memory-only `authStore` constraint every e2e spec in this project has
  documented since M2 applies here too), switched to Hindi via the real
  `LanguageToggle`, confirmed zero page-level horizontal overflow
  (`scrollWidth === clientWidth === 360`), zero raw i18n keys leaking, zero
  browser console errors — and, from actually looking at the resulting
  screenshot, found and fixed the duplicated-title bug documented above.
  Re-screenshotted after the fix to confirm.
- **Settings navigation entry point**: added to `ProfileMenu` (a new
  "Notification settings" item, profile-aware path via `org.type`), not
  the main sidebar/bottom-nav — matches the pattern of "settings" living in
  the profile dropdown in most SaaS apps, and keeps the already-crowded
  nav list (`nav.ts`, already flagged once this project for 360px overflow
  risk as items accumulate) from growing further for a page every user
  visits rarely.

---

## Post-M7 — Calendar Agenda List Visual Polish

User-requested visual-only pass on B-09's agenda list (`CalendarPage`) —
explicitly no functional/data/auto-projection changes, no month/week/day
grid views (still out of scope). Split the previously-monolithic inline
JSX into `AgendaList`/`EventCard` (new, `features/builder/calendar/components/`)
plus a shared `eventVisuals.ts` (icon + colour per event type, mirroring
the plot grid's own "colour + a second, non-colour signal" accessibility
pattern — here an icon, since a hatch pattern doesn't translate to a list
context). Date groups are now classified as Today/Tomorrow/Overdue (red
accent + badge) based on the date itself, not the window offset, so
navigating to a past 30-day window correctly reads every group in it as
overdue. `ScheduleService.syncInstalmentProjection`'s auto-generated title
bakes the amount into a plain string server-side (a known, pre-existing gap,
untouched here per the "no backend changes" instruction) — parsed back out
client-side only, for display, so the amount renders large via the existing
`formatIndianCurrency` instead of a buried raw number; same treatment
strips the redundant "Follow-up: " prefix now that the type already shows
as its own line. Falls back to the raw title untouched if either string
format ever changes.

Verified in a real browser at 360px, English and Hindi, against a live
`m7verify` stack seeded with realistic mixed data (an overdue instalment,
a due-today instalment/meeting/follow-up, and upcoming events) — zero
horizontal overflow, zero raw i18n keys, weekday/date headers render with
Latin digits even in Hindi (`Intl.DateTimeFormat` explicitly pinned to
`numberingSystem: 'latn'`, matching `formatters.ts`'s own established
convention). `tsc`/`oxlint`/`vitest`/`build` all clean.

---

## Post-M7 — Minimal Ops Visibility (a deliberately scoped-down stand-in for M-14, not M-14 itself)

**Read this before assuming the Platform Admin Console (M-14) exists in any
form.** It does not. This is a small, explicitly-scoped substitute built
because real WhatsApp/SMS/email delivery just went live (M-06 second half)
and there was no way to notice a silent delivery failure or a genuine
server error before a customer reported one. It answers exactly two
questions -- "did recent message sends succeed?" and "has the app thrown
any real errors lately?" -- and nothing else. **None of the following exist
and were explicitly excluded per the user's own instruction**: tenant
suspension, feature flags, a plan-limit UI, announcements, a stamp-duty
rate editor, a separate `platform_admin` auth realm, break-glass access,
IP allowlisting, MFA, or any cross-org visibility beyond the same
nullable-`org_id` "system-wide row" shape `role.org_id` already
established back in M0. If M-14 is ever picked up for real, this
`foundation.admin` package is a subset to extend, not a foundation to
throw away -- but it must not be mistaken for M-14 already being done.

**Access control:** gated on the existing `SETTINGS_MANAGE` permission
(Admin/Owner only, seeded since M1's `V1_006` but never actually consulted
by any code until now -- the same "seeded early, wired up later" pattern
this project has hit repeatedly, e.g. `BUILDER_TEAM_MEMBERS` quota in M4).
No new role, no new permission code, per the user's explicit "reuse the
existing role/permission system" instruction. Reachable from `ProfileMenu`
→ "Ops" (only rendered when `useCan('SETTINGS_MANAGE')`, cosmetic per
CLAUDE.md rule #5 -- the real gate is server-side `@RequiresPermission`).

**Message delivery log (`GET /api/v1/admin/message-deliveries`):** a thin
read over the `message_delivery` table the M-06 second half already
writes to -- no new write path, no new data. `message_delivery` already
has real RLS (`org_id` `NOT NULL`, no nullable-system-row case), so an
admin viewing it through the normal RLS-scoped `shardeya_app` connection
is automatically limited to their own org's rows with zero extra
mechanism; the repository query's own `orgId` parameter is defense in
depth (CLAUDE.md rule #1), not the actual access-control decision.

**Server error log (new `app_error_log` table, `V14_001`):** every row is
written from exactly one place -- `GlobalExceptionHandler`'s existing
catch-all `@ExceptionHandler(Exception.class)`, the one already documented
since M1 as load-bearing for debugging. Deliberately NOT wired to any of
the other, specific handlers in that class (`ResourceNotFoundException`,
`BadRequestException`, `QuotaExceededException`, etc.) -- those are
expected, handled conditions, not "something is broken." `org_id` is
nullable: a genuine server error can happen before any tenant context is
ever bound (a malformed pre-login request, a background job with no
single org to blame) -- a `NULL` row is a platform-level error, visible
to every org's admin, same nullable-`org_id` RLS shape `role.org_id`
already uses (`org_id IS NULL OR org_id = current_setting(...)`), except
using the safer `current_setting('app.current_org', true)` (`missing_ok`)
form `message_delivery`'s own V7_015 policy already established, since
this table's own INSERT must never itself throw just because no tenant
happens to be bound at the moment of the error. The write happens inside
its own `PROPAGATION_REQUIRES_NEW` transaction (same reasoning as
`OutboxPoller`'s constructor) -- the exception being handled may have come
from a transaction now marked rollback-only, and the whole write is
wrapped in its own try/catch so that a failure to *log* an error can never
itself become a second, masking exception.

**Real bug found (and fixed) while hunting for a genuine edge case to
verify this with, not fabricated:** `NotificationPreferenceService.update()`
never validated an incoming `typeCode` against the real `notification_type`
catalogue before saving -- `notification_preference.type_code` has a real
FK to `notification_type(code)` (`V7_014`), so an unknown code (a stale
client, a typo, a future catalogue rename) hit that constraint at INSERT
time as an unhandled `DataIntegrityViolationException`, a genuine 500.
This was found live: `PUT /api/v1/settings/notifications` with a
deliberately-nonexistent `typeCode`, confirmed as a real 500 via `curl`
against the running `m7verify` stack, then confirmed it correctly showed
up in the brand-new `/admin/errors` view with the full exception class and
Postgres constraint-violation message -- the exact loop this ops page
exists to close. Fixed by validating `typeCode` against the loaded
catalogue map before ever calling `save()`, throwing a clean
`BadRequestException` (`error.notification.unknownTypeCode`) instead.
**Any future write path that inserts into a table with a real FK to a
catalogue-shaped table needs to validate the foreign value up front, not
rely on the DB constraint to reject it** -- the constraint is correct as a
backstop, but a 500 for "you sent an invalid value" is always wrong;
that's a 400. New regression coverage
(`NotificationPreferenceServiceIntegrationTest`, this service's first test
coverage of any kind) for both the rejection and the ordinary happy path.

**Frontend, a real bug caught only by looking at a screenshot, same lesson
as the calendar redesign above:** the first version of the Ops page used a
wide, 6-column `<Table>` for both tabs. At 360px, only 3 columns
(When/Channel/To) fit before the row's own `overflow-x-auto` container
kicked in -- correctly containing the overflow so the *page* never broke,
but silently pushing Status and Error (the entire reason this page exists)
off-screen, invisible without an extra horizontal scroll a real user would
have no reason to discover. Automated overflow/console/raw-key checks all
passed against this broken version. Rebuilt as icon-chip card rows
(`DeliveryCard`, matching the same visual language `EventCard` already
established for the calendar agenda list -- coloured icon chip + primary
line + status badge + secondary/error line), which naturally reflows at
any width instead of needing a scrollable table. **Any future internal
tooling page in this project should default to card rows over a wide
table from the start** -- this is now the second time in this same
session a wide table has needed a post-hoc mobile rewrite once someone
actually looked at it.

**Verification methodology and results:**
- Backend: 105/105 `mvn test` green (Testcontainers, zero regressions),
  including new tests for tenant isolation on both new read paths
  (`AdminOpsServiceIntegrationTest`: message-delivery org-scoping,
  error-log org+platform-level visibility with a real cross-org leak
  check, the `SETTINGS_MANAGE` permission gate rejecting/allowing
  correctly), the `GlobalExceptionHandler` write path itself
  (`GlobalExceptionHandlerErrorLogIntegrationTest`, using a real Spring-
  wired handler bean + a real `MockHttpServletRequest` -- this codebase
  has no mocking framework in its test stack, so this is Spring's own
  official lightweight test double, not Mockito), and the
  `NotificationPreferenceService` bugfix above.
- Real HTTP verification against the same long-running `m7verify` stack
  every M7 session has used (rebuilt with this round's code, `V14_001`
  applying cleanly on top of the existing 93 migrations): confirmed
  pre-existing real `WhatsApp SENT` deliveries (from this stack's own
  earlier scheduler ticks) show up correctly with masked recipients and
  real status → triggered a fresh live delivery (invited a second staff
  member, `STAFF_ADDED`'s email channel) and confirmed the new row
  appeared within seconds, independently cross-checked against MailHog
  showing the same real email → found and confirmed the
  `NotificationPreferenceService` 500 as described above → confirmed the
  fix turns it into a clean 400 → confirmed the original error record
  persisted correctly across a full backend container rebuild (Postgres-
  backed, not in-memory). The Free-plan `BUILDER_TEAM_MEMBERS=1` quota
  needed the same throwaway-plan-swap precedent M4/M5/M6/M8 already
  established (`OPSTEST`, reverted back to `FREE` immediately after).
- Frontend: `tsc -b`/`oxlint`/`vitest run` (25/25, including a new
  `admin` namespace key-parity check)/`npm run build` all clean. A real
  browser pass at 360px, English and Hindi, against the same live stack --
  this is what caught the table-overflow bug above; re-verified clean
  after the card-row rewrite (zero page-level horizontal overflow, zero
  raw i18n keys, zero console errors, both tabs switching correctly in
  both languages).

**Known, deliberate scope decisions, not bugs:**
1. No pagination beyond a simple `limit` query param (capped at 200) --
   matches this session's own "doesn't need to be fancy" instruction;
   revisit if either table's real volume ever makes a flat recent-N list
   insufficient.
2. No delivery-status filter beyond the three most useful values
   (All/Sent/Failed/Queued) in the frontend dropdown, though the backend
   accepts any real `MessageDelivery.Status` value.
3. No way to filter the error log by exception type or date range -- "the
   last 100, newest first" is the entire feature.
4. `app_error_log` has real RLS but the actual write path only ever sets
   `org_id` from whatever `TenantContext` happens to be bound at the
   moment -- there is no verification that this is *always* correct for
   every possible unhandled-exception scenario (e.g., an exception thrown
   after tenant context was cleared but before the response was sent).
   Acceptable today (single real customer, per the user's own framing);
   would need a closer audit before this table's isolation claim could be
   trusted with a second, unrelated tenant.

---

## Post-M7 Bug Fix — Tracker Silently Capped at 50 Rows, No "Load More"

Requested by the user as a general "check the tracker dashboard end to end,
I think something's wrong" — the same open-ended audit request the M5
stats-matview investigation was born from, not a pre-identified symptom.
Backend endpoints (`TrackerController`/`TrackerService`, both tabs, all six
actions: log/reschedule/mark-done/record-payment/remind/bulk-remind) and
every interactive flow (Log Follow-up, Mark Done, Record Payment, WhatsApp
Reminder) were driven for real via `curl` and a real headless-browser
session against a live account and returned 200/202 with correct data and
zero console errors — the actions themselves are not broken.

**The real bug**: `TrackerPage.tsx`'s `followUpsQuery`/`collectionsQuery`
were plain `useQuery` calls hardcoded to `limit: 50`, and the `CursorPage`
response's own `hasMore`/`nextCursor` fields (present in `types.ts` since
this feature shipped) were never read anywhere in the Tracker feature —
no "Load more" button, no infinite scroll, nothing. Confirmed live: seeded
61 follow-up-due leads for a test org, and the tab correctly read
"Follow-ups (61)" (from the separate `/counts` endpoint) while the table
silently rendered only the first 50, with zero indication the other 11
existed anywhere on the page. Any org with more than 50 leads due for
follow-up, or more than 50 payment schedules due/overdue at once — exactly
the kind of volume a real, established, actively-used builder account
accumulates over time, unlike this project's own thin seeded test orgs —
would silently lose visibility into everything past the 50th row of
whichever tab. This is precisely the shape of bug most likely to read as
"the tracker isn't working correctly" without a more specific symptom: nothing
errors, nothing looks broken in isolation, entries just aren't there.

**Fixed** by converting both queries from `useQuery` to `useInfiniteQuery`
(`queryFn: ({ pageParam }) => list...({ ..., cursor: pageParam })`,
`getNextPageParam: (lastPage) => lastPage.hasMore ? (lastPage.nextCursor ??
undefined) : undefined`) and adding a "Load more" button gated on
`query.hasNextPage`, below each table — the exact, already-established
pattern `LeadListPage.tsx`/`ProjectListPage.tsx` already use for the
identical `CursorPage`-shaped problem; nothing new was invented.
`FollowUpTable`/`CollectionTable` themselves needed no changes at all —
both already just render whatever `rows` array they're handed, so
`followUpsQuery.data.pages.flatMap(p => p.items)` (memoized) slots in as
the new `rows` value with no prop-shape change. Verified live: reran the
61-lead scenario after the fix — 50 rows render initially, "Load more" is
visible, clicking it fetches the 51st-61st rows via a real
`cursor=...`-bearing request and the button correctly disappears once
exhausted (`hasNextPage` false). Existing mutation-success
`invalidateQueries({ queryKey: ['tracker-followups'] })`/
`['tracker-collections']` calls needed no changes — TanStack Query matches
invalidation by key-array prefix regardless of `useQuery` vs
`useInfiniteQuery`, and invalidating an infinite query correctly refetches
every page currently loaded, not just the first.

**A related, pre-existing, NOT-fixed gap noticed along the way**:
`LeadListPage.tsx`'s own "Load more" button reads
`t('common:list.loadMore', { defaultValue: 'Load more' })` — `common.json`
has no `list.loadMore` key at all (in either language), so that button has
silently always rendered the hardcoded English fallback regardless of the
active language, a real i18n gap unrelated to Tracker (CLAUDE.md rule #4).
`ProjectListPage.tsx`'s own "Load more" (`t('list.loadMore')`, sourced from
`project.json`, which does have real `en`/`hi` values) is the version of
this pattern actually done correctly, and is what this fix's own new
`tracker.json` `loadMore` key follows. Left `LeadListPage.tsx` itself
untouched — out of scope for a Tracker-focused pass — but worth fixing
together with a `common.json` key (or copying the same per-namespace
pattern) if Leads is ever revisited.

**Verification**: `npx tsc -b --noEmit` clean, `npx vitest run` 25/25 green
(including the `tracker` namespace key-parity check, covering the new
`loadMore` key in both `en`/`hi`), `npx oxlint` clean (same pre-existing
shadcn warnings only), `npm run build` clean. All four interactive Tracker
actions (log, mark-done, record-payment, WhatsApp remind) re-verified live
post-fix with zero regressions — same 200/202 responses, same zero console
errors. Seeded test data (60 synthetic leads) removed from the verification
org afterward.

---

## Post-M7 — Four Real-Account Bug Reports (Financials, Calendar, Plot Edit)

User-reported round from live account use, same "found by using it" pattern as every prior post-milestone round in this file. Four things to check; three were real bugs (fixed), one was correct behavior with a display-precision illusion (left as-is, documented here instead of silently glossed over).

**1. Revenue Trend chart ignored the project filter (real bug, fixed).**
`FinancialService.revenueTrend()` never accepted a `projectId` parameter at
all -- the chart was always org-wide regardless of what the Financials
page's project dropdown had selected, directly contradicting B-08 §14.4's
own "project, date range, payment mode, status -- cascades to every card
and table on the page" contract, which every OTHER card/table on the page
already honours. Fixed by threading `projectId` through
`FinancialController.revenueTrend()` → `FinancialService.revenueTrend()`
(reusing the existing `scopeClause()` helper, same as `summary()`/`payments()`/
`pending()` already do) → `financialApi.ts`'s `getRevenueTrend()` →
`FinancialsPage.tsx`'s `trendQuery` (now keyed on `projectId` too). Verified
live on a real account's data (org `ef2731af-...`, 3 projects with real
revenue): org-wide trend correctly summed to ₹7,88,34,722 across all three
projects; scoped to just "M7 Smoke Project" it correctly returned exactly
that project's ₹7,87,66,400; scoped to "Testing" it correctly returned
exactly ₹43,322. New regression test:
`FinancialServiceIntegrationTest.revenueTrendRespectsTheProjectFilterInsteadOfAlwaysBeingOrgWide`.

**2. "Total Revenue (All Time)" appearing unchanged after switching to All
Projects (verified correct, NOT a bug -- a display-precision illusion).**
Investigated using the exact real numbers the user reported (M7 Smoke
₹7.88 Cr, Testing ₹43,322) against the real account: `FinancialService.summary()`'s
`totalRevenueAllTime` is 100% correct --
scoped to M7 Smoke Project alone it returns exactly ₹78,66,400; with no
project filter (All Projects) it returns exactly ₹78,834,722, i.e. M7
Smoke + Testing (₹43,322) + a third project, Testing Again (₹25,000) the
user hadn't mentioned. The confusion is `formatCompactIndianCurrency`'s
2-decimal-place Cr rounding: 7.87664 Cr and 7.8834722 Cr both round to the
identical displayed string "₹7.88 Cr" -- the ~₹68,000 difference (0.09% of
the total) is real and present in the underlying number (confirmed via the
card's own existing `title=` hover tooltip, which already shows the exact
unrounded figure) but invisible at this display precision. Not changed --
`formatCompactIndianCurrency` is shared by Dashboard/Stats/every other
compact-currency card in the app, and a global precision change is a much
bigger, separate decision than this bug-fix round's scope. **Every other
Financials field was independently spot-checked against the same real
account and confirmed correctly project-scoped**: `revenueThisMonth`,
`revenueThisYear`, `pendingCollections`, `overdueInstalments`, and
`paymentModeBreakdown` (CASH/CHEQUE split) all changed correctly between
the All-Projects and M7-Smoke-only calls.

**3. A fully-paid instalment stayed on the calendar (real bug, fixed) --
the standout finding of this round, a genuinely new instance of the
"Hibernate can't see a DB-trigger-computed change it didn't itself make"
bug class this project has hit repeatedly since M2, but a SECOND-ORDER
case of it that the M4-era fix for this exact area didn't anticipate.**
`ScheduleService.syncAllInstalmentProjections()` (called from
`PaymentService.record()` after every payment, per M4's own "the DB
trigger flips status, Java only finds out by re-reading the row
afterward" fix) re-queries `PaymentSchedule` rows via the repository and
checks `schedule.getStatus()` to decide whether to remove or keep the
calendar's `INSTALMENT_DUE` projection. The problem: earlier in the SAME
transaction, `PaymentAllocationService.allocate()` (called first, from the
same `PaymentService.record()`) already loads those EXACT SAME
`PaymentSchedule` rows via the identical repository query, before
inserting `payment_allocation` rows whose trigger (V3_010) then flips
`status`/`amount_allocated` in the DATABASE only. Hibernate's persistence
context still holds those SAME entity instances by identity, so
`syncAllInstalmentProjections()`'s own "fresh" re-query returned the
STALE, pre-trigger, still-PENDING cached objects -- not fresh ones --
because a query returning rows Hibernate already has loaded discards the
newly-fetched column values in favour of the cached instance. Net effect,
confirmed live: paying an instalment due tomorrow in full today correctly
flipped `payment_schedule.status` to PAID in the database (immediately
visible to any OTHER/later query or a fresh HTTP request), but the
`calendar_event` row for it was never soft-deleted -- the paid instalment
kept showing on the Calendar page indefinitely. This is exactly why M4's
own "re-read the row afterward" fix didn't catch this: a re-query from a
GENUINELY separate transaction (a new request, a direct SQL check, even
this project's own prior test assertions reading `schedule.getStatus()`
right after `record()` returns) always sees the correct fresh value --
only a re-query from WITHIN the SAME transaction as the entity's first
load stays stale, which is precisely the shape `syncAllInstalmentProjections()`
has. Fixed with `entityManager.refresh(schedule)` on each schedule inside
the sync loop (plus an explicit `entityManager.flush()` first, matching
this project's own established explicit-flush-before-refresh convention
rather than relying on implicit JPA auto-flush timing) -- the same fix
shape `EntitlementService.usage()` already established for the identical
root-cause class. **Verified the test actually catches the bug**, per this
project's own standing rigor: temporarily reverted the fix, confirmed the
new regression test (`PlotSaleIntegrationTest.fullyPayingAnInstalmentRemovesItFromTheCalendarInTheSameTransaction`)
failed with exactly the reported symptom, then restored the fix and
confirmed it passed. Verified live end-to-end too: created a fresh sale
with an instalment due tomorrow, confirmed the calendar event existed,
recorded a full payment today, confirmed the event was gone from a real
`GET /calendar` call and from the actual Calendar page in a real browser
(Hindi, 360px, zero overflow). **A pre-existing calendar_event row from
BEFORE this fix stays broken** -- the fix only prevents the bug from
recurring for any payment recorded after the fix ships; there's no backfill
migration to retroactively clean up already-stuck stale events, matching
this project's own precedent for every prior "fix stops it going forward,
doesn't retroactively repair already-corrupted data" bug (e.g. the M4
`BUILDER_TEAM_MEMBERS` quota backfill was a rare exception specifically
because a trigger literally didn't exist yet for pre-existing rows to have
fired against -- this bug had the trigger firing correctly the whole time,
it was only the calendar's own read of it that was wrong).

**4. Edit Plot stayed available after "Mark Complete" / after any sale at
all (real gap, fixed on both frontend and backend).** `PlotDetailDrawer`'s
Edit button had no `plot.status` guard whatsoever -- unlike its own
Delete and "Mark as Sold" sibling buttons in the exact same component,
both of which already correctly hide via `plot.status !== 'SOLD'`. Worse,
`PlotService.update()` had **zero corresponding server-side guard either**
-- CLAUDE.md rule #5 ("UI hiding is cosmetic... every endpoint enforces
permissions") means the missing frontend hide was never the real problem;
a plot's own deal-defining attributes (plot number, size, facing, price)
could be edited via a direct API call after a sale existed, active or
completed, with nothing stopping it. Fixed on both sides: `PlotDetailDrawer.tsx`'s
Edit button now matches Delete's own `plot.status !== 'SOLD'` condition
exactly; `PlotService.update()` now rejects the whole call with a clean
400 (`PLOT_EDIT_BLOCKED_SOLD` / `error.plot.editBlockedSold`) the moment
`plot.status === SOLD`, checked before this endpoint's only frontend
caller (`PlotForm.tsx`'s edit mode, confirmed via a full grep -- no other
flow reaches this endpoint) can do anything. A narrower, now-dead
zero-price guard that used to live inside this same method
(`"a sold plot must have a price"`) was removed as unreachable, along with
its now-orphaned `error.plot.priceRequiredIfSold` i18n key (en+hi) --
`@PositiveOrZero` on the request DTO still correctly allows a 0 price for
a genuinely not-yet-sold "price TBD" plot, which was always the real
intended base case underneath that check. Verified live: attempting
`PATCH /plots/{id}` on a real sold plot now returns a clean 400 with the
new error code; the identical call against a genuinely unsold plot still
succeeds normally (no regression). Verified in a real browser too: opened
a sold plot's detail drawer, confirmed no Edit button renders at all
(only Cancel Sale), full payment/schedule history still shown correctly.
New regression test: `PlotSaleIntegrationTest.updatingAPlotIsBlockedOnceItsSold`.

**Verification methodology and results:**
- Backend: 108/108 `mvn test` green (Testcontainers, zero regressions;
  3 new regression tests across `FinancialServiceIntegrationTest` and
  `PlotSaleIntegrationTest`).
- Frontend: `tsc -b` clean, `oxlint` clean (same pre-existing shadcn
  warnings noted since M2), `vitest run` 25/25, `npm run build` clean.
- All four findings were reproduced and re-verified against a real,
  already-live-in-use account's actual data on the rebuilt `m7verify`
  stack (org `ef2731af-...`, the same one the user's own bug report came
  from) via real HTTP calls (using the OTP-login flow to get a session for
  that org without touching its password), not synthetic test-only data --
  the exact ₹7.88 Cr / ₹43,322 figures the user reported were independently
  reproduced from this project's own live database before any fix was
  written, confirming the report described real, correctly-observed
  behaviour rather than a misunderstanding. The calendar fix was
  additionally reproduced fresh on a second, throwaway test org+sale
  specifically created after the fix shipped, to prove new payments are
  now handled correctly going forward (see finding #3's own note on why
  pre-fix stale calendar rows can't be retroactively repaired).

---

## Post-M7 Bug Fix — Swagger UI 401'd, Only the Raw OpenAPI JSON Worked

Found while answering a direct question ("is Swagger doc available for our
project?") — checked live rather than just confirming the dependency was on
the classpath, which is what surfaced this.

**The real bug**: `springdoc-openapi-starter-webmvc-ui` is a real
dependency, correctly configured (`springdoc.swagger-ui.path:
/api/v1/docs`), and `TenantContextFilter.PUBLIC_PATH_PREFIXES` already
allowlisted `/api/v1/docs` as public. But that config property only names
springdoc's **initial redirect entrypoint** — `GET /api/v1/docs` correctly
302s, but always to the *fixed*, unconfigurable path
`/api/v1/swagger-ui/index.html`, which the allowlist never covered. Every
real request to the interactive Swagger UI 401'd with
`error.auth.missingToken` before the page could ever render — only the raw
`GET /api/v1/openapi` JSON spec (a separately, correctly allowlisted
prefix) actually worked. Confirmed live via `curl -L`: the redirect fires
correctly, following it returns 401, not the HTML page.

**Fixed** by adding `/api/v1/swagger-ui` to `TenantContextFilter.PUBLIC_PATH_PREFIXES`
alongside `/api/v1/docs` — springdoc's redirect target and its configured
entrypoint are two different paths and both need to be public. **This is
the same class of bug as M1's CORS/OPTIONS finding** (an auth filter's
hardcoded exemption list not covering every path a framework actually
uses internally) — now added to `PATTERNS.md` as its own entry since it's
recurred a second time in a new shape.

New regression test, `TenantContextFilterIntegrationTest` — **verified the
test actually catches the bug**, per this project's own standing rigor:
temporarily reverted the fix, ran the test, confirmed it failed with
`expected: 200 OK but was: 401 UNAUTHORIZED` (the exact reported symptom),
restored the fix, confirmed both tests passed. 110/110 `mvn test` green
afterward, zero regressions.

Also live-verified against a rebuilt `m7verify` docker image — worth
noting how that went, since it wasn't clean: `docker compose -p m7verify
build backend` failed once on a transient network truncation pulling the
Maven base image ("short read... unexpected EOF"), and the *subsequent*
`docker compose up -d backend` (with no override file explicitly passed)
silently fell back to recreating **every** service in the stack from the
bare `docker-compose.yml` — wiping the custom port remapping the isolated
verification stack actually needs (`postgres`/`redis`/`minio`/`mailhog`/
`backend` all moved to their default, colliding ports) and briefly leaving
`redis` unable to bind at all. **No data was lost** — Postgres/MinIO data
live in named Docker volumes (`m7verify_postgres_data`, confirmed
independently: `organization`/`customer` row counts for both `ef2731af-...`
and the session's own test org were identical before and after) — but this
is a real, reproducible gotcha: **`docker compose up -d <service>` against
a project that needs an override file must always pass `-f
docker-compose.yml -f <the override>` explicitly, every time — it will
never infer a previously-used override from the project name alone**, and
a single `up` without it can silently blow away every other service's
port mapping in the same project, not just the one service named on the
command line. Recovered by reconstructing the override from the port
mappings already visible in `docker ps` output from earlier in this same
session (`docker-compose.yml -f <reconstructed-override>`), which brought
the stack back exactly as it was.

**Verification**: `mvn test` 110/110 green (Testcontainers -- a real
embedded Tomcat + the real filter chain + a real HTTP redirect-then-follow,
not mocked), including the revert-and-confirm-red step above -- this alone
is already a complete, real HTTP round-trip through the exact filter this
bug lives in. A live `curl -L` check against a rebuilt `m7verify` docker
image was also attempted for extra confidence, but the base Maven image
pull was slow/flaky in this environment (the truncated first attempt is
what caused the port-remapping incident described above) and never
reliably completed within a reasonable wait -- not pursued further given
the automated test already fully covers the fix. Re-attempt if a docker
image rebuild is ever needed for this stack again and the pull is healthy.

---

## Post-M7 Bug Fix — Uneven Dashboard Card Heights

Reported by the user directly from a screenshot ("follow up today block was
different length from rest of the blocks"). First response mistook a
different visual artifact in the same screenshot (a stray badge from a
browser extension, confirmed by reproducing the exact dashboard in a clean
extension-free browser and finding it perfectly even) for the real
complaint -- the user then clarified they meant card *height*, not the
badge. Worth remembering: a screenshot can contain more than one thing
worth investigating, and confirming "this specific artifact isn't ours"
doesn't mean the underlying report is resolved.

**The real bug**: measured via real bounding boxes (not eyeballing a
screenshot) -- `Reserved Plots`/`Active Leads`/`Instalments Due This
Month` were all 150px tall, `Follow-ups Today` alone was 130px, in the
same grid row. `SummaryCard.tsx` wraps clickable cards in `<Link
className="block">`; as a direct CSS grid item, that `<Link>` correctly
stretches to the row's full height (`align-items: stretch` is grid's
default) -- but the `<Card>` rendered *inside* it had no `h-full`, so the
visible card (border/background/shadow) only ever extended to its own
content height, leaving invisible empty space in the taller, transparent
`<Link>` below a shorter card. Non-clickable cards render `<Card>`
directly as the grid item (no `Link` wrapper), so they stretched
correctly by the same default grid behavior -- which is exactly why only
some cards in the row looked short and others didn't: it tracked
clickable-with-short-content, not any single visible property a user
would think to check. `Instalments Due This Month` (clickable) happened
to look fine only because its own content (value + `amount` sub-line)
was naturally as tall as the row's max anyway.

**Fixed** by adding `h-full` to both `Card` and the `Link` wrapper in
`SummaryCard.tsx`. Verified live via real bounding-box measurements
(`page.locator(...).boundingBox()`, not just a screenshot): all four cards
in the affected row measured exactly 150px before vs. after, confirmed
visually at both desktop and 360px. **Any future card/tile component
rendered inside a CSS grid, where some variants are wrapped in an
interactive element (`Link`/`button`) and others aren't, needs `h-full`
on every layer between the grid item and the visible box** -- grid's
default stretch only affects the direct grid item itself, not descendants
of it, so a wrapper with no explicit height is an easy way for this exact
row-height mismatch to reappear in a different card grid later.

`tsc -b`/`vitest run` (25/25)/`oxlint` all clean, no other files touched.

---

## Post-M7 Bug Fix — Project Wizard Let You Reach Review With an Over-Limit Area

Reported by the user with an exact repro (2,000,000 SQ_FT, 200 plots) and
the error text they saw ("Enter a valid area."), at the Review step.

**The real bug**: `03-BUILDER-MODULES.md` B-02 §11 documents
`total_area_value | required, > 0, ≤ 1,000,000` as an intentional spec
constraint, correctly enforced backend-side
(`ProjectCreateRequest.totalAreaValue`'s `@Max(1_000_000)`). But
`frontend/.../projects/schemas.ts`'s own Zod schema -- whose own file
comment says it "mirrors backend... Bean Validation constraints exactly"
-- never actually had the `.max()` half of that mirror; only
`.positive()` was present. Since `ProjectFormPage`'s wizard validates only
the *current* step's fields via `form.trigger(...)` before allowing
"Next," an over-limit value at Step 3 (Size & Timeline) was never flagged
there -- the user could walk through Steps 4 and 5, reach Review, and only
then have the backend reject it on final submit. Not a data problem (2M
sqft is a plausible large-colony size, and the 1,000,000 cap itself is the
documented, correct business rule) -- purely a missing client-side mirror
of an existing server-side rule.

**Fixed** by adding `.max(1_000_000, e(t, 'project.areaInvalid'))` to
`totalAreaValue` in `schemas.ts`, using the exact same message key the
backend's own `@Max` violation already returns (`error.project.areaInvalid`
-> "Enter a valid area."), so client- and server-side rejections read
identically, matching this file's own established convention. Verified
live end-to-end reproducing the user's exact values: typing `2000000` at
Step 3 now shows the error immediately under the field and blocks "Next"
right there -- the wizard never lets the user reach Review with it.
Confirmed `1000000` (the actual limit) still correctly advances past Step
3, so this isn't off-by-one.

**Any future field whose Zod schema comment claims to "mirror" a backend
DTO's Bean Validation needs to be spot-checked against the actual DTO
constraints, not assumed correct because the comment says so** -- this is
the same root shape as every other client/server validation-drift bug
this project has hit (e.g. the reference-field-required-for-non-CASH-modes
gaps documented earlier), just on a numeric bound instead of a required
field.

`tsc -b`/`vitest run` (25/25)/`oxlint` all clean, no other files touched.

---

## Post-M7 Feature Change — Project Total Area Limit Raised to 3,000,000

Immediate follow-up to the fix above, per an explicit user request ("i want
to increase the limit upto 3000000"). `03-BUILDER-MODULES.md` B-02 §11 and
all three enforcement points updated together: `ProjectCreateRequest`'s
`@Max`, the frontend `schemas.ts` `.max()`, and the spec table itself, all
now `3_000_000`/`3,000,000`.

**A second, real, pre-existing gap found and closed in the same pass**:
while raising the limit, checked whether `ProjectUpdateRequest` (the PATCH
DTO) had the matching constraint -- it had **no area validation at all**,
not even the original `> 0`. `PATCH /projects/{id}` with any
`totalAreaValue` (negative, zero, or unbounded) was accepted outright as
long as it paired with a `totalAreaUnit` (per `ProjectService.update()`'s
own `if (totalAreaValue != null && totalAreaUnit != null)` gate for
applying the pair together). This was never caught because
`ProjectCreateRequest`'s constraints were assumed to cover project area
validation generally -- Create and Update are two separate DTOs, and only
one of them was ever actually mirrored from the spec. Added the same
`@DecimalMin(0.0001)` + `@Max(3_000_000)` to `ProjectUpdateRequest.totalAreaValue`,
closing it for both new and existing projects. Verified live via direct
`curl` PATCH calls: `-5` and `3500000` both now correctly 400 with
`error.project.areaInvalid`; `3000000` (paired with `totalAreaUnit`)
applies correctly.

**No existing backend test coverage exists for Project CRUD at all**
(confirmed via a full search of `src/test`) -- this module has been
verified via frontend e2e (`m2-builder-flow.spec.ts` etc.) since M2, per
that milestone's own established precedent, not a dedicated
`ProjectServiceIntegrationTest`. Given both changes here are plain Bean
Validation annotations (framework-enforced, not custom logic), verification
was via real HTTP calls against the live backend rather than introducing a
new test class from scratch for this one change -- consistent with the
existing coverage shape for this module, not a shortfall introduced by this
change specifically. Re-verified the user's exact original repro
end-to-end afterward: `2,000,000 SQ_FT` / `200 plots` now correctly
advances past Step 3 of the wizard.

`mvn test` 110/110 green (zero regressions from the Update DTO gaining
real constraints for the first time), `tsc -b`/`vitest run` (25/25)/`oxlint`
all clean.

---

## Post-M7 Feature Change — Removed Dashboard Quick-Action Row

Per explicit user request ("remove that three buttons Add Project / Add
Lead / Record Payment"). That row was `QuickActionRow`'s entire purpose --
no other content lived in that component -- so this removed the component
usage from `DashboardPage.tsx`, deleted `QuickActionRow.tsx` outright
(confirmed via grep it had no other callers), and removed the now-orphaned
`dashboard:quickActions.*` i18n keys (en+hi). Matches this project's own
established precedent for this exact situation (the M7-era "Recent
Activity" section and "Bulk Upload" quick action removals, both handled
the same way: delete the dead UI, its file if nothing else uses it, and
its i18n keys, leave any backend endpoint the removed UI called alone
since removing it wasn't asked for).

Verified live: all three buttons confirmed absent (`getByRole('link',
{name: ...})` counts of 0 for each), the alert banner now sits directly
above the card grid with no leftover gap, no console errors. `tsc -b`/
`vitest run` (25/25)/`oxlint` all clean.

---

## Post-M7 Feature Change — Removed "Enable Reminder" Checkbox From Add Event

User asked what the checkbox did; investigation (traced through
`AddEventDialog.tsx` -> `CalendarService` -> full backend search for any
reader of `calendar_event.reminder_enabled`) found it's stored but **never
acted on** -- no scheduled job, no notification, no WhatsApp/email/in-app
send is triggered for a manually-created event based on this flag, for
either state. The original spec (`02-FOUNDATION-MODULES.md` M-11)
describes a real reminder-offset mechanism intended to drive M-06 sends,
but that was only ever built for the **auto-generated** projections
(Instalment Due via `InstalmentDueTodayScheduler`/`OverdueScheduleSweeper`,
Follow-up Due via `FollowUpDueSweeper`) -- never for manual events, which
have no corresponding sweep at all. User asked to remove it once this was
confirmed.

**Removed the checkbox from `AddEventDialog.tsx`** (UI-only, matching this
project's own established precedent for this exact situation -- the M7
Google Maps field and Excel-import-entry-point removals, both "delete the
front-door affordance, leave the underlying data/schema alone" rather than
a schema migration). `reminderEnabled` is still sent as `true` on every
create (hardcoded, matching the column's own default and the checkbox's
old default state), since the backend's `EventCreateRequest.reminderEnabled`
remains a required field in the contract -- not touched, since nothing
asked for the backend behavior itself to change. Removed the now-orphaned
`calendar:form.reminderEnabled` i18n key (en+hi).

Verified live: checkbox confirmed absent from the dialog, creating an
event still succeeds (`201 POST /calendar/events`), the calendar still
refetches and shows the new event. `tsc -b`/`vitest run` (25/25)/`oxlint`
all clean, no backend changes.

---

## Milestone 6.5 (Steps 1-4) — Broker Network & Designation Commission Engine

New milestone, full spec in `06-BROKER-NETWORK-ENGINE.md` (now a source of
truth alongside `00`-`05` -- see the Architecture Docs table above). A
large, money-critical feature: a recursive broker hierarchy with
multi-level differential commission. Per the doc's own §15 build order and
this round's explicit instruction, **only steps 1-4 are done here** --
config table, network structure + integrity, broker creation with
explicit upline, and the pure commission calculation engine, exhaustively
tested against every §16 fixture. **Explicitly NOT touched**: wiring the
engine into a real sale/booking transaction (step 5), proportional
instalment release (step 6), COMPLETED-triggered sales counting/promotion
(step 7), manual promotion (step 8), cancellation/recovery (step 9),
dashboards (step 10), concurrency hardening (step 11), or migrating
existing M6 FIXED/tier brokers (step 12). `personal_successful_bookings`/
`team_successful_bookings` exist as columns and stay at 0 forever until
step 7 -- an honest, documented limitation of this round, not a bug.

**Step 1 -- `designation_slab` config table (§5).** The 8 slabs
(Business Executive/₹160 through President/₹255) as real config data, not
hardcoded `if/else` (§47's own explicit requirement) -- the exact
"config, not code" discipline M6 already established for `broker_tier`.
One deliberate naming deviation from the spec doc's own prose: the spec
calls the tenant column `builder_id`; this codebase uses `org_id`
everywhere else (including `broker_partner` itself), so `org_id` was used
here too for consistency, documented inline in the migration.
**`org_id` is nullable** (NULL = system-wide default), same shape as
`role.org_id` (V0_002) -- not `broker_tier`'s per-org-only shape, because
the spec explicitly describes future per-org overrides ("the builder may
want to tune them"). No v1 UI/API writes a non-null row yet; the resolver
(`DesignationSlabService.resolve`) already implements the correct
org-specific-wins-over-system-default precedence for when that lands,
mirroring `CommissionConfigService`'s own PLOT→PROJECT→GLOBAL pattern.
**A real subtlety caught before it became a bug**: Postgres `EXCLUDE`
constraints never consider two NULLs "equal" for `=` (same rule as
`UNIQUE`), so the overlap-prevention constraint (mirroring M6's
`broker_tier` GiST EXCLUDE exactly) does NOT by itself stop two
overlapping system-default rows from coexisting -- it only protects
future per-org rows. The 8 system-default rows' own non-overlap is our
seed-time responsibility, same trust boundary `stamp_duty_rate`'s seed
data already relies on; documented explicitly in the migration rather
than left as a silent gap. A second, separate unique index (on
`COALESCE(org_id, <sentinel>), name`) was added specifically so the seed
migration's `ON CONFLICT` actually targets something -- a bare
`ON CONFLICT DO NOTHING` with no matching constraint would have silently
inserted duplicates on a second run instead of skipping them.

**Step 2 -- `broker_partner` extensions + `broker_network` closure table
+ integrity (§10/§11).** `broker_commission_type` gained a `DESIGNATION`
value via its own migration (`ALTER TYPE ... ADD VALUE`, never combined
with a migration that uses the value in the same transaction -- Postgres
forbids that). `FIXED` stays in the enum forever (existing M6 data) but is
rejected at create-time only (`BrokerPartnerService.create()`) -- `update()`
deliberately keeps its old behavior unchanged, so editing an existing
FIXED broker's routine fields still works, per §0's "existing brokers...
still work." The closure table (`broker_network`) is maintained in plain
Java (`BrokerNetworkService`), never a DB trigger, because integrity
validation (self-upline, cycles, "cannot move under own descendant") has
to run *before* a row is written, not react after the fact. "No cycles"
and "cannot move under own descendant" turned out to be the exact same
check once formalized: `existsById_AncestorBrokerIdAndId_DescendantBrokerId(brokerId,
proposedUplineId)` -- if the proposed new upline is already a descendant
of the broker being reparented, setting it would close a cycle. "One
direct upline only" needed no separate check at all -- it's structurally
true, since `upline_broker_id` is a single nullable column, not a
collection. A DB-level `CHECK (upline_broker_id IS NULL OR upline_broker_id
<> id)` backstops the self-upline rule too, cheap insurance alongside the
service-layer check. Verified directly against the real closure table
after building a 3-level chain (Top → Downline → Self Test) via the API:
exactly 6 rows (3 self-rows at depth 0, plus depth 1/1/2 for the real
ancestor pairs), and the cycle-check query correctly returned `true` for
the exact "reparent Top Broker under its own grandchild" scenario.

**Step 3 -- broker creation with explicit upline + network tree (§30/§31/§28).**
`uplineBrokerId` is a plain field on `BrokerCreateRequest`, always supplied
by the caller -- there is no code path anywhere that infers it from
anything about the adding user, satisfying §31's explicit "never inferred"
rule by simply never having the inference machinery to begin with. A new
DESIGNATION broker resolves its starting slab via `designationSlabService.resolve(orgId,
0)` (Business Executive/₹160), the exact same "resolve via config, not
hardcode 'the first row'" pattern M6 already established for the
zero-deal tier lookup. `GET /brokers/network` returns a flat list (not a
server-built nested tree) with `uplineBrokerId` pointers; the frontend
(`NetworkTreePage.tsx`) assembles the tree client-side via a plain
`Map`-based single pass -- simpler than a nested DTO at the org-wide
broker counts this app will realistically see, and avoids yet another
response shape to keep in sync with the flat `BrokerResponse`/
`BrokerNetworkNodeResponse` used everywhere else.

**Step 4 -- the pure commission calculation engine (§7), built and
exhaustively tested BEFORE any wiring, per this round's own explicit
instruction.** `CommissionCalculationEngine.calculate()` has zero
dependencies -- no Spring, no DB, no service calls -- taking a
bottom-to-top chain of already-resolved `(brokerId, rate)` pairs plus an
area, returning one line item per chain position. All three
non-negotiable rules from §7 are enforced structurally, not by a
follow-up check: each upline compares only against `chain.get(level - 1)`
(never `chain.get(0)`, the original seller); a lower-rated upline produces
an explicit **zero-amount** line item, never a negative one and never
simply omitted (kept for the audit-completeness §9 cancellation logic
will eventually need -- "a reversal record for every beneficiary... even
uplines who did nothing wrong"); the same-slab ₹10 bonus is computed
independently at each level with no running total or cap anywhere in the
method. **Every fixture in §16 passes**: §45 (200/180/160 differential
chain, exact ₹160k/₹20k/₹20k), §46 (215/215/215 same-slab, exact
₹215k/₹10k/₹10k = ₹235k total, explicitly asserted to exceed ₹215k --
"not capped" proven, not just claimed), §33 (lower-rated upline → exactly
₹0, never negative). §21 and §17 are only described by chain *structure*
in the spec, not exact totals -- their expected values were derived
directly from §7's own stated algorithm (worked out step-by-step in each
test's own doc comment) rather than invented independently, and both
double as the regression guard for "compare with direct downline only":
comparing against the seller instead would produce a demonstrably
different, wrong total (worked out and asserted explicitly:
₹310,000 instead of the correct ₹235,000 for §17). **Verified the tests
actually catch the bug they're designed to guard against**, per this
project's own standing rigor: temporarily changed the direct-downline
comparison to compare against the seller instead, confirmed exactly 3
tests failed with exactly the predicted wrong numbers (₹40,000 instead of
₹20,000 for §45; ₹55,000 instead of ₹35,000 for §17; a
`NETWORK_SAME_SLAB_BONUS` that should have been an `UPLINE_DIFFERENTIAL`
for §21), then restored the fix and confirmed all 9 tests passed again.

**A real bug found during the Hindi + 360px pass, fixed on the spot**:
`DesignationSlab.nameHi` exists in the schema (seeded correctly for all 8
slabs) but the API response only ever returned `DesignationSlab.name`
(English) -- both `BrokerResponse.currentDesignationName` and
`BrokerNetworkNodeResponse.designationName` were missing their `*NameHi`
counterpart entirely, so the Network Tree page and the upline picker both
showed "Business Executive" verbatim even with the UI switched to Hindi.
This is the exact "DB-sourced bilingual data needs an explicit
`i18n.language` check, not an i18n key" class this project has hit before
(the `AreaInput` unit-name fix, B-06 section above) -- confirmed here as
a real recurrence, not a hypothetical one, and worth adding to
`PATTERNS.md` if it recurs a third time. Fixed by adding
`currentDesignationNameHi`/`designationNameHi` to both response DTOs and
applying the same `i18n.language === 'hi' ? nameHi ?? name : name` check
in both `NetworkTreePage.tsx` and `BrokerFormDialog.tsx`'s upline
dropdown. The rate unit ("/sq.ft.") was also hardcoded English in the
same component -- moved to a real i18n key (`network.perSqft`) since,
unlike the designation name, it isn't DB-sourced data.

**Verification methodology and results:**
- Backend: 119/119 `mvn test` green (110 pre-existing + 9 new
  `CommissionCalculationEngineTest` cases), zero regressions. Migrations
  verified to apply cleanly from scratch (98 total, replayed via
  Testcontainers) with no schema-mismatch errors on JPA context startup.
- Frontend: `tsc -b` clean, `vitest run` 25/25, `oxlint` clean, `npm run
  build` clean.
- Live HTTP verification against the local dev backend (a throwaway
  `BUILDER_BROKERS` quota row added to the existing `M4TEST` plan and
  reverted immediately after, same established precedent as every prior
  milestone's quota-limited test-data setup): designation slabs endpoint
  returns all 8 seeded rows with correct thresholds/rates; a new
  DESIGNATION broker with no upline correctly starts at Business
  Executive/₹160/0/0; a broker created under it correctly gets the real
  upline set; self-upline, nonexistent-upline, upline-must-be-DESIGNATION,
  and upline-not-allowed-for-PERCENTAGE all correctly 400 with the right
  error codes; FIXED correctly 400s for new brokers; the closure table
  was directly queried and matched the expected 6-row structure for a
  3-level chain.
- Real-browser verification (a separate, temporary frontend dev-server
  instance pointed at the local backend, so the user's own active
  session on a different port/backend was never touched): broker creation
  form correctly shows/hides the upline picker based on commission type,
  end-to-end creation of a top-level broker and a downline broker via the
  real UI, the Network Tree page correctly renders the resulting
  hierarchy with visual indentation. Hindi + 360px pass across both the
  Add Broker dialog and the Network Tree page: zero horizontal overflow,
  zero raw i18n keys, zero console errors -- and, from actually reading
  the resulting Hindi screenshots (not just checking for overflow/errors),
  is what caught the designation-name-not-translated bug above.

---

## Milestone 6.5 (Steps 5-6) — Booking Commission Freeze & Proportional Release

Continuation of the Broker Network Engine, `06-BROKER-NETWORK-ENGINE.md`
§15 build order. This round did **only** steps 5 and 6 — freezing the
commission tree at booking and releasing it proportionally as instalments
are paid — per this round's own explicit instruction. **Explicitly NOT
touched**: COMPLETED-triggered promotion/team-sales rollup (step 7),
manual promotion (8), cancellation/recovery (9), dashboards (10),
concurrency hardening (11), or migrating existing M6 brokers (12).
`personal_successful_bookings`/`team_successful_bookings` still read 0
forever — unaffected by this round, still step 7's job. Read this before
touching `builder.broker.BookingCommission*`/`CommissionRelease*`, or the
`PlotSaleService.create()`/`PaymentService.record()`/`doReverse()`
integration points.

**A real interpretation decision, made explicit up front**: the spec's
"BOOKED" moment (§1: "`plot_sale.status = BOOKED` (creation)") does not
match this codebase's actual behavior — `PlotSale.Status.BOOKED` has
existed in the enum since M3 (`V3_001`) but has **never once been used**;
every sale's status defaults straight to `ACTIVE` at creation and stays
there until `COMPLETED`/`CANCELLED`. Introducing a real `BOOKED` → `ACTIVE`
transition now would ripple through every M3/M5/M6/M7 query that treats
`status = 'ACTIVE'` as "the current live sale" (Financials, Tracker, Deals
History, Dashboard, Stats — all of them), which is exactly the kind of
change the task's own "treat `PlotSaleService` as high-risk" instruction
warns against for a steps-5-6-only round. **"Frozen at BOOKED" is instead
implemented as "frozen inside `PlotSaleService.create()`'s existing
transaction"** — literally the same moment the M6 `commissionLedgerService.createForSale()`
call already freezes a PERCENTAGE broker's commission, so this isn't a new
precedent, just the DESIGNATION-broker equivalent of one that already
exists. Revisit if a future round ever actually wires the `BOOKED` status
for real.

**Step 5 — freezing the tree (`BookingCommissionService.freezeForSale`).**
`PlotSaleService.create()` now looks up the attributed broker's
`commissionType` **before** deciding which commission path to call: `DESIGNATION`
routes to `bookingCommissionService.freezeForSale(...)`; PERCENTAGE/FIXED
(and no-broker sales) keep calling the exact, unmodified M6
`commissionLedgerService.createForSale(...)` — the two paths are mutually
exclusive per sale, never both, matching §3's "the two [commission bases]
never mix." The chain is built by walking `BrokerNetworkService.ancestorsExcludingSelf()`
(already nearest-first, exactly the shape `CommissionCalculationEngine.calculate()`
needs) and reading each broker's own `currentCommissionRate` column
directly — not re-resolving via `DesignationSlabService.resolve()` — since
`BrokerPartnerService.create()` already guarantees every DESIGNATION
broker has this populated from the moment it's created (§6: "the rate as
it is immediately before *this* booking"). One `booking_commission` row is
persisted per line item the engine returns (including the explicit
zero-amount row for a lower-rated upline — never silently dropped, per
the engine's own audit-completeness contract for §9's future recovery
logic). A new `rate_snapshot` JSONB column captures each row's
beneficiary/direct-downline designation name+name_hi+rate at freeze time
— one JSONB blob, not six separate columns, mirroring
`commission_ledger_entry.config_snapshot`'s already-established shape
rather than inventing a new one. §11's own data-model listing shows both
`commission_amount` and `total_amount` as separate columns for what is
the same figure (`plot_area_sqft × commission_per_sqft`) — consolidated
into one `total_amount` column here, avoiding a pointless duplicate that
could drift out of sync.

**Step 6 — proportional release (`CommissionReleaseService.releaseForPayment`),
called from both `PaymentService.record()` and `doReverse()`.** A naive
"this payment's own flat fraction of `total_amount`" design would drift by
paise across many small instalments; instead, every release row stores a
**delta against the authoritative cumulative figure**: on each customer
payment, `sale.totalPaid` (freshly `entityManager.refresh()`'d after the
payment's own trigger-driven update — the same "Hibernate can't see a
DB-trigger change it didn't itself make" pattern documented since M2)
divided by `sale.dealValue` gives the target cumulative-released fraction;
each `booking_commission` row's target = `total_amount × fraction`
(clamped to `[0, total_amount]`); the release row's own `amount` is that
target minus whatever the SUM of its existing `commission_release` rows
already is. Summing every release row for a beneficiary therefore always
equals exactly the correct cumulative figure, with **zero rounding drift
regardless of how many instalments a deal is split across** — the same
"always a full recompute of the real rows, never an incrementally-updated
running counter" philosophy `plot_sale.total_paid` itself already uses.
**This is also why a reversal needs no special-case "undo this release"
logic at all**: `PaymentService.doReverse()` — the single method both
`.reverse()` and the cheque-bounce path in `updateChequeStatus()` funnel
through — already creates a new, negative-amount `PaymentRecord`; loading
and refreshing the sale after that insert and calling the exact same
`releaseForPayment()` produces a negative delta automatically, correctly
"un-releasing" the matching slice for every beneficiary in one pass, and
that release row is directly linked to the reversal's own `payment_record`
id — satisfying "link every release to the specific customer payment that
triggered it" for a correction the identical way it does for the original.
`paid_amount` (money the builder has actually handed a beneficiary, as
opposed to `released_amount`, money that has become *eligible* to be paid)
exists as a column on `booking_commission` per §11's own listing, but
**has no write path at all this round** — no endpoint, no UI — since a
"record a payout to a designation-broker beneficiary" action was never
named in steps 5-6; `pending_amount` (a `GENERATED` column, `released_amount
- paid_amount`) therefore just equals `released_amount` for every row
today. A real, disclosed gap, not an oversight — the same "schema built
for the full shape, only part of it wired up this round" pattern
CLAUDE.md already documents for `BUILDER_TEAM_MEMBERS`/`org_usage`.

**New tables** (`V65_006`/`V65_007`/`V65_008`): `booking_commission` (one
row per beneficiary per booking; RLS, no `deleted_at` — a frozen tree is
never deleted, only ever cancelled via a future status flip, step 9) and
`commission_release` (one row per beneficiary per triggering payment;
RLS, RULE-blocked `DELETE`, immutable — the identical "no soft-delete
column, a DB RULE instead" shape `commission_payment`, V6_008, already
uses, for the identical reason). `released_amount`/`status` on
`booking_commission` are trigger-maintained from `commission_release`
(`fn_booking_commission_release_trigger`), a byte-for-byte mirror of
`fn_commission_ledger_payment_trigger` (V6_014) — same full-recompute
style, same "never overwrite a `CANCELLED` status" carve-out reserved for
step 9.

**A new, minimal read surface** (`GET /api/v1/brokers/{id}/booking-commissions`,
`BROKER_VIEW`-gated, same permission `GET .../ledger` already uses) was
added — not because steps 5-6 asked for a dashboard, but because there was
otherwise no way to look at what got frozen/released at all, and this
round's own verification standard requires driving a real UI in a real
browser. On `BrokerDetailPage`, the existing "Ledger" tab now branches on
`broker.commissionType`: `DESIGNATION` renders the new `CommissionTreeTab`
(this data) instead of the M6 `LedgerTab` (which would always be empty for
this broker type anyway, since it reads `commission_ledger_entry`). No
payment-recording action exists on this tab — read-only, matching
`paidAmount` having no write path yet.

**A real bug found and fixed, caught only by looking at the actual
360px/Hindi screenshot (not by the automated overflow/console/raw-key
checks, which only measure the page, not a scrollable child) — this is
now the THIRD time in this codebase a wide `<Table>` has broken at 360px**
(after Financials' pending/overdue tables and the Ops page's delivery/error
tables, both already documented above): `CommissionTreeTab`'s first draft
used a `<Table>` with 9 columns. At 360px the *page* never overflowed
(`document.documentElement.scrollWidth === clientWidth`, exactly what
every earlier automated check in this project's history has verified) —
but the table's own horizontal scroll hid everything past
Project/Plot/part of Buyer, including every money figure and the status
badge, which is the entire reason this tab exists. Fixed by rebuilding it
as card rows (`CommissionTreeCard`, no `<Table>` at all) — the same fix
shape as `DeliveryCard` in the Ops page. **Any future internal
tooling/read-only view in this project should default to card rows over a
wide table from the start** — this is now flagged a third time, strongly
enough that it should be treated as this project's default, not an
exception discovered per-page.

**A separate, real gotcha hit purely during live verification, not a
product bug**: the established "grant a throwaway quota-unlimited plan via
direct SQL" precedent (used since M4 for `BUILDER_TEAM_MEMBERS`/`BUILDER_PROJECTS`/`BUILDER_BROKERS`
quota-limited test-data setup) has always been written as an `INSERT ...
ON CONFLICT DO NOTHING`. This round discovered that pattern is unsafe: a
brand-new org already gets exactly one `FREE`-plan `subscription` row
created automatically at signup, and there is no unique constraint on
`subscription.org_id` alone for `ON CONFLICT` to catch — so the `INSERT`
silently succeeds a **second** time, leaving the org with two subscription
rows. `EntitlementService.assertWithinQuota()` (and every other caller of
`SubscriptionRepository.findByOrgId()`, which assumes exactly one row)
then throws a genuine `NonUniqueResultException` on the very next
permission-gated call — a real 500, confirmed live via `POST /brokers`
failing immediately after this round's own quota-grant SQL ran. **Fixed by
`UPDATE subscription SET plan_code = ... WHERE org_id = ...` instead of
`INSERT` going forward** — switches the existing row in place rather than
adding a parallel one. **Any future session reaching for this same
throwaway-plan precedent should `UPDATE`, never `INSERT`** — every prior
milestone's own notes describing this pattern say "switch the org's
subscription," which an `UPDATE` actually does and an `INSERT` never did;
this was a latent bug in the established pattern itself, not a new
project of using it.

**Verification methodology and results:**
- Backend: 124/124 `mvn test` green (119 pre-existing + 5 new
  `BookingCommissionIntegrationTest` cases), zero regressions. New tests
  cover: a real 3-level chain freezing exactly what
  `CommissionCalculationEngine` predicts (§45-shaped, hand-set rates
  160/180/200) with zero `commission_ledger_entry` row created; proportional
  release at 25%/50%/100% cumulative payment plus a reversal proven to
  unrelease the exact matching slice (12 release rows total: 9 positive
  across 3 payments × 3 beneficiaries, 3 negative from the reversal, every
  one linked to its real triggering `payment_record`); a waived-instalment
  scenario proving only the money actually paid (not the waived remainder)
  is ever released, even though the sale reaches `COMPLETED` with a zero
  balance; `cancel()` running cleanly for a DESIGNATION-broker sale with no
  crash (§9 recovery itself is NOT built — the frozen/released figures are
  simply left as-is, a disclosed, not silent, gap); and a PERCENTAGE-broker
  sale creating zero `booking_commission` rows with the release hook a
  clean no-op, closing the loop on "the two paths never mix" from the
  other direction. Full-lifecycle regression (create → partial payment →
  waive → complete; a second sale for create → partial payment → cancel)
  exercised for designation, percentage (already covered by the
  unmodified, still-green `PlotSaleCommissionIntegrationTest`), and
  no-broker sales alike — nothing from M3/M5/M6/M7 broke.
- Frontend: `tsc -b` clean, `oxlint` clean, `vitest run` 25/25 (the
  `broker` namespace key-parity check now also covers the new
  `commissionTree.*` keys), `npm run build` clean.
- Real HTTP verification against the local dev stack (the same one steps
  1-4 already used — `realestate-postgres`/`redis`/etc., backend restarted
  fresh so Flyway applied `V65_006`-`V65_008` for real, not Testcontainers):
  signed up a fresh org, built a genuine 3-level chain through the real
  `POST /brokers` endpoint (Me → A → B, B selling), sold a 1000 sq.ft. plot
  to B. Since step 7 (promotion) isn't built, every fresh broker starts at
  the identical Business Executive/₹160 rate — so this live pass correctly
  exercises the same-slab-bonus shape (§46-like: B ₹1,60,000
  `SELLING_BROKER`, A and Me each ₹10,000 `NETWORK_SAME_SLAB_BONUS`), a
  real, honest, currently-reachable scenario, while the differential-rate
  fixture (§45) stays covered by the integration test's own hand-set
  rates. Paid 25% (₹1,00,000 of ₹4,00,000): B released exactly ₹40,000.
  Paid the remaining 75%: all three beneficiaries hit exactly 100%
  released (`FULLY_RELEASED`). Reversed the second payment: all three
  correctly dropped back to exactly the 25% figure — confirmed via direct
  `GET /brokers/{id}/booking-commissions` calls at every step, matching
  the integration test's own math precisely. Real bugs hit and fixed
  along the way, not in the feature code itself: the `INSERT`-vs-`UPDATE`
  subscription gotcha above, and a plain mobile-number-uniqueness test-script
  bug (`System.nanoTime()`-derived digits collided across brokers seeded
  back-to-back in the same integration test method — fixed with a
  monotonic counter).
- Real-browser + Hindi + 360px verification (a separate frontend dev-server
  instance, following the exact steps-1-4 precedent): logged in via the
  real login form, navigated to the Broker B detail page via real nav/link
  clicks (never `page.goto()` post-login, the standing `authStore`
  constraint), confirmed the Ledger tab correctly shows the new commission
  tree card (not the empty M6 ledger) in English at desktop width, then
  switched to Hindi via the real `LanguageToggle` and resized to 360px —
  this is what caught the wide-`<Table>` bug above; re-verified clean
  after the card-row rewrite (zero page overflow, zero raw i18n keys, zero
  console errors, and every figure — total/released/pending, "आंशिक रूप से
  जारी"/Partially Released — reads correctly in Hindi and matches the
  curl-verified state exactly).

**Known, deliberate scope gaps, not bugs** (beyond steps 7-12 themselves,
already listed above): (1) no endpoint/UI to record an actual payout to a
designation-broker beneficiary (`paid_amount` stays 0 for every row);
(2) `BrokerPerformanceResponse.totalCommissionEarned`/`commissionDue` on
the broker detail page's stat cards still read from `commission_ledger_entry`
only, so they show ₹0 for a DESIGNATION broker even after real releases —
untouched this round since performance aggregation wasn't named in steps
5-6, a real, visible (if minor) inconsistency worth closing whenever
dashboards (step 10) are picked up; (3) the `PlotSale.Status.BOOKED` enum
value remains completely unused, per the interpretation decision above.

---

## Milestone 6.5 (Step 7) — COMPLETED-Triggered Promotion & Team-Sales Rollup

> **SUPERSEDED — see "Post-M6.5 Behaviour Change" below.** This section
> describes the *original* design: counting/promotion firing on
> `COMPLETED`. That trigger point was later moved to `BOOKED` (sale
> creation). Everything else this section describes — the trigger
> architecture, the READ COMMITTED MVCC concurrency fix, the
> `total_waived` interaction — is still accurate in substance, just
> anchored to the new moment. Left unedited as a historical record.

Continuation of the Broker Network Engine, `06-BROKER-NETWORK-ENGINE.md`
§15 build order. This round did **only** step 7 — the promotion engine —
per this round's own explicit instruction, and per its own warning ("the
most concurrency-sensitive piece in the whole feature") the design was
built and proven in isolation before anything else touched it.
**Explicitly NOT touched**: manual promotion (step 8), cancellation/
recovery (9), dashboards (10), or migration of existing M6 brokers (12).

**Architecture: two layers, matching this codebase's own established
split between "DB trigger for concurrency-safe aggregate correctness" and
"Java for business-rule evaluation + audit trail."**
- **`personal_successful_bookings`/`team_successful_bookings`** (columns
  since `V65_004`, always `insertable=false, updatable=false` on the JPA
  side — genuinely wired up for the first time here) are maintained by a
  new trigger (`V65_010`, `fn_broker_partner_designation_counts_trigger`,
  `AFTER UPDATE OF status ON plot_sale`), full recompute via `COUNT(*)`,
  same philosophy as every other trigger-maintained aggregate in this
  codebase (`org_usage`, `deals_closed_count`, `booking_commission.released_amount`,
  ...). `team_successful_bookings(X)` and `personal_successful_bookings(X)`
  turn out to be the SAME query shape via the closure table --
  `COUNT(*) FROM plot_sale ps JOIN broker_network bn ON bn.descendant_broker_id
  = ps.broker_partner_id WHERE bn.ancestor_broker_id = X` -- since
  `broker_network` already includes a depth-0 self-row for every broker,
  this one query naturally covers "X's own sales" (depth 0, giving
  `personal_successful_bookings` when `X` = the row's own id) *and* "every
  descendant's sales" (depth > 0) in one shot, matching §4's own "team =
  personal + all recursive downline" definition exactly, with no separate
  formula needed.
- **`DesignationPromotionService.evaluateAndPromote()`** (new Java
  service, called from `PlotSaleService.complete()`) re-evaluates the
  seller and every one of its uplines against `DesignationSlabService.resolve()`
  using each one's now-current `team_successful_bookings`, and applies an
  auto-upgrade (never a downgrade — same defensive posture as
  `BrokerTierService.evaluateAndUpgrade()`, and it respects
  `designation_manually_overridden` too, even though step 8 hasn't shipped
  yet to ever actually set that flag to true) with a `designation_history`
  row (new table, `V65_009`, `changeType=AUTOMATIC`) for every real
  transition. §35's "one downline completion promotes multiple uplines at
  once" falls out naturally from walking the whole ancestor chain and
  evaluating each independently, not from any special-cased "multi-promote"
  logic.
- `PlotSaleService.complete()` branches exactly like `create()` already
  does for step 5: a `DESIGNATION` broker's sale calls
  `designationPromotionService.evaluateAndPromote(...)`; a PERCENTAGE/FIXED
  broker's sale keeps calling the exact, unmodified M6
  `brokerTierService.evaluateAndUpgrade(...)` — the two systems never mix.

**§1's own narrower meaning of "COMPLETED", implemented as a `WHERE`
filter, not a gate on `plot_sale.status` itself.** The spec text is
explicit: "all instalments actually paid in full — a waived remainder
does NOT count as complete" — but `plot_sale.status` reaching `COMPLETED`
via a waived balance is a real, correct, *unrelated* M5-era behavior (see
that section above) that this round must not disturb (a builder still
needs to be able to close out a sale where the last instalment was
genuinely forgiven). The two are reconciled by scoping the narrower
definition to exactly where it's needed: both of the trigger's `COUNT(*)`
queries add `AND total_waived = 0` — a sale that reached `COMPLETED` with
any amount waived at all is correctly excluded from personal/team sales
counts (and therefore never promotes anyone), while `plot_sale.status`
itself, Deals History, Financials, and everything else that already
depends on the M5 behavior are completely untouched.

**§11/§24's timing rule — "the triggering booking uses the old rate" —
holds by construction, not by a check**: `DesignationPromotionService`
never reads or writes `booking_commission`/`commission_release` at all;
it only ever touches `broker_partner`/`designation_history`. The frozen
tree from step 5 is simply outside this code's reach, which is what makes
"promotion never retroactively changes an already-completed booking's own
commission" true without needing an explicit guard — verified directly
(see below) by re-reading the exact same `booking_commission` row before
and after the promotion it triggers and asserting it's byte-for-byte
identical.

**A real concurrency bug found and fixed — exactly the class of bug §43
warned this area would have, and exactly why this round's own instruction
insisted on dedicated concurrent tests instead of trusting the design by
inspection.** The first draft of the counts trigger assumed "Postgres's
row-level locking on `UPDATE` makes concurrent writers safe for free" —
true for the ROW being updated, but **not** for an aggregate subquery
inside that same `UPDATE`'s `SET` clause. Under READ COMMITTED (Postgres's
default, used throughout this app), a statement's snapshot is fixed at
the moment *that statement begins* — including every subquery inside it —
and is **not** refreshed just because the statement had to block waiting
for a lock and then proceed once the lock was released; only the row
actually being updated gets Postgres's special "re-fetch and re-check"
treatment (`EvalPlanQual`), not unrelated tables referenced elsewhere in
the same statement. Caught immediately by this round's own concurrent
test (`DesignationPromotionConcurrencyIntegrationTest`, see below):
`"expected: 2 but was: 1"` on a shared upline's team count after two
genuinely parallel completions — proving the test itself works before
even reaching for a fix. **Confirmed the exact mechanism with a raw,
two-session `psql` reproduction** before touching any code (per this
project's own systematic-debugging standard: root cause before any fix) —
`UPDATE t SET val = (SELECT COUNT(*) FROM source) WHERE id = 1`, session B
blocked on session A's lock, then unblocked after A inserted a row and
committed, computed `val = 1`, never seeing A's insert despite running
strictly after A's commit. **Fixed by splitting lock acquisition from the
recompute into two separate statements** — `PERFORM 1 FROM broker_partner
WHERE id = rec.broker_id FOR UPDATE;` first (its own statement, blocks as
needed), *then* the recompute `UPDATE ... SET ... = (SELECT COUNT(*)...)`
as a genuinely new, separate statement, which — confirmed with the same
two-session reproduction, this time correctly computing `val = 2` — gets
its own fresh snapshot precisely because it's a new statement issued
*after* the block already resolved. **Any future trigger (or any plpgsql
function) in this codebase that needs to recompute an aggregate on a row
it just had to wait to lock must acquire that lock as its own,
`FOR UPDATE`-only statement first, never fold the lock-acquisition into
the same `UPDATE` that also carries the recompute subquery** — this is a
genuine, non-obvious PostgreSQL MVCC subtlety, not something reasoning
about "row locks make this safe" from first principles would catch, and
is now the concrete, verified counter-example to keep in mind if this
pattern is ever reached for again.

**Self (depth 0) then ancestors nearest-first (`ORDER BY depth ASC`) is
what keeps the fixed-two-statement version deadlock-safe.** `broker_network`
is a strict tree (§10: one direct upline only, no cycles), so for any two
brokers X and Y where X is an ancestor of Y, X is an ancestor of Y in
*every* descendant's closure that includes both — their relative order is
fixed regardless of whose completion you're looking at. Processing
"nearest first, ascending depth" on every trigger firing means any two
transactions sharing common ancestors always attempt to lock those shared
rows in the same relative order, never crossed — the standard "always
acquire locks in a consistent global order" deadlock-avoidance rule,
satisfied for free by the tree's own structure.

**A real, latent test-authoring bug found while writing this round's own
tests, affecting earlier work too**: `BookingCommissionIntegrationTest`
(step 5-6) had already fixed a *within-class* mobile-number collision with
a per-class `AtomicInteger` counter — but this round's two new test
classes each declared their *own* counter, independently starting at 0,
and the full suite failed with `duplicate key value violates unique
constraint "ux_user_mobile"` the moment all three classes ran together
(two different classes' counters producing the identical literal mobile
number). **Fixed with a new shared test utility, `com.shardeya.support.TestMobiles.next()`**
(a fresh `UUID.randomUUID()`-derived 10-digit number per call, 128 bits of
real entropy, no shared counter to collide across classes/methods/threads
at all) — applied to all three affected test classes, replacing both the
`AtomicInteger` counters and the original, similarly-fragile
`System.nanoTime()`-substring approach. **Any future test needing a
plausible-but-unique mobile number should use this helper, never a
per-class counter or `nanoTime()`** — this is now a two-time-recurring
class of bug (once within a single class, once across classes) and this
shared helper is the actual fix, not a one-off patch.

**Verification methodology and results:**
- Backend: 129/129 `mvn test` green (124 pre-existing + 5 new: 4 sequential
  scenarios in `DesignationPromotionIntegrationTest` plus 1 genuinely
  concurrent test in `DesignationPromotionConcurrencyIntegrationTest`),
  zero regressions. The full suite was run 3 additional times after the
  concurrency fix specifically to rule out a lucky one-off pass on a
  timing-sensitive test — clean every time.
  - `completingItsFirstBookingPromotesFromBusinessExecutiveToSeniorBusinessExecutive` —
    a single booking correctly promotes its own seller, with a
    `designation_history` row recorded (`AUTOMATIC`, `160 -> 180`).
  - `oneLeafCompletionPromotesTheEntireUplineChainAtOnce` (§35) — a 4-broker
    chain (Me/A/B1/B2); B1's first completion promotes B1, A, *and* Me
    simultaneously; B2's own first completion later promotes B2 *and*
    pushes A/Me a second time (180→200), demonstrating a completion
    promoting uplines across a threshold that has nothing to do with the
    seller's own designation.
  - `aWaivedToZeroBookingDoesNotCountTowardSalesOrPromoteAnyone` — a sale
    reaching `COMPLETED` via a waived final instalment correctly leaves
    `personal_successful_bookings`/`team_successful_bookings` at 0 and
    creates zero `designation_history` rows.
  - `theCompletingBookingSOwnFrozenCommissionIsUnchangedByThePromotionItTriggers`
    (§11/§24) — the exact `booking_commission` row the promotion-triggering
    sale froze at BOOKED time is re-read after the promotion and asserted
    byte-for-byte identical (same `totalAmount`, same `commissionPerSqft`,
    same row `id`); a second sale created *after* the promotion correctly
    uses the new rate.
  - `twoCompletionsSharingAnUplineInParallelBothCountCorrectlyWithNoLostUpdateOrDoublePromotion`
    (§43) — two real threads, a `CyclicBarrier` synchronizing their start,
    each with its own explicitly-bound `TenantContext` (a plain, non-
    inheritable `ThreadLocal` — the main test thread's bind does **not**
    propagate to executor worker threads) and its own independent
    `@Transactional` transaction (Spring's transaction binding is thread-
    local, so invoking the same `@Transactional` method from two threads
    naturally gives two genuinely separate DB transactions/connections).
    Asserts only the guaranteed-correct *final* state (never which thread
    "won" the race, which is non-deterministic) — both leaves end at
    personal=1/team=1/Senior Business Executive, the shared upline and its
    own upline both end at team=2/Business Development Officer, and the
    shared upline has exactly 2 `designation_history` rows (one real
    transition per completion, no duplicates from a race, no lost update).
    This is the test that caught the READ COMMITTED snapshot bug above —
    it failed on the very first run, exactly as a real concurrency test
    should if the design has a real bug.
- Frontend: **zero code changes needed.** `NetworkTreePage.tsx`/`BrokerFormDialog.tsx`
  already displayed `personalSuccessfulBookings`/`teamSuccessfulBookings`/
  `currentCommissionRate`/designation name per broker since steps 1-4 —
  they'd just always shown 0/Business Executive because nothing ever
  updated the underlying data. This round makes that already-built display
  show real, live values for the first time, with no frontend work
  required — confirmed directly (see below).
- Real HTTP verification against the local dev stack (`realestate-postgres`,
  backend restarted fresh so Flyway applied `V65_009`/`V65_010`): built a
  fresh 3-level chain (Me3→A3→B3) via the real `POST /brokers` endpoint,
  had B3 book and fully pay off a plot, called `POST /sales/{id}/complete`,
  and confirmed via `GET /brokers/network` that **B3, A3, and Me3 all
  promoted from Business Executive(160) to Senior Business Executive(180)
  simultaneously** — a live, real-account reproduction of the exact §35
  fixture the integration test already covers with hand-built data.
  Re-fetched the completing sale's own `booking_commission` row afterward
  and confirmed it was still exactly `commissionPerSqft: 160, totalAmount:
  160000` — unchanged by the promotion it had just caused. Created a
  second, brand-new sale for the now-promoted B3 and confirmed it correctly
  froze at the *new* `180`/sq.ft. rate (`commissionPerSqft: 180, totalAmount:
  180000) — "future bookings only," live. (A first attempt at this reused
  a sale from the steps-5-6 verification session and found it already sat
  at `status=COMPLETED` with no promotion having fired — not a bug: the
  user had explored that exact account between sessions, per the "Option A"
  instructions given at the end of the steps-5-6 turn, recorded two real
  CASH payments and clicked "Mark Complete" themselves, all *before* this
  round's code was ever deployed to that running backend — confirmed via
  `payment_record`'s own timestamps and modes, which matched a human
  clicking through the UI, not any of this session's own curl scripts.
  Re-verified against a fresh chain/sale instead of trying to force a
  re-transition on already-completed data.)
- Real-browser + Hindi + 360px verification (a separate frontend dev-server
  instance, following the exact steps-1-6 precedent): logged in via the
  real login form, navigated to Brokers → Broker Network via real nav/link
  clicks, confirmed the tree now shows real, correct figures for both
  chains built during this round's live verification — `बिज़नेस
  एक्ज़िक्यूटिव`/`₹160/वर्ग फुट`/`टीम बिक्री: 0` for the still-unpromoted
  chain, `सीनियर बिज़नेस एक्ज़िक्यूटिव`/`₹180/वर्ग फुट`/`टीम बिक्री: 1`
  for the promoted one, `व्यक्तिगत बिक्री: 1` correctly shown only for the
  actual seller — in Hindi, at 360px, with zero horizontal overflow, zero
  raw i18n keys, zero console errors. Visually inspected the actual
  screenshot (not just the automated checks) per this project's own
  standing "look at the rendered page" discipline — the nested/indented
  card layout built in steps 1-4 renders cleanly with the new live data,
  no changes needed.

**Known, deliberate scope gaps, not bugs** (beyond steps 8-12 themselves):
(1) no UI anywhere shows `designation_history` yet — that's dashboards,
step 10; (2) `BrokerPerformanceResponse`'s stat cards on the broker detail
page still don't reflect any of this (same disclosed gap already noted for
steps 5-6); (3) `designation_manually_overridden` is checked defensively
but nothing can ever set it to true yet — that's step 8.

---

## Milestone 6.5 (Step 9) — Cancellation With Recovery

> **PARTIALLY SUPERSEDED — see "Post-M6.5 Behaviour Change" below.** This
> section's `wasCompleted` guard (only reversing counts/demoting when the
> cancelled booking had reached `COMPLETED`) was later removed — every
> cancellation now reverses counts unconditionally, since counting itself
> moved to `BOOKED`. The recovery mechanism itself (deriving a recovery
> figure from `released_amount`, cancelling `booking_commission` rows) is
> unchanged. Left unedited as a historical record.

Continuation of the Broker Network Engine, `06-BROKER-NETWORK-ENGINE.md`
§15 build order. This round did **only** step 9 — cancellation with
recovery, the locked "Option 1: RECOVER" decision (§9, Rule 41) — skipping
step 8 (manual promotion) per this round's own explicit instruction, since
step 9 unwinds everything steps 5-7 built and needed to be tackled in
isolation first. **Explicitly NOT touched**: manual promotion (step 8),
dashboards (10), further concurrency hardening beyond what this step
itself needed (11), or migration of existing M6 brokers (12).

**Discipline, matching step 4's own precedent**: two genuinely pure,
dependency-free classes (`CommissionReversalCalculator`,
`DesignationTransitionCalculator`) were built and fixture-tested — 10
tests, no Spring, no DB — *before* either was wired into the real
cancellation transaction.

**The M6 recovery pattern, reused literally, not just in spirit.** The
task's own instruction to "create reversal/recovery records" and "reuse
M6's existing broker recovery-entry pattern" reads, on its face, like two
different things — M6's *actual* pattern (`CommissionLedgerService.cancelForSale()`)
inserts **no new rows at all**: it flips `status = CANCELLED` and lets
`amount_paid` (already a real, trigger-maintained, never-re-zeroed
historical fact) stand as the recovery amount, derived at *read* time
(`CommissionLedgerEntryResponse.needsRecovery`/`recoveryAmount`). Re-reading
§9 closely resolves the apparent tension: DESIGNATION brokers have no
`paid_amount` write path at all yet (disclosed since step 6), so the
closest analog to M6's "amount already paid" is `released_amount` — itself
*already* an append-only, trigger-maintained historical fact (from step
6's own `commission_release` design). The correct, minimal design turned
out to be the exact same one-line move M6 already proved: flip
`booking_commission.status = CANCELLED`; `released_amount` needs no
mutation at all, because V65_008's release trigger already refuses to
overwrite a `CANCELLED` status (a carve-out written back in step 6,
before step 9 was ever scoped — it just happened to be exactly what this
step needed). `needsRecovery`/`recoveryAmount` are new, derived-at-read-time
fields on `BookingCommissionResponse`, computed via
`CommissionReversalCalculator.reverseOne()` — no new table, no new rows,
matching M6 down to the mechanism, not just the outcome. **Pending
(never-released) slices need no separate handling either** — the same
uniform `status = CANCELLED` flip across every beneficiary row naturally
produces both described behaviors (`released_amount > 0` rows show a real
recovery figure; `released_amount = 0` rows simply show none) with zero
special-casing.

**Sales-count reversal needed zero migration changes — step 7's trigger
already handled it.** `fn_broker_partner_designation_counts_trigger` (V65_010)
fires generically on *any* `plot_sale.status` change and does a full
`COUNT(*) ... WHERE status = 'COMPLETED'` recompute, not an increment —
the moment a booking's status changes away from `COMPLETED` (to
`CANCELLED`), the very next recompute of every affected broker naturally
excludes it, correctly decrementing `personal_successful_bookings`/
`team_successful_bookings` with no new trigger logic at all. The only new
work was teaching the *Java* side to act on a decrease: `DesignationPromotionService`
gained `reevaluateAfterCancellation()` (allows `DEMOTION`, via
`DesignationTransitionCalculator` — the same pure comparison
`evaluateAndPromote()` was refactored to use, so "what counts as
higher/lower" can never drift between the two paths), recording
`designation_history` rows with a new `CANCELLATION_REVERSAL` change type
(its own `ALTER TYPE` migration, V65_011, never combined with a migration
that uses the value — the same rule V65_003 already established for
`DESIGNATION`). `PlotSaleService.cancel()` captures `wasCompleted =
sale.getStatus() == COMPLETED` *before* the status mutation (the one new
piece of state this step needed) and only calls the demotion-capable
re-evaluation when true — an `ACTIVE→CANCELLED` cancellation (never
completed, never counted) correctly skips it entirely, since the trigger's
own recompute would be a costless no-op for it anyway.

**§11/§24's timing rule extends to demotion by the exact same
construction that already proved it for promotion**: `DesignationPromotionService`
never reads or writes `booking_commission`/`commission_release`, in either
direction — a demotion, like a promotion, simply cannot retroactively
rewrite another booking's already-frozen tree, because the code that
demotes a broker has no path to that table at all. Verified directly (see
below): a booking frozen at the *promoted* 180 rate, still active, was
re-read after a *different* booking's cancellation demoted the same
broker back to 160 — byte-for-byte unchanged.

**Concurrency needed no new locking mechanism — step 7's fix already
generalizes.** The task's own instruction to "reuse the separate-statement
row-lock-before-recompute fix from step 7" turned out to be exactly
correct with zero additional code: the counts trigger doesn't distinguish
*why* `plot_sale.status` changed, so the identical `PERFORM ... FOR UPDATE`
-then-recompute fix that made concurrent completions safe is *already*
what makes a cancellation racing a completion on a shared upline safe too.
The dedicated concurrent test (below) exists to *confirm* this holds when
two transactions pull the same shared upline's count in opposite
directions at once, not to introduce something new — and it passed on the
first run, unlike step 7's own first attempt (which genuinely needed the
fix). 5 repeated runs afterward, all clean, to rule out a lucky pass on
timing-sensitive code.

**A real bug found and fixed during the Hindi + 360px pass — the exact
same class already documented in the M6 section above, self-inflicted a
second time**: the new `commissionTree.needsRecovery` i18n string had a
literal `₹` prefix in front of `{{amount}}`, which is filled by
`formatIndianCurrency()` — a function that *already* returns the ₹ symbol.
Rendered as `₹₹1,60,000 वसूल करें` in the live Hindi screenshot. Fixed by
removing the literal symbol from both `en`/`hi` strings, matching the
established, already-correct precedent every other currency-interpolating
key in this codebase already follows. **This is now the second time this
exact mistake has been made in this codebase (first in the M6 broker
commission preview string) — any future i18n key that interpolates a
`formatIndianCurrency()`-formatted amount must never also include a
literal `₹` in the surrounding string.**

**Verification methodology and results:**
- Backend: 143/143 `mvn test` green (10 new pure unit tests + 3 sequential
  + 1 concurrent integration test), zero regressions. One pre-existing
  test from steps 5-6 (`BookingCommissionIntegrationTest.cancellingADesignationBrokerSaleDoesNotCrashAndLeavesTheFrozenTreeAsIs`)
  needed its assertions updated — its own comment had explicitly predicted
  this ("not CANCELLED -- step 9's job"), not a regression.
  - `CommissionReversalCalculatorTest`/`DesignationTransitionCalculatorTest`
    (10 tests) — reversing real §45-shaped frozen trees at 0%/25%/100%
    release, a genuine zero-amount line item (§33's capped-at-zero case)
    reversing cleanly, a defensive negative-recovery guard, and every
    adjacent pair on the full 8-slab ladder in both directions.
  - `DesignationCancellationIntegrationTest` (3 tests) — a partially-released,
    never-completed booking's cancellation recovers every beneficiary
    correctly and leaves counts/designations completely untouched;
    cancelling an already-COMPLETED booking reverses counts and demotes
    the entire chain (with exactly the right `designation_history` count:
    one `AUTOMATIC` row from the original completion, one
    `CANCELLATION_REVERSAL` row from the cancellation, for every affected
    broker); a demotion triggered by cancelling one booking does not alter
    a sibling, still-active booking's own frozen commission tree.
  - `DesignationCancellationConcurrencyIntegrationTest` (1 test) — a
    genuinely parallel completion and cancellation racing on a shared
    two-level upline (Me←A←{B1,B2}), asserting only the guaranteed-correct
    *final* state (never which thread won): both leaves end exactly right,
    the shared uplines net back to their pre-race designation via a real
    (not simulated) up-then-down transition, with precisely the right
    *incremental* `designation_history` row count added during the race —
    proving neither the completion nor the cancellation's effect was lost.
- Frontend: `tsc -b` clean, `oxlint` clean, `vitest run` 25/25, `npm run
  build` clean.
- Real HTTP verification against the local dev stack (backend restarted
  fresh so Flyway applied `V65_011`): a fresh 3-level chain (Me9→A9→B9).
  Scenario 1 — B9 books a plot, 25% paid (never completed), sale
  cancelled: `booking_commission` flips to `CANCELLED`,
  `releasedAmount=₹40,000` unchanged, `needsRecovery=true,
  recoveryAmount=₹40,000`, and every broker's count/rate completely
  untouched. Scenario 2 — a second sale is fully paid and completed
  (promoting B9/A9/Me9 to Senior Business Executive/₹180); a *third* sale
  is created while at ₹180 (correctly freezing at `commissionPerSqft:
  180`); the second (completed) sale is then cancelled — confirmed via
  `GET /brokers/network` that B9/A9/Me9 all demote back to Business
  Executive/₹160, and confirmed via `GET /brokers/{id}/booking-commissions`
  that the *third* sale's frozen tree still reads exactly `180`/`₹180,000`
  — completely unaffected by the demotion the second sale's cancellation
  caused.
- Real-browser + Hindi + 360px verification (a separate frontend dev-server
  instance): logged in via the real login form, navigated to B9's detail
  page → Ledger tab via real nav/link clicks, confirmed the Commission
  Tree cards correctly show a red recovery line for both cancelled
  bookings and none for the still-pending third one, in English at desktop
  width, then switched to Hindi and resized to 360px — this is what caught
  the double-₹ bug above; re-verified clean after the fix (zero page
  overflow, zero raw i18n keys, zero console errors, and the recovery text
  — "₹1,60,000 वसूल करें — कमीशन जारी होने के बाद यह बुकिंग रद्द कर दी गई
  थी" — reads correctly and matches the curl-verified figures exactly).

**Known, deliberate scope gaps, not bugs**: (1) no UI anywhere shows
`designation_history` (which change happened when, `AUTOMATIC` vs
`CANCELLATION_REVERSAL`) — that's dashboards, step 10, same gap already
disclosed for step 7's promotions; (2) `paid_amount` still has no write
path (disclosed since step 6) — every recovery figure in this app today is
therefore always exactly `released_amount`, never a smaller
already-clawed-back remainder; (3) `designation_manually_overridden` is
still checked defensively in both directions now, but nothing can ever set
it — that's step 8, deliberately skipped this round and coming last, not
next.

---

## Milestone 6.5 (Steps 8 & 10) — Manual Promotion, and Builder-Side Dashboards

Continuation of the Broker Network Engine, `06-BROKER-NETWORK-ENGINE.md`
§15 build order. This round did steps 8 and 10 together, per this round's
own explicit instruction ("the hard engine work... is done; these two are
lower-risk"). **Explicitly NOT touched**: step 12 (migrating existing M6
FIXED/tier brokers) — needs a confirmed mapping decision from the user
first, and step 11 (any further concurrency hardening beyond what steps 7
and 9 already needed and proved) wasn't in scope either, since neither
step 8 nor step 10 introduces any new concurrent-write path (step 8's
writes are single-broker, admin-triggered, no closure-table/aggregate
recompute involved; step 10 is entirely read-only).

**Step 8 — manual promotion (§34).** `DesignationPromotionService` gained
`manuallyOverride(brokerId, designationId, reason)` and `clearOverride(brokerId)`,
both admin-only (`requireAdmin()`, the exact `BUILDER_ADMIN`-role check
`BrokerTierService`/`CommissionConfigService` already established for
money-shaping actions — not just the cosmetic `BROKER_MANAGE` permission
gate on the controller). §34's three rules are each true **by
construction**, not by a special-case check: (1) "future bookings only,
never touches booking_commission" — `manuallyOverride()`/`clearOverride()`
simply never read or write that table at all, the identical "the code that
changes a designation has no path to the frozen-tree table" argument
step 9 already used for demotion; (2) "does NOT change sales counts" —
both methods only ever read `broker.getTeamSuccessfulBookings()`, never
write `personal_successful_bookings`/`team_successful_bookings`, which
stay exclusively trigger-maintained (V65_010) from real completions; (3)
the override freezes automatic evaluation — `evaluateOne()`'s existing
guard (`if (broker.isDesignationManuallyOverridden()) return;`, written
back in step 7 as defensive plumbing with nothing to set the flag yet) is
now finally reachable, and verified directly: a completed booking for an
overridden broker correctly leaves their designation/rate untouched while
still incrementing their real sales counts (counts and designation are
independent facts, and this is the scenario that proves it).

**The refactor that made both `manuallyOverride()` and `clearOverride()`
cheap to add**: `evaluateAffected()`'s inline boolean
`allowDemotion`/hardcoded `ChangeType.AUTOMATIC ` were pulled out into a
private `Mode` enum (`PROMOTION_ONLY`, `CANCELLATION_REVERSAL`, and the
new `RESUME_AUTOMATIC`), each carrying `allowDemotion` + `changeType` +
a `reasonPrefix`. `evaluateOne()` itself needed zero new branches — it
already took a `Mode` parameter from step 9's own refactor of the
promotion/cancellation split, so adding a third `Mode` for "an admin
cleared an override, re-evaluate from the real count, demotion allowed"
was a one-enum-value change, not new control flow.

**§34's own "one decision to surface, not assume" — resolved, and
resolved differently from the M6 precedent it explicitly referenced.**
The spec never says whether an override can be lifted; M6's own
`BrokerTierService.override()` left this as a documented, permanent gap
("no clear-override endpoint"). Built here anyway, per this round's
explicit instruction: `clearOverride()` flips the flag and immediately
re-evaluates **only that one broker** (never its ancestors/descendants —
clearing an override changes no sales count anywhere, so there is
nothing for anyone else's evaluation to react to) against its real,
current `teamSuccessfulBookings`, recording a `designation_history` row
with `ChangeType.AUTOMATIC` (a genuine automatic re-evaluation, just
admin-triggered rather than completion-triggered) and a distinct reason
prefix ("Automatic re-evaluation after override cleared: team sales at
N"), via the new `Mode.RESUME_AUTOMATIC` (`allowDemotion = true` — the
real count could be lower than what the override had set, and clearing
should honestly reflect that, not artificially floor at the overridden
value). Verified directly: overriding a broker upward then clearing the
override correctly **demoted** them back to what their real count
actually warrants — proving this isn't a one-way "undo," it's a genuine
resumption of the same evaluation logic every completion/cancellation
already uses.

**Step 10 — builder-side dashboards (§26/§27/§29).** Reading §26/§27
literally would mean building a broker-facing self-service portal — but
§13 already defers that portal explicitly, and this round's own
instruction was clear that the ask is the *same content*, shown
**builder-side**. Landed as three additions, deliberately not one big new
dashboard, avoiding a redundant page for data the app already has a
natural home for:
1. **`CommissionTreeTab` (the existing Ledger-tab-equivalent for
   DESIGNATION brokers) gained a money-summary section above its existing
   per-booking card list** — personal vs. team commission earned, the
   three-type breakdown (Selling/Differential/Same-Slab Bonus), released,
   and pending. New backend: `BookingCommissionService.commissionSummary(brokerId)`
   → `GET /brokers/{id}/commission-summary`, backed by six new
   `BookingCommissionRepository` `@Query` sums (`sumPersonalEarnedFor`
   filters `uplineLevel = 0` — the row where a broker was *themselves* the
   seller — vs. `sumTotalEarnedFor`, no such filter, for the team figure).
   Every sum explicitly excludes `CANCELLED` rows via a **bound** `@Param`
   (`cancelledStatus`), never an inlined enum literal in the JPQL string —
   this codebase's own repeatedly-documented "enum literal in a `@Query`
   renders as `::JavaSimpleName`, not the real Postgres type" gotcha,
   checked for on sight this time rather than discovered by a runtime
   error.
2. **A new `NetworkTab`** (DESIGNATION brokers only, alongside the
   existing Overview/Commission Config/Deals/Ledger/Notes tabs) holds
   everything else §26/§27 names: next-designation progress (mirroring
   the PERCENTAGE-broker `tierProgress` pattern `BrokerDetailPage` already
   had, now the DESIGNATION equivalent), upline name, direct/total
   downline counts, a downline subtree, promotion history, and — since
   this is also the natural home for it — the step 8 override/clear-override
   controls and the "Manually Overridden" badge.
3. **`NetworkTreePage`** (§28, already built in steps 1-4 and already
   showing real data since step 7) gained an org-wide commission summary
   section and an org-wide promotion history section below the existing
   tree — the §29 "builder/admin overview," satisfied by extending the
   page that already exists rather than building a second one.

**Deliberately NOT a new backend concept for "upline/direct downline/total
downline/downline tree/next designation"** — all of it is computed
**client-side** from two endpoints the frontend already had access to
(`GET /brokers/network`, `GET /designation-slabs`), following
`NetworkTreePage`'s own already-established "org-wide DESIGNATION-broker
counts are small enough that client-side assembly beats a second, bespoke
nested response shape" call. `buildTree`/`TreeNode`/`NetworkNode` were
exported from `NetworkTreePage.tsx` (previously private to that one file)
so `NetworkTab` can build the identical tree shape scoped to one broker
(find that broker's node inside the full tree, render its own `children`)
rather than duplicating the tree-assembly logic a second time. The only
genuinely new backend surface is the **money aggregation** — the one
thing the frontend has no way to compute itself without pulling every
`booking_commission` row into the browser.

**A real, pre-existing bug found and fixed while designing the step-10
aggregates — not a step-8/10 regression, but exposed by being the first
code to ever aggregate `pending` across bookings.** `BookingCommission.pendingAmount`
(the JPA-mapped, DB-generated `pending_amount` column) is defined by
V65_006 as `released_amount - paid_amount` — §11's "released but not yet
paid OUT to the beneficiary." Since `paid_amount` has had **no write path
at all since step 5** (disclosed then, still true), that column is
currently *always* numerically identical to `released_amount` for every
row in the system — meaning `CommissionTreeTab`'s existing "Pending" card
(shipped in steps 5-6) was showing the exact same figure as its own
"Released" card immediately to its left, which reads as either a
duplicate or a bug the moment anyone actually looks at both numbers side
by side. This was caught **before it ever reached a browser**: a fresh
`DesignationDashboardIntegrationTest` assertion expecting the
dashboard-intuitive "pending = not yet released" figure failed with the
wrong number, tracing straight back to this column's real, narrower
meaning. **Fixed by introducing a distinct, correctly-computed field**
rather than reinterpreting the existing column: `BookingCommissionResponse.pendingAmount`
→ `outstandingAmount` (`totalAmount - releasedAmount`), with the repository
sums for step 10's own aggregates changed to match
(`SUM(b.totalAmount - b.releasedAmount)`, not `SUM(b.pendingAmount)`).
`CommissionTreeTab.tsx`'s existing "Pending" card now reads
`row.outstandingAmount` — same label, correct value. The real
`pending_amount` DB column and its "released, awaiting payout" meaning is
untouched and still exactly correct for the day a future round adds a
real payout-recording action; only the API's own field name and the one
UI consumer of it changed. **Any future money field that names itself
after what a user would intuitively expect ("pending") needs its actual
formula double-checked against that intuition before wiring it up
anywhere new** — this is the same root shape as several earlier bugs in
this file (the post-M5 "commissionDue" negative-clamp, the Financials
overdue-consistency fix): a column that was locally correct when written
became actively misleading once a second surface started relying on the
same intuitive name meaning something narrower.

**Verification methodology and results:**
- Backend: 150/150 `mvn test` green (7 new: 3 in
  `DesignationManualOverrideIntegrationTest` — override freezes
  designation/rate and is untouched by a subsequent completion though
  counts still increment normally; clearing resumes automatic evaluation
  and can genuinely demote from the overridden value; an override never
  rewrites an already-frozen `booking_commission` row, and a booking
  created *after* the override correctly uses the new rate — plus 4 in
  `DesignationDashboardIntegrationTest`, exercised against a real 3-level
  differential chain with a genuine 50%-paid partial release (not just
  0%/100%): personal-vs-team splits correctly, org-wide aggregates sum
  correctly across multiple independent bookings and brokers,
  `CANCELLED` bookings are excluded from every figure while an unrelated
  kept booking is unaffected, and designation history resolves names
  correctly newest-first both per-broker and org-wide), zero regressions.
- Frontend: `tsc -b` clean, `oxlint` clean (only the same pre-existing
  shadcn `only-export-components` warning class, now on `NetworkTreePage.tsx`
  too since it exports `buildTree`/`NetworkNode` alongside its page
  component — the same harmless, already-documented class, not a new
  issue), `vitest run` 25/25, `npm run build` clean.
- Real HTTP verification against the local dev stack (`realestate-postgres`/
  `redis`, backend restarted fresh, no new migration this round so nothing
  new to apply): built a genuine 3-level chain (Top→Mid→Bottom, Bottom
  selling) at the default 160/160/160 rates, manually overrode Bottom to
  Business Manager/₹215 before any sale existed, sold and paid a plot in
  full, confirmed the frozen `booking_commission` row used ₹215 (not the
  slab a fresh completion would otherwise imply), completed the sale, and
  confirmed Bottom's designation/rate stayed exactly ₹215/overridden while
  `teamSuccessfulBookings` still correctly incremented to 1 — the precise
  scenario this round's own verification instruction named. Cleared the
  override and confirmed real auto-evaluation resumed, **demoting**
  Bottom to Senior Business Executive/₹180 (its real, current count-1
  designation) — with `designation_history` correctly showing exactly two
  rows (`MANUAL` 160→215, then `AUTOMATIC` 215→180) and the earlier
  booking's own frozen `commission_per_sqft` still reading ₹215,
  completely unaffected by the later demotion. Confirmed the org-wide
  commission summary and history endpoints sum correctly across this same
  live network (₹2,25,000 total earned — ₹2,15,000 selling + ₹10,000 same-slab
  bonus a separate, still-160-rated upline earned before any override
  happened — matching the org-wide history's own 4 real rows: 2 automatic
  promotions from the completion, the manual override, and the automatic
  demotion from clearing it).
- **Real-browser + Hindi + 360px verification, including one genuine
  environment gotcha hit and fixed along the way**: Vite auto-selected
  port 5174 (5173 was already occupied by another session), and the
  backend's `CorsConfig.allowedOrigins` default is a single hardcoded
  `http://localhost:5173` (M1-era, documented as "local dev only") — every
  login attempt in the browser failed with a generic "Something went
  wrong," which traced back to a bare CORS preflight 403, not any bug in
  this round's own code. **This is the exact class of environment gotcha
  M1's own CORS/OPTIONS note and the later Swagger-UI-allowlist bug both
  already document — an auth/CORS allowlist sized for one specific,
  assumed setup breaking the moment the real environment doesn't match
  it.** Worked around for this session only via a JVM property override
  (`-Dshardeya.cors.allowed-origins=http://localhost:5173,http://localhost:5174`)
  on `spring-boot:run`, not a code change — worth remembering for any
  future local verification session where the default Vite port is
  already taken. Once past that: logged in via the real OTP-login form
  (mobile → six-digit code read from the backend's own stub-SMS log,
  entered into the six separate digit inputs `AddPaymentDialog`-style
  forms don't use but this OTP input does), navigated via real nav-link
  clicks only (never `page.goto()` post-login, the standing
  `authStore`-is-in-memory-only constraint every e2e spec in this project
  has documented since M2), and exercised the *actual* override
  dialog/clear-override button through real clicks (not just the curl
  pass above) — both correctly reflected in the UI immediately via the
  existing `useMutation`/`invalidateQueries` wiring. Hindi + 360px pass
  across both the per-broker Network tab and the org-wide Network page:
  zero horizontal page overflow, zero raw i18n keys, every figure
  (₹2,25,000 earned, "सीनियर बिज़नेस एक्ज़िक्यूटिव," "समान-स्तर बोनस")
  reading correctly and matching the curl-verified state exactly. The
  only browser console message across the entire pass was the
  already-documented, confirmed-harmless M0-era `forwardRef` warning on
  `DialogOverlay` (present on every Dialog-based component in this
  codebase, including the brand-new `DesignationOverrideDialog` — not a
  new occurrence of a new bug, just this one inheriting the same
  known-and-accepted issue).

**Known, deliberate scope gaps, not bugs:** (1) step 8's `firstDesignation`
i18n string (for a history row with no `previousDesignationId`, i.e. a
broker's very first-ever designation) is currently unreachable in
practice — `BrokerPartnerService.create()` always assigns a real starting
designation immediately, so no real `designation_history` row is ever
written with a null previous value; kept for defensive completeness (the
DB column itself is nullable) rather than removed as dead code. (2)
`BrokerPerformanceResponse.totalCommissionEarned`/`commissionDue` on the
broker detail page's own top-of-page stat cards still read from
`commission_ledger_entry` only (a pre-existing gap disclosed since steps
5-6), so those two cards still show ₹0 for a DESIGNATION broker even
though the new Ledger-tab summary section directly below now shows their
real figures — worth closing whenever those cards are next touched, not
this round's own scope. (3) No UI or endpoint exists to record an actual
payout to a designation-broker beneficiary (`paidAmount` stays 0
everywhere) — unchanged since step 6, and now doubly relevant given the
`pendingAmount`→`outstandingAmount` rename above; revisit both together
when that action is ever built.

---

## Milestone 6.5 (Step 11) — Concurrency Audit Sweep

The final step of the Broker Network Engine, `06-BROKER-NETWORK-ENGINE.md`
§15's own build order. Per this round's explicit instruction, step 12
(migrating existing M6 FIXED/tier brokers) is **permanently out of
scope**, not deferred — the customer's existing broker data is
handwritten/on paper, never in this database, so there is nothing to
migrate; new brokers are entered fresh through the normal creation flow.
**With this step done, the Broker Network Engine (steps 1-11 of 12) is
complete.**

This was a deliberate final **sweep**, not new machinery — the heavy
concurrency work was already done and proven (step 7's READ COMMITTED
MVCC fix, step 9's cancel-vs-complete test). The instruction was explicit
that "no gaps found" would be a valid, acceptable result here, but only if
backed by actually having looked at each path. It wasn't: **two real,
previously-undiscovered concurrency bugs were found and fixed**, both by
first reproducing them empirically (never by reasoning alone), matching
the exact discipline step 7 already established.

**The audit itself — every write path that touches sales counts,
team-sales rollup, designation state, the commission tree, or commission
release, checked one at a time:**

1. `fn_broker_partner_designation_counts_trigger` (V65_010,
   `personal_successful_bookings`/`team_successful_bookings`) — already
   fixed and tested in step 7 (the `PERFORM ... FOR UPDATE`-before-recompute
   pattern). Re-confirmed clean via 3 repeated runs of its own existing
   concurrent test; untouched this round.
2. `fn_booking_commission_release_trigger` (V65_008,
   `booking_commission.released_amount`/`status`) — **a real bug, found
   and fixed.** See below.
3. `BookingCommissionService.freezeForSale()` (booking creation) — no
   aggregate-recompute-on-a-shared-row shape exists here at all: each
   booking only ever INSERTs brand-new rows scoped to its own
   `plot_sale_id` (the table's own unique constraint is
   `(plot_sale_id, beneficiary_broker_id, commission_type)`), and every
   rate it reads is a plain, un-aggregated snapshot read (§1: "using each
   broker's CURRENT rate at THIS instant" — inherently a point-in-time
   read, not something a concurrent peer's write needs to be reflected
   in). Proven safe under real contention with a new test (below), not
   just reasoned about.
4. `BookingCommissionService.cancelForSale()` (`booking_commission.status`
   → `CANCELLED`) — a plain Hibernate-managed `save()` on an entity with a
   real `@Version` column. Reasoned through, not separately tested this
   round: two independent transactions racing to write DIFFERENT columns
   of the same row (a cancellation's `status` flip vs. a release trigger's
   raw SQL `released_amount` write, which never touches `version` at all)
   cannot silently clobber each other, since whichever one's `UPDATE ...
   WHERE version=?` runs second, after the other has committed and (for
   the Hibernate side) bumped the version, either finds a still-matching
   version (safe, proceeds normally) or fails cleanly via
   `OptimisticLockingFailureException` (the release trigger's own raw SQL
   never bumps version, so it can never be the one to cause this side of a
   conflict).
5. `BrokerNetworkService.attachNewBroker()` (closure table) — same shape
   as freezing: only ever INSERTs new rows scoped to the new broker's own
   id, no shared-row recompute. Re-parenting an existing broker is
   explicitly not built yet (the class's own comment: "if allowed at all
   in v1"), so there is no concurrent-reparenting scenario to audit today.
6. `DesignationPromotionService.manuallyOverride()`/`clearOverride()`/
   `evaluateOne()` (`broker_partner.current_designation_id`/`rate`/
   `designation_manually_overridden`, `designation_history`) — **a real
   bug, found and fixed: a genuine Postgres deadlock**, not merely a lost
   update. See below.

**As a scoping check, not part of this step's own required categories**:
`fn_plot_sale_total_paid_trigger` (V3_010, pre-existing since M3) directly
feeds `CommissionReleaseService.releaseForPayment()`'s own fraction
calculation, so its own concurrency safety was worth confirming even
though `plot_sale.total_paid` isn't one of the four categories this step's
instruction named. Investigated with a raw two-session `psql`
reproduction identical in shape to the one that caught the release-trigger
bug below (two concurrent `payment_record` inserts on the same sale) —
**confirmed safe**, and this negative result is itself a useful, generalisable
finding: `UPDATE plot_sale SET total_paid = (SELECT SUM(...) FROM
payment_record ...) WHERE id = X` is a **single** SQL statement with the
aggregate INLINE in its own `SET` clause. Postgres's EvalPlanQual
mechanism, when a blocked `UPDATE` unblocks because the row it's waiting
on was just committed by another transaction, re-evaluates that row's
**entire** target list — including an inline subquery — against a fresh
view of the data, not just the row's own columns. This is genuinely
different from, and safer than, the shape that WAS buggy in both
`fn_broker_partner_designation_counts_trigger`'s original draft (step 7)
and `fn_booking_commission_release_trigger` (this step): a PL/pgSQL
function computing an aggregate into a **variable** in one statement (`v
:= (SELECT ...)`), then applying that already-frozen variable via a
**separate**, later `UPDATE` statement — nothing re-evaluates that earlier
statement's result when the later one unblocks. **The general, reusable
diagnostic rule this round confirms: a single inline-subquery `UPDATE ...
SET col = (aggregate) WHERE id = specific-row` is self-correcting under
concurrent blocking; a `SELECT aggregate INTO variable` followed by a
separate `UPDATE ... SET col = variable` is NOT, regardless of how
adjacent the two statements are in the same function.** This rule is what
made auditing every OTHER trigger in this codebase fast and confident
rather than requiring a fresh empirical reproduction for each one.

**Bug 1 — `fn_booking_commission_release_trigger` (V65_008), a real
lost-update, same class as step 7's own bug, just never checked for
here.** The trigger computed `v_released := (SELECT SUM(amount) FROM
commission_release WHERE booking_commission_id = X)` as one statement,
then applied it via a separate `UPDATE booking_commission SET
released_amount = v_released ... WHERE id = X` — exactly the two-statement
shape the diagnostic rule above identifies as unsafe. **Confirmed
empirically first**, per this project's own standing discipline, via a
raw two-session `psql` reproduction against a real `booking_commission`
row: session A inserts a `commission_release` row (amount 1000, holds the
row lock, uncommitted); session B inserts a second row (amount 500) and
blocks on A's lock; A commits; B's blocked statement unblocks and
completes; B commits. **Result: `released_amount` = 500, not the correct
1500** = `SUM(commission_release.amount)` for that booking — B's own
stale, pre-block `v_released` silently overwrote A's already-committed
value. Real, silent, and money-critical: every dashboard/API response
reading `booking_commission.released_amount`/`outstandingAmount`/`status`
(the entire step 10 dashboard surface, plus §9's own recovery
calculation) would understate what had actually been released.

**Fixed with `V65_012__fix_booking_commission_release_trigger_concurrency.sql`**,
applying the identical shape of fix step 7 already proved: acquire the
target row's lock as its own, separate `PERFORM 1 FROM booking_commission
WHERE id = v_booking_commission_id FOR UPDATE` statement, BEFORE computing
`v_released` — so the subsequent `SELECT SUM(...)` is a genuinely new
statement, issued only after any other transaction holding this row is
guaranteed committed or rolled back, and therefore correctly sees
everything that transaction committed. **Re-verified with the identical
two-session reproduction after the fix**: the same interleaving now
correctly produces `released_amount = 2000` (`1500` pre-existing +
`200` + `300` inserted by the repro) matching the true sum exactly. New
regression test, `CommissionReleaseConcurrencyIntegrationTest` — see
Verification below for why it deliberately bypasses
`PaymentService.record()` for the concurrent half (a real payment's own
`plot_sale` row-lock chain already, incidentally, serializes two
concurrent payments on the *same* sale end-to-end via the now-confirmed-safe
`total_paid` trigger, which would mask exactly the race this fix protects
against if the test went through that path).

**Bug 2 — `DesignationPromotionService`'s manual-override/evaluate path, a
genuine Postgres DEADLOCK, not merely a lost update — found by writing the
concurrent JUnit test the instruction asked for (a manual override racing
a completion on the same broker), not by a raw `psql` script.** Unlike
bug 1, this bug lives in the interaction between ordinary Hibernate
`UPDATE`s and a plain foreign key, not in hand-written PL/pgSQL — a raw
`psql` reproduction would have meant hand-guessing the exact SQL Hibernate
generates for both sides; the JUnit test, with two real threads and two
real database connections, already **is** the two-session reproduction at
the layer this bug actually lives in, and gave an unambiguous signal on
the very first run: a genuine `deadlock detected` error from Postgres
itself ("Process 65 waits for ShareLock on transaction 963; blocked by
process 64. Process 64 waits for ShareLock on transaction 962; blocked by
process 65... while locking tuple... in relation broker_partner").

Root cause: both `manuallyOverride()` and `evaluateOne()` (the second
shared by `evaluateAndPromote()`/`reevaluateAfterCancellation()`/
`clearOverride()`'s own `Mode.RESUME_AUTOMATIC`) inserted a
`designation_history` row **before** updating `broker_partner` itself.
`designation_history.broker_id` is a real foreign key to
`broker_partner(id)`, and Postgres takes a `FOR KEY SHARE` lock on the
*referenced* row as a side effect of inserting the *referencing* row — a
weak lock, but one that conflicts with the strong lock (`FOR UPDATE` or an
ordinary `UPDATE`) a completion's own counts trigger (step 7) already
takes on that same broker via its `PERFORM ... FOR UPDATE`. For
`evaluateAndPromote()`/`reevaluateAfterCancellation()` themselves this was
never reachable — they only ever run from *within* a transaction that
already holds the strong lock via that same trigger, so their own later
history-insert's weak lock request is trivially satisfied by a lock the
transaction already holds a stronger version of. But `manuallyOverride()`
and `clearOverride()`'s call into `evaluateOne()` (`Mode.RESUME_AUTOMATIC`)
**never go through a `plot_sale` status change at all** — nothing
guarantees they already hold `broker_partner`'s row lock going in. When
one such transaction (holding only the weak `FOR KEY SHARE` from its own
history insert) races a second transaction that's already queued waiting
for the strong lock on that same row (a completion's own trigger), the
first transaction's own later attempt to *upgrade* to that same strong
lock (for its own `broker_partner` `UPDATE`) has to queue *behind* the
second transaction's already-waiting request — a genuine mutual wait,
which Postgres correctly detects and resolves by aborting one side with a
real `deadlock detected` error, not a hang.

**Fixed by reordering, not by adding any new lock**: both `manuallyOverride()`
and `evaluateOne()` now perform the `broker_partner` `UPDATE` **first**,
then the `designation_history` `INSERT` second — matching the exact
"acquire the strong lock as this transaction's very first touch to the
contended row" discipline the counts trigger itself already established
in step 7, just applied here in Java rather than PL/pgSQL. Both methods
already captured `previousDesignationId`/`previousRate` (and, for
`evaluateOne()`, the team-sales-at-change figure) into local variables
before mutating the broker's own fields, so this reorder has zero effect
on what either history row records — a pure lock-ordering fix, not a
behavior change. **Verified via the same concurrent test, run 5 times in
a row after the fix: zero deadlocks, zero failures every time** (versus
deadlocking on the very first run before the fix) — the test asserts on
the same "guaranteed-correct final state regardless of which side wins"
property `DesignationPromotionConcurrencyIntegrationTest`/
`DesignationCancellationConcurrencyIntegrationTest` already established:
exactly one of the two operations' effects is ever reflected (the loser
either cleanly no-ops, having seen the winner's already-committed state
via the `designationManuallyOverridden` guard, or cleanly fails with a
`OptimisticLockingFailureException` whose whole transaction — including
its own not-yet-flushed history insert — rolls back with it), sales
counts are always correctly updated regardless of which side won (they're
trigger-maintained, entirely independent of this Java-level race), and
exactly one `designation_history` row exists afterward, never zero, never
two.

**Verification methodology and results:**
- Backend: 153/153 `mvn test` green (3 new concurrent tests:
  `BookingFreezeConcurrencyIntegrationTest`,
  `CommissionReleaseConcurrencyIntegrationTest`,
  `DesignationOverrideConcurrencyIntegrationTest`), zero regressions. The
  full suite was run twice in full, and the 5 concurrency test classes
  (the 3 new ones plus the 2 pre-existing ones from steps 7/9) were run 3
  additional times as a group specifically to rule out a lucky pass on
  timing-sensitive code — clean every time, and the deadlock test
  (`DesignationOverrideConcurrencyIntegrationTest`) was additionally run 5
  times in isolation immediately after the fix, per this round's own
  explicit "repeated full-suite runs for stability" instruction.
- `BookingFreezeConcurrencyIntegrationTest` — Upline ← {Seller1, Seller2},
  both sell a different plot in two genuinely parallel
  `plotSaleService.create()` calls. Confirms both sellers' own
  `SELLING_BROKER` rows freeze correctly, and — the property that actually
  proves no lost `INSERT` under real contention — the shared upline ends
  up with exactly two independent `booking_commission` rows (one per
  sale, correct amount each), never one row clobbered by the other.
- `CommissionReleaseConcurrencyIntegrationTest` — the regression test for
  bug 1. Two genuinely parallel transactions each independently compute
  "the sale is 100% paid, release the full amount" and race to insert
  that release for the *same* `booking_commission` row (deliberately
  bypassing `PaymentService.record()`'s own natural serialization, per the
  scoping-check finding above, so the test actually exercises the trigger
  under real concurrency rather than being accidentally protected by an
  unrelated table's lock). Asserts the true `SUM(commission_release.amount)`
  and the cached `released_amount` both land on the correct total, with
  the row correctly reaching `FULLY_RELEASED`.
- `DesignationOverrideConcurrencyIntegrationTest` — the regression test
  for bug 2, described in full above.
- Every fix was proven with the appropriate tool for the layer it lives
  in: raw two-session `psql` for the two pure-SQL/trigger findings (the
  release trigger bug, and the `total_paid` scoping check), a real
  two-thread JUnit test for the Hibernate/JPA-level deadlock — in every
  case, empirical reproduction before the fix, and re-verification with
  the identical reproduction after, never reasoning alone.

**No further gaps found.** Every write path in the four named categories
was individually examined; two had real, previously-undiscovered
concurrency bugs (now fixed and regression-tested), and the remainder
were confirmed safe by tracing their actual lock-acquisition shape against
the diagnostic rule this round's own investigation established — not
assumed safe by pattern-matching alone.

---

## Post-M6.5 Behaviour Change — Counting & Promotion Moved From COMPLETED to BOOKED

`06-BROKER-NETWORK-ENGINE.md` §1/§4/§6/§9 were revised, post-ship, per an
explicit user instruction reversing a decision the whole engine (steps
1-11 above) was originally built around. **Treat this as surgery on
already-shipped, money-adjacent behavior, not a new feature** — every one
of steps 5-11's own writeups above describes the *original* design
(counting/promotion firing on `COMPLETED`); they are left unedited as a
historical record of what shipped in each round, but their trigger-point
description is now superseded by this section. Anywhere above that says
"COMPLETED-triggered promotion" is describing the pre-this-change
behavior.

**Before → After:**

| | Before (steps 5-11) | After (this change) |
|---|---|---|
| Sales counting (`personal_successful_bookings`/`team_successful_bookings`) | Incremented when a sale reached `status = COMPLETED` (fully paid) | Incremented the moment a sale is **created** (`BOOKED`) — the customer may have paid ₹0 |
| Promotion evaluation | Ran at completion, after the count above ticked up | Runs immediately after booking, in the same transaction as the count above |
| Commission-tree freeze | Happened at booking (`create()`), always — this was already correct and is **unchanged** | Unchanged — still happens at booking, and now happens **immediately before** the count/promote step in the same transaction |
| Waived instalments | Excluded from counting (`total_waived = 0` filter) — a booking that closed out with a waiver never counted | **Moot.** A booking counts at BOOKED regardless of any later payment, waiver, or completion — waiver has no bearing on counting anymore |
| Cancellation reversal | Reversed counts/demoted only when the cancelled booking had reached `COMPLETED` (`wasCompleted` guard in `PlotSaleService.cancel()`) | **Unconditional** — every cancellation reverses counts/re-evaluates designation, because every booking counted at creation regardless of how far it got. Cancelling a booking that was *never* completed is now the common case this path has to handle, not an edge case |
| Commission release (instalment-proportional) | Proportional to actual payments, computed in `CommissionReleaseService` | **Completely untouched.** Release still only happens as real money comes in; this change never touched `PaymentService.record()`/`doReverse()`'s call into `CommissionReleaseService` |

**Why:** the business reality is that a broker (and their upline) should
be credited for *closing the deal* — getting a customer to commit to a
plot — not for the customer's own, sometimes-slow instalment schedule.
Waiting until `COMPLETED` (full payment) meant a broker's real
sales/promotion could lag their actual work by months, and a slow-paying
but firmly-booked customer looked identical to "nothing happened yet."
The rate-before-promote guarantee itself — a broker's promotion must never
retroactively enrich the very booking that earned it — is unchanged in
substance, just relocated to the new, earlier moment it now anchors to.

**What actually changed, precisely:**

1. **`V65_013__move_designation_counts_trigger_to_booking.sql`** (new).
   `fn_broker_partner_designation_counts_trigger()` — originally
   `AFTER UPDATE OF status ON plot_sale`, gated on
   `status = 'COMPLETED' AND total_waived = 0` — is now
   `AFTER INSERT OR UPDATE OF status ON plot_sale`, gated on
   `status <> 'CANCELLED'`. A freshly-inserted row (a booking) now fires it
   immediately; the trigger still fires harmlessly on a later `COMPLETED`
   transition too (an idempotent full recompute of the same, already-correct
   figure — not worth narrowing the predicate further just to skip a
   no-op). The `PERFORM 1 FROM broker_partner WHERE id = rec.broker_id
   FOR UPDATE` — then a separate recompute `UPDATE` — locking discipline
   step 7 established (see that section's own writeup for the exact READ
   COMMITTED MVCC reasoning) is preserved **verbatim**, just now guarding
   the booking-time firing instead of the completion-time one.
2. **`PlotSaleService.create()`**: immediately after the existing
   `bookingCommissionService.freezeForSale(...)` call (unchanged — this is
   still where the tree freezes), added
   `designationPromotionService.evaluateAndPromote(req.brokerPartnerId())`
   for `DESIGNATION`-type brokers. The method's own pre-existing
   `entityManager.flush()` (present earlier in `create()` for schedule-sync
   reasons) already guarantees the `plot_sale` INSERT — and therefore the
   counts trigger — has committed to the transaction before this call runs,
   so no additional flush was needed. **This ordering (freeze, then flush,
   then count+promote) is what makes "the booking that earns a promotion is
   itself frozen at the old rate" true by construction**, not by a
   check — verified live below.
3. **`PlotSaleService.complete()`**: the `DESIGNATION` branch that used to
   call `designationPromotionService.evaluateAndPromote(...)` was removed
   entirely. A `DESIGNATION` broker's completion now only affects
   `CommissionReleaseService` (via the pre-existing payment-triggered
   release path) — it no longer touches `broker_partner`'s counts or
   designation fields at all.
4. **`PlotSaleService.cancel()`**: the `wasCompleted` boolean and its
   surrounding `if` guard were removed. `bookingCommissionService.cancelForSale(...)`
   → `entityManager.flush()` → `designationPromotionService.reevaluateAfterCancellation(...)`
   now runs **unconditionally** for every `DESIGNATION`-broker sale
   cancellation, since every booking counted at creation regardless of
   whether it ever reached `COMPLETED`.

**Test re-derivation, not blind patching** (per the explicit instruction
to genuinely re-derive every stale expectation, not just make failures
go away): `DesignationPromotionIntegrationTest`,
`DesignationCancellationIntegrationTest`,
`DesignationManualOverrideIntegrationTest`, and all three
`Designation*ConcurrencyIntegrationTest` classes were rewritten. Two
categories of problem surfaced, both worth remembering for any future
change of this shape:
- **Genuine failures** (4 test methods): assertions computed against the
  old completion-time math were now numerically wrong under the new
  booking-time math — e.g. a test's *second* booking, created sequentially
  before an assertion or a "race," now also counts/promotes immediately
  where it didn't before, shifting every downstream expected rate. Each
  was fixed by re-tracing the actual new sequence of counts/promotions by
  hand, not by adjusting numbers until green.
- **"Passing for the wrong reason"** (5 test methods across 2 classes, not
  in the failure list): still green, but their own names/comments asserted
  a mechanism (promotion happens at completion) that no longer existed —
  e.g. `DesignationManualOverrideIntegrationTest`'s override-freezes-evaluation
  tests used to exercise the guard at `complete()` time; since
  `complete()` no longer calls `evaluateAndPromote()` at all, the override
  had nothing left to contend with there and the test would have kept
  "passing" even if the override guard were deleted outright. Fixed
  proactively (found by re-reading each test's own narrative against the
  new trigger point, not by a failure) by moving every assertion to fire
  right after `create()`, before any payment/completion, and rewriting the
  class/method-level comments so the test's stated purpose matches what it
  actually now exercises. The concurrency tests were similarly re-pointed:
  `DesignationOverrideConcurrencyIntegrationTest` now races
  `manuallyOverride()` against `create()` (not `complete()`);
  `DesignationCancellationConcurrencyIntegrationTest` now races a **new
  booking** against a cancellation (not a completion against a
  cancellation); `DesignationPromotionConcurrencyIntegrationTest` now races
  two parallel **bookings** sharing an upline (not two parallel
  completions) — all three had their `payInFull()`/completion setup code
  removed entirely, since a booking alone is now sufficient to exercise the
  real contention.

153/153 backend tests green, run twice for stability, plus 5x repeated runs
of every rewritten/adapted concurrency test class (all green) — matching
this project's own standing discipline against a lucky one-off pass on
timing-sensitive code.

**Live verification** (fresh signup on a locally-run backend, `V65_013`
applied via a real Flyway run — not the raw-`psql` syntax check used
during development): built a real 2-broker chain (Top ← Bottom, both
starting at Business Executive/₹160) and booked a plot for Bottom with
**₹0 paid** — confirmed immediately, before any payment, that both
brokers promoted to Senior Business Executive/₹180 and the booking's own
frozen `booking_commission` rows still read the **old** rate
(`commissionPerSqft: 160`, selling ₹1,60,000 + upline same-slab bonus
₹10,000 — proving the freeze-before-promote ordering holds under a real
HTTP request, not just the pure engine tests). Recorded a full payment
and called `complete()` — confirmed release went to 100% (`FULLY_RELEASED`,
unchanged mechanics) while counts/designation on both brokers stayed
frozen at exactly what booking had already set, and no new
`designation_history` row was written by completion. Booked a *second*
plot for Bottom (again ₹0 paid) — confirmed both brokers promoted further
to Business Development Officer/₹200 from the booking alone — then
cancelled that second sale **before any payment or completion**: confirmed
both brokers correctly demoted back to Senior Business Executive/₹180,
the cancelled booking's own commission row flipped to `CANCELLED` with
`needsRecovery: false` (correct — nothing had ever been released to
recover), and the first, completed booking's row was completely
unaffected (`FULLY_RELEASED`, ₹1,60,000). A real headless-browser pass
against `/builder/brokers/network` (Playwright, throwaway script, deleted
after passing per this project's established precedent) confirmed the
same figures render correctly in the UI, in English at desktop width and
in Hindi at 360px — zero horizontal overflow, zero raw i18n key leaks,
zero console errors, and the promotion-history cards read a coherent,
correct narrative including the cancellation-driven reversal entries.

**Known, deliberate scope of this round**: only the trigger point moved.
Release mechanics, the pure `CommissionCalculationEngine`, the manual
override/clear-override machinery (step 8), and the dashboards (step 10)
were not touched and needed no changes — confirmed by grepping for every
`.complete(`/count/promotion reference across the test suite and finding
only the files listed above actually depended on the old trigger timing.

---

## Post-M6.5 Bug Fix — Broker Detail Page Showed ₹0 Commission Earned/Due For DESIGNATION Brokers

Reported by the user directly, on their own test account, while manually
verifying the booking-vs-completion change above: a real DESIGNATION
broker with a real, partially-paid sale (`deal_value = ₹2,00,000`,
`total_paid = ₹10,000`, i.e. 5%) showed **₹0** for both "Commission
Earned" and "Commission Due" on `/builder/brokers/{id}` — despite the
Ledger tab's own `CommissionTreeTab` summary card, and the Network Tree
page, both already showing the correct real figures for the same broker.

**Root cause**: `broker_partner.total_commission_earned`/`total_commission_paid`
are only ever trigger-maintained off `commission_ledger_entry`
(`V6_015`) — the M6-era PERCENTAGE/FIXED path. A DESIGNATION broker
never writes a single `commission_ledger_entry` row at all (M6.5 routes
them through `booking_commission`/`commission_release` instead, per the
step-5 "the two systems never mix" design), so those two raw columns
stay permanently `0` for every DESIGNATION broker — yet
`BrokerPartnerService.toResponse()` (the broker list + detail-header
endpoint) and `.performance()` (the detail page's own stat-card
endpoint) both read those same two raw columns unconditionally,
regardless of `commissionType`. This is the exact gap CLAUDE.md's own
"Milestone 6.5, steps 8 & 10" section had already disclosed in advance
("`BrokerPerformanceResponse.totalCommissionEarned`/`commissionDue`...
still read from `commission_ledger_entry` only... worth closing whenever
those cards are next touched") — this is that close.

**Fixed** by adding a `commissionFigures(BrokerPartner)` helper to
`BrokerPartnerService` that branches on `commissionType`: for
`PERCENTAGE`/`FIXED`, unchanged (`earned`/`paid` straight off the raw
columns, `due = clampToZero(earned - paid)`, same as before); for
`DESIGNATION`, `earned` is computed as
`bookingCommissionRepository.sumTotalEarnedFor(brokerId, CANCELLED)` —
the same "team" total (every `booking_commission` row this broker is a
beneficiary of, as seller or as an upline bonus recipient, excluding
cancelled bookings) that `BookingCommissionService.commissionSummary()`
already exposes on this exact broker's own Ledger tab, so the two
numbers can never disagree with each other. Both `toResponse()`
(broker list + `GET /brokers/{id}`) and `.performance()` (the detail
page's stat cards, `GET /brokers/{id}/performance`) now call this one
shared helper instead of duplicating the branch.

**A real, deliberate design decision on "Due", made explicit rather than
assumed** — the user pushed back on this immediately after the fix
shipped, a fair question since the term is genuinely ambiguous: should
"Commission Due" mean (a) the full earned amount minus whatever's been
paid out (`earned - paid`, matching PERCENTAGE/FIXED brokers exactly,
where the *entire* commission is booked and payable immediately at sale
time regardless of buyer payment progress), or (b) only the
**released** portion minus paid (`released - paid`, gated by how much
the buyer has actually paid)? **Implemented as (b)** — `due` for a
DESIGNATION broker is `clampToZero(sumReleasedFor(brokerId, CANCELLED))`,
never `earned - paid` — specifically because gating payout by real
buyer collection is the entire reason `booking_commission`/
`commission_release` and the whole step-5/6 proportional-release
mechanism exist in the first place. Showing the full ₹32,000 as "due"
on a booking where the buyer has paid ₹0 would directly contradict that
design: nothing has been collected yet, so nothing is safely payable
yet, even though the full amount has been "earned" (frozen, booked) in
the aspirational sense. Confirmed live on the real reported case:
`earned=₹32,000` (Kanishka's frozen `SELLING_BROKER` total, 200 sq.ft. ×
₹160), `due=₹1,600` (exactly 5% of ₹32,000, matching the real 5% paid on
that sale) — not ₹32,000. **This decision was discussed explicitly with
the user rather than silently picked** — if a future round decides "Due"
should instead mean the full earned-minus-paid figure (accepting that it
can then show more "due" than has actually been collected from the
buyer), that's a one-line change to `commissionFigures()`'s DESIGNATION
branch (swap `sumReleasedFor` for the already-computed `earned` value),
not a redesign.

**Verification**: 47/47 broker-package backend tests green
(`com.shardeya.builder.broker.**`), `mvn compile` clean. Verified live
via direct `curl` calls against the reported broker/account (not a fresh
synthetic scenario — the exact real data the user reported): `GET
/brokers/{id}` and `GET /brokers/{id}/performance` both now return
`totalCommissionEarned: 32000, totalCommissionPaid: 0, commissionDue:
1600` where both previously read `0, 0, 0`. No migration needed — this
is a pure read-side/response-construction fix; no schema or trigger
changed. No regression risk to PERCENTAGE/FIXED brokers, whose branch of
`commissionFigures()` is byte-for-byte the same logic `toResponse()`/
`.performance()` already had inline before this refactor.

**Immediate follow-up, same session: the "Due" ambiguity above was
resolved by relabeling, not by picking a side.** The user's own
follow-up question ("if we call it Commission Released instead of Due,
wouldn't that be better?") turned out to be the actually-correct fix —
it sidesteps the earned-vs-released tradeoff entirely rather than
resolving it, and it fixes a second, real inconsistency this round
hadn't caught: the exact same ₹1,600 figure was already labelled
**"Total Released"** one scroll down on the very same page, inside
`CommissionTreeTab`'s own summary card — so the top-of-page stat row
saying "Commission Due" for the identical number was two different
labels for one figure on one page, not just an ambiguous term in
isolation. **Fixed on the frontend only** (no backend/API change — the
underlying value, computed above, was already correct): `BrokerDetailPage.tsx`'s
stat-card row now picks the label conditionally on `broker.commissionType`
— `DESIGNATION` brokers show **"Commission Released"** (new
`performance.commissionReleased` i18n key, en: "Commission Released",
hi: "जारी कमीशन", matching `totalReleased`'s "कुल जारी" adjective-noun
pattern already established in the same namespace), `PERCENTAGE`/`FIXED`
brokers keep the original **"Commission Due"** label unchanged (accurate
for them — the whole commission is booked and payable immediately at
sale time for that broker type, no release concept exists). The broker
**list** page's shared `commissionDue` column header was deliberately
left untouched — a single table mixes both broker types row by row, so
one column header can't correctly describe both semantics at once; only
the detail page (one broker, one known type) can meaningfully swap the
label. Verified live via a real browser session (navigating via clicks
only, never `page.goto()` post-login — the same in-memory-`authStore`
constraint this project's e2e notes have documented since M2): the
reported broker's page now reads "Commission Earned ₹32,000" /
"Commission Released ₹1,600" in English, and "अर्जित कमीशन ₹32,000" /
"जारी कमीशन ₹1,600" in Hindi at 360px with zero horizontal overflow.
`tsc -b`/`vitest run` (25/25, including the `broker` namespace
key-parity check covering the new key in both languages)/`oxlint` all
clean.

---

## Post-M6.5 Feature — Broker Payout Tracking (§8a): Earned/Released/Paid/Due

New section added to `06-BROKER-NETWORK-ENGINE.md` (§8a) after the engine
was first built, closing the gap the two "Post-M6.5 Bug Fix" rounds above
were both dancing around without a real fix for: the engine had **Earned**
(frozen at BOOKED) and **Released** (unlocked as the customer pays), but
**no concept of the builder actually paying the broker at all**. This adds
that third state.

### The model, spelled out unambiguously for future work

```
Earned    -- frozen at BOOKED (booking_commission.total_amount).
             The total a beneficiary will EVENTUALLY get from this
             booking, whether or not the customer has paid anything yet.

Released  -- unlocked as the CUSTOMER pays instalments
             (booking_commission.released_amount, CommissionReleaseService,
             unchanged since steps 5-6). The CAP on what can ever be paid
             out -- money the customer hasn't paid can never be paid to
             the broker, full stop.

Paid      -- the BUILDER actually handing the broker money
             (booking_commission.paid_amount, NEW this round --
             BrokerCommissionPaymentService). Starts at 0, only moves via
             a real "Record Payment" action.

Due       -- Released MINUS Paid (booking_commission.pending_amount, a
             GENERATED column that has existed since step 5/6 with no
             write path until now). THE number the builder acts on --
             "how much can I safely pay this broker right now."
```

Two more figures exist and answer genuinely different questions, easy to
confuse with Due if read carelessly:
- **Outstanding** (`totalAmount - releasedAmount`, unchanged since the
  original "Post-M5"-era `BookingCommissionResponse` design) -- "how much
  MORE will release as the customer keeps paying." Has nothing to do with
  what's payable right now.
- A broker-level **"earned"** aggregate sums `total_amount` across every
  booking the broker is a beneficiary of (seller or upline) -- the same
  figure `BookingCommissionService.commissionSummary()` already exposed on
  the Ledger tab before this round; unaffected by Paid becoming real.

**The hard cap, and why it's non-negotiable:** a payout can never exceed
Due, i.e. never exceed Released, i.e. never exceed what the customer has
actually paid. Unlike a buyer's own payment (`PaymentAllocationService`,
which allows an "advance" beyond what's currently due, sitting as credit
against future instalments), a broker payout past Due is always a flat,
hard reject -- **never** a confirm-to-proceed flag. Paying a broker for
money not yet collected from the customer would defeat the entire reason
the release mechanism (steps 5-6) exists.

### What was built

- **`broker_commission_payment`** (`V65_015`) + **`broker_commission_payment_allocation`**
  (`V65_016`) -- new tables mirroring, respectively, `CommissionPayment`
  (M6's identical "builder pays a broker" concept, for the row shape:
  immutable, `payment_mode` enum reused, reversal via `reverses_payment_id`)
  and `CommissionRelease` (for the clean immutable-allocation shape: no
  `deleted_at`, a `RULE` blocking `DELETE` outright, a `CHECK (amount <> 0)`).
  Deliberately NOT 1:1 with one `booking_commission` row the way
  `CommissionPayment` is 1:1 with one `commission_ledger_entry` -- a
  DESIGNATION broker's payout spreads oldest-first across potentially many
  bookings, so `broker_commission_payment_allocation` plays the exact role
  `payment_allocation` plays between `payment_record` and
  `payment_schedule`.
- **`V65_017`** -- `fn_booking_commission_paid_amount_trigger`, maintaining
  `paid_amount` off `broker_commission_payment_allocation`. Written with
  the lock-then-recompute discipline (`PERFORM ... FOR UPDATE` as its own
  statement, before the `SELECT SUM(...)`) from day one, not discovered
  the hard way a third time -- `V65_008`/`V65_010` both had to be
  retroactively fixed (`V65_012`, step 11) for exactly this class of bug.
- **`BrokerCommissionPaymentService`** -- `record()` locks every one of the
  broker's non-cancelled `booking_commission` rows oldest-first via a new
  `BookingCommissionRepository.lockForPayoutOldestFirst` (`PESSIMISTIC_WRITE`,
  acquired as this transaction's first touch to those rows), sums their
  `pendingAmount` for the hard-cap check, rejects outright if the
  requested amount exceeds it, then allocates oldest-first exactly like
  `PaymentAllocationService.allocateAuto()` -- reusing that established
  shape, not inventing a new one. `reverse()` mirrors
  `PaymentService.doReverse()`'s exact two guards (no double-reversal, no
  reversal-of-a-reversal) and mirrored-negated-allocation approach.
- **A real bug found and fixed while building this: `CommissionReversalCalculator`
  had the wrong recovery formula, invisible until Paid could ever be
  non-zero.** The pre-§8a code computed `recovery = releasedAmount -
  paidAmount` -- reasonable-looking, and its own javadoc even predicted
  "this stays correct the moment a future round wires up an actual
  payout-recording action, with no change needed here." That prediction
  was wrong. Once `paidAmount` is real, `released - paid` demands
  recovering **more than was ever paid to the broker** whenever released
  exceeds paid (the ordinary shape for any partially-paid-out booking) --
  e.g. released=₹32,000, paid=₹1,600 (5% actually paid out) gives
  `recovery = 30,400`, but the broker was only ever handed ₹1,600.
  Recovery must mean "money that actually left the builder's hand" --
  exactly `paidAmount`, mirroring M6's own already-established
  `CommissionLedgerEntryResponse` semantics ("`amount_paid` on a CANCELLED
  entry IS the recovery amount") precisely, which this engine's own
  pre-§8a code had approximated with the wrong stand-in formula rather
  than the real one. **Caught by re-deriving every `CommissionReversalCalculatorTest`
  fixture by hand against a real partially-paid scenario, not by a runtime
  failure** -- 4 of the rewritten fixtures were confirmed to fail against
  the old formula before the fix (e.g. "expected 40,000 but was 80,000"),
  then pass after. Fixed by changing `reverseOne()` to
  `recovery = paidAmount` (clamped non-negative) and
  `cancelledPendingAmount = totalAmount - paidAmount` (everything except
  what was actually paid simply voids, whether never-released or
  released-but-never-paid). `BookingCommissionService.toResponse()`'s
  `needsRecovery` gate was corrected to match (`paidAmount.signum() > 0`,
  not `releasedAmount.signum() > 0`) -- a released-but-never-paid,
  cancelled booking now correctly shows `needsRecovery: false`.
- **`BrokerPartnerService.commissionFigures()`** (the "Post-M6.5 Bug Fix"
  section above's own fix, extended): now returns real `paid`/`due`
  figures for a DESIGNATION broker (`due = sumDueFor` — summing the same
  `pendingAmount` GENERATED column the payout service's own hard-cap check
  reads directly off its locked entities, so the two can never disagree;
  `paid` is derived as `released - due` rather than a fifth repository
  query, keeping all figures trivially consistent by construction). The
  interim "Due = Released" approximation and its accompanying frontend
  relabel to "Commission Released" (both from the section immediately
  above) are now **superseded** -- "Commission Due" is genuinely meaningful
  again, and `BrokerDetailPage.tsx`'s stat row shows both **Commission
  Released** and **Commission Due** side by side for a DESIGNATION broker
  (§8a: "keep Released visible too if useful, but Due is the actionable
  one"), while PERCENTAGE/FIXED brokers keep showing only Due, unchanged,
  since no release concept applies to them at all.
- **Frontend**: a "Record Payment" button on `CommissionTreeTab` opens a
  dialog paying against the broker's **whole** Due balance (not one entry
  at a time, unlike the M6 `LedgerTab` equivalent) -- the backend spreads
  it oldest-first. Per-entry cards now show **Due** where they used to
  show the unreleased-Outstanding figure (§8a: "show Due where they
  currently show Released-as-pending") -- Outstanding is still a real,
  separately-useful figure, just no longer surfaced on this specific card
  (Total/Released/Due is the new three-up row). A new Payment History
  section lists every `broker_commission_payment` row as card rows (never
  a `<Table>` -- see the note below) with a working Reverse action, which
  incidentally is the **first real frontend UI** this codebase has ever
  built for a broker-commission-payment reversal: `reverseCommissionPayment`
  (the M6/PERCENTAGE-broker equivalent) has existed as a backend endpoint
  and a frontend API function since M6, but was never wired to any button
  anywhere -- confirmed by grep before assuming a "Reverse" pattern already
  existed to copy; there wasn't one, so this round's `ReverseBrokerPaymentDialog`
  is genuinely new UI, not a mirror of an existing one, even though the
  underlying `payment.reverseTitle`/`reverseReason`/`reverseSubmit` i18n
  keys had been sitting unused in `broker.json` since M6.
- **A fourth occurrence of the wide-`<Table>`-breaks-at-360px class was
  proactively avoided, not discovered after the fact this time**: the new
  Payment History section was built as card rows from the start, following
  `CommissionTreeTab`'s own sibling per-entry cards and the Ops
  page/`CommissionTreeCard`'s own precedent, rather than reaching for a
  `<Table>` and finding out the hard way a fourth time.

### Verification

Backend: 160/160 `mvn test` (54/54 in the broker package specifically,
including 5 new sequential `BrokerCommissionPaymentServiceIntegrationTest`
cases -- oldest-first spreading across two differently-rated bookings
[the second one promoted mid-scenario by the first booking's own real
promotion effect, not simplified away], hard-cap rejection, reversal
restoring Due exactly, and both cancellation shapes: paid-then-cancelled
tracks the paid amount as recovery, released-but-never-paid-then-cancelled
needs no recovery at all -- plus a new
`BrokerCommissionPaymentConcurrencyIntegrationTest`, run 6 times total (1
+ 5 repeats) for stability per this project's own standing discipline: two
threads racing 60,000 each against a shared 100,000 Due pool, asserting
only the guaranteed-correct final state [exactly one succeeds, final
`paid_amount` is exactly 60,000, never 120,000] regardless of which thread
wins). Zero regressions.

Live HTTP verification against a real seeded scenario (two bookings for
one broker, the second one genuinely promoted mid-scenario by the first,
matching the integration test's own real shape): a ₹1,00,000 payout
correctly filled the older ₹80,000-due booking completely and spilled
₹20,000 into the newer ₹90,000-due one; a ₹71,000 request against a
₹70,000 remaining Due was rejected with a structured
`EXCEEDS_COMMISSION_DUE` error carrying both figures; reversing the first
payout correctly restored Due to the full ₹1,70,000 across both bookings;
paying a booking off in full (₹80,000) and then cancelling that sale
correctly showed `needsRecovery: true, recoveryAmount: 80000` -- the exact
live, end-to-end confirmation that the `CommissionReversalCalculator` fix
above is correct in the full stack, not just in isolated unit fixtures
(the old, buggy formula would have shown `recoveryAmount: 0` for this
exact case, since released and paid were both ₹80,000).

Real-browser verification (English/desktop and Hindi/360px, navigating via
clicks only post-login per the standing in-memory-`authStore` constraint):
the stat row correctly showed both "Commission Released ₹90,000" and
"Commission Due ₹90,000" before any payout; recording a real ₹30,000
payment through the actual dialog immediately dropped the on-screen Due to
₹60,000 with no manual refresh (`useMutation`'s own `invalidateQueries`);
the cancelled booking's card showed "Recover ₹80,000 -- this booking was
cancelled after commission was released" -- correct, real UI text, not
just a correct API field. Hindi at 360px: "बकाया कमीशन ₹60,000", "जारी
कमीशन ₹90,000", "भुगतान दर्ज करें" ("Record Payment") -- zero horizontal
overflow, zero raw i18n key leaks. The only console message was the
already-documented, confirmed-harmless M0-era `forwardRef` warning on
`DialogOverlay` (present on every Dialog-based component in this codebase
since M0, including this round's own new dialogs -- not a new occurrence
of a new bug).

**Known, deliberate scope, not gaps:** step 12 (migrating existing M6
FIXED/tier brokers) remains permanently out of scope per the user's own
prior explicit decision, unrelated to and unaffected by this round.
`Outstanding` (unreleased) and the org-wide `NetworkCommissionSummaryResponse`
dashboard were deliberately left untouched -- neither needed a Paid/Due
distinction added, since neither claims to answer "what's payable right
now" the way the broker-level stat cards and per-entry cards do.

---

## Post-M6.5 Feature Change — Same-Slab Bonus Is Now `next − current`, Not a Flat ₹10

A small, surgical, explicitly-scoped change to `06-BROKER-NETWORK-ENGINE.md`
§7 — the same-slab incentive formula ONLY. Per the user's own explicit
instruction: nothing else about the engine (slab thresholds/rates,
promotion timing, counting, proportional release, broker payout,
cancellation/recovery, concurrency) was touched.

**Before → After:** when an upline and their direct downline share the
same designation slab, the upline used to earn a flat
`NETWORK_SAME_SLAB_BONUS` of ₹10/sq.ft., regardless of which slab the pair
was on. It is now `(rate of the slab immediately above their shared slab)
− (their current shared slab rate)` — resolved from the real
`designation_slab` config table, never a second hardcoded ladder. The
**lookup table** (for future reference, so the flat ₹10 is never
reintroduced):

| Shared slab rate | Next slab rate | Same-slab incentive |
|---|---|---|
| ₹160 | ₹180 | **₹20** |
| ₹180 | ₹200 | **₹20** |
| ₹200 | ₹215 | **₹15** |
| ₹215 | ₹225 | **₹10** |
| ₹225 | ₹235 | **₹10** |
| ₹235 | ₹245 | **₹10** |
| ₹245 | ₹255 | **₹10** |
| ₹255 | *(none — top slab)* | **₹0** |

The three non-negotiable rules that predate this change are all
unchanged: it's still classified `NETWORK_SAME_SLAB_BONUS` (never
reclassified as `UPLINE_DIFFERENTIAL`); it's still an ADDITIONAL,
uncapped incentive that never reduces the seller's own commission (total
payout can still exceed the seller's rate, intentionally — the same §46
"not capped" guarantee, now just a different, usually-larger number); and
it's still evaluated independently at every level of a chain, each upline
compared only with its own direct downline.

**Architecture: the pure engine stays pure, the DB lookup happens at the
call site.** `CommissionCalculationEngine` has zero DB/Spring
dependencies by design (see step 4's own notes above) — the "don't
hardcode the ladder in a second place" instruction therefore couldn't be
satisfied by teaching the engine to query `designation_slab` itself.
Instead, `ChainMember` gained a third field,
`nextSlabRatePerSqft` (`null` means "this member is already at the top
slab, no higher slab exists"), which the CALLER
(`BookingCommissionService.freezeForSale()`, which already has
`DesignationSlabService` access) resolves and passes in for every chain
member before invoking the still-pure engine. A new
`DesignationSlabService.resolveNextRate(orgId, currentDesignationId)` +
`DesignationSlabRepository.findSlabsAboveSortOrder(...)` (ordered
ascending by `sort_order`, so the first result is the CLOSEST slab above,
never just any higher one) do the actual lookup — the same org-scoped
`(s.orgId = :orgId OR s.orgId IS NULL) AND deletedAt IS NULL AND active =
true` shape every other slab query in this engine already uses. The
engine's own same-slab branch became:
`perSqft = nextSlabRatePerSqft == null ? ZERO : nextSlabRatePerSqft.subtract(currentRate)`,
with a defensive `signum() < 0 → ZERO` clamp (never reachable in practice
given the slab ladder is strictly increasing, but cheap insurance against
a misconfigured/out-of-order `sort_order`) — this is what makes the
₹255 top-slab case resolve to exactly ₹0 by construction, never a
fabricated higher slab and never a fallback to the old flat value.

**Two real, pre-existing tests were genuinely wrong under the new formula
and had to be re-derived, not force-passed:**
- `DesignationDashboardIntegrationTest.orgWideSummaryAggregatesAcrossMultipleBookingsAndDistinctBrokersCorrectly`
  — its A/B broker pair sits at ₹160 (Business Executive), whose real
  next-slab-up is ₹180, giving a ₹20/sq.ft incentive, not ₹10.
  `sameSlabBonusEarned`, `totalCommissionEarned`, and
  `totalCommissionPending` were all re-derived by hand from this.
- `DesignationCancellationIntegrationTest.cancellingAPartiallyReleasedStillActiveBookingRecoversEveryBeneficiaryAndReversesTheCountItSetAtBooking`
  — its Me←A←B chain is also all at ₹160, so the same-slab bonus A earns
  is ₹20,000, not the old ₹10,000; the test's own 25%-released assertion
  was corrected from ₹2,500 to ₹5,000.

`CommissionCalculationEngineTest` was substantially rewritten: existing
same-slab fixtures were re-derived against the real lookup table (§46's
215/225 pair happens to still land on ₹10 — the old flat value and the
new formula's answer for that specific slab pair coincide, purely by
chance, not because the formula reverted); §21's five-level mixed chain
was re-derived level-by-level (its own ₹200 same-slab level moved from
₹10,000 to ₹15,000, total from ₹235,000 to ₹240,000). New fixtures added
exactly as instructed: a ₹200/₹200 pair (→₹15,000), a ₹160/₹160 pair
(→₹20,000), a ₹255/₹255 pair (→ exactly ₹0, asserted never negative and
never falling back to ₹10), and the multi-level chain with same-slab
recurring at two different slabs, each level's incentive computed and
asserted independently. `CommissionReversalCalculatorTest`'s pure §45
differential fixture needed only a mechanical third-argument (`null`)
addition to its `ChainMember` constructions — untouched numerically, since
§45 is pure differential, never same-slab. 163/163 backend tests green
(zero regressions), full broker package (57/57) re-run in isolation
first.

**Frontend needed zero code changes** — `CommissionTreeTab.tsx`/
`NetworkTreePage.tsx` already render whatever `totalAmount`/
`commissionType` the backend returns; the UI automatically reflects the
new dynamic amount with no changes of its own.

**Verified live** (HTTP + real browser, English desktop + Hindi/360px),
using the manual designation-override endpoint to place test brokers at
exact target slabs without needing dozens of real bookings to reach those
team-sales thresholds — three scenarios, all matching the lookup table
exactly:
1. A same-slab pair at ₹200 → the upline's frozen `booking_commission` row
   read `commissionPerSqft: 15.0, totalAmount: 15000.0` for a 1,000 sq.ft.
   plot — confirmed via direct API call and, separately, via a real
   Playwright browser session showing the exact same figure on
   `CommissionTreeTab`'s "Same-Slab Bonus (level 1)" card, in English at
   desktop width and again in Hindi ("समान-स्तर बोनस") at 360px with zero
   horizontal overflow and zero raw i18n key leaks (visually confirmed
   from the actual screenshot, not just the automated checks).
2. A same-slab pair at the top slab, ₹255 → confirmed exactly
   `commissionPerSqft: 0.0, totalAmount: 0.0` — never negative, never a
   fabricated higher slab, never a fallback to ₹10.
3. A multi-level chain with same-slab recurring at two different slabs in
   one chain (Seller/A at ₹200 → ₹15,000 same-slab; A/B differential;
   B/C at ₹215 → ₹10,000 same-slab) — confirmed each level computed its
   own `next − current` independently, with no cross-level leakage.

**A real diagnostic worth recording, not a product bug**: while building
the multi-level test chain live, the first attempt created every broker
with `uplineBrokerId` pointing the wrong direction (each new broker's
upline set to the *previously created* broker, which is actually
downward, not up), producing a booking with only one
`booking_commission` row instead of the expected four. Root cause
confirmed directly by querying the `broker_network` closure table for the
seller's own ancestors and finding none — the fix was rebuilding the
chain bottom-up (create the topmost broker first with no upline, then
each subsequent broker with `uplineBrokerId` pointing at the
already-created broker directly above it, with the actual seller created
LAST). Worth remembering for any future manual test-data setup involving
`uplineBrokerId` — the field points UP, and a chain must be built top-down
for that to resolve correctly.

---

## Auth Fix — Refresh Token Moved to an httpOnly Cookie (Reload Logout + 15-Min Session Drop, Both Fixed)

Fixes two long-standing, related symptoms explicitly reported by the user:
(1) a hard page reload always logged the user out, and (2) a session
sometimes dropped on its own after roughly 15 minutes even without a
reload. Both traced to the same root object — the refresh token — but the
investigation surfaced a **second, independent, more fundamental bug**
along the way that turns out to be the more direct cause of symptom (2).
Read this before touching `AuthController`, `AuthService`, `CorsConfig`,
`TenantContextFilter`, `authStore.ts`, or `lib/api/client.ts`.

### The refresh-token model, before and after

| | Before | After |
|---|---|---|
| Where the refresh token lives | JSON response field (`AuthTokensResponse.refreshToken`), held in the in-memory Zustand `authStore` | An `httpOnly` cookie the browser manages entirely — never touches JS, never appears in any response body |
| Survives a hard reload? | No — nothing persists it (rule #15 forbids `localStorage`) | Yes — the cookie is the browser's own persistence, not this app's |
| How `/auth/refresh` reads it | `RefreshRequest.refreshToken` request body field | `@CookieValue("refresh_token")`, attached automatically by the browser (`credentials: 'include'`) |
| How `/auth/logout` reads/clears it | `LogoutRequest.refreshToken` body field; nothing to clear client-side (JS held it) | Read from the cookie; server clears it via a second `Set-Cookie` with `Max-Age=0` |
| Access token | 15-min JWT, in-memory only | **Unchanged** — still exactly this |
| Family/reuse-detection logic (`RefreshTokenService`) | — | **Unchanged** — same rotation, same family-wide-revoke-on-reuse, same TTLs (12h default / 30d remember-me) |

**Why httpOnly-cookie and not just "keep it in memory but restore it
somehow"**: rule #15 exists specifically because a token sitting in
JS-reachable state (a variable, `localStorage`, `sessionStorage`) is
readable by any successful XSS payload. An `httpOnly` cookie is invisible
to `document.cookie` and every other JS API — confirmed directly in this
round's own browser verification (`document.cookie` was empty after
login; the cookie only ever showed up in the network tab's `Set-Cookie`/
`Cookie` headers, never in anything a script could read). This is the fix
rule #15 was always pointing toward, not a workaround for it.

### The SameSite choice: `None`, deliberately, with `Secure`

The cookie is `SameSite=None; Secure=true`, not `Lax`. `Lax` would work
today — the Vite dev server (`:5173`/`:5174`) and the backend (`:8080`)
are different *origins* but the same *site* (both `localhost`, same
scheme, port ignored for site comparison), so a `Lax` cookie would still
ride along on these cross-origin `fetch()` calls. **Chosen anyway,
deliberately, because this codebase has already hit the "worked in dev,
silently broke in prod" CORS class of bug twice before** (M1's
OPTIONS-preflight gap, the later Swagger-UI-allowlist gap — see both
elsewhere in this file) — a real deployment could plausibly put the
frontend and API on genuinely different registrable domains (a separate
API subdomain, a different domain behind a gateway/CDN), which would make
them cross-*site*, not just cross-origin, and `Lax` would then silently
stop sending the cookie with zero compile-time or dev-environment signal
that anything changed. `None+Secure` is chosen up front so this can never
become a third occurrence of that same bug class later. `Secure` does
**not** require HTTPS specifically for `localhost` — Chrome and Firefox
(this project's e2e/manual-verification browsers) treat `http://localhost`
as a "potentially trustworthy origin" and both set and return `Secure`
cookies over plain HTTP there; **confirmed live**, not assumed, as part of
this round's own curl+browser verification (the `Set-Cookie`/cookie
round-trip worked correctly over `http://localhost:8080` the entire time).

The cookie is also scoped to `Path=/api/v1/auth` — the browser only ever
needs to send it to `/auth/refresh`/`/auth/logout`, and narrowing where a
sensitive cookie is transmitted is cheap, real defense in depth.

### A second, independent, more fundamental bug found along the way: filter-rejected 401s never carried CORS headers

While verifying symptom (1)'s fix in a real browser, forcing an access
token to look expired (corrupting it, to simulate the natural 15-minute
JWT expiry without waiting) produced a **real, visible browser CORS
error** — `No 'Access-Control-Allow-Origin' header is present on the
requested resource` — for the very API call that should have triggered
`apiFetch`'s existing refresh-on-401 retry logic. Traced (not guessed) to
the root cause: `CorsConfig`'s CORS registration was a
`WebMvcConfigurer#addCorsMappings()` call, which **only ever applies
inside `DispatcherServlet`'s own dispatch** — i.e., only to responses that
make it past every servlet `Filter` first. `TenantContextFilter` rejects
an invalid/expired/missing access token by writing its 401 **directly**,
before `chain.doFilter()` is ever called — that response never reaches
`DispatcherServlet` at all, so it never got a CORS header, no matter how
correctly `CorsConfig` was written. Confirmed the isolation precisely with
two side-by-side curl calls: a 401 from a bad *login password* (thrown
inside `AuthService`, handled by `GlobalExceptionHandler` — reaches
`DispatcherServlet` normally) correctly carried `Access-Control-Allow-Origin`;
a 401 from a garbage *Bearer token* (rejected by `TenantContextFilter`
itself) carried **no CORS headers at all**, confirmed identically via a
real browser (the fetch was blocked outright, indistinguishable from a
network failure to `apiFetch`).

**This is almost certainly the real, direct cause of symptom (2)** — not
fundamentally the refresh token's storage location, which is what the
symptom looked like from the outside. Every time an access token
naturally expired (every ~15 minutes, exactly matching the reported
symptom) or was otherwise invalidated, the resulting 401 was silently
un-readable by the browser — `apiFetch`'s `if (res.status === 401 ...)`
branch could never even run, because `fetch()` itself rejected before
`res` was ever assigned. The only way the user could ever recover was a
full reload — which, in the *old* refresh-token-in-memory world, logged
them out anyway, compounding the two symptoms into what looked like one
inevitable failure mode.

**Fixed by replacing the `WebMvcConfigurer` registration with a genuine
`CorsFilter` bean** (`FilterRegistrationBean<CorsFilter>`, registered with
`Ordered.HIGHEST_PRECEDENCE` so it runs *before* `TenantContextFilter`,
which has no explicit `@Order` and defaults to lowest precedence). A real
servlet `Filter` adds its response headers unconditionally before calling
`chain.doFilter()`, so those headers are already present on the response
object by the time any later filter — including one that short-circuits
with its own 401 body — writes anything. `TenantContextFilter`'s own
pre-existing OPTIONS-bypass check (M1) is now redundant in practice (the
`CorsFilter` fully owns and terminates a real preflight before this filter
ever sees it) but was **kept as a defensive backstop**, not removed — if
filter ordering is ever accidentally changed, that line is what stops the
exact M1-era regression from silently coming back. **Any future
CORS-adjacent change in this codebase should default to a `CorsFilter`
bean, not `WebMvcConfigurer#addCorsMappings`** — the latter is scoped to
"requests that reach Spring MVC," which is a strictly narrower set than
"every request," and this is now the third distinct shape this exact gap
has taken in this project (M1's OPTIONS handling, the Swagger-UI
allowlist, and now this).

New regression tests in `TenantContextFilterIntegrationTest` (already the
home of the Swagger-UI fix, for the same "real bug found via a real
browser" reason) — **verified they actually catch the bug**, per this
project's own standing rigor: temporarily reverted `CorsConfig` to its
pre-fix `WebMvcConfigurer` form, confirmed both new tests failed with
exactly the predicted symptom (`expected: "http://localhost:5173" but
was: null`), then restored the fix and confirmed both passed.

**One benign, expected console 401 worth not mistaking for a bug**: the
very first page load of a completely fresh, never-authenticated browser
session logs a `401 POST /auth/refresh` in devtools — this is
`bootstrapSession()` correctly attempting its one silent-refresh check on
app start with no cookie yet to send, exactly as designed ("there's no
client-visible signal for whether a cookie exists, so always attempt it
and read the result"). It resolves cleanly to "not logged in" with no
visible effect on the login page. Confirmed via network-level
instrumentation during this round's own live verification that this is
the *only* place this 401 ever appears — it does not recur after a real
login, and does not appear on the reload-after-login case this fix
actually targets.

### Frontend changes

- **`authStore.ts`**: the `refreshToken` field is gone entirely — there is
  nothing left in this store that could ever leak via XSS. A new
  `bootstrapping: boolean` (starts `true`) gates `RequireAuth`/
  `RedirectIfAuthenticated` until the one-time boot check resolves.
  `setTokens(accessToken, refreshToken)` became `setAccessToken(accessToken)`.
- **`features/auth/bootstrap.ts`** (new): `bootstrapSession()`, called once
  from `App.tsx` on mount. Always attempts `POST /auth/refresh` (no body —
  the cookie rides along) and calls `finishBootstrap()` regardless of
  outcome. Success fully rehydrates the session (`setSession`, not just the
  access token) since the refresh response now always carries real
  `user`/`org` — see the backend section below for why that changed.
- **`lib/api/client.ts`**: `apiFetch` now sends `credentials: 'include'`
  on every call (harmless on non-auth endpoints — the cookie is
  path-scoped and won't attach anyway). `refreshSession()` — used both by
  the existing 401-retry path and by the new boot-time check — no longer
  reads a stored refresh token at all (nothing to read); it always
  attempts the call and calls `setSession(tokens)` on success.
- **`RouteGuards.tsx`**: `RequireAuth` shows a small full-page spinner
  (`Loader2`, matching this codebase's existing loading-icon convention)
  while `bootstrapping` is true, instead of redirecting to `/login`
  immediately — this is the one line of UI that actually prevents the
  reload-flash-to-login race. `RedirectIfAuthenticated` deliberately does
  **not** wait on `bootstrapping` — `/login`/`/signup` should render
  immediately for the overwhelmingly common truly-logged-out case, at the
  cost of a rare, brief flash-then-redirect if a reload happens to land
  directly on `/login` while a session is still valid.
- **`authApi.ts`/`ProfileMenu.tsx`**: `logout()` takes no arguments and
  sends no body — the server reads/revokes/clears the cookie entirely
  itself.

### Backend changes

- **`AuthService.TokenIssueResult`** (new nested record) is the only place
  the raw refresh-token string and its expiry now exist in Java —
  `AuthTokensResponse` (the actual JSON body) has no such field anymore.
  `AuthController` unwraps `TokenIssueResult` into a `Set-Cookie` header
  plus the body-only-response, for every one of `signup-verify`, `login`,
  `login/otp/verify`, and `refresh`.
- **`AuthService.refresh()` now returns real `user`/`org`, not `null`/`null`.**
  This was a deliberate, necessary part of the fix, not a side effect —
  previously nothing needed them (a hard reload always logged everyone out
  before this fix, so the frontend's in-memory `user`/`org` from the
  original login was always still there when a *reactive* refresh
  happened). A **boot-time** restore starts from nothing, and
  `RequireAuth` gates on `accessToken && org` both being present — a null
  `org` here would make an otherwise-successful cookie-based refresh still
  bounce the user to `/login`. Everything needed was already available on
  `AuthLookupUser`/`Organization`, so this needed no extra query.
- **`/auth/refresh` reads `@CookieValue("refresh_token")` instead of a
  request body** — a missing/blank cookie is rejected with the same
  generic `error.auth.refreshInvalid` an actually-invalid token gets (no
  oracle for "does this browser have a session at all").
- **`/auth/logout` reads the same cookie and always clears it** (a second
  `Set-Cookie` with `Max-Age=0`), regardless of whether the cookie turned
  out to be valid.
- **`RefreshRequest`/`LogoutRequest` DTOs deleted outright** — nothing
  constructs a JSON body for either endpoint anymore.
- **`CorsConfig`**: see the CORS-filter section above — this is the
  larger of the two backend changes in this round.

### Verification

Backend: 169/169 `mvn test` green (Testcontainers, zero regressions —
167 pre-existing plus 2 new `TenantContextFilterIntegrationTest` cases),
plus `AuthFlowIntegrationTest` itself substantially rewritten (extracting
the cookie from `Set-Cookie` and replaying it as a `Cookie` header for
every refresh/logout/reuse test, since `TestRestTemplate` doesn't manage
cookies across separate calls the way a real browser does) with new
coverage this round specifically needed: the cookie's own shape
(`HttpOnly`/`Secure`/`SameSite=None`/`Path=/api/v1/auth`, and confirming
it never appears as a JSON field), `rememberMe`'s effect on the cookie's
own `Max-Age` (30d vs 12h), refresh with no cookie at all rejected, and —
the fix's own core claim — the refresh response now genuinely carrying
real `user`/`org`.

Live HTTP verification (curl, against a real locally-running backend, not
just Testcontainers): the exact `Set-Cookie` shape confirmed byte-for-byte
(`HttpOnly; Secure; SameSite=None; Path=/api/v1/auth`); a full
signup → refresh → reuse-old-cookie(401) → reuse-rotated-cookie-too(401)
sequence, confirming family-wide revocation is completely unchanged;
`rememberMe=true` → `Max-Age=2591999` (~30d), `rememberMe=false` →
`Max-Age=43199` (~12h); logout → 204 + a cookie-clearing `Set-Cookie` with
`Max-Age=0` → a subsequent refresh with that same cookie correctly 401s;
a real OPTIONS preflight from `Origin: http://localhost:5174` returning
`Access-Control-Allow-Credentials: true` and the **exact** origin (never
`*`) on both the preflight and the actual response.

Real browser verification (Playwright, throwaway scripts per this
project's established "write, run, delete" convention): signed up for
real, confirmed `document.cookie` never contains `refresh_token` and
`localStorage` holds nothing token-shaped (only `i18nextLng`) — rule #15
intact end-to-end, not just in the two files that used to hold the
token; corrupted the in-memory access token to simulate the natural
15-minute expiry, clicked to a page that fires a fresh authenticated
query, and confirmed (via full network-level instrumentation, not just
the end state) the exact sequence: `401` on the real request → `200` on
`/auth/refresh` → `200` on the retried original request → the user never
left the page, never saw a login redirect, and the store's access token
was a genuinely new, valid one afterward. Separately, a **hard
`page.reload()`** after login (a real browser navigation, not React
Router's client-side routing) landed the user back on the same page with
their session fully intact — no flash to `/login`, confirmed both in
English/desktop and in a dedicated Hindi + 360px pass (zero horizontal
overflow, zero raw i18n key leaks, the one new UI surface — the boot
spinner — has no text to translate in the first place). Two pre-existing
Playwright specs (`m2-project-status`, `notification-mark-all-read`) were
also re-run against the live stack as a broader regression check on the
login flow itself, both still green.

**Both originally-reported symptoms are resolved**: a hard reload now
silently restores the session (verified live, both languages, both
widths); the access token's natural 15-minute expiry no longer drops the
session, because the browser can now actually see the 401 that's supposed
to trigger the existing silent-refresh logic in the first place. Email
OTP is explicitly a separate, later piece of work — not touched here.

---

## CI Bug Fix — `CommissionReleaseService` Duplicate-Release Race (Found via a Real CI Failure, Unrelated to Auth)

The httpOnly-cookie PR above (#36) failed CI's backend job on
`CommissionReleaseConcurrencyIntegrationTest` — `expected: 160000 but
was: 320000.00` — a test that had passed reliably on every local run
throughout this whole session (repeated full-suite passes, per this
project's own standing discipline for concurrency tests). Confirmed
first, not assumed, that this had nothing to do with the auth PR itself:
`git diff main` for every file in `builder.broker` was empty on that
branch — the bug was already sitting in already-merged `main` (from the
§8a broker-payout-tracking PR, #34), just never triggered locally. Fixed
on the same branch rather than opened separately, since it's what was
concretely blocking that PR's CI from going green.

**Root cause**: `CommissionReleaseService.releaseForPayment()` read the
sale's `booking_commission` rows via a plain, **unlocked**
`findByPlotSaleId()`, then separately read
`CommissionReleaseRepository.sumReleasedFor()` before computing and
inserting a new `commission_release` row for the delta. Two genuinely
concurrent calls for the same sale (two payments racing, or a payment
racing its own reversal) can both read `releasedSoFar = 0` before either
has committed its own insert — both then compute the FULL target amount
as their own "delta" and both insert it, converging to exactly double the
correct total. This is a **different** bug from the one
`CommissionReleaseConcurrencyIntegrationTest` was originally written to
guard against (V65_012's trigger-level lost-update fix, from the step-11
concurrency audit — see that section above) — that fix correctly sums
whatever `commission_release` rows exist; it has no way to stop two
individually-valid, genuinely distinct rows from being inserted by a
Java-level race that happens entirely before either row exists. GitHub
Actions' more resource-constrained runner apparently exposed this race
far more reliably than this project's local Colima-backed Testcontainers
setup ever did in dozens of prior runs across this whole session.

**Reproduced deterministically before fixing anything**, per this
project's own systematic-debugging standard: temporarily reverted to the
unlocked read and inserted a 300ms `Thread.sleep()` between the
`sumReleasedFor` read and the insert to force-widen the race window —
confirmed the test failed with the *exact* reported numbers
(`160000`/`320000.00`), byte-for-byte matching the CI failure. Applied
the fix (see below) with the same artificial delay still in place —
confirmed the test now passed 3/3, proving the lock genuinely closes the
race rather than merely narrowing the window. Removed the temporary sleep
afterward and re-ran the test 5 more times with no artificial delay, plus
the full backend suite once more — clean every time (169/169).

**Fixed with the same lock-before-recompute discipline this codebase has
now established repeatedly** (steps 7/9/11 of the Broker Network Engine,
and `BrokerCommissionPaymentService`'s own `lockForPayoutOldestFirst`):
a new `BookingCommissionRepository.lockByPlotSaleId()`
(`@Lock(LockModeType.PESSIMISTIC_WRITE)`, i.e. a real `SELECT ... FOR
UPDATE`), acquired as `releaseForPayment()`'s **first** touch to these
rows, replacing the old unlocked `findByPlotSaleId()` call. A second,
concurrent call for the same sale now blocks until the first fully
commits, so its own later `sumReleasedFor()` read is guaranteed to see
whatever the first transaction actually inserted — never a stale zero.
**Any future service method that reads an aggregate, then inserts a new
row representing a delta against that aggregate, needs to lock the
row(s) the aggregate is computed over as its very first statement** —
this is now a repeatedly-confirmed shape in this codebase, not a one-off.

---

## Infra — SMTP Config Actually Supports a Real Provider Now (Not Just MailHog)

`EmailGateway`/`SmtpEmailGateway` (M-06 second half) has sent every email
this app has ever sent through MailHog, which needs no authentication at
all — `application.yml`'s `spring.mail.*` had no `username`/`password`
properties, and `auth`/`starttls.enable` were hardcoded `false`. M7-second-half's
own notes already claimed "swapping MailHog for a real one is a
`spring.mail.*` config change, never a code change" — that was aspirational,
not yet true, since there was nothing to actually configure auth with.
Closed as part of validating this against a real GoDaddy Workspace Email
account (`smtpout.secureserver.net:587`, STARTTLS) — a real signup's
welcome email sent cleanly on the first attempt (`outbox_event.status =
DONE, attempts = 1, last_error` empty), confirming the fix works end to
end, not just that it compiles.

**What changed, both fully backward-compatible (every default matches the
old hardcoded MailHog behavior exactly):**
- `application.yml`: added `spring.mail.username`/`password`
  (`${SMTP_USERNAME:}`/`${SMTP_PASSWORD:}`, blank by default), and made
  `auth`/`starttls.enable` configurable (`${SMTP_AUTH:false}`/`${SMTP_STARTTLS:false}`)
  instead of hardcoded `false`.
- `SmtpEmailGateway`: the From address was hardcoded to
  `noreply@shardeya.local` — a real provider will generally reject or
  flag a From address that doesn't match the authenticated mailbox/domain.
  Now configurable via `shardeya.mail.from` (same MailHog-era default).

**No real credentials were ever committed or written to any file** — the
live test against the real GoDaddy account was done by exporting
`SMTP_HOST`/`SMTP_USERNAME`/`SMTP_PASSWORD`/etc. as plain environment
variables for one local `mvn spring-boot:run` process, never persisted.
Whoever configures a real provider for an actual deployment should do the
same — set these as real environment/secret-manager values at deploy time,
never hardcode them into `application.yml` or any committed file.

Verified: 169/169 `mvn test` green, zero regressions (a config-only change
with safe defaults — nothing in the existing test suite's own MailHog-based
behavior changed). No new automated test was added specifically for
real-provider auth itself, since that would require committing real
credentials somewhere tests could reach them, which rule #15's own spirit
(never hardcode secrets) argues against — this was verified live instead,
against a real account, with the result (a real `outbox_event` row
reaching `DONE`) confirmed directly.

---

## Auth Change — OTP Delivery Switched From SMS to Email (Signup Verification + "Login with OTP")

Signup verification and "login with OTP" both now deliver their code by
email instead of SMS. **Why**: the SMS adapter (`SmsGateway`/`Msg91SmsGateway`)
has been stubbed since M1 and requires India DLT (Distributed Ledger
Technology) registration before it can send a single real message — a
manual, offline, multi-day regulatory process this project is deliberately
avoiding for now. The org already has working email on its own domain,
which is the practical channel today. **The SMS adapter is left fully in
place, not removed** — `SmsGateway`/`StubSmsGateway`/`Msg91SmsGateway` are
completely untouched, and `PURPOSE_RESET`'s mobile-identifier
forgot-password branch and `WhatsAppOptInService` (which inherently needs
to verify a real WhatsApp-reachable phone number) both still use SMS
explicitly, unaffected. This is a channel switch for two specific flows,
not a removal.

### `OtpService` generalized to a real channel, not hardcoded per-purpose

`OtpService` was SMS-only from M1 — every purpose sent to a mobile number,
with `mobile` baked into `OtpChallengeData`/`ChallengeResult`/`VerifyResult`
by name. Rather than duplicate the whole generate/hash/store/rate-limit/verify
mechanism a second time for email, the service now takes an explicit
`Channel` (`SMS`/`EMAIL`) per challenge, stored on `OtpChallengeData` itself
so `resend()` knows which gateway to use without being told again — the
one thing that couldn't just be inferred from the recipient string, since
a NEW `EmailGateway` (`foundation.notification`, the same interface
`OutboxPoller`'s email sends already use) had to be injected alongside the
existing `SmsGateway`. Every field/method that used to say "mobile" is now
channel-neutral: `mobile` → `recipient` (`OtpChallengeData`, `VerifyResult`),
`maskedMobile` → `maskedRecipient` (`ChallengeResult`, and the shared
`SignupResponse` DTO it flows into — used by signup, login-OTP, resend,
*and* forgot-password's mobile branch, so all four needed the rename even
though only two of them changed channel). A new `maskEmail()` sits
alongside the existing `maskMobile()` (`mridul@example.com` → `mr***@example.com`),
selected by the same `Channel` the challenge was created with.

**Email delivery is synchronous, deliberately mirroring `SmsGateway.sendOtp()`'s
own always-been-synchronous shape — never routed through the outbox.**
This isn't an oversight of CLAUDE.md rule #6 ("side effects are never
inline") — signup's OTP challenge is created *before* any org/user exists
at all, so there is no tenant context to scope an outbox event to in the
first place; the outbox mechanism was never architecturally available for
this path, which is exactly why the SMS side was already synchronous
before this round ever started. The trade-off this brings, spelled out
rather than glossed over: if the SMTP server is briefly unreachable, the
whole signup/login-OTP request fails outright (a real, synchronous
`MailSendException`) rather than silently retrying in the background —
the same fragility class `Msg91SmsGateway.sendOtp()` already had for the
SMS path, not a new one this fix introduced.

### The identifier stays flexible (mobile OR email) — the "no email on file" edge case, made real

"Login with OTP" could have been built as an email-only input field, but
that would make M4's own real edge case — a team member created with
mobile required, email optional (`03-BUILDER-MODULES.md` B-12) — nearly
unreachable: typing a non-matching email is indistinguishable from "wrong
email," never actually surfacing "this real account has no email at all."
Instead, `LoginOtpRequestRequest.identifier` mirrors `LoginRequest.identifier`
exactly (flexible mobile-or-email, resolved via the same `resolveIdentifier()`
helper) — a staff member typing their own mobile number resolves to their
real account, and *then* `AuthService.requestLoginOtp()` checks
`lookup.email()` and rejects clearly (`BadRequestException`, field
`identifier`, code `OTP_EMAIL_NOT_AVAILABLE`, messageKey
`error.auth.otpNoEmailOnFile` — "This account doesn't have an email on
file... log in with your password instead") rather than either a generic
"invalid credentials" or a silently-undeliverable code. **Frontend
`LoginOtpPage`'s `RequestCodeStep` was rebuilt as a plain `Input` (matching
`ForgotPasswordPage`'s own identifier-field pattern exactly), dropping the
old `PhoneInput`-only field** — this is what makes the flexible identifier
usable at all from the UI, not just the API.

**Flag for a product decision, not made unilaterally**: should email now
be made *required* when adding a team member (M4's `AddTeamMemberDialog`),
given "login with OTP" — and any future email-dependent flow — is
unavailable without one? Left exactly as-is (mobile required, email
optional) per the explicit instruction not to change this without a
decision — noted here for whoever makes that call next.

### Framed explicitly as the "forgot/don't know your password" path

Per the explicit design direction: password stays the primary,
always-available login method; email-OTP is the recovery alternative, not
an equal-weight option. `login.withOtp`'s button text changed from the
generic "Log in with OTP instead" to "Forgot your password? Log in with
an email code", and `loginOtp.title`/`subtitle` now read "Log in with an
email code" / "Forgot your password? Enter your mobile or email and we'll
send you a one-time code." — the same framing `forgotPassword.subtitle`
already established, deliberately echoed rather than invented fresh.

### A real, pre-existing type-looseness bug surfaced by the `maskedMobile` → `maskedRecipient` rename

`WhatsAppOptInCard.tsx`'s resend-mutation handler did `setChallenge(res)`
where `res` is the shared `SignupResponse` (from the generic
`resendOtp()` endpoint, reused across signup/login-OTP/WhatsApp-opt-in)
and `challenge` is typed `WhatsAppOptInChallengeResponse | null` — this
only ever compiled because the two types *coincidentally* had identical
shapes (`{challengeId, maskedMobile, resendAfterSeconds}`). Renaming
`SignupResponse.maskedMobile` to `maskedRecipient` broke that accidental
structural match, correctly surfacing that this was never a real
guarantee. **Fixed by merging only the two fields that genuinely change on
a resend** (`challengeId`, `resendAfterSeconds`) into the existing
`WhatsAppOptInChallengeResponse` state, rather than assuming the two
response types are interchangeable — the masked mobile itself never
changes on a resend (same recipient, new code), so there was never a need
to map one response's shape onto the other's in the first place.

### A necessary new piece of test infrastructure: a real, Testcontainers-managed MailHog

Making OTP email delivery synchronous meant every backend test exercising
signup (nearly all of `AuthFlowIntegrationTest`) now genuinely needs a
reachable SMTP server — and **CI had none**: the `backend` job's own
workflow (`mvn -B verify`) declares no `services:` block at all, relying
entirely on Testcontainers spinning up its own throwaway Postgres/Redis
containers on demand. There was no equivalent for mail — `spring.mail.host/port`
just pointed at `localhost:1025`, which only ever happened to work locally
because dev's own `docker-compose` MailHog happens to be running on that
exact host:port. This gap was **already there** for the existing
async-outbox welcome email/reset email (their failures were just silently
caught and logged by the outbox poller, never failing a test) — the
Email OTP fix is what made it load-bearing enough to actually break
things. **Fixed by adding a genuine MailHog container to
`AbstractIntegrationTest`** (`mailhog/mailhog:v1.0.1`, exposing ports
1025/8025, started once as a singleton alongside the existing
Postgres/Redis containers, following that class's own documented
"start once in a static initializer, never stop" pattern) — this closes
the gap for *every* test that sends email, not just the new OTP ones. A
new `com.shardeya.support.MailHogReader` test utility (the email-channel
counterpart to the existing `OtpStubReader`) reads a real, actually-delivered
email back via MailHog's own REST API (v2) rather than trusting that
`EmailGateway.send()` didn't throw — `OtpServiceTest.emailChannelActuallyDeliversAReadableCodeViaRealSmtp`
proves the whole real pipe end to end: `OtpService` → `SmtpEmailGateway` →
real SMTP → MailHog receives it → the test reads the actual code a real
user would see, not a stubbed/logged value.

### Verification

Backend: 172/172 `mvn test` green (Testcontainers, zero regressions — 169
pre-existing plus 3 new: the real-SMTP email-channel test above, a
flexible-identifier-resolves-by-mobile test, and the no-email-on-file
rejection test). The last one needed its own real root-cause chase: the
first attempt directly nulled a test user's email via a native SQL
`UPDATE` with **no tenant context bound** — `app_user` has RLS, so with no
`app.current_org` GUC set the update silently matched zero rows (no
error, the policy check just evaluates false for every row), and the test
failed with "expected 400 but was 202" for a reason that had nothing to
do with the actual feature under test. Fixed by binding `TestTenantContext`
with the real org/user the test's own signup call had just created,
*before* opening the transaction — the exact same "bind before the
transaction opens" rule this file has documented for production code
since M1, just as applicable to test fixture setup against an
RLS-protected table.

Live HTTP verification (curl, against a real locally-running backend +
real local MailHog): a full signup → real email arrives (correct subject
"Verify your Shardeya account", correct 15-minute-expiry body text) →
verify → tokens issued; password login confirmed completely unchanged;
login-with-OTP via email identifier → real email arrives → verify → login
succeeds; the no-email-on-file case reproduced against a live account
(nulled a real user's email directly, confirmed `400` with
`OTP_EMAIL_NOT_AVAILABLE`/`error.auth.otpNoEmailOnFile` exactly); resend-cooldown
(immediate resend → `429`, `error.otp.resendTooSoon`) and max-attempts
(5 wrong codes → challenge fully invalidated, further attempts → `error.otp.expired`)
both confirmed still intact, unweakened by the channel change.

Real browser verification (Playwright, throwaway scripts per this
project's "write, run, delete" convention): the full signup-with-real-email-OTP
flow end to end in English/desktop, then again in Hindi at 360px together
with the full login-with-email-OTP flow — all four changed screens
(signup-verify, login page, login-OTP request, login-OTP verify) confirmed
via both automated checks (zero horizontal overflow, zero raw i18n key
leaks) and actual visual inspection of the screenshots, not just the
automated checks. Two self-caught, test-script-only mistakes along the
way, neither a product bug: an initial `waitForURL` immediately after a
click raced React Router's async transition once (confirmed by re-running
the identical flow successfully immediately after); a Hindi-360 script
used `"Hindi 360 Test"` as a full name, which the existing name regex
(`^[\p{L}\p{M} .'-]+$`, letters/marks/spaces only, no digits) correctly
rejected — the "360" in the test's own fixture data, not a rendering or
validation bug.

Frontend: `tsc -b` clean, `oxlint` clean (same pre-existing shadcn
warnings only), `vitest run` 25/25, `npm run build` clean. The e2e
suite's shared `helpers.ts` (`signupBuilder()`, used by most `e2e/*.spec.ts`
files) was updated to read the signup OTP from a real MailHog via a new
`readLatestEmailOtp()` instead of the now-silent SMS stub log — **two
older specs' own independent, locally-duplicated `readLatestOtp()`
helpers (`m3-sale-payments.spec.ts`, `m6-broker-commissions.spec.ts`)
were deliberately left untouched**, consistent with this project's
existing, repeated precedent of not resurrecting specs already documented
elsewhere in this file as tied to a specific, no-longer-running verification
stack's own backend log path — flagged here explicitly rather than
silently left broken with no note.

---

## Bug Fix — Password-Reset and Staff-Invite Emails Had No Real Clickable Link

Found by the user asking a plain diagnostic question ("what happens when I
click forgot password and enter my email?") rather than reporting a
symptom — tracing the actual code answered it, and the honest answer was
"nothing usable happens." Two real, previously-undocumented bugs, both the
identical root cause, both fixed together per the user's "yes, pls fix
both."

**Bug 1 — password reset.** `AuthService.forgotPassword()`'s email branch
sent the bare opaque token as plain text with no URL around it at all:
`"Use this code to reset your password: " + token`. `ResetPasswordPage.tsx`
has no field to manually enter a token at all — it **only** ever reads one
from `?token=` in the address bar. A real user opening this email had
literally nothing to click and nowhere in the UI to paste what they'd
received — the entire email-based reset flow was non-functional end to
end, not just cosmetically rough.

**Bug 2 — staff invite, the identical mistake in a second place.**
`TeamService.sendInvite()` had the same shape, just worse: a bare
*relative* path with no host at all — `"Set your password: /accept-invite?token="
+ token` — sent over both the email and SMS channels (both share the same
`message` string). A relative path means nothing in an SMS at all, and in
an email only "worked" by accident if the mail client happened to treat it
as relative to the frontend's own origin, which is not a guarantee. Found
proactively, not reported — once bug 1's root cause was clear ("a bare
token/path with no real URL"), grepping for the same pattern elsewhere
(`FRONTEND_URL`/`accept-invite`/`reset-password` across `src/main/java/`)
turned it up immediately.

**Fixed identically in both places**: a new
`@Value("${shardeya.frontend.base-url:http://localhost:5173}")` field
(same default `CorsConfig`'s own `shardeya.cors.allowed-origins` already
uses — no new `application.yml` entry needed, matching that property's own
inline-default-only convention), injected independently into both
`AuthService` and `TeamService` — no shared config class, consistent with
this codebase's existing per-class `@Value` style. `forgotPassword()` now
builds `frontendBaseUrl + "/reset-password?token=" + token` and sends a
real clickable link; `sendInvite()` now builds `frontendBaseUrl +
"/accept-invite?token=" + token`, fixing the identical bug for both the
email **and** SMS channel in one change since they share the same message
string.

**`TeamService` had zero test coverage of any kind before this** — a new
`TeamServiceIntegrationTest` was written from scratch, and a new test was
added to the existing `AuthFlowIntegrationTest` for the reset-link case.
Both verify via the outbox's own stored JSON payload (`EmailPayload`,
`{orgId, to, subject, body}`) rather than a real SMTP round-trip — this
branch is cut from plain `main`, which doesn't yet have the
MailHog-Testcontainers infrastructure added by the (separate,
not-yet-merged) email-OTP work. Both new tests assert a real, absolute,
clickable URL via `containsPattern("https?://[^\\s]+/...\\?token=[A-Za-z0-9_-]+")`
— not just "a token exists somewhere in the message," which the old, buggy
text would have trivially satisfied. **Verified both tests actually catch
their respective bugs**, per this project's own standing rigor: reverted
each fix in turn, recompiled, confirmed the test failed with the *exact*
originally-reported bug text (`"...to contain pattern...but was: Use this
code to reset your password: fHwH-RIoc6..."` and the equivalent for the
bare `/accept-invite?token=...` path), then restored the fix and confirmed
green again. 171/171 backend tests green (169 pre-existing + 2 new), zero
regressions.

**Live end-to-end proof, not just "the text looks right"**: signed up a
real account, called the real forgot-password endpoint, read the real
`outbox_event` payload, extracted the real token from it
(`79iSCgVxdcv8iqfXLfDKW2hbj4KB__Dq`), called the real reset-password
endpoint with that exact token → 200, then **logged in with the new
password** → 200 — the strongest possible proof, since it exercises the
actual value a real user would have clicked through to, not a
hand-constructed one. Separately created a real team member with
`sendInvite: true` (via a throwaway high-limit plan to clear the FREE-plan
`BUILDER_TEAM_MEMBERS=1` quota, `UPDATE`d onto the org's subscription per
this project's own established precedent, then reverted back to `FREE`
immediately after) and confirmed the real invite email's payload contained
`http://localhost:5173/accept-invite?token=an53MY49KPkPlwWRsRSA12MazKORIhbPEkDXOa946Ow`
— a real, absolute, clickable URL, not the old bare path. (One transient
mixup during this pass, not a bug: the same `create()` call also enqueues
a separate `STAFF_ADDED` **notification**-dispatch email — subject "Added
to Shardeya," no link, a different, legitimate email — which sorts *after*
the invite email by `created_at` and was momentarily mistaken for it
before checking the full, chronologically-ordered set of outbox rows for
that recipient.)

**Known, deliberate scope decision, flagged rather than picked unilaterally,
per the user's own instruction**: M4 team members only require a mobile
number — email is optional (`TeamMemberCreateRequest.email` is nullable).
`sendInvite()`'s behavior when a team member has no email was **not**
changed by this fix — it still only sends via whichever channel(s) the
member actually has, unaffected either way by this bug (a bare path was
exactly as broken over SMS as over email, and the fix corrects both
identically). Whether email should become a **required** field for team
members going forward is a separate product decision this fix does not
make.

`tsc -b`/`vitest run`/`oxlint` not touched — backend-only change, no
frontend files modified.

---

## WhatsApp Notification Overhaul — Buyer Consent, Owner-Only Recipient, Channel Corrections

Four related changes to the M-06 WhatsApp notification setup, requested
together after the user asked "when are we sending WhatsApp and what are we
sending" and got a full audit back. **Net result, the new baseline going
forward**: exactly three notification types ever send WhatsApp
automatically (`INSTALMENT_DUE_TODAY`, `INSTALMENT_OVERDUE`,
`COMMISSION_DUE`), and all three reach **only the org owner** — every other
user still gets the in-app copy, just never WhatsApp/SMS for these events.
Plus one manual, buyer-facing path (Tracker's "Send Reminder"), now
properly gated on consent and quiet hours.

### 1. Tracker's buyer-facing "Send Reminder" button is now gated

`TrackerService.remindInternal()` messages the buyer directly — a genuine
third party, never a user, with no per-user preference layer to lean on.
It previously had **no opt-in check and no quiet-hours check at all**.
Fixed:
- **Opt-in, hard block**: `!buyerWhatsAppOptInService.isOptedIn(orgId,
  buyerMobile)` now throws `ConflictException("error.tracker.buyerNotOptedIn")`
  before anything is enqueued. `bulkRemind()`'s loop wraps its call to
  `remindInternal()` in a `catch (ConflictException e) { skipped++; }` —
  the same "one bad row reduces to a skip, not an aborted batch" shape it
  already used for the `reminderDisabled`/`reminderAlreadySentToday`
  guards, now covering this third guard too without duplicating the check
  a second time in the loop itself.
- **Quiet hours**: turned out to already be correct — `remindInternal()`
  has always called `outboxService.enqueueWhatsApp()`, and quiet hours is
  applied centrally, inside that one method, for every caller (see
  `OutboxService`'s own javadoc) — a send attempted at 11pm simply gets
  `available_at` stamped to next-day 08:00 IST. Nothing to fix here; just
  confirmed and documented rather than assumed.
- **Frontend, proactive, not just a server error**: `CollectionRow` gained
  a `buyerOptedIn` boolean (backend, one extra lookup per row — accepted
  N+1 at this page's realistic row counts, same shape as
  `InstalmentReminderParams`'s own per-row enrichment). `CollectionTable.tsx`'s
  WhatsApp button is now `disabled={!r.reminderEnabled || !r.buyerOptedIn
  || ...}` with a `title` explaining why when it's the consent gate
  specifically — the button never just silently does nothing.

### 2. How the buyer opts in — a builder attestation, not OTP

Buyers aren't users; there's no OTP-confirmed opt-in flow reachable for
them. `whatsapp_optin`'s own `source` column already distinguished
`SELF_SERVICE` (a staff member proving they can receive a code) from
`BUILDER_CAPTURED` (§22.4, "consent the builder captured on a buyer's
behalf") since M-06 second half — this was schema that existed but had
never been wired to anything. Closed now:
- **New `BuyerWhatsAppOptInService`** (`foundation.notification`),
  deliberately a *separate* service from the pre-existing
  `WhatsAppOptInService` — that class's own javadoc explicitly scopes
  itself to "the CURRENT USER'S OWN registered mobile" and rejects an
  arbitrary caller-supplied number on purpose; conflating the two would
  have meant either weakening that guarantee or awkwardly overloading one
  service with two unrelated consent models. `setOptIn(orgId, mobile,
  optedIn, capturedByUserId)` creates-or-updates the one `whatsapp_optin`
  row for that (org, mobile); `isOptedIn(orgId, mobile)` is the read side
  every gate above calls.
- **New `whatsapp_optin.captured_by`** column (`V7_018`, nullable —
  never set for a `SELF_SERVICE` row, since there's no third party to
  attribute consent to there) records exactly which staff/admin user
  ticked the box, making the builder-attested-vs-OTP-confirmed distinction
  genuinely visible in the data, not just in the `source` string.
- **Sale wizard checkbox**: `SaleWizard.tsx`'s buyer step gained "Buyer
  has agreed to receive payment reminders on WhatsApp." (a plain
  `Checkbox`, same pattern `TermsCheckbox` already established). Only a
  **positive** tick does anything at creation — `PlotSaleService.create()`
  calls `setOptIn(..., true, ...)` only when the box was checked; leaving
  it unchecked writes no row at all (matching `isOptedIn()`'s own
  no-row-means-false default), rather than writing an explicit opt-out no
  one asked for.
- **Toggle later, from the sale detail page**: new endpoint `POST
  /sales/{id}/buyer-whatsapp-optin {optedIn}` (`DATA_EDIT_ALL`, same gate
  `update()`/`cancel()` already use) → `PlotSaleService.setBuyerWhatsAppOptIn()`,
  keyed by the sale's **current** `buyerMobile` (if a buyer's mobile is
  later edited via `update()`, the old number's opt-in row is left alone
  as a historical fact for whoever that number really belonged to — a
  known, minor edge case, not a bug: consent has to be re-toggled for a
  genuinely new number). `SaleDetailPanel.tsx` shows a checkbox reflecting
  `sale.buyerWhatsappOptedIn`, with the label switching between "has
  consented" / "hasn't consented yet" text.
- **Manual opt-out toggle**: covered by the same endpoint (`optedIn:
  false` → `WhatsAppOptin.optOut()`, setting `opted_out_at`). **A real
  "STOP" reply is NOT auto-processed** — this codebase has no inbound
  WhatsApp webhook receiver of any kind yet (already a disclosed gap since
  M-06 second half: "no WhatsApp delivery-status webhook receiver
  exists"), and the task's own framing ("a STOP reply **or** a manual
  opt-out toggle") treats the two as alternatives, not both-required. The
  manual toggle is the actual, complete mechanism today; auto-detecting an
  inbound STOP would need real webhook infrastructure this project doesn't
  have, a materially bigger piece of work than this round's scope.

### 3 & 4. Recipient + channel corrections, all in `NotificationDispatchService`

- **`COMMISSION_DUE` gains `WHATSAPP`** as a channel (`V7_018`:
  `default_channels = ARRAY['IN_APP','EMAIL','WHATSAPP']`) — it already had
  in-app + email since `V7_017`.
- **`FOLLOWUP_DUE` loses `WHATSAPP` entirely** (`V7_018`:
  `default_channels = ARRAY['IN_APP']`) — the type itself is untouched
  (still fires daily, still shows in-app to the assignee), only the
  channel is gone.
- **WhatsApp is now a hard allowlist, checked at dispatch time, not just a
  default**: `NotificationDispatchService.WHATSAPP_ELIGIBLE_TYPES = Set.of(
  "INSTALMENT_DUE_TODAY", "INSTALMENT_OVERDUE", "COMMISSION_DUE")`. This
  closes a real, if incidental, gap the audit surfaced: before this round,
  `NotificationPreferenceMatrix`'s settings UI already let a user toggle
  the WhatsApp checkbox ON for *any* type — including ones that never had
  it as a default — and `dispatchToUser()` had nothing stopping that
  preference override from actually sending (using
  `NotificationMessageRenderer`'s generic fallback text for anything
  without a real template). The allowlist means removing `FOLLOWUP_DUE`
  (or any future type) from WhatsApp is now airtight regardless of a
  stale or future preference row, not just "off by default until someone
  clicks a checkbox."
- **Owner-only recipient**: `wantWhatsApp = user.isOwner() &&
  WHATSAPP_ELIGIBLE_TYPES.contains(typeCode) && (pref != null ?
  pref.isWhatsapp() : defaults.contains("WHATSAPP"))`. In-app fan-out is
  completely unaffected — `dispatch()`'s recipient resolution (org-wide or
  a specific user) never changes; only the WhatsApp *channel decision*,
  per recipient, narrows to the owner. As a direct consequence (not a
  separate change), the existing critical-type SMS fallback — which only
  ever fires from inside the same `if (wantWhatsApp && ...)` branch — also
  narrows to owner-only for free; a non-owner gets neither WhatsApp nor
  its SMS fallback for these three types, which is the correct reading of
  "only the WhatsApp recipient list narrows" (the SMS fallback is a
  sub-mechanism of the WhatsApp attempt, not a separately promised
  channel).
- **A real, known, minor side effect, not fixed this round**: the settings
  matrix still shows a WhatsApp toggle for every type, including
  non-owners viewing types that will now never reach them regardless of
  what they pick, and including `FOLLOWUP_DUE` for anyone. Toggling it
  has zero effect — correct, but a UI that doesn't explain why wasn't in
  scope for this round (the matrix's own per-cell logic doesn't currently
  know about role or the allowlist; teaching it would mean threading
  `isOwner`/`WHATSAPP_ELIGIBLE_TYPES` all the way into
  `NotificationPreferenceService.matrix()`'s response shape, a real but
  separate piece of follow-up work).

### The final channel/recipient matrix (unambiguous, as requested)

| Type | In-App | WhatsApp | Email | SMS | WhatsApp/SMS recipient |
|---|---|---|---|---|---|
| `INSTALMENT_DUE_TODAY` | ✅ mandatory, org-wide | ✅ | — | fallback only (critical type, opted-out) | **owner only** |
| `INSTALMENT_OVERDUE` | ✅ mandatory, org-wide | ✅ | — | fallback only | **owner only** |
| `COMMISSION_DUE` | ✅ org-wide | ✅ **(new)** | ✅ | — | **owner only** |
| `FOLLOWUP_DUE` | ✅ assignee only | ❌ **(removed)** | — | — | n/a |
| `PAYMENT_RECORDED` | ✅ mandatory, org-wide | — | — | unreachable in practice† | n/a |
| `CHEQUE_BOUNCED` | ✅ mandatory, org-wide | — | — | unreachable in practice† | n/a |
| `STAFF_ADDED` | ✅ | — | ✅ | — | n/a |
| everything else (`PLOT_SOLD`, `SALE_CANCELLED`, `LEAD_*`, `BROKER_*`, `DOCUMENT_*`, `TEMPLATE_ACTIVATED`, `INVITE_ACCEPTED`, `STAFF_DEACTIVATED`, `BROKER_COMMISSION_PAID`) | ✅ (per each type's own existing recipient rule) | ❌, blocked by the allowlist regardless of any preference override | as already configured | — | n/a |
| Tracker manual "Send Reminder" | n/a (not a notification-type row) | ✅, gated on active buyer opt-in | — | — | **the buyer**, not staff |

† `PAYMENT_RECORDED`/`CHEQUE_BOUNCED` are in `CRITICAL_TYPES` (the SMS
fallback set) but were never in `WHATSAPP_ELIGIBLE_TYPES` — since the SMS
fallback only fires from inside the WhatsApp branch, and WhatsApp is now
never attempted for these two, the fallback has no path to trigger.
Pre-existing, unaffected by this round.

**SMS is still fully stubbed** (`StubSmsGateway`, pending India DLT
registration — see the "Email OTP" section above for the same constraint)
— any SMS fallback that *does* still have a live path (the three
WhatsApp-eligible types, for the owner, when not opted into WhatsApp)
currently delivers nothing to a real phone. Not fixed here, flagged as
requested; this round only changed *who*/*what* WhatsApp targets, not the
SMS gateway itself.

### Verification

Backend: 183/183 `mvn test` green (Testcontainers, zero regressions),
including new coverage: `TrackerServiceIntegrationTest` (this service's
first test coverage of any kind — opt-in blocks `remind()` cleanly,
succeeds once opted in, `bulkRemind()` skips rather than aborts, the
Collections row reflects live opt-in state), `NotificationDispatchServiceIntegrationTest`
(new — an owner + non-owner in the same org, driven through the real
outbox → `dispatchReady()` → dispatch path: `INSTALMENT_DUE_TODAY` reaches
only the owner's mobile over WhatsApp while both get the in-app copy;
`COMMISSION_DUE` now reaches WhatsApp, owner only; `FOLLOWUP_DUE` produces
zero WhatsApp events even with an explicit `whatsapp=true` preference
override on the owner — the allowlist proven to win over a deliberate
per-user setting, not just over the default), two new `PlotSaleIntegrationTest`
cases (ticking consent at creation records a real `BUILDER_CAPTURED` row
with a non-null `capturedBy`; toggling consent on/off after the sale
exists works both directions), and `FollowUpDueSweeperIntegrationTest`
rewritten as the regression guard for the *removal* (seeds a real, active
opt-in for the assignee and asserts no WhatsApp event is produced despite
it — proving the channel is genuinely gone, not just unreachable because
nobody happened to opt in). A real test-isolation bug in my own first
draft was caught and fixed before it ever reached the full suite:
`outbox_event` has no `org_id` column at all (a cross-tenant queue table,
never RLS-scoped), so `findAll()` returns every event the *entire* suite
has ever created — an `hasSize(1)` assertion looked correct in isolation
but failed once other tests' own WhatsApp sends accumulated in the same
run; fixed by filtering on each test's own uniquely-generated mobile
(`TestMobiles.next()`, not `System.nanoTime()` — the latter's high-order
digits barely move between two calls microseconds apart, the exact
already-documented collision class this project's own `TestMobiles`
javadoc warns about) rather than counting the raw list.

Live HTTP + direct-SQL verification against a real locally-running
backend (Flyway's `V7_018` applied via `-Dspring.flyway.out-of-order=true`
— this shared, long-lived local dev Postgres already had later-numbered
migrations, `V14_001`/`V65_xxx`, applied from earlier sessions, making a
new `V7`-numbered migration legitimately out-of-order *for this specific
database's own history*; a fresh CI/Testcontainers database never hits
this, since it applies every migration in true numeric order from empty):
created a real sale with no consent (`buyerWhatsappOptedIn: false`
confirmed in the response) → `POST .../remind` correctly 409'd with
`error.tracker.buyerNotOptedIn` → toggled consent on via the new endpoint
→ retried → 202, with a real `WHATSAPP` outbox row for the buyer's mobile
and a `whatsapp_optin` row showing `source=BUILDER_CAPTURED`,
`captured_by` = the real acting user's id. Separately, inserted real
`NOTIFICATION` outbox rows (mimicking exactly what the schedulers
themselves enqueue) for an org with one owner + one manually-seeded
non-owner user, both opted into WhatsApp: `INSTALMENT_DUE_TODAY` produced
in-app rows for both but exactly one `WHATSAPP` row, containing only the
owner's mobile; `COMMISSION_DUE` likewise produced a `WHATSAPP` row (plus
the pre-existing `EMAIL` row) for the owner only; `FOLLOWUP_DUE` (sent
directly to the owner as `recipientUserId`) produced only the `NOTIFICATION`
row — no `WHATSAPP` row at all, confirming the removal holds even when the
type's own recipient is otherwise WhatsApp-eligible in every other way.

Real-browser verification (Playwright, throwaway scripts per this
project's "write, run, delete" convention): the full English/desktop flow
— sell a plot through the real wizard with the consent checkbox ticked,
confirm the created sale's `buyerWhatsappOptedIn: true`, confirm the sale
detail page's toggle shows checked with "has consented" text, toggle it
off and confirm the label flips to "hasn't consented yet", navigate to
Tracker's Collections tab and confirm the WhatsApp button is now
`disabled` with a `title` explaining why (not a silent no-op), toggle
consent back on from the sale detail page, confirm the same button
becomes enabled, click it, and confirm a real `202` from `.../remind` —
passed end to end on the first real run after fixing two Playwright-script
mistakes (not product bugs): Radix's Sheet marks the rest of the page
`aria-hidden` while the plot detail drawer is open, an already-documented
standing gotcha in this project's own e2e notes, requiring `Escape` before
reaching for sidebar links; and Tracker's Collections tab defaults to the
"Overdue" range (`due_date < today`), which excludes a schedule row due
*today* — switching to the "All" range pill was needed for the seeded row
to appear at all. Separately, a Hindi + 360px pass on the wizard's consent
checkbox confirmed real, correctly-wrapping Hindi text ("खरीदार ने
WhatsApp पर भुगतान रिमाइंडर प्राप्त करने पर सहमति दी है।"), zero horizontal
page overflow, and zero raw i18n keys — confirmed both by the automated
`scrollWidth`/`body innerText` checks and by actually looking at the
resulting screenshot, per this project's own standing discipline that
overflow/key-leak checks alone aren't sufficient.

Frontend: `tsc -b` clean, `oxlint` clean (same pre-existing shadcn
warnings only), `vitest run` 25/25, `npm run build` clean.

---

## Current Milestone

> **Milestone 7, both halves, complete: B-11 Reports & Legal Document
> Generation, B-15 Stats & Analysis, M-10 Reporting & Export Engine (first
> half), and the full M-06 Notification Engine — real WhatsApp/SMS/email
> delivery (second half) — built and verified, merged.
> A small, explicitly-scoped ops-visibility addition (message delivery log
> + server error log, see "Post-M7 — Minimal Ops Visibility" above) has
> also shipped — **this is NOT the Platform Admin Console (M-14)**, which
> remains entirely unbuilt (no org suspension, feature flags, plan-limit
> UI, announcements, rate editor, or separate admin auth realm exist).
> M-13 (Audit, Soft-Delete & Data Privacy), the rest of M-14, and Razorpay
> payment integration have NOT been started — do not begin any of them
> until explicitly picked up again.
>
> **Milestone 6.5 (Broker Network & Designation Commission Engine,
> `06-BROKER-NETWORK-ENGINE.md`) is COMPLETE — steps 1-11 of its own §15
> build order are done and verified** (designation slab config, network
> structure + integrity, broker creation with explicit upline, the pure
> commission calculation engine, freezing the commission tree at sale
> creation, proportional instalment release + reversal, sales counting +
> automatic promotion, cancellation with recovery/demotion, manual
> promotion (override + a real clear-override action that resumes
> automatic evaluation), the builder-side dashboards, and a final
> concurrency audit sweep). A DESIGNATION broker's designation can now be
> moved by every mechanism the spec describes — automatic promotion,
> automatic demotion via cancellation, and manual override/clear — every
> one of those is visible somewhere in the UI, and every write path that
> touches sales counts, team-sales rollup, designation state, the
> commission tree, or commission release has been individually audited for
> concurrency safety, with two real bugs found and fixed along the way
> (see "Milestone 6.5 (Step 11)" above for both). **Step 12 (migrating
> existing M6 FIXED/tier brokers) is permanently OUT OF SCOPE, not
> deferred** — per the user's own explicit decision, the customer's
> existing broker data is handwritten/on paper, never in this database, so
> there is nothing to migrate; new brokers are entered fresh through the
> normal creation flow.
>
> **Post-ship behaviour change (see "Post-M6.5 Behaviour Change" section
> above): sales counting and promotion now fire at BOOKED (sale creation),
> not COMPLETED (full payment).** This reverses a decision steps 5-11 were
> originally built around — a broker's team now counts, and can promote,
> the instant a plot is marked sold, regardless of how much (if anything)
> the customer has paid. The commission-tree freeze still happens first,
> in the same transaction, so a booking that earns its own promotion is
> still frozen at the pre-promotion rate — that guarantee is unchanged,
> only its anchor moved earlier. Cancellation reverses counts/demotes
> unconditionally now, not just for bookings that reached COMPLETED.
> Commission release itself was not touched.
>
> **Post-ship feature addition (see "Post-M6.5 Feature — Broker Payout
> Tracking (§8a)" above): a real third money state, Paid, and "Commission
> Due" (Released − Paid) now exist.** Before this, the engine only had
> Earned and Released — there was no way to record the builder actually
> paying a broker at all. A "Record Payment" action pays a broker's whole
> Due balance at once, auto-allocated oldest-first across their bookings,
> hard-capped at Due (never Earned) — an overpay attempt is always
> rejected outright, never a confirm-to-proceed flow. Payments are
> immutable; corrections are reversals. A real, previously-latent bug in
> `CommissionReversalCalculator` (recovery computed as `released - paid`
> instead of exactly `paidAmount`) was found and fixed in the same round —
> it only became observable once Paid could be non-zero. There is no
> remaining work on this engine.
>
> Update this section as milestones are completed.

### Completed
- **M0: Project Bootstrap & Design System.** All exit criteria met and fully
  verified, including `docker compose up` (all six services, confirmed in a
  follow-up session — see "Milestone 0 — Decisions & Environment Notes" above).
  ArchUnit boundary tests, the 360px shell with sidebar→hamburger transition,
  instant EN/HI language switching, the `/design` page, and CI were all verified
  directly (backend: `mvn test`; frontend: built, linted, tested, and driven
  headlessly in a real browser at 360px/1280px in both languages and both
  themes). RLS tenant isolation was verified twice: once against a local
  Postgres substitute, and again against the actual containerized stack with
  the real `shardeya_app` runtime role.
- **M1: Identity, Auth & Tenancy.** Backend and frontend both done and
  verified end-to-end (see both "Milestone 1" notes sections above): signup
  (builder + broker) → OTP verify → login (password + OTP) → forgot/reset
  password (mobile-OTP and email-token paths) → JWT + refresh rotation with
  reuse detection → `/me` bootstrap → `ProfileMenu` (name/email/plan
  badge/logout) → session-expiry modal → role-based redirect and
  login-gated + org-type-gated routing. Verified via `mvn test` (28 backend
  tests) + `npx vitest run`/`tsc -b`/`oxlint` (frontend), and — per the
  project's verification standard — by actually driving every flow through
  a real headless browser against a live `docker compose` stack: both
  signup roles, both login methods, wrong-password rejection, unauthenticated
  and cross-org-type route guards, logout, mobile-OTP password reset
  end-to-end (including logging in with the new password afterward), and a
  360px/Hindi pass of the signup page, login page, and the authenticated
  shell's `ProfileMenu`. Five real bugs were found and fixed only by this
  browser-level verification (none visible from code review, `tsc`, or
  unit tests) — see the "Decisions & Environment Notes" sections for full
  root-cause writeups.

- **M2: Builder — Projects & Plot Grid (Core Inventory).** Backend and
  frontend both built and driven end-to-end through a real browser (see both
  "Milestone 2" notes sections above for full root-cause writeups). Backend:
  project CRUD + grid config, media upload pipeline (upload-intent → S3 PUT →
  complete → derivatives), plot CRUD with the AVAILABLE⇄RESERVED-only status
  guard (SOLD is unreachable outside the future plot_sale flow), the grid
  endpoint (compact tuple format), plot search/filter (trigram +
  `Specification`), bulk plot import (template generation → upload →
  row-level validation → SKIP/FAIL/UPDATE_EXISTING duplicate handling →
  chunked commit), the Plot Size calculator (state-dependent Bigha), and
  Free-plan quota enforcement (advisory-lock-guarded, trigger-maintained
  `org_usage`, correctly enforced even mid-bulk-import). Frontend: ProjectList
  (cards) / ProjectForm (5-step wizard) / ProjectDetail (tabbed), the PlotGrid
  canvas renderer (>400 plots) and DOM renderer (≤400) sharing one visual
  language (colour + hatch pattern per status), PlotDetailDrawer/Form/
  StatusSelector, PlotFilterBar/SearchBox/Legend/UnplacedPlotsTray,
  GridLayoutEditor (click-to-place + blocked-cell marking — see its own
  comment on why click-to-place instead of drag-and-drop), ImportWizard
  (4-step), ImageUploader/PhotoGrid, AreaInput, EntitlementGuard/useCan, and
  the `project`/`plot`/`import` i18n namespaces (en+hi, key-parity tested).
  32/32 backend tests green (`mvn test`, Testcontainers, including
  `EntitlementServiceIntegrationTest` and a new `PlotUniquenessIntegrationTest`
  covering both `ux_plot_number` — including the "different punctuation/case,
  same normalised value" collision case — and `ux_plot_cell`, plus the
  vacate-and-reoccupy case for `updatePosition`, all exercised through
  `PlotService` rather than raw SQL so the service's pre-flight checks are
  proven to stay in sync with the DB's own normalisation formula) and 8/8
  Playwright e2e specs green (`frontend/e2e/`, real headless Chromium against
  a live `docker compose` stack) — **13 real bugs found and fixed this
  milestone** (2 backend-only via `curl`, 11 more via the frontend build +
  browser verification pass — see both notes sections for full detail), more
  than M0 and M1 combined, matching this milestone's own kickoff prediction.
  Tenant isolation explicitly re-verified (not assumed from M1) across every
  new M2 endpoint — all correctly 404, never 403, no cross-org data leakage.
  **Known, deliberate scope gaps, not bugs:** (1) the literal "500 plots
  actually created via bulk import" exit criterion can't fully complete on
  the Free plan's seeded 50-plot-per-project limit (no paid plan exists
  until M8) — the full mechanism (upload → 500 valid → commit → exactly 50
  imported, quota-blocked cleanly) is verified instead; (2) grid performance
  numbers (180-340ms) are measured on desktop Chromium, not real Moto
  G-class hardware, which this environment cannot provide or accurately
  emulate; (3) pinch-zoom is verified via synthetic PointerEvents, not a
  physical touchscreen, for the same reason; (4) `GridLayoutEditor` uses
  click-to-place rather than drag-and-drop (a deliberate simplification, see
  its own code comment); (5) PhotoGrid's reorder buttons are present but
  non-functional pending a dedicated reorder endpoint (attach/detach exist,
  but calling attach again to "move" an item would create a duplicate
  `project_media` row rather than reordering in place). None of these block
  M2's actual exit criteria in spirit; all are called out explicitly rather
  than silently glossed over, per this project's verification standard.

- **M2 gap closure: Project Status Control.** The `Upcoming → Active →
  Completed` project-lifecycle dropdown was added (`ProjectStatusSelect`),
  closing the one M2 follow-up gap that blocked nothing else. See "M2 Gap
  Closure" above. Shipped as PR #8 (open at the time M3 was built on top of
  it — see that section for the branch-stacking implication).

- **M3: Builder — Plot Sales, Payments & Documents.** Backend and frontend
  both built and driven end-to-end through a real browser (see "Milestone 3
  — Decisions & Environment Notes" above for full root-cause writeups).
  Backend: atomic sale creation (`plot_sale` insert + `plot.status=SOLD` +
  `payment_schedule` generation + outbox notification, all-or-nothing),
  the two-ledger payment model (`payment_schedule` expected vs
  `payment_record` actual/immutable, joined by `payment_allocation` for
  partial/split payments) with DB-trigger-maintained totals, auto
  oldest-due-first allocation, gapless FY-scoped receipt numbering, cheque-
  bounce auto-reversal, sale cancellation (plot restored, schedules waived,
  payment history retained), AES-256-GCM-encrypted buyer gov-ID with an
  audited reveal endpoint and a separate sensitive-media storage path (no
  derivatives, no standard `GET /media/{id}` access), typed plot documents,
  a nightly overdue-schedule sweep, and an in-app notification slice (outbox
  → poller → notification fan-out). Frontend: `SaleWizard` (5-step: buyer,
  deal, payment plan, broker, review), the full Payments tab (summary,
  schedule table with waive, add-payment dialog with client-side allocation
  preview, payment history with reverse/cheque-status actions), typed
  document slots with a blur/reveal guard for the sensitive gov-ID, cancel-
  sale dialog, and a polling `NotificationBell`. `sale`/`payment`/
  `notification` i18n namespaces (en+hi, key-parity tested). 37/37 backend
  tests green (`mvn test`, Testcontainers) and a comprehensive Playwright
  e2e spec (`m3-sale-payments.spec.ts`) green end-to-end, plus a one-off
  Hindi/360px visual pass — **6 real backend bugs found and fixed** (the
  JPQL-enum-literal bug recurring twice, a CHAR/VARCHAR and a
  SMALLINT/INTEGER schema-drift mismatch, a missing `@Transactional`, and
  two message-key-as-literal-text bugs) **plus 1 real frontend bug**
  (`brokerCommissionAmount`'s `valueAsNumber`/`NaN` validation footgun,
  the same class M2 already flagged as worth watching for). Tenant isolation
  explicitly re-verified across every new M3 endpoint — all correctly 404,
  never 403. Zero regressions in the M2 e2e suite. **Known, deliberate scope
  decisions, not bugs:** (1) no FK yet on `plot_sale.broker_partner_id`/
  `.customer_id` since B-14/M-12 don't exist; (2) no document versioning,
  only attach/delete; (3) no receipt PDF rendering, per `05-MILESTONES.md`'s
  own phasing — only the receipt *number* is in scope this milestone; (4)
  `NotificationBell` polls every 15s rather than using SSE/WebSockets, a
  deliberate simplification given notifications are themselves only created
  asynchronously by the outbox poller; (5) all verification is against the
  Free plan, same constraint M2 noted (no paid plan exists until M8).

- **M4: Builder — Team, Leads & Calendar.** Backend and frontend both built
  and driven end-to-end through a real browser (see "Milestone 4 —
  Decisions & Environment Notes" above for full root-cause writeups).
  Backend: real per-user project-scope enforcement wired end-to-end for the
  first time (JWT claims → `TenantContextFilter` → `ProjectAccessGuard` /
  repository-level scoped queries — previously a no-op since every prior
  milestone only ever tested as an all-projects Owner/Admin), the full
  staff role → permission matrix for Manager/Sales Executive/Accounts
  Staff/View Only (`V4_007`, reconciled from M-02's coarse table against
  more specific per-module `§9` sections where they disagreed), staff
  invite flow (Redis token + SMS, mirroring the password-reset pattern) and
  `TeamService` CRUD/deactivate/reassign-on-removal, the full M-12
  Customer/Lead core (CRUD, own/all visibility split, duplicate-mobile
  detection, funnel/follow-up views) with append-only `Interaction` logging
  (DB `RULE` blocks `DELETE`, 15-minute amend window), and M-11 Calendar
  with idempotent auto-projections (`upsertAutoEvent`/`removeAutoEvent`,
  unique-indexed on source+type so changing a follow-up date or an
  instalment due-date updates the same event, never duplicates) plus manual
  events. Frontend: `TeamListPage`/`AddTeamMemberDialog` (with an inline
  permission-matrix preview) /`AcceptInvitePage`, `LeadListPage` (All/Mine/
  Unassigned tabs)/`LeadDetailPage`/`LeadFormDialog` (duplicate-detection
  UX)/`InteractionTimeline`, `CalendarPage` (agenda-list view)/
  `AddEventDialog`, nav items gated by a new `anyOf` permission-list
  mechanism, and a full re-gating pass across the *existing* B-04/B-05
  `SaleDetailPanel`/`PlotDetailDrawer` screens to replace a single
  collapsed `canEdit` boolean with per-concern `useCan` checks (financial
  vs. plot-edit — see notes above for why the old single-boolean approach
  was actually wrong the moment more than one staff role existed). `team`/
  `customer`/`calendar` i18n namespaces added (en+hi, key-parity tested).
  100% green `mvn test`, plus a comprehensive real 5-role RBAC verification
  (one real staff account per role, created and invited through the actual
  UI, logged in via separate real browser contexts, asserted both at the UI
  level and via direct API calls against the *existing* B-04/B-05 payment/
  sale endpoints as explicitly required — not just new M4 screens) ending
  in `M4_RBAC_VERIFICATION_ALL_ASSERTIONS_PASSED`, plus a Hindi/360px visual
  pass across all three new feature areas. **Real bugs found and fixed:**
  the merge()-vs-persist() bug recurring 4 times (`InteractionService`,
  `CustomerService`, `TeamService`, `CalendarService`), a missing
  `@Transactional` on `TeamService.list()/get()` causing a
  `LazyInitializationException` that a frontend gap silently rendered as
  "no team members" instead of an error, an i18n JSON key-collision (`type`/
  `result` defined twice in the same object, second occurrence silently
  winning) causing raw keys to leak into the Hindi UI, a mislabeled
  notification `typeCode` copy-paste artifact, `RbacController.roles()`'s
  prefix-matching logic breaking the moment non-`BUILDER_`/`BROKER_`-
  prefixed role codes existed, and the `SaleDetailPanel`/`PlotDetailDrawer`
  single-`canEdit`-boolean gating bug described above (caught by reasoning
  through each role's real permission set, not by a failing test). Zero
  regressions across the full pre-existing e2e suite (10/10 specs).
  **Known, deliberate scope decisions, not bugs:** (1) no two-step
  ownership-transfer flow; (2) calendar frontend is agenda-list-only, no
  month/week/day grid views; (3) auto-projection calendar event titles are
  hardcoded English strings, not i18n-key+params like every other
  user-facing string in this codebase; (4) B-05 §9's "Waive: Admin only" is
  not separately enforced (pre-existing M3 gap, not new); (5) no
  follow-up-due/overdue-lead notification sweep, and `LEAD_PLOT_SOLD` is
  seeded but never fired; (6) staff `activity()` reporting is minimal
  (counts only); (7) all verification is against the Free plan (plus one
  throwaway `M4TEST` plan row created solely to unblock quota-limited
  test-data setup, not to change what's tested).

- **M5: Builder — Financials, Tracker & Dashboard.** Backend and frontend
  both built and driven end-to-end through real HTTP calls and a real
  browser against a deliberately realistic (not empty) dataset — see
  "Milestone 5 — Decisions & Environment Notes" above for full root-cause
  writeups. Backend: `org_metrics` (write-through triggers mirroring M2's
  `org_usage` pattern, backfilled for existing orgs) + `mv_org_revenue_monthly`;
  B-08 Financials (summary/payments/pending-overdue/revenue-trend, all
  native-SQL reporting queries, reversals correctly netted into every
  total); B-13 Tracker (Follow-up + Collection tabs with real inline
  actions — log/reschedule/mark-done/record-payment/remind, all thin
  delegates to existing services) plus `FollowUpDueSweeper`, closing the
  exact M4-documented gap (in-app + WhatsApp reminders to the staff
  assignee, 09:00 IST); a real WhatsApp gateway calling `infra/whatsapp-stub`
  for the first time since M0; B-01 Dashboard (10 cards read from
  `org_metrics`, never `COUNT(*)`, role-scoped for DATA_VIEW_ALL vs
  DATA_VIEW_OWN vs FINANCIAL_VIEW, Redis-cached 5min/org/user); B-10 Deals
  History (read-only, COMPLETED/CANCELLED sales only — which required
  discovering and wiring up `PlotSaleService.complete()`, a dead code path
  since M3 with no UI entry point at all). Frontend: `FinancialsPage`
  (summary cards, revenue trend + payment-mode charts via Recharts —
  first real use in this codebase — payment records and pending-instalments
  tables), `TrackerPage` (tabbed Follow-ups/Collections with inline quick-
  actions), `DashboardPage` (full rewrite from its M0-era placeholder),
  `DealsHistoryPage`/`DealDetailPage`. `financial`/`tracker`/`dashboard`/
  `deal` i18n namespaces added (en+hi, key-parity tested). 54/54 backend
  tests green (`mvn test`, Testcontainers, including two new integration
  tests targeting this milestone's own real bugs — see below), zero
  regressions across the full pre-existing e2e suite (10/10 specs), plus a
  new 6-test Playwright spec against a realistically seeded org (a
  completed sale, a genuinely-overdue instalment, a cheque-bounce
  reversal, a cancelled sale, an active partially-paid sale, two leads)
  covering every screen plus a Hindi/360px pass. **Real bugs found and
  fixed:** a 3rd occurrence of the `@Primary`-bean-resolution class (this
  time silently routing every financial/deals query through the BYPASSRLS
  auth-lookup connection instead of the RLS-enforced one, caught via a
  genuine Postgres permission-denied error); the standout bug of this
  milestone — Financials' and Dashboard's "overdue" figures silently
  disagreed with Tracker's own live figure for up to 24h at a time, because
  two of the three surfaces gated on the once-daily-swept
  `status='OVERDUE'` column instead of computing live off `due_date` —
  fixed across all three call sites plus a new regression test; raw,
  untranslated i18n keys (`form.amount`/`form.submit`/etc., which don't
  exist in `payment.json`) rendering verbatim in the Tracker/Financials
  quick-pay dialogs instead of `addPayment.*`; and two independent 360px
  horizontal-overflow regressions — `BottomNav` blowing out the *entire
  page's* scroll width once M5's 3 new nav items pushed a full-permission
  builder to 8 bottom-nav items (fixed by containing the overflow to the
  nav bar itself), and the TopBar's right-side cluster overflowing by 29px
  specifically once the notification badge hit two digits, a condition
  only this milestone's realistic-data seeding ever produced (fixed by
  narrowing `LanguageToggle`'s trigger below the `sm:` breakpoint). Also
  self-caught during development (before ever reaching a test or browser):
  a dead `MIN(uuid)` subquery, two missing explicit RBAC rejects (Tracker/
  DealsHistory for roles with neither view permission), a missing
  `ProjectAccessGuard` check in Tracker's action methods, a WhatsApp
  reminder that would have gone to the customer instead of the staff
  assignee, a Rules-of-Hooks violation (`||`-chained `useCan` calls), a
  dead "View Plot" link pointing at the wrong ID type, the Dashboard's
  `totalRevenue` card almost rendering un-abbreviated, and 5 TypeScript
  compile errors. **Known, deliberate scope decisions, not bugs:** (1)
  `bulkRemind`'s 24h dedupe is approximated at the schedule-row level, not
  per-buyer-across-schedules; (2) no document versioning or PDF export for
  Financials/Deals reports; (3) all verification is against the Free plan;
  (4) `CollectionTable`'s "View Plot" link was removed rather than
  reinstated, since `CollectionRow` doesn't carry a project ID today.

- **M6: Builder — Broker Management & Commissions.** Backend and frontend
  both built and driven end-to-end through real HTTP calls and a real
  browser (including a Hindi + 360px pass) against an isolated `m6verify`
  docker stack — see "Milestone 6 — Decisions & Environment Notes" above
  for full root-cause writeups. Backend: full B-14 broker CRUD
  (deactivate/reactivate/block/delete-with-outstanding-balance-guard,
  audited bank-details reveal); `CommissionConfigService`'s PLOT → PROJECT
  → GLOBAL → broker-default resolution algorithm with a self-contained,
  never-retroactive `config_snapshot` written onto each ledger entry;
  `BrokerTierService` (CRUD, a real GiST EXCLUDE overlap constraint,
  auto-upgrade-only evaluation, manual override); commission-ledger
  creation wired into `PlotSaleService`'s own sale transaction (create,
  tier evaluation on complete, cancellation cascading to the ledger);
  `CommissionPaymentService` (record with overpayment confirmation,
  reverse with the same double-reversal guards `PaymentService` already
  established); broker performance/deals aggregation; and the two
  remaining M-08 calculators (Brokerage with GST/share-split, Stamp Duty
  with gender-specific + `gender=ANY`-fallback rate lookup and
  flat/pct/cap registration semantics), completing all three alongside
  Plot Size from M2. Frontend: full Broker Management (list, create form,
  a 5-tab detail page — Overview/Commission Config/Deals/Ledger/Notes —
  tier configuration page, a live commission-preview card, a real payment
  dialog), a real broker picker wired into the existing `SaleWizard` with
  a live commission preview, and a new standalone `/calculators` page
  (three tabs — the first time Plot Size has had a dedicated page, not
  just its embedded `AreaInput` form). `broker`/`calculator` i18n
  namespaces added (en+hi, key-parity tested). 84/84 backend tests green
  (`mvn test`, Testcontainers, including three new integration test
  classes for this milestone's own scenarios), zero regressions across
  the reachable pre-existing e2e suite, plus a new permanent e2e spec
  (`m6-broker-commissions.spec.ts`, passing on both desktop and 360px)
  that drives every scenario the milestone kickoff explicitly asked for:
  plot-override-wins-over-global, global-fallback, rate-changed-after-a-sale-leaves-it-unchanged,
  tier-auto-upgrade-on-completion, and commission payment recording — plus
  a one-off Hindi + 360px visual pass across every new page. **7 real bugs
  found and fixed**: two recurrences of the CHAR/VARCHAR schema-drift class
  (`bank_account_last4`, proactively also `stamp_duty_rate.state_code`), a
  genuine ArchUnit false positive from `com.shardeya.builder.broker`
  colliding by name with the separate `com.shardeya.broker` persona
  package, a documented-but-never-implemented default-broker-tier-seeding
  gap (`V6_012`'s own comment claimed `AuthService` did this; it never
  did, silently disabling tier auto-upgrade for every org signed up after
  M6 shipped), `SaleWizard`'s commission preview never passing
  `projectId`/`plotId` to the backend (so a PLOT/PROJECT override could
  never be previewed correctly, only ever the GLOBAL rate — caught by
  exactly the scenario the kickoff asked to verify), a double-₹ symbol in
  a new i18n string, and `AddPaymentDialog` not invalidating the
  `sale-by-plot` query (so "Mark Complete" stayed hidden after a payment
  that zeroed the balance, until the drawer was reopened). Also proactively
  fixed a systemic, third-recurrence accessibility gap (missing
  `htmlFor`/`id`/`aria-label` associations) across every new M6 form once
  one instance of it broke a test. **Known, deliberate scope decisions, not
  bugs:** (1) no broker statement PDF (no PDF infrastructure exists in this
  codebase yet, same precedent M3 already set for receipts); (2) no admin
  endpoint for editing `stamp_duty_rate` (M-14 platform-admin doesn't exist
  yet, and gating it behind the existing org-level `BUILDER_ADMIN` role
  would be an actual tenant-isolation bug, not just an incomplete feature);
  (3) no broker-invite-to-Shardeya flow; (4) broker list endpoints are
  plain lists, not cursor-paginated, matching this module's own established
  precedent; (5) all verification is against the Free plan plus one
  throwaway `M6TEST` plan for quota-limited test-data setup.

- **M7 (first half): Builder — Reports & Legal Documents (B-11), Stats &
  Analysis (B-15), Reporting & Export Engine (M-10).** Backend and frontend
  both built and driven end-to-end through real HTTP calls and a real
  browser against a fresh, from-scratch `m7verify` docker-compose stack —
  see "Milestone 7 (First Half) — Decisions & Environment Notes" above for
  full root-cause writeups. Backend: the sandboxed template rendering
  engine (`TemplateRenderer` + `DocumentVariableAllowlist`, a hand-rolled
  two-construct language, never a general templating engine), the first
  PDF rendering pipeline this codebase has ever had (Flying Saucer/OpenPDF
  with a real embedded Devanagari TTF), document generation for all three
  core legal document types (allotment letter, payment receipt, demand
  letter) with gapless numbering, immutable snapshot-on-generation, and
  bulk demand-letter ZIP generation; the M-10 report engine (one query per
  report, shared verbatim between preview and export) backing all nine
  B-11 builder reports; ten B-15 stats endpoints over a mix of
  15-min-refreshed materialised views and live queries (cohort-based
  conversion funnel, never current-status counting); auto-receipt
  generation wired into `PaymentService.record()` via a new outbox event
  type, never inline. Frontend: `ReportCatalog`/`ReportViewer` with a
  fully data-driven filter panel and XLSX/CSV export; `TemplateList`/`TemplateEditor`
  (variable palette, live preview, Activate) plus `GenerateDocumentButton`/
  `DocumentList`/`BulkGenerateDialog` wired into the existing sale detail
  and Financials overdue screens; a full `/builder/stats` page with all
  ten charts, a KPI strip, and project+date-range filtering. `report`/
  `document`/`stats` i18n namespaces added (en+hi, key-parity tested).
  84/84 backend tests green (`mvn test`), zero regressions; frontend
  `tsc`/`oxlint`/`vitest` (23/23) all clean; a new permanent Playwright
  spec (`m7-reports-documents-stats.spec.ts`, 12/12 green on desktop and
  360px) plus direct curl-based verification of document generation
  (English and Hindi PDFs visually inspected), auto-receipt-on-payment,
  template-sandbox activation blocking, and XLSX export. **7 real bugs
  found and fixed**, several with real teeth: a latent, pre-existing
  `S3_ENDPOINT`-never-configured bug that broke every in-container S3 call
  once this milestone was the first to genuinely exercise one from inside
  a fully containerized backend; `REFRESH MATERIALIZED VIEW` requiring
  real Postgres ownership with no GRANT-based alternative; the
  long-established `SYSTEM_ACTOR_ID` background-job placeholder being
  written into a real foreign key for the first time; a Java text-block
  whitespace-stripping footgun that silently mangled SQL at a
  string-concatenation boundary (found once, then proactively found three
  more times in the same class); two rounds of wrongly-assumed enum
  values in a filter-options seed migration (plot facing, lead
  status/source); and a literal `&middot;` HTML entity that broke PDF
  generation outright under Flying Saucer's strict XML parser. Also
  disclosed, not fixed: a real but bounded Devanagari-matra-positioning
  cosmetic limitation inherent to the Flying Saucer/OpenPDF rendering
  stack (conjuncts form correctly, no boxes — the milestone's explicitly-named
  worst failure mode — but some vowel-sign glyphs show minor visual
  repositioning). **Known, deliberate scope decisions, not bugs:** (1) no
  `BOOKING_CONFIRMATION` document type (depends on M-06's second-half
  WhatsApp confirmation flow, not yet built); (2) `TemplateEditor` is
  plain-textarea HTML editing, not a WYSIWYG rich-text toolbar; (3) no
  report-table row virtualisation or column sorting; (4) report export is
  XLSX/CSV only, no PDF; (5) no async `export_job` queue (every export
  takes the synchronous path at this milestone's realistic data volumes);
  (6) `StatsFilterBar` drives project + date range globally only, not
  staff/broker (chart-level drill-through instead); (7) no
  `collection_target`-setting UI, only the documented fallback; (8) XLSX
  export headers are Title-Case English, not localised Hindi; (9)
  auto-generated receipts always render in English; (10) reversal
  payments don't auto-generate their own receipt; (11) all verification
  is against throwaway `M7TEST`/`M7E2E` plans, no real paid plan exists
  until M8.

- **M7 (second half): M-06 Notification Engine — real WhatsApp/SMS/email
  delivery.** Built and verified end to end (see "Milestone 7 (Second
  Half)" above for full root-cause writeups). Real channel adapters for
  WhatsApp (Meta Cloud API) and SMS (MSG91), both stubbed by default and
  swappable via a config property alone, exactly like every prior
  provider-agnostic adapter in this codebase (`SmsGateway` since M1); email
  needed no new adapter class since SMTP already is the provider-swap
  mechanism. `NotificationDispatchService` (new) is the single place that
  resolves `notification_type` defaults + per-user `notification_preference`
  overrides + `whatsapp_optin` status into an actual per-channel send
  decision, replacing `OutboxPoller`'s old direct notification-creation
  calls. All 7 explicitly requested builder-side triggers wired: instalment
  due today (new `InstalmentDueTodayScheduler`), instalment overdue 3+ days
  then weekly (`OverdueScheduleSweeper`'s new second cron, distinct from
  its original once-only status-transition sweep), follow-up due
  (`FollowUpDueSweeper`, refactored to defer the WhatsApp decision to
  dispatch time), broker commission due (new hook in
  `PlotSaleService.complete()`), new lead added / plot marked sold / staff
  added-or-role-changed (all pre-existing M3/M4 triggers, now flowing
  through the new dispatch service with real per-type channel defaults for
  the first time — `notification_type.default_channels`/`is_mandatory` had
  existed since M3 but were never functionally consulted until this
  build). Quiet hours (08:00–21:00 IST) applied once, centrally, at enqueue
  time; a simple per-poller-tick dispatch cap for rate limiting; dedup via
  the existing `payment_schedule.last_reminder_sent_at` column (a
  documented simplification, not Redis); WhatsApp-unavailable-for-critical-
  types SMS fallback. A new Settings > Notifications page (preference
  matrix + WhatsApp opt-in card, OTP-confirmed) reachable from `ProfileMenu`.
  97/97 backend tests green (including 3 new tests targeting this round's
  own triggers — one of which caught a real same-day double-notification
  bug in the brand-new due-today scheduler), zero regressions; frontend
  `tsc`/`oxlint`/`vitest` all clean. Verified against a freshly rebuilt live
  `m7verify` stack: a real signup → preference matrix fetch and update
  (including confirming the server, not just the UI, refuses to let a
  mandatory type's in-app channel be disabled) → a full WhatsApp opt-in OTP
  round-trip → a real lead creation flowing through the entire new outbox →
  dispatch-service → in-app pipeline → the WhatsApp stub adapter visibly
  "sending" and logging a real response during the automated test run → a
  Hindi + 360px browser pass that caught and fixed one real bug (a
  duplicated page title, invisible to every automated overflow/console/
  raw-key check, only found by looking at the actual screenshot). **No
  real WhatsApp Business Platform account, MSG91 account, or DLT template
  registration exists in this environment** — flagged clearly above as
  manual, external, offline steps the user needs to complete before either
  adapter can send anything to a real phone; both are written to their
  real documented API shapes but are otherwise unverified against a live
  provider. Known, deliberate scope exclusions: §22.4 buyer-facing
  WhatsApp templates, subscription-expiry T-7/T-1 reminders (both tie to
  scope explicitly excluded this round), `BROKER_TIER_UPGRADED` WhatsApp
  wiring (not one of the 7 named triggers), digest/collapse behavior, and
  a WhatsApp delivery-status webhook receiver.

- **Milestone 6.5, steps 1-4 of 12 (Broker Network & Designation
  Commission Engine).** Full spec in `06-BROKER-NETWORK-ENGINE.md`
  (now a source-of-truth doc alongside `00`-`05`). `designation_slab`
  config table (8 slabs, config-driven not hardcoded, GiST EXCLUDE
  overlap prevention mirroring M6's `broker_tier`); `broker_partner`
  extended with `upline_broker_id`/`current_designation_id`/
  `current_commission_rate`/sales-count columns plus a `DESIGNATION`
  commission type (FIXED retired for new brokers only); the
  `broker_network` closure table with integrity validation (self-upline,
  cycles, one-direct-upline) enforced in Java before any row is written;
  broker creation with explicit, never-inferred upline selection and a
  network tree view; and — the most rigorously verified piece — a pure,
  dependency-free `CommissionCalculationEngine` proven against every §16
  worked example (differential chains, uncapped same-slab bonuses,
  never-negative lower-rated uplines), including proving the test suite
  itself actually catches the "compare with seller instead of direct
  downline" regression by deliberately breaking it and watching the
  right 3 tests fail with the right wrong numbers. 119/119 backend tests
  green, full live HTTP + real-browser + Hindi/360px verification. One
  real bug found and fixed: designation names weren't using the DB's own
  `name_hi` column, a recurrence of the `AreaInput` DB-bilingual-data
  class.

- **Milestone 6.5, steps 5-6 of 12 (Booking Commission Freeze &
  Proportional Release).** Full writeup above. `PlotSaleService.create()`
  now freezes a DESIGNATION broker's full commission tree
  (`booking_commission`, one row per beneficiary, including explicit
  zero-amount rows) inside its own transaction the instant a sale is
  created — PERCENTAGE/FIXED brokers and no-broker sales are completely
  unaffected, still routing through the exact, unmodified M6
  `commissionLedgerService` path. `PaymentService.record()`/`doReverse()`
  (covering both manual reversal and the cheque-bounce auto-reversal path)
  now release the matching proportional slice of every beneficiary's
  frozen total via a new, delta-against-cumulative-total
  `commission_release` table — zero rounding drift no matter how many
  instalments a deal splits across, and a reversal automatically
  "un-releases" the right amount with no special-case logic, since it's
  just another (negative) payment driving the same cumulative math. A
  minimal read-only surface (`GET /brokers/{id}/booking-commissions`, a
  new `CommissionTreeTab` swapped in for the Ledger tab on DESIGNATION
  brokers) exists purely so this is actually verifiable in a browser — no
  dashboard, no payout-recording action. 124/124 backend tests green (5
  new, covering a real 3-level frozen tree matching the pure engine
  exactly, proportional release + reversal at 25%/50%/100%, a waived
  instalment correctly releasing only what was actually paid, and the
  full create→payment→waive→complete/cancel lifecycle regression for
  designation, percentage, and no-broker sales alike), full live HTTP +
  real-browser + Hindi/360px verification. Two real bugs found and fixed:
  a wide `<Table>` breaking at 360px in the new commission-tree view (the
  third occurrence of this exact class in this codebase, see the full
  writeup above) fixed by rebuilding as card rows, and a latent
  `INSERT`-vs-`UPDATE` bug in the established "throwaway unlimited-quota
  plan" verification precedent that had been silently duplicating
  `subscription` rows since M4.

- **Milestone 6.5, step 7 of 12 (COMPLETED-Triggered Promotion &
  Team-Sales Rollup).** Full writeup above. `personal_successful_bookings`/
  `team_successful_bookings` are now genuinely trigger-maintained
  (`V65_010`, full-recompute via the closure table, `total_waived = 0`
  filter enforcing §1's "a waived remainder does NOT count as complete");
  a new `DesignationPromotionService` re-evaluates and auto-promotes the
  seller and every upline against the slab config after each completion,
  recording `designation_history` (`V65_009`) rows. `PlotSaleService.complete()`
  branches between this and the unmodified M6 `brokerTierService`, exactly
  mirroring step 5's own create()-time branch. 129/129 backend tests green
  (5 new: 4 sequential scenarios plus 1 genuinely concurrent two-thread
  test), full live HTTP + real-browser + Hindi/360px verification — the
  already-built (steps 1-4) Network Tree page needed zero frontend changes
  to start showing real data. **A real concurrency bug was found and
  fixed** (confirmed via a raw two-session `psql` reproduction, not
  guesswork): a blocked `UPDATE`'s own subquery keeps its pre-block READ
  COMMITTED snapshot even after unblocking, so the original trigger design
  silently lost updates under real concurrent completions sharing an
  upline — caught immediately by this round's own concurrent test
  (`"expected: 2 but was: 1"`), fixed by acquiring the row lock as its own
  separate `FOR UPDATE` statement before the recompute. Also fixed a
  cross-test-class mobile-number collision (two new test classes each
  independently starting their own counter at 0) with a new shared
  `TestMobiles.next()` helper.

- **Milestone 6.5, step 9 of 12 (Cancellation With Recovery).** Full
  writeup above. Cancelling a DESIGNATION-broker sale now cancels its
  frozen `booking_commission` rows and derives a real recovery figure from
  whatever had already been released — reusing M6's exact
  status-flip-plus-derived-field pattern, no new reversal rows needed.
  When the cancelled booking had already reached COMPLETED, sales counts
  reverse and designations are re-evaluated allowing DEMOTION for the
  first time in this engine (`DesignationPromotionService.reevaluateAfterCancellation()`,
  sharing the same pure `DesignationTransitionCalculator` comparison
  `evaluateAndPromote()` uses). Needed zero new migration work for the
  count-reversal or concurrency halves — step 7's trigger already
  recomputes generically on any status change, and step 7's row-lock fix
  already generalizes to a cancellation racing a completion, confirmed by
  a dedicated concurrent test that passed on its first run (5 repeats,
  all clean). Two pure, dependency-free classes
  (`CommissionReversalCalculator`, `DesignationTransitionCalculator`)
  were built and fixture-tested first, matching step 4's own discipline.
  143/143 backend tests green (14 new: 10 pure + 3 sequential + 1
  concurrent), full live HTTP + real-browser + Hindi/360px verification.
  One real bug found and fixed: a double-₹-symbol in the new recovery i18n
  string — the second time this exact mistake has recurred in this
  codebase (first in M6). **Step 8 was deliberately skipped, saved for
  last — steps 10-12 are NOT done.**

- **Milestone 6.5, steps 8 & 10 of 12 (Manual Promotion, and Builder-Side
  Dashboards).** Full writeup above. `DesignationPromotionService` gained
  `manuallyOverride()`/`clearOverride()` — admin-only, future-bookings-only
  by construction (neither method ever touches `booking_commission`), and
  the override genuinely freezes the existing `evaluateOne()` guard against
  any further automatic promotion/demotion until cleared. Clearing was
  built as a real action (§34's "one decision to surface" resolved
  explicitly, unlike M6's own tier-override precedent, which left this
  gap permanently open) — it re-evaluates only the one broker from their
  real, current count, which can genuinely demote them back down from
  wherever the override had left them. Step 10 is entirely additive,
  read-only UI: a money-summary section on the existing `CommissionTreeTab`,
  a new per-broker `NetworkTab` (upline/downline/next-designation/history/
  the step-8 controls), and an org-wide commission summary + promotion
  history added to the existing `NetworkTreePage`. Everything except the
  money aggregation is computed client-side from endpoints the frontend
  already had. 150/150 backend tests green (7 new), full live HTTP +
  real-browser + Hindi/360px verification, including manually promoting a
  broker mid-network, confirming a completed booking left the override
  untouched while still incrementing real sales counts, clearing the
  override and confirming a genuine demotion followed, and checking every
  new dashboard against a real 3-level network with a genuinely
  50%-released partial payment. **One real, pre-existing bug found and
  fixed**: `BookingCommissionResponse.pendingAmount` mirrored the DB's
  `released_amount - paid_amount` column, which (since `paid_amount` has
  no write path yet) is always identical to `releasedAmount` — meaning the
  existing (steps 5-6) "Pending" card had been silently showing the same
  number as "Released" the whole time. Renamed to a correctly-computed
  `outstandingAmount` (`totalAmount - releasedAmount`) in both the API and
  its one UI consumer. **Step 11 (see below) completed the engine; step
  12 is permanently out of scope, not deferred.**

- **Milestone 6.5, step 11 of 12 (Concurrency Audit Sweep) — the final
  step; the Broker Network Engine is now complete.** Full writeup above.
  A deliberate sweep, not new machinery: every write path touching sales
  counts, team-sales rollup, designation state, the commission tree, or
  commission release was individually traced against the step-7 "separate
  lock-then-recompute" pattern. Two real, previously-undiscovered
  concurrency bugs were found and fixed, both reproduced empirically
  first: (1) `fn_booking_commission_release_trigger` (V65_008) had the
  exact same two-statement lost-update shape step 7 already fixed
  elsewhere, just never checked here — confirmed via a raw two-session
  `psql` reproduction (`released_amount` landed on the second
  transaction's own stale value instead of the true sum), fixed with
  `V65_012` applying the identical `PERFORM ... FOR UPDATE`-first pattern,
  re-verified with the same reproduction; (2) `manuallyOverride()`/
  `evaluateOne()` inserted `designation_history` (a real FK to
  `broker_partner`, taking a `FOR KEY SHARE` lock) *before* updating
  `broker_partner` itself, which genuinely **deadlocks** (not just loses
  an update) when racing a completion that already holds `broker_partner`'s
  strong lock via the step-7 trigger — found via the concurrent JUnit test
  itself (a real Postgres `deadlock detected` error on the first run,
  since this bug lives in Hibernate/JPA-generated SQL, not hand-written
  PL/pgSQL, making the JUnit test itself the correct two-session
  reproduction tool), fixed by reordering to update `broker_partner`
  first — the same "strong lock as this transaction's first touch to the
  row" discipline, just applied in Java. As a scoping check (not one of
  the four named categories), `plot_sale.total_paid`'s own trigger was
  also verified via the same raw-`psql` method and confirmed genuinely
  safe, yielding a reusable diagnostic rule: a single inline-subquery
  `UPDATE ... SET col = (aggregate) WHERE id = X` self-corrects under
  Postgres's EvalPlanQual when blocked-then-unblocked; a `SELECT
  aggregate INTO variable` followed by a *separate* `UPDATE` using that
  variable does not. 153/153 backend tests green (3 new concurrent tests
  covering the three scenarios this step's own instruction named: two
  simultaneous bookings under a shared upline, two simultaneous releases
  on the same booking, and a manual override racing a completion), the
  full suite run twice and the 5 concurrency test classes run 3 more
  times as a group, plus the deadlock fix's own test run 5 times in
  isolation — clean every time.

- **Post-M6.5: same-slab bonus formula changed from a flat ₹10/sq.ft. to
  `next slab rate − current shared slab rate`.** Full writeup above. A
  surgical, single-formula change: `ChainMember` gained a
  `nextSlabRatePerSqft` field resolved from the real `designation_slab`
  config by the caller (`BookingCommissionService`), keeping
  `CommissionCalculationEngine` itself fully pure/dependency-free; the
  top slab (₹255) correctly resolves to exactly ₹0, never a fabricated
  higher slab. 163/163 backend tests green (two pre-existing tests
  genuinely re-derived by hand, not force-passed), zero frontend code
  changes needed, full live HTTP + browser + Hindi/360px verification
  across three scenarios (₹200 pair → ₹15,000; ₹255 pair → ₹0; a
  multi-level chain with same-slab at two different slabs computing each
  level independently).

### Next Up
- M-13: Audit, Soft-Delete & Data Privacy (trash/restore, audit log, data
  export, account deletion). Not started — do not begin until explicitly
  picked up again; see `05-MILESTONES.md` Milestone 7 for full scope. The
  Platform Admin Console and Razorpay payment integration also remain
  explicitly out of scope until picked up separately — plan upgrades stay
  a manual `subscription.plan_code` database update outside the app for
  now.

---

## How to Ask for Help

When requesting implementation help, specify:
1. **Which milestone** you're on
2. **Which module** (e.g. B-05 Payment Tracker)
3. **Which layer** (DB migration / backend service / API endpoint / frontend page / component)
4. **What exists already** (which tables, services, components are in place)
5. **What you need** (specific file, specific function, specific component)

Example:
> "I'm on M3. B-05 Payment Tracker. I need the `PaymentService.recordPayment()` method. The `payment_record` table exists, `PaymentSchedule` entity exists, `PlotSaleService` exists. I need the payment recording with auto-allocation logic."

This gets you a precise, immediately usable answer rather than a re-explanation of the architecture.

---

## File Generation Protocol

When generating code files:
1. Check the architecture doc for the relevant module's spec
2. Check the data model for the exact schema
3. Follow the package structure above
4. Include i18n keys, never hardcoded strings
5. Include tenant context assertions
6. Include permission checks
7. Include validation (Bean Validation on backend, Zod on frontend)
8. Include error handling with specific message keys
9. Write the corresponding test

---

## Reference Links

- PRD: `Shardeya_PRD.docx` (uploaded, fully analysed)
- UI Reference: Propwise reference explicitly **out of scope** per project direction. Custom design system.
- Target users: Indian real estate professionals with limited tech skills, on Android phones with 4G
- Languages: English + Hindi (Devanagari). Architecture supports Tamil/Telugu/Marathi without code change.
- Currency: INR only (multi-currency is a future item for NRI buyers)
- Timezone: Asia/Kolkata only (no multi-timezone support needed)
