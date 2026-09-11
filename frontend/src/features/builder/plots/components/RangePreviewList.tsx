import { useTranslation } from 'react-i18next';
import { TriangleAlert } from 'lucide-react';
import type { QuickCreatePreviewResponse } from '../types';

interface RangePreviewListProps {
  preview: QuickCreatePreviewResponse;
}

// B-06 §7: "the generated list of plot numbers is shown in full before
// anything is written — collisions ... flagged individually." A typo in
// `end` should never silently create 700 plots instead of 75, so this
// always renders the FULL list, not just a truncated sample.
export function RangePreviewList({ preview }: RangePreviewListProps) {
  const { t } = useTranslation('plot');
  const collisionCount = preview.plotNumbers.filter((p) => p.collidesWithExisting || p.collidesWithinRequest).length;

  return (
    <div className="space-y-2 rounded-md border border-border p-3">
      <div className="flex items-center justify-between text-sm">
        <p className="font-medium">{t('quickCreate.preview.summary', { count: preview.totalCount })}</p>
        {!preview.withinQuota && (
          <span className="text-xs font-medium text-destructive">
            {t('quickCreate.preview.quotaWarning', { used: preview.quotaUsed, limit: preview.quotaLimit })}
          </span>
        )}
      </div>
      {!preview.withinDeclaredCount && (
        <div role="alert" className="flex items-start gap-2 rounded-md border border-destructive/40 bg-destructive/10 p-2 text-sm text-destructive">
          <TriangleAlert className="mt-0.5 size-4 shrink-0" />
          <span>
            {t('quickCreate.preview.declaredCountWarning', {
              declared: preview.declaredPlotCount,
              current: preview.currentPlotCount,
            })}
          </span>
        </div>
      )}
      {collisionCount > 0 && (
        <div role="alert" className="flex items-start gap-2 rounded-md border border-destructive/40 bg-destructive/10 p-2 text-sm text-destructive">
          <TriangleAlert className="mt-0.5 size-4 shrink-0" />
          <span>{t('quickCreate.preview.collisionWarning', { count: collisionCount })}</span>
        </div>
      )}
      <ul className="grid max-h-48 grid-cols-3 gap-x-3 gap-y-1 overflow-y-auto text-sm sm:grid-cols-4 md:grid-cols-6">
        {preview.plotNumbers.map((p, i) => (
          <li
            key={`${p.plotNumber}-${i}`}
            className={
              p.collidesWithExisting || p.collidesWithinRequest
                ? 'rounded bg-destructive/10 px-1.5 py-0.5 font-medium text-destructive'
                : 'px-1.5 py-0.5'
            }
            title={
              p.collidesWithExisting
                ? t('quickCreate.preview.collidesWithExisting')
                : p.collidesWithinRequest
                  ? t('quickCreate.preview.collidesWithinRequest')
                  : undefined
            }
          >
            {p.plotNumber}
          </li>
        ))}
      </ul>
    </div>
  );
}
