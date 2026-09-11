import { apiFetch } from '@/lib/api/client';
import type { DashboardResponse } from '../types';

export function getDashboard(): Promise<DashboardResponse> {
  return apiFetch('/builder/dashboard');
}
