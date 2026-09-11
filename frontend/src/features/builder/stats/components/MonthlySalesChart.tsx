import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { ChartCard } from './ChartCard';
import { EmptyChartNote } from './EmptyChartNote';
import type { MonthPoint } from '../types';

interface MonthlySalesChartProps {
  data: MonthPoint[];
  projectId?: string;
}

// B-15 §7 "every chart is drill-through: clicking a bar navigates to the
// filtered list behind it" -- here, the Sales report for that month.
export function MonthlySalesChart({ data, projectId }: MonthlySalesChartProps) {
  const { t } = useTranslation('stats');
  const navigate = useNavigate();
  const hasData = data.some((d) => d.count > 0);

  return (
    <ChartCard title={t('charts.monthlySales.title')}>
      {!hasData ? (
        <EmptyChartNote hint={t('empty.monthlySalesHint')} />
      ) : (
        <ResponsiveContainer width="100%" height={200}>
          <BarChart data={data} margin={{ left: 0, right: 8, top: 8, bottom: 0 }}>
            <XAxis dataKey="month" tick={{ fontSize: 11 }} />
            <YAxis tick={{ fontSize: 11 }} width={30} allowDecimals={false} />
            <Tooltip />
            <Bar
              dataKey="count"
              fill="var(--color-primary, #2563eb)"
              radius={[3, 3, 0, 0]}
              cursor="pointer"
              onClick={(e) => {
                // Recharts' own onClick event shape doesn't line up cleanly
                // with the chart's data type across versions -- cast at the
                // boundary rather than fight its generics.
                const point = e as unknown as MonthPoint;
                if (!point?.month) return;
                // SALES only supports a from/to range, not a single-month key
                // -- bound the range to exactly this month's calendar days.
                const [y, m] = point.month.split('-').map(Number);
                const from = `${point.month}-01`;
                const to = new Date(y, m, 0).toISOString().slice(0, 10);
                const params = new URLSearchParams({ from, to, ...(projectId ? { projectId } : {}) });
                navigate(`/builder/reports/SALES?${params.toString()}`);
              }}
            />
          </BarChart>
        </ResponsiveContainer>
      )}
    </ChartCard>
  );
}
