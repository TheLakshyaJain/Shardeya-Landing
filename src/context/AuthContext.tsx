import React, { createContext, useContext, useState, useEffect } from 'react';

export type UserRole = 'developer' | 'broker';

export interface User {
  id: string;
  name: string;
  email: string;
  role: UserRole;
  organization: string;
  designation: string;
  avatarInitials: string;
  reraNumber?: string;
  createdAt: string;
  lastLoginAt: string;
}

export interface StoredUserAccount extends User {
  passwordHash: string; // In production this is salted hash, simulated here
}

export interface SignupData {
  name: string;
  email: string;
  password: string;
  role: UserRole;
  organization: string;
  designation?: string;
  reraNumber?: string;
}

interface LockoutData {
  count: number;
  lockedUntil: number | null;
}

interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  login: (email: string, password: string, role?: UserRole) => Promise<{ success: boolean; error?: string }>;
  signup: (data: SignupData) => Promise<{ success: boolean; error?: string; user?: User }>;
  logout: () => void;
  requestPasswordReset: (email: string) => Promise<{ success: boolean; message: string }>;
  getLockoutStatus: (email: string) => { isLocked: boolean; remainingSeconds: number; attemptsLeft: number };
}

const SEEDED_ACCOUNTS: StoredUserAccount[] = [
  {
    id: 'user_dev_01',
    name: 'Rajeshwar Singhania',
    email: 'director@apexdevelopers.com',
    passwordHash: 'Shardeya@2026',
    role: 'developer',
    organization: 'Apex Greens Developers LLP',
    designation: 'Chief Operations Director',
    avatarInitials: 'RS',
    reraNumber: 'MAHARERA/P51800019283',
    createdAt: '2025-01-15T10:00:00.000Z',
    lastLoginAt: new Date().toISOString(),
  },
  {
    id: 'user_broker_01',
    name: 'Vikram Malhotra',
    email: 'partner@apexrealty.com',
    passwordHash: 'Shardeya@2026',
    role: 'broker',
    organization: 'Diamond Channel Syndicate',
    designation: 'Principal Managing Partner',
    avatarInitials: 'VM',
    reraNumber: 'MAHARERA/A51800001234',
    createdAt: '2025-02-01T11:30:00.000Z',
    lastLoginAt: new Date().toISOString(),
  }
];

