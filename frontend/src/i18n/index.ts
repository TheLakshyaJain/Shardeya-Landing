import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';

import enCommon from './en/common.json';
import hiCommon from './hi/common.json';
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

export const SUPPORTED_LANGUAGES = ['en', 'hi'] as const;
export type SupportedLanguage = (typeof SUPPORTED_LANGUAGES)[number];

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      en: {
        common: enCommon, auth: enAuth, errors: enErrors, project: enProject, plot: enPlot, import: enImport,
        sale: enSale, payment: enPayment, notification: enNotification, team: enTeam, customer: enCustomer,
        calendar: enCalendar, financial: enFinancial, tracker: enTracker, deal: enDeal, dashboard: enDashboard,
        broker: enBroker, calculator: enCalculator, report: enReport, document: enDocument, stats: enStats,
        settings: enSettings, admin: enAdmin,
      },
      hi: {
        common: hiCommon, auth: hiAuth, errors: hiErrors, project: hiProject, plot: hiPlot, import: hiImport,
        sale: hiSale, payment: hiPayment, notification: hiNotification, team: hiTeam, customer: hiCustomer,
        calendar: hiCalendar, financial: hiFinancial, tracker: hiTracker, deal: hiDeal, dashboard: hiDashboard,
        broker: hiBroker, calculator: hiCalculator, report: hiReport, document: hiDocument, stats: hiStats,
        settings: hiSettings, admin: hiAdmin,
      },
    },
    fallbackLng: 'en',
    supportedLngs: SUPPORTED_LANGUAGES,
    defaultNS: 'common',
    ns: ['common', 'auth', 'errors', 'project', 'plot', 'import', 'sale', 'payment', 'notification', 'team', 'customer', 'calendar', 'financial', 'tracker', 'deal', 'dashboard', 'broker', 'calculator', 'report', 'document', 'stats', 'settings', 'admin'],
    interpolation: { escapeValue: false },
    detection: {
      order: ['localStorage', 'navigator'],
      caches: ['localStorage'],
    },
  });

i18n.on('languageChanged', (lng) => {
  document.documentElement.lang = lng;
});

export default i18n;
