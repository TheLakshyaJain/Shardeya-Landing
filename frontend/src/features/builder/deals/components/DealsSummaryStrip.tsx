import { useTranslation } from 'react-i18next';
import { formatCompactIndianCurrency } from '@/lib/formatters';
import type { DealsSummary } from '../types';

interface DealsSummaryStripProps {
  summary: DealsSummary;
}

export function DealsSummaryStrip({ summary }: DealsSummaryStripProps) {
  const { t } = useTranslation('deal');
  return (
    <p className="mb-3 text-sm text-muted-foreground">
      {t('summaryStrip', {
        count: summary.completedCount,
        value: formatCompactIndianCurrency(summary.completedValue),
        cancelled: summary.cancelledCount,
      })}
    </p>
  );
}
