import React, { useState } from 'react';
import { useAuth } from '../../../context/AuthContext';
import { useCrm } from '../../../context/CrmContext';
import { Sidebar, CrmTab } from './Sidebar';
import { TopBar } from './TopBar';

// Builder Views
import { BuilderDashboard } from '../builder/BuilderDashboard';
import { ProjectListPage } from '../builder/projects/ProjectListPage';
import { InteractivePlotGrid } from '../builder/plots/InteractivePlotGrid';
import { BookingModal } from '../builder/plots/BookingModal';
import { AddPlotsWizard } from '../builder/plots/AddPlotsWizard';
import { LeadListPage } from '../builder/leads/LeadListPage';
import { LeadFormModal } from '../builder/leads/LeadFormModal';
import { FinancialsPage } from '../builder/financials/FinancialsPage';
import { RecordPaymentModal } from '../builder/financials/RecordPaymentModal';
import { DealsPage } from '../builder/deals/DealsPage';
import { BrokerListPage } from '../builder/brokers/BrokerListPage';
import { CalendarPage } from '../builder/calendar/CalendarPage';
import { ReportsCatalogPage } from '../builder/reports/ReportsCatalogPage';

// Broker Views
import { BrokerDashboard } from '../broker/BrokerDashboard';
import { SharedInventoryPage } from '../broker/SharedInventoryPage';
import { CommissionLedgerPage } from '../broker/CommissionLedgerPage';

// Shared Tools
import { CrmCalculatorsPage } from '../calculators/CrmCalculatorsPage';
import { Plot } from '../../../types/crm';
import { X, Building2 } from 'lucide-react';

interface CrmAppShellProps {
  onBackToLanding: () => void;
}

