# Shardeya — Broker Network & Designation Commission Engine

**Extends and partly replaces B-14 (Broker Management).** Builder-side only in v1.
**Status: fully specified, pre-implementation. This is its own milestone (call it M6.5 / "Broker Network"), built AFTER the builder product ships to the first customer — not squeezed in before.**

This is one of the largest single features in the product: a recursive network engine with money attached (multi-level differential commission, promotion state, snapshotting, proportional release, reversals, concurrency safety). It must be built with full milestone discipline and real verification, not rushed.

---

## 0. Relationship to existing B-14 (from M6)

M6 built: broker partners with `PERCENTAGE` / `FIXED` commission and flat tiers (Bronze/Silver/Gold/Platinum by deal count), commission ledger, commission payments, recovery entries on cancellation.

This feature changes that as follows:
- **New brokers** get only two commission types: `PERCENTAGE` (unchanged from M6) or `DESIGNATION` (this engine). `FIXED` is retired for new brokers.
- The **flat tier system is replaced** by the 8-level designation hierarchy for `DESIGNATION` brokers.
- **Existing brokers** created under M6 (percentage/fixed/tier): must be migrated. Migration decision — keep percentage brokers as-is (they still work); fixed and tier brokers need an explicit migration path (see §12). Confirm the exact migration with the user before touching live data.
- `PERCENTAGE` brokers do **not** participate in the network hierarchy (no upline/downline, no differential, no designation) unless a future feature adds it.

---

## 1. Core model — the two lifecycle moments (the crux)

> **REVISED (supersedes the original completion-based rule).** Counting and promotion now happen at **BOOKED**, not at COMPLETED. The original design counted a sale toward personal/team sales and promotion only once fully paid; this was deliberately reversed by the user. The reasoning and consequences are recorded below so the history is clear.

Every designation-based booking has **two distinct moments that do different things**:

| Moment | Existing status | What happens |
|---|---|---|
| **BOOKED** | `plot_sale.status = BOOKED` (creation — the plot is marked sold, customer may have paid ₹0) | **Two things happen, in this exact order:** (1) the **entire commission tree is calculated and FROZEN** — selling broker's amount + every upline's differential + every same-slab bonus — using each broker's *current* rate at this instant; then (2) the booking **counts** as +1 toward the selling broker's personal sales and +1 toward the team sales of the seller and every ancestor up the chain, and **promotion is evaluated** for all of them. Instalment payments later release **proportional slices** of the frozen tree. |
| **COMPLETED** | `plot_sale.status = COMPLETED` (all instalments actually paid in full) | Marks the money as fully collected. **Does NOT trigger counting or promotion** — that already happened at BOOKED. It exists for financial tracking (balance fully settled) and to complete proportional commission release. |

**Critical ordering within BOOKED (freeze-before-promote — confirmed as "Option X"):** the commission tree for *this* booking is frozen at the broker's **pre-booking rate first**; only *after* that does this booking's own +1 count possibly promote the broker. So the booking that earns a promotion is itself paid at the *old* rate — the new (promoted) rate applies only to *future* bookings. This preserves the booking-time-rate rule (§6) exactly; the only change is that freeze and count now fire at the same instant (booking) instead of months apart.

**Why counting moved to booking:** the user's business rewards the broker for making the sale (buyer committed / plot sold), not for the builder's later collection of instalments. A broker should rank up when they close the sale, not wait months for the customer to finish paying.

**Consequences, accepted by the user:**
- A booking counts and can promote **even if the customer has paid ₹0 so far**. Commission still *releases* only as real money comes in (§8) — counting and release are decoupled.
- Because a booking counts immediately, a booking that later **cancels** is the primary thing that can reverse a count/promotion (§9 handles this — reverse count, demote if warranted, recover released money). This makes robust cancellation handling more important than before, not less.
- **Waiver is now moot for counting.** Under the old rule a waived-remainder completion was excluded from counting. Now counting happens at booking, so a waiver at the end changes nothing about the count — the booking already counted at BOOKED. Only a **cancellation** removes the count. (A waiver still means the sale never reaches "fully paid," so it simply never reaches COMPLETED — but that no longer affects broker counts.)