const MAX_FAILED_ATTEMPTS = 5;
const LOCKOUT_DURATION_MS = 60 * 1000; // 60 seconds

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  // 1. Stored accounts database in localStorage
  const [usersDb, setUsersDb] = useState<StoredUserAccount[]>(() => {
    try {
      const saved = localStorage.getItem('shardeya_users_v1');
      if (saved) {
        const parsed = JSON.parse(saved);
        if (Array.isArray(parsed) && parsed.length > 0) {
          return parsed;
        }
      }
    } catch {
      // Fallback
    }
    localStorage.setItem('shardeya_users_v1', JSON.stringify(SEEDED_ACCOUNTS));
    return SEEDED_ACCOUNTS;
  });

  // 2. Active authenticated session in localStorage
  const [user, setUser] = useState<User | null>(() => {
    try {
      const savedSession = localStorage.getItem('shardeya_auth_session_v1');
      if (savedSession) {
        return JSON.parse(savedSession);
      }
    } catch {
      // Fallback
    }
    return null;
  });

  // 3. Brute force lockouts
  const [lockouts, setLockouts] = useState<Record<string, LockoutData>>(() => {
    try {
      const saved = localStorage.getItem('shardeya_auth_lockouts_v1');
      if (saved) {
        return JSON.parse(saved);
      }
    } catch {
      // Fallback
    }
    return {};
  });

  // Keep lockouts persisted
  useEffect(() => {
    localStorage.setItem('shardeya_auth_lockouts_v1', JSON.stringify(lockouts));
  }, [lockouts]);

  // Keep users database persisted
  useEffect(() => {
    localStorage.setItem('shardeya_users_v1', JSON.stringify(usersDb));
  }, [usersDb]);

  // Keep session persisted
  useEffect(() => {
    if (user) {
      localStorage.setItem('shardeya_auth_session_v1', JSON.stringify(user));
    } else {
      localStorage.removeItem('shardeya_auth_session_v1');
    }
  }, [user]);

  const getLockoutStatus = (email: string) => {
    const key = email.trim().toLowerCase();
    const data = lockouts[key];
    if (!data) {
      return { isLocked: false, remainingSeconds: 0, attemptsLeft: MAX_FAILED_ATTEMPTS };
    }

    const now = Date.now();
    if (data.lockedUntil && data.lockedUntil > now) {
      const remainingSeconds = Math.ceil((data.lockedUntil - now) / 1000);
      return { isLocked: true, remainingSeconds, attemptsLeft: 0 };
    }

    const attemptsLeft = Math.max(0, MAX_FAILED_ATTEMPTS - data.count);
    return { isLocked: false, remainingSeconds: 0, attemptsLeft };
  };

  const recordFailedAttempt = (email: string) => {
    const key = email.trim().toLowerCase();
    const current = lockouts[key] || { count: 0, lockedUntil: null };
    const newCount = current.count + 1;
    const now = Date.now();

    if (newCount >= MAX_FAILED_ATTEMPTS) {
      setLockouts((prev) => ({
        ...prev,
        [key]: { count: newCount, lockedUntil: now + LOCKOUT_DURATION_MS }
      }));
    } else {
      setLockouts((prev) => ({
        ...prev,
        [key]: { count: newCount, lockedUntil: null }
      }));
    }
  };

  const clearFailedAttempts = (email: string) => {
    const key = email.trim().toLowerCase();
    if (lockouts[key]) {
      setLockouts((prev) => {
        const next = { ...prev };
        delete next[key];
        return next;
      });
    }
  };

  const login = async (
    email: string, 
    password: string, 
    role?: UserRole
  ): Promise<{ success: boolean; error?: string }> => {
    const cleanEmail = email.trim().toLowerCase();
    
    // Check lockout
    const lockout = getLockoutStatus(cleanEmail);
    if (lockout.isLocked) {
      return {
        success: false,
        error: `Workstation locked due to excessive failed attempts. Please wait ${lockout.remainingSeconds}s before retrying.`
      };
    }

    // Simulate network latency (250ms)
    await new Promise((res) => setTimeout(res, 250));

    const match = usersDb.find((u) => u.email.toLowerCase() === cleanEmail);

    if (!match) {
      recordFailedAttempt(cleanEmail);
      const remaining = Math.max(0, MAX_FAILED_ATTEMPTS - ((lockouts[cleanEmail]?.count || 0) + 1));
      return {
        success: false,
        error: remaining > 0
          ? `No account registered with this corporate email. (${remaining} attempts remaining)`
          : `Account locked for 60s due to repeated invalid credentials.`
      };
    }

    if (match.passwordHash !== password) {
      recordFailedAttempt(cleanEmail);
      const remaining = Math.max(0, MAX_FAILED_ATTEMPTS - ((lockouts[cleanEmail]?.count || 0) + 1));
      return {
        success: false,
        error: remaining > 0 
          ? `Incorrect password. Please verify and retry. (${remaining} attempts remaining)`
          : `Account locked for 60s due to repeated invalid credentials.`
      };
    }

    if (role && match.role !== role) {
      const correctRoleLabel = match.role === 'developer' ? 'Developer Console' : 'Channel Partner Network';
      return {
        success: false,
        error: `Account role mismatch: This email is registered under the ${correctRoleLabel}. Please switch tabs.`
      };
    }

    // Successful login: update session & clear lockouts
    clearFailedAttempts(cleanEmail);
    const updatedUser: User = {
      id: match.id,
      name: match.name,
      email: match.email,
      role: match.role,
      organization: match.organization,
      designation: match.designation,
      avatarInitials: match.avatarInitials,
      reraNumber: match.reraNumber,
      createdAt: match.createdAt,
      lastLoginAt: new Date().toISOString(),
    };

    // Update in DB
    setUsersDb((prev) => prev.map((u) => u.id === match.id ? { ...u, lastLoginAt: updatedUser.lastLoginAt } : u));
    setUser(updatedUser);

    return { success: true };
  };

  const signup = async (data: SignupData): Promise<{ success: boolean; error?: string; user?: User }> => {
    const cleanEmail = data.email.trim().toLowerCase();

    // Validate email format
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(cleanEmail)) {
      return { success: false, error: 'Please enter a valid corporate email address.' };
    }

    // Password strength check: min 8 chars, at least 1 uppercase, 1 digit, 1 special char
    if (data.password.length < 8) {
      return { success: false, error: 'Password must be at least 8 characters long.' };
    }
    if (!/[A-Z]/.test(data.password)) {
      return { success: false, error: 'Password must include at least one uppercase letter (A-Z).' };
    }
    if (!/[0-9]/.test(data.password)) {
      return { success: false, error: 'Password must include at least one number (0-9).' };
    }
    if (!/[!@#$%^&*(),.?":{}|<>]/.test(data.password)) {
      return { success: false, error: 'Password must include at least one special character (!@#$%^&*).' };
    }

    // Simulate network latency (300ms)
    await new Promise((res) => setTimeout(res, 300));

    // Check duplicate
    const exists = usersDb.some((u) => u.email.toLowerCase() === cleanEmail);
    if (exists) {
      return {
        success: false,
        error: 'An account with this corporate email address already exists. Please sign in.'
      };
    }

    // Generate Initials
    const nameParts = data.name.trim().split(/\s+/);
    const avatarInitials = nameParts.length >= 2 
      ? `${nameParts[0][0]}${nameParts[1][0]}`.toUpperCase()
      : (data.name.substring(0, 2) || 'SH').toUpperCase();

    const newAccount: StoredUserAccount = {
      id: `usr_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`,
      name: data.name.trim(),
      email: cleanEmail,
      passwordHash: data.password,
      role: data.role,
      organization: data.organization.trim(),
      designation: data.designation?.trim() || (data.role === 'developer' ? 'Real Estate Principal' : 'Registered Channel Partner'),
      avatarInitials,
      reraNumber: data.reraNumber?.trim() || undefined,
      createdAt: new Date().toISOString(),
      lastLoginAt: new Date().toISOString(),
    };

    setUsersDb((prev) => [...prev, newAccount]);

    const activeUser: User = {
      id: newAccount.id,
      name: newAccount.name,
      email: newAccount.email,
      role: newAccount.role,
      organization: newAccount.organization,
      designation: newAccount.designation,
      avatarInitials: newAccount.avatarInitials,
      reraNumber: newAccount.reraNumber,
      createdAt: newAccount.createdAt,
      lastLoginAt: newAccount.lastLoginAt,
    };

    setUser(activeUser);
    clearFailedAttempts(cleanEmail);

    return { success: true, user: activeUser };
  };

  const logout = () => {
    setUser(null);
  };

  const requestPasswordReset = async (email: string): Promise<{ success: boolean; message: string }> => {
    const cleanEmail = email.trim().toLowerCase();
    await new Promise((res) => setTimeout(res, 350));

    const exists = usersDb.some((u) => u.email.toLowerCase() === cleanEmail);
    if (!exists) {
      return {
        success: false,
        message: 'No registered workspace found with this corporate email address.'
      };
    }

    return {
      success: true,
      message: `A secure 256-bit password reset authorization link has been dispatched to ${cleanEmail}. Check your inbox within 10 minutes.`
    };
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        login,
        signup,
        logout,
        requestPasswordReset,
        getLockoutStatus,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
