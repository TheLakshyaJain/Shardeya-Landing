import { apiFetch } from '@/lib/api/client';
import type { CursorPage, DealDetailResponse, DealRow, DealsSummary } from '../types';

export interface DealListParams {
  status?: string;
  projectId?: string;
  staffId?: string;
  from?: string;
  to?: string;
  search?: string;
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

export function listDeals(params: DealListParams = {}): Promise<CursorPage<DealRow>> {
  return apiFetch(`/builder/deals${qs(params)}`);
}

export function getDeal(saleId: string): Promise<DealDetailResponse> {
  return apiFetch(`/builder/deals/${saleId}`);
}

export function getDealsSummary(from?: string, to?: string): Promise<DealsSummary> {
  return apiFetch(`/builder/deals/summary${qs({ from, to })}`);
}
