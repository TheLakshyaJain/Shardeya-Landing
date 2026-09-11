# Shardeya — Foundation Modules (M-01 … M-14)

Every module follows the same 13-point template. Foundation modules are shared by both profiles and must be built before profile modules that depend on them.

---

# M-01 · Identity, Tenancy & Authentication

**PRD:** §2.1, §2.2, §2.3, §25.2

### 1. Why this module exists
Nothing in Shardeya can be built without knowing *who* is acting and *which tenant's* data they may touch. §25.1 makes data isolation a product promise, not just a security control — a broker seeing another broker's pipeline is a business-ending bug. This module owns signup, OTP, login, session lifecycle, and the `TenantContext` every other module reads.

### 2. Dependencies
**Depends on:** nothing (root module).
**Depended on by:** everything.
**External:** SMS gateway (MSG91/Gupshup/Twilio) for OTP, SMTP for email.

### 3. Database schema
`organization`, `app_user`, `refresh_token`, `otp_challenge`, `role`, `role_permission` (see `01-DATA-MODEL.md` §1).

### 4. Backend APIs
```
POST   /api/v1/auth/signup                  {fullName, mobile, email, password, confirmPassword, role, city, acceptTerms}
                                            → 202 {challengeId, maskedMobile, resendAfterSeconds}
POST   /api/v1/auth/otp/verify              {challengeId, code} → 200 {accessToken, refreshToken, user, org}
POST   /api/v1/auth/otp/resend              {challengeId} → 202
POST   /api/v1/auth/login                   {identifier, password, rememberMe} → 200 {tokens...}
POST   /api/v1/auth/login/otp/request       {mobile} → 202 {challengeId}
POST   /api/v1/auth/login/otp/verify        {challengeId, code} → 200
POST   /api/v1/auth/password/forgot         {identifier} → 202
POST   /api/v1/auth/password/reset          {token|challengeId, code, newPassword, confirmPassword} → 200
POST   /api/v1/auth/refresh                 {refreshToken} → 200 (rotates)
POST   /api/v1/auth/logout                  → 204
GET    /api/v1/me                           → profile + org + role + permissions[] + entitlements + unreadCount
PATCH  /api/v1/me                           {fullName, email, city, language}
POST   /api/v1/me/password                  {currentPassword, newPassword}
POST   /api/v1/me/avatar                    → media upload intent
```

### 5. Frontend pages
`/signup`, `/signup/verify`, `/login`, `/login/otp`, `/forgot-password`, `/reset-password`, `/terms`, `/privacy`.

### 6. Components
`AuthLayout` (brand panel + form panel, stacks on mobile), `RoleSelectCard` (§2.1 — two large tappable cards with icon, label, selected border state), `PhoneInput` (+91 prefix locked, numeric keypad on mobile, 10-digit mask), `PasswordInput` (show/hide toggle, strength meter), `OtpInput` (6 boxes, auto-advance, paste-fill, `autocomplete="one-time-code"` so Android autofills from SMS), `ResendTimer` (60s countdown), `TermsCheckbox`, `FormError`.

### 7. Business logic
- **Signup** creates `organization` + `app_user(is_owner=true)` + `role` assignment + `subscription(plan=FREE, status=ACTIVE)` in one transaction, but only **after** OTP verification. Before verification, the payload lives in Redis under `challengeId` (TTL 15 min) — this prevents half-created orgs from abandoned signups.
- **Role choice is immutable after signup.** Broker↔Builder are structurally different tenants. A user wanting to switch must create a second account (documented in the UI at the point of selection: "You can't change this later").
- **Login identifier** is mobile-or-email: if `^\d{10}$` → mobile lookup, else email lookup.
- **Password hashing** Argon2id. On successful login with a legacy/weaker hash, transparently rehash.
- **Remember Me** → refresh token TTL 30 days; unchecked → 12 hours.
- **Refresh rotation with reuse detection:** each refresh issues a new token and marks the old `replaced_by`. Presenting an already-replaced token revokes the entire `family_id` and forces re-login (stolen-token containment).
- **Failed logins:** 5 failures → 15-minute lock, exponential thereafter. Always return the same generic error regardless of whether the user exists (no enumeration).
- **Staff login** (§2.3): `role = BUILDER_*` and `is_owner=false` → same Builder dashboard, permissions applied by M-02.

### 8. User flow
```
Signup → fill form → validate → POST /signup → OTP screen (6 boxes, 60s resend)
   → verify → org+user created → JWT issued → route by org.type
                                              ├── BROKER  → /broker/dashboard
                                              └── BUILDER → /builder/dashboard

Login → identifier + password → JWT → route by org.type + role
   └── "Login with OTP" → mobile → OTP → JWT

Forgot → identifier → (mobile: OTP | email: signed link, 30 min, single-use)
   → new password → all refresh tokens revoked → /login
```

### 9. Permissions
Public endpoints: signup, login, otp, forgot, reset. Everything else requires a valid access token. `/me` requires only authentication. Password change requires the current password even for owners.

### 10. Edge cases
- Mobile already registered → "This number is already registered. Log in instead?" with a link. Do **not** reveal which role.
- Email registered but mobile not (or vice versa) → block signup, direct to login/recovery.
- OTP requested 3× in an hour → hard block for 1 hour with a clear message + "contact support".
- User closes the tab mid-OTP → challenge expires in 15 min, must restart. Redis payload garbage-collected.
- SMS gateway down → fall back to a second provider; if both fail, return 503 with a specific `code=OTP_DELIVERY_FAILED` and offer email OTP for signups that supplied email.
- Clock skew on device → OTP validity is server-side only.
- User deleted while holding a valid access token → JWT carries `token_version`; incrementing it on the user record invalidates outstanding access tokens within 15 min. For immediate revocation, a Redis denylist keyed on `jti`.
- Org in `PENDING_DELETION` → login allowed (so they can cancel), but all writes rejected with a banner offering "Cancel deletion".
- Suspended org (non-payment/abuse) → login allowed, read-only, upgrade/contact CTA.

### 11. Validation rules
| Field | Rule | Error key |
|---|---|---|
| fullName | required, 2–100 chars, letters/spaces/`.'-` only | `error.name.invalid` |
| mobile | required, exactly 10 digits, first digit 6–9 | `error.mobile.length` |
| email | required, RFC 5322 lite, ≤255, lowercased, no disposable domains | `error.email.invalid` |
| password | ≥8 chars, ≥1 letter + ≥1 digit, not in top-10k breached list, ≠ mobile/name | `error.password.weak` |
| confirmPassword | must equal password | `error.password.mismatch` |
| role | required, ∈ {BROKER, BUILDER} | `error.role.required` |
| city | required, 2–100 chars | `error.city.required` |
| acceptTerms | must be true | `error.terms.required` |
| otp | exactly 6 digits, ≤5 attempts | `error.otp.invalid` |

Hindi messages are keyed, e.g. `error.mobile.length` → "मोबाइल नंबर 10 अंकों का होना चाहिए" (§24.4 demands specificity).

### 12. Notifications
- Welcome (in-app + email) on signup completion.
- OTP via SMS (transactional template, DLT-registered for India).
- Password changed → email + in-app + SMS ("If this wasn't you, contact support").
- New device login → in-app + email with device/IP/time.
- Account locked → email.

### 13. Future scalability
- `user_org_membership` join table already designed → multi-org accounts and account switching without data migration.
- OIDC/social login slots into the same `app_user` via a `federated_identity` table.
- 2FA (TOTP) — `user_mfa` table, same OTP UI component.
- Regional sharding: `org_id` is a UUID with no location semantics; a `shard_map` lookup can be introduced at the connection-routing layer without schema change.

---

# M-02 · RBAC & Permission Engine

**PRD:** §18.3, §2.3, §25.1

### 1. Why this module exists
§18.3 defines five roles with a 6-dimension permission matrix, plus per-project scoping (§18.2) and a "own leads only" data filter for Sales Executives. Scattering these `if` statements across controllers guarantees an inconsistency that leaks financial data. One engine, declaratively applied.

### 2. Dependencies
**Depends on:** M-01.
**Depended on by:** every module with a mutating or reading endpoint; especially B-08, B-12, B-14.

### 3. Database schema
`role`, `role_permission`, `user_project_access`, plus a `permission` reference catalogue.

**Permission catalogue** (code → meaning):
```
DATA_VIEW_ALL            DATA_VIEW_OWN
DATA_CREATE              DATA_EDIT_ALL           DATA_EDIT_OWN
DATA_DELETE
FINANCIAL_VIEW           FINANCIAL_RECORD_PAYMENT    FINANCIAL_EDIT
TEAM_VIEW                TEAM_MANAGE
PROJECT_CREATE           PROJECT_EDIT            PROJECT_DELETE
PLOT_CREATE              PLOT_EDIT               PLOT_DELETE       PLOT_BULK_UPLOAD
LEAD_ASSIGN
BROKER_VIEW              BROKER_MANAGE           BROKER_COMMISSION_PAY
REPORT_VIEW_ALL          REPORT_VIEW_OWN         REPORT_FINANCIAL
DOCUMENT_GENERATE        DOCUMENT_TEMPLATE_EDIT
SENSITIVE_VIEW           EXPORT_DATA             IMPORT_DATA
SETTINGS_MANAGE          SUBSCRIPTION_MANAGE
```

**Role → permission mapping (materialising §18.3):**

| Permission group | Admin (Owner) | Manager | Sales Exec | Accounts | View Only |
|---|---|---|---|---|---|
| View all data | ✅ | ✅ | own leads only | financials only | ✅ |
| Add/Edit data | ✅ | ✅ | own leads only | financials only | ❌ |
| Delete data | ✅ | ❌ | ❌ | ❌ | ❌ |
| Financial access | ✅ | ✅ | ❌ | ✅ | ❌ |
| Team management | ✅ | ❌ | ❌ | ❌ | ❌ |
| Reports | ✅ all | ✅ all | own only | financial only | view only |
| Broker management | ✅ | ✅ | ❌ | commission ledger view | ❌ |
| Document generation | ✅ | ✅ | ❌ | receipts only | ❌ |
| Sensitive (Aadhaar/bank) | ✅ | ✅ | ❌ | ✅ | ❌ |
| Subscription | ✅ | ❌ | ❌ | ❌ | ❌ |

`BROKER_OWNER` (broker profile) gets every permission except the Builder-only ones.

