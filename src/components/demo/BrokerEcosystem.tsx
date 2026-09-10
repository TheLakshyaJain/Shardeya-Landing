import React, { useState } from 'react';
import { Award, TrendingUp, CheckCircle2, IndianRupee, ShieldCheck } from 'lucide-react';
import { sampleBrokerTiers } from '../../data/sampleData';
import { useLanguage } from '../../context/LanguageContext';
import { GlassCard } from '../common/GlassCard';

export const BrokerEcosystem: React.FC = () => {
  const { t, language } = useLanguage();
  const [salesCr, setSalesCr] = useState<number>(18);

  const isHi = language === 'hi';

  const getCurrentTierIndex = (volume: number) => {
    if (volume >= 30) return 3;
    if (volume >= 15) return 2;
    if (volume >= 5) return 1;
    return 0;
  };

  const currentTierIndex = getCurrentTierIndex(salesCr);
  const currentTier = sampleBrokerTiers[currentTierIndex];

  const effectiveRate = currentTier.baseCommissionPercent + currentTier.bonusPercent;
  const totalSalesInINR = salesCr * 10000000;
  const baseEarnings = totalSalesInINR * (currentTier.baseCommissionPercent / 100);
  const bonusEarnings = totalSalesInINR * (currentTier.bonusPercent / 100);
  const totalEarnings = baseEarnings + bonusEarnings;

  const nextTier = currentTierIndex < sampleBrokerTiers.length - 1 ? sampleBrokerTiers[currentTierIndex + 1] : null;
  const gapToNextTier = nextTier ? nextTier.minSalesCr - salesCr : 0;

  const formatRupees = (amount: number) => {
    if (amount >= 10000000) {
      return `₹${(amount / 10000000).toFixed(2)} Cr`;
    }
    return `₹${(amount / 100000).toFixed(2)} Lakh`;
  };

  return (
    <section id="brokers" className="py-24 relative bg-slate-50/50 border-b border-slate-200">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        
        {/* Section Header */}
        <div className="text-center max-w-3xl mx-auto mb-16">
          <div className="inline-flex items-center gap-2 px-3.5 py-1 rounded-full border border-amber-200 bg-amber-50 text-amber-900 text-xs font-sans mb-3 shadow-warm-sm">
            <span className="w-2 h-2 rounded-full bg-amber-500" />
            <span className="font-semibold">{t.broker.tag}</span>
          </div>
          <h2 className="text-3xl sm:text-4xl lg:text-5xl font-serif text-slate-950 font-normal tracking-tight">
            {t.broker.title}
          </h2>
          <p className="mt-4 text-base text-slate-600 font-sans">
            {t.broker.subtitle}
          </p>
        </div>

        {/* 4-Tier Cards */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5 mb-12">
          {sampleBrokerTiers.map((tier, idx) => {
            const isCurrentActive = idx === currentTierIndex;
            return (
              <GlassCard
                key={tier.name}
                variant={isCurrentActive ? 'emerald' : 'default'}
                className={`p-6 text-left transition-all duration-300 ${
                  isCurrentActive
                    ? 'border-amber-400 ring-2 ring-amber-400/80 bg-gradient-to-b from-amber-50/70 to-white shadow-glow-amber scale-[1.03]'
                    : 'opacity-85 hover:opacity-100 border-slate-200 bg-white hover:shadow-md'
                }`}
              >
                <div className="flex items-center justify-between mb-3">
                  <span className="text-[11px] font-sans font-bold text-slate-500 uppercase">LEVEL 0{idx + 1}</span>
                  {isCurrentActive && (
                    <span className="px-2 py-0.5 rounded-full bg-amber-500 text-white text-[9px] font-sans font-bold uppercase tracking-wider shadow-sm">
                      {isHi ? 'सक्रिय लेवल' : 'ACTIVE LEVEL'}
                    </span>
                  )}
                </div>

                <h3 className="text-lg font-serif font-bold text-slate-950 mb-0.5">{tier.name}</h3>
                <div className="text-xs text-slate-600 mb-4 font-sans font-medium">
                  {tier.minSalesCr === 0
                    ? (isHi ? 'आरंभिक स्तर' : 'Entry Level')
                    : `Sales Target: ₹${tier.minSalesCr} Cr+`}
                </div>

                <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 mb-4">
                  <div className="text-[11px] font-sans text-slate-600 uppercase font-bold">{t.broker.commissionRate}</div>
                  <div className="text-2xl font-serif font-bold text-emerald-700 mt-0.5">
                    {(tier.baseCommissionPercent + tier.bonusPercent).toFixed(1)}%
                  </div>
                  <div className="text-[10px] text-slate-500 font-sans mt-0.5">
                    {tier.baseCommissionPercent}% Base + {tier.bonusPercent}% Bonus
                  </div>
                </div>

                <ul className="space-y-2 text-xs text-slate-700 font-sans">
                  {tier.perks.map((perk, pIdx) => (
                    <li key={pIdx} className="flex items-start gap-2">
                      <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600 shrink-0 mt-0.5" />
                      <span>{perk}</span>
                    </li>
                  ))}
                </ul>
              </GlassCard>
            );
          })}
        </div>

        {/* Dynamic Calculator Console */}
        <div className="bg-white/90 border border-slate-200 rounded-2xl p-6 sm:p-10 shadow-warm-md">
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-center">
            
            {/* Slider */}
            <div className="lg:col-span-7 space-y-6 text-left">
              <div>
                <div className="flex items-center justify-between mb-2 font-sans">
                  <label className="text-xs font-bold uppercase tracking-wider text-espresso-700">
                    {t.broker.salesVolumeSlider}
                  </label>
                  <span className="text-2xl font-serif font-bold text-espresso-950">
                    ₹{salesCr} Crores
                  </span>
                </div>

                <input
                  type="range"
                  min="1"
                  max="50"
                  step="1"
                  value={salesCr}
                  onChange={(e) => setSalesCr(Number(e.target.value))}
                  className="w-full h-2 bg-sand-200 rounded-lg appearance-none cursor-pointer accent-forest border border-sand-300"
                />
                <div className="flex justify-between text-[10px] text-espresso-500 mt-1.5 font-sans">
                  <span>₹1 Cr (Registered CP)</span>
                  <span>₹5 Cr (Preferred)</span>
                  <span>₹15 Cr (Super CP)</span>
                  <span>₹30 Cr+ (Sole Syndicate)</span>
                </div>
              </div>

              {/* Next Tier */}
              {nextTier ? (
                <div className="p-4 rounded-xl bg-sand-50 border border-sand-300 flex items-center justify-between shadow-warm-sm">
                  <div className="flex items-center gap-3">
                    <div className="p-2 rounded-lg bg-bronze-subtle text-bronze">
                      <TrendingUp className="w-5 h-5" />
                    </div>
                    <div>
                      <div className="text-xs font-bold text-espresso-900 font-sans">
                        {isHi
                          ? `अगला लेवल: ${nextTier.name}`
                          : `Next Level: ${nextTier.name}`}
                      </div>
                      <div className="text-[11px] text-espresso-600 font-sans">
                        {isHi
                          ? `मात्र ₹${gapToNextTier} करोड़ की और बिक्री से ${(nextTier.baseCommissionPercent + nextTier.bonusPercent).toFixed(1)}% कमीशन दर प्राप्त करें।`
                          : `Sell ₹${gapToNextTier} Cr more to reach ${(nextTier.baseCommissionPercent + nextTier.bonusPercent).toFixed(1)}% commission.`}
                      </div>
                    </div>
                  </div>
                  <div className="text-xs font-sans font-bold text-forest bg-forest-subtle px-2.5 py-1 rounded border border-forest/20">
                    +{nextTier.bonusPercent}% Extra
                  </div>
                </div>
              ) : (
                <div className="p-4 rounded-xl bg-forest-subtle border border-forest/30 flex items-center gap-3 text-forest text-xs font-bold font-sans">
                  <ShieldCheck className="w-5 h-5 shrink-0" />
                  <span>
                    {isHi
                      ? 'सर्वोच्च "सोल सेलिंग सिंडिकेट" लेवल — 3.0% शीर्ष कमीशन व एस्क्रो से सीधा भुगतान।'
                      : 'Highest Sole Selling Syndicate Level Reached — 3.0% top commission rate with direct escrow disbursal.'}
                  </span>
                </div>
              )}

              {/* Rules */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-2">
                {t.broker.featuresList.map((item, idx) => (
                  <div key={idx} className="flex items-start gap-2 text-xs text-espresso-700 font-sans">
                    <CheckCircle2 className="w-4 h-4 text-forest shrink-0 mt-0.5" />
                    <span>{item}</span>
                  </div>
                ))}
              </div>
            </div>

            {/* Payout Summary */}
            <div className="lg:col-span-5">
              <div className="p-6 sm:p-8 rounded-2xl bg-white border border-sand-300 shadow-warm-md text-left">
                <div className="text-[11px] font-sans font-bold uppercase tracking-wider text-espresso-500 mb-1">
                  {t.broker.calculatedEarnings}
                </div>
                <div className="text-3xl sm:text-4xl font-serif font-bold text-espresso-950 mb-4">
                  {formatRupees(totalEarnings)}
                </div>

                <div className="space-y-3 py-4 border-y border-sand-200 text-xs font-sans">
                  <div className="flex justify-between text-espresso-700">
                    <span>{isHi ? 'आधार कमीशन' : 'Base Commission'} ({currentTier.baseCommissionPercent}%):</span>
                    <span className="font-bold text-espresso-900">{formatRupees(baseEarnings)}</span>
                  </div>
                  <div className="flex justify-between text-espresso-700">
                    <span>{isHi ? 'सुपर-क्लोजर बोनस' : 'Closer Bonus'} (+{currentTier.bonusPercent}%):</span>
                    <span className="font-bold text-forest">+{formatRupees(bonusEarnings)}</span>
                  </div>
                  <div className="flex justify-between text-espresso-700">
                    <span>{isHi ? 'प्रभावी कमीशन दर' : 'Net Commission Rate'}:</span>
                    <span className="font-bold text-bronze-dark">{effectiveRate.toFixed(1)}%</span>
                  </div>
                </div>

                <div className="mt-5">
                  <button
                    onClick={() => alert(`Generated Commission Voucher for ₹${salesCr} Cr volume.`)}
                    className="w-full py-3.5 px-4 rounded-xl bg-forest hover:bg-forest-light text-white font-sans font-bold text-xs uppercase tracking-wider shadow-warm-sm transition-all flex items-center justify-center gap-2"
                  >
                    <IndianRupee className="w-4 h-4" />
                    {isHi ? 'कमीशन वाउचर डाउनलोड करें' : 'Download Commission Voucher'}
                  </button>
                </div>
              </div>
            </div>

          </div>
        </div>

      </div>
    </section>
  );
};
