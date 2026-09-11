import { useTranslation } from 'react-i18next';

// B-15 §10: "new org with no data -> informative empty state explaining
// what will appear, not an empty axis" -- one shared note used by every
// chart below instead of each rendering its own blank Recharts canvas.
export function EmptyChartNote({ hint }: { hint?: string }) {
  const { t } = useTranslation('stats');
  return (
    <div className="flex h-[180px] flex-col items-center justify-center text-center text-xs text-muted-foreground">
      <p>{t('empty.noData')}</p>
      {hint && <p className="mt-1">{hint}</p>}
    </div>
  );
}
