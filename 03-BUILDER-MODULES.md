# Shardeya — Builder Modules (B-01 … B-15)

**This is the core product.** The Builder side is where the platform's defensibility lives: plot-level inventory, instalment collection, a broker channel with tiered commissions, staff RBAC, and legal document generation. Nothing else in Indian real-estate SaaS at this price point does all five well.

---

# B-01 · Builder Dashboard

**PRD:** §11.1, §11.2

### 1. Why this module exists
§11.1 defines ten summary cards that must answer, in one glance on a phone, the only questions a builder asks each morning: *What's left to sell? Who owes me money today? Who do I need to call?* Every card is a clickable entry point into a filtered module view, making the dashboard the primary navigation surface, not decoration.

### 2. Dependencies
**Depends on:** M-01, M-02, M-04, M-09, B-02, B-03, B-04, B-05, B-07, B-08, B-10.
**Depended on by:** nothing (leaf).
> Built **last** within its milestone, since every card reads from another module. During earlier milestones it renders only the cards whose sources exist.

### 3. Database schema
No owned tables. Reads a maintained aggregate table:

```sql
CREATE TABLE org_metrics (
  org_id            UUID PRIMARY KEY,
  total_projects    INTEGER NOT NULL DEFAULT 0,
  total_plots       INTEGER NOT NULL DEFAULT 0,
  available_plots   INTEGER NOT NULL DEFAULT 0,
  sold_plots        INTEGER NOT NULL DEFAULT 0,
  reserved_plots    INTEGER NOT NULL DEFAULT 0,
  active_leads      INTEGER NOT NULL DEFAULT 0,
  total_revenue     NUMERIC(19,2) NOT NULL DEFAULT 0,
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
```
Time-window figures (follow-ups today, instalments due this month, deals closed this month) are queried live against indexed columns — they change hourly and are cheap with the right index.

### 4. Backend APIs
```
GET /api/v1/builder/dashboard          → all ten cards in one payload
GET /api/v1/builder/dashboard/activity → recent activity feed (last 20 events)
```
Single call, Redis-cached 5 minutes per `(org, user)` — user matters because a Sales Executive's numbers are scoped to their own leads.

**Response shape:**
```json
{
  "cards": {
    "totalProjects":        {"value": 4,        "link": "/builder/projects"},
    "totalPlots":           {"value": 1240,     "link": "/builder/projects"},
    "availablePlots":       {"value": 612,      "link": "/builder/projects?plotStatus=AVAILABLE"},
    "soldPlots":            {"value": 528,      "link": "/builder/projects?plotStatus=SOLD"},
    "reservedPlots":        {"value": 100,      "link": "/builder/projects?plotStatus=RESERVED"},
    "activeLeads":          {"value": 87,       "link": "/builder/customers"},
    "followUpsToday":       {"value": 12,       "link": "/builder/customers?followUp=today"},
    "instalmentsDueMonth":  {"value": 34, "amount": "4250000", "link": "/builder/tracker?tab=collection&range=month"},
    "totalRevenue":         {"value": "182400000", "link": "/builder/financials"},
    "dealsClosedMonth":     {"value": 9,        "link": "/builder/deals?range=month"}
  },
  "alerts": [{"type":"OVERDUE_INSTALMENTS","count":7,"amount":"980000"}],
  "generatedAt": "2026-07-24T09:14:00+05:30"
}
```

### 5. Frontend pages
`/builder/dashboard` — landing screen for every builder account.

### 6. Components
`DashboardGrid` (responsive: 4 cols desktop → 2 cols tablet → 2 cols on 360px with compact cards), `SummaryCard` (icon, label, big value, optional sub-value, trend arrow vs last month, tap → filtered destination), `AlertBanner` (overdue instalments, over-quota, subscription expiring), `QuickActionRow` (Add Project · Add Lead · Record Payment · Bulk Upload — the four highest-frequency actions, always one tap away), `ActivityFeed`, `CardSkeleton`, `EmptyDashboard` (first-run state with a guided "Create your first project" flow).

### 7. Business logic
- **All ten §11.1 cards implemented exactly**, each with the specified click-through destination.
- Counters read `org_metrics` (write-through maintained); time-windowed figures query live.
- **Role-scoped values:** Sales Executive sees their own leads and follow-ups, and *no* financial cards (§18.3 Financial Access ❌). Accounts Staff sees financial cards and not the lead pipeline. View Only sees everything, no actions.
- **Trend indicators** compare to the equivalent prior period — one extra query, disproportionate value.
- Instalments-due card shows **count and total amount**; the amount is what a builder actually cares about.
- Empty state: a new builder with zero projects sees a three-step onboarding checklist (Create project → Add plots → Add your first lead) instead of ten zeroes.
- `updated_at` surfaces as "Updated 2 min ago" with a pull-to-refresh on mobile.

### 8. User flow
Login → dashboard → "34 instalments due this month · ₹42.5L" → tap → Collection Tracker filtered to this month → tap a row → "Send WhatsApp Reminder" or "Record Payment". Three taps from login to collecting money. That path is the product.

### 9. Permissions
`DATA_VIEW_ALL` → full dashboard. `DATA_VIEW_OWN` → lead/follow-up cards scoped, inventory cards shown, financial cards hidden. `FINANCIAL_VIEW` gates revenue, instalments, and the overdue alert. Every card respects `user_project_access`.

### 10. Edge cases
- Brand-new org → onboarding checklist, not zeroes.
- Project-scoped staff → all counts respect scope; a "Showing 2 of 5 projects" note prevents confusion about why numbers look small.
- Very large numbers → Indian abbreviations (₹18.24 Cr, not ₹182400000).
- Metrics drift (missed trigger) → nightly reconciliation corrects and alerts; the dashboard never blocks on it.
- Timezone boundary → "today" and "this month" computed in IST.
- Slow network → skeletons, then cached values with a staleness marker; never an infinite spinner.
- 360px width → cards remain 2-per-row and legible; no horizontal scroll (§24.2).
- Cache invalidation: any write to plots/payments/leads publishes an invalidation for that org.

### 11. Validation rules
Read-only module. Query params (`range`, `projectId`) validated as enums/UUIDs in scope.

### 12. Notifications
None originated. The dashboard **surfaces** notification-worthy states inline (overdue banner) so a user who ignores the bell still sees them.

### 13. Future scalability
- Configurable dashboard (drag to reorder, hide cards) — `user_dashboard_config`.
- Role-specific default layouts.
- Target-vs-actual widgets once §21.1 "Collection vs Target" targets are configurable.
- Real-time updates via the existing SSE channel.
- Native mobile home-screen widget.

---

# B-02 · Manage Projects

**PRD:** §12.1, §12.2.1

### 1. Why this module exists
The project is the root aggregate of the entire builder domain — plots, sales, payments, leads, commissions, reports and staff scoping all hang off it. §12.2.1 specifies 21 fields including RERA number, approvals, layout maps and brochures, which are also the raw material for the legal documents in §17.2.

### 2. Dependencies
**Depends on:** M-01, M-02, M-05 (cover/gallery/layout/brochure), M-09 (project quota).
**Depended on by:** B-03 … B-15. This is the second module built in the Builder track.

### 3. Database schema
`project` (data model §4). Additionally:
```sql
CREATE TABLE project_media (
  id UUID PK, project_id UUID NOT NULL, media_id UUID NOT NULL,
  role VARCHAR(20) NOT NULL,  -- COVER | GALLERY | LAYOUT | BROCHURE
  sort_order INT NOT NULL DEFAULT 0, [STD]
);
CREATE INDEX ix_project_status ON project(org_id, status) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_project_name ON project(org_id, lower(name)) WHERE deleted_at IS NULL;
```

### 4. Backend APIs
```
GET    /api/v1/projects?status=&city=&search=&sort=&cursor=   → list w/ plot counts
POST   /api/v1/projects
GET    /api/v1/projects/{id}                                   → full detail + counts + revenue
PATCH  /api/v1/projects/{id}
DELETE /api/v1/projects/{id}                                   → soft, guarded
POST   /api/v1/projects/{id}/restore
GET    /api/v1/projects/{id}/summary                           → plot status breakdown, revenue, leads
POST   /api/v1/projects/{id}/media          {mediaId, role, sortOrder}
DELETE /api/v1/projects/{id}/media/{mediaId}
PATCH  /api/v1/projects/{id}/status         {status}
GET    /api/v1/projects/{id}/grid-config
PUT    /api/v1/projects/{id}/grid-config    {rows, cols, blockedCells[]}
```

### 5. Frontend pages
`/builder/projects` (list), `/builder/projects/new`, `/builder/projects/{id}` (overview with tabs: Overview · Plots · Leads · Financials · Documents · Reports), `/builder/projects/{id}/edit`.

### 6. Components
`ProjectCard` (§12.1: cover image, name + location, Total/Available/Sold/Reserved counts as coloured chips, status badge, View/Edit/Delete), `ProjectList` (cards on mobile, table toggle on desktop), `ProjectForm` (**multi-step wizard** — 21 fields in one screen is unusable on a phone: Step 1 Basics, Step 2 Location, Step 3 Size & Timeline, Step 4 Approvals & RERA, Step 5 Media), `ProjectStatusBadge`, `PlotStatusChips` (mini stacked bar showing the sale mix at a glance), `AreaInput` (value + unit selector, live sqft conversion shown beneath), `ApprovalTagInput` (chip input with optional document attachment per approval), `MapLinkInput` (validated + embedded preview), `ProjectHeader` (sticky, with the tab bar), `DeleteProjectDialog` (type-the-name confirmation).

### 7. Business logic
- **All §12.2.1 fields implemented.** `declared_plot_count` is the builder's stated total; the actual `COUNT(plot)` is tracked separately and any mismatch is surfaced as an informational note ("You've declared 200 plots but added 187") — not an error, because plots are added over time.
- **Area normalisation:** `total_area_sqft` derived at write time from `(value, unit, org.state_code)` via `measurement_unit`. All cross-project comparison uses sqft.
- **Status semantics (§12.2.1):** `UPCOMING` (no sales allowed — plots can be created and priced but not sold), `ACTIVE` (normal), `COMPLETED` (no new sales; existing collections continue). Moving to `COMPLETED` with unsold plots prompts a confirmation.
- **Project quota** (§23.1: Free 1, Pro 5, Premium unlimited) checked pre-flight; the Add button is disabled with an explanatory tooltip at limit.
- **Deletion guard:** blocked if any plot has a non-cancelled `plot_sale`. The error lists the blocking plots with links — a bare "cannot delete" is a support ticket.
- **Grid configuration** (§12.2.2) lives here: rows, cols, and blocked cells (roads, parks, amenities). Changing dimensions never destroys plot data — plots outside the new bounds become "unplaced" and appear in a tray for re-placement.
- **RERA number** validated for format per state where a pattern is known; stored regardless (many projects predate RERA).
- Project detail header shows live: total plots, status split, total value, collected, outstanding.

### 8. User flow
```
Manage Projects → + New Project
  → Step 1: name, type, status
  → Step 2: address, locality, city, state, pincode, maps link
  → Step 3: total area + unit, declared plot count, launch date, completion date
  → Step 4: approvals (chips), RERA number, description
  → Step 5: cover image, gallery, layout map, brochure
  → Save → project detail → "Add plots" CTA → choose Bulk Upload (B-06) or Manual (B-03)
```

### 9. Permissions
Create: `PROJECT_CREATE` (Admin, Manager). Edit: `PROJECT_EDIT`. Delete: `PROJECT_DELETE` (Admin only, §18.3). View: any role, filtered by `user_project_access`. Sales Executive sees projects but not project-level financial summaries.

### 10. Edge cases
- Duplicate project name in the same org → blocked (case-insensitive) with a suggestion to append a phase ("Green Valley Phase 2").
- Project with 0 plots → grid shows an empty state with both add paths.
- `declared_plot_count` lowered below actual plot count → warning, allowed (the declaration is aspirational).
- Deleting a project with plots but no sales → allowed; plots soft-delete with it and restore together.
- Changing state after plots exist → area conversions for **Bigha** would shift; the system freezes historical `size_sqft` and warns that new entries will use the new regional factor.
- Cover image deleted → next gallery image promotes.
- Very large project (5,000 plots) → detail page loads counts from aggregates, never `COUNT(*)` on render.
- Project moved to `COMPLETED` with pending instalments → allowed; collections and reminders continue (this is normal — possession happens before final payment).
- Google Maps link with a tracking redirect → normalised and host-allowlisted.

### 11. Validation rules
| Field | Rule |
|---|---|
| name | required, 2–150, unique per org (case-insensitive) |
| project_type | required, ∈ 5 enum values |
| status | required, ∈ {UPCOMING, ACTIVE, COMPLETED} |
| address | required, 5–500 |
| locality | required, 2–150 |
| city | required, 2–100 |
| state_code | required, valid Indian state |
| pincode | optional, exactly 6 digits, first digit ≠ 0 |
| google_maps_url | optional, https, host ∈ {maps.google.com, goo.gl/maps, maps.app.goo.gl} |
| total_area_value | required, > 0, ≤ 3,000,000 |
| total_area_unit | required, ∈ catalogue |
| declared_plot_count | required, ≥ 1, ≤ 100,000 |
| launch_date | optional, ≥ 1970 |
| expected_completion_date | optional, ≥ launch_date |
| description | ≤ 5,000 |
| rera_number | ≤ 60; state-pattern validated where known (warning, not block) |
| cover / gallery / layout / brochure | per M-05 rules; gallery ≤ 30 images |

### 12. Notifications
- Project created → in-app to owner + all Managers.
- Status changed to `COMPLETED` → in-app to team.
- Project deleted → in-app to owner (with restore link).
- Approaching project quota (at limit − 1) → in-app.

### 13. Future scalability
- **Phases/sub-projects** — a `parent_project_id` self-reference; very common as colonies expand.
- Tower/floor/unit hierarchy for `APARTMENT` type projects (the current grid assumes a plot layout; apartments need block→floor→unit). Modelled as an alternative `inventory_layout` strategy on the same `plot` table.
- Public project microsite generated from the project record (marketing + lead capture straight into M-12).
- Geo-fencing / actual map overlay of the layout on satellite imagery (PostGIS-ready: add a `geometry` column).
- RERA API integration for automatic approval status.

---

# B-03 · Plot Inventory & Interactive Grid

**PRD:** §12.2.2, §12.3.1

### 1. Why this module exists
This is **the signature feature of the product**. §12.2.2 describes a visual, colour-coded, zoomable colony map where each cell is a plot — the mental model a builder already has on the wall of their site office. Replacing a physical chart with a live one, on a phone, is the reason a builder switches to Shardeya. Getting this fast and legible at 360px is the hardest UI problem in the project.

### 2. Dependencies
**Depends on:** M-01, M-02, M-03, M-05, M-08 (size conversions), M-09 (plots-per-project quota), B-02.
**Depended on by:** B-04, B-05, B-06, B-08, B-10, B-13, B-15.

