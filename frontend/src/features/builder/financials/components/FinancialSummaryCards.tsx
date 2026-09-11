import { useTranslation } from 'react-i18next';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { formatCompactIndianCurrency } from '@/lib/formatters';
import type { FinancialSummaryResponse } from '../types';

interface FinancialSummaryCardsProps {
  summary: FinancialSummaryResponse;
}

// B-08 §14.1's six cards, rendered 2-per-row at 360px (CLAUDE.md §24.2 / this
// project's own established DashboardGrid convention).
export function FinancialSummaryCards({ summary }: FinancialSummaryCardsProps) {
  const { t } = useTranslation('financial');

  const cards: { key: string; value: number; danger?: boolean }[] = [
    { key: 'totalRevenueAllTime', value: summary.totalRevenueAllTime },
    { key: 'revenueThisMonth', value: summary.revenueThisMonth, danger: summary.revenueThisMonth < 0 },
    { key: 'revenueThisYear', value: summary.revenueThisYear },
    { key: 'pendingCollections', value: summary.pendingCollections },
    { key: 'overdueInstalments', value: summary.overdueInstalments.amount, danger: summary.overdueInstalments.amount > 0 },
    { key: 'totalBrokerCommissionPaid', value: summary.totalBrokerCommissionPaid },
  ];

  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
      {cards.map((c) => (
        <Card key={c.key}>
          <CardHeader className="pb-1">
            <CardTitle className="text-xs font-medium text-muted-foreground">{t(`summary.${c.key}`)}</CardTitle>
          </CardHeader>
          <CardContent>
            <p className={`text-lg font-semibold ${c.danger ? 'text-destructive' : ''}`} title={String(c.value)}>
              {formatCompactIndianCurrency(c.value)}
            </p>
            {c.key === 'overdueInstalments' && (
              <p className="text-xs text-muted-foreground">
                {t('summary.overdueCount', { count: summary.overdueInstalments.count })}
              </p>
            )}
          </CardContent>
        </Card>
      ))}
      {summary.scoped && (
        <p className="col-span-2 text-xs text-muted-foreground sm:col-span-3">
          {t('summary.scopedNote', { scoped: summary.scopedProjectCount, total: summary.totalProjectCount })}
        </p>
      )}
    </div>
  );
}
