import React from 'react';
import { Globe, ArrowUp } from 'lucide-react';
import { useLanguage } from '../../context/LanguageContext';

export const Footer: React.FC = () => {
  const { t, language, toggleLanguage } = useLanguage();
  const isHi = language === 'hi';

  const scrollToTop = () => {
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  return (
    <footer className="bg-sand-150 border-t border-sand-300 py-12 relative overflow-hidden text-left">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-6 pb-8 border-b border-sand-300">
          
          {/* Brand */}
          <div className="space-y-3">
            <div className="flex items-center gap-3">
              <div className="w-8 h-8 rounded-lg bg-forest flex items-center justify-center text-white font-serif font-bold text-base shadow-warm-sm">
                S
              </div>
              <span className="font-serif font-bold text-2xl tracking-tight text-espresso-950">
                SHARDEYA
              </span>
            </div>

            <p className="text-xs sm:text-sm text-espresso-700 max-w-md leading-relaxed font-sans">
              {isHi
                ? 'शार्देय — रियल एस्टेट बिल्डर्स, डेवलपर्स और ब्रोकरों के लिए आसान और संपूर्ण ऑपरेटिंग प्लेटफॉर्म।'
                : 'The all-in-one real estate platform and CRM built for modern property builders, developers, and brokers.'}
            </p>
          </div>

          {/* System Status & Actions */}
          <div className="flex flex-wrap items-center gap-4">
            <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-sand-50 border border-sand-300">
              <span className="w-2 h-2 rounded-full bg-forest" />
              <span className="text-xs font-mono font-bold text-espresso-800">
                {t.footer.systemStatus}
              </span>
            </div>

            <button
              onClick={toggleLanguage}
              className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-sand-50 border border-sand-300 text-espresso-800 transition-colors shadow-warm-sm font-bold text-xs font-mono hover:bg-sand-200"
            >
              <Globe className="w-3.5 h-3.5 text-forest" />
              <span>{isHi ? 'Switch to English' : 'हिन्दी में देखें'}</span>
            </button>

            <button
              onClick={scrollToTop}
              className="p-2 rounded-lg bg-sand-50 border border-sand-300 text-espresso-700 transition-colors shadow-warm-sm hover:bg-sand-200"
              title="Scroll to Top"
            >
              <ArrowUp className="w-4 h-4" />
            </button>
          </div>

        </div>

        {/* Bottom Copyright */}
        <div className="pt-6 text-xs text-espresso-600 font-mono text-center sm:text-left">
          © {new Date().getFullYear()} Shardeya Group Pvt. Ltd. {t.footer.rights}
        </div>

      </div>
    </footer>
  );
};