---

## 2. Commission types (three distinct kinds per booking)

```
SELLING_BROKER          — area × selling broker's own rate
UPLINE_DIFFERENTIAL     — area × (upline rate − direct downline rate), when positive
NETWORK_SAME_SLAB_BONUS — area × (next-slab rate − current-slab rate), when upline and direct downline are on the same slab (₹0 at the top slab ₹255)
```
Stored as separate commission records for accounting/reporting (§22, §40).

---

## 3. Commission base — AREA, not value

For `DESIGNATION` brokers, commission **ignores deal value entirely**:
```
commission = plot_area_sqft × rate_per_sqft
```
Two plots of identical area but very different price pay identical broker commission. (This is the opposite basis from `PERCENTAGE` brokers, who are value-based. The two never mix.)

`plot_area_sqft` uses the canonical `size_sqft` already stored on every plot (the dual-stored area from the core architecture — never the raw value+unit).

---

## 4. Sales counting — whole bookings, whole business

- A completed booking counts as **1**, regardless of area or value.
- **Personal sales** = bookings the broker personally sold that reached COMPLETED.
- **Team sales (total)** = personal + **all recursive downline** completed bookings (every descendant, not just direct children).
- Counted **across the builder's entire business** (all projects combined), not per project.
- A broker's **designation is global** to the builder, not per-project.

---

## 5. Designation slabs

| Total team sales (completed, before the next booking) | Designation | Rate ₹/sq.ft. |
|---:|---|---:|
| 0 | Business Executive | 160 |
| 1 | Senior Business Executive | 180 |
| 2 | Business Development Officer | 200 |
| 3–5 | Business Manager | 215 |
| 6–9 | Assistant Sales Director | 225 |
| 10–14 | Sales Director | 235 |
| 15–19 | Vice President | 245 |
| 20+ | President | 255 |

**These slabs must be data (a config table), not hardcoded `if/else`** (§47) — the builder may want to tune them, and hardcoding makes every change a code change.

---

## 6. Promotion timing (Rule 5 + 6 — critical)

- The booking uses the broker's rate **as it is immediately before that booking**.
- Within the BOOKED moment, the commission tree is **frozen first** (at the pre-booking rate), **then** the count increments and promotion is evaluated — so the booking that triggers a promotion is itself earned at the old rate, and the promoted rate applies to **future** bookings only.
- Example: team sales = 2 (Business Development Officer, ₹200). Broker's 3rd booking is **made/sold** → that booking's tree freezes at ₹200 → *then* team sales becomes 3 → promoted to Business Manager (₹215) → ₹215 applies to **future** bookings only. (This all happens at booking time now, not at completion.)

---

## 7. The commission calculation, precisely (frozen at BOOKED)

Given a booking by selling broker S, plot area `Q` sq.ft.:

```
S receives:  Q × rate(S)                          [SELLING_BROKER]

walk up the upline chain: for each (upline U, its direct downline D on this chain):
    if   rate(U) >  rate(D):  U receives Q × (rate(U) − rate(D))   [UPLINE_DIFFERENTIAL]
    elif rate(U) == rate(D):  U receives Q × (nextSlabRate(U) − rate(U))   [NETWORK_SAME_SLAB_BONUS]
                              (where nextSlabRate is the rate of the slab immediately above U's
                               current slab; if U is at the top slab ₹255, this is Q × 0 = ₹0)
    else (rate(U) < rate(D)): U receives 0
    continue up until an upline with no further upline
```

**Non-negotiable rules baked in:**
- Each upline compares **only** with its *direct downline on the chain*, never with the original seller (§17).
- Never negative — a lower-rated upline (possible via manual promotion/demotion) gets ₹0, never a negative number (§16, §33).
- The same-slab incentive is evaluated **independently at every level** (§21) and is **not capped** — total builder payout can exceed the seller's own rate, and that is intentional; do not redistribute or shrink it to force the total back down (§13, §15, §20).

