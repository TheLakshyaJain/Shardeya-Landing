import React, { useState } from 'react';
import { 
  FileText, Search, Printer, Download, Eye, 
  CheckCircle2, IndianRupee, ShieldCheck, User 
} from 'lucide-react';
import { useCrm } from '../../../../context/CrmContext';
import { PlotSale } from '../../../../types/crm';
import { AllotmentLetterModal } from './AllotmentLetterModal';

export const DealsPage: React.FC = () => {
  const { plotSales, plots, activeProject } = useCrm();

  const [searchQuery, setSearchQuery] = useState('');
  const [selectedSaleForDoc, setSelectedSaleForDoc] = useState<PlotSale | null>(null);

  const filteredSales = plotSales.filter((sale) => {
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      return sale.buyerName.toLowerCase().includes(q) || sale.allotmentLetterNo.toLowerCase().includes(q) || sale.buyerMobile.includes(q);
    }
    return true;
  });

  return (
    <div className="space-y-6 text-left animate-fadeIn">
      
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-sand-300">
        <div>
          <h1 className="font-serif font-bold text-2xl sm:text-3xl text-espresso-950">
            Deals, Bookings & Legal Allotments
          </h1>
          <p className="text-xs text-espresso-600 mt-1">
            Official repository of executed buyer contracts, sub-registrar deeds, and printable RERA allotment letters.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <div className="px-3 py-1.5 rounded-xl bg-sand-100 border border-sand-300 text-xs font-mono font-bold text-espresso-800">
            {plotSales.length} Total Registered Allotments
          </div>
        </div>
      </div>

      {/* Filter Bar */}
      <div className="bg-white rounded-2xl border border-sand-300 p-4 shadow-warm-sm flex items-center justify-between">
        <div className="relative flex-1 max-w-sm">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-espresso-400" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search by buyer name, phone, or letter #..."
            className="w-full pl-9 pr-3 py-2 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white text-xs font-sans text-espresso-950 outline-none"
          />
        </div>
      </div>

      {/* Deals Table */}
      <div className="bg-white rounded-2xl border border-sand-300 shadow-warm-sm overflow-x-auto">
        <table className="w-full text-left text-xs font-sans">
          <thead className="bg-sand-50 border-b border-sand-200 text-espresso-600 uppercase font-bold text-[10px]">
            <tr>
              <th className="py-3.5 px-4">Allotment Ref #</th>
              <th className="py-3.5 px-4">Unit Allotted</th>
              <th className="py-3.5 px-4">Buyer Full Name</th>
              <th className="py-3.5 px-4">Govt ID Proof</th>
              <th className="py-3.5 px-4">Deal Value</th>
              <th className="py-3.5 px-4">Collections Paid</th>
              <th className="py-3.5 px-4">Balance Due</th>
              <th className="py-3.5 px-4">Status</th>
              <th className="py-3.5 px-4 text-right">Legal Document</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-sand-200">
            {filteredSales.map((sale) => {
              const plot = plots.find((p) => p.id === sale.plotId);

              return (
                <tr key={sale.id} className="hover:bg-sand-50 transition-colors">
                  <td className="py-3.5 px-4 font-mono font-bold text-forest">
                    {sale.allotmentLetterNo}
                  </td>
                  <td className="py-3.5 px-4 font-mono font-bold text-espresso-950">
                    {plot?.plotNumber || 'Unit'}
                  </td>
                  <td className="py-3.5 px-4">
                    <div className="font-bold text-espresso-950">{sale.buyerName}</div>
                    <div className="font-mono text-[10px] text-espresso-500">{sale.buyerMobile}</div>
                  </td>
                  <td className="py-3.5 px-4 font-mono text-espresso-700">
                    {sale.buyerGovIdType}: •••• {sale.buyerGovIdLast4}
                  </td>
                  <td className="py-3.5 px-4 font-mono font-bold text-espresso-900">
                    ₹{(sale.dealValue).toLocaleString('en-IN')}
                  </td>
                  <td className="py-3.5 px-4 font-mono font-bold text-emerald-700">
                    ₹{(sale.totalPaid).toLocaleString('en-IN')}
                  </td>
                  <td className="py-3.5 px-4 font-mono font-bold text-rose-700">
                    ₹{(sale.balanceDue).toLocaleString('en-IN')}
                  </td>
                  <td className="py-3.5 px-4">
                    <span className={`px-2 py-0.5 rounded-full text-[10px] font-mono font-bold ${
                      sale.status === 'COMPLETED' ? 'bg-emerald-100 text-emerald-800' : 'bg-blue-100 text-blue-800'
                    }`}>
                      {sale.status}
                    </span>
                  </td>
                  <td className="py-3.5 px-4 text-right">
                    <button
                      onClick={() => setSelectedSaleForDoc(sale)}
                      className="px-3 py-1.5 rounded-lg bg-forest hover:bg-forest-light text-white text-[11px] font-bold shadow-sm inline-flex items-center gap-1.5 transition-colors"
                    >
                      <Printer className="w-3 h-3" />
                      <span>Allotment Letter</span>
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {/* Allotment Letter Modal */}
      {selectedSaleForDoc && (
        <AllotmentLetterModal
          sale={selectedSaleForDoc}
          onClose={() => setSelectedSaleForDoc(null)}
        />
      )}

    </div>
  );
};
