import React, { useState } from 'react';
import { X, CheckCircle2, Building2, Phone, Mail, User } from 'lucide-react';
import confetti from 'canvas-confetti';
import { useLanguage } from '../../context/LanguageContext';

interface DemoModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const DemoModal: React.FC<DemoModalProps> = ({ isOpen, onClose }) => {
  const { language } = useLanguage();
  const [submitted, setSubmitted] = useState(false);
  const [formData, setFormData] = useState({
    name: '',
    email: '',
    phone: '',
    organization: '',
    projectUnits: '50-200 Units',
  });

  if (!isOpen) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitted(true);
    confetti({
      particleCount: 80,
      spread: 60,
      origin: { y: 0.6 },
      colors: ['#1B382B', '#8F7348', '#292524'],
    });
  };

  const isHi = language === 'hi';

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-espresso-950/40 backdrop-blur-sm animate-fadeIn">
      <div className="relative w-full max-w-lg p-6 sm:p-8 bg-sand-50 border border-sand-300 rounded-2xl shadow-warm-lg text-left">
        {/* Close */}
        <button
          onClick={onClose}
          className="absolute top-5 right-5 p-2 text-espresso-500 hover:text-espresso-800 rounded-lg bg-sand-150 hover:bg-sand-200 transition-colors"
        >
          <X className="w-4 h-4" />
        </button>

        {!submitted ? (
          <div>
            <div className="flex items-center gap-2 px-3 py-1 mb-4 rounded-full bg-forest-subtle border border-forest/20 w-fit">
              <span className="w-1.5 h-1.5 rounded-full bg-forest" />
              <span className="text-[10px] font-mono font-bold tracking-wider uppercase text-forest">
                {isHi ? 'लाइव डेमो' : '15-MINUTE PRODUCT WALKTHROUGH'}
              </span>
            </div>

            <h3 className="text-2xl font-serif font-bold text-espresso-950 mb-2">
              {isHi ? 'शार्देय का लाइव डेमो बुक करें' : 'Book a Personal Demo'}
            </h3>
            <p className="text-sm text-espresso-700 mb-6 font-sans">
              {isHi
                ? 'हमारे रियल एस्टेट विशेषज्ञ आपको दिखाएंगे कि कैसे आप अपने प्लॉट्स, ब्रोकर कमीशन, पेमेंट्स और व्हाट्सएप अलर्ट्स को आसानी से मैनेज कर सकते हैं।'
                : 'See how Shardeya manages your plots, broker commissions, customer payments, and WhatsApp site visit alerts for your projects.'}
            </p>

            <form onSubmit={handleSubmit} className="space-y-4 font-sans text-xs">
              <div>
                <label className="block font-bold text-espresso-800 mb-1.5 font-sans uppercase text-[11px] tracking-wider">
                  {isHi ? 'आपका पूरा नाम' : 'FULL NAME'}
                </label>
                <div className="relative">
                  <User className="absolute left-3.5 top-3 w-4 h-4 text-espresso-400" />
                  <input
                    required
                    type="text"
                    value={formData.name}
                    onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                    placeholder={isHi ? 'उदा. विक्रम राठौड़' : 'e.g. Vikramaditya Singhania'}
                    className="w-full pl-10 pr-4 py-2.5 bg-white border border-sand-300 rounded-lg text-espresso-900 placeholder:text-espresso-400 text-xs focus:outline-none focus:border-forest focus:bg-sand-50 transition-colors"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block font-bold text-espresso-800 mb-1.5 font-sans uppercase text-[11px] tracking-wider">
                    {isHi ? 'कार्य ईमेल' : 'CORPORATE EMAIL'}
                  </label>
                  <div className="relative">
                    <Mail className="absolute left-3.5 top-3 w-4 h-4 text-espresso-400" />
                    <input
                      required
                      type="email"
                      value={formData.email}
                      onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                      placeholder="vikram@realty.com"
                      className="w-full pl-10 pr-4 py-2.5 bg-white border border-sand-300 rounded-lg text-espresso-900 placeholder:text-espresso-400 text-xs focus:outline-none focus:border-forest focus:bg-sand-50 transition-colors"
                    />
                  </div>
                </div>

                <div>
                  <label className="block font-bold text-espresso-800 mb-1.5 font-sans uppercase text-[11px] tracking-wider">
                    {isHi ? 'व्हाट्सएप नंबर' : 'WHATSAPP / MOBILE'}
                  </label>
                  <div className="relative">
                    <Phone className="absolute left-3.5 top-3 w-4 h-4 text-espresso-400" />
                    <input
                      required
                      type="tel"
                      value={formData.phone}
                      onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
                      placeholder="+91 98XXX XXXXX"
                      className="w-full pl-10 pr-4 py-2.5 bg-white border border-sand-300 rounded-lg text-espresso-900 placeholder:text-espresso-400 text-xs focus:outline-none focus:border-forest focus:bg-sand-50 transition-colors"
                    />
                  </div>
                </div>
              </div>

              <div>
                <label className="block font-bold text-espresso-800 mb-1.5 font-sans uppercase text-[11px] tracking-wider">
                  {isHi ? 'कंपनी / डेवलपर फर्म' : 'DEVELOPER / BROKERAGE FIRM'}
                </label>
                <div className="relative">
                  <Building2 className="absolute left-3.5 top-3 w-4 h-4 text-espresso-400" />
                  <input
                    required
                    type="text"
                    value={formData.organization}
                    onChange={(e) => setFormData({ ...formData, organization: e.target.value })}
                    placeholder={isHi ? 'उदा. सुप्रीम इंफ्रास्ट्रक्चर' : 'e.g. Apex Luxury Real Estate'}
                    className="w-full pl-10 pr-4 py-2.5 bg-white border border-sand-300 rounded-lg text-espresso-900 placeholder:text-espresso-400 text-xs focus:outline-none focus:border-forest focus:bg-sand-50 transition-colors"
                  />
                </div>
              </div>

              <div>
                <label className="block font-bold text-espresso-800 mb-1.5 font-sans uppercase text-[11px] tracking-wider">
                  {isHi ? 'परियोजना का पैमाना' : 'ACTIVE PORTFOLIO SCALE'}
                </label>
                <select
                  value={formData.projectUnits}
                  onChange={(e) => setFormData({ ...formData, projectUnits: e.target.value })}
                  className="w-full px-4 py-2.5 bg-white border border-sand-300 rounded-lg text-espresso-900 text-xs focus:outline-none focus:border-forest focus:bg-sand-50 transition-colors font-sans"
                >
                  <option value="1-50 Units">{isHi ? '1 - 50 इकाइयां (Boutique Development)' : '1 - 50 Units (Boutique Development)'}</option>
                  <option value="50-200 Units">{isHi ? '50 - 200 इकाइयां (High-Rise Residential)' : '50 - 200 Units (High-Rise Residential)'}</option>
                  <option value="200-1000 Units">{isHi ? '200 - 1000 इकाइयां (Integrated Plotted Estate)' : '200 - 1,000 Units (Integrated Plotted Estate)'}</option>
                  <option value="1000+ Units">{isHi ? '1000+ इकाइयां (Multi-City Syndicate)' : '1,000+ Units (Multi-City Syndicate)'}</option>
                </select>
              </div>

              <button
                type="submit"
                className="w-full mt-2 py-3.5 px-6 rounded-lg bg-forest hover:bg-forest-light text-white font-bold text-xs uppercase tracking-wider shadow-warm-sm transition-all flex items-center justify-center gap-2"
              >
                <span>{isHi ? 'डेमो बुक करें' : 'Book My Demo'}</span>
              </button>
            </form>
          </div>
        ) : (
          <div className="py-8 text-center space-y-4 font-sans">
            <div className="w-14 h-14 mx-auto rounded-full bg-forest-subtle border border-forest/30 flex items-center justify-center text-forest">
              <CheckCircle2 className="w-7 h-7" />
            </div>
            <h3 className="text-2xl font-serif font-bold text-espresso-950">
              {isHi ? 'डेमो बुक हो गया!' : 'Demo Scheduled!'}
            </h3>
            <p className="text-xs text-espresso-700 max-w-sm mx-auto leading-relaxed">
              {isHi
                ? `धन्यवाद ${formData.name}! हमारी टीम 15 मिनट में आपके व्हाट्सएप (${formData.phone}) पर आमंत्रण भेज देगी।`
                : `Thank you, ${formData.name}! Our team will send the calendar invite directly to your WhatsApp (${formData.phone}) within 15 minutes.`}
            </p>
            <button
              onClick={() => {
                setSubmitted(false);
                onClose();
              }}
              className="mt-4 px-6 py-2.5 rounded-lg bg-sand-200 hover:bg-sand-300 text-espresso-900 text-xs font-bold font-sans transition-colors shadow-warm-sm"
            >
              CLOSE
            </button>
          </div>
        )}
      </div>
    </div>
  );
};
