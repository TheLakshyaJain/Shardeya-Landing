import React from 'react';
import { Layers, Users, MessageSquare, Smartphone, Calculator, FileText } from 'lucide-react';
import { useLanguage } from '../../context/LanguageContext';
import { GlassCard } from '../common/GlassCard';

export const CoreFeaturesGrid: React.FC = () => {
  const { language } = useLanguage();
  const isHi = language === 'hi';

  const features = [
    {
      icon: Layers,
      title: isHi ? 'प्रोजेक्ट व प्लॉट लेआउट' : 'Interactive Plot & Project Layouts',
      desc: isHi
        ? 'हर प्लॉट और फ्लैट का सुपर बिल्ट-अप एरिया, फेसिंग, खरीदार का KYC, संबंधित ब्रोकर और किस्तों का पूरा हिसाब एक स्क्रीन पर।'
        : 'Track all plots, villas, and apartments. See buyer details, KYC verification, assigned brokers, and construction stages at a glance.',
      badge: 'INVENTORY',
    },
    {
      icon: Users,
      title: isHi ? 'ब्रोकर लेवल्स व ऑटो-कमीशन' : 'Broker Levels & Auto-Commissions',
      desc: isHi
        ? 'बिक्री के आधार पर स्वतः पदोन्नति। तुरंत कमीशन वाउचर और TDS लेजर जनरेट करें।'
        : 'Promote brokers automatically as deals close. Instant commission calculations, team overrides, and lead clash protection.',
      badge: 'BROKERS',
    },
    {
      icon: MessageSquare,
      title: isHi ? 'व्हाट्सएप रिमाइंडर व साइट विजिट' : 'Automated WhatsApp & Site Visits',
      desc: isHi
        ? 'गूगल मैप्स लोकेशन के साथ साइट विजिट कन्फर्मेशन, निर्माण प्रगति की फोटो और 1-क्लिक डिजिटल रसीदें सीधे व्हाट्सएप पर।'
        : 'Send automated site visit confirmations with Google Maps, construction photo updates, and instant digital payment receipts on WhatsApp.',
      badge: 'WHATSAPP',
    },
    {
      icon: FileText,
      title: isHi ? 'किस्त भुगतान व मांग पत्र' : 'Milestone Demands & Receipts',
      desc: isHi
        ? 'निर्माण चरणों के आधार पर ऑटोमैटिक मांग पत्र भेजें, किस्तों का हिसाब रखें और डिजिटल रसीदें जारी करें।'
        : 'Issue stage-linked demand letters, track payment installments for each unit, and generate clean digital receipts.',
      badge: 'BILLING',
    },
    {
      icon: Smartphone,
      title: isHi ? 'साइट नोट्स व फील्ड एक्टिविटी' : 'Site Notes & Field Activity Log',
      desc: isHi
        ? 'फील्ड सेल्स टीम साइट पर खड़े होकर ग्राहक की प्राथमिकताएं, विज़िट रिमार्क्स और टोकन एग्रीमेंट सीधे दर्ज कर सकती है।'
        : 'On-site sales executives log buyer preferences, site inspection remarks, and token agreements directly from their phone.',
      badge: 'FIELD SALES',
    },
    {
      icon: Calculator,
      title: isHi ? 'स्मार्ट रियल एस्टेट कैलकुलेटर' : 'Real Estate Precision Calculators',
      desc: isHi
        ? 'होम लोन EMI, राज्यवार स्टैंप ड्यूटी और तिमाही कैश फ्लो का एक क्लिक में सटीक हिसाब लगाएं।'
        : 'Integrated EMI planners, state-wise stamp duty tariffs, and builder cash realization models ready for all stakeholders.',
      badge: 'CALCULATORS',
    },
  ];

  return (
    <section id="features" className="py-24 relative bg-sand-50 border-b border-sand-300">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        
        {/* Section Header */}
        <div className="text-center max-w-3xl mx-auto mb-16">
          <div className="inline-flex items-center gap-2 px-3.5 py-1 rounded-full border border-sand-300 bg-sand-150 text-espresso-800 text-xs font-sans mb-3 shadow-warm-sm">
            <span className="w-2 h-2 rounded-full bg-forest" />
            <span className="font-semibold">
              {isHi ? 'मुख्य विशेषताएं' : 'Core Features'}
            </span>
          </div>
          <h2 className="text-3xl sm:text-4xl lg:text-5xl font-serif text-espresso-950 font-normal tracking-tight">
            {isHi
              ? 'बिल्डर्स और ब्रोकर्स के लिए संपूर्ण समाधान'
              : 'Everything Built for Modern Real Estate Teams'}
          </h2>
          <p className="mt-4 text-base text-espresso-700 font-sans">
            {isHi
              ? 'एक्सेल शीट्स और बिखरे हुए व्हाट्सएप मैसेज को कहें अलविदा। हर प्रक्रिया को आसान और पारदर्शी बनाएं।'
              : 'Replace scattered spreadsheets and lost WhatsApp chats with one clean, unified platform.'}
          </p>
        </div>

        {/* 6 Feature Cards */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {features.map((feat, idx) => {
            const Icon = feat.icon;
            return (
              <GlassCard
                key={idx}
                variant="default"
                className="p-8 text-left flex flex-col justify-between group bg-sand-100 border-sand-300 hover:bg-white shadow-warm-sm"
              >
                <div>
                  <div className="flex items-center justify-between mb-5">
                    <div className="w-10 h-10 rounded-xl bg-white border border-sand-300 flex items-center justify-center text-forest shadow-warm-sm group-hover:border-forest transition-colors">
                      <Icon className="w-5 h-5" />
                    </div>
                    <span className="text-[10px] font-mono font-bold tracking-wider px-2.5 py-1 rounded bg-sand-200 text-espresso-700">
                      {feat.badge}
                    </span>
                  </div>

                  <h3 className="text-lg font-serif font-bold text-espresso-950 mb-2">
                    {feat.title}
                  </h3>

                  <p className="text-xs sm:text-sm text-espresso-700 leading-relaxed font-sans">
                    {feat.desc}
                  </p>
                </div>

                <div className="mt-6 pt-4 border-t border-sand-200 flex items-center gap-2 text-xs font-sans font-bold text-forest">
                  <span>{isHi ? 'अधिक जानें' : 'Learn More'}</span>
                  <span className="group-hover:translate-x-1 transition-transform">→</span>
                </div>
              </GlassCard>
            );
          })}
        </div>

      </div>
    </section>
  );
};
