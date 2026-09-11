import { useQueries } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { getPlot } from '../api/plotApi';

interface UnplacedPlotsTrayProps {
  unplacedIds: string[];
  armedId: string | null;
  onArm: (id: string | null) => void;
}

// The GridLayoutEditor "click an unplaced plot, then click an open cell"
// placement flow (see PlotGridCanvas/PlotGridDom's armedPlacement prop) --
// chosen over drag-and-drop since a <canvas> has no native drop target and
// custom pointer-drag physics on canvas would be substantial extra surface
// area for a gain that click-to-place already delivers, including on touch.
export function UnplacedPlotsTray({ unplacedIds, armedId, onArm }: UnplacedPlotsTrayProps) {
  const { t } = useTranslation('plot');

  const queries = useQueries({
    queries: unplacedIds.map((id) => ({ queryKey: ['plot', id], queryFn: () => getPlot(id) })),
  });

  if (unplacedIds.length === 0) {
    return <p className="text-sm text-muted-foreground">{t('grid.unplacedEmpty')}</p>;
  }

  return (
    <div className="space-y-2">
      <p className="text-sm font-medium text-foreground">{t('grid.unplacedCount', { count: unplacedIds.length })}</p>
      <div className="flex flex-wrap gap-2">
        {unplacedIds.map((id, i) => {
          const plot = queries[i]?.data;
          const armed = armedId === id;
          return (
            <button
              key={id}
              type="button"
              onClick={() => onArm(armed ? null : id)}
              className={`rounded-md border px-2 py-1 text-xs font-medium transition-colors ${
                armed ? 'border-primary bg-primary text-primary-foreground' : 'border-border bg-card hover:bg-accent'
              }`}
            >
              {plot?.plotNumber ?? '...'}
            </button>
          );
        })}
      </div>
      {armedId && <p className="text-xs text-muted-foreground">{t('grid.dragHint')}</p>}
    </div>
  );
}
