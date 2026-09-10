import React, { useState } from 'react';
import { 
  Building2, CheckCircle2, ShieldCheck, FileText, Download, 
  ArrowUpRight, AlertCircle, RefreshCw, Check, Landmark, Percent, ExternalLink
} from 'lucide-react';
import { useLanguage } from '../../context/LanguageContext';
import { GlassCard } from '../common/GlassCard';

interface PaymentEntry {
  id: string;
  utr: string;
  buyer: string;
  unit: string;
  amount: number;
  date: string;
  bank: string;
  status: 'pending' | 'reconciled';
}

export const AccountsReconciliation: React.FC = () => {
  const { language } = useLanguage();
  const isHi = language === 'hi';

  const [activeTab, setActiveTab] = useState<'reconciliation' | 'allotment'>('reconciliation');
  const [selectedPaymentId, setSelectedPaymentId] = useState<string>('pay-1');
  const [reconciledPayments, setReconciledPayments] = useState<Record<string, boolean>>({
    'pay-1': false,
    'pay-2': true,
    'pay-3': false,
  });
  const [isReconciling, setIsReconciling] = useState<boolean>(false);
  const [downloadToast, setDownloadToast] = useState<string | null>(null);

  const payments: PaymentEntry[] = [
    {
      id: 'pay-1',
      utr: 'HDFCR520260904018274',
      buyer: 'Dr. Neha & Rajesh Talwar',
      unit: 'Villa Plot #103',
      amount: 4500000, // 45 Lakhs
      date: 'Today, 11:20 AM',
      bank: 'HDFC Bank — Escrow Sync',
      status: reconciledPayments['pay-1'] ? 'reconciled' : 'pending',
    },
    {
      id: 'pay-2',
      utr: 'ICIC000010928371829',
      buyer: 'Vikramaditya Singhania',
      unit: 'Villa Plot #101',
      amount: 6000000, // 60 Lakhs
      date: 'Yesterday, 04:45 PM',
      bank: 'ICICI Bank — Virtual Acc',
      status: reconciledPayments['pay-2'] ? 'reconciled' : 'pending',
    },
    {
      id: 'pay-3',
      utr: 'SBIN004928104819283',
      buyer: 'Rahul & Anita Singhal',
      unit: 'Apt B-502',
      amount: 3200000, // 32 Lakhs
      date: '08 Sep 2026',
      bank: 'State Bank of India',
      status: reconciledPayments['pay-3'] ? 'reconciled' : 'pending',
    },
  ];

  const activePayment = payments.find((p) => p.id === selectedPaymentId) || payments[0];
  const isSelectedReconciled = reconciledPayments[activePayment.id];

  // 70:30 calculation for active payment
  const escrow70 = activePayment.amount * 0.70;
  const operational30 = activePayment.amount * 0.30;

  const handleReconcile = (id: string) => {
    setIsReconciling(true);
    setTimeout(() => {
      setReconciledPayments((prev) => ({ ...prev, [id]: true }));
      setIsReconciling(false);
    }, 600);
  };

  const handleDownload = (docName: string) => {
    setDownloadToast(docName);
    setTimeout(() => setDownloadToast(null), 3000);
  };

  const formatLakhs = (val: number) => {
    return `₹${(val / 100000).toFixed(2)} Lakh`;
  };

  return (
    <section id="accounts" className="py-24 relative bg-slate-50/70 border-b border-slate-200">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        
        {/* Section Header */}
        <div className="text-center max-w-3xl mx-auto mb-14">
          <div className="inline-flex items-center gap-2 px-3.5 py-1 rounded-full border border-emerald-200 bg-emerald-50 text-emerald-900 text-xs font-sans mb-3 shadow-warm-sm">
            <ShieldCheck className="w-3.5 h-3.5 text-emerald-600" />
            <span className="font-semibold uppercase tracking-wider">
              {isHi ? 'वैधानिक अनुपालन व वित्तीय लेजर' : 'Statutory Compliance & Financials'}
            </span>
          </div>
          <h2 className="text-3xl sm:text-4xl lg:text-5xl font-serif text-slate-950 font-normal tracking-tight">
            {isHi 
              ? 'आवंटन लेजर और RERA 70:30 एस्क्रो ऑटोमेशन'
              : 'Allotment Ledger & RERA 70:30 Escrow Engine'}
          </h2>
          <p className="mt-4 text-base text-slate-600 font-sans leading-relaxed">
            {isHi
              ? 'आने वाले बैंक RTGS पेमेंट्स का तुरंत UTR मिलान करें, 70% निर्माण एस्क्रो को 30% संचालन खाते से अलग करें और डिजिटल आवंटन पत्र जनरेट करें।'
              : 'Reconcile buyer RTGS payments against demand milestones, automatically bifurcate funds into statutory accounts under RERA Section 4, and issue verifiable digital allotment certificates.'}
          </p>

          {/* View Toggle Tabs */}
          <div className="flex items-center justify-center gap-2 mt-8">
            <button
              onClick={() => setActiveTab('reconciliation')}
              className={`flex items-center gap-2 px-5 py-2.5 rounded-xl text-xs font-sans font-bold transition-all shadow-warm-sm ${
                activeTab === 'reconciliation'
                  ? 'bg-slate-900 text-white shadow-md'
                  : 'bg-white text-slate-700 hover:bg-slate-100 border border-slate-200'
              }`}
            >
              <Landmark className="w-3.5 h-3.5 text-emerald-400" />
              <span>{isHi ? 'RTGS व UTR मिलान लेजर' : 'Live RTGS Reconciliation'}</span>
            </button>
            <button
              onClick={() => setActiveTab('allotment')}
              className={`flex items-center gap-2 px-5 py-2.5 rounded-xl text-xs font-sans font-bold transition-all shadow-warm-sm ${
                activeTab === 'allotment'
                  ? 'bg-slate-900 text-white shadow-md'
                  : 'bg-white text-slate-700 hover:bg-slate-100 border border-slate-200'
              }`}
            >
              <FileText className="w-3.5 h-3.5 text-amber-400" />
              <span>{isHi ? 'डिजिटल आवंटन पत्र (Allotment Letter)' : 'Digital Allotment Letter'}</span>
            </button>
          </div>
        </div>

        {/* Tab 1: Live Bank RTGS Reconciliation & RERA 70:30 Split */}
        {activeTab === 'reconciliation' && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
            
            {/* Left Queue: Incoming RTGS Payments */}
            <div className="lg:col-span-5 space-y-3 text-left">
              <div className="flex items-center justify-between px-1 mb-1">
                <span className="text-xs font-sans font-bold uppercase tracking-wider text-slate-500">
                  {isHi ? 'आवक बैंक UTR कतार (Live Feed)' : 'Incoming RTGS Queue (Direct Bank API)'}
                </span>
                <span className="text-[11px] font-mono text-emerald-700 font-bold bg-emerald-50 border border-emerald-200 px-2 py-0.5 rounded">
                  HDFC & ICICI Synced
                </span>
              </div>

              {payments.map((p) => {
                const isSelected = p.id === selectedPaymentId;
                const isReconciled = reconciledPayments[p.id];

                return (
                  <div
                    key={p.id}
                    onClick={() => setSelectedPaymentId(p.id)}
                    className={`p-4 rounded-xl border transition-all cursor-pointer text-left ${
                      isSelected
                        ? 'bg-white border-emerald-500 ring-2 ring-emerald-500/20 shadow-warm-md'
                        : 'bg-white/80 hover:bg-white border-slate-200 hover:border-slate-300 shadow-warm-sm'
                    }`}
                  >
                    <div className="flex items-center justify-between mb-1.5">
                      <span className="font-mono text-[11px] font-bold text-slate-500">
                        {p.utr}
                      </span>
                      {isReconciled ? (
                        <span className="inline-flex items-center gap-1 text-[10px] font-sans font-bold text-emerald-700 bg-emerald-100 px-2 py-0.5 rounded-full">
                          <Check className="w-3 h-3" />
                          {isHi ? 'समाधान पूर्ण' : 'Reconciled'}
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 text-[10px] font-sans font-bold text-amber-800 bg-amber-100 px-2 py-0.5 rounded-full">
                          <RefreshCw className="w-2.5 h-2.5 animate-spin" />
                          {isHi ? 'मिलान प्रतीक्षित' : 'Pending Match'}
                        </span>
                      )}
                    </div>

                    <div className="flex items-baseline justify-between mt-1">
                      <div>
                        <div className="text-sm font-sans font-bold text-slate-900">{p.buyer}</div>
                        <div className="text-xs text-slate-500 font-sans">{p.unit} • {p.bank}</div>
                      </div>
                      <div className="text-right">
                        <div className="font-serif font-bold text-base text-slate-950">
                          {formatLakhs(p.amount)}
                        </div>
                        <div className="text-[10px] text-slate-400 font-mono">{p.date}</div>
                      </div>
                    </div>
                  </div>
                );
              })}

              <div className="p-3.5 rounded-xl bg-slate-100/80 border border-slate-200 text-xs text-slate-600 font-sans flex items-start gap-2.5">
                <AlertCircle className="w-4 h-4 text-slate-500 shrink-0 mt-0.5" />
                <span>
                  {isHi
                    ? 'RERA धारा 4 नियम: सभी प्राप्त राशियों का 70% सीधे वैधानिक निर्माण एस्क्रो खाते में जमा होना अनिवार्य है।'
                    : 'RERA Section 4 Rule: 70% of collections are automatically locked in the project escrow and can only be withdrawn upon CA/Architect stage certification.'}
                </span>
              </div>
            </div>

            {/* Right Split Console: 70:30 Statutory Segregation */}
            <div className="lg:col-span-7">
              <GlassCard variant="default" className="p-6 sm:p-8 bg-white border-slate-200 shadow-warm-lg text-left">
                
                {/* Active Payment Header */}
                <div className="flex flex-wrap items-start justify-between gap-4 pb-6 border-b border-slate-200">
                  <div>
                    <div className="text-[11px] font-sans font-bold uppercase tracking-wider text-slate-500 mb-1">
                      {isHi ? 'चयनित रसीद विवरण' : 'Selected Transaction'}
                    </div>
                    <h3 className="font-serif text-2xl font-bold text-slate-950">
                      {activePayment.buyer}
                    </h3>
                    <div className="text-xs text-slate-600 font-sans mt-0.5">
                      Unit: <span className="font-bold text-slate-800">{activePayment.unit}</span> • UTR: <span className="font-mono text-slate-700">{activePayment.utr}</span>
                    </div>
                  </div>

                  <div className="text-right">
                    <div className="text-[11px] font-sans text-slate-500">{isHi ? 'कुल प्राप्त राशि' : 'Total Amount Paid'}</div>
                    <div className="font-serif text-3xl font-bold text-emerald-800">
                      {formatLakhs(activePayment.amount)}
                    </div>
                  </div>
                </div>

                {/* 70:30 Interactive Split Visual */}
                <div className="py-6 space-y-4">
                  <div className="flex items-center justify-between text-xs font-sans font-bold">
                    <span className="text-emerald-900 flex items-center gap-1.5">
                      <span className="w-2.5 h-2.5 rounded-full bg-emerald-600" />
                      70% RERA Construction Escrow (₹{(escrow70 / 100000).toFixed(2)}L)
                    </span>
                    <span className="text-amber-900 flex items-center gap-1.5">
                      <span className="w-2.5 h-2.5 rounded-full bg-amber-500" />
                      30% Developer Operations (₹{(operational30 / 100000).toFixed(2)}L)
                    </span>
                  </div>

                  {/* Visual Split Bar */}
                  <div className="w-full h-4 rounded-full bg-slate-100 overflow-hidden flex p-0.5 border border-slate-200">
                    <div 
                      className="h-full rounded-l-full bg-gradient-to-r from-emerald-600 to-teal-600 transition-all duration-500" 
                      style={{ width: '70%' }} 
                      title="70% RERA Escrow"
                    />
                    <div 
                      className="h-full rounded-r-full bg-gradient-to-r from-amber-500 to-amber-600 transition-all duration-500" 
                      style={{ width: '30%' }} 
                      title="30% Ops & Brokerage"
                    />
                  </div>

                  {/* Dual Account Breakdown Cards */}
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-2">
                    
                    {/* 70% Escrow Account */}
                    <div className="p-4 rounded-xl bg-emerald-50/70 border border-emerald-200">
                      <div className="flex items-center justify-between mb-1">
                        <span className="text-[10px] font-mono font-bold uppercase text-emerald-800">
                          RERA ESCROW ACCOUNT
                        </span>
                        <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-200 text-emerald-900">
                          70% Statutory
                        </span>
                      </div>
                      <div className="font-serif text-2xl font-bold text-emerald-950 mt-1">
                        {formatLakhs(escrow70)}
                      </div>
                      <div className="text-[11px] text-emerald-800/80 font-sans mt-2 space-y-1">
                        <div>• Land acquisition & construction costs</div>
                        <div>• Ring-fenced: No third-party liens</div>
                        <div>• Released only against Architect/CA Form-3</div>
                      </div>
                    </div>

                    {/* 30% Operational Account */}
                    <div className="p-4 rounded-xl bg-amber-50/70 border border-amber-200">
                      <div className="flex items-center justify-between mb-1">
                        <span className="text-[10px] font-mono font-bold uppercase text-amber-800">
                          DEVELOPER OPERATIONS
                        </span>
                        <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-amber-200 text-amber-900">
                          30% Disbursable
                        </span>
                      </div>
                      <div className="font-serif text-2xl font-bold text-amber-950 mt-1">
                        {formatLakhs(operational30)}
                      </div>
                      <div className="text-[11px] text-amber-800/80 font-sans mt-2 space-y-1">
                        <div>• Broker commission payouts & TDS</div>
                        <div>• Administrative overhead & site sales</div>
                        <div>• Developer profit realization</div>
                      </div>
                    </div>

                  </div>
                </div>

                {/* Bottom Action Footer */}
                <div className="pt-4 border-t border-slate-200 flex flex-wrap items-center justify-between gap-3">
                  <div className="flex items-center gap-2 text-xs font-sans text-slate-500">
                    <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                    <span>RERA Project Reg: <strong className="text-slate-800 font-mono">HRERA-PKL-GGM-1294-2024</strong></span>
                  </div>

                  <div className="flex items-center gap-3">
                    {!isSelectedReconciled ? (
                      <button
                        onClick={() => handleReconcile(activePayment.id)}
                        disabled={isReconciling}
                        className="px-5 py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white font-sans font-bold text-xs shadow-warm-sm transition-all flex items-center gap-2"
                      >
                        {isReconciling ? (
                          <>
                            <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                            <span>{isHi ? 'बैंक से मिलान जारी...' : 'Reconciling UTR...'}</span>
                          </>
                        ) : (
                          <>
                            <Check className="w-3.5 h-3.5" />
                            <span>{isHi ? 'UTR सत्यापित व विभाजित करें' : 'Verify UTR & Allocate Split'}</span>
                          </>
                        )}
                      </button>
                    ) : (
                      <div className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-emerald-100 text-emerald-900 text-xs font-sans font-bold">
                        <CheckCircle2 className="w-4 h-4 text-emerald-700" />
                        <span>{isHi ? 'खाते में मिलान पूर्ण' : 'Fully Reconciled & Booked'}</span>
                      </div>
                    )}

                    <button
                      onClick={() => handleDownload('Form-3 RERA CA Certificate')}
                      className="px-4 py-2.5 rounded-xl bg-white hover:bg-slate-50 border border-slate-200 text-slate-800 font-sans font-semibold text-xs shadow-warm-sm transition-all flex items-center gap-1.5"
                      title="Download Form-3 CA Certificate"
                    >
                      <Download className="w-3.5 h-3.5 text-slate-600" />
                      <span>{isHi ? 'CA प्रमाण पत्र' : 'Form-3 CA Export'}</span>
                    </button>
                  </div>
                </div>

              </GlassCard>
            </div>

          </div>
        )}

        {/* Tab 2: Verifiable Digital Allotment Letter */}
        {activeTab === 'allotment' && (
          <div className="max-w-4xl mx-auto">
            <GlassCard variant="default" className="p-8 sm:p-12 bg-white border-slate-200 shadow-warm-lg text-left">
              
              {/* Institutional Letterhead */}
              <div className="flex flex-wrap items-start justify-between gap-6 pb-6 border-b-2 border-slate-900">
                <div>
                  <div className="font-serif font-bold text-2xl tracking-tight text-slate-950">
                    SHARDEYA INFRASTRUCTURE PRIVATE LIMITED
                  </div>
                  <div className="text-xs text-slate-500 font-sans mt-0.5">
                    CIN: U45200HR2018PTC074819 • RERA Registration: HRERA-PKL-GGM-1294-2024
                  </div>
                  <div className="text-xs text-slate-500 font-sans">
                    Apex Greens Integrated Township, Sector 84, Gurugram, Haryana
                  </div>
                </div>

                <div className="text-right">
                  <span className="inline-block px-3 py-1 rounded bg-slate-100 border border-slate-300 font-mono text-xs font-bold text-slate-800">
                    REF # SIPL/ALLOT/2026/0104
                  </span>
                  <div className="text-xs text-slate-500 font-mono mt-1">Date: 10 Sep 2026</div>
                </div>
              </div>

              {/* Title */}
              <div className="text-center py-6">
                <h3 className="font-serif text-xl font-bold uppercase tracking-wide text-slate-900 underline decoration-slate-300 underline-offset-4">
                  PROVISIONAL ALLOTMENT LETTER
                </h3>
                <p className="text-xs text-slate-500 font-sans mt-1">
                  Issued under Section 11 of Real Estate (Regulation and Development) Act, 2016
                </p>
              </div>

              {/* Allottee & Unit Matrix */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-6 p-6 rounded-xl bg-slate-50 border border-slate-200 text-xs font-sans mb-6">
                <div className="space-y-2">
                  <div>
                    <span className="text-slate-500">Primary Allottee:</span>
                    <strong className="block text-sm text-slate-900 font-serif">Dr. Vikramaditya Singhania</strong>
                  </div>
                  <div>
                    <span className="text-slate-500">KYC & Aadhaar Status:</span>
                    <span className="block font-bold text-emerald-700">Verified (UIDAI e-KYC #9482)</span>
                  </div>
                  <div>
                    <span className="text-slate-500">Contact / Email:</span>
                    <span className="block text-slate-800 font-mono">+91 98110 ***** • v.singh****@gmail.com</span>
                  </div>
                </div>

                <div className="space-y-2">
                  <div>
                    <span className="text-slate-500">Unit Allotted:</span>
                    <strong className="block text-sm text-slate-900 font-serif">Villa Plot #104 (Park Facing Corner)</strong>
                  </div>
                  <div>
                    <span className="text-slate-500">Plot Area:</span>
                    <span className="block text-slate-800 font-bold">2,400 sq.ft (266.67 sq.yds)</span>
                  </div>
                  <div>
                    <span className="text-slate-500">Authorized Channel Partner:</span>
                    <span className="block text-slate-800">Kapoor & Associates Syndicate (CP-0042)</span>
                  </div>
                </div>
              </div>

              {/* Financial Consideration Table */}
              <div className="overflow-x-auto mb-6">
                <table className="w-full text-left text-xs font-sans border border-slate-200">
                  <thead className="bg-slate-100 text-slate-700 font-bold uppercase">
                    <tr>
                      <th className="p-3 border-b border-slate-200">Particulars</th>
                      <th className="p-3 border-b border-slate-200 text-right">Calculation Basis</th>
                      <th className="p-3 border-b border-slate-200 text-right">Amount (₹)</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-200 text-slate-800">
                    <tr>
                      <td className="p-3">Basic Plot Consideration Value</td>
                      <td className="p-3 text-right font-mono">2,400 sq.ft @ ₹8,500/sq.ft</td>
                      <td className="p-3 text-right font-mono font-semibold">₹2,04,00,000</td>
                    </tr>
                    <tr>
                      <td className="p-3">Preferential Location Charge (PLC - Corner Park)</td>
                      <td className="p-3 text-right font-mono">5% of Basic Value</td>
                      <td className="p-3 text-right font-mono font-semibold">₹10,20,000</td>
                    </tr>
                    <tr>
                      <td className="p-3">External Development Charges (EDC/IDC)</td>
                      <td className="p-3 text-right font-mono">Statutory Govt Rate</td>
                      <td className="p-3 text-right font-mono font-semibold">₹4,80,000</td>
                    </tr>
                    <tr className="bg-slate-50 font-bold text-slate-950 text-sm">
                      <td className="p-3">Total Agreed Consideration</td>
                      <td className="p-3 text-right font-mono text-xs text-slate-500">Excluding Stamp Duty & Registration</td>
                      <td className="p-3 text-right font-mono text-emerald-800">₹2,19,00,000</td>
                    </tr>
                  </tbody>
                </table>
              </div>

              {/* Stamped Certification & Signatures */}
              <div className="pt-6 border-t border-slate-200 flex flex-wrap items-center justify-between gap-6">
                <div className="flex items-center gap-3">
                  <div className="w-12 h-12 rounded-full border-2 border-emerald-600 border-dashed flex items-center justify-center text-emerald-700">
                    <ShieldCheck className="w-6 h-6" />
                  </div>
                  <div className="text-xs font-sans">
                    <div className="font-bold text-slate-900">Digitally Authenticated</div>
                    <div className="text-slate-500 font-mono text-[11px]">Cert # E-SIGN-IN-981048</div>
                    <div className="text-[10px] text-emerald-700 font-bold">RERA 70:30 Statutory Ring-Fence Verified</div>
                  </div>
                </div>

                <div className="flex items-center gap-3">
                  <button
                    onClick={() => handleDownload('Provisional Allotment Letter #104')}
                    className="px-5 py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white font-sans font-bold text-xs shadow-warm-sm transition-all flex items-center gap-2"
                  >
                    <Download className="w-3.5 h-3.5" />
                    <span>{isHi ? 'डिजिटल आवंटन पत्र डाउनलोड करें' : 'Download Stamped PDF'}</span>
                  </button>
                  <button
                    onClick={() => handleDownload('Payment Milestone Schedule')}
                    className="px-4 py-2.5 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-800 font-sans font-semibold text-xs border border-slate-200 transition-all flex items-center gap-1.5"
                  >
                    <ExternalLink className="w-3.5 h-3.5" />
                    <span>{isHi ? 'किस्त शेड्यूल' : 'Milestone Schedule'}</span>
                  </button>
                </div>
              </div>

            </GlassCard>
          </div>
        )}

        {/* Download Feedback Toast */}
        {downloadToast && (
          <div className="fixed bottom-6 right-6 z-50 bg-slate-900 text-white px-5 py-3 rounded-xl shadow-warm-lg flex items-center gap-3 font-sans text-xs border border-slate-700 animate-fade-in">
            <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
            <span>Generated & verified: <strong>{downloadToast}</strong></span>
          </div>
        )}

      </div>
    </section>
  );
};
