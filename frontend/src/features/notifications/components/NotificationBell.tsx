import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Bell } from 'lucide-react';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { getUnreadCount, listNotifications, markAllNotificationsRead, markNotificationRead } from '../api/notificationApi';
import type { NotificationResponse } from '../types';

// M3 kickoff sketched SSE for real-time delivery; this uses polling instead
// (15s interval, same TanStack Query refetchInterval mechanism already used
// elsewhere in the app) -- a deliberate simplification, not an oversight.
// SSE needs a long-lived connection + reconnect/backoff logic the bell's
// actual exit criterion ("shows a Plot Sold notification") doesn't require;
// polling meets it with far less new surface area. Revisit if true
// real-time delivery becomes an actual product requirement later.
const POLL_INTERVAL_MS = 15_000;

function notificationKey(titleKey: string): string {
  return titleKey.startsWith('notification.') ? titleKey.slice('notification.'.length) : titleKey;
}

export function NotificationBell() {
  const { t } = useTranslation(['notification', 'common']);
  const queryClient = useQueryClient();

  const unreadQuery = useQuery({ queryKey: ['notifications-unread-count'], queryFn: getUnreadCount, refetchInterval: POLL_INTERVAL_MS });
  const listQuery = useQuery({ queryKey: ['notifications'], queryFn: () => listNotifications(20), refetchInterval: POLL_INTERVAL_MS });

  const invalidateBoth = () => {
    queryClient.invalidateQueries({ queryKey: ['notifications-unread-count'] });
    queryClient.invalidateQueries({ queryKey: ['notifications'] });
  };

  const markReadMutation = useMutation({ mutationFn: markNotificationRead, onSuccess: invalidateBoth });
  const markAllReadMutation = useMutation({ mutationFn: markAllNotificationsRead, onSuccess: invalidateBoth });

  const unreadCount = unreadQuery.data?.count ?? 0;
  const notifications = listQuery.data ?? [];

  function renderNotification(n: NotificationResponse) {
    return t(notificationKey(n.titleKey), { ns: 'notification', ...n.params, defaultValue: notificationKey(n.titleKey) });
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" className="relative" aria-label={t('bell.label', { ns: 'notification' })}>
          <Bell className="size-5" />
          {unreadCount > 0 && (
            <Badge className="absolute -right-1 -top-1 flex h-4 min-w-4 items-center justify-center rounded-full px-1 text-[10px]" variant="destructive">
              {unreadCount > 99 ? '99+' : unreadCount}
            </Badge>
          )}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-80">
        <div className="flex items-center justify-between px-2 py-1.5">
          <DropdownMenuLabel className="p-0">{t('bell.title', { ns: 'notification' })}</DropdownMenuLabel>
          {unreadCount > 0 && (
            <Button variant="ghost" size="sm" className="h-auto p-0 text-xs" onClick={() => markAllReadMutation.mutate()}>
              {t('bell.markAllRead', { ns: 'notification' })}
            </Button>
          )}
        </div>
        <DropdownMenuSeparator />
        {notifications.length === 0 ? (
          <p className="px-2 py-4 text-center text-sm text-muted-foreground">{t('bell.empty', { ns: 'notification' })}</p>
        ) : (
          <div className="max-h-96 overflow-y-auto">
            {notifications.map((n) => (
              <DropdownMenuItem
                key={n.id}
                className={`flex flex-col items-start gap-0.5 whitespace-normal ${n.read ? 'opacity-60' : ''}`}
                onSelect={() => !n.read && markReadMutation.mutate(n.id)}
              >
                <span className="text-sm">{renderNotification(n)}</span>
                <span className="text-xs text-muted-foreground">{new Date(n.createdAt).toLocaleString()}</span>
              </DropdownMenuItem>
            ))}
          </div>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
