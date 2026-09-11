import { apiFetch } from '@/lib/api/client';
import type { PaymentCreateRequest, PaymentResponse } from '../../payments/types';
import type { InteractionResult, InteractionType } from '../../leads/types';
import type { CollectionRow, CursorPage, FollowUpRow, TrackerCounts, TrackerRange } from '../types';

function qs(params: Record<string, string | number | boolean | undefined>): string {
  const u = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== '') u.set(k, String(v));
  });
  const s = u.toString();
  return s ? `?${s}` : '';
}

export function listFollowUps(params: { range?: TrackerRange; assignedTo?: string; projectId?: string; cursor?: string; limit?: number } = {}): Promise<CursorPage<FollowUpRow>> {
  return apiFetch(`/builder/tracker/follow-ups${qs(params)}`);
}

export function listCollections(params: { range?: TrackerRange; projectId?: string; cursor?: string; limit?: number } = {}): Promise<CursorPage<CollectionRow>> {
  return apiFetch(`/builder/tracker/collections${qs(params)}`);
}

export function getTrackerCounts(): Promise<TrackerCounts> {
  return apiFetch('/builder/tracker/counts');
}

export function logFollowUp(customerId: string, req: { type: InteractionType; remarks: string; nextDate?: string; result?: InteractionResult }) {
  return apiFetch(`/builder/tracker/follow-ups/${customerId}/log`, { method: 'POST', body: req });
}

export function rescheduleFollowUp(customerId: string, newDate: string, reason: string) {
  return apiFetch(`/builder/tracker/follow-ups/${customerId}/reschedule`, { method: 'POST', body: { newDate, reason } });
}

export function markFollowUpDone(customerId: string, remarks: string) {
  return apiFetch(`/builder/tracker/follow-ups/${customerId}/mark-done`, { method: 'POST', body: { remarks } });
}

export function recordCollectionPayment(scheduleId: string, req: PaymentCreateRequest): Promise<PaymentResponse> {
  return apiFetch(`/builder/tracker/collections/${scheduleId}/record-payment`, { method: 'POST', body: req });
}

export function remindCollection(scheduleId: string): Promise<void> {
  return apiFetch(`/builder/tracker/collections/${scheduleId}/remind`, { method: 'POST' });
}

export function bulkRemindCollections(scheduleIds: string[]): Promise<{ sent: number; skipped: number }> {
  return apiFetch('/builder/tracker/collections/bulk-remind', { method: 'POST', body: { scheduleIds } });
}
