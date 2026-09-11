import React, { useState } from 'react';
import { 
  BarChart3, Download, Printer, TrendingUp, 
  ShieldCheck, FileSpreadsheet, PieChart, Layers, 
  DollarSign, ArrowUpRight, ArrowDownRight, Calendar
} from 'lucide-react';
import { useCrm } from '../../../../context/CrmContext';
import { useLanguage } from '../../../../context/LanguageContext';

export const ReportsCatalogPage: React.FC = () => {
  const { plots, plotSales, paymentRecords, brokers, vouchers, activeProject } = useCrm();
  const { language } = useLanguage();
  const isHi = language === 'hi';

  const [activeReport, setActiveReport] = useState<'velocity' | 'rera' | 'broker' | 'inventory'>('velocity');

  // Computed metrics
  const totalPlots = plots.length;
  const soldPlots = plots.filter(p => p.status === 'SOLD').length;
  const reservedPlots = plots.filter(p => p.status === 'RESERVED').length;
  const availablePlots = plots.filter(p => p.status === 'AVAILABLE').length;
  const absorptionRate = totalPlots > 0 ? ((soldPlots + reservedPlots) / totalPlots) * 100 : 0;

  const totalSalesValue = plotSales.reduce((sum, s) => sum + s.dealValue, 0);
  const totalCollections = paymentRecords.reduce((sum, p) => sum + p.amount, 0);
  const reraEscrowShare = totalCollections * 0.70; // 70% mandatory RERA Escrow
  const operationalShare = totalCollections * 0.30; // 30% operations

  const totalAreaSqft = plots.reduce((sum, p) => sum + p.sizeSqft, 0);
  const soldAreaSqft = plots.filter(p => p.status === 'SOLD').reduce((sum, p) => sum + p.sizeSqft, 0);
  const avgRealization = soldAreaSqft > 0 ? totalSalesValue / soldAreaSqft : 2200;

  const handlePrint = () => {
    window.print();
  };

  const handleExportCSV = () => {
    let csvContent = 'data:text/csv;charset=utf-8,';
    csvContent += 'Plot No,Project,Status,Area (Sqft),Price (INR),Buyer Name\n';
    plots.forEach(p => {
      const sale = plotSales.find(s => s.id === p.currentSaleId);
      csvContent += `"${p.plotNumber}","${activeProject?.name || ''}","${p.status}",${p.sizeSqft},${p.price},"${sale?.buyerName || ''}"\n`;
    });
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `shardeya_${activeProject?.id || 'report'}_sales.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-forest/10 text-forest border border-forest/20 tracking-wide uppercase">
              {isHi ? 'ऑडिट व विश्लेषण' : 'Intelligence & Audits'}
            </span>
          </div>
          <h1 className="text-2xl sm:text-3xl font-serif font-bold text-espresso-950 mt-1">
            {isHi ? 'प्रोजेक्ट रिपोर्ट व RERA अनुपालन' : 'Reports & Compliance Analytics'}
          </h1>
          <p className="text-sm text-espresso-700">
            {isHi 
              ? 'बिक्री गति, RERA 70:30 एस्क्रो ऑडिट, और चैनल पार्टनर प्रदर्शन रिपोर्ट।' 
              : 'Sales velocity matrices, statutory 70:30 RERA escrow breakdowns, and broker payout audits.'}
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={handleExportCSV}
            className="inline-flex items-center gap-2 px-3.5 py-2 rounded-xl bg-white border border-sand-300 text-espresso-700 hover:bg-sand-100 text-xs font-semibold transition-colors"
          >
            <FileSpreadsheet className="w-4 h-4 text-forest" />
            {isHi ? 'CSV डाउनलोड' : 'Export CSV'}
          </button>
          <button
            onClick={handlePrint}
            className="inline-flex items-center gap-2 px-3.5 py-2 rounded-xl bg-forest hover:bg-forest-600 text-white text-xs font-semibold shadow-sm transition-colors"
          >
            <Printer className="w-4 h-4" />
            {isHi ? 'प्रिंट रिपोर्ट' : 'Print Official Audit'}
          </button>
        </div>
      </div>

      {/* Report Switcher Tabs */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        {[
          { id: 'velocity' as const, label: isHi ? 'बिक्री गति' : 'Sales Velocity', icon: TrendingUp, metric: `${absorptionRate.toFixed(1)}% Absorbed` },
          { id: 'rera' as const, label: isHi ? 'RERA 70:30 एस्क्रो' : '70:30 Escrow Audit', icon: ShieldCheck, metric: `₹${(reraEscrowShare / 100000).toFixed(1)}L Protected` },
          { id: 'broker' as const, label: isHi ? 'ब्रोकर सिंडिकेट' : 'Broker Performance', icon: BarChart3, metric: `${brokers.length} Partners` },
          { id: 'inventory' as const, label: isHi ? 'इन्वेंटरी रियलाइजेशन' : 'Inventory Aging', icon: Layers, metric: `${availablePlots} Units Unsold` },
        ].map(item => (
          <button
            key={item.id}
            onClick={() => setActiveReport(item.id)}
            className={`p-4 rounded-xl text-left border transition-all ${
              activeReport === item.id
                ? 'bg-white border-forest shadow-md ring-1 ring-forest/20'
                : 'bg-white border-sand-300 hover:border-sand-400'
            }`}
          >
            <div className="flex items-center justify-between text-espresso-600">
              <item.icon className={`w-5 h-5 ${activeReport === item.id ? 'text-forest' : 'text-espresso-400'}`} />
              <span className="text-[11px] font-mono text-espresso-500 font-medium">{item.metric}</span>
            </div>
            <div className={`mt-2 font-serif font-bold text-sm ${activeReport === item.id ? 'text-forest' : 'text-espresso-950'}`}>
              {item.label}
            </div>
          </button>
        ))}
      </div>

      {/* Main Report View */}
      {activeReport === 'velocity' && (
        <div className="space-y-6">
          <div className="p-6 rounded-2xl bg-white border border-sand-300 shadow-sm">
            <h3 className="font-serif font-bold text-lg text-espresso-950 mb-1">
              Plotted Land Absorption & Realization Velocity
            </h3>
            <p className="text-xs text-espresso-600 mb-6">
              Track unit conversion rates, total area booked, and average price realization per square foot.
            </p>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
              <div className="p-4 rounded-xl bg-sand-50 border border-sand-200">
                <div className="text-xs text-espresso-600">Cumulative Realization</div>
                <div className="text-2xl font-serif font-bold text-forest mt-1">₹{(totalSalesValue / 10000000).toFixed(2)} Cr</div>
                <div className="text-xs text-espresso-500 mt-1">Against ₹{(plots.reduce((s, p) => s + p.price, 0) / 10000000).toFixed(2)} Cr total GDV</div>
              </div>

              <div className="p-4 rounded-xl bg-sand-50 border border-sand-200">
                <div className="text-xs text-espresso-600">Average Price Realized</div>
                <div className="text-2xl font-serif font-bold text-espresso-950 mt-1">₹{Math.round(avgRealization)} / sqft</div>
                <div className="text-xs text-forest mt-1 flex items-center gap-0.5">
                  <ArrowUpRight className="w-3.5 h-3.5" /> +8.4% above launch base rate
                </div>
              </div>

              <div className="p-4 rounded-xl bg-sand-50 border border-sand-200">
                <div className="text-xs text-espresso-600">Inventory Status</div>
                <div className="text-2xl font-serif font-bold text-espresso-950 mt-1">{soldPlots + reservedPlots} / {totalPlots} Units</div>
                <div className="text-xs text-espresso-600 mt-1">{absorptionRate.toFixed(1)}% township fully absorbed</div>
              </div>
            </div>

            {/* Progress Bar Breakdown */}
            <div className="space-y-2">
              <div className="flex justify-between text-xs font-medium text-espresso-700">
                <span>Inventory Composition</span>
                <span>{soldPlots} Sold • {reservedPlots} Reserved • {availablePlots} Available</span>
              </div>
              <div className="h-4 w-full bg-sand-200 rounded-full overflow-hidden flex">
                <div style={{ width: `${(soldPlots / totalPlots) * 100}%` }} className="bg-emerald-600 h-full" title="Sold" />
                <div style={{ width: `${(reservedPlots / totalPlots) * 100}%` }} className="bg-amber-500 h-full" title="Reserved" />
                <div style={{ width: `${(availablePlots / totalPlots) * 100}%` }} className="bg-sand-300 h-full" title="Available" />
              </div>
              <div className="flex items-center gap-4 text-xs text-espresso-600 pt-1">
                <div className="flex items-center gap-1.5"><div className="w-3 h-3 rounded bg-emerald-600" /> Sold ({soldPlots})</div>
                <div className="flex items-center gap-1.5"><div className="w-3 h-3 rounded bg-amber-500" /> Reserved ({reservedPlots})</div>
                <div className="flex items-center gap-1.5"><div className="w-3 h-3 rounded bg-sand-300" /> Available ({availablePlots})</div>
              </div>
            </div>
          </div>
        </div>
      )}

      {activeReport === 'rera' && (
        <div className="p-6 rounded-2xl bg-white border border-sand-300 shadow-sm space-y-6">
          <div className="flex items-start justify-between">
            <div>
              <span className="inline-flex items-center gap-1 text-xs font-semibold px-2.5 py-0.5 rounded bg-forest/10 text-forest">
                <ShieldCheck className="w-3.5 h-3.5" /> Statutory Section 4(2)(l)(D)
              </span>
              <h3 className="font-serif font-bold text-lg text-espresso-950 mt-1">
                RERA Mandatory 70:30 Escrow Ledger
              </h3>
              <p className="text-xs text-espresso-600">
                70% of all customer collection remittances must be maintained in a dedicated bank escrow solely for land and civil development costs.
              </p>
            </div>
            <div className="text-right">
              <div className="text-xs text-espresso-500 font-mono">Project RERA ID</div>
              <div className="text-sm font-bold font-mono text-espresso-950">{activeProject?.reraNumber || 'UPRERA/PRJ992182/2024'}</div>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="p-5 rounded-xl border border-emerald-300 bg-emerald-50/50">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold uppercase tracking-wider text-emerald-800">70% Dedicated Project Escrow</span>
                <span className="text-xs font-semibold bg-emerald-200 text-emerald-900 px-2 py-0.5 rounded">RERA Ring-fenced</span>
              </div>
              <div className="text-3xl font-serif font-bold text-emerald-950 mt-3">
                ₹{(reraEscrowShare / 100000).toFixed(2)} Lakhs
              </div>
              <p className="text-xs text-emerald-700 mt-2">
                Reserved exclusively for township boundary, 60ft arterial roads, underground drainage, and sub-registrar stamp duties.
              </p>
            </div>

            <div className="p-5 rounded-xl border border-slate-300 bg-slate-50/50">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold uppercase tracking-wider text-slate-800">30% Operational & Marketing Account</span>
                <span className="text-xs font-semibold bg-slate-200 text-slate-900 px-2 py-0.5 rounded">Operating Liquidity</span>
              </div>
              <div className="text-3xl font-serif font-bold text-slate-950 mt-3">
                ₹{(operationalShare / 100000).toFixed(2)} Lakhs
              </div>
              <p className="text-xs text-slate-600 mt-2">
                Utilizable for corporate overheads, channel partner commission payouts, and media outreach campaigns.
              </p>
            </div>
          </div>

          {/* Audit Verification Table */}
          <div className="border border-sand-200 rounded-xl overflow-hidden">
            <div className="p-3 bg-sand-100 font-serif font-bold text-xs text-espresso-800">
              Verified Collection Inflows & Statutory Allocations
            </div>
            <table className="w-full text-left text-xs">
              <thead className="bg-sand-50 border-b border-sand-200 text-espresso-600">
                <tr>
                  <th className="py-2.5 px-4">Receipt No</th>
                  <th className="py-2.5 px-4">Date</th>
                  <th className="py-2.5 px-4 text-right">Inflow Amount</th>
                  <th className="py-2.5 px-4 text-right text-forest">70% Escrow Account</th>
                  <th className="py-2.5 px-4 text-right">30% Op Account</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-sand-200">
                {paymentRecords.map(pay => (
                  <tr key={pay.id} className="hover:bg-sand-50/50">
                    <td className="py-2.5 px-4 font-mono font-medium">{pay.receiptNo}</td>
                    <td className="py-2.5 px-4 text-espresso-600">{pay.paidOn}</td>
                    <td className="py-2.5 px-4 text-right font-mono font-bold text-espresso-950">₹{pay.amount.toLocaleString('en-IN')}</td>
                    <td className="py-2.5 px-4 text-right font-mono text-forest font-semibold">₹{(pay.amount * 0.7).toLocaleString('en-IN')}</td>
                    <td className="py-2.5 px-4 text-right font-mono text-espresso-700">₹{(pay.amount * 0.3).toLocaleString('en-IN')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {activeReport === 'broker' && (
        <div className="p-6 rounded-2xl bg-white border border-sand-300 shadow-sm space-y-6">
          <div>
            <h3 className="font-serif font-bold text-lg text-espresso-950">
              Channel Partner Syndicate Performance & Payout Audit
            </h3>
            <p className="text-xs text-espresso-600">
              Analysis of broker-driven volume, conversion ratios, and TDS deduction verification.
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {brokers.map(broker => (
              <div key={broker.id} className="p-4 rounded-xl border border-sand-200 bg-sand-50/50">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-forest uppercase">{broker.tier} Partner</span>
                  <span className="text-xs font-mono text-espresso-500">{broker.commissionRate}% Rate</span>
                </div>
                <h4 className="text-base font-serif font-bold text-espresso-950 mt-1">{broker.firmName}</h4>
                <div className="text-xs text-espresso-600">{broker.fullName} • {broker.cityArea}</div>
                <div className="mt-4 pt-3 border-t border-sand-200 grid grid-cols-2 gap-2 text-xs">
                  <div>
                    <div className="text-espresso-500">Deals Closed</div>
                    <div className="text-base font-bold text-espresso-950">{broker.dealsClosedCount} Units</div>
                  </div>
                  <div>
                    <div className="text-espresso-500">Total Payout</div>
                    <div className="text-base font-bold text-forest">₹{(broker.totalCommissionEarned / 100000).toFixed(2)}L</div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {activeReport === 'inventory' && (
        <div className="p-6 rounded-2xl bg-white border border-sand-300 shadow-sm space-y-6">
          <div>
            <h3 className="font-serif font-bold text-lg text-espresso-950">
              Inventory Ageing & Spatial Realization Matrix
            </h3>
            <p className="text-xs text-espresso-600">
              Corner plots, park-facing parcels, and boulevard-facing units realization premium analysis.
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="p-4 rounded-xl border border-sand-200 bg-emerald-50/40">
              <div className="text-xs text-emerald-800 font-semibold uppercase">Corner Plots (10% Premium)</div>
              <div className="text-2xl font-serif font-bold text-espresso-950 mt-1">
                {plots.filter(p => p.isCorner).length} Units
              </div>
              <div className="text-xs text-espresso-600 mt-1">
                {plots.filter(p => p.isCorner && p.status === 'SOLD').length} already sold
              </div>
            </div>

            <div className="p-4 rounded-xl border border-sand-200 bg-amber-50/40">
              <div className="text-xs text-amber-800 font-semibold uppercase">Park / Garden Facing Units</div>
              <div className="text-2xl font-serif font-bold text-espresso-950 mt-1">
                {plots.filter(p => p.isGarden).length} Units
              </div>
              <div className="text-xs text-espresso-600 mt-1">
                Commanding +₹100/sqft over base
              </div>
            </div>

            <div className="p-4 rounded-xl border border-sand-200 bg-sand-100">
              <div className="text-xs text-espresso-700 font-semibold uppercase">Available For Allotment</div>
              <div className="text-2xl font-serif font-bold text-forest mt-1">
                {availablePlots} Plots
              </div>
              <div className="text-xs text-espresso-600 mt-1">
                ₹{(plots.filter(p => p.status === 'AVAILABLE').reduce((s, p) => s + p.price, 0) / 10000000).toFixed(2)} Cr pipeline
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
