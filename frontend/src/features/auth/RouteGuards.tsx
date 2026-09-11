import type { ReactNode } from 'react';
import { Loader2 } from 'lucide-react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuthStore } from './store/authStore';
import { dashboardPathFor } from './redirect';
import type { ShellProfile } from '@/components/layout/nav';

interface RequireAuthProps {
  /** When set, also enforces the logged-in org's type matches this shell (builders can't wander into /broker/*). */
  profile?: ShellProfile;
  children: ReactNode;
}

// Shown for the brief window (a single HTTP round trip) while the boot-time
// silent refresh (features/auth/bootstrap.ts) is still in flight. Without
// this, a hard reload of an authenticated page would flash to /login for
// that instant — accessToken starts null on every fresh page load (rule #15:
// nothing persists it), and the refresh hasn't had a chance to restore it
// yet, even though it's very likely about to.
function BootstrappingSpinner() {
  return (
    <div className="flex h-screen w-screen items-center justify-center">
      <Loader2 className="size-8 animate-spin text-muted-foreground" />
    </div>
  );
}

// accessToken and org are always set together (authStore.setSession) and
// cleared together (clear/markSessionExpired), so accessToken !== null is
// sufficient to guarantee org is populated too.
export function RequireAuth({ profile, children }: RequireAuthProps) {
  const bootstrapping = useAuthStore((s) => s.bootstrapping);
  const accessToken = useAuthStore((s) => s.accessToken);
  const org = useAuthStore((s) => s.org);
  const location = useLocation();

  if (bootstrapping) {
    return <BootstrappingSpinner />;
  }

  if (!accessToken || !org) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  if (profile && org.type.toLowerCase() !== profile) {
    return <Navigate to={dashboardPathFor(org.type)} replace />;
  }

  return <>{children}</>;
}

// Keeps an already-authenticated user from landing back on /login or
// /signup — sends them straight to their dashboard instead.
export function RedirectIfAuthenticated({ children }: { children: ReactNode }) {
  const bootstrapping = useAuthStore((s) => s.bootstrapping);
  const accessToken = useAuthStore((s) => s.accessToken);
  const org = useAuthStore((s) => s.org);

  // Deliberately NOT the spinner here — /login and /signup are always the
  // first thing a truly logged-out user sees, and blocking their render
  // behind the bootstrap check would delay the login form for everyone just
  // to correctly redirect the much rarer case of a reload landing directly
  // on /login while still authenticated. A user in that rare case briefly
  // sees the login form, then gets redirected the instant bootstrap resolves
  // — an acceptable trade favouring the common path.
  if (!bootstrapping && accessToken && org) {
    return <Navigate to={dashboardPathFor(org.type)} replace />;
  }

  return <>{children}</>;
}
