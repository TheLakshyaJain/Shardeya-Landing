import React, { useState } from 'react';
import { 
  Menu, Search, Bell, Globe, ChevronDown, Plus, 
  Building2, Users, Wallet, Layers, AlertCircle, CheckCircle2 
} from 'lucide-react';
import { useCrm } from '../../../context/CrmContext';
import { useLanguage } from '../../../context/LanguageContext';
import { useAuth } from '../../../context/AuthContext';

interface TopBarProps {
  onOpenMobileSidebar: () => void;
  onOpenNewLead?: () => void;
  onOpenNewPlot?: () => void;
  onOpenRecordPayment?: () => void;
  searchQuery: string;
  onSearchChange: (q: string) => void;
}

export const TopBar: React.FC<TopBarProps> = ({
  onOpenMobileSidebar,
  onOpenNewLead,
  onOpenNewPlot,
  onOpenRecordPayment,
  searchQuery,
  onSearchChange,
}) => {
  const { projects, activeProjectId, setActiveProjectId, paymentSchedules } = useCrm();
  const { language, toggleLanguage } = useLanguage();
  const { user } = useAuth();

  const [projectMenuOpen, setProjectMenuOpen] = useState(false);
  const [quickMenuOpen, setQuickMenuOpen] = useState(false);
  const [notifOpen, setNotifOpen] = useState(false);

  const isDeveloper = user?.role === 'developer';
  const activeProject = projects.find((p) => p.id === activeProjectId);

  // Calculate overdue alerts
  const overdueCount = paymentSchedules.filter((s) => s.status === 'OVERDUE').length;

  return (
    <header className="h-16 bg-white border-b border-sand-300 px-4 sm:px-6 flex items-center justify-between sticky top-0 z-30 shadow-warm-sm">
      
      {/* Left: Mobile hamburger & Project selector */}
      <div className="flex items-center gap-3">
        <button
          onClick={onOpenMobileSidebar}
          className="p-2 rounded-lg text-espresso-700 hover:bg-sand-100 lg:hidden"
        >
          <Menu className="w-5 h-5" />
        </button>

        {isDeveloper && (
          <div className="relative">
            <button
              onClick={() => setProjectMenuOpen(!projectMenuOpen)}
              className="flex items-center gap-2.5 px-3 py-1.5 rounded-xl border border-sand-300 bg-sand-50 hover:bg-white text-xs font-sans font-bold text-espresso-900 transition-all shadow-warm-sm"
            >
              <div className="w-2 h-2 rounded-full bg-forest animate-pulse" />
              <span className="truncate max-w-[140px] sm:max-w-[220px]">
                {activeProject?.name || 'Select Project'}
              </span>
              <ChevronDown className="w-3.5 h-3.5 text-espresso-500" />
            </button>

            {projectMenuOpen && (
              <div className="absolute left-0 mt-2 w-64 bg-white rounded-2xl border border-sand-300 shadow-warm-lg p-2 z-50 animate-fadeIn">
                <div className="text-[10px] font-mono uppercase tracking-wider text-espresso-500 px-2.5 py-1">
                  Active Projects
                </div>
                {projects.map((proj) => (
                  <button
                    key={proj.id}
                    onClick={() => {
                      setActiveProjectId(proj.id);
                      setProjectMenuOpen(false);
                    }}
                    className={`w-full text-left px-3 py-2 rounded-xl text-xs font-sans transition-all flex items-center justify-between ${
                      proj.id === activeProjectId 
                        ? 'bg-forest/10 text-forest font-bold' 
                        : 'text-espresso-800 hover:bg-sand-100'
                    }`}
                  >
                    <div>
                      <div className="font-semibold">{proj.name}</div>
                      <div className="text-[10px] text-espresso-500">{proj.locality}</div>
                    </div>
                    {proj.id === activeProjectId && <CheckCircle2 className="w-3.5 h-3.5 text-forest" />}
                  </button>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {/* Center: Global Search */}
      <div className="hidden md:flex items-center flex-1 max-w-md mx-6">
        <div className="relative w-full">
          <Search className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-espresso-400" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => onSearchChange(e.target.value)}
            placeholder="Search plots (e.g. #104), buyer phone, or RERA ID..."
            className="w-full pl-10 pr-4 py-1.5 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white focus:border-forest text-xs font-sans text-espresso-950 placeholder:text-espresso-400 outline-none transition-all"
          />
        </div>
      </div>

      {/* Right Action Icons */}
      <div className="flex items-center gap-2.5">
        
        {/* Quick Action "+ New" Button (Developer) */}
        {isDeveloper && (
          <div className="relative">
            <button
              onClick={() => setQuickMenuOpen(!quickMenuOpen)}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-forest hover:bg-forest-light text-white text-xs font-sans font-bold shadow-warm-sm transition-all"
            >
              <Plus className="w-3.5 h-3.5" />
              <span className="hidden sm:inline">New Entry</span>
              <ChevronDown className="w-3 h-3" />
            </button>

            {quickMenuOpen && (
              <div className="absolute right-0 mt-2 w-48 bg-white rounded-2xl border border-sand-300 shadow-warm-lg p-1.5 z-50 animate-fadeIn">
                <button
                  onClick={() => { setQuickMenuOpen(false); onOpenNewLead?.(); }}
                  className="w-full text-left px-3 py-2 rounded-xl text-xs text-espresso-800 hover:bg-sand-100 flex items-center gap-2"
                >
                  <Users className="w-4 h-4 text-forest" />
                  <span>Add Buyer Lead</span>
                </button>
                <button
                  onClick={() => { setQuickMenuOpen(false); onOpenRecordPayment?.(); }}
                  className="w-full text-left px-3 py-2 rounded-xl text-xs text-espresso-800 hover:bg-sand-100 flex items-center gap-2"
                >
                  <Wallet className="w-4 h-4 text-forest" />
                  <span>Record Payment</span>
                </button>
                <button
                  onClick={() => { setQuickMenuOpen(false); onOpenNewPlot?.(); }}
                  className="w-full text-left px-3 py-2 rounded-xl text-xs text-espresso-800 hover:bg-sand-100 flex items-center gap-2"
                >
                  <Layers className="w-4 h-4 text-forest" />
                  <span>Add Plots (Bulk)</span>
                </button>
              </div>
            )}
          </div>
        )}

        {/* Language Switcher */}
        <button
          onClick={toggleLanguage}
          className="p-2 rounded-lg border border-sand-300 bg-sand-50 hover:bg-sand-100 text-xs font-mono font-bold text-espresso-800 transition-all"
          title="Toggle English / हिन्दी"
        >
          <span className="text-[11px] font-bold">{language.toUpperCase()}</span>
        </button>

        {/* Notifications Bell */}
        <div className="relative">
          <button
            onClick={() => setNotifOpen(!notifOpen)}
            className="p-2 rounded-lg border border-sand-300 bg-sand-50 hover:bg-sand-100 text-espresso-700 relative transition-all"
            title="Notifications"
          >
            <Bell className="w-4 h-4" />
            {overdueCount > 0 && (
              <span className="absolute -top-1 -right-1 w-4 h-4 bg-rose-600 text-white rounded-full text-[9px] font-bold flex items-center justify-center">
                {overdueCount}
              </span>
            )}
          </button>

          {notifOpen && (
            <div className="absolute right-0 mt-2 w-80 bg-white rounded-2xl border border-sand-300 shadow-warm-lg p-3 z-50 animate-fadeIn text-left">
              <div className="flex items-center justify-between pb-2 border-b border-sand-200">
                <span className="font-serif font-bold text-sm text-espresso-950">Urgent Alerts</span>
                <span className="text-[10px] font-mono bg-rose-100 text-rose-800 px-1.5 py-0.5 rounded font-bold">
                  {overdueCount} Overdue
                </span>
              </div>
              <div className="mt-2 space-y-2 max-h-64 overflow-y-auto text-xs">
                {overdueCount > 0 ? (
                  paymentSchedules.filter((s) => s.status === 'OVERDUE').map((item) => (
                    <div key={item.id} className="p-2.5 rounded-xl bg-rose-50 border border-rose-200 text-rose-900 space-y-1">
                      <div className="flex items-center gap-1.5 font-bold">
                        <AlertCircle className="w-3.5 h-3.5 text-rose-600" />
                        <span>{item.label}</span>
                      </div>
                      <div className="text-[11px] text-rose-800">
                        Amount: ₹{(item.expectedAmount).toLocaleString('en-IN')} • {item.daysOverdue} days overdue
                      </div>
                    </div>
                  ))
                ) : (
                  <div className="py-4 text-center text-espresso-500 text-xs">
                    All payment collections are current.
                  </div>
                )}
              </div>
            </div>
          )}
        </div>

      </div>
    </header>
  );
};