> **REVISED same-slab formula (supersedes the flat ₹10).** When an upline and their direct downline are on the **same slab**, the incentive is no longer a flat ₹10/sq.ft. It is now **(rate of the next slab up) − (their current shared slab rate)**, per sq.ft. Everything else about the same-slab bonus is unchanged: still its own `NETWORK_SAME_SLAB_BONUS` type (never reclassified as a differential), still an additional/uncapped network incentive (never reduces or redistributes the seller's commission), still evaluated independently at every level of the chain. **Only the amount formula changed.** Nothing else in the engine changes — slab thresholds, rates, promotion timing, counting, release, payout, cancellation all stay exactly as they are.
>
> Lookup values ("next − current" per slab):
>
> | Same slab both on (₹/sq.ft.) | Next slab up | Same-slab incentive ₹/sq.ft. |
> |---:|---:|---:|
> | 160 | 180 | 20 |
> | 180 | 200 | 20 |
> | 200 | 215 | 15 |
> | 215 | 225 | 10 |
> | 225 | 235 | 10 |
> | 235 | 245 | 10 |
> | 245 | 255 | 10 |
> | 255 | (none) | 0 |
>
> **Top-slab edge case (mandatory):** if both are at ₹255 (top), there is no higher slab → incentive is exactly **₹0**. Never fabricate a higher slab, never produce a negative number, never fall back to the old ₹10. Handle it as an explicit zero.
>
> Worked example (both on ₹200, 1000 sq.ft. plot): seller earns 1000 × ₹200 = ₹2,00,000; a same-slab upline earns (₹215 − ₹200) = ₹15/sq.ft. → 1000 × ₹15 = ₹15,000. Two same-slab uplines each get ₹15,000 independently.
- All rates used are the **frozen booking-time rates**, snapshotted onto each commission record (§23).

Worked examples to use as test fixtures: §45 (differential chain 160/180/200), §46 (same-slab 215/215/215 → total ₹235k), §21 (mixed chain), §17 (four-level 160/180/215/235).

---

## 8. Proportional commission release on instalments

- The full commission tree amount is frozen at BOOKED but **not paid** yet.
- As the customer pays each instalment, the **same fraction** of every beneficiary's frozen commission becomes releasable/payable. Customer pays ¼ of plot value → ¼ of each commission (seller + every upline + every bonus) is released.
- Payment tracking is separate per beneficiary and per type (§25): each has Total / Paid / Pending. Every commission payment links to booking, broker, beneficiary, the customer payment that triggered it, project, plot.
- Reuse the existing immutable-payment + allocation patterns from B-05/M6 (payments never edited/deleted; corrections are reversals).

### 8a. Broker payout — the third state (Paid), and "Commission Due"

**Added after the engine was first built.** Release (customer-driven) and payout (builder actually handing the broker money) are two different things and must not be conflated. Three states now exist:

```
Earned    — frozen at BOOKED (the total the beneficiary will get)
Released  — unlocked as the CUSTOMER pays instalments (the cap on what can be paid out)
Paid      — the BUILDER recorded actually paying the broker (new; starts at 0)
```

- **Commission Due = Released − Paid.** This is the money the builder currently owes the broker and is cleared to pay (because the customer's corresponding money is in). This replaces the current "Released" figure shown on the broker screen as the headline the builder acts on. (Keep Released visible too if useful, but "Due" is the number the builder pays against.)
- **Payout is capped at Released, never Earned.** The builder can never pay a broker more than has been released by customer collection — this is the entire point (ties broker payout to collection, protects builder cash flow). A payout attempt exceeding Due is rejected.
- **"Record Payment" button** on the broker: pays against the broker's **whole Due balance**, auto-allocated **oldest-first across their due commission entries** — exactly the allocation pattern B-05 already uses for customer instalment payments. The builder records "I paid Lakshya ₹X"; the system spreads it across their oldest-due entries.
- **Payments are immutable; mistakes are reversals** — reuse the B-05/M6 reversal pattern. No editing/deleting a recorded broker payment; a wrong one is reversed.
- **Cancellation with money already paid out (extends §9):** if a booking is cancelled after the builder actually **paid** the broker (not just released), the recovery entry must track the **paid** amount as owed back by the broker, netting against their future payouts — same recovery pattern as §9, now covering real money-out, not just an un-release. A paid-then-cancelled slice is the strongest recovery case and must be handled, not just the released-then-cancelled one.

Data: a `broker_commission_payment` table (mirroring B-05's payment/allocation shape) with allocations linking each payout to the specific `booking_commission` entries it settles; `paid_amount` maintained per entry; `due = released − paid` derived. Immutable rows + reversal rows, tenant-scoped, concurrency-safe with the same lock-then-recompute discipline used everywhere else in the engine.

---

## 9. Cancellation (Rule 41 + user decision: RECOVER)

If a booking is cancelled after commissions were generated (and possibly partly paid):
- Create **reversal/recovery records** for every beneficiary in the tree (seller + all uplines + bonuses) — never delete history.
- Already-**paid** slices → recovery entries against each beneficiary (nets against their future commission), exactly like M6's existing broker-recovery pattern. This applies to the **whole upline chain**, even uplines who did nothing wrong.
- **Pending** (unreleased) slices → cancelled.
- If the booking had reached COMPLETED and counted toward sales → **reverse the sales counts** for the seller and every ancestor, and **recalculate designations** (a cancellation can, in principle, demote — designations recalc from the corrected counts). Record these as designation-history entries.
- All auditable; nothing destroyed.

---

## 10. Network structure & integrity

- Each `DESIGNATION` broker has **at most one direct upline** (`uplineBrokerId`, nullable = top-level). Multiple top-level brokers allowed under one builder.
- Upline is **explicitly selected at broker creation** — never inferred from the adder's own upline (adding B under A must set B.upline = A, even though A's upline is Me; Me is only an *indirect* upline of B). §31.
- **Integrity rules, enforced (not just UI):** no self-upline; no cycles (A→B→C→A); cannot move a broker under its own descendant; one direct upline only.
- **Closure table** `broker_network(ancestor_id, descendant_id, depth, builder_id)` (§38) for efficient recursive team-sales and tree queries — maintained transactionally on every add/re-parent. Recursive CTE is the fallback/verification path.
- Re-parenting (if allowed at all in v1) must re-validate all integrity rules and rebuild the affected closure rows.

---

## 11. Data model (new / changed)

**broker_partner (extend):**
```
commission_type            PERCENTAGE | DESIGNATION   (FIXED retired for new)
upline_broker_id           UUID NULL FK → broker_partner
current_designation        FK → designation_slab   (DESIGNATION only)
current_commission_rate    NUMERIC   (₹/sq.ft., snapshot of the slab rate)
personal_successful_bookings   INT
team_successful_bookings       INT   (recursive; closure-table maintained)
designation_manually_overridden BOOLEAN
```

**designation_slab (new, config — NOT hardcoded):**
```
id, builder_id (or NULL = system default), name, min_team_sales, max_team_sales (NULL = open top),
rate_per_sqft, sort_order, is_active
```
Seeded with the 8 rows from §5. GiST EXCLUDE constraint preventing overlapping ranges (same pattern M6 already used for tiers).

**broker_network (new, closure table):**
```
ancestor_broker_id, descendant_broker_id, depth, builder_id
```

**booking_commission (new — one booking → many rows):**
```
id, booking_id (plot_sale), selling_broker_id, beneficiary_broker_id, upline_level,
commission_type (SELLING_BROKER | UPLINE_DIFFERENTIAL | NETWORK_SAME_SLAB_BONUS),
beneficiary_designation_at_booking, beneficiary_rate_at_booking,
direct_downline_designation_at_booking, direct_downline_rate_at_booking,
plot_area_sqft, commission_per_sqft, commission_amount,
total_amount, released_amount, paid_amount, pending_amount,
status, created_at
```

**commission_release / commission_payment (extend M6):** link each release to the triggering customer payment; support per-beneficiary, per-type tracking.

**designation_history (new):**
```
id, broker_id, builder_id, previous_designation, new_designation,
previous_rate, new_rate, change_type (AUTOMATIC | MANUAL), reason,
effective_at, changed_by
```

All tenant-scoped (`org_id`/`builder_id`), soft-delete where applicable, RLS as everywhere.

---

## 12. Migration from M6

- Percentage brokers → unchanged.
- Fixed / tier brokers → need an explicit decision (convert to percentage? to a starting designation? leave frozen as legacy?). **Do not auto-migrate live financial data without the user confirming the mapping.** Historical M6 commission ledger entries must remain intact and unchanged (snapshot integrity).

---

## 13. Builder-side UI (v1 scope)

- **Broker creation flow** (§30): info → commission type (Percentage / Designation) → if Designation, select upline (or none = top-level); new broker starts Business Executive / ₹160 / 0 / 0.
- **Network tree view** (§28): each node shows name, designation, personal sales, team sales, rate. Builder sees the whole network.
- **Builder/admin dashboard** (§29): full network, upline/downline, personal & team sales, current designation & rate, the three commission kinds, earned/paid/pending, promotion history, network growth, direct+indirect downline counts.
- **Manual promotion** (§34): changes designation + rate going forward; does NOT change sales counts, does NOT create fake bookings, does NOT alter historical commissions; freezes auto-evaluation (override flag); recorded in designation_history.
- **Broker-facing dashboard/portal (§26–27) is DEFERRED** — not in v1.

---

## 14. Concurrency (§43)

Multiple bookings completing simultaneously for the same broker/downline must not: lose a count, double-promote, assign a wrong designation, or snapshot a wrong rate. Use DB transactions + row locking (or optimistic concurrency) around: sales-count increment, closure-table team-sales rollup, designation evaluation, and commission-tree snapshotting. This is the single most bug-prone area — it needs dedicated concurrent-execution tests, not just sequential ones.

---

## 15. Build order (when this milestone runs)

1. designation_slab config table + seed + overlap constraint.
2. broker_partner extensions + broker_network closure table + integrity validation (self/cycle/descendant/one-upline).
3. Broker creation with upline selection; network tree query + UI.
4. Commission calculation engine (frozen tree at BOOKED) — pure, heavily unit-tested against §45/§46/§21/§17 fixtures BEFORE wiring to anything.
5. Wire freeze into the sale/BOOKED transaction (this touches PlotSaleService again — the already-fragile path; full sale-lifecycle regression required).
6. Proportional release on instalment payments.
7. COMPLETED → sales-count increment + recursive upline team-sales rollup + designation evaluation + promotion history.
8. Manual promotion.
9. Cancellation → recovery across the chain + sales reversal + designation recalc.
10. Builder dashboards (network, per-broker, promotion history).
11. Concurrency hardening + concurrent tests.
12. Migration of existing M6 brokers (with user-confirmed mapping).

Each step verified for real (browser + real HTTP + real data), Hindi + 360px, per the project's standing bar.

---

## 16. Test fixtures (must all pass, verified against real data)

- §45: 200/180/160 chain, 1000 sq.ft. → B ₹160k, A ₹20k, Me ₹20k.
- §46 (revised for new formula): 215/215/215, 1000 sq.ft. → B ₹215k, A ₹10k bonus (215→225 = ₹10), Me ₹10k bonus, total ₹235k (NOT capped to 215). [At ₹215 the next-minus-current happens to still be ₹10; use a ₹200 case too to prove the formula really varies.]
- NEW same-slab fixture: 200/200/200, 1000 sq.ft. → seller ₹200k, each same-slab upline (215−200)=₹15/sq.ft → ₹15k each.
- NEW same-slab fixture: 160/160, 1000 sq.ft. → same-slab upline (180−160)=₹20/sq.ft → ₹20k.
- NEW top-slab fixture: 255/255, any area → same-slab incentive = ₹0 (no higher slab, no fallback to ₹10, no negative).
- §21: 215/215/200/200/160 five-level mixed differential + same-slab at multiple levels (recompute expected bonuses with the new formula).
- §17: 235/215/180/160 four-level, compare-with-direct-downline-only.
- §33: upline rate < downline rate → upline ₹0 (never negative).
- §11/§24: promotion applies only after the booking; the triggering booking uses the old rate.
- §35: one downline completion promotes multiple uplines at once.
- Cancellation: recovery entries across a 3-level chain after partial release; sales counts reversed; designations recalced.
- Concurrency: two simultaneous completions on the same upline don't lose a count or double-promote.
