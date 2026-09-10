import React, { useState } from 'react';
import { CornerDownLeft, Sparkles } from 'lucide-react';
import { useLanguage } from '../../context/LanguageContext';
import { GlassCard } from '../common/GlassCard';

export const AiCopilotPreview: React.FC = () => {
  const { t, language } = useLanguage();
  const [activePrompt, setActivePrompt] = useState<number>(1);
  const [isTyping, setIsTyping] = useState<boolean>(false);

  const isHi = language === 'hi';

  const prompts = [
    {
      id: 1,
      textEn: "Show overdue payments for Tower B in Q3",
      textHi: "टावर B में Q3 के सभी बकाया भुगतान दिखाएं",
    },
    {
      id: 2,
      textEn: "Which broker has the highest conversion in Sector 82?",
      textHi: "सेक्टर 82 में किस ब्रोकर का कन्वर्शन सबसे अधिक है?",
    },
    {
      id: 3,
      textEn: "Draft bilingual site visit follow-up on WhatsApp",
      textHi: "हिंदी व अंग्रेजी में व्हाट्सएप साइट विजिट फॉलो-अप ड्राफ्ट करें",
    },
  ];

  const handleSelectPrompt = (id: number) => {
    setIsTyping(true);
    setActivePrompt(id);
    setTimeout(() => {
      setIsTyping(false);
    }, 350);
  };

  return (
    <section className="py-20 relative bg-sand-100 border-b border-sand-300">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        
        {/* Section Header */}
        <div className="text-center max-w-3xl mx-auto mb-12">
          <div className="inline-flex items-center gap-2 px-3.5 py-1 rounded-full border border-sand-300 bg-sand-50 text-espresso-800 text-xs font-sans mb-3 shadow-warm-sm">
            <span className="w-2 h-2 rounded-full bg-forest" />
            <span className="font-semibold">{t.copilot.tag}</span>
          </div>
          <h2 className="text-3xl sm:text-4xl lg:text-5xl font-serif text-espresso-950 font-normal tracking-tight">
            {t.copilot.title}
          </h2>
          <p className="mt-4 text-base text-espresso-700 font-sans">
            {t.copilot.subtitle}
          </p>
        </div>

        {/* AI Console Container */}
        <div className="max-w-4xl mx-auto">
          <GlassCard variant="default" className="p-6 sm:p-8 text-left bg-white border-sand-300 shadow-warm-md">
            
            {/* Quick Prompt Pill Buttons */}
            <div className="flex flex-wrap gap-2 mb-6 font-sans text-xs">
              <span className="text-espresso-500 self-center mr-1 text-[11px] uppercase font-bold">
                {isHi ? 'प्रॉम्प्ट चुनें:' : 'Sample Questions:'}
              </span>
              {prompts.map((p) => (
                <button
                  key={p.id}
                  onClick={() => handleSelectPrompt(p.id)}
                  className={`px-3 py-1.5 rounded-lg transition-all font-medium ${
                    activePrompt === p.id
                      ? 'bg-forest text-white font-bold shadow-warm-sm'
                      : 'bg-sand-100 text-espresso-800 hover:bg-sand-200 border border-sand-300'
                  }`}
                >
                  {isHi ? p.textHi : p.textEn}
                </button>
              ))}
            </div>

            {/* Simulated Query Box */}
            <div className="relative mb-6">
              <div className="w-full pl-4 pr-12 py-3 bg-sand-50 border border-sand-300 rounded-xl text-espresso-900 text-xs font-sans flex items-center shadow-warm-sm">
                <span className="text-forest mr-2 font-bold text-sm">›</span>
                <span className="truncate font-medium">
                  {isHi ? prompts.find(p => p.id === activePrompt)?.textHi : prompts.find(p => p.id === activePrompt)?.textEn}
                </span>
                <button className="absolute right-2 p-1.5 rounded-lg bg-forest text-white hover:bg-forest-light transition-colors shadow-warm-sm">
                  <CornerDownLeft className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>

            {/* Response Output Panel */}
            <div className="p-5 sm:p-6 rounded-xl bg-sand-50 border border-sand-300 space-y-4 font-sans">
              <div className="flex items-center justify-between pb-3 border-b border-sand-200 text-xs">
                <div className="flex items-center gap-2 font-serif font-bold text-espresso-950">
                  <Sparkles className="w-4 h-4 text-forest" />
                  <span>{t.copilot.queryResultTitle}</span>
                </div>
                <span className="text-[10px] font-sans font-bold text-forest bg-forest-subtle px-2 py-0.5 rounded border border-forest/20">
                  INSTANT REPORT • 0.2s
                </span>
              </div>

              {isTyping ? (
                <div className="py-8 flex items-center justify-center gap-2 text-forest text-xs font-sans">
                  <span className="w-2 h-2 rounded-full bg-forest animate-pulse" />
                  <span>Analyzing records...</span>
                </div>
              ) : (
                <div>
                  {activePrompt === 1 && (
                    <div className="space-y-3 text-xs">
                      <p className="text-espresso-800">
                        {isHi
                          ? 'टावर B के विश्लेषण में कुल 4 इकाइयों पर ₹67,50,000 की राशि 15+ दिनों से देय पाई गई है। विवरण नीचे दिया गया है:'
                          : 'Found 2 payments overdue past the 15-day grace period in Tower B:'}
                      </p>

                      <div className="space-y-2 mt-2">
                        <div className="p-3 rounded-lg bg-white border border-sand-300 flex items-center justify-between shadow-warm-sm">
                          <div>
                            <div className="font-bold text-espresso-950 font-sans">Unit B-801 • Deepak Chawla</div>
                            <div className="text-[10px] text-espresso-500">Milestone: 4th Slab Casting • 12 Days Overdue</div>
                          </div>
                          <div className="text-right">
                            <div className="font-bold text-espresso-950 font-sans">₹33,75,000</div>
                            <button
                              onClick={() => alert("Automated demand dispatch sent to Deepak Chawla")}
                              className="text-[10px] text-forest underline font-bold mt-0.5"
                            >
                              Send Reminder
                            </button>
                          </div>
                        </div>

                        <div className="p-3 rounded-lg bg-white border border-sand-300 flex items-center justify-between shadow-warm-sm">
                          <div>
                            <div className="font-bold text-espresso-950 font-sans">Unit B-403 • R. K. Malhotra</div>
                            <div className="text-[10px] text-espresso-500">Milestone: Plinth Completion • 6 Days Overdue</div>
                          </div>
                          <div className="text-right">
                            <div className="font-bold text-espresso-950 font-sans">₹33,75,000</div>
                            <button
                              onClick={() => alert("Automated demand dispatch sent to R.K. Malhotra")}
                              className="text-[10px] text-forest underline font-bold mt-0.5"
                            >
                              Send Reminder
                            </button>
                          </div>
                        </div>
                      </div>

                      <div className="pt-2">
                        <button
                          onClick={() => alert("Batch WhatsApp reminders sent to all buyers.")}
                          className="px-4 py-2 rounded-lg bg-forest text-white font-bold text-xs shadow-warm-sm hover:bg-forest-light transition-colors font-sans"
                        >
                          {isHi ? 'सभी खरीदारों को 1-क्लिक रिमाइंडर भेजें' : 'Send Reminders to All Buyers'}
                        </button>
                      </div>
                    </div>
                  )}

                  {activePrompt === 2 && (
                    <div className="space-y-3 text-xs">
                      <p className="text-espresso-800">
                        {isHi
                          ? 'सेक्टर 82 परियोजना के लिए 32 सक्रिय ब्रोकरों का विश्लेषण पूर्ण हुआ। शीर्ष प्रदर्शनकर्ता:'
                          : 'Top converting partner for Sector 82 project:'}
                      </p>

                      <div className="p-4 rounded-lg bg-white border border-sand-300 flex items-center justify-between shadow-warm-sm">
                        <div className="flex items-center gap-3">
                          <div className="w-9 h-9 rounded-lg bg-bronze-subtle text-bronze flex items-center justify-center font-bold text-sm font-serif">
                            #1
                          </div>
                          <div>
                            <div className="font-bold text-espresso-950 font-sans">Kapoor & Associates Syndicate</div>
                            <div className="text-[11px] text-espresso-600">
                              Conversion Rate: <span className="text-forest font-bold">42.8%</span> (Average: 14%)
                            </div>
                          </div>
                        </div>
                        <div className="text-right font-sans">
                          <div className="font-bold text-espresso-950">₹38.5 Cr Realized</div>
                          <div className="text-[10px] text-espresso-500">14 Units Closed</div>
                        </div>
                      </div>

                      <div className="text-espresso-700 text-[11px] font-sans">
                        💡 <strong>Suggestion:</strong> Assign 4 exclusive pre-launch penthouses from Tower C to Kapoor Syndicate to accelerate sell-out.
                      </div>
                    </div>
                  )}

                  {activePrompt === 3 && (
                    <div className="space-y-3 text-xs">
                      <p className="text-espresso-800">
                        {isHi
                          ? 'साइट विजिट के बाद का व्यक्तिगत व्हाट्सएप संदेश तैयार है:'
                          : 'Personalized post-site-visit WhatsApp follow-up template ready:'}
                      </p>

                      <div className="p-3.5 rounded-lg bg-white border border-sand-300 space-y-2 text-espresso-900 font-sans text-[11px]">
                        <div>
                          <strong>[English]:</strong> "Dear Mr. [Buyer_Name], thank you for visiting Shardeya Estate today! Here is the floor plan and project brochure: [Link]. Plot [Unit_ID] is kept on priority hold for you until Tuesday 6 PM."
                        </div>
                        <div className="pt-2 border-t border-sand-200 text-forest">
                          <strong>[हिन्दी]:</strong> "आदरणीय [Buyer_Name] जी, शार्देय एस्टेट के निरीक्षण हेतु सादर धन्यवाद! प्लॉट का नक्शा और ब्रोशर यहाँ देखें: [Link]। प्लॉट [Unit_ID] मंगलवार शाम 6 बजे तक आपके लिए होल्ड पर है।"
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              )}
            </div>

          </GlassCard>
        </div>

      </div>
    </section>
  );
};
