import React, { useState, useEffect } from 'react';
import { 
  ArrowLeft, ShieldCheck, Mail, Lock, 
  Eye, EyeOff, CheckCircle2, ArrowRight, Building2, 
  Users, RefreshCw, Check, AlertCircle, Sparkles,
  KeyRound, HelpCircle, X, ShieldAlert, BadgeCheck
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

  // Auth Mode: Sign In vs Sign Up
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

  // UI & Loading States
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [successToast, setSuccessToast] = useState('');
  
  // Forgot Password Modal State
  const [showForgotModal, setShowForgotModal] = useState(false);
  const [forgotEmail, setForgotEmail] = useState('');
  const [forgotStatus, setForgotStatus] = useState<{ loading: boolean; message: string; isError: boolean } | null>(null);

  // Lockout Countdown Timer
  const [lockoutSeconds, setLockoutSeconds] = useState(0);

  // Update lockout countdown if email changes
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

  // Sync mode with hash if changed externally
  useEffect(() => {
    if (initialMode) {
      setAuthMode(initialMode);
    }
  }, [initialMode]);

  // Password strength calculation for Sign Up
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

  const getStrengthLabel = () => {
    if (!signupPassword) return { label: 'Empty', color: 'bg-sand-200', text: 'text-espresso-400' };
    if (passwordScore <= 1) return { label: isHi ? 'कमजोर (असुरक्षित)' : 'Weak', color: 'bg-rose-500', text: 'text-rose-700' };
    if (passwordScore === 2) return { label: isHi ? 'मध्यम' : 'Fair', color: 'bg-amber-500', text: 'text-amber-700' };
    if (passwordScore === 3) return { label: isHi ? 'मजबूत' : 'Strong', color: 'bg-emerald-500', text: 'text-emerald-700' };
    return { label: isHi ? 'उद्यम-स्तरीय सुरक्षित' : 'Enterprise Fortified', color: 'bg-forest', text: 'text-forest' };
  };

  // Quick autofill demo accounts for frictionless testing
  const handleAutofillDemo = (targetRole: UserRole) => {
    setRole(targetRole);
    setAuthMode('login');
    setErrorMessage('');
    if (targetRole === 'developer') {
      setLoginEmail('director@apexdevelopers.com');
      setLoginPassword('Shardeya@2026');
    } else {
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
      setErrorMessage(isHi ? 'कृपया कॉर्पोरेट ईमेल और पासवर्ड दोनों दर्ज करें।' : 'Please enter both corporate email and password.');
      return;
    }

    setIsLoading(true);
    const result = await login(loginEmail, loginPassword, role);
    setIsLoading(false);

    if (!result.success) {
      setErrorMessage(result.error || 'Authentication failed. Please check credentials.');
      const status = getLockoutStatus(loginEmail);
      if (status.isLocked) {
        setLockoutSeconds(status.remainingSeconds);
      }
    } else {
      setSuccessToast(isHi ? 'प्रमाणीकरण सफल! आपका स्वागत है।' : 'Authentication verified. Welcome to Shardeya.');
    }
  };

  // Sign Up Handler
  const handleSignUp = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage('');
    setSuccessToast('');

    if (!signupName.trim()) {
      setErrorMessage(isHi ? 'कृपया अपना पूरा नाम दर्ज करें।' : 'Please enter your full legal name.');
      return;
    }

    if (!signupEmail.trim()) {
      setErrorMessage(isHi ? 'कृपया कॉर्पोरेट ईमेल दर्ज करें।' : 'Please enter your corporate email address.');
      return;
    }

    if (!signupOrg.trim()) {
      setErrorMessage(isHi ? 'कृपया अपनी संस्था / फर्म का नाम दर्ज करें।' : 'Please enter your organization or firm name.');
      return;
    }

    if (passwordScore < 3) {
      setErrorMessage(isHi ? 'पासवर्ड सुरक्षा मानकों को पूरा करें (न्यूनतम 8 अक्षर, बड़ा अक्षर, संख्या व विशेष चिन्ह)।' : 'Please satisfy password complexity criteria (minimum 8 characters, uppercase, number & symbol).');
      return;
    }

    if (signupPassword !== signupConfirmPassword) {
      setErrorMessage(isHi ? 'पासवर्ड और पुष्टि पासवर्ड मेल नहीं खाते।' : 'Passwords do not match. Please verify and re-type.');
      return;
    }

    if (!agreeTerms) {
      setErrorMessage(isHi ? 'कृपया नियम एवं शर्तों और रेरा नीतियों को स्वीकार करें।' : 'Please accept the Shardeya Enterprise Terms & RERA Privacy Protocol.');
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
      setErrorMessage(result.error || 'Account creation failed. Please check inputs.');
    } else {
      try {
        confetti({
          particleCount: 80,
          spread: 70,
          origin: { y: 0.6 }
        });
      } catch {
        // Safe fallback
      }
      setSuccessToast(isHi ? 'उद्यम खाता सफलतापूर्वक बनाया गया!' : 'Enterprise account successfully provisioned!');
    }
  };

  // Password Reset Dispatch Handler
  const handleSendReset = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!forgotEmail.trim()) {
      setForgotStatus({
        loading: false,
        message: isHi ? 'कृपया अपना पंजीकृत कॉर्पोरेट ईमेल दर्ज करें।' : 'Please enter your registered corporate email.',
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
    <div className="min-h-screen bg-sand-100 text-espresso-950 font-sans flex flex-col justify-between selection:bg-forest/15 selection:text-forest">
      
      {/* Enterprise Top Header */}
      <header className="w-full bg-white/90 backdrop-blur-xl border-b border-sand-300 py-3.5 px-4 sm:px-8 shadow-warm-sm sticky top-0 z-30">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          
          {/* Brand Logo */}
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-lg bg-forest flex items-center justify-center text-white font-serif font-bold text-lg shadow-warm-sm">
              S
            </div>
            <div>
              <span className="font-serif font-bold text-xl tracking-tight text-espresso-950 block leading-none">
                SHARDEYA GROUP
              </span>
              <span className="text-[10px] font-mono text-espresso-600 uppercase tracking-wider block mt-1">
                Real Estate Enterprise OS • Secure Auth
              </span>
            </div>
          </div>

          {/* Quick Return Button */}
          <button
            onClick={onBack}
            className="inline-flex items-center gap-2 px-4 py-2 rounded-lg border border-sand-300 bg-sand-50 hover:bg-sand-200 text-espresso-800 text-xs font-bold transition-all shadow-warm-sm group"
          >
            <ArrowLeft className="w-4 h-4 text-forest group-hover:-translate-x-1 transition-transform" />
            <span>{isHi ? 'वेबसाइट पर वापस जाएं' : 'Back to Website'}</span>
          </button>
        </div>
      </header>

      {/* Main Authentication Container */}
      <main className="flex-1 flex items-center justify-center px-4 py-8 sm:py-12">
        <div className="w-full max-w-lg">

          {/* SUCCESS STATE: Authenticated Active Session Card */}
          {user ? (
            <div className="bg-white rounded-2xl border border-sand-300 p-8 shadow-warm-lg text-center animate-fadeIn">
              <div className="w-20 h-20 rounded-full bg-emerald-100 border-2 border-emerald-300 text-emerald-800 flex items-center justify-center mx-auto mb-4 font-serif font-bold text-2xl shadow-warm-sm">
                {user.avatarInitials}
              </div>

              <div className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full bg-emerald-50 text-emerald-800 border border-emerald-200 text-xs font-mono font-bold mb-3">
                <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                <span>ACTIVE ENTERPRISE SESSION</span>
              </div>

              <h2 className="font-serif font-bold text-2xl sm:text-3xl text-espresso-950">
                {user.name}
              </h2>
              <p className="text-sm text-espresso-700 font-sans font-medium mt-1">
                {user.designation}
              </p>
              <div className="text-xs font-mono text-forest font-semibold mt-1">
                {user.organization}
              </div>

              {user.reraNumber && (
                <div className="mt-2 inline-block px-2.5 py-0.5 rounded bg-sand-100 border border-sand-200 text-[11px] font-mono text-espresso-600">
                  RERA: <span className="font-bold text-espresso-800">{user.reraNumber}</span>
                </div>
              )}

              {/* Security Telemetry Details */}
              <div className="mt-6 p-4 rounded-xl bg-sand-50 border border-sand-200 text-left text-xs space-y-2.5">
                <div className="flex justify-between items-center text-espresso-600">
                  <span>Authorized Identity:</span>
                  <span className="font-mono font-bold text-espresso-900">{user.email}</span>
                </div>
                <div className="flex justify-between items-center text-espresso-600">
                  <span>Portal Role:</span>
                  <span className="font-bold text-espresso-900 capitalize">
                    {user.role === 'developer' ? 'Real Estate Builder / Developer' : 'Authorized Channel Partner'}
                  </span>
                </div>
                <div className="flex justify-between items-center text-espresso-600">
                  <span>Session Encryption:</span>
                  <span className="font-mono font-bold text-emerald-700 flex items-center gap-1">
                    <ShieldCheck className="w-3.5 h-3.5 text-forest" />
                    256-Bit TLS 1.3 • AES-GCM
                  </span>
                </div>
                <div className="flex justify-between items-center text-espresso-600">
                  <span>Session ID:</span>
                  <span className="font-mono text-espresso-500 text-[11px]">{user.id}</span>
                </div>
              </div>

              {/* Action Buttons */}
              <div className="mt-6 space-y-3">
                <button
                  onClick={onBack}
                  className="w-full py-3.5 px-4 rounded-xl bg-forest hover:bg-forest-light text-white font-sans font-bold text-xs uppercase tracking-wider shadow-warm-sm transition-all flex items-center justify-center gap-2"
                >
                  <span>Return to Shardeya Platform</span>
                  <ArrowRight className="w-4 h-4" />
                </button>

                <button
                  onClick={() => {
                    logout();
                    setSuccessToast('');
                    setErrorMessage('');
                  }}
                  className="w-full py-2.5 px-4 rounded-xl border border-sand-300 text-espresso-700 hover:bg-sand-100 text-xs font-sans font-semibold transition-all"
                >
                  Sign Out of Enterprise Session
                </button>
              </div>
            </div>
          ) : (
            /* UNTOUCHED AUTHENTICATION INTERFACE (Sign In / Sign Up) */
            <div className="bg-white rounded-2xl border border-sand-300 shadow-warm-lg overflow-hidden">
              
              {/* Primary Dual Mode Navigation: Sign In vs Sign Up */}
              <div className="grid grid-cols-2 border-b border-sand-300 bg-sand-100/70 p-1.5 gap-1.5">
                <button
                  type="button"
                  onClick={() => {
                    setAuthMode('login');
                    setErrorMessage('');
                    setSuccessToast('');
                  }}
                  className={`py-2.5 px-3 rounded-xl text-xs font-sans font-bold transition-all flex items-center justify-center gap-2 ${
                    authMode === 'login'
                      ? 'bg-white text-forest shadow-warm-sm border border-sand-300/80'
                      : 'text-espresso-600 hover:text-espresso-950 hover:bg-white/50'
                  }`}
                >
                  <KeyRound className="w-3.5 h-3.5" />
                  <span>{isHi ? 'साइन इन (लॉगिन)' : 'Sign In'}</span>
                </button>

                <button
                  type="button"
                  onClick={() => {
                    setAuthMode('signup');
                    setErrorMessage('');
                    setSuccessToast('');
                  }}
                  className={`py-2.5 px-3 rounded-xl text-xs font-sans font-bold transition-all flex items-center justify-center gap-2 ${
                    authMode === 'signup'
                      ? 'bg-white text-forest shadow-warm-sm border border-sand-300/80'
                      : 'text-espresso-600 hover:text-espresso-950 hover:bg-white/50'
                  }`}
                >
                  <Sparkles className="w-3.5 h-3.5" />
                  <span>{isHi ? 'नया खाता बनाएं' : 'Create Account'}</span>
                </button>
              </div>

              {/* Persona Switcher: Developer vs Channel Partner */}
              <div className="grid grid-cols-2 border-b border-sand-300 bg-sand-50">
                <button
                  type="button"
                  onClick={() => {
                    setRole('developer');
                    setErrorMessage('');
                  }}
                  className={`py-3 px-4 text-xs font-sans font-bold flex items-center justify-center gap-2 transition-all border-b-2 ${
                    role === 'developer'
                      ? 'border-forest bg-white text-forest shadow-warm-sm'
                      : 'border-transparent text-espresso-600 hover:text-espresso-900'
                  }`}
                >
                  <Building2 className="w-4 h-4" />
                  <span>{isHi ? 'डेवलपर कंसोल' : 'Developer Console'}</span>
                </button>

                <button
                  type="button"
                  onClick={() => {
                    setRole('broker');
                    setErrorMessage('');
                  }}
                  className={`py-3 px-4 text-xs font-sans font-bold flex items-center justify-center gap-2 transition-all border-b-2 ${
                    role === 'broker'
                      ? 'border-forest bg-white text-forest shadow-warm-sm'
                      : 'border-transparent text-espresso-600 hover:text-espresso-900'
                  }`}
                >
                  <Users className="w-4 h-4" />
                  <span>{isHi ? 'चैनल पार्टनर' : 'Channel Partner'}</span>
                </button>
              </div>

              <div className="p-6 sm:p-8">
                
                {/* Header Information */}
                <div className="mb-5 text-left">
                  <h1 className="font-serif font-bold text-2xl text-espresso-950">
                    {authMode === 'login'
                      ? (role === 'developer' ? (isHi ? 'डेवलपर लॉगिन' : 'Sign in to Developer Console') : (isHi ? 'चैनल पार्टनर लॉगिन' : 'Sign in to Partner Portal'))
                      : (role === 'developer' ? (isHi ? 'डेवलपर खाता बनाएं' : 'Create Developer Account') : (isHi ? 'चैनल पार्टनर खाता बनाएं' : 'Create Channel Partner Account'))}
                  </h1>
                  <p className="text-xs text-espresso-600 mt-1">
                    {role === 'developer'
                      ? 'Access plot inventory, unit allotments, demand milestones, and RERA compliance.'
                      : 'Track lead lock protection, buyer site visits, and instant brokerage payouts.'}
                  </p>
                </div>

                {/* Status Banners */}
                {errorMessage && (
                  <div className="mb-4 p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 text-xs font-sans text-left flex items-start gap-2 animate-fadeIn">
                    <AlertCircle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
                    <span>{errorMessage}</span>
                  </div>
                )}

                {successToast && (
                  <div className="mb-4 p-3 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs font-sans text-left flex items-start gap-2 animate-fadeIn">
                    <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0 mt-0.5" />
                    <span>{successToast}</span>
                  </div>
                )}

                {lockoutSeconds > 0 && (
                  <div className="mb-4 p-3 rounded-xl bg-amber-50 border border-amber-200 text-amber-900 text-xs font-sans text-left flex items-start gap-2 animate-fadeIn">
                    <ShieldAlert className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
                    <div>
                      <span className="font-bold">Workstation Lockout Active:</span> Excessive failed attempts detected. Retrying is locked for <span className="font-mono font-bold text-amber-800">{lockoutSeconds} seconds</span>.
                    </div>
                  </div>
                )}

                {/* ========================================================
                    MODE 1: SIGN IN FORM (Email & Password ONLY)
                   ======================================================== */}
                {authMode === 'login' && (
                  <form onSubmit={handleSignIn} className="space-y-4 text-left">
                    
                    {/* Corporate Email */}
                    <div>
                      <label className="block text-xs font-bold text-espresso-800 uppercase tracking-wider mb-1.5">
                        Corporate Email / संस्थागत ईमेल
                      </label>
                      <div className="relative flex items-center">
                        <Mail className="w-4 h-4 absolute left-3.5 text-espresso-500" />
                        <input
                          type="email"
                          required
                          placeholder={role === 'developer' ? 'director@apexdevelopers.com' : 'partner@apexrealty.com'}
                          value={loginEmail}
                          onChange={(e) => setLoginEmail(e.target.value)}
                          className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white focus:border-forest text-sm font-sans text-espresso-950 focus:outline-none transition-all"
                        />
                      </div>
                    </div>

                    {/* Password */}
                    <div>
                      <div className="flex items-center justify-between mb-1.5">
                        <label className="block text-xs font-bold text-espresso-800 uppercase tracking-wider">
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
                        <Lock className="w-4 h-4 absolute left-3.5 text-espresso-500" />
                        <input
                          type={showLoginPassword ? 'text' : 'password'}
                          required
                          placeholder="••••••••••••"
                          value={loginPassword}
                          onChange={(e) => setLoginPassword(e.target.value)}
                          className="w-full pl-10 pr-10 py-2.5 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white focus:border-forest text-sm font-sans text-espresso-950 focus:outline-none transition-all"
                        />
                        <button
                          type="button"
                          onClick={() => setShowLoginPassword(!showLoginPassword)}
                          className="absolute right-3 text-espresso-500 hover:text-espresso-800 p-1"
                          title={showLoginPassword ? 'Hide password' : 'Show password'}
                        >
                          {showLoginPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                        </button>
                      </div>
                    </div>

                    {/* Remember me */}
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

                    {/* Submit Button */}
                    <button
                      type="submit"
                      disabled={isLoading || lockoutSeconds > 0}
                      className="w-full py-3 px-4 rounded-xl bg-forest hover:bg-forest-light disabled:opacity-50 disabled:cursor-not-allowed text-white font-sans font-bold text-xs uppercase tracking-wider shadow-warm-sm transition-all flex items-center justify-center gap-2 mt-2"
                    >
                      {isLoading ? (
                        <RefreshCw className="w-4 h-4 animate-spin" />
                      ) : (
                        <>
                          <span>
                            {role === 'developer' ? 'Sign In to Developer Console' : 'Sign In to Partner Portal'}
                          </span>
                          <ArrowRight className="w-4 h-4" />
                        </>
                      )}
                    </button>

                    {/* Quick Demo Pre-fill Pill (Frictionless Test Access) */}
                    <div className="pt-4 border-t border-sand-200 text-center">
                      <p className="text-[11px] text-espresso-500 mb-2">
                        Testing the CRM prototype? Pre-fill verified demo credentials:
                      </p>
                      <div className="flex items-center justify-center gap-2">
                        <button
                          type="button"
                          onClick={() => handleAutofillDemo('developer')}
                          className={`text-[11px] font-mono px-2.5 py-1 rounded-lg border transition-all ${
                            role === 'developer' && loginEmail === 'director@apexdevelopers.com'
                              ? 'bg-forest/10 border-forest text-forest font-bold'
                              : 'bg-sand-50 border-sand-300 text-espresso-700 hover:bg-sand-100'
                          }`}
                        >
                          Apex Developer Demo
                        </button>
                        <button
                          type="button"
                          onClick={() => handleAutofillDemo('broker')}
                          className={`text-[11px] font-mono px-2.5 py-1 rounded-lg border transition-all ${
                            role === 'broker' && loginEmail === 'partner@apexrealty.com'
                              ? 'bg-forest/10 border-forest text-forest font-bold'
                              : 'bg-sand-50 border-sand-300 text-espresso-700 hover:bg-sand-100'
                          }`}
                        >
                          Diamond Broker Demo
                        </button>
                      </div>
                    </div>
                  </form>
                )}

                {/* ========================================================
                    MODE 2: SIGN UP FORM (Enterprise Registration)
                   ======================================================== */}
                {authMode === 'signup' && (
                  <form onSubmit={handleSignUp} className="space-y-4 text-left animate-fadeIn">
                    
                    {/* Full Name */}
                    <div>
                      <label className="block text-xs font-bold text-espresso-800 uppercase tracking-wider mb-1.5">
                        Authorized Representative Name / पूरा नाम
                      </label>
                      <input
                        type="text"
                        required
                        placeholder={role === 'developer' ? 'e.g. Rajeshwar Singhania' : 'e.g. Vikram Malhotra'}
                        value={signupName}
                        onChange={(e) => setSignupName(e.target.value)}
                        className="w-full px-3.5 py-2.5 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white focus:border-forest text-sm font-sans text-espresso-950 focus:outline-none transition-all"
                      />
                    </div>

                    {/* Corporate Email */}
                    <div>
                      <label className="block text-xs font-bold text-espresso-800 uppercase tracking-wider mb-1.5">
                        Corporate Email / कार्य ईमेल
                      </label>
                      <div className="relative flex items-center">
                        <Mail className="w-4 h-4 absolute left-3.5 text-espresso-500" />
                        <input
                          type="email"
                          required
                          placeholder={role === 'developer' ? 'rajeshwar@apexdevelopers.com' : 'vikram@syndicaterealty.com'}
                          value={signupEmail}
                          onChange={(e) => setSignupEmail(e.target.value)}
                          className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white focus:border-forest text-sm font-sans text-espresso-950 focus:outline-none transition-all"
                        />
                      </div>
                    </div>

                    {/* Organization & Designation Grid */}
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      <div>
                        <label className="block text-xs font-bold text-espresso-800 uppercase tracking-wider mb-1.5">
                          {role === 'developer' ? 'Development Firm' : 'Brokerage / Syndicate'}
                        </label>
                        <input
                          type="text"
                          required
                          placeholder={role === 'developer' ? 'Apex Realty Developers' : 'Cityline Brokerage'}
                          value={signupOrg}
                          onChange={(e) => setSignupOrg(e.target.value)}
                          className="w-full px-3.5 py-2.5 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white focus:border-forest text-sm font-sans text-espresso-950 focus:outline-none transition-all"
                        />
                      </div>

                      <div>
                        <label className="block text-xs font-bold text-espresso-800 uppercase tracking-wider mb-1.5">
                          Designation (Optional)
                        </label>
                        <input
                          type="text"
                          placeholder={role === 'developer' ? 'Managing Director' : 'Principal Broker'}
                          value={signupDesignation}
                          onChange={(e) => setSignupDesignation(e.target.value)}
                          className="w-full px-3.5 py-2.5 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white focus:border-forest text-sm font-sans text-espresso-950 focus:outline-none transition-all"
                        />
                      </div>
                    </div>

                    {/* RERA Number (Cadastral Monospace) */}
                    <div>
                      <div className="flex items-center justify-between mb-1.5">
                        <label className="block text-xs font-bold text-espresso-800 uppercase tracking-wider">
                          RERA Registration ID
                        </label>
                        <span className="text-[10px] text-espresso-500 font-mono">
                          {role === 'developer' ? 'Project / Promoter ID' : 'Agent RERA Certificate'}
                        </span>
                      </div>
                      <input
                        type="text"
                        placeholder="e.g. MAHARERA/P51800019283"
                        value={signupRera}
                        onChange={(e) => setSignupRera(e.target.value.toUpperCase())}
                        className="w-full px-3.5 py-2.5 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white focus:border-forest text-sm font-mono text-espresso-950 focus:outline-none transition-all tracking-wide"
                      />
                    </div>

                    {/* Password & Strength Meter */}
                    <div>
                      <label className="block text-xs font-bold text-espresso-800 uppercase tracking-wider mb-1.5">
                        Enterprise Password / पासवर्ड
                      </label>
                      <div className="relative flex items-center">
                        <Lock className="w-4 h-4 absolute left-3.5 text-espresso-500" />
                        <input
                          type={showSignupPassword ? 'text' : 'password'}
                          required
                          placeholder="Min 8 chars, 1 uppercase, 1 symbol"
                          value={signupPassword}
                          onChange={(e) => setSignupPassword(e.target.value)}
                          className="w-full pl-10 pr-10 py-2.5 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white focus:border-forest text-sm font-sans text-espresso-950 focus:outline-none transition-all"
                        />
                        <button
                          type="button"
                          onClick={() => setShowSignupPassword(!showSignupPassword)}
                          className="absolute right-3 text-espresso-500 hover:text-espresso-800 p-1"
                        >
                          {showSignupPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                        </button>
                      </div>

                      {/* Password Strength Indicator */}
                      {signupPassword && (
                        <div className="mt-2 space-y-1.5">
                          <div className="flex items-center justify-between text-[11px]">
                            <span className="text-espresso-600 font-sans">Strength:</span>
                            <span className={`font-bold font-mono ${getStrengthLabel().text}`}>
                              {getStrengthLabel().label}
                            </span>
                          </div>
                          <div className="h-1.5 w-full bg-sand-200 rounded-full overflow-hidden flex gap-1">
                            <div className={`h-full flex-1 rounded-full transition-all ${passwordScore >= 1 ? getStrengthLabel().color : 'bg-transparent'}`} />
                            <div className={`h-full flex-1 rounded-full transition-all ${passwordScore >= 2 ? getStrengthLabel().color : 'bg-transparent'}`} />
                            <div className={`h-full flex-1 rounded-full transition-all ${passwordScore >= 3 ? getStrengthLabel().color : 'bg-transparent'}`} />
                            <div className={`h-full flex-1 rounded-full transition-all ${passwordScore >= 4 ? getStrengthLabel().color : 'bg-transparent'}`} />
                          </div>

                          {/* Interactive Checklist */}
                          <div className="grid grid-cols-2 gap-1 pt-1 text-[10px] text-espresso-600">
                            <span className={`flex items-center gap-1 ${passwordCriteria.length ? 'text-emerald-700 font-semibold' : ''}`}>
                              <Check className={`w-3 h-3 ${passwordCriteria.length ? 'text-emerald-600' : 'text-espresso-400'}`} />
                              8+ Characters
                            </span>
                            <span className={`flex items-center gap-1 ${passwordCriteria.hasUpper ? 'text-emerald-700 font-semibold' : ''}`}>
                              <Check className={`w-3 h-3 ${passwordCriteria.hasUpper ? 'text-emerald-600' : 'text-espresso-400'}`} />
                              Uppercase (A-Z)
                            </span>
                            <span className={`flex items-center gap-1 ${passwordCriteria.hasNumber ? 'text-emerald-700 font-semibold' : ''}`}>
                              <Check className={`w-3 h-3 ${passwordCriteria.hasNumber ? 'text-emerald-600' : 'text-espresso-400'}`} />
                              Number (0-9)
                            </span>
                            <span className={`flex items-center gap-1 ${passwordCriteria.hasSpecial ? 'text-emerald-700 font-semibold' : ''}`}>
                              <Check className={`w-3 h-3 ${passwordCriteria.hasSpecial ? 'text-emerald-600' : 'text-espresso-400'}`} />
                              Special Symbol (!@#$)
                            </span>
                          </div>
                        </div>
                      )}
                    </div>

                    {/* Confirm Password */}
                    <div>
                      <label className="block text-xs font-bold text-espresso-800 uppercase tracking-wider mb-1.5">
                        Confirm Password / पासवर्ड की पुष्टि करें
                      </label>
                      <input
                        type={showSignupPassword ? 'text' : 'password'}
                        required
                        placeholder="Re-enter your password"
                        value={signupConfirmPassword}
                        onChange={(e) => setSignupConfirmPassword(e.target.value)}
                        className={`w-full px-3.5 py-2.5 rounded-xl border bg-sand-50 focus:bg-white text-sm font-sans text-espresso-950 focus:outline-none transition-all ${
                          signupConfirmPassword && signupPassword !== signupConfirmPassword
                            ? 'border-rose-400 focus:border-rose-600'
                            : 'border-sand-300 focus:border-forest'
                        }`}
                      />
                      {signupConfirmPassword && (
                        <p className={`text-[10px] mt-1 font-semibold ${signupPassword === signupConfirmPassword ? 'text-emerald-700' : 'text-rose-600'}`}>
                          {signupPassword === signupConfirmPassword ? '✓ Passwords match perfectly' : '✗ Passwords do not match'}
                        </p>
                      )}
                    </div>

                    {/* Terms Checkbox */}
                    <div className="pt-2">
                      <label className="flex items-start gap-2.5 cursor-pointer text-xs text-espresso-700">
                        <input
                          type="checkbox"
                          required
                          checked={agreeTerms}
                          onChange={(e) => setAgreeTerms(e.target.checked)}
                          className="mt-0.5 rounded border-sand-400 text-forest focus:ring-forest"
                        />
                        <span>
                          I certify that I represent an authorized real estate enterprise and agree to Shardeya's RERA compliance protocol and enterprise terms.
                        </span>
                      </label>
                    </div>

                    {/* Submit Registration */}
                    <button
                      type="submit"
                      disabled={isLoading}
                      className="w-full py-3.5 px-4 rounded-xl bg-forest hover:bg-forest-light disabled:opacity-50 text-white font-sans font-bold text-xs uppercase tracking-wider shadow-warm-sm transition-all flex items-center justify-center gap-2 mt-4"
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
                )}

              </div>

              {/* Secure Card Footer */}
              <div className="bg-sand-50 px-6 py-3 border-t border-sand-200 flex items-center justify-between text-[11px] text-espresso-600 font-sans">
                <span className="flex items-center gap-1.5 text-espresso-700">
                  <ShieldCheck className="w-3.5 h-3.5 text-forest" />
                  <span>256-Bit TLS 1.3 Enterprise Standard</span>
                </span>
                <span className="text-espresso-500 font-mono">AES-256 Auth</span>
              </div>
            </div>
          )}

        </div>
      </main>

      {/* Forgot Password Modal */}
      {showForgotModal && (
        <div className="fixed inset-0 z-50 bg-espresso-950/60 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl border border-sand-300 max-w-md w-full p-6 shadow-warm-lg animate-fadeIn text-left">
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

            <p className="text-xs text-espresso-600 mt-3">
              Enter your corporate email address. If an account is registered with Shardeya, an encrypted, time-limited password reset link will be dispatched immediately.
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
                <label className="block text-xs font-bold text-espresso-800 uppercase tracking-wider mb-1">
                  Corporate Email
                </label>
                <input
                  type="email"
                  required
                  placeholder="director@developer.com"
                  value={forgotEmail}
                  onChange={(e) => setForgotEmail(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white focus:border-forest text-sm font-sans text-espresso-950 focus:outline-none"
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

      {/* Enterprise Footer */}
      <footer className="w-full py-5 px-4 text-center text-xs text-espresso-500 font-mono border-t border-sand-200 bg-white/50">
        © {new Date().getFullYear()} Shardeya Group Pvt. Ltd. All rights reserved. • ISO 27001 & RERA Architecture Ready
      </footer>

    </div>
  );
};
