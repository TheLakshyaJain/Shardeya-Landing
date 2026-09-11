import React, { useState, useEffect } from 'react';
import { 
  ArrowLeft, ShieldCheck, Phone, Mail, Lock, 
  Eye, EyeOff, CheckCircle2, ArrowRight, Building2, 
  Users, KeyRound, RefreshCw, Check
} from 'lucide-react';
import { useLanguage } from '../../context/LanguageContext';

interface LoginPageProps {
  onBack: () => void;
}

type UserRole = 'developer' | 'broker';
type AuthMethod = 'phone' | 'email';

export const LoginPage: React.FC<LoginPageProps> = ({ onBack }) => {
  const { language } = useLanguage();
  const isHi = language === 'hi';

  const [role, setRole] = useState<UserRole>('developer');
  const [authMethod, setAuthMethod] = useState<AuthMethod>('phone');

  // Phone state
  const [phone, setPhone] = useState('');
  const [otpSent, setOtpSent] = useState(false);
  const [otp, setOtp] = useState(['', '', '', '', '', '']);
  const [timer, setTimer] = useState(30);
  const [isCounting, setIsCounting] = useState(false);

  // Email state
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(true);

  // Feedback state
  const [authenticatedUser, setAuthenticatedUser] = useState<{
    name: string;
    role: string;
    organization: string;
    avatarInitials: string;
  } | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  // Countdown timer for OTP
  useEffect(() => {
    let interval: ReturnType<typeof setInterval>;
    if (isCounting && timer > 0) {
      interval = setInterval(() => {
        setTimer((prev) => prev - 1);
      }, 1000);
    } else if (timer === 0) {
      setIsCounting(false);
    }
    return () => clearInterval(interval);
  }, [isCounting, timer]);

  const handleSendOtp = (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage('');
    if (phone.replace(/\D/g, '').length < 10) {
      setErrorMessage(isHi ? 'कृपया मान्य 10-अंकीय मोबाइल नंबर दर्ज करें' : 'Please enter a valid 10-digit mobile number');
      return;
    }
    setIsLoading(true);
    setTimeout(() => {
      setIsLoading(false);
      setOtpSent(true);
      setTimer(30);
      setIsCounting(true);
      // Autofill simulated OTP
      setOtp(['4', '8', '2', '9', '1', '0']);
    }, 700);
  };

  const handleOtpChange = (index: number, value: string) => {
    if (value.length > 1) value = value.slice(-1);
    const newOtp = [...otp];
    newOtp[index] = value;
    setOtp(newOtp);

    // Auto-focus next input
    if (value && index < 5) {
      const nextInput = document.getElementById(`otp-${index + 1}`);
      if (nextInput) nextInput.focus();
    }
  };

  const handleVerifyOtp = (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage('');
    const enteredOtp = otp.join('');
    if (enteredOtp.length < 6) {
      setErrorMessage(isHi ? 'कृपया 6-अंकीय सत्यापन कोड दर्ज करें' : 'Please enter the complete 6-digit OTP');
      return;
    }

    setIsLoading(true);
    setTimeout(() => {
      setIsLoading(false);
      setAuthenticatedUser({
        name: role === 'developer' ? 'Rajeshwar Singhania' : 'Vikram Malhotra',
        role: role === 'developer' ? 'Chief Operations Director' : 'Principal Channel Partner (Diamond Syndicate)',
        organization: role === 'developer' ? 'Apex Greens Developers LLP' : 'Apex Realty Network',
        avatarInitials: role === 'developer' ? 'RS' : 'VM',
      });
    }, 800);
  };

  const handleEmailLogin = (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage('');
    if (!email || !password) {
      setErrorMessage(isHi ? 'कृपया ईमेल और पासवर्ड दोनों दर्ज करें' : 'Please enter both corporate email and password');
      return;
    }

    setIsLoading(true);
    setTimeout(() => {
      setIsLoading(false);
      setAuthenticatedUser({
        name: role === 'developer' ? 'Rajeshwar Singhania' : 'Vikram Malhotra',
        role: role === 'developer' ? 'Chief Operations Director' : 'Principal Channel Partner',
        organization: role === 'developer' ? 'Apex Greens Developers LLP' : 'Apex Realty Network',
        avatarInitials: role === 'developer' ? 'RS' : 'VM',
      });
    }, 800);
  };

  return (
    <div className="min-h-screen bg-sand-100 text-espresso-950 font-sans flex flex-col justify-between selection:bg-forest/15 selection:text-forest">
      
      {/* Top Bar */}
      <header className="w-full bg-white/90 backdrop-blur-xl border-b border-sand-300 py-4 px-4 sm:px-8 shadow-warm-sm">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          
          {/* Brand */}
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-lg bg-forest flex items-center justify-center text-white font-serif font-bold text-lg shadow-warm-sm">
              S
            </div>
            <div>
              <span className="font-serif font-bold text-xl tracking-tight text-espresso-950 block leading-none">
                SHARDEYA GROUP
              </span>
              <span className="text-[10px] font-mono text-espresso-600 uppercase tracking-wider block mt-1">
                Enterprise Workspace Portal
              </span>
            </div>
          </div>

          {/* Return Button */}
          <button
            onClick={onBack}
            className="inline-flex items-center gap-2 px-4 py-2 rounded-lg border border-sand-300 bg-sand-50 hover:bg-sand-200 text-espresso-800 text-xs font-bold transition-all shadow-warm-sm group"
          >
            <ArrowLeft className="w-4 h-4 text-forest group-hover:-translate-x-1 transition-transform" />
            <span>{isHi ? 'मुख्य वेबसाइट पर वापस जाएं' : 'Back to Website'}</span>
          </button>
        </div>
      </header>

      {/* Main Authentication Card */}
      <main className="flex-1 flex items-center justify-center px-4 py-12">
        <div className="w-full max-w-md">

          {authenticatedUser ? (
            /* Success State / Authenticated Simulation View */
            <div className="bg-white rounded-2xl border border-sand-300 p-8 shadow-warm-lg text-center animate-fadeIn">
              <div className="w-16 h-16 rounded-full bg-emerald-100 border border-emerald-300 text-emerald-800 flex items-center justify-center mx-auto mb-4 font-serif font-bold text-xl shadow-warm-sm">
                {authenticatedUser.avatarInitials}
              </div>

              <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-emerald-50 text-emerald-800 border border-emerald-200 text-xs font-mono font-bold mb-3">
                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />
                <span>AUTHENTICATION VERIFIED</span>
              </div>

              <h2 className="font-serif font-bold text-2xl text-espresso-950">
                {authenticatedUser.name}
              </h2>
              <p className="text-xs text-espresso-700 font-sans mt-1">
                {authenticatedUser.role}
              </p>
              <div className="text-xs font-mono text-forest font-semibold mt-0.5">
                {authenticatedUser.organization}
              </div>

              <div className="mt-6 p-4 rounded-xl bg-sand-50 border border-sand-200 text-left text-xs space-y-2">
                <div className="flex justify-between text-espresso-600">
                  <span>Session Security:</span>
                  <span className="font-mono font-bold text-emerald-700">256-Bit TLS 1.3</span>
                </div>
                <div className="flex justify-between text-espresso-600">
                  <span>Portal Permissions:</span>
                  <span className="font-bold text-espresso-900">
                    {role === 'developer' ? 'Full Developer Console Access' : 'Channel Partner Lead Vault'}
                  </span>
                </div>
                <div className="flex justify-between text-espresso-600">
                  <span>Authorized Token:</span>
                  <span className="font-mono text-espresso-500">SHARD-AUTH-99218-X</span>
                </div>
              </div>

              <div className="mt-6 space-y-2.5">
                <button
                  onClick={onBack}
                  className="w-full py-3 px-4 rounded-xl bg-forest hover:bg-forest-light text-white font-sans font-bold text-xs uppercase tracking-wider shadow-warm-sm transition-all flex items-center justify-center gap-2"
                >
                  <span>Return to Landing Page</span>
                  <ArrowRight className="w-4 h-4" />
                </button>

                <button
                  onClick={() => {
                    setAuthenticatedUser(null);
                    setOtpSent(false);
                    setOtp(['', '', '', '', '', '']);
                    setPassword('');
                  }}
                  className="w-full py-2.5 px-4 rounded-xl border border-sand-300 text-espresso-700 hover:bg-sand-100 text-xs font-sans font-semibold transition-all"
                >
                  Sign Out / Switch Portal
                </button>
              </div>
            </div>
          ) : (
            /* Login Form Container */
            <div className="bg-white rounded-2xl border border-sand-300 shadow-warm-lg overflow-hidden">
              
              {/* Role Selection Tabs */}
              <div className="grid grid-cols-2 border-b border-sand-300 bg-sand-50/80">
                <button
                  type="button"
                  onClick={() => {
                    setRole('developer');
                    setOtpSent(false);
                    setErrorMessage('');
                  }}
                  className={`py-3.5 px-4 text-xs font-sans font-bold flex items-center justify-center gap-2 transition-all border-b-2 ${
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
                    setOtpSent(false);
                    setErrorMessage('');
                  }}
                  className={`py-3.5 px-4 text-xs font-sans font-bold flex items-center justify-center gap-2 transition-all border-b-2 ${
                    role === 'broker'
                      ? 'border-forest bg-white text-forest shadow-warm-sm'
                      : 'border-transparent text-espresso-600 hover:text-espresso-900'
                  }`}
                >
                  <Users className="w-4 h-4" />
                  <span>{isHi ? 'चैनल पार्टनर पोर्टल' : 'Channel Partner'}</span>
                </button>
              </div>

              <div className="p-6 sm:p-8">
                
                {/* Title */}
                <div className="mb-6 text-left">
                  <h1 className="font-serif font-bold text-2xl text-espresso-950">
                    {role === 'developer'
                      ? (isHi ? 'डेवलपर साइन-इन' : 'Sign in to Developer Console')
                      : (isHi ? 'चैनल पार्टनर साइन-इन' : 'Sign in to Partner Portal')}
                  </h1>
                  <p className="text-xs text-espresso-600 mt-1">
                    {role === 'developer'
                      ? 'Access plot inventory, buyer allotments, and milestone demand letters.'
                      : 'Track lead protection, client site visits, and commission voucher payouts.'}
                  </p>
                </div>

                {/* Auth Method Selector */}
                <div className="flex items-center gap-2 p-1 bg-slate-100 rounded-xl border border-slate-200 mb-6 font-sans text-xs">
                  <button
                    type="button"
                    onClick={() => {
                      setAuthMethod('phone');
                      setErrorMessage('');
                    }}
                    className={`flex-1 py-2 rounded-lg font-semibold transition-all flex items-center justify-center gap-1.5 ${
                      authMethod === 'phone'
                        ? 'bg-white text-espresso-950 shadow-sm font-bold'
                        : 'text-espresso-600 hover:text-espresso-950'
                    }`}
                  >
                    <Phone className="w-3.5 h-3.5 text-forest" />
                    <span>Mobile OTP</span>
                  </button>

                  <button
                    type="button"
                    onClick={() => {
                      setAuthMethod('email');
                      setErrorMessage('');
                    }}
                    className={`flex-1 py-2 rounded-lg font-semibold transition-all flex items-center justify-center gap-1.5 ${
                      authMethod === 'email'
                        ? 'bg-white text-espresso-950 shadow-sm font-bold'
                        : 'text-espresso-600 hover:text-espresso-950'
                    }`}
                  >
                    <Mail className="w-3.5 h-3.5 text-forest" />
                    <span>Corporate Email</span>
                  </button>
                </div>

                {/* Error Banner */}
                {errorMessage && (
                  <div className="mb-5 p-3 rounded-lg bg-rose-50 border border-rose-200 text-rose-700 text-xs font-sans text-left">
                    {errorMessage}
                  </div>
                )}

                {/* AUTH METHOD 1: Mobile OTP */}
                {authMethod === 'phone' && (
                  <div>
                    {!otpSent ? (
                      <form onSubmit={handleSendOtp} className="space-y-4 text-left">
                        <div>
                          <label className="block text-xs font-bold text-espresso-800 uppercase tracking-wider mb-1.5">
                            Mobile Number / मोबाइल नंबर
                          </label>
                          <div className="relative flex items-center">
                            <span className="absolute left-3.5 text-xs font-mono font-bold text-espresso-600">
                              +91
                            </span>
                            <input
                              type="tel"
                              maxLength={10}
                              placeholder="98765 43210"
                              value={phone}
                              onChange={(e) => setPhone(e.target.value.replace(/\D/g, ''))}
                              className="w-full pl-12 pr-4 py-2.5 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white focus:border-forest text-sm font-mono text-espresso-950 focus:outline-none transition-all"
                            />
                          </div>
                          <p className="text-[11px] text-espresso-500 mt-1">
                            We will send a 6-digit verification code via WhatsApp or SMS.
                          </p>
                        </div>

                        <button
                          type="submit"
                          disabled={isLoading}
                          className="w-full py-3 px-4 rounded-xl bg-forest hover:bg-forest-light text-white font-sans font-bold text-xs uppercase tracking-wider shadow-warm-sm transition-all flex items-center justify-center gap-2 mt-2"
                        >
                          {isLoading ? (
                            <RefreshCw className="w-4 h-4 animate-spin" />
                          ) : (
                            <>
                              <span>Send Verification OTP</span>
                              <ArrowRight className="w-4 h-4" />
                            </>
                          )}
                        </button>
                      </form>
                    ) : (
                      /* OTP Verification Screen */
                      <form onSubmit={handleVerifyOtp} className="space-y-4 text-left animate-fadeIn">
                        <div>
                          <div className="flex items-center justify-between mb-1.5">
                            <label className="block text-xs font-bold text-espresso-800 uppercase tracking-wider">
                              Enter 6-Digit OTP Code
                            </label>
                            <button
                              type="button"
                              onClick={() => setOtpSent(false)}
                              className="text-[11px] font-mono text-forest hover:underline"
                            >
                              Edit +91 {phone}
                            </button>
                          </div>

                          {/* 6-box OTP input */}
                          <div className="flex gap-2 justify-between my-3">
                            {otp.map((digit, idx) => (
                              <input
                                key={idx}
                                id={`otp-${idx}`}
                                type="text"
                                maxLength={1}
                                value={digit}
                                onChange={(e) => handleOtpChange(idx, e.target.value)}
                                className="w-11 h-12 text-center text-lg font-mono font-bold rounded-lg border border-sand-300 bg-sand-50 focus:bg-white focus:border-forest focus:ring-2 focus:ring-forest/20 text-espresso-950 focus:outline-none transition-all"
                              />
                            ))}
                          </div>

                          <div className="flex items-center justify-between text-xs text-espresso-600 mt-2">
                            <span>
                              {isCounting ? (
                                <span className="font-mono text-espresso-500">Resend code in {timer}s</span>
                              ) : (
                                <button
                                  type="button"
                                  onClick={() => {
                                    setTimer(30);
                                    setIsCounting(true);
                                    setOtp(['4', '8', '2', '9', '1', '0']);
                                  }}
                                  className="text-forest font-bold hover:underline"
                                >
                                  Resend Code via SMS
                                </button>
                              )}
                            </span>
                            <span className="text-[11px] text-emerald-700 font-mono font-semibold">
                              Simulated Code Auto-Filled
                            </span>
                          </div>
                        </div>

                        <button
                          type="submit"
                          disabled={isLoading}
                          className="w-full py-3 px-4 rounded-xl bg-forest hover:bg-forest-light text-white font-sans font-bold text-xs uppercase tracking-wider shadow-warm-sm transition-all flex items-center justify-center gap-2"
                        >
                          {isLoading ? (
                            <RefreshCw className="w-4 h-4 animate-spin" />
                          ) : (
                            <>
                              <span>Verify & Enter Console</span>
                              <CheckCircle2 className="w-4 h-4" />
                            </>
                          )}
                        </button>
                      </form>
                    )}
                  </div>
                )}

                {/* AUTH METHOD 2: Corporate Email & Password */}
                {authMethod === 'email' && (
                  <form onSubmit={handleEmailLogin} className="space-y-4 text-left">
                    <div>
                      <label className="block text-xs font-bold text-espresso-800 uppercase tracking-wider mb-1.5">
                        Corporate Email / कार्य ईमेल
                      </label>
                      <div className="relative flex items-center">
                        <Mail className="w-4 h-4 absolute left-3.5 text-espresso-500" />
                        <input
                          type="email"
                          placeholder="director@developer.com"
                          value={email}
                          onChange={(e) => setEmail(e.target.value)}
                          className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white focus:border-forest text-sm font-sans text-espresso-950 focus:outline-none transition-all"
                        />
                      </div>
                    </div>

                    <div>
                      <div className="flex items-center justify-between mb-1.5">
                        <label className="block text-xs font-bold text-espresso-800 uppercase tracking-wider">
                          Password / पासवर्ड
                        </label>
                        <a href="#" onClick={(e) => { e.preventDefault(); alert('In production, a password reset link is securely dispatched to your verified enterprise email address.'); }} className="text-[11px] text-forest hover:underline">
                          Forgot password?
                        </a>
                      </div>
                      <div className="relative flex items-center">
                        <Lock className="w-4 h-4 absolute left-3.5 text-espresso-500" />
                        <input
                          type={showPassword ? 'text' : 'password'}
                          placeholder="••••••••••••"
                          value={password}
                          onChange={(e) => setPassword(e.target.value)}
                          className="w-full pl-10 pr-10 py-2.5 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white focus:border-forest text-sm font-sans text-espresso-950 focus:outline-none transition-all"
                        />
                        <button
                          type="button"
                          onClick={() => setShowPassword(!showPassword)}
                          className="absolute right-3 text-espresso-500 hover:text-espresso-800 p-1"
                          title={showPassword ? 'Hide password' : 'Show password'}
                        >
                          {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
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
                      disabled={isLoading}
                      className="w-full py-3 px-4 rounded-xl bg-forest hover:bg-forest-light text-white font-sans font-bold text-xs uppercase tracking-wider shadow-warm-sm transition-all flex items-center justify-center gap-2 mt-2"
                    >
                      {isLoading ? (
                        <RefreshCw className="w-4 h-4 animate-spin" />
                      ) : (
                        <>
                          <span>Sign In to Workspace</span>
                          <ArrowRight className="w-4 h-4" />
                        </>
                      )}
                    </button>
                  </form>
                )}

              </div>

              {/* Card Footer */}
              <div className="bg-sand-50 px-6 py-3 border-t border-sand-200 flex items-center justify-between text-[11px] text-espresso-600 font-sans">
                <span className="flex items-center gap-1 text-espresso-500">
                  <ShieldCheck className="w-3.5 h-3.5 text-forest" />
                  <span>256-Bit TLS 1.3 Session</span>
                </span>
                <span>Authorized Personnel Only</span>
              </div>
            </div>
          )}

        </div>
      </main>

      {/* Footer */}
      <footer className="w-full py-6 px-4 text-center text-xs text-espresso-500 font-mono border-t border-sand-200 bg-white/50">
        © {new Date().getFullYear()} Shardeya Group Pvt. Ltd. All rights reserved.
      </footer>

    </div>
  );
};
