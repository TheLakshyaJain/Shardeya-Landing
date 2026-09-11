import React, { useState } from 'react';
import { 
  Layers, Search, Filter, Plus, Flame, 
  Trees, CornerUpRight, Compass, Eye, CheckCircle2,
  Clock, IndianRupee, ShieldCheck
} from 'lucide-react';
import { useCrm } from '../../../../context/CrmContext';
import { Plot, PlotStatus, PlotFacing } from '../../../../types/crm';
import { PlotDetailDrawer } from './PlotDetailDrawer';

interface InteractivePlotGridProps {
  onOpenBookingModal: (plot: Plot) => void;
  onOpenBulkUpload: () => void;
}

export const InteractivePlotGrid: React.FC<InteractivePlotGridProps> = ({
  onOpenBookingModal,
  onOpenBulkUpload,
}) => {
  const { plots, activeProject } = useCrm();

  // Filters
  const [statusFilter, setStatusFilter] = useState<'ALL' | PlotStatus>('ALL');
  const [facingFilter, setFacingFilter] = useState<'ALL' | PlotFacing>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedPlot, setSelectedPlot] = useState<Plot | null>(null);

  // Filtered plots for active project
  const projectPlots = plots.filter((p) => p.projectId === activeProject?.id);

  const filteredPlots = projectPlots.filter((p) => {
    if (statusFilter !== 'ALL' && p.status !== statusFilter) return false;
    if (facingFilter !== 'ALL' && p.facing !== facingFilter) return false;
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      return p.plotNumber.toLowerCase().includes(q) || (p.reservedFor?.toLowerCase().includes(q) ?? false);
    }
    return true;
  });

  const getStatusClasses = (status: PlotStatus) => {
    switch (status) {
      case 'AVAILABLE':
        return 'bg-emerald-50/80 border-emerald-300 text-emerald-900 hover:border-emerald-500 hover:bg-emerald-100/70';
      case 'RESERVED':
        return 'bg-amber-50/90 border-amber-300 text-amber-900 hover:border-amber-500 hover:bg-amber-100/80';
      case 'SOLD':
        return 'bg-slate-100/90 border-slate-300 text-slate-700 hover:border-slate-400';
      default:
        return 'bg-white border-sand-300 text-espresso-800';
    }
  };

  return (
    <div className="space-y-6 text-left animate-fadeIn">
      
      {/* Top Header & Project Context */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-sand-300">
        <div>
          <div className="flex items-center gap-2">
            <span className="text-xs font-mono font-bold text-forest uppercase">
              {activeProject?.name}
            </span>
            <span className="text-xs text-espresso-500 font-sans">• Cadastral Masterplan Matrix</span>
          </div>
          <h1 className="font-serif font-bold text-2xl sm:text-3xl text-espresso-950 mt-0.5">
            Interactive Plot Layout Grid
          </h1>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={onOpenBulkUpload}
            className="px-3.5 py-2 rounded-xl bg-forest hover:bg-forest-light text-white text-xs font-sans font-bold shadow-warm-sm transition-all flex items-center gap-1.5"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Add / Bulk Create Plots</span>
          </button>
        </div>
      </div>

      {/* Filter Bar & Legend */}
      <div className="bg-white rounded-2xl border border-sand-300 p-4 shadow-warm-sm space-y-3">
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3">
          
          {/* Quick Search */}
          <div className="relative flex-1 max-w-sm">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-espresso-400" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search by Plot Number (e.g. Plot #104)..."
              className="w-full pl-9 pr-3 py-2 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white focus:border-forest text-xs font-sans text-espresso-950 outline-none"
            />
          </div>

          {/* Status Tabs */}
          <div className="flex flex-wrap items-center gap-1.5 p-1 bg-sand-100 rounded-xl border border-sand-200 text-xs">
            <button
              onClick={() => setStatusFilter('ALL')}
              className={`px-3 py-1.5 rounded-lg font-semibold transition-all ${
                statusFilter === 'ALL' ? 'bg-white shadow-sm font-bold text-espresso-950' : 'text-espresso-600 hover:text-espresso-950'
              }`}
            >
              All ({projectPlots.length})
            </button>
            <button
              onClick={() => setStatusFilter('AVAILABLE')}
              className={`px-3 py-1.5 rounded-lg font-semibold transition-all ${
                statusFilter === 'AVAILABLE' ? 'bg-emerald-100 text-emerald-800 font-bold' : 'text-espresso-600 hover:text-espresso-950'
              }`}
            >
              Available ({projectPlots.filter((p) => p.status === 'AVAILABLE').length})
            </button>
            <button
              onClick={() => setStatusFilter('RESERVED')}
              className={`px-3 py-1.5 rounded-lg font-semibold transition-all ${
                statusFilter === 'RESERVED' ? 'bg-amber-100 text-amber-800 font-bold' : 'text-espresso-600 hover:text-espresso-950'
              }`}
            >
              48h Hold ({projectPlots.filter((p) => p.status === 'RESERVED').length})
            </button>
            <button
              onClick={() => setStatusFilter('SOLD')}
              className={`px-3 py-1.5 rounded-lg font-semibold transition-all ${
                statusFilter === 'SOLD' ? 'bg-slate-200 text-slate-800 font-bold' : 'text-espresso-600 hover:text-espresso-950'
              }`}
            >
              Allotted ({projectPlots.filter((p) => p.status === 'SOLD').length})
            </button>
          </div>

          {/* Facing Filter */}
          <div className="flex items-center gap-2 text-xs">
            <span className="text-espresso-500 font-semibold flex items-center gap-1">
              <Compass className="w-3.5 h-3.5 text-forest" />
              Facing:
            </span>
            <select
              value={facingFilter}
              onChange={(e) => setFacingFilter(e.target.value as any)}
              className="px-2.5 py-1.5 rounded-lg border border-sand-300 bg-sand-50 text-xs font-semibold text-espresso-800 focus:outline-none"
            >
              <option value="ALL">All Directions</option>
              <option value="E">East (E)</option>
              <option value="NE">North-East (NE)</option>
              <option value="N">North (N)</option>
              <option value="W">West (W)</option>
              <option value="S">South (S)</option>
              <option value="NW">North-West (NW)</option>
            </select>
          </div>

        </div>

        {/* Legend */}
        <div className="pt-2 border-t border-sand-200 flex flex-wrap items-center gap-4 text-[11px] text-espresso-600 font-sans">
          <span className="flex items-center gap-1.5 font-semibold">
            <span className="w-3 h-3 rounded bg-emerald-400 border border-emerald-600 inline-block" />
            <span>Available Plot</span>
          </span>
          <span className="flex items-center gap-1.5 font-semibold">
            <span className="w-3 h-3 rounded bg-amber-400 border border-amber-600 inline-block" />
            <span>48h Token Hold (Reserved)</span>
          </span>
          <span className="flex items-center gap-1.5 font-semibold">
            <span className="w-3 h-3 rounded bg-slate-300 border border-slate-500 inline-block" />
            <span>Allotted / Sold</span>
          </span>
          <span className="flex items-center gap-1 text-espresso-500 ml-auto">
            <span>Click any plot to inspect or allot</span>
          </span>
        </div>
      </div>

      {/* Cadastral Interactive Grid Matrix */}
      <div className="bg-white rounded-2xl border border-sand-300 p-6 shadow-warm-md overflow-x-auto">
        <div className="min-w-[760px]">
          
          {/* Arterial Road Marker */}
          <div className="mb-4 py-2 px-4 rounded-xl bg-sand-100 border border-sand-300 text-center font-mono text-[11px] uppercase tracking-widest text-espresso-600 font-bold flex items-center justify-center gap-3">
            <span>═ 60-Foot Main Arterial Boulevard ═</span>
          </div>

          <div className="grid grid-cols-6 gap-3.5">
            {filteredPlots.map((plot) => (
              <div
                key={plot.id}
                onClick={() => setSelectedPlot(plot)}
                className={`
                  p-3 rounded-xl border-2 transition-all cursor-pointer flex flex-col justify-between h-28 relative group
                  ${getStatusClasses(plot.status)}
                `}
              >
                {/* Top: Plot Number & Attributes Badges */}
                <div className="flex items-center justify-between">
                  <span className="font-mono font-bold text-xs">
                    {plot.plotNumber}
                  </span>

                  <div className="flex items-center gap-1">
                    {plot.isHot && (
                      <span title="Hot Plot" className="text-amber-600">
                        <Flame className="w-3.5 h-3.5" />
                      </span>
                    )}
                    {plot.isGarden && (
                      <span title="Park Facing" className="text-emerald-700">
                        <Trees className="w-3.5 h-3.5" />
                      </span>
                    )}
                    {plot.isCorner && (
                      <span title="Corner Plot" className="text-blue-700">
                        <CornerUpRight className="w-3.5 h-3.5" />
                      </span>
                    )}
                  </div>
                </div>

                {/* Center: Dimensions & Facing */}
                <div className="my-1">
                  <div className="text-[11px] font-sans font-bold">
                    {plot.sizeValue.toLocaleString()} {plot.sizeUnit === 'SQ_FT' ? 'sq.ft' : 'Gaj'}
                  </div>
                  <div className="text-[10px] opacity-75 font-mono">
                    Facing: {plot.facing} • ₹{plot.pricePerSqft}/sqft
                  </div>
                </div>

                {/* Bottom: Price or Buyer */}
                <div className="pt-1 border-t border-black/10 flex items-center justify-between text-[11px] font-mono font-bold">
                  <span>₹{(plot.price / 100000).toFixed(1)}L</span>
                  <span className="text-[9px] uppercase tracking-wider opacity-80">
                    {plot.status}
                  </span>
                </div>
              </div>
            ))}
          </div>

          {/* Rear Boundary Marker */}
          <div className="mt-4 py-2 px-4 rounded-xl bg-sand-100 border border-sand-300 text-center font-mono text-[11px] uppercase tracking-widest text-espresso-600 font-bold">
            <span>═ 30-Foot Internal Sector Road ═</span>
          </div>

        </div>
      </div>

      {/* Selected Plot Detail Drawer */}
      {selectedPlot && (
        <PlotDetailDrawer
          plot={selectedPlot}
          onClose={() => setSelectedPlot(null)}
          onOpenBooking={() => {
            const p = selectedPlot;
            setSelectedPlot(null);
            onOpenBookingModal(p);
          }}
        />
      )}

    </div>
  );
};
