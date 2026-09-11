// Mirrors backend/src/main/java/com/shardeya/builder/tracker/dto exactly.

export interface FollowUpRow {
  customerId: string;
  followUpDate: string;
  customerName: string;
  customerMobile: string;
  projectName: string | null;
  status: string;
  assignedTo: string | null;
  assignedToName: string | null;
  lastRemark: string | null;
  daysOverdue: number;
}

export interface CollectionRow {
  scheduleId: string;
  plotSaleId: string;
  dueDate: string;
  projectName: string;
  plotNumber: string;
  buyerName: string;
  buyerMobile: string;
  amountDue: number;
  daysOverdue: number;
  totalBalance: number;
  reminderEnabled: boolean;
  // Whether the buyer has an active WhatsApp opt-in on file -- drives the
  // Send Reminder button's disabled state, not just server-side enforcement.
  buyerOptedIn: boolean;
}

export interface TrackerCounts {
  followUps: number;
  collections: number;
}

export type TrackerRange = 'today' | 'week' | 'overdue' | 'all';

export interface CursorPage<T> {
  items: T[];
  nextCursor: string | null;
  hasMore: boolean;
}
