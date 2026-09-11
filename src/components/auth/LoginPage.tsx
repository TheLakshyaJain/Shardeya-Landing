import React, { useState, useEffect } from 'react';
import { 
  ArrowLeft, ShieldCheck, Mail, Lock, 
  Eye, EyeOff, CheckCircle2, ArrowRight, Building2, 
  Users, RefreshCw, Check, AlertCircle, Sparkles,
  KeyRound, X, ShieldAlert, BadgeCheck, Compass,
  Layers, ChevronRight
} from 'lucide-react';
import confetti from 'canvas-confetti';
import { useLanguage } from '../../context/LanguageContext';
import { useAuth, UserRole } from '../../context/AuthContext';

interface LoginPageProps {
  onBack: () => void;
  initialMode?: 'login' | 'signup';
}

export const LoginPage: React.FC<LoginPageProps> = ({ onBack, initialMode = 'login' }) => {
  const { language } = useLanguage();
  const isHi = language === 'hi';
  const { user, login, signup, logout, requestPasswordReset, getLockoutStatus } = useAuth();

  // Mode: Sign In vs Sign Up (toggled via clean header/footer links, NOT stacked tabs)
  const [authMode, setAuthMode] = useState<'login' | 'signup'>(initialMode);
  const [role, setRole] = useState<UserRole>('developer');

  // Sign In Form State
  const [loginEmail, setLoginEmail] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
  const [showLoginPassword, setShowLoginPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(true);

  // Sign Up Form State
  const [signupName, setSignupName] = useState('');
  const [signupEmail, setSignupEmail] = useState('');
  const [signupOrg, setSignupOrg] = useState('');
  const [signupDesignation, setSignupDesignation] = useState('');
  const [signupRera, setSignupRera] = useState('');
  const [signupPassword, setSignupPassword] = useState('');
  const [signupConfirmPassword, setSignupConfirmPassword] = useState('');
  const [showSignupPassword, setShowSignupPassword] = useState(false);
  const [agreeTerms, setAgreeTerms] = useState(false);

  // Status & Feedback States
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [successToast, setSuccessToast] = useState('');

  // Password Reset Modal
  const [showForgotModal, setShowForgotModal] = useState(false);
  const [forgotEmail, setForgotEmail] = useState('');
  const [forgotStatus, setForgotStatus] = useState<{ loading: boolean; message: string; isError: boolean } | null>(null);

  // Brute-force Lockout Countdown
  const [lockoutSeconds, setLockoutSeconds] = useState(0);

  useEffect(() => {
    if (loginEmail) {
      const status = getLockoutStatus(loginEmail);
      if (status.isLocked) {
        setLockoutSeconds(status.remainingSeconds);
      }
    }
  }, [loginEmail]);

  useEffect(() => {
    let interval: ReturnType<typeof setInterval>;
    if (lockoutSeconds > 0) {
      interval = setInterval(() => {
        setLockoutSeconds((prev) => Math.max(0, prev - 1));
      }, 1000);
    }
    return () => clearInterval(interval);
  }, [lockoutSeconds]);

  useEffect(() => {
    if (initialMode) {
      setAuthMode(initialMode);
    }
  }, [initialMode]);

  // Password strength logic
  const passwordCriteria = {
    length: signupPassword.length >= 8,
    hasUpper: /[A-Z]/.test(signupPassword),
    hasNumber: /[0-9]/.test(signupPassword),
    hasSpecial: /[!@#$%^&*(),.?":{}|<>]/.test(signupPassword),
  };

  const passwordScore = [
    passwordCriteria.length,
    passwordCriteria.hasUpper,
    passwordCriteria.hasNumber,
    passwordCriteria.hasSpecial
  ].filter(Boolean).length;

  const getStrengthBar = () => {
    if (!signupPassword) return { text: '', color: 'bg-sand-200', width: 'w-0' };
    if (passwordScore <= 1) return { text: isHi ? 'कमजोर' : 'Weak', color: 'bg-rose-500', width: 'w-1/4' };
    if (passwordScore === 2) return { text: isHi ? 'मध्यम' : 'Fair', color: 'bg-amber-500', width: 'w-2/4' };
    if (passwordScore === 3) return { text: isHi ? 'मजबूत' : 'Strong', color: 'bg-emerald-500', width: 'w-3/4' };
    return { text: isHi ? 'उद्यम-स्तरीय' : 'Enterprise Secure', color: 'bg-forest', width: 'w-full' };
  };

  // Subtle Demo Autofill Helper
  const handleAutofill = (type: 'dev' | 'broker') => {
    setAuthMode('login');
    setErrorMessage('');
    if (type === 'dev') {
      setRole('developer');
      setLoginEmail('director@apexdevelopers.com');
      setLoginPassword('Shardeya@2026');
    } else {
      setRole('broker');
      setLoginEmail('partner@apexrealty.com');
      setLoginPassword('Shardeya@2026');
    }
  };

  // Sign In Handler
  const handleSignIn = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage('');
    setSuccessToast('');

    if (!loginEmail.trim() || !loginPassword) {
      setErrorMessage(isHi ? 'कृपया ईमेल और पासवर्ड दर्ज करें।' : 'Please enter your corporate email and password.');
      return;
    }

    setIsLoading(true);
    const result = await login(loginEmail, loginPassword, role);
    setIsLoading(false);

    if (!result.success) {
      setErrorMessage(result.error || 'Invalid credentials. Please verify and retry.');
      const status = getLockoutStatus(loginEmail);
      if (status.isLocked) {
        setLockoutSeconds(status.remainingSeconds);
      }
    } else {
      setSuccessToast(isHi ? 'प्रमाणीकरण सफल!' : 'Identity verified successfully.');
    }
  };

  // Sign Up Handler
  const handleSignUp = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage('');
    setSuccessToast('');

    if (!signupName.trim()) {
      setErrorMessage(isHi ? 'कृपया अपना नाम दर्ज करें।' : 'Please enter your full authorized name.');
      return;
    }
    if (!signupEmail.trim()) {
      setErrorMessage(isHi ? 'कृपया कॉर्पोरेट ईमेल दर्ज करें।' : 'Please enter your corporate email address.');
      return;
    }
    if (!signupOrg.trim()) {
      setErrorMessage(isHi ? 'कृपया अपनी फर्म का नाम दर्ज करें।' : 'Please enter your firm or enterprise name.');
      return;
    }
    if (passwordScore < 3) {
      setErrorMessage(isHi ? 'पासवर्ड में न्यूनतम 8 अक्षर, एक बड़ा अक्षर, संख्या और प्रतीक होना चाहिए।' : 'Password must be at least 8 characters and include uppercase, numbers, and symbols.');
      return;
    }
    if (signupPassword !== signupConfirmPassword) {
      setErrorMessage(isHi ? 'पासवर्ड मेल नहीं खाते।' : 'Passwords do not match. Please verify.');
      return;
    }
    if (!agreeTerms) {
      setErrorMessage(isHi ? 'कृपया सेवा शर्तों को स्वीकार करें।' : 'Please accept the Shardeya Enterprise Terms & RERA Privacy Protocol.');
      return;
    }

    setIsLoading(true);
    const result = await signup({
      name: signupName,
      email: signupEmail,
      password: signupPassword,
      role,
      organization: signupOrg,
      designation: signupDesignation,
      reraNumber: signupRera,
    });
    setIsLoading(false);

    if (!result.success) {
      setErrorMessage(result.error || 'Failed to create account.');
    } else {
      try {
        confetti({ particleCount: 70, spread: 60, origin: { y: 0.6 } });
      } catch {
        // Fallback
      }
      setSuccessToast(isHi ? 'खाता सफलतापूर्वक बनाया गया!' : 'Enterprise account created successfully.');
    }
  };

  const handleSendReset = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!forgotEmail.trim()) {
      setForgotStatus({
        loading: false,
        message: isHi ? 'कृपया ईमेल दर्ज करें।' : 'Please enter your email address.',
        isError: true,
      });
      return;
    }
    setForgotStatus({ loading: true, message: '', isError: false });
    const res = await requestPasswordReset(forgotEmail);
    setForgotStatus({
      loading: false,
      message: res.message,
      isError: !res.success,
    });
  };

  return (
    <div className="min-h-screen bg-sand-100 font-sans selection:bg-forest/15 selection:text-forest flex flex-col lg:flex-row">
      
      {/* ============================================================
          LEFT PANEL: Architectural Heritage & Editorial Brand Showcase
         ============================================================ */}
      <div className="lg:w-5/12 xl:w-5/12 bg-[#051F19] text-white p-8 sm:p-12 lg:p-16 flex flex-col justify-between relative overflow-hidden">
        
        {/* Subtle Architectural Grid Background Pattern */}
        <div 
          className="absolute inset-0 opacity-[0.07] pointer-events-none"
          style={{
            backgroundImage: `linear-gradient(#fff 1px, transparent 1px), linear-gradient(90deg, #fff 1px, transparent 1px)`,
            backgroundSize: '40px 40px'
          }}
        />

        {/* Ambient Emerald Radial Glow */}
        <div className="absolute -top-32 -left-32 w-96 h-96 bg-emerald-500/15 rounded-full blur-3xl pointer-events-none" />
        <div className="absolute -bottom-32 -right-32 w-96 h-96 bg-amber-500/10 rounded-full blur-3xl pointer-events-none" />

        {/* Brand Header */}
        <div className="relative z-10">
          <div className="flex items-center gap-3.5 mb-8">
            <div className="w-10 h-10 rounded-xl bg-forest-light/20 border border-forest-light/40 flex items-center justify-center text-emerald-300 font-serif font-bold text-xl backdrop-blur-md">
              S
            </div>
            <div>
              <span className="font-serif font-bold text-2xl tracking-tight text-white block leading-tight">
                SHARDEYA
              </span>
              <span className="text-[10px] font-mono tracking-widest text-emerald-400/90 uppercase block mt-0.5">
                Real Estate Operating System
              </span>
            </div>
          </div>
        </div>

        {/* Center Editorial Showcase */}
        <div className="relative z-10 my-auto py-8">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-white/10 border border-white/15 text-[11px] font-mono tracking-wider text-emerald-300 mb-6 backdrop-blur-md">
            <Compass className="w-3.5 h-3.5" />
            <span>ENTERPRISE PLATFORM</span>
          </div>

          <h2 className="font-serif text-3xl sm:text-4xl text-white font-normal leading-snug tracking-tight mb-6">
            Architecting the future of Indian land parcels & development.
          </h2>

          <p className="text-sand-300/80 text-sm leading-relaxed mb-8 max-w-md font-sans">
            A unified real estate operating system orchestrating multi-phase plotting layouts, automated demand milestones, and verified broker syndicates under strict RERA compliance.
          </p>

          {/* Floating Metric Card */}
          <div className="p-5 rounded-2xl bg-white/[0.05] border border-white/10 backdrop-blur-md space-y-3.5 max-w-md shadow-2xl">
            <div className="flex items-center justify-between text-xs text-sand-300/70 border-b border-white/10 pb-2.5">
              <span className="font-mono text-[11px] uppercase tracking-wider">Active Workspace</span>
              <span className="font-mono text-emerald-400 font-semibold">Verified RERA Escrow</span>
            </div>

            <div className="grid grid-cols-2 gap-4 pt-1">
              <div>
                <div className="text-[11px] text-sand-400 font-sans">Inventory Realization</div>
                <div className="text-xl font-serif font-bold text-white mt-0.5">₹420+ Cr</div>
              </div>
              <div>
                <div className="text-[11px] text-sand-400 font-sans">Broker Lead Protection</div>
                <div className="text-xl font-serif font-bold text-emerald-400 mt-0.5">48-Hr Lock</div>
              </div>
            </div>

            <div className="pt-2 text-[11px] text-sand-300/60 italic font-serif">
              "Eliminated double allotments and transformed our broker syndicate payouts."
              <span className="block text-[10px] not-italic font-sans text-sand-400 mt-1">
                — Rajeshwar Singhania, Director, Apex Greens LLP
              </span>
            </div>
          </div>
        </div>

        {/* Footer Security Badges */}
        <div className="relative z-10 pt-6 border-t border-white/10 flex items-center justify-between text-[11px] text-sand-400 font-mono">
          <span className="flex items-center gap-1.5">
            <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
            256-Bit TLS 1.3
          </span>
          <span>ISO 27001 Certified</span>
          <span>RERA Compliant</span>
        </div>

      </div>

      {/* ============================================================
          RIGHT PANEL: Clean, Focused, Human-Crafted Authentication Form
         ============================================================ */}
      <div className="lg:w-7/12 xl:w-7/12 bg-white flex flex-col justify-between p-6 sm:p-10 lg:p-14 overflow-y-auto">
        
        {/* Top Action Bar */}
        <div className="flex items-center justify-between pb-6 border-b border-sand-200/80">
          <button
            onClick={onBack}
            className="inline-flex items-center gap-2 text-xs font-semibold text-espresso-700 hover:text-forest transition-colors group"
          >
            <ArrowLeft className="w-4 h-4 text-forest transition-transform group-hover:-translate-x-1" />
            <span>{isHi ? 'वेबसाइट पर वापस जाएं' : 'Back to Website'}</span>
          </button>

          {/* Clean Top Switcher (Replaces the clunky stacked tabs!) */}
          {!user && (
            <div className="text-xs font-sans">
              {authMode === 'login' ? (
                <span className="text-espresso-600">
                  {isHi ? 'नया खाता बनाना चाहते हैं?' : "Don't have an enterprise account?"}{' '}
                  <button
                    type="button"
                    onClick={() => {
                      setAuthMode('signup');
                      setErrorMessage('');
                      setSuccessToast('');
                      window.location.hash = 'signup';
                    }}
                    className="font-bold text-forest hover:text-forest-dark transition-colors inline-flex items-center gap-0.5 ml-1"
                  >
                    <span>{isHi ? 'खाता बनाएं' : 'Create workspace'}</span>
                    <ArrowRight className="w-3.5 h-3.5" />
                  </button>
                </span>
              ) : (
                <span className="text-espresso-600">
                  {isHi ? 'पहले से पंजीकृत हैं?' : 'Already have a workspace account?'}{' '}
                  <button
                    type="button"
                    onClick={() => {
                      setAuthMode('login');
                      setErrorMessage('');
                      setSuccessToast('');
                      window.location.hash = 'login';
                    }}
                    className="font-bold text-forest hover:text-forest-dark transition-colors inline-flex items-center gap-0.5 ml-1"
                  >
                    <span>{isHi ? 'साइन इन करें' : 'Sign in'}</span>
                    <ArrowRight className="w-3.5 h-3.5" />
                  </button>
                </span>
              )}
            </div>
          )}
        </div>

        {/* Center Canvas Container */}
        <div className="max-w-md w-full mx-auto my-auto py-8">

          {/* 1. AUTHENTICATED STATE: Prestigious Passcard */}
          {user ? (
            <div className="text-center animate-fadeIn space-y-6">
              <div className="w-20 h-20 rounded-2xl bg-emerald-50 border border-emerald-200 text-forest flex items-center justify-center mx-auto font-serif font-bold text-2xl shadow-warm-sm">
                {user.avatarInitials}
              </div>

              <div>
                <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-emerald-50 text-emerald-800 border border-emerald-200 text-xs font-mono font-bold mb-3">
                  <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />
                  <span>AUTHENTICATED ENTERPRISE SESSION</span>
                </div>
                <h2 className="font-serif font-bold text-3xl text-espresso-950">
                  {user.name}
                </h2>
                <p className="text-sm text-espresso-600 font-sans mt-1">
                  {user.designation}
                </p>
                <p className="text-xs font-mono text-forest font-semibold mt-0.5">
                  {user.organization} {user.reraNumber ? `• ${user.reraNumber}` : ''}
                </p>
              </div>

              <div className="p-4 rounded-xl bg-sand-100 border border-sand-300/80 text-left text-xs space-y-2 font-sans">
                <div className="flex justify-between text-espresso-600">
                  <span>Corporate Identity:</span>
                  <span className="font-mono font-semibold text-espresso-900">{user.email}</span>
                </div>
                <div className="flex justify-between text-espresso-600">
                  <span>Portal Permissions:</span>
                  <span className="font-semibold text-espresso-900 capitalize">
                    {user.role === 'developer' ? 'Developer Console (Inventory & Demand)' : 'Channel Partner Syndicate'}
                  </span>
                </div>
                <div className="flex justify-between text-espresso-600">
                  <span>Security Protocol:</span>
                  <span className="font-mono text-emerald-700 font-bold">256-Bit TLS 1.3 • Verified</span>
                </div>
              </div>

              <div className="space-y-2.5 pt-2">
                <button
                  onClick={onBack}
                  className="w-full py-3.5 px-5 rounded-xl bg-forest hover:bg-forest-light text-white font-sans font-bold text-xs uppercase tracking-wider shadow-warm-sm transition-all flex items-center justify-center gap-2"
                >
                  <span>Enter Shardeya Platform</span>
                  <ArrowRight className="w-4 h-4" />
                </button>

                <button
                  onClick={() => {
                    logout();
                    setSuccessToast('');
                  }}
                  className="w-full py-2.5 px-5 rounded-xl border border-sand-300 text-espresso-700 hover:bg-sand-100 text-xs font-semibold transition-all"
                >
                  Sign Out of Session
                </button>
              </div>
            </div>
          ) : (
            /* 2. AUTHENTICATION FORMS */
            <div>
              
              {/* Form Headline */}
              <div className="mb-6">
                <h1 className="font-serif font-bold text-3xl text-espresso-950 tracking-tight">
                  {authMode === 'login'
                    ? (isHi ? 'कार्यक्षेत्र में प्रवेश करें' : 'Sign in to Shardeya')
                    : (isHi ? 'उद्यम खाता बनाएं' : 'Create Enterprise Account')}
                </h1>
                <p className="text-xs text-espresso-600 mt-1.5 leading-relaxed">
                  {authMode === 'login'
                    ? 'Enter your corporate credentials to access your real estate workspace.'
                    : 'Register your development firm or registered brokerage syndicate.'}
                </p>
              </div>

              {/* Status Alert Banners */}
              {errorMessage && (
                <div className="mb-5 p-3.5 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 text-xs flex items-start gap-2.5 animate-fadeIn">
                  <AlertCircle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
                  <span>{errorMessage}</span>
                </div>
              )}

              {successToast && (
                <div className="mb-5 p-3.5 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs flex items-start gap-2.5 animate-fadeIn">
                  <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0 mt-0.5" />
                  <span>{successToast}</span>
                </div>
              )}

              {lockoutSeconds > 0 && (
                <div className="mb-5 p-3.5 rounded-xl bg-amber-50 border border-amber-200 text-amber-900 text-xs flex items-start gap-2.5 animate-fadeIn">
                  <ShieldAlert className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
                  <div>
                    <span className="font-bold">Security Lockout Active:</span> Too many failed attempts. Please wait <span className="font-mono font-bold text-amber-800">{lockoutSeconds}s</span> before retrying.
                  </div>
                </div>
              )}

              {/* ========================================================
                  SIGN IN: Clean Segmented Role Switcher + Email & Password
                 ======================================================== */}
              {authMode === 'login' && (
                <div>
                  {/* Subtle, refined segmented selector for Developer vs Channel Partner */}
                  <div className="mb-6 p-1 bg-sand-100 rounded-xl border border-sand-200/80 flex gap-1">
                    <button
                      type="button"
                      onClick={() => {
                        setRole('developer');
                        setErrorMessage('');
                      }}
                      className={`flex-1 py-2.5 px-3 rounded-lg text-xs font-sans font-semibold transition-all flex items-center justify-center gap-2 ${
                        role === 'developer'
                          ? 'bg-white text-espresso-950 shadow-warm-sm border border-sand-300/70 font-bold'
                          : 'text-espresso-600 hover:text-espresso-900'
                      }`}
                    >
                      <Building2 className={`w-3.5 h-3.5 ${role === 'developer' ? 'text-forest' : 'text-espresso-400'}`} />
                      <span>{isHi ? 'डेवलपर कंसोल' : 'Developer Console'}</span>
                    </button>

                    <button
                      type="button"
                      onClick={() => {
                        setRole('broker');
                        setErrorMessage('');
                      }}
                      className={`flex-1 py-2.5 px-3 rounded-lg text-xs font-sans font-semibold transition-all flex items-center justify-center gap-2 ${
                        role === 'broker'
                          ? 'bg-white text-espresso-950 shadow-warm-sm border border-sand-300/70 font-bold'
                          : 'text-espresso-600 hover:text-espresso-900'
                      }`}
                    >
                      <Users className={`w-3.5 h-3.5 ${role === 'broker' ? 'text-forest' : 'text-espresso-400'}`} />
                      <span>{isHi ? 'चैनल पार्टनर' : 'Channel Partner'}</span>
                    </button>
                  </div>

                  {/* Sign In Form */}
                  <form onSubmit={handleSignIn} className="space-y-4">
                    <div>
                      <label className="block text-xs font-semibold text-espresso-800 mb-1.5">
                        Corporate Email / संस्थागत ईमेल
                      </label>
                      <div className="relative flex items-center">
                        <Mail className="w-4 h-4 absolute left-3.5 text-espresso-400" />
                        <input
                          type="email"
                          required
                          placeholder={role === 'developer' ? 'director@apexdevelopers.com' : 'partner@apexrealty.com'}
                          value={loginEmail}
                          onChange={(e) => setLoginEmail(e.target.value)}
                          className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-sand-300 bg-sand-50/50 hover:bg-white focus:bg-white focus:border-forest focus:ring-4 focus:ring-forest/10 text-sm font-sans text-espresso-950 transition-all outline-none"
                        />
                      </div>
                    </div>

                    <div>
                      <div className="flex items-center justify-between mb-1.5">
                        <label className="block text-xs font-semibold text-espresso-800">
                          Password / पासवर्ड
                        </label>
                        <button
                          type="button"
                          onClick={() => {
                            setForgotEmail(loginEmail);
                            setShowForgotModal(true);
                            setForgotStatus(null);
                          }}
                          className="text-[11px] text-forest font-semibold hover:underline"
                        >
                          Forgot password?
                        </button>
                      </div>
                      <div className="relative flex items-center">
                        <Lock className="w-4 h-4 absolute left-3.5 text-espresso-400" />
                        <input
                          type={showLoginPassword ? 'text' : 'password'}
                          required
                          placeholder="••••••••••••"
                          value={loginPassword}
                          onChange={(e) => setLoginPassword(e.target.value)}
                          className="w-full pl-10 pr-10 py-2.5 rounded-xl border border-sand-300 bg-sand-50/50 hover:bg-white focus:bg-white focus:border-forest focus:ring-4 focus:ring-forest/10 text-sm font-sans text-espresso-950 transition-all outline-none"
                        />
                        <button
                          type="button"
                          onClick={() => setShowLoginPassword(!showLoginPassword)}
                          className="absolute right-3 text-espresso-400 hover:text-espresso-700 p-1"
                        >
                          {showLoginPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                        </button>
                      </div>
                    </div>

                    <div className="flex items-center justify-between pt-1">
                      <label className="flex items-center gap-2 cursor-pointer text-xs text-espresso-700">
                        <input
                          type="checkbox"
                          checked={rememberMe}
                          onChange={(e) => setRememberMe(e.target.checked)}
                          className="rounded border-sand-400 text-forest focus:ring-forest"
                        />
                        <span>Remember this workstation</span>
                      </label>
                    </div>

                    <button
                      type="submit"
                      disabled={isLoading || lockoutSeconds > 0}
                      className="w-full py-3.5 px-4 rounded-xl bg-forest hover:bg-forest-light disabled:opacity-50 text-white font-sans font-bold text-xs uppercase tracking-wider shadow-warm-sm hover:shadow-warm-md transition-all flex items-center justify-center gap-2 mt-2"
                    >
                      {isLoading ? (
                        <RefreshCw className="w-4 h-4 animate-spin" />
                      ) : (
                        <>
                          <span>Sign In to {role === 'developer' ? 'Developer Console' : 'Partner Portal'}</span>
                          <ArrowRight className="w-4 h-4" />
                        </>
                      )}
                    </button>

                    {/* Discreet Demo Quick-Access */}
                    <div className="pt-6 mt-4 border-t border-sand-200/80 text-center">
                      <p className="text-[11px] text-espresso-500 font-sans mb-2">
                        Evaluating the Shardeya platform?
                      </p>
                      <div className="flex items-center justify-center gap-4 text-xs font-semibold">
                        <button
                          type="button"
                          onClick={() => handleAutofill('dev')}
                          className="text-forest hover:text-forest-dark hover:underline transition-colors flex items-center gap-1"
                        >
                          <Building2 className="w-3.5 h-3.5" />
                          <span>Pre-fill Apex Developer</span>
                        </button>
                        <span className="text-sand-300">•</span>
                        <button
                          type="button"
                          onClick={() => handleAutofill('broker')}
                          className="text-forest hover:text-forest-dark hover:underline transition-colors flex items-center gap-1"
                        >
                          <Users className="w-3.5 h-3.5" />
                          <span>Pre-fill Diamond Broker</span>
                        </button>
                      </div>
                    </div>
                  </form>
                </div>
              )}

              {/* ========================================================
                  SIGN UP: Bespoke Visual Role Cards + Registration Form
                 ======================================================== */}
              {authMode === 'signup' && (
                <div>
                  
                  {/* Bespoke Visual Role Selection Cards (Elegant, not tabs!) */}
                  <div className="mb-6">
                    <label className="block text-xs font-semibold text-espresso-800 mb-2">
                      Select Organization Type / संस्था का प्रकार
                    </label>
                    <div className="grid grid-cols-2 gap-3">
                      <div
                        onClick={() => setRole('developer')}
                        className={`p-3.5 rounded-xl border-2 cursor-pointer transition-all ${
                          role === 'developer'
                            ? 'border-forest bg-forest/[0.03] shadow-warm-sm'
                            : 'border-sand-300/80 hover:border-sand-400 bg-sand-50/40'
                        }`}
                      >
                        <div className="flex items-center justify-between mb-1">
                          <Building2 className={`w-4 h-4 ${role === 'developer' ? 'text-forest' : 'text-espresso-500'}`} />
                          {role === 'developer' && <CheckCircle2 className="w-3.5 h-3.5 text-forest" />}
                        </div>
                        <div className="text-xs font-bold text-espresso-950">Real Estate Developer</div>
                        <div className="text-[10px] text-espresso-600 mt-0.5 leading-tight">
                          Promoters, plot layouts & RERA escrow
                        </div>
                      </div>

                      <div
                        onClick={() => setRole('broker')}
                        className={`p-3.5 rounded-xl border-2 cursor-pointer transition-all ${
                          role === 'broker'
                            ? 'border-forest bg-forest/[0.03] shadow-warm-sm'
                            : 'border-sand-300/80 hover:border-sand-400 bg-sand-50/40'
                        }`}
                      >
                        <div className="flex items-center justify-between mb-1">
                          <Users className={`w-4 h-4 ${role === 'broker' ? 'text-forest' : 'text-espresso-500'}`} />
                          {role === 'broker' && <CheckCircle2 className="w-3.5 h-3.5 text-forest" />}
                        </div>
                        <div className="text-xs font-bold text-espresso-950">Channel Partner</div>
                        <div className="text-[10px] text-espresso-600 mt-0.5 leading-tight">
                          Brokerages, lead lock & payouts
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* Sign Up Form */}
                  <form onSubmit={handleSignUp} className="space-y-3.5">
                    <div>
                      <label className="block text-xs font-semibold text-espresso-800 mb-1">
                        Authorized Representative Name / नाम
                      </label>
                      <input
                        type="text"
                        required
                        placeholder={role === 'developer' ? 'e.g. Rajeshwar Singhania' : 'e.g. Vikram Malhotra'}
                        value={signupName}
                        onChange={(e) => setSignupName(e.target.value)}
                        className="w-full px-3.5 py-2.5 rounded-xl border border-sand-300 bg-sand-50/50 hover:bg-white focus:bg-white focus:border-forest focus:ring-4 focus:ring-forest/10 text-sm font-sans text-espresso-950 transition-all outline-none"
                      />
                    </div>

                    <div>
                      <label className="block text-xs font-semibold text-espresso-800 mb-1">
                        Corporate Email Address / कार्य ईमेल
                      </label>
                      <div className="relative flex items-center">
                        <Mail className="w-4 h-4 absolute left-3.5 text-espresso-400" />
                        <input
                          type="email"
                          required
                          placeholder={role === 'developer' ? 'director@apexdevelopers.com' : 'partner@apexrealty.com'}
                          value={signupEmail}
                          onChange={(e) => setSignupEmail(e.target.value)}
                          className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-sand-300 bg-sand-50/50 hover:bg-white focus:bg-white focus:border-forest focus:ring-4 focus:ring-forest/10 text-sm font-sans text-espresso-950 transition-all outline-none"
                        />
                      </div>
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      <div>
                        <label className="block text-xs font-semibold text-espresso-800 mb-1">
                          {role === 'developer' ? 'Development Firm' : 'Brokerage / Syndicate'}
                        </label>
                        <input
                          type="text"
                          required
                          placeholder={role === 'developer' ? 'Apex Realty Developers' : 'Diamond Syndicate'}
                          value={signupOrg}
                          onChange={(e) => setSignupOrg(e.target.value)}
                          className="w-full px-3.5 py-2.5 rounded-xl border border-sand-300 bg-sand-50/50 hover:bg-white focus:bg-white focus:border-forest focus:ring-4 focus:ring-forest/10 text-sm font-sans text-espresso-950 transition-all outline-none"
                        />
                      </div>

                      <div>
                        <label className="block text-xs font-semibold text-espresso-800 mb-1">
                          RERA ID (Optional)
                        </label>
                        <input
                          type="text"
                          placeholder="MAHARERA/P518..."
                          value={signupRera}
                          onChange={(e) => setSignupRera(e.target.value.toUpperCase())}
                          className="w-full px-3.5 py-2.5 rounded-xl border border-sand-300 bg-sand-50/50 hover:bg-white focus:bg-white focus:border-forest focus:ring-4 focus:ring-forest/10 text-sm font-mono text-espresso-950 transition-all outline-none uppercase"
                        />
                      </div>
                    </div>

                    <div>
                      <label className="block text-xs font-semibold text-espresso-800 mb-1">
                        Enterprise Password
                      </label>
                      <div className="relative flex items-center">
                        <Lock className="w-4 h-4 absolute left-3.5 text-espresso-400" />
                        <input
                          type={showSignupPassword ? 'text' : 'password'}
                          required
                          placeholder="Min 8 chars, 1 uppercase, 1 symbol"
                          value={signupPassword}
                          onChange={(e) => setSignupPassword(e.target.value)}
                          className="w-full pl-10 pr-10 py-2.5 rounded-xl border border-sand-300 bg-sand-50/50 hover:bg-white focus:bg-white focus:border-forest focus:ring-4 focus:ring-forest/10 text-sm font-sans text-espresso-950 transition-all outline-none"
                        />
                        <button
                          type="button"
                          onClick={() => setShowSignupPassword(!showSignupPassword)}
                          className="absolute right-3 text-espresso-400 hover:text-espresso-700 p-1"
                        >
                          {showSignupPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                        </button>
                      </div>

                      {/* Minimalist 4-segment progress bar */}
                      {signupPassword && (
                        <div className="mt-2 space-y-1">
                          <div className="flex justify-between items-center text-[10px]">
                            <span className="text-espresso-500 font-sans">Strength:</span>
                            <span className="font-mono font-bold text-forest">{getStrengthBar().text}</span>
                          </div>
                          <div className="h-1 w-full bg-sand-200 rounded-full overflow-hidden">
                            <div className={`h-full ${getStrengthBar().color} ${getStrengthBar().width} transition-all duration-300`} />
                          </div>
                        </div>
                      )}
                    </div>

                    <div>
                      <label className="block text-xs font-semibold text-espresso-800 mb-1">
                        Confirm Password
                      </label>
                      <input
                        type={showSignupPassword ? 'text' : 'password'}
                        required
                        placeholder="Re-enter your password"
                        value={signupConfirmPassword}
                        onChange={(e) => setSignupConfirmPassword(e.target.value)}
                        className={`w-full px-3.5 py-2.5 rounded-xl border bg-sand-50/50 hover:bg-white focus:bg-white text-sm font-sans text-espresso-950 transition-all outline-none ${
                          signupConfirmPassword && signupPassword !== signupConfirmPassword
                            ? 'border-rose-400 focus:border-rose-600'
                            : 'border-sand-300 focus:border-forest focus:ring-4 focus:ring-forest/10'
                        }`}
                      />
                    </div>

                    <div className="pt-1">
                      <label className="flex items-start gap-2 cursor-pointer text-xs text-espresso-600 leading-snug">
                        <input
                          type="checkbox"
                          required
                          checked={agreeTerms}
                          onChange={(e) => setAgreeTerms(e.target.checked)}
                          className="mt-0.5 rounded border-sand-400 text-forest focus:ring-forest"
                        />
                        <span>
                          I represent an authorized real estate enterprise and agree to Shardeya's RERA compliance terms.
                        </span>
                      </label>
                    </div>

                    <button
                      type="submit"
                      disabled={isLoading}
                      className="w-full py-3.5 px-4 rounded-xl bg-forest hover:bg-forest-light disabled:opacity-50 text-white font-sans font-bold text-xs uppercase tracking-wider shadow-warm-sm hover:shadow-warm-md transition-all flex items-center justify-center gap-2 mt-3"
                    >
                      {isLoading ? (
                        <RefreshCw className="w-4 h-4 animate-spin" />
                      ) : (
                        <>
                          <span>Create Enterprise Workspace</span>
                          <ArrowRight className="w-4 h-4" />
                        </>
                      )}
                    </button>
                  </form>
                </div>
              )}

            </div>
          )}

        </div>

        {/* Bottom Minimal Footer */}
        <div className="pt-6 border-t border-sand-200/80 flex items-center justify-between text-[11px] text-espresso-500 font-sans">
          <span>© {new Date().getFullYear()} Shardeya Group Pvt. Ltd.</span>
          <span className="font-mono text-emerald-800">ISO 27001 • RERA Ready</span>
        </div>

      </div>

      {/* Forgot Password Modal */}
      {showForgotModal && (
        <div className="fixed inset-0 z-50 bg-espresso-950/60 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl border border-sand-300 max-w-md w-full p-6 shadow-2xl animate-fadeIn text-left">
            <div className="flex items-center justify-between pb-3 border-b border-sand-200">
              <div className="flex items-center gap-2">
                <KeyRound className="w-5 h-5 text-forest" />
                <h3 className="font-serif font-bold text-lg text-espresso-950">
                  Password Recovery
                </h3>
              </div>
              <button
                onClick={() => setShowForgotModal(false)}
                className="p-1 rounded-lg hover:bg-sand-100 text-espresso-600"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <p className="text-xs text-espresso-600 mt-3 leading-relaxed">
              Enter your corporate email address. A time-limited, encrypted reset authorization link will be dispatched to your inbox.
            </p>

            {forgotStatus && (
              <div className={`mt-3 p-3 rounded-xl text-xs flex items-start gap-2 ${
                forgotStatus.isError 
                  ? 'bg-rose-50 border border-rose-200 text-rose-800' 
                  : 'bg-emerald-50 border border-emerald-200 text-emerald-800'
              }`}>
                {forgotStatus.isError ? (
                  <AlertCircle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
                ) : (
                  <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0 mt-0.5" />
                )}
                <span>{forgotStatus.message}</span>
              </div>
            )}

            <form onSubmit={handleSendReset} className="mt-4 space-y-4">
              <div>
                <label className="block text-xs font-semibold text-espresso-800 mb-1">
                  Corporate Email
                </label>
                <input
                  type="email"
                  required
                  placeholder="director@apexdevelopers.com"
                  value={forgotEmail}
                  onChange={(e) => setForgotEmail(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white focus:border-forest text-sm font-sans text-espresso-950 outline-none"
                />
              </div>

              <div className="flex items-center gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setShowForgotModal(false)}
                  className="flex-1 py-2.5 rounded-xl border border-sand-300 text-espresso-700 hover:bg-sand-100 text-xs font-semibold"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={forgotStatus?.loading}
                  className="flex-1 py-2.5 rounded-xl bg-forest hover:bg-forest-light text-white text-xs font-bold uppercase tracking-wider shadow-warm-sm flex items-center justify-center gap-2"
                >
                  {forgotStatus?.loading ? <RefreshCw className="w-3.5 h-3.5 animate-spin" /> : 'Send Reset Link'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

    </div>
  );
};
