import { useTranslation } from 'react-i18next';
import { cellBackgroundStyle } from '../gridVisuals';
import type { PlotStatus } from '../types';

const ITEMS: PlotStatus[] = ['AVAILABLE', 'RESERVED', 'SOLD'];

export function PlotLegend() {
  const { t } = useTranslation('plot');
  return (
    <div className="flex flex-wrap items-center gap-3 text-xs">
      <span className="font-medium text-foreground">{t('grid.legendTitle')}:</span>
      {ITEMS.map((status) => (
        <span key={status} className="flex items-center gap-1.5">
          <span className="size-3.5 rounded-sm" style={cellBackgroundStyle(status)} />
          {t(`status.${status}`)}
        </span>
      ))}
      <span className="flex items-center gap-1.5">
        <span className="size-3.5 rounded-sm" style={cellBackgroundStyle('BLOCKED')} />
        {t('grid.blocked')}
      </span>
    </div>
  );
}
