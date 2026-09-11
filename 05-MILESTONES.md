# Shardeya — Implementation Milestones

**Principle:** each milestone produces a deployable increment that a real user can test. No milestone depends on an unfinished later milestone. Milestones are sequenced so that the **Builder side ships first** (it is the core product) and the Broker side layers on top of the shared foundation with minimal net-new work.

**Estimated cadence:** 2–3 week sprints per milestone for a 2–3 person team. Adjust based on actual velocity after M0.

---

## Milestone 0 — Project Bootstrap & Design System

**Goal:** a running local dev environment, CI pipeline, empty shell that authenticates, and a design system in place so every subsequent screen looks consistent.

### What gets built
| Layer | Deliverable |
|---|---|
| **Infra** | Docker Compose: Postgres 16, Redis 7, MinIO (S3-compatible), MailHog (SMTP), WhatsApp stub server |
| **Backend** | Spring Boot 3.3 skeleton: module package structure (`platform`, `foundation`, `builder`, `broker`, `shared`), Flyway baseline migration (empty schema + `organization`, `app_user`, `role`, `role_permission` tables), ArchUnit boundary tests, OpenAPI config, global exception handler (RFC 9457), tenant filter infrastructure, health endpoint |
| **Frontend** | Vite + React 18 + TypeScript scaffold, Tailwind + shadcn/ui setup, i18next with `common` namespace (EN + HI), `AppShell` / `TopBar` / `Sidebar` / `MobileDrawer` / `BottomNav` / `PageHeader` components, routing skeleton (`/broker/*`, `/builder/*`), dark/light mode tokens, responsive breakpoint tests |
| **Design** | Colour palette, typography scale, spacing scale, component variants (buttons, inputs, cards, badges, dialogs, tables, empty states), documented in a Storybook or a `/design` route |
| **CI** | GitHub Actions: lint, unit tests, ArchUnit, Flyway dry-run, Lighthouse mobile budget (placeholder), Docker build |
| **Docs** | `CLAUDE.md` (working agreement), `README.md` (setup in 3 commands), `.env.example` |

### Exit criteria
- `docker compose up` → app starts, health endpoint returns 200
- ArchUnit passes: `builder.*` cannot import `broker.*` and vice versa
- Shell renders on 360px Android (Chrome DevTools) with sidebar → hamburger transition
- Language toggle switches all visible labels between EN and HI instantly
- CI green on an empty `main` branch
- Design system page renders all component variants

### Implementation steps
1. Init Spring Boot project with module packages; add Flyway, OpenAPI, global error handler
2. Write baseline migration V1: `organization`, `app_user`, `role`, `role_permission`, RLS policies
3. Add ArchUnit tests for package boundaries
4. Init Vite + React + TS; install Tailwind, shadcn/ui, i18next, TanStack Query, Zustand
5. Build `AppShell`, `TopBar`, `Sidebar`/`MobileDrawer`, `BottomNav`, `PageHeader`
6. Create i18n namespace `common` with EN/HI JSON files; wire `LanguageToggle`
7. Define design tokens (colours, typography, spacing) in Tailwind config
8. Build all primitive components in shadcn: Button, Input, Select, Textarea, Dialog, Card, Badge, Table, EmptyState, Skeleton, Toast
9. Set up Docker Compose with all services
10. Set up CI pipeline
11. Write `CLAUDE.md` and `README.md`

---

## Milestone 1 — Identity, Auth & Tenancy

**Goal:** a user can sign up as a Broker or Builder, verify OTP, log in, see their empty role-specific dashboard shell, and log out. Multi-tenancy isolation is proven.

### Modules delivered
- **M-01** Identity, Tenancy & Authentication (complete)
- **M-02** RBAC & Permission Engine (core — broker owner + builder admin roles; staff roles in M4)
- **M-03** Localization — already wired in M0; this milestone adds the `auth` and `errors` namespaces
- **M-04** App Shell — login-gated routing, profile menu, subscription badge (static "Free")

### What gets built
| Layer | Deliverable |
|---|---|
| **DB** | Full M-01 schema: `organization`, `app_user`, `role`, `role_permission`, `refresh_token`, `otp_challenge` (Redis primary + DB audit), `subscription` (seeded FREE) |
| **Backend** | Signup → OTP verify → org+user creation, Login (password + OTP), Forgot/Reset password, JWT (access 15min + refresh 30d rotation with reuse detection), `TenantContext` filter, RLS per-connection setup, `/me` bootstrap endpoint, rate limiting on OTP |
| **Frontend** | `/signup` (with `RoleSelectCard`), `/signup/verify` (OTP input), `/login`, `/login/otp`, `/forgot-password`, `/reset-password`, role-based redirect, `ProfileMenu` (View/Edit Profile, Change Password, Logout), session-expiry modal with form-state preservation |
| **Tests** | Tenant isolation test suite: create two orgs, assert every endpoint returns 404 for foreign resources. OTP rate-limit test. Refresh token reuse-detection test. |

### Exit criteria
- Signup as Broker → OTP → lands on `/broker/dashboard` (empty shell)
- Signup as Builder → OTP → lands on `/builder/dashboard` (empty shell)
- Login with password works; Login with OTP works
- Forgot password → reset → can log in with new password
- Remember Me checked → refresh token lasts 30 days
- Tenant A cannot see Tenant B's `/me` data (test proves this)
- Hindi signup form renders all labels correctly
- Mobile (360px) → signup form is usable with thumb-only input
- CI green with all new tests

