import React, { useState } from 'react';
import { MessageSquare, CheckCheck, MapPin, Calendar, CreditCard, Sparkles, Send, PhoneCall, Video } from 'lucide-react';
import { sampleWhatsAppConversations } from '../../data/sampleData';
import { useLanguage } from '../../context/LanguageContext';

export const WhatsAppSimulator: React.FC = () => {
  const { t, language } = useLanguage();
  const [activeScenario, setActiveScenario] = useState<'siteVisit' | 'paymentAlert' | 'aiInquiry'>('siteVisit');
  const [phoneLang, setPhoneLang] = useState<'en' | 'hi'>(language);

  React.useEffect(() => {
    setPhoneLang(language);
  }, [language]);

  const isHi = phoneLang === 'hi';
  const messages = sampleWhatsAppConversations[activeScenario];

  return (
    <section id="whatsapp" className="py-24 relative bg-slate-50/60 border-b border-slate-200">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        
        {/* Section Header */}
        <div className="text-center max-w-3xl mx-auto mb-16">
          <div className="inline-flex items-center gap-2 px-3.5 py-1 rounded-full border border-emerald-200 bg-emerald-50 text-emerald-900 text-xs font-sans mb-3 shadow-warm-sm">
            <span className="w-2 h-2 rounded-full bg-emerald-600" />
            <span className="font-semibold">{t.whatsapp.tag}</span>
          </div>
          <h2 className="text-3xl sm:text-4xl lg:text-5xl font-serif text-slate-950 font-normal tracking-tight">
            {t.whatsapp.title}
          </h2>
          <p className="mt-4 text-base text-slate-600 font-sans">
            {t.whatsapp.subtitle}
          </p>
        </div>

        {/* Content */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 items-center">
          
          {/* Left Controls */}
          <div className="lg:col-span-6 space-y-6 text-left">
            <h3 className="text-2xl font-serif font-bold text-slate-950">
              {isHi ? 'ऑटोमेशन टेस्ट करें' : 'Try WhatsApp Scenarios'}
            </h3>
            <p className="text-sm text-slate-600 leading-relaxed font-sans">
              {isHi
                ? 'शार्देय आधिकारिक व्हाट्सएप बिजनेस API से सीधे जुड़ता है। साइट विजिट का गूगल मैप्स पिन भेजना हो या किस्तों का रिमाइंडर — सब कुछ बिना किसी मानवीय गलती के आसानी से होता है।'
                : 'Connect directly to the official WhatsApp Business Cloud API. Send automated site visit reminders with Google Maps pins, milestone payment alerts, and instant customer replies.'}
            </p>

            {/* Scenario Selector Tabs */}
            <div className="space-y-3 font-sans">
              <button
                onClick={() => setActiveScenario('siteVisit')}
                className={`w-full p-4 rounded-xl border text-left transition-all flex items-start gap-4 ${
                  activeScenario === 'siteVisit'
                    ? 'border-emerald-500 bg-white shadow-md ring-2 ring-emerald-400/80 -translate-y-0.5'
                    : 'border-slate-200 bg-white hover:bg-slate-50 hover:border-emerald-300'
                }`}
              >
                <div className="p-2.5 rounded-lg bg-emerald-100 text-emerald-800 shrink-0">
                  <MapPin className="w-5 h-5" />
                </div>
                <div>
                  <div className="text-sm font-bold text-slate-900">{t.whatsapp.btnSiteVisit}</div>
                  <div className="text-xs text-slate-600 mt-0.5">
                    {isHi
                      ? 'गूगल मैप्स लोकेशन पिन, गोल्फ कार्ट और साइट मैनेजर नंबर'
                      : 'Google Maps pin, entry gate details, and site manager contact'}
                  </div>
                </div>
              </button>

              <button
                onClick={() => setActiveScenario('paymentAlert')}
                className={`w-full p-4 rounded-xl border text-left transition-all flex items-start gap-4 ${
                  activeScenario === 'paymentAlert'
                    ? 'border-amber-500 bg-white shadow-md ring-2 ring-amber-400/80 -translate-y-0.5'
                    : 'border-slate-200 bg-white hover:bg-slate-50 hover:border-amber-300'
                }`}
              >
                <div className="p-2.5 rounded-lg bg-amber-100 text-amber-800 shrink-0">
                  <CreditCard className="w-5 h-5" />
                </div>
                <div>
                  <div className="text-sm font-bold text-slate-900">{t.whatsapp.btnPaymentAlert}</div>
                  <div className="text-xs text-slate-600 mt-0.5">
                    {isHi
                      ? 'कंस्ट्रक्शन स्लैब की फोटो, देय किस्त और डिजिटल रसीद'
                      : 'Construction milestone photo, invoice amount & digital receipt'}
                  </div>
                </div>
              </button>

              <button
                onClick={() => setActiveScenario('aiInquiry')}
                className={`w-full p-4 rounded-xl border text-left transition-all flex items-start gap-4 ${
                  activeScenario === 'aiInquiry'
                    ? 'border-indigo-500 bg-white shadow-md ring-2 ring-indigo-400/80 -translate-y-0.5'
                    : 'border-slate-200 bg-white hover:bg-slate-50 hover:border-indigo-300'
                }`}
              >
                <div className="p-2.5 rounded-lg bg-sand-200 text-espresso-800 shrink-0">
                  <Sparkles className="w-5 h-5" />
                </div>
                <div>
                  <div className="text-sm font-bold text-espresso-900">{t.whatsapp.btnAiInquiry}</div>
                  <div className="text-xs text-espresso-600 mt-0.5">
                    {isHi
                      ? 'हिंदी भाषा में ग्राहकों के सवालों के त्वरित उत्तर व मीटिंग बुकिंग'
                      : 'Automatic responses to buyer inquiries & calendar scheduling'}
                  </div>
                </div>
              </button>
            </div>

            {/* Language Switcher */}
            <div className="p-3.5 rounded-xl bg-sand-50 border border-sand-300 shadow-warm-sm flex items-center justify-between text-xs">
              <span className="text-espresso-700 font-sans font-medium">
                {isHi ? 'चैट की भाषा बदलें:' : 'Message Language:'}
              </span>
              <div className="flex gap-1.5 font-sans">
                <button
                  onClick={() => setPhoneLang('en')}
                  className={`px-3 py-1 rounded-md text-xs font-bold transition-colors ${
                    phoneLang === 'en' ? 'bg-forest text-white' : 'bg-sand-200 text-espresso-700'
                  }`}
                >
                  English
                </button>
                <button
                  onClick={() => setPhoneLang('hi')}
                  className={`px-3 py-1 rounded-md text-xs font-bold transition-colors ${
                    phoneLang === 'hi' ? 'bg-forest text-white' : 'bg-sand-200 text-espresso-700'
                  }`}
                >
                  हिन्दी
                </button>
              </div>
            </div>
          </div>

          {/* Right Phone Mockup */}
          <div className="lg:col-span-6 flex justify-center">
            <div className="w-full max-w-[370px] rounded-[40px] p-3 bg-stone-300 border-[2px] border-stone-400 shadow-warm-lg relative">
              <div className="w-24 h-3.5 bg-stone-800 rounded-full mx-auto mb-2" />

              <div className="w-full h-[560px] rounded-[30px] bg-[#EFEAE2] overflow-hidden flex flex-col relative border border-stone-300">
                {/* Header */}
                <div className="bg-[#1B4332] px-4 py-3 flex items-center justify-between text-white shadow-warm-sm">
                  <div className="flex items-center gap-3">
                    <div className="w-8 h-8 rounded-full bg-white flex items-center justify-center font-bold text-forest text-xs">
                      S
                    </div>
                    <div>
                      <div className="text-xs font-bold flex items-center gap-1 font-sans">
                        <span>Shardeya Bot</span>
                        <CheckCheck className="w-3.5 h-3.5 text-bronze-light" />
                      </div>
                      <div className="text-[10px] text-white/80 font-sans">
                        {t.whatsapp.onlineStatus}
                      </div>
                    </div>
                  </div>
                  <div className="flex items-center gap-3 text-white/80">
                    <Video className="w-4 h-4" />
                    <PhoneCall className="w-4 h-4" />
                  </div>
                </div>

                {/* Messages */}
                <div className="flex-1 p-3.5 space-y-3 overflow-y-auto">
                  <div className="text-center">
                    <span className="text-[9px] bg-white/90 text-espresso-600 px-2 py-0.5 rounded uppercase font-sans font-bold border border-sand-300">
                      Verified Business Message
                    </span>
                  </div>

                  {messages.map((msg) => {
                    const isBot = msg.sender === 'bot';
                    const text = isHi ? msg.textHi : msg.textEn;

                    return (
                      <div
                        key={msg.id}
                        className={`flex ${isBot ? 'justify-start' : 'justify-end'} text-left`}
                      >
                        <div
                          className={`max-w-[85%] rounded-xl p-3 text-xs shadow-warm-sm ${
                            isBot
                              ? 'bg-white text-espresso-950 rounded-tl-none border border-sand-200'
                              : 'bg-[#D9FDD3] text-espresso-950 rounded-tr-none'
                          }`}
                        >
                          <p className="whitespace-pre-line leading-relaxed font-sans">{text}</p>

                          {msg.type === 'map' && (
                            <div className="mt-2.5 p-2 rounded-lg bg-sand-100 border border-sand-300 flex items-center gap-2 text-[11px] text-espresso-800 font-bold font-sans">
                              <MapPin className="w-4 h-4 text-forest shrink-0" />
                              <span className="truncate">{msg.mapTitle}</span>
                            </div>
                          )}

                          {msg.type === 'payment_link' && (
                            <div className="mt-2.5 p-2 rounded-lg bg-bronze-subtle border border-bronze/40 flex items-center justify-between text-[11px]">
                              <span className="text-bronze-dark font-serif font-bold text-sm">{msg.amount}</span>
                              <span className="px-2 py-0.5 rounded bg-forest text-white font-sans font-bold text-[10px]">
                                Pay via RTGS / UPI
                              </span>
                            </div>
                          )}

                          {msg.type === 'calendar' && (
                            <div className="mt-2.5 p-2 rounded-lg bg-sand-100 border border-sand-300 flex items-center gap-2 text-[11px] text-espresso-800 font-bold font-sans">
                              <Calendar className="w-4 h-4 text-forest" />
                              <span>Calendar Invite Sent</span>
                            </div>
                          )}

                          <div className="text-[9px] text-espresso-400 text-right mt-1 flex items-center justify-end gap-1 font-sans">
                            <span>{msg.time}</span>
                            {isBot && <CheckCheck className="w-3 h-3 text-forest" />}
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>

                {/* Footer */}
                <div className="bg-[#F0F2F5] p-2 flex items-center gap-2 border-t border-stone-300">
                  <div className="flex-1 bg-white rounded-full px-3 py-1.5 text-xs text-espresso-400 text-left truncate border border-stone-200 font-sans">
                    {isHi ? 'संदेश लिखें...' : 'Type message...'}
                  </div>
                  <div className="w-7 h-7 rounded-full bg-forest flex items-center justify-center text-white shadow-warm-sm">
                    <Send className="w-3.5 h-3.5" />
                  </div>
                </div>

              </div>
            </div>
          </div>

        </div>

      </div>
    </section>
  );
};
