# SHARDEYA REAL ESTATE OS — MASTER DESIGN SYSTEM & COLOR SCHEME
**Version 1.0 • Canonical Architecture & Styling Specification**

This document defines the strict, authoritative visual and technical design tokens for the entire **Shardeya Real Estate CRM**. Every module, form, table, card, and modal built for Shardeya MUST strictly adhere to this specification.

---

## 1. Color Palette & Token Hierarchy

### A. Primary Brand Foundations (Forest Emerald)
Represents land, stability, prosperity, and Indian institutional architectural heritage.

| Token | Hex Code | Tailwind Equivalent | Primary Usage |
|---|---|---|---|
| `forest-950` | `#022C22` | `bg-emerald-950` | High-contrast dark surfaces, sidebar headers |
| `forest-900` | `#064E3B` | `bg-emerald-900` | Deep container backgrounds, active navigation pills |
| `forest-800` | `#065F46` | `bg-emerald-800` | Active button hover, solid badge borders |
| `forest-700` | `#047857` | `bg-emerald-700` | **Primary Brand Green**, main action buttons, focus rings |
| `forest-600` | `#059669` | `bg-emerald-600` | Standard accents, icon highlights, active links |
| `forest-500` | `#10B981` | `bg-emerald-500` | Live telemetry pulses, positive status indicators |
| `forest-100` | `#D1FAE5` | `bg-emerald-100` | Soft positive badge backgrounds |
| `forest-50` | `#ECFDF5` | `bg-emerald-50` | Light emerald tints, unit 'Available' container background |

---

### B. Surface & Canvas Foundations (Warm Sand & Slate)
Eliminates harsh clinical blue-grays in favor of warm, tactile, quiet-luxury architectural paper tones.

| Token | Hex Code | Tailwind Equivalent | Primary Usage |
|---|---|---|---|
| `canvas-page` | `#F8FAFC` | `bg-slate-50` / `sand-100` | Main application background (ambient micro-grid canvas) |
| `surface-card` | `#FFFFFF` | `bg-white` | Primary content cards, modal windows, table surfaces |
| `surface-subtle`| `#F1F5F9` | `bg-slate-100` / `sand-150` | Input backgrounds, segmented control tracks, table headers |
| `surface-hover` | `#E2E8F0` | `bg-slate-200` | Secondary button hover, row hover highlight |
| `border-subtle` | `#E2E8F0` | `border-slate-200` | Card borders, subtle field dividers |
| `border-medium` | `#CBD5E1` | `border-slate-300` | Input borders, active card borders, tab containers |
| `border-strong` | `#94A3B8` | `border-slate-400` | High-emphasis dividers, focused element outlines |

---

### C. Text & Typography Tokens (Espresso Charcoal)
Guarantees WCAG AAA contrast (>7:1) across all light and medium backgrounds. Pure black (`#000000`) is avoided to eliminate eye fatigue.

| Token | Hex Code | Tailwind Equivalent | Usage |
|---|---|---|---|
| `text-primary` | `#0F172A` | `text-slate-900` | Page headlines, major numbers, primary labels |
| `text-secondary`| `#334155` | `text-slate-700` | Body copy, table data, active filter labels |
| `text-muted` | `#64748B` | `text-slate-500` | Helper descriptions, timestamps, subheaders |
| `text-placeholder`| `#94A3B8`| `text-slate-400` | Input placeholder text, disabled actions |
| `text-inverse` | `#FFFFFF` | `text-white` | Text on `forest-700`, `slate-900`, or dark surfaces |

---

### D. Semantic Status & Domain Real Estate Colors
Strictly standardized for real estate inventory, demand collections, and broker tiers.

| Domain Meaning | Background | Text | Border | Usage |
|---|---|---|---|---|
| **Available Unit** | `#ECFDF5` (`emerald-50`) | `#065F46` (`emerald-800`) | `#A7F3D0` (`emerald-200`) | Free plot or apartment ready for booking |
| **Token Hold (48h)** | `#FFFBEB` (`amber-50`) | `#92400E` (`amber-800`) | `#FDE68A` (`amber-200`) | Temporary hold with live countdown |
| **Booked / Allotted** | `#F1F5F9` (`slate-100`) | `#334155` (`slate-700`) | `#CBD5E1` (`slate-300`) | Allotted unit with active buyer payment schedule |
| **Overdue / Dispute** | `#FEF2F2` (`rose-50`) | `#991B1B` (`rose-800`) | `#FECACA` (`rose-200`) | Milestone payment default or dual-broker dispute |
| **Escrow / Verified** | `#EFF6FF` (`blue-50`) | `#1E40AF` (`blue-800`) | `#BFDBFE` (`blue-200`) | Bank-verified UTR match, RERA registered |

---

## 2. Typography Rules

- **Display & Headings**: `Playfair Display`, `Georgia`, serif
  - `font-serif font-bold tracking-tight text-slate-900`
  - Reserved for Page Titles, Hero Headlines, Card Section Titles, and Financial Metric Numbers.
- **Interface & Operational Text**: `Plus Jakarta Sans`, `Inter`, sans-serif
  - `font-sans font-medium / font-semibold text-slate-800`
  - Used for forms, buttons, table data, navigation, and badges.
- **Cadastral & Telemetry Data**: `Space Mono`, `Menlo`, monospace
  - `font-mono text-xs font-bold text-slate-700`
  - Used for Unit IDs (`#104`), Plot Dimensions (`30'0" x 71'8"`), RERA IDs, UTR Bank numbers, and timestamps.

---

## 3. UI Elevation, Shadows & Radii

- **Container Radii**:
  - Main Cards: `rounded-2xl` (16px)
  - Inputs & Buttons: `rounded-xl` (12px)
  - Badges & Pills: `rounded-lg` (8px) or `rounded-full` (9999px)
  - Segmented Track: `rounded-xl` (12px)
- **Shadows**:
  - Card Resting: `shadow-warm-sm` (`0 2px 10px 0 rgba(15, 23, 42, 0.04)`)
  - Card Elevated/Modal: `shadow-warm-lg` (`0 20px 40px -8px rgba(15, 23, 42, 0.12)`)
  - Focus Ring: `ring-2 ring-emerald-600/30 ring-offset-1`

---

## 4. Strict Form & Authentication Standards

1. **Email & Password Authentication**:
   - Standard corporate login: Email input (`type="email"`) with `@` domain validation.
   - Password input (`type="password"`) with eye toggle (`Eye` / `EyeOff`).
   - Real-time password criteria on sign-up (Minimum 8 characters, 1 uppercase, 1 number, 1 special character).
2. **No Mobile OTP on Core Auth**:
   - Mobile OTP is strictly removed from the primary login/signup flow per user instruction.
3. **Role Awareness**:
   - Every user belongs to either **Real Estate Developer / Builder** or **Channel Partner / Broker Syndicate**.
   - Clear visual role indicators throughout authentication and workspace portals.
4. **Security & Feedback**:
   - TLS 1.3 256-Bit session encryption indicator.
   - Brute-force rate limiting warning (locks out after 5 consecutive failures for 30s).
   - Clear, friendly inline error messages (red alert pill with icon).

---
*All components in Shardeya Real Estate OS must abide by this document.*
