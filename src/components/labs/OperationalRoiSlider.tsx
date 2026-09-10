import React, { useState } from 'react';
import { 
  TrendingUp, Clock, ShieldCheck, IndianRupee, 
  Sparkles, CheckCircle2, ArrowRight 
} from 'lucide-react';
import { audioHaptics } from '../../utils/audioHaptics';

export const OperationalRoiSlider: React.FC = () => {
  const [annualUnits, setAnnualUnits] = useState<number>(75);
  const [avgTicketCr, setAvgTicketCr] = useState<number>(1.85);

  const hoursSavedPerUnit = 7.5;
  const totalHoursSaved = Math.round(annualUnits * hoursSavedPerUnit);
  const disputesPrevented = Math.max(1, Math.round(annualUnits * 0.09));
  const totalSalesVolumeCr = (annualUnits * avgTicketCr).toFixed(1);
  
  // Working capital interest saved due to 14-day faster collection realization @ 10.5% p.a.
  const capitalInterestSavedLakhs = (
    (annualUnits * avgTicketCr * 100 * 0.105 * (14 / 365))
  ).toFixed(1);

  const handleSliderChange = (val: number) => {
    setAnnualUnits(val);
    audioHaptics.playSnap();
  };

  return (
    <div className="w-full text-left space-y-6">
      <div>
        <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-slate-100 border border-slate-200 text-slate-700 text-xs font-mono font-bold uppercase tracking-wider mb-2">
          <TrendingUp className="w-3.5 h-3.5 text-emerald-600" />
          <span>Practical Operational Economics</span>
        </div>
        <h3 className="text-2xl sm:text-3xl font-serif font-bold text-slate-950">
          Operational Hours & Dispute Savings Calculator
        </h3>
        <p className="text-sm text-slate-600 font-sans mt-1">
          Calculate the exact operational time and working capital your development team reclaims by replacing spreadsheets with Shardeya.
        </p>
      </div>

      <div className="p-6 sm:p-10 rounded-2xl bg-white border border-slate-200 shadow-warm-md">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-center">
          
          {/* Controls Column */}
          <div className="lg:col-span-7 space-y-6">
            <div>
              <div className="flex items-center justify-between mb-2">
                <label className="text-xs font-bold uppercase tracking-wider text-slate-700 font-sans">
                  Annual Units / Plots Sold by Your Firm:
                </label>
                <span className="font-serif font-bold text-2xl text-slate-950">
                  {annualUnits} Units / Year
                </span>
              </div>

              <input
                type="range"
                min="20"
                max="500"
                step="5"
                value={annualUnits}
                onChange={(e) => handleSliderChange(Number(e.target.value))}
                className="w-full h-2.5 bg-slate-200 rounded-lg appearance-none cursor-pointer accent-emerald-600"
              />
              <div className="flex justify-between text-[11px] font-mono text-slate-500 mt-1.5">
                <span>20 Units (Boutique)</span>
                <span>150 Units (Midsize)</span>
                <span>500 Units (Large Township)</span>
              </div>
            </div>

            {/* Average Ticket Size Quick Select */}
            <div>
              <label className="text-xs font-bold uppercase tracking-wider text-slate-700 font-sans block mb-2">
                Average Unit Consideration Ticket:
              </label>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                {[
                  { label: '₹85 Lakh', val: 0.85 },
                  { label: '₹1.85 Cr', val: 1.85 },
                  { label: '₹2.75 Cr', val: 2.75 },
                  { label: '₹4.50 Cr', val: 4.50 },
                ].map((ticket) => (
                  <button
                    key={ticket.label}
                    onClick={() => {
                      setAvgTicketCr(ticket.val);
                      audioHaptics.playClick();
                    }}
                    className={`py-2 px-3 rounded-xl text-xs font-mono font-bold border transition-all ${
                      avgTicketCr === ticket.val
                        ? 'bg-slate-900 text-white border-slate-900 shadow-sm'
                        : 'bg-slate-50 hover:bg-slate-100 border-slate-200 text-slate-700'
                    }`}
                  >
                    {ticket.label}
                  </button>
                ))}
              </div>
            </div>

            <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 text-xs text-slate-600 font-sans flex items-start gap-2.5">
              <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0 mt-0.5" />
              <span>
                Based on annual gross volume of <strong>₹{totalSalesVolumeCr} Crores</strong> across <strong>{annualUnits} units</strong>.
              </span>
            </div>
          </div>

          {/* Reactive Metrics Column */}
          <div className="lg:col-span-5 grid grid-cols-1 gap-3.5">
            
            {/* Metric 1: Hours Saved */}
            <div className="p-5 rounded-xl bg-emerald-50/70 border border-emerald-200 text-left">
              <div className="flex items-center justify-between text-emerald-800 text-xs font-bold font-mono uppercase mb-1">
                <span className="flex items-center gap-1.5">
                  <Clock className="w-4 h-4 text-emerald-600" />
                  Manual Reconciliations Saved
                </span>
                <span className="text-[10px] px-1.5 py-0.5 rounded bg-emerald-200 text-emerald-900">
                  ANNUAL
                </span>
              </div>
              <div className="font-serif font-bold text-3xl text-emerald-950">
                {totalHoursSaved} Hours / Year
              </div>
              <div className="text-[11px] text-emerald-800/80 font-sans mt-1">
                Eliminates repetitive ledger entries, manual WhatsApp follow-ups, and bank statement matching.
              </div>
            </div>

            {/* Metric 2: Disputes Prevented */}
            <div className="p-5 rounded-xl bg-amber-50/70 border border-amber-200 text-left">
              <div className="flex items-center justify-between text-amber-900 text-xs font-bold font-mono uppercase mb-1">
                <span className="flex items-center gap-1.5">
                  <ShieldCheck className="w-4 h-4 text-amber-600" />
                  Broker Disputes Prevented
                </span>
                <span className="text-[10px] px-1.5 py-0.5 rounded bg-amber-200 text-amber-950">
                  ZERO CLASHES
                </span>
              </div>
              <div className="font-serif font-bold text-3xl text-amber-950">
                {disputesPrevented} Deals Protected
              </div>
              <div className="text-[11px] text-amber-800 font-sans mt-1">
                Timestamped lead protection locks ensure channel partners never fight over double attribution.
              </div>
            </div>

            {/* Metric 3: Capital Interest Saved */}
            <div className="p-5 rounded-xl bg-slate-900 text-white text-left">
              <div className="flex items-center justify-between text-slate-400 text-xs font-mono uppercase mb-1">
                <span className="flex items-center gap-1.5 text-emerald-400">
                  <IndianRupee className="w-4 h-4 text-emerald-400" />
                  Working Capital Savings
                </span>
                <span className="text-[10px] px-1.5 py-0.5 rounded bg-emerald-950 text-emerald-300 font-bold border border-emerald-500/40">
                  14-DAY SPEEDUP
                </span>
              </div>
              <div className="font-serif font-bold text-3xl text-white">
                ₹{capitalInterestSavedLakhs} Lakhs
              </div>
              <div className="text-[11px] text-slate-400 font-sans mt-1">
                Accelerates milestone payment inflows from 21 days down to 4 days via WhatsApp demand links.
              </div>
            </div>

          </div>

        </div>
      </div>
    </div>
  );
};
