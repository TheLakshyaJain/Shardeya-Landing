import { apiFetch } from '@/lib/api/client';
import type {
  PaymentCreateRequest,
  PaymentResponse,
  PaymentSummaryResponse,
  ScheduleCreateRequest,
  ScheduleResponse,
  ScheduleUpdateRequest,
} from '../types';

export function recordPayment(saleId: string, req: PaymentCreateRequest, idempotencyKey: string): Promise<PaymentResponse> {
  return apiFetch(`/sales/${saleId}/payments`, { method: 'POST', body: req, idempotencyKey });
}

export function listPayments(saleId: string): Promise<PaymentResponse[]> {
  return apiFetch(`/sales/${saleId}/payments`);
}

export function getPaymentSummary(saleId: string): Promise<PaymentSummaryResponse> {
  return apiFetch(`/sales/${saleId}/payments/summary`);
}

export function reversePayment(paymentId: string, reason: string): Promise<PaymentResponse> {
  return apiFetch(`/payments/${paymentId}/reverse`, { method: 'POST', body: { reason } });
}

export function updateChequeStatus(paymentId: string, status: 'CLEARED' | 'BOUNCED'): Promise<PaymentResponse> {
  return apiFetch(`/payments/${paymentId}/cheque-status`, { method: 'PATCH', body: { status } });
}

export function listSchedule(saleId: string): Promise<ScheduleResponse[]> {
  return apiFetch(`/sales/${saleId}/schedule`);
}

export function addScheduleRow(saleId: string, req: ScheduleCreateRequest): Promise<ScheduleResponse> {
  return apiFetch(`/sales/${saleId}/schedule`, { method: 'POST', body: req });
}

export function updateScheduleRow(scheduleId: string, req: ScheduleUpdateRequest): Promise<ScheduleResponse> {
  return apiFetch(`/schedule/${scheduleId}`, { method: 'PATCH', body: req });
}

export function deleteScheduleRow(scheduleId: string): Promise<void> {
  return apiFetch(`/schedule/${scheduleId}`, { method: 'DELETE' });
}

export function waiveScheduleRow(scheduleId: string, reason: string): Promise<ScheduleResponse> {
  return apiFetch(`/schedule/${scheduleId}/waive`, { method: 'PATCH', body: { reason } });
}