### 4. Backend APIs
```
GET  /api/v1/permissions/me            → {permissions[], projectScope[], scopeMode}
GET  /api/v1/roles                     → available roles for this org type
GET  /api/v1/roles/{code}/permissions  → matrix for the UI
```
Enforcement is annotation-driven, not endpoint-driven:
```java
@RequiresPermission("FINANCIAL_RECORD_PAYMENT")
@ScopedToProject(param = "projectId")
public PaymentResponse recordPayment(...)
```

### 5. Frontend pages
No dedicated page. Surfaces inside B-12 (Admin Panel) as the read-only permission matrix shown when picking a role.

### 6. Components
`<Can permission="X">` render guard, `useCan()` hook, `<PermissionMatrix>` (read-only table with ✅/❌), `<ProjectScopeSelector>` (multi-select), `<RestrictedBadge>` (lock icon + tooltip explaining who can do this).

### 7. Business logic
- **Three composable predicates** applied in the repository layer, never in controllers:
  1. `org_id = :ctx.orgId` (always)
  2. project scope: if `scopeMode=SCOPED` → `project_id IN :ctx.projects`
  3. ownership: if user lacks `DATA_VIEW_ALL` but has `DATA_VIEW_OWN` → `assigned_to = :ctx.userId OR created_by = :ctx.userId`
- **UI hiding is not security.** Every hidden control has a server-side check. A permission test suite asserts each endpoint × each role.
- **Foreign resource ⇒ 404, not 403.** A Sales Executive probing another exec's lead ID must not learn it exists.
- **Owner is unrevokable.** `is_owner` cannot be demoted; ownership transfer is an explicit two-step flow (owner initiates, target confirms via OTP).
- **Permission changes take effect within 15 minutes** (access-token TTL) or immediately via the Redis denylist. The affected user sees a toast: "Your access has been updated" and the app refetches `/me`.

### 8. User flow
Builder Admin → Admin Panel → Add Team Member → pick role → matrix preview renders → optionally restrict project access → invite sent. Staff logs in and sees only their permitted sidebar links.

### 9. Permissions
`TEAM_MANAGE` to assign roles. Nobody can grant a permission they don't hold (no privilege escalation).

### 10. Edge cases
- Last admin cannot be demoted/removed → blocked with explanation.
- Staff loses project access mid-session while viewing that project → next API call 404s; client shows "You no longer have access to this project" and redirects.
- Sales Exec's lead is reassigned to someone else → it vanishes from their list. They keep read access to *their own* historical interactions (audit integrity) but not to the live lead.
- Role deleted (custom roles, future) → users fall back to `VIEW_ONLY`, owner notified.
- Deactivated staff → sessions revoked immediately; their historical `created_by`/`conducted_by` references remain intact.
- A Sales Exec must still be able to record a **site visit outcome** on a plot they don't "own" — solved by scoping on the *lead*, not the plot.

### 11. Validation rules
- Role code must exist and be valid for the org type.
- If `scopeMode=SCOPED`, at least one project must be selected.
- Cannot assign `BUILDER_ADMIN` to more than one user unless the org allows co-admins (setting, default off).

### 12. Notifications
- "Staff added or role changed" → in-app + email to the affected staff member (§22.3).
- Owner notified when any role change occurs.

### 13. Future scalability
- Custom roles per org: `role.org_id` is already nullable → build a role builder UI on the same tables.
- Field-level permissions (e.g. hide `deal_value` from Sales Exec) via a `role_field_restriction` table; the DTO serializer already routes through a masking layer.
- ABAC: predicates are composable objects, so "only leads from my city" is a new predicate class.

---

# M-03 · Localization (i18n)

**PRD:** §3.3, §24.5, §1.3

### 1. Why this module exists
§24.5 requires *every* label, button, heading, placeholder, and **error message** in English and Hindi (proper Devanagari, no transliteration), switching instantly without reload, remembered across logins, and architecturally ready for Tamil/Telugu/Marathi. Retrofitting i18n is the single most expensive rework in a project like this — it must be a day-one constraint.

### 2. Dependencies
**Depends on:** M-01 (per-user language preference).
**Depended on by:** every UI module, M-06 (notification templates), M-10 (report headers), B-11 (document templates).

### 3. Database schema
`app_user.language`, `organization.default_language`. Server-side message bundles live in `messages_en.properties` / `messages_hi.properties` (resource files, versioned in git, not the DB). `notification_type` and `report_definition` carry `*_key` columns resolved against the same bundles.

### 4. Backend APIs
```
GET /api/v1/i18n/{lang}/{namespace}.json   (public, long-cache, ETag)
PATCH /api/v1/me  {language}
```
Backend **never returns a rendered sentence** for validation or notification content — it returns `{code, messageKey, params}`. Rendering is the client's job. Exception: WhatsApp/SMS/email, rendered server-side using the recipient's stored language.

### 5. Frontend pages
None. A `LanguageToggle` in the top bar (§3.1) on every screen.

### 6. Components
`LanguageToggle` (EN | हिं pill switch), `I18nProvider`, `<Trans>` for interpolated/rich text, `useFormatters()` returning locale-aware number (`1,23,456` Indian grouping), currency (`₹`), date (`24 जुलाई 2026`), and relative-time formatters.

### 7. Business logic
- Namespaces per module (`common`, `auth`, `property`, `plot`, `finance`, `broker`, `errors`, `notifications`) — lazily loaded with the route chunk, so Hindi bundles don't bloat the initial payload.
- Toggle = `i18n.changeLanguage()` → React re-renders. **No reload, no refetch** (data is language-neutral).
- Preference persisted to `localStorage` immediately (instant on next boot) and to the server via a debounced `PATCH /me` (survives device change).
- **User-entered data is never translated** (§3.3) — only keys under the i18n namespaces. Enforced by lint rule: no `t()` call may receive a variable that originated from an API data field.
- **Enum labels are keys, not values.** `plot.status = 'SOLD'` renders via `t('plot.status.SOLD')` → "बिका हुआ". The DB never stores display text.
- Pluralisation via ICU MessageFormat (Hindi has 2 plural forms; some future languages have more).
- Missing key in production → falls back to English and logs to a `missing_translations` telemetry endpoint (never shows a raw key to a user).

### 8. User flow
Any screen → tap EN/हिं → entire UI switches in <100ms → next login, same language.

### 9. Permissions
None — available to all authenticated users, and on the public auth pages.

### 10. Edge cases
- Devanagari text is taller and wider than Latin — every button/card/table header must be tested at Hindi length. CI runs a "pseudo-Hindi" visual regression pass (longest-string expansion) to catch overflow before Hindi copy exists.
- Mixed content: a Hindi UI with English property names is normal and correct — don't "fix" it.
- Number input: users may type Devanagari digits (१२३). Normalise on input.
- Font: bundle `Noto Sans Devanagari` subset (~40KB woff2) with `font-display: swap`; do not rely on Android system fonts, which vary.
- RTL is not needed now but no layout may hardcode `left/right` — use logical properties (`margin-inline-start`) so a future Urdu is not a rewrite.
- Exported Excel/PDF must respect language for **headers only**; data stays as entered.

### 11. Validation rules
- CI fails if `messages_hi.properties` is missing any key present in `messages_en.properties`.
- CI fails on any hardcoded user-facing string in JSX (ESLint `i18next/no-literal-string`).
- Translation files must be valid ICU.

### 12. Notifications
Notification body rendered at *send* time in the recipient's language for WhatsApp/SMS/email; rendered at *view* time for in-app (so a user who switches language sees old notifications in the new language).

### 13. Future scalability
- Adding Tamil = add `messages_ta.properties` + a locale entry + toggle option. Zero code change. This is the explicit §3.3 requirement.
- Translation management: files are flat key→value, so TMS integration (Crowdin/Lokalise) is a CI hook.
- Per-tenant terminology overrides (some builders say "unit", others "plot") via an `org_term_override` table layered on top of the bundle at resolve time.

---

# M-04 · App Shell, Navigation & Settings

**PRD:** §3.1, §3.2, §24.2

### 1. Why this module exists
§3.1 and §3.2 define a persistent frame — top bar with logo, page title, language toggle, notification bell, profile menu, settings, subscription badge; plus a collapsible sidebar that becomes a hamburger on mobile. Building this once, correctly, is what makes 30 feature screens feel like one product.

### 2. Dependencies
**Depends on:** M-01, M-02 (which links to show), M-03, M-06 (bell count), M-09 (plan badge).

### 3. Database schema
`app_user.language`, plus `user_ui_preference (user_id, sidebar_collapsed, density, theme)`.

### 4. Backend APIs
Served by `GET /api/v1/me` (single bootstrap call returning user, org, role, permissions, entitlements, unread count, plan badge) — one round trip on app load, cached by TanStack Query.

### 5. Frontend pages
Layout routes: `/broker/*` and `/builder/*` wrap all authenticated screens.

### 6. Components
`AppShell`, `TopBar`, `Sidebar` (desktop) / `MobileDrawer` (hamburger), `NavItem` (icon + label + active state + permission guard), `PageHeader` (title + breadcrumb + primary action slot), `NotificationBell` (badge count, dropdown panel), `ProfileMenu` (View Profile / Edit Profile / Change Password / Logout — §3.1), `SettingsSheet`, `SubscriptionBadge` (Free/Pro/Premium pill → §23.2), `CommandPalette` (⌘K global search, desktop), `BottomNav` (mobile: 5 most-used destinations), `Breadcrumb`, `EmptyState`, `ErrorBoundary`, `OfflineBanner`.

### 7. Business logic
- Sidebar items are **declarative config** filtered by `permissions` + `entitlements` + `org.type`. A Sales Executive simply never renders "Financials".
- Broker sidebar (§4.2): Dashboard, Property Manager, Customer Manager, Brokerage Analysis, Calendar, Deals History, Reports & Stats, Calculator, Settings, Subscription.
- Builder sidebar (§11.2): Dashboard, Manage Projects, Customer/Lead Manager, Financials/Accounts, Calendar, Deals History, Reports & Legal Docs, Calculator, Admin Panel, Follow-up & Collection Tracker, Broker Management, Stats & Analysis, Subscription.
- Mobile: sidebar → drawer (swipe or hamburger); the 5 highest-frequency destinations also appear in a persistent bottom nav (Dashboard, Projects/Properties, Customers, Calendar, More).
- Page title in the top bar is set by each route via a `usePageTitle()` hook — also sets `document.title`.
- Settings sheet holds: notification preferences (M-06), language, account details, data export/deletion (M-13).

