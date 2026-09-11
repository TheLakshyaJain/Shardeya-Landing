// Mirrors backend/src/main/java/com/shardeya/builder/financial/dto exactly.

export interface OverdueSummary {
  amount: number;
  count: number;
}

export interface ModeBreakdown {
  mode: string;
  amount: number;
  count: number;
}

export interface FinancialSummaryResponse {
  totalRevenueAllTime: number;
  revenueThisMonth: number;
  revenueThisYear: number;
  pendingCollections: number;
  overdueInstalments: OverdueSummary;
  totalBrokerCommissionPaid: number;
  paymentModeBreakdown: ModeBreakdown[];
  scoped: boolean;
  scopedProjectCount: number;
  totalProjectCount: number;
}

export interface FinancialPaymentRow {
  id: string;
  paidOn: string;
  projectName: string;
  plotNumber: string;
  buyerName: string;
  instalmentSequenceNo: number | null;
  amount: number;
  mode: string;
  reference: string | null;
  chequeStatus: string | null;
  recordedByName: string | null;
  isReversal: boolean;
  dueToChequeBounce: boolean;
  remarks: string | null;
  createdAt: string;
}

export interface PendingInstalmentRow {
  scheduleId: string;
  plotSaleId: string;
  buyerName: string;
  buyerMobile: string;
  projectName: string;
  plotNumber: string;
  amountDue: number;
  dueDate: string;
  daysOverdue: number;
  status: string;
  totalSaleBalance: number;
  reminderEnabled: boolean;
}

export interface RevenueTrendPoint {
  month: string;
  collected: number;
  paymentCount: number;
}

export interface CursorPage<T> {
  items: T[];
  nextCursor: string | null;
  hasMore: boolean;
}
