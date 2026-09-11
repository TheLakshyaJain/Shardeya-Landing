import React from 'react';
import { 
  Building2, Layers, CheckCircle2, Clock, 
  Users, PhoneCall, Wallet, IndianRupee, 
  ArrowUpRight, AlertTriangle, FileText, ArrowRight,
  Plus, Upload
} from 'lucide-react';
import { useCrm } from '../../../context/CrmContext';
import { CrmTab } from '../shell/Sidebar';

interface BuilderDashboardProps {
  onNavigate: (tab: CrmTab) => void;
  onOpenNewLead: () => void;
  onOpenRecordPayment: () => void;
  onOpenNewPlot: () => void;
}

export const BuilderDashboard: React.FC<BuilderDashboardProps> = ({
  onNavigate,
  onOpenNewLead,
  onOpenRecordPayment,
  onOpenNewPlot,
}) => {
  const { 
    projects, plots, plotSales, paymentSchedules, 
    paymentRecords, leads, activeProject 
  } = useCrm();

  // Metrics Calculation
  const totalProjects = projects.length;
  const projectPlots = plots.filter((p) => p.projectId === activeProject?.id);
  const totalPlotsCount = projectPlots.length;
  const availablePlotsCount = projectPlots.filter((p) => p.status === 'AVAILABLE').length;
  const soldPlotsCount = projectPlots.filter((p) => p.status === 'SOLD').length;
  const reservedPlotsCount = projectPlots.filter((p) => p.status === 'RESERVED').length;

  const activeLeadsCount = leads.filter((l) => l.status !== 'DEAL_CLOSED' && l.status !== 'LOST').length;
  const followUpsTodayCount = leads.filter((l) => l.followUpDate === '2026-09-12' || l.followUpDate === '2026-09-13').length;

  // Financials
  const overdueSchedules = paymentSchedules.filter((s) => s.status === 'OVERDUE');
  const overdueAmount = overdueSchedules.reduce((acc, curr) => acc + (curr.expectedAmount - curr.amountAllocated), 0);
  const totalRevenuePaid = paymentRecords.reduce((acc, curr) => acc + curr.amount, 0);

  const kpis = [
    {
      label: 'Active Colonies / Projects',
      value: totalProjects.toString(),
      subValue: `${activeProject?.declaredPlotCount || 148} Declared Masterplan Units`,
      icon: Building2,
      tab: 'projects' as CrmTab,
      badge: 'Active RERA',
      badgeColor: 'bg-emerald-50 text-emerald-800 border-emerald-200',
    },
    {
      label: 'Plotted Inventory Matrix',
      value: totalPlotsCount.toString(),
      subValue: `${availablePlotsCount} Available · ${soldPlotsCount} Sold`,
      icon: Layers,
      tab: 'plots' as CrmTab,
      badge: `${Math.round((soldPlotsCount / (totalPlotsCount || 1)) * 100)}% Absorbed`,
      badgeColor: 'bg-blue-50 text-blue-800 border-blue-200',
    },
    {
      label: 'Available Ready Plots',
      value: availablePlotsCount.toString(),
      subValue: 'Ready for immediate booking token',
      icon: CheckCircle2,
      tab: 'plots' as CrmTab,
      badge: 'Open for CP',
      badgeColor: 'bg-emerald-50 text-emerald-800 border-emerald-200',
    },
    {
      label: '48h Temporary Holds',
      value: reservedPlotsCount.toString(),
      subValue: reservedPlotsCount > 0 ? `${reservedPlotsCount} plots reserved by buyers` : 'No active reservations',
      icon: Clock,
      tab: 'plots' as CrmTab,
      badge: reservedPlotsCount > 0 ? 'Active Holds' : 'None Active',
      badgeColor: 'bg-amber-50 text-amber-800 border-amber-200',
    },
    {
      label: 'Active Buyer Leads',
      value: activeLeadsCount.toString(),
      subValue: activeLeadsCount > 0 ? `${activeLeadsCount} inquiries in pipeline` : 'No active inquiries',
      icon: Users,
      tab: 'leads' as CrmTab,
      badge: activeLeadsCount > 0 ? `${activeLeadsCount} Active` : 'Clean Pipeline',
      badgeColor: 'bg-emerald-50 text-emerald-800 border-emerald-200',
    },
    {
      label: 'Pending Follow-Ups Today',
      value: followUpsTodayCount.toString(),
      subValue: followUpsTodayCount > 0 ? `${followUpsTodayCount} tasks scheduled today` : 'No appointments today',
      icon: PhoneCall,
      tab: 'calendar' as CrmTab,
      badge: 'High Priority',
      badgeColor: 'bg-indigo-50 text-indigo-800 border-indigo-200',
    },
    {
      label: 'Milestone Demands Due',
      value: `₹${(overdueAmount / 100000).toFixed(1)}L`,
      subValue: `${overdueSchedules.length} pending installment demand notices`,
      icon: AlertTriangle,
      tab: 'financials' as CrmTab,
      badge: 'Overdue Action',
      badgeColor: 'bg-rose-50 text-rose-800 border-rose-200',
    },
    {
      label: 'Total Revenue Realized',
      value: `₹${(totalRevenuePaid / 10000000).toFixed(2)} Cr`,
      subValue: 'Bank RTGS/Cheque verified escrow',
      icon: IndianRupee,
      tab: 'financials' as CrmTab,
      badge: 'Escrow Cleared',
      badgeColor: 'bg-emerald-50 text-emerald-800 border-emerald-200',
    },
  ];

  return (
    <div className="space-y-6 animate-fadeIn text-left">
      
      {/* Top Banner: Project Title & Quick Actions Row */}
      <div className="bg-white rounded-2xl border border-sand-300 p-5 sm:p-6 shadow-warm-sm flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="px-2 py-0.5 rounded bg-forest/10 text-forest text-[11px] font-mono font-bold">
              {activeProject?.reraNumber}
            </span>
            <span className="text-xs text-espresso-500 font-sans">• {activeProject?.locality}</span>
          </div>
          <h1 className="font-serif font-bold text-2xl sm:text-3xl text-espresso-950 mt-1">
            {activeProject?.name}
          </h1>
          <p className="text-xs text-espresso-600 mt-1 max-w-xl">
            {activeProject?.description}
          </p>
        </div>

        {/* Action Row */}
        <div className="flex flex-wrap items-center gap-2.5">
          <button
            onClick={onOpenNewLead}
            className="px-3.5 py-2 rounded-xl bg-forest hover:bg-forest-light text-white text-xs font-sans font-bold shadow-warm-sm transition-all flex items-center gap-1.5"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Add Buyer Lead</span>
          </button>

          <button
            onClick={onOpenRecordPayment}
            className="px-3.5 py-2 rounded-xl border border-sand-300 bg-sand-50 hover:bg-sand-100 text-espresso-800 text-xs font-sans font-bold shadow-warm-sm transition-all flex items-center gap-1.5"
          >
            <Wallet className="w-3.5 h-3.5 text-forest" />
            <span>Record Payment</span>
          </button>

          <button
            onClick={onOpenNewPlot}
            className="px-3.5 py-2 rounded-xl border border-sand-300 bg-sand-50 hover:bg-sand-100 text-espresso-800 text-xs font-sans font-bold shadow-warm-sm transition-all flex items-center gap-1.5"
          >
            <Upload className="w-3.5 h-3.5 text-forest" />
            <span>Bulk Plots</span>
          </button>
        </div>
      </div>

      {/* Urgent Attention Alert Banner if overdue instalments exist */}
      {overdueSchedules.length > 0 && (
        <div className="p-4 rounded-2xl bg-rose-50/80 border border-rose-200 shadow-warm-sm flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex items-start gap-3">
            <div className="p-2 rounded-xl bg-rose-100 text-rose-700 mt-0.5">
              <AlertTriangle className="w-5 h-5" />
            </div>
            <div>
              <h4 className="font-sans font-bold text-sm text-rose-950">
                Action Required: {overdueSchedules.length} Milestone Installment Demands Overdue
              </h4>
              <p className="text-xs text-rose-800 mt-0.5">
                Total outstanding ₹{(overdueAmount).toLocaleString('en-IN')}. Dispatch automated WhatsApp reminders and formal demand letters.
              </p>
            </div>
          </div>
          <button
            onClick={() => onNavigate('financials')}
            className="px-4 py-2 rounded-xl bg-rose-700 hover:bg-rose-800 text-white text-xs font-sans font-bold shadow-sm transition-all flex items-center justify-center gap-1.5 shrink-0"
          >
            <span>Open Collection Tracker</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </button>
        </div>
      )}

      {/* 10-KPI Summary Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {kpis.map((kpi, idx) => {
          const Icon = kpi.icon;
          return (
            <div
              key={idx}
              onClick={() => onNavigate(kpi.tab)}
              className="p-5 rounded-2xl bg-white border border-sand-300 hover:border-forest/50 hover:shadow-warm-md transition-all cursor-pointer group relative overflow-hidden"
            >
              <div className="flex items-center justify-between mb-3">
                <span className={`text-[10px] font-mono px-2 py-0.5 rounded border font-semibold ${kpi.badgeColor}`}>
                  {kpi.badge}
                </span>
                <div className="p-2 rounded-xl bg-sand-100 text-forest group-hover:bg-forest group-hover:text-white transition-colors">
                  <Icon className="w-4 h-4" />
                </div>
              </div>

              <div className="font-serif font-bold text-2xl text-espresso-950 group-hover:text-forest transition-colors">
                {kpi.value}
              </div>
              <div className="text-xs font-sans font-bold text-espresso-800 mt-1">
                {kpi.label}
              </div>
              <div className="text-[11px] text-espresso-500 mt-0.5 truncate">
                {kpi.subValue}
              </div>

              <div className="mt-3 pt-3 border-t border-sand-200 flex items-center justify-between text-[11px] font-semibold text-forest">
                <span>View Details</span>
                <ArrowUpRight className="w-3.5 h-3.5 group-hover:translate-x-0.5 group-hover:-translate-y-0.5 transition-transform" />
              </div>
            </div>
          );
        })}
      </div>

      {/* Bottom Row: Recent Allotment Activity & Lead Pipeline Status */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        
        {/* Recent Allotments */}
        <div className="bg-white rounded-2xl border border-sand-300 p-5 shadow-warm-sm">
          <div className="flex items-center justify-between pb-3 border-b border-sand-200">
            <div className="flex items-center gap-2">
              <FileText className="w-4 h-4 text-forest" />
              <h3 className="font-serif font-bold text-base text-espresso-950">
                Recent Unit Allotments
              </h3>
            </div>
            <button
              onClick={() => onNavigate('deals')}
              className="text-xs font-semibold text-forest hover:underline"
            >
              View All Deals →
            </button>
          </div>

          <div className="divide-y divide-sand-200 mt-2">
            {plotSales.length === 0 ? (
              <div className="py-8 text-center text-xs text-espresso-500">
                No unit allotments executed yet. Select an available plot from the matrix to book.
              </div>
            ) : (
              plotSales.slice(0, 4).map((sale) => (
                <div key={sale.id} className="py-3 flex items-center justify-between text-xs">
                  <div>
                    <div className="font-bold text-espresso-950">{sale.buyerName}</div>
                    <div className="text-[11px] text-espresso-500 font-mono">
                      {sale.allotmentLetterNo} • {sale.purchaseDate}
                    </div>
                  </div>
                  <div className="text-right">
                    <div className="font-mono font-bold text-espresso-900">
                      ₹{(sale.dealValue).toLocaleString('en-IN')}
                    </div>
                    <span className={`inline-block px-1.5 py-0.5 rounded text-[10px] font-mono font-bold ${
                      sale.status === 'COMPLETED' ? 'bg-emerald-100 text-emerald-800' : 'bg-blue-100 text-blue-800'
                    }`}>
                      {sale.status}
                    </span>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        {/* Buyer Leads In Pipeline */}
        <div className="bg-white rounded-2xl border border-sand-300 p-5 shadow-warm-sm">
          <div className="flex items-center justify-between pb-3 border-b border-sand-200">
            <div className="flex items-center gap-2">
              <Users className="w-4 h-4 text-forest" />
              <h3 className="font-serif font-bold text-base text-espresso-950">
                Priority Buyer Pipeline
              </h3>
            </div>
            <button
              onClick={() => onNavigate('leads')}
              className="text-xs font-semibold text-forest hover:underline"
            >
              View All Leads →
            </button>
          </div>

          <div className="divide-y divide-sand-200 mt-2">
            {leads.length === 0 ? (
              <div className="py-8 text-center text-xs text-espresso-500">
                No leads in pipeline yet. Click '+ Add Buyer Lead' to register inquiries.
              </div>
            ) : (
              leads.slice(0, 4).map((lead) => (
                <div key={lead.id} className="py-3 flex items-center justify-between text-xs">
                  <div>
                    <div className="font-bold text-espresso-950 flex items-center gap-1.5">
                      <span>{lead.fullName}</span>
                      {lead.isImportant && <span className="text-amber-500">★</span>}
                    </div>
                    <div className="text-[11px] text-espresso-500">
                      {lead.mobile} • Budget: ₹{(lead.budgetMin/100000).toFixed(0)}L - ₹{(lead.budgetMax/100000).toFixed(0)}L
                    </div>
                  </div>
                  <div className="text-right">
                    <span className="inline-block px-2 py-0.5 rounded-full text-[10px] font-sans font-bold bg-sand-100 text-espresso-700 border border-sand-200">
                      {lead.status.replace(/_/g, ' ')}
                    </span>
                    <div className="text-[10px] text-espresso-400 mt-0.5">
                      Follow-up: {lead.followUpDate}
                    </div>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

      </div>

    </div>
  );
};
