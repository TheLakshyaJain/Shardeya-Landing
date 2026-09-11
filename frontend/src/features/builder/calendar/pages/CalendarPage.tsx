import { useMemo, useState } from 'react';
import { useQuery, useQueryClient, useMutation } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { ChevronLeft, ChevronRight, Plus } from 'lucide-react';
import { PageHeader } from '@/components/layout/PageHeader';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { cn } from '@/lib/utils';
import { useCan } from '@/hooks/useCan';
import { listTeam } from '@/features/builder/team/api/teamApi';
import { listCalendarEvents, completeCalendarEvent } from '../api/calendarApi';
import { AddEventDialog } from '../components/AddEventDialog';
import { AgendaList } from '../components/AgendaList';
import { CALENDAR_EVENT_TYPES } from '../types';
import { EVENT_TYPE_ICONS, EVENT_TYPE_STYLES } from '../eventVisuals';

function isoDate(d: Date): string {
  return d.toISOString().slice(0, 10);
}

// Agenda list is the ONLY view built this milestone -- mobile defaults to
// agenda per M-11 §6 ("a month grid is near-useless on a 360px screen"), and
// given this milestone's overall scope, the month/week/day grid views
// (also named in B-09 §6) are a deliberate, documented trim: the agenda list
// alone already proves every M4 exit criterion that touches the calendar
// (auto-projections render, staff filter works). Add the grid views as
// their own follow-up piece of work.
//
// Post-M4 visual-only pass: layout/spacing/colour-coding rewritten (see
// AgendaList/EventCard/eventVisuals.ts, new this pass) -- no change to the
// query shape, the windowOffset pagination, or what data is fetched below.
export function CalendarPage() {
  const { t } = useTranslation(['calendar', 'common']);
  const queryClient = useQueryClient();
  const canViewAll = useCan('DATA_VIEW_ALL');
  const canCreate = useCan('DATA_CREATE');
  const [staffFilter, setStaffFilter] = useState<string>('__all__');
  const [addOpen, setAddOpen] = useState(false);
  // The agenda view has no month/week/day grid (see the comment above), but
  // it still needs SOME way to look beyond a fixed 30-day window -- without
  // this, any follow-up/instalment/event scheduled more than 30 days out is
  // permanently invisible here with no indication why. windowOffset counts
  // 30-day windows relative to today (0 = today..+30d, 1 = the next 30-day
  // window, -1 = the previous one, etc).
  const [windowOffset, setWindowOffset] = useState(0);
  const [completingId, setCompletingId] = useState<string | undefined>(undefined);

  const today = useMemo(() => new Date(), []);
  const windowStart = new Date(today.getTime() + windowOffset * 30 * 24 * 60 * 60 * 1000);
  const from = isoDate(windowStart);
  const to = isoDate(new Date(windowStart.getTime() + 30 * 24 * 60 * 60 * 1000));

  const teamQuery = useQuery({ queryKey: ['team'], queryFn: () => listTeam(), enabled: canViewAll });

  const eventsQuery = useQuery({
    queryKey: ['calendar', from, to, staffFilter],
    queryFn: () =>
      listCalendarEvents({
        from,
        to,
        assignedTo: staffFilter === '__all__' || staffFilter === '__unassigned__' ? undefined : staffFilter,
      }),
  });

  const completeMutation = useMutation({
    mutationFn: completeCalendarEvent,
    onMutate: (id: string) => setCompletingId(id),
    onSettled: () => setCompletingId(undefined),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['calendar'] }),
  });

  const events = (eventsQuery.data ?? []).filter((e) =>
    staffFilter === '__unassigned__' ? !e.assignedTo : true,
  );

  return (
    <div>
      <PageHeader
        title={t('title')}
        actions={
          canCreate && (
            <Button onClick={() => setAddOpen(true)}>
              <Plus className="size-4" />
              {t('addEvent')}
            </Button>
          )
        }
      />

      <div className="mb-4 flex flex-col gap-3 rounded-lg border bg-card p-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-2">
          <Button variant="outline" size="icon" onClick={() => setWindowOffset((o) => o - 1)} aria-label={t('nav.previous')}>
            <ChevronLeft className="size-4" />
          </Button>
          <div className="text-sm">
            <p className="font-medium text-foreground">{t('nav.range', { from, to })}</p>
            {windowOffset !== 0 && (
              <button type="button" className="text-xs text-primary underline-offset-2 hover:underline" onClick={() => setWindowOffset(0)}>
                {t('nav.today')}
              </button>
            )}
          </div>
          <Button variant="outline" size="icon" onClick={() => setWindowOffset((o) => o + 1)} aria-label={t('nav.next')}>
            <ChevronRight className="size-4" />
          </Button>
        </div>

        {canViewAll && (
          <Select value={staffFilter} onValueChange={setStaffFilter}>
            <SelectTrigger aria-label={t('staffFilter.all')} className="sm:w-48">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="__all__">{t('staffFilter.all')}</SelectItem>
              <SelectItem value="__unassigned__">{t('staffFilter.unassigned')}</SelectItem>
              {(teamQuery.data ?? []).map((m) => (
                <SelectItem key={m.id} value={m.id}>
                  {m.fullName}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        )}
      </div>

      <div className="mb-5 flex flex-wrap items-center gap-x-4 gap-y-1.5">
        {CALENDAR_EVENT_TYPES.map((ty) => {
          const Icon = EVENT_TYPE_ICONS[ty];
          const style = EVENT_TYPE_STYLES[ty];
          return (
            <span key={ty} className="flex items-center gap-1.5 text-xs text-muted-foreground">
              <span className={cn('flex size-5 items-center justify-center rounded-full', style.chip)}>
                <Icon className={cn('size-3', style.icon)} />
              </span>
              {t(`eventType.${ty}`)}
            </span>
          );
        })}
      </div>

      {eventsQuery.isLoading ? (
        <div className="space-y-2">
          {[0, 1, 2].map((i) => (
            <div key={i} className="h-16 animate-pulse rounded-lg border bg-muted/40" />
          ))}
        </div>
      ) : (
        <AgendaList events={events} onComplete={(id) => completeMutation.mutate(id)} completingId={completingId} />
      )}

      <AddEventDialog open={addOpen} onOpenChange={setAddOpen} />
    </div>
  );
}
