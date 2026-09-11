import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { formatCompactIndianCurrency } from '@/lib/formatters';
import { ChartCard } from './ChartCard';
import { EmptyChartNote } from './EmptyChartNote';
import type { BrokerRanking } from '../types';

// B-15 §6 "horizontal bar, top 5" -- a Recharts BarChart with layout="vertical"
// (Recharts' own naming: bars grow horizontally when layout="vertical").
export function TopBrokersChart({ data }: { data: BrokerRanking[] }) {
  const { t } = useTranslation('stats');
  const navigate = useNavigate();

  return (
    <ChartCard title={t('charts.topBrokers.title')}>
      {data.length === 0 ? (
        <EmptyChartNote hint={t('empty.topBrokersHint')} />
      ) : (
        <ResponsiveContainer width="100%" height={Math.max(160, data.length * 36)}>
          <BarChart data={data} layout="vertical" margin={{ left: 8, right: 24, top: 4, bottom: 4 }}>
            <CartesianGrid horizontal={false} strokeDasharray="3 3" />
            <XAxis type="number" tick={{ fontSize: 11 }} tickFormatter={(v: number) => formatCompactIndianCurrency(v)} />
            <YAxis type="category" dataKey="brokerName" tick={{ fontSize: 11 }} width={100} />
            <Tooltip formatter={(v) => formatCompactIndianCurrency(Number(v))} />
            <Bar
              dataKey="revenueGenerated"
              fill="#7c3aed"
              radius={[0, 3, 3, 0]}
              cursor="pointer"
              onClick={(e) => navigate(`/builder/brokers/${(e as unknown as BrokerRanking).brokerPartnerId}`)}
            />
          </BarChart>
        </ResponsiveContainer>
      )}
    </ChartCard>
  );
}
