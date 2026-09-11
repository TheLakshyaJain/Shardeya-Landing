import React from 'react';
import { 
  X, Compass, Trees, CornerUpRight, Flame, 
  IndianRupee, CheckCircle2, User, Phone, 
  Calendar, FileText, ArrowRight, ShieldCheck 
} from 'lucide-react';
import { Plot } from '../../../../types/crm';
import { useCrm } from '../../../../context/CrmContext';

interface PlotDetailDrawerProps {
  plot: Plot;
  onClose: () => void;
  onOpenBooking: () => void;
}

export const PlotDetailDrawer: React.FC<PlotDetailDrawerProps> = ({
  plot,
  onClose,
  onOpenBooking,
}) => {
  const { plotSales, paymentSchedules } = useCrm();

  // Find linked sale if sold
  const sale = plotSales.find((s) => s.plotId === plot.id);
  const schedules = sale ? paymentSchedules.filter((s) => s.plotSaleId === sale.id) : [];

  return (
    <div className="fixed inset-0 z-50 bg-espresso-950/60 backdrop-blur-sm flex justify-end">
      <div className="w-full max-w-md bg-white h-full shadow-2xl flex flex-col justify-between p-6 overflow-y-auto animate-fadeIn text-left">
        
        {/* Header */}
        <div>
          <div className="flex items-center justify-between pb-4 border-b border-sand-200">
            <div>
              <span className={`inline-block px-2.5 py-0.5 rounded-full text-[10px] font-mono font-bold uppercase mb-1 ${
                plot.status === 'AVAILABLE' ? 'bg-emerald-100 text-emerald-800' :
                plot.status === 'RESERVED' ? 'bg-amber-100 text-amber-800' : 'bg-slate-200 text-slate-800'
              }`}>
                {plot.status}
              </span>
              <h2 className="font-serif font-bold text-2xl text-espresso-950">
                {plot.plotNumber}
              </h2>
            </div>

            <button
              onClick={onClose}
              className="p-1.5 rounded-xl hover:bg-sand-100 text-espresso-500"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          {/* Plot Specifications Grid */}
          <div className="mt-5 p-4 rounded-xl bg-sand-50 border border-sand-200 space-y-3 text-xs">
            <div className="flex justify-between items-center text-espresso-600">
              <span>Dimension / Area:</span>
              <span className="font-sans font-bold text-espresso-900">
                {plot.sizeValue.toLocaleString()} {plot.sizeUnit === 'SQ_FT' ? 'sq.ft' : 'Gaj'} ({Math.round(plot.sizeSqft / 9)} Gaj)
              </span>
            </div>

            <div className="flex justify-between items-center text-espresso-600">
              <span>Primary Facing:</span>
              <span className="font-mono font-bold text-forest flex items-center gap-1">
                <Compass className="w-3.5 h-3.5" />
                {plot.facing} Direction
              </span>
            </div>

            <div className="flex justify-between items-center text-espresso-600">
              <span>Base Rate per Sq.Ft:</span>
              <span className="font-mono font-bold text-espresso-900">
                ₹{plot.pricePerSqft.toLocaleString('en-IN')} / sq.ft
              </span>
            </div>

            <div className="flex justify-between items-center text-espresso-600">
              <span>Total Asking Price:</span>
              <span className="font-serif font-bold text-base text-espresso-950">
                ₹{(plot.price).toLocaleString('en-IN')}
              </span>
            </div>

            <div className="pt-2 border-t border-sand-200 flex items-center gap-2">
              {plot.isCorner && (
                <span className="px-2 py-0.5 rounded bg-blue-50 text-blue-700 font-mono text-[10px] font-bold border border-blue-200">
                  Corner Plot
                </span>
              )}
              {plot.isGarden && (
                <span className="px-2 py-0.5 rounded bg-emerald-50 text-emerald-700 font-mono text-[10px] font-bold border border-emerald-200">
                  Park Facing
                </span>
              )}
              {plot.isHot && (
                <span className="px-2 py-0.5 rounded bg-amber-50 text-amber-700 font-mono text-[10px] font-bold border border-amber-200">
                  High Demand
                </span>
              )}
            </div>
          </div>

          {/* Reserved Details if applicable */}
          {plot.status === 'RESERVED' && (
            <div className="mt-5 p-4 rounded-xl bg-amber-50/80 border border-amber-200 text-xs text-amber-900 space-y-1.5">
              <div className="font-bold flex items-center gap-1.5">
                <span>Temporary 48-Hour Token Hold</span>
              </div>
              <div>Reserved For: <span className="font-bold">{plot.reservedFor}</span></div>
              <div className="text-[11px] text-amber-800">
                Hold valid until {plot.reservedUntil || 'Tomorrow 5:00 PM'}. Automatically releases to public inventory if booking token is not realized.
              </div>
            </div>
          )}

          {/* Sold / Active Allotment details */}
          {sale && (
            <div className="mt-5 space-y-4">
              <h3 className="font-serif font-bold text-base text-espresso-950 pb-1 border-b border-sand-200">
                Buyer & Allotment Details
              </h3>

              <div className="p-4 rounded-xl bg-sand-50 border border-sand-200 space-y-2.5 text-xs">
                <div className="flex justify-between items-center text-espresso-600">
                  <span>Buyer Full Name:</span>
                  <span className="font-bold text-espresso-900">{sale.buyerName}</span>
                </div>
                <div className="flex justify-between items-center text-espresso-600">
                  <span>Contact Mobile:</span>
                  <span className="font-mono font-bold text-espresso-900">{sale.buyerMobile}</span>
                </div>
                <div className="flex justify-between items-center text-espresso-600">
                  <span>Govt ID ({sale.buyerGovIdType}):</span>
                  <span className="font-mono text-espresso-700">•••• •••• {sale.buyerGovIdLast4}</span>
                </div>
                <div className="flex justify-between items-center text-espresso-600">
                  <span>Allotment Agreement:</span>
                  <span className="font-mono font-bold text-forest">{sale.allotmentLetterNo}</span>
                </div>
                <div className="flex justify-between items-center text-espresso-600">
                  <span>Agreed Sale Value:</span>
                  <span className="font-mono font-bold text-espresso-900">
                    ₹{(sale.dealValue).toLocaleString('en-IN')}
                  </span>
                </div>
                <div className="flex justify-between items-center text-espresso-600">
                  <span>Amount Paid So Far:</span>
                  <span className="font-mono font-bold text-emerald-700">
                    ₹{(sale.totalPaid).toLocaleString('en-IN')}
                  </span>
                </div>
                <div className="flex justify-between items-center text-espresso-600">
                  <span>Pending Balance:</span>
                  <span className="font-mono font-bold text-rose-700">
                    ₹{(sale.balanceDue).toLocaleString('en-IN')}
                  </span>
                </div>
              </div>

              {/* Installment Milestone Schedule */}
              {schedules.length > 0 && (
                <div className="space-y-2 text-xs">
                  <h4 className="font-sans font-bold text-xs uppercase tracking-wider text-espresso-600">
                    Payment Milestones Schedule
                  </h4>
                  <div className="space-y-1.5">
                    {schedules.map((sch) => (
                      <div key={sch.id} className="p-2.5 rounded-lg border border-sand-200 bg-white flex items-center justify-between">
                        <div>
                          <div className="font-semibold text-espresso-900">{sch.label}</div>
                          <div className="text-[10px] text-espresso-500 font-mono">Due: {sch.dueDate}</div>
                        </div>
                        <div className="text-right">
                          <div className="font-mono font-bold text-espresso-900">₹{(sch.expectedAmount).toLocaleString('en-IN')}</div>
                          <span className={`inline-block px-1.5 py-0.5 rounded text-[9px] font-mono font-bold ${
                            sch.status === 'PAID' ? 'bg-emerald-100 text-emerald-800' :
                            sch.status === 'OVERDUE' ? 'bg-rose-100 text-rose-800' : 'bg-sand-200 text-espresso-700'
                          }`}>
                            {sch.status}
                          </span>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}

        </div>

        {/* Action Button: Book / Allot Plot */}
        <div className="pt-6 border-t border-sand-200 space-y-2">
          {plot.status === 'AVAILABLE' ? (
            <button
              onClick={onOpenBooking}
              className="w-full py-3.5 px-4 rounded-xl bg-forest hover:bg-forest-light text-white font-sans font-bold text-xs uppercase tracking-wider shadow-warm-sm transition-all flex items-center justify-center gap-2"
            >
              <span>Book / Allot This Plot</span>
              <ArrowRight className="w-4 h-4" />
            </button>
          ) : (
            <button
              onClick={onClose}
              className="w-full py-2.5 px-4 rounded-xl border border-sand-300 text-espresso-700 hover:bg-sand-100 text-xs font-semibold"
            >
              Close Drawer
            </button>
          )}
        </div>

      </div>
    </div>
  );
};
