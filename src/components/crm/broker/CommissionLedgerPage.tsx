import React, { useState } from 'react';
import { 
  Wallet, CheckCircle2, Clock, Download, 
  FileText, ArrowDownRight, ShieldCheck, Printer,
  Filter
} from 'lucide-react';
import { useCrm } from '../../../context/CrmContext';
import { useAuth } from '../../../context/AuthContext';
import { useLanguage } from '../../../context/LanguageContext';

export const CommissionLedgerPage: React.FC = () => {
  const { vouchers, brokers } = useCrm();
  const { user } = useAuth();
  const { language } = useLanguage();
  const isHi = language === 'hi';

  const currentBroker = brokers.find(b => b.email === user?.email) || brokers[0];
  const brokerVouchers = vouchers.filter(v => v.brokerId === currentBroker?.id);

  const [statusFilter, setStatusFilter] = useState('ALL');

  const filteredVouchers = brokerVouchers.filter(v => {
    if (statusFilter !== 'ALL' && v.status !== statusFilter) return false;
    return true;
  });

  const totalGross = brokerVouchers.reduce((s, v) => s + v.commissionAmount, 0);
  const totalTds = brokerVouchers.reduce((s, v) => s + v.tdsDeduction, 0);
  const totalNet = brokerVouchers.reduce((s, v) => s + v.netPayable, 0);
  const totalDisbursed = brokerVouchers.filter(v => v.status === 'PAID').reduce((s, v) => s + v.netPayable, 0);

  const handlePrint = () => {
    window.print();
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-forest/10 text-forest border border-forest/20 tracking-wide uppercase">
              {isHi ? 'ब्रोकरेज लेजर' : 'Commission Account'}
            </span>
          </div>
          <h1 className="text-2xl sm:text-3xl font-serif font-bold text-espresso-950 mt-1">
            {isHi ? 'कमीशन वाउचर व टीडीएस लेजर' : 'Commission Statement & TDS Ledger'}
          </h1>
          <p className="text-sm text-espresso-700">
            {isHi 
              ? 'आधिकारिक वाउचर संख्या, बैंक UTR और धारा 194H के तहत 5% टीडीएस कटौती विवरण।' 
              : 'Official commission receipts, bank UTR verification, and Form 26AS compatible TDS deductions.'}
          </p>
        </div>

        <button
          onClick={handlePrint}
          className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-forest hover:bg-forest-600 text-white font-medium shadow-sm transition-colors text-sm self-start sm:self-auto"
        >
          <Printer className="w-4 h-4" />
          {isHi ? 'स्टेटमेंट प्रिंट करें' : 'Print Statement'}
        </button>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="p-4 rounded-xl bg-white border border-sand-300 shadow-sm">
          <div className="text-xs font-medium text-espresso-600 uppercase tracking-wider">
            Gross Brokerage
          </div>
          <div className="text-2xl font-bold font-serif text-espresso-950 mt-1">
            ₹{(totalGross / 100000).toFixed(2)} L
          </div>
          <div className="text-xs text-espresso-500 mt-1">
            From {brokerVouchers.length} closed units
          </div>
        </div>

        <div className="p-4 rounded-xl bg-white border border-sand-300 shadow-sm">
          <div className="text-xs font-medium text-amber-900 uppercase tracking-wider">
            TDS Deducted (5%)
          </div>
          <div className="text-2xl font-bold font-serif text-amber-900 mt-1">
            ₹{(totalTds / 1000).toFixed(1)} K
          </div>
          <div className="text-xs text-espresso-500 mt-1">
            Sec 194H IT Compliance
          </div>
        </div>

        <div className="p-4 rounded-xl bg-white border border-sand-300 shadow-sm">
          <div className="text-xs font-medium text-forest uppercase tracking-wider">
            Net Paid Out
          </div>
          <div className="text-2xl font-bold font-serif text-forest mt-1">
            ₹{(totalDisbursed / 100000).toFixed(2)} L
          </div>
          <div className="text-xs text-forest mt-1 flex items-center gap-1 font-medium">
            <CheckCircle2 className="w-3.5 h-3.5" /> Credited to Bank
          </div>
        </div>

        <div className="p-4 rounded-xl bg-white border border-sand-300 shadow-sm">
          <div className="text-xs font-medium text-blue-900 uppercase tracking-wider">
            Under Clearance
          </div>
          <div className="text-2xl font-bold font-serif text-blue-900 mt-1">
            ₹{((totalNet - totalDisbursed) / 1000).toFixed(1)} K
          </div>
          <div className="text-xs text-espresso-500 mt-1">
            Scheduled next cycle
          </div>
        </div>
      </div>

      {/* Filter Tabs */}
      <div className="flex items-center gap-2 border-b border-sand-300 pb-3">
        {['ALL', 'PAID', 'APPROVED', 'PENDING'].map(st => (
          <button
            key={st}
            onClick={() => setStatusFilter(st)}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-colors ${
              statusFilter === st
                ? 'bg-forest text-white'
                : 'bg-white border border-sand-300 text-espresso-700 hover:bg-sand-100'
            }`}
          >
            {st} ({st === 'ALL' ? brokerVouchers.length : brokerVouchers.filter(v => v.status === st).length})
          </button>
        ))}
      </div>

      {/* Vouchers Table */}
      <div className="bg-white border border-sand-300 rounded-xl overflow-hidden shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-sand-100/75 text-xs text-espresso-700 font-semibold border-b border-sand-300">
              <tr>
                <th className="py-3 px-4">Voucher No</th>
                <th className="py-3 px-4">Unit / Township</th>
                <th className="py-3 px-4 text-right">Deal Value</th>
                <th className="py-3 px-4 text-right">Gross Comm.</th>
                <th className="py-3 px-4 text-right">TDS (5%)</th>
                <th className="py-3 px-4 text-right">Net Payable</th>
                <th className="py-3 px-4 text-center">Status</th>
                <th className="py-3 px-4">Bank Ref / UTR</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-sand-200">
              {filteredVouchers.map(v => (
                <tr key={v.id} className="hover:bg-sand-50/50 transition-colors">
                  <td className="py-3.5 px-4 font-mono text-xs font-semibold text-espresso-950">
                    {v.voucherNo}
                    <div className="text-[11px] text-espresso-500 font-sans font-normal">{v.dealDate}</div>
                  </td>
                  <td className="py-3.5 px-4">
                    <div className="font-semibold text-espresso-900">{v.plotNumber}</div>
                    <div className="text-xs text-espresso-600 truncate max-w-[160px]">{v.projectName}</div>
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
                  </td>
                  <td className="py-3.5 px-4 font-mono text-xs text-espresso-700">
                    {v.paymentRef || <span className="text-espresso-400 italic">Processing</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
