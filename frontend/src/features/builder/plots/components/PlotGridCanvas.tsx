import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import { Minus, Plus, RotateCcw } from 'lucide-react';
import { paintCell, STATUS_CODE_TO_KEY } from '../gridVisuals';
import { useGridViewport } from '../useGridViewport';
import type { GridPlotTuple, GridResponse, PlotFilter } from '../types';

export const CELL_SIZE = 28;

interface PlotGridCanvasProps {
  grid: GridResponse;
  filter: PlotFilter;
  searchTerm?: string;
  onSelectPlotNumber: (plotNumber: string) => void;
  armedPlacement?: boolean;
  onPlaceArmedPlot?: (gridRow: number, gridCol: number) => void;
}

function matchesFilter(tuple: GridPlotTuple, filter: PlotFilter): boolean {
  const [, , , statusCode, sizeSqft, isHotFlag] = tuple;
  const status = STATUS_CODE_TO_KEY[statusCode];
  if (filter.status && filter.status !== status) return false;
  if (filter.sizeMin !== undefined && sizeSqft < filter.sizeMin) return false;
  if (filter.sizeMax !== undefined && sizeSqft > filter.sizeMax) return false;
  if (filter.isHot && isHotFlag !== 1) return false;
  return true;
}

// Grid tuples are deliberately compact — [row, col, plotNumber, statusCode,
// sizeSqft, isHot] only (see backend GridResponse javadoc) — so facing,
// price and corner/garden filters can't be evaluated here at all; those only
// narrow the Plots List tab, which calls the real filtered /plots endpoint.
// Status/size/hot/search are the only filter dimensions this view can honour.
const hasUnsupportedFilter = (filter: PlotFilter) =>
  filter.facing !== undefined || filter.priceMin !== undefined || filter.priceMax !== undefined || filter.isCorner || filter.isGarden;

