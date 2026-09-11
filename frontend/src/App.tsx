import { useEffect } from 'react';
import { Navigate, Route, BrowserRouter, Routes } from 'react-router-dom';
import { AppProviders } from '@/app/providers';
import { LandingPage } from '@/app/LandingPage';
import { bootstrapSession } from '@/features/auth/bootstrap';
import { AppShell } from '@/components/layout/AppShell';
import { BuilderDashboardPage } from '@/features/builder/dashboard/DashboardPage';
import { ProjectListPage } from '@/features/builder/projects/pages/ProjectListPage';
import { ProjectFormPage } from '@/features/builder/projects/pages/ProjectFormPage';
import { ProjectDetailPage } from '@/features/builder/projects/pages/ProjectDetailPage';
import { ImportWizardPage } from '@/features/builder/import/pages/ImportWizardPage';
import { AddPlotsPage } from '@/features/builder/plots/pages/AddPlotsPage';
import { BrokerDashboardPage } from '@/features/broker/dashboard/DashboardPage';
import { DesignSystemPage } from '@/features/design/DesignSystemPage';
import { RequireAuth, RedirectIfAuthenticated } from '@/features/auth/RouteGuards';
import { SignupPage } from '@/features/auth/pages/SignupPage';
import { SignupVerifyPage } from '@/features/auth/pages/SignupVerifyPage';
import { LoginPage } from '@/features/auth/pages/LoginPage';
import { LoginOtpPage } from '@/features/auth/pages/LoginOtpPage';
import { ForgotPasswordPage } from '@/features/auth/pages/ForgotPasswordPage';
import { ResetPasswordPage } from '@/features/auth/pages/ResetPasswordPage';
import { SessionExpiredDialog } from '@/features/auth/components/SessionExpiredDialog';
import { TeamListPage } from '@/features/builder/team/pages/TeamListPage';
import { AcceptInvitePage } from '@/features/builder/team/pages/AcceptInvitePage';
import { LeadListPage } from '@/features/builder/leads/pages/LeadListPage';
import { LeadDetailPage } from '@/features/builder/leads/pages/LeadDetailPage';
import { CalendarPage } from '@/features/builder/calendar/pages/CalendarPage';
import { FinancialsPage } from '@/features/builder/financials/pages/FinancialsPage';
import { TrackerPage } from '@/features/builder/tracker/pages/TrackerPage';
import { DealsHistoryPage } from '@/features/builder/deals/pages/DealsHistoryPage';
import { DealDetailPage } from '@/features/builder/deals/pages/DealDetailPage';
import { BrokerListPage } from '@/features/builder/brokers/pages/BrokerListPage';
import { BrokerDetailPage } from '@/features/builder/brokers/pages/BrokerDetailPage';
import { TierConfigPage } from '@/features/builder/brokers/pages/TierConfigPage';
import { NetworkTreePage } from '@/features/builder/brokers/pages/NetworkTreePage';
import { CalculatorsPage } from '@/features/calculators/pages/CalculatorsPage';
import { ReportCatalogPage } from '@/features/builder/reports/pages/ReportCatalogPage';
import { ReportViewerPage } from '@/features/builder/reports/pages/ReportViewerPage';
import { TemplateListPage } from '@/features/builder/documents/pages/TemplateListPage';
import { TemplateEditorPage } from '@/features/builder/documents/pages/TemplateEditorPage';
import { StatsPage } from '@/features/builder/stats/pages/StatsPage';
import { NotificationSettingsPage } from '@/features/settings/pages/NotificationSettingsPage';
import { OpsPage } from '@/features/admin/pages/OpsPage';
import { SystemPortalPage } from '@/features/portal/pages/SystemPortalPage';

function App() {
  // Once, on app start: attempt a silent cookie-based refresh to restore a
  // session that survives a hard reload. See features/auth/bootstrap.ts and
  // RouteGuards.tsx's own bootstrapping-gate comment for the full mechanism.
  useEffect(() => {
    void bootstrapSession();
  }, []);

  return (
    <AppProviders>
      <BrowserRouter>
        <SessionExpiredDialog />
        <Routes>
          <Route path="/" element={<LandingPage />} />
          <Route path="/design" element={<DesignSystemPage />} />
          <Route path="/portal" element={<SystemPortalPage />} />

          <Route
            path="/signup"
            element={
              <RedirectIfAuthenticated>
                <SignupPage />
              </RedirectIfAuthenticated>
            }
          />
          <Route path="/signup/verify" element={<SignupVerifyPage />} />
          <Route
            path="/login"
            element={
              <RedirectIfAuthenticated>
                <LoginPage />
              </RedirectIfAuthenticated>
            }
          />
          <Route
            path="/login/otp"
            element={
              <RedirectIfAuthenticated>
                <LoginOtpPage />
              </RedirectIfAuthenticated>
            }
          />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          <Route path="/accept-invite" element={<AcceptInvitePage />} />

          <Route
            path="/builder"
            element={
              <RequireAuth profile="builder">
                <AppShell profile="builder" />
              </RequireAuth>
            }
          >
            <Route index element={<Navigate to="dashboard" replace />} />
            <Route path="dashboard" element={<BuilderDashboardPage />} />
            <Route path="projects" element={<ProjectListPage />} />
            <Route path="projects/new" element={<ProjectFormPage />} />
            <Route path="projects/:id/edit" element={<ProjectFormPage />} />
            <Route path="projects/:id" element={<ProjectDetailPage />} />
            <Route path="projects/:projectId/plots/new-bulk" element={<AddPlotsPage />} />
            <Route path="projects/:projectId/import" element={<ImportWizardPage />} />
            <Route path="leads" element={<LeadListPage />} />
            <Route path="leads/:id" element={<LeadDetailPage />} />
            <Route path="calendar" element={<CalendarPage />} />
            <Route path="financials" element={<FinancialsPage />} />
            <Route path="tracker" element={<TrackerPage />} />
            <Route path="deals" element={<DealsHistoryPage />} />
            <Route path="deals/:id" element={<DealDetailPage />} />
            <Route path="admin/team" element={<TeamListPage />} />
            <Route path="brokers" element={<BrokerListPage />} />
            <Route path="brokers/tiers" element={<TierConfigPage />} />
            <Route path="brokers/network" element={<NetworkTreePage />} />
            <Route path="brokers/:id" element={<BrokerDetailPage />} />
            <Route path="calculators" element={<CalculatorsPage />} />
            <Route path="reports" element={<ReportCatalogPage />} />
            <Route path="reports/:code" element={<ReportViewerPage />} />
            <Route path="documents/templates" element={<TemplateListPage />} />
            <Route path="documents/templates/:id/edit" element={<TemplateEditorPage />} />
            <Route path="stats" element={<StatsPage />} />
            <Route path="settings/notifications" element={<NotificationSettingsPage />} />
            <Route path="admin/ops" element={<OpsPage />} />
            <Route path="portal" element={<SystemPortalPage />} />
          </Route>

          <Route
            path="/broker"
            element={
              <RequireAuth profile="broker">
                <AppShell profile="broker" />
              </RequireAuth>
            }
          >
            <Route index element={<Navigate to="dashboard" replace />} />
            <Route path="dashboard" element={<BrokerDashboardPage />} />
            <Route path="settings/notifications" element={<NotificationSettingsPage />} />
            <Route path="admin/ops" element={<OpsPage />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </AppProviders>
  );
}

export default App;
