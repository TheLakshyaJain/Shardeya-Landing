# Recurring Bug Patterns

A compact, deduplicated index of bug classes that have already shown up **more
than once** in this codebase, extracted from the full root-cause writeups in
`CLAUDE.md`. Most "new" bugs reported by a customer turn out to be one of
these recurring in a file that hasn't hit it yet — check this list first,
before assuming a fresh investigation is needed from zero.

Each entry is a fast "does this look familiar?" check, not the full story.
If one matches, search `CLAUDE.md` for the bolded search term to read the
original root-cause writeup (exact file/line, the fix, and the regression
test) before reusing the fix — reasoning by analogy from a one-line summary
is exactly how new variants of these bugs get introduced.

**When you fix a NEW instance of a pattern already listed here**, don't just
fix it — bump the recurrence count in this file and add the new file/module
to the "seen in" list. That's what makes this list keep paying for itself.

---

## Backend — JPA / Hibernate

### 1. `merge()` instead of `persist()` on manually-assigned `@Id` entities
A JPA `@Id` assigned in Java (`UUID.randomUUID()`) with no `@GeneratedValue`
makes Spring Data's default `isNew()` check always return `false`, so
`repository.save()` routes through `entityManager.merge()` — which returns a
**different, new managed instance** and leaves the object you passed in
detached forever. Silent as long as nothing downstream needs the original
reference to be managed or to see DB-generated column values.
**Fix:** always `entity = repository.save(entity);` and use the *returned*
reference. If the entity has a `@CreationTimestamp` or other DB-generated
column, that alone isn't enough — also `entityManager.flush();
entityManager.refresh(entity);` right after, or the generated columns stay
null in the response.
**Search:** `merge()`-vs-persist()
**Recurred:** 5+ times (Plot M2; InteractionService/CustomerService/
TeamService/CalendarService, all in one M4 sweep).

### 2. Hibernate can't see a DB-trigger-computed change it didn't itself make
Any Postgres trigger that recomputes a column (`total_paid`, `price_per_unit`,
`org_usage.current_value`, `payment_schedule.status`) is invisible to
Hibernate's persistence context if the *same* entity is already loaded in the
*same* transaction — a "fresh" re-query returns the cached, stale instance,
not the DB's real current value. This bites hardest when a re-query happens
inside the same transaction as the write that triggered the recompute (a
genuinely separate transaction/request always sees the fresh value fine,
which is why this is easy to miss in testing).
**Fix:** explicit `entityManager.flush()` then `entityManager.refresh(entity)`
before reading the column.
**Search:** "can't see a DB-trigger-computed change"
**Recurred:** 4+ times (`pricePerUnit`/`org_usage` M2, `EntitlementService.usage()`,
`ScheduleService.syncAllInstalmentProjections()` twice — once at
introduction, once as a second-order case Post-M7).

### 3. `entityManager.refresh()`/similar needs an active transaction
Calling `refresh()` (or anything else that needs a live persistence context)
from a method with no `@Transactional` throws `TransactionRequiredException`
even if the method is otherwise a pure read.
**Fix:** add `@Transactional(readOnly = true)`, even to read-only methods,
whenever they touch `EntityManager` directly.
**Search:** `TransactionRequiredException`
**Recurred:** `PaymentService.summary()` (M3), `PlotQuickCreateService.preview()`.

### 4. Inlined enum literal in a JPQL `@Query` string
Writing a Java enum constant directly in a `@Query` string (e.g.
`s.status <> com.shardeya...PlotSale$Status.CANCELLED`) makes Hibernate
render it as `'CANCELLED'::Status` — the enum's bare **Java simple name**,
not the real Postgres enum type (`plot_sale_status`) — failing at runtime
with `type "Status" does not exist`.
**Fix:** always bind enum values as a `@Param` (a `Collection<T>` for an
`IN` clause), never write the constant inline in the query string.
**Search:** "inline an enum constant"
**Recurred:** 4+ times across `PlotSaleRepository`, `PaymentScheduleRepository`,
`CustomerRepository` (twice, in the same sitting as the comment warning
about it was written).

### 5. `@Primary` bean wins ambiguous autowiring unconditionally
A bean marked `@Primary` wins type-based autowiring even against a
constructor/factory-method parameter whose **name** matches a different,
non-`@Primary` bean — Spring only falls back to by-name matching when no
candidate is `@Primary`. A type that's both auto-configured by Spring Boot
(`@ConditionalOnMissingBean`) *and* has an app-defined `@Primary` bean of the
same type is especially dangerous: the auto-configuration silently never
runs, and the only bean left in the context is the wrong one.
**Fix:** any bean-method parameter of a type with an `@Primary` candidate
elsewhere needs an explicit `@Qualifier("actualBeanName")`.
**Search:** `@Primary`
**Recurred:** 3 times (`AuthLookupRepository`'s DataSource/DataSourceProperties
twice in one bug, `NamedParameterJdbcTemplate` in `FinancialService`/
`DealsHistoryService` — the latter silently ran queries through a BYPASSRLS
connection instead of the RLS-enforced one).

### 6. Migration column type narrower than the entity's default mapping
A migration declaring `CHAR(n)`/`SMALLINT` for a field the entity maps as
plain `String`/`int` (which Hibernate expects as `VARCHAR`/`INTEGER`) fails
Spring Boot's startup schema validation — but this is actually the *cheap*
outcome, since it's caught for free by any test that boots the context.
**Fix:** default new migration columns to `VARCHAR`/`INTEGER` unless there's
a specific reason to narrow (and if narrowed, add an explicit
`@Column(columnDefinition=...)` on the entity to match).
**Search:** "CHAR/VARCHAR"
**Recurred:** 4 times (`V1_008`, `buyer_gov_id_last4`, `bank_account_last4`,
`stamp_duty_rate.state_code`).

---

## Backend — SQL / Postgres

### 7. Native SQL comparing a bound parameter against an enum-typed column
`pr.mode = :mode` fails with `operator does not exist: payment_mode =
character varying` — a bound JDBC parameter arrives typed `varchar`, and
Postgres has no implicit cast from `varchar` to an enum type for a bound
parameter (an inline string *literal* in the SQL text, like an `IN (...)`
list, can cast implicitly — a bound parameter can't).
**Fix:** `CAST(:param AS enum_type_name)` explicitly.
**Search:** "operator does not exist"
**Recurred:** `FinancialService.payments()` (mode filter),
`DealsHistoryService.list()` (status filter) — found together by
proactively checking every other native-SQL comparison once the first one
turned up.

### 8. Java text block + string concatenation across a line boundary
Text blocks strip trailing whitespace **per line, independently** — a
`"""...WHERE """ + var + """...""" ` splice loses the space after `WHERE`
if that line happens to be the shortest-indented one in its own segment,
silently gluing tokens together (`WHEREs.org_id`). Compiles fine, looks
correct in the source, fails at runtime with a SQL grammar error.
**Fix:** one text block with `%s` placeholders + `.formatted()`, never
`""" + var + """` spanning a line boundary.
**Search:** "strip trailing whitespace"
**Recurred:** found once in `StatsService.overview()`, then proactively
found 3 more times in the same class once the pattern was recognized.

### 9. Chunked/batched operation, exception mid-batch rolls back the WHOLE chunk
Letting a business-rule exception (e.g. a quota check) propagate out of a
`TransactionTemplate` callback rolls back everything in that transaction —
including rows processed earlier in the *same* chunk, not just the ones
after the failure point. "A failure partway through a chunk doesn't roll
back previous chunks" only protects *earlier* chunks, not earlier rows in
the *same* one.
**Fix:** catch the business exception inside the per-row loop and `break`
instead of letting it propagate, when partial progress is a legitimate,
expected outcome.
**Search:** "rolled back the entire chunk"
**Recurred:** `ImportService.commitChunk()` (quota exceeded mid-chunk).

### 10. A hand-rolled "is this fully settled" check drifts from the real balance
Re-deriving "is this paid off" as `totalPaid >= dealValue` (or similar)
instead of reading the single authoritative derived field (`balanceDue`)
silently breaks the moment a new concept changes what "settled" means (e.g.
waiving an instalment reduces `balanceDue` but not `totalPaid`).
**Fix:** always gate on the one authoritative derived figure, never
re-derive the same fact a second way from its components.
**Search:** "must read `balanceDue`, never re-derive"
**Recurred:** twice — `PlotSaleService.complete()`'s gate, and a UI balance
comparison against a rounded vs. raw figure (commission payment overpayment
guard).

---

## Backend — Tenant Isolation / RLS / Background Jobs

### 11. RLS + a background/scheduled job with no tenant context bound
`REFRESH MATERIALIZED VIEW`, a bulk cross-org sweep, or any job with no
single org's context to bind runs as the RLS-enforced app role and silently
matches **zero rows for every org, forever** — no error, no log line, looks
identical to "no data exists yet." This is more dangerous than an outright
error because nothing signals anything went wrong.
**Fix:** a dedicated BYPASSRLS role + datasource for that specific job only
(mirroring `AuthLookupDataSourceConfig`'s pattern), never reused elsewhere;
add explicit error logging to the scheduled tick itself (`@Scheduled`
swallows uncaught exceptions silently by default).
**Search:** "silently matched zero rows on every single refresh"
**Recurred:** the stats materialized-view refresh job (found via a direct
user report of "the whole Stats page looks buggy").

### 12. `REFRESH MATERIALIZED VIEW` requires real ownership, not GRANTs
Unlike ordinary tables, `REFRESH` has no GRANT-based escape hatch — the
connecting role must actually **own** the view (or be superuser).
**Fix:** `ALTER MATERIALIZED VIEW ... OWNER TO <the role that refreshes it>`
in a migration. Safe since materialized views can't have RLS anyway
(Postgres doesn't support it).
**Search:** "must be owner of materialized view"

### 13. A placeholder "system actor" ID persisted into a real foreign key
`SYSTEM_ACTOR_ID` (`new UUID(0,0)`) is fine for binding the `TenantContext`
RLS GUC in background jobs, but was never meant to be **written into a real
`REFERENCES app_user(id)` column** — doing so throws a genuine
`ConstraintViolationException`, and because it happens inside outbox retry
logic, it fails 5 times then silently gives up.
**Fix:** look up a real user explicitly (org owner, or whoever triggered the
underlying event) for any column with a real FK, never assume a bound
`TenantContext.userId()` is safe to persist.
**Search:** `SYSTEM_ACTOR_ID`

---

## Backend — Security / Auth Filters

### 14. Auth filter's public-path allowlist doesn't cover every path a framework actually uses
An allowlisted path prefix a config property *names* isn't necessarily
every path that config actually makes public. `TenantContextFilter`
allowlisted `springdoc.swagger-ui.path` (`/api/v1/docs`) since that's the
configured entrypoint — but that only covers the *initial redirect*;
springdoc always serves the real UI assets (the page, its JS/CSS) from the
fixed `/api/v1/swagger-ui/**` path regardless of that config, so the
redirect target itself 401'd before the page could render. The raw
OpenAPI JSON spec was fine the whole time (a real, separately-covered
prefix), which is what made this easy to miss — "the docs work" was true
for the machine-readable spec and false for the actual browsable page.
**Fix:** don't assume a framework's "configure this path" property is the
only path involved — check what it actually redirects to / serves from,
and allowlist every real path, not just the one named in config.
**Search:** "only covers springdoc's initial redirect entrypoint"
**Recurred:** this is the same underlying class as M1's CORS/OPTIONS
finding (a custom filter's hardcoded rule not accounting for every real
request shape a framework generates) — different filter, different
framework feature, same root shape: a security filter's exemption logic
was written for the *obvious* case and missed a real one right next to it.

---

## Frontend — React / Forms

### 15. shadcn primitive missing `React.forwardRef`
Harmless for `asChild`-composed Dialog/Sheet triggers (a console warning
only) — but a **real, silent functional bug** for: (a) anything
react-hook-form's `register()` binds to, since RHF reads the DOM value via
the dropped ref, causing every field to submit as `undefined`; (b) anything
measured by Radix Popper positioning (a `DropdownMenuTrigger`, not a
Dialog/Sheet trigger), which renders fully off-screen with no visible error.
**Fix:** wrap the primitive in `forwardRef`. Check any new shadcn primitive
used with `register()` or as a *measuring* trigger before assuming a
forwardRef warning is the harmless case.
**Search:** "cannot be given refs"
**Recurred:** `Input`, `Button` (M1), `Textarea` (M2).

### 16. `valueAsNumber: true` on an optional numeric RHF field
An empty input under `valueAsNumber` becomes `NaN`, not `undefined` —
`z.number().optional()` only accepts `undefined`, so validation silently
fails and `handleSubmit`'s callback never fires at all (zero visible error
anywhere; the submit button just appears to do nothing).
**Fix:** `setValueAs: (v) => (v === '' ? undefined : Number(v))` instead of
bare `valueAsNumber: true`, for every optional numeric field.
**Search:** `valueAsNumber`

### 17. Conditional/short-circuited hook calls
`useCan(a) || useCan(b)` skips the second hook call whenever the first
returns `true`, violating the Rules of Hooks (inconsistent hook count
across renders).
**Fix:** call both hooks unconditionally as separate `const`s, combine the
booleans afterward.
**Search:** "Rules of Hooks"

### 18. Two JSX elements at the same tree position, ternary-swapped attribute
A "Next" (`type="button"`) and "Submit" (`type="submit"`) button at the same
JSX slot, distinguished only by a step-state ternary, can share the same DOM
node across a re-render — if the ternary flips as a *synchronous side
effect of the same click's own handler*, the browser's native click
activation can fire against the new `type="submit"` before the click
finishes processing, submitting the form on what was meant to be a "next
step" click.
**Fix:** give the two elements distinct `key`s so React mounts a fresh node
instead of mutating in place.
**Search:** "activation behaviour"

### 19. Shared component fed by more than one differently-keyed query
The same table/dialog component rendered twice on one page (e.g. "Pending"
and "Overdue" sections both using `PendingInstalmentsTable`, fed by
different query keys) — a mutation's `onSuccess` invalidating only the query
key the developer had in mind leaves the *other* instance stale until an
unrelated navigation forces a remount.
**Fix:** invalidate every query key the component could plausibly be
fed by, unconditionally — the component has no way to know which instance
it is.
**Search:** "shared component rendered against multiple differently-keyed queries"
**Recurred:** `AddPaymentDialog` → `sale-by-plot`, `PendingInstalmentsTable`
→ `financial-pending`/`financial-overdue`.

### 20. Missing `hasMore`/`nextCursor` consumption on a `CursorPage` endpoint
A plain `useQuery` hardcoded to one page's `limit`, with the API's own
`hasMore`/`nextCursor` fields never read anywhere in the feature, silently
truncates the list past that limit — no error, no visible indication,
invisible against any test org with fewer rows than the limit.
**Fix:** `useInfiniteQuery` + a "Load more" button gated on `hasNextPage`,
matching `LeadListPage.tsx`/`ProjectListPage.tsx`'s existing pattern.
**Search:** "silently capped"
**Recurred:** Tracker's Follow-ups/Collections tabs (this was the most
recent real customer-facing instance).

---

## Frontend — i18n

### 21. Duplicate key name at different points in the same JSON object
Two keys with the identical name in one object literal (an enum-lookup
object `type: {...}` and a later plain field label also named `type`) — the
**second silently wins** at parse time, the first is discarded, and
`t('interaction.type.CALL')` resolves to `undefined`, rendering the literal
key string in the UI. Only the key-parity test (en/hi agree with each
other) exists — nothing checks that a key resolves to something sensible.
**Fix:** pick visibly distinct names up front (`typeLabel` vs `type`) for an
enum-lookup object and a plain field label with similar names. Only ever
caught by looking at a real screenshot.
**Search:** "silently wins"

### 22. Hardcoded currency symbol duplicated with a formatter that already adds one
`formatIndianCurrency()` already includes `₹` — writing a translation string
with a literal `₹{{amount}}` prefix renders `₹₹...`. Caught only visually.
**Search:** "double-₹"

---

## Frontend — Mobile / 360px

### 23. Wide `<Table>` truncated to key columns off-screen at 360px
The table's own `overflow-x-auto` correctly contains the overflow so the
*page* never breaks, but the columns that actually matter (Status, Error)
silently scroll off past what a real user has any reason to discover.
Automated overflow/console-error checks all pass against this — the bug is
only visible by looking.
**Fix:** default internal tooling / dense-data pages to card/chip rows
instead of a wide table, so the layout naturally reflows at any width.
**Search:** "second time in this same session a wide table"

### 24. Adding items to a fixed-width flex nav bar with no overflow handling
Degrades silently until enough items are added that the nav's combined
width exceeds the viewport — and because the nav itself has no
`overflow-x` containment, the excess width can propagate all the way up to
`document.documentElement.scrollWidth`, making the **whole page**
horizontally scrollable and pushing later nav items off-screen entirely.
**Fix:** `overflow-x-auto` on the nav itself + `shrink-0` on each item, and
check any new nav-item addition at 360px specifically.
**Search:** "BottomNav overflowed the entire page"

---

## Process

### 25. Automated checks (overflow / console-error / i18n-key-leak) are necessary but not sufficient
Several real bugs (a duplicated page title stacked on itself, a double-₹
symbol, a JSON key collision) passed every automated check this project
runs and were only found by actually looking at a real screenshot.
**Rule:** a browser-driven verification pass always needs a real look at
the rendered output, not just "did it error."

### 26. A regression test needs to be proven to actually catch the bug
Writing a test that passes *after* the fix doesn't by itself prove it would
have failed *before* the fix. This project's standing discipline: revert
the fix, confirm the new test goes red with the reported symptom, restore
the fix, confirm green — done explicitly at least twice (`InteractionService`
Mark Done fix, calendar stale-cache fix) after skipping it once elsewhere
turned out to matter.
**Search:** "verified the test actually catches the bug"
