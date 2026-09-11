export interface MonthPoint {
  month: string;
  value: number;
  count: number;
}

export interface BreakdownSlice {
  label: string;
  count: number;
  value: number | null;
}

export interface FunnelStage {
  stage: string;
  count: number;
  conversionFromFirst: number | null;
}

export interface BrokerRanking {
  brokerPartnerId: string;
  brokerName: string;
  dealsClosed: number;
  revenueGenerated: number;
}

export interface CollectionVsTargetPoint {
  month: string;
  actual: number;
  target: number;
}

export interface StaffPerformanceRow {
  userId: string;
  staffName: string;
  leadsHandled: number;
  dealsClosed: number;
  followUpsLogged: number;
  paymentsRecorded: number;
}

export interface StatsOverviewResponse {
  conversionRate: number | null;
  avgDealValue: number | null;
  avgDaysToClose: number | null;
  collectionEfficiency: number | null;
  dataAsOf: string | null;
}
