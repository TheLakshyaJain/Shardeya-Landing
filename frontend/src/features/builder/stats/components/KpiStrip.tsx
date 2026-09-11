import { useTranslation } from 'react-i18next';
import { formatIndianCurrency } from '@/lib/formatters';
import type { StatsOverviewResponse } from '../types';

interface KpiStripProps {
  data: StatsOverviewResponse;
}

// B-15 §6 KpiStrip: conversion rate, avg deal value, avg days-to-close,
// collection efficiency. §10 "division by zero -> shown as '—', never NaN
// or 0%" -- every ratio here is already null from the backend rather than
// 0 when its denominator was zero (StatsService's own CASE WHEN guards),
// so this only needs to render null as the dash, never compute a ratio itself.
export function KpiStrip({ data }: KpiStripProps) {
  const { t } = useTranslation('stats');

  const cards = [
    { label: t('kpi.conversionRate'), value: data.conversionRate !== null ? `${data.conversionRate}%` : '—' },
    { label: t('kpi.avgDealValue'), value: data.avgDealValue !== null ? formatIndianCurrency(data.avgDealValue) : '—' },
    { label: t('kpi.avgDaysToClose'), value: data.avgDaysToClose !== null ? t('kpi.days', { count: Math.round(data.avgDaysToClose) }) : '—' },
    { label: t('kpi.collectionEfficiency'), value: data.collectionEfficiency !== null ? `${data.collectionEfficiency}%` : '—' },
  ];

  return (
    <div>
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {cards.map((c) => (
          <div key={c.label} className="rounded-lg border border-border bg-card p-3">
            <div className="text-xs text-muted-foreground">{c.label}</div>
            <div className="mt-1 text-lg font-semibold">{c.value}</div>
          </div>
        ))}
      </div>
      {data.dataAsOf && (
        <p className="mt-2 text-xs text-muted-foreground">
          {t('kpi.asOf', { time: new Date(data.dataAsOf).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' }) })}
        </p>
      )}
    </div>
  );
}
