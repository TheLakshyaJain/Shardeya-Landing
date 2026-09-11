import { useTranslation } from 'react-i18next';
import { Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import { ChartCard } from './ChartCard';
import { EmptyChartNote } from './EmptyChartNote';
import type { BreakdownSlice } from '../types';

const PALETTE = ['#2563eb', '#16a34a', '#f59e0b', '#dc2626', '#7c3aed', '#0891b2', '#db2777', '#65a30d', '#ea580c', '#4338ca'];

export function LeadSourcePieChart({ data }: { data: BreakdownSlice[] }) {
  const { t } = useTranslation('stats');
  const total = data.reduce((sum, d) => sum + d.count, 0);

  return (
    <ChartCard title={t('charts.leadSource.title')}>
      {total === 0 ? (
        <EmptyChartNote />
      ) : (
        <div className="relative">
          <ResponsiveContainer width="100%" height={200}>
            <PieChart>
              <Pie data={data} dataKey="count" nameKey="label" innerRadius={50} outerRadius={80}>
                {data.map((d, i) => <Cell key={d.label} fill={PALETTE[i % PALETTE.length]} />)}
              </Pie>
              <Tooltip />
              <Legend verticalAlign="bottom" height={24} formatter={(v: string) => t(`leadSource.${v}`, { defaultValue: v })} />
            </PieChart>
          </ResponsiveContainer>
          <div className="pointer-events-none absolute inset-x-0 top-[76px] text-center">
            <div className="text-lg font-semibold">{total}</div>
            <div className="text-[10px] text-muted-foreground">{t('charts.leadSource.total')}</div>
          </div>
        </div>
      )}
    </ChartCard>
  );
}
