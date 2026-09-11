import { useTranslation } from 'react-i18next';
import { formatIndianCurrency } from '@/lib/formatters';
import type { PaymentSummaryResponse } from '../types';

// B-05 §6: "the single most-looked-at widget in the product" -- Total /
// Paid / Balance with a progress bar.
export function PaymentSummaryPanel({ summary }: { summary: PaymentSummaryResponse }) {
  const { t } = useTranslation('payment');
  const pct = summary.dealValue > 0 ? Math.min(100, Math.max(0, (summary.totalPaid / summary.dealValue) * 100)) : 0;

  return (
    <div className="space-y-2 rounded-md border border-border bg-card p-3">
      <div className={`grid gap-2 text-center text-sm ${summary.totalWaived > 0 ? 'grid-cols-4' : 'grid-cols-3'}`}>
        <div>
          <p className="text-xs text-muted-foreground">{t('summary.dealValue')}</p>
          <p className="font-semibold">{formatIndianCurrency(summary.dealValue)}</p>
        </div>
        <div>
          <p className="text-xs text-muted-foreground">{t('summary.totalPaid')}</p>
          <p className="font-semibold text-green-600 dark:text-green-500">{formatIndianCurrency(summary.totalPaid)}</p>
        </div>
        {/* Only shown once something has actually been waived -- keeps the
            panel at its usual 3 columns for the vast majority of sales with
            no waiver, per the "reason a real bug was found" fix: balance
            due now correctly excludes this, so it needs its own visible line
            or a waiver would look like it silently vanished instead. */}
        {summary.totalWaived > 0 && (
          <div>
            <p className="text-xs text-muted-foreground">{t('summary.totalWaived')}</p>
            <p className="font-semibold text-amber-600 dark:text-amber-500">{formatIndianCurrency(summary.totalWaived)}</p>
          </div>
        )}
        <div>
          <p className="text-xs text-muted-foreground">{t('summary.balanceDue')}</p>
          <p className={`font-semibold ${summary.balanceDue < 0 ? 'text-destructive' : ''}`}>{formatIndianCurrency(summary.balanceDue)}</p>
        </div>
      </div>
      <div className="h-2 w-full overflow-hidden rounded-full bg-muted">
        <div className="h-full rounded-full bg-primary transition-all" style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}
