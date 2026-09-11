import { apiFetch } from '@/lib/api/client';
import type {
  CursorPage,
  CustomerCreateRequest,
  CustomerResponse,
  CustomerUpdateRequest,
  FunnelResponse,
  InteractionCreateRequest,
  InteractionResponse,
} from '../types';

export interface LeadListParams {
  cursor?: string;
  limit?: number;
  projectId?: string;
  plotId?: string;
  assignedTo?: string;
  source?: string;
  status?: string;
  important?: boolean;
  followUp?: string;
  search?: string;
}

export function listLeads(params: LeadListParams = {}): Promise<CursorPage<CustomerResponse>> {
  const qs = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== '') qs.set(k, String(v));
  });
  const s = qs.toString();
  return apiFetch(`/builder/customers${s ? `?${s}` : ''}`);
}

export function getLead(id: string): Promise<CustomerResponse> {
  return apiFetch(`/customers/${id}`);
}

export function createLead(req: CustomerCreateRequest): Promise<CustomerResponse> {
  return apiFetch('/builder/customers', { method: 'POST', body: req });
}

export function updateLead(id: string, req: CustomerUpdateRequest): Promise<CustomerResponse> {
  return apiFetch(`/customers/${id}`, { method: 'PATCH', body: req });
}

export function deleteLead(id: string): Promise<void> {
  return apiFetch(`/customers/${id}`, { method: 'DELETE' });
}

export function setImportant(id: string, important: boolean): Promise<CustomerResponse> {
  return apiFetch(`/customers/${id}/important`, { method: 'POST', body: { important } });
}

export function updateLeadStatus(id: string, status: string, note?: string): Promise<CustomerResponse> {
  return apiFetch(`/customers/${id}/status`, { method: 'PATCH', body: { status, note } });
}

export function assignLead(id: string, userId: string): Promise<CustomerResponse> {
  return apiFetch(`/customers/${id}/assign`, { method: 'PATCH', body: { userId } });
}

export function bulkAssignLeads(customerIds: string[], userId: string): Promise<void> {
  return apiFetch('/customers/bulk-assign', { method: 'POST', body: { customerIds, userId } });
}

export function unassignedLeads(limit = 50): Promise<CustomerResponse[]> {
  return apiFetch(`/builder/customers/unassigned?limit=${limit}`);
}

export function leadFunnel(projectId?: string): Promise<FunnelResponse> {
  return apiFetch(`/builder/customers/funnel${projectId ? `?projectId=${projectId}` : ''}`);
}

export function listInteractions(customerId: string, limit = 50): Promise<InteractionResponse[]> {
  return apiFetch(`/customers/${customerId}/interactions?limit=${limit}`);
}

export function addInteraction(customerId: string, req: InteractionCreateRequest): Promise<InteractionResponse> {
  return apiFetch(`/customers/${customerId}/interactions`, { method: 'POST', body: req });
}

export function amendInteraction(id: string, remarks: string): Promise<InteractionResponse> {
  return apiFetch(`/interactions/${id}`, { method: 'PATCH', body: { remarks } });
}
