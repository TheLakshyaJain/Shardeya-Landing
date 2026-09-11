import { Menu } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import { NotificationBell } from '@/features/notifications/components/NotificationBell';
import { LanguageToggle } from './LanguageToggle';
import { ThemeToggle } from './ThemeToggle';
import { ProfileMenu } from './ProfileMenu';

interface TopBarProps {
  onMenuClick: () => void;
}

export function TopBar({ onMenuClick }: TopBarProps) {
  const { t } = useTranslation();

  return (
    <header className="flex h-14 items-center gap-3 border-b border-border bg-card px-3 md:px-4">
      <Button
        variant="ghost"
        size="icon"
        className="md:hidden"
        aria-label={t('nav.openMenu')}
        onClick={onMenuClick}
      >
        <Menu className="size-5" />
      </Button>
      <span className="text-base font-semibold text-foreground">{t('app.name')}</span>
      <div className="ml-auto flex items-center gap-2">
        <ThemeToggle />
        <LanguageToggle />
        <NotificationBell />
        <ProfileMenu />
      </div>
    </header>
  );
}
