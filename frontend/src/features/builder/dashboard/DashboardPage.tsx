import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { PageHeader } from '@/components/layout/PageHeader';
import { Skeleton } from '@/components/ui/skeleton';
import { getDashboard } from './api/dashboardApi';
import { SummaryCard } from './components/SummaryCard';
import { AlertBanner } from './components/AlertBanner';
import { EmptyDashboard } from './components/EmptyDashboard';

// B-01 -- all ten §11.1 cards, the landing screen for every builder
// account. DashboardGrid: 4 cols desktop -> 2 cols tablet -> 2 cols at
// 360px (CLAUDE.md §24.2), never 1 -- a single card per row on a 360px
// phone wastes the one screen every user opens first.
export function BuilderDashboardPage() {
  const { t } = useTranslation('dashboard');
  const dashboardQuery = useQuery({ queryKey: ['builder-dashboard'], queryFn: getDashboard });

  if (dashboardQuery.isLoading || !dashboardQuery.data) {
    return (
      <>
        <PageHeader title={t('title')} />
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <Skeleton key={i} className="h-24 w-full rounded-md" />
          ))}
        </div>
      </>
    );
  }

  const data = dashboardQuery.data;

  if (data.isOnboarding) {
    return (
      <>
        <PageHeader title={t('title')} />
        <EmptyDashboard />
      </>
    );
  }

  const updatedMinutesAgo = Math.max(0, Math.round((Date.now() - new Date(data.generatedAt).getTime()) / 60000));

  return (
    <div>
      <PageHeader title={t('title')} />
      <p className="mb-3 text-xs text-muted-foreground">
        {updatedMinutesAgo === 0 ? t('updatedJustNow') : t('updatedMinutesAgo', { count: updatedMinutesAgo })}
      </p>
      {data.scoped && (
        <p className="mb-3 text-xs text-muted-foreground">
          {t('scopedNote', { scoped: data.scopedProjectCount, total: data.totalProjectCount })}
        </p>
      )}

      <AlertBanner alerts={data.alerts} />

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {Object.entries(data.cards).map(([key, card]) => (
          <SummaryCard key={key} cardKey={key} card={card} />
        ))}
      </div>
    </div>
  );
}
