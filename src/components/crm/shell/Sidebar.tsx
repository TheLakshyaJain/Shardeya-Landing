import React from 'react';
import { 
  LayoutDashboard, Building2, Layers, Users, 
  Wallet, FileText, Handshake, CalendarDays, 
  Calculator, BarChart3, LogOut, ChevronRight,
  ShieldCheck, X
} from 'lucide-react';
import { useAuth } from '../../../context/AuthContext';
import { useLanguage } from '../../../context/LanguageContext';

export type CrmTab = 
  | 'dashboard' 
  | 'projects' 
  | 'plots' 
  | 'leads' 
  | 'financials' 
  | 'deals' 
  | 'brokers' 
  | 'calendar' 
  | 'calculators' 
  | 'reports'
  | 'shared-inventory'
  | 'commissions';

interface SidebarProps {
  currentTab: CrmTab;
  onSelectTab: (tab: CrmTab) => void;
  isOpenMobile: boolean;
  onCloseMobile: () => void;
  onBackToLanding: () => void;
}

export const Sidebar: React.FC<SidebarProps> = ({
  currentTab,
  onSelectTab,
  isOpenMobile,
  onCloseMobile,
  onBackToLanding
}) => {
  const { user, logout } = useAuth();
  const { language } = useLanguage();
  const isHi = language === 'hi';
  const isDeveloper = user?.role === 'developer';

  const developerNav = [
    { id: 'dashboard' as CrmTab, label: isHi ? 'डैशबोर्ड' : 'Executive Dashboard', icon: LayoutDashboard },
    { id: 'projects' as CrmTab, label: isHi ? 'प्रोजेक्ट्स व मास्टरप्लान' : 'Projects & Phases', icon: Building2 },
    { id: 'plots' as CrmTab, label: isHi ? 'प्लॉट इन्वेंटरी ग्रिड' : 'Plot Inventory Matrix', icon: Layers },
    { id: 'leads' as CrmTab, label: isHi ? 'लीड्स व ग्राहक CRM' : 'Buyer Leads CRM', icon: Users },
    { id: 'financials' as CrmTab, label: isHi ? 'वित्तीय व किस्त संग्रह' : 'Financials & Milestones', icon: Wallet },
    { id: 'deals' as CrmTab, label: isHi ? 'आवंटन व सौदे' : 'Deals & Allotments', icon: FileText },
    { id: 'brokers' as CrmTab, label: isHi ? 'चैनल पार्टनर नेटवर्क' : 'Broker Syndicate Engine', icon: Handshake },
    { id: 'calendar' as CrmTab, label: isHi ? 'साइट विजिट कैलेंडर' : 'Site Visits & Calendar', icon: CalendarDays },
    { id: 'calculators' as CrmTab, label: isHi ? 'रियल एस्टेट कैलकुलेटर' : 'Financial Calculators', icon: Calculator },
    { id: 'reports' as CrmTab, label: isHi ? 'रिपोर्ट्स व ऑडिट' : 'Reports & Analytics', icon: BarChart3 },
  ];

  const brokerNav = [
    { id: 'dashboard' as CrmTab, label: isHi ? 'पार्टनर डैशबोर्ड' : 'Partner Dashboard', icon: LayoutDashboard },
    { id: 'shared-inventory' as CrmTab, label: isHi ? 'डेवलपर इन्वेंटरी' : 'Shared Inventories', icon: Building2 },
    { id: 'leads' as CrmTab, label: isHi ? 'मेरे ग्राहक व लीड्स' : 'Protected Leads (48h)', icon: Users },
    { id: 'commissions' as CrmTab, label: isHi ? 'कमीशन वाउचर्स' : 'Commission Ledgers', icon: Wallet },
    { id: 'calendar' as CrmTab, label: isHi ? 'विजिट्स कैलेंडर' : 'Site Visits Calendar', icon: CalendarDays },
    { id: 'calculators' as CrmTab, label: isHi ? 'कैलकुलेटर' : 'Calculators & Stamp Duty', icon: Calculator },
  ];

  const navItems = isDeveloper ? developerNav : brokerNav;

  return (
    <>
      {/* Mobile Backdrop */}
      {isOpenMobile && (
        <div 
          onClick={onCloseMobile}
          className="fixed inset-0 z-40 bg-espresso-950/50 backdrop-blur-sm lg:hidden"
        />
      )}

      {/* Sidebar Container */}
      <aside className={`
        fixed top-0 bottom-0 left-0 z-50 w-64 bg-[#0B1411] text-white flex flex-col justify-between
        transition-transform duration-300 ease-in-out border-r border-emerald-950/80
        lg:static lg:translate-x-0
        ${isOpenMobile ? 'translate-x-0' : '-translate-x-full'}
      `}>
        {/* Brand Header */}
        <div>
          <div className="h-16 flex items-center justify-between px-5 border-b border-white/10 bg-white/[0.02]">
            <div className="flex items-center gap-3">
              <div className="w-8 h-8 rounded-lg bg-forest flex items-center justify-center font-serif font-bold text-white text-base shadow-sm">
                S
              </div>
              <div>
                <span className="font-serif font-bold text-lg tracking-tight text-white block leading-none">
                  SHARDEYA
                </span>
                <span className="text-[9px] font-mono text-emerald-400/80 uppercase tracking-widest block mt-1">
                  {isDeveloper ? 'Developer Console' : 'Partner Portal'}
                </span>
              </div>
            </div>

            {/* Mobile Close Button */}
            <button 
              onClick={onCloseMobile}
              className="p-1 rounded-lg text-sand-400 hover:text-white lg:hidden"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          {/* Navigation Links */}
          <nav className="p-3 space-y-1 overflow-y-auto max-h-[calc(100vh-14rem)]">
            {navItems.map((item) => {
              const Icon = item.icon;
              const isActive = currentTab === item.id;

              return (
                <button
                  key={item.id}
                  onClick={() => {
                    onSelectTab(item.id);
                    onCloseMobile();
                  }}
                  className={`
                    w-full flex items-center justify-between px-3 py-2.5 rounded-xl text-xs font-sans font-medium transition-all
                    ${isActive 
                      ? 'bg-forest text-white shadow-warm-sm font-semibold' 
                      : 'text-sand-300 hover:bg-white/5 hover:text-white'
                    }
                  `}
                >
                  <div className="flex items-center gap-3">
                    <Icon className={`w-4 h-4 ${isActive ? 'text-white' : 'text-emerald-400/80'}`} />
                    <span>{item.label}</span>
                  </div>
                  {isActive && <ChevronRight className="w-3.5 h-3.5 opacity-80" />}
                </button>
              );
            })}
          </nav>
        </div>

        {/* User Capsule & Quick Actions */}
        <div className="p-3 border-t border-white/10 bg-white/[0.02] space-y-2">
          {/* User info */}
          <div className="p-2.5 rounded-xl bg-white/[0.04] border border-white/5 flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-full bg-forest text-white flex items-center justify-center font-serif text-xs font-bold shrink-0">
              {user?.avatarInitials || 'SH'}
            </div>
            <div className="min-w-0 flex-1">
              <div className="text-xs font-bold text-white truncate">{user?.name}</div>
              <div className="text-[10px] font-mono text-emerald-400/80 truncate">
                {user?.organization}
              </div>
            </div>
          </div>

          {/* Return & Signout buttons */}
          <div className="grid grid-cols-2 gap-2 text-xs">
            <button
              onClick={onBackToLanding}
              className="py-2 px-2.5 rounded-lg bg-white/5 hover:bg-white/10 text-sand-300 hover:text-white text-[11px] font-sans font-medium transition-colors text-center"
            >
              Exit to Site
            </button>
            <button
              onClick={logout}
              className="py-2 px-2.5 rounded-lg border border-rose-900/30 bg-rose-950/20 hover:bg-rose-900/30 text-rose-300 text-[11px] font-sans font-medium transition-colors flex items-center justify-center gap-1.5"
            >
              <LogOut className="w-3 h-3" />
              <span>Sign Out</span>
            </button>
          </div>
        </div>
      </aside>
    </>
  );
};
