# Shardeya — Broker Modules (BR-01 … BR-08)

The Broker side is a single-user product: one person managing listings, leads, and commission income. It reuses M-12 (customers), M-11 (calendar), M-10 (reports), M-05 (media) and M-07 (import) almost entirely, so its net new surface is smaller than the Builder side despite covering PRD §4–§10 in full.

---

# BR-01 · Broker Dashboard

**PRD:** §4.1, §4.2

### 1. Why this module exists
§4.1 defines eight summary cards that answer a broker's morning questions: what's live, who do I call, what did I earn. Every card deep-links to a filtered module view, making the dashboard the primary navigation.

### 2. Dependencies
**Depends on:** M-01, M-02, M-04, M-09, BR-02, BR-03, BR-04, BR-05, BR-07. Built last in the Broker track.

### 3. Database schema
No owned tables. Reads `org_metrics` (extended with broker columns: `active_properties`, `properties_for_sale`, `properties_for_rent`, `hot_properties`, `active_customers`) plus live queries for time-windowed figures.

### 4. Backend APIs
```
GET /api/v1/broker/dashboard          → all eight cards
GET /api/v1/broker/dashboard/activity → recent activity feed
```
Redis-cached 5 minutes, invalidated on write.

**Cards (§4.1), each with its click destination:**
| Card | Value | On click |
|---|---|---|
| Active Properties | count of active listings | Property Manager |
| Properties for Sale | count, `transaction_type=SELL` | Property Manager filtered to Sale |
| Properties for Rent | count, `transaction_type=RENT` | Property Manager filtered to Rent |
| Total Customers/Leads | active pipeline count | Customer Manager |
| Follow-ups Today | `follow_up_date = today` | Customer Manager filtered to today |
| Deals Closed This Month | finalised this month | Deals History filtered to this month |
| Total Brokerage (Month) | `SUM(brokerage_receipt)` this month | Brokerage Analysis |
| Hot Properties | `is_hot = true` | Property Manager filtered to Hot |

### 5. Frontend pages
`/broker/dashboard`.

### 6. Components
`DashboardGrid`, `SummaryCard` (with trend vs last month), `QuickActionRow` (Add Property · Add Customer · Log Follow-up · Record Brokerage), `TodayPanel` (today's follow-ups and site visits inline — a broker's whole day on one screen), `StalePropertiesAlert` ("7 properties not updated in 30+ days" per §10.1), `ActivityFeed`, `EmptyDashboard` (onboarding checklist).

### 7. Business logic
- All eight §4.1 cards with exact click-through targets.
- Counters from `org_metrics`; time-windowed figures queried live against indexed columns.
- Trend arrows compare to the equivalent prior period.
- Brokerage figure uses **received** amounts, not finalised deal values — a broker cares about money in hand; pending brokerage is shown separately in BR-05.
- Empty state for a new broker: "Add your first property" three-step checklist.

### 8. User flow
Login → dashboard → "Follow-ups Today: 8" → tap → Customer Manager filtered → call → log → back.

### 9. Permissions
Broker orgs are single-user; the owner sees everything. (The structure supports future multi-agent brokerages without change.)

### 10. Edge cases
New account → checklist. Large currency → Indian abbreviations. Metrics drift → nightly reconciliation. IST boundaries for "today"/"this month". 360px → 2-column cards, no horizontal scroll. Offline → cached values with a staleness marker.

### 11. Validation rules
Read-only; query params validated as enums/UUIDs.

### 12. Notifications
None originated; surfaces stale-property and follow-up states inline.

### 13. Future scalability
Configurable card layout; multi-agent brokerage rollups; goal tracking ("₹5L brokerage this month" with progress); home-screen widget.

---

# BR-02 · Property Manager

**PRD:** §5.1, §5.2.1, §5.2.2

### 1. Why this module exists
§5 is the broker's inventory. Thirty-one fields across basic info, physical attributes, owner details and media (§5.2.1, §5.2.2), with a rich filter set (§5.1). The `property_updated_date` field is quietly the most important one — it powers the "Inactive Properties" report (§10.1) and the 30-day staleness notification (§22.2), which is what keeps a broker's listings trustworthy.

### 2. Dependencies
**Depends on:** M-01, M-02, M-05, M-07 (bulk upload), M-09 (property quota), M-10 (export).
**Depended on by:** BR-01, BR-03, BR-05, BR-07, BR-08.

### 3. Database schema
`property`, `property_media` (data model §6).

### 4. Backend APIs
```
GET    /api/v1/properties?search=&locality[]=&type=&transactionType=&isHot=&dealState=
                          &priceMin=&priceMax=&sizeMin=&sizeMax=&sort=&cursor=
POST   /api/v1/properties
GET    /api/v1/properties/{id}                  → detail + linked customers + deals
PATCH  /api/v1/properties/{id}
DELETE /api/v1/properties/{id}                  → soft, guarded
POST   /api/v1/properties/{id}/restore
PATCH  /api/v1/properties/{id}/hot              {isHot}
PATCH  /api/v1/properties/{id}/verify           → sets property_updated_date = today
POST   /api/v1/properties/{id}/media            {mediaId, role, sortOrder}
POST   /api/v1/properties/{id}/media/reorder    {mediaIds[]}
DELETE /api/v1/properties/{id}/media/{mediaId}
GET    /api/v1/properties/localities            → distinct localities for the filter
POST   /api/v1/properties/export                {filters, scope, format}
POST   /api/v1/properties/import                → M-07
```