### 3. Database schema
`plot` (data model §4). Key indexes repeated here because grid performance depends on them:
```sql
CREATE UNIQUE INDEX ux_plot_number ON plot(project_id, plot_number_norm) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_plot_cell   ON plot(project_id, grid_row, grid_col) WHERE deleted_at IS NULL AND grid_row IS NOT NULL;
CREATE INDEX ix_plot_grid ON plot(project_id, grid_row, grid_col)
  INCLUDE (plot_number, status, size_sqft, is_hot, price) WHERE deleted_at IS NULL;
```
The covering index means the entire grid payload is served from one index-only scan.

### 4. Backend APIs
```
GET  /api/v1/projects/{id}/plots/grid          → compact array for grid render (see below)
GET  /api/v1/projects/{id}/plots?status=&facing=&sizeMin=&sizeMax=&priceMin=&priceMax=
                                &isHot=&isCorner=&isGarden=&search=&sort=&cursor=   → list view
POST /api/v1/projects/{id}/plots
GET  /api/v1/plots/{id}                        → full detail incl. sale, payments, documents
PATCH /api/v1/plots/{id}
DELETE /api/v1/plots/{id}                      → soft, guarded
PATCH /api/v1/plots/{id}/status                {status, reservedFor?, reservedUntil?}
PATCH /api/v1/plots/{id}/hot                   {isHot}
PUT  /api/v1/plots/{id}/position               {gridRow, gridCol}
POST /api/v1/projects/{id}/plots/bulk-position {placements[]}
POST /api/v1/projects/{id}/plots/bulk-update   {plotIds[], patch}   // bulk price/status change
GET  /api/v1/projects/{id}/plots/stats         → counts + value by status/facing/size band
```

**Grid payload is deliberately compact** — 5,000 plots must not be 5MB:
```json
{
  "rows": 40, "cols": 25,
  "blocked": [[3,4],[3,5]],
  "plots": [[3,7,"A-12",1,1200,0],[3,8,"A-13",2,1200,1]],
  "legend": {"statusCodes":{"1":"AVAILABLE","2":"RESERVED","3":"SOLD"}},
  "unplaced": ["<plotId>", "..."]
}
```
Tuple order: `[row, col, plotNumber, statusCode, sizeSqft, isHot]`. ~45 bytes/plot → 5,000 plots ≈ 225KB, gzips to ~40KB.

### 5. Frontend pages
`/builder/projects/{id}/plots` (grid, default) · `?view=list` (table) · `/builder/plots/{id}` (detail, also opens as a drawer over the grid).

### 6. Components
- `PlotGrid` — canvas-or-virtualised-DOM renderer. **Decision: `<canvas>` for >400 plots, DOM cells below that.** DOM gives free accessibility and hit-testing at small sizes; canvas is the only way to keep 5,000 cells at 60fps on a mid-range Android.
- `PlotCell` — plot number + size in small text (§12.2.2), status colour fill, fire icon if hot, corner/garden marker, selected ring.
- `GridControls` — zoom in/out/fit, pan reset, fullscreen.
- `GridFilterBar` — Show All / Available / Sold / Reserved (§12.2.2), plus facing, size range, price range, hot.
- `PlotSearchBox` — search by plot number; **highlights and scrolls the cell into view** with a pulse animation (§12.2.2).
- `PlotLegend` — Green = Available, Red = Sold, Yellow = Reserved (§12.2.2), with counts per status.
- `PlotDetailDrawer` — slides in over the grid on desktop, full-screen sheet on mobile; contains the tabbed detail (Info · Buyer · Payments · Documents).
- `PlotForm`, `PlotStatusSelector` (with the reservation sub-form), `GridLayoutEditor` (drag unplaced plots onto cells, mark cells as blocked/road/park), `UnplacedPlotsTray`, `BulkSelectToolbar` (marquee-select on desktop, long-press multi-select on mobile → bulk price/status update), `PlotListTable` (virtualised alternative view).

### 7. Business logic
- **Grid rendering:** viewport-culled — only visible cells drawn. Pinch-zoom and pan on touch (§24.2 "must be zoomable and scrollable on mobile"), wheel-zoom and drag on desktop. Zoom levels adapt label detail: at <0.6× only colour, at 0.6–1× plot number, at >1× number + size.
- **Colour is not the only signal.** §24.4 accessibility and real-world colour-blindness mean each status also carries a distinct pattern (solid / diagonal hatch / dotted) and the cell label carries a status letter at high zoom. A grid that's only colour-coded fails ~8% of male users.
- **Status transitions are guarded:**
  - `AVAILABLE → RESERVED`: requires `reserved_for`; `reserved_until` defaults to +15 days.
  - `AVAILABLE|RESERVED → SOLD`: **cannot be set directly** — it is a side effect of creating a `plot_sale` (B-04). The status dropdown routes to the sale form. This single rule prevents the most common data-integrity failure: a plot marked Sold with no buyer, no price, and no payment record.
  - `SOLD → AVAILABLE`: only via cancelling the sale (B-04), with a reason, and only if refunds/adjustments are acknowledged.
  - `RESERVED → AVAILABLE`: allowed, logged.
- **Reservation expiry:** a nightly job flags reservations past `reserved_until` and notifies the owner ("3 reservations expired") — it does *not* auto-release, because releasing a plot someone is negotiating on is worse than a stale flag.
- **Price per unit** auto-computed and shown live as the builder types price and size (§12.3.1).
- **Bulk operations** are the difference between usable and unusable: select 40 plots → set price ₹1,800/sqft → each plot's price computed from its own size. Builders reprice by block constantly.
- **Grid auto-layout** for imported plots without positions: infer block/row from plot-number patterns (`A-12`, `B/7`, `12A`) using a configurable strategy, else fill row-major into free cells.
- **Plot numbering is free-text** (`A-12`, `45`, `SEC-3/9`) because Indian layouts have no convention. Uniqueness is enforced on a normalised form so `A-12`, `a12`, and `A 12` collide.

### 8. User flow
```
Project → Plots tab → grid renders (colour-coded)
  → filter "Available Only" → 612 green cells
  → search "A-12" → cell pulses and scrolls into view
  → tap cell → drawer: Plot A-12, 1200 sqft, ₹42,00,000, East facing, Corner
  → "Mark as Sold" → routes to the sale form (B-04)
  → save → cell turns red instantly (optimistic) → confirmed by server
```

### 9. Permissions
View: any role with project scope. Create/edit: `PLOT_CREATE` / `PLOT_EDIT` (Admin, Manager). Delete: `PLOT_DELETE` (Admin only). Price editing may be further restricted via a `PLOT_PRICE_EDIT` sub-permission (Manager can edit plots but a builder may not want them repricing) — configurable, default granted to Manager. Sales Executive: **read-only** on the grid, which is exactly what they need on a site visit.

