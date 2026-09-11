import { apiFetch } from '@/lib/api/client';
import type { CalendarEventResponse, EventCreateRequest } from '../types';

export interface CalendarListParams {
  from: string;
  to: string;
  assignedTo?: string;
  projectId?: string;
}

export function listCalendarEvents(params: CalendarListParams): Promise<CalendarEventResponse[]> {
  const qs = new URLSearchParams({ from: params.from, to: params.to });
  if (params.assignedTo) qs.set('assignedTo', params.assignedTo);
  if (params.projectId) qs.set('projectId', params.projectId);
  return apiFetch(`/calendar?${qs.toString()}`);
}

export function createCalendarEvent(req: EventCreateRequest): Promise<CalendarEventResponse> {
  return apiFetch('/calendar/events', { method: 'POST', body: req });
}

export function completeCalendarEvent(id: string): Promise<CalendarEventResponse> {
  return apiFetch(`/calendar/events/${id}/complete`, { method: 'POST' });
}

export function deleteCalendarEvent(id: string): Promise<void> {
  return apiFetch(`/calendar/events/${id}`, { method: 'DELETE' });
}

export function staffCalendarSummary(from: string, to: string): Promise<Record<string, number>> {
  return apiFetch(`/builder/calendar/staff-summary?from=${from}&to=${to}`);
}
