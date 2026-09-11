import React, { useState } from 'react';
import { X, UserPlus, Users, Phone, IndianRupee } from 'lucide-react';
import { useCrm } from '../../../../context/CrmContext';

interface LeadFormModalProps {
  onClose: () => void;
  onSuccess: () => void;
}

export const LeadFormModal: React.FC<LeadFormModalProps> = ({
  onClose,
  onSuccess,
}) => {
  const { addLead, brokers, activeProjectId } = useCrm();

  const [fullName, setFullName] = useState('');
  const [mobile, setMobile] = useState('');
  const [email, setEmail] = useState('');
  const [budgetMin, setBudgetMin] = useState(4000000);
  const [budgetMax, setBudgetMax] = useState(6000000);
  const [preferredPropertyType, setPreferredPropertyType] = useState<'PLOT' | 'VILLA' | 'APARTMENT'>('PLOT');
  const [source, setSource] = useState<'REFERRAL' | 'FACEBOOK' | 'WALK_IN' | 'WEBSITE' | 'BROKER' | 'EXHIBITION'>('WALK_IN');
  const [sourceBrokerId, setSourceBrokerId] = useState('');
  const [followUpDate, setFollowUpDate] = useState('2026-09-15');
  const [remarks, setRemarks] = useState('');
  const [isImportant, setIsImportant] = useState(false);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!fullName.trim() || !mobile.trim()) return;

    addLead({
      fullName: fullName.trim(),
      mobile: mobile.trim(),
      email: email.trim() || undefined,
      budgetMin: Number(budgetMin),
      budgetMax: Number(budgetMax),
      preferredPropertyType,
      source,
      sourceBrokerId: source === 'BROKER' ? sourceBrokerId : undefined,
      status: 'INTERESTED',
      interestedProjectId: activeProjectId,
      assignedTo: 'Rajeshwar Singhania',
      followUpDate,
      isImportant,
      remarks: remarks.trim(),
    });

    onSuccess();
  };

  return (
    <div className="fixed inset-0 z-50 bg-espresso-950/60 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl border border-sand-300 max-w-lg w-full p-6 sm:p-8 shadow-2xl animate-fadeIn text-left max-h-[90vh] overflow-y-auto">
        
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-sand-200">
          <div>
            <span className="text-[10px] font-mono text-forest uppercase tracking-wider font-bold">
              Buyer CRM
            </span>
            <h2 className="font-serif font-bold text-2xl text-espresso-950">
              Register New Lead Inquiry
            </h2>
          </div>

          <button
            onClick={onClose}
            className="p-1.5 rounded-xl hover:bg-sand-100 text-espresso-500"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="mt-5 space-y-3.5 text-xs font-sans">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="block font-semibold text-espresso-800 mb-1">
                Full Legal Name *
              </label>
              <input
                type="text"
                required
                placeholder="e.g. Alok Singhal"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                className="w-full px-3.5 py-2 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white text-xs text-espresso-950 outline-none"
              />
            </div>

            <div>
              <label className="block font-semibold text-espresso-800 mb-1">
                Mobile Number *
              </label>
              <input
                type="tel"
                required
                placeholder="+91 98110 00000"
                value={mobile}
                onChange={(e) => setMobile(e.target.value)}
                className="w-full px-3.5 py-2 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white text-xs font-mono text-espresso-950 outline-none"
              />
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="block font-semibold text-espresso-800 mb-1">
                Budget Minimum (₹)
              </label>
              <input
                type="number"
                value={budgetMin}
                onChange={(e) => setBudgetMin(Number(e.target.value))}
                className="w-full px-3.5 py-2 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white text-xs font-mono outline-none"
              />
            </div>

            <div>
              <label className="block font-semibold text-espresso-800 mb-1">
                Budget Maximum (₹)
              </label>
              <input
                type="number"
                value={budgetMax}
                onChange={(e) => setBudgetMax(Number(e.target.value))}
                className="w-full px-3.5 py-2 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white text-xs font-mono outline-none"
              />
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="block font-semibold text-espresso-800 mb-1">
                Acquisition Channel Source
              </label>
              <select
                value={source}
                onChange={(e) => setSource(e.target.value as any)}
                className="w-full px-3.5 py-2 rounded-xl border border-sand-300 bg-sand-50 text-xs font-sans outline-none"
              >
                <option value="WALK_IN">Direct Site Walk-In</option>
                <option value="BROKER">Channel Partner Referral</option>
                <option value="FACEBOOK">Facebook / Meta Ads</option>
                <option value="WEBSITE">Direct Corporate Website</option>
                <option value="REFERRAL">Existing Buyer Referral</option>
              </select>
            </div>

            {source === 'BROKER' && (
              <div>
                <label className="block font-semibold text-espresso-800 mb-1">
                  Referring Broker Partner
                </label>
                <select
                  value={sourceBrokerId}
                  onChange={(e) => setSourceBrokerId(e.target.value)}
                  className="w-full px-3.5 py-2 rounded-xl border border-sand-300 bg-sand-50 text-xs font-sans outline-none"
                >
                  <option value="">Select Channel Partner</option>
                  {brokers.map((b) => (
                    <option key={b.id} value={b.id}>
                      {b.fullName} ({b.firmName})
                    </option>
                  ))}
                </select>
              </div>
            )}
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="block font-semibold text-espresso-800 mb-1">
                Next Follow-Up Date
              </label>
              <input
                type="date"
                value={followUpDate}
                onChange={(e) => setFollowUpDate(e.target.value)}
                className="w-full px-3.5 py-2 rounded-xl border border-sand-300 bg-sand-50 text-xs font-mono outline-none"
              />
            </div>

            <div className="flex items-center pt-5">
              <label className="flex items-center gap-2 cursor-pointer text-espresso-700 font-semibold">
                <input
                  type="checkbox"
                  checked={isImportant}
                  onChange={(e) => setIsImportant(e.target.checked)}
                  className="rounded text-forest focus:ring-forest"
                />
                <span>Mark as Priority VIP Lead ★</span>
              </label>
            </div>
          </div>

          <div>
            <label className="block font-semibold text-espresso-800 mb-1">
              Initial Inquired Plot Requirements / Notes
            </label>
            <textarea
              rows={2}
              placeholder="e.g. Looking for 200 Gaj park-facing plot for immediate villa construction..."
              value={remarks}
              onChange={(e) => setRemarks(e.target.value)}
              className="w-full p-2.5 rounded-xl border border-sand-300 bg-sand-50 text-xs font-sans outline-none"
            />
          </div>

          <div className="pt-3 border-t border-sand-200 flex items-center justify-between gap-3">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 rounded-xl border border-sand-300 text-espresso-700 hover:bg-sand-100 font-semibold"
            >
              Cancel
            </button>

            <button
              type="submit"
              className="px-5 py-2 rounded-xl bg-forest hover:bg-forest-light text-white font-bold uppercase tracking-wider shadow-warm-sm"
            >
              Save Lead to CRM
            </button>
          </div>
        </form>

      </div>
    </div>
  );
};
