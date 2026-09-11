import { apiFetch } from '@/lib/api/client';
import { useAuthStore } from '@/features/auth/store/authStore';
import type { DocType, DocumentTemplateResponse, GeneratedDocumentResponse, VariablePaletteResponse } from '../types';

export function listTemplates(): Promise<DocumentTemplateResponse[]> {
  return apiFetch('/documents/templates');
}

export function getTemplate(id: string): Promise<DocumentTemplateResponse> {
  return apiFetch(`/documents/templates/${id}`);
}

export function getVariablePalette(docType: DocType): Promise<VariablePaletteResponse> {
  return apiFetch(`/documents/templates/variables?docType=${docType}`);
}

export function cloneTemplate(docType: DocType, language: 'en' | 'hi'): Promise<DocumentTemplateResponse> {
  return apiFetch('/documents/templates', { method: 'POST', body: { docType, language } });
}

export function updateTemplate(id: string, req: { name: string; bodyHtml: string; headerHtml: string; footerHtml: string }): Promise<DocumentTemplateResponse> {
  return apiFetch(`/documents/templates/${id}`, { method: 'PUT', body: req });
}

export function previewTemplate(id: string, sampleEntityId: string): Promise<{ renderedHtml: string }> {
  return apiFetch(`/documents/templates/${id}/preview`, { method: 'POST', body: { sampleEntityId } });
}

export function activateTemplate(id: string): Promise<DocumentTemplateResponse> {
  return apiFetch(`/documents/templates/${id}/activate`, { method: 'POST' });
}

export function generateDocument(req: { docType: DocType; entityId: string; templateId?: string; language: 'en' | 'hi' }, idempotencyKey: string): Promise<GeneratedDocumentResponse> {
  return apiFetch('/documents/generate', { method: 'POST', body: req, idempotencyKey });
}

export function listDocumentsForEntity(entityType: string, entityId: string): Promise<GeneratedDocumentResponse[]> {
  return apiFetch(`/documents?entityType=${entityType}&entityId=${entityId}`);
}

// Binary download -- same pattern as downloadImportTemplate/exportReport.
export async function downloadDocument(id: string, filenameHint: string): Promise<void> {
  const token = useAuthStore.getState().accessToken;
  const base = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1';
  const res = await fetch(`${base}/documents/${id}/download`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!res.ok) throw new Error(`Download failed with status ${res.status}`);
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `${filenameHint}.pdf`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

export async function bulkGenerateDemandLetters(entityIds: string[], language: 'en' | 'hi'): Promise<void> {
  const token = useAuthStore.getState().accessToken;
  const base = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1';
  const res = await fetch(`${base}/documents/bulk-generate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify({ docType: 'DEMAND_LETTER', entityIds, language }),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.errors?.[0]?.messageKey ?? 'error.internal');
  }
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = 'demand-letters.zip';
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
