import { NavLink } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/utils';
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import type { NavItem } from './nav';

interface MobileDrawerProps {
  items: NavItem[];
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function MobileDrawer({ items, open, onOpenChange }: MobileDrawerProps) {
  const { t } = useTranslation();

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="left" className="w-64 p-0">
        <SheetHeader>
          <SheetTitle>{t('app.name')}</SheetTitle>
        </SheetHeader>
        <nav className="flex flex-col gap-1 p-3" aria-label={t('nav.mainNav')}>
          {items.map(({ to, labelKey, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              onClick={() => onOpenChange(false)}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium text-muted-foreground hover:bg-accent hover:text-accent-foreground',
                  isActive && 'bg-accent text-accent-foreground',
                )
              }
            >
              <Icon className="size-4" />
              {t(labelKey)}
            </NavLink>
          ))}
        </nav>
      </SheetContent>
    </Sheet>
  );
}
