import { useTranslation } from 'react-i18next';
import { useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { PageHeader } from '@/components/layout/PageHeader';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { useCan } from '@/hooks/useCan';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { formatIndianCurrency } from '@/lib/formatters';
import { blockBroker, deactivateBroker, getBankDetails, getBroker, getPerformance, listTiers, reactivateBroker } from '../api/brokerApi';
import { CommissionConfigTab } from '../components/CommissionConfigTab';
import { CommissionTreeTab } from '../components/CommissionTreeTab';
import { DealsTab } from '../components/DealsTab';
import { LedgerTab } from '../components/LedgerTab';
import { NetworkTab } from '../components/NetworkTab';
import { NotesTab } from '../components/NotesTab';

export function BrokerDetailPage() {
  const { t } = useTranslation(['broker', 'common']);
  const { id } = useParams<{ id: string }>();
  const queryClient = useQueryClient();
  const canManage = useCan('BROKER_MANAGE');
  const canViewSensitive = useCan('SENSITIVE_VIEW');

  const brokerQuery = useQuery({ queryKey: ['broker', id], queryFn: () => getBroker(id!), enabled: !!id });
  const performanceQuery = useQuery({ queryKey: ['broker-performance', id], queryFn: () => getPerformance(id!), enabled: !!id });
  const tiersQuery = useQuery({ queryKey: ['broker-tiers'], queryFn: () => listTiers() });

  const deactivateMutation = useMutation({
    mutationFn: () => deactivateBroker(id!),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['broker', id] }),
  });
  const reactivateMutation = useMutation({
    mutationFn: () => reactivateBroker(id!),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['broker', id] }),
  });
  const blockMutation = useMutation({
    mutationFn: () => blockBroker(id!),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['broker', id] }),
  });

  if (brokerQuery.isLoading) return null;
  if (brokerQuery.isError) return <p className="text-sm text-destructive">{resolveErrorMessage(brokerQuery.error)}</p>;
  const broker = brokerQuery.data;
  if (!broker) return null;

  const tiers = [...(tiersQuery.data ?? [])].sort((a, b) => a.sortOrder - b.sortOrder);
  const currentTierIdx = tiers.findIndex((tier) => tier.id === broker.tierId);
  const nextTier = currentTierIdx >= 0 ? tiers[currentTierIdx + 1] : undefined;
  const remaining = nextTier ? Math.max(0, nextTier.minDeals - broker.dealsClosedCount) : 0;

  return (
    <div>
      <PageHeader
        title={broker.fullName}
        description={broker.cityArea ?? undefined}
        actions={
          canManage && (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="outline">{t('common:actions.more', { defaultValue: 'Actions' })}</Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent>
                {broker.status === 'ACTIVE' && (
                  <DropdownMenuItem onClick={() => deactivateMutation.mutate()}>{t('detail.deactivate')}</DropdownMenuItem>
                )}
                {broker.status !== 'ACTIVE' && (
                  <DropdownMenuItem onClick={() => reactivateMutation.mutate()}>{t('detail.reactivate')}</DropdownMenuItem>
                )}
                {broker.status !== 'BLOCKED' && (
                  <DropdownMenuItem onClick={() => blockMutation.mutate()} className="text-destructive">
                    {t('detail.block')}
                  </DropdownMenuItem>
                )}
              </DropdownMenuContent>
            </DropdownMenu>
          )
        }
      />

      <div className="mb-4 flex flex-wrap items-center gap-3">
        <Badge variant={broker.status === 'ACTIVE' ? 'default' : 'outline'}>{t(`status.${broker.status}`)}</Badge>
        {broker.tierName && (
          <span className="text-sm text-muted-foreground">
            {nextTier
              ? t('detail.tierProgress', { tierName: broker.tierName, remaining, nextTierName: nextTier.name })
              : `${broker.tierName} — ${t('detail.tierMax')}`}
          </span>
        )}
        {broker.commissionType === 'DESIGNATION' && broker.designationManuallyOverridden && (
          <Badge variant="secondary">{t('designation.overriddenBadge')}</Badge>
        )}
      </div>

      {performanceQuery.data && (
        <div className="mb-6 grid grid-cols-2 gap-3 sm:grid-cols-4">
          <StatCard label={t('performance.totalDeals')} value={String(performanceQuery.data.totalDealsAttributed)} />
          <StatCard label={t('performance.revenueGenerated')} value={formatIndianCurrency(performanceQuery.data.totalRevenueGenerated)} />
          <StatCard label={t('performance.commissionEarned')} value={formatIndianCurrency(performanceQuery.data.totalCommissionEarned)} />
          {/* 06-BROKER-NETWORK-ENGINE.md §8a: "keep Released visible too if
              useful, but Due is the actionable one" -- DESIGNATION brokers
              show both (Released is a real, distinct figure once Paid can
              be non-zero); PERCENTAGE/FIXED brokers have no release concept
              at all, so they only ever show Due, exactly as before. */}
          {broker.commissionType === 'DESIGNATION' && performanceQuery.data.totalCommissionReleased != null && (
            <StatCard label={t('performance.commissionReleased')} value={formatIndianCurrency(performanceQuery.data.totalCommissionReleased)} />
          )}
          <StatCard label={t('performance.commissionDue')} value={formatIndianCurrency(performanceQuery.data.commissionDue)} />
        </div>
      )}

      <Tabs defaultValue="overview">
        <TabsList>
          <TabsTrigger value="overview">{t('detail.tabs.overview')}</TabsTrigger>
          <TabsTrigger value="commissionConfig">{t('detail.tabs.commissionConfig')}</TabsTrigger>
          <TabsTrigger value="deals">{t('detail.tabs.deals')}</TabsTrigger>
          <TabsTrigger value="ledger">{t('detail.tabs.ledger')}</TabsTrigger>
          {broker.commissionType === 'DESIGNATION' && <TabsTrigger value="network">{t('detail.tabs.network')}</TabsTrigger>}
          <TabsTrigger value="notes">{t('detail.tabs.notes')}</TabsTrigger>
        </TabsList>

        <TabsContent value="overview" className="space-y-3 pt-4 text-sm">
          <dl className="grid grid-cols-2 gap-x-4 gap-y-2">
            <dt className="text-muted-foreground">{t('form.mobile')}</dt>
            <dd>{broker.mobile}</dd>
            <dt className="text-muted-foreground">{t('form.email')}</dt>
            <dd>{broker.email ?? '—'}</dd>
            <dt className="text-muted-foreground">{t('form.firmName')}</dt>
            <dd>{broker.firmName ?? '—'}</dd>
            <dt className="text-muted-foreground">{t('form.commissionType')}</dt>
            <dd>
              {broker.commissionType === 'PERCENTAGE'
                ? `${broker.commissionPct}%`
                : formatIndianCurrency(broker.commissionFixed ?? 0)}
            </dd>
          </dl>
          {canViewSensitive && <BankDetailsSection brokerId={broker.id} />}
        </TabsContent>

        <TabsContent value="commissionConfig" className="pt-4">
          <CommissionConfigTab brokerId={broker.id} canManage={canManage} />
        </TabsContent>

        <TabsContent value="deals" className="pt-4">
          <DealsTab brokerId={broker.id} />
        </TabsContent>

        <TabsContent value="ledger" className="pt-4">
          {/* DESIGNATION brokers never get an M6 commission_ledger_entry
              (that path is PERCENTAGE/FIXED-only, see PlotSaleService) --
              the Ledger tab shows their frozen/releasing booking_commission
              tree instead, under the same tab so there's exactly one place
              a builder looks for "what does this broker earn." */}
          {broker.commissionType === 'DESIGNATION' ? (
            <CommissionTreeTab brokerId={broker.id} />
          ) : (
            <LedgerTab brokerId={broker.id} />
          )}
        </TabsContent>

        {broker.commissionType === 'DESIGNATION' && (
          <TabsContent value="network" className="pt-4">
            <NetworkTab broker={broker} canManage={canManage} />
          </TabsContent>
        )}

        <TabsContent value="notes" className="pt-4">
          <NotesTab brokerId={broker.id} />
        </TabsContent>
      </Tabs>
    </div>
  );
}

