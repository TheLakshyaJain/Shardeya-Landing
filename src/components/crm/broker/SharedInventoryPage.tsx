import React, { useState } from 'react';
import { 
  Building2, Search, Filter, Share2, Download, 
  CheckCircle2, Compass, ShieldCheck, MapPin, 
  FileText, ExternalLink, Sparkles
} from 'lucide-react';
import { useCrm } from '../../../context/CrmContext';
import { useAuth } from '../../../context/AuthContext';
import { useLanguage } from '../../../context/LanguageContext';
import { Plot } from '../../../types/crm';

export const SharedInventoryPage: React.FC = () => {
  const { plots, activeProject, brokers } = useCrm();
  const { user } = useAuth();
  const { language } = useLanguage();
  const isHi = language === 'hi';

  const currentBroker = brokers.find(b => b.email === user?.email) || brokers[0];
  const commissionRate = currentBroker?.commissionRate || 2.5;

  const [searchQuery, setSearchQuery] = useState('');
  const [filterFacing, setFilterFacing] = useState('ALL');
  const [filterCorner, setFilterCorner] = useState(false);

  const availablePlots = plots.filter(p => {
    const matchesSearch = p.plotNumber.toLowerCase().includes(searchQuery.toLowerCase()) ||
                          p.sizeSqft.toString().includes(searchQuery);
    const matchesFacing = filterFacing === 'ALL' || p.facing === filterFacing;
    const matchesCorner = !filterCorner || p.isCorner;
    return matchesSearch && matchesFacing && matchesCorner;
  });

  const sharePlotOnWhatsApp = (plot: Plot) => {
    const gaj = (plot.sizeSqft / 9).toFixed(0);
    const text = encodeURIComponent(
      `*SHARDEYA EXCLUSIVE REAL ESTATE INVENTORY*\n\n` +
      `📌 *Unit:* ${plot.plotNumber} (${activeProject?.name || 'Apex Greens'})\n` +
      `📐 *Dimensions:* ${plot.sizeSqft} Sq Ft (~${gaj} Gaj)\n` +
      `🧭 *Facing:* ${plot.facing} ${plot.isCorner ? '• CORNER PLOT' : ''} ${plot.isGarden ? '• PARK FACING' : ''}\n` +
      `💰 *Allotment Price:* ₹${plot.price.toLocaleString('en-IN')}\n` +
      `🏛️ *RERA Registered:* ${activeProject?.reraNumber || 'UPRERA/PRJ992182/2024'}\n\n` +
      `Features: 60ft wide roads, underground electricity cabling, gated community with 24/7 biometric security.\n` +
      `Contact authorized Channel Partner: ${currentBroker?.firmName} (${currentBroker?.mobile})`
    );
    window.open(`https://wa.me/?text=${text}`, '_blank');
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-forest/10 text-forest border border-forest/20 tracking-wide uppercase">
              {isHi ? 'साझा इन्वेंटरी' : 'Live Developer Inventory'}
            </span>
            <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-amber-100 text-amber-800">
              Your Commission: {commissionRate}%
            </span>
          </div>
          <h1 className="text-2xl sm:text-3xl font-serif font-bold text-espresso-950 mt-1">
            {activeProject?.name || 'Apex Greens'} {isHi ? 'प्लॉट इन्वेंटरी' : 'Available Units'}
          </h1>
          <p className="text-sm text-espresso-700">
            {isHi 
              ? 'बिल्डर द्वारा अधिकृत लाइव प्लॉट कैटलॉग। क्लाइंट्स के साथ सीधे शेयर करें व कमीशन लॉक करें।' 
              : 'Direct builder inventory pipeline. Pre-verified RERA titles with guaranteed brokerage allocation.'}
          </p>
        </div>

        <div className="p-3 rounded-xl bg-forest/10 border border-forest/20 text-xs text-forest font-medium flex items-center gap-2">
          <ShieldCheck className="w-4 h-4 text-forest shrink-0" />
          <span>Double-Allotment Protected: Units lock immediately upon submission</span>
        </div>
      </div>

      {/* Filter Row */}
      <div className="p-4 rounded-xl bg-white border border-sand-300 shadow-sm flex flex-col md:flex-row items-stretch md:items-center justify-between gap-3">
        <div className="relative flex-1">
          <Search className="w-4 h-4 text-espresso-400 absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            placeholder={isHi ? 'प्लॉट नंबर या साइज से खोजें...' : 'Search plot number or sqft...'}
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            className="w-full pl-9 pr-4 py-2 text-sm bg-sand-50 border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 focus:border-forest"
          />
        </div>

        <div className="flex items-center gap-2 flex-wrap">
          <select
            value={filterFacing}
            onChange={e => setFilterFacing(e.target.value)}
            className="px-3 py-2 text-xs bg-white border border-sand-300 rounded-lg text-espresso-800 font-medium focus:outline-none focus:ring-2 focus:ring-forest/20"
          >
            <option value="ALL">All Facings</option>
            <option value="N">North Facing</option>
            <option value="NE">North-East (Ishan)</option>
            <option value="E">East Facing</option>
            <option value="W">West Facing</option>
            <option value="S">South Facing</option>
          </select>

          <button
            onClick={() => setFilterCorner(!filterCorner)}
            className={`px-3 py-2 rounded-lg text-xs font-semibold transition-colors ${
              filterCorner 
                ? 'bg-forest text-white' 
                : 'bg-white border border-sand-300 text-espresso-700 hover:bg-sand-100'
            }`}
          >
            Corner Units Only
          </button>
        </div>
      </div>

      {/* Plots Card Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {availablePlots.map(plot => {
          const estimatedCommission = (plot.price * commissionRate) / 100;
          const gaj = (plot.sizeSqft / 9).toFixed(0);

          return (
            <div 
              key={plot.id}
              className="p-5 rounded-xl bg-white border border-sand-300 shadow-sm flex flex-col justify-between hover:border-sand-400 hover:shadow-md transition-all"
            >
              <div>
                {/* Header */}
                <div className="flex items-start justify-between">
                  <div>
                    <span className={`px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider ${
                      plot.status === 'AVAILABLE' ? 'bg-emerald-100 text-emerald-800' :
                      plot.status === 'RESERVED' ? 'bg-amber-100 text-amber-800' :
                      'bg-slate-100 text-slate-800'
                    }`}>
                      {plot.status}
                    </span>
                    <h3 className="text-xl font-serif font-bold text-espresso-950 mt-1">
                      {plot.plotNumber}
                    </h3>
                  </div>

                  <div className="flex gap-1">
                    {plot.isCorner && (
                      <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-amber-100 text-amber-800">
                        CORNER
                      </span>
                    )}
                    {plot.isGarden && (
                      <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-100 text-emerald-800">
                        PARK
                      </span>
                    )}
                  </div>
                </div>

                {/* Specs */}
                <div className="mt-4 grid grid-cols-2 gap-2 text-xs">
                  <div className="p-2 rounded bg-sand-50 border border-sand-200">
                    <span className="text-espresso-500">Dimensions:</span>
                    <div className="font-bold text-espresso-950 mt-0.5 font-mono">
                      {plot.sizeSqft} sqft <span className="text-espresso-500 font-sans font-normal">({gaj} Gaj)</span>
                    </div>
                  </div>
                  <div className="p-2 rounded bg-sand-50 border border-sand-200">
                    <span className="text-espresso-500">Direction:</span>
                    <div className="font-bold text-espresso-950 mt-0.5 flex items-center gap-1">
                      <Compass className="w-3.5 h-3.5 text-forest" />
                      {plot.facing} Facing
                    </div>
                  </div>
                </div>

                {/* Pricing & Brokerage */}
                <div className="mt-4 pt-3 border-t border-sand-200 space-y-1">
                  <div className="flex justify-between items-baseline">
                    <span className="text-xs text-espresso-600">Unit Deal Value:</span>
                    <span className="text-lg font-serif font-bold text-espresso-950 font-mono">
                      ₹{plot.price.toLocaleString('en-IN')}
                    </span>
                  </div>
                  <div className="flex justify-between items-center text-xs p-2 rounded-lg bg-emerald-50 text-emerald-900 border border-emerald-200">
                    <span className="font-medium">Your Brokerage ({commissionRate}%):</span>
                    <span className="font-mono font-bold text-forest">
                      ₹{Math.round(estimatedCommission).toLocaleString('en-IN')}
                    </span>
                  </div>
                </div>
              </div>

              {/* Action Buttons */}
              <div className="mt-4 pt-3 border-t border-sand-200 flex items-center gap-2">
                <button
                  onClick={() => sharePlotOnWhatsApp(plot)}
                  className="w-full py-2 rounded-xl bg-forest hover:bg-forest-600 text-white text-xs font-semibold shadow-sm transition-colors flex items-center justify-center gap-1.5"
                >
                  <Share2 className="w-3.5 h-3.5" />
                  Share with Client (WhatsApp)
                </button>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