### 5. Frontend pages
`/broker/properties` (list), `/broker/properties/new`, `/broker/properties/{id}` (detail with tabs: Details · Photos · Interested Customers · Deals), `/broker/properties/{id}/edit`, `/broker/properties/import`.

### 6. Components
`PropertyList` (§5.1 table on desktop: Cover Photo · Name/Address · Type · Sale/Rent badge · Size · Price · Area/Locality · Hot? · Last Updated · Actions; **card grid on mobile** per §24.2), `PropertyFilterBar` (§5.1: search, locality multi-select, type, sale/rent, hot, deal status, sort by date added / price / last updated), `PropertyCard`, `PropertyForm` (**multi-step**: Basics → Location → Specifications → Owner → Media — 31 fields on one mobile screen is unusable), `PropertyTypeSelector` (**drives conditional fields**: floors/bedrooms/bathrooms/furnishing appear only for Flat/House), `SizeInput` (value + unit with live sqft conversion), `PriceInput` (Indian formatting, "₹42,00,000 (42 Lakh)" helper text), `HotToggle` (fire icon), `FacingSelector` (8-point compass picker), `VastuSelector`, `OwnerContactCard` (with call/WhatsApp buttons), `PhotoGrid` (drag reorder, cover badge — M-05), `MediaUrlInput` (video/tour, host-allowlisted with preview), `StaleBadge` ("Not updated in 45 days" with a one-tap "Verify now"), `ExportButton`, `PropertyQuotaBar`.

### 7. Business logic
- **All §5.2.1 fields implemented**, with conditional visibility by property type: `Plot` hides floors/bedrooms/bathrooms/furnishing; `Flat`/`House` show them.
- **`property_updated_date`** is the date of last physical verification, not the record's `updated_at`. A one-tap "Verify now" sets it to today. Properties over 30 days stale are badged in the list, surfaced on the dashboard, reported in §10.1, and notified per §22.2. This single field is what prevents a broker's listings from rotting.
- **Hot property** toggle drives a badge, a filter, and a dashboard card (§4.1).
- **Deal state** (`ACTIVE`/`CLOSED`/`CANCELLED`) is derived from the property's deals (BR-03) and drives the §5.1 filter.
- **Rent vs Sale:** for `RENT`, the price field relabels to "Monthly Rent" and the brokerage calculator defaults to a months-of-rent basis rather than a percentage of value.
- Size normalised to `size_sqft` for all filtering and sorting.
- Locality list built from distinct values, with autocomplete from prior entries — this keeps the area filter clean without forcing a fixed taxonomy.
- Property quota (§23.1: Free 10 / Pro 100 / Premium unlimited) checked pre-flight.
- Deletion blocked if a deal has reached `DEAL_FINALISED` or beyond.

### 8. User flow
```
Property Manager → + Add Property
  → Step 1: title "3BHK Flat Vijay Nagar", type Flat, Sale
  → Step 2: address, locality, city, pincode
  → Step 3: size 1450 sqft, price ₹65,00,000, negotiable, facing East, 3BHK/2 bath,
            floor 4 of 8, semi-furnished, corner, vastu yes
  → Step 4: owner name + mobile, updated date = today
  → Step 5: 8 photos (first = cover), YouTube walkthrough link
  → Save → appears in list with Hot toggle available
```

### 9. Permissions
Broker owner: full. (Structure supports future brokerage staff with the same M-02 engine.)

### 10. Edge cases
- Duplicate property (same address, same owner) → soft warning with a link, not a block; brokers legitimately relist.
- Property with 0 photos → allowed to save as a draft, but §5.2.2 requires ≥1 photo before it can be marked Hot or linked to a customer.
- Price of ₹0 → blocked for Sale; allowed for Rent only with an explicit "Price on request" flag.
- Owner mobile shared across many properties (a single landlord) → allowed; the owner's other listings are shown on the detail page, which is genuinely useful.
- Rent property with a sale-sized price (₹65,00,000/month) → confirmation prompt.
- Floor number > total floors → validation error.
- Property sold via a deal → `deal_state = CLOSED`, hidden from active filters by default but never deleted.
- Very large photo set on 4G → progressive loading, thumbnails first.
- Quota reached → Add disabled with an exact count and upgrade CTA.
- Locality typos creating near-duplicates ("Vijay Nagar" / "Vijaynagar") → autocomplete suggests existing values on type; a merge tool is a v2 item.

