import { useTranslation } from 'react-i18next';
import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { formatCompactIndianCurrency } from '@/lib/formatters';
import type { RevenueTrendPoint } from '../types';

interface RevenueTrendChartProps {
  data: RevenueTrendPoint[];
}

export function RevenueTrendChart({ data }: RevenueTrendChartProps) {
  const { t } = useTranslation('financial');
  return (
    <div>
      <h3 className="mb-2 text-sm font-medium">{t('revenueTrend.title')}</h3>
      <ResponsiveContainer width="100%" height={220}>
        <BarChart data={data} margin={{ left: 0, right: 8, top: 8, bottom: 0 }}>
          <XAxis dataKey="month" tick={{ fontSize: 11 }} />
          <YAxis tick={{ fontSize: 11 }} tickFormatter={(v: number) => formatCompactIndianCurrency(v)} width={60} />
          <Tooltip formatter={(value) => formatCompactIndianCurrency(Number(value))} />
          <Bar dataKey="collected" fill="var(--color-primary, #2563eb)" radius={[3, 3, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
