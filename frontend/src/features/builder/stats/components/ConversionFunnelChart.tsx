import { useTranslation } from 'react-i18next';
import { ChartCard } from './ChartCard';
import { EmptyChartNote } from './EmptyChartNote';
import type { FunnelStage } from '../types';

// B-15 §7: cohort progression (of leads created in the period, how many
// EVER reached each stage), never current-status counts -- see
// StatsService.conversionFunnel()'s own comment for why. Rendered as
// horizontal bars sized relative to the first stage, matching B-15 §10's
// "horizontal bars preferred... at 360px" -- built that way unconditionally
// rather than as a desktop-only funnel shape plus a separate mobile variant.
export function ConversionFunnelChart({ data }: { data: FunnelStage[] }) {
  const { t } = useTranslation('stats');
  const first = data[0]?.count ?? 0;

  return (
    <ChartCard title={t('charts.conversionFunnel.title')}>
      {first === 0 ? (
        <EmptyChartNote hint={t('empty.funnelHint')} />
      ) : (
        <div className="space-y-3">
          {data.map((stage) => (
            <div key={stage.stage}>
              <div className="mb-1 flex items-center justify-between text-xs">
                <span className="font-medium">{t(`funnelStage.${stage.stage}`, { defaultValue: stage.stage })}</span>
                <span className="text-muted-foreground">
                  {stage.count} {stage.conversionFromFirst !== null && `(${stage.conversionFromFirst}%)`}
                </span>
              </div>
              <div className="h-5 w-full rounded bg-muted">
                <div
                  className="h-5 rounded bg-primary transition-all"
                  style={{ width: `${first === 0 ? 0 : (stage.count / first) * 100}%` }}
                />
              </div>
            </div>
          ))}
        </div>
      )}
    </ChartCard>
  );
}