export const CrmAppShell: React.FC<CrmAppShellProps> = ({ onBackToLanding }) => {
  const { user } = useAuth();
  const { activeProject, projects, plots, addProject } = useCrm();

  const [currentTab, setCurrentTab] = useState<CrmTab>('dashboard');
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  // Global Modals State
  const [isBookingModalOpen, setIsBookingModalOpen] = useState(false);
  const [selectedBookingPlot, setSelectedBookingPlot] = useState<Plot | null>(null);
  const [isAddPlotsModalOpen, setIsAddPlotsModalOpen] = useState(false);
  const [isRecordPaymentModalOpen, setIsRecordPaymentModalOpen] = useState(false);
  const [isNewLeadModalOpen, setIsNewLeadModalOpen] = useState(false);
  const [isNewProjectModalOpen, setIsNewProjectModalOpen] = useState(false);

  // New Project Form State
  const [newProjectForm, setNewProjectForm] = useState({
    name: '',
    projectType: 'RESIDENTIAL_PLOT_COLONY' as const,
    locality: '',
    city: 'Saharanpur',
    stateCode: 'UP',
    address: '',
    reraNumber: '',
    totalAreaValue: 25,
    totalAreaUnit: 'BIGHA' as const,
    declaredPlotCount: 120,
    launchDate: new Date().toISOString().split('T')[0],
    description: '',
    gridRows: 4,
    gridCols: 6
  });

  const isDeveloper = user?.role === 'developer';

  const handleOpenBooking = (plot?: Plot) => {
    if (plot) setSelectedBookingPlot(plot);
    setIsBookingModalOpen(true);
  };

  const handleCreateProject = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newProjectForm.name) return;
    addProject({
      ...newProjectForm,
      expectedCompletionDate: '2027-12-31',
      status: 'ACTIVE',
      totalAreaSqft: newProjectForm.totalAreaValue * 27000
    });
    setIsNewProjectModalOpen(false);
    setNewProjectForm({
      name: '',
      projectType: 'RESIDENTIAL_PLOT_COLONY',
      locality: '',
      city: 'Saharanpur',
      stateCode: 'UP',
      address: '',
      reraNumber: '',
      totalAreaValue: 25,
      totalAreaUnit: 'BIGHA',
      declaredPlotCount: 120,
      launchDate: new Date().toISOString().split('T')[0],
      description: '',
      gridRows: 4,
      gridCols: 6
    });
  };

  // Render view based on tab & role
  const renderContent = () => {
    switch (currentTab) {
      case 'dashboard':
        return isDeveloper ? (
          <BuilderDashboard
            onNavigate={setCurrentTab}
            onOpenNewLead={() => setIsNewLeadModalOpen(true)}
            onOpenRecordPayment={() => setIsRecordPaymentModalOpen(true)}
            onOpenNewPlot={() => setIsAddPlotsModalOpen(true)}
          />
        ) : (
          <BrokerDashboard
            onNavigate={setCurrentTab}
            onOpenNewLead={() => setIsNewLeadModalOpen(true)}
          />
        );

      case 'projects':
        return (
          <ProjectListPage
            onNavigate={setCurrentTab}
            onOpenNewProject={() => setIsNewProjectModalOpen(true)}
            onSelectProjectDetail={() => setCurrentTab('plots')}
          />
        );

      case 'plots':
        return (
          <InteractivePlotGrid
            onOpenBookingModal={handleOpenBooking}
            onOpenBulkUpload={() => setIsAddPlotsModalOpen(true)}
          />
        );

      case 'leads':
        return (
          <LeadListPage
            onOpenNewLeadModal={() => setIsNewLeadModalOpen(true)}
          />
        );

      case 'financials':
        return <FinancialsPage />;

      case 'deals':
        return <DealsPage />;

      case 'brokers':
        return <BrokerListPage />;

      case 'calendar':
        return <CalendarPage />;

      case 'calculators':
        return <CrmCalculatorsPage />;

      case 'reports':
        return <ReportsCatalogPage />;

      case 'shared-inventory':
        return <SharedInventoryPage />;

      case 'commissions':
        return <CommissionLedgerPage />;

      default:
        return (
          <BuilderDashboard
            onNavigate={setCurrentTab}
            onOpenNewLead={() => setIsNewLeadModalOpen(true)}
            onOpenRecordPayment={() => setIsRecordPaymentModalOpen(true)}
            onOpenNewPlot={() => setIsAddPlotsModalOpen(true)}
          />
        );
    }
  };

  return (
    <div className="flex h-screen bg-[#F8FAFC] text-espresso-950 overflow-hidden font-sans">
      {/* Sidebar Navigation */}
      <Sidebar
        currentTab={currentTab}
        onSelectTab={(tab) => {
          setCurrentTab(tab);
          setMobileSidebarOpen(false);
        }}
        isOpenMobile={mobileSidebarOpen}
        onCloseMobile={() => setMobileSidebarOpen(false)}
        onBackToLanding={onBackToLanding}
      />

      {/* Main Content Area */}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        {/* Top Header Bar */}
        <TopBar
          onOpenMobileSidebar={() => setMobileSidebarOpen(true)}
          onOpenNewLead={() => setIsNewLeadModalOpen(true)}
          onOpenNewPlot={() => setIsAddPlotsModalOpen(true)}
          onOpenRecordPayment={() => setIsRecordPaymentModalOpen(true)}
          searchQuery={searchQuery}
          onSearchChange={setSearchQuery}
        />

        {/* Scrollable Workspace View */}
        <main className="flex-1 overflow-y-auto p-4 sm:p-6 lg:p-8">
          <div className="max-w-7xl mx-auto">
            {renderContent()}
          </div>
        </main>
      </div>

      {/* Global Modals */}

      {/* 1. Unit Booking Modal */}
      {isBookingModalOpen && (selectedBookingPlot || plots[0]) && (
        <BookingModal
          plot={selectedBookingPlot || plots.find((p: Plot) => p.projectId === activeProject?.id && p.status === 'AVAILABLE') || plots[0]}
          onClose={() => {
            setIsBookingModalOpen(false);
            setSelectedBookingPlot(null);
          }}
          onSuccess={() => {
            setIsBookingModalOpen(false);
            setSelectedBookingPlot(null);
          }}
        />
      )}

      {/* 2. Add Plots / Layout Wizard */}
      {isAddPlotsModalOpen && (
        <AddPlotsWizard
          onClose={() => setIsAddPlotsModalOpen(false)}
          onSuccess={() => setIsAddPlotsModalOpen(false)}
        />
      )}

      {/* 3. Record Payment Modal */}
      {isRecordPaymentModalOpen && (
        <RecordPaymentModal
          onClose={() => setIsRecordPaymentModalOpen(false)}
          onSuccess={() => setIsRecordPaymentModalOpen(false)}
        />
      )}

      {/* 4. New Buyer Inquiry Modal */}
      {isNewLeadModalOpen && (
        <LeadFormModal
          onClose={() => setIsNewLeadModalOpen(false)}
          onSuccess={() => setIsNewLeadModalOpen(false)}
        />
      )}

      {/* 5. Create Project Modal */}
      {isNewProjectModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-espresso-950/60 backdrop-blur-sm p-4">
          <div className="w-full max-w-lg bg-white rounded-2xl shadow-2xl border border-sand-300 overflow-hidden">
            <div className="p-5 bg-[#0B1411] text-white flex items-center justify-between">
              <div>
                <span className="text-xs text-emerald-400 font-mono uppercase tracking-wider">New Masterplan</span>
                <h3 className="text-lg font-serif font-bold mt-0.5">Register Plotted Project</h3>
              </div>
              <button 
                onClick={() => setIsNewProjectModalOpen(false)}
                className="p-1 rounded-lg text-white/70 hover:text-white hover:bg-white/10"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleCreateProject} className="p-6 space-y-4">
              <div>
                <label className="block text-xs font-semibold text-espresso-700 mb-1">Project / Township Name *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Green Meadows Phase 1"
                  value={newProjectForm.name}
                  onChange={e => setNewProjectForm(p => ({ ...p, name: e.target.value }))}
                  className="w-full px-3 py-2 text-sm bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 focus:border-forest"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-espresso-700 mb-1">Locality / Highway *</label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. Delhi Road"
                    value={newProjectForm.locality}
                    onChange={e => setNewProjectForm(p => ({ ...p, locality: e.target.value }))}
                    className="w-full px-3 py-2 text-sm bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 focus:border-forest"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-espresso-700 mb-1">City</label>
                  <input
                    type="text"
                    value={newProjectForm.city}
                    onChange={e => setNewProjectForm(p => ({ ...p, city: e.target.value }))}
                    className="w-full px-3 py-2 text-sm bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 focus:border-forest"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-espresso-700 mb-1">RERA Registration No.</label>
                  <input
                    type="text"
                    placeholder="UPRERA/PRJ.../2026"
                    value={newProjectForm.reraNumber}
                    onChange={e => setNewProjectForm(p => ({ ...p, reraNumber: e.target.value }))}
                    className="w-full px-3 py-2 text-sm bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 focus:border-forest font-mono"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-espresso-700 mb-1">Total Land Parcel (Bigha)</label>
                  <input
                    type="number"
                    value={newProjectForm.totalAreaValue}
                    onChange={e => setNewProjectForm(p => ({ ...p, totalAreaValue: parseFloat(e.target.value) || 0 }))}
                    className="w-full px-3 py-2 text-sm bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 focus:border-forest font-mono"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-espresso-700 mb-1">Brief Description</label>
                <textarea
                  rows={2}
                  placeholder="Colony layout specifications, arterial boulevard details..."
                  value={newProjectForm.description}
                  onChange={e => setNewProjectForm(p => ({ ...p, description: e.target.value }))}
                  className="w-full px-3 py-2 text-sm bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 focus:border-forest"
                />
              </div>

              <div className="flex items-center gap-3 pt-3">
                <button
                  type="button"
                  onClick={() => setIsNewProjectModalOpen(false)}
                  className="w-1/2 py-2.5 rounded-xl border border-sand-300 text-espresso-700 text-sm font-medium hover:bg-sand-100 transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="w-1/2 py-2.5 rounded-xl bg-forest hover:bg-forest-600 text-white text-sm font-semibold shadow-sm transition-colors"
                >
                  Create Masterplan
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
