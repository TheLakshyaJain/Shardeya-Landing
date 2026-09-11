import { apiFetch } from '@/lib/api/client';
import { useAuthStore } from '@/features/auth/store/authStore';
import type { ImportCommitRequest, ImportJobResponse, ImportRowResponse, ImportRowStatus, PlotImportStartRequest } from '../types';

export async function downloadImportTemplate(projectId: string, filenameHint: string): Promise<void> {
  // Binary download with the caller's auth token attached, unlike a plain
  // <a href> which can't carry an Authorization header — fetch the bytes via
  // apiFetch's underlying pattern isn't reusable here since it always parses
  // JSON, so this one call talks to fetch() directly with the token.
  const token = useAuthStore.getState().accessToken;
  const base = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1';
  const res = await fetch(`${base}/projects/${projectId}/plots/import/template`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!res.ok) throw new Error(`Template download failed with status ${res.status}`);
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `${filenameHint}-plot-import-template.xlsx`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

export function startImport(projectId: string, req: PlotImportStartRequest): Promise<ImportJobResponse> {
  return apiFetch(`/projects/${projectId}/plots/import`, { method: 'POST', body: req });
}

export function getImportJob(id: string): Promise<ImportJobResponse> {
  return apiFetch(`/import/jobs/${id}`);
}

export function getImportRows(id: string, status?: ImportRowStatus): Promise<ImportRowResponse[]> {
  const params = status ? `?status=${status}` : '';
  return apiFetch(`/import/jobs/${id}/rows${params}`);
}

export function patchImportRow(jobId: string, rowId: string, data: Record<string, string>): Promise<ImportRowResponse> {
  return apiFetch(`/import/jobs/${jobId}/rows/${rowId}`, { method: 'PATCH', body: data });
}

export function commitImport(id: string, req: ImportCommitRequest): Promise<ImportJobResponse> {
  return apiFetch(`/import/jobs/${id}/commit`, { method: 'POST', body: req });
}

export function cancelImport(id: string): Promise<void> {
  return apiFetch(`/import/jobs/${id}/cancel`, { method: 'POST' });
}
