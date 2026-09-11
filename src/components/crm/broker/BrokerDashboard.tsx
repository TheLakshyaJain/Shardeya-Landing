import React from 'react';
import { 
  ShieldCheck, Award, Users, Wallet, Clock, 
  Building2, ArrowRight, Share2, Download, 
  CheckCircle2, AlertCircle, Phone, MessageSquare
} from 'lucide-react';
import { useCrm } from '../../../context/CrmContext';
import { useAuth } from '../../../context/AuthContext';
import { useLanguage } from '../../../context/LanguageContext';
import { CrmTab } from '../shell/Sidebar';

interface BrokerDashboardProps {
  onNavigate: (tab: CrmTab) => void;
  onOpenNewLead: () => void;
}

export const BrokerDashboard: React.FC<BrokerDashboardProps> = ({
  onNavigate,
  onOpenNewLead
}) => {
  const { user } = useAuth();
  const { brokers, vouchers, leads, plots, activeProject } = useCrm();
  const { language } = useLanguage();
  const isHi = language === 'hi';

  // Current logged in broker (or fallback to Diamond CP for demo)
  const currentBroker = brokers.find(b => b.email === user?.email) || brokers[0];

  const brokerVouchers = vouchers.filter(v => v.brokerId === currentBroker?.id);
  const pendingVouchers = brokerVouchers.filter(v => v.status !== 'PAID');
  const totalEarned = currentBroker?.totalCommissionEarned || 0;
  const totalPaid = currentBroker?.totalCommissionPaid || 0;
  const totalPending = pendingVouchers.reduce((s, v) => s + v.netPayable, 0);

  // Protected leads for this broker
  const brokerLeads = leads.filter(l => l.source === 'BROKER' && (!l.sourceBrokerId || l.sourceBrokerId === currentBroker?.id));

  return (
    <div className="space-y-6">
      {/* Top Banner with Broker Credentials */}
      <div className="p-6 rounded-2xl bg-gradient-to-r from-[#0B1411] via-[#0E201B] to-[#0B1411] text-white border border-emerald-900/60 shadow-lg relative overflow-hidden">
        <div className="absolute right-0 top-0 bottom-0 w-1/3 bg-[radial-gradient(ellipse_at_top_right,_var(--tw-gradient-stops))] from-forest/30 via-transparent to-transparent pointer-events-none" />

        <div className="relative z-10 flex flex-col md:flex-row md:items-center justify-between gap-6">
          <div>
            <div className="flex items-center gap-2 mb-2 flex-wrap">
              <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 tracking-wide uppercase">
                {isHi ? 'प्रमाणित चैनल पार्टनर' : 'Authorized Channel Partner'}
              </span>
              <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-amber-400/20 text-amber-300 border border-amber-400/30 tracking-wide">
                {currentBroker?.tier} Tier Partner ({currentBroker?.commissionRate}%)
              </span>
              <span className="text-xs text-sand-300 font-mono flex items-center gap-1">
                <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
                RERA: {currentBroker?.reraNumber || 'MAHARERA/A51800001234'}
              </span>
            </div>

            <h1 className="text-2xl sm:text-3xl font-serif font-bold text-white">
              {currentBroker?.firmName || 'Diamond Channel Syndicate'}
            </h1>
            <p className="text-sm text-sand-300 mt-1">
              Principal: <strong className="text-white">{currentBroker?.fullName || user?.name}</strong> • Territory: {currentBroker?.cityArea || 'Delhi NCR & Western UP'}
            </p>
          </div>

          <div className="flex items-center gap-3">
            <button
              onClick={onOpenNewLead}
              className="px-4 py-2.5 rounded-xl bg-forest hover:bg-forest-600 text-white text-sm font-semibold shadow-md transition-colors flex items-center gap-2 shrink-0"
            >
              <Users className="w-4 h-4" />
              {isHi ? 'नया ग्राहक लॉक करें (48h)' : 'Register Protected Lead'}
            </button>
            <button
              onClick={() => onNavigate('shared-inventory')}
              className="px-4 py-2.5 rounded-xl bg-white/10 hover:bg-white/15 text-white text-sm font-medium border border-white/20 transition-colors shrink-0"
            >
              {isHi ? 'इन्वेंटरी देखें' : 'View Inventory'}
            </button>
          </div>
        </div>
      </div>

      {/* 4 Stat Cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="p-4 rounded-xl bg-white border border-sand-300 shadow-sm">
          <div className="text-xs font-medium text-espresso-600 uppercase tracking-wider">
            {isHi ? 'कुल अर्जित कमीशन' : 'Lifetime Earnings'}
          </div>
          <div className="text-2xl font-bold font-serif text-espresso-950 mt-1">
            ₹{(totalEarned / 100000).toFixed(2)} L
          </div>
          <div className="text-xs text-forest mt-1 flex items-center gap-1 font-medium">
            <CheckCircle2 className="w-3.5 h-3.5" /> ₹{(totalPaid / 100000).toFixed(2)}L Disbursed
          </div>
        </div>

        <div className="p-4 rounded-xl bg-white border border-amber-200 bg-amber-50/40 shadow-sm">
          <div className="text-xs font-medium text-amber-900 uppercase tracking-wider">
            {isHi ? 'लंबित वाउचर्स' : 'Pending Settlement'}
          </div>
          <div className="text-2xl font-bold font-serif text-amber-950 mt-1">
            ₹{(totalPending / 100000).toFixed(2)} L
          </div>
          <div className="text-xs text-amber-700 mt-1">
            {pendingVouchers.length} vouchers under clearance
          </div>
        </div>

        <div className="p-4 rounded-xl bg-white border border-sand-300 shadow-sm">
          <div className="text-xs font-medium text-espresso-600 uppercase tracking-wider">
            {isHi ? '48-घंटे सुरक्षित लीड्स' : 'Active Protected Leads'}
          </div>
          <div className="text-2xl font-bold font-serif text-espresso-950 mt-1">
            {brokerLeads.length} Buyers
          </div>
          <div className="text-xs text-forest mt-1 flex items-center gap-1 font-medium">
            <ShieldCheck className="w-3.5 h-3.5" /> Poach-proof lock
          </div>
        </div>

        <div className="p-4 rounded-xl bg-white border border-sand-300 shadow-sm">
          <div className="text-xs font-medium text-espresso-600 uppercase tracking-wider">
            {isHi ? 'बंद किए गए सौदे' : 'Units Allotted'}
          </div>
          <div className="text-2xl font-bold font-serif text-espresso-950 mt-1">
            {currentBroker?.dealsClosedCount || 0} Units
          </div>
          <div className="text-xs text-espresso-600 mt-1">
            Qualified for {currentBroker?.tier} tier
          </div>
        </div>
      </div>

      {/* 48-Hour Lead Protection Clause & Active Protected Buyers */}
      <div className="p-5 rounded-2xl bg-white border border-sand-300 shadow-sm space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-sand-200 pb-3">
          <div>
            <h3 className="text-base font-serif font-bold text-espresso-950 flex items-center gap-2">
              <Clock className="w-4 h-4 text-forest" />
              {isHi ? '48-घंटे एक्सक्लूसिव लीड प्रोटेक्शन' : '48-Hour Protected Lead Syndicate'}
            </h3>
            <p className="text-xs text-espresso-600">
              When you submit a buyer lead, the client phone number is locked strictly to your account for 48 hours to eliminate developer bypass and broker poaching.
            </p>
          </div>
          <button
            onClick={() => onNavigate('leads')}
            className="text-xs text-forest font-semibold hover:underline flex items-center gap-1 self-start sm:self-auto"
          >
            All Leads ({brokerLeads.length}) <ArrowRight className="w-3.5 h-3.5" />
          </button>
        </div>

        <div className="space-y-2.5">
          {brokerLeads.map(lead => (
            <div key={lead.id} className="p-3.5 rounded-xl border border-sand-200 bg-sand-50/50 flex flex-col sm:flex-row sm:items-center justify-between gap-3 hover:bg-sand-100/60 transition-colors">
              <div>
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="font-semibold text-espresso-950 text-sm">{lead.fullName}</span>
                  <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-emerald-100 text-emerald-800 flex items-center gap-1">
                    <ShieldCheck className="w-3 h-3" /> 48h Locked
                  </span>
                  <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-sand-200 text-espresso-700">
                    {lead.status.replace('_', ' ')}
                  </span>
                </div>
                <div className="text-xs text-espresso-600 mt-1 flex items-center gap-3 flex-wrap">
                  <span className="font-mono">{lead.mobile}</span>
                  <span>Budget: ₹{(lead.budgetMin / 100000).toFixed(0)} - {(lead.budgetMax / 100000).toFixed(0)}L</span>
                  <span>Interested: {activeProject?.name}</span>
                </div>
              </div>

              <div className="flex items-center gap-2 self-end sm:self-center">
                <a
                  href={`tel:${lead.mobile}`}
                  className="p-2 rounded-lg bg-white border border-sand-300 text-espresso-700 hover:text-forest transition-colors"
                  title="Call Buyer"
                >
                  <Phone className="w-4 h-4" />
                </a>
                <a
                  href={`https://wa.me/${lead.mobile.replace(/[^0-9]/g, '')}`}
                  target="_blank"
                  rel="noreferrer"
                  className="p-2 rounded-lg bg-white border border-sand-300 text-emerald-600 hover:bg-emerald-50 transition-colors"
                  title="WhatsApp"
                >
                  <MessageSquare className="w-4 h-4" />
                </a>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Available Developer Inventories Preview */}
      <div className="p-5 rounded-2xl bg-white border border-sand-300 shadow-sm space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-base font-serif font-bold text-espresso-950 flex items-center gap-2">
              <Building2 className="w-4 h-4 text-forest" />
              {isHi ? 'उपलब्ध डेवलपर इन्वेंटरी' : 'Available Developer Inventory & Payout Rates'}
            </h3>
            <p className="text-xs text-espresso-600">
              Live inventory feed from Apex Greens & Royal Palm Orchards with instant commission vouchers.
            </p>
          </div>
          <button
            onClick={() => onNavigate('shared-inventory')}
            className="text-xs text-forest font-semibold hover:underline flex items-center gap-1"
          >
            Full Matrix <ArrowRight className="w-3.5 h-3.5" />
          </button>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="p-4 rounded-xl border border-sand-200 bg-sand-50/50">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-forest uppercase">Plotted Township</span>
              <span className="text-xs font-mono font-bold text-espresso-900">UPRERA Certified</span>
            </div>
            <h4 className="text-base font-serif font-bold text-espresso-950 mt-1">Apex Greens Phase 1 & 2</h4>
            <p className="text-xs text-espresso-600 mt-0.5">Saharanpur - Dehradun Corridor • 1800 - 2700 Sq Ft plots</p>
            <div className="mt-4 pt-3 border-t border-sand-200 flex items-center justify-between text-xs">
              <span className="font-semibold text-forest">Your Rate: {currentBroker?.commissionRate}% (₹90k - ₹1.5L / unit)</span>
              <span className="text-espresso-600">{plots.filter(p => p.status === 'AVAILABLE').length} Available</span>
            </div>
          </div>

          <div className="p-4 rounded-xl border border-sand-200 bg-sand-50/50">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-forest uppercase">Luxury Villa Plots</span>
              <span className="text-xs font-mono font-bold text-espresso-900">HARERA Certified</span>
            </div>
            <h4 className="text-base font-serif font-bold text-espresso-950 mt-1">Royal Palm Orchards</h4>
            <p className="text-xs text-espresso-600 mt-0.5">Sohna Elevated Expressway, Gurugram • Bespoke country estates</p>
            <div className="mt-4 pt-3 border-t border-sand-200 flex items-center justify-between text-xs">
              <span className="font-semibold text-forest">Your Rate: {currentBroker?.commissionRate}% (₹2.0L - ₹3.5L / unit)</span>
              <span className="text-espresso-600">85 Total Units</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