### 11. Validation rules
| Field | Rule |
|---|---|
| title | required, 3–150 |
| address_line1 | required, 5–255 |
| locality | required, 2–150 |
| city | required, 2–100 |
| pincode | optional, 6 digits, first ≠ 0 |
| property_type | required, ∈ {PLOT, FLAT, HOUSE} |
| transaction_type | required, ∈ {SELL, RENT} |
| size_value | required, > 0, ≤ 10,000,000 |
| size_unit | required, ∈ catalogue |
| price | required, > 0, ≤ 10,000 crore |
| property_updated_date | required, ≤ today, ≥ today − 5 years |
| owner_name | required, 2–120 |
| owner_mobile | required, 10 digits |
| total_floors | 0–200 |
| floor_number | 0 ≤ n ≤ total_floors |
| bedrooms / bathrooms | 0–50 |
| description | ≤ 5,000 |
| photos | ≥ 1 to publish, ≤ 20, ≤ 2MB each |
| video/tour URL | https, host ∈ allowlist |
| quota | Free 10 / Pro 100 / Premium ∞ |

### 12. Notifications (§22.2)
- Property not updated in 30 days → in-app + email, with a one-tap verify action.
- Property added → in-app confirmation.
- Property quota at 80% / 100% → in-app.
- Photo upload failed → in-app.

### 13. Future scalability
- Publish to portals (99acres, MagicBricks, Housing) via API — the single most requested broker feature.
- Public shareable property page / PDF one-pager for WhatsApp — near-free to build on the existing data and hugely used.
- Owner portal for listing verification.
- Duplicate detection and locality merge tooling.
- Price-history tracking and locality benchmarking.
- Matching engine: auto-suggest properties for each customer's budget/type/locality (the data model already supports it).

---

# BR-03 · Property ↔ Customer Linking & Deal Pipeline

**PRD:** §5.2.3, §5.2.4, §5.2.5, §5.2.6

### 1. Why this module exists
§5.2.3–§5.2.6 describe the broker's actual work: linking interested customers to a property, logging follow-ups per property-customer pair, tracking an 8-stage deal pipeline, and recording the brokerage received. This is a many-to-many relationship (one customer interested in several properties; one property with several interested customers), and the `deal` entity is what makes it tractable.

### 2. Dependencies
**Depends on:** M-12 (customers, interactions), M-06, M-11, BR-02.
**Depended on by:** BR-01, BR-05, BR-06, BR-07, BR-08.

### 3. Database schema
`deal`, `deal_stage_history`, `brokerage_receipt`, `customer_property_interest` (data model §6, §3).

### 4. Backend APIs
```
GET  /api/v1/properties/{id}/customers             → §5.2.3 linked customers w/ status
POST /api/v1/properties/{id}/customers             {customerIds[]}     → link existing
POST /api/v1/properties/{id}/customers/new         {customerPayload}   → create + auto-link
DELETE /api/v1/properties/{id}/customers/{customerId}

GET   /api/v1/deals?propertyId=&customerId=&stage=&archived=&cursor=
POST  /api/v1/deals                                {propertyId, customerId}
GET   /api/v1/deals/{id}
PATCH /api/v1/deals/{id}/stage                     {stage, note, dealValue?}
GET   /api/v1/deals/{id}/history                   → stage transitions
GET   /api/v1/deals/{id}/interactions              → §5.2.4 follow-up log for this pair
POST  /api/v1/deals/{id}/interactions              {occurredOn, remarks, nextFollowUpDate, result}

GET   /api/v1/deals/{id}/brokerage
POST  /api/v1/deals/{id}/brokerage  [Idempotency-Key]  {amount, receivedFrom, paymentDate, paymentMode, receiptNote}
PATCH /api/v1/brokerage-receipts/{id}
POST  /api/v1/brokerage-receipts/{id}/reverse      {reason}
```

### 5. Frontend pages
Property detail → **Interested Customers** tab; Customer detail → **Properties** tab; `/broker/deals/{id}` (deal detail).