function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-border bg-card p-3">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="text-lg font-semibold">{value}</p>
    </div>
  );
}

function BankDetailsSection({ brokerId }: { brokerId: string }) {
  const { t } = useTranslation('broker');
  const bankQuery = useQuery({
    queryKey: ['broker-bank-details', brokerId],
    queryFn: () => getBankDetails(brokerId),
    enabled: false,
  });
  return (
    <div className="pt-2">
      {!bankQuery.data ? (
        <Button variant="outline" size="sm" onClick={() => bankQuery.refetch()}>
          {t('detail.viewBankDetails')}
        </Button>
      ) : (
        <dl className="grid grid-cols-2 gap-x-4 gap-y-2">
          <dt className="text-muted-foreground">{t('form.bankAccountName')}</dt>
          <dd>{bankQuery.data.bankAccountName ?? '—'}</dd>
          <dt className="text-muted-foreground">{t('form.bankAccountNumber')}</dt>
          <dd>{bankQuery.data.bankAccountNumber ?? '—'}</dd>
          <dt className="text-muted-foreground">{t('form.ifsc')}</dt>
          <dd>{bankQuery.data.ifsc ?? '—'}</dd>
          <dt className="text-muted-foreground">{t('form.upiId')}</dt>
          <dd>{bankQuery.data.upiId ?? '—'}</dd>
        </dl>
      )}
    </div>
  );
}
