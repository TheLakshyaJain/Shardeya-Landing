import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import { ChartCard } from './ChartCard';
import { EmptyChartNote } from './EmptyChartNote';
import type { BreakdownSlice } from '../types';

const COLORS: Record<string, string> = { AVAILABLE: '#16a34a', RESERVED: '#f59e0b', SOLD: '#2563eb' };

// B-15 §10 "pies become donuts with a centre total" at 360px -- built as a
// donut unconditionally (innerRadius set always) rather than a separate
// mobile-only variant, since a donut reads fine at desktop width too.
export function PlotStatusPieChart({ data, projectId }: { data: BreakdownSlice[]; projectId?: string }) {
  const { t } = useTranslation('stats');
  const navigate = useNavigate();
  const total = data.reduce((sum, d) => sum + d.count, 0);

  return (
    <ChartCard title={t('charts.plotStatus.title')}>
      {total === 0 ? (
        <EmptyChartNote hint={t('empty.plotStatusHint')} />
      ) : (
        <div className="relative">
          <ResponsiveContainer width="100%" height={200}>
            <PieChart>
              <Pie
                data={data}
                dataKey="count"
                nameKey="label"
                innerRadius={50}
                outerRadius={80}
                cursor="pointer"
                onClick={(e) => {
                  const slice = e as unknown as BreakdownSlice;
                  navigate(`/builder/reports/PLOT_INVENTORY?status=${slice.label}${projectId ? `&projectId=${projectId}` : ''}`);
                }}
              >
                {data.map((d) => <Cell key={d.label} fill={COLORS[d.label] ?? '#94a3b8'} />)}
              </Pie>
              <Tooltip />
              <Legend verticalAlign="bottom" height={24} formatter={(v: string) => t(`plotStatus.${v}`, { defaultValue: v })} />
            </PieChart>
          </ResponsiveContainer>
          <div className="pointer-events-none absolute inset-x-0 top-[76px] text-center">
            <div className="text-lg font-semibold">{total}</div>
            <div className="text-[10px] text-muted-foreground">{t('charts.plotStatus.total')}</div>
          </div>
        </div>
      )}
    </ChartCard>
  );
}
