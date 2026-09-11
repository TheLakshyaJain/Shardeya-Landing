import { apiFetch } from '@/lib/api/client';
import type {
  BulkPositionPlacement,
  BulkUpdateRequest,
  GridResponse,
  PlotCreateRequest,
  PlotFilter,
  PlotResponse,
  PlotStatsResponse,
  PlotStatusUpdateRequest,
  PlotUpdateRequest,
  QuickCreateCommitResponse,
  QuickCreatePreviewResponse,
  QuickCreateRequest,
} from '../types';

export function getGrid(projectId: string): Promise<GridResponse> {
  return apiFetch(`/projects/${projectId}/plots/grid`);
}

export function getPlotStats(projectId: string): Promise<PlotStatsResponse> {
  return apiFetch(`/projects/${projectId}/plots/stats`);
}

export function listPlots(projectId: string, filter: PlotFilter = {}, limit = 500): Promise<PlotResponse[]> {
  const params = new URLSearchParams({ limit: String(limit) });
  for (const [key, value] of Object.entries(filter)) {
    if (value !== undefined && value !== null && value !== '') {
      params.set(key, String(value));
    }
  }
  return apiFetch(`/projects/${projectId}/plots?${params.toString()}`);
}

export function createPlot(projectId: string, req: PlotCreateRequest): Promise<PlotResponse> {
  return apiFetch(`/projects/${projectId}/plots`, { method: 'POST', body: req });
}

export function getPlot(id: string): Promise<PlotResponse> {
  return apiFetch(`/plots/${id}`);
}

export function updatePlot(id: string, req: PlotUpdateRequest): Promise<PlotResponse> {
  return apiFetch(`/plots/${id}`, { method: 'PATCH', body: req });
}

export function updatePlotStatus(id: string, req: PlotStatusUpdateRequest): Promise<PlotResponse> {
  return apiFetch(`/plots/${id}/status`, { method: 'PATCH', body: req });
}

export function updatePlotHot(id: string, isHot: boolean): Promise<PlotResponse> {
  return apiFetch(`/plots/${id}/hot`, { method: 'PATCH', body: { isHot } });
}

export function updatePlotPosition(id: string, gridRow: number, gridCol: number): Promise<PlotResponse> {
  return apiFetch(`/plots/${id}/position`, { method: 'PUT', body: { gridRow, gridCol } });
}

export function deletePlot(id: string): Promise<void> {
  return apiFetch(`/plots/${id}`, { method: 'DELETE' });
}

export function bulkPositionPlots(projectId: string, placements: BulkPositionPlacement[]): Promise<void> {
  return apiFetch(`/projects/${projectId}/plots/bulk-position`, { method: 'POST', body: { placements } });
}

export function bulkUpdatePlots(projectId: string, req: BulkUpdateRequest): Promise<{ updated: number }> {
  return apiFetch(`/projects/${projectId}/plots/bulk-update`, { method: 'POST', body: req });
}

export function quickCreatePreview(projectId: string, req: QuickCreateRequest): Promise<QuickCreatePreviewResponse> {
  return apiFetch(`/projects/${projectId}/plots/quick-create`, { method: 'POST', body: req });
}

export function quickCreateCommit(projectId: string, req: QuickCreateRequest): Promise<QuickCreateCommitResponse> {
  return apiFetch(`/projects/${projectId}/plots/quick-create/commit`, { method: 'POST', body: req });
}
