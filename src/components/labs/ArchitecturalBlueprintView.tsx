import React, { useState } from 'react';
import { 
  Compass, Eye, Layers, Ruler, Maximize2, ShieldCheck, 
  MapPin, Sliders, CheckCircle2, FileCode2 
} from 'lucide-react';
import { audioHaptics } from '../../utils/audioHaptics';

export const ArchitecturalBlueprintView: React.FC = () => {
  const [blueprintMode, setBlueprintMode] = useState<boolean>(true);
  const [selectedPlotId, setSelectedPlotId] = useState<string>('bp-104');
  const [showDimensions, setShowDimensions] = useState<boolean>(true);
  const [showVastu, setShowVastu] = useState<boolean>(true);

  const plots = [
    {
      id: 'bp-101',
      num: 'Plot #101',
      width: '30\'0"',
      depth: '71\'8"',
      area: '2,150 sq.ft',
      sqYds: '238.8 sq.yd',
      facing: 'North-East',
      roadFrontage: '40ft Internal Road',
      frontSetback: '15\'0"',
      rearSetback: '10\'0"',
      sideSetback: '6\'0"',
      farAllowed: '2.64 (5,676 sq.ft buildable)',
      sbcSoil: '185 kN/m² (Lab Certified)',
      coordinates: '28°24\'32.1"N 76°58\'56.4"E',
      status: 'available',
      price: '₹1.85 Cr',
    },
    {
      id: 'bp-102',
      num: 'Plot #102',
      width: '32\'0"',
      depth: '75\'0"',
      area: '2,400 sq.ft',
      sqYds: '266.6 sq.yd',
      facing: 'East Facing',
      roadFrontage: '40ft Internal Road',
      frontSetback: '15\'0"',
      rearSetback: '10\'0"',
      sideSetback: '6\'0"',
      farAllowed: '2.64 (6,336 sq.ft buildable)',
      sbcSoil: '182 kN/m² (Lab Certified)',
      coordinates: '28°24\'33.5"N 76°58\'58.1"E',
      status: 'booked',
      price: '₹2.05 Cr',
    },
    {
      id: 'bp-103',
      num: 'Plot #103',
      width: '35\'0"',
      depth: '80\'0"',
      area: '2,800 sq.ft',
      sqYds: '311.1 sq.yd',
      facing: 'North Facing',
      roadFrontage: '60ft Sector Arterial',
      frontSetback: '18\'0"',
      rearSetback: '12\'0"',
      sideSetback: '7\'0"',
      farAllowed: '2.64 (7,392 sq.ft buildable)',
      sbcSoil: '190 kN/m² (Lab Certified)',
      coordinates: '28°24\'35.0"N 76°58\'59.8"E',
      status: 'token_hold',
      price: '₹2.45 Cr',
    },
    {
      id: 'bp-104',
      num: 'Villa Plot #104',
      width: '32\'0"',
      depth: '75\'0"',
      area: '2,400 sq.ft',
      sqYds: '266.6 sq.yd',
      facing: 'North-East Corner Dual Road',
      roadFrontage: '60ft Arterial & 40ft Sector Road',
      frontSetback: '18\'0"',
      rearSetback: '12\'0"',
      sideSetback: '8\'0" (Corner Exemption)',
      farAllowed: '2.64 (6,336 sq.ft buildable)',
      sbcSoil: '188 kN/m² (Lab Certified)',
      coordinates: '28°24\'36.2"N 76°59\'01.4"E',
      status: 'available',
      price: '₹2.15 Cr',
    },
  ];

  const activePlot = plots.find((p) => p.id === selectedPlotId) || plots[3];

  const handleToggleMode = (mode: boolean) => {
    setBlueprintMode(mode);
    audioHaptics.playSwitch();
  };

  const handleSelectPlot = (id: string) => {
    setSelectedPlotId(id);
    audioHaptics.playClick();
  };

  return (
    <div className="w-full text-left space-y-6">
      {/* Header & Blueprint Switcher */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-slate-100 border border-slate-200 text-slate-700 text-xs font-mono font-bold uppercase tracking-wider mb-2">
            <Ruler className="w-3.5 h-3.5 text-cyan-600" />
            <span>Architectural CAD & Surveying Mode</span>
          </div>
          <h3 className="text-2xl sm:text-3xl font-serif font-bold text-slate-950">
            Precision Surveyor Blueprint Engine
          </h3>
          <p className="text-sm text-slate-600 font-sans mt-1">
            Toggle between standard customer presentation and high-precision CAD blueprint mode with exact setback and FAR calculations.
          </p>
        </div>

        {/* View Switcher Toggle */}
        <div className="flex items-center gap-3">
          <div className="flex items-center p-1 rounded-xl bg-slate-200/80 border border-slate-300 gap-1 text-xs font-sans font-bold">
            <button
              onClick={() => handleToggleMode(false)}
              className={`px-3.5 py-2 rounded-lg transition-all ${
                !blueprintMode
                  ? 'bg-white text-slate-900 shadow-warm-sm'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              Standard Presentation
            </button>
            <button
              onClick={() => handleToggleMode(true)}
              className={`flex items-center gap-1.5 px-3.5 py-2 rounded-lg transition-all ${
                blueprintMode
                  ? 'bg-slate-950 text-cyan-400 shadow-warm-sm font-mono'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <FileCode2 className="w-3.5 h-3.5" />
              <span>Blueprint CAD Mode</span>
            </button>
          </div>
        </div>
      </div>

      {/* Main Canvas Area */}
      <div
        className={`relative rounded-2xl overflow-hidden border-2 transition-all duration-500 p-6 sm:p-8 ${
          blueprintMode
            ? 'bg-[#091428] border-cyan-500/40 text-cyan-100 shadow-2xl'
            : 'bg-white border-slate-200 text-slate-900 shadow-warm-md'
        }`}
      >
        {/* Background Grid Pattern */}
        {blueprintMode && (
          <div 
            className="absolute inset-0 pointer-events-none opacity-25"
            style={{
              backgroundImage: `linear-gradient(#00f2fe 1px, transparent 1px), linear-gradient(90deg, #00f2fe 1px, transparent 1px)`,
              backgroundSize: '32px 32px'
            }}
          />
        )}

        {/* Top Blueprint Status Bar */}
        <div className="relative z-10 flex flex-wrap items-center justify-between gap-4 pb-6 border-b border-cyan-500/20">
          <div className="flex items-center gap-3">
            <div className={`p-2 rounded-lg ${blueprintMode ? 'bg-cyan-950/80 text-cyan-400 border border-cyan-500/30' : 'bg-slate-100 text-slate-700'}`}>
              <Compass className="w-5 h-5 animate-spin-slow" />
            </div>
            <div>
              <div className="font-mono text-xs font-bold tracking-wider uppercase">
                {blueprintMode ? 'DWG-REF: SEC84-APEX-PHASE1.DWG • SCALE 1:200' : 'Project Layout: Apex Greens'}
              </div>
              <div className="text-[11px] opacity-75 font-sans">
                DTCP Approved Layout Plan • RERA Registration: HRERA-PKL-GGM-1294-2024
              </div>
            </div>
          </div>

          {/* Blueprint Layer Controls */}
          <div className="flex items-center gap-3 font-mono text-xs">
            <label className="flex items-center gap-1.5 cursor-pointer">
              <input
                type="checkbox"
                checked={showDimensions}
                onChange={() => {
                  setShowDimensions(!showDimensions);
                  audioHaptics.playClick();
                }}
                className="accent-cyan-500 rounded"
              />
              <span>Dimensions</span>
            </label>
            <label className="flex items-center gap-1.5 cursor-pointer">
              <input
                type="checkbox"
                checked={showVastu}
                onChange={() => {
                  setShowVastu(!showVastu);
                  audioHaptics.playClick();
                }}
                className="accent-cyan-500 rounded"
              />
              <span>Vastu Axis</span>
            </label>
          </div>
        </div>

        {/* Interactive Masterplan Layout Vector Box */}
        <div className="relative z-10 my-8">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {plots.map((plot) => {
              const isSelected = selectedPlotId === plot.id;

              return (
                <div
                  key={plot.id}
                  onClick={() => handleSelectPlot(plot.id)}
                  className={`p-5 rounded-xl border transition-all cursor-pointer text-left relative overflow-hidden ${
                    blueprintMode
                      ? isSelected
                        ? 'bg-cyan-950/60 border-cyan-400 ring-2 ring-cyan-400/40 shadow-glow-cyan'
                        : 'bg-[#0f213e]/80 border-cyan-500/30 hover:border-cyan-400/60 hover:bg-[#12284b]'
                      : isSelected
                      ? 'bg-white border-emerald-500 ring-2 ring-emerald-500/20 shadow-warm-md'
                      : 'bg-slate-50 border-slate-200 hover:bg-white'
                  }`}
                >
                  {/* Watermark Blueprint corner */}
                  {blueprintMode && (
                    <div className="absolute top-0 right-0 w-8 h-8 pointer-events-none border-t-2 border-r-2 border-cyan-400/40" />
                  )}

                  <div className="flex items-center justify-between mb-2">
                    <span className="font-mono text-xs font-bold tracking-wider">
                      {plot.num}
                    </span>
                    <span
                      className={`font-mono text-[9px] px-2 py-0.5 rounded font-bold uppercase ${
                        plot.status === 'available'
                          ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/40'
                          : plot.status === 'booked'
                          ? 'bg-slate-500/20 text-slate-300 border border-slate-500/40'
                          : 'bg-amber-500/20 text-amber-400 border border-amber-500/40'
                      }`}
                    >
                      {plot.status.replace('_', ' ')}
                    </span>
                  </div>

                  <div className="font-serif font-bold text-lg mb-1">{plot.price}</div>
                  <div className="text-xs opacity-75 font-sans mb-3">{plot.area} ({plot.sqYds})</div>

                  {/* Dimension Callouts (Blueprint Mode) */}
                  {showDimensions && (
                    <div className="p-2.5 rounded-lg bg-black/30 border border-cyan-500/20 font-mono text-[10px] space-y-1">
                      <div className="flex justify-between text-cyan-300">
                        <span>Front × Depth:</span>
                        <strong>{plot.width} × {plot.depth}</strong>
                      </div>
                      <div className="flex justify-between opacity-80">
                        <span>Frontage:</span>
                        <span>{plot.roadFrontage}</span>
                      </div>
                    </div>
                  )}

                  {/* Vastu Callout */}
                  {showVastu && (
                    <div className="mt-2 text-[10px] font-mono text-cyan-200/90 truncate">
                      🧭 {plot.facing}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>

        {/* Selected Plot Detailed Surveyor Specifications */}
        <div className={`relative z-10 p-6 rounded-xl border font-mono text-xs ${
          blueprintMode
            ? 'bg-black/40 border-cyan-500/30'
            : 'bg-slate-50 border-slate-200'
        }`}>
          <div className="flex flex-wrap items-center justify-between gap-4 pb-4 border-b border-cyan-500/20 mb-4">
            <div className="flex items-center gap-2">
              <span className="w-2.5 h-2.5 rounded-full bg-cyan-400 animate-pulse" />
              <strong className="text-sm">{activePlot.num} Architectural Schedule</strong>
              <span className="opacity-60">({activePlot.coordinates})</span>
            </div>
            <div className="font-bold text-emerald-400 text-sm">
              List Value: {activePlot.price}
            </div>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 text-left">
            <div>
              <span className="opacity-60 block text-[10px] uppercase">Setbacks (Front/Rear/Side)</span>
              <strong className="text-cyan-300">{activePlot.frontSetback} / {activePlot.rearSetback} / {activePlot.sideSetback}</strong>
            </div>
            <div>
              <span className="opacity-60 block text-[10px] uppercase">Permissible FAR</span>
              <strong className="text-cyan-300">{activePlot.farAllowed}</strong>
            </div>
            <div>
              <span className="opacity-60 block text-[10px] uppercase">Soil Bearing Capacity</span>
              <strong className="text-cyan-300">{activePlot.sbcSoil}</strong>
            </div>
            <div>
              <span className="opacity-60 block text-[10px] uppercase">Road Right of Way</span>
              <strong className="text-cyan-300">{activePlot.roadFrontage}</strong>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
