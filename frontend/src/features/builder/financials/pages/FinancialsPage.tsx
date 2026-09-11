import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/data/EmptyState';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Skeleton } from '@/components/ui/skeleton';
import { Wallet } from 'lucide-react';
import { getFinancialSummary, getRevenueTrend, listFinancialPayments, listPendingInstalments } from '../api/financialApi';
import { FinancialSummaryCards } from '../components/FinancialSummaryCards';
import { RevenueTrendChart } from '../components/RevenueTrendChart';
import { PaymentModeBreakdown } from '../components/PaymentModeBreakdown';
import { PaymentRecordsTable } from '../components/PaymentRecordsTable';
import { PendingInstalmentsTable } from '../components/PendingInstalmentsTable';
import { FinancialFilterBar } from '../components/FinancialFilterBar';
import { Button } from '@/components/ui/button';
import { useCan } from '@/hooks/useCan';
import { BulkGenerateDialog } from '../../documents/components/BulkGenerateDialog';

// B-08 §5: /builder/financials with tabs Overview · Payments · Pending &
// Overdue · Commission Paid. FINANCIAL_VIEW-gated entirely at the route
// level (see nav.ts/AppShell) -- Sales Executive and View Only never even
// see the sidebar link, and the backend independently 403s regardless.
export function FinancialsPage() {
  const { t } = useTranslation(['financial', 'common']);
  const canGenerateDocuments = useCan('DOCUMENT_GENERATE');
  const [bulkGenerateOpen, setBulkGenerateOpen] = useState(false);

  // Deep-linked from the Dashboard's "Instalments Due This Month" card
  // (?tab=pending) -- read once on mount so the initial tab reflects the
  // URL instead of always falling back to Overview, which is what silently
  // broke that card's redirect before.
  const [searchParams] = useSearchParams();
  const VALID_TABS = ['overview', 'payments', 'pending', 'commission'] as const;
  const urlTab = searchParams.get('tab');
  const initialTab = (VALID_TABS as readonly string[]).includes(urlTab ?? '') ? (urlTab as (typeof VALID_TABS)[number]) : 'overview';
  const [tab, setTab] = useState<'overview' | 'payments' | 'pending' | 'commission'>(initialTab);
  const [projectId, setProjectId] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [mode, setMode] = useState('');

  const summaryQuery = useQuery({
    queryKey: ['financial-summary', projectId, from, to],
    queryFn: () => getFinancialSummary(projectId || undefined, from || undefined, to || undefined),
  });
  const trendQuery = useQuery({
    queryKey: ['financial-trend', projectId],
    queryFn: () => getRevenueTrend(projectId || undefined, 12),
    enabled: tab === 'overview',
  });
  const paymentsQuery = useQuery({
    queryKey: ['financial-payments', projectId, from, to, mode],
    queryFn: () => listFinancialPayments({ projectId: projectId || undefined, from: from || undefined, to: to || undefined, mode: mode || undefined, limit: 50 }),
    enabled: tab === 'payments',
  });
  const pendingQuery = useQuery({
    queryKey: ['financial-pending', projectId],
    queryFn: () => listPendingInstalments({ projectId: projectId || undefined, limit: 50 }),
    enabled: tab === 'pending',
  });
  const overdueQuery = useQuery({
    queryKey: ['financial-overdue', projectId],
    queryFn: () => listPendingInstalments({ projectId: projectId || undefined, overdueOnly: true, limit: 50 }),
    enabled: tab === 'pending',
  });

  const noData = summaryQuery.data
    && summaryQuery.data.totalRevenueAllTime === 0
    && summaryQuery.data.pendingCollections === 0
    && summaryQuery.data.overdueInstalments.count === 0;

  return (
    <div>
      <PageHeader title={t('title')} />
      <FinancialFilterBar
        projectId={projectId} onProjectChange={setProjectId}
        from={from} onFromChange={setFrom} to={to} onToChange={setTo}
        {...(tab === 'payments' ? { mode, onModeChange: setMode } : {})}
      />

      <Tabs value={tab} onValueChange={(v) => setTab(v as typeof tab)} className="mb-4">
        <TabsList>
          <TabsTrigger value="overview">{t('tabs.overview')}</TabsTrigger>
          <TabsTrigger value="payments">{t('tabs.payments')}</TabsTrigger>
          <TabsTrigger value="pending">{t('tabs.pending')}</TabsTrigger>
          <TabsTrigger value="commission">{t('tabs.commission')}</TabsTrigger>
        </TabsList>
      </Tabs>

      {summaryQuery.isLoading && <Skeleton className="h-32 w-full rounded-md" />}

      {tab === 'overview' && summaryQuery.data && (
        noData ? (
          <EmptyState icon={<Wallet className="size-10" />} title={t('emptyOrg')} />
        ) : (
          <div className="space-y-6">
            <FinancialSummaryCards summary={summaryQuery.data} />
            <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
              {trendQuery.data && <RevenueTrendChart data={trendQuery.data} />}
              <PaymentModeBreakdown data={summaryQuery.data.paymentModeBreakdown} />
            </div>
          </div>
        )
      )}

      {tab === 'payments' && (
        paymentsQuery.data && paymentsQuery.data.items.length === 0 ? (
          <EmptyState icon={<Wallet className="size-10" />} title={t('payments.empty')} />
        ) : (
          paymentsQuery.data && <PaymentRecordsTable rows={paymentsQuery.data.items} />
        )
      )}

      {tab === 'pending' && (
        <div className="space-y-6">
          <div>
            <h3 className="mb-2 text-sm font-medium">{t('pending.title')}</h3>
            {pendingQuery.data && pendingQuery.data.items.length === 0 ? (
              <EmptyState icon={<Wallet className="size-10" />} title={t('pending.empty')} />
            ) : (
              pendingQuery.data && <PendingInstalmentsTable rows={pendingQuery.data.items} />
            )}
          </div>
          <div>
            <div className="mb-2 flex items-center justify-between">
              <h3 className="text-sm font-medium text-destructive">{t('pending.overdueTitle')}</h3>
              {canGenerateDocuments && overdueQuery.data && overdueQuery.data.items.length > 0 && (
                <Button size="sm" variant="outline" onClick={() => setBulkGenerateOpen(true)}>
                  {t('pending.generateDemandLetters')}
                </Button>
              )}
            </div>
            {overdueQuery.data && overdueQuery.data.items.length === 0 ? (
              <EmptyState icon={<Wallet className="size-10" />} title={t('pending.overdueEmpty')} />
            ) : (
              overdueQuery.data && <PendingInstalmentsTable rows={overdueQuery.data.items} />
            )}
          </div>
        </div>
      )}

      {overdueQuery.data && (
        <BulkGenerateDialog
          open={bulkGenerateOpen}
          onOpenChange={setBulkGenerateOpen}
          candidates={Array.from(
            new Map(overdueQuery.data.items.map((r) => [r.plotSaleId, { plotSaleId: r.plotSaleId, buyerName: r.buyerName, plotNumber: r.plotNumber }])).values(),
          )}
        />
      )}

      {tab === 'commission' && (
        // B-14 Broker Management doesn't exist yet -- an honest empty state,
        // not a fabricated zero-value table (same "don't gloss over a real
        // gap" standard this project has held to since M3's broker fields).
        <EmptyState icon={<Wallet className="size-10" />} title={t('commission.notBuilt')} />
      )}
    </div>
  );
}