### Implementation steps
1. Backend: `AuthController`, `AuthService`, `OtpService` (Redis), SMS gateway adapter (stub in dev)
2. Backend: JWT generation/validation, `RefreshTokenService` with rotation + reuse detection
3. Backend: `TenantContextFilter` (populates ThreadLocal from JWT), RLS connection setup interceptor
4. Backend: `UserController` (`/me`, profile edit, password change)
5. Backend: signup transaction (org + user + role + subscription in one tx, post-OTP)
6. Backend: rate limiting (Redis token bucket) on OTP endpoints
7. DB: Flyway V2 migration with all M-01 tables + seed system roles
8. Frontend: `AuthLayout`, `SignupForm` with `RoleSelectCard`, `PhoneInput`, `PasswordInput`
9. Frontend: `OtpInput` (6 boxes, auto-advance, paste, `autocomplete="one-time-code"`)
10. Frontend: `LoginForm`, `ForgotPasswordForm`, `ResetPasswordForm`
11. Frontend: Auth context (token storage, refresh interceptor, role-based routing)
12. Frontend: `ProfileMenu` dropdown, Edit Profile page, Change Password page
13. Frontend: session-expiry modal (detect 401, attempt silent refresh, show modal if expired, preserve form state)
14. i18n: `auth.json` and `errors.json` in EN + HI
15. Tests: tenant isolation suite, OTP tests, refresh rotation tests
16. Integration test: full signup → login → /me → logout flow

---

## Milestone 2 — Builder: Projects & Plot Grid (Core Inventory)

**Goal:** a Builder can create a project, add plots (manually and via bulk upload), see the interactive colour-coded grid, filter/search plots, and edit plot details. This is the signature feature.

### Modules delivered
- **B-02** Manage Projects (complete)
- **B-03** Plot Inventory & Interactive Grid (complete)
- **B-06** Bulk Plot Creation & Upload — both paths: Quick Range Create (no file, range + shared properties) and Excel Bulk Import (migration, existing/mixed data) (complete)
- **M-05** Media & Document Storage (upload pipeline, standard bucket only — sensitive bucket in M3)
- **M-07** Import Engine (plot-specific configuration)
- **M-08** Calculators (Plot Size calculator only — needed for size entry; rest in M6)
- **M-09** Subscription & Entitlements (quota enforcement only — payment in M8)

### What gets built
| Layer | Deliverable |
|---|---|
| **DB** | `project`, `project_media`, `plot`, `media_asset`, `import_job`, `import_row`, `measurement_unit`, `plan`, `plan_limit`, `subscription`, `org_usage` |
| **Backend** | Full project CRUD, plot CRUD, grid config, compact grid endpoint, plot search (tsvector + trigram), bulk upload pipeline (template generation, streaming parse, two-phase validate→commit), media upload intent + completion + derivatives (async), area conversion service, entitlement checks |
| **Frontend** | Project list (cards), Project form (5-step wizard), Project detail with tabs, **PlotGrid** (canvas for >400 plots, DOM below, pinch-zoom + pan, status colours, search-highlight, click→drawer), PlotDetailDrawer, PlotForm, PlotFilterBar, PlotSearchBox, PlotLegend, GridLayoutEditor, UnplacedPlotsTray, ImportWizard (4-step), ImageUploader + PhotoGrid, AreaInput, `EntitlementGuard` |
| **Tests** | Grid renders 5,000 plots in <250ms (perf test). Plot-number uniqueness. Grid cell uniqueness. Bulk import with 500 rows. Quota enforcement. |

### Exit criteria
- Create a project with all §12.2.1 fields including cover image and layout map
- Add 10 plots manually → grid renders with green cells
- Bulk upload 500 plots from the downloadable template → preview → commit → grid populates
- Grid: pinch-zoom on mobile, status colours correct, search highlights a cell, click opens detail
- Filter: Available Only shows green cells only
- Edit a plot's price → grid cell reflects the change
- Project quota and plots-per-project quota enforced (Free plan limits)
- All above works in Hindi
- Performance: grid interactive in <250ms with 500 plots on a Moto G-class emulator

### Implementation steps
1. DB: V3 migration — `project`, `plot`, `media_asset`, `project_media`, `measurement_unit` seed, `plan` + `plan_limit` seed, `org_usage`
2. Backend: `ProjectController` + `ProjectService` (CRUD, status transitions, quota checks)
3. Backend: `PlotController` + `PlotService` (CRUD, status guards, grid positioning, search)
4. Backend: grid endpoint (compact tuple format, covering index query)
5. Backend: `MediaService` (pre-signed upload, completion, async derivative generation, EXIF strip, AV stub)
6. Backend: `ImportService` (template generation with data-validation dropdowns, streaming parse, per-row validation, chunked commit)
7. Backend: `AreaConversionService` (unit catalogue, state-aware Bigha)
8. Backend: `EntitlementService` + `OrgUsageService` (trigger-maintained counters, pre-flight checks)
9. Frontend: `ProjectList`, `ProjectForm` (5-step wizard), `ProjectDetail` with tab layout
10. Frontend: `PlotGrid` — canvas renderer with viewport culling, pinch/zoom, pan, status colours, accessibility patterns
11. Frontend: `PlotDetailDrawer`, `PlotForm`, `PlotStatusSelector`
12. Frontend: `PlotFilterBar`, `PlotSearchBox` (highlight + scroll-to), `PlotLegend`, `UnplacedPlotsTray`
13. Frontend: `GridLayoutEditor` (drag unplaced→cell, mark blocked cells)
14. Frontend: `ImportWizard` (download template → upload → preview table with inline editing → commit with progress)
15. Frontend: `ImageUploader` (compression, progress, retry), `PhotoGrid` (drag reorder)
16. Frontend: `AreaInput` (value + unit, live sqft conversion)
17. Frontend: `EntitlementGuard` component + `useCan` hook for quota display
18. i18n: `project.json`, `plot.json`, `import.json` namespaces
19. Tests: grid performance, uniqueness, bulk import, quota

