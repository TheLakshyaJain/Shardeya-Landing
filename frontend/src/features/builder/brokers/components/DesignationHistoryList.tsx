import { useTranslation } from 'react-i18next';
import { EmptyState } from '@/components/data/EmptyState';
import { Badge } from '@/components/ui/badge';
import type { DesignationHistoryResponse } from '../types';

/**
 * 06-BROKER-NETWORK-ENGINE.md §26/§27/§29 -- "promotion history", shared
 * between the per-broker Network tab and NetworkTreePage's org-wide
 * overview (showBrokerName distinguishes the two). Card rows, not a
 * <Table> -- the same reflow-at-any-width choice CommissionTreeTab's own
 * javadoc already documents (this is now the 4th list in this codebase to
 * make that same call up front rather than discover the 360px break
 * after the fact).
 */
export function DesignationHistoryList({ rows, showBrokerName }: { rows: DesignationHistoryResponse[]; showBrokerName: boolean }) {
  const { t, i18n } = useTranslation('broker');

  if (rows.length === 0) {
    return <EmptyState title={t('designation.history.empty')} />;
  }

  return (
    <ul className="space-y-2">
      {rows.map((row) => {
        const previousName = i18n.language === 'hi' ? (row.previousDesignationNameHi ?? row.previousDesignationName) : row.previousDesignationName;
        const newName = i18n.language === 'hi' ? (row.newDesignationNameHi ?? row.newDesignationName) : row.newDesignationName;
        return (
          <li key={row.id} className="rounded-lg border border-border bg-card p-3 shadow-sm">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div className="min-w-0">
                {showBrokerName && <p className="text-sm font-medium text-foreground">{row.brokerName ?? '—'}</p>}
                <p className="text-sm">
                  {previousName
                    ? t('designation.history.changedFromTo', { from: previousName, to: newName, rate: row.newRate })
                    : t('designation.history.firstDesignation', { to: newName, rate: row.newRate })}
                </p>
                {row.reason && <p className="text-xs text-muted-foreground">{row.reason}</p>}
                <p className="text-xs text-muted-foreground">
                  {row.effectiveAt.slice(0, 10)}
                  {row.changedByName && ` — ${t('designation.history.by', { name: row.changedByName })}`}
                </p>
              </div>
              <Badge variant={row.changeType === 'MANUAL' ? 'secondary' : 'outline'}>
                {t(`designation.history.changeType.${row.changeType}`)}
              </Badge>
            </div>
          </li>
        );
      })}
    </ul>
  );
}
