import { useTranslation } from 'react-i18next';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { formatCompactIndianCurrency } from '@/lib/formatters';
import { ChartCard } from './ChartCard';
import { EmptyChartNote } from './EmptyChartNote';
import type { BreakdownSlice } from '../types';

export function RevenueByProjectChart({ data }: { data: BreakdownSlice[] }) {
  const { t } = useTranslation('stats');
  const hasData = data.some((d) => (d.value ?? 0) > 0);

  return (
    <ChartCard title={t('charts.revenueByProject.title')}>
      {!hasData ? (
        <EmptyChartNote />
      ) : (
        <ResponsiveContainer width="100%" height={Math.max(160, data.length * 36)}>
          <BarChart data={data} layout="vertical" margin={{ left: 8, right: 24, top: 4, bottom: 4 }}>
            <CartesianGrid horizontal={false} strokeDasharray="3 3" />
            <XAxis type="number" tick={{ fontSize: 11 }} tickFormatter={(v: number) => formatCompactIndianCurrency(v)} />
            <YAxis type="category" dataKey="label" tick={{ fontSize: 11 }} width={100} />
            <Tooltip formatter={(v) => formatCompactIndianCurrency(Number(v))} />
            <Bar dataKey="value" fill="#0891b2" radius={[0, 3, 3, 0]} />
          </BarChart>
        </ResponsiveContainer>
      )}
    </ChartCard>
  );
}
