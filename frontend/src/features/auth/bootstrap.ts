import { refreshSession } from '@/lib/api/client';
import { useAuthStore } from './store/authStore';

// Fires once on app start (App.tsx). There is no client-visible signal for
// "does a refresh_token cookie exist" — the cookie is httpOnly by design
// (CLAUDE.md rule #15) — so this always attempts POST /auth/refresh and
// reads the result: success rebuilds the session (accessToken + user + org,
// via refreshSession()'s own setSession call) with no redirect to /login;
// failure (no cookie, expired, revoked) just leaves the store cleared, which
// RequireAuth already treats as "go to /login". Either way `finishBootstrap()`
// unblocks routing — see RouteGuards.tsx's own bootstrapping check for why
// that gate exists (without it, a reload would flash to /login for the
// instant this call is in flight, even when it's about to succeed).
export async function bootstrapSession(): Promise<void> {
  await refreshSession();
  useAuthStore.getState().finishBootstrap();
}
