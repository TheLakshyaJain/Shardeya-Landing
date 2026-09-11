import { create } from 'zustand';
import { queryClient } from '@/app/providers';
import type { AuthTokensResponse, EntitlementsSummary, MeResponse, OrgSummary, UserSummary } from '../types';

// CLAUDE.md rule #15: never localStorage for auth tokens. The access token
// stays exactly as before -- plain in-memory state, no zustand `persist`
// middleware, gone on a hard reload. The refresh token was ALSO in-memory
// here until this fix; it never touches JS at all now -- it lives only in an
// httpOnly Set-Cookie the backend sets on login/signup/refresh, which this
// store has no field for and never could read even if it wanted to. That's
// what makes a hard reload survivable without breaking rule #15: `bootstrap()`
// below fires a single POST /auth/refresh on app start with no body at all
// (the browser attaches the cookie automatically); the browser, not this
// store, is what "remembers" the session across a reload.
interface AuthState {
  accessToken: string | null;
  user: UserSummary | null;
  org: OrgSummary | null;
  permissions: string[];
  entitlements: EntitlementsSummary | null;
  sessionExpired: boolean;
  // True from app start until the one-time boot-time silent refresh
  // (features/auth/bootstrap.ts) has resolved either way. RequireAuth/
  // RedirectIfAuthenticated both wait on this before deciding whether to
  // redirect, so a reload doesn't flash to /login before the cookie-based
  // restore has had a chance to run.
  bootstrapping: boolean;
  setSession: (tokens: AuthTokensResponse) => void;
  setAccessToken: (accessToken: string) => void;
  setMe: (me: MeResponse) => void;
  markSessionExpired: () => void;
  finishBootstrap: () => void;
  clear: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  user: null,
  org: null,
  permissions: [],
  entitlements: null,
  sessionExpired: false,
  bootstrapping: true,

  setSession: (tokens) =>
    set({
      accessToken: tokens.accessToken,
      user: tokens.user,
      org: tokens.org,
      sessionExpired: false,
    }),

  setAccessToken: (accessToken) => set({ accessToken }),

  setMe: (me) => set({ user: me.user, org: me.org, permissions: me.permissions, entitlements: me.entitlements }),

  finishBootstrap: () => set({ bootstrapping: false }),

  // Every TanStack Query cache entry is keyed by query name/params alone,
  // never by user id -- so without wiping the cache here, a second user
  // logging in on the same browser tab (no full page reload, since a hard
  // reload already logs everyone out per this store's own in-memory design)
  // would briefly render the PREVIOUS user's cached notifications, leads,
  // team list, everything, until each individual query happened to refetch.
  // queryClient.clear() closes that window at the one place both logout
  // paths (manual + session-expiry) already funnel through.
  markSessionExpired: () => {
    queryClient.clear();
    set({
      accessToken: null,
      user: null,
      org: null,
      permissions: [],
      entitlements: null,
      sessionExpired: true,
    });
  },

  clear: () => {
    queryClient.clear();
    set({
      accessToken: null,
      user: null,
      org: null,
      permissions: [],
      entitlements: null,
      sessionExpired: false,
    });
  },
}));

export function isAuthenticated(): boolean {
  return useAuthStore.getState().accessToken !== null;
}

// Dev-only escape hatch for e2e tests to seed a session without repeating
// the OTP UI dance in every spec (see frontend/e2e/helpers.ts) -- never
// bundled into a production build since import.meta.env.DEV is
// statically false there and Vite dead-code-eliminates this block.
if (import.meta.env.DEV) {
  (window as unknown as { __authStore: typeof useAuthStore }).__authStore = useAuthStore;
}
