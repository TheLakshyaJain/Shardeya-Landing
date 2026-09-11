import React, { useState } from 'react';
import { X, Wallet, CheckCircle2, IndianRupee, RefreshCw } from 'lucide-react';
import { useCrm } from '../../../../context/CrmContext';

interface RecordPaymentModalProps {
  initialPlotSaleId?: string;
  onClose: () => void;
  onSuccess: () => void;
}

export const RecordPaymentModal: React.FC<RecordPaymentModalProps> = ({
  initialPlotSaleId = '',
  onClose,
  onSuccess,
}) => {
  const { plotSales, plots, recordPayment } = useCrm();

  const [plotSaleId, setPlotSaleId] = useState<string>(initialPlotSaleId || plotSales[0]?.id || '');
  const [amount, setAmount] = useState<number>(500000);
  const [paidOn, setPaidOn] = useState(new Date().toISOString().split('T')[0]);
  const [mode, setMode] = useState<'CASH' | 'CHEQUE' | 'BANK_TRANSFER' | 'UPI'>('BANK_TRANSFER');
  const [reference, setReference] = useState('UTR-HDFC' + Math.floor(10000000 + Math.random() * 90000000));
  const [receivedBy, setReceivedBy] = useState('Chief Operations Director');
  const [remarks, setRemarks] = useState('Milestone bank transfer verified in RERA Escrow account');
  const [isLoading, setIsLoading] = useState(false);

  const selectedSale = plotSales.find((s) => s.id === plotSaleId);
  const selectedPlot = plots.find((p) => p.id === selectedSale?.plotId);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!plotSaleId || amount <= 0) return;

    setIsLoading(true);
    setTimeout(() => {
      recordPayment({
        plotSaleId,
        amount: Number(amount),
        paidOn,
        mode,
        reference,
        receivedBy,
        remarks: remarks.trim() || undefined,
      });
      setIsLoading(false);
      onSuccess();
    }, 350);
  };

  return (
    <div className="fixed inset-0 z-50 bg-espresso-950/60 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl border border-sand-300 max-w-lg w-full p-6 sm:p-8 shadow-2xl animate-fadeIn text-left">
        
        <div className="flex items-center justify-between pb-4 border-b border-sand-200">
          <div>
            <span className="text-[10px] font-mono text-forest uppercase tracking-wider font-bold">
              Cash & Bank Realization
            </span>
            <h2 className="font-serif font-bold text-2xl text-espresso-950">
              Record Buyer Payment
            </h2>
          </div>

          <button
            onClick={onClose}
            className="p-1.5 rounded-xl hover:bg-sand-100 text-espresso-500"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="mt-5 space-y-4 text-xs font-sans">
          <div>
            <label className="block font-semibold text-espresso-800 mb-1">
              Select Unit Allotment / Buyer *
            </label>
            <select
              value={plotSaleId}
              onChange={(e) => setPlotSaleId(e.target.value)}
              className="w-full px-3.5 py-2.5 rounded-xl border border-sand-300 bg-sand-50 font-sans text-xs font-bold text-espresso-950 outline-none"
            >
              {plotSales.map((s) => {
                const p = plots.find((plt) => plt.id === s.plotId);
                return (
                  <option key={s.id} value={s.id}>
                    {p?.plotNumber} — {s.buyerName} (Balance Due: ₹{s.balanceDue.toLocaleString('en-IN')})
                  </option>
                );
              })}
            </select>
          </div>

          {selectedSale && (
            <div className="p-3 rounded-xl bg-sand-50 border border-sand-200 flex justify-between items-center text-xs">
              <div>
                <span className="text-espresso-500">Agreed Deal Value:</span>
                <div className="font-bold text-espresso-900 font-mono">₹{selectedSale.dealValue.toLocaleString('en-IN')}</div>
              </div>
              <div>
                <span className="text-espresso-500">Total Paid:</span>
                <div className="font-bold text-emerald-700 font-mono">₹{selectedSale.totalPaid.toLocaleString('en-IN')}</div>
              </div>
              <div>
                <span className="text-espresso-500">Current Balance:</span>
                <div className="font-bold text-rose-700 font-mono">₹{selectedSale.balanceDue.toLocaleString('en-IN')}</div>
              </div>
            </div>
          )}

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block font-semibold text-espresso-800 mb-1">
                Deposit Amount (₹) *
              </label>
              <input
                type="number"
                required
                min={1}
                value={amount}
                onChange={(e) => setAmount(Number(e.target.value))}
                className="w-full px-3.5 py-2 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white text-xs font-mono font-bold text-emerald-800 outline-none"
              />
            </div>

            <div>
              <label className="block font-semibold text-espresso-800 mb-1">
                Date of Credit *
              </label>
              <input
                type="date"
                required
                value={paidOn}
                onChange={(e) => setPaidOn(e.target.value)}
                className="w-full px-3.5 py-2 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white text-xs font-mono outline-none"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block font-semibold text-espresso-800 mb-1">
                Payment Channel
              </label>
              <select
                value={mode}
                onChange={(e) => setMode(e.target.value as any)}
                className="w-full px-3 py-2 rounded-xl border border-sand-300 bg-sand-50 text-xs outline-none"
              >
                <option value="BANK_TRANSFER">Bank NEFT / RTGS</option>
                <option value="CHEQUE">Cheque Deposit / DD</option>
                <option value="UPI">UPI Transfer</option>
                <option value="CASH">Cash Office Collection</option>
              </select>
            </div>

            <div>
              <label className="block font-semibold text-espresso-800 mb-1">
                Bank Reference / UTR Number *
              </label>
              <input
                type="text"
                required
                placeholder="UTR-HDFC00..."
                value={reference}
                onChange={(e) => setReference(e.target.value)}
                className="w-full px-3.5 py-2 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white text-xs font-mono outline-none"
              />
            </div>
          </div>

          <div>
            <label className="block font-semibold text-espresso-800 mb-1">
              Internal Verification Remarks
            </label>
            <textarea
              rows={2}
              value={remarks}
              onChange={(e) => setRemarks(e.target.value)}
              className="w-full p-2.5 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white text-xs outline-none"
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
              disabled={isLoading}
              className="px-5 py-2 rounded-xl bg-forest hover:bg-forest-light text-white font-bold uppercase tracking-wider shadow-warm-sm flex items-center gap-1.5"
            >
              {isLoading ? <RefreshCw className="w-3.5 h-3.5 animate-spin" /> : 'Confirm & Issue Receipt'}
            </button>
          </div>
        </form>

      </div>
    </div>
  );
};
