import { cellBackgroundStyle, STATUS_CODE_TO_KEY } from '../gridVisuals';
import type { GridResponse, PlotFilter } from '../types';

interface PlotGridDomProps {
  grid: GridResponse;
  filter: PlotFilter;
  searchTerm?: string;
  onSelectPlotNumber: (plotNumber: string) => void;
  armedPlacement?: boolean;
  onPlaceArmedPlot?: (gridRow: number, gridCol: number) => void;
}

// Simple DOM/CSS-grid renderer for small projects (<=400 plots) -- no
// canvas, no custom pan/zoom (native scroll is enough at this scale), same
// colour+hatch visual language as PlotGridCanvas via gridVisuals.
export function PlotGridDom({ grid, filter, searchTerm, onSelectPlotNumber, armedPlacement, onPlaceArmedPlot }: PlotGridDomProps) {
  const blockedSet = new Set(grid.blocked.map(([r, c]) => `${r}:${c}`));
  const plotByCell = new Map(grid.plots.map((p) => [`${p[0]}:${p[1]}`, p]));
  const showFilter = Object.values(filter).some((v) => v !== undefined && v !== '' && v !== false);

  const cells = [];
  for (let row = 0; row < grid.rows; row++) {
    for (let col = 0; col < grid.cols; col++) {
      const key = `${row}:${col}`;
      const tuple = plotByCell.get(key);
      const isBlocked = blockedSet.has(key);

      // Empty and blocked cells have no plot to click through to, but they
      // still need to respond to clicks while armed (block-mode toggling, or
      // placing an unplaced plot onto an open/blocked cell) -- the canvas
      // renderer already handles this correctly since it computes row/col
      // from raw click coordinates over the whole canvas rather than
      // per-cell elements, but this DOM renderer rendered these two cases as
      // plain non-interactive <div>s with no onClick at all, so armed clicks
      // here silently did nothing. Only surfaced once a real project's plot
      // count stayed under CANVAS_THRESHOLD (the common case for manual
      // testing) -- e2e coverage only ever exercised the >400-plot canvas
      // path for grid placement.
      if (!tuple && !isBlocked) {
        cells.push(
          armedPlacement && onPlaceArmedPlot ? (
            <button
              key={key}
              type="button"
              onClick={() => onPlaceArmedPlot(row, col)}
              className="cursor-crosshair rounded-sm border border-dashed border-border/50"
            />
          ) : (
            <div key={key} className="rounded-sm border border-dashed border-border/50" />
          ),
        );
        continue;
      }
      if (isBlocked) {
        cells.push(
          armedPlacement && onPlaceArmedPlot ? (
            <button
              key={key}
              type="button"
              onClick={() => onPlaceArmedPlot(row, col)}
              style={cellBackgroundStyle('BLOCKED')}
              className="cursor-crosshair rounded-sm"
            />
          ) : (
            <div key={key} className="rounded-sm" style={cellBackgroundStyle('BLOCKED')} />
          ),
        );
        continue;
      }

      const [, , plotNumber, statusCode, sizeSqft, isHot] = tuple!;
      const status = STATUS_CODE_TO_KEY[statusCode];
      const isSearchMatch = !!searchTerm && plotNumber.toLowerCase() === searchTerm.toLowerCase();
      const dim =
        showFilter &&
        ((filter.status && filter.status !== status) ||
          (filter.sizeMin !== undefined && sizeSqft < filter.sizeMin) ||
          (filter.sizeMax !== undefined && sizeSqft > filter.sizeMax) ||
          (filter.isHot && isHot !== 1));

      cells.push(
        <button
          key={key}
          type="button"
          onClick={() => (armedPlacement && onPlaceArmedPlot ? onPlaceArmedPlot(row, col) : onSelectPlotNumber(plotNumber))}
          style={cellBackgroundStyle(status)}
          className={`flex items-center justify-center rounded-sm text-[10px] font-medium text-white transition-opacity ${dim ? 'opacity-25' : ''} ${isSearchMatch ? 'ring-2 ring-yellow-400 ring-offset-1' : ''} ${armedPlacement ? 'cursor-crosshair' : 'cursor-pointer'}`}
          title={plotNumber}
        >
          {plotNumber}
        </button>,
      );
    }
  }

  return (
    <div
      className="grid gap-0.5 overflow-auto rounded-md border border-border bg-muted/20 p-2"
      style={{ gridTemplateColumns: `repeat(${grid.cols}, minmax(28px, 1fr))`, gridAutoRows: '28px' }}
    >
      {cells}
    </div>
  );
}