### 6. Components
`InterestedCustomersPanel` (§5.2.3: Name · Contact · Budget · Current Status · Next Follow-up, with "Link Existing Customer" and "Add New Customer" buttons), `CustomerLinkPicker` (search with budget-fit indicator — shows whether the customer's budget covers the asking price, which is exactly the judgement a broker makes), `DealPipelineStepper` (§5.2.5: the 8 stages as a horizontal progress bar on desktop, a vertical stepper on mobile, with the current stage highlighted), `StageChangeDialog` (with a note and, for finalisation, the agreed deal value), `FollowUpTimeline` (§5.2.4: reverse-chronological log per property-customer pair), `AddFollowUpDialog` (§5.2.4 fields: date, remarks, next follow-up date, result), `BrokerageForm` (§5.2.6: amount, received from Owner/Buyer/Both, payment date, payment mode, receipt note), `BrokerageCalculatorLink` (opens M-08 prefilled with the deal value), `DealSummaryCard`, `QuickCallWhatsApp`.

### 7. Business logic

**The 8-stage pipeline (§5.2.5), exactly as specified:**
| # | Stage | Effect |
|---|---|---|
| 1 | Interested | Deal created |
| 2 | Call Done | — |
| 3 | Site Visit Done | Creates a site-visit calendar record |
| 4 | Follow Ups In Progress | — |
| 5 | Deal Cancelled | **Archives** the deal; property `deal_state` recalculated |
| 6 | Deal Finalised | Captures `deal_value`; property → `CLOSED`; brokerage becomes expected |
| 7 | Agreement / Registry Done | Legal milestone |
| 8 | Brokerage Received | **Archives** the deal; moves to Deals History (§9) |

- **Stages 5 and 8 move the customer out of the active pipeline into Deals History**, exactly per §5.2.5. Stages are not strictly linear — a broker may jump from Interested straight to Deal Finalised — and every transition is recorded in `deal_stage_history` with a timestamp and note. Moving *backwards* is allowed with a confirmation, because deals genuinely regress.
- **One deal per (property, customer) pair.** A customer interested in 4 properties has 4 deals with independent stages, which is the correct model — the same person can be at Site Visit on one property and Cancelled on another.
- **Follow-ups (§5.2.4) are logged against the deal**, so the timeline on the property shows exactly what was discussed about *that* property with *that* customer. A `nextFollowUpDate` propagates to `customer.follow_up_date` and the calendar (M-11).
- **Interactions are append-only** (§6.5) — a 15-minute amendment window, then permanent.
- **Brokerage (§5.2.6)** may be recorded in multiple receipts (part payments are common). Expected brokerage is optionally set at finalisation so BR-05's "Pending Brokerage" is meaningful. GST at 18% is computed and displayed separately per §3.4.2 but stored as its own field so it can be excluded from income figures.
- **Property `deal_state`** derives from its deals: any deal at stage ≥6 and not cancelled → `CLOSED`; all deals cancelled → `CANCELLED`; otherwise `ACTIVE`.
- Advancing to stage 8 without any brokerage receipt prompts "Record the brokerage amount?" — otherwise the income data silently goes missing, which defeats BR-05.

### 8. User flow
```
Property detail → Interested Customers → "Link Existing Customer"
  → search "Rajesh" → budget ₹60–70L vs asking ₹65L → good fit → link
  → deal created at stage Interested
  → Add Follow-up: "Called, wants a Sunday visit" · next Sunday · Positive
  → Sunday: stage → Site Visit Done
  → negotiation → stage → Deal Finalised · deal value ₹62,00,000
  → registry → stage → Agreement/Registry Done
  → payment → Record Brokerage: ₹1,24,000 · from Both · UPI
  → stage → Brokerage Received → archived to Deals History
```

### 9. Permissions
Broker owner: full. Brokerage records are immutable after 24 hours except by reversal.

### 10. Edge cases
- Same customer linked twice to the same property → prevented by the unique index; the existing deal opens instead.
- Property sold to customer A while customers B and C are still linked → their deals are prompted for closure ("This property was sold. Mark these 3 deals as cancelled?") — bulk action available.
- Deal finalised then cancelled → allowed with a reason; if brokerage was already received, a reversal is required and the UI states this explicitly.
- Customer deleted with an active deal → blocked (M-12 rule).
- Property deleted with a finalised deal → blocked.
- Brokerage recorded before finalisation → allowed (advances happen), flagged on the deal.
- Deal value far below the asking price (>40% variance) → confirmation prompt.
- Rent deals → brokerage is typically 1–2 months' rent, not a percentage; the form accepts a direct amount and the calculator offers a months-of-rent mode.
- Two brokers involved (co-brokerage) → v1 records only the net amount received, with the split noted in the receipt note; a proper co-broker split is a v2 item.
- Stage moved backwards → confirmation, logged, no data lost.

### 11. Validation rules
| Field | Rule |
|---|---|
| propertyId, customerId | must exist, in org, not deleted |
| stage | ∈ 8 enum values |
| deal_value | required when moving to `DEAL_FINALISED`; > 0 |
| stage note | required for `DEAL_CANCELLED` (5–500 chars) |
| interaction.remarks | required, 1–5,000 |
| interaction.occurred_on | required, ≤ today |
| next_follow_up_date | ≥ today if provided |
| brokerage.amount | > 0, ≤ deal_value (above 20% of deal value warns) |
| received_from | required, ∈ {OWNER, BUYER, BOTH} |
| payment_date | required, ≤ today |
| payment_mode | required, ∈ {CASH, BANK_TRANSFER, CHEQUE, UPI} |

### 12. Notifications (§22.2)
- Deal stage updated → in-app.
- Follow-up date reached → in-app + WhatsApp, 09:00.
- Site visit scheduled → in-app + WhatsApp, 1 day and 1 hour before.
- Follow-up overdue with no action → in-app + WhatsApp next morning.
- Brokerage received → in-app confirmation.

### 13. Future scalability
- Co-brokerage with split tracking.
- Deal-stage automation (auto-advance to Follow Ups In Progress after a logged call).
- Buyer–owner–broker three-way WhatsApp coordination.
- e-Agreement generation for brokerage terms (reusing B-11's document engine).
- Predicted close probability per deal from stage velocity.

---

# BR-04 · Broker Customer / Lead Manager

**PRD:** §6.1 – §6.5

### 1. Why this module exists
§6 is the broker's centralised pipeline across all properties. It is M-12 configured for the broker profile: multi-property interest instead of project/plot interest, no staff assignment, and the "Important Customer" gold-star feature (§6.4) as a prioritisation mechanism.

### 2. Dependencies
**Depends on:** M-12 (core), M-06, M-11, M-07 (import), M-10 (export), BR-02.
**Depended on by:** BR-01, BR-03, BR-05, BR-06, BR-07, BR-08.

### 3. Database schema
`customer`, `interaction`, `customer_property_interest` (M-12), using broker-specific columns: `preferred_property_type`, `preferred_locality`, `size_requirement`, `no_further_follow_up`, `is_important`.

### 4. Backend APIs
M-12's endpoints, scoped to the broker profile, plus:
```
GET /api/v1/broker/customers?search=&status=&propertyId=&followUp=today|week|overdue
                             &important=&sort=followUpDate|dateAdded|name|budget&cursor=
GET /api/v1/broker/customers/{id}/matches   → properties matching this customer's criteria
```

### 5. Frontend pages
`/broker/customers`, `/broker/customers/{id}`, `/broker/customers/new`, `/broker/customers/import`.

### 6. Components
M-12's set, configured for broker: `CustomerList` (§6.1 columns: ★ Important · Customer Name · Contact Number · Budget · Property Interested · Status · Follow-up Date (red if overdue) · Remarks (truncated) · Actions: Edit / Delete / Mark Important / View History), `CustomerFilterBar` (§6.1: search by name/phone, status, property interested in, follow-up range, important only, sort by follow-up date / date added / name / budget), `ImportantStar` (§6.4: gold star toggle, filterable), `PropertyInterestPicker` (multi-select from the broker's properties), `StatusBadge` (§6.3 colours: Interested blue · Site Visit Scheduled orange · Site Visit Done yellow · Following Up purple · Deal Closed green · Lost red), `MatchingPropertiesPanel` (properties fitting this customer's budget, type, and locality — one of the highest-value screens in the broker product), `InteractionTimeline` (§6.5), `AddInteractionDialog` (§6.5 fields: date, type Call/Visit/WhatsApp/Meeting, remarks, next follow-up date, result Positive/Neutral/Negative/Not Interested), `NoFurtherFollowUpToggle`, `QuickCallWhatsApp`.

### 7. Business logic
- **All §6.1 columns and filters implemented** exactly, including the red overdue styling on follow-up dates.
- **Status pipeline (§6.3)** with the six specified statuses and colours. `DEAL_CLOSED` and `LOST` set `closed_at` and move the customer to Deals History (§9) — filtered out of the active list by default, never deleted.
- **Important customer (§6.4):** toggleable at any time, gold star in the list, dedicated filter, and sorted to the top when the "Important only" filter is off but the sort is by priority.
- **Multi-property interest (§6.2):** a customer can be linked to several properties; each link creates a `deal` (BR-03) so pipelines are tracked per property, not per person.
- **`no_further_follow_up` (§6.2)** suppresses all reminders and removes the customer from the Follow-up Due report without changing their status — the correct model for "interested, but call me next year".
- **Interaction log (§6.5)** is append-only and timestamped; adding one with a next date updates `customer.follow_up_date` and the calendar.
- **Matching engine:** properties where `price BETWEEN budget_min AND budget_max`, type matches `preferred_property_type`, and locality matches `preferred_locality` — ranked by fit. Cheap to build on existing indexes, and it directly generates broker activity.
- Duplicate detection on mobile at create time.
- Customer quota (§23.1: Free 25 / Pro 500 / Premium unlimited).

### 8. User flow
```
Customer Manager → + Add Customer
  → Rajesh Kumar · 98xxxxxxx · budget ₹60–70L · prefers Flat in Vijay Nagar · 3BHK
  → source: Referral · status: Interested · follow-up: tomorrow · marked Important ★
  → Save → Matching Properties panel shows 4 fits
  → link two of them → two deals created
  → tomorrow 09:00: reminder → call → log: "Wants to see both on Sunday" · next Sunday
```

### 9. Permissions
Broker owner: full.

### 10. Edge cases
All M-12 edge cases, plus:
- Customer interested in a property that gets sold → flagged with alternatives from the matching engine.
- Budget range far outside all inventory → the matching panel says so, prompting the broker to source rather than showing an empty list.
- Customer marked Lost then returns → reopen prompt, history preserved.
- Quota reached → Add disabled with upgrade CTA.
- Bulk import with duplicate numbers → flagged in preview.
- 5,000 customers → cursor pagination, server-side search.

### 11. Validation rules
Per M-12, plus: `property_interested` IDs must belong to the broker's org; `preferred_property_type` ∈ enum; `size_requirement` ≤ 80 chars; quota per §23.1.

### 12. Notifications (§22.2)
- New customer added → in-app confirmation.
- Follow-up due today → in-app + WhatsApp at 09:00.
- Follow-up overdue → in-app + WhatsApp the next morning.
- Customer quota at 80% / 100% → in-app.

### 13. Future scalability
- Auto lead capture from a broker's website / portal enquiries / Facebook Lead Ads.
- Lead scoring and a prioritised daily call list.
- WhatsApp two-way inbox.
- Automated property recommendations pushed to the customer over WhatsApp.
- Referral tracking (which past customers send new ones).

---

# BR-05 · Brokerage Analysis

**PRD:** §7.1 – §7.4

### 1. Why this module exists
§7 is the broker's income view — the single reason many brokers will pay for the product. Six summary cards, a detailed receipts table, five filters, and three charts turn scattered commission payments into a picture of the business: what's earned, what's pending, and which properties actually make money.

### 2. Dependencies
**Depends on:** M-02, M-10 (export), BR-02, BR-03, BR-04.
**Depended on by:** BR-01, BR-08.

### 3. Database schema
No owned tables — reads `brokerage_receipt`, `deal`, `property`, `customer`.

```sql
CREATE INDEX ix_brokerage_date ON brokerage_receipt(org_id, payment_date DESC) WHERE deleted_at IS NULL;
CREATE MATERIALIZED VIEW mv_brokerage_monthly AS
SELECT org_id, date_trunc('month', payment_date) AS month,
       SUM(amount) AS total, COUNT(DISTINCT deal_id) AS deals
FROM brokerage_receipt WHERE deleted_at IS NULL GROUP BY 1,2;
```

### 4. Backend APIs
```
GET /api/v1/broker/brokerage/summary?from=&to=                → six §7.1 cards
GET /api/v1/broker/brokerage/receipts?from=&to=&propertyId=&customerId=&mode=&cursor=
GET /api/v1/broker/brokerage/monthly?months=12                → §7.4 bar chart
GET /api/v1/broker/brokerage/by-property-type                 → §7.4 pie chart
GET /api/v1/broker/brokerage/top-properties?limit=5           → §7.4 bar chart
GET /api/v1/broker/brokerage/pending                          → finalised, not yet received
POST /api/v1/broker/brokerage/export {filters, format}
```

**Summary (§7.1):**
```json
{
  "totalAllTime": "2840000",
  "thisMonth": "185000",
  "thisYear": "1240000",
  "dealsClosedThisMonth": 3,
  "avgBrokeragePerDeal": "94666",
  "pendingBrokerage": {"amount": "320000", "dealCount": 4}
}
```

### 5. Frontend pages
`/broker/brokerage`.

### 6. Components
`BrokerageSummaryCards` (six per §7.1), `MonthlyBrokerageChart` (§7.4: 12-month bar), `BrokerageByTypePie` (§7.4: Plot / Flat / House), `TopPropertiesChart` (§7.4: top 5 by brokerage), `BrokerageTable` (§7.2 columns: Date of Receipt · Customer Name · Property · Deal Value · Brokerage Amount · Received From · Payment Mode · Actions: View Detail / Edit), `BrokerageFilterBar` (§7.3: date range, month & year, property, customer, payment mode), `PendingBrokeragePanel` (deals finalised but not paid — with a "Send reminder" action to the owner/buyer), `ExportButton`, `GstToggle` (show figures inclusive or exclusive of the 18% GST — brokers need both for their own filing), `PaymentModeBreakdown`.

### 7. Business logic
- **All six §7.1 cards**: all-time total, this month, this year, deals closed this month, average per deal (`total ÷ distinct deals`), and pending brokerage (deals at `DEAL_FINALISED` or `AGREEMENT_REGISTRY_DONE` with no or partial receipts).
- **All three §7.4 charts** with the specified types.
- **All five §7.3 filters**, with export of the filtered set.
- **GST handling:** stored separately per receipt; the toggle switches every figure between gross and net. Brokers file GST on this, so mixing the two is a real problem.
- **Pending brokerage** is the module's most actionable output — it's money already earned and not collected. Each row offers a WhatsApp reminder to the paying party.
- Reversals included in all sums.
- Averages exclude zero-brokerage deals so the figure stays meaningful.

### 8. User flow
Brokerage Analysis → "This Month ₹1,85,000 · Pending ₹3,20,000" → tap Pending → 4 deals → one finalised 6 weeks ago → tap → "Send reminder" to the owner over WhatsApp → later record the receipt.

### 9. Permissions
Broker owner: full.

### 10. Edge cases
- No brokerage yet → informative empty state.
- Reversal making a month negative → shown as negative with explanation.
- Deal with brokerage but no deal value (rent deals often) → excluded from percentage-based charts, included in totals.
- Date range across a financial year → both calendar-year and Indian FY views.
- Very large or very small amounts → Indian abbreviated formatting with exact values on tap.
- Materialised view up to 15 minutes stale → the current month computed live.
- Property deleted after brokerage was earned → the receipt retains a name snapshot.

### 11. Validation rules
Read/aggregate. Filters: `from ≤ to`, range ≤ 10 years, IDs in scope, mode ∈ enum.

### 12. Notifications
- Monthly brokerage summary → email at month end.
- Pending brokerage older than 30 days → in-app reminder.

### 13. Future scalability
- Expense tracking (travel, marketing, office) for true net income.
- Income tax / GST filing exports.
- Target setting with progress tracking.
- Income forecasting from the active pipeline (deal value × stage probability × brokerage rate).
- Multi-agent brokerage with per-agent splits.

---

# BR-06 · Broker Calendar

**PRD:** §8.1 – §8.4

### 1. Why this module exists
§8 gives the broker one view of every commitment: auto-projected follow-ups and site visits, plus manually added meetings and important dates (agreement, registration).

### 2. Dependencies
**Depends on:** M-11 (engine), M-06, BR-02, BR-03, BR-04.
**Depended on by:** BR-01.

### 3. Database schema
`calendar_event` (M-11), with broker projections from `customer.follow_up_date` and site-visit stages.

### 4. Backend APIs
M-11's endpoints under `/api/v1/broker/calendar`.

### 5. Frontend pages
`/broker/calendar`.

### 6. Components
M-11's set: `CalendarMonthView` (§8.1: coloured dot / count badge per day), `CalendarWeekView`, `CalendarDayView`, `ViewSwitcher` (§8.1 Month / Week / Day), `DayEventList`, `AddEventDialog` (§8.3 fields: Event Title*, Date*, Time, Customer Linked, Property Linked, Notes, Reminder toggle), `EventTypeLegend`, `AgendaList` (mobile default).

### 7. Business logic
- **Four event types (§8.2):** Follow-up Reminder (auto from Customer Manager), Property Visit (auto when a site visit is scheduled for a customer on a property), Manual Meeting, Important Date (agreement, registration).
- Auto events are projections keyed to their source; rescheduling routes to the source record.
- Colour-coded by type with icons for accessibility.
- Overdue items pinned above today.

### 8. User flow
Calendar → 15 Aug shows 3 dots → tap → "Follow-up: Rajesh", "Site visit: 3BHK Vijay Nagar 4pm", "Registry: Plot Scheme 78" → tap the visit → property and customer details with call buttons.

### 9. Permissions
Broker owner: full.

### 10. Edge cases
Per M-11: past-dated follow-ups shown as overdue; deleted customers remove their events; many events per day collapse to a count; date-only events avoid UTC off-by-one; manual event deletion allowed, auto events not.

### 11. Validation rules
Per M-11: title required 2–150; date required; linked customer/property must exist in org; reminder offsets ∈ {0,1,3,7}.

### 12. Notifications (§8.4)
- In-app notification on the day of the event.
- WhatsApp on the morning of the event (if enabled in settings).
- Optional 1-day advance notification.
- Site visits additionally 1 hour before (§22.2).

### 13. Future scalability
Google Calendar sync, iCal feed, recurring events, drag-to-reschedule, travel-time-aware visit scheduling, shared availability link for customers to book a visit slot.

---

# BR-07 · Broker Deals History

**PRD:** §9.1, §9.2, §9.3

### 1. Why this module exists
§9 is the archive of every closed and cancelled deal. It is the broker's track record — used for their own analysis, for proving performance to owners, and as the record of last resort when a commission is disputed months later.

### 2. Dependencies
**Depends on:** M-02, M-10 (export), BR-02, BR-03, BR-04, BR-05.
**Depended on by:** BR-08.

### 3. Database schema
No owned tables — a read view over `deal` where `is_archived = true` (stage = `DEAL_CANCELLED` or `BROKERAGE_RECEIVED`), joined to property, customer, and brokerage receipts.

```sql
CREATE INDEX ix_deal_archive ON deal(org_id, COALESCE(cancelled_on, finalised_on) DESC)
  WHERE is_archived AND deleted_at IS NULL;
```

### 4. Backend APIs
```
GET /api/v1/broker/deals-history?status=&from=&to=&propertyType=&customerName=&cursor=
GET /api/v1/broker/deals-history/{dealId}       → §9.3 full detail
POST /api/v1/broker/deals-history/export        {filters, format}
```

### 5. Frontend pages
`/broker/deals`, `/broker/deals/{id}`.

### 6. Components
`DealsHistoryTable` (§9.1 columns: Deal Date · Customer Name · Customer Contact · Property · Deal Type Sale/Rent · Deal Value · Deal Status Closed/Cancelled · Brokerage Received · Remarks · Actions), `DealFilterBar` (§9.2: status, date range, property type, customer name), `DealStatusBadge`, `DealDetailView` (§9.3: complete customer info, property info, full follow-up timeline, deal pipeline stages **with dates**, brokerage details, uploaded documents), `DealTimeline` (vertical: linked → each follow-up → each stage change → brokerage received), `ExportButton`, `DealsSummaryStrip` (count and total brokerage for the filtered set).

### 7. Business logic
- **Entry criteria (§9):** deals at `DEAL_CANCELLED` or `BROKERAGE_RECEIVED` are archived here, exactly per §5.2.5.
- **§9.3 detail view** assembles every related record: the customer's full profile, the property snapshot as it was, the complete interaction timeline for that pair, the stage history with timestamps, all brokerage receipts, and any documents.
- **Property snapshot:** the property may have changed or been deleted since; the deal detail shows the values at deal time alongside a link to the current record if it still exists.
- Filters and export per §9.2.
- Read-only — corrections happen in BR-03 and flow through.

### 8. User flow
Deals History → filter: Closed, 2026, Flat → 12 deals, ₹14.2L brokerage → tap one → full timeline from first enquiry to payment → export the year for the accountant.

### 9. Permissions
Broker owner: full.

### 10. Edge cases
- Cancelled deal with brokerage already received → both shown; the reversal (if any) appears in the timeline.
- Property deleted after the deal → snapshot values retained.
- Customer deleted → deal retained with a snapshot name and contact.
- Same customer, same property, relisted and re-dealt → two separate deals, cross-linked.
- Very old deals → date filter defaults to the last 2 years for load performance.
- Rent deals with no percentage-based brokerage → shown as a flat amount.

### 11. Validation rules
Read-only. Filters: `from ≤ to`, range ≤ 10 years, status ∈ {CLOSED, CANCELLED}, property type ∈ enum.

### 12. Notifications
None originated.

### 13. Future scalability
- Client testimonial / rating capture after a closed deal.
- Repeat-customer identification and re-engagement prompts.
- Performance benchmarking (close rate, average days to close, average brokerage).
- Public "track record" page a broker can share with owners.

---

# BR-08 · Broker Reports & Stats

**PRD:** §10.1, §10.2

### 1. Why this module exists
§10.1 lists seven report types, each with its own filters, viewable on screen and downloadable as PDF or Excel with charts where applicable (§10.2). Together they let a broker answer questions about their inventory, pipeline and income without exporting everything to Excel manually.

### 2. Dependencies
**Depends on:** M-10 (report engine — this module is its broker configuration), M-02, M-09 (analytics tier, export entitlement), BR-02 … BR-07.
**Depended on by:** nothing (leaf). Built last in the Broker track.

### 3. Database schema
No owned tables. `report_definition` rows with `profile = BROKER`.

### 4. Backend APIs
M-10's endpoints with `profile=BROKER`:
```
GET  /api/v1/reports?profile=BROKER
POST /api/v1/reports/{code}/preview  {filters, page}
POST /api/v1/reports/{code}/export   {filters, format}
```

### 5. Frontend pages
`/broker/reports`, `/broker/reports/{code}`.

### 6. Components
M-10's set: `ReportCatalog` (seven cards), `ReportFilterPanel` (dynamic per definition), `ReportTable` (virtualised, mobile → cards), `ReportChart`, `ExportButton` (PDF / Excel / CSV per §10.2), `SavedFilterChips`.

### 7. Business logic

**All seven §10.1 reports, implemented as M-10 definitions:**

| Report | Contents | Filters (§10.1) | Charts |
|---|---|---|---|
| Property Summary | All properties with full details | Type, Status, Area, Price range, Date range | Pie by type; bar by locality |
| Customer Pipeline | All active customers with current status | Status, Follow-up date, Budget range | Funnel by status |
| Deals Closed | All closed deals with brokerage details | Date range, Property, Deal value range | Bar by month |
| Brokerage Income | Month-wise / year-wise brokerage earned | Date range, Payment mode, Property | Bar by month; pie by mode |
| Follow-up Due | Customers with follow-ups pending today / this week | Date, Status | — |
| Hot Properties | All properties marked Hot | Area, Type, Price | Bar by locality |
| Inactive Properties | Properties not updated in 30+ days | Date, Area, Type | Bar by staleness band |

- **§10.2 requirements:** each report renders as an on-screen table, downloads as PDF or Excel/CSV, and shows bar/pie charts where applicable.
- The Inactive Properties report is directly actionable — each row has a one-tap "Verify now" that updates `property_updated_date`, which is far better than a report the broker reads and forgets.
- Follow-up Due doubles as a work queue with inline "Log follow-up".
- Analytics entitlement (§23.1): Basic (Free) = Property Summary and Follow-up Due, on-screen only; Advanced (Pro) = all seven with export; Full (Premium) = all plus custom date ranges and saved views.

### 8. User flow
Reports → Inactive Properties → 14 properties not updated in 30+ days → sorted by staleness → verify 9 inline with one tap each → export the rest to call the owners.

### 9. Permissions
Broker owner: full. Export gated by `EXPORT_DATA` entitlement (Free plan: ❌ per §23.1).

### 10. Edge cases
Per M-10: empty results get an explanatory empty state; large exports become async jobs; CSV uses UTF-8 with BOM for Devanagari; CSV injection prevented; statement timeouts return a "narrow your range" message; wide reports become landscape PDFs and horizontally scrollable tables on mobile.

### 11. Validation rules
Per M-10: report code must exist and be permitted; date range ≤ 5 years with `from ≤ to`; format supported by the definition; requested columns a subset of the definition.

### 12. Notifications
- Export ready / failed → in-app.
- Weekly summary report → email (optional, off by default).

### 13. Future scalability
- Scheduled weekly/monthly emailed reports.
- Custom report builder for Premium.
- Owner-facing report sharing (send a property performance summary to the owner).
- Comparative benchmarking against anonymised platform averages.
