import React from 'react';
import { Quote, CheckCircle2 } from 'lucide-react';
import { useLanguage } from '../../context/LanguageContext';
import { GlassCard } from '../common/GlassCard';

export const TestimonialsSection: React.FC = () => {
  const { t, language } = useLanguage();
  const isHi = language === 'hi';

  const testimonials = [
    {
      nameEn: "Rajeshwar Singhal",
      nameHi: "राजेश्वर सिंघल",
      roleEn: "Managing Director, Apex Greens Township (₹850 Cr Portfolio)",
      roleHi: "प्रबंध निदेशक, एपेक्स ग्रीन्स टाउनशिप (₹850 करोड़ पोर्टफोलियो)",
      avatar: "RS",
      quoteEn: "Shardeya transformed our entire sales velocity. In our previous phase, we spent weeks tracking which plot was blocked and reconciling bank payments. With Shardeya's interactive masterplan and WhatsApp alerts, we sold out Phase 2 in just 42 days with 99.8% on-time milestone payments.",
      quoteHi: "शार्देय ने हमारी बिक्री की गति को पूरी तरह बदल दिया। पहले हमें एक्सेल शीट में यह खोजने में हफ्तों लग जाते थे कि कौन सा प्लॉट ब्लॉक है और किसकी किस्त आई। शार्देय के इंटरैक्टिव मास्टरप्लान और ऑटोमैटिक व्हाट्सएप अलर्ट्स की बदौलत हमने दूसरा चरण मात्र 42 दिनों में बेच दिया।",
    },
    {
      nameEn: "Sanjay 'Sanju' Kapoor",
      nameHi: "संजय 'संजू' कपूर",
      roleEn: "Founder & CEO, Kapoor Real Estate Syndicate (400+ Active Brokers)",
      roleHi: "संस्थापक एवं सीईओ, कपूर रियल एस्टेट सिंडिकेट (400+ सक्रिय ब्रोकर)",
      avatar: "SK",
      quoteEn: "My brokers used to argue over lead conflicts and commission disputes. Shardeya's dynamic 4-tier engine gives them absolute clarity. When a deal closes, they receive an official WhatsApp voucher and ledger within 5 minutes. Our team's closing volume doubled in 6 months.",
      quoteHi: "पहले हमारे ब्रोकर्स में लीड के क्लैश और कमीशन को लेकर असंतोष रहता था। शार्देय के 4-टियर डायनामिक इंजन ने सब कुछ पारदर्शी बना दिया। डील क्लोज होते ही 5 मिनट में व्हाट्सएप पर मुहरयुक्त वाउचर मिल जाता है। हमारी क्लोजिंग दर 6 महीने में दोगुनी हो गई।",
    },
    {
      nameEn: "Arunodaya Banerjee",
      nameHi: "अरुणोदय बनर्जी",
      roleEn: "Chief Financial Officer, Skyline Metropark Developments",
      roleHi: "मुख्य वित्तीय अधिकारी (CFO), स्काईलाइन मेट्रोपार्क डेवलपमेंट्स",
      avatar: "AB",
      quoteEn: "The RERA escrow management and builder cash-flow forecasting in Shardeya are unmatched. It automatically segments the 70% construction pool and handles multi-crore RTGS reconciliation flawlessly. Essential for any serious developer.",
      quoteHi: "शार्देय का RERA एस्क्रो और कैश-फ्लो मैनेजमेंट अद्वितीय है। यह 70% निर्माण राशि को स्वतः अलग करता है और करोड़ों के RTGS भुगतान का स्वतः बैंक मिलान करता है। हर गंभीर बिल्डर के लिए यह अनिवार्य टूल है।",
    },
  ];

  return (
    <section className="py-24 relative bg-sand-100 border-b border-sand-300">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        
        {/* Section Header */}
        <div className="text-center max-w-3xl mx-auto mb-16">
          <div className="inline-flex items-center gap-2 px-3.5 py-1 rounded-full border border-sand-300 bg-sand-50 text-espresso-700 text-xs font-sans mb-3 shadow-warm-sm">
            <span className="w-2 h-2 rounded-full bg-bronze" />
            <span className="uppercase text-[11px] font-bold">{t.testimonials.tag}</span>
          </div>
          <h2 className="text-3xl sm:text-4xl lg:text-5xl font-serif text-espresso-950 font-normal tracking-tight">
            {t.testimonials.title}
          </h2>
          <p className="mt-4 text-base text-espresso-700 font-sans">
            {t.testimonials.subtitle}
          </p>
        </div>

        {/* Testimonials 3 Columns */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 text-left">
          {testimonials.map((test, idx) => {
            const name = isHi ? test.nameHi : test.nameEn;
            const role = isHi ? test.roleHi : test.roleEn;
            const quote = isHi ? test.quoteHi : test.quoteEn;

            return (
              <GlassCard
                key={idx}
                variant="default"
                className="p-8 flex flex-col justify-between bg-sand-50/90 border-sand-300 shadow-warm-sm hover:shadow-warm-md"
              >
                <div>
                  <Quote className="w-6 h-6 text-bronze/70 mb-4" />
                  <p className="font-serif text-base text-espresso-800 leading-relaxed italic mb-8 font-normal">
                    "{quote}"
                  </p>
                </div>

                <div className="pt-4 border-t border-sand-200 flex items-center gap-3">
                  <div className="w-10 h-10 rounded-lg bg-sand-200 text-espresso-800 flex items-center justify-center font-serif font-bold text-sm shrink-0 border border-sand-300">
                    {test.avatar}
                  </div>
                  <div>
                    <h4 className="text-sm font-bold text-espresso-950 flex items-center gap-1.5 font-sans">
                      <span>{name}</span>
                      <CheckCircle2 className="w-3.5 h-3.5 text-forest" />
                    </h4>
                    <p className="text-[11px] text-espresso-600 leading-snug font-sans mt-0.5">{role}</p>
                  </div>
                </div>
              </GlassCard>
            );
          })}
        </div>

      </div>
    </section>
  );
};
