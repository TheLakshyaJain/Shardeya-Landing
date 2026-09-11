import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/utils';
import { formatIndianCurrency } from '@/lib/formatters';
import { EVENT_TYPE_ICONS, EVENT_TYPE_STYLES, parseInstalmentTitle, stripKnownPrefix } from '../eventVisuals';
import type { CalendarEventResponse } from '../types';

interface EventCardProps {
  event: CalendarEventResponse;
  overdue: boolean;
  onComplete: (id: string) => void;
  completing: boolean;
}

export function EventCard({ event, overdue, onComplete, completing }: EventCardProps) {
  const { t } = useTranslation(['calendar', 'common']);
  const Icon = EVENT_TYPE_ICONS[event.eventType];
  const style = EVENT_TYPE_STYLES[event.eventType];
  const instalment = event.eventType === 'INSTALMENT_DUE' ? parseInstalmentTitle(event.title) : null;

  return (
    <li
      className={cn(
        'flex items-start gap-3 rounded-lg border border-l-4 bg-card p-3 shadow-sm',
        style.accent,
        overdue && 'bg-destructive/5',
      )}
    >
      <span className={cn('flex size-9 shrink-0 items-center justify-center rounded-full', style.chip)} aria-hidden>
        <Icon className={cn('size-4', style.icon)} />
      </span>

      <div className="min-w-0 flex-1">
        {instalment ? (
          <>
            <p className="text-base font-semibold leading-tight text-foreground">{formatIndianCurrency(instalment.amount)}</p>
            <p className="truncate text-sm text-muted-foreground">{instalment.label}</p>
          </>
        ) : (
          <p className="line-clamp-2 text-sm font-medium leading-snug text-foreground">
            {stripKnownPrefix(event.eventType, event.title)}
          </p>
        )}
        <div className="mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-muted-foreground">
          <span>{t(`eventType.${event.eventType}`)}</span>
          {event.eventTime && (
            <>
              <span aria-hidden>&middot;</span>
              <span>{event.eventTime}</span>
            </>
          )}
          {overdue && (
            <Badge variant="destructive" className="h-4 rounded-sm px-1.5 text-[10px] font-medium">
              {t('agenda.overdue')}
            </Badge>
          )}
        </div>
      </div>

      {event.source === 'MANUAL' && event.status !== 'DONE' && (
        <Button size="sm" variant="ghost" className="shrink-0" disabled={completing} onClick={() => onComplete(event.id)}>
          {t('complete')}
        </Button>
      )}
    </li>
  );
}
