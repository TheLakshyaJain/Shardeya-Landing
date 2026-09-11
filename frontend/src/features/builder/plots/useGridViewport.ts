import { useCallback, useRef, useState } from 'react';

export interface Viewport {
  scale: number;
  offsetX: number;
  offsetY: number;
}

const MIN_SCALE = 0.25;
const MAX_SCALE = 6;
const CLICK_DRAG_THRESHOLD_PX = 6;

// Pointer Events (not separate mouse/touch listeners) so mouse-drag-pan,
// single-finger touch-pan, and two-finger pinch-zoom on mobile are all
// handled through one code path -- multiple simultaneous pointers is exactly
// how a pinch gesture surfaces here (two "pointerdown"s, each pointer's
// "pointermove" updates independently).
export function useGridViewport(containerRef: React.RefObject<HTMLElement | null>) {
  const [viewport, setViewport] = useState<Viewport>({ scale: 1, offsetX: 0, offsetY: 0 });
  const pointers = useRef(new Map<number, { x: number; y: number }>());
  const pinchStartDistance = useRef<number | null>(null);
  const pinchStartScale = useRef(1);
  const panStart = useRef<{ x: number; y: number; offsetX: number; offsetY: number } | null>(null);
  const dragDistance = useRef(0);

  const clampScale = (s: number) => Math.min(MAX_SCALE, Math.max(MIN_SCALE, s));

  const onPointerDown = useCallback((e: React.PointerEvent) => {
    // setPointerCapture throws a DOMException for any pointerId the browser
    // doesn't recognize as an active pointer (confirmed via synthetic
    // PointerEvents in an e2e pinch-zoom test, though a real device could hit
    // the same edge case) -- letting that throw escape aborted this handler
    // BEFORE the pointer ever got tracked below, so pointers.current never
    // reached size 2 and pinch-zoom silently never engaged. The capture is a
    // nice-to-have (keeps receiving events if the finger slides off the
    // element); tracking the pointer for pan/pinch math is not optional.
    try {
      (e.target as Element).setPointerCapture?.(e.pointerId);
    } catch {
      // Pointer capture is best-effort; the tracking below still works without it.
    }
    pointers.current.set(e.pointerId, { x: e.clientX, y: e.clientY });
    dragDistance.current = 0;

    if (pointers.current.size === 2) {
      const [p1, p2] = [...pointers.current.values()];
      pinchStartDistance.current = Math.hypot(p2.x - p1.x, p2.y - p1.y);
      pinchStartScale.current = viewport.scale;
      panStart.current = null;
    } else if (pointers.current.size === 1) {
      panStart.current = { x: e.clientX, y: e.clientY, offsetX: viewport.offsetX, offsetY: viewport.offsetY };
    }
  }, [viewport]);

  const onPointerMove = useCallback((e: React.PointerEvent) => {
    if (!pointers.current.has(e.pointerId)) return;
    pointers.current.set(e.pointerId, { x: e.clientX, y: e.clientY });

    if (pointers.current.size === 2 && pinchStartDistance.current) {
      const [p1, p2] = [...pointers.current.values()];
      const distance = Math.hypot(p2.x - p1.x, p2.y - p1.y);
      const nextScale = clampScale(pinchStartScale.current * (distance / pinchStartDistance.current));
      setViewport((v) => ({ ...v, scale: nextScale }));
      return;
    }

    if (pointers.current.size === 1 && panStart.current) {
      // Snapshot panStart.current into locals before scheduling the state
      // update: setViewport's updater runs whenever React actually flushes
      // it, which can be AFTER a later pointerup has already nulled out this
      // ref (pointerup mutates it synchronously; state updates are deferred).
      // Re-reading `panStart.current!` inside the updater raced exactly that
      // -- a real crash ("Cannot read properties of null (reading 'offsetX')"),
      // reproduced by dispatching a rapid pointerdown/move/up pinch sequence
      // synchronously (a real two-finger gesture on a touchscreen fires
      // just as fast) -- not visible from a slow, manual mouse test.
      const { x: startX, y: startY, offsetX: startOffsetX, offsetY: startOffsetY } = panStart.current;
      const dx = e.clientX - startX;
      const dy = e.clientY - startY;
      dragDistance.current = Math.hypot(dx, dy);
      setViewport((v) => ({ ...v, offsetX: startOffsetX + dx, offsetY: startOffsetY + dy }));
    }
  }, []);

  const endPointer = useCallback((e: React.PointerEvent) => {
    pointers.current.delete(e.pointerId);
    if (pointers.current.size < 2) pinchStartDistance.current = null;
    if (pointers.current.size === 0) panStart.current = null;
  }, []);

  // Desktop mouse wheel zoom, centred on the cursor position.
  const onWheel = useCallback((e: React.WheelEvent) => {
    e.preventDefault();
    const rect = containerRef.current?.getBoundingClientRect();
    if (!rect) return;
    const cursorX = e.clientX - rect.left;
    const cursorY = e.clientY - rect.top;

    setViewport((v) => {
      const nextScale = clampScale(v.scale * (e.deltaY > 0 ? 0.9 : 1.1));
      const scaleRatio = nextScale / v.scale;
      return {
        scale: nextScale,
        offsetX: cursorX - (cursorX - v.offsetX) * scaleRatio,
        offsetY: cursorY - (cursorY - v.offsetY) * scaleRatio,
      };
    });
  }, [containerRef]);

  const reset = useCallback(() => setViewport({ scale: 1, offsetX: 0, offsetY: 0 }), []);
  const zoomBy = useCallback((factor: number) => setViewport((v) => ({ ...v, scale: clampScale(v.scale * factor) })), []);

  /** True if the most recent pointer session moved enough to count as a drag rather than a tap/click. */
  const wasDrag = useCallback(() => dragDistance.current > CLICK_DRAG_THRESHOLD_PX, []);

  return { viewport, setViewport, onPointerDown, onPointerMove, onPointerUp: endPointer, onPointerCancel: endPointer, onWheel, reset, zoomBy, wasDrag };
}