---

## Milestone 3 — Builder: Plot Sales, Payments & Documents

**Goal:** a Builder can sell a plot (record buyer details, set up an instalment schedule), record payments, track balances and overdue instalments, and attach sale documents including government ID. This is the revenue engine.

### Modules delivered
- **B-04** Plot Sale, Buyer & Documents (complete)
- **B-05** Payment Tracker & Instalment Schedules (complete)
- **M-05** Media — sensitive bucket for government ID documents (§25.2)
- **M-06** Notification Engine (in-app bell only — WhatsApp/SMS in M7; outbox pattern set up here)

### What gets built
| Layer | Deliverable |
|---|---|
| **DB** | `plot_sale`, `payment_schedule`, `payment_record`, `payment_allocation`, `plot_document`, `receipt_sequence`, `notification`, `notification_type`, `outbox_event` |
| **Backend** | Sale creation (single transaction: plot_sale + plot status + schedule + commission ledger placeholder + outbox), payment recording with auto-allocation (oldest-due-first) + receipt generation, payment reversal, cheque lifecycle, schedule management (add/edit/waive), sensitive document upload (KMS bucket, audited access), notification service (in-app store + SSE stream), outbox poller |
| **Frontend** | `SaleWizard` (5-step: Buyer → Deal → Payment plan → Broker placeholder → Review), `BuyerForm`, `LeadLinkPicker`, `GovIdInput` (masked), `PaymentPlanBuilder` (with presets + reconciliation bar), `DocumentSlots` (4 typed + Other), `SensitiveDocGuard`, `PaymentSummaryPanel` (total/paid/balance with progress bar), `ScheduleTable`, `AddPaymentDialog` (with allocation preview), `PaymentHistoryTable`, `OverdueBadge`, `ChequeStatusChip`, `CancelSaleDialog`, `NotificationBell` + `NotificationPanel` (SSE-driven) |
| **Tests** | Sale creates all related records atomically. Payment allocation correctly handles partial and overpayments. Overdue status fires on date boundary. Sensitive access is logged. Receipt numbers are gapless. Concurrent sale of the same plot fails cleanly. |

### Exit criteria
- Mark a plot as Sold → sale wizard → buyer details + gov ID + instalment schedule → save → plot turns red
- Record 3 payments → balances update correctly, schedule rows turn green
- Record a cheque → mark bounced → reversal auto-created → balance reverts
- Overdue instalment shows red badge with days overdue
- Government ID: encrypted at rest, masked display, reveal is audited, stored in separate bucket
- Add sale agreement PDF and registry deed
- Cancel a sale → plot returns to Available
- Notification bell shows "Plot A-12 sold" in-app notification
- Hindi payment forms and receipts render correctly
- All works on 360px mobile

### Implementation steps
1. DB: V4 migration — all payment and sale tables, triggers for `total_paid` maintenance, receipt sequence
2. Backend: `PlotSaleService` (transactional sale creation with status guard + plot update + schedule generation + outbox)
3. Backend: `PaymentService` (record with allocation, reversal, cheque lifecycle, Idempotency-Key)
4. Backend: `PaymentAllocationService` (oldest-due-first default, manual override)
5. Backend: `ScheduleService` (CRUD, waive, overdue detection job)
6. Backend: `ReceiptService` (gapless numbering, PDF generation stub — full PDF in M5)
7. Backend: Sensitive media path (separate bucket config, KMS encryption, audited access endpoint)
8. Backend: `NotificationService` (in-app create + query + SSE stream), `OutboxPoller` (1s poll, batch 100, SKIP LOCKED)
9. Backend: `CancelSaleService` (plot restoration, schedule cancellation, commission handling)
10. Frontend: `SaleWizard` (5 steps), `BuyerForm`, `GovIdInput`, `PaymentPlanBuilder`
11. Frontend: `PaymentSummaryPanel`, `ScheduleTable`, `AddPaymentDialog` with allocation preview
12. Frontend: `PaymentHistoryTable`, `ChequeStatusChip`, `OverdueBadge`
13. Frontend: `DocumentSlots`, `SensitiveDocGuard` (blur + reveal)
14. Frontend: `CancelSaleDialog` (consequences list)
15. Frontend: `NotificationBell` + `NotificationPanel` (SSE + polling fallback)
16. i18n: `sale.json`, `payment.json`, `notification.json` namespaces
17. Tests: atomicity, allocation, overdue, sensitive access, concurrency, receipt gaplessness

---

## Milestone 4 — Builder: Team, Leads & Calendar

**Goal:** a Builder can add team members with roles, assign leads to staff, log follow-up interactions, and see everything on a calendar. The builder becomes a multi-user product.

### Modules delivered
- **B-12** Admin Panel / Team & Roles (complete)
- **B-07** Builder Customer / Lead Manager (complete)
- **M-11** Calendar & Reminder Engine (complete)
- **B-09** Builder Calendar (complete)
- **M-02** RBAC — all five builder staff roles fully enforced