### 8. User flow
Login → shell mounts → `/me` resolves → sidebar renders permitted links → route renders inside.

### 9. Permissions
Each nav item declares `requiredPermission`. Direct URL navigation to a forbidden route renders a 403 page with a "Go to dashboard" action (not a blank screen).

### 10. Edge cases
- 360px width (§24.2 minimum): top bar collapses to logo + title + bell + avatar; language toggle and settings move into the profile menu.
- Very long page titles truncate with ellipsis and a tooltip.
- Notification badge >99 shows "99+".
- Sidebar state persists per device.
- Offline (common on Indian 4G) → banner + queued mutations retried; read views served from TanStack Query cache with a "showing cached data" marker.
- Deep link while logged out → store intended URL, restore after login.
- Session expiry mid-form → refresh attempted silently; if it fails, a modal preserves the form state and offers re-login without data loss. **This matters:** losing a half-filled 30-field plot form is a churn event.

### 11. Validation rules
N/A (structural module).

### 12. Notifications
Hosts the bell. Real-time updates via SSE (`/api/v1/notifications/stream`) with polling fallback every 60s.

### 13. Future scalability
- Nav config is data — a future per-tenant menu customisation is a table read.
- Shell is profile-agnostic; a third profile (e.g. "Channel Partner" portal) plugs in a new nav config.
- PWA: shell is the app-shell cache target; installability + push notifications are additive.

---

# M-05 · Media & Document Storage

**PRD:** §5.2.2, §12.2.1, §12.3.3, §24.3, §25.2

### 1. Why this module exists
Photos, layout maps, brochures, agreements, registry deeds, and **government ID scans** all flow through one pipeline with wildly different sensitivity. §25.2 requires Aadhaar/PAN and financial documents to be "stored securely with restricted access" — that means a separate, encrypted, audited path, not just another row.

### 2. Dependencies
**Depends on:** M-01, M-02 (`SENSITIVE_VIEW`), M-09 (storage quota).
**Depended on by:** BR-02, B-02, B-03, B-04, B-11.

### 3. Database schema
`media_asset`, `property_media`, `plot_document`, plus `sensitive_access_log`.

### 4. Backend APIs
```
POST /api/v1/media/upload-intent   {filename, mimeType, sizeBytes, purpose, entityType, entityId}
                                    → {mediaId, uploadUrl, headers, expiresIn}
POST /api/v1/media/{id}/complete    → {mediaId, status}
GET  /api/v1/media/{id}             → {url (pre-signed), derivatives, meta}
GET  /api/v1/media/{id}/download    → 302 to pre-signed URL (audited for sensitive)
DELETE /api/v1/media/{id}
POST /api/v1/properties/{id}/media/reorder  {mediaIds[]}
```

### 5. Frontend pages
None standalone; embedded in every form with attachments.

### 6. Components
`ImageUploader` (drag-drop + camera capture on mobile, client-side compression, progress ring, retry), `PhotoGrid` (thumbnail grid, drag-to-reorder via dnd-kit, cover badge on first, §5.2.2), `DocumentUploader` (typed slots: Sale Agreement / Registry / Plot Map / ID Proof / Other+label), `FilePreview` (image lightbox, PDF inline viewer), `SensitiveFileGuard` (blurred until an explicit "Reveal" click that writes an access log), `MediaQuotaBar`.

### 7. Business logic
- **Client compresses before upload** (§24.3 max 2MB): target 2MB, max edge 2560px, quality ladder 0.9→0.6. Original never uploaded if oversized.
- **Direct-to-S3 via pre-signed PUT.** Bytes never touch the app server → no request-size limits, no memory pressure, works on flaky connections with resumable multipart for >5MB.
- **Server validates by magic bytes**, not extension or client-supplied MIME.
- **Derivatives:** thumb 200w, card 600w, full 1600w, WebP + JPEG fallback, generated async.
- **EXIF GPS stripped** — a property photo should not leak the broker's home coordinates.
- **AV scan** before `READY`. `REJECTED` assets are purged.
- **Sensitive class** (`BUYER_ID_PROOF`, bank docs): separate bucket, SSE-KMS, no derivatives, 5-minute pre-signed URLs, `SENSITIVE_VIEW` permission required, every access written to `sensitive_access_log`, excluded from bulk exports unless explicitly requested by the owner.
- **Orphan reaping:** `media_asset` rows in `PENDING` for >24h with no parent are deleted from S3 and DB.
- Cover image = `property_media.sort_order = 0`; reorder is a single transactional bulk update.

### 8. User flow
Add Property → tap "Add Photos" → gallery/camera → compress (progress) → parallel upload (max 3 concurrent) → thumbnails appear → drag first photo to set cover → Save.

### 9. Permissions
Upload requires `DATA_CREATE`/`DATA_EDIT_*` on the parent entity. Sensitive view requires `SENSITIVE_VIEW`. Delete requires `DATA_DELETE` (or ownership of an unattached upload).

### 10. Edge cases
- Upload interrupted (tunnel, lift, train) → resumable multipart + retry with backoff; UI shows per-file state, not a global spinner.
- User uploads a 40MB DSLR photo → compressed client-side; if the browser can't (very old device), server rejects >10MB with a specific message.
- HEIC from iPhone → converted to JPEG client-side (heic2any) or server-side fallback.
- 20-photo cap (§5.2.2) enforced client and server.
- Deleting the cover photo → next photo auto-promotes to cover.
- PDF that's actually an executable → magic-byte check rejects.
- Same file uploaded twice → checksum dedupe reuses the S3 object, creates a new `media_asset` row (different entity ownership).
- Video: prefer URL over upload; if uploaded, cap 100MB and transcode to 720p H.264 async.
- URL fields (`video_url`, `virtual_tour_url`, `google_maps_url`) validated against a host allowlist to prevent the property detail page becoming an open redirect / XSS iframe vector.

### 11. Validation rules
| Field | Rule |
|---|---|
| Photos | ≥1 required on property (§5.2.2 "Min 1"), ≤20, each ≤2MB post-compression, jpeg/png/webp/heic |
| Brochure | PDF only, ≤10MB |
| Video file | mp4/mov, ≤100MB |
| Video/tour URL | https, host ∈ allowlist |
| Layout map | PDF/PNG/JPEG, ≤20MB |
| ID proof | PDF/JPEG/PNG, ≤5MB, forced sensitive class |
| Other document | label required, 2–120 chars |

### 12. Notifications
- Upload failed after retries → in-app.
- AV rejection → in-app with the filename and reason.
- Storage quota at 80% / 100% → in-app + email.

### 13. Future scalability
- CDN in front of the standard bucket (Cloudflare/CloudFront) with signed cookies for a whole session.
- On-the-fly image resizing service replaces pre-generated derivatives.
- OCR on uploaded ID/registry documents to prefill buyer fields (high value for §12.3.2 data entry, and a natural v2 feature).
- Cold-tier lifecycle: documents untouched for 2 years → S3 IA/Glacier.

---

# M-06 · Notification Engine

**PRD:** §22 (all), §8.4, §12.3.4, §19, §20.4

### 1. Why this module exists
§22 defines ~20 distinct triggers across 4 channels (in-app, WhatsApp, SMS, email) with user-configurable preferences, plus **outbound messages to buyers who are not users of the platform** (§22.4). This is a genuine subsystem, not a utility class. Doing it inline in business services would make every payment write depend on WhatsApp uptime.

### 2. Dependencies
**Depends on:** M-01, M-03 (templates per language), M-09 (WhatsApp is Pro+ only, §23.1).
**Depended on by:** essentially every module.
**External:** WhatsApp Business API (Meta Cloud API or a BSP like Gupshup/Interakt), SMS gateway with DLT registration, SMTP.

### 3. Database schema
`notification`, `notification_type`, `notification_preference`, `outbox_event`, `message_delivery`, `whatsapp_optin`.

### 4. Backend APIs
```
GET    /api/v1/notifications?cursor=&unreadOnly=       → list
GET    /api/v1/notifications/unread-count
POST   /api/v1/notifications/{id}/read
POST   /api/v1/notifications/read-all
GET    /api/v1/notifications/stream                    → SSE
GET    /api/v1/settings/notifications                  → matrix of type × channel
PUT    /api/v1/settings/notifications                  → bulk update
POST   /api/v1/settings/whatsapp/opt-in                {mobile} → OTP-confirmed opt-in
POST   /api/v1/notifications/send-reminder             {entityType, entityId, channel}  // manual "Send WhatsApp Reminder" (§14.3, §19.2)
```

### 5. Frontend pages
`/settings/notifications` (preference matrix). Bell panel lives in M-04.

### 6. Components
`NotificationPanel` (grouped Today / Earlier, infinite scroll, mark-all-read), `NotificationItem` (icon by type, relative time, deep link to entity), `NotificationPreferenceMatrix` (rows = event types grouped by category, columns = 4 channel toggles, mandatory ones locked), `WhatsAppOptInCard`, `SendReminderButton` (with confirm dialog + cost/quota hint).

### 7. Business logic

**Trigger catalogue** — implemented exactly as specified:

*Broker (§22.2)*
| Event | Channels | Timing |
|---|---|---|
| Follow-up date reached | In-app + WhatsApp | 09:00 IST on the day |
| Site visit scheduled | In-app + WhatsApp | 1 day before, 1 hour before |
| Deal stage updated | In-app | Immediate |
| New customer added | In-app | Immediate |
| Property not updated 30 days | In-app + Email | Daily scan, 09:00 |
| Subscription expiry | In-app + Email | T−7, T−1 |
| Follow-up overdue (no action) | In-app + WhatsApp | 09:00, day after due date |

*Builder (§22.3)*
| Event | Channels | Timing |
|---|---|---|
| Instalment due today | In-app + WhatsApp | 09:00 IST |
| Instalment overdue 3+ days | In-app + WhatsApp | 09:00, day 3, then weekly |
| Follow-up due today | In-app + WhatsApp | 09:00 |
| New lead added | In-app | Immediate → owner + assigned staff |
| Broker tier upgraded | In-app (builder) + In-app/WhatsApp (broker) | Immediate |
| Broker commission due | In-app + Email | On deal close |
| Staff added / role changed | In-app + Email | Immediate → affected staff |
| Plot marked Sold | In-app | Immediate → owner + relevant staff |
| Subscription expiry | In-app + Email | T−7, T−1 |
| Legal document generated | In-app | On completion |

