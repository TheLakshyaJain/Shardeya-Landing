import React, { useState, useEffect } from 'react';
import { LanguageProvider } from './context/LanguageContext';
import { AuthProvider } from './context/AuthContext';
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
  const [view, setView] = useState<'landing' | 'auth'>(() => {
    if (typeof window !== 'undefined') {
      const hash = window.location.hash;
      return (hash === '#login' || hash === '#signup') ? 'auth' : 'landing';
    }
    return 'landing';
  });

  const [authMode, setAuthMode] = useState<'login' | 'signup'>(() => {
    if (typeof window !== 'undefined' && window.location.hash === '#signup') {
      return 'signup';
    }
    return 'login';
  });

  const [isDemoModalOpen, setIsDemoModalOpen] = useState(false);

  useEffect(() => {
    const handleHashChange = () => {
      const hash = window.location.hash;
      if (hash === '#login') {
        setAuthMode('login');
        setView('auth');
        window.scrollTo({ top: 0, behavior: 'smooth' });
      } else if (hash === '#signup') {
        setAuthMode('signup');
        setView('auth');
        window.scrollTo({ top: 0, behavior: 'smooth' });
      } else if (!hash || hash === '#') {
        setView('landing');
      }
    };

    window.addEventListener('hashchange', handleHashChange);
    return () => window.removeEventListener('hashchange', handleHashChange);
  }, []);

  const handleOpenAuth = (mode: 'login' | 'signup' = 'login') => {
    setAuthMode(mode);
    setView('auth');
    window.location.hash = mode;
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const handleBackToLanding = () => {
    setView('landing');
    if (window.location.hash === '#login' || window.location.hash === '#signup') {
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

  if (view === 'auth') {
    return <LoginPage onBack={handleBackToLanding} initialMode={authMode} />;
  }

  return (
    <div className="min-h-screen bg-sand-100 bg-ambient-luminous text-espresso-950 selection:bg-forest/15 selection:text-forest">
      {/* Navigation */}
      <Navbar
        onOpenDemo={handleOpenDemo}
        onOpenLogin={handleOpenAuth}
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
      <AuthProvider>
        <AppContent />
      </AuthProvider>
    </LanguageProvider>
  );
}
