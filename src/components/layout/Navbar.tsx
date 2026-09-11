import React, { useState } from 'react';
import { 
  Globe, Menu, X, ChevronRight, Layers, MessageSquare, 
  Calculator, Users, LogOut, ShieldCheck 
} from 'lucide-react';
import { useLanguage } from '../../context/LanguageContext';
import { useAuth } from '../../context/AuthContext';

interface NavbarProps {
  onOpenDemo: () => void;
  onOpenLogin?: (mode?: 'login' | 'signup') => void;
  onOpenApp?: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({ onOpenDemo, onOpenLogin, onOpenApp }) => {
  const { language, toggleLanguage, t } = useLanguage();
  const { user, isAuthenticated, logout } = useAuth();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  const isHi = language === 'hi';

  const navLinks = [
    { label: t.nav.masterplan, href: '#masterplan', icon: Layers },
    { label: t.nav.brokerCrm, href: '#brokers', icon: Users },
    { label: t.nav.whatsapp, href: '#whatsapp', icon: MessageSquare },
    { label: t.nav.calculators, href: '#calculators', icon: Calculator },
  ];

  return (
    <nav className="fixed top-0 left-0 right-0 z-40 bg-white/85 backdrop-blur-xl border-b border-slate-200/90 transition-all duration-300 shadow-warm-sm">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-20">
          
          {/* Brand Logo - Clean */}
          <a href="#" className="flex items-center gap-3 group">
            <div className="w-9 h-9 rounded-lg bg-forest flex items-center justify-center text-white font-serif font-bold text-lg shadow-warm-sm transition-transform group-hover:scale-105">
              S
            </div>
            <span className="font-serif font-bold text-2xl tracking-tight text-espresso-950">
              SHARDEYA
            </span>
          </a>

          {/* Desktop Navigation Links */}
          <div className="hidden lg:flex items-center gap-8">
            {navLinks.map((link) => {
              const Icon = link.icon;
              return (
                <a
                  key={link.label}
                  href={link.href}
                  className="flex items-center gap-2 text-xs font-sans font-semibold text-espresso-700 hover:text-forest transition-colors tracking-wide uppercase"
                >
                  <Icon className="w-3.5 h-3.5 text-espresso-400 group-hover:text-forest" />
                  <span>{link.label}</span>
                </a>
              );
            })}
          </div>

          {/* Right Action Bar */}
          <div className="hidden md:flex items-center gap-3">
            {/* Language Switcher */}
            <button
              onClick={toggleLanguage}
              className="flex items-center gap-2 px-3 py-1.5 rounded-lg border border-sand-300 bg-sand-50 hover:bg-sand-200 text-xs font-mono font-bold text-espresso-800 transition-all shadow-warm-sm"
              title="Toggle English / हिन्दी"
            >
              <Globe className="w-3.5 h-3.5 text-forest" />
              <span>{language === 'en' ? 'हिन्दी' : 'English'}</span>
              <span className="text-[9px] px-1.5 py-0.5 rounded bg-white text-bronze-dark font-bold border border-sand-300">
                {language.toUpperCase()}
              </span>
            </button>

            {/* Authenticated State vs Guest State */}
            {isAuthenticated && user ? (
              <div className="flex items-center gap-2.5">
                {/* User Capsule / Profile trigger */}
                <button
                  onClick={() => onOpenApp ? onOpenApp() : onOpenLogin?.('login')}
                  className="flex items-center gap-2 pl-2 pr-3 py-1.5 rounded-xl border border-emerald-300 bg-emerald-50/70 hover:bg-emerald-100/70 transition-all shadow-warm-sm"
                  title="Open Shardeya Platform"
                >
                  <div className="w-6 h-6 rounded-full bg-forest text-white flex items-center justify-center font-serif text-xs font-bold shadow-sm">
                    {user.avatarInitials}
                  </div>
                  <div className="text-left">
                    <span className="block text-xs font-bold text-espresso-950 leading-tight">
                      {user.name.split(' ')[0]}
                    </span>
                    <span className="block text-[9px] font-mono text-emerald-800 uppercase tracking-wider leading-none">
                      {user.role === 'developer' ? 'Developer' : 'Partner'}
                    </span>
                  </div>
                </button>

                {/* Open Platform Action */}
                <button
                  onClick={onOpenApp}
                  className="px-3.5 py-2 rounded-lg bg-forest hover:bg-forest-600 text-white font-sans font-bold text-xs uppercase tracking-wider shadow-warm-sm transition-all flex items-center gap-1.5"
                >
                  <span>{isHi ? 'प्लेटफॉर्म खोलें' : 'Open Platform'}</span>
                  <ChevronRight className="w-3.5 h-3.5" />
                </button>

                {/* Quick Sign Out Button */}
                <button
                  onClick={logout}
                  className="p-2 rounded-lg border border-sand-300 text-espresso-600 hover:text-rose-600 hover:bg-rose-50 transition-colors"
                  title="Sign Out"
                >
                  <LogOut className="w-4 h-4" />
                </button>
              </div>
            ) : (
              <>
                {/* ONLY Login Button - exactly like before, no Signup button on landing page */}
                {onOpenLogin && (
                  <button
                    onClick={() => onOpenLogin('login')}
                    className="text-xs font-bold uppercase tracking-wider text-espresso-800 hover:text-forest transition-colors px-2 py-1.5"
                  >
                    {isHi ? 'लॉगिन' : 'Login'}
                  </button>
                )}

                {/* Request Access VIP CTA */}
                <button
                  onClick={onOpenDemo}
                  className="px-5 py-2.5 rounded-lg bg-forest hover:bg-forest-light text-white font-sans font-bold text-xs uppercase tracking-wider shadow-warm-sm transition-all transform hover:scale-[1.01] active:scale-98 flex items-center gap-2"
                >
                  <span>{t.nav.requestAccess}</span>
                  <ChevronRight className="w-3.5 h-3.5" />
                </button>
              </>
            )}
          </div>

          {/* Mobile Hamburger Button */}
          <div className="flex md:hidden items-center gap-2 font-mono">
            <button
              onClick={toggleLanguage}
              className="px-2.5 py-1 rounded-md border border-sand-300 bg-sand-50 text-xs text-espresso-800 font-bold"
            >
              {language === 'en' ? 'हिन्दी' : 'EN'}
            </button>
            <button
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              className="p-2 rounded-lg text-espresso-800 hover:bg-sand-200"
            >
              {mobileMenuOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
            </button>
          </div>
        </div>
      </div>

      {/* Mobile Menu Dropdown */}
      {mobileMenuOpen && (
        <div className="md:hidden border-b border-slate-200 bg-white/95 backdrop-blur-xl px-4 pt-3 pb-6 space-y-3 shadow-warm-md">
          {navLinks.map((link) => {
            const Icon = link.icon;
            return (
              <a
                key={link.href}
                href={link.href}
                onClick={() => setMobileMenuOpen(false)}
                className="flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-sans font-bold text-espresso-900 hover:bg-sand-200 hover:text-forest transition-colors"
              >
                <Icon className="w-4 h-4 text-forest" />
                {link.label}
              </a>
            );
          })}

          <div className="pt-3 border-t border-sand-300 space-y-2">
            {isAuthenticated && user ? (
              <>
                <div className="p-3 rounded-xl bg-emerald-50 border border-emerald-200 flex items-center justify-between">
                  <div className="flex items-center gap-2.5">
                    <div className="w-8 h-8 rounded-full bg-forest text-white flex items-center justify-center font-serif text-xs font-bold">
                      {user.avatarInitials}
                    </div>
                    <div>
                      <div className="text-xs font-bold text-espresso-950">{user.name}</div>
                      <div className="text-[10px] font-mono text-emerald-800 uppercase">{user.role} Console</div>
                    </div>
                  </div>
                  <ShieldCheck className="w-4 h-4 text-forest" />
                </div>

                <button
                  onClick={() => {
                    setMobileMenuOpen(false);
                    if (onOpenApp) onOpenApp();
                    else onOpenLogin?.('login');
                  }}
                  className="w-full py-2.5 px-4 rounded-lg bg-forest text-white font-sans font-bold text-xs tracking-wider uppercase flex items-center justify-center gap-2 shadow-warm-sm"
                >
                  {isHi ? 'शारदेया प्लेटफॉर्म खोलें' : 'Open Shardeya Platform'}
                </button>

                <button
                  onClick={() => {
                    logout();
                    setMobileMenuOpen(false);
                  }}
                  className="w-full py-2 px-4 rounded-lg border border-sand-300 text-espresso-700 font-sans font-bold text-xs tracking-wider uppercase flex items-center justify-center gap-2 hover:bg-sand-100"
                >
                  Sign Out
                </button>
              </>
            ) : (
              <>
                {/* Single Login Button exactly like before */}
                {onOpenLogin && (
                  <button
                    onClick={() => {
                      setMobileMenuOpen(false);
                      onOpenLogin('login');
                    }}
                    className="w-full py-2.5 px-4 rounded-lg border border-sand-300 text-espresso-800 font-sans font-bold text-xs tracking-wider uppercase flex items-center justify-center gap-2 hover:bg-sand-200 transition-colors"
                  >
                    {isHi ? 'लॉगिन' : 'Login'}
                  </button>
                )}
                <button
                  onClick={() => {
                    setMobileMenuOpen(false);
                    onOpenDemo();
                  }}
                  className="w-full py-3 px-4 rounded-lg bg-forest text-white font-sans font-bold text-xs tracking-wider uppercase flex items-center justify-center gap-2 shadow-warm-sm"
                >
                  {t.nav.requestAccess}
                </button>
              </>
            )}
          </div>
        </div>
      )}
    </nav>
  );
};