*Buyer, WhatsApp (§22.4)* — templated, pre-approved with Meta:
- Payment received: "Dear {name}, your payment of ₹{amount} for Plot {plotNo}, {project} has been recorded. Balance: ₹{balance}. Thank you."
- Instalment due: "Dear {name}, your instalment of ₹{amount} for Plot {plotNo} is due on {date}. Please arrange the payment. Contact: {builderContact}"
- Booking confirmation: "Dear {name}, your booking for Plot {plotNo}, {project} is confirmed. We will be in touch shortly."

**Pipeline:** business write → `outbox_event` (same tx) → poller → resolve recipients → check `notification_preference` + entitlement + opt-in + quiet hours → render template in recipient language → enqueue per channel → provider adapter → record `message_delivery` → webhook updates status.

**Deduplication:** `(type_code, entity_id, business_date)` key in Redis prevents a restart of the daily job double-sending. Idempotency is mandatory here — a builder WhatsApping a buyer twice about the same instalment is a real reputational cost.

**Digest:** if a user would receive >5 of the same type in one run (e.g. 40 instalments due today), collapse into one message: "You have 40 instalments due today totalling ₹32,00,000. Open the Collection Tracker."

**Channel fallback (§22.1):** WhatsApp fails or not opted in → SMS *only for critical alerts* (payment confirmation, overdue). Informational notifications never fall back to paid SMS.

**Quiet hours:** no WhatsApp/SMS before 08:00 or after 21:00 IST; queued to the next window.

### 8. User flow
Builder sets an instalment due 15 Aug → scheduler at 09:00 on 15 Aug → in-app bell + WhatsApp to builder → separately, a buyer reminder to the buyer's number (if opted in) → builder taps notification → lands on the Collection Tracker filtered to that plot → taps "Record Payment".

### 9. Permissions
Users see only their own notifications. Manual sends require `DATA_EDIT_*` on the entity and `WHATSAPP_ENABLED` entitlement. Buyer-facing sends require `FINANCIAL_RECORD_PAYMENT` or `DATA_EDIT_ALL`.

### 10. Edge cases
- WhatsApp 24-hour session window: outside it only **approved template messages** may be sent. All buyer messages are templates by design.
- Template rejected by Meta → fall back to SMS, alert platform admin, surface a status in the builder's settings.
- Buyer replies "STOP" → `opted_out_at` set, all future sends to that number blocked, builder sees an "Opted out" flag next to the number.
- Wrong/disconnected number → delivery webhook `FAILED` → flag on the buyer record: "WhatsApp not reachable".
- User disables all channels for a type → still stored in-app if `is_mandatory` (security, subscription, payment confirmations cannot be silenced).
- Timezone: an org that sets a different `notification_hour` shifts its whole schedule.
- Massive orgs: notification fan-out is batched and rate-limited per provider quota.
- Deleted entity → notification links to a "This item no longer exists" page rather than 500ing.
- Daily job crashes halfway → outbox rows remain `PENDING`, retried; dedupe prevents duplicates.

### 11. Validation rules
- Mobile must be verified + opted in before WhatsApp.
- Template variables must all resolve; a missing variable blocks the send and raises an internal alert (never send "Dear null").
- Preference updates: mandatory types cannot be disabled for in-app.
- Manual reminder: max 1 per entity per 24h (anti-harassment + cost).

### 12. Notifications
(This module *is* notifications. Its own meta-alerts: delivery failure rate >10% → platform admin alert.)

### 13. Future scalability
- Channel adapters behind a `NotificationChannel` interface → adding push (FCM), Telegram, or RCS is one class.
- Extract dispatcher to its own service when volume warrants; the outbox contract is already the boundary.
- Per-tenant WhatsApp Business numbers (currently one platform number) — `org_whatsapp_config` table.
- User-defined notification rules ("alert me when any plot in Project X sells") via a rule-engine table.

---

# M-07 · Import Engine (Bulk Upload)

**PRD:** §3.6, §12.4, §1.3

### 1. Why this module exists
§1.3 makes bulk upload a first-class principle, and §12.4 makes it the *primary* way a builder onboards a 500-plot project. Nobody types 500 plots into a form. The quality of this feature determines whether a builder finishes onboarding — it is an activation-critical path, not a convenience.

### 2. Dependencies
**Depends on:** M-01, M-02 (`IMPORT_DATA`), M-05 (file storage), M-09 (`BULK_UPLOAD_ENABLED` is Pro+).
**Depended on by:** BR-02, B-06, M-12.

### 3. Database schema
`import_job`, `import_row`.

### 4. Backend APIs
```
GET  /api/v1/import/template?entityType=PLOT&projectId=...   → 302 to generated .xlsx
POST /api/v1/import/jobs        {entityType, projectId, mediaId} → {jobId}
GET  /api/v1/import/jobs/{id}                                   → status + counts
GET  /api/v1/import/jobs/{id}/rows?status=INVALID&cursor=       → preview rows + errors
PATCH /api/v1/import/jobs/{id}/rows/{rowId}   {data}            → inline fix, revalidate
POST /api/v1/import/jobs/{id}/commit  {skipInvalid: true}       → starts import (idempotent)
POST /api/v1/import/jobs/{id}/cancel
GET  /api/v1/import/jobs/{id}/error-report                      → .xlsx of failed rows w/ reasons
GET  /api/v1/import/jobs?entityType=&cursor=                    → history
```

### 5. Frontend pages
`/builder/projects/{id}/plots/import`, `/broker/properties/import`, `/customers/import`.

### 6. Components
`ImportWizard` (4 steps: Download template → Upload → Preview & fix → Commit), `TemplateDownloadCard` (with a "sample row" explanation panel), `FileDropzone`, `ImportPreviewTable` (virtualised, invalid rows red with per-cell error tooltips, editable inline), `ImportErrorSummary` (grouped by error type: "12 rows: duplicate plot number"), `ImportProgress` (live via SSE), `ImportResultCard` (imported / skipped / download error report), `ImportHistoryList`.

### 7. Business logic
- **Template is generated, not static.** It embeds the org's valid enum values as Excel data-validation dropdowns (facing, status, unit), a locked header row, an instructions sheet in the user's language, and — for plots — the target project pre-filled. This kills 80% of errors before they happen.
- **Two-phase: validate → preview → commit.** §3.6 and §12.4 both require a preview before import. Never import blind.
- **Streaming parse** (Apache POI SXSSF read / OpenCSV) — a 5,000-row file must not load fully into memory.
- **Per-row validation** produces structured errors `{column, code, messageKey, params}` so the preview can render them in Hindi.
- **Duplicate handling:** within-file duplicates flagged; against-DB duplicates offer a mode choice: `SKIP` / `UPDATE_EXISTING` / `FAIL`. Default `SKIP` with a clear count.
- **Inline correction** in the preview table, revalidated live — much better than "fix your file and re-upload".
- **Partial commit** (§3.6: *"Successfully validated rows are imported; failed rows shown separately"*): valid rows import, invalid rows are downloadable as a pre-filled error workbook with a "Reason" column.
- **Chunked commit** in batches of 200 inside separate transactions, with progress. A failure at row 3,000 does not roll back the first 2,999 — the job records exactly where it stopped and is resumable.
- **Entitlement pre-check:** if importing 400 plots would exceed the plan cap, the wizard shows this *before* upload, with the exact overage and an upgrade CTA. Never let a user do 20 minutes of work and then fail.
- **Plot-specific (§12.4):** grid position may be given as `grid_row`/`grid_col`, or auto-derived from plot number patterns (`A-12` → block A, index 12) using a configurable strategy, or left unplaced for manual arrangement in B-03.

### 8. User flow
```
Project → Bulk Upload Plots → Download Template (pre-filled with project)
  → fill offline in Excel → Upload
  → server validates → Preview: 487 valid, 13 invalid
  → fix 9 inline, decide to skip 4
  → Commit → progress bar → "487 plots imported. 4 skipped."
  → Download error report for the 4
  → Grid renders
```

### 9. Permissions
`IMPORT_DATA` + create permission on the target entity + project scope. Builder Admin and Manager only; Sales Executive cannot bulk-import.

### 10. Edge cases
- Wrong template version → detected via a hidden version cell; rejected with "Please download the latest template".
- Extra/reordered columns → matched by header name, not position; unknown columns ignored with a warning.
- Empty file / header-only → clear message, not a crash.
- Merged cells, formulas, formatted numbers → values read (not formulas); currency strings like `₹ 12,50,000` normalised.
- Devanagari digits and Hindi enum values accepted (a Hindi-UI user will type `उपलब्ध`) — enum matching is locale-aware.
- 50,000-row file → hard cap at 10,000 per job with guidance to split; job runs async either way.
- Two concurrent imports into the same project → second is queued; plot-number uniqueness is enforced at the DB, so a race produces a clean per-row error, not corruption.
- Browser closed mid-import → job continues server-side; user returns to the history page and sees the result.
- Upload succeeds but commit never called → job auto-expires after 7 days, file purged.
- Mixed units in one file (some plots in sqft, some in gaj) → fully supported; `size_sqft` normalises.

### 11. Validation rules
**Plots (§12.3.1):** `plot_number` required + unique within project; `size_value` > 0; `size_unit` ∈ catalogue; `status` ∈ {AVAILABLE, RESERVED, SOLD}; `price` ≥ 0; `facing` ∈ 8 values or blank; if `status=SOLD` then buyer name + mobile + purchase date required; `reserved_for` required if `status=RESERVED`; `grid_row`/`grid_col` within grid bounds and not double-occupied.
**Properties (§5.2.1):** title, address1, locality, city, type, transaction type, size, price, owner name, owner mobile, updated date all required; owner mobile 10 digits.
**Customers (§6.2/§13.2):** name, mobile, budget min/max required; `budget_max ≥ budget_min`; status ∈ enum; follow-up date not in the past (warning, not error).

### 12. Notifications
- Import complete → in-app with counts, deep link to results.
- Import failed → in-app + error report link.
- Long import (>2 min) → email on completion so the user can walk away.

