import React, { useState } from 'react';
import { 
  Building2, Plus, MapPin, Layers, CheckCircle2, 
  Calendar, FileText, ArrowRight, Compass 
} from 'lucide-react';
import { useCrm } from '../../../../context/CrmContext';
import { Project } from '../../../../types/crm';
import { CrmTab } from '../../shell/Sidebar';

interface ProjectListPageProps {
  onNavigate: (tab: CrmTab) => void;
  onOpenNewProject: () => void;
  onSelectProjectDetail: (projectId: string) => void;
}

export const ProjectListPage: React.FC<ProjectListPageProps> = ({
  onNavigate,
  onOpenNewProject,
  onSelectProjectDetail,
}) => {
  const { projects, plots, setActiveProjectId } = useCrm();

  return (
    <div className="space-y-6 text-left animate-fadeIn">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-sand-300">
        <div>
          <h1 className="font-serif font-bold text-2xl sm:text-3xl text-espresso-950">
            Real Estate Projects & Masterplans
          </h1>
          <p className="text-xs text-espresso-600 mt-1">
            Manage your land parcels, multi-phase plotted colonies, and RERA compliance documentation.
          </p>
        </div>

        <button
          onClick={onOpenNewProject}
          className="px-4 py-2.5 rounded-xl bg-forest hover:bg-forest-light text-white text-xs font-sans font-bold shadow-warm-sm transition-all flex items-center justify-center gap-2 self-start sm:self-auto"
        >
          <Plus className="w-4 h-4" />
          <span>New Project Colony</span>
        </button>
      </div>

      {/* Projects Cards Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {projects.map((proj) => {
          const projPlots = plots.filter((p) => p.projectId === proj.id);
          const availableCount = projPlots.filter((p) => p.status === 'AVAILABLE').length;
          const soldCount = projPlots.filter((p) => p.status === 'SOLD').length;
          const reservedCount = projPlots.filter((p) => p.status === 'RESERVED').length;

          return (
            <div
              key={proj.id}
              className="bg-white rounded-2xl border border-sand-300 p-6 shadow-warm-sm hover:shadow-warm-md transition-all flex flex-col justify-between"
            >
              <div>
                {/* Header info */}
                <div className="flex items-center justify-between gap-2 mb-3">
                  <span className="px-2.5 py-1 rounded-full text-[10px] font-mono font-bold bg-emerald-50 text-emerald-800 border border-emerald-200">
                    {proj.status} • {proj.projectType.replace(/_/g, ' ')}
                  </span>
                  <span className="text-[11px] font-mono text-espresso-600 font-semibold">
                    {proj.reraNumber}
                  </span>
                </div>

                <h3 className="font-serif font-bold text-xl text-espresso-950">
                  {proj.name}
                </h3>

                <div className="flex items-center gap-1.5 text-xs text-espresso-600 mt-1">
                  <MapPin className="w-3.5 h-3.5 text-forest" />
                  <span>{proj.address}</span>
                </div>

                <p className="text-xs text-espresso-700 mt-3 line-clamp-2 leading-relaxed">
                  {proj.description}
                </p>

                {/* Plot Counts bar */}
                <div className="grid grid-cols-4 gap-2 mt-5 p-3 rounded-xl bg-sand-50 border border-sand-200 text-center text-xs">
                  <div>
                    <div className="font-bold text-espresso-950">{proj.declaredPlotCount}</div>
                    <div className="text-[10px] text-espresso-500">Declared</div>
                  </div>
                  <div>
                    <div className="font-bold text-emerald-700">{availableCount}</div>
                    <div className="text-[10px] text-espresso-500">Available</div>
                  </div>
                  <div>
                    <div className="font-bold text-amber-700">{reservedCount}</div>
                    <div className="text-[10px] text-espresso-500">Hold (48h)</div>
                  </div>
                  <div>
                    <div className="font-bold text-espresso-900">{soldCount}</div>
                    <div className="text-[10px] text-espresso-500">Allotted</div>
                  </div>
                </div>

                {/* Meta details */}
                <div className="mt-4 flex flex-wrap items-center gap-4 text-xs text-espresso-600">
                  <div className="flex items-center gap-1.5">
                    <Compass className="w-3.5 h-3.5 text-forest" />
                    <span>Area: {proj.totalAreaValue} {proj.totalAreaUnit}</span>
                  </div>
                  <div className="flex items-center gap-1.5">
                    <Calendar className="w-3.5 h-3.5 text-forest" />
                    <span>Launch: {proj.launchDate}</span>
                  </div>
                </div>
              </div>

              {/* Actions */}
              <div className="mt-6 pt-4 border-t border-sand-200 flex items-center justify-between gap-3">
                <button
                  onClick={() => {
                    setActiveProjectId(proj.id);
                    onNavigate('plots');
                  }}
                  className="px-3 py-1.5 rounded-lg border border-sand-300 text-xs font-semibold text-espresso-800 hover:bg-sand-100 flex items-center gap-1.5"
                >
                  <Layers className="w-3.5 h-3.5 text-forest" />
                  <span>Interactive Grid</span>
                </button>

                <button
                  onClick={() => onSelectProjectDetail(proj.id)}
                  className="px-3.5 py-1.5 rounded-lg bg-forest hover:bg-forest-light text-white text-xs font-bold transition-colors flex items-center gap-1.5"
                >
                  <span>Project Overview</span>
                  <ArrowRight className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
