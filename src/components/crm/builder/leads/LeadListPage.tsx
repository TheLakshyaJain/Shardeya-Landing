import React, { useState } from 'react';
import { 
  Users, Plus, Search, Filter, Phone, 
  MessageSquare, Calendar, Star, CheckCircle2, 
  ArrowRight, Clock, MapPin, Building2, UserPlus, Eye
} from 'lucide-react';
import { useCrm } from '../../../../context/CrmContext';
import { Lead, LeadStatus } from '../../../../types/crm';
import { LeadDetailDrawer } from './LeadDetailDrawer';
import { LeadFormModal } from './LeadFormModal';

interface LeadListPageProps {
  onOpenNewLeadModal: () => void;
}

export const LeadListPage: React.FC<LeadListPageProps> = ({
  onOpenNewLeadModal,
}) => {
  const { leads, updateLeadStatus } = useCrm();

  const [viewMode, setViewMode] = useState<'KANBAN' | 'TABLE'>('KANBAN');
  const [stageFilter, setStageFilter] = useState<'ALL' | LeadStatus>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedLead, setSelectedLead] = useState<Lead | null>(null);

  const STAGES: { id: LeadStatus; label: string; color: string }[] = [
    { id: 'INTERESTED', label: 'New Inquiries', color: 'border-blue-300 bg-blue-50/50' },
    { id: 'SITE_VISIT_SCHEDULED', label: 'Site Visit Scheduled', color: 'border-amber-300 bg-amber-50/50' },
    { id: 'SITE_VISIT_DONE', label: 'Site Visit Done', color: 'border-indigo-300 bg-indigo-50/50' },
    { id: 'FOLLOWING_UP', label: 'Negotiation & Terms', color: 'border-purple-300 bg-purple-50/50' },
    { id: 'DEAL_CLOSED', label: 'Allotment Closed', color: 'border-emerald-300 bg-emerald-50/50' },
    { id: 'LOST', label: 'Disqualified / Lost', color: 'border-sand-300 bg-sand-50/50' },
  ];

  const filteredLeads = leads.filter((l) => {
    if (stageFilter !== 'ALL' && l.status !== stageFilter) return false;
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      return l.fullName.toLowerCase().includes(q) || l.mobile.includes(q) || (l.email?.toLowerCase().includes(q) ?? false);
    }
    return true;
  });

  return (
    <div className="space-y-6 text-left animate-fadeIn">
      
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-sand-300">
        <div>
          <h1 className="font-serif font-bold text-2xl sm:text-3xl text-espresso-950">
            Buyer Leads & Customer Pipeline
          </h1>
          <p className="text-xs text-espresso-600 mt-1">
            Track inquiries from initial discovery to physical site visits, follow-up logs, and final plot allotments.
          </p>
        </div>

        <div className="flex items-center gap-2">
          {/* View toggle */}
          <div className="p-1 bg-sand-100 rounded-xl border border-sand-200 flex text-xs font-semibold">
            <button
              onClick={() => setViewMode('KANBAN')}
              className={`px-3 py-1.5 rounded-lg transition-all ${
                viewMode === 'KANBAN' ? 'bg-white shadow-sm font-bold text-espresso-950' : 'text-espresso-600'
              }`}
            >
              Kanban Board
            </button>
            <button
              onClick={() => setViewMode('TABLE')}
              className={`px-3 py-1.5 rounded-lg transition-all ${
                viewMode === 'TABLE' ? 'bg-white shadow-sm font-bold text-espresso-950' : 'text-espresso-600'
              }`}
            >
              List View
            </button>
          </div>

          <button
            onClick={onOpenNewLeadModal}
            className="px-3.5 py-2 rounded-xl bg-forest hover:bg-forest-light text-white text-xs font-sans font-bold shadow-warm-sm transition-all flex items-center gap-1.5"
          >
            <UserPlus className="w-3.5 h-3.5" />
            <span>Add Buyer Lead</span>
          </button>
        </div>
      </div>

      {/* Search & Filter Bar */}
      <div className="bg-white rounded-2xl border border-sand-300 p-4 shadow-warm-sm flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div className="relative flex-1 max-w-sm">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-espresso-400" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search lead by name or mobile number..."
            className="w-full pl-9 pr-3 py-2 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white text-xs font-sans text-espresso-950 outline-none"
          />
        </div>

        <div className="flex items-center gap-2 text-xs">
          <span className="text-espresso-500 font-semibold">Filter Stage:</span>
          <select
            value={stageFilter}
            onChange={(e) => setStageFilter(e.target.value as any)}
            className="px-3 py-2 rounded-xl border border-sand-300 bg-sand-50 text-xs font-semibold text-espresso-800 outline-none"
          >
            <option value="ALL">All Stages ({leads.length})</option>
            {STAGES.map((s) => (
              <option key={s.id} value={s.id}>{s.label}</option>
            ))}
          </select>
        </div>
      </div>

      {/* KANBAN BOARD VIEW */}
      {viewMode === 'KANBAN' && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6 gap-4 overflow-x-auto pb-4">
          {STAGES.map((stage) => {
            const stageLeads = filteredLeads.filter((l) => l.status === stage.id);

            return (
              <div
                key={stage.id}
                className="bg-sand-100/70 rounded-2xl border border-sand-300/80 p-3 min-w-[220px] flex flex-col justify-between"
              >
                <div>
                  {/* Column Header */}
                  <div className="flex items-center justify-between pb-2.5 mb-3 border-b border-sand-200">
                    <span className="font-sans font-bold text-xs text-espresso-900 truncate">
                      {stage.label}
                    </span>
                    <span className="w-5 h-5 rounded-full bg-sand-200 text-espresso-700 text-[10px] font-mono font-bold flex items-center justify-center">
                      {stageLeads.length}
                    </span>
                  </div>

                  {/* Cards */}
                  <div className="space-y-2.5">
                    {stageLeads.map((lead) => (
                      <div
                        key={lead.id}
                        onClick={() => setSelectedLead(lead)}
                        className="p-3 bg-white rounded-xl border border-sand-300 hover:border-forest hover:shadow-warm-sm transition-all cursor-pointer text-left space-y-2 group"
                      >
                        <div className="flex items-center justify-between">
                          <span className="font-bold text-xs text-espresso-950 flex items-center gap-1 group-hover:text-forest transition-colors">
                            <span>{lead.fullName}</span>
                            {lead.isImportant && <Star className="w-3 h-3 text-amber-500 fill-amber-500" />}
                          </span>
                          <span className="text-[10px] font-mono text-espresso-400">
                            {lead.source}
                          </span>
                        </div>

                        <div className="text-[11px] text-espresso-600 font-mono">
                          {lead.mobile}
                        </div>

                        <div className="flex items-center justify-between pt-1 border-t border-sand-100 text-[10px] text-espresso-500">
                          <span>Budget: ₹{(lead.budgetMin/100000).toFixed(0)}L-{(lead.budgetMax/100000).toFixed(0)}L</span>
                          <span className="font-mono text-forest font-semibold">{lead.followUpDate}</span>
                        </div>
                      </div>
                    ))}

                    {stageLeads.length === 0 && (
                      <div className="py-6 text-center text-xs text-espresso-400 font-sans italic">
                        No leads in this stage
                      </div>
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* TABLE VIEW */}
      {viewMode === 'TABLE' && (
        <div className="bg-white rounded-2xl border border-sand-300 shadow-warm-sm overflow-x-auto">
          <table className="w-full text-left text-xs font-sans">
            <thead className="bg-sand-50 border-b border-sand-200 text-espresso-600 uppercase font-bold text-[10px]">
              <tr>
                <th className="py-3.5 px-4">Lead Name</th>
                <th className="py-3.5 px-4">Phone / Email</th>
                <th className="py-3.5 px-4">Budget Range</th>
                <th className="py-3.5 px-4">Source</th>
                <th className="py-3.5 px-4">Stage Status</th>
                <th className="py-3.5 px-4">Next Follow-Up</th>
                <th className="py-3.5 px-4 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-sand-200">
              {filteredLeads.map((lead) => (
                <tr key={lead.id} className="hover:bg-sand-50 transition-colors">
                  <td className="py-3 px-4 font-bold text-espresso-950 flex items-center gap-1.5">
                    {lead.isImportant && <Star className="w-3 h-3 text-amber-500 fill-amber-500" />}
                    <span>{lead.fullName}</span>
                  </td>
                  <td className="py-3 px-4 font-mono text-espresso-700">{lead.mobile}</td>
                  <td className="py-3 px-4 font-mono">₹{(lead.budgetMin/100000).toFixed(0)}L - ₹{(lead.budgetMax/100000).toFixed(0)}L</td>
                  <td className="py-3 px-4 text-espresso-600">{lead.source}</td>
                  <td className="py-3 px-4">
                    <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-sand-100 text-espresso-800 border border-sand-300">
                      {lead.status.replace(/_/g, ' ')}
                    </span>
                  </td>
                  <td className="py-3 px-4 font-mono text-forest font-semibold">{lead.followUpDate}</td>
                  <td className="py-3 px-4 text-right">
                    <button
                      onClick={() => setSelectedLead(lead)}
                      className="px-2.5 py-1 rounded-lg border border-sand-300 hover:bg-sand-100 text-[11px] font-bold text-espresso-800"
                    >
                      Inspect Lead
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Selected Lead Drawer with Append-Only Interaction History */}
      {selectedLead && (
        <LeadDetailDrawer
          lead={selectedLead}
          onClose={() => setSelectedLead(null)}
        />
      )}

    </div>
  );
};
