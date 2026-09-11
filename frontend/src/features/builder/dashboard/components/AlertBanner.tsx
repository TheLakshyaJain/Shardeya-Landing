import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { AlertTriangle } from 'lucide-react';
import { formatCompactIndianCurrency } from '@/lib/formatters';
import type { DashboardAlert } from '../types';

interface AlertBannerProps {
  alerts: DashboardAlert[];
}

export function AlertBanner({ alerts }: AlertBannerProps) {
  const { t } = useTranslation('dashboard');
  if (alerts.length === 0) return null;

  return (
    <div className="mb-4 space-y-2">
      {alerts.map((a) => (
        <Link
          key={a.type}
          to="/builder/tracker?tab=collections&range=overdue"
          className="flex items-center gap-2 rounded-md border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive"
        >
          <AlertTriangle className="size-4 shrink-0" />
          <span>{t(`alerts.${a.type}`, { count: a.count, amount: formatCompactIndianCurrency(a.amount) })}</span>
        </Link>
      ))}
    </div>
  );
}
