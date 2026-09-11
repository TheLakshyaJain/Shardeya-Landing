import React, { useState } from 'react';
import { 
  Handshake, Plus, Search, Filter, ShieldCheck, 
  Award, TrendingUp, CheckCircle2, AlertCircle, 
  ExternalLink, Building, Phone, Mail, FileCheck,
  CreditCard, ChevronRight, X
} from 'lucide-react';
import { useCrm } from '../../../../context/CrmContext';
import { useLanguage } from '../../../../context/LanguageContext';
import { BrokerPartner, CommissionVoucher } from '../../../../types/crm';

export const BrokerListPage: React.FC = () => {
  const { brokers, vouchers, addBroker, updateBrokerTier, markVoucherPaid } = useCrm();
  const { language } = useLanguage();
  const isHi = language === 'hi';

  const [activeTab, setActiveTab] = useState<'partners' | 'vouchers'>('partners');
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedTier, setSelectedTier] = useState<string>('ALL');
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [settleVoucher, setSettleVoucher] = useState<CommissionVoucher | null>(null);
  const [utrRef, setUtrRef] = useState('');

  // Form state for new broker
  const [newBroker, setNewBroker] = useState({
    fullName: '',
    mobile: '',
    email: '',
    firmName: '',
    cityArea: '',
    reraNumber: '',
    commissionType: 'PERCENTAGE' as const,
    commissionRate: 2.0,
    tier: 'Gold' as BrokerPartner['tier'],
    status: 'ACTIVE' as const
  });

  const tiers: { tier: BrokerPartner['tier']; rate: number; color: string; desc: string }[] = [
    { tier: 'Bronze', rate: 1.5, color: 'bg-amber-100 text-amber-800 border-amber-300', desc: '1-5 allotments' },
    { tier: 'Silver', rate: 2.0, color: 'bg-slate-100 text-slate-800 border-slate-300', desc: '6-15 allotments' },
    { tier: 'Gold', rate: 2.5, color: 'bg-yellow-100 text-yellow-800 border-yellow-300', desc: '16-30 allotments' },
    { tier: 'Platinum', rate: 3.0, color: 'bg-emerald-100 text-emerald-800 border-emerald-300', desc: '30+ allotments & syndicates' }
  ];

  const filteredBrokers = brokers.filter(b => {
    const matchesSearch = b.fullName.toLowerCase().includes(searchQuery.toLowerCase()) ||
                          b.firmName.toLowerCase().includes(searchQuery.toLowerCase()) ||
                          b.cityArea.toLowerCase().includes(searchQuery.toLowerCase()) ||
                          b.mobile.includes(searchQuery);
    const matchesTier = selectedTier === 'ALL' || b.tier === selectedTier;
    return matchesSearch && matchesTier;
  });

  const totalClosedDeals = brokers.reduce((sum, b) => sum + b.dealsClosedCount, 0);
  const totalCommissionEarned = brokers.reduce((sum, b) => sum + b.totalCommissionEarned, 0);
  const totalCommissionPaid = brokers.reduce((sum, b) => sum + b.totalCommissionPaid, 0);
  const pendingVouchers = vouchers.filter(v => v.status !== 'PAID');
  const pendingVoucherTotal = pendingVouchers.reduce((sum, v) => sum + v.netPayable, 0);

  const handleCreateBroker = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newBroker.fullName || !newBroker.mobile || !newBroker.firmName) return;

    addBroker(newBroker);
    setIsAddModalOpen(false);
    setNewBroker({
      fullName: '',
      mobile: '',
      email: '',
      firmName: '',
      cityArea: '',
      reraNumber: '',
      commissionType: 'PERCENTAGE',
      commissionRate: 2.0,
      tier: 'Gold',
      status: 'ACTIVE'
    });
  };

  const handleSettle = (e: React.FormEvent) => {
    e.preventDefault();
    if (!settleVoucher || !utrRef) return;
    markVoucherPaid(settleVoucher.id, utrRef);
    setSettleVoucher(null);
    setUtrRef('');
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-forest/10 text-forest border border-forest/20 tracking-wide uppercase">
              {isHi ? 'चैनल पार्टनर सिंडिकेट' : 'Channel Partner Network'}
            </span>
          </div>
          <h1 className="text-2xl sm:text-3xl font-serif font-bold text-espresso-950 mt-1">
            {isHi ? 'दलाल व चैनल पार्टनर प्रबंधन' : 'Broker Syndicate Engine'}
          </h1>
          <p className="text-sm text-espresso-700">
            {isHi 
              ? 'RERA प्रमाणित ब्रोकर डायरेक्टरी, 4-स्तरीय कमीशन ढांचा व वाउचर निपटान।' 
              : 'RERA registered channel partners, multi-tier payout contracts, and tax-deducted voucher settlements.'}
          </p>
        </div>

        <button
          onClick={() => setIsAddModalOpen(true)}
          className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-forest hover:bg-forest-600 text-white font-medium shadow-sm transition-colors text-sm self-start sm:self-auto"
        >
          <Plus className="w-4 h-4" />
          {isHi ? 'नया पार्टनर जोड़ें' : 'Onboard Channel Partner'}
        </button>
      </div>

      {/* KPI Stats Strip */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="p-4 rounded-xl bg-white border border-sand-300 shadow-sm">
          <div className="text-xs font-medium text-espresso-600 uppercase tracking-wider">
            {isHi ? 'कुल सक्रिय पार्टनर' : 'Active Partners'}
          </div>
          <div className="text-2xl font-bold font-serif text-espresso-950 mt-1">
            {brokers.length}
          </div>
          <div className="text-xs text-forest mt-1 flex items-center gap-1 font-medium">
            <ShieldCheck className="w-3.5 h-3.5" /> 100% RERA Verified
          </div>
        </div>

        <div className="p-4 rounded-xl bg-white border border-sand-300 shadow-sm">
          <div className="text-xs font-medium text-espresso-600 uppercase tracking-wider">
            {isHi ? 'कुल बंद सौदे' : 'Total Deals Closed'}
          </div>
          <div className="text-2xl font-bold font-serif text-espresso-950 mt-1">
            {totalClosedDeals} Units
          </div>
          <div className="text-xs text-espresso-600 mt-1">
            Across active layouts
          </div>
        </div>

        <div className="p-4 rounded-xl bg-white border border-sand-300 shadow-sm">
          <div className="text-xs font-medium text-espresso-600 uppercase tracking-wider">
            {isHi ? 'कुल वितरित कमीशन' : 'Disbursed Commission'}
          </div>
          <div className="text-2xl font-bold font-serif text-espresso-950 mt-1">
            ₹{(totalCommissionPaid / 100000).toFixed(2)} L
          </div>
          <div className="text-xs text-forest mt-1">
            Settled via bank RTGS
          </div>
        </div>

        <div className="p-4 rounded-xl bg-white border border-amber-200 bg-amber-50/40 shadow-sm">
          <div className="text-xs font-medium text-amber-900 uppercase tracking-wider">
            {isHi ? 'लंबित वाउचर देनदारी' : 'Pending Settlement'}
          </div>
          <div className="text-2xl font-bold font-serif text-amber-950 mt-1">
            ₹{(pendingVoucherTotal / 100000).toFixed(2)} L
          </div>
          <div className="text-xs text-amber-700 mt-1 font-medium">
            {pendingVouchers.length} vouchers queued for release
          </div>
        </div>
      </div>

      {/* Commission Tier Slabs */}
      <div className="p-4 rounded-xl bg-white border border-sand-300 shadow-sm">
        <div className="flex items-center justify-between mb-3">
          <h3 className="text-sm font-serif font-bold text-espresso-950 flex items-center gap-2">
            <Award className="w-4 h-4 text-forest" />
            {isHi ? 'Shardeya 4-स्तरीय कमीशन नीति' : 'Standard 4-Tier Commission Slabs'}
          </h3>
          <span className="text-xs text-espresso-500">Auto-upgrades on volume thresholds</span>
        </div>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          {tiers.map(t => (
            <div key={t.tier} className="p-3 rounded-lg border border-sand-200 bg-sand-50/50">
              <div className="flex items-center justify-between">
                <span className={`px-2 py-0.5 rounded text-xs font-semibold ${t.color}`}>
                  {t.tier}
                </span>
                <span className="text-base font-bold text-forest">{t.rate}%</span>
              </div>
              <p className="text-xs text-espresso-600 mt-2">{t.desc}</p>
            </div>
          ))}
        </div>
      </div>

      {/* Tabs Switcher: Partners Directory vs Commission Vouchers */}
      <div className="border-b border-sand-300 flex gap-4">
        <button
          onClick={() => setActiveTab('partners')}
          className={`pb-3 text-sm font-semibold flex items-center gap-2 border-b-2 transition-colors ${
            activeTab === 'partners'
              ? 'border-forest text-forest'
              : 'border-transparent text-espresso-600 hover:text-espresso-950'
          }`}
        >
          <Handshake className="w-4 h-4" />
          {isHi ? 'पार्टनर डायरेक्टरी' : 'Partner Directory'} ({brokers.length})
        </button>
        <button
          onClick={() => setActiveTab('vouchers')}
          className={`pb-3 text-sm font-semibold flex items-center gap-2 border-b-2 transition-colors ${
            activeTab === 'vouchers'
              ? 'border-forest text-forest'
              : 'border-transparent text-espresso-600 hover:text-espresso-950'
          }`}
        >
          <CreditCard className="w-4 h-4" />
          {isHi ? 'कमीशन वाउचर निपटान' : 'Commission Vouchers & TDS'} ({vouchers.length})
        </button>
      </div>

      {activeTab === 'partners' ? (
        <>
          {/* Filter Bar */}
          <div className="flex flex-col sm:flex-row gap-3">
            <div className="relative flex-1">
              <Search className="w-4 h-4 text-espresso-400 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                placeholder={isHi ? 'नाम, फर्म, क्षेत्र या मोबाइल से खोजें...' : 'Search by broker name, firm, locality or mobile...'}
                value={searchQuery}
                onChange={e => setSearchQuery(e.target.value)}
                className="w-full pl-9 pr-4 py-2 text-sm bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 focus:border-forest"
              />
            </div>
            <div className="flex items-center gap-2 overflow-x-auto pb-1">
              <span className="text-xs text-espresso-500 font-medium">Tier:</span>
              {['ALL', 'Bronze', 'Silver', 'Gold', 'Platinum'].map(tier => (
                <button
                  key={tier}
                  onClick={() => setSelectedTier(tier)}
                  className={`px-3 py-1.5 rounded-lg text-xs font-semibold whitespace-nowrap transition-colors ${
                    selectedTier === tier
                      ? 'bg-forest text-white'
                      : 'bg-white border border-sand-300 text-espresso-700 hover:bg-sand-100'
                  }`}
                >
                  {tier}
                </button>
              ))}
            </div>
          </div>

          {/* Broker Directory Cards */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {filteredBrokers.map(broker => {
              const pendingBrokerVouchers = vouchers.filter(v => v.brokerId === broker.id && v.status !== 'PAID');
              const pendingAmt = pendingBrokerVouchers.reduce((s, v) => s + v.netPayable, 0);

              return (
                <div key={broker.id} className="p-5 rounded-xl bg-white border border-sand-300 shadow-sm flex flex-col justify-between hover:border-sand-400 transition-colors">
                  <div>
                    <div className="flex items-start justify-between">
                      <div>
                        <span className={`px-2 py-0.5 rounded text-[11px] font-semibold uppercase tracking-wider ${
                          broker.tier === 'Platinum' ? 'bg-emerald-100 text-emerald-800' :
                          broker.tier === 'Gold' ? 'bg-yellow-100 text-yellow-800' :
                          broker.tier === 'Silver' ? 'bg-slate-100 text-slate-800' :
                          'bg-amber-100 text-amber-800'
                        }`}>
                          {broker.tier} Tier ({broker.commissionRate}%)
                        </span>
                        <h3 className="text-lg font-serif font-bold text-espresso-950 mt-1">
                          {broker.fullName}
                        </h3>
                        <div className="flex items-center gap-1.5 text-xs text-espresso-600 mt-0.5">
                          <Building className="w-3.5 h-3.5 text-espresso-400" />
                          <span className="font-medium">{broker.firmName}</span>
                        </div>
                      </div>
                      <div className="text-right">
                        <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-forest bg-forest/10 px-2 py-0.5 rounded">
                          <ShieldCheck className="w-3 h-3" /> RERA
                        </span>
                      </div>
                    </div>

                    <div className="mt-4 pt-3 border-t border-sand-200 text-xs space-y-1.5 text-espresso-700">
                      <div className="flex items-center gap-2">
                        <Phone className="w-3.5 h-3.5 text-espresso-400" />
                        <a href={`tel:${broker.mobile}`} className="hover:text-forest font-mono">{broker.mobile}</a>
                      </div>
                      <div className="flex items-center gap-2">
                        <Mail className="w-3.5 h-3.5 text-espresso-400" />
                        <span className="truncate">{broker.email || 'No email registered'}</span>
                      </div>
                      <div className="text-espresso-500 font-mono text-[11px]">
                        RERA: {broker.reraNumber || 'Applied'}
                      </div>
                    </div>

                    <div className="mt-4 grid grid-cols-3 gap-2 py-2.5 px-3 rounded-lg bg-sand-100 text-center">
                      <div>
                        <div className="text-[11px] text-espresso-600">Deals</div>
                        <div className="text-sm font-bold text-espresso-950">{broker.dealsClosedCount}</div>
                      </div>
                      <div>
                        <div className="text-[11px] text-espresso-600">Earned</div>
                        <div className="text-sm font-bold text-forest">₹{(broker.totalCommissionEarned / 100000).toFixed(1)}L</div>
                      </div>
                      <div>
                        <div className="text-[11px] text-espresso-600">Paid</div>
                        <div className="text-sm font-bold text-espresso-950">₹{(broker.totalCommissionPaid / 100000).toFixed(1)}L</div>
                      </div>
                    </div>
                  </div>

                  <div className="mt-4 pt-3 border-t border-sand-200 flex items-center justify-between">
                    {pendingAmt > 0 ? (
                      <span className="text-xs text-amber-700 font-medium">
                        Due: ₹{pendingAmt.toLocaleString('en-IN')}
                      </span>
                    ) : (
                      <span className="text-xs text-forest flex items-center gap-1 font-medium">
                        <CheckCircle2 className="w-3.5 h-3.5" /> All Settled
                      </span>
                    )}

                    <button
                      onClick={() => {
                        const nextTier = broker.tier === 'Bronze' ? 'Silver' : broker.tier === 'Silver' ? 'Gold' : 'Platinum';
                        const rate = nextTier === 'Platinum' ? 3.0 : nextTier === 'Gold' ? 2.5 : 2.0;
                        updateBrokerTier(broker.id, nextTier, rate);
                      }}
                      className="text-xs text-forest font-semibold hover:underline"
                    >
                      Promote Tier →
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        </>
      ) : (
        /* Commission Vouchers Ledger */
        <div className="bg-white border border-sand-300 rounded-xl overflow-hidden shadow-sm">
          <div className="p-4 border-b border-sand-200 bg-sand-50/50 flex flex-col sm:flex-row sm:items-center justify-between gap-2">
            <div>
              <h3 className="font-serif font-bold text-espresso-950">
                {isHi ? 'कमीशन वाउचर ऑडिट लेजर' : 'Commission Voucher Ledger & TDS Audit'}
              </h3>
              <p className="text-xs text-espresso-600">
                Mandatory 5% TDS deduction under Sec 194H of Indian Income Tax Act.
              </p>
            </div>
            <div className="text-xs text-espresso-600 font-mono">
              Total Vouchers: {vouchers.length}
            </div>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-sand-100/75 text-xs text-espresso-700 font-semibold border-b border-sand-300">
                <tr>
                  <th className="py-3 px-4">Voucher No</th>
                  <th className="py-3 px-4">Broker Firm</th>
                  <th className="py-3 px-4">Plot / Project</th>
                  <th className="py-3 px-4 text-right">Deal Value</th>
                  <th className="py-3 px-4 text-right">Gross Comm.</th>
                  <th className="py-3 px-4 text-right">TDS (5%)</th>
                  <th className="py-3 px-4 text-right">Net Payable</th>
                  <th className="py-3 px-4 text-center">Status</th>
                  <th className="py-3 px-4 text-center">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-sand-200">
                {vouchers.map(v => {
                  const broker = brokers.find(b => b.id === v.brokerId);
                  return (
                    <tr key={v.id} className="hover:bg-sand-50/50 transition-colors">
                      <td className="py-3.5 px-4 font-mono text-xs font-medium text-espresso-900">
                        {v.voucherNo}
                        <div className="text-[11px] text-espresso-500 font-sans">{v.dealDate}</div>
                      </td>
                      <td className="py-3.5 px-4">
                        <div className="font-medium text-espresso-950">{broker?.firmName || 'Partner'}</div>
                        <div className="text-xs text-espresso-600">{broker?.fullName}</div>
                      </td>
                      <td className="py-3.5 px-4">
                        <div className="font-semibold text-espresso-900">{v.plotNumber}</div>
                        <div className="text-xs text-espresso-600 truncate max-w-[150px]">{v.projectName}</div>
                      </td>
                      <td className="py-3.5 px-4 text-right font-mono text-espresso-800">
                        ₹{v.dealValue.toLocaleString('en-IN')}
                      </td>
                      <td className="py-3.5 px-4 text-right font-mono font-medium text-espresso-900">
                        ₹{v.commissionAmount.toLocaleString('en-IN')}
                      </td>
                      <td className="py-3.5 px-4 text-right font-mono text-amber-800 text-xs">
                        -₹{v.tdsDeduction.toLocaleString('en-IN')}
                      </td>
                      <td className="py-3.5 px-4 text-right font-mono font-bold text-forest">
                        ₹{v.netPayable.toLocaleString('en-IN')}
                      </td>
                      <td className="py-3.5 px-4 text-center">
                        <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                          v.status === 'PAID'
                            ? 'bg-emerald-100 text-emerald-800'
                            : v.status === 'APPROVED'
                            ? 'bg-blue-100 text-blue-800'
                            : 'bg-amber-100 text-amber-800'
                        }`}>
                          {v.status}
                        </span>
                        {v.paymentRef && (
                          <div className="text-[10px] font-mono text-espresso-500 mt-0.5 truncate max-w-[120px] mx-auto">
                            {v.paymentRef}
                          </div>
                        )}
                      </td>
                      <td className="py-3.5 px-4 text-center">
                        {v.status !== 'PAID' ? (
                          <button
                            onClick={() => setSettleVoucher(v)}
                            className="px-3 py-1 rounded bg-forest hover:bg-forest-600 text-white text-xs font-medium transition-colors"
                          >
                            Disburse
                          </button>
                        ) : (
                          <span className="text-xs text-forest flex items-center justify-center gap-1">
                            <CheckCircle2 className="w-3.5 h-3.5" /> Disbursed
                          </span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Settle Voucher Modal */}
      {settleVoucher && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-espresso-950/60 backdrop-blur-sm p-4">
          <div className="w-full max-w-md bg-white rounded-2xl shadow-2xl border border-sand-300 overflow-hidden">
            <div className="p-5 bg-[#0B1411] text-white flex items-center justify-between">
              <div>
                <span className="text-xs text-emerald-400 font-mono uppercase tracking-wider">Settlement Authorization</span>
                <h3 className="text-lg font-serif font-bold mt-0.5">Release Broker Commission</h3>
              </div>
              <button 
                onClick={() => setSettleVoucher(null)}
                className="p-1 rounded-lg text-white/70 hover:text-white hover:bg-white/10"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleSettle} className="p-6 space-y-4">
              <div className="p-3 bg-sand-100 rounded-xl space-y-1.5 text-xs text-espresso-800">
                <div className="flex justify-between">
                  <span>Voucher No:</span>
                  <span className="font-mono font-bold">{settleVoucher.voucherNo}</span>
                </div>
                <div className="flex justify-between">
                  <span>Unit:</span>
                  <span className="font-semibold">{settleVoucher.plotNumber} ({settleVoucher.projectName})</span>
                </div>
                <div className="flex justify-between">
                  <span>Gross Commission:</span>
                  <span>₹{settleVoucher.commissionAmount.toLocaleString('en-IN')}</span>
                </div>
                <div className="flex justify-between text-amber-800">
                  <span>Less TDS (Sec 194H @ 5%):</span>
                  <span>-₹{settleVoucher.tdsDeduction.toLocaleString('en-IN')}</span>
                </div>
                <div className="flex justify-between font-bold text-sm text-forest pt-1.5 border-t border-sand-200">
                  <span>Net Disbursal Amount:</span>
                  <span>₹{settleVoucher.netPayable.toLocaleString('en-IN')}</span>
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-espresso-700 mb-1">
                  Bank Transfer / RTGS / UTR Reference No. *
                </label>
                <input
                  type="text"
                  required
                  placeholder="e.g. UTR-HDFC009988123"
                  value={utrRef}
                  onChange={e => setUtrRef(e.target.value)}
                  className="w-full px-3 py-2 text-sm bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 focus:border-forest font-mono"
                />
              </div>

              <div className="flex items-center gap-3 pt-3">
                <button
                  type="button"
                  onClick={() => setSettleVoucher(null)}
                  className="w-1/2 py-2.5 rounded-xl border border-sand-300 text-espresso-700 text-sm font-medium hover:bg-sand-100 transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="w-1/2 py-2.5 rounded-xl bg-forest hover:bg-forest-600 text-white text-sm font-semibold shadow-sm transition-colors"
                >
                  Confirm & Settle
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Onboard Broker Modal */}
      {isAddModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-espresso-950/60 backdrop-blur-sm p-4">
          <div className="w-full max-w-lg bg-white rounded-2xl shadow-2xl border border-sand-300 overflow-hidden">
            <div className="p-5 bg-[#0B1411] text-white flex items-center justify-between">
              <div>
                <span className="text-xs text-emerald-400 font-mono uppercase tracking-wider">Onboarding Form</span>
                <h3 className="text-lg font-serif font-bold mt-0.5">Register Channel Partner / Broker</h3>
              </div>
              <button 
                onClick={() => setIsAddModalOpen(false)}
                className="p-1 rounded-lg text-white/70 hover:text-white hover:bg-white/10"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleCreateBroker} className="p-6 space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-espresso-700 mb-1">Broker / Principal Name *</label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. Vikram Malhotra"
                    value={newBroker.fullName}
                    onChange={e => setNewBroker(p => ({ ...p, fullName: e.target.value }))}
                    className="w-full px-3 py-2 text-sm bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 focus:border-forest"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-espresso-700 mb-1">Firm / Agency Name *</label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. Diamond Realty Syndicate"
                    value={newBroker.firmName}
                    onChange={e => setNewBroker(p => ({ ...p, firmName: e.target.value }))}
                    className="w-full px-3 py-2 text-sm bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 focus:border-forest"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-espresso-700 mb-1">Mobile Number *</label>
                  <input
                    type="tel"
                    required
                    placeholder="+91 98111 22334"
                    value={newBroker.mobile}
                    onChange={e => setNewBroker(p => ({ ...p, mobile: e.target.value }))}
                    className="w-full px-3 py-2 text-sm bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 focus:border-forest font-mono"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-espresso-700 mb-1">Email Address</label>
                  <input
                    type="email"
                    placeholder="partner@realty.com"
                    value={newBroker.email}
                    onChange={e => setNewBroker(p => ({ ...p, email: e.target.value }))}
                    className="w-full px-3 py-2 text-sm bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 focus:border-forest"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-espresso-700 mb-1">RERA Registration No.</label>
                  <input
                    type="text"
                    placeholder="e.g. UPRERA/A/2024/9912"
                    value={newBroker.reraNumber}
                    onChange={e => setNewBroker(p => ({ ...p, reraNumber: e.target.value }))}
                    className="w-full px-3 py-2 text-sm bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 focus:border-forest font-mono"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-espresso-700 mb-1">Operating Territory / City</label>
                  <input
                    type="text"
                    placeholder="e.g. Dehradun Road, Saharanpur"
                    value={newBroker.cityArea}
                    onChange={e => setNewBroker(p => ({ ...p, cityArea: e.target.value }))}
                    className="w-full px-3 py-2 text-sm bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 focus:border-forest"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-espresso-700 mb-1">Initial Commission Tier</label>
                  <select
                    value={newBroker.tier}
                    onChange={e => {
                      const tier = e.target.value as BrokerPartner['tier'];
                      const rate = tier === 'Platinum' ? 3.0 : tier === 'Gold' ? 2.5 : tier === 'Silver' ? 2.0 : 1.5;
                      setNewBroker(p => ({ ...p, tier, commissionRate: rate }));
                    }}
                    className="w-full px-3 py-2 text-sm bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 focus:border-forest"
                  >
                    <option value="Bronze">Bronze (1.5%)</option>
                    <option value="Silver">Silver (2.0%)</option>
                    <option value="Gold">Gold (2.5%)</option>
                    <option value="Platinum">Platinum (3.0%)</option>
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-espresso-700 mb-1">Commission Rate (%)</label>
                  <input
                    type="number"
                    step="0.1"
                    value={newBroker.commissionRate}
                    onChange={e => setNewBroker(p => ({ ...p, commissionRate: parseFloat(e.target.value) || 0 }))}
                    className="w-full px-3 py-2 text-sm bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 focus:border-forest font-mono"
                  />
                </div>
              </div>

              <div className="flex items-center gap-3 pt-3">
                <button
                  type="button"
                  onClick={() => setIsAddModalOpen(false)}
                  className="w-1/2 py-2.5 rounded-xl border border-sand-300 text-espresso-700 text-sm font-medium hover:bg-sand-100 transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="w-1/2 py-2.5 rounded-xl bg-forest hover:bg-forest-600 text-white text-sm font-semibold shadow-sm transition-colors"
                >
                  Onboard Broker
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
