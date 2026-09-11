import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/data/EmptyState';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { clearDesignationOverride, getDesignationHistory, listBrokerNetwork, listDesignationSlabs } from '../api/brokerApi';
import { buildTree, NetworkNode, type TreeNode } from '../pages/NetworkTreePage';
import { DesignationHistoryList } from './DesignationHistoryList';
import { DesignationOverrideDialog } from './DesignationOverrideDialog';
import type { BrokerResponse } from '../types';

function findNode(nodes: TreeNode[], id: string): TreeNode | undefined {
  for (const node of nodes) {
    if (node.id === id) return node;
    const found = findNode(node.children, id);
    if (found) return found;
  }
  return undefined;
}

function countDescendants(node: TreeNode): number {
  return node.children.reduce((sum, child) => sum + 1 + countDescendants(child), 0);
}

/**
 * 06-BROKER-NETWORK-ENGINE.md §26/§27 (per-broker upline/downline/next-
 * designation/history) + §34 build-order step 8 (the manual-override
 * controls). DESIGNATION brokers only -- BrokerDetailPage only renders
 * this tab for that commission type. Upline/direct-downline/total-downline/
 * subtree are all computed client-side from GET /brokers/network (already
 * small org-wide, see buildTree's own comment) rather than a second,
 * bespoke backend endpoint -- the same "cheaper to assemble client-side
 * than invent a new response shape" call BrokerCommissionSummaryResponse's
 * own javadoc already made explicit.
 */
export function NetworkTab({ broker, canManage }: { broker: BrokerResponse; canManage: boolean }) {
  const { t, i18n } = useTranslation(['broker', 'common']);
  const queryClient = useQueryClient();
  const [overrideOpen, setOverrideOpen] = useState(false);

  const networkQuery = useQuery({ queryKey: ['broker-network'], queryFn: listBrokerNetwork });
  const slabsQuery = useQuery({ queryKey: ['designation-slabs'], queryFn: listDesignationSlabs });
  const historyQuery = useQuery({ queryKey: ['broker-designation-history', broker.id], queryFn: () => getDesignationHistory(broker.id) });

  const tree = useMemo(() => buildTree(networkQuery.data ?? []), [networkQuery.data]);
  const selfNode = useMemo(() => findNode(tree, broker.id), [tree, broker.id]);
  const uplineNode = broker.uplineBrokerId ? (networkQuery.data ?? []).find((n) => n.id === broker.uplineBrokerId) : undefined;
  const directDownlineCount = selfNode?.children.length ?? 0;
  const totalDownlineCount = selfNode ? countDescendants(selfNode) : 0;

  const slabs = [...(slabsQuery.data ?? [])].sort((a, b) => a.sortOrder - b.sortOrder);
  const currentIdx = slabs.findIndex((s) => s.id === broker.currentDesignationId);
  const nextSlab = currentIdx >= 0 ? slabs[currentIdx + 1] : undefined;
  const remaining = nextSlab ? Math.max(0, nextSlab.minTeamSales - broker.teamSuccessfulBookings) : 0;
  const nextSlabName = nextSlab ? (i18n.language === 'hi' ? (nextSlab.nameHi ?? nextSlab.name) : nextSlab.name) : undefined;

  const clearMutation = useMutation({
    mutationFn: () => clearDesignationOverride(broker.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['broker', broker.id] });
      queryClient.invalidateQueries({ queryKey: ['broker-designation-history', broker.id] });
      queryClient.invalidateQueries({ queryKey: ['network-designation-history'] });
      queryClient.invalidateQueries({ queryKey: ['broker-network'] });
    },
  });

  return (
    <div className="space-y-6">
      {canManage && (
        <div className="flex flex-wrap items-center gap-2">
          <Button variant="outline" size="sm" onClick={() => setOverrideOpen(true)}>
            {t('designation.changeDesignation')}
          </Button>
          {broker.designationManuallyOverridden && (
            <Button variant="outline" size="sm" onClick={() => clearMutation.mutate()} disabled={clearMutation.isPending}>
              {t('designation.clearOverride')}
            </Button>
          )}
        </div>
      )}
      {clearMutation.isError && <p className="text-sm text-destructive">{resolveErrorMessage(clearMutation.error)}</p>}

      {broker.designationManuallyOverridden && (
        <div className="rounded-lg border border-amber-500/40 bg-amber-500/10 p-3 text-sm">
          <Badge variant="secondary" className="mb-1">
            {t('designation.overriddenBadge')}
          </Badge>
          <p className="text-muted-foreground">{t('designation.overriddenNote')}</p>
        </div>
      )}

      {!broker.designationManuallyOverridden && (
        <p className="text-sm text-muted-foreground">
          {nextSlab
            ? t('designation.nextDesignation', { remaining, designationName: nextSlabName, rate: nextSlab.ratePerSqft })
            : t('designation.atTopDesignation')}
        </p>
      )}

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
        <div>
          <p className="text-xs text-muted-foreground">{t('designation.upline')}</p>
          <p className="text-sm font-medium">{uplineNode ? uplineNode.fullName : t('designation.noUpline')}</p>
        </div>
        <div>
          <p className="text-xs text-muted-foreground">{t('designation.directDownline')}</p>
          <p className="text-sm font-medium">{directDownlineCount}</p>
        </div>
        <div>
          <p className="text-xs text-muted-foreground">{t('designation.totalDownline')}</p>
          <p className="text-sm font-medium">{totalDownlineCount}</p>
        </div>
      </div>

      <div>
        <h3 className="mb-2 text-sm font-medium">{t('designation.downlineTree')}</h3>
        {!selfNode || selfNode.children.length === 0 ? (
          <EmptyState title={t('designation.noDownline')} />
        ) : (
          <div className="space-y-2">
            {selfNode.children.map((child) => (
              <NetworkNode key={child.id} node={child} depth={0} />
            ))}
          </div>
        )}
      </div>

      <div>
        <h3 className="mb-2 text-sm font-medium">{t('designation.history.title')}</h3>
        <DesignationHistoryList rows={historyQuery.data ?? []} showBrokerName={false} />
      </div>

      <DesignationOverrideDialog brokerId={broker.id} open={overrideOpen} onOpenChange={setOverrideOpen} />
    </div>
  );
}
