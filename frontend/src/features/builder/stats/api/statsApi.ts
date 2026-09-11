import { apiFetch } from '@/lib/api/client';
import type {
  BreakdownSlice, BrokerRanking, CollectionVsTargetPoint, FunnelStage, MonthPoint, StaffPerformanceRow, StatsOverviewResponse,
} from '../types';

export interface StatsFilters {
  projectId?: string;
  from?: string;
  to?: string;
}

// `object` (not a Record<string, ...>) so a plain interface without its own
// index signature (StatsFilters) can be passed straight through -- the
// same loosen-and-cast shape other API modules in this codebase already
// use for the identical qs()-helper-vs-typed-interface friction.
function qs(params: object): string {
  const entries = Object.entries(params as Record<string, string | number | undefined>).filter(([, v]) => v !== undefined && v !== '');
  return entries.length ? `?${entries.map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`).join('&')}` : '';
}

export function getOverview(f: StatsFilters): Promise<StatsOverviewResponse> {
  return apiFetch(`/builder/stats/overview${qs(f)}`);
}

export function getMonthlySales(projectId: string | undefined, months: number): Promise<MonthPoint[]> {
  return apiFetch(`/builder/stats/monthly-sales${qs({ projectId, months })}`);
}

export function getMonthlyRevenue(projectId: string | undefined, months: number): Promise<MonthPoint[]> {
  return apiFetch(`/builder/stats/monthly-revenue${qs({ projectId, months })}`);
}

export function getPlotStatusBreakdown(projectId?: string): Promise<BreakdownSlice[]> {
  return apiFetch(`/builder/stats/plot-status-breakdown${qs({ projectId })}`);
}

export function getLeadsBySource(f: StatsFilters): Promise<BreakdownSlice[]> {
  return apiFetch(`/builder/stats/leads-by-source${qs(f)}`);
}

export function getConversionFunnel(f: StatsFilters): Promise<FunnelStage[]> {
  return apiFetch(`/builder/stats/conversion-funnel${qs(f)}`);
}

export function getTopBrokers(limit: number, from?: string, to?: string): Promise<BrokerRanking[]> {
  return apiFetch(`/builder/stats/top-brokers${qs({ limit, from, to })}`);
}

export function getRevenueByProject(from?: string, to?: string): Promise<BreakdownSlice[]> {
  return apiFetch(`/builder/stats/revenue-by-project${qs({ from, to })}`);
}

export function getCollectionVsTarget(projectId: string | undefined, months: number): Promise<CollectionVsTargetPoint[]> {
  return apiFetch(`/builder/stats/collection-vs-target${qs({ projectId, months })}`);
}

export function getOverdueTrend(projectId: string | undefined, months: number): Promise<MonthPoint[]> {
  return apiFetch(`/builder/stats/overdue-trend${qs({ projectId, months })}`);
}

export function getStaffPerformance(from?: string, to?: string): Promise<StaffPerformanceRow[]> {
  return apiFetch(`/builder/stats/staff-performance${qs({ from, to })}`);
}
