import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { ClipboardList } from 'lucide-react';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/data/EmptyState';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Skeleton } from '@/components/ui/skeleton';
import { Button } from '@/components/ui/button';
import { useCan } from '@/hooks/useCan';
import { listCollections, listFollowUps, getTrackerCounts } from '../api/trackerApi';
import { FollowUpTable } from '../components/FollowUpTable';
import { CollectionTable } from '../components/CollectionTable';
import { RangeFilterPills } from '../components/RangeFilterPills';
import type { TrackerRange } from '../types';

// B-13 -- the daily work queue. Follow-ups tab: DATA_VIEW_ALL or
// DATA_VIEW_OWN. Collections tab: FINANCIAL_VIEW, hidden entirely for Sales
// Executive/View Only (§9). A role with neither sees neither tab and an
// honest empty state, not a 403 page -- this component itself never
// 403s, each underlying query does if the tab is actually opened.
export function TrackerPage() {
  const { t } = useTranslation(['tracker', 'common']);
  // Both useCan() calls must always run regardless of the other's result --
  // `||` short-circuits, which would skip the second hook call on some
  // renders and violate the Rules of Hooks (a real bug caught before it
  // ever reached a browser: this must be two unconditional hook calls
  // combined afterward, not `useCan(a) || useCan(b)`).
  const hasViewAll = useCan('DATA_VIEW_ALL');
  const hasViewOwn = useCan('DATA_VIEW_OWN');
  const canViewLeads = hasViewAll || hasViewOwn;
  const canEditLeads = canViewLeads; // logging/marking-done needs the same visibility as viewing
  const canViewFinancial = useCan('FINANCIAL_VIEW');
  const canRecordPayment = useCan('FINANCIAL_RECORD_PAYMENT');

  // Deep-linked from the Dashboard's "Follow-ups Today" card
  // (?tab=followups&range=today) -- read once on mount so the initial tab/
  // range reflect the URL instead of always falling back to the hardcoded
  // default, which is what silently broke that card's redirect before.
  const [searchParams] = useSearchParams();
  const urlTab = searchParams.get('tab');
  const urlRange = searchParams.get('range') as TrackerRange | null;
  const VALID_RANGES: TrackerRange[] = ['today', 'week', 'overdue', 'all'];
  const initialTab: 'followups' | 'collections' =
    urlTab === 'collections' && canViewFinancial
      ? 'collections'
      : urlTab === 'followups' && canViewLeads
        ? 'followups'
        : canViewLeads
          ? 'followups'
          : 'collections';
  const initialRange: TrackerRange = urlRange && VALID_RANGES.includes(urlRange) ? urlRange : initialTab === 'followups' ? 'today' : 'overdue';

  const [tab, setTab] = useState<'followups' | 'collections'>(initialTab);
  const [followUpRange, setFollowUpRange] = useState<TrackerRange>(initialTab === 'followups' ? initialRange : 'today');
  const [collectionRange, setCollectionRange] = useState<TrackerRange>(initialTab === 'collections' ? initialRange : 'overdue');

  const countsQuery = useQuery({ queryKey: ['tracker-counts'], queryFn: getTrackerCounts });
  // Real bug found live: this was a plain useQuery capped at limit=50 with
  // the CursorPage's own hasMore/nextCursor never read anywhere -- an org
  // with more than 50 leads due for follow-up (or more than 50 collections
  // due/overdue) silently lost visibility into everything past the 50th
  // row, with no "load more" control and no visible indication of
  // truncation beyond the tab badge's count (from /counts, a separate
  // query) not matching the row count -- easy to miss since the badge and
  // the table are never compared against each other by a real user.
  // Confirmed by seeding 61 due leads for a test org: the tab read
  // "Follow-ups (61)" while only 50 rows ever rendered. Fixed by switching
  // to useInfiniteQuery + a "Load more" button, matching the identical,
  // already-established pattern LeadListPage/ProjectListPage use for the
  // same CursorPage-shaped endpoints.
  const followUpsQuery = useInfiniteQuery({
    queryKey: ['tracker-followups', followUpRange],
    queryFn: ({ pageParam }) => listFollowUps({ range: followUpRange, limit: 50, cursor: pageParam }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => (lastPage.hasMore ? (lastPage.nextCursor ?? undefined) : undefined),
    enabled: canViewLeads && tab === 'followups',
  });
  const collectionsQuery = useInfiniteQuery({
    queryKey: ['tracker-collections', collectionRange],
    queryFn: ({ pageParam }) => listCollections({ range: collectionRange, limit: 50, cursor: pageParam }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => (lastPage.hasMore ? (lastPage.nextCursor ?? undefined) : undefined),
    enabled: canViewFinancial && tab === 'collections',
  });
  const followUpRows = useMemo(() => followUpsQuery.data?.pages.flatMap((p) => p.items) ?? [], [followUpsQuery.data]);
  const collectionRows = useMemo(() => collectionsQuery.data?.pages.flatMap((p) => p.items) ?? [], [collectionsQuery.data]);

  if (!canViewLeads && !canViewFinancial) {
    return (
      <div>
        <PageHeader title={t('title')} />
        <EmptyState icon={<ClipboardList className="size-10" />} title={t('noAccess')} />
      </div>
    );
  }

  return (
    <div>
      <PageHeader title={t('title')} />

      <Tabs value={tab} onValueChange={(v) => setTab(v as typeof tab)} className="mb-4">
        <TabsList>
          {canViewLeads && (
            <TabsTrigger value="followups">
              {t('tabs.followUps')} {countsQuery.data ? `(${countsQuery.data.followUps})` : ''}
            </TabsTrigger>
          )}
          {canViewFinancial && (
            <TabsTrigger value="collections">
              {t('tabs.collections')} {countsQuery.data ? `(${countsQuery.data.collections})` : ''}
            </TabsTrigger>
          )}
        </TabsList>
      </Tabs>

      {tab === 'followups' && canViewLeads && (
        <>
          <RangeFilterPills value={followUpRange} onChange={setFollowUpRange} />
          {followUpsQuery.isLoading ? (
            <Skeleton className="h-48 w-full rounded-md" />
          ) : followUpRows.length === 0 ? (
            <EmptyState icon={<ClipboardList className="size-10" />} title={t('followUp.empty')} />
          ) : (
            <>
              <FollowUpTable rows={followUpRows} canEdit={canEditLeads} />
              {followUpsQuery.hasNextPage && (
                <div className="mt-4 flex justify-center">
                  <Button variant="outline" onClick={() => followUpsQuery.fetchNextPage()} disabled={followUpsQuery.isFetchingNextPage}>
                    {t('loadMore')}
                  </Button>
                </div>
              )}
            </>
          )}
        </>
      )}

      {tab === 'collections' && canViewFinancial && (
        <>
          <RangeFilterPills value={collectionRange} onChange={setCollectionRange} />
          {collectionsQuery.isLoading ? (
            <Skeleton className="h-48 w-full rounded-md" />
          ) : collectionRows.length === 0 ? (
            <EmptyState icon={<ClipboardList className="size-10" />} title={t('collection.empty')} />
          ) : (
            <>
              <CollectionTable rows={collectionRows} canEdit={canRecordPayment} />
              {collectionsQuery.hasNextPage && (
                <div className="mt-4 flex justify-center">
                  <Button variant="outline" onClick={() => collectionsQuery.fetchNextPage()} disabled={collectionsQuery.isFetchingNextPage}>
                    {t('loadMore')}
                  </Button>
                </div>
              )}
            </>
          )}
        </>
      )}
    </div>
  );
}
