import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { PageHeader } from '@/components/layout/PageHeader';
import { Skeleton } from '@/components/ui/skeleton';
import {
  getCollectionVsTarget, getConversionFunnel, getLeadsBySource, getMonthlyRevenue, getMonthlySales,
  getOverdueTrend, getOverview, getPlotStatusBreakdown, getRevenueByProject, getStaffPerformance, getTopBrokers,
} from '../api/statsApi';
import { StatsFilterBar } from '../components/StatsFilterBar';
import { KpiStrip } from '../components/KpiStrip';
import { MonthlySalesChart } from '../components/MonthlySalesChart';
import { MonthlyRevenueChart } from '../components/MonthlyRevenueChart';
import { PlotStatusPieChart } from '../components/PlotStatusPieChart';
import { LeadSourcePieChart } from '../components/LeadSourcePieChart';
import { ConversionFunnelChart } from '../components/ConversionFunnelChart';
import { TopBrokersChart } from '../components/TopBrokersChart';
import { RevenueByProjectChart } from '../components/RevenueByProjectChart';
import { CollectionVsTargetChart } from '../components/CollectionVsTargetChart';
import { OverdueTrendChart } from '../components/OverdueTrendChart';
import { StaffPerformanceTable } from '../components/StaffPerformanceTable';
import { useCan } from '@/hooks/useCan';

// B-15 §5/§6 /builder/stats: all ten §21.1 charts + KpiStrip, one shared
// filter state (projectId/from/to) driving parallel queries -- "a single
// filter state driving parallel queries with a shared cache key" (§7),
// implemented here as one state object feeding N independent useQuery
// calls (TanStack Query's own per-key caching already gives the "shared
// cache key" property the spec asks for, without a custom cache layer).
export function StatsPage() {
  const { t } = useTranslation('stats');
  const canViewFinancial = useCan('FINANCIAL_VIEW');
  const [projectId, setProjectId] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');

  const filters = { projectId: projectId || undefined, from: from || undefined, to: to || undefined };

  const overviewQuery = useQuery({ queryKey: ['stats-overview', filters], queryFn: () => getOverview(filters) });
  const monthlySalesQuery = useQuery({ queryKey: ['stats-monthly-sales', projectId], queryFn: () => getMonthlySales(projectId || undefined, 12) });
  const monthlyRevenueQuery = useQuery({
    queryKey: ['stats-monthly-revenue', projectId], queryFn: () => getMonthlyRevenue(projectId || undefined, 12), enabled: canViewFinancial,
  });
  const plotStatusQuery = useQuery({ queryKey: ['stats-plot-status', projectId], queryFn: () => getPlotStatusBreakdown(projectId || undefined) });
  const leadSourceQuery = useQuery({ queryKey: ['stats-lead-source', filters], queryFn: () => getLeadsBySource(filters) });
  const funnelQuery = useQuery({ queryKey: ['stats-funnel', filters], queryFn: () => getConversionFunnel(filters) });
  const topBrokersQuery = useQuery({ queryKey: ['stats-top-brokers', from, to], queryFn: () => getTopBrokers(5, from || undefined, to || undefined), enabled: canViewFinancial });
  const revenueByProjectQuery = useQuery({
    queryKey: ['stats-revenue-by-project', from, to], queryFn: () => getRevenueByProject(from || undefined, to || undefined), enabled: canViewFinancial,
  });
  const collectionVsTargetQuery = useQuery({
    queryKey: ['stats-collection-vs-target', projectId], queryFn: () => getCollectionVsTarget(projectId || undefined, 12), enabled: canViewFinancial,
  });
  const overdueTrendQuery = useQuery({
    queryKey: ['stats-overdue-trend', projectId], queryFn: () => getOverdueTrend(projectId || undefined, 12), enabled: canViewFinancial,
  });
  const staffPerformanceQuery = useQuery({ queryKey: ['stats-staff-performance', from, to], queryFn: () => getStaffPerformance(from || undefined, to || undefined) });

  return (
    <div>
      <PageHeader title={t('title')} description={t('description')} />
      <StatsFilterBar
        projectId={projectId} onProjectChange={setProjectId}
        from={from} to={to} onRangeChange={(f, tt) => { setFrom(f); setTo(tt); }}
      />

      {overviewQuery.isLoading ? <Skeleton className="h-20 w-full rounded-md" /> : overviewQuery.data && <KpiStrip data={overviewQuery.data} />}

      <div className="mt-6 grid grid-cols-1 gap-4 lg:grid-cols-2">
        {monthlySalesQuery.data && <MonthlySalesChart data={monthlySalesQuery.data} projectId={projectId || undefined} />}
        {canViewFinancial && monthlyRevenueQuery.data && <MonthlyRevenueChart data={monthlyRevenueQuery.data} />}
        {plotStatusQuery.data && <PlotStatusPieChart data={plotStatusQuery.data} projectId={projectId || undefined} />}
        {leadSourceQuery.data && <LeadSourcePieChart data={leadSourceQuery.data} />}
        {funnelQuery.data && <ConversionFunnelChart data={funnelQuery.data} />}
        {canViewFinancial && topBrokersQuery.data && <TopBrokersChart data={topBrokersQuery.data} />}
        {canViewFinancial && revenueByProjectQuery.data && <RevenueByProjectChart data={revenueByProjectQuery.data} />}
        {canViewFinancial && collectionVsTargetQuery.data && <CollectionVsTargetChart data={collectionVsTargetQuery.data} />}
        {canViewFinancial && overdueTrendQuery.data && <OverdueTrendChart data={overdueTrendQuery.data} />}
        {staffPerformanceQuery.data && (
          <div className="lg:col-span-2">
            <StaffPerformanceTable data={staffPerformanceQuery.data} />
          </div>
        )}
      </div>
    </div>
  );
}
