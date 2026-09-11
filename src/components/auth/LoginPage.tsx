import React, { useState, useEffect } from 'react';
import { 
  ArrowLeft, Mail, Lock, Eye, EyeOff, 
  CheckCircle2, ArrowRight, Building2, Users, 
  RefreshCw, AlertCircle, KeyRound, X, ShieldAlert 
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

  // Mode: Sign In vs Sign Up (toggled naturally via text link, not stacked tabs)
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

  // Password strength calculation
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
    <div className="min-h-screen bg-sand-100 font-sans selection:bg-forest/15 selection:text-forest flex flex-col justify-between">
      
      {/* Clean Minimalist Header */}
      <header className="w-full bg-white/80 backdrop-blur-md border-b border-sand-300 py-4 px-4 sm:px-8">
        <div className="max-w-5xl mx-auto flex items-center justify-between">
          <a href="#" onClick={(e) => { e.preventDefault(); onBack(); }} className="flex items-center gap-3 group">
            <div className="w-9 h-9 rounded-lg bg-forest flex items-center justify-center text-white font-serif font-bold text-lg shadow-warm-sm transition-transform group-hover:scale-105">
              S
            </div>
            <span className="font-serif font-bold text-2xl tracking-tight text-espresso-950">
              SHARDEYA
            </span>
          </a>

          <button
            onClick={onBack}
            className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-lg border border-sand-300 bg-sand-50 hover:bg-sand-200 text-espresso-800 text-xs font-semibold transition-all shadow-warm-sm group"
          >
            <ArrowLeft className="w-3.5 h-3.5 text-forest transition-transform group-hover:-translate-x-0.5" />
            <span>{isHi ? 'वेबसाइट पर वापस जाएं' : 'Back to Website'}</span>
          </button>
        </div>
      </header>

      {/* Main Centered Content */}
      <main className="flex-1 flex items-center justify-center px-4 py-10 sm:py-14">
        <div className="w-full max-w-md">

          {/* 1. AUTHENTICATED STATE */}
          {user ? (
            <div className="bg-white rounded-2xl border border-sand-300 p-8 shadow-warm-md text-center animate-fadeIn space-y-6">
              <div className="w-16 h-16 rounded-full bg-emerald-50 border-2 border-emerald-200 text-forest flex items-center justify-center mx-auto font-serif font-bold text-xl shadow-warm-sm">
                {user.avatarInitials}
              </div>

              <div>
                <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-emerald-50 text-emerald-800 border border-emerald-200 text-xs font-mono font-bold mb-3">
                  <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />
                  <span>AUTHENTICATED SESSION</span>
                </div>
                <h2 className="font-serif font-bold text-2xl text-espresso-950">
                  {user.name}
                </h2>
                <p className="text-xs text-espresso-600 font-sans mt-1">
                  {user.designation}
                </p>
                <p className="text-xs font-mono text-forest font-semibold mt-0.5">
                  {user.organization} {user.reraNumber ? `• ${user.reraNumber}` : ''}
                </p>
              </div>

              <div className="p-4 rounded-xl bg-sand-50 border border-sand-200 text-left text-xs space-y-2 font-sans">
                <div className="flex justify-between text-espresso-600">
                  <span>Corporate Identity:</span>
                  <span className="font-mono font-semibold text-espresso-900">{user.email}</span>
                </div>
                <div className="flex justify-between text-espresso-600">
                  <span>Workspace Role:</span>
                  <span className="font-semibold text-espresso-900 capitalize">
                    {user.role === 'developer' ? 'Developer Console' : 'Channel Partner Network'}
                  </span>
                </div>
              </div>

              <div className="space-y-2 pt-2">
                <button
                  onClick={onBack}
                  className="w-full py-3 px-4 rounded-xl bg-forest hover:bg-forest-light text-white font-sans font-bold text-xs uppercase tracking-wider shadow-warm-sm transition-all flex items-center justify-center gap-2"
                >
                  <span>Return to Website</span>
                  <ArrowRight className="w-4 h-4" />
                </button>

                <button
                  onClick={() => {
                    logout();
                    setSuccessToast('');
                  }}
                  className="w-full py-2.5 px-4 rounded-xl border border-sand-300 text-espresso-700 hover:bg-sand-100 text-xs font-semibold transition-all"
                >
                  Sign Out
                </button>
              </div>
            </div>
          ) : (
            /* 2. AUTHENTICATION CARD */
            <div className="bg-white rounded-2xl border border-sand-300 shadow-warm-md p-7 sm:p-9">
              
              {/* Card Title */}
              <div className="mb-6 text-center">
                <h1 className="font-serif font-bold text-2xl sm:text-3xl text-espresso-950 tracking-tight">
                  {authMode === 'login'
                    ? (isHi ? 'लॉगिन करें' : 'Sign in to Shardeya')
                    : (isHi ? 'खाता बनाएं' : 'Create an Account')}
                </h1>
                <p className="text-xs text-espresso-600 mt-1.5">
                  {authMode === 'login'
                    ? 'Enter your corporate credentials to access your portal.'
                    : 'Register your developer firm or broker syndicate.'}
                </p>
              </div>

              {/* Single Persona Selector (NO stacked tabs!) */}
              <div className="mb-6 p-1 bg-sand-100 rounded-xl border border-sand-200 flex gap-1">
                <button
                  type="button"
                  onClick={() => {
                    setRole('developer');
                    setErrorMessage('');
                  }}
                  className={`flex-1 py-2 rounded-lg text-xs font-sans font-semibold transition-all flex items-center justify-center gap-2 ${
                    role === 'developer'
                      ? 'bg-white text-espresso-950 shadow-warm-sm font-bold'
                      : 'text-espresso-600 hover:text-espresso-900'
                  }`}
                >
                  <Building2 className={`w-3.5 h-3.5 ${role === 'developer' ? 'text-forest' : 'text-espresso-400'}`} />
                  <span>{isHi ? 'डेवलपर' : 'Developer'}</span>
                </button>

                <button
                  type="button"
                  onClick={() => {
                    setRole('broker');
                    setErrorMessage('');
                  }}
                  className={`flex-1 py-2 rounded-lg text-xs font-sans font-semibold transition-all flex items-center justify-center gap-2 ${
                    role === 'broker'
                      ? 'bg-white text-espresso-950 shadow-warm-sm font-bold'
                      : 'text-espresso-600 hover:text-espresso-900'
                  }`}
                >
                  <Users className={`w-3.5 h-3.5 ${role === 'broker' ? 'text-forest' : 'text-espresso-400'}`} />
                  <span>{isHi ? 'चैनल पार्टनर' : 'Channel Partner'}</span>
                </button>
              </div>

              {/* Alerts */}
              {errorMessage && (
                <div className="mb-4 p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 text-xs flex items-start gap-2 animate-fadeIn text-left">
                  <AlertCircle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
                  <span>{errorMessage}</span>
                </div>
              )}

              {successToast && (
                <div className="mb-4 p-3 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs flex items-start gap-2 animate-fadeIn text-left">
                  <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0 mt-0.5" />
                  <span>{successToast}</span>
                </div>
              )}

              {lockoutSeconds > 0 && (
                <div className="mb-4 p-3 rounded-xl bg-amber-50 border border-amber-200 text-amber-900 text-xs flex items-start gap-2 animate-fadeIn text-left">
                  <ShieldAlert className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
                  <div>
                    <span className="font-bold">Lockout Active:</span> Please wait <span className="font-mono font-bold text-amber-800">{lockoutSeconds}s</span> before retrying.
                  </div>
                </div>
              )}

              {/* ========================================================
                  SIGN IN FORM
                 ======================================================== */}
              {authMode === 'login' && (
                <form onSubmit={handleSignIn} className="space-y-4 text-left">
                  <div>
                    <label className="block text-xs font-semibold text-espresso-800 mb-1.5">
                      Corporate Email
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
                        Password
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
                    className="w-full py-3 px-4 rounded-xl bg-forest hover:bg-forest-light disabled:opacity-50 text-white font-sans font-bold text-xs uppercase tracking-wider shadow-warm-sm transition-all flex items-center justify-center gap-2 mt-2"
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

                  {/* Mode switch link */}
                  <div className="text-center pt-3 text-xs text-espresso-600">
                    <span>Don't have an enterprise account? </span>
                    <button
                      type="button"
                      onClick={() => {
                        setAuthMode('signup');
                        setErrorMessage('');
                        setSuccessToast('');
                        window.location.hash = 'signup';
                      }}
                      className="font-bold text-forest hover:underline"
                    >
                      Create one
                    </button>
                  </div>
                </form>
              )}

              {/* ========================================================
                  SIGN UP FORM
                 ======================================================== */}
              {authMode === 'signup' && (
                <form onSubmit={handleSignUp} className="space-y-3.5 text-left animate-fadeIn">
                  <div>
                    <label className="block text-xs font-semibold text-espresso-800 mb-1">
                      Full Legal Name
                    </label>
                    <input
                      type="text"
                      required
                      placeholder="e.g. Rajeshwar Singhania"
                      value={signupName}
                      onChange={(e) => setSignupName(e.target.value)}
                      className="w-full px-3.5 py-2.5 rounded-xl border border-sand-300 bg-sand-50/50 hover:bg-white focus:bg-white focus:border-forest focus:ring-4 focus:ring-forest/10 text-sm font-sans text-espresso-950 transition-all outline-none"
                    />
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-espresso-800 mb-1">
                      Corporate Email Address
                    </label>
                    <div className="relative flex items-center">
                      <Mail className="w-4 h-4 absolute left-3.5 text-espresso-400" />
                      <input
                        type="email"
                        required
                        placeholder="director@developer.com"
                        value={signupEmail}
                        onChange={(e) => setSignupEmail(e.target.value)}
                        className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-sand-300 bg-sand-50/50 hover:bg-white focus:bg-white focus:border-forest focus:ring-4 focus:ring-forest/10 text-sm font-sans text-espresso-950 transition-all outline-none"
                      />
                    </div>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs font-semibold text-espresso-800 mb-1">
                        {role === 'developer' ? 'Development Firm' : 'Brokerage Name'}
                      </label>
                      <input
                        type="text"
                        required
                        placeholder={role === 'developer' ? 'Apex Developers' : 'Cityline Realty'}
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
                        placeholder="MAHARERA/..."
                        value={signupRera}
                        onChange={(e) => setSignupRera(e.target.value.toUpperCase())}
                        className="w-full px-3.5 py-2.5 rounded-xl border border-sand-300 bg-sand-50/50 hover:bg-white focus:bg-white focus:border-forest focus:ring-4 focus:ring-forest/10 text-sm font-mono text-espresso-950 transition-all outline-none uppercase"
                      />
                    </div>
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-espresso-800 mb-1">
                      Password
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

                    {signupPassword && (
                      <div className="mt-1.5 space-y-1">
                        <div className="flex justify-between items-center text-[10px]">
                          <span className="text-espresso-500">Strength:</span>
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
                        I agree to Shardeya's enterprise terms and RERA compliance protocol.
                      </span>
                    </label>
                  </div>

                  <button
                    type="submit"
                    disabled={isLoading}
                    className="w-full py-3 px-4 rounded-xl bg-forest hover:bg-forest-light disabled:opacity-50 text-white font-sans font-bold text-xs uppercase tracking-wider shadow-warm-sm transition-all flex items-center justify-center gap-2 mt-2"
                  >
                    {isLoading ? (
                      <RefreshCw className="w-4 h-4 animate-spin" />
                    ) : (
                      <>
                        <span>Create Account</span>
                        <ArrowRight className="w-4 h-4" />
                      </>
                    )}
                  </button>

                  {/* Mode switch link */}
                  <div className="text-center pt-3 text-xs text-espresso-600">
                    <span>Already have an enterprise account? </span>
                    <button
                      type="button"
                      onClick={() => {
                        setAuthMode('login');
                        setErrorMessage('');
                        setSuccessToast('');
                        window.location.hash = 'login';
                      }}
                      className="font-bold text-forest hover:underline"
                    >
                      Sign in
                    </button>
                  </div>
                </form>
              )}

            </div>
          )}

        </div>
      </main>

      {/* Minimal Footer */}
      <footer className="w-full py-4 text-center text-xs text-espresso-500 font-mono">
        © {new Date().getFullYear()} Shardeya Group Pvt. Ltd. All rights reserved.
      </footer>

      {/* Forgot Password Modal */}
      {showForgotModal && (
        <div className="fixed inset-0 z-50 bg-espresso-950/50 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl border border-sand-300 max-w-md w-full p-6 shadow-xl animate-fadeIn text-left">
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
              Enter your corporate email address to receive a secure password reset link.
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
