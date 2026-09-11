// Mirrors backend/src/main/java/com/shardeya/builder/dashboard/dto exactly.

export interface DashboardCard {
  value: number;
  amount: number | null;
  link: string;
}

export interface DashboardAlert {
  type: string;
  count: number;
  amount: number;
}

export interface DashboardResponse {
  cards: Record<string, DashboardCard>;
  alerts: DashboardAlert[];
  generatedAt: string;
  isOnboarding: boolean;
  scoped: boolean;
  scopedProjectCount: number;
  totalProjectCount: number;
}