### What gets built
| Layer | Deliverable |
|---|---|
| **DB** | `staff_invite`, `user_project_access` populated, `customer` (builder columns), `interaction`, `calendar_event` |
| **Backend** | Team CRUD (add, edit role, deactivate, remove, invite flow with secure set-password link, resend), full RBAC enforcement across all existing endpoints (project scope + ownership predicates), builder customer/lead CRUD with assignment, interaction logging (append-only with amendment window), calendar service (auto-projection from follow-ups and instalment dues, manual events), staff activity aggregation |
| **Frontend** | Admin Panel: `TeamMemberTable`, `AddTeamMemberForm` (with inline permission matrix preview), `RoleSelector`, `PermissionMatrixTable`, `ProjectAccessSelector`, `InviteStatusChip`, `DeactivateDialog` (with reassignment picker). Customer Manager: `CustomerList` (builder variant with Assigned To, Project/Plot interest), `CustomerForm` (builder fields), `AssignStaffDropdown`, `BulkAssignToolbar`, `UnassignedLeadsBanner`, `InteractionTimeline`, `AddInteractionDialog`. Calendar: `CalendarMonthView`, `CalendarWeekView`, `CalendarDayView`, `ViewSwitcher`, `StaffCalendarFilter`, `InstalmentEventCard`, `AddEventDialog`, `AgendaList` (mobile default) |
| **Tests** | Sales Executive sees only own leads. Accounts Staff sees financials but not leads. View Only can read but not write. Project scope is respected on all queries. Interactions are append-only. Calendar auto-events follow source changes. Team quota enforced. |

### Exit criteria
- Add a Sales Executive, scoped to one project → they log in and see only that project's leads
- Accounts Staff can record payments (M3) but cannot see the lead pipeline
- View Only can read everything but every edit button is hidden and the API rejects writes
- Assign a lead → assignee gets a notification → their customer list shows the lead
- Log 3 follow-up interactions → timeline renders reverse-chronologically → entries are undeletable
- Calendar shows auto-projected follow-up dates + instalment dues + manual meetings
- Staff filter on calendar works
- Deactivating a staff member → reassignment prompt → leads transferred
- Team quota enforced (Free = owner only)
- All above in Hindi on mobile

### Implementation steps
1. DB: V5 migration — `staff_invite`, customer builder columns, `interaction` with delete-block rule, `calendar_event`
2. Backend: `TeamService` (CRUD, invite flow, deactivation with reassignment, reactivation)
3. Backend: Full RBAC enforcement — `@RequiresPermission` annotations on all existing controllers, `@ScopedToProject`, ownership predicates in repositories
4. Backend: `BuilderCustomerService` (CRUD with assignment, source attribution, duplicate detection)
5. Backend: `InteractionService` (append-only, 15-min amendment, follow-up date propagation)
6. Backend: `CalendarService` (auto-projection upsert keyed on source, manual event CRUD, range queries)
7. Backend: Staff activity aggregation queries
8. Frontend: Admin Panel — all components listed above
9. Frontend: Builder Customer Manager — all components
10. Frontend: Calendar — all views with mobile agenda default
11. Frontend: Wire permission guards across all existing M2/M3 screens
12. i18n: `team.json`, `customer.json`, `calendar.json` namespaces
13. Tests: exhaustive RBAC matrix tests, interaction immutability, calendar projection tests

---

## Milestone 5 — Builder: Financials, Tracker & Dashboard

**Goal:** the Builder has a complete financial overview, a daily work queue (follow-up + collection tracker), and a populated dashboard. The product is functionally complete for daily use.

### Modules delivered
- **B-08** Financials / Account Manager (complete)
- **B-13** Follow-up & Collection Tracker (complete)
- **B-01** Builder Dashboard (complete — all 10 cards wired)
- **B-10** Builder Deals History (complete)

### What gets built
| Layer | Deliverable |
|---|---|
| **DB** | `org_metrics` (builder columns), `mv_org_revenue_monthly` materialised view |
| **Backend** | Financial summary endpoint (6 cards), payment records listing, pending/overdue view with WhatsApp reminder action (stub), bulk reminder, tracker endpoints (follow-up queue, collection queue, inline actions), deals history listing + detail view, dashboard endpoint (10 cards + alerts + activity), `org_metrics` maintenance triggers, materialised view refresh job |
| **Frontend** | Financials: `FinancialSummaryCards`, `PaymentRecordsTable`, `PendingInstalmentsTable` (with inline Record Payment and Send Reminder buttons), `FinancialFilterBar`, `RevenueTrendChart`, `PaymentModeBreakdown`, `BulkReminderDialog`. Tracker: `TrackerTabs` (with count badges), `FollowUpTable` (with inline QuickLogDialog), `CollectionTable` (with inline QuickPaymentDialog), `RangeFilterPills`, `CallButton`, `WhatsAppButton`. Deals History: `DealsHistoryTable`, `DealFilterBar`, `DealDetailView`, `DealTimeline`. Dashboard: `DashboardGrid` (all 10 §11.1 cards), `SummaryCard`, `AlertBanner`, `QuickActionRow`, `EmptyDashboard` (onboarding checklist) |
| **Tests** | Dashboard cards reflect real data. Financial sums include reversals. Overdue detection fires correctly. Tracker inline actions update the source and remove the row. Deals history shows only archived deals. |

