import React from 'react';
import { useLanguage } from '../../context/LanguageContext';

export const StatsTicker: React.FC = () => {
  const { language } = useLanguage();
  const isHi = language === 'hi';

  const stats = [
    {
      value: '100% Escrow',
      label: isHi ? 'धारा 4 वैधानिक 70:30 विभाजन' : 'Section 4 Statutory Escrow',
      footnote: isHi ? '70% निर्माण व 30% संचालन' : 'Automated 70:30 bank split',
    },
    {
      value: '15-Min Match',
      label: isHi ? 'RTGS व UTR समाधान' : 'Bank UTR Reconciliation',
      footnote: isHi ? 'मांग पत्र व किस्तों का मिलान' : 'Against buyer milestone dues',
    },
    {
      value: 'Zero Disputes',
      label: isHi ? 'ब्रोकर कमीशन व स्लैब सटीकता' : 'CP Commission Integrity',
      footnote: isHi ? 'लीड लॉक व स्लैब सुरक्षा' : 'Timestamped leads & tier tracking',
    },
    {
      value: 'द्विभाषी / Dual',
      label: isHi ? 'हिंदी व अंग्रेजी दोनों में' : 'Native English & हिन्दी',
      footnote: isHi ? 'साइट व सेल्स टीम हेतु उपयुक्त' : 'For site supervisors & CP teams',
    },
  ];

  return (
    <div className="relative border-y border-slate-200 bg-white/80 backdrop-blur-md py-10 shadow-warm-sm">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-8 divide-y sm:divide-y-0 sm:divide-x divide-slate-200">
          {stats.map((stat, idx) => (
            <div
              key={idx}
              className={`text-left group transition-transform hover:-translate-y-0.5 ${idx > 0 ? 'sm:pl-8' : ''} ${idx > 1 ? 'pt-6 sm:pt-0' : ''}`}
            >
              <div className="font-serif text-3xl sm:text-4xl font-bold bg-gradient-to-r from-emerald-700 via-teal-700 to-slate-900 bg-clip-text text-transparent tracking-tight mb-1">
                {stat.value}
              </div>
              <div className="text-xs font-sans font-bold text-slate-800 mb-0.5 flex items-center gap-1.5">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 opacity-80 group-hover:scale-125 transition-transform" />
                <span>{stat.label}</span>
              </div>
              <div className="text-[11px] font-sans text-slate-500 font-medium">
                {stat.footnote}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};
