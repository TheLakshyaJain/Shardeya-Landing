import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { CalendarCheck2 } from 'lucide-react';
import { EmptyState } from '@/components/data/EmptyState';
import { cn } from '@/lib/utils';
import { EventCard } from './EventCard';
import type { CalendarEventResponse } from '../types';

interface AgendaListProps {
  events: CalendarEventResponse[];
  onComplete: (id: string) => void;
  completingId?: string;
}

function isoDate(d: Date): string {
  return d.toISOString().slice(0, 10);
}

// Date-driven, not window-driven -- a date group is "overdue" purely
// because it's before today, regardless of which 30-day window is being
// viewed. In the default (windowOffset=0) view this never fires (the query
// itself starts at today), but navigating to a past window makes every
// group in it correctly read as overdue rather than looking like ordinary
// upcoming events.
function dateGroupInfo(date: string, todayStr: string, tomorrowStr: string) {
  return { isToday: date === todayStr, isTomorrow: date === tomorrowStr, isOverdue: date < todayStr };
}

export function AgendaList({ events, onComplete, completingId }: AgendaListProps) {
  const { t, i18n } = useTranslation(['calendar', 'common']);

  const byDate = useMemo(() => {
    const map = new Map<string, CalendarEventResponse[]>();
    for (const e of events) {
      const list = map.get(e.eventDate) ?? [];
      list.push(e);
      map.set(e.eventDate, list);
    }
    return [...map.entries()].sort(([a], [b]) => a.localeCompare(b));
  }, [events]);

  if (byDate.length === 0) {
    return (
      <EmptyState
        icon={<CalendarCheck2 className="size-10" />}
        title={t('agenda.emptyTitle')}
        description={t('agenda.emptyDescription')}
      />
    );
  }

  const todayStr = isoDate(new Date());
  const tomorrowStr = isoDate(new Date(Date.now() + 24 * 60 * 60 * 1000));
  const dateFormatter = new Intl.DateTimeFormat(i18n.language, {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    // CLAUDE.md: digit grouping stays Latin numerals even in the Hindi UI
    // (formatters.ts already established this for currency) -- some ICU
    // locales default 'hi' to Devanagari digits for numeric date fields,
    // which this pins back to plain 1-9.
    numberingSystem: 'latn',
  });

  return (
    <div className="space-y-5">
      {byDate.map(([date, dayEvents]) => {
        const { isToday, isTomorrow, isOverdue } = dateGroupInfo(date, todayStr, tomorrowStr);
        const label = isToday ? t('agenda.today') : isTomorrow ? t('agenda.tomorrow') : dateFormatter.format(new Date(date));

        return (
          <div key={date}>
            <div className="mb-2 flex items-center gap-2">
              <h3
                className={cn(
                  'text-sm font-semibold',
                  isToday ? 'text-primary' : isOverdue ? 'text-destructive' : 'text-foreground',
                )}
              >
                {label}
              </h3>
              <span className="text-xs text-muted-foreground">({dayEvents.length})</span>
              {isOverdue && (
                <span className="rounded-sm bg-destructive/10 px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wide text-destructive">
                  {t('agenda.overdue')}
                </span>
              )}
            </div>
            <ul className="space-y-2">
              {dayEvents.map((e) => (
                <EventCard key={e.id} event={e} overdue={isOverdue} onComplete={onComplete} completing={completingId === e.id} />
              ))}
            </ul>
          </div>
        );
      })}
    </div>
  );
}