### Exit criteria
- Dashboard shows all 10 cards with correct live data; each card deep-links to the right filtered view
- Financials: 6 summary cards correct; payment table filterable; overdue tab with days-overdue column
- Follow-up Tracker: today's follow-ups listed; log a follow-up inline → row disappears
- Collection Tracker: overdue instalments listed; record payment inline → row disappears
- Deals History: completed and cancelled deals with full detail timeline
- Empty-state onboarding for a new builder (no projects yet)
- All cards use Indian currency abbreviation (₹18.24 Cr)
- Works on 360px mobile

### Implementation steps
1. DB: V6 migration — `org_metrics`, materialised view, triggers
2. Backend: Financial aggregation endpoints (summary, payments, pending, overdue, revenue trend)
3. Backend: Tracker endpoints (follow-up queue with scope, collection queue, inline log/payment/remind)
4. Backend: Deals history endpoints (listing, detail assembly, export)
5. Backend: Dashboard endpoint (10 cards from `org_metrics` + live windowed queries + alerts)
6. Backend: `org_metrics` trigger maintenance + nightly reconciliation job
7. Frontend: Financials page with all components
8. Frontend: Tracker page with two tabs and inline action dialogs
9. Frontend: Deals History page with detail view
10. Frontend: Dashboard with all 10 cards, alerts, quick actions, empty state
11. Tests: data correctness, inline actions, scope enforcement

---

## Milestone 6 — Builder: Broker Management & Commissions

**Goal:** a Builder can manage their broker channel — onboarding, three-level commission configuration, automatic tier advancement, commission ledger with payments, and broker performance stats.

### Modules delivered
- **B-14** Broker Management (complete)
- **M-08** Calculators — all three (Plot Size, Brokerage, Stamp Duty)

