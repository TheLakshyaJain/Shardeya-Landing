import React, { useState, useEffect } from 'react';
import { LanguageProvider } from './context/LanguageContext';
import { AuthProvider } from './context/AuthContext';
import { CrmProvider } from './context/CrmContext';
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
import { CrmAppShell } from './components/crm/shell/CrmAppShell';
import { useAuth } from './context/AuthContext';

export const AppContent: React.FC = () => {
  const { user, isAuthenticated } = useAuth();

  const [view, setView] = useState<'landing' | 'auth' | 'app'>(() => {
    if (typeof window !== 'undefined') {
      const hash = window.location.hash;
      if (hash === '#app') return 'app';
      if (hash === '#login' || hash === '#signup') return 'auth';
      return 'landing';
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

  // Direct authenticated user straight to dashboard if on auth view
  useEffect(() => {
    if (isAuthenticated && view === 'auth') {
      setView('app');
      window.location.hash = 'app';
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  }, [isAuthenticated, view]);

  useEffect(() => {
    const handleHashChange = () => {
      const hash = window.location.hash;
      if (hash === '#app') {
        setView('app');
        window.scrollTo({ top: 0, behavior: 'smooth' });
      } else if (hash === '#login' || hash === '#signup') {
        if (isAuthenticated) {
          setView('app');
          window.location.hash = 'app';
        } else {
          setAuthMode(hash === '#signup' ? 'signup' : 'login');
          setView('auth');
        }
        window.scrollTo({ top: 0, behavior: 'smooth' });
      } else if (!hash || hash === '#') {
        setView('landing');
      }
    };

    window.addEventListener('hashchange', handleHashChange);
    return () => window.removeEventListener('hashchange', handleHashChange);
  }, [isAuthenticated]);

  const handleOpenAuth = (mode: 'login' | 'signup' = 'login') => {
    if (isAuthenticated) {
      handleOpenApp();
      return;
    }
    setAuthMode(mode);
    setView('auth');
    window.location.hash = mode;
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const handleOpenApp = () => {
    setView('app');
    window.location.hash = 'app';
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const handleBackToLanding = () => {
    setView('landing');
    if (window.location.hash === '#login' || window.location.hash === '#signup' || window.location.hash === '#app') {
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

  // 1. CRM Real Estate Platform View
  if (view === 'app') {
    return <CrmAppShell onBackToLanding={handleBackToLanding} />;
  }

  // 2. Authentication View
  if (view === 'auth') {
    return (
      <LoginPage 
        onBack={handleBackToLanding} 
        onEnterApp={handleOpenApp} 
        initialMode={authMode} 
      />
    );
  }

  // 3. Marketing Landing Page View
  return (
    <div className="min-h-screen bg-sand-100 bg-ambient-luminous text-espresso-950 selection:bg-forest/15 selection:text-forest">
      {/* Navigation */}
      <Navbar
        onOpenDemo={handleOpenDemo}
        onOpenLogin={handleOpenAuth}
        onOpenApp={handleOpenApp}
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
        <CrmProvider>
          <AppContent />
        </CrmProvider>
      </AuthProvider>
    </LanguageProvider>
  );
}
