import React, { useState } from 'react';
import { 
  X, CheckCircle2, User, Phone, Mail, 
  FileText, IndianRupee, Handshake, CreditCard,
  Building2, ShieldCheck, RefreshCw 
} from 'lucide-react';
import confetti from 'canvas-confetti';
import { Plot } from '../../../../types/crm';
import { useCrm } from '../../../../context/CrmContext';

interface BookingModalProps {
  plot: Plot;
  onClose: () => void;
  onSuccess: () => void;
}

export const BookingModal: React.FC<BookingModalProps> = ({
  plot,
  onClose,
  onSuccess,
}) => {
  const { bookPlotSale, brokers } = useCrm();

  const [buyerName, setBuyerName] = useState('');
  const [buyerMobile, setBuyerMobile] = useState('');
  const [buyerEmail, setBuyerEmail] = useState('');
  const [govIdType, setGovIdType] = useState<'AADHAAR' | 'PAN'>('PAN');
  const [govIdLast4, setGovIdLast4] = useState('');
  const [dealValue, setDealValue] = useState(plot.price.toString());
  const [paymentType, setPaymentType] = useState<'LUMP_SUM' | 'INSTALMENT'>('INSTALMENT');
  const [selectedBroker, setSelectedBroker] = useState<string>('');
  const [bookingAmount, setBookingAmount] = useState((Math.round(plot.price * 0.1)).toString());
  const [paymentMode, setPaymentMode] = useState<'CASH' | 'CHEQUE' | 'BANK_TRANSFER' | 'UPI'>('BANK_TRANSFER');
  const [paymentRef, setPaymentRef] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);

    setTimeout(() => {
      bookPlotSale({
        plotId: plot.id,
        buyerName,
        buyerMobile,
        buyerEmail: buyerEmail || undefined,
        buyerGovIdType: govIdType,
        buyerGovIdLast4: govIdLast4 || '9921',
        dealValue: Number(dealValue),
        paymentType,
        brokerPartnerId: selectedBroker || undefined,
        bookingAmount: Number(bookingAmount),
        paymentMode,
        paymentRef: paymentRef || `UTR-${Math.floor(10000000 + Math.random() * 90000000)}`,
      });

      setIsLoading(false);
      try {
        confetti({ particleCount: 80, spread: 70, origin: { y: 0.6 } });
      } catch {
        // Fallback
      }
      onSuccess();
    }, 400);
  };

  return (
    <div className="fixed inset-0 z-50 bg-espresso-950/60 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl border border-sand-300 max-w-xl w-full p-6 sm:p-8 shadow-2xl animate-fadeIn text-left max-h-[90vh] overflow-y-auto">
        
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-sand-200">
          <div>
            <div className="text-[10px] font-mono uppercase tracking-wider text-forest font-bold">
              Official Unit Allotment
            </div>
            <h2 className="font-serif font-bold text-2xl text-espresso-950">
              Book {plot.plotNumber}
            </h2>
            <p className="text-xs text-espresso-600 mt-0.5">
              Area: {plot.sizeValue} {plot.sizeUnit === 'SQ_FT' ? 'sq.ft' : 'Gaj'} • Asking Rate: ₹{(plot.price).toLocaleString('en-IN')}
            </p>
          </div>

          <button
            onClick={onClose}
            className="p-1.5 rounded-xl hover:bg-sand-100 text-espresso-500"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="mt-5 space-y-4 text-xs font-sans">
          
          {/* Section 1: Buyer Information */}
          <div className="space-y-3">
            <h4 className="font-serif font-bold text-sm text-espresso-900 border-b border-sand-100 pb-1">
              1. Buyer Details & Identification
            </h4>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="block font-semibold text-espresso-800 mb-1">
                  Buyer Full Legal Name *
                </label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Dr. Harshvardhan Kapoor"
                  value={buyerName}
                  onChange={(e) => setBuyerName(e.target.value)}
                  className="w-full px-3.5 py-2 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white focus:border-forest text-xs font-sans text-espresso-950 outline-none"
                />
              </div>

              <div>
                <label className="block font-semibold text-espresso-800 mb-1">
                  Mobile Number *
                </label>
                <input
                  type="tel"
                  required
                  placeholder="+91 98101 23456"
                  value={buyerMobile}
                  onChange={(e) => setBuyerMobile(e.target.value)}
                  className="w-full px-3.5 py-2 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white focus:border-forest text-xs font-mono text-espresso-950 outline-none"
                />
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <div>
                <label className="block font-semibold text-espresso-800 mb-1">
                  ID Document Type
                </label>
                <select
                  value={govIdType}
                  onChange={(e) => setGovIdType(e.target.value as any)}
                  className="w-full px-3 py-2 rounded-xl border border-sand-300 bg-sand-50 text-xs font-sans text-espresso-900 outline-none"
                >
                  <option value="PAN">PAN Card</option>
                  <option value="AADHAAR">Aadhaar Card</option>
                </select>
              </div>

              <div>
                <label className="block font-semibold text-espresso-800 mb-1">
                  Last 4 Digits of ID *
                </label>
                <input
                  type="text"
                  required
                  maxLength={4}
                  placeholder="e.g. 8812"
                  value={govIdLast4}
                  onChange={(e) => setGovIdLast4(e.target.value)}
                  className="w-full px-3.5 py-2 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white focus:border-forest text-xs font-mono text-espresso-950 outline-none"
                />
              </div>

              <div>
                <label className="block font-semibold text-espresso-800 mb-1">
                  Email (Optional)
                </label>
                <input
                  type="email"
                  placeholder="buyer@domain.com"
                  value={buyerEmail}
                  onChange={(e) => setBuyerEmail(e.target.value)}
                  className="w-full px-3.5 py-2 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white focus:border-forest text-xs font-sans text-espresso-950 outline-none"
                />
              </div>
            </div>
          </div>

          {/* Section 2: Deal & Sourcing Broker */}
          <div className="space-y-3 pt-2">
            <h4 className="font-serif font-bold text-sm text-espresso-900 border-b border-sand-100 pb-1">
              2. Transaction Value & Sourcing Channel
            </h4>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <div>
                <label className="block font-semibold text-espresso-800 mb-1">
                  Agreed Deal Value (₹) *
                </label>
                <input
                  type="number"
                  required
                  value={dealValue}
                  onChange={(e) => setDealValue(e.target.value)}
                  className="w-full px-3.5 py-2 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white focus:border-forest text-xs font-mono font-bold text-espresso-950 outline-none"
                />
              </div>

              <div>
                <label className="block font-semibold text-espresso-800 mb-1">
                  Payment Structure
                </label>
                <select
                  value={paymentType}
                  onChange={(e) => setPaymentType(e.target.value as any)}
                  className="w-full px-3 py-2 rounded-xl border border-sand-300 bg-sand-50 text-xs font-sans text-espresso-900 outline-none font-semibold"
                >
                  <option value="INSTALMENT">Milestone Construction-Linked</option>
                  <option value="LUMP_SUM">Full Down-Payment / Lump Sum</option>
                </select>
              </div>

              <div>
                <label className="block font-semibold text-espresso-800 mb-1">
                  Channel Partner / Broker
                </label>
                <select
                  value={selectedBroker}
                  onChange={(e) => setSelectedBroker(e.target.value)}
                  className="w-full px-3 py-2 rounded-xl border border-sand-300 bg-sand-50 text-xs font-sans text-espresso-900 outline-none"
                >
                  <option value="">Direct Developer Sale (No Broker)</option>
                  {brokers.map((b) => (
                    <option key={b.id} value={b.id}>
                      {b.fullName} ({b.firmName} - {b.commissionRate}%)
                    </option>
                  ))}
                </select>
              </div>
            </div>
          </div>

          {/* Section 3: Token Receipt */}
          <div className="space-y-3 pt-2">
            <h4 className="font-serif font-bold text-sm text-espresso-900 border-b border-sand-100 pb-1">
              3. Initial Booking Token Receipt
            </h4>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <div>
                <label className="block font-semibold text-espresso-800 mb-1">
                  Token Paid Amount (₹) *
                </label>
                <input
                  type="number"
                  required
                  value={bookingAmount}
                  onChange={(e) => setBookingAmount(e.target.value)}
                  className="w-full px-3.5 py-2 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white focus:border-forest text-xs font-mono font-bold text-emerald-800 outline-none"
                />
              </div>

              <div>
                <label className="block font-semibold text-espresso-800 mb-1">
                  Payment Mode
                </label>
                <select
                  value={paymentMode}
                  onChange={(e) => setPaymentMode(e.target.value as any)}
                  className="w-full px-3 py-2 rounded-xl border border-sand-300 bg-sand-50 text-xs font-sans text-espresso-900 outline-none"
                >
                  <option value="BANK_TRANSFER">Bank NEFT / RTGS</option>
                  <option value="CHEQUE">Bank Cheque / DD</option>
                  <option value="UPI">UPI Digital Transfer</option>
                  <option value="CASH">Cash Deposit</option>
                </select>
              </div>

              <div>
                <label className="block font-semibold text-espresso-800 mb-1">
                  Bank Reference / UTR / Cheque #
                </label>
                <input
                  type="text"
                  placeholder="e.g. UTR-HDFC9921012"
                  value={paymentRef}
                  onChange={(e) => setPaymentRef(e.target.value)}
                  className="w-full px-3.5 py-2 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white focus:border-forest text-xs font-mono text-espresso-950 outline-none"
                />
              </div>
            </div>
          </div>

          {/* Submit */}
          <div className="pt-4 border-t border-sand-200 flex items-center justify-between gap-3">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2.5 rounded-xl border border-sand-300 text-espresso-700 hover:bg-sand-100 font-semibold text-xs"
            >
              Cancel
            </button>

            <button
              type="submit"
              disabled={isLoading}
              className="px-6 py-2.5 rounded-xl bg-forest hover:bg-forest-light text-white font-bold text-xs uppercase tracking-wider shadow-warm-sm transition-all flex items-center gap-2"
            >
              {isLoading ? (
                <RefreshCw className="w-4 h-4 animate-spin" />
              ) : (
                <>
                  <span>Execute Unit Allotment</span>
                  <CheckCircle2 className="w-4 h-4" />
                </>
              )}
            </button>
          </div>

        </form>

      </div>
    </div>
  );
};
