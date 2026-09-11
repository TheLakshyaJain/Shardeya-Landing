import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/data/EmptyState';
import { Skeleton } from '@/components/ui/skeleton';
import { Badge } from '@/components/ui/badge';
import { formatIndianCurrency } from '@/lib/formatters';
import { getNetworkCommissionSummary, getNetworkDesignationHistory, listBrokerNetwork } from '../api/brokerApi';
import type { BrokerNetworkNodeResponse } from '../types';
import { Network } from 'lucide-react';
import { DesignationHistoryList } from '../components/DesignationHistoryList';
import { SummaryStat } from '../components/SummaryStat';

export interface TreeNode extends BrokerNetworkNodeResponse {
  children: TreeNode[];
}

// 06-BROKER-NETWORK-ENGINE.md §28: name, designation, personal sales, team
// sales, rate per node. Flat list -> tree assembled client-side (org-wide
// DESIGNATION-broker counts are small; a nested server response would be
// more code for no real benefit at this scale). Exported so
// BrokerNetworkSection (§26/§27's per-broker upline/downline view) can
// build the identical shape rooted at one broker instead of every org root.
export function buildTree(nodes: BrokerNetworkNodeResponse[]): TreeNode[] {
  const byId = new Map<string, TreeNode>(nodes.map((n) => [n.id, { ...n, children: [] }]));
  const roots: TreeNode[] = [];
  for (const node of byId.values()) {
    if (node.uplineBrokerId && byId.has(node.uplineBrokerId)) {
      byId.get(node.uplineBrokerId)!.children.push(node);
    } else {
      roots.push(node);
    }
  }
  return roots;
}

export function NetworkNode({ node, depth }: { node: TreeNode; depth: number }) {
  const { t, i18n } = useTranslation('broker');
  // Designation names are DB-sourced bilingual data (name/name_hi columns),
  // not an i18n key -- same explicit language check every other DB-sourced
  // bilingual field in this app needs (established since the AreaInput
  // unit-name fix), since i18next has no way to auto-switch data that
  // isn't routed through t().
  const designationLabel = i18n.language === 'hi' ? (node.designationNameHi ?? node.designationName) : node.designationName;
  return (
    <div>
      <div
        className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-md border border-border px-3 py-2 text-sm"
        style={{ marginLeft: depth * 20 }}
      >
        <span className="font-medium">{node.fullName}</span>
        {designationLabel && <Badge variant="outline">{designationLabel}</Badge>}
        {node.status !== 'ACTIVE' && <Badge variant="secondary">{t(`status.${node.status}`)}</Badge>}
        <span className="text-muted-foreground">
          {t('network.rate')}: {node.currentCommissionRate != null ? `${formatIndianCurrency(node.currentCommissionRate)}${t('network.perSqft')}` : '—'}
        </span>
        <span className="text-muted-foreground">
          {t('network.personalSales')}: {node.personalSuccessfulBookings}
        </span>
        <span className="text-muted-foreground">
          {t('network.teamSales')}: {node.teamSuccessfulBookings}
        </span>
      </div>
      {node.children.length > 0 && (
        <div className="mt-2 space-y-2">
          {node.children.map((child) => (
            <NetworkNode key={child.id} node={child} depth={depth + 1} />
          ))}
        </div>
      )}
    </div>
  );
}

export function NetworkTreePage() {
  const { t } = useTranslation('broker');
  const query = useQuery({ queryKey: ['broker-network'], queryFn: listBrokerNetwork });
  const tree = useMemo(() => buildTree(query.data ?? []), [query.data]);

  return (
    <div>
      <PageHeader title={t('network.title')} />
      {query.isLoading ? (
        <Skeleton className="h-48 w-full rounded-md" />
      ) : query.isError ? (
        <EmptyState icon={<Network className="size-10" />} title={t('network.loadError')} />
      ) : tree.length === 0 ? (
        <EmptyState icon={<Network className="size-10" />} title={t('network.empty')} />
      ) : (
        <div className="space-y-2">
          {tree.map((root) => (
            <NetworkNode key={root.id} node={root} depth={0} />
          ))}
        </div>
      )}

      {tree.length > 0 && (
        <div className="mt-8 space-y-6">
          <NetworkCommissionSummarySection />
          <div>
            <h3 className="mb-3 text-sm font-medium">{t('designation.history.title')}</h3>
            <NetworkHistorySection />
          </div>
        </div>
      )}
    </div>
  );
}

// §29's builder/admin overview: the org-wide equivalent of
// CommissionTreeTab's per-broker summary cards -- earned (by the three
// commission types), released, pending, across every DESIGNATION broker.
function NetworkCommissionSummarySection() {
  const { t } = useTranslation('broker');
  const query = useQuery({ queryKey: ['network-commission-summary'], queryFn: getNetworkCommissionSummary });
  if (!query.data) return null;
  const s = query.data;

  return (
    <div className="rounded-lg border border-border bg-card p-4">
      <h3 className="mb-3 text-sm font-medium">{t('designation.networkSummary.title')}</h3>
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <SummaryStat label={t('designation.networkSummary.totalBrokers')} value={String(s.totalDesignationBrokers)} />
        <SummaryStat label={t('designation.networkSummary.totalEarned')} value={formatIndianCurrency(s.totalCommissionEarned)} />
        <SummaryStat label={t('designation.networkSummary.totalReleased')} value={formatIndianCurrency(s.totalCommissionReleased)} />
        <SummaryStat label={t('designation.networkSummary.totalPending')} value={formatIndianCurrency(s.totalCommissionPending)} />
      </div>
      <div className="mt-3 grid grid-cols-3 gap-3 border-t border-border pt-3 text-sm">
        <SummaryStat label={t('designation.summary.sellingBroker')} value={formatIndianCurrency(s.sellingBrokerEarned)} />
        <SummaryStat label={t('designation.summary.uplineDifferential')} value={formatIndianCurrency(s.uplineDifferentialEarned)} />
        <SummaryStat label={t('designation.summary.sameSlabBonus')} value={formatIndianCurrency(s.sameSlabBonusEarned)} />
      </div>
    </div>
  );
}

function NetworkHistorySection() {
  const query = useQuery({ queryKey: ['network-designation-history'], queryFn: getNetworkDesignationHistory });
  return <DesignationHistoryList rows={query.data ?? []} showBrokerName />;
}
