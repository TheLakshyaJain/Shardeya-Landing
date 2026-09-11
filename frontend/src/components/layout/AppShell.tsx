import { useState } from 'react';
import { Outlet } from 'react-router-dom';
import { TopBar } from './TopBar';
import { Sidebar } from './Sidebar';
import { MobileDrawer } from './MobileDrawer';
import { BottomNav } from './BottomNav';
import { navItemsFor, type ShellProfile } from './nav';
import { useAuthStore } from '@/features/auth/store/authStore';

interface AppShellProps {
  profile: ShellProfile;
}

export function AppShell({ profile }: AppShellProps) {
  const [drawerOpen, setDrawerOpen] = useState(false);
  const permissions = useAuthStore((s) => s.permissions);
  // UI hiding is cosmetic (CLAUDE.md rule #5) -- every one of these routes
  // still enforces the same permission server-side; this just avoids
  // showing a nav link to a page that would immediately 403/reject writes.
  const items = navItemsFor(profile).filter((item) => !item.anyOf || item.anyOf.some((p) => permissions.includes(p)));

  return (
    <div className="flex h-dvh flex-col">
      <TopBar onMenuClick={() => setDrawerOpen(true)} />
      <div className="flex min-h-0 flex-1">
        <Sidebar items={items} />
        <main className="min-w-0 flex-1 overflow-y-auto p-4">
          <Outlet />
        </main>
      </div>
      <BottomNav items={items} />
      <MobileDrawer items={items} open={drawerOpen} onOpenChange={setDrawerOpen} />
    </div>
  );
}
