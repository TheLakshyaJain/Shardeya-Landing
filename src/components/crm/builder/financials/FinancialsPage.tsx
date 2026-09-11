import React, { useState } from 'react';
import { 
  Wallet, IndianRupee, AlertTriangle, CheckCircle2, 
  MessageSquare, Plus, FileText, Download, Clock,
  ArrowRight, ShieldCheck, Printer, Search
} from 'lucide-react';
import { useCrm } from '../../../../context/CrmContext';
import { RecordPaymentModal } from './RecordPaymentModal';

export const FinancialsPage: React.FC = () => {
  const { 
    plotSales, paymentSchedules, paymentRecords, 
    plots, activeProject, recordPayment 
  } = useCrm();

  const [activeTab, setActiveTab] = useState<'OVERDUE' | 'RECEIPTS' | 'SCHEDULES'>('OVERDUE');
  const [isRecordPaymentOpen, setIsRecordPaymentOpen] = useState(false);
  const [selectedSaleForPayment, setSelectedSaleForPayment] = useState<string>('');
  const [searchQuery, setSearchQuery] = useState('');
  const [waReminderToast, setWaReminderToast] = useState<string | null>(null);

  // Financial aggregates
  const totalDealValue = plotSales.reduce((acc, curr) => acc + curr.dealValue, 0);
  const totalCollections = paymentRecords.reduce((acc, curr) => acc + curr.amount, 0);
  const totalPendingBalance = Math.max(0, totalDealValue - totalCollections);

  const overdueSchedules = paymentSchedules.filter((s) => s.status === 'OVERDUE');
  const overdueAmount = overdueSchedules.reduce((acc, curr) => acc + (curr.expectedAmount - curr.amountAllocated), 0);

  const handleSendReminder = (buyerName: string, plotNo: string, amount: number) => {
    setWaReminderToast(`Milestone demand reminder dispatched to ${buyerName} for ${plotNo} (₹${amount.toLocaleString('en-IN')}) via WhatsApp.`);
    setTimeout(() => setWaReminderToast(null), 4000);
  };

  return (
    <div className="space-y-6 text-left animate-fadeIn">
      
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-sand-300">
        <div>
          <h1 className="font-serif font-bold text-2xl sm:text-3xl text-espresso-950">
            Financials, Milestones & Collections
          </h1>
          <p className="text-xs text-espresso-600 mt-1">
            Track bank collections, milestone demand notices, overdue aging schedules, and RERA escrow realization.
          </p>
        </div>

        <button
          onClick={() => {
            setSelectedSaleForPayment('');
            setIsRecordPaymentOpen(true);
          }}
          className="px-4 py-2.5 rounded-xl bg-forest hover:bg-forest-light text-white text-xs font-sans font-bold shadow-warm-sm transition-all flex items-center gap-1.5 self-start sm:self-auto"
        >
          <Plus className="w-4 h-4" />
          <span>Record Bank Payment</span>
        </button>
      </div>

      {/* Financial Overview Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="p-5 rounded-2xl bg-white border border-sand-300 shadow-warm-sm">
          <div className="flex items-center justify-between text-xs text-espresso-500 mb-2">
            <span>Total Sales Value</span>
            <IndianRupee className="w-4 h-4 text-forest" />
          </div>
          <div className="font-serif font-bold text-2xl text-espresso-950">
            ₹{(totalDealValue / 10000000).toFixed(2)} Cr
          </div>
          <div className="text-[11px] text-espresso-600 mt-1">
            Across {plotSales.length} executed unit allotments
          </div>
        </div>

        <div className="p-5 rounded-2xl bg-white border border-sand-300 shadow-warm-sm">
          <div className="flex items-center justify-between text-xs text-emerald-800 mb-2">
            <span>Total Realized in Escrow</span>
            <CheckCircle2 className="w-4 h-4 text-forest" />
          </div>
          <div className="font-serif font-bold text-2xl text-emerald-700">
            ₹{(totalCollections / 10000000).toFixed(2)} Cr
          </div>
          <div className="text-[11px] text-emerald-800 mt-1 font-semibold">
            {Math.round((totalCollections / (totalDealValue || 1)) * 100)}% Collected & Cleared
          </div>
        </div>

        <div className="p-5 rounded-2xl bg-white border border-sand-300 shadow-warm-sm">
          <div className="flex items-center justify-between text-xs text-espresso-500 mb-2">
            <span>Pending Receivables</span>
            <Clock className="w-4 h-4 text-amber-600" />
          </div>
          <div className="font-serif font-bold text-2xl text-espresso-950">
            ₹{(totalPendingBalance / 10000000).toFixed(2)} Cr
          </div>
          <div className="text-[11px] text-espresso-600 mt-1">
            Tied to future construction stages
          </div>
        </div>

        <div className="p-5 rounded-2xl bg-white border border-sand-300 shadow-warm-sm">
          <div className="flex items-center justify-between text-xs text-rose-800 mb-2">
            <span>Milestone Demands Overdue</span>
            <AlertTriangle className="w-4 h-4 text-rose-600" />
          </div>
          <div className="font-serif font-bold text-2xl text-rose-700">
            ₹{(overdueAmount / 100000).toFixed(1)}L
          </div>
          <div className="text-[11px] text-rose-800 mt-1 font-semibold">
            {overdueSchedules.length} demands past due date
          </div>
        </div>
      </div>

      {waReminderToast && (
        <div className="p-3.5 rounded-2xl bg-emerald-50 border border-emerald-200 text-emerald-900 text-xs flex items-center gap-2 animate-fadeIn shadow-warm-sm">
          <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
          <span>{waReminderToast}</span>
        </div>
      )}

      {/* Navigation Tabs */}
      <div className="bg-white rounded-2xl border border-sand-300 shadow-warm-sm overflow-hidden">
        <div className="p-3 bg-sand-50 border-b border-sand-200 flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-1.5 p-1 bg-sand-200/70 rounded-xl text-xs font-semibold">
            <button
              onClick={() => setActiveTab('OVERDUE')}
              className={`px-3.5 py-1.5 rounded-lg transition-all ${
                activeTab === 'OVERDUE' ? 'bg-white shadow-sm font-bold text-espresso-950' : 'text-espresso-600'
              }`}
            >
              Overdue Collection Tracker ({overdueSchedules.length})
            </button>
            <button
              onClick={() => setActiveTab('RECEIPTS')}
              className={`px-3.5 py-1.5 rounded-lg transition-all ${
                activeTab === 'RECEIPTS' ? 'bg-white shadow-sm font-bold text-espresso-950' : 'text-espresso-600'
              }`}
            >
              Verified Receipts Ledger ({paymentRecords.length})
            </button>
            <button
              onClick={() => setActiveTab('SCHEDULES')}
              className={`px-3.5 py-1.5 rounded-lg transition-all ${
                activeTab === 'SCHEDULES' ? 'bg-white shadow-sm font-bold text-espresso-950' : 'text-espresso-600'
              }`}
            >
              All Payment Schedules ({paymentSchedules.length})
            </button>
          </div>
        </div>

        {/* TAB 1: OVERDUE COLLECTION TRACKER */}
        {activeTab === 'OVERDUE' && (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs font-sans">
              <thead className="bg-sand-50 border-b border-sand-200 text-espresso-600 uppercase font-bold text-[10px]">
                <tr>
                  <th className="py-3.5 px-4">Plot Allotment</th>
                  <th className="py-3.5 px-4">Buyer Details</th>
                  <th className="py-3.5 px-4">Milestone Stage</th>
                  <th className="py-3.5 px-4">Due Date</th>
                  <th className="py-3.5 px-4">Overdue Days</th>
                  <th className="py-3.5 px-4">Outstanding (₹)</th>
                  <th className="py-3.5 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-sand-200">
                {overdueSchedules.map((sch) => {
                  const sale = plotSales.find((s) => s.id === sch.plotSaleId);
                  const plot = plots.find((p) => p.id === sale?.plotId);
                  const remaining = sch.expectedAmount - sch.amountAllocated;

                  return (
                    <tr key={sch.id} className="hover:bg-rose-50/30 transition-colors">
                      <td className="py-3 px-4 font-mono font-bold text-forest">
                        {plot?.plotNumber || 'Unit'}
                      </td>
                      <td className="py-3 px-4">
                        <div className="font-bold text-espresso-950">{sale?.buyerName}</div>
                        <div className="font-mono text-[11px] text-espresso-500">{sale?.buyerMobile}</div>
                      </td>
                      <td className="py-3 px-4 font-medium text-espresso-800">{sch.label}</td>
                      <td className="py-3 px-4 font-mono text-espresso-600">{sch.dueDate}</td>
                      <td className="py-3 px-4 font-mono text-rose-700 font-bold">
                        {sch.daysOverdue} Days Late
                      </td>
                      <td className="py-3 px-4 font-mono font-bold text-rose-700 text-sm">
                        ₹{remaining.toLocaleString('en-IN')}
                      </td>
                      <td className="py-3 px-4 text-right space-x-2">
                        <button
                          onClick={() => handleSendReminder(sale?.buyerName || 'Buyer', plot?.plotNumber || 'Unit', remaining)}
                          className="px-2.5 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white font-bold text-[11px] inline-flex items-center gap-1 shadow-sm"
                        >
                          <MessageSquare className="w-3.5 h-3.5" />
                          <span>WhatsApp Notice</span>
                        </button>
                        <button
                          onClick={() => {
                            setSelectedSaleForPayment(sale?.id || '');
                            setIsRecordPaymentOpen(true);
                          }}
                          className="px-2.5 py-1.5 rounded-lg bg-forest hover:bg-forest-light text-white font-bold text-[11px] inline-flex items-center gap-1 shadow-sm"
                        >
                          <span>Record Receipt</span>
                        </button>
                      </td>
                    </tr>
                  );
                })}

                {overdueSchedules.length === 0 && (
                  <tr>
                    <td colSpan={7} className="py-8 text-center text-espresso-500">
                      No overdue demands. All buyers are current on their milestone schedule.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}

        {/* TAB 2: VERIFIED RECEIPTS LEDGER */}
        {activeTab === 'RECEIPTS' && (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs font-sans">
              <thead className="bg-sand-50 border-b border-sand-200 text-espresso-600 uppercase font-bold text-[10px]">
                <tr>
                  <th className="py-3.5 px-4">Receipt Number</th>
                  <th className="py-3.5 px-4">Buyer & Unit</th>
                  <th className="py-3.5 px-4">Amount Credited</th>
                  <th className="py-3.5 px-4">Payment Mode</th>
                  <th className="py-3.5 px-4">Bank Reference / UTR</th>
                  <th className="py-3.5 px-4">Deposit Date</th>
                  <th className="py-3.5 px-4">Authorized By</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-sand-200">
                {paymentRecords.map((pay) => {
                  const sale = plotSales.find((s) => s.id === pay.plotSaleId);
                  const plot = plots.find((p) => p.id === sale?.plotId);

                  return (
                    <tr key={pay.id} className="hover:bg-sand-50 transition-colors">
                      <td className="py-3 px-4 font-mono font-bold text-espresso-950 flex items-center gap-1.5">
                        <FileText className="w-3.5 h-3.5 text-forest" />
                        <span>{pay.receiptNo}</span>
                      </td>
                      <td className="py-3 px-4">
                        <div className="font-bold text-espresso-950">{sale?.buyerName}</div>
                        <div className="font-mono text-[10px] text-forest">{plot?.plotNumber}</div>
                      </td>
                      <td className="py-3 px-4 font-mono font-bold text-emerald-700 text-sm">
                        ₹{(pay.amount).toLocaleString('en-IN')}
                      </td>
                      <td className="py-3 px-4">
                        <span className="px-2 py-0.5 rounded bg-sand-100 font-mono text-[10px] font-bold text-espresso-800 border border-sand-300">
                          {pay.mode}
                        </span>
                      </td>
                      <td className="py-3 px-4 font-mono text-espresso-600">{pay.reference}</td>
                      <td className="py-3 px-4 font-mono text-espresso-800">{pay.paidOn}</td>
                      <td className="py-3 px-4 text-espresso-700">{pay.receivedBy}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {/* TAB 3: ALL PAYMENT SCHEDULES */}
        {activeTab === 'SCHEDULES' && (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs font-sans">
              <thead className="bg-sand-50 border-b border-sand-200 text-espresso-600 uppercase font-bold text-[10px]">
                <tr>
                  <th className="py-3.5 px-4">Unit Allotment</th>
                  <th className="py-3.5 px-4">Milestone Description</th>
                  <th className="py-3.5 px-4">Expected Value</th>
                  <th className="py-3.5 px-4">Amount Allocated</th>
                  <th className="py-3.5 px-4">Due Date</th>
                  <th className="py-3.5 px-4">Milestone Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-sand-200">
                {paymentSchedules.map((sch) => {
                  const sale = plotSales.find((s) => s.id === sch.plotSaleId);
                  const plot = plots.find((p) => p.id === sale?.plotId);

                  return (
                    <tr key={sch.id} className="hover:bg-sand-50 transition-colors">
                      <td className="py-3 px-4">
                        <div className="font-mono font-bold text-forest">{plot?.plotNumber}</div>
                        <div className="text-[10px] text-espresso-500">{sale?.buyerName}</div>
                      </td>
                      <td className="py-3 px-4 font-semibold text-espresso-900">{sch.label}</td>
                      <td className="py-3 px-4 font-mono font-bold">₹{sch.expectedAmount.toLocaleString('en-IN')}</td>
                      <td className="py-3 px-4 font-mono text-emerald-700">₹{sch.amountAllocated.toLocaleString('en-IN')}</td>
                      <td className="py-3 px-4 font-mono text-espresso-600">{sch.dueDate}</td>
                      <td className="py-3 px-4">
                        <span className={`px-2 py-0.5 rounded text-[10px] font-mono font-bold ${
                          sch.status === 'PAID' ? 'bg-emerald-100 text-emerald-800' :
                          sch.status === 'OVERDUE' ? 'bg-rose-100 text-rose-800' : 'bg-sand-200 text-espresso-800'
                        }`}>
                          {sch.status}
                        </span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

      </div>

      {/* Record Payment Modal */}
      {isRecordPaymentOpen && (
        <RecordPaymentModal
          initialPlotSaleId={selectedSaleForPayment}
          onClose={() => setIsRecordPaymentOpen(false)}
          onSuccess={() => setIsRecordPaymentOpen(false)}
        />
      )}

    </div>
  );
};
