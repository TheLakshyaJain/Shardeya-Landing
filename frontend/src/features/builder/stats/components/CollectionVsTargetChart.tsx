import { useTranslation } from 'react-i18next';
import { Bar, BarChart, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { formatCompactIndianCurrency } from '@/lib/formatters';
import { ChartCard } from './ChartCard';
import { EmptyChartNote } from './EmptyChartNote';
import type { CollectionVsTargetPoint } from '../types';

// B-15 §7: target = collection_target if configured, else
// SUM(payment_schedule.expected_amount) due that month -- no target-setting
// UI ships this round (see CLAUDE.md), so every bar shown here is
// currently the fallback, not an explicitly configured figure.
export function CollectionVsTargetChart({ data }: { data: CollectionVsTargetPoint[] }) {
  const { t } = useTranslation('stats');
  const hasData = data.some((d) => d.actual > 0 || d.target > 0);

  return (
    <ChartCard title={t('charts.collectionVsTarget.title')}>
      {!hasData ? (
        <EmptyChartNote />
      ) : (
        <ResponsiveContainer width="100%" height={220}>
          <BarChart data={data} margin={{ left: 0, right: 8, top: 8, bottom: 0 }}>
            <XAxis dataKey="month" tick={{ fontSize: 11 }} />
            <YAxis tick={{ fontSize: 11 }} width={50} tickFormatter={(v: number) => formatCompactIndianCurrency(v)} />
            <Tooltip formatter={(v) => formatCompactIndianCurrency(Number(v))} />
            <Legend wrapperStyle={{ fontSize: 11 }} formatter={(v) => t(`charts.collectionVsTarget.${v}`)} />
            <Bar dataKey="actual" fill="#16a34a" radius={[3, 3, 0, 0]} />
            <Bar dataKey="target" fill="#94a3b8" radius={[3, 3, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      )}
    </ChartCard>
  );
}
