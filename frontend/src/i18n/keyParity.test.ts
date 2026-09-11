import { describe, expect, it } from 'vitest';
import en from './en/common.json';
import hi from './hi/common.json';
import enAuth from './en/auth.json';
import hiAuth from './hi/auth.json';
import enErrors from './en/errors.json';
import hiErrors from './hi/errors.json';
import enProject from './en/project.json';
import hiProject from './hi/project.json';
import enPlot from './en/plot.json';
import hiPlot from './hi/plot.json';
import enImport from './en/import.json';
import hiImport from './hi/import.json';
import enSale from './en/sale.json';
import hiSale from './hi/sale.json';
import enPayment from './en/payment.json';
import hiPayment from './hi/payment.json';
import enNotification from './en/notification.json';
import hiNotification from './hi/notification.json';
import enTeam from './en/team.json';
import hiTeam from './hi/team.json';
import enCustomer from './en/customer.json';
import hiCustomer from './hi/customer.json';
import enCalendar from './en/calendar.json';
import hiCalendar from './hi/calendar.json';
import enFinancial from './en/financial.json';
import hiFinancial from './hi/financial.json';
import enTracker from './en/tracker.json';
import hiTracker from './hi/tracker.json';
import enDeal from './en/deal.json';
import hiDeal from './hi/deal.json';
import enDashboard from './en/dashboard.json';
import hiDashboard from './hi/dashboard.json';
import enBroker from './en/broker.json';
import hiBroker from './hi/broker.json';
import enCalculator from './en/calculator.json';
import hiCalculator from './hi/calculator.json';
import enReport from './en/report.json';
import hiReport from './hi/report.json';
import enDocument from './en/document.json';
import hiDocument from './hi/document.json';
import enStats from './en/stats.json';
import hiStats from './hi/stats.json';
import enSettings from './en/settings.json';
import hiSettings from './hi/settings.json';
import enAdmin from './en/admin.json';
import hiAdmin from './hi/admin.json';

function flattenKeys(obj: unknown, prefix = ''): string[] {
  if (typeof obj !== 'object' || obj === null) return [prefix];
  return Object.entries(obj).flatMap(([key, value]) =>
    flattenKeys(value, prefix ? `${prefix}.${key}` : key),
  );
}

// CLAUDE.md rule #4: every i18n key must exist in both en and hi. This is the
// automated version of that CI gate — one check per namespace.
describe('i18n key parity', () => {
  it.each([
    ['common', en, hi],
    ['auth', enAuth, hiAuth],
    ['errors', enErrors, hiErrors],
    ['project', enProject, hiProject],
    ['plot', enPlot, hiPlot],
    ['import', enImport, hiImport],
    ['sale', enSale, hiSale],
    ['payment', enPayment, hiPayment],
    ['notification', enNotification, hiNotification],
    ['team', enTeam, hiTeam],
    ['customer', enCustomer, hiCustomer],
    ['calendar', enCalendar, hiCalendar],
    ['financial', enFinancial, hiFinancial],
    ['tracker', enTracker, hiTracker],
    ['deal', enDeal, hiDeal],
    ['dashboard', enDashboard, hiDashboard],
    ['broker', enBroker, hiBroker],
    ['calculator', enCalculator, hiCalculator],
    ['report', enReport, hiReport],
    ['document', enDocument, hiDocument],
    ['stats', enStats, hiStats],
    ['settings', enSettings, hiSettings],
    ['admin', enAdmin, hiAdmin],
  ])('%s namespace has identical keys in en and hi', (_namespace, enResource, hiResource) => {
    const enKeys = flattenKeys(enResource).sort();
    const hiKeys = flattenKeys(hiResource).sort();

    expect(hiKeys).toEqual(enKeys);
  });
});
