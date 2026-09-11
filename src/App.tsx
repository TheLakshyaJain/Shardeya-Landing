import React, { useState, useEffect } from 'react';
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
import { LoginPage } from './components/auth/LoginPage';

export const AppContent: React.FC = () => {
  const [view, setView] = useState<'landing' | 'login'>(() => {
    if (typeof window !== 'undefined') {
      return window.location.hash === '#login' ? 'login' : 'landing';
    }
    return 'landing';
  });

  const [isDemoModalOpen, setIsDemoModalOpen] = useState(false);

  useEffect(() => {
    const handleHashChange = () => {
      if (window.location.hash === '#login') {
        setView('login');
        window.scrollTo({ top: 0, behavior: 'smooth' });
      } else if (!window.location.hash || window.location.hash === '#') {
        setView('landing');
      }
    };

    window.addEventListener('hashchange', handleHashChange);
    return () => window.removeEventListener('hashchange', handleHashChange);
  }, []);

  const handleOpenLogin = () => {
    setView('login');
    window.location.hash = 'login';
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const handleBackToLanding = () => {
    setView('landing');
    if (window.location.hash === '#login') {
      window.history.pushState(null, '', window.location.pathname);
    }
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

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

  if (view === 'login') {
    return <LoginPage onBack={handleBackToLanding} />;
  }

  return (
    <div className="min-h-screen bg-sand-100 bg-ambient-luminous text-espresso-950 selection:bg-forest/15 selection:text-forest">
      {/* Navigation */}
      <Navbar
        onOpenDemo={handleOpenDemo}
        onOpenLogin={handleOpenLogin}
      />

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
