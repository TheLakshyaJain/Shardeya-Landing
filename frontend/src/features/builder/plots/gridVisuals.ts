import type { CSSProperties } from 'react';
import type { PlotStatus } from './types';

// Status must be distinguishable by more than colour alone (CLAUDE.md M2
// kickoff requirement) -- every status gets a distinct fill colour AND a
// distinct hatch pattern, checked for both the canvas renderer (drawn
// per-cell) and the DOM renderer (CSS repeating-gradient equivalents below).
export const STATUS_COLORS: Record<PlotStatus, string> = {
  AVAILABLE: '#16a34a', // green-600 - solid fill, no hatch
  RESERVED: '#d97706', // amber-600 - diagonal stripes
  SOLD: '#dc2626', // red-600 - cross-hatch
};

export const BLOCKED_COLOR = '#94a3b8'; // slate-400, diagonal hatch (same family as SOLD but a different colour entirely)

export const STATUS_CODE_TO_KEY: Record<number, PlotStatus> = { 1: 'AVAILABLE', 2: 'RESERVED', 3: 'SOLD' };
export const STATUS_KEY_TO_CODE: Record<PlotStatus, number> = { AVAILABLE: 1, RESERVED: 2, SOLD: 3 };

/** Paints a status's fill + hatch pattern into a single grid cell (canvas renderer). */
export function paintCell(ctx: CanvasRenderingContext2D, x: number, y: number, size: number, status: PlotStatus | 'BLOCKED', dim = false) {
  const color = status === 'BLOCKED' ? BLOCKED_COLOR : STATUS_COLORS[status];
  ctx.save();
  ctx.globalAlpha = dim ? 0.25 : 1;
  ctx.fillStyle = color;
  ctx.fillRect(x, y, size, size);

  if (status === 'RESERVED' || status === 'SOLD' || status === 'BLOCKED') {
    // Clip to the cell before drawing hatch lines so strokes never bleed
    // into neighbouring cells.
    ctx.beginPath();
    ctx.rect(x, y, size, size);
    ctx.clip();

    ctx.strokeStyle = 'rgba(255,255,255,0.55)';
    ctx.lineWidth = Math.max(1, size * 0.06);
    ctx.beginPath();
    if (status === 'RESERVED') {
      for (let i = -size; i < size * 2; i += size / 3) {
        ctx.moveTo(x + i, y + size);
        ctx.lineTo(x + i + size, y);
      }
    } else {
      for (let i = -size; i < size * 2; i += size / 3) {
        ctx.moveTo(x + i, y + size);
        ctx.lineTo(x + i + size, y);
        ctx.moveTo(x + i, y);
        ctx.lineTo(x + i + size, y + size);
      }
    }
    ctx.stroke();
  }
  ctx.restore();
}

/** CSS equivalents for the DOM renderer (<=400 plots) — same colour+pattern language as the canvas painter above. */
export function cellBackgroundStyle(status: PlotStatus | 'BLOCKED'): CSSProperties {
  const color = status === 'BLOCKED' ? BLOCKED_COLOR : STATUS_COLORS[status];
  if (status === 'AVAILABLE') {
    return { backgroundColor: color };
  }
  if (status === 'RESERVED') {
    return {
      backgroundColor: color,
      backgroundImage:
        'repeating-linear-gradient(45deg, rgba(255,255,255,0.5) 0, rgba(255,255,255,0.5) 2px, transparent 2px, transparent 8px)',
    };
  }
  return {
    backgroundColor: color,
    backgroundImage:
      'repeating-linear-gradient(45deg, rgba(255,255,255,0.5) 0, rgba(255,255,255,0.5) 2px, transparent 2px, transparent 8px), ' +
      'repeating-linear-gradient(-45deg, rgba(255,255,255,0.5) 0, rgba(255,255,255,0.5) 2px, transparent 2px, transparent 8px)',
  };
}
