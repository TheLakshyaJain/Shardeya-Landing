import { useTranslation } from 'react-i18next';
import { Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { formatCompactIndianCurrency } from '@/lib/formatters';
import { ChartCard } from './ChartCard';
import { EmptyChartNote } from './EmptyChartNote';
import type { MonthPoint } from '../types';

export function OverdueTrendChart({ data }: { data: MonthPoint[] }) {
  const { t } = useTranslation('stats');
  const hasData = data.some((d) => d.value > 0);

  return (
    <ChartCard title={t('charts.overdueTrend.title')}>
      {!hasData ? (
        <EmptyChartNote hint={t('empty.overdueTrendHint')} />
      ) : (
        <ResponsiveContainer width="100%" height={200}>
          <LineChart data={data} margin={{ left: 0, right: 8, top: 8, bottom: 0 }}>
            <XAxis dataKey="month" tick={{ fontSize: 11 }} />
            <YAxis tick={{ fontSize: 11 }} width={50} tickFormatter={(v: number) => formatCompactIndianCurrency(v)} />
            <Tooltip formatter={(v) => formatCompactIndianCurrency(Number(v))} />
            <Line type="monotone" dataKey="value" stroke="#dc2626" strokeWidth={2} dot={{ r: 3 }} />
          </LineChart>
        </ResponsiveContainer>
      )}
    </ChartCard>
  );
}
