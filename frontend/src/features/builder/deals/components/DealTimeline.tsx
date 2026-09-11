import { useTranslation } from 'react-i18next';
import { formatIndianCurrency } from '@/lib/formatters';
import type { DealDetailResponse } from '../types';

interface DealTimelineProps {
  deal: DealDetailResponse;
}

interface TimelineEvent {
  date: string;
  label: string;
  detail?: string;
}

// B-10 §6: "lead created -> visits -> booking -> each payment -> completion,
// as a vertical timeline -- this single view answers almost every dispute."
// Booking/completion are synthesised from the sale's own dates; visits/calls
// come from the lead's interaction log (only present if this sale is linked
// to a tracked customer -- plenty of sales, especially older/migrated ones,
// aren't); payments come straight from the payment history already fetched
// for this detail view.
export function DealTimeline({ deal }: DealTimelineProps) {
  const { t } = useTranslation('deal');

  const events: TimelineEvent[] = [];
  for (const i of deal.interactionTimeline) {
    events.push({ date: i.occurredOn, label: t('timeline.interaction', { type: i.type }), detail: i.remarks });
  }
  events.push({ date: deal.purchaseDate, label: t('timeline.booked') });
  for (const p of deal.payments) {
    events.push({ date: p.paidOn, label: t('timeline.payment', { amount: formatIndianCurrency(p.amount) }), detail: p.receiptNo });
  }
  if (deal.status === 'CANCELLED') {
    events.push({ date: deal.purchaseDate, label: t('timeline.cancelled'), detail: deal.cancellationReason ?? undefined });
  } else if (deal.status === 'COMPLETED') {
    events.push({ date: deal.purchaseDate, label: t('timeline.completed') });
  }
  events.sort((a, b) => a.date.localeCompare(b.date));

  if (events.length === 0) {
    return <p className="text-sm text-muted-foreground">{t('timeline.empty')}</p>;
  }

  return (
    <ol className="space-y-3 border-l pl-4">
      {events.map((e, idx) => (
        <li key={idx} className="relative">
          <span className="absolute -left-[21px] top-1 size-2 rounded-full bg-primary" />
          <p className="text-xs text-muted-foreground">{e.date}</p>
          <p className="text-sm font-medium">{e.label}</p>
          {e.detail && <p className="text-sm text-muted-foreground">{e.detail}</p>}
        </li>
      ))}
    </ol>
  );
}