### 10. Edge cases
- **5,000-plot grid on a 2GB Android** → canvas renderer, tile-based drawing, no DOM node per plot. Tested as an explicit performance gate.
- Plots with no grid position → "Unplaced (37)" tray; the grid remains fully usable.
- Grid resized smaller than existing placements → affected plots move to unplaced, never deleted; a warning names how many.
- Two plots assigned the same cell → prevented by unique index; the API returns which plot occupies it.
- Duplicate plot number on manual create → inline error naming the existing plot with a link.
- Non-rectangular layouts (real colonies are never rectangles) → blocked cells render as road/park/blank; that's what makes it look like the actual layout.
- Plot deleted with an active sale → blocked, with the sale linked.
- Concurrent status change (two staff, same plot) → optimistic lock; loser sees "Plot A-12 was just marked Sold by Ramesh".
- Price 0 (plots reserved for the builder's own family, per §12.3.1 "Reserved For") → allowed when status is `RESERVED`, blocked when `SOLD`.
- Size in Bigha for one plot, sqft for another → both stored as entered, both compared via `size_sqft`.
- Search "12" matching A-12, B-12, 120, 121 → all highlighted, with a match count and next/prev navigation.
- Zooming while the drawer is open → grid pans behind; drawer state preserved.
- Offline → grid served from cache with a staleness marker; status changes queue and sync.

### 11. Validation rules
| Field | Rule |
|---|---|
| plot_number | required, 1–30 chars, unique per project (normalised) |
| status | required, ∈ {AVAILABLE, RESERVED, SOLD}; SOLD only via sale creation |
| reserved_for | required if status = RESERVED, 2–150 |
| reserved_until | optional, ≥ today, ≤ today + 365 |
| size_value | required, > 0, ≤ 10,000,000 |
| size_unit | required, ∈ catalogue |
| price | required, ≥ 0; > 0 required if status = SOLD |
| facing | optional, ∈ 8 values |
| grid_row / grid_col | ≥ 0, < project rows/cols, cell unoccupied and not blocked |
| remarks | ≤ 2,000 |
| plots-per-project quota | Free 50 / Pro 500 / Premium ∞ (§23.1) |

### 12. Notifications
- Plot marked Sold → in-app to owner + relevant staff (§22.3).
- Reservation expiring in 2 days / expired → in-app.
- Bulk update completed → in-app with counts.
- Plot quota at 90% / 100% → in-app with upgrade CTA.

### 13. Future scalability
- **True map overlay:** replace the abstract grid with the actual layout PDF/satellite image and place plots at real coordinates (PostGIS `geometry` column, already anticipated). This is the natural v2 and a strong differentiator.
- Apartment mode: block → floor → unit rendering on the same entity.
- Public availability view — a shareable read-only grid link for buyers/brokers, with prices optionally hidden. Extremely high marketing value.
- Plot merge/split (common when a builder reconfigures) with full lineage tracking.
- Price history table for trend analysis.
- Offline-first grid with a service worker for site offices with poor connectivity.

---

# B-04 · Plot Sale, Buyer & Documents

**PRD:** §12.3.2, §12.3.3

### 1. Why this module exists
This is the moment inventory becomes revenue. §12.3.2 captures buyer identity (including government ID), the transaction, and the broker attribution that drives the entire commission system in B-14. §12.3.3 attaches the legal paper trail. Modelling this as a separate `plot_sale` aggregate rather than columns on `plot` is what makes cancellation, resale, and accurate historical reporting possible.

### 2. Dependencies
**Depends on:** M-01, M-02, M-05 (sensitive documents), M-06, M-12 (link to the originating lead), B-02, B-03, B-14 (commission resolution).
**Depended on by:** B-05, B-08, B-10, B-11, B-14, B-15.

### 3. Database schema
`plot_sale`, `plot_document` (data model §4).

### 4. Backend APIs
```
POST   /api/v1/plots/{plotId}/sale        → create sale (marks plot SOLD)  [Idempotency-Key]
GET    /api/v1/plots/{plotId}/sale
PATCH  /api/v1/sales/{id}                  → edit buyer/deal details
POST   /api/v1/sales/{id}/cancel           {reason, refundHandling}
POST   /api/v1/sales/{id}/complete         → mark COMPLETED (fully paid)
GET    /api/v1/sales/{id}/documents
POST   /api/v1/sales/{id}/documents        {docType, label?, mediaId}
DELETE /api/v1/sales/documents/{id}
GET    /api/v1/sales/{id}/gov-id           → reveals decrypted ID  [SENSITIVE_VIEW, audited]
POST   /api/v1/sales/{id}/convert-lead     {customerId}   → link an existing lead as buyer
```

**Create-sale request** (single transaction):
```json
{
  "customerId": "…",                 // optional — link the lead
  "buyerName": "…", "buyerMobile": "…", "buyerEmail": "…",
  "buyerGovIdType": "AADHAAR", "buyerGovIdNumber": "…", "buyerGovIdMediaId": "…",
  "purchaseDate": "2026-07-24",
  "dealValue": "4200000",
  "brokerPartnerId": "…",            // or externalBrokerName/Mobile
  "brokerCommissionOverride": null,  // null = resolve from config
  "paymentType": "INSTALMENT",
  "schedule": [ {"label":"Booking","amount":"500000","dueDate":"2026-07-24"},
                {"label":"On agreement","amount":"1500000","dueDate":"2026-09-01"},
                {"label":"On registry","amount":"2200000","dueDate":"2026-12-01"} ],
  "handledBy": "…"
}
```

### 5. Frontend pages
`/builder/plots/{id}` → **Buyer** and **Documents** tabs; `/builder/plots/{id}/sell` (sale wizard).

### 6. Components
`SaleWizard` (Step 1 Buyer · Step 2 Deal terms · Step 3 Payment plan · Step 4 Broker · Step 5 Review), `BuyerForm`, `LeadLinkPicker` ("Is this buyer already a lead?" — searches M-12 and prefills, avoiding double entry), `GovIdInput` (type + masked number + upload; Aadhaar shown as `XXXX XXXX 1234`), `BrokerPicker` (searchable dropdown of `broker_partner` + "Not in the system" free-text fallback per §12.3.2), `CommissionPreview` (**shows the resolved commission live before saving** — the builder must see "₹84,000 to Ramesh Sharma (2% of ₹42L)" before committing), `PaymentPlanBuilder` (add instalment rows, live total vs deal value reconciliation bar), `DocumentSlots` (§12.3.3: four typed slots + Other with label), `SensitiveDocGuard`, `SaleSummaryCard`, `CancelSaleDialog` (reason + refund handling + explicit consequences list).

### 7. Business logic
- **Creating a sale is one transaction** that: inserts `plot_sale`, sets `plot.status = SOLD` and `plot.current_sale_id`, generates `payment_schedule` rows, resolves and inserts the `commission_ledger_entry` (B-14), updates the linked `customer.status = DEAL_CLOSED`, writes outbox events (plot sold notification, booking-confirmation WhatsApp to buyer, commission-due alert), and bumps `org_metrics`. Any failure rolls back all of it — a half-created sale is unrecoverable in practice.
- **`deal_value` may differ from `plot.price`** (negotiation is universal). Both retained; the variance appears in reports.
- **Government ID encrypted at rest**, only last-4 stored in clear. Reveal is a separate permissioned, audited call (§25.2).
- **Broker attribution** at sale time drives everything downstream. Two paths: a registered `broker_partner` (full commission automation) or free-text external broker (§12.3.2 "if not in system") — the latter records the commission amount manually with no ledger automation, and prompts "Add this broker to your network to track commissions automatically."
- **Commission snapshot:** the resolved rate is frozen onto the ledger entry. Changing a broker's rate next month must never retroactively alter last month's commission.
- **Lump sum vs instalment** (§12.3.4): lump sum creates a single `payment_schedule` row due on the purchase date.
- **Cancellation** (§16): sets `status=CANCELLED`, returns the plot to `AVAILABLE`, cancels unpaid schedule rows, marks the commission ledger entry `CANCELLED` (and if commission was already paid, creates a recovery entry rather than silently erasing it), retains all payment records with a reversal path, and moves the deal to Deals History as `Cancelled`. The plot becomes resellable — a new `plot_sale` row, with the old one intact for history.
- **Completion:** when `total_paid >= deal_value`, the sale can be marked `COMPLETED`, which is what moves it into Deals History as a completed deal (§16) and increments the broker's `deals_closed_count`, potentially triggering a tier upgrade (§20.4).
- **Documents (§12.3.3):** four typed slots (Sale Agreement, Registry/Deed, Plot Map, Buyer ID Proof) plus multi-file "Other" with a required label. ID Proof is auto-classified sensitive.

### 8. User flow
```
Grid → tap Plot A-12 (Available) → "Mark as Sold"
  → "Is the buyer an existing lead?" → search "Rajesh" → select → buyer fields prefill
  → deal value ₹42,00,000, purchase date, gov ID + scan
  → payment plan: Booking ₹5L today, ₹15L on agreement, ₹22L on registry
     (bar shows ₹42L / ₹42L ✓)
  → broker: Ramesh Sharma → preview "Commission ₹84,000 (2%)"
  → Review → Confirm
  → plot turns red · buyer gets WhatsApp booking confirmation
  · commission ledger entry created · lead marked Deal Closed
  · allotment letter offered for download (B-11)
```

### 9. Permissions
Create/edit sale: `DATA_EDIT_ALL` + `FINANCIAL_VIEW` (Admin, Manager). Sales Executive **cannot** create a sale — they hand off to a Manager (§18.3: no financial access). Accounts Staff can view sales and record payments but not create or cancel them. Cancel: Admin only. Gov ID reveal: `SENSITIVE_VIEW`. Document upload: `DATA_EDIT_ALL`.

### 10. Edge cases
- Two staff selling the same plot simultaneously → unique partial index on `plot_sale(plot_id) WHERE status <> 'CANCELLED'` makes the second fail cleanly with "This plot was just sold by {user}".
- Schedule total ≠ deal value → warning with the difference; allowed (builders often leave a final amount unscheduled), but flagged on the sale summary.
- Buyer is also an existing lead for a *different* project → linking is allowed; the lead's status becomes Deal Closed with a note naming which project.
- Sale created, then plot size/price edited → the sale's `deal_value` is independent and unaffected.
- Cancellation after ₹20L collected → refund is **not** processed by the platform (we don't move money); the flow requires the builder to record a refund as a negative `payment_record`, and the UI states the outstanding refund clearly.
- Same buyer buying 3 plots → three sales, one buyer identity; a future `buyer` entity is anticipated but v1 stores buyer details per sale (denormalised deliberately — buyer details at time of sale are legally significant and must not change retroactively).
- Broker deactivated after the sale → ledger entry stands; commission still payable.
- Purchase date backdated (data migration of past sales) → allowed up to 10 years back; instalment schedules may be created fully in the past and immediately show as overdue/paid.
- Aadhaar entered with spaces/dashes → normalised; Verhoeff checksum validated (a warning, not a block — builders do enter typos and shouldn't be stopped).
- Uploading the wrong document type → replaceable; the old version is retained with a version number (legal documents should never silently vanish).

### 11. Validation rules
| Field | Rule |
|---|---|
| buyer_name | required, 2–120 |
| buyer_mobile | required, 10 digits |
| buyer_email | optional, valid |
| buyer_gov_id_type | required if number provided |
| buyer_gov_id_number | Aadhaar 12 digits + Verhoeff (warn); PAN `[A-Z]{5}[0-9]{4}[A-Z]` |
| purchase_date | required, ≤ today, ≥ today − 10 years |
| deal_value | required, > 0, ≤ 10,000 crore; if < 50% of plot price → confirm prompt |
| payment_type | required |
| schedule rows | ≥ 1; each amount > 0; due dates ascending; sum ≤ deal_value × 1.0 |
| broker_partner_id | must exist, be ACTIVE, in org |
| external broker | name required if mobile given |
| handled_by | must be an active user |
| documents | per M-05; Sale Agreement + Registry recommended (soft warning if absent at completion) |

### 12. Notifications
- Plot marked Sold → in-app to owner + relevant staff (§22.3).
- Booking confirmation → WhatsApp to buyer (§22.4).
- Broker commission due → in-app + email to owner (§22.3).
- Broker notified of the deal → in-app/WhatsApp to the broker if they have a linked account.
- Sale cancelled → in-app to owner + handling staff.
- Allotment letter generated → in-app (§22.3).

### 13. Future scalability
- **Buyer entity** with a full purchase history across projects (a repeat buyer is a builder's best lead source).
- Co-buyers / joint ownership (very common) — a `sale_buyer` join table.
- e-KYC / DigiLocker integration for Aadhaar verification.
- e-Stamp and registration-appointment integration.
- Booking-to-sale two-step flow (token amount → booking → agreement → registry) as a formal state machine.
- Digital signature on allotment letters.

---

# B-05 · Payment Tracker & Instalment Schedules

**PRD:** §12.3.4

### 1. Why this module exists
§12.3.4 is the financial heart of the builder product. Indian plot sales are almost always instalment-based over 12–36 months, and builders currently track this in notebooks and WhatsApp. Auto-computed balances, due-date tracking, overdue flags and automated reminders directly convert into collected cash — this is the module a builder will pay for.

### 2. Dependencies
**Depends on:** M-01, M-02, M-06 (reminders — critical), M-09, B-03, B-04.
**Depended on by:** B-08, B-10, B-11 (receipts, demand letters), B-13, B-15.

### 3. Database schema
`payment_schedule`, `payment_record`, `payment_allocation` (data model §4).

**The allocation table is the key design decision.** A builder receives ₹3,00,000 against two pending instalments of ₹2,00,000 each. Without allocations you cannot answer "is instalment #2 partially paid?" — and every collection report becomes wrong. One receipt → many allocations.

Receipt numbering (gapless, per org, per financial year):
```sql
CREATE TABLE receipt_sequence (
  org_id UUID, fy VARCHAR(7), next_value BIGINT,
  PRIMARY KEY (org_id, fy)
);
-- allocated under SELECT ... FOR UPDATE inside the payment transaction
```

### 4. Backend APIs
```
GET  /api/v1/sales/{id}/payments                     → records + schedule + summary
POST /api/v1/sales/{id}/payments   [Idempotency-Key] {amount, paidOn, mode, reference, remarks,
                                                      allocations?: [{scheduleId, amount}]}
GET  /api/v1/payments/{id}
POST /api/v1/payments/{id}/reverse {reason}          → contra entry
PATCH /api/v1/payments/{id}/cheque-status {status}   → CLEARED | BOUNCED
GET  /api/v1/sales/{id}/schedule
POST /api/v1/sales/{id}/schedule                     {label, amount, dueDate}
PATCH /api/v1/schedule/{id}                          {amount, dueDate, label}
DELETE /api/v1/schedule/{id}                         (unpaid only)
PATCH /api/v1/schedule/{id}/waive                    {reason}
PATCH /api/v1/schedule/{id}/reminder                 {enabled}
POST  /api/v1/schedule/{id}/send-reminder            → manual WhatsApp (§14.3)
GET   /api/v1/payments/summary?projectId=&from=&to=  → collected, pending, overdue
```

### 5. Frontend pages
Plot detail → **Payments** tab. Also surfaced in B-08 (Financials) and B-13 (Collection Tracker).

### 6. Components
`PaymentSummaryPanel` (§12.3.4: Total Amount · Amount Paid So Far · Balance Remaining, with a progress bar — the single most-looked-at widget in the product), `PaymentTypeToggle` (Lump Sum / Instalment), `ScheduleTable` (instalment #, label, expected amount, due date, status chip, allocated, actions), `AddPaymentDialog` (amount, date, mode, reference, remarks, **auto-allocation preview**), `PaymentHistoryTable` (§12.3.4: Instalment # · Amount · Date · Mode · Reference · Received By · Remarks), `AllocationEditor` (shows how a receipt is split, editable), `OverdueBadge` (days overdue, red), `ChequeStatusChip`, `ReceiptDownloadButton`, `SendReminderButton`, `ReversePaymentDialog`, `ScheduleBuilder` (with quick presets: "12 monthly instalments", "quarterly", "custom").

### 7. Business logic
- **Two ledgers, deliberately separate.** `payment_schedule` = what *should* be paid. `payment_record` = what *was* paid. `payment_allocation` connects them. Merging them (the tempting simplification) makes partial payments, advance payments, and waivers unrepresentable.
- **Auto-allocation, oldest-due-first**, shown as a preview before save and manually overridable. Excess beyond all scheduled instalments becomes an unallocated advance, visible as "Advance: ₹50,000" and consumed by future instalments automatically.
- **Derived figures (§12.3.4):** `amount_paid = SUM(payment_record.amount)` (trigger-maintained), `balance = deal_value − amount_paid`. Never computed ad hoc in the UI.
- **Schedule status:** `PENDING` → `PARTIALLY_PAID` (0 < allocated < expected) → `PAID` (allocated ≥ expected). A nightly job flips `PENDING`/`PARTIALLY_PAID` to `OVERDUE` when `due_date < today`. §12.3.4 requires the "turns red if past due date and not paid" behaviour.
- **Payments are immutable.** A wrong entry is corrected by a reversal (negative amount, `reverses_payment_id`), never an edit or delete. Cash businesses need this — an editable payment log is worthless as evidence in a dispute.
- **Cheque lifecycle:** recorded as `PENDING`, counts toward paid provisionally; marking `BOUNCED` auto-creates a reversal and notifies. Bounced cheques are common enough that ignoring them would corrupt every balance.
- **Receipt generation** (§17.2) fires automatically on each payment via the outbox, producing a numbered PDF attached to the record.
- **Reminders** (§12.3.4 toggle, §22.3): due-date reminder at 09:00 to the builder and (opted-in) to the buyer; overdue reminder at day 3, then weekly.
- **Waiver** for discounts/adjustments — requires a reason, permission, and appears distinctly in reports (never silently reduces the expected total).

### 8. User flow
```
Collection Tracker → "Plot A-12 · Rajesh Kumar · ₹2,00,000 due 15 Aug · 6 days overdue"
  → Record Payment → ₹2,00,000 · 21 Aug · UPI · UTR 1234
  → allocation preview: "Instalment 2 → ₹2,00,000 (fully paid)"
  → Save
  → schedule row turns green · balance updates ₹22L → ₹20L
  · receipt PDF generated · buyer gets WhatsApp confirmation with new balance
  · row leaves the overdue tracker
```

### 9. Permissions
Record payment: `FINANCIAL_RECORD_PAYMENT` (Admin, Manager, Accounts Staff). Reverse: `FINANCIAL_EDIT` (Admin, Accounts). Edit schedule: `FINANCIAL_EDIT`. Waive: Admin only. View: `FINANCIAL_VIEW` — Sales Executive sees **no payment data at all** (§18.3), which also means the plot detail hides the Payments tab for them.

### 10. Edge cases
- Payment exceeding the balance → allowed with a confirmation, recorded as an advance/overpayment and shown as a negative balance with an explicit "Refund due: ₹X" flag.
- Payment before the sale date → warning (token amounts do get paid before paperwork), allowed.
- Deleting an instalment that has allocations → blocked; must reverse the payment first.
- Changing `deal_value` after payments exist → allowed with recomputation of balance and a warning if it drops below `amount_paid`.
- Cheque bounces two months later → reversal dated at the bounce date; the balance and overdue state recompute; a bank-charge line can be added as a separate schedule row.
- Two staff recording the same payment simultaneously → Idempotency-Key + a duplicate-detection warning ("A payment of ₹2,00,000 was recorded 3 minutes ago by Suresh — is this a different payment?").
- Sale cancelled with payments recorded → payments retained; refund tracked as negative records; the plot's history remains complete.
- Instalment due on 31st in a 30-day month → dates are explicit, not offsets; the schedule builder clamps to month-end.
- Buyer pays in cash across 8 small amounts → 8 records, all allocating to one instalment; the schedule shows partial progress.
- Financial-year rollover mid-transaction → receipt sequence keyed by FY computed from `paid_on`, not `now()`.
- Extremely long schedules (60 instalments) → schedule table paginates; the summary stays fixed.

### 11. Validation rules
| Field | Rule |
|---|---|
| amount | required, ≠ 0, |amount| ≤ 100 crore, 2 decimals |
| paid_on | required, ≤ today, ≥ sale date − 90 days |
| mode | required, ∈ {CASH, CHEQUE, BANK_TRANSFER, UPI, DD} |
| reference | required if mode ≠ CASH, 2–120 |
| cheque_status | only if mode = CHEQUE |
| allocations | sum ≤ payment amount; each schedule must belong to this sale |
| schedule.expected_amount | > 0 |
| schedule.due_date | required; ≥ sale date − 90 days |
| schedule labels | ≤ 80 chars |
| waive reason | required, 5–500 |
| reversal reason | required, 5–500 |

### 12. Notifications (§22.3, §22.4)
- Instalment due today → in-app + WhatsApp to builder; WhatsApp to buyer.
- Instalment overdue 3+ days → in-app + WhatsApp to builder; escalating buyer reminders.
- Payment recorded → in-app to owner; **WhatsApp to buyer with the new balance** (§22.4 exact template).
- Cheque bounced → in-app + email to owner and Accounts.
- Receipt generated → in-app with a download link.
- Weekly collection digest → email to owner.

### 13. Future scalability
- **Payment gateway collection links** — send the buyer a UPI/payment link inside the reminder; auto-reconcile on webhook. This is the highest-value follow-on: it closes the loop from reminder to money received.
- Bank statement import + auto-reconciliation by UTR.
- Interest/penalty on overdue instalments (configurable rate) — modelled as auto-generated schedule rows.
- Construction-linked payment plans (milestone-triggered rather than date-triggered).
- Tally/Zoho Books export.
- TDS (1% on property >₹50L) tracking, which builders currently handle manually.

---

# B-06 · Bulk Plot Creation & Upload

**PRD:** §12.4 (extended beyond the PRD's original Excel-only spec — see §1 below)

### 1. Why this module exists
§12.4 gives this its own section for good reason: a builder onboarding a 500-plot colony will not type 500 forms. If this feature is awkward, the builder never finishes setup and never becomes a paying customer. It is the single highest-leverage activation feature on the builder side.

**This module now has two distinct paths, because they serve two distinct situations:**

| Path | For | Why |
|---|---|---|
| **A. Quick Range Create** | A brand-new project — plots don't exist yet, are largely identical (same facing/size/price to start), and follow a simple numbering pattern | No file handling at all. Matches §1.3's "no page should require technical knowledge" principle far better than forcing Excel round-trips for a person creating fresh inventory. This is now the **default, first-offered** option. |
| **B. Excel Bulk Import** (original §12.4 design) | Migrating an existing colony — plots already have varied sizes/prices, some are already sold with real buyer/payment history | Excel is the only sane way to bring in heterogeneous, already-existing data. This remains essential and unchanged — it is not being replaced. |

Both create ordinary `plot` rows through the same service layer; neither bypasses validation, quota checks, or grid placement logic.

### 2. Dependencies
**Depends on:** M-07 (the generic import engine, for Path B only), M-09 (`BULK_UPLOAD_ENABLED` gates Path B only — Path A is available on every plan, see §7), B-02, B-03.
**Depended on by:** B-03 (populates the grid).
> Path B is a thin, plot-specific configuration of M-07. Path A is new, lightweight, and has no file-processing dependency at all.

### 3. Database schema
Path B reuses `import_job`, `import_row` unchanged. Path A needs no new tables — it is a service-layer loop that calls the same `PlotService.create()` used by manual single-plot creation, so every constraint (unique plot number, grid bounds, quota) is enforced exactly once, in one place.

### 4. Backend APIs
```
-- Path A: Quick Range Create
POST /api/v1/projects/{id}/plots/quick-create
     {
       ranges: [
         { prefix: "A", separator: "-", start: 1, end: 75, padWidth: 0 }
         // supports multiple ranges in one call, e.g. a second block:
         // { prefix: "B", separator: "-", start: 1, end: 50, padWidth: 0 }
       ],
       sharedProperties: {
         sizeValue, sizeUnit, facing?, price, isGarden?, isCorner?, isHot?, remarks?
       },
       autoPlace: true   // reuses B-03's grid auto-placement strategy
     }
     → returns a PREVIEW (generated plot numbers + a flag for any that already
       exist) — nothing is written until the caller POSTs the same payload
       with `confirm: true`, or calls the commit endpoint below
POST /api/v1/projects/{id}/plots/quick-create/commit   {sameBodyAsAbove}
     → creates all plots as AVAILABLE, in one chunked transaction, with progress

-- Path B: Excel Bulk Import (unchanged)
GET  /api/v1/projects/{id}/plots/import/template   → .xlsx pre-filled with project context
POST /api/v1/projects/{id}/plots/import            {mediaId, duplicateMode, autoPlace}
     → reuses M-07 job lifecycle for preview/commit
```

### 5. Frontend pages
`/builder/projects/{id}/plots/new-bulk` — a single page offering **both** paths as two tabs or two clearly-labelled cards: "Quick Create (recommended for new plots)" and "Import from Excel (for existing/migrated data)". Reachable from the grid's "Add Plots" button, which is the single entry point for either.

### 6. Components
**Path A:** `RangeCreateForm` — one or more `RangeRow` inputs (prefix, separator, start #, end #, optional zero-padding), a `SharedPropertiesPanel` (the fields applied to every generated plot: size, unit, facing, price, flags), a live `RangePreviewList` (e.g. "This will create 75 plots: A-1, A-2, … A-75" with any collisions against existing plot numbers flagged before commit), `AddAnotherRangeButton` (for multi-block projects), `QuickCreateSummary` (post-commit: "75 plots created").
**Path B:** unchanged — reuses M-07's `ImportWizard`, `PlotTemplateCard`, `DuplicateModeSelector`, `AutoPlaceToggle`, `GridPreview`, `PlotImportSummary`.

### 7. Business logic

**Path A — Quick Range Create:**
- **Range parsing:** each range is `prefix (optional) + separator (optional) + start + end + padWidth (optional)`. `{prefix:"A", separator:"-", start:1, end:75}` generates `A-1 … A-75`; `{start:1, end:200}` with no prefix generates `1 … 200`; `padWidth:3` generates `A-001 … A-075`. Multiple ranges in one submission cover multi-block layouts (`A1–A75`, then `B1–B50`) without two separate operations.
- **Preview before commit, always.** The generated list of plot numbers is shown in full before anything is written — collisions with existing plot numbers in the project are flagged individually (skip / rename inline), exactly like Path B's preview step. A typo in `end` should never silently create 700 plots instead of 75.
- **Shared properties apply to every plot in the batch**, written as their individual starting values — **not** a link back to a template. Editing one plot afterward (B-03) never affects the others. `status` is always `AVAILABLE` for Path A; RESERVED/SOLD are set individually afterward.
- **Grid auto-placement:** since the numbers are sequential by construction, this is the easy case for B-03's auto-placement (row-major fill, or block-aware if the prefix maps cleanly to a grid row) — offered as a toggle, on by default.
- **Quota pre-check** (§23.1 plots-per-project) before generation, with the exact count and an upgrade prompt if the range would exceed it — same rule as Path B.
- **No entitlement gate.** Unlike Excel import (Pro/Premium only per §23.1's `BULK_UPLOAD_ENABLED`), Quick Range Create has no file-processing cost and no reason to be paywalled — it's available on every plan including Free, subject only to the plot-count quota itself. This is a deliberate product decision: the feature that removes the most onboarding friction for a brand-new (likely Free-plan) builder shouldn't be the one thing locked behind an upgrade.
- **Chunked commit** (200/transaction) with progress, same pattern as Path B, for very large ranges.

**Path B — Excel Bulk Import:** unchanged from the original design — template download, streaming parse, per-row validation, preview with inline correction, sold-row-as-complete-sale handling for migrated data, duplicate modes, auto-placement fallback for non-range-parseable numbering.

### 8. User flow
```
Project (0 plots) → "Add Plots"
  → two options shown: [Quick Create] [Import from Excel]

Quick Create:
  → Range: prefix "A", 1 to 75 → shared properties: 1200 sqft, East facing, ₹42,00,000
  → preview: "A-1 … A-75, 75 plots, no collisions" → Create
  → progress → "75 plots created" → grid renders, auto-placed
  → later: edit A-12 individually to correct its facing without touching the others

Import from Excel (unchanged):
  → Download Template → fill 500 rows offline → Upload
  → Preview: 487 valid · 13 invalid → fix 9 inline · skip 4 → Commit
  → "487 plots imported" → grid renders
```

### 9. Permissions
Both paths: `PLOT_CREATE` + project scope (Admin, Manager). Path B additionally requires `IMPORT_DATA` and the Pro/Premium entitlement; Path A requires neither — only the plot-count quota applies.

### 10. Edge cases
**Path A specific:**
- `end < start` in a range → validation error before any preview is generated.
- Range spanning an implausibly large count (e.g. 1 to 50,000) → same cap and "split it up" guidance as Path B's row cap.
- Two submitted ranges overlapping each other (`A1–A50` and `A25–A75`) → flagged in the preview as internal collisions, not just against existing plots.
- Range partially colliding with existing plot numbers → collision list shown per-number with a per-item skip/rename choice, not an all-or-nothing failure.
- Grid too small for the generated count → auto-placement fills as many as fit and reports the rest as unplaced (same tray behaviour as B-03), never silently drops them.
- Zero-padding requested inconsistently across multiple ranges → each range's padding is independent and previewed as such.

**Path B:** all edge cases carried over unchanged from the original spec (sold-row imports, leading zeros, mixed units, broker-name matching, 10,000-row cap, etc.) — see full list below.
- Import that would exceed plots-per-project → blocked pre-upload with the exact overage and upgrade CTA.
- `status=SOLD` without buyer details → row invalid with a specific reason.
- `total_paid` > `deal_value` → row invalid.
- Duplicate grid coordinates within the file → flagged; the second occurrence becomes unplaced.
- Plot numbers with leading zeros (`007`) → Excel strips them; the template forces text formatting on that column and the parser preserves the raw string.
- Mixed units across rows → fully supported.
- A project already containing plots → import appends; `UPDATE_EXISTING` mode updates matches.
- 10,000-row file → capped, guidance to split by block.
- Broker names in the file that don't match any `broker_partner` → recorded as external broker text, with a post-import prompt: "6 broker names weren't found in your network. Add them?"

### 11. Validation rules
**Path A:** `start` ≥ 1; `end` ≥ `start`; `(end − start + 1)` across all ranges combined ≤ plots-per-project quota remaining and ≤ an absolute cap (e.g. 5,000 per submission, mirroring Path B's row cap); `prefix` ≤ 10 chars if given; `padWidth` 0–6; `sharedProperties.sizeValue` > 0; `sharedProperties.price` ≥ 0; generated numbers, after normalisation, must be unique within the project (checked in the same way as manual/Path-B creation).
**Path B:** unchanged — per M-07's plot rules, plus sold-row conditionals (`buyer_name`, `buyer_mobile`, `purchase_date`, `deal_value` required when `status=SOLD`; `reserved_for` required when `status=RESERVED`; `grid_row`/`grid_col` within project bounds and unoccupied).

### 12. Notifications
Path A: creation complete → in-app with the count ("75 plots created"). Path B: unchanged (import complete/failed → in-app with counts and error-report link; email for imports exceeding 2 minutes).

### 13. Future scalability
- Save a range+shared-properties combination as a reusable **project template** for builders who add plots in batches over time (e.g. releasing a colony phase by phase).
- **Column mapping UI** for Path B, so builders can upload their existing spreadsheet unchanged — the single biggest reduction in *migration* friction.
- Import layout PDFs and auto-extract plot numbers via OCR (feeds either path).
- Scheduled re-import from a shared Drive sheet for builders who keep working in Excel.
- Bulk document import (zip of PDFs matched to plot numbers).

---

# B-07 · Builder Customer / Lead Manager

**PRD:** §13.1, §13.2, §13.3

### 1. Why this module exists
§13 is the builder's sales pipeline: leads interested in projects and specific plots, assigned to staff, with source attribution (including which broker brought them) and a complete interaction history. The two builder-specific additions over the broker version — **staff assignment** and **broker source attribution** — are what make the Admin Panel (B-12) and Broker Management (B-14) meaningful.

### 2. Dependencies
**Depends on:** M-12 (core), M-02 (own-leads-only scoping), M-06, M-11, B-02, B-03, B-12 (staff), B-14 (broker source).
**Depended on by:** B-04 (lead → buyer), B-10, B-13, B-15.

### 3. Database schema
`customer` + `interaction` (M-12), using the builder-specific columns: `interested_project_id`, `interested_plot_id`, `assigned_to`, `source_broker_id`.

### 4. Backend APIs
M-12's endpoints, plus:
```
GET   /api/v1/builder/customers?projectId=&plotId=&assignedTo=&source=&brokerId=&…
PATCH /api/v1/customers/{id}/assign      {userId}
POST  /api/v1/customers/bulk-assign      {customerIds[], userId}
GET   /api/v1/builder/customers/unassigned
GET   /api/v1/builder/customers/funnel?projectId=   → counts per stage (feeds §21.1)
```

### 5. Frontend pages
`/builder/customers`, `/builder/customers/{id}`, `/builder/customers/new`.

### 6. Components
M-12's set, plus: `AssignStaffDropdown` (avatar + name + current lead count — assigning to an already-overloaded exec is a real problem), `ProjectPlotPicker` (project → plot cascade, plot list filtered to Available/Reserved), `BrokerSourcePicker` (searchable `broker_partner` list, shown only when source = BROKER), `UnassignedLeadsBanner` (Admin/Manager view: "12 leads unassigned"), `BulkAssignToolbar`, `LeadFunnelMini` (stage counts on the list header), `StaffFilterTabs` (All / Mine / Unassigned).

### 7. Business logic
- **All §13.1 columns implemented:** Important star · Name · Contact · Budget · Project Interested In · Plot/Unit Interested · Status · Assigned To · Follow-up Date (red if overdue) · Source · Remarks · Actions.
- **Assignment rules:** unassigned leads visible to Admin/Manager; assigning notifies the assignee (§22.3); Sales Executives see only `assigned_to = me` (§18.3 "Own leads only"); reassignment is logged as an interaction so the history explains the handover.
- **Broker source attribution:** when `source = BROKER`, `source_broker_id` is required. This links the lead to the broker's performance record even before a deal closes, which is what makes B-14's performance stats honest (deals closed *and* leads brought).
- **Plot interest** is soft — a lead may be interested in a plot that later sells to someone else. When that happens the system flags the lead ("Plot A-12 is no longer available") and prompts to suggest alternatives with similar size/facing/price. This is a small feature with outsized sales value.
- **Lead → buyer conversion** (B-04) prefills the sale form from the lead and sets status `DEAL_CLOSED` on completion.
- **Pipeline statuses** identical to §6.3 with the same colour coding.
- Funnel counts (Interested → Visit → Closed) computed per project for §21.1.

### 8. User flow
```
Walk-in at the site office → Sales Exec taps "+ Add Lead" on mobile
  → name, mobile, budget ₹35–45L, Project: Green Valley, Plot: A-12
  → source: Walk-in · status: Interested · follow-up: tomorrow
  → auto-assigned to the creating exec
  → tomorrow 09:00: WhatsApp + in-app reminder
  → logs a call: "Wants East facing, will visit Sunday" · next follow-up Sunday
  → status → Site Visit Scheduled → calendar event appears
  → after visit: status → Site Visit Done
  → Manager converts to sale (B-04)
```

### 9. Permissions
Create: `DATA_CREATE` (all roles except View Only). Edit: `DATA_EDIT_ALL` or `DATA_EDIT_OWN`. Delete: Admin only. Assign: `LEAD_ASSIGN` (Admin, Manager). View: `DATA_VIEW_ALL` or own-only. Accounts Staff: no lead access (§18.3).

### 10. Edge cases
All M-12 edge cases, plus:
- Lead interested in a plot that gets sold → flagged with alternatives suggested.
- Lead assigned to staff who are then deactivated → surfaces in "Unassigned" with a note; Admin notified.
- Same lead brought by two different brokers (genuinely disputed in practice) → one `source_broker_id`, but the interaction log records the dispute; commission attribution follows the sale, not the lead.
- Sales Exec creates a lead then it's reassigned → they keep read access to their own logged interactions but lose the live lead.
- Bulk assign 200 leads → chunked, progress shown, one notification per assignee (digested).
- Lead with no project selected → allowed (early-stage enquiries); appears under "No project" filter.
- Project deleted with leads attached → leads retained, `interested_project_id` nulled, flagged for review.

### 11. Validation rules
M-12's rules, plus: `interested_plot_id` must belong to `interested_project_id`; `source_broker_id` required when `source = BROKER` and must be an ACTIVE partner; `assigned_to` must be an active user with lead-handling capability (not Accounts Staff or View Only).

### 12. Notifications (§22.3)
- New lead added → in-app to owner + assigned staff.
- Lead assigned/reassigned → in-app + email to the new assignee.
- Follow-up due today → in-app + WhatsApp to the assignee (not the owner — a builder with 5 execs doesn't want 60 reminders).
- Follow-up overdue → in-app + WhatsApp to assignee, escalating to the Manager after 3 days.
- Lead's interested plot sold → in-app to the assignee.

### 13. Future scalability
- Auto lead capture (website, Facebook Lead Ads, IndiaMART, 99acres/MagicBricks) via a per-org webhook — the top v2 request from builders.
- Round-robin / load-balanced auto-assignment.
- Lead scoring and prioritised call lists.
- Call recording integration (Exotel/Knowlarity) attached to interactions.
- Site-visit check-in with geolocation to verify visits actually happened — builders care about this for staff accountability.
- WhatsApp two-way inbox per lead.

---

# B-08 · Financials / Account Manager

**PRD:** §14.1, §14.2, §14.3, §14.4

### 1. Why this module exists
§14 is the builder's money view: total revenue, monthly/yearly cuts, pending collections, overdue instalments, and commission paid out — plus the operational sub-tab for chasing money (§14.3). B-05 records payments at the plot level; B-08 aggregates them across the whole business, which is what the owner and the accountant actually look at.

### 2. Dependencies
**Depends on:** M-01, M-02 (`FINANCIAL_VIEW`), M-06, M-10 (export), B-02, B-04, B-05, B-14 (commission paid).
**Depended on by:** B-01, B-11, B-13, B-15.

### 3. Database schema
No owned tables — a read/aggregation module over `payment_record`, `payment_schedule`, `payment_allocation`, `plot_sale`, `commission_payment`.

Supporting rollup for fast summary cards:
```sql
CREATE MATERIALIZED VIEW mv_org_revenue_monthly AS
SELECT org_id, project_id, date_trunc('month', paid_on) AS month,
       SUM(amount) AS collected, COUNT(*) AS payment_count
FROM payment_record WHERE deleted_at IS NULL
GROUP BY 1,2,3;
CREATE UNIQUE INDEX ON mv_org_revenue_monthly(org_id, project_id, month);
-- REFRESH CONCURRENTLY every 15 min; current month also computed live
```

### 4. Backend APIs
```
GET /api/v1/builder/financials/summary?projectId=&from=&to=   → the six §14.1 cards
GET /api/v1/builder/financials/payments?projectId=&from=&to=&mode=&status=&cursor=   → §14.2
GET /api/v1/builder/financials/pending?projectId=&overdueOnly=&cursor=              → §14.3
GET /api/v1/builder/financials/commission-paid?from=&to=&brokerId=
POST /api/v1/builder/financials/export   {filters, format}
GET /api/v1/builder/financials/revenue-trend?months=12
```

**Summary response (§14.1):**
```json
{
  "totalRevenueAllTime": "182400000",
  "revenueThisMonth": "8450000",
  "revenueThisYear": "62300000",
  "pendingCollections": "94200000",
  "overdueInstalments": {"amount": "3800000", "count": 27},
  "totalBrokerCommissionPaid": "4120000"
}
```

### 5. Frontend pages
`/builder/financials` with tabs: **Overview** · **Payments** (§14.2) · **Pending & Overdue** (§14.3) · **Commission Paid**.

### 6. Components
`FinancialSummaryCards` (six cards per §14.1), `RevenueTrendChart` (12-month bar), `PaymentRecordsTable` (§14.2 columns: Date · Project · Plot Number · Buyer Name · Instalment # · Amount · Mode · Reference · Recorded By · Actions), `PendingInstalmentsTable` (§14.3 columns: Buyer · Project · Plot · Amount Due · Due Date · **Days Overdue** (red if positive) · Status · Actions: Record Payment / Send WhatsApp Reminder), `FinancialFilterBar` (§14.4: project, date range, payment mode, status), `ExportButton`, `PaymentModeBreakdown` (pie — cash vs digital, which builders watch closely), `CollectionEfficiencyGauge` (collected ÷ due — a metric builders don't currently have and immediately value), `BulkReminderDialog` ("Send reminders to all 27 overdue buyers" — with a confirmation showing the exact count and cost).

### 7. Business logic
- **All six §14.1 cards** computed exactly: all-time revenue (`SUM(payment_record.amount)` including reversals), this month, this year, pending collections (`SUM(plot_sale.balance_due)` for non-cancelled sales), overdue instalments (`SUM(expected − allocated)` for schedule rows past due), total broker commission paid (`SUM(commission_payment.amount)`).
- **Pending vs Overdue are distinct:** pending = not yet due; overdue = past due date and unpaid. §14.3 shows both with `days_overdue = today − due_date`, negative values suppressed.
- **Cash-vs-digital** breakdown by mode — genuinely useful, and the data is already there.
- **Bulk reminders** from the overdue tab: select rows → send WhatsApp to all, rate-limited, deduplicated (max one per buyer per 24h), with a preview of the exact message.
- **Reversals included** in all sums so a bounced cheque immediately reduces revenue.
- **Project filter** cascades to every card and table (§14.4).
- **Collection efficiency** = collected ÷ (collected + overdue) for the period — a leading indicator of cash-flow trouble.
- Every figure is drill-through: tapping a card opens the filtered table behind it.

### 8. User flow
```
Financials → Overview: "Overdue ₹38,00,000 · 27 instalments" (red)
  → tap → Pending & Overdue tab, filtered to overdue
  → sorted by days overdue (worst first)
  → select all → "Send WhatsApp Reminder to 27 buyers" → preview → confirm
  → later: one buyer pays → Record Payment inline → row disappears → cards update
```

### 9. Permissions
`FINANCIAL_VIEW` required for the whole module — Admin, Manager, Accounts Staff (§18.3). **Sales Executive and View Only cannot access it at all**; the sidebar link is not rendered and the route 403s. Recording payments requires `FINANCIAL_RECORD_PAYMENT`. Export requires `EXPORT_DATA` + entitlement. Project-scoped staff see only their projects' figures, with a note explaining the scope.

### 10. Edge cases
- Org with no sales → clear empty state, not six zeroes with no explanation.
- Reversals making a month's revenue negative → displayed as negative with an explanatory tooltip, never hidden.
- Payment recorded against a cancelled sale → excluded from revenue, shown in a separate "Cancelled deals" line so the numbers reconcile.
- Project-scoped staff → totals reflect scope; an explicit "Showing 2 of 5 projects" banner prevents a Manager from reporting wrong numbers upward.
- Very large sums → Indian abbreviated format (₹18.24 Cr) with the exact figure on hover/tap.
- Date range spanning a financial-year boundary → both calendar and Indian FY (Apr–Mar) views available; Indian builders think in FY.
- Materialised view stale by up to 15 minutes → the current month is always computed live, so the number a builder checks after recording a payment is immediately correct.
- Timezone → all period boundaries in IST.
- 50,000 payment records → cursor pagination, indexed filters, statement timeout guard.
- Bulk reminder to buyers who opted out → skipped with a count shown ("24 sent, 3 skipped — opted out").

### 11. Validation rules
Read/aggregate module. Filter validation: `from ≤ to`, range ≤ 5 years, `projectId` in scope, `mode`/`status` ∈ enum. Bulk reminder: max 200 recipients per action.

### 12. Notifications (§22.3)
- Overdue instalment alerts (originated by B-05, surfaced here).
- Weekly financial digest → email to owner + Accounts: collected, pending, overdue, top defaulters.
- Monthly revenue summary → email.
- Broker commission due → in-app + email.
- Bulk reminder completed → in-app with sent/skipped/failed counts.

### 13. Future scalability
- Expense tracking (land cost, development, marketing, salaries) → true P&L per project. The most-requested extension and a natural upsell.
- Bank reconciliation via statement import.
- Tally / Zoho Books / QuickBooks export.
- GST reporting and TDS tracking.
- Cash-flow forecasting from the schedule (we already know exactly what's due when — a 12-month projected-collections chart is nearly free).
- Multi-currency for NRI buyers.
- Read replica for financial reporting.

---

# B-09 · Builder Calendar

**PRD:** §15

### 1. Why this module exists
§15 requires the same calendar as the broker's, plus two builder-specific realities: **instalment due dates appear automatically**, and **staff calendars are visible to the builder admin**. A builder running five sales executives needs to see the whole team's week in one view.

### 2. Dependencies
**Depends on:** M-11 (engine), M-02 (staff visibility), B-05 (instalment due dates), B-07 (leads), B-12 (staff).
**Depended on by:** B-13.

### 3. Database schema
`calendar_event` (M-11). Builder usage adds `event_type = INSTALMENT_DUE` projections keyed on `payment_schedule.id`, and `assigned_to` for staff filtering.

### 4. Backend APIs
M-11's endpoints, plus:
```
GET /api/v1/builder/calendar?from=&to=&assignedTo=&projectId=&types=
GET /api/v1/builder/calendar/staff-summary?from=&to=   → per-staff event counts
```

### 5. Frontend pages
`/builder/calendar`.

### 6. Components
M-11's set, plus `StaffCalendarFilter` (dropdown: All Staff / individual / Unassigned — §15), `ProjectCalendarFilter`, `StaffWorkloadStrip` (per-staff event counts for the visible range — shows at a glance who is overloaded), `InstalmentEventCard` (amount, plot, buyer, with inline "Record Payment" and "Send Reminder" actions — turning the calendar into a working surface rather than a display).

### 7. Business logic
- **Four auto-projected sources (§15):** customer follow-up dates → FOLLOW_UP; `SITE_VISIT_SCHEDULED` leads → SITE_VISIT; `payment_schedule.due_date` → INSTALMENT_DUE; manual entries → MANUAL_MEETING / IMPORTANT_DATE.
- **Colour coding by type** (§15) with a legend; each type also carries a distinct icon for accessibility.
- **Staff visibility (§15):** Admin/Manager see all events with a staff filter; Sales Executives see only their own assigned events; Accounts Staff see only instalment-due events.
- Instalment events carry the amount in the label, because "₹2,00,000 due — Plot A-12" is the useful string, not "Instalment due".
- Project-scoped staff see events only from their scoped projects.
- Mobile defaults to the agenda list; the month grid is one tap away.

### 8. User flow
Calendar → filter "All Staff" → 15 Aug shows 4 follow-ups (2 Suresh, 2 Priya) and 6 instalments due → tap an instalment → "Record Payment" inline → done without leaving the calendar.

### 9. Permissions
View all: `DATA_VIEW_ALL`. View own: `DATA_VIEW_OWN`. Instalment events additionally require `FINANCIAL_VIEW` — a Sales Executive's calendar shows follow-ups and visits, never money.

### 10. Edge cases
All M-11 edge cases, plus:
- A day with 80 instalments due → grouped as one "42 instalments due · ₹56,00,000" entry expanding to a list.
- Instalment paid → the projection is removed on payment, same day.
- Staff deactivated → their events surface as "Unassigned" for reassignment.
- Project-scoped staff → events from out-of-scope projects are invisible, not greyed out.
- Rescheduling an instalment date from the calendar → prompts that it changes the payment schedule, with a reason captured.

### 11. Validation rules
Per M-11, plus `assignedTo` must be an active org user, and instalment events cannot be manually created or deleted (they follow the schedule).

### 12. Notifications (§15, §22.3)
In-app + WhatsApp on the event day and one day before; site visits also 1 hour before. Instalment reminders route to the builder and, separately, to the buyer.

### 13. Future scalability
Google/Outlook sync, iCal feed, team scheduling view, drag-to-reschedule, geo check-in for site visits, capacity-aware visit booking.

---

# B-10 · Builder Deals History

**PRD:** §16

### 1. Why this module exists
§16 is the permanent archive: every completed and cancelled deal with buyer, financials, broker, and handling staff. It answers the questions that drive a builder's decisions — which staff close, which brokers deliver, how much is still outstanding on completed deals — and is the record of last resort in a dispute.

### 2. Dependencies
**Depends on:** M-02, M-10 (export), B-04, B-05, B-07, B-12, B-14.
**Depended on by:** B-15 (analytics source).

### 3. Database schema
No owned tables — a read view over `plot_sale` where `status ∈ {COMPLETED, CANCELLED}`, joined to plot, project, customer, broker, staff and payment aggregates.

```sql
CREATE INDEX ix_sale_archive ON plot_sale(org_id, status, COALESCE(cancelled_at, purchase_date) DESC)
  WHERE status IN ('COMPLETED','CANCELLED') AND deleted_at IS NULL;
```

### 4. Backend APIs
```
GET /api/v1/builder/deals?status=&projectId=&brokerId=&staffId=&from=&to=&search=&cursor=
GET /api/v1/builder/deals/{saleId}          → full detail
POST /api/v1/builder/deals/export           {filters, format}
GET /api/v1/builder/deals/summary?from=&to= → counts + values
```

### 5. Frontend pages
`/builder/deals`, `/builder/deals/{id}`.

### 6. Components
`DealsHistoryTable` (all §16 columns: Date · Project · Plot Number · Plot Size · Buyer Name · Buyer Contact · Deal Value · Total Collected · Balance · Broker · Broker Commission · Deal Status · Staff Handled By · Actions), `DealFilterBar` (§16: project, date range, broker, deal status, staff member), `DealStatusBadge` (Completed green / Cancelled red), `DealDetailView` (§16 "full detail view includes all payment history, documents, follow-up timeline"), `DealTimeline` (lead created → visits → booking → each payment → completion, as a vertical timeline — this single view answers almost every dispute), `ExportButton`, `DealsSummaryStrip`.

### 7. Business logic
- **Entry criteria (§16):** a sale enters history when `status = COMPLETED` (fully paid) or `CANCELLED`. Active sales with outstanding balance stay in the live modules.
- **Balance on completed deals** is normally zero but may be non-zero if a deal was force-completed with a waiver — shown explicitly, never hidden.
- **Cancelled deals** retain all payment records so the refund position is visible; the plot itself is back in inventory and possibly resold — the detail view links to the subsequent sale so the plot's full lineage is traceable.
- **Detail view assembles** buyer info, plot/project info, the full payment history with receipts, all documents, the lead's interaction timeline, commission ledger entry, and every audit event.
- Filters and export exactly per §16.
- Deals history is **read-only**. Corrections happen in the source modules and flow through, which keeps a single source of truth.

### 8. User flow
Deals History → filter: Project = Green Valley, Broker = Ramesh, 2026 → 14 deals, ₹5.8 Cr → tap one → full timeline from first enquiry to final payment → download the agreement → export the filtered list to Excel for the accountant.

### 9. Permissions
View: `DATA_VIEW_ALL` (Admin, Manager, View Only). Financial columns (Deal Value, Collected, Balance, Commission) require `FINANCIAL_VIEW` — otherwise those columns are omitted, not blanked. Sales Executive sees only deals they handled (`handled_by = me`). Export requires `EXPORT_DATA` + entitlement.

### 10. Edge cases
- A plot sold, cancelled, and resold → two history entries, cross-linked, with the plot's full lineage.
- Deal completed but documents missing → a "Documents incomplete" flag; the deal is still valid.
- Broker deleted/deactivated after the deal → name retained via the ledger snapshot; the link shows an "Inactive" marker.
- Staff removed → `handled_by` retained; the name renders with a "(former staff)" note.
- Deals migrated via bulk upload → marked `Migrated` in the source column so historical analytics can exclude them.
- Very old deals (10 years) → same treatment; date filters default to the last 2 years to keep the first load fast.
- Cancelled deal with a refund still pending → prominently flagged with the outstanding refund amount.

### 11. Validation rules
Read-only. Filters validated: date range ≤ 10 years, `from ≤ to`, IDs in scope, status ∈ {COMPLETED, CANCELLED}.

### 12. Notifications
None originated. Deal completion is notified by B-04/B-05.

### 13. Future scalability
- Post-sale customer service tracking (possession, complaints, maintenance).
- Referral tracking — which past buyers brought new leads (a builder's cheapest lead source).
- Resale/transfer registry when a buyer sells on before registry.
- Cohort analysis: time-to-close, deal-value trends by quarter.

---

# B-11 · Reports & Legal Document Generation

**PRD:** §17.1, §17.2

### 1. Why this module exists
Two distinct capabilities under one PRD section. §17.1 lists nine builder reports. §17.2 is more interesting: **auto-generated legal documents** — allotment letters, payment receipts, demand letters — customisable from the Admin Panel. Builders currently produce these in Word with copy-paste, which is slow and error-prone. Generating them from live data is a high-visibility, high-trust feature.

### 2. Dependencies
**Depends on:** M-05 (PDF storage), M-09 (`LEGAL_DOCS` tier: Free ❌ / Pro Basic / Premium Full), M-10 (report engine), M-14 (system templates), B-02, B-04, B-05, B-12, B-14.
**Depended on by:** B-05 (receipts), B-13 (demand letters).

### 3. Database schema
`document_template`, `generated_document` (data model §9), plus:
```sql
CREATE TABLE document_number_sequence (
  org_id UUID, doc_type VARCHAR(30), fy VARCHAR(7), next_value BIGINT,
  PRIMARY KEY (org_id, doc_type, fy)
);
```

### 4. Backend APIs
```
-- Reports (§17.1) — via M-10
GET  /api/v1/reports?profile=BUILDER
POST /api/v1/reports/{code}/preview
POST /api/v1/reports/{code}/export

-- Legal documents (§17.2)
GET  /api/v1/documents/templates                      → org templates + system defaults
POST /api/v1/documents/templates                      → clone a system default for editing
PATCH /api/v1/documents/templates/{id}
POST /api/v1/documents/templates/{id}/preview {sampleEntityId} → rendered HTML preview
POST /api/v1/documents/templates/{id}/activate
POST /api/v1/documents/generate  {docType, entityId, templateId?, language}  [Idempotency-Key]
GET  /api/v1/documents/{id}                            → metadata
GET  /api/v1/documents/{id}/download                   → PDF
GET  /api/v1/documents?entityType=&entityId=           → all documents for an entity
POST /api/v1/documents/bulk-generate {docType, entityIds[]}  → e.g. demand letters for all overdue
```

### 5. Frontend pages
`/builder/reports` (catalog), `/builder/reports/{code}` (viewer), `/builder/documents/templates`, `/builder/documents/templates/{id}/edit`.

### 6. Components
`ReportCatalog` (nine §17.1 report cards), `ReportViewer` (M-10), `TemplateList`, `TemplateEditor` (rich text with a **variable palette** — click to insert `{{buyer.name}}`, `{{plot.number}}`; logo upload; header/footer; live preview against a real record), `VariablePalette` (grouped: Builder · Project · Plot · Buyer · Payment · Dates), `DocumentPreviewPane`, `GenerateDocumentButton` (on plot, sale, and payment views), `DocumentList` (per entity, with version history), `BulkGenerateDialog` ("Generate demand letters for 27 overdue buyers" → single merged PDF or a ZIP), `LanguageSelector` (English / Hindi document).

### 7. Business logic

**Reports (§17.1) — all nine, as M-10 definitions:**
| Report | Key filters |
|---|---|
| Project Summary | project, status, date |
| Plot Inventory | project, status, facing, size range |
| Sales | project, date range, broker |
| Collection | project, date range, payment mode |
| Pending Collections | project, overdue only |
| Broker Commission | broker, project, date range |
| Lead / Customer | project, status, source, staff |
| Follow-up Due | staff, date |
| Staff Activity | staff, date range |

**Legal documents (§17.2):**
- **Allotment Letter** — buyer name, plot details, size, price, payment terms table, project details, builder letterhead, terms and conditions, signature block.
- **Payment Receipt** — receipt number, date, buyer, plot, amount in figures **and Indian words** ("Two Lakh Rupees Only" — legally expected on Indian receipts), mode, reference, total paid to date, balance remaining.
- **Demand Letter** — buyer, plot, overdue instalment(s), amount due, days overdue, due date, payment instructions, builder contact.
- **Booking Confirmation** — mirrors the §22.4 WhatsApp confirmation as a formal document.

- **Template sandbox:** a restricted expression language with an allowlisted variable set. No arbitrary code, no loops beyond the provided collections (`{{#each instalments}}`), output HTML-escaped. A template referencing an unknown variable cannot be activated.
- **Snapshot on generation:** the rendered data is stored as JSONB on `generated_document`. Reprinting a 2026 receipt in 2029 reproduces the 2026 figures even if the template changed and the sale was amended.
- **Document numbering** is gapless per org, per type, per financial year.
- **Bilingual output** (§24.5 spirit): templates exist per language; the builder chooses at generation, defaulting to the buyer's preference.
- **Auto-generation:** a payment receipt is generated automatically on every `payment_record` insert via the outbox. Allotment letters and demand letters are on-demand or bulk.
- **Bulk demand letters** from the overdue list — one action producing 27 personalised PDFs is the kind of thing that replaces an afternoon of work.
- **Entitlement (§23.1):** Free = none; Pro = Basic (receipts + allotment letters, system templates only, no customisation); Premium = Full (all types + custom templates + bulk generation).

### 8. User flow
```
Admin Panel → Document Templates → Allotment Letter → Edit
  → upload letterhead, adjust terms, insert {{plot.number}}, {{payment.schedule}}
  → Preview against a real sale → looks right → Activate
Later: Plot A-12 → Buyer tab → "Generate Allotment Letter"
  → language: Hindi → generated in ~2s → download PDF → print/WhatsApp to buyer
Monthly: Financials → Overdue (27) → "Generate Demand Letters"
  → single ZIP of 27 personalised PDFs
```

### 9. Permissions
Reports: per M-10 definitions — Sales Exec own-scoped only, Accounts financial only, View Only read. Template editing: `DOCUMENT_TEMPLATE_EDIT` (Admin only). Document generation: `DOCUMENT_GENERATE` (Admin, Manager; Accounts Staff limited to receipts). Sensitive-field inclusion requires `SENSITIVE_VIEW`.

### 10. Edge cases
- Template with an unresolvable variable → activation blocked with the offending variable named; never generate "Dear {{buyer.name}}".
- Missing data (no buyer email) → optional variables render as blank; required ones block generation with a clear list of what's missing.
- Very long payment schedules → the document paginates with repeated table headers.
- Devanagari in PDF → the renderer must embed a Devanagari-capable font; this is an explicit test case (a common failure mode is boxes or missing conjuncts).
- Amount in words for large figures → correct Indian convention (lakh/crore, not million/billion).
- Bulk generation of 500 documents → async job, progress, ZIP delivered on completion.
- Template edited after documents were generated → old documents unchanged (snapshot + template version).
- Org with no logo → a text-based letterhead fallback rather than a broken image.
- Downgrade from Premium to Pro with custom templates → templates preserved but deactivated, system defaults restored, data never destroyed.
- Concurrent generation of the same receipt → idempotency key returns the existing document.

### 11. Validation rules
- Template name required, 2–120; unique per org per type per language.
- `body_html` ≤ 200KB; sanitised (no `<script>`, no external resource loads).
- Variables must be in the allowlist for that document type.
- Generation requires the entity to exist, be in scope, and be in a valid state (no allotment letter for an unsold plot).
- Demand letters require at least one overdue instalment.
- Bulk generation ≤ 500 entities per job.

### 12. Notifications (§22.3)
- Document generated → in-app with download link ("PDF ready for download").
- Bulk generation complete → in-app + email with the ZIP link.
- Generation failed → in-app with the reason.
- Template activated → in-app to Admin.

### 13. Future scalability
- Digital signature / e-sign integration (Aadhaar eSign, DocuSign).
- Direct WhatsApp/email delivery of the generated document to the buyer.
- More document types: possession letter, NOC, cancellation letter, transfer deed, maintenance agreement.
- Template marketplace of legally-reviewed regional templates.
- Multi-page brochure generation from project data.

---

# B-12 · Admin Panel (Team & Roles)

**PRD:** §18.1, §18.2, §18.3

### 1. Why this module exists
§18 is what separates a builder account from a broker account. A colony developer has sales staff, an accountant, and site managers who must see different things. The permission matrix in §18.3 is a product commitment: an Accounts Staff member sees financials but not the lead pipeline; a Sales Executive sees their own leads and no money at all. Without this, the builder shares one login — and then never trusts the platform with real data.

### 2. Dependencies
**Depends on:** M-01, M-02 (the engine this configures), M-06 (invites), M-09 (team-member quota), B-02 (project scoping).
**Depended on by:** B-05, B-07, B-08, B-09, B-13, B-15 (staff attribution everywhere).

### 3. Database schema
`app_user`, `role`, `role_permission`, `user_project_access` (data model §1), plus:
```sql
CREATE TABLE staff_invite (
  id UUID PK, org_id UUID NOT NULL, app_user_id UUID NOT NULL,
  token_hash TEXT NOT NULL, channel VARCHAR(10),   -- SMS | EMAIL
  sent_at TIMESTAMPTZ, expires_at TIMESTAMPTZ NOT NULL,
  accepted_at TIMESTAMPTZ, resend_count SMALLINT NOT NULL DEFAULT 0, [STD]
);
```

### 4. Backend APIs
```
GET    /api/v1/builder/team?status=&role=&cursor=      → §18.1 list
POST   /api/v1/builder/team                             {fullName, mobile, email, roleCode, projectAccess[], sendInvite}
GET    /api/v1/builder/team/{userId}
PATCH  /api/v1/builder/team/{userId}                    {fullName, email, roleCode, projectAccess[]}
POST   /api/v1/builder/team/{userId}/deactivate         {reason}
POST   /api/v1/builder/team/{userId}/reactivate
DELETE /api/v1/builder/team/{userId}                    → soft remove
POST   /api/v1/builder/team/{userId}/resend-invite
POST   /api/v1/builder/team/{userId}/reset-password     → sends a reset link
GET    /api/v1/builder/team/{userId}/activity?from=&to= → what this person has done
POST   /api/v1/builder/team/transfer-ownership          {newOwnerId}  → two-step, OTP confirmed
GET    /api/v1/roles?profile=BUILDER                    → role list + permission matrix
```

### 5. Frontend pages
`/builder/admin/team`, `/builder/admin/team/new`, `/builder/admin/team/{id}`, `/builder/admin/settings` (org profile, logo, notification hour, document templates entry point).

### 6. Components
`TeamMemberTable` (§18.1 columns: Name · Mobile/Email · Role · Status · Date Added · Actions: Edit Role / Deactivate / Remove), `AddTeamMemberForm` (§18.2 fields: Full Name, Mobile, Email, Role, Project Access, Send Invite toggle), `RoleSelector` (**shows the permission matrix inline when a role is picked** — an admin should see exactly what they're granting before saving), `PermissionMatrixTable` (the full §18.3 grid, read-only), `ProjectAccessSelector` (multi-select with "All projects" default per §18.2), `InviteStatusChip` (Invited / Active / Inactive), `StaffActivityPanel` (leads handled, deals closed, payments recorded, last login — feeds §17.1 Staff Activity Report), `DeactivateDialog` (shows what happens to their assigned leads, with a reassignment picker), `TransferOwnershipFlow`, `TeamQuotaBar` (§23.1: 1 / 3 / 15).

### 7. Business logic
- **Five roles exactly as §18.3**, mapped to permissions in M-02. The matrix is displayed to the admin verbatim so there is no ambiguity about what a role can do.
- **Invite flow (§18.2):** creating a staff member creates an `app_user` with `status = INVITED` and no password. If "Send Invite" is on, an SMS and/or email goes out with a single-use link (7-day expiry). The staff member sets their own password. **Credentials are never generated and sent in plaintext** — the PRD's "sends login credentials" is implemented as a secure set-password link, which is the correct interpretation.
- **Project access (§18.2):** default all; scoping to specific projects sets `project_access_mode = SCOPED` and writes `user_project_access` rows. Applied as a hard predicate everywhere (M-02).
- **Deactivate vs Remove:** deactivate revokes sessions and blocks login while retaining all historical attribution. Remove is a soft delete, also retaining attribution — a deal handled by a removed employee must still show who handled it. There is no hard delete of a staff member who has touched data.
- **Reassignment on deactivation:** if the person has assigned leads, the dialog requires choosing a new assignee (or explicitly "leave unassigned"). Leads silently vanishing into an inactive user's account is a real and expensive failure.
- **Team quota (§23.1):** Free = owner only, Pro = 3, Premium = 15. Invited-but-not-accepted users count.
- **Ownership transfer** is two-step (initiate → target confirms with OTP) and is the only way `is_owner` moves.
- **Self-protection:** an admin cannot deactivate, remove, or demote themselves; the last admin cannot be demoted.
- **Staff activity tracking** aggregates from `created_by` / `assigned_to` / `received_by` / `handled_by` across modules — no separate tracking table needed.

### 8. User flow
```
Admin Panel → Team → + Add Member
  → name, mobile, email
  → Role: Sales Executive → matrix appears: "Own leads only · No financial access · No delete"
  → Project Access: Green Valley only
  → Send Invite: on → Save
  → staff receives SMS: "You've been added to {Builder} on Shardeya. Set your password: <link>"
  → sets password → logs in → sees only Green Valley leads assigned to them, no Financials link
```

### 9. Permissions
`TEAM_MANAGE` — **Admin/Owner only** (§18.3: Team Management ✅ only for Admin). Manager, Sales Exec, Accounts, View Only cannot access the Admin Panel; the sidebar link is not rendered. Viewing the team roster (without editing) can be granted via `TEAM_VIEW` to Managers.

### 10. Edge cases
- Mobile already registered to another org → blocked with "This number is already registered on Shardeya." (No detail about where — no enumeration.)
- Invite expired → resend generates a fresh token; old token invalidated.
- Staff never accepts → remains `INVITED`, counts toward quota, shown with an "Invite pending, sent 12 days ago" marker and a one-tap resend.
- Deactivating the only Manager → allowed but warned.
- Downgrading a plan below the current team size → existing staff retained and functional; **no new members** can be added until under the limit. Never lock people out of an account they use daily.
- Role changed mid-session → permissions refresh within 15 minutes or immediately via denylist; a toast explains the change.
- Project access removed while the staff member is viewing that project → next call 404s with a clear redirect.
- Staff member who is also the owner of a *broker* account elsewhere → currently blocked by global mobile uniqueness; documented as the multi-org roadmap item.
- Removed staff's interactions → retained permanently (audit); their name renders with "(former staff)".
- Two admins editing the same member concurrently → optimistic lock.

### 11. Validation rules
| Field | Rule |
|---|---|
| full_name | required, 2–100 |
| mobile | required, 10 digits, globally unique |
| email | optional, valid, globally unique if provided |
| role_code | required, ∈ builder roles, ≠ PLATFORM_ADMIN |
| project_access | if SCOPED, ≥ 1 project, all in org |
| send_invite | requires mobile (SMS) or email |
| team quota | per plan (§23.1) |
| deactivation | requires lead reassignment if leads are assigned |
| self-action | cannot deactivate/remove/demote self |
| last admin | cannot be demoted or removed |

### 12. Notifications (§22.3)
- Staff added → SMS/email invite to the member; in-app to the owner.
- Role changed → in-app + email to the affected member ("Staff added or role changed" per §22.3).
- Project access changed → in-app + email.
- Deactivated → email to the member; in-app to owner.
- Invite accepted → in-app to owner.
- Ownership transfer initiated/completed → in-app + email + SMS to both parties.

### 13. Future scalability
- **Custom roles** — `role.org_id` is already nullable; a role builder UI on the existing permission catalogue.
- Team hierarchy (Manager → their executives) with reporting rollups.
- Attendance / activity dashboards and target-setting per executive.
- Territory-based assignment (by locality, not just project).
- SSO for larger builder groups.
- Approval workflows (e.g. a discount above 5% needs Manager approval) — a natural extension of the permission engine.

---

# B-13 · Follow-up & Collection Tracker

**PRD:** §19.1, §19.2

### 1. Why this module exists
§19 is the builder's daily work queue: one screen with two tabs answering "who do I call today?" and "who owes me money?". Everything in it exists elsewhere (B-07, B-05, B-08) — but a builder should not have to visit three modules to run their morning. This is the module that gets opened most often, and its value is entirely in reducing clicks to action.

### 2. Dependencies
**Depends on:** M-02, M-06, M-11, B-05, B-07, B-08, B-12.
**Depended on by:** B-01 (dashboard deep links).

### 3. Database schema
No owned tables — an action-oriented read view over `customer` (follow-ups) and `payment_schedule` (collections).

Supporting indexes:
```sql
CREATE INDEX ix_followup_queue ON customer(org_id, follow_up_date, assigned_to)
  WHERE deleted_at IS NULL AND no_further_follow_up = false
    AND status NOT IN ('DEAL_CLOSED','LOST');
CREATE INDEX ix_collection_queue ON payment_schedule(org_id, due_date, status)
  WHERE status IN ('PENDING','PARTIALLY_PAID','OVERDUE') AND deleted_at IS NULL;
```

### 4. Backend APIs
```
GET  /api/v1/builder/tracker/follow-ups?range=today|week|overdue|all&assignedTo=&projectId=&cursor=
GET  /api/v1/builder/tracker/collections?range=today|week|overdue|all&projectId=&cursor=
GET  /api/v1/builder/tracker/counts                    → badge counts for both tabs
POST /api/v1/builder/tracker/follow-ups/{customerId}/log        {type, remarks, nextDate, result}
POST /api/v1/builder/tracker/follow-ups/{customerId}/reschedule {newDate, reason}
POST /api/v1/builder/tracker/follow-ups/{customerId}/mark-done  {remarks}
POST /api/v1/builder/tracker/collections/{scheduleId}/record-payment  {…}
POST /api/v1/builder/tracker/collections/{scheduleId}/remind    {channel}
POST /api/v1/builder/tracker/collections/bulk-remind            {scheduleIds[]}
```

### 5. Frontend pages
`/builder/tracker` with two tabs: **Follow-ups** (§19.1) · **Collections** (§19.2).

### 6. Components
`TrackerTabs` (with live count badges — "Follow-ups (12) · Collections (27)"), `FollowUpTable` (§19.1 columns: Follow-up Date · Customer Name + contact · Project Interested · Assigned To · Status · Last Remark · Actions: Log Follow-up / Reschedule / Mark Done), `CollectionTable` (§19.2 columns: Due Date · Project · Plot Number · Buyer Name + contact · Amount Due · Days Overdue (red) · Total Balance · Actions: Record Payment / Send WhatsApp Reminder / View Plot), `RangeFilterPills` (Today · This Week · Overdue · All), `QuickLogDialog` (**inline, does not navigate away** — logging a call must take 3 taps), `QuickPaymentDialog` (inline payment recording), `CallButton` / `WhatsAppButton` (`tel:` and `wa.me` deep links — the actual action a user takes), `BulkRemindToolbar`, `OverdueSeverityChip` (1–7 days amber, 8–30 orange, 30+ red), `EmptyQueueState` ("Nothing due today — nice work").

### 7. Business logic
- **§19.1 Follow-up Tracker:** all customers with a `follow_up_date`, sorted earliest first, excluding `DEAL_CLOSED`/`LOST` and those flagged `no_further_follow_up`. Overdue items pinned above today's.
- **§19.2 Collection Tracker:** all `payment_schedule` rows in `PENDING`/`PARTIALLY_PAID`/`OVERDUE` across all projects, sorted by due date, with `days_overdue` computed live and total buyer balance shown alongside the individual instalment (a buyer ₹2L behind on one instalment but ₹20L outstanding overall is a different conversation).
- **Three actions per row, all inline.** Logging a follow-up from here creates an `interaction`, updates `customer.follow_up_date`, refreshes the calendar projection, and removes the row from today's queue — without a page navigation. Recording a payment does the equivalent on the collections side.
- **Scoping:** Sales Executives see only their assigned follow-ups and no collections tab at all. Accounts Staff see only collections. Managers and Admins see both, with a staff filter.
- **Bulk reminders** with dedupe (one per buyer per 24h), opt-in checks, quota checks, and a preview of the exact message.
- **Severity banding** on overdue days drives sort priority and colour.
- Counts feed the B-01 dashboard cards and the notification bell.

### 8. User flow
```
Morning → Dashboard → "Follow-ups Today: 12" → Tracker
  Tab 1: 12 rows. Row 1: Rajesh Kumar · Green Valley · Following Up · "Wants East facing"
    → tap Call → phone dials → conversation
    → Log Follow-up → "Visiting Sunday" · next: Sunday · Positive → Save
    → row disappears; count → 11
  Tab 2: 27 overdue. Sorted worst first: 45 days, ₹2,00,000, total balance ₹18,00,000
    → Send WhatsApp Reminder → templated message with amount and due date
    → or Record Payment if they've paid
```

### 9. Permissions
Follow-ups tab: `DATA_VIEW_ALL` (all) or `DATA_VIEW_OWN` (assigned only). Collections tab: `FINANCIAL_VIEW` — hidden entirely for Sales Executive and View Only. Recording payments: `FINANCIAL_RECORD_PAYMENT`. Bulk reminders: `FINANCIAL_RECORD_PAYMENT` + WhatsApp entitlement.

### 10. Edge cases
- Empty queue → a positive empty state, not a blank table.
- 500 overdue instalments → paginated, sorted by severity, with a summary strip ("₹1.2 Cr overdue across 500 instalments").
- Follow-up marked done without a next date → customer leaves the queue; if their status is still active, a gentle prompt asks whether to schedule the next one (the most common way pipelines go cold).
- Payment recorded that fully settles the instalment → row disappears immediately (optimistic), confirmed by the server.
- Buyer opted out of WhatsApp → the reminder button shows "Opted out" and offers SMS instead.
- Two staff acting on the same row → optimistic locking; the loser sees "Already handled by Priya 2 minutes ago".
- Instalment due today but paid yesterday in advance → allocation consumes it; the row never appears.
- Customer with `no_further_follow_up` → excluded, but visible under the "All" filter with a marker.
- Timezone → "today" is IST.
- Offline → the queue is cached; logged actions queue and sync, with a pending marker per row.

### 11. Validation rules
- `range` ∈ {today, week, overdue, all}.
- Log follow-up: remarks required (1–5,000); next date optional, ≥ today if provided.
- Reschedule: new date required, ≥ today, reason ≥ 5 chars.
- Record payment: full B-05 validation.
- Bulk remind: ≤ 200 per action, dedupe enforced server-side.

### 12. Notifications (§22.3)
- Follow-up due today → in-app + WhatsApp at 09:00 to the assignee.
- Follow-up overdue → in-app + WhatsApp; escalates to the Manager after 3 days.
- Instalment due today → in-app + WhatsApp to builder; buyer gets the §22.4 reminder.
- Instalment overdue 3+ days → in-app + WhatsApp, then weekly.
- Daily digest at 09:00 → "You have 12 follow-ups and 27 collections today."

### 13. Future scalability
- **Auto-dialler / click-to-call with recording** — turns the tracker into a call centre queue.
- Priority scoring (deal value × days overdue × engagement) to order the queue by expected value rather than date.
- Escalation ladders (reminder → demand letter → legal notice) as a configurable workflow.
- Payment links embedded in reminders (see B-05 scalability).
- Assignment of collections to specific staff, mirroring lead assignment.
- SLA tracking on follow-up responsiveness per executive.

---

# B-14 · Broker Management

**PRD:** §20.1 – §20.6

### 1. Why this module exists
§20 is the most distinctive module in the entire PRD and the strongest strategic asset. Builders sell primarily through channel partners, and today they track broker commissions in notebooks — leading to disputes, delayed payouts, and brokers taking their leads elsewhere. Shardeya turns that into a system: onboarding, three-level commission configuration, an auditable ledger, and a **gamified tier system** that gives builders a lever to motivate their channel. It is also the natural bridge to the broker side of the platform: a broker managed here is a broker who can be invited to Shardeya as a user.

### 2. Dependencies
**Depends on:** M-01, M-02, M-05 (tier badges), M-06, M-09 (broker quota: Free N/A, Pro 10, Premium unlimited), B-02, B-04 (sale attribution), B-05.
**Depended on by:** B-04 (commission resolution at sale), B-08 (commission paid), B-10, B-15 (top-broker analytics).

### 3. Database schema
`broker_partner`, `broker_commission_config`, `broker_tier`, `commission_ledger_entry`, `commission_payment` (data model §5).

Additional:
```sql
CREATE TABLE broker_tier_history (
  id UUID PK, org_id UUID, broker_partner_id UUID NOT NULL,
  from_tier_id UUID, to_tier_id UUID NOT NULL,
  deals_at_change INTEGER NOT NULL, changed_at TIMESTAMPTZ NOT NULL,
  changed_by UUID, is_manual BOOLEAN NOT NULL DEFAULT false, reason TEXT
);
CREATE TABLE broker_interaction (   -- §20.6 "follow-up history and notes"
  id UUID PK, org_id UUID, broker_partner_id UUID NOT NULL,
  occurred_on DATE NOT NULL, type VARCHAR(20), remarks TEXT NOT NULL,
  next_follow_up_date DATE, conducted_by UUID, [STD]
);
```

### 4. Backend APIs
```
-- 20.1 / 20.2 Broker CRUD
GET    /api/v1/brokers?status=&tier=&city=&search=&sort=&cursor=
POST   /api/v1/brokers
GET    /api/v1/brokers/{id}                     → §20.6 full profile
PATCH  /api/v1/brokers/{id}
POST   /api/v1/brokers/{id}/deactivate | /reactivate | /block
DELETE /api/v1/brokers/{id}                     → soft, guarded
GET    /api/v1/brokers/{id}/bank-details        [SENSITIVE_VIEW, audited]

-- 20.3 Commission configuration
GET    /api/v1/brokers/{id}/commission-configs
POST   /api/v1/brokers/{id}/commission-configs  {scope, projectId?, plotId?, type, rate, effectiveFrom}
PATCH  /api/v1/commission-configs/{id}
DELETE /api/v1/commission-configs/{id}
POST   /api/v1/brokers/{id}/commission-preview  {projectId, plotId, dealValue} → resolved commission

-- 20.4 Tiers
GET    /api/v1/broker-tiers
POST   /api/v1/broker-tiers                     {name, minDeals, maxDeals, bonusType, bonusValue, badgeMediaId, perks}
PATCH  /api/v1/broker-tiers/{id}
DELETE /api/v1/broker-tiers/{id}
POST   /api/v1/brokers/{id}/tier-override       {tierId, reason}
POST   /api/v1/broker-tiers/recalculate         → admin re-evaluation of all brokers

-- 20.5 Commission ledger
GET    /api/v1/brokers/{id}/ledger?status=&from=&to=&cursor=
GET    /api/v1/commission-ledger?brokerId=&projectId=&status=&from=&to=   → org-wide
POST   /api/v1/commission-ledger/{id}/payments  [Idempotency-Key] {amount, paidOn, mode, reference, remarks}
POST   /api/v1/commission-payments/{id}/reverse {reason}
GET    /api/v1/brokers/{id}/statement?from=&to= → PDF statement

-- 20.6 Profile extras
GET    /api/v1/brokers/{id}/performance
GET    /api/v1/brokers/{id}/deals?cursor=
GET    /api/v1/brokers/{id}/interactions
POST   /api/v1/brokers/{id}/interactions
POST   /api/v1/brokers/{id}/invite              → invite to join Shardeya as a broker user
```

### 5. Frontend pages
`/builder/brokers` (list), `/builder/brokers/new`, `/builder/brokers/{id}` (§20.6 profile with tabs: Overview · Commission Config · Deals · Ledger · Notes), `/builder/brokers/tiers` (tier configuration), `/builder/commission-ledger` (org-wide).

### 6. Components
- `BrokerTable` (§20.1 columns: Name · Mobile · City/Area · **Current Level/Tier** · Deals Closed · Total Commission Paid · Commission Due · Last Active · Status · Actions: View / Edit / Deactivate / Message).
- `BrokerForm` (§20.2: all 16 fields including RERA number, firm name, commission type, bank details, UPI ID, per-project toggle).
- `TierBadge` (custom icon + name, colour by rank).
- `TierProgressBar` (§20.6: "Gold — 3 more deals to reach Platinum" — the single most motivating element in the module; also shown in any broker-facing view).
- `CommissionConfigTable` (§20.3: Project · Type · Rate · Effective From · Actions) with a `ScopeSelector` (Global / Per-Project / Per-Plot).
- `CommissionPreviewCard` (enter a deal value → see the resolved commission and which rule applied — removes all ambiguity).
- `TierConfigEditor` (§20.4: name, deals threshold, bonus type, bonus value, badge upload, perks description; with an overlap validator).
- `CommissionLedgerTable` (§20.5: Deal Date · Project · Plot · Buyer · Deal Value · Commission Earned · Status · Amount Paid · Balance Due · Payment Date · Mode · Actions).
- `RecordCommissionPaymentDialog`, `BrokerPerformancePanel` (deals, revenue generated, commission earned/pending, conversion rate), `BrokerDealsTable`, `BrokerNotesTimeline`, `SendMessageDialog` (WhatsApp to broker), `BrokerStatementDownload`, `InviteBrokerCard`.

### 7. Business logic

**Commission resolution (§20.3)** — the core algorithm, run at sale creation in B-04:
```
resolve(broker, project, plot, dealValue, saleDate):
  config = first match, by precedence, where effective_from <= saleDate
             and (effective_to is null or effective_to >= saleDate):
     1. scope = PLOT    and plot_id    = plot
     2. scope = PROJECT and project_id = project
     3. scope = GLOBAL
     4. broker.commission_type / commission_pct / commission_fixed   (fallback)

  base = config.type = PERCENTAGE ? dealValue * rate/100 : rate

  tier = broker.tier
  bonus = tier.bonus_type = PCT   ? base * tier.bonus_value/100
        : tier.bonus_type = FIXED ? tier.bonus_value
        : 0

  total = base + bonus
  snapshot {config_id, scope, type, rate, tier_id, tier_bonus_type, tier_bonus_value}
```
The snapshot is written to `commission_ledger_entry.config_snapshot`. **Rate changes are never retroactive** — this is the single most important rule in the module, because commission disputes are what destroy builder–broker relationships.

**Tier system (§20.4):**
- Default tiers seeded per org: Bronze 0–2, Silver 3–9, Gold 10–24, Platinum 25+ — all names, thresholds, bonuses, badges and perks editable per §20.4.
- `deals_closed_count` increments when a `plot_sale` attributed to the broker reaches `COMPLETED`. A trigger then re-evaluates the tier.
- On upgrade: write `broker_tier_history`, notify the builder (§22.3 "Broker tier upgraded") and the broker (in-app + optional WhatsApp per §20.4).
- **Tiers never downgrade automatically.** A broker who reached Gold does not drop when a deal is cancelled — that would be perceived as punitive and would destroy the motivational value. Cancellations reduce the count but the tier only moves down by explicit manual override, with a reason.
- Manual override (§20.4) sets `tier_manually_overridden = true`, which freezes automatic evaluation until cleared.
- Overlapping tier ranges are rejected by an exclusion constraint.

**Ledger (§20.5):**
- One `commission_ledger_entry` per attributed sale, created at sale time with status `PENDING`.
- Payments recorded against it (partial allowed) move it to `PARTIALLY_PAID` → `PAID`.
- Commission payments are immutable; corrections are reversals.
- Sale cancelled → entry `CANCELLED`; if commission was already paid, a **recovery entry** (negative) is created rather than deleting history, and the builder is prompted about adjusting it against the broker's next payout.
- Broker statement PDF (B-11 machinery) covering a date range — brokers ask for this constantly, and producing it in one tap is a relationship win.

**Broker quota (§23.1):** Free = N/A (module hidden), Pro = 10, Premium = unlimited.

**Invite to Shardeya:** a broker in the network can be invited to create their own Shardeya broker account, pre-linked via `linked_user_id`. This is the growth loop — every builder becomes a distribution channel for the broker product.

### 8. User flow
```
Broker Management → + Add Broker
  → Ramesh Sharma · 98xxxxxx · Indore · RERA no · commission: Percentage 2%
  → per-project rates: on → Green Valley 2.5%, Sunrise 1.5%
  → bank details / UPI → Save → tier: Bronze (0 deals)

Sale happens (B-04) → broker selected → preview "₹84,000 (2.5% of ₹33.6L)"
  → ledger entry created, status Pending
  → builder notified: "Commission of ₹84,000 due to Ramesh Sharma"

Deal completes (fully paid) → deals_closed_count 2 → 3
  → crosses Silver threshold → tier upgraded
  → builder notified · broker notified: "Congratulations, you're now Silver"

Broker profile → Ledger → "Balance due ₹2,45,000"
  → Record Payment → ₹2,45,000 · UPI · UTR → all pending entries settled
  → statement PDF generated and WhatsApped to the broker
```

### 9. Permissions
View brokers: `BROKER_VIEW` (Admin, Manager). Create/edit: `BROKER_MANAGE` (Admin, Manager). Delete/block: Admin only. Commission configuration and tier configuration: **Admin only** — a Manager setting commission rates is a fraud vector. Record commission payment: `BROKER_COMMISSION_PAY` (Admin, Accounts Staff). Bank details: `SENSITIVE_VIEW`, audited. Sales Executive and View Only: no access; sidebar link not rendered.

### 10. Edge cases
- Duplicate broker mobile within the org → blocked with a link to the existing record.
- Broker with no commission configuration at sale time → falls back to their default; if that's also unset, the sale form **requires** a manual commission amount rather than silently recording zero.
- Per-project rate added after a sale in that project → the existing sale is unaffected (snapshot).
- Rate changed with a backdated `effective_from` → warning that it will not alter existing ledger entries; a separate explicit "recalculate" action exists for genuine corrections, and it is fully audited.
- Deal cancelled after commission paid → recovery entry with a clear "Recover ₹X from Ramesh Sharma" flag; never a silent deletion.
- Broker crosses two tiers at once (bulk-imported historical deals) → jumps to the highest applicable, one notification, history records the jump.
- Tier threshold edited so a broker no longer qualifies → they keep their tier (no automatic demotion); the builder can override manually.
- Broker deleted with an outstanding ledger balance → blocked: "Ramesh Sharma has ₹2,45,000 in unpaid commission. Settle or write off first."
- Broker blocked mid-deal → existing ledger entries stand; no new sales can be attributed to them.
- Two brokers claiming the same lead → attribution follows the sale record; the lead's `source_broker_id` and the sale's `broker_partner_id` may differ, and the discrepancy is surfaced on the sale for the builder to resolve.
- External broker (free text) → no ledger automation; a persistent prompt suggests adding them.
- Commission exceeding a sanity threshold (>10% of deal value) → confirmation prompt.
- Broker quota reached on Pro → Add disabled with upgrade CTA.
- Bank/UPI details entered incorrectly → the platform does not transfer money, so this is display-only; a "verify with the broker" note is shown before generating a statement.

### 11. Validation rules
| Field | Rule |
|---|---|
| full_name | required, 2–120 |
| mobile | required, 10 digits, unique per org |
| email | optional, valid |
| city_area | optional, ≤ 150 |
| rera_number | optional, ≤ 60 |
| firm_name | optional, ≤ 150 |
| commission_type | required, ∈ {PERCENTAGE, FIXED} |
| commission_pct | required if PERCENTAGE; 0–20 (>10 warns) |
| commission_fixed | required if FIXED; > 0, ≤ 1 crore |
| ifsc | optional, `^[A-Z]{4}0[A-Z0-9]{6}$` |
| upi_id | optional, `^[\w.\-]{2,}@[a-zA-Z]{2,}$` |
| bank_account_number | optional, 9–18 digits, encrypted |
| tier.name | required, 2–40, unique per org |
| tier.min_deals | required, ≥ 0; ranges must not overlap |
| tier.bonus_value | ≥ 0; if PCT, ≤ 100 |
| config.effective_from | required; unique per (broker, scope, target) |
| commission payment amount | > 0, ≤ balance_due (overpayment requires confirmation) |

### 12. Notifications (§22.3, §20.4)
- Broker commission becomes due (after a deal closes) → in-app + email to owner.
- Broker tier upgraded → in-app to builder; in-app + optional WhatsApp to the broker.
- Commission payment recorded → in-app to owner; WhatsApp to broker with the amount and updated balance.
- Broker added → in-app to owner; welcome WhatsApp to the broker with the commission structure.
- Commission overdue (pending > 30 days) → in-app + email to owner.
- Broker deactivated/blocked → in-app to owner.

### 13. Future scalability
- **Broker portal** — brokers log in (`linked_user_id` already modelled) to see their own ledger, tier progress, and available inventory. This is the highest-value extension in the entire product: it makes the builder's channel sticky and converts brokers into Shardeya users.
- Lead assignment to brokers (builder pushes leads out to the channel).
- Automated commission payout via a payments API.
- Broker leaderboards and campaign contests ("close 5 in October, get a 1% bonus") — the tier system is the foundation.
- Multi-builder brokers: one broker serving several Shardeya builders, with a consolidated view.
- TDS deduction on commission payouts (a real compliance requirement above ₹15,000/year).
- Broker document vault (RERA certificate, PAN, agreement).

---

# B-15 · Stats & Analysis

**PRD:** §21.1, §21.2

### 1. Why this module exists
§21.1 specifies ten charts and metrics covering sales, revenue, inventory mix, lead sources, conversion funnel, broker performance, project revenue, collection vs target, overdue trend, and staff performance. Individually these exist across other modules; together they are the view a builder uses to make decisions — which project to push, which source to spend on, which executive is actually closing.

### 2. Dependencies
**Depends on:** M-02, M-09 (`ANALYTICS` tier: Basic / Advanced / Full), M-10, B-02 … B-14 (all data sources).
**Depended on by:** nothing (leaf). Built last in the Builder track.

### 3. Database schema
No owned tables. Reads from materialised views refreshed every 15 minutes:

```sql
CREATE MATERIALIZED VIEW mv_monthly_sales AS
SELECT org_id, project_id, date_trunc('month', purchase_date) AS month,
       COUNT(*) AS plots_sold, SUM(deal_value) AS sale_value
FROM plot_sale WHERE status <> 'CANCELLED' AND deleted_at IS NULL
GROUP BY 1,2,3;

CREATE MATERIALIZED VIEW mv_lead_funnel AS
SELECT org_id, interested_project_id AS project_id, source, status, assigned_to,
       COUNT(*) AS lead_count
FROM customer WHERE deleted_at IS NULL
GROUP BY 1,2,3,4,5;

CREATE MATERIALIZED VIEW mv_broker_performance AS
SELECT org_id, broker_partner_id, project_id,
       COUNT(*) FILTER (WHERE status='COMPLETED') AS deals_closed,
       SUM(deal_value) FILTER (WHERE status <> 'CANCELLED') AS revenue_generated
FROM plot_sale WHERE deleted_at IS NULL AND broker_partner_id IS NOT NULL
GROUP BY 1,2,3;

CREATE MATERIALIZED VIEW mv_staff_activity AS
SELECT org_id, user_id, month, leads_handled, deals_closed, follow_ups_logged, payments_recorded
FROM ( … union of per-module aggregates … );
```
Plus an optional `collection_target (org_id, project_id, month, target_amount)` table to make §21.1 "Collection vs Target" real rather than a placeholder.

### 4. Backend APIs
```
GET /api/v1/builder/stats/overview?projectId=&from=&to=&staffId=&brokerId=
GET /api/v1/builder/stats/monthly-sales?months=12&projectId=
GET /api/v1/builder/stats/monthly-revenue?months=12&projectId=
GET /api/v1/builder/stats/plot-status-breakdown?projectId=
GET /api/v1/builder/stats/leads-by-source?projectId=&from=&to=
GET /api/v1/builder/stats/conversion-funnel?projectId=&from=&to=
GET /api/v1/builder/stats/top-brokers?limit=5&from=&to=
GET /api/v1/builder/stats/revenue-by-project?from=&to=
GET /api/v1/builder/stats/collection-vs-target?months=12&projectId=
GET /api/v1/builder/stats/overdue-trend?months=12&projectId=
GET /api/v1/builder/stats/staff-performance?from=&to=
POST /api/v1/builder/stats/export {charts[], format}
```
All accept the same §21.2 filter set so the whole page updates as one.

### 5. Frontend pages
`/builder/stats`.

### 6. Components
`StatsFilterBar` (§21.2: project, date range, staff member, broker — applied globally to every chart), `MonthlySalesChart` (bar, 12 months), `MonthlyRevenueChart` (bar), `PlotStatusPieChart` (Available/Sold/Reserved), `LeadSourcePieChart`, `ConversionFunnelChart` (Interested → Site Visit → Deal Closed with stage-to-stage percentages), `TopBrokersChart` (horizontal bar, top 5), `RevenueByProjectChart`, `CollectionVsTargetChart` (grouped bar, expected vs actual), `OverdueTrendChart` (line), `StaffPerformanceTable` (leads handled · deals closed · follow-ups done · revenue generated), `KpiStrip` (conversion rate, average deal value, average days-to-close, collection efficiency), `ChartCard` (title, subtitle, export-as-image, expand), `DateRangePicker` (with presets: This Month · Last Month · This Quarter · This FY · Last 12 Months · Custom), `TierDistributionChart` (bonus: how the broker network is distributed across tiers).

### 7. Business logic
- **All ten §21.1 charts implemented** with the specified chart types.
- **§21.2 filters apply to every chart simultaneously** and update without a page reload — implemented as a single filter state driving parallel queries with a shared cache key.
- **Conversion funnel** measures *cohort* progression, not current-status counts: of leads created in the period, how many ever reached Site Visit, how many ever closed. Current-status counting understates conversion badly and would make the chart misleading.
- **Collection vs Target:** actual = `SUM(payment_record)` per month; target = `collection_target` if configured, otherwise `SUM(payment_schedule.expected_amount)` due that month — which is a meaningful default and requires no setup.
- **Staff performance** aggregated from existing attribution columns; feeds the §17.1 Staff Activity Report.
- **Analytics entitlement (§23.1):** Basic (Free) = plot status pie + monthly sales only; Advanced (Pro) = all ten charts; Full (Premium) = all charts + custom date ranges + chart export + saved views.
- **Data freshness** shown explicitly ("as of 09:15") — a builder making a decision needs to know how current the number is.
- Every chart is drill-through: clicking a bar navigates to the filtered list behind it.

### 8. User flow
Stats & Analysis → default: last 12 months, all projects → sales trending down for 2 months → filter to Project = Sunrise → the drop is isolated there → lead-source pie shows broker leads collapsed → Top Brokers shows one broker inactive 60 days → tap through to that broker's profile → log a follow-up. The whole point of the module is that this chain takes under a minute.

### 9. Permissions
`REPORT_VIEW_ALL` for the full page. `FINANCIAL_VIEW` gates revenue, collection, and overdue charts — a Sales Executive sees lead and conversion charts scoped to their own leads, and no money. Accounts Staff sees financial charts only. Project-scoped staff see only their projects, with an explicit scope banner.

### 10. Edge cases
- New org with no data → each chart shows an informative empty state explaining what will appear, not an empty axis.
- Single month of data → charts render with one bar plus a "more data needed for trends" note.
- Division by zero in conversion rate → shown as "—", never NaN or 0%.
- Materialised views stale (15 min) → timestamp shown; the current day is computed live for the KPI strip.
- Filters yielding zero rows → "No data for these filters" with a one-tap clear.
- Very wide date range (10 years) → auto-buckets to quarters/years rather than rendering 120 bars.
- Charts on 360px → horizontal bars preferred over vertical; legends move below; pies become donuts with a centre total; tap-for-tooltip instead of hover.
- Cancelled deals → excluded from sales/revenue but available via a toggle, because "how many did we lose?" is a real question.
- Migrated historical data → flagged and excludable, so trends aren't distorted by a one-time import spike.
- Staff/broker filters combined → intersection, with a clear indication when it produces a very small sample.

### 11. Validation rules
- Date range: `from ≤ to`, ≤ 5 years, not in the future.
- `months` ∈ 1–60.
- `limit` for top-N ∈ 3–20.
- Filter IDs must exist and be in scope.
- Export: ≤ 20 charts per request.

### 12. Notifications
None originated. Optional (future): monthly performance summary email, and anomaly alerts ("sales down 40% vs last month").

### 13. Future scalability
- Scheduled emailed dashboards.
- Configurable targets per project/staff/month, turning §21.1's "Collection vs Target" into a full target-management feature.
- Predictive analytics: projected collections, expected inventory sell-out date, lead-scoring models.
- Cohort and retention analysis on buyers.
- Benchmarking against anonymised platform averages ("your conversion is 12% vs 9% for similar projects") — a genuinely defensible network effect.
- Read replica / OLAP store (ClickHouse) when materialised views stop keeping up.
- Custom dashboard builder for Premium.