export function PlotGridCanvas({ grid, filter, searchTerm, onSelectPlotNumber, armedPlacement, onPlaceArmedPlot }: PlotGridCanvasProps) {
  const { t } = useTranslation('plot');
  const containerRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [containerSize, setContainerSize] = useState({ width: 0, height: 0 });
  const lastCenteredSearch = useRef<string | undefined>(undefined);

  const { viewport, setViewport, onPointerDown, onPointerMove, onPointerUp, onPointerCancel, onWheel, reset, zoomBy, wasDrag } =
    useGridViewport(containerRef);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const ro = new ResizeObserver((entries) => {
      const entry = entries[0];
      if (entry) setContainerSize({ width: entry.contentRect.width, height: entry.contentRect.height });
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  // Center the viewport on a searched plot number the first time it matches
  // (not on every render -- lastCenteredSearch tracks what we've already panned to).
  useEffect(() => {
    if (!searchTerm || searchTerm === lastCenteredSearch.current || containerSize.width === 0) return;
    const match = grid.plots.find((p) => p[2].toLowerCase() === searchTerm.toLowerCase());
    if (!match) return;
    lastCenteredSearch.current = searchTerm;
    const [row, col] = match;
    setViewport((v) => ({
      scale: v.scale,
      offsetX: containerSize.width / 2 - (col + 0.5) * CELL_SIZE * v.scale,
      offsetY: containerSize.height / 2 - (row + 0.5) * CELL_SIZE * v.scale,
    }));
  }, [searchTerm, grid.plots, containerSize, setViewport]);

  useEffect(() => {
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext('2d');
    if (!canvas || !ctx || containerSize.width === 0) return;

    const dpr = window.devicePixelRatio || 1;
    canvas.width = containerSize.width * dpr;
    canvas.height = containerSize.height * dpr;
    canvas.style.width = `${containerSize.width}px`;
    canvas.style.height = `${containerSize.height}px`;

    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, containerSize.width, containerSize.height);
    ctx.save();
    ctx.translate(viewport.offsetX, viewport.offsetY);
    ctx.scale(viewport.scale, viewport.scale);

    // Viewport culling: skip anything whose cell rect falls outside the
    // visible canvas area (in screen space) rather than drawing all rows*cols.
    const visLeft = -viewport.offsetX / viewport.scale - CELL_SIZE;
    const visTop = -viewport.offsetY / viewport.scale - CELL_SIZE;
    const visRight = (containerSize.width - viewport.offsetX) / viewport.scale + CELL_SIZE;
    const visBottom = (containerSize.height - viewport.offsetY) / viewport.scale + CELL_SIZE;

    for (const [row, col] of grid.blocked) {
      const x = col * CELL_SIZE;
      const y = row * CELL_SIZE;
      if (x < visLeft || x > visRight || y < visTop || y > visBottom) continue;
      paintCell(ctx, x, y, CELL_SIZE, 'BLOCKED');
    }

    let highlight: [number, number] | null = null;
    const showDim = hasUnsupportedFilter(filter) === false && Object.values(filter).some((v) => v !== undefined && v !== '' && v !== false);
    for (const tuple of grid.plots) {
      const [row, col, plotNumber, statusCode] = tuple;
      const x = col * CELL_SIZE;
      const y = row * CELL_SIZE;
      if (x < visLeft || x > visRight || y < visTop || y > visBottom) continue;
      const status = STATUS_CODE_TO_KEY[statusCode];
      const dim = showDim && !matchesFilter(tuple, filter);
      paintCell(ctx, x, y, CELL_SIZE, status, dim);
      if (searchTerm && plotNumber.toLowerCase() === searchTerm.toLowerCase()) highlight = [row, col];
    }

    if (highlight) {
      const [row, col] = highlight;
      ctx.strokeStyle = '#facc15';
      ctx.lineWidth = 3 / viewport.scale;
      ctx.strokeRect(col * CELL_SIZE + 1.5, row * CELL_SIZE + 1.5, CELL_SIZE - 3, CELL_SIZE - 3);
    }

    ctx.restore();
  }, [grid, viewport, containerSize, filter, searchTerm]);

  const handleClick = useCallback(
    (e: React.MouseEvent<HTMLCanvasElement>) => {
      if (wasDrag()) return;
      const rect = canvasRef.current!.getBoundingClientRect();
      const screenX = e.clientX - rect.left;
      const screenY = e.clientY - rect.top;
      const gridX = (screenX - viewport.offsetX) / viewport.scale;
      const gridY = (screenY - viewport.offsetY) / viewport.scale;
      const col = Math.floor(gridX / CELL_SIZE);
      const row = Math.floor(gridY / CELL_SIZE);

      if (armedPlacement && onPlaceArmedPlot) {
        onPlaceArmedPlot(row, col);
        return;
      }
      const tuple = grid.plots.find((p) => p[0] === row && p[1] === col);
      if (tuple) onSelectPlotNumber(tuple[2]);
    },
    [grid.plots, viewport, wasDrag, armedPlacement, onPlaceArmedPlot, onSelectPlotNumber],
  );

  return (
    <div
      ref={containerRef}
      className="relative h-full min-h-[400px] w-full touch-none overflow-hidden rounded-md border border-border bg-muted/20"
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onPointerCancel={onPointerCancel}
      onWheel={onWheel}
      data-testid="plot-grid-viewport"
      data-scale={viewport.scale.toFixed(4)}
    >
      <canvas ref={canvasRef} onClick={handleClick} className={armedPlacement ? 'cursor-crosshair' : 'cursor-pointer'} />
      <div className="absolute bottom-3 right-3 flex flex-col gap-1">
        <Button type="button" size="icon" variant="secondary" onClick={() => zoomBy(1.25)} aria-label={t('grid.zoomIn')}>
          <Plus className="size-4" />
        </Button>
        <Button type="button" size="icon" variant="secondary" onClick={() => zoomBy(0.8)} aria-label={t('grid.zoomOut')}>
          <Minus className="size-4" />
        </Button>
        <Button type="button" size="icon" variant="secondary" onClick={reset} aria-label={t('grid.resetView')}>
          <RotateCcw className="size-4" />
        </Button>
      </div>
    </div>
  );
}
