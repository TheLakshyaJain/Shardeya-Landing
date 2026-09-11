import React from 'react';
import { X, Printer, ShieldCheck, Download, CheckCircle2 } from 'lucide-react';
import { PlotSale } from '../../../../types/crm';
import { useCrm } from '../../../../context/CrmContext';

interface AllotmentLetterModalProps {
  sale: PlotSale;
  onClose: () => void;
}

export const AllotmentLetterModal: React.FC<AllotmentLetterModalProps> = ({
  sale,
  onClose,
}) => {
  const { plots, activeProject } = useCrm();
  const plot = plots.find((p) => p.id === sale.plotId);

  const handlePrint = () => {
    window.print();
  };

  return (
    <div className="fixed inset-0 z-50 bg-espresso-950/70 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl border border-sand-300 max-w-2xl w-full p-8 shadow-2xl animate-fadeIn text-left max-h-[92vh] overflow-y-auto">
        
        {/* Top Actions */}
        <div className="flex items-center justify-between pb-4 border-b border-sand-200">
          <div className="flex items-center gap-2">
            <span className="text-xs font-mono font-bold text-forest">RERA COMPLIANT TEMPLATE</span>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={handlePrint}
              className="px-3 py-1.5 rounded-lg bg-forest hover:bg-forest-light text-white text-xs font-bold flex items-center gap-1.5 shadow-sm"
            >
              <Printer className="w-3.5 h-3.5" />
              <span>Print Allotment Letter</span>
            </button>

            <button
              onClick={onClose}
              className="p-1.5 rounded-lg hover:bg-sand-100 text-espresso-500"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* Printable Document Body */}
        <div className="mt-6 p-8 border border-sand-300 rounded-xl bg-sand-50/30 text-xs font-sans space-y-6 text-espresso-900 leading-relaxed shadow-inner">
          
          {/* Company Letterhead */}
          <div className="text-center pb-6 border-b border-sand-300">
            <div className="font-serif font-bold text-xl text-espresso-950 tracking-wide">
              SHARDEYA GROUP PVT. LTD.
            </div>
            <div className="text-[11px] text-espresso-600 font-sans mt-0.5">
              Corporate Promoter & Township Developers • CIN: U70100UP2024PTC189211
            </div>
            <div className="text-[10px] font-mono text-forest mt-1 font-semibold">
              RERA Reg. ID: {activeProject?.reraNumber || 'UPRERA/PRJ992182/2024'}
            </div>
          </div>

          {/* Letter Ref & Date */}
          <div className="flex justify-between items-center font-mono text-[11px] text-espresso-600">
            <div>Letter No: <span className="font-bold text-espresso-950">{sale.allotmentLetterNo}</span></div>
            <div>Issue Date: <span className="font-bold text-espresso-950">{sale.purchaseDate}</span></div>
          </div>

          {/* Recipient */}
          <div className="space-y-0.5">
            <div className="font-bold text-espresso-950">To,</div>
            <div className="font-serif font-bold text-base text-espresso-950">{sale.buyerName}</div>
            <div className="font-mono text-espresso-600">Contact Mobile: {sale.buyerMobile}</div>
            <div className="font-mono text-espresso-600">Govt ID ({sale.buyerGovIdType}): •••• •••• {sale.buyerGovIdLast4}</div>
          </div>

          {/* Subject */}
          <div className="p-2.5 rounded-lg bg-sand-100 font-bold text-espresso-950 text-center uppercase tracking-wide text-[11px]">
            SUBJECT: OFFICIAL LETTER OF ALLOTMENT FOR {plot?.plotNumber} IN "{activeProject?.name}"
          </div>

          {/* Body Paragraphs */}
          <div className="space-y-3">
            <p>
              Dear Sir/Madam,
            </p>
            <p>
              We have the pleasure to inform you that following the realization of your initial booking token and submission of standard KYC documentation, Shardeya Group Pvt. Ltd. hereby confirms the provision of official unit allotment for residential/villa plot in our approved integrated township <strong>{activeProject?.name}</strong>, situated at {activeProject?.address}.
            </p>

            {/* Schedule of Property Table */}
            <div className="my-4 border border-sand-300 rounded-lg overflow-hidden">
              <div className="bg-sand-100 px-3 py-1.5 font-bold text-[11px] text-espresso-900 border-b border-sand-300">
                SCHEDULE OF ALLOTTED PROPERTY
              </div>
              <div className="divide-y divide-sand-200 text-[11px]">
                <div className="grid grid-cols-2 px-3 py-1.5">
                  <span className="text-espresso-600">Allotted Plot Number:</span>
                  <span className="font-mono font-bold text-espresso-950">{plot?.plotNumber}</span>
                </div>
                <div className="grid grid-cols-2 px-3 py-1.5">
                  <span className="text-espresso-600">Super Plotted Area:</span>
                  <span className="font-bold text-espresso-950">{plot?.sizeValue.toLocaleString()} {plot?.sizeUnit === 'SQ_FT' ? 'sq.ft' : 'Gaj'}</span>
                </div>
                <div className="grid grid-cols-2 px-3 py-1.5">
                  <span className="text-espresso-600">Cardinal Facing:</span>
                  <span className="font-mono font-bold text-forest">{plot?.facing} Direction</span>
                </div>
                <div className="grid grid-cols-2 px-3 py-1.5">
                  <span className="text-espresso-600">Agreed Total Consideration Value:</span>
                  <span className="font-mono font-bold text-espresso-950">₹{sale.dealValue.toLocaleString('en-IN')}</span>
                </div>
                <div className="grid grid-cols-2 px-3 py-1.5">
                  <span className="text-espresso-600">Amount Received & Credited in Escrow:</span>
                  <span className="font-mono font-bold text-emerald-700">₹{sale.totalPaid.toLocaleString('en-IN')}</span>
                </div>
                <div className="grid grid-cols-2 px-3 py-1.5">
                  <span className="text-espresso-600">Balance Consideration Payable:</span>
                  <span className="font-mono font-bold text-rose-700">₹{sale.balanceDue.toLocaleString('en-IN')}</span>
                </div>
              </div>
            </div>

            <p>
              This allotment is subject to timely payment of milestone construction instalments as agreed under the Master Service Protocol and Section 13 of the Real Estate (Regulation and Development) Act.
            </p>
          </div>

          {/* Signatures */}
          <div className="pt-8 flex items-end justify-between border-t border-sand-300 text-center">
            <div className="space-y-1">
              <div className="h-10 border-b border-dashed border-sand-400 w-40 mx-auto" />
              <div className="font-bold text-[10px] uppercase">Allottee Signature</div>
            </div>

            <div className="space-y-1">
              <div className="font-serif font-bold text-forest text-sm">Shardeya RealTech LLP</div>
              <div className="text-[10px] text-espresso-500 font-mono">Digitally Signed & Sealed</div>
              <div className="font-bold text-[10px] uppercase text-espresso-950">Authorized Promoter Signatory</div>
            </div>
          </div>

        </div>

      </div>
    </div>
  );
};
