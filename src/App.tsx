import React, { useState } from 'react';
import { LanguageProvider } from './context/LanguageContext';
import { Navbar } from './components/layout/Navbar';
import { HeroSection } from './components/hero/HeroSection';
import { MasterplanVisualizer } from './components/demo/MasterplanVisualizer';
import { BrokerEcosystem } from './components/demo/BrokerEcosystem';
import { WhatsAppSimulator } from './components/demo/WhatsAppSimulator';
import { RealEstateCalculators } from './components/demo/RealEstateCalculators';
import { CoreFeaturesGrid } from './components/features/CoreFeaturesGrid';
import { Footer } from './components/layout/Footer';
import { DemoModal } from './components/modals/DemoModal';

export const AppContent: React.FC = () => {
  const [isDemoModalOpen, setIsDemoModalOpen] = useState(false);

  const handleOpenDemo = () => {
    setIsDemoModalOpen(true);
  };

  const handleCloseDemo = () => {
    setIsDemoModalOpen(false);
  };

  const handleExploreMasterplan = () => {
    const el = document.getElementById('masterplan');
    if (el) {
      el.scrollIntoView({ behavior: 'smooth' });
    }
  };

  return (
    <div className="min-h-screen bg-sand-100 bg-ambient-luminous text-espresso-950 selection:bg-forest/15 selection:text-forest">
      {/* Navigation */}
      <Navbar onOpenDemo={handleOpenDemo} />

      {/* Hero Section */}
      <HeroSection
        onOpenDemo={handleOpenDemo}
        onExploreMasterplan={handleExploreMasterplan}
      />

      {/* Core USP 1: Interactive Masterplan & Plot Matrix */}
      <MasterplanVisualizer />

      {/* Core USP 2: Broker Ecosystem & Dynamic Commission Engine */}
      <BrokerEcosystem />

      {/* Core USP 3: Omnichannel WhatsApp Automation & Site Visits */}
      <WhatsAppSimulator />

      {/* Core USP 4: Real Estate Calculators Suite */}
      <RealEstateCalculators />

      {/* Deep-Dive: Core Features Grid */}
      <CoreFeaturesGrid />

      {/* Enterprise Footer */}
      <Footer />

      {/* Priority VIP Demo Booking Modal */}
      <DemoModal isOpen={isDemoModalOpen} onClose={handleCloseDemo} />
    </div>
  );
};

export default function App() {
  return (
    <LanguageProvider>
      <AppContent />
    </LanguageProvider>
  );
}