### 13. Future scalability
- Column mapping UI (map arbitrary headers to fields) for builders with existing spreadsheets in their own format — the biggest real-world friction point after v1.
- Scheduled/recurring imports from a Drive folder.
- API-based import for tenants with existing ERP.
- Import of documents in bulk (zip of PDFs matched to plot numbers by filename).

---

# M-08 · Calculators

**PRD:** §3.4.1, §3.4.2, §3.4.3

### 1. Why this module exists
Three calculators (§3.4) that brokers and builders currently do on paper or a phone calculator, with a real error rate. Also a low-cost, high-frequency engagement surface — and the stamp duty calculator is the only place the platform holds regulated reference data, which §3.4.3 explicitly requires to be admin-updatable.

### 2. Dependencies
**Depends on:** M-03 (unit names in Hindi), M-14 (admin-managed rates).
**Depended on by:** BR-02 (size entry can invoke the plot calculator), B-03, B-14 (brokerage preview).

### 3. Database schema
`measurement_unit`, `stamp_duty_rate` (see data model §2). Optionally `calculation_history (id, org_id, user_id, calc_type, inputs JSONB, outputs JSONB, created_at)` — useful and cheap.

### 4. Backend APIs
```
GET  /api/v1/calc/units?stateCode=UP                → unit list with factors
POST /api/v1/calc/plot-size        {length, width, unit}
POST /api/v1/calc/brokerage        {dealValue, brokeragePct, ownerSharePct?, buyerSharePct?}
POST /api/v1/calc/stamp-duty       {stateCode, propertyType, transactionType, value, buyerGender}
GET  /api/v1/calc/stamp-duty/states                 → states with current rates + last updated
POST /api/v1/calc/history                            → save
GET  /api/v1/calc/history?type=&cursor=
```
> Plot-size and brokerage maths also run **client-side** for instant feedback; the server endpoint exists for consistency, history, and any future audit. Stamp duty is **server-only** — rates must never be stale in a cached bundle.

### 5. Frontend pages
`/calculators` with three tabs (§3.4): Plot Size, Brokerage, Stamp Duty.

### 6. Components
`CalculatorTabs`, `UnitToggle` (ft/m), `NumericInput` (Indian grouping, numeric keypad), `ResultCard` (large primary figure, secondary conversions), `ConversionTable`, `RateDisclaimer` (with `effective_from` date and source), `CopyResultButton`, `ShareOnWhatsAppButton`, `CalcHistoryDrawer`.

### 7. Business logic

**Plot Size (§3.4.1):** `area = length × width` in the chosen unit. Outputs: **Sq Ft, Sq M, Bigha, Gunta, Dismil** — plus Sq Yd (Gaj), which is the unit most Indian plot buyers actually use. Bigha resolves against the user's org `state_code` and displays which regional definition was applied ("Bigha (UP standard)") — silently picking one is a correctness bug.

**Brokerage (§3.4.2):**
```
total = dealValue × brokeragePct / 100
ownerShare = dealValue × ownerSharePct / 100     (if provided)
buyerShare = dealValue × buyerSharePct / 100     (if provided)
gst = total × 18 / 100                           (shown separately, §3.4.2)
netPlusGst = total + gst
```
If both split percentages are given they need not sum to `brokeragePct` (real deals charge each side independently) — but if they do sum to something different, show an informational note, not an error.

**Stamp Duty (§3.4.3):** look up `stamp_duty_rate` by `(state, propertyType, transactionType, gender)` effective today, falling back to `gender=ANY`. Outputs: **Stamp Duty, Registration Charges, Total Government Charges**, with registration honouring `flat` / `pct` / `cap` semantics (many states cap registration at ₹30,000). Always render the disclaimer: rates are indicative, effective from {date}, confirm with the sub-registrar.

### 8. User flow
Sidebar → Calculators → tab → enter values → results update live → Copy / Share on WhatsApp (a broker sending a client "stamp duty on ₹45L in UP = ₹…" is the actual use case).

### 9. Permissions
All authenticated users, both profiles. No entitlement gate — calculators are free on every plan (they drive habit formation).

### 10. Edge cases
- Zero or negative dimensions → inline error, no result.
- Absurd values (10,000 ft × 10,000 ft) → computed but flagged "Please check — this is {n} acres".
- State with no rate row → "Rates for {state} are being updated. Please check with your local sub-registrar." Never guess.
- Rate changed mid-session → server always authoritative; the response carries `effectiveFrom` so a stale client is visibly stale.
- Floating point → all maths in `BigDecimal`, rounded to whole rupees for currency, 2 decimals for area.
- Bigha ambiguity → resolved by state with the definition shown; if the org has no state set, prompt once.
- Offline → plot-size and brokerage still work (client-side); stamp duty shows a cached result with an "offline, may be outdated" marker.

### 11. Validation rules
| Field | Rule |
|---|---|
| length, width | > 0, ≤ 100000, max 2 decimals |
| dealValue | > 0, ≤ 10,000 crore |
| brokeragePct | 0–100, max 3 decimals |
| ownerSharePct / buyerSharePct | 0–100 |
| propertyValue (stamp duty) | > 0 |
| state, propertyType, transactionType, gender | required, ∈ enum |

### 12. Notifications
None routinely. Platform admin is alerted when any state's rates pass `effective_to` without a successor row (prevents silently serving expired rates).

### 13. Future scalability
- EMI / home-loan calculator, ROI calculator, rent-yield calculator — same tab pattern.
- Circle-rate lookup by locality (big value-add; needs a data partnership).
- Embeddable public calculator widget for a broker's own website — a genuine lead-generation feature and a marketing channel for Shardeya.
- GST rate becomes a config row rather than a constant (rates change).

---

# M-09 · Subscription & Entitlements

**PRD:** §23.1, §23.2, §3.1 (badge)

### 1. Why this module exists
§23.1 gates 13 different capabilities across three tiers. Every gate must be enforced server-side, checked cheaply (on every write), and communicated to the user *before* they hit the wall — a user who fills a 30-field form and then learns they're at their limit will churn.

### 2. Dependencies
**Depends on:** M-01, M-06 (expiry reminders).
**Depended on by:** every module that creates entities or uses gated features.
**External:** payment gateway (Razorpay — UPI/cards/netbanking, standard for Indian SaaS).

### 3. Database schema
`plan`, `plan_limit`, `subscription`, `subscription_payment`, `org_usage`.

### 4. Backend APIs
```
GET  /api/v1/subscription              → plan, status, period, autoRenew, usage[]
GET  /api/v1/subscription/plans        → comparison matrix (§23.1)
GET  /api/v1/subscription/usage        → [{limitKey, used, limit, pct}]
POST /api/v1/subscription/checkout     {planCode, billingCycle} → gateway order
POST /api/v1/subscription/webhook      (gateway → us, signature-verified)
POST /api/v1/subscription/cancel       {atPeriodEnd: true}
PATCH /api/v1/subscription/auto-renew  {enabled}
GET  /api/v1/subscription/payments     → history
GET  /api/v1/subscription/invoice/{id} → PDF
```

### 5. Frontend pages
`/subscription` (§23.2: current plan, expiry, usage stats, upgrade button, payment history, auto-renew toggle, cancel), `/subscription/plans` (comparison), `/subscription/checkout`, `/subscription/success`.

