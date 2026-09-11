import { apiFetch } from '@/lib/api/client';
import type {
  CursorPage,
  GridConfigRequest,
  GridConfigResponse,
  ProjectCreateRequest,
  ProjectDetailResponse,
  ProjectMediaItem,
  ProjectResponse,
  ProjectUpdateRequest,
} from '../types';

export function listProjects(cursor?: string, limit = 25): Promise<CursorPage<ProjectResponse>> {
  const params = new URLSearchParams({ limit: String(limit) });
  if (cursor) params.set('cursor', cursor);
  return apiFetch(`/projects?${params.toString()}`);
}

export function getProject(id: string): Promise<ProjectDetailResponse> {
  return apiFetch(`/projects/${id}`);
}

export function createProject(req: ProjectCreateRequest): Promise<ProjectDetailResponse> {
  return apiFetch('/projects', { method: 'POST', body: req });
}

export function updateProject(id: string, req: ProjectUpdateRequest): Promise<ProjectDetailResponse> {
  return apiFetch(`/projects/${id}`, { method: 'PATCH', body: req });
}

export function updateProjectStatus(id: string, status: string): Promise<ProjectDetailResponse> {
  return apiFetch(`/projects/${id}/status`, { method: 'PATCH', body: { status } });
}

export function deleteProject(id: string): Promise<void> {
  return apiFetch(`/projects/${id}`, { method: 'DELETE' });
}

export function restoreProject(id: string): Promise<ProjectDetailResponse> {
  return apiFetch(`/projects/${id}/restore`, { method: 'POST' });
}

export function getGridConfig(id: string): Promise<GridConfigResponse> {
  return apiFetch(`/projects/${id}/grid-config`);
}

export function putGridConfig(id: string, req: GridConfigRequest): Promise<GridConfigResponse> {
  return apiFetch(`/projects/${id}/grid-config`, { method: 'PUT', body: req });
}

export function listProjectMedia(id: string, role?: string): Promise<ProjectMediaItem[]> {
  const params = role ? `?role=${encodeURIComponent(role)}` : '';
  return apiFetch(`/projects/${id}/media${params}`);
}

export function attachProjectMedia(id: string, mediaId: string, role: string, sortOrder = 0): Promise<void> {
  return apiFetch(`/projects/${id}/media`, { method: 'POST', body: { mediaId, role, sortOrder } });
}

export function detachProjectMedia(id: string, mediaId: string): Promise<void> {
  return apiFetch(`/projects/${id}/media/${mediaId}`, { method: 'DELETE' });
}
