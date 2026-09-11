import { useTranslation } from 'react-i18next';
import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { formatCompactIndianCurrency } from '@/lib/formatters';
import { ChartCard } from './ChartCard';
import { EmptyChartNote } from './EmptyChartNote';
import type { MonthPoint } from '../types';

export function MonthlyRevenueChart({ data }: { data: MonthPoint[] }) {
  const { t } = useTranslation('stats');
  const hasData = data.some((d) => d.value > 0);

  return (
    <ChartCard title={t('charts.monthlyRevenue.title')}>
      {!hasData ? (
        <EmptyChartNote />
      ) : (
        <ResponsiveContainer width="100%" height={200}>
          <BarChart data={data} margin={{ left: 0, right: 8, top: 8, bottom: 0 }}>
            <XAxis dataKey="month" tick={{ fontSize: 11 }} />
            <YAxis tick={{ fontSize: 11 }} width={50} tickFormatter={(v: number) => formatCompactIndianCurrency(v)} />
            <Tooltip formatter={(v) => formatCompactIndianCurrency(Number(v))} />
            <Bar dataKey="value" fill="#16a34a" radius={[3, 3, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      )}
    </ChartCard>
  );
}
