import { apiFetch } from '@/lib/api/client';
import type { AppErrorLogRow, MessageDeliveryLogRow, MessageStatus } from '../types';

export function listMessageDeliveries(status?: MessageStatus, limit = 50): Promise<MessageDeliveryLogRow[]> {
  const params = new URLSearchParams({ limit: String(limit) });
  if (status) params.set('status', status);
  return apiFetch(`/admin/message-deliveries?${params.toString()}`);
}

export function listErrors(limit = 50): Promise<AppErrorLogRow[]> {
  return apiFetch(`/admin/errors?limit=${limit}`);
}
