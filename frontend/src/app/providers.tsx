import type { ReactNode } from 'react';
import { ThemeProvider } from 'next-themes';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Toaster } from '@/components/ui/sonner';

// Exported (not module-private) so authStore.clear()/markSessionExpired()
// can wipe every cached query on logout/session-expiry -- see authStore's
// own comment on why this matters: without it, a second user logging in on
// the same browser tab would briefly see the previous user's cached data
// (notifications, leads, everything) until each query happened to refetch.
export const queryClient = new QueryClient();

export function AppProviders({ children }: { children: ReactNode }) {
  return (
    <ThemeProvider attribute="class" defaultTheme="system" enableSystem disableTransitionOnChange>
      <QueryClientProvider client={queryClient}>
        {children}
        <Toaster />
      </QueryClientProvider>
    </ThemeProvider>
  );
}