### What gets built
| Layer | Deliverable |
|---|---|
| **DB** | `broker_partner`, `broker_commission_config`, `broker_tier`, `broker_tier_history`, `commission_ledger_entry`, `commission_payment`, `broker_interaction`, `stamp_duty_rate` |
| **Backend** | Broker CRUD, commission config CRUD with resolution algorithm, tier CRUD with overlap validation, tier auto-evaluation on deal completion (trigger chain: sale completes → broker.deals_closed_count++ → tier evaluated → upgrade notification), commission ledger creation at sale time (wire into M3's sale transaction), commission payment recording, broker statement PDF, broker performance aggregation, all three calculators (plot size, brokerage, stamp duty with rate lookup) |
| **Frontend** | Broker Management: `BrokerTable`, `BrokerForm`, `TierBadge`, `TierProgressBar`, `CommissionConfigTable`, `ScopeSelector`, `CommissionPreviewCard`, `TierConfigEditor`, `CommissionLedgerTable`, `RecordCommissionPaymentDialog`, `BrokerPerformancePanel`, `BrokerDealsTable`, `BrokerNotesTimeline`, `BrokerStatementDownload`, `InviteBrokerCard`. Calculators: `CalculatorTabs`, `PlotSizeCalculator`, `BrokerageCalculator`, `StampDutyCalculator` with state/type/gender selectors |
| **Tests** | Commission resolves correctly at PLOT → PROJECT → GLOBAL → default precedence. Tier upgrades automatically. Tier never auto-downgrades. Commission snapshot immutable. Ledger balance correct with partial payments. Calculators return correct figures (state-specific Bigha, stamp duty). |

### Exit criteria
- Add a broker with per-project commission rates → preview shows correct amount
- Create a sale attributed to the broker → commission ledger entry auto-created
- Complete the sale → broker's deal count increments → tier upgrades from Bronze to Silver → notifications fire
- Record commission payment → ledger balance updates
- Generate a broker statement PDF
- Broker detail page shows performance stats, deal history, tier progress bar
- All three calculators work with correct state-specific rates
- Stamp duty rates editable (admin endpoint, M14 basic)
- Broker quota enforced (Pro = 10)

### Implementation steps
1. DB: V7 migration — all broker and commission tables, tier seeds, stamp duty rates seed
2. Backend: `BrokerPartnerService` (CRUD, deactivate/block/reactivate)
3. Backend: `CommissionConfigService` (CRUD, resolution algorithm with precedence)
4. Backend: `BrokerTierService` (CRUD, overlap validation, auto-evaluation, manual override)
5. Backend: Wire commission ledger creation into `PlotSaleService` (M3)
6. Backend: `CommissionPaymentService` (record, reverse)
7. Backend: `BrokerStatementService` (PDF via B-11 machinery when available, or standalone)
8. Backend: Tier evaluation trigger chain (sale complete → count → evaluate → notify)
9. Backend: Calculator endpoints (plot size, brokerage with GST, stamp duty with rate lookup)
10. Frontend: Broker Management — all listed components
11. Frontend: Wire broker picker into the `SaleWizard` (M3) with commission preview
12. Frontend: Calculator page with three tabs
13. i18n: `broker.json`, `commission.json`, `calculator.json` namespaces
14. Tests: resolution precedence, tier lifecycle, ledger correctness, calculator accuracy

---

## Milestone 7 — Builder: Reports, Legal Docs, Stats & Notifications

**Goal:** complete the Builder product with reports, legal document generation, analytics dashboard, and the full notification system (WhatsApp, SMS, email).

### Modules delivered
- **B-11** Reports & Legal Document Generation (complete)
- **B-15** Stats & Analysis (complete)
- **M-06** Notification Engine — full (WhatsApp, SMS, email channels + buyer notifications)
- **M-10** Reporting & Export Engine (builder configuration)
- **M-13** Audit, Soft-Delete & Privacy (complete — trash, audit log, data export, account deletion)

### What gets built
| Layer | Deliverable |
|---|---|
| **DB** | `document_template`, `generated_document`, `document_number_sequence`, `report_definition` (9 builder reports seeded), `notification_preference`, `message_delivery`, `whatsapp_optin`, `audit_log` (partitioned), `sensitive_access_log`, `data_export_request`, `account_deletion_request`, materialised views for stats |
| **Backend** | 9 builder report definitions + preview/export, document template CRUD (sandboxed rendering), document generation (allotment letter, payment receipt, demand letter), bulk generation, notification channel adapters (WhatsApp via Meta Cloud API, SMS via MSG91, email via SES), notification preference management, all §22.3 triggers wired, §22.4 buyer messages, stats endpoints (10 charts), audit interceptor, soft-delete lifecycle (nightly purge), data export job, account deletion flow |
| **Frontend** | Reports: `ReportCatalog`, `ReportViewer`, `ReportFilterPanel`, `ReportChart`, `ExportButton`. Legal Docs: `TemplateList`, `TemplateEditor` (variable palette + live preview), `GenerateDocumentButton`, `DocumentList`, `BulkGenerateDialog`. Stats: all 10 chart components, `StatsFilterBar`, `DateRangePicker`, `KpiStrip`, `StaffPerformanceTable`. Notifications: `NotificationPreferenceMatrix`, `WhatsAppOptInCard`. Privacy: `TrashList`, `RestoreButton`, `AuditTimeline`, `DataExportCard`, `DeleteAccountFlow` |
| **Tests** | All 9 reports return correct data. Templates render without unresolved variables. Receipts have gapless numbers. WhatsApp sends successfully (sandbox). All 16 builder notification triggers fire. Audit log captures mutations. Soft delete + 30-day purge works. Data export ZIP contains all entities. |

### Exit criteria
- All 9 builder reports render on-screen and export as Excel/PDF
- Edit an allotment letter template → preview against a real sale → generate PDF
- Record a payment → receipt PDF auto-generated → buyer gets WhatsApp confirmation
- Generate demand letters for 27 overdue buyers in one action → ZIP of PDFs
- Stats page shows all 10 charts with working filters
- Notification preferences configurable per channel
- Deleted items appear in Trash with restore option
- Audit log shows who changed what and when
- Data export produces a downloadable ZIP
- Account deletion flow with 30-day cancellation window

### Implementation steps
1. DB: V8 migration — templates, documents, reports, notifications, audit, stats views
2. Backend: Report definitions (9 builder) + M-10 engine (preview, export, async for large)
3. Backend: Template service (sandboxed rendering, variable allowlist, preview)
4. Backend: Document generation (allotment letter, receipt, demand letter, booking confirmation)
5. Backend: WhatsApp adapter (Meta Cloud API, template management, webhook for status)
6. Backend: SMS adapter (MSG91, DLT templates)
7. Backend: Email adapter (SES)
8. Backend: Wire all §22.3 notification triggers into existing services via outbox events
9. Backend: §22.4 buyer notification templates + opt-in management
10. Backend: Audit interceptor (Hibernate), soft-delete lifecycle job, data export job, deletion flow
11. Backend: Stats endpoints (10 charts from materialised views)
12. Frontend: Reports, Legal Docs, Stats — all listed components
13. Frontend: Notification preferences, privacy settings
14. i18n: `report.json`, `document.json`, `stats.json`, `privacy.json` namespaces
15. Tests: comprehensive testing across all delivered modules

---

## Milestone 8 — Broker: Full Product

**Goal:** the Broker side is complete. Since the foundation modules are all built, the Broker side is primarily UI configuration over existing engines.

### Modules delivered
- **BR-02** Property Manager (complete)
- **BR-03** Property ↔ Customer Linking & Deal Pipeline (complete)
- **BR-04** Broker Customer / Lead Manager (complete)
- **BR-05** Brokerage Analysis (complete)
- **BR-06** Broker Calendar (complete)
- **BR-07** Broker Deals History (complete)
- **BR-08** Broker Reports & Stats (complete)
- **BR-01** Broker Dashboard (complete — all 8 cards)

### What gets built
| Layer | Deliverable |
|---|---|
| **DB** | `property`, `property_media`, `deal`, `deal_stage_history`, `brokerage_receipt`, `customer_property_interest`, `report_definition` (7 broker reports seeded), `org_metrics` (broker columns) |
| **Backend** | Property CRUD (31 fields, conditional by type, staleness tracking), property media management, deal CRUD with 8-stage pipeline, deal stage history, follow-up logging per deal, brokerage receipt recording, customer-property linking, broker-specific customer endpoints, brokerage analysis aggregations (6 cards, 3 charts), broker calendar projections, deals history, 7 broker report definitions, broker dashboard (8 cards), broker notification triggers (§22.2) |
| **Frontend** | Property Manager: `PropertyList` (table/cards), `PropertyFilterBar`, `PropertyForm` (5-step wizard with type-conditional fields), all §5.2.1 field components, `PhotoGrid`, `MediaUrlInput`, `StaleBadge`, `PropertyQuotaBar`. Deals: `InterestedCustomersPanel`, `CustomerLinkPicker`, `DealPipelineStepper` (8 stages), `StageChangeDialog`, `FollowUpTimeline`, `BrokerageForm`. Customer Manager: `CustomerList` (broker variant), `ImportantStar`, `StatusBadge` (6 colours), `PropertyInterestPicker`, `MatchingPropertiesPanel`, `NoFurtherFollowUpToggle`. Brokerage: `BrokerageSummaryCards` (6), 3 charts, `BrokerageTable`, `BrokerageFilterBar`, `PendingBrokeragePanel`. Calendar: broker variant of M-11. Deals History: `DealsHistoryTable`, `DealDetailView`. Reports: 7 report cards. Dashboard: 8 cards, `TodayPanel`, `StalePropertiesAlert` |
| **Tests** | Property type conditional fields. 8-stage pipeline transitions. Brokerage calculations. Staleness detection. Dashboard cards. All 7 reports. Broker notifications. |

### Exit criteria
- Full broker workflow: add property → add customer → link → log follow-ups → advance pipeline through 8 stages → record brokerage → archived in Deals History
- Property form: type-conditional fields (Flat shows bedrooms, Plot doesn't)
- Brokerage Analysis: all 6 cards and 3 charts with correct data
- Calendar shows follow-ups and site visits
- All 7 reports render and export
- Dashboard with all 8 cards, deep-linked
- Hot property, Important customer, Stale property features work
- Bulk property upload via M-07
- Property export as Excel/CSV
- All works in Hindi on 360px mobile

### Implementation steps
1. DB: V9 migration — property, deal, brokerage tables, broker report definitions, broker metrics
2. Backend: Property CRUD (conditional validation, staleness, media), deal pipeline, brokerage
3. Backend: Broker customer extensions (multi-property interest, matching)
4. Backend: Brokerage analysis aggregation, broker calendar projections
5. Backend: Deals history, 7 report definitions, dashboard aggregation
6. Backend: Broker notification triggers (§22.2)
7. Frontend: Property Manager — all components (form is the largest single form in the app)
8. Frontend: Deal pipeline — stepper, follow-up timeline, brokerage form
9. Frontend: Broker Customer Manager — all components
10. Frontend: Brokerage Analysis — cards, charts, tables
11. Frontend: Calendar, Deals History, Reports, Dashboard
12. i18n: `property.json`, `deal.json`, `brokerage.json` namespaces
13. Tests: full broker workflow end-to-end

---

## Milestone 9 — Subscription Payments & Platform Admin

**Goal:** users can upgrade/downgrade plans via Razorpay, and the Shardeya team has an admin console for operations.

### Modules delivered
- **M-09** Subscription — payment integration (complete)
- **M-14** Platform Admin Console (complete)

### What gets built
| Layer | Deliverable |
|---|---|
| **DB** | `subscription_payment`, `feature_flag`, `system_announcement`, `platform_access_log` fully wired |
| **Backend** | Razorpay integration (checkout order creation, webhook handler, signature verification, idempotent activation, auto-renewal, retry schedule, cancellation, proration), grace period enforcement, over-limit mode. Platform admin: separate auth realm, org search/view/suspend/reactivate, break-glass with logging, stamp-duty rate CRUD, plan-limit CRUD, document template management (system defaults), notification catalogue management, feature flags, announcements, revenue dashboard, message delivery log |
| **Frontend** | Subscription: `PlanComparisonTable`, `UsageMeter`, `UpgradePrompt`, checkout flow (Razorpay widget), success/failure pages, `PaymentHistoryTable`, `AutoRenewToggle`, `CancelFlow`, `FeatureLockOverlay`. Platform Admin (separate SPA at `/platform`): all components from M-14 spec |
| **Tests** | Subscription upgrade via webhook. Downgrade with excess data → over-limit mode. Grace period enforcement. Break-glass access logged and time-boxed. Stamp duty rate changes are effective-dated. Feature flags work. |

### Exit criteria
- Builder upgrades from Free to Pro → Razorpay → webhook → entitlements immediately unlock
- At-limit user: "Add" disabled with upgrade prompt → upgrade → immediately unblocked
- Downgrade → existing data preserved, no new entities above limit
- Subscription expiry → 7-day grace (read-only) → 30-day (export-only)
- Platform admin: search orgs, view health, break-glass with audit
- Edit stamp duty rates with effective dating
- Feature flags with percentage rollout
- Announcements targeted by plan/profile

### Implementation steps
1. DB: V10 migration — subscription payments, platform admin tables
2. Backend: Razorpay integration (order, webhook, activation, renewal, retry)
3. Backend: Grace period job, over-limit enforcement
4. Backend: Platform admin service layer (separate auth, break-glass, all CRUD)
5. Frontend: Subscription management page, checkout flow
6. Frontend: Platform Admin SPA (separate build)
7. Tests: payment lifecycle, entitlement enforcement, admin operations

---

## Milestone 10 — Polish, Performance & Hardening

**Goal:** production-ready quality. Performance budgets met, mobile UX refined, error handling comprehensive, i18n complete, accessibility verified.

### What gets done
| Area | Work |
|---|---|
| **Performance** | Lighthouse audit on all key pages at 360px/4G → meet budgets (LCP ≤2.5s, JS ≤180KB initial). Bundle analysis and code-splitting refinement. Dashboard API p95 ≤400ms. Plot grid 500-plot render ≤250ms. Database query review: EXPLAIN ANALYZE on all list/filter queries; add missing indexes. Redis cache tuning. Image lazy-loading and srcset verification. |
| **Mobile UX** | Full walkthrough of every form and table on a real 360px Android device (Moto G or equivalent). Fix tap targets, overflow, keyboard interactions, form-state preservation across orientation changes. Ensure tables → cards on mobile. Bottom nav polish. |
| **i18n** | Complete Hindi translation review by a native speaker. Devanagari rendering test (conjuncts, matras, numbers). Font loading optimised. Number/currency/date formatting in Hindi locale. CI gate: no missing keys. |
| **Error handling** | Every API error returns a specific `messageKey` with params. Every frontend error boundary catches gracefully. Offline mode: queued mutations with retry. Session expiry mid-form preserves state. Network error toasts with retry. |
| **Accessibility** | All interactive elements labelled (§24.4). Keyboard navigation for the plot grid. Focus management on modals/drawers. Colour-contrast check (WCAG AA). Status communicated by more than colour (patterns, icons, labels). |
| **Security** | Penetration testing: SQL injection, XSS, CSRF, IDOR (tenant isolation), upload exploits, JWT handling. Rate-limit verification. Sensitive field masking in logs. Content-Security-Policy headers. |
| **Data seeding** | Comprehensive demo tenants: 1 broker (50 properties, 100 customers, 20 deals), 1 builder (3 projects × 200 plots, 100 leads, 80 sales with payment histories, 5 brokers). Used for staging, demos, and testing. |
| **Documentation** | API docs (OpenAPI rendered), deployment runbook, monitoring setup, on-call playbook. |

### Exit criteria
- Lighthouse ≥ 85 performance on mobile 4G for dashboard and grid pages
- Zero overflow/scroll issues on 360px for all pages
- Hindi UI reviewed and corrected by a native speaker
- Penetration test: zero critical/high findings
- Demo tenant: full walkthrough without encountering an error
- All CI gates green and enforced on `main`

---

## Milestone 11 — Launch Preparation

**Goal:** staging validated, monitoring live, go-live checklist complete.

### What gets done
| Area | Work |
|---|---|
| **Staging** | Full deployment to staging environment with production topology. Data migration rehearsal with anonymised data. Load test: 100 concurrent users, 50 orgs, key flows (dashboard, grid, payment). |
| **Monitoring** | OpenTelemetry → Grafana dashboards: request latency, error rate, DB connection pool, Redis hit rate, outbox lag, WhatsApp delivery rate. Alerting: p95 >2s, error rate >1%, outbox >100 pending, payment webhook failures. Loki for structured log search. |
| **Ops** | Automated backup verification (restore test). Flyway migration rollback procedure tested. Blue/green deployment tested. SSL certificate renewal automation. Secrets rotation documented. |
| **Legal** | Terms of Service and Privacy Policy (drafted for Indian jurisdiction). Cookie consent (minimal — no third-party tracking on v1). DLT SMS template registration. WhatsApp Business template approvals from Meta. |
| **Go-live** | Domain and DNS. SSL. CDN for static assets + media bucket. Production DB with RLS verified. Feature flags: all features ON. Announcement: "Welcome to Shardeya" banner for early adopters. Support channel (email + WhatsApp). |

### Exit criteria
- Staging passes a 4-hour soak test at target load
- All monitoring dashboards live with correct thresholds
- Backup restore tested successfully
- Legal documents published
- Production infrastructure provisioned and health-checked
- Go-live checklist signed off

---

## Milestone 12 — Post-Launch Iteration

**Goal:** first real-user feedback incorporated, highest-value quick wins shipped.

### Candidate items (prioritised by user feedback)
- PWA: installable, offline caching, push notifications via FCM
- Public plot availability link (shareable read-only grid)
- Auto lead capture webhook
- Payment collection links in WhatsApp reminders
- Google Calendar sync
- Broker portal (brokers log in to see their own ledger and inventory)
- Expense tracking for builders
- Column mapping for bulk imports
- iCal feed for calendars
- Locality autocomplete improvements

---

## Milestone Dependency Graph

```
M0 ─── M1 ─┬── M2 ─── M3 ─── M4 ─── M5 ─── M6 ─── M7 ─┬── M9 ─── M10 ─── M11
            │                                              │
            └── M8 ────────────────────────────────────────┘
                (Broker — can start after M1, but runs faster after M7
                 when all foundation is ready)
```

Builder track (M2→M7) is the critical path. Broker (M8) can be parallelised if a second frontend developer is available after M4, since all foundation modules are in place by then. M9 (subscriptions + admin) can also be parallelised once M7 is underway.

---

## Risk Mitigation

| Risk | Mitigation |
|---|---|
| Plot grid performance on low-end Android | Canvas renderer decided at M2; explicit perf gate (250ms @ 500 plots) in exit criteria; tested on a real device, not emulators |
| WhatsApp Business API approval delays | All WhatsApp features work with a stub in dev/staging; the product is fully usable with in-app + email notifications alone; WhatsApp is additive |
| Razorpay integration complexity | Deferred to M9 (after the product works); Free plan is fully functional without payment |
| Hindi translation quality | Native speaker review at M10; CI gate prevents missing keys; instant-switch architecture means fixes are a JSON commit |
| Tenant isolation failure | Three-layer model (context + filter + RLS); automated test suite runs on every CI build; penetration test at M10 |
| Scope creep on Builder modules | Each milestone has explicit exit criteria; features not in the PRD are deferred to M12+ |
| Key-person risk on a small team | `CLAUDE.md` and this architecture doc ensure any developer can onboard from the documentation |
