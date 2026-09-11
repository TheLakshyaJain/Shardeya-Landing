import React, { useState } from 'react';
import { 
  X, Phone, MessageSquare, Calendar, User, 
  MapPin, Clock, Plus, CheckCircle2, Star, 
  FileText, ArrowRight, ShieldCheck, Send 
} from 'lucide-react';
import { Lead, LeadStatus, Interaction } from '../../../../types/crm';
import { useCrm } from '../../../../context/CrmContext';

interface LeadDetailDrawerProps {
  lead: Lead;
  onClose: () => void;
}

export const LeadDetailDrawer: React.FC<LeadDetailDrawerProps> = ({
  lead,
  onClose,
}) => {
  const { interactions, addLeadInteraction, updateLeadStatus } = useCrm();

  // Filter interactions for this lead
  const leadInteractions = interactions.filter((i) => i.customerId === lead.id);

  // Form for new interaction log
  const [newNote, setNewNote] = useState('');
  const [interactionType, setInteractionType] = useState<'CALL' | 'VISIT' | 'WHATSAPP' | 'MEETING'>('CALL');
  const [result, setResult] = useState<'POSITIVE' | 'NEUTRAL' | 'NEGATIVE' | 'NEXT_SCHEDULED'>('POSITIVE');
  const [showLogForm, setShowLogForm] = useState(false);
  const [waSentToast, setWaSentToast] = useState(false);

  const handleAddInteraction = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newNote.trim()) return;

    addLeadInteraction({
      customerId: lead.id,
      occurredOn: new Date().toISOString().split('T')[0],
      type: interactionType,
      remarks: newNote.trim(),
      result,
      conductedBy: lead.assignedTo || 'Rajeshwar Singhania',
    });

    setNewNote('');
    setShowLogForm(false);
  };

  const handleSendWhatsApp = () => {
    setWaSentToast(true);
    setTimeout(() => setWaSentToast(false), 3500);

    // Also append WhatsApp message to audit trail
    addLeadInteraction({
      customerId: lead.id,
      occurredOn: new Date().toISOString().split('T')[0],
      type: 'WHATSAPP',
      remarks: 'Dispatched verified Masterplan PDF, UPRERA registration copy, and latest corner plot price matrix.',
      result: 'POSITIVE',
      conductedBy: 'Automated Real Estate Desk',
    });
  };

  return (
    <div className="fixed inset-0 z-50 bg-espresso-950/60 backdrop-blur-sm flex justify-end">
      <div className="w-full max-w-lg bg-white h-full shadow-2xl flex flex-col justify-between p-6 overflow-y-auto animate-fadeIn text-left">
        
        <div>
          {/* Header */}
          <div className="flex items-center justify-between pb-4 border-b border-sand-200">
            <div>
              <div className="flex items-center gap-2">
                <span className="text-[10px] font-mono uppercase tracking-wider text-forest font-bold">
                  Lead Profile
                </span>
                {lead.isImportant && <span className="text-amber-500 font-bold text-xs">★ Priority VIP</span>}
              </div>
              <h2 className="font-serif font-bold text-2xl text-espresso-950">
                {lead.fullName}
              </h2>
            </div>

            <button
              onClick={onClose}
              className="p-1.5 rounded-xl hover:bg-sand-100 text-espresso-500"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          {/* Quick Communication & WhatsApp Trigger Bar */}
          <div className="my-4 p-3 bg-sand-50 rounded-xl border border-sand-200 flex items-center justify-between gap-3 text-xs">
            <div className="flex items-center gap-2">
              <a
                href={`tel:${lead.mobile}`}
                className="px-3 py-1.5 rounded-lg bg-white border border-sand-300 hover:border-forest text-espresso-800 font-bold font-mono flex items-center gap-1.5 shadow-sm"
              >
                <Phone className="w-3.5 h-3.5 text-forest" />
                <span>{lead.mobile}</span>
              </a>
            </div>

            <button
              onClick={handleSendWhatsApp}
              className="px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white font-bold flex items-center gap-1.5 shadow-sm"
            >
              <MessageSquare className="w-3.5 h-3.5" />
              <span>Send WhatsApp Brochure</span>
            </button>
          </div>

          {waSentToast && (
            <div className="mb-4 p-3 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs flex items-center gap-2 animate-fadeIn">
              <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
              <span>WhatsApp message & project documents dispatched successfully!</span>
            </div>
          )}

          {/* Lead Details Overview */}
          <div className="p-4 rounded-xl bg-sand-50 border border-sand-200 space-y-2.5 text-xs">
            <div className="flex justify-between items-center text-espresso-600">
              <span>Pipeline Stage:</span>
              <select
                value={lead.status}
                onChange={(e) => updateLeadStatus(lead.id, e.target.value as any)}
                className="px-2.5 py-1 rounded-lg border border-sand-300 bg-white font-bold text-espresso-950 text-xs outline-none"
              >
                <option value="INTERESTED">New Inquiry</option>
                <option value="SITE_VISIT_SCHEDULED">Site Visit Scheduled</option>
                <option value="SITE_VISIT_DONE">Site Visit Done</option>
                <option value="FOLLOWING_UP">Negotiation & Terms</option>
                <option value="DEAL_CLOSED">Allotment Closed</option>
                <option value="LOST">Lost / Ineligible</option>
              </select>
            </div>

            <div className="flex justify-between items-center text-espresso-600">
              <span>Budget Range:</span>
              <span className="font-mono font-bold text-espresso-900">
                ₹{(lead.budgetMin/100000).toFixed(0)}L - ₹{(lead.budgetMax/100000).toFixed(0)}L
              </span>
            </div>

            <div className="flex justify-between items-center text-espresso-600">
              <span>Acquisition Source:</span>
              <span className="font-semibold text-espresso-800">{lead.source}</span>
            </div>

            <div className="flex justify-between items-center text-espresso-600">
              <span>Assigned Executive:</span>
              <span className="font-semibold text-forest">{lead.assignedTo}</span>
            </div>

            <div className="flex justify-between items-center text-espresso-600">
              <span>Next Follow-Up Date:</span>
              <span className="font-mono font-bold text-espresso-900">{lead.followUpDate}</span>
            </div>

            {lead.remarks && (
              <div className="pt-2 border-t border-sand-200 text-espresso-700 italic">
                "{lead.remarks}"
              </div>
            )}
          </div>

          {/* Append-Only Interaction History Log (§13.3) */}
          <div className="mt-6 space-y-3">
            <div className="flex items-center justify-between pb-1 border-b border-sand-200">
              <h3 className="font-serif font-bold text-base text-espresso-950">
                Interaction Audit Trail ({leadInteractions.length})
              </h3>

              <button
                onClick={() => setShowLogForm(!showLogForm)}
                className="text-xs font-bold text-forest hover:underline flex items-center gap-1"
              >
                <Plus className="w-3.5 h-3.5" />
                <span>Log Interaction</span>
              </button>
            </div>

            {/* Quick Add Log Form */}
            {showLogForm && (
              <form onSubmit={handleAddInteraction} className="p-3.5 rounded-xl bg-sand-50 border border-sand-300 space-y-3 animate-fadeIn text-xs">
                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label className="block font-semibold text-espresso-800 mb-1">Channel Type</label>
                    <select
                      value={interactionType}
                      onChange={(e) => setInteractionType(e.target.value as any)}
                      className="w-full px-2.5 py-1.5 rounded-lg border border-sand-300 bg-white"
                    >
                      <option value="CALL">Phone Call</option>
                      <option value="VISIT">Physical Site Visit</option>
                      <option value="WHATSAPP">WhatsApp Discussion</option>
                      <option value="MEETING">In-Office Meeting</option>
                    </select>
                  </div>

                  <div>
                    <label className="block font-semibold text-espresso-800 mb-1">Outcome</label>
                    <select
                      value={result}
                      onChange={(e) => setResult(e.target.value as any)}
                      className="w-full px-2.5 py-1.5 rounded-lg border border-sand-300 bg-white"
                    >
                      <option value="POSITIVE">Positive Interest</option>
                      <option value="NEXT_SCHEDULED">Next Follow-Up Set</option>
                      <option value="NEUTRAL">Neutral / Browsing</option>
                      <option value="NEGATIVE">Budget Mismatch</option>
                    </select>
                  </div>
                </div>

                <div>
                  <label className="block font-semibold text-espresso-800 mb-1">Notes & Next Actions *</label>
                  <textarea
                    rows={2}
                    required
                    placeholder="Enter discussion remarks, client reactions, and committed timelines..."
                    value={newNote}
                    onChange={(e) => setNewNote(e.target.value)}
                    className="w-full p-2 rounded-lg border border-sand-300 bg-white outline-none"
                  />
                </div>

                <div className="flex items-center justify-end gap-2">
                  <button
                    type="button"
                    onClick={() => setShowLogForm(false)}
                    className="px-3 py-1.5 rounded-lg border border-sand-300 hover:bg-sand-100"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="px-4 py-1.5 rounded-lg bg-forest text-white font-bold"
                  >
                    Save to Immutable Log
                  </button>
                </div>
              </form>
            )}

            {/* Interactions Timeline */}
            <div className="space-y-2.5 max-h-64 overflow-y-auto">
              {leadInteractions.map((int) => (
                <div key={int.id} className="p-3 rounded-xl bg-white border border-sand-200 text-xs space-y-1">
                  <div className="flex items-center justify-between text-[11px]">
                    <span className="font-bold text-espresso-950 flex items-center gap-1.5">
                      <span className="px-1.5 py-0.5 rounded bg-sand-100 font-mono text-[9px] uppercase font-bold text-forest">
                        {int.type}
                      </span>
                      <span>By {int.conductedBy}</span>
                    </span>
                    <span className="font-mono text-espresso-400">{int.occurredOn}</span>
                  </div>

                  <p className="text-espresso-700 leading-relaxed font-sans pt-0.5">
                    {int.remarks}
                  </p>

                  <div className="text-[10px] text-emerald-700 font-semibold pt-0.5">
                    Outcome: {int.result.replace(/_/g, ' ')}
                  </div>
                </div>
              ))}

              {leadInteractions.length === 0 && (
                <div className="py-6 text-center text-xs text-espresso-400 font-sans italic">
                  No interactions recorded yet. Click "Log Interaction" to record the first contact.
                </div>
              )}
            </div>
          </div>

        </div>

        {/* Footer */}
        <div className="pt-4 border-t border-sand-200 text-center">
          <button
            onClick={onClose}
            className="w-full py-2.5 px-4 rounded-xl border border-sand-300 text-espresso-700 hover:bg-sand-100 text-xs font-semibold"
          >
            Close Profile
          </button>
        </div>

      </div>
    </div>
  );
};
