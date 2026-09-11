import type { LucideIcon } from 'lucide-react';
import {
  Archive, BarChart3, Building2, Calculator, CalendarDays, ClipboardList, Contact, Database, FileSignature, FileText, Handshake,
  LayoutDashboard, Users, Wallet,
} from 'lucide-react';

export type ShellProfile = 'builder' | 'broker';

export interface NavItem {
  to: string;
  labelKey: string;
  icon: LucideIcon;
  /** Rendered only when the user holds at least one of these permissions -- UI hiding is cosmetic (CLAUDE.md rule #5), the server still enforces every one of these on the endpoints themselves. */
  anyOf?: string[];
}

export function navItemsFor(profile: ShellProfile): NavItem[] {
  const items: NavItem[] = [{ to: `/${profile}/dashboard`, labelKey: 'nav.dashboard', icon: LayoutDashboard }];
  if (profile === 'builder') {
    items.push({ to: '/builder/projects', labelKey: 'nav.projects', icon: Building2 });
    items.push({ to: '/builder/leads', labelKey: 'nav.leads', icon: Contact, anyOf: ['DATA_VIEW_ALL', 'DATA_VIEW_OWN'] });
    items.push({ to: '/builder/calendar', labelKey: 'nav.calendar', icon: CalendarDays });
    items.push({
      to: '/builder/tracker',
      labelKey: 'nav.tracker',
      icon: ClipboardList,
      anyOf: ['DATA_VIEW_ALL', 'DATA_VIEW_OWN', 'FINANCIAL_VIEW'],
    });
    items.push({ to: '/builder/financials', labelKey: 'nav.financials', icon: Wallet, anyOf: ['FINANCIAL_VIEW'] });
    items.push({ to: '/builder/deals', labelKey: 'nav.deals', icon: Archive, anyOf: ['DATA_VIEW_ALL', 'DATA_VIEW_OWN'] });
    items.push({ to: '/builder/admin/team', labelKey: 'nav.team', icon: Users, anyOf: ['TEAM_VIEW', 'TEAM_MANAGE'] });
    // B-14 §9: "Sales Executive and View Only: no access; sidebar link not
    // rendered" -- BROKER_VIEW is held by Admin/Manager/Accounts Staff only.
    items.push({ to: '/builder/brokers', labelKey: 'nav.brokers', icon: Handshake, anyOf: ['BROKER_VIEW'] });
    items.push({ to: '/builder/calculators', labelKey: 'nav.calculators', icon: Calculator });
    items.push({ to: '/builder/reports', labelKey: 'nav.reports', icon: FileText, anyOf: ['REPORT_VIEW_ALL', 'REPORT_VIEW_OWN', 'REPORT_FINANCIAL'] });
    // B-11 §9: template editing is Admin-only (DOCUMENT_TEMPLATE_EDIT) --
    // every other role's document actions live on the plot/sale screens
    // themselves (GenerateDocumentButton), not this nav item.
    items.push({ to: '/builder/documents/templates', labelKey: 'nav.documentTemplates', icon: FileSignature, anyOf: ['DOCUMENT_TEMPLATE_EDIT'] });
    // B-15 §9: REPORT_VIEW_ALL for the full page -- Accounts Staff
    // (FINANCIAL_VIEW but no REPORT_VIEW_ALL) has no stats access this
    // round (see StatsController's own comment on why that split isn't
    // implemented yet).
    items.push({ to: '/builder/stats', labelKey: 'nav.stats', icon: BarChart3, anyOf: ['REPORT_VIEW_ALL'] });
    items.push({ to: '/portal', labelKey: 'nav.portal', icon: Database });
  }
  return items;
}
