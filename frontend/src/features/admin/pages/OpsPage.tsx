import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { Bug, Mail, MessageCircle, MessageSquare } from 'lucide-react';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/data/EmptyState';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { FormError } from '@/components/forms/FormError';
import { cn } from '@/lib/utils';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { listErrors, listMessageDeliveries } from '../api/adminOpsApi';
import type { MessageChannel, MessageDeliveryLogRow, MessageStatus } from '../types';

// M-14 stand-in -- see AdminOpsService's own backend javadoc and CLAUDE.md
// "Post-M7 -- Minimal Ops Visibility". This is deliberately NOT the full
// Platform Admin Console (no org suspension, feature flags, rate editor,
// break-glass, separate auth realm): just enough to answer "would we
// notice before the customer does" for the two things that just went
// live -- real WhatsApp/SMS/email delivery, and genuine server errors.
//
// Card rows, not a wide <Table> -- an initial version used a 6-column
// table that pushed Status/Error clean off a 360px viewport (contained by
// its own overflow-x-auto, so the page itself never overflowed, but the
// one piece of information this page exists for -- "did it fail, and why"
// -- was invisible without an extra horizontal scroll). Caught the same
// way the calendar agenda list's own redesign this session was verified:
// by actually looking at a real 360px screenshot, not just checking for
// page-level overflow.
const REFRESH_INTERVAL_MS = 30_000;

const CHANNEL_ICONS: Record<MessageChannel, typeof Mail> = {
  WHATSAPP: MessageCircle,
  SMS: MessageSquare,
  EMAIL: Mail,
};

const STATUS_STYLES: Record<string, { chip: string; icon: string; badge: string }> = {
  SENT: { chip: 'bg-green-50 dark:bg-green-950', icon: 'text-green-600 dark:text-green-400', badge: 'bg-green-50 text-green-700 dark:bg-green-950 dark:text-green-400' },
  DELIVERED: { chip: 'bg-green-50 dark:bg-green-950', icon: 'text-green-600 dark:text-green-400', badge: 'bg-green-50 text-green-700 dark:bg-green-950 dark:text-green-400' },
  READ: { chip: 'bg-green-50 dark:bg-green-950', icon: 'text-green-600 dark:text-green-400', badge: 'bg-green-50 text-green-700 dark:bg-green-950 dark:text-green-400' },
  FAILED: { chip: 'bg-red-50 dark:bg-red-950', icon: 'text-red-600 dark:text-red-400', badge: 'bg-red-50 text-red-700 dark:bg-red-950 dark:text-red-400' },
  QUEUED: { chip: 'bg-muted', icon: 'text-muted-foreground', badge: 'bg-muted text-muted-foreground' },
};

function DeliveryCard({ row }: { row: MessageDeliveryLogRow }) {
  const { t } = useTranslation('admin');
  const Icon = CHANNEL_ICONS[row.channel];
  const style = STATUS_STYLES[row.status] ?? STATUS_STYLES.QUEUED;
  return (
    <li className={cn('flex items-start gap-3 rounded-lg border p-3', row.status === 'FAILED' && 'bg-destructive/5')}>
      <span className={cn('flex size-9 shrink-0 items-center justify-center rounded-full', style.chip)} aria-hidden>
        <Icon className={cn('size-4', style.icon)} />
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex items-center justify-between gap-2">
          <span className="truncate font-mono text-sm text-foreground">{row.recipientMasked}</span>
          <Badge variant="outline" className={cn('shrink-0 text-[10px]', style.badge)}>
            {t(`deliveries.status.${row.status}`, { defaultValue: row.status })}
          </Badge>
        </div>
        <p className="mt-0.5 text-xs text-muted-foreground">
          {row.channel} &middot; {row.provider}
        </p>
        {row.errorCode && <p className="mt-1 break-words text-xs text-destructive">{row.errorCode}</p>}
        <p className="mt-1 text-[10px] text-muted-foreground">{new Date(row.createdAt).toLocaleString()}</p>
      </div>
    </li>
  );
}

