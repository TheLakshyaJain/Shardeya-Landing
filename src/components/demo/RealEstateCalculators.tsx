import React, { useState } from 'react';
import { Calculator, PieChart, Building, Landmark } from 'lucide-react';
import { useLanguage } from '../../context/LanguageContext';
import { GlassCard } from '../common/GlassCard';

export const RealEstateCalculators: React.FC = () => {
  const { t } = useLanguage();
  const [activeTab, setActiveTab] = useState<'emi' | 'cashflow' | 'stampduty'>('emi');

  // EMI Calculator
  const [loanAmount, setLoanAmount] = useState<number>(7500000);
  const [interestRate, setInterestRate] = useState<number>(8.5);
  const [tenureYears, setTenureYears] = useState<number>(20);

  const monthlyRate = interestRate / (12 * 100);
  const totalMonths = tenureYears * 12;
  const emi =
    (loanAmount * monthlyRate * Math.pow(1 + monthlyRate, totalMonths)) /
    (Math.pow(1 + monthlyRate, totalMonths) - 1);
  const totalPayable = emi * totalMonths;
  const totalInterest = totalPayable - loanAmount;

  // Builder Cash Flow
  const [projectGrossCr, setProjectGrossCr] = useState<number>(120);
  const [stagePercent, setStagePercent] = useState<number>(45);

  const realizedCash = (projectGrossCr * (stagePercent / 100));
  const upcomingQuarterReceivable = (projectGrossCr * 0.15);

  // Stamp Duty
  const [propertyValueLakh, setPropertyValueLakh] = useState<number>(120);
  const [selectedState, setSelectedState] = useState<{ name: string; rate: number; regPercent: number }>({
    name: 'Maharashtra',
    rate: 6.0,
    regPercent: 1.0,
  });

  const stateTariffs = [
    { name: 'Maharashtra', rate: 6.0, regPercent: 1.0 },
    { name: 'Delhi NCR', rate: 6.0, regPercent: 1.0 },
    { name: 'Karnataka', rate: 5.0, regPercent: 1.0 },
    { name: 'Uttar Pradesh (Noida/Lucknow)', rate: 7.0, regPercent: 1.0 },
    { name: 'Haryana (Gurugram)', rate: 6.0, regPercent: 0.5 },
  ];

  const propValueINR = propertyValueLakh * 100000;
  const stampDutyAmount = propValueINR * (selectedState.rate / 100);
  const registrationFee = propValueINR * (selectedState.regPercent / 100);
  const totalGovtTaxes = stampDutyAmount + registrationFee;

  const formatCurrency = (amount: number) => {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0,
    }).format(amount);
  };

  return (
    <section id="calculators" className="py-24 relative bg-sand-50 border-b border-sand-300">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        
        {/* Header */}
        <div className="text-center max-w-3xl mx-auto mb-14">
          <div className="inline-flex items-center gap-2 px-3.5 py-1 rounded-full border border-sand-300 bg-sand-150 text-espresso-800 text-xs font-sans mb-3 shadow-warm-sm">
            <span className="w-2 h-2 rounded-full bg-forest" />
            <span className="font-semibold">{t.calculators.tag}</span>
          </div>
          <h2 className="text-3xl sm:text-4xl lg:text-5xl font-serif text-espresso-950 font-normal tracking-tight">
            {t.calculators.title}
          </h2>
          <p className="mt-4 text-base text-espresso-700 font-sans">
            {t.calculators.subtitle}
          </p>
        </div>

        {/* Tab Navigation */}
        <div className="flex justify-center mb-10">
          <div className="p-1 bg-slate-200/70 rounded-xl border border-slate-300/80 shadow-warm-sm flex flex-wrap gap-1 font-sans">
            <button
              onClick={() => setActiveTab('emi')}
              className={`px-5 py-2.5 rounded-lg text-xs font-semibold transition-all flex items-center gap-2 ${
                activeTab === 'emi'
                  ? 'bg-emerald-700 text-white shadow-sm font-bold ring-1 ring-emerald-800'
                  : 'text-slate-700 hover:text-slate-950 hover:bg-white/70'
              }`}
            >
              <PieChart className="w-3.5 h-3.5" />
              {t.calculators.tabEmi}
            </button>
            <button
              onClick={() => setActiveTab('cashflow')}
              className={`px-5 py-2.5 rounded-lg text-xs font-semibold transition-all flex items-center gap-2 ${
                activeTab === 'cashflow'
                  ? 'bg-emerald-700 text-white shadow-sm font-bold ring-1 ring-emerald-800'
                  : 'text-slate-700 hover:text-slate-950 hover:bg-white/70'
              }`}
            >
              <Building className="w-3.5 h-3.5" />
              {t.calculators.tabCashFlow}
            </button>
            <button
              onClick={() => setActiveTab('stampduty')}
              className={`px-5 py-2.5 rounded-lg text-xs font-semibold transition-all flex items-center gap-2 ${
                activeTab === 'stampduty'
                  ? 'bg-emerald-700 text-white shadow-sm font-bold ring-1 ring-emerald-800'
                  : 'text-slate-700 hover:text-slate-950 hover:bg-white/70'
              }`}
            >
              <Landmark className="w-3.5 h-3.5" />
              {t.calculators.tabStampDuty}
            </button>
          </div>
        </div>

        {/* Active Calculator Content */}
        <div className="max-w-4xl mx-auto">
          {activeTab === 'emi' && (
            <GlassCard variant="default" className="p-6 sm:p-10 text-left bg-sand-100/90 border-sand-300 shadow-warm-md">
              <div className="grid grid-cols-1 md:grid-cols-12 gap-8 items-center">
                
                <div className="md:col-span-7 space-y-6">
                  <div>
                    <div className="flex justify-between text-xs font-sans font-bold text-espresso-800 mb-2">
                      <span>{t.calculators.loanAmount}</span>
                      <span className="text-forest font-bold text-sm">{formatCurrency(loanAmount)}</span>
                    </div>
                    <input
                      type="range"
                      min="1000000"
                      max="50000000"
                      step="500000"
                      value={loanAmount}
                      onChange={(e) => setLoanAmount(Number(e.target.value))}
                      className="w-full h-2 bg-sand-200 rounded-lg appearance-none cursor-pointer accent-forest border border-sand-300"
                    />
                    <div className="flex justify-between text-[10px] text-espresso-500 mt-1 font-sans">
                      <span>₹10 Lakh</span>
                      <span>₹5 Crore</span>
                    </div>
                  </div>

                  <div>
                    <div className="flex justify-between text-xs font-sans font-bold text-espresso-800 mb-2">
                      <span>{t.calculators.interestRate}</span>
                      <span className="text-forest font-bold text-sm">{interestRate}%</span>
                    </div>
                    <input
                      type="range"
                      min="7.0"
                      max="14.0"
                      step="0.1"
                      value={interestRate}
                      onChange={(e) => setInterestRate(Number(e.target.value))}
                      className="w-full h-2 bg-sand-200 rounded-lg appearance-none cursor-pointer accent-forest border border-sand-300"
                    />
                    <div className="flex justify-between text-[10px] text-espresso-500 mt-1 font-sans">
                      <span>7.0% (Prime Bank)</span>
                      <span>14.0%</span>
                    </div>
                  </div>

                  <div>
                    <div className="flex justify-between text-xs font-sans font-bold text-espresso-800 mb-2">
                      <span>{t.calculators.tenureYears}</span>
                      <span className="text-forest font-bold text-sm">{tenureYears} Years</span>
                    </div>
                    <input
                      type="range"
                      min="5"
                      max="30"
                      step="1"
                      value={tenureYears}
                      onChange={(e) => setTenureYears(Number(e.target.value))}
                      className="w-full h-2 bg-sand-200 rounded-lg appearance-none cursor-pointer accent-forest border border-sand-300"
                    />
                    <div className="flex justify-between text-[10px] text-espresso-500 mt-1 font-sans">
                      <span>5 Years</span>
                      <span>30 Years</span>
                    </div>
                  </div>
                </div>

                <div className="md:col-span-5 p-6 rounded-xl bg-white border border-sand-300 shadow-warm-sm text-left">
                  <div className="text-[11px] font-sans font-bold text-espresso-500 uppercase mb-1">
                    {t.calculators.monthlyEmi}
                  </div>
                  <div className="text-3xl font-serif font-bold text-espresso-950 mb-4">
                    {formatCurrency(emi)}
                    <span className="text-xs text-espresso-500 font-sans font-normal"> / mo</span>
                  </div>

                  <div className="space-y-3 py-3 border-t border-sand-200 text-xs font-sans">
                    <div className="flex justify-between text-espresso-700">
                      <span>Principal Amount:</span>
                      <span className="font-bold text-espresso-900">{formatCurrency(loanAmount)}</span>
                    </div>
                    <div className="flex justify-between text-espresso-700">
                      <span>{t.calculators.totalInterest}:</span>
                      <span className="font-bold text-bronze-dark">{formatCurrency(totalInterest)}</span>
                    </div>
                    <div className="flex justify-between text-espresso-700">
                      <span>Total Payable:</span>
                      <span className="font-bold text-espresso-950">{formatCurrency(totalPayable)}</span>
                    </div>
                  </div>

                  <div className="mt-4 pt-3 border-t border-sand-200">
                    <div className="w-full h-2 rounded-full bg-bronze/30 overflow-hidden flex">
                      <div
                        className="h-full bg-forest"
                        style={{ width: `${(loanAmount / totalPayable) * 100}%` }}
                      />
                    </div>
                  </div>
                </div>

              </div>
            </GlassCard>
          )}

          {activeTab === 'cashflow' && (
            <GlassCard variant="default" className="p-6 sm:p-10 text-left bg-sand-100/90 border-sand-300 shadow-warm-md">
              <div className="grid grid-cols-1 md:grid-cols-12 gap-8 items-center">
                
                <div className="md:col-span-7 space-y-6">
                  <div>
                    <div className="flex justify-between text-xs font-sans font-bold text-espresso-800 mb-2">
                      <span>{t.calculators.projectCost}</span>
                      <span className="text-forest font-bold text-sm">₹{projectGrossCr} Crores</span>
                    </div>
                    <input
                      type="range"
                      min="20"
                      max="500"
                      step="5"
                      value={projectGrossCr}
                      onChange={(e) => setProjectGrossCr(Number(e.target.value))}
                      className="w-full h-2 bg-sand-200 rounded-lg appearance-none cursor-pointer accent-forest border border-sand-300"
                    />
                    <div className="flex justify-between text-[10px] text-espresso-500 mt-1 font-sans">
                      <span>₹20 Cr</span>
                      <span>₹500 Cr (Large Township)</span>
                    </div>
                  </div>

                  <div>
                    <div className="flex justify-between text-xs font-sans font-bold text-espresso-800 mb-2">
                      <span>{t.calculators.constructionStage} ({stagePercent}%)</span>
                      <span className="text-bronze-dark font-sans font-bold text-xs">
                        {stagePercent < 25 ? 'Foundation Plinth' : (stagePercent < 60 ? 'Superstructure Casting' : 'Finishing & Handover')}
                      </span>
                    </div>
                    <input
                      type="range"
                      min="10"
                      max="100"
                      step="5"
                      value={stagePercent}
                      onChange={(e) => setStagePercent(Number(e.target.value))}
                      className="w-full h-2 bg-sand-200 rounded-lg appearance-none cursor-pointer accent-forest border border-sand-300"
                    />
                  </div>

                  <div className="p-3 rounded-lg bg-sand-50 border border-sand-300 text-xs text-espresso-700 space-y-1 font-sans">
                    <div className="text-espresso-900 font-bold">RERA 70:30 Escrow Rule Applied:</div>
                    <p className="text-espresso-600 text-[11px]">
                      70% of realized cash (₹{(realizedCash * 0.7).toFixed(1)} Cr) is automatically ring-fenced for construction procurement.
                    </p>
                  </div>
                </div>

                <div className="md:col-span-5 p-6 rounded-xl bg-white border border-sand-300 shadow-warm-sm text-left">
                  <div className="text-[11px] font-sans font-bold text-espresso-500 uppercase mb-1">
                    Cash Realized to Date
                  </div>
                  <div className="text-3xl font-serif font-bold text-espresso-950 mb-4">
                    ₹{realizedCash.toFixed(1)} Cr
                  </div>

                  <div className="space-y-3 py-3 border-t border-sand-200 text-xs font-sans">
                    <div className="flex justify-between text-espresso-700">
                      <span>{t.calculators.projectedRealization}:</span>
                      <span className="font-bold text-forest">+₹{upcomingQuarterReceivable.toFixed(1)} Cr</span>
                    </div>
                    <div className="flex justify-between text-espresso-700">
                      <span>70% Escrow Pool:</span>
                      <span className="font-bold text-espresso-900">₹{(realizedCash * 0.7).toFixed(1)} Cr</span>
                    </div>
                    <div className="flex justify-between text-espresso-700">
                      <span>Developer Surplus:</span>
                      <span className="font-bold text-bronze-dark">₹{(realizedCash * 0.3).toFixed(1)} Cr</span>
                    </div>
                  </div>
                </div>

              </div>
            </GlassCard>
          )}

          {activeTab === 'stampduty' && (
            <GlassCard variant="default" className="p-6 sm:p-10 text-left bg-sand-100/90 border-sand-300 shadow-warm-md">
              <div className="grid grid-cols-1 md:grid-cols-12 gap-8 items-center">
                
                <div className="md:col-span-7 space-y-6">
                  <div>
                    <label className="block text-xs font-sans font-bold uppercase text-espresso-700 mb-2">
                      {t.calculators.stateSelect}
                    </label>
                    <select
                      value={selectedState.name}
                      onChange={(e) => {
                        const s = stateTariffs.find(item => item.name === e.target.value);
                        if (s) setSelectedState(s);
                      }}
                      className="w-full px-4 py-2.5 bg-white border border-sand-300 rounded-lg text-espresso-900 text-xs font-semibold focus:outline-none focus:border-forest shadow-warm-sm font-sans"
                    >
                      {stateTariffs.map((state) => (
                        <option key={state.name} value={state.name}>
                          {state.name} (Duty: {state.rate}%)
                        </option>
                      ))}
                    </select>
                  </div>

                  <div>
                    <div className="flex justify-between text-xs font-sans font-bold text-espresso-800 mb-2">
                      <span>Property Agreement Value</span>
                      <span className="text-forest font-bold text-sm">
                        {propertyValueLakh >= 100 ? `₹${(propertyValueLakh / 100).toFixed(2)} Cr` : `₹${propertyValueLakh} Lakh`}
                      </span>
                    </div>
                    <input
                      type="range"
                      min="25"
                      max="1000"
                      step="5"
                      value={propertyValueLakh}
                      onChange={(e) => setPropertyValueLakh(Number(e.target.value))}
                      className="w-full h-2 bg-sand-200 rounded-lg appearance-none cursor-pointer accent-forest border border-sand-300"
                    />
                    <div className="flex justify-between text-[10px] text-espresso-500 mt-1 font-sans">
                      <span>₹25 Lakh</span>
                      <span>₹10 Crore</span>
                    </div>
                  </div>
                </div>

                <div className="md:col-span-5 p-6 rounded-xl bg-white border border-sand-300 shadow-warm-sm text-left">
                  <div className="text-[11px] font-sans font-bold text-espresso-500 uppercase mb-1">
                    {t.calculators.calculatedDuty}
                  </div>
                  <div className="text-3xl font-serif font-bold text-espresso-950 mb-4">
                    {formatCurrency(totalGovtTaxes)}
                  </div>

                  <div className="space-y-3 py-3 border-t border-sand-200 text-xs font-sans">
                    <div className="flex justify-between text-espresso-700">
                      <span>State Stamp Duty ({selectedState.rate}%):</span>
                      <span className="font-bold text-espresso-900">{formatCurrency(stampDutyAmount)}</span>
                    </div>
                    <div className="flex justify-between text-espresso-700">
                      <span>Registration Authority ({selectedState.regPercent}%):</span>
                      <span className="font-bold text-espresso-900">{formatCurrency(registrationFee)}</span>
                    </div>
                    <div className="flex justify-between text-espresso-700 font-bold">
                      <span>Total Government Outflow:</span>
                      <span className="text-forest">{formatCurrency(totalGovtTaxes)}</span>
                    </div>
                  </div>
                </div>

              </div>
            </GlassCard>
          )}
        </div>

      </div>
    </section>
  );
};
