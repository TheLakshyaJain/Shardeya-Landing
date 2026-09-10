import React, { useState } from 'react';
import { Layers, UserCheck, FileText, Send, CheckCircle2 } from 'lucide-react';
import { sampleUnits } from '../../data/sampleData';
import { UnitData, UnitStatus } from '../../types';
import { useLanguage } from '../../context/LanguageContext';
import { GlassCard } from '../common/GlassCard';

export const MasterplanVisualizer: React.FC = () => {
  const { t, language } = useLanguage();
  const [selectedUnit, setSelectedUnit] = useState<UnitData | null>(sampleUnits[0]);
  const [activeFilter, setActiveFilter] = useState<string>('all');
  const [statusFilter, setStatusFilter] = useState<string>('all');
  const [noticeSent, setNoticeSent] = useState(false);

  const isHi = language === 'hi';

  const formatPrice = (price: number) => {
    if (price >= 10000000) {
      return `₹${(price / 10000000).toFixed(2)} Cr`;
    }
    return `₹${(price / 100000).toFixed(1)} Lakh`;
  };

  const filteredUnits = sampleUnits.filter((unit) => {
    const matchesType =
      activeFilter === 'all' ||
      (activeFilter === 'villa' && unit.type === 'Villa Plot') ||
      (activeFilter === 'apartment' && (unit.type === 'Apartment' || unit.type === 'Penthouse')) ||
      (activeFilter === 'commercial' && unit.type === 'Commercial Shop');

    const matchesStatus =
      statusFilter === 'all' || unit.status === statusFilter;

    return matchesType && matchesStatus;
  });

  const getStatusBadge = (status: UnitStatus) => {
    switch (status) {
      case 'available':
        return {
          label: t.masterplan.statusAvailable,
          bg: 'bg-emerald-100 text-emerald-800 border-emerald-300 font-semibold',
          dot: 'bg-emerald-500',
        };
      case 'booked':
        return {
          label: t.masterplan.statusBooked,
          bg: 'bg-slate-100 text-slate-700 border-slate-300 font-semibold',
          dot: 'bg-slate-500',
        };
      case 'negotiation':
        return {
          label: t.masterplan.statusNegotiation,
          bg: 'bg-amber-100 text-amber-900 border-amber-300 font-semibold',
          dot: 'bg-amber-500',
        };
      case 'reserved':
        return {
          label: t.masterplan.statusReserved,
          bg: 'bg-indigo-100 text-indigo-800 border-indigo-300 font-semibold',
          dot: 'bg-indigo-500',
        };
    }
  };

  return (
    <section id="masterplan" className="py-24 relative bg-slate-50/60 border-b border-slate-200">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        
        {/* Section Header */}
        <div className="text-center max-w-3xl mx-auto mb-14">
          <div className="inline-flex items-center gap-2 px-3.5 py-1 rounded-full border border-sand-300 bg-sand-50 text-espresso-800 text-xs font-sans mb-3 shadow-warm-sm">
            <span className="w-2 h-2 rounded-full bg-forest" />
            <span className="font-semibold">{t.masterplan.tag}</span>
          </div>
          <h2 className="text-3xl sm:text-4xl lg:text-5xl font-serif text-espresso-950 font-normal tracking-tight">
            {t.masterplan.title}
          </h2>
          <p className="mt-4 text-base text-espresso-700 font-sans">
            {t.masterplan.subtitle}
          </p>
        </div>

        {/* Filter Bar */}
        <div className="flex flex-col lg:flex-row items-center justify-between gap-4 mb-8 bg-sand-50 p-3.5 rounded-xl border border-sand-300 shadow-warm-sm">
          
          {/* Category Tabs */}
          <div className="flex flex-wrap items-center gap-1.5 p-1 bg-slate-200/70 rounded-xl border border-slate-300/80 font-sans">
            {[
              { id: 'all', label: t.masterplan.filterAll },
              { id: 'villa', label: t.masterplan.filterVillas },
              { id: 'apartment', label: t.masterplan.filterApartments },
              { id: 'commercial', label: t.masterplan.filterCommercial },
            ].map((tab) => {
              const isActive = activeFilter === tab.id;
              return (
                <button
                  key={tab.id}
                  onClick={() => setActiveFilter(tab.id)}
                  className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                    isActive
                      ? 'bg-emerald-700 text-white shadow-sm font-bold ring-1 ring-emerald-800'
                      : 'text-slate-700 hover:text-slate-950 hover:bg-white/70'
                  }`}
                >
                  {tab.label}
                </button>
              );
            })}
          </div>

          {/* Status Filter Badges */}
          <div className="flex flex-wrap items-center gap-2 text-xs font-sans">
            <button
              onClick={() => setStatusFilter('all')}
              className={`px-2.5 py-1 rounded-md border text-[11px] font-bold transition-colors ${
                statusFilter === 'all'
                  ? 'border-espresso-800 bg-espresso-900 text-white'
                  : 'border-transparent text-espresso-600 hover:text-espresso-950'
              }`}
            >
              ALL
            </button>
            <button
              onClick={() => setStatusFilter('available')}
              className={`flex items-center gap-1.5 px-2.5 py-1 rounded-md border text-[11px] font-bold ${
                statusFilter === 'available'
                  ? 'border-forest bg-forest-subtle text-forest'
                  : 'border-transparent text-espresso-600 hover:text-forest'
              }`}
            >
              <span className="w-1.5 h-1.5 rounded-full bg-forest" />
              AVAILABLE
            </button>
            <button
              onClick={() => setStatusFilter('booked')}
              className={`flex items-center gap-1.5 px-2.5 py-1 rounded-md border text-[11px] font-bold ${
                statusFilter === 'booked'
                  ? 'border-sand-400 bg-sand-200 text-espresso-800'
                  : 'border-transparent text-espresso-600 hover:text-espresso-900'
              }`}
            >
              <span className="w-1.5 h-1.5 rounded-full bg-espresso-700" />
              BOOKED
            </button>
            <button
              onClick={() => setStatusFilter('negotiation')}
              className={`flex items-center gap-1.5 px-2.5 py-1 rounded-md border text-[11px] font-bold ${
                statusFilter === 'negotiation'
                  ? 'border-bronze bg-bronze-subtle text-bronze-dark'
                  : 'border-transparent text-espresso-600 hover:text-bronze'
              }`}
            >
              <span className="w-1.5 h-1.5 rounded-full bg-bronze" />
              DISCUSSION
            </button>
          </div>

        </div>

        {/* Masterplan Grid + Inspection Drawer */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
          
          {/* Visual Grid (8 Cols) */}
          <div className="lg:col-span-8 bg-sand-50 p-5 rounded-2xl border border-sand-300 shadow-warm-sm">
            <div className="flex items-center justify-between mb-4 pb-3 border-b border-sand-200">
              <span className="text-xs font-mono font-bold text-espresso-700 uppercase tracking-wider">
                {filteredUnits.length} Units Available
              </span>
            </div>

            {/* Grid of Plots */}
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3">
              {filteredUnits.map((unit) => {
                const badge = getStatusBadge(unit.status);
                const isSelected = selectedUnit?.id === unit.id;

                return (
                  <button
                    key={unit.id}
                    onClick={() => setSelectedUnit(unit)}
                    className={`relative p-3.5 rounded-xl border text-left transition-all duration-200 group ${
                      isSelected
                        ? 'border-emerald-600 bg-emerald-50/90 shadow-md ring-2 ring-emerald-500/80 -translate-y-0.5'
                        : 'border-slate-200 bg-white hover:bg-slate-50 hover:border-emerald-300 hover:shadow-sm'
                    }`}
                  >
                    <div className="flex items-center justify-between mb-1.5">
                      <span className="text-xs font-bold text-espresso-900 group-hover:text-forest transition-colors font-sans">
                        {unit.number}
                      </span>
                      <span className={`w-2 h-2 rounded-full ${badge.dot}`} />
                    </div>

                    <div className="text-[11px] text-espresso-600 mb-1 font-sans line-clamp-1">
                      {unit.areaSqFt} sq.ft • {unit.facing.split(' ')[0]}
                    </div>

                    <div className="text-xs font-bold text-espresso-950 font-sans">
                      {formatPrice(unit.priceTotal)}
                    </div>

                    <div className="mt-2 pt-1.5 border-t border-sand-200 flex items-center justify-between text-[10px] font-sans">
                      <span className={`px-1.5 py-0.5 rounded border text-[9px] font-bold ${badge.bg}`}>
                        {badge.label}
                      </span>
                      {unit.buyer && (
                        <span className="text-espresso-600 font-mono">
                          {unit.buyer.completedMilestones}/{unit.buyer.totalMilestones} Paid
                        </span>
                      )}
                    </div>
                  </button>
                );
              })}
            </div>

            <div className="mt-4 p-2.5 rounded-lg bg-sand-150 border border-sand-300 flex items-center justify-between text-xs text-espresso-700 font-sans">
              <span>💡 {t.masterplan.clickPrompt}</span>
              <span className="text-forest font-bold">RERA Registered</span>
            </div>
          </div>

          {/* Unit Intelligence Dossier (4 Cols) */}
          <div className="lg:col-span-4">
            {selectedUnit ? (
              <GlassCard variant="default" className="p-6 text-left bg-white border-sand-300 shadow-warm-md">
                {/* Header */}
                <div className="flex items-start justify-between pb-4 border-b border-sand-200">
                  <div>
                    <div className="flex items-center gap-2">
                      <h3 className="text-2xl font-serif font-bold text-espresso-950">{selectedUnit.number}</h3>
                      <span className={`text-[10px] font-sans px-2 py-0.5 rounded border uppercase font-bold ${getStatusBadge(selectedUnit.status).bg}`}>
                        {getStatusBadge(selectedUnit.status).label}
                      </span>
                    </div>
                    <p className="text-xs text-espresso-600 mt-1 font-sans">
                      {selectedUnit.type} • {selectedUnit.areaSqFt} sq.ft • {selectedUnit.facing}
                    </p>
                  </div>
                  <div className="text-right">
                    <div className="text-lg font-serif font-bold text-espresso-900">{formatPrice(selectedUnit.priceTotal)}</div>
                    <div className="text-[10px] text-espresso-500 font-mono">₹{(selectedUnit.priceTotal / selectedUnit.areaSqFt).toFixed(0)}/sq.ft</div>
                  </div>
                </div>

                {/* Buyer & Financial Dossier */}
                {selectedUnit.buyer ? (
                  <div className="py-4 space-y-4 border-b border-sand-200">
                    <div>
                      <div className="text-xs font-sans font-bold text-espresso-600 uppercase tracking-wider mb-2 flex items-center gap-1.5">
                        <UserCheck className="w-3.5 h-3.5 text-forest" />
                        {t.masterplan.buyerDetails}
                      </div>
                      <div className="p-3 rounded-xl bg-sand-100 border border-sand-300 space-y-1 text-xs">
                        <div className="flex justify-between">
                          <span className="text-espresso-600">{isHi ? 'नाम' : 'Buyer'}:</span>
                          <span className="font-bold text-espresso-900 font-sans">{selectedUnit.buyer.name}</span>
                        </div>
                        <div className="flex justify-between">
                          <span className="text-espresso-600">KYC Status:</span>
                          <span className="px-1.5 py-0.2 rounded bg-forest-subtle text-forest font-bold text-[10px] border border-forest/30">
                            {selectedUnit.buyer.kycStatus}
                          </span>
                        </div>
                        <div className="flex justify-between font-mono text-[11px]">
                          <span className="text-espresso-600">{isHi ? 'संपर्क' : 'Contact'}:</span>
                          <span className="text-espresso-800">{selectedUnit.buyer.phone}</span>
                        </div>
                      </div>
                    </div>

                    {/* Milestone Payment Progress */}
                    <div>
                      <div className="text-xs font-sans font-bold text-espresso-600 uppercase tracking-wider mb-2 flex items-center justify-between">
                        <span>{t.masterplan.paymentProgress}</span>
                        <span className="text-forest font-bold font-mono">
                          {Math.round((selectedUnit.buyer.amountPaid / selectedUnit.priceTotal) * 100)}% Paid
                        </span>
                      </div>
                      <div className="w-full h-2 rounded-full bg-sand-200 overflow-hidden border border-sand-300 mb-2">
                        <div
                          className="h-full bg-forest rounded-full transition-all duration-500"
                          style={{
                            width: `${(selectedUnit.buyer.amountPaid / selectedUnit.priceTotal) * 100}%`,
                          }}
                        />
                      </div>
                      <div className="flex justify-between text-[11px] font-mono text-espresso-700">
                        <span>Paid: {formatPrice(selectedUnit.buyer.amountPaid)}</span>
                        <span>Pending: {formatPrice(selectedUnit.priceTotal - selectedUnit.buyer.amountPaid)}</span>
                      </div>
                    </div>

                    {/* Next Due Milestone */}
                    <div className="p-3 rounded-xl bg-bronze-subtle border border-bronze/30 flex items-center justify-between">
                      <div>
                        <div className="text-[10px] text-bronze-dark font-sans font-bold uppercase">{t.masterplan.nextDue}</div>
                        <div className="text-xs text-espresso-900 font-sans font-bold">{selectedUnit.buyer.nextInstallmentDue}</div>
                      </div>
                      <div className="text-right">
                        <div className="text-sm font-serif font-bold text-espresso-950">{formatPrice(selectedUnit.buyer.nextAmount)}</div>
                        <div className="text-[10px] text-bronze-dark font-sans">Milestone 5/6</div>
                      </div>
                    </div>
                  </div>
                ) : (
                  <div className="py-6 text-center text-espresso-700 text-xs bg-sand-50 rounded-xl my-4 border border-dashed border-sand-300">
                    <CheckCircle2 className="w-7 h-7 text-forest mx-auto mb-1.5" />
                    <p className="font-bold text-espresso-900 font-sans">{isHi ? 'यूनिट बुकिंग के लिए उपलब्ध' : 'Available for Immediate Booking'}</p>
                    <p className="text-[11px] mt-1 text-espresso-600 font-sans">
                      {isHi ? 'बेस रेट: ₹' + (selectedUnit.priceTotal / selectedUnit.areaSqFt).toFixed(0) + '/वर्ग फीट' : 'Standard allotment deed ready.'}
                    </p>
                  </div>
                )}

                {/* Broker Details */}
                {selectedUnit.broker && (
                  <div className="py-3 border-b border-sand-200 space-y-1 text-xs">
                    <div className="font-sans font-bold text-espresso-600 uppercase text-[11px]">
                      {t.masterplan.brokerDetails}
                    </div>
                    <div className="flex items-center justify-between pt-1">
                      <div>
                        <div className="font-bold text-espresso-900 font-sans">{selectedUnit.broker.name}</div>
                        <div className="text-[10px] text-bronze-dark font-sans font-semibold">{selectedUnit.broker.tier}</div>
                      </div>
                      <div className="text-right font-sans">
                        <div className="font-bold text-forest">{formatPrice(selectedUnit.broker.commissionEarned)}</div>
                        <div className="text-[10px] text-espresso-500">{selectedUnit.broker.commissionRate}% Commission</div>
                      </div>
                    </div>
                  </div>
                )}

                {/* Actions */}
                <div className="pt-4 space-y-2">
                  <button
                    onClick={() => {
                      setNoticeSent(true);
                      setTimeout(() => setNoticeSent(false), 3000);
                    }}
                    className="w-full py-2.5 px-4 rounded-lg bg-forest hover:bg-forest-light text-white font-sans font-bold text-xs transition-colors flex items-center justify-center gap-2 shadow-warm-sm"
                  >
                    <Send className="w-3.5 h-3.5" />
                    {noticeSent
                      ? (isHi ? '✓ व्हाट्सएप रिमाइंडर भेजा गया!' : '✓ WhatsApp Reminder Dispatched!')
                      : (isHi ? 'व्हाट्सएप पर किस्त रिमाइंडर भेजें' : 'Send WhatsApp Payment Reminder')}
                  </button>

                  <button
                    onClick={() => alert(`Opening allotment draft for ${selectedUnit.number}...`)}
                    className="w-full py-2.5 px-4 rounded-lg bg-sand-100 hover:bg-sand-200 border border-sand-300 text-espresso-900 font-sans font-semibold text-xs transition-colors flex items-center justify-center gap-2"
                  >
                    <FileText className="w-3.5 h-3.5 text-espresso-500" />
                    {t.masterplan.viewAgreement}
                  </button>
                </div>
              </GlassCard>
            ) : null}
          </div>

        </div>

      </div>
    </section>
  );
};
