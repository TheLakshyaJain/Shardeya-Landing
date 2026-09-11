import { useTranslation } from 'react-i18next';
import { Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import { formatCompactIndianCurrency } from '@/lib/formatters';
import type { ModeBreakdown } from '../types';

const COLORS: Record<string, string> = {
  CASH: '#16a34a',
  CHEQUE: '#f59e0b',
  BANK_TRANSFER: '#2563eb',
  UPI: '#7c3aed',
  DD: '#0891b2',
};

interface PaymentModeBreakdownProps {
  data: ModeBreakdown[];
}

// B-08 §6: "cash vs digital, which builders watch closely."
export function PaymentModeBreakdown({ data }: PaymentModeBreakdownProps) {
  const { t } = useTranslation(['financial', 'payment']);
  const chartData = data.filter((d) => d.amount !== 0).map((d) => ({ name: t(`mode.${d.mode}`, { ns: 'payment' }), value: Math.abs(d.amount), mode: d.mode }));

  if (chartData.length === 0) {
    return <p className="text-sm text-muted-foreground">{t('modeBreakdown.empty')}</p>;
  }

  return (
    <div>
      <h3 className="mb-2 text-sm font-medium">{t('modeBreakdown.title')}</h3>
      <ResponsiveContainer width="100%" height={220}>
        <PieChart>
          <Pie data={chartData} dataKey="value" nameKey="name" innerRadius={40} outerRadius={80}>
            {chartData.map((entry) => (
              <Cell key={entry.mode} fill={COLORS[entry.mode] ?? '#94a3b8'} />
            ))}
          </Pie>
          <Tooltip formatter={(value) => formatCompactIndianCurrency(Number(value))} />
          <Legend wrapperStyle={{ fontSize: 12 }} />
        </PieChart>
      </ResponsiveContainer>
    </div>
  );
}
