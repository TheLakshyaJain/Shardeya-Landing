import { NavLink } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/utils';
import type { NavItem } from './nav';

interface BottomNavProps {
  items: NavItem[];
}

export function BottomNav({ items }: BottomNavProps) {
  const { t } = useTranslation();

  return (
    <nav
      // M5 added Tracker/Financials/Deals History, taking a builder with
      // every permission from 5 nav items to 8 -- too many to fit a 360px
      // bar with justify-around and no wrapping. Without its own overflow
      // handling, the flex children blew out past the nav's own box and
      // the *entire page* gained horizontal scroll (confirmed: the excess
      // nav width, ~210px, matched document.documentElement.scrollWidth's
      // overflow exactly). overflow-x-auto + shrink-0 items contains the
      // scroll to this bar alone -- a swipe reveals the rest, same as any
      // mobile tab strip, instead of the whole dashboard scrolling sideways.
      className="flex h-14 shrink-0 items-center gap-1 overflow-x-auto border-t border-border bg-card px-1 md:hidden"
      aria-label={t('nav.mainNav')}
    >
      {items.map(({ to, labelKey, icon: Icon }) => (
        <NavLink
          key={to}
          to={to}
          className={({ isActive }) =>
            cn(
              'flex shrink-0 flex-col items-center gap-0.5 px-3 py-1 text-xs font-medium text-muted-foreground',
              isActive && 'text-primary',
            )
          }
        >
          <Icon className="size-5" />
          {t(labelKey)}
        </NavLink>
      ))}
    </nav>
  );
}