### 6. Components
`PlanBadge` (top bar), `PlanComparisonTable` (the full §23.1 matrix, mobile → stacked cards with a plan switcher), `UsageMeter` (e.g. "67 of 100 properties used" §23.2, colour-shifting at 80%/100%), `UpgradePrompt` (contextual modal naming the exact blocked action), `PaymentHistoryTable`, `InvoiceDownload`, `AutoRenewToggle`, `CancelFlow` (retention step → confirm → what-you-lose summary), `FeatureLockOverlay` (blurs a gated feature with a lock + upgrade CTA rather than hiding it — users should know what they're missing).

### 7. Business logic
- **Entitlements resolved once per request** from a Redis-cached snapshot keyed `org:{id}:entitlements` (invalidated on plan change). Checking a limit must not cost a query.
- **Two gate types:** *quota* (count-based: properties, customers, projects, plots-per-project, team members, brokers) and *boolean/tier* (export, bulk upload, WhatsApp, legal docs, analytics, backup).
- **Quota checks read `org_usage`, never `COUNT(*)`.** Counters maintained by triggers; a nightly reconciliation catches drift.
- **Pre-flight, not post-hoc:** the "Add" button is disabled with an explanatory tooltip when at limit; the create endpoint also rejects (defence in depth) with `code=QUOTA_EXCEEDED` and a payload containing `{limitKey, used, limit, upgradeTo}` so the client can render a precise upgrade prompt.
- **Plots-per-project is per project**, not org-wide (§23.1) — a Pro builder may have 5 projects × 500 plots.
- **Downgrade with excess data:** never delete user data. Instead the org enters `OVER_LIMIT` mode: existing data stays fully readable and exportable, but **no new** entities of the exceeded type can be created until they're under the limit. Shown as a persistent banner with exact numbers.
- **Grace period:** 7 days after expiry → read-only + banner. Day 8–30 → login and export only. Day 30+ → account suspended, data retained 90 days.
- **Free plan has no expiry** — it's a permanent tier, not a trial.
- **Webhook is the source of truth** for payment state, signature-verified, idempotent by gateway event ID. Never activate a plan from a client-side success callback.
- **Proration** on upgrade: credit unused days of the current plan against the new one.

### 8. User flow
```
Builder on Free tries to create a 2nd project
  → button disabled, tooltip "Free plan allows 1 project"
  → tap → UpgradePrompt: "Upgrade to Pro for 5 projects"
  → /subscription/plans → select Pro → Razorpay → webhook → entitlements refresh
  → returns to the exact action they were blocked on
```
That last step matters: never dump a user on the dashboard after paying.

### 9. Permissions
`SUBSCRIPTION_MANAGE` — owner only (§18.3: only Admin). Others see the plan badge and usage but the upgrade CTA reads "Ask your admin to upgrade".

### 10. Edge cases
- Payment succeeds, webhook delayed → success page polls `/subscription` for up to 60s, then shows "Payment received, activating shortly" and reconciles when the webhook lands.
- Payment fails mid-upgrade → plan unchanged, clear failure reason, retry CTA.
- Duplicate webhook → idempotent by event ID.
- Card expires on auto-renew → `PAST_DUE`, retry schedule (day 1, 3, 5, 7), notifications at each step.
- Cancel mid-period → access until `current_period_end`, then downgrade to Free with the `OVER_LIMIT` rules.
- Org deletes data to get under a limit → counters update, `OVER_LIMIT` clears automatically.
- Team member count: deactivated staff don't count; invited-but-not-accepted **do** (prevents invite-farming).
- Upgrade during an active bulk import → entitlement re-checked at commit, so a mid-flight upgrade unblocks a job that would have failed.
- Clock/timezone at period boundaries → all period maths in IST, inclusive of the end date.

### 11. Validation rules
- `planCode` must exist and be active, and must be valid for the org type (`BUILDER_*` limits are meaningless to a broker).
- Cannot "upgrade" to the current plan.
- Cannot cancel an already-cancelled subscription.
- Webhook signature must verify or the request is dropped and alerted.

### 12. Notifications (§22.2, §22.3)
- Expiry reminder T−7 and T−1 → in-app + email.
- Payment successful → in-app + email + invoice PDF.
- Payment failed → in-app + email + SMS.
- Usage at 80% and 100% of any quota → in-app.
- Downgrade effective → in-app + email listing what changed.

### 13. Future scalability
- Annual billing with a discount — `plan.price_yearly` already modelled.
- Add-ons (extra team seats, extra WhatsApp credits) via an `org_addon` table layered onto entitlement resolution.
- Per-tenant custom enterprise plans: `plan_limit` override rows scoped to an org.
- Usage-based billing for WhatsApp — `message_delivery.cost_paise` is already recorded.
- Referral credits, coupons: a `discount` table applied at checkout.

---

# M-10 · Reporting & Export Engine

**PRD:** §3.5, §10, §17.1, §7.3, §9.2, §14.4

### 1. Why this module exists
Sixteen distinct report types across the two profiles (§10.1: 7 broker; §17.1: 9 builder), each with its own filters and Excel/CSV/PDF output, plus export-current-view on nearly every list screen (§3.5). Writing sixteen bespoke report endpoints would be sixteen places to introduce a tenant leak. One engine, sixteen declarative definitions.

### 2. Dependencies
**Depends on:** M-01, M-02 (report permissions), M-03 (localised headers), M-05 (result storage), M-09 (`EXPORT_ENABLED`, analytics tier).
**Depended on by:** BR-05, BR-08, B-08, B-11, B-15.

### 3. Database schema
`report_definition`, `export_job`.

### 4. Backend APIs
```
GET  /api/v1/reports                                → definitions available to me (profile + permission + tier filtered)
POST /api/v1/reports/{code}/preview  {filters, page} → on-screen table (§10.2)
POST /api/v1/reports/{code}/export   {filters, format} → {jobId}
POST /api/v1/export/{entityType}     {filters, scope: ALL|FILTERED, format, columns[]} → {jobId}
GET  /api/v1/export/jobs/{id}                        → status
GET  /api/v1/export/jobs/{id}/download               → 302 pre-signed (expires 24h)
GET  /api/v1/export/jobs?cursor=                     → history
```

### 5. Frontend pages
`/broker/reports`, `/builder/reports`, `/reports/{code}` (viewer with filter panel + table + charts + download).

### 6. Components
`ReportCatalog` (cards grouped by category), `ReportFilterPanel` (dynamic from `supported_filters`), `ReportTable` (virtualised, sortable, sticky header, mobile→cards), `ReportChart` (Recharts), `ExportButton` (dropdown: Excel / CSV / PDF; and "All records" vs "Filtered only" per §3.5), `ExportProgressToast`, `ColumnPicker`, `SavedFilterChips`.

### 7. Business logic
- **A report definition is data:** `{code, dataSource (SQL view or query builder), columns[{key, labelKey, type, format, width}], filters[], defaultSort, permission, minTier, charts[]}`. Adding a report = adding a definition + a query, not a new endpoint.
- **Same query powers preview and export** — the on-screen table and the downloaded file can never disagree.
- **Small exports (<5,000 rows) stream synchronously**; larger ones become async jobs with an in-app notification on completion. Users on a phone should not stare at a spinner.
- **Excel via SXSSF streaming** (constant memory), with frozen header, auto-filter, Indian currency number format (`₹#,##,##0.00`), date format `dd-MM-yyyy`, and localised headers.
- **PDF (§10.2)** via HTML template → renderer, with org logo, filter summary, generated-at timestamp, page numbers, and charts as embedded SVG.
- **Filter snapshot** is stored on the `export_job` so a downloaded file can always answer "what filters produced this?".
- **Sensitive columns** (Aadhaar, bank account) are excluded by default; including them requires `SENSITIVE_VIEW` and writes a `sensitive_access_log`.
- **Every query is tenant- and scope-predicated** through the same repository layer as the UI — reports are the classic place isolation is forgotten.

**Broker reports (§10.1):** Property Summary · Customer Pipeline · Deals Closed · Brokerage Income · Follow-up Due · Hot Properties · Inactive Properties (not updated 30+ days).
**Builder reports (§17.1):** Project Summary · Plot Inventory · Sales · Collection · Pending Collections · Broker Commission · Lead/Customer · Follow-up Due · Staff Activity.

### 8. User flow
Reports → pick report → set filters → preview loads (paged) → adjust → Export → Excel → small file downloads immediately / large file notifies when ready.

### 9. Permissions
Each definition declares a permission. Sales Executive → own-scoped reports only (§18.3 "Own reports"). Accounts Staff → financial reports only. View Only → view, and export only if `EXPORT_DATA` granted. Export is entitlement-gated (Free plan: ❌, §23.1).

### 10. Edge cases
- Zero results → an empty state explaining which filter is probably too narrow, plus a "clear filters" action; still allow an empty export (users use them as templates).
- 500,000 rows → cap at 100,000 per export with a message to narrow the range; suggest a date-range split.
- Filter combination with no index → guarded by a statement timeout (10s) returning "This report is taking too long, please narrow the date range" rather than hanging.
- Export while data changes → the file reflects a consistent snapshot (`REPEATABLE READ`).
- Download link expired (24h) → regenerate with one click using the stored filter snapshot.
- CSV + Devanagari → UTF-8 **with BOM**, otherwise Excel on Windows mangles Hindi.
- CSV injection: any cell starting with `= + - @` is prefixed with `'`.
- Very wide reports on mobile → PDF is landscape; the on-screen table becomes horizontally scrollable with a frozen first column.

### 11. Validation rules
- `code` must exist and be permitted for the caller.
- Date range ≤ 5 years; `from ≤ to`.
- Format ∈ {XLSX, CSV, PDF} and supported by that definition.
- Requested columns must be a subset of the definition's columns.

### 12. Notifications
- Export ready → in-app + email link (async jobs).
- Export failed → in-app with reason.
- Scheduled reports (future) → email attachment.

### 13. Future scalability
- Scheduled reports (weekly sales email) — `report_schedule` table + the existing job runner.
- Custom report builder for Premium (§23.1 "Full + Custom") — `report_definition` rows become tenant-owned.
- Read replica for reporting to isolate analytical load from transactional.
- Materialised views + nightly refresh for the heaviest aggregates when tenants grow.

---

# M-11 · Calendar & Reminder Engine

**PRD:** §8 (broker), §15 (builder)

### 1. Why this module exists
Both profiles need one place showing everything due (§8.2, §15): follow-ups auto-projected from customers, site visits, instalment due dates, and manual meetings. The subtle requirement is that most events are **derived**, not entered — if a follow-up date changes on a customer, the calendar must change with it, without a duplicate entry.

### 2. Dependencies
**Depends on:** M-01, M-02, M-06 (reminders), M-12 (customers).
**Depended on by:** BR-06, B-09, B-13.

### 3. Database schema
`calendar_event` (see data model §7).

### 4. Backend APIs
```
GET  /api/v1/calendar?from=&to=&types=&assignedTo=&projectId=   → events
GET  /api/v1/calendar/day/{date}                                → day detail
GET  /api/v1/calendar/counts?from=&to=                          → per-day counts for dot badges
POST /api/v1/calendar/events        {title, date, time, customerId?, propertyId?, projectId?, plotId?, notes, reminderEnabled, assignedTo?}
PATCH /api/v1/calendar/events/{id}
DELETE /api/v1/calendar/events/{id}                             (MANUAL only)
POST /api/v1/calendar/events/{id}/complete
POST /api/v1/calendar/events/{id}/reschedule  {newDate, newTime, reason}
```

### 5. Frontend pages
`/broker/calendar`, `/builder/calendar`.

### 6. Components
`CalendarMonthView` (dot/count badge per day §8.1), `CalendarWeekView`, `CalendarDayView`, `ViewSwitcher` (Month/Week/Day §8.1), `DayEventList`, `EventCard` (colour-coded by type §15), `AddEventDialog` (§8.3 fields), `EventTypeLegend`, `StaffFilter` (builder only §15), `AgendaList` (**mobile default** — a month grid is near-useless on a 360px screen; we default mobile to an agenda list with a compact month strip, month grid available on tap).

### 7. Business logic
- **Auto events are projections.** When `customer.follow_up_date` is set, a `calendar_event(source=AUTO, source_entity_type=CUSTOMER, event_type=FOLLOW_UP)` is upserted keyed on `(source_entity_type, source_entity_id, event_type)`. Changing the date updates the same row. Clearing it soft-deletes the row. No duplicates, ever.
- Sources: customer follow-up date → FOLLOW_UP; customer at `SITE_VISIT_SCHEDULED` → SITE_VISIT; `payment_schedule.due_date` → INSTALMENT_DUE (builder, §15); manual entries → MANUAL_MEETING / IMPORTANT_DATE.
- **Auto events are not directly editable or deletable** — the UI routes "Reschedule" to the *source* record (changing the follow-up date on the customer), which is the correct mental model and keeps one source of truth.
- Colour coding by type; counts per day computed in one grouped query.
- **Staff visibility (§15):** staff see events assigned to them or unassigned; Builder Admin/Manager see all with a staff filter.
- **Reminders:** `reminder_offsets` (default `{0,1}` = same-day and day-before, matching §8.4 and §15) drive M-06 sends.
- Month view fetches a 6-week window (leading/trailing days included) in one call.

### 8. User flow
Sidebar → Calendar → month grid with dots → tap 15 Aug → list: "Follow-up: Rajesh Kumar", "Instalment due: Plot A-12 ₹2,00,000" → tap → deep link to the customer or the collection tracker.

### 9. Permissions
`DATA_VIEW_ALL` → all org events. `DATA_VIEW_OWN` → only own/assigned. Manual event creation requires `DATA_CREATE`. Deleting another user's manual event requires `DATA_DELETE`.

### 10. Edge cases
- Follow-up date in the past → shown on its date, marked overdue in red, and surfaced in an "Overdue" section pinned above today.
- 60 events on one day → day cell shows "12+", day view is virtualised.
- Customer deleted → auto events soft-deleted with it.
- Timezone → all dates are IST business dates; no DST in India, so no DST bugs, but never store as UTC timestamps for date-only events (this is the classic off-by-one).
- Event with no time → sorted last within the day, labelled "All day".
- Two auto sources producing the same day/type for the same entity → the unique index makes it one row.
- Rescheduling an auto event → prompts "This will change the follow-up date on {customer}. Continue?" — explicit, not silent.
- Staff reassignment → the event's `assigned_to` follows the lead.

### 11. Validation rules
- Title required for manual events, 2–150 chars.
- Date required; manual events may be up to 5 years out.
- Time optional; if present, valid 24h.
- Linked entities must exist and be in the caller's scope.
- Reminder offsets ∈ {0,1,3,7} days.

### 12. Notifications (§8.4, §15)
- In-app on the day of the event.
- WhatsApp on the morning of the event (if enabled).
- Optional 1-day-advance notification.
- Site visit: additionally 1 hour before (§22.2).

### 13. Future scalability
- Google Calendar / Outlook two-way sync (`external_calendar_link` table) — heavily requested by builder sales teams.
- iCal feed export (read-only) — cheap, immediate value.
- Recurring events (`rrule` column).
- Team calendar / resource view for site-visit scheduling across staff.
- Drag-to-reschedule on desktop.

---

# M-12 · Customer / Lead Core

**PRD:** §6 (broker), §13 (builder), §5.2.3, §5.2.4

### 1. Why this module exists
Broker customers (§6) and builder leads (§13) are ~85% the same entity: identity, budget, source, pipeline status, follow-up date, important flag, remarks, and an append-only interaction history. The 15% that differs (property interest vs project/plot interest; staff assignment) is additive. Building this once prevents two divergent pipeline implementations.

### 2. Dependencies
**Depends on:** M-01, M-02, M-05, M-06, M-11.
**Depended on by:** BR-03, BR-04, BR-07, B-07, B-10, B-13.

### 3. Database schema
`customer`, `interaction`, `customer_property_interest` (see data model §3).

### 4. Backend APIs
```
GET    /api/v1/customers?search=&status=&followUp=today|week|overdue&important=&assignedTo=
                        &projectId=&propertyId=&sort=&cursor=
POST   /api/v1/customers
GET    /api/v1/customers/{id}
PATCH  /api/v1/customers/{id}
DELETE /api/v1/customers/{id}                 (soft)
POST   /api/v1/customers/{id}/restore
POST   /api/v1/customers/{id}/important       {important: bool}
PATCH  /api/v1/customers/{id}/status          {status, note}
PATCH  /api/v1/customers/{id}/assign          {userId}      (builder)
GET    /api/v1/customers/{id}/interactions?cursor=
POST   /api/v1/customers/{id}/interactions    {occurredOn, type, remarks, nextFollowUpDate, result}
PATCH  /api/v1/interactions/{id}              (15-min amendment window only)
POST   /api/v1/customers/{id}/properties      {propertyIds[]}   (broker link, §6.2)
DELETE /api/v1/customers/{id}/properties/{propertyId}
GET    /api/v1/customers/follow-ups?range=today|week|overdue
```

### 5. Frontend pages
`/broker/customers`, `/builder/customers`, `/customers/{id}` (detail), `/customers/new`, `/customers/{id}/edit`.

### 6. Components
`CustomerList` (table on desktop, **cards on mobile** per §24.2), `CustomerFilterBar`, `CustomerCard`, `CustomerForm`, `ImportantStar` (gold toggle §6.4), `StatusBadge` (colour-coded per §6.3), `BudgetRangeInput` (dual field with Indian formatting and a lakh/crore helper), `FollowUpDateChip` (**red when overdue** §6.1), `InteractionTimeline` (reverse-chronological §6.5), `AddInteractionDialog`, `PropertyInterestPicker` (broker multi-select §6.2), `ProjectPlotPicker` (builder), `AssignStaffDropdown` (builder §13.2), `QuickCallButton` (`tel:` link), `QuickWhatsAppButton` (`wa.me` deep link — brokers live in WhatsApp; this single button drives daily usage).

### 7. Business logic
- **One entity, profile-shaped forms.** The API accepts the union; validation and UI render per `org.type`.
- **Status pipeline (§6.3):** `INTERESTED`(blue) → `SITE_VISIT_SCHEDULED`(orange) → `SITE_VISIT_DONE`(yellow) → `FOLLOWING_UP`(purple) → `DEAL_CLOSED`(green) | `LOST`(red). Closed/Lost set `closed_at` and drop the record out of the active pipeline into Deals History (§6.3, §13). It is **not** deleted — filters simply exclude terminal statuses by default.
- **Status is not strictly linear** — a customer can jump from Interested to Deal Closed. We record every transition in the interaction log rather than blocking transitions. Only Closed/Lost→active requires a confirm ("Reopen this lead?").
- **Adding an interaction with a `nextFollowUpDate` writes back to `customer.follow_up_date`** and upserts the calendar projection (M-11). This is the core loop: log a call → next date set → calendar → tomorrow's reminder.
- **`no_further_follow_up`** (§6.2) suppresses all reminders and removes the customer from Follow-up Due reports without changing status.
- **Interactions are append-only** (§6.5, §13.3) — DB rule blocks delete; a 15-minute amendment window logs `original_remarks`.
- **Builder assignment (§13.2)**: unassigned leads are visible to Admin/Manager; assigning notifies the assignee (§22.3).
- **Duplicate detection:** on create, if the mobile already exists in the org, show "A customer with this number already exists: {name}, {status}" with options *Open existing* / *Create anyway* (families genuinely share numbers) / *Merge*.
- Budget stored as min/max; the list shows "₹25L – ₹40L" using Indian lakh/crore formatting.

### 8. User flow
```
Customer Manager → Add Customer → name, mobile, budget, interest, status, follow-up date → Save
  → appears in list; if follow-up = today, also on the dashboard card and the calendar
Later: open customer → Log Follow-up → type=Call, remarks, next date=+7d, result=Positive
  → timeline entry (permanent) → follow_up_date updated → calendar updated → reminder scheduled
Deal happens → status = Deal Closed → moves to Deals History
```

### 9. Permissions
Create/edit: `DATA_CREATE` / `DATA_EDIT_ALL` or `DATA_EDIT_OWN` (Sales Exec limited to `assigned_to = me`). Delete: `DATA_DELETE` (Admin only per §18.3). Reassign: `LEAD_ASSIGN`. View: scoped by `DATA_VIEW_ALL` vs `DATA_VIEW_OWN`.

### 10. Edge cases
- Same person interested in 5 properties → one `customer`, five `customer_property_interest` rows, five `deal` rows (broker). Never duplicate the person.
- Customer marked Lost then returns → reopen prompt, status change logged, previous interactions preserved.
- Follow-up date set to today at 23:00 → still fires the next morning's job with an "overdue" marker; never silently skipped.
- Mobile number changed → historical WhatsApp deliveries keep the old number in `message_delivery` (audit); opt-in must be re-established for the new number.
- Sales Exec's lead reassigned → disappears from their list mid-session; a toast explains why.
- Bulk-imported customers with the same number → flagged in preview as duplicates.
- Deleting a customer with an active deal → blocked: "This customer has an active deal. Close or cancel it first."
- 10,000 customers → cursor pagination + server-side search; the client never holds the full list.
- Budget min > max → validation error before save.
- Assigned staff deactivated → leads flagged "Unassigned (previous owner deactivated)" on the Admin's dashboard.

### 11. Validation rules
| Field | Rule |
|---|---|
| full_name | required, 2–120 |
| mobile | required, 10 digits, 6–9 leading |
| alternate_mobile | optional, 10 digits, ≠ mobile |
| email | optional, valid |
| budget_min / budget_max | required, > 0, `max ≥ min`, ≤ 10,000 crore |
| status | required, ∈ enum |
| follow_up_date | optional; past dates allowed with a warning; ≤ 5 years out |
| interested_project_id / plot_id | must exist, in scope; plot must belong to the project |
| assigned_to | must be an active user in the org with lead-handling capability |
| remarks | ≤ 5,000 chars |
| interaction.remarks | required, 1–5,000 |
| interaction.occurred_on | required, not in the future |

### 12. Notifications
- New customer added → in-app confirmation (§22.2/§22.3) + notify assignee.
- Follow-up due today → in-app + WhatsApp, 09:00.
- Follow-up overdue with no logged action → in-app + WhatsApp the next morning (§22.2).
- Lead reassigned → in-app + email to the new assignee.
- Status changed to Deal Closed → in-app to owner.

### 13. Future scalability
- Lead scoring (recency, budget fit, engagement) — a computed column + nightly job.
- Auto lead capture from website forms / Facebook Lead Ads / IndiaMART via a webhook endpoint (`POST /api/v1/public/leads` with a per-org API key) — the highest-value v2 feature for builders.
- Round-robin auto-assignment.
- Merge duplicates UI.
- Call-recording integration (Exotel/Knowlarity) attaching recordings to interactions — very common in Indian real estate sales teams.
- WhatsApp two-way inbox.

---

# M-13 · Audit, Soft-Delete & Data Privacy

**PRD:** §25.1, §25.2, §25.3, §25.4, §24.4

### 1. Why this module exists
§25 makes four hard promises: isolation, security, portability (full export within 7 days), and deletion (soft for 30 days, then hard). These are compliance obligations and trust features, and they must be implemented uniformly — a single module that forgets soft-delete becomes the one that permanently loses a builder's project.

### 2. Dependencies
**Depends on:** M-01, M-02, M-05, M-10 (export machinery).
**Depended on by:** every module (as a cross-cutting concern).

### 3. Database schema
`audit_log` (monthly partitions), `sensitive_access_log`, `platform_access_log`, `data_export_request`, `account_deletion_request`, plus the `deleted_at`/`deleted_by` columns on every business table.

### 4. Backend APIs
```
GET  /api/v1/audit?entityType=&entityId=&actorId=&from=&to=&cursor=
GET  /api/v1/trash?entityType=&cursor=                → soft-deleted, within 30 days
POST /api/v1/trash/{entityType}/{id}/restore
DELETE /api/v1/trash/{entityType}/{id}                → immediate hard delete (owner only, double-confirm)
POST /api/v1/privacy/export-request                   → full account export (§25.3)
GET  /api/v1/privacy/export-request/{id}
POST /api/v1/privacy/delete-account   {password, otp} → schedules purge at +30d
POST /api/v1/privacy/delete-account/cancel
```

### 5. Frontend pages
`/settings/privacy` (export my data, delete account), `/settings/trash` (recently deleted, restore), `/settings/activity` (audit log, owner/admin only).

### 6. Components
`ConfirmDeleteDialog` (§24.4 requires a confirmation before *any* delete — types the entity name for high-impact deletions like a project), `TrashList` (with "auto-deletes in N days"), `RestoreButton`, `AuditTimeline` (who changed what, before→after diff), `DataExportCard`, `DeleteAccountFlow` (3 steps: consequences → password+OTP → 30-day cancellation window explained), `SensitiveRevealButton`.

### 7. Business logic
- **Soft delete everywhere** (§25.4): `deleted_at` set, row excluded from all queries via partial indexes and a global Hibernate filter. Restorable for 30 days.
- **Nightly purge job** hard-deletes rows past 30 days, cascading to owned children and S3 objects.
- **Financial immutability:** `payment_record`, `commission_payment`, `brokerage_receipt`, `interaction` cannot be deleted at all. Corrections are reversals/amendments. This is stated plainly in the UI so users don't fight it.
- **Cascade rules are explicit, not database-default:**
  - Delete project → soft-delete its plots; **blocked** if any plot has a non-cancelled sale (must cancel sales first, with a clear listing of which).
  - Delete plot → blocked if it has an active sale or any payment records.
  - Delete property → soft-delete media links; blocked if a deal is past `DEAL_FINALISED`.
  - Delete customer → blocked if an active deal exists; interactions retained.
  - Delete staff → converted to "deactivate"; historical references retained.
- **Audit interceptor** writes a JSONB diff on every create/update/delete of tenant data, excluding hashed/encrypted fields.
- **Account deletion (§25.4):** owner-initiated, password + OTP, 30-day scheduled purge with cancellation available throughout, warning emails at T−7 and T−1, then irreversible purge of DB rows and S3 objects. A minimal transaction record is retained where Indian tax law requires (7 years) — disclosed in the flow.
- **Full data export (§25.3):** async job producing a ZIP of Excel workbooks (one per entity) + a documents folder + a manifest JSON; delivered via a 7-day link. SLA is 7 days but the implementation targets minutes.
- **Platform admin break-glass (§25.1):** reason code, 60-minute time-box, immutable log, tenant-visible in `/settings/activity` (Milestone 9).

### 8. User flow
```
Delete a property → confirm dialog naming it → soft-deleted → toast "Deleted. Undo" (10s)
  → later: Settings → Trash → "Deletes permanently in 23 days" → Restore
Delete account → Settings → Privacy → Delete Account → consequences list → password + OTP
  → "Your account will be deleted on 23 Aug 2026. You can cancel any time before then."
```

### 9. Permissions
Trash view/restore: `DATA_DELETE`. Hard delete: owner only. Audit log: owner + Manager. Account deletion: owner only. Export own data: any user for their scope; full-account export: owner.

### 10. Edge cases
- Restore a plot whose project was hard-deleted → blocked with an explanation; restore the project first (and the project's purge is what removes the option).
- Restore causing a quota breach → blocked with an upgrade prompt.
- Restoring a plot whose number was reused → conflict; prompt to rename on restore.
- Deleted entity referenced by an existing report/export → the file already generated is unaffected (snapshot).
- Owner deletes account with an active paid subscription → subscription cancelled, proration refund per policy, stated explicitly in the flow.
- Deletion scheduled, then the owner logs in → persistent banner with a one-tap cancel.
- Audit log growth → monthly partitions, 24-month retention, older partitions archived to S3 as Parquet.
- Concurrent edit → optimistic locking via `version`; the loser sees "This record was changed by {user} while you were editing" with a diff, not a silent overwrite.

### 11. Validation rules
- Delete confirmation must match the entity name for projects and account deletion.
- Account deletion requires current password + fresh OTP.
- Restore only within 30 days.
- Export requests: max 2 per org per 24h.

### 12. Notifications
- Account deletion scheduled → email + SMS + in-app.
- T−7 and T−1 before purge → email + SMS.
- Deletion cancelled → confirmation.
- Data export ready → email + in-app.
- Platform admin accessed your account → in-app + email (transparency).

### 13. Future scalability
- DPDP Act (India) alignment: consent records, purpose limitation, grievance officer contact — the `data_export_request`/`account_deletion_request` tables are the foundation.
- Per-entity retention policies configurable by tenant.
- Immutable audit export (WORM S3) for enterprise tenants.
- Field-level encryption expansion as the sensitive-field list grows.

---

# M-14 · Platform Admin Console

**PRD:** §1.2 (Admin user type), §3.4.3 (rate updates), §23 (plans), §25.1

### 1. Why this module exists
§1.2 defines a fourth user type: the Shardeya platform owner, who manages all users, subscriptions, and platform-wide settings. Additionally §3.4.3 explicitly requires stamp-duty rates to be updatable from an admin panel, and §17.2 requires default document templates. Without this, every rate change and support request is a database migration.

### 2. Dependencies
**Depends on:** M-01, M-02, M-13.
**Depended on by:** M-08 (rates), M-09 (plans), B-11 (default templates).

### 3. Database schema
`platform_admin` (separate from `app_user`), `platform_access_log`, `stamp_duty_rate`, `plan`, `plan_limit`, `notification_type`, `document_template (org_id IS NULL)`, `feature_flag`, `system_announcement`.

### 4. Backend APIs
Namespaced `/api/v1/platform/**`, separate auth realm, IP-allowlisted, mandatory MFA.
```
GET  /platform/orgs?type=&plan=&status=&search=&cursor=
GET  /platform/orgs/{id}                       → profile, usage, subscription, health
POST /platform/orgs/{id}/suspend | /reactivate
POST /platform/orgs/{id}/access-grant  {reasonCode, justification}   → 60-min break-glass
POST /platform/orgs/{id}/plan-override {planCode, until, reason}
GET  /platform/subscriptions/revenue?from=&to=
CRUD /platform/stamp-duty-rates
CRUD /platform/plans, /platform/plan-limits
CRUD /platform/document-templates              (system defaults)
CRUD /platform/notification-types
GET  /platform/metrics                          → signups, DAU, churn, error rate, message costs
GET  /platform/message-deliveries?status=FAILED
CRUD /platform/announcements
CRUD /platform/feature-flags
```

### 5. Frontend pages
Separate SPA at `/platform`: Dashboard, Organizations, Organization Detail, Subscriptions & Revenue, Stamp Duty Rates, Plans & Limits, Document Templates, Notification Catalogue, Message Delivery Log, Announcements, Feature Flags, Access Log.

### 6. Components
`OrgTable`, `OrgHealthCard`, `BreakGlassDialog` (reason code required), `RateEditor` (with effective-dating and a preview of what changes), `PlanLimitMatrix`, `TemplateEditor` (variable palette + live preview), `MetricsChart`, `DeliveryFailureTable`, `AnnouncementComposer` (targeted by plan/profile/region), `FeatureFlagToggle`.

### 7. Business logic
- **Platform admins never receive a tenant JWT.** Read access to tenant data is via break-glass: reason code + justification, 60-minute expiry, immutable `platform_access_log`, tenant-visible.
- **Rate changes are effective-dated, never destructive.** Editing a rate creates a new row with `effective_from`; the old row gets `effective_to`. Historical calculations remain reproducible.
- **Plan limit changes** apply to new billing periods by default; applying immediately requires an explicit toggle and notifies affected orgs.
- **Feature flags** support gradual rollout (percentage, org allowlist) — essential for shipping the Builder modules incrementally without long-lived branches.
- **Announcements** render as a dismissible banner in the tenant app, targetable by plan/profile.
- Every platform action is audited to a separate stream with 7-year retention.

### 8. User flow
Support ticket: "my instalment shows overdue" → admin searches org → opens detail → sees plan, usage, recent errors → break-glass with reason `SUPPORT_TICKET#1234` → views the plot's payment schedule read-only → resolves → access auto-expires → tenant owner sees the access entry.

### 9. Permissions
Platform admin roles: `SUPPORT` (read + break-glass), `OPS` (+ suspend, plan override), `SUPER_ADMIN` (+ plans, rates, flags, admin management). MFA mandatory. Session 30 minutes idle.

### 10. Edge cases
- Break-glass on a `PENDING_DELETION` org → allowed, heavily logged.
- Rate edit with an `effective_from` in the past → warning; requires confirmation since it changes historical calculations.
- Suspending an org mid-session → active sessions become read-only immediately.
- Two admins editing the same rate → optimistic locking.
- Announcement to 10,000 orgs → fan-out via the outbox, not synchronously.
- Feature flag disabled while a user is mid-flow → the client degrades gracefully rather than erroring.

### 11. Validation rules
- Stamp duty percentages 0–20; registration cap ≥ 0; `effective_from` required and unique per key combination.
- Plan limits: integers ≥ −1.
- Templates must reference only allowlisted variables; a template with an unknown variable cannot be activated.
- Suspension requires a reason.

### 12. Notifications
- Tenant owner notified on: suspension, plan override, break-glass access, forced plan change.
- Platform admins alerted on: payment webhook failures, message delivery failure rate >10%, error-rate spikes, reconciliation drift.

### 13. Future scalability
- Impersonation with explicit tenant consent (rather than break-glass) for complex support.
- Self-serve support console for tenant owners (view their own audit + delivery logs).
- Partner/reseller hierarchy for regional distributors — a natural Indian go-to-market channel.
- Automated rate ingestion from state government sources.
