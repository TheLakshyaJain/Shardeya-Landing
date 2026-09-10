import React, { useState } from 'react';
import { 
  ArrowRight, Layers, Users, MessageSquare, 
  Building2, Send, Check, Phone, ArrowUpRight, Sparkles 
} from 'lucide-react';
import { useLanguage } from '../../context/LanguageContext';

interface HeroSectionProps {
  onOpenDemo: () => void;
  onExploreMasterplan: () => void;
}

export const HeroSection: React.FC<HeroSectionProps> = ({ onOpenDemo, onExploreMasterplan }) => {
  const { t, language } = useLanguage();
  const isHi = language === 'hi';

  // Interactive showcase state
  const [activeTab, setActiveTab] = useState<'plots' | 'brokers' | 'whatsapp'>('plots');
  
  // Interactive plot selection state
  const [selectedHeroPlot, setSelectedHeroPlot] = useState<{
    id: string;
    num: string;
    type: string;
    size: string;
    price: string;
    status: 'available' | 'booked' | 'discussion';
    buyer?: string;
    broker?: string;
    paidPct?: number;
  }>({
    id: 'p1',
    num: 'Villa Plot #104',
    type: 'Park Facing Corner',
    size: '2,400 sq.ft',
    price: '₹2.15 Cr',
    status: 'available',
  });

  // Interactive broker slider state
  const [brokerSalesCr, setBrokerSalesCr] = useState<number>(6.5);

  // Interactive whatsapp scenario state
  const [waTriggered, setWaTriggered] = useState<string>('visit');
  const [waSentToast, setWaSentToast] = useState<boolean>(false);

  const heroPlots = [
    { id: 'p1', num: 'Plot #101', type: 'Villa Plot', size: '2,150 sq.ft', price: '₹1.85 Cr', status: 'available' as const },
    { id: 'p2', num: 'Apt A-204', type: '3 BHK Luxury', size: '1,820 sq.ft', price: '₹1.65 Cr', status: 'booked' as const, buyer: 'Vikram Malhotra', broker: 'Apex Realty', paidPct: 65 },
    { id: 'p3', num: 'Plot #103', type: 'Club Facing', size: '2,800 sq.ft', price: '₹2.45 Cr', status: 'discussion' as const, buyer: 'Pooja Agarwal', broker: 'Syndicate 9' },
    { id: 'p4', num: 'Villa Plot #104', type: 'Corner Plot', size: '2,400 sq.ft', price: '₹2.15 Cr', status: 'available' as const },
    { id: 'p5', num: 'Apt B-502', type: '4 BHK Duplex', size: '3,100 sq.ft', price: '₹2.95 Cr', status: 'booked' as const, buyer: 'Rahul Singhal', broker: 'Urban Nest', paidPct: 80 },
    { id: 'p6', num: 'Plot #106', type: 'East Facing', size: '1,950 sq.ft', price: '₹1.75 Cr', status: 'available' as const },
  ];

  // Calculated broker tier
  const getBrokerTier = (cr: number) => {
    if (cr < 3) return { name: 'Registered CP', rate: '1.0%', color: 'from-slate-600 to-slate-800', badge: 'bg-slate-100 text-slate-800 border-slate-300' };
    if (cr < 7) return { name: 'Preferred Partner', rate: '1.5%', color: 'from-amber-500 to-amber-700', badge: 'bg-amber-100 text-amber-900 border-amber-300' };
    if (cr < 12) return { name: 'Strategic Super CP', rate: '2.0%', color: 'from-emerald-600 to-teal-700', badge: 'bg-emerald-100 text-emerald-900 border-emerald-300' };
    return { name: 'Sole Selling Syndicate', rate: '2.5%', color: 'from-indigo-600 to-purple-800', badge: 'bg-indigo-100 text-indigo-900 border-indigo-300' };
  };

  const currentTier = getBrokerTier(brokerSalesCr);
  const calculatedCommissionLakhs = ((brokerSalesCr * 100) * (parseFloat(currentTier.rate) / 100)).toFixed(2);

  const handleSendWaNotice = (scenario: string) => {
    setWaTriggered(scenario);
    setWaSentToast(true);
    setTimeout(() => setWaSentToast(false), 3000);
  };

  return (
    <section className="relative min-h-[90vh] pt-32 pb-24 flex items-center justify-center overflow-hidden bg-ambient-warm drafting-grid">
      <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10 w-full text-center">
        {/* Clear, High-Impact Editorial Headline */}
        <h1 className="text-4xl sm:text-5xl lg:text-6xl xl:text-7xl font-serif text-slate-950 tracking-tight leading-[1.12] max-w-4xl mx-auto">
          {t.hero.titleLine1} <br />
          <span className="italic font-normal text-slate-800">{t.hero.titleLine2} </span>
          <span className="bg-gradient-to-r from-emerald-600 via-teal-600 to-amber-600 bg-clip-text text-transparent font-bold">
            {t.hero.titleHighlight}
          </span>
        </h1>

        {/* Simple, Plain-English Subtitle */}
        <p className="mt-6 text-base sm:text-lg lg:text-xl text-slate-600 max-w-2xl mx-auto leading-relaxed font-sans font-normal">
          {t.hero.subtitle}
        </p>

        {/* Action Button */}
        <div className="flex flex-wrap items-center justify-center gap-4 pt-8">
          <button
            onClick={onExploreMasterplan}
            className="px-8 py-4 rounded-xl bg-gradient-to-r from-emerald-600 to-emerald-700 hover:from-emerald-700 hover:to-emerald-800 text-white font-sans font-bold text-sm tracking-wide shadow-glow-emerald hover:shadow-lg transition-all transform hover:scale-[1.02] active:scale-98 flex items-center gap-2.5 group"
          >
            <span>{t.hero.ctaPrimary}</span>
            <ArrowRight className="w-4 h-4 text-white/90 group-hover:translate-x-1 transition-transform" />
          </button>
        </div>

        {/* INTERACTIVE LIVE PLATFORM SHOWCASE (Vibrant & Hands-on) */}
        <div className="mt-14 max-w-5xl mx-auto text-left">
          
          <div className="rounded-2xl border border-slate-200/90 bg-white/90 backdrop-blur-xl shadow-warm-lg overflow-hidden transition-all duration-300">
            
            {/* Top Interactive Tabs Bar */}
            <div className="bg-slate-50/90 px-4 sm:px-6 py-3 border-b border-slate-200 flex flex-wrap items-center justify-between gap-3">
              <div className="flex items-center gap-2 font-mono text-xs font-bold text-slate-700">
                <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 animate-pulse" />
                <span className="uppercase tracking-wider">{isHi ? 'प्रोजेक्ट कंसोल — एपेक्स ग्रीन्स' : 'Project Console — Apex Greens Township'}</span>
              </div>

              {/* 3 Interactive Feature Tabs */}
              <div className="flex flex-wrap items-center gap-1.5 p-1 bg-slate-200/70 rounded-xl">
                <button
                  onClick={() => setActiveTab('plots')}
                  className={`flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                    activeTab === 'plots'
                      ? 'bg-white text-emerald-800 shadow-warm-sm'
                      : 'text-slate-600 hover:text-slate-900'
                  }`}
                >
                  <Layers className="w-3.5 h-3.5 text-emerald-600" />
                  <span>{isHi ? 'प्लॉट इन्वेंटरी' : 'Plot Layouts'}</span>
                </button>

                <button
                  onClick={() => setActiveTab('brokers')}
                  className={`flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                    activeTab === 'brokers'
                      ? 'bg-white text-amber-800 shadow-warm-sm'
                      : 'text-slate-600 hover:text-slate-900'
                  }`}
                >
                  <Users className="w-3.5 h-3.5 text-amber-600" />
                  <span>{isHi ? 'ब्रोकर कमीशन' : 'Broker Engine'}</span>
                </button>

                <button
                  onClick={() => setActiveTab('whatsapp')}
                  className={`flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                    activeTab === 'whatsapp'
                      ? 'bg-white text-emerald-800 shadow-warm-sm'
                      : 'text-slate-600 hover:text-slate-900'
                  }`}
                >
                  <MessageSquare className="w-3.5 h-3.5 text-emerald-600" />
                  <span>{isHi ? 'व्हाट्सएप अलर्ट्स' : 'WhatsApp Automation'}</span>
                </button>
              </div>
            </div>

            {/* Tab 1: Interactive Plot Layouts */}
            {activeTab === 'plots' && (
              <div className="p-6 sm:p-8 grid grid-cols-1 lg:grid-cols-12 gap-6 items-center animate-fadeIn">
                {/* Plots Grid */}
                <div className="lg:col-span-7">
                  <div className="flex items-center justify-between mb-3">
                    <span className="text-xs font-bold text-slate-700 uppercase tracking-wider font-mono">
                      Phase 1 Masterplan — Click A Unit
                    </span>
                    <div className="flex items-center gap-3 text-[11px] font-sans">
                      <span className="flex items-center gap-1 text-emerald-700 font-semibold">
                        <span className="w-2 h-2 rounded-full bg-emerald-500" /> Available
                      </span>
                      <span className="flex items-center gap-1 text-slate-600 font-semibold">
                        <span className="w-2 h-2 rounded-full bg-slate-400" /> Booked
                      </span>
                      <span className="flex items-center gap-1 text-amber-700 font-semibold">
                        <span className="w-2 h-2 rounded-full bg-amber-500" /> Discussion
                      </span>
                    </div>
                  </div>

                  <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
                    {heroPlots.map((plot) => {
                      const isSelected = selectedHeroPlot.id === plot.id;
                      const statusColor = 
                        plot.status === 'available'
                          ? 'border-emerald-300 bg-emerald-50/60 hover:bg-emerald-100/70 text-emerald-900'
                          : plot.status === 'booked'
                          ? 'border-slate-300 bg-slate-100/80 hover:bg-slate-200/80 text-slate-900'
                          : 'border-amber-300 bg-amber-50/70 hover:bg-amber-100/80 text-amber-950';

                      return (
                        <button
                          key={plot.id}
                          onClick={() => setSelectedHeroPlot(plot)}
                          className={`p-3.5 rounded-xl border text-left transition-all ${statusColor} ${
                            isSelected 
                              ? 'ring-2 ring-emerald-600 shadow-md transform -translate-y-0.5' 
                              : 'hover:shadow-sm'
                          }`}
                        >
                          <div className="flex items-center justify-between">
                            <span className="font-mono text-xs font-bold">{plot.num}</span>
                            <span className={`text-[10px] px-1.5 py-0.5 rounded font-bold uppercase ${
                              plot.status === 'available' 
                                ? 'bg-emerald-200/80 text-emerald-900' 
                                : plot.status === 'booked'
                                ? 'bg-slate-300 text-slate-800'
                                : 'bg-amber-200 text-amber-900'
                            }`}>
                              {plot.status}
                            </span>
                          </div>
                          <div className="text-[11px] text-slate-600 mt-1 font-medium">{plot.size}</div>
                          <div className="font-bold text-sm mt-1 text-slate-950">{plot.price}</div>
                        </button>
                      );
                    })}
                  </div>
                </div>

                {/* Plot Inspection Panel */}
                <div className="lg:col-span-5 bg-slate-50 rounded-xl p-5 border border-slate-200 text-left">
                  <div className="flex items-center justify-between pb-3 border-b border-slate-200">
                    <div>
                      <span className="text-[10px] font-mono uppercase text-slate-500 font-bold">Selected Dossier</span>
                      <h4 className="font-serif font-bold text-lg text-slate-950">{selectedHeroPlot.num}</h4>
                    </div>
                    <span className={`px-2.5 py-1 rounded-full text-xs font-bold uppercase ${
                      selectedHeroPlot.status === 'available'
                        ? 'bg-emerald-100 text-emerald-800 border border-emerald-300'
                        : selectedHeroPlot.status === 'booked'
                        ? 'bg-slate-200 text-slate-800 border border-slate-300'
                        : 'bg-amber-100 text-amber-900 border border-amber-300'
                    }`}>
                      {selectedHeroPlot.status}
                    </span>
                  </div>

                  <div className="py-3 space-y-2 text-xs">
                    <div className="flex justify-between text-slate-600">
                      <span>Unit Type:</span>
                      <span className="font-semibold text-slate-900">{selectedHeroPlot.type}</span>
                    </div>
                    <div className="flex justify-between text-slate-600">
                      <span>Carpet / Plot Area:</span>
                      <span className="font-semibold text-slate-900">{selectedHeroPlot.size}</span>
                    </div>
                    <div className="flex justify-between text-slate-600">
                      <span>Allotment Price:</span>
                      <span className="font-bold text-emerald-700 text-sm">{selectedHeroPlot.price}</span>
                    </div>
                    {selectedHeroPlot.buyer && (
                      <div className="flex justify-between text-slate-600 pt-1 border-t border-slate-200">
                        <span>Allotted Buyer:</span>
                        <span className="font-semibold text-slate-900">{selectedHeroPlot.buyer}</span>
                      </div>
                    )}
                    {selectedHeroPlot.broker && (
                      <div className="flex justify-between text-slate-600">
                        <span>Assigned Broker:</span>
                        <span className="font-semibold text-slate-900">{selectedHeroPlot.broker}</span>
                      </div>
                    )}
                  </div>

                  <button
                    onClick={onExploreMasterplan}
                    className="w-full mt-3 py-2.5 px-4 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white font-bold text-xs flex items-center justify-center gap-2 shadow-warm-sm transition-all"
                  >
                    <span>View Full 24-Unit Masterplan</span>
                    <ArrowUpRight className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            )}

            {/* Tab 2: Interactive Broker Engine */}
            {activeTab === 'brokers' && (
              <div className="p-6 sm:p-8 grid grid-cols-1 lg:grid-cols-12 gap-6 items-center animate-fadeIn">
                <div className="lg:col-span-7 space-y-4">
                  <div>
                    <span className="text-xs font-bold text-slate-700 uppercase tracking-wider font-mono">
                      Dynamic Broker Sales Volume Slider
                    </span>
                    <p className="text-xs text-slate-600 mt-0.5">
                      Drag to simulate broker quarterly gross closing volume and witness automatic tier upgrade:
                    </p>
                  </div>

                  {/* Volume Slider */}
                  <div className="p-4 rounded-xl bg-slate-50 border border-slate-200">
                    <div className="flex justify-between items-center mb-2">
                      <span className="text-xs font-semibold text-slate-700">Quarterly Closings:</span>
                      <span className="font-serif font-bold text-xl text-emerald-700">₹{brokerSalesCr.toFixed(1)} Crore</span>
                    </div>
                    <input
                      type="range"
                      min="1.0"
                      max="15.0"
                      step="0.5"
                      value={brokerSalesCr}
                      onChange={(e) => setBrokerSalesCr(parseFloat(e.target.value))}
                      className="w-full h-2 bg-slate-200 rounded-lg appearance-none cursor-pointer accent-emerald-600"
                    />
                    <div className="flex justify-between text-[10px] text-slate-500 font-mono mt-1">
                      <span>₹1.0 Cr (Registered)</span>
                      <span>₹5.0 Cr (Preferred)</span>
                      <span>₹10.0 Cr (Super CP)</span>
                      <span>₹15.0 Cr+ (Sole CP)</span>
                    </div>
                  </div>
                </div>

                {/* Broker Outcome Card */}
                <div className="lg:col-span-5 bg-gradient-to-br from-amber-50 to-emerald-50 rounded-xl p-5 border border-amber-200/80 text-left">
                  <span className="text-[10px] font-mono font-bold uppercase text-amber-900">Auto-Promoted Level</span>
                  <div className="flex items-center justify-between mt-1">
                    <h4 className="font-serif font-bold text-xl text-slate-950">{currentTier.name}</h4>
                    <span className={`px-2.5 py-0.5 rounded-full text-xs font-bold border ${currentTier.badge}`}>
                      {currentTier.rate} Cut
                    </span>
                  </div>

                  <div className="mt-4 pt-3 border-t border-amber-200/60">
                    <div className="text-xs text-slate-600">Calculated Payout Voucher:</div>
                    <div className="font-serif font-bold text-2xl text-emerald-800 mt-0.5">
                      ₹{calculatedCommissionLakhs} Lakhs
                    </div>
                    <div className="text-[11px] text-slate-500 mt-1 flex items-center gap-1.5">
                      <Check className="w-3.5 h-3.5 text-emerald-600" />
                      <span>Instant WhatsApp Voucher & Tax TDS Ledger</span>
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* Tab 3: Interactive WhatsApp Automation */}
            {activeTab === 'whatsapp' && (
              <div className="p-6 sm:p-8 grid grid-cols-1 lg:grid-cols-12 gap-6 items-center animate-fadeIn">
                <div className="lg:col-span-6 space-y-3">
                  <span className="text-xs font-bold text-slate-700 uppercase tracking-wider font-mono">
                    {isHi ? 'व्हाट्सएप ऑटोमेशन एक्शन' : 'WhatsApp Automation In Action'}
                  </span>
                  <p className="text-xs text-slate-600">
                    {isHi ? 'नीचे किसी भी एक्शन पर क्लिक करके देखें कि संदेश कैसे भेजे जाते हैं:' : 'Select any trigger below to preview automatic WhatsApp notices:'}
                  </p>

                  <div className="space-y-2">
                    <button
                      onClick={() => handleSendWaNotice('visit')}
                      className={`w-full p-3 rounded-xl border text-left flex items-center justify-between transition-all ${
                        waTriggered === 'visit'
                          ? 'bg-emerald-50 border-emerald-400 text-emerald-950 font-bold shadow-sm'
                          : 'bg-white border-slate-200 text-slate-700 hover:bg-slate-50'
                      }`}
                    >
                      <div className="flex items-center gap-2.5 text-xs">
                        <Send className="w-3.5 h-3.5 text-emerald-600" />
                        <span>1. Issue Site Visit Gate-Pass with Live GPS</span>
                      </div>
                      <ArrowRight className="w-3.5 h-3.5 text-slate-400" />
                    </button>

                    <button
                      onClick={() => handleSendWaNotice('milestone')}
                      className={`w-full p-3 rounded-xl border text-left flex items-center justify-between transition-all ${
                        waTriggered === 'milestone'
                          ? 'bg-emerald-50 border-emerald-400 text-emerald-950 font-bold shadow-sm'
                          : 'bg-white border-slate-200 text-slate-700 hover:bg-slate-50'
                      }`}
                    >
                      <div className="flex items-center gap-2.5 text-xs">
                        <Send className="w-3.5 h-3.5 text-emerald-600" />
                        <span>2. Send RERA 3rd Slab Demand Notice (₹25L)</span>
                      </div>
                      <ArrowRight className="w-3.5 h-3.5 text-slate-400" />
                    </button>

                    <button
                      onClick={() => handleSendWaNotice('bilingual')}
                      className={`w-full p-3 rounded-xl border text-left flex items-center justify-between transition-all ${
                        waTriggered === 'bilingual'
                          ? 'bg-emerald-50 border-emerald-400 text-emerald-950 font-bold shadow-sm'
                          : 'bg-white border-slate-200 text-slate-700 hover:bg-slate-50'
                      }`}
                    >
                      <div className="flex items-center gap-2.5 text-xs">
                        <Send className="w-3.5 h-3.5 text-emerald-600" />
                        <span>3. Bilingual Buyer Lead Qualification (हिन्दी/EN)</span>
                      </div>
                      <ArrowRight className="w-3.5 h-3.5 text-slate-400" />
                    </button>
                  </div>

                  {waSentToast && (
                    <div className="p-2.5 rounded-lg bg-emerald-100 border border-emerald-300 text-emerald-800 text-xs flex items-center gap-2 animate-fadeIn font-medium">
                      <Check className="w-4 h-4 text-emerald-700 shrink-0" />
                      <span>WhatsApp API simulation dispatched successfully!</span>
                    </div>
                  )}
                </div>

                {/* Smartphone Preview Mockup */}
                <div className="lg:col-span-6 bg-slate-900 text-white rounded-2xl p-4 shadow-xl text-left font-sans">
                  <div className="flex items-center justify-between pb-3 border-b border-slate-800">
                    <div className="flex items-center gap-2">
                      <div className="w-7 h-7 rounded-full bg-emerald-600 flex items-center justify-center text-white font-bold text-xs">
                        S
                      </div>
                      <div>
                        <div className="text-xs font-bold text-white flex items-center gap-1">
                          <span>Shardeya Verified Business</span>
                          <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 inline-block" />
                        </div>
                        <div className="text-[10px] text-slate-400">Official Cloud API Gateway</div>
                      </div>
                    </div>
                    <span className="text-[10px] font-mono text-emerald-400 bg-emerald-950/80 px-2 py-0.5 rounded border border-emerald-800">
                      LIVE
                    </span>
                  </div>

                  <div className="py-4 space-y-3 text-xs">
                    {waTriggered === 'visit' && (
                      <div className="bg-emerald-950/70 border border-emerald-800/80 p-3 rounded-xl space-y-1.5 animate-fadeIn">
                        <div className="font-bold text-emerald-300">🎟️ Gate Pass: Apex Greens Township</div>
                        <div className="text-slate-300 text-[11px]">
                          Namaste Rajeshwar ji! Your site visit for <b>Villa Plot #104</b> is confirmed for Saturday 11:30 AM.
                        </div>
                        <div className="text-[10px] text-emerald-400 font-mono pt-1">
                          📍 GPS Directions: maps.google.com/?q=28.4595,77.0266
                        </div>
                      </div>
                    )}

                    {waTriggered === 'milestone' && (
                      <div className="bg-emerald-950/70 border border-emerald-800/80 p-3 rounded-xl space-y-1.5 animate-fadeIn">
                        <div className="font-bold text-emerald-300">📑 RERA Demand Notice • Slab 3</div>
                        <div className="text-slate-300 text-[11px]">
                          Unit A-204 construction has reached 5th floor casting. Milestone due: <b>₹25,00,000</b>.
                        </div>
                        <div className="text-[10px] text-emerald-400 font-mono pt-1">
                          🏦 RERA Escrow A/C: 0048-XXXX-9912 (70% Ring-Fenced)
                        </div>
                      </div>
                    )}

                    {waTriggered === 'bilingual' && (
                      <div className="bg-emerald-950/70 border border-emerald-800/80 p-3 rounded-xl space-y-1.5 animate-fadeIn">
                        <div className="font-bold text-emerald-300">🤖 Shardeya AI Assistant / शार्देय सहायक</div>
                        <div className="text-slate-300 text-[11px]">
                          नमस्ते विक्रम जी! क्या आप 3 BHK पार्क फेसिंग फ्लैट का ब्रोशर और पेमेंट प्लान देखना चाहेंगे?
                        </div>
                        <div className="text-[10px] text-emerald-400 font-mono pt-1">
                          Reply 1 for PDF Brochure • Reply 2 to Book Call
                        </div>
                      </div>
                    )}
                  </div>
                </div>
              </div>
            )}

          </div>

        </div>

      </div>
    </section>
  );
};