function MessageDeliveriesTab() {
  const { t } = useTranslation(['admin', 'common']);
  const [status, setStatus] = useState<MessageStatus | '__all__'>('__all__');
  const query = useQuery({
    queryKey: ['admin-message-deliveries', status],
    queryFn: () => listMessageDeliveries(status === '__all__' ? undefined : status, 100),
    refetchInterval: REFRESH_INTERVAL_MS,
  });

  return (
    <div className="space-y-4">
      <Select value={status} onValueChange={(v) => setStatus(v as MessageStatus | '__all__')}>
        <SelectTrigger aria-label={t('deliveries.statusFilter')} className="w-48">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="__all__">{t('deliveries.statusAll')}</SelectItem>
          <SelectItem value="SENT">{t('deliveries.status.SENT')}</SelectItem>
          <SelectItem value="FAILED">{t('deliveries.status.FAILED')}</SelectItem>
          <SelectItem value="QUEUED">{t('deliveries.status.QUEUED')}</SelectItem>
        </SelectContent>
      </Select>

      {query.isLoading ? (
        <Skeleton className="h-48 w-full" />
      ) : query.isError ? (
        <FormError message={resolveErrorMessage(query.error)} />
      ) : (query.data ?? []).length === 0 ? (
        <EmptyState title={t('deliveries.empty')} />
      ) : (
        <ul className="space-y-2">
          {(query.data ?? []).map((row) => (
            <DeliveryCard key={row.id} row={row} />
          ))}
        </ul>
      )}
    </div>
  );
}

function ErrorsTab() {
  const { t } = useTranslation(['admin', 'common']);
  const query = useQuery({
    queryKey: ['admin-errors'],
    queryFn: () => listErrors(100),
    refetchInterval: REFRESH_INTERVAL_MS,
  });

  if (query.isLoading) return <Skeleton className="h-48 w-full" />;
  if (query.isError) return <FormError message={resolveErrorMessage(query.error)} />;
  if ((query.data ?? []).length === 0) return <EmptyState title={t('errors.empty')} />;

  return (
    <ul className="space-y-2">
      {(query.data ?? []).map((row) => (
        <li key={row.id} className="flex items-start gap-3 rounded-lg border bg-destructive/5 p-3">
          <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-red-50 dark:bg-red-950" aria-hidden>
            <Bug className="size-4 text-red-600 dark:text-red-400" />
          </span>
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
              <span className="truncate font-mono text-sm font-medium text-foreground">
                {row.httpMethod} {row.path}
              </span>
              {row.platformLevel && (
                <Badge variant="outline" className="text-[10px]">
                  {t('errors.platformLevel')}
                </Badge>
              )}
            </div>
            <p className="mt-0.5 truncate text-xs text-muted-foreground">{row.exceptionClass}</p>
            {row.message && <p className="mt-1 line-clamp-2 text-xs text-destructive">{row.message}</p>}
            <p className="mt-1 text-[10px] text-muted-foreground">{new Date(row.occurredAt).toLocaleString()}</p>
          </div>
        </li>
      ))}
    </ul>
  );
}

export function OpsPage() {
  const { t } = useTranslation(['admin', 'common']);
  return (
    <div>
      <PageHeader title={t('title')} description={t('subtitle')} />
      <Tabs defaultValue="deliveries">
        <TabsList>
          <TabsTrigger value="deliveries">{t('tabs.deliveries')}</TabsTrigger>
          <TabsTrigger value="errors">{t('tabs.errors')}</TabsTrigger>
        </TabsList>
        <TabsContent value="deliveries">
          <MessageDeliveriesTab />
        </TabsContent>
        <TabsContent value="errors">
          <ErrorsTab />
        </TabsContent>
      </Tabs>
    </div>
  );
}
