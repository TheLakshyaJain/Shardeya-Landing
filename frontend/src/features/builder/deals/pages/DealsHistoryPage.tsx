import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { Archive } from 'lucide-react';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/data/EmptyState';
import { Skeleton } from '@/components/ui/skeleton';
import { useCan } from '@/hooks/useCan';
import { getDealsSummary, listDeals } from '../api/dealsApi';
import { DealFilterBar } from '../components/DealFilterBar';
import { DealsSummaryStrip } from '../components/DealsSummaryStrip';
import { DealsHistoryTable } from '../components/DealsHistoryTable';

// B-10 -- read-only archive. A sale enters this list only once
// COMPLETED or CANCELLED (enforced server-side); active sales with an
// outstanding balance never appear here regardless of any filter.
export function DealsHistoryPage() {
  const { t } = useTranslation(['deal', 'common']);
  const canViewFinancial = useCan('FINANCIAL_VIEW');
  const [status, setStatus] = useState('');
  const [projectId, setProjectId] = useState('');
  const [search, setSearch] = useState('');

  const summaryQuery = useQuery({ queryKey: ['builder-deals-summary'], queryFn: () => getDealsSummary() });
  const dealsQuery = useQuery({
    queryKey: ['builder-deals', status, projectId, search],
    queryFn: () => listDeals({ status: status || undefined, projectId: projectId || undefined, search: search || undefined, limit: 50 }),
  });

  return (
    <div>
      <PageHeader title={t('title')} />
      {summaryQuery.data && <DealsSummaryStrip summary={summaryQuery.data} />}
      <DealFilterBar status={status} onStatusChange={setStatus} projectId={projectId} onProjectChange={setProjectId} search={search} onSearchChange={setSearch} />

      {dealsQuery.isLoading ? (
        <Skeleton className="h-48 w-full rounded-md" />
      ) : dealsQuery.data && dealsQuery.data.items.length === 0 ? (
        <EmptyState icon={<Archive className="size-10" />} title={t('empty')} />
      ) : (
        dealsQuery.data && <DealsHistoryTable rows={dealsQuery.data.items} showFinancials={canViewFinancial} />
      )}
    </div>
  );
}
