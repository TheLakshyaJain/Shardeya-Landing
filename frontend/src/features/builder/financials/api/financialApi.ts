import { apiFetch } from '@/lib/api/client';
import type { PaymentCreateRequest, PaymentResponse } from '../../payments/types';
import type {
  CursorPage,
  FinancialPaymentRow,
  FinancialSummaryResponse,
  PendingInstalmentRow,
  RevenueTrendPoint,
} from '../types';

export interface FinancialListParams {
  projectId?: string;
  from?: string;
  to?: string;
  mode?: string;
  cursor?: string;
  limit?: number;
}

function qs(params: object): string {
  const u = new URLSearchParams();
  Object.entries(params as Record<string, unknown>).forEach(([k, v]) => {
    if (v !== undefined && v !== '') u.set(k, String(v));
  });
  const s = u.toString();
  return s ? `?${s}` : '';
}

export function getFinancialSummary(projectId?: string, from?: string, to?: string): Promise<FinancialSummaryResponse> {
  return apiFetch(`/builder/financials/summary${qs({ projectId, from, to })}`);
}

export function listFinancialPayments(params: FinancialListParams = {}): Promise<CursorPage<FinancialPaymentRow>> {
  return apiFetch(`/builder/financials/payments${qs(params)}`);
}

export function listPendingInstalments(
  params: FinancialListParams & { overdueOnly?: boolean } = {},
): Promise<CursorPage<PendingInstalmentRow>> {
  return apiFetch(`/builder/financials/pending${qs(params)}`);
}

export function getRevenueTrend(projectId?: string, months = 12): Promise<RevenueTrendPoint[]> {
  return apiFetch(`/builder/financials/revenue-trend${qs({ projectId, months })}`);
}

export function recordScheduleQuickPayment(scheduleId: string, req: PaymentCreateRequest): Promise<PaymentResponse> {
  return apiFetch(`/builder/tracker/collections/${scheduleId}/record-payment`, { method: 'POST', body: req });
}

export function sendScheduleReminder(scheduleId: string): Promise<void> {
  return apiFetch(`/builder/tracker/collections/${scheduleId}/remind`, { method: 'POST' });
}

export function bulkRemind(scheduleIds: string[]): Promise<{ sent: number; skipped: number }> {
  return apiFetch('/builder/tracker/collections/bulk-remind', { method: 'POST', body: { scheduleIds } });
}
