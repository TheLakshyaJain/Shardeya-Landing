import { apiFetch } from '@/lib/api/client';
import { useAuthStore } from '@/features/auth/store/authStore';
import type { ReportDefinitionSummary, ReportPreviewResponse } from '../types';

export function listReports(): Promise<ReportDefinitionSummary[]> {
  return apiFetch('/reports');
}

export function previewReport(code: string, filters: Record<string, string>, page: number): Promise<ReportPreviewResponse> {
  return apiFetch(`/reports/${code}/preview`, { method: 'POST', body: { filters, page } });
}

// Binary download, like downloadImportTemplate — apiFetch always parses JSON,
// so this talks to fetch() directly with the auth token attached.
export async function exportReport(code: string, filters: Record<string, string>, format: 'XLSX' | 'CSV'): Promise<void> {
  const token = useAuthStore.getState().accessToken;
  const base = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1';
  const res = await fetch(`${base}/reports/${code}/export`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify({ filters, format }),
  });
  if (!res.ok) throw new Error(`Export failed with status ${res.status}`);
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `${code.toLowerCase()}.${format === 'XLSX' ? 'xlsx' : 'csv'}`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
