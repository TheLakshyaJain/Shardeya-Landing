import React, { useState, useRef, useCallback } from 'react';
import { 
  AlertTriangle, CheckCircle2, Sliders, MessageSquare, 
  FileSpreadsheet, ShieldCheck, ArrowLeftRight, Clock 
} from 'lucide-react';
import { audioHaptics } from '../../utils/audioHaptics';

export const BeforeAfterSlider: React.FC = () => {
  const [sliderPos, setSliderPos] = useState<number>(50); // percentage 0 - 100
  const [isDragging, setIsDragging] = useState<boolean>(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const updatePosition = useCallback((clientX: number) => {
    if (!containerRef.current) return;
    const rect = containerRef.current.getBoundingClientRect();
    const x = Math.max(0, Math.min(clientX - rect.left, rect.width));
    const percent = Math.round((x / rect.width) * 100);
    setSliderPos(percent);
  }, []);

  const handleMouseDown = (e: React.MouseEvent) => {
    setIsDragging(true);
    audioHaptics.playClick();
    updatePosition(e.clientX);
  };

  const handleMouseMove = (e: React.MouseEvent) => {
    if (!isDragging) return;
    updatePosition(e.clientX);
  };

  const handleMouseUp = () => {
    if (isDragging) {
      setIsDragging(false);
      audioHaptics.playSnap();
    }
  };

  const handleTouchStart = (e: React.TouchEvent) => {
    setIsDragging(true);
    audioHaptics.playClick();
    if (e.touches[0]) updatePosition(e.touches[0].clientX);
  };

  const handleTouchMove = (e: React.TouchEvent) => {
    if (!isDragging) return;
    if (e.touches[0]) updatePosition(e.touches[0].clientX);
  };

  const handleTouchEnd = () => {
    setIsDragging(false);
    audioHaptics.playSnap();
  };

  const handlePreset = (pct: number) => {
    setSliderPos(pct);
    audioHaptics.playClick();
  };

  return (
    <div className="w-full text-left space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-slate-100 border border-slate-200 text-slate-700 text-xs font-mono font-bold uppercase tracking-wider mb-2">
            <ArrowLeftRight className="w-3.5 h-3.5 text-emerald-600" />
            <span>Interactive Operational Contrast</span>
          </div>
          <h3 className="text-2xl sm:text-3xl font-serif font-bold text-slate-950">
            The Daily Real Estate Chaos vs Shardeya Clarity
          </h3>
          <p className="text-sm text-slate-600 font-sans mt-1">
            Drag the slider horizontally to compare typical builder operations against Shardeya's unified workflow.
          </p>
        </div>

        {/* Preset Buttons */}
        <div className="flex items-center gap-2 bg-slate-100 p-1 rounded-xl border border-slate-200 text-xs font-sans font-medium">
          <button
            onClick={() => handlePreset(20)}
            className={`px-3 py-1.5 rounded-lg transition-all ${
              sliderPos <= 30 ? 'bg-white shadow-warm-sm text-rose-700 font-bold' : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            The Chaos (20%)
          </button>
          <button
            onClick={() => handlePreset(50)}
            className={`px-3 py-1.5 rounded-lg transition-all ${
              sliderPos > 30 && sliderPos < 70 ? 'bg-white shadow-warm-sm text-slate-900 font-bold' : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            50 / 50 Split
          </button>
          <button
            onClick={() => handlePreset(80)}
            className={`px-3 py-1.5 rounded-lg transition-all ${
              sliderPos >= 70 ? 'bg-white shadow-warm-sm text-emerald-700 font-bold' : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            Shardeya (80%)
          </button>
        </div>
      </div>

      {/* Main Interactive Slider Box */}
      <div
        ref={containerRef}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
        onTouchStart={handleTouchStart}
        onTouchMove={handleTouchMove}
        onTouchEnd={handleTouchEnd}
        className="relative w-full h-[480px] sm:h-[440px] rounded-2xl overflow-hidden border-2 border-slate-300 select-none cursor-ew-resize shadow-warm-lg bg-slate-900"
      >
        {/* ========================================================================= */}
        {/* RIGHT LAYER: Shardeya Operational Clarity (Background Layer)               */}
        {/* ========================================================================= */}
        <div className="absolute inset-0 bg-gradient-to-br from-emerald-50 via-slate-50 to-amber-50 p-6 sm:p-8 flex flex-col justify-between overflow-hidden">
          <div className="flex items-center justify-between pb-4 border-b border-slate-200">
            <div className="flex items-center gap-2.5">
              <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 animate-pulse" />
              <span className="font-serif font-bold text-lg text-slate-950">SHARDEYA REAL ESTATE PLATFORM</span>
              <span className="px-2.5 py-0.5 rounded-full bg-emerald-100 text-emerald-800 font-mono text-[10px] font-bold border border-emerald-300">
                100% OPERATIONAL CLARITY
              </span>
            </div>
            <span className="text-xs font-mono font-bold text-emerald-700">Automated & Sync'd</span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 my-auto">
            {/* Clear Plot Card */}
            <div className="p-4 rounded-xl bg-white border border-emerald-300 shadow-warm-sm text-left">
              <div className="flex justify-between items-center mb-1.5">
                <span className="font-mono text-xs font-bold text-slate-800">Plot #104 (Corner Villa)</span>
                <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-100 text-emerald-800">
                  BOOKED
                </span>
              </div>
              <div className="text-xl font-serif font-bold text-emerald-800 mb-2">₹2.15 Cr</div>
              <div className="space-y-1 text-xs text-slate-600">
                <div className="flex items-center gap-1.5 text-slate-800 font-medium">
                  <ShieldCheck className="w-3.5 h-3.5 text-emerald-600" />
                  Buyer: Vikramaditya Singhania
                </div>
                <div className="text-[11px] text-slate-500">CP Tagged: Kapoor & Associates (Level 2)</div>
                <div className="text-[11px] font-bold text-emerald-700">Commission Locked: ₹3.22 Lakh</div>
              </div>
            </div>

            {/* Clear WhatsApp Update */}
            <div className="p-4 rounded-xl bg-white border border-emerald-300 shadow-warm-sm text-left">
              <div className="flex items-center gap-1.5 text-xs font-bold text-emerald-800 mb-2">
                <MessageSquare className="w-4 h-4 text-emerald-600" />
                <span>Official WhatsApp Cloud API</span>
              </div>
              <div className="p-2.5 rounded-lg bg-emerald-50 text-[11px] text-emerald-950 font-sans leading-relaxed border border-emerald-200">
                "Namaste Mr. Singhania! Payment receipt for ₹42,75,000 received. Unit #104 allotment letter issued."
              </div>
              <div className="text-[10px] text-slate-500 font-mono mt-2 flex items-center justify-between">
                <span>Dispatched: 0.4s</span>
                <span className="text-emerald-700 font-bold">✓✓ Read & Confirmed</span>
              </div>
            </div>

            {/* Clear Broker Ledger */}
            <div className="p-4 rounded-xl bg-white border border-emerald-300 shadow-warm-sm text-left">
              <div className="flex items-center justify-between text-xs font-bold text-slate-800 mb-2">
                <span>CP Commission Voucher</span>
                <span className="text-emerald-600 font-mono">No Disputes</span>
              </div>
              <div className="space-y-2 text-xs">
                <div className="flex justify-between text-slate-600">
                  <span>Sales Volume:</span>
                  <span className="font-bold text-slate-900">₹6.50 Cr</span>
                </div>
                <div className="flex justify-between text-slate-600">
                  <span>Slab Rate:</span>
                  <span className="font-bold text-emerald-700">1.5% Preferred CP</span>
                </div>
                <div className="pt-2 border-t border-slate-200 flex justify-between font-bold text-slate-950">
                  <span>Approved Payout:</span>
                  <span className="font-serif text-base text-emerald-800">₹9,75,000</span>
                </div>
              </div>
            </div>
          </div>

          <div className="p-3 rounded-xl bg-emerald-100/70 border border-emerald-300 text-xs text-emerald-900 font-sans flex items-center justify-between">
            <span className="flex items-center gap-2 font-medium">
              <CheckCircle2 className="w-4 h-4 text-emerald-700 shrink-0" />
              100% Audit-Proof • Automated Lead Locks • Zero Broker Double-Claim Disputes
            </span>
            <span className="font-mono text-[11px] font-bold text-emerald-800 uppercase hidden sm:inline">
              PROVEN ROI
            </span>
          </div>
        </div>

        {/* ========================================================================= */}
        {/* LEFT LAYER: The Daily Real Estate Chaos (Clipped on top)                  */}
        {/* ========================================================================= */}
        <div
          className="absolute inset-y-0 left-0 bg-gradient-to-br from-slate-950 via-slate-900 to-rose-950 p-6 sm:p-8 flex flex-col justify-between overflow-hidden border-r-2 border-white/80 shadow-2xl"
          style={{ width: `${sliderPos}%` }}
        >
          <div className="flex items-center justify-between pb-4 border-b border-rose-500/30">
            <div className="flex items-center gap-2.5">
              <AlertTriangle className="w-5 h-5 text-rose-400" />
              <span className="font-serif font-bold text-lg text-rose-200">THE DAILY SPREADSHEET & WHATSAPP CHAOS</span>
              <span className="px-2.5 py-0.5 rounded-full bg-rose-950/80 text-rose-300 font-mono text-[10px] font-bold border border-rose-500/40">
                BEFORE SHARDEYA
              </span>
            </div>
            <span className="text-xs font-mono font-bold text-rose-400">High Risk & Lost Hours</span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 my-auto">
            {/* Chaotic Excel */}
            <div className="p-4 rounded-xl bg-rose-950/40 border border-rose-500/40 text-left">
              <div className="flex items-center gap-1.5 text-xs font-bold text-rose-300 mb-2">
                <FileSpreadsheet className="w-4 h-4 text-rose-400" />
                <span>Master_Inventory_Final_v4_UPDATED.xlsx</span>
              </div>
              <div className="font-mono text-[11px] space-y-1 text-rose-200/90">
                <div className="p-1 rounded bg-rose-900/40 border border-rose-700/40 text-rose-300">
                  Row 42: Plot #104 — <strong className="text-amber-300">DOUBLE BOOKED?</strong>
                </div>
                <div>Broker A: Token ₹1L (Cash)</div>
                <div>Broker B: "Sir I locked this on WhatsApp!"</div>
                <div className="text-rose-400 font-bold">ERROR: #REF! in Cell F18</div>
              </div>
            </div>

            {/* Chaotic WhatsApp */}
            <div className="p-4 rounded-xl bg-slate-900/80 border border-slate-700 text-left">
              <div className="flex items-center justify-between text-xs text-slate-400 mb-2 font-mono">
                <span>Broker WhatsApp Group (48 unread)</span>
                <Clock className="w-3 h-3 text-rose-400" />
              </div>
              <div className="space-y-1.5 text-[11px] text-slate-300 font-sans">
                <div className="p-2 rounded bg-slate-800 text-rose-300">
                  "Bhaiya Plot 104 kisne becha? Mera client 3 din se rate confirm kar raha tha!"
                </div>
                <div className="p-2 rounded bg-slate-800 text-amber-200">
                  "Commission 2% bola tha GM sir ne, accounts team 1.5% kyu de rahi hai?!"
                </div>
              </div>
            </div>

            {/* Lost Paper Receipts */}
            <div className="p-4 rounded-xl bg-rose-950/40 border border-rose-500/40 text-left">
              <div className="text-xs font-bold text-rose-300 mb-2">Paper Ledger & RTGS Slip Misplaced</div>
              <div className="text-[11px] text-rose-200/80 space-y-2">
                <div>• Buyer claims ₹50L RTGS done 4 days ago. Bank UTR not traced.</div>
                <div>• Builder demands penalty interest; buyer threatens RERA legal notice.</div>
                <div className="font-bold text-rose-300">Result: 3 weeks lost in reconciliation.</div>
              </div>
            </div>
          </div>

          <div className="p-3 rounded-xl bg-rose-900/30 border border-rose-500/30 text-xs text-rose-300 font-sans flex items-center justify-between">
            <span className="flex items-center gap-2">
              <AlertTriangle className="w-4 h-4 text-rose-400 shrink-0" />
              Spreadsheet Overwrites • Commission Disputes • Lost Buyer Documentation
            </span>
            <span className="font-mono text-[11px] font-bold text-rose-400 uppercase hidden sm:inline">
              AVERAGE LOSS: ₹18L/YR
            </span>
          </div>
        </div>

        {/* ========================================================================= */}
        {/* DRAGGABLE DIVIDER HANDLE                                                  */}
        {/* ========================================================================= */}
        <div
          className="absolute inset-y-0 w-1 bg-white cursor-ew-resize flex items-center justify-center z-30 shadow-2xl"
          style={{ left: `${sliderPos}%` }}
        >
          <div className="w-9 h-9 rounded-full bg-slate-950 border-2 border-white shadow-xl flex items-center justify-center text-white transform -translate-x-1/2 hover:scale-110 active:scale-95 transition-transform">
            <ArrowLeftRight className="w-4 h-4 text-emerald-400" />
          </div>
        </div>

        {/* Floating Tooltip Hint */}
        <div className="absolute bottom-4 left-1/2 -translate-x-1/2 z-20 pointer-events-none">
          <span className="px-3 py-1 rounded-full bg-black/75 backdrop-blur-md text-white text-[11px] font-sans font-medium border border-white/20 shadow-md">
            Drag divider left or right to wipe comparison
          </span>
        </div>
      </div>
    </div>
  );
};
