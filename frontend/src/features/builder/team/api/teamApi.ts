import { apiFetch } from '@/lib/api/client';
import type {
  DeactivateRequest,
  RoleSummary,
  StaffActivityResponse,
  TeamMemberCreateRequest,
  TeamMemberResponse,
  TeamMemberUpdateRequest,
} from '../types';

export function listTeam(status?: string, role?: string): Promise<TeamMemberResponse[]> {
  const params = new URLSearchParams();
  if (status) params.set('status', status);
  if (role) params.set('role', role);
  const qs = params.toString();
  return apiFetch(`/builder/team${qs ? `?${qs}` : ''}`);
}

export function getTeamMember(id: string): Promise<TeamMemberResponse> {
  return apiFetch(`/builder/team/${id}`);
}

export function createTeamMember(req: TeamMemberCreateRequest): Promise<TeamMemberResponse> {
  return apiFetch('/builder/team', { method: 'POST', body: req });
}

export function updateTeamMember(id: string, req: TeamMemberUpdateRequest): Promise<TeamMemberResponse> {
  return apiFetch(`/builder/team/${id}`, { method: 'PATCH', body: req });
}

export function deactivateTeamMember(id: string, req: DeactivateRequest): Promise<void> {
  return apiFetch(`/builder/team/${id}/deactivate`, { method: 'POST', body: req });
}

export function reactivateTeamMember(id: string): Promise<void> {
  return apiFetch(`/builder/team/${id}/reactivate`, { method: 'POST' });
}

export function removeTeamMember(id: string, req: DeactivateRequest): Promise<void> {
  return apiFetch(`/builder/team/${id}`, { method: 'DELETE', body: req });
}

export function resendInvite(id: string): Promise<void> {
  return apiFetch(`/builder/team/${id}/resend-invite`, { method: 'POST' });
}

export function resetTeamMemberPassword(id: string): Promise<void> {
  return apiFetch(`/builder/team/${id}/reset-password`, { method: 'POST' });
}

export function getTeamMemberActivity(id: string): Promise<StaffActivityResponse> {
  return apiFetch(`/builder/team/${id}/activity`);
}

export function listRoles(): Promise<RoleSummary[]> {
  return apiFetch('/roles');
}

export function getRolePermissions(code: string): Promise<string[]> {
  return apiFetch(`/roles/${code}/permissions`);
}

export function acceptInvite(token: string, password: string, confirmPassword: string): Promise<void> {
  return apiFetch('/auth/accept-invite', { method: 'POST', body: { token, password, confirmPassword }, anonymous: true });
}
