// Mirrors backend/src/main/java/com/shardeya/builder/payment/dto exactly.

export type PaymentMode = 'CASH' | 'CHEQUE' | 'BANK_TRANSFER' | 'UPI' | 'DD';
export type ChequeStatus = 'PENDING' | 'CLEARED' | 'BOUNCED';
export type ScheduleStatus = 'PENDING' | 'PARTIALLY_PAID' | 'PAID' | 'OVERDUE' | 'WAIVED';

export interface AllocationRequest {
  scheduleId: string;
  amount: number;
}

export interface PaymentCreateRequest {
  amount: number;
  paidOn: string;
  mode: PaymentMode;
  reference?: string;
  remarks?: string;
  allocations?: AllocationRequest[];
}

export interface AllocationResponse {
  scheduleId: string;
  amount: number;
}

export interface PaymentResponse {
  id: string;
  plotSaleId: string;
  receiptNo: string;
  amount: number;
  paidOn: string;
  mode: PaymentMode;
  reference: string | null;
  chequeStatus: ChequeStatus | null;
  receivedBy: string;
  remarks: string | null;
  reversesPaymentId: string | null;
  allocations: AllocationResponse[];
  createdAt: string;
}

export interface PaymentSummaryResponse {
  dealValue: number;
  totalPaid: number;
  totalWaived: number;
  balanceDue: number;
}

export interface ScheduleCreateRequest {
  label?: string;
  amount: number;
  dueDate: string;
}

export interface ScheduleUpdateRequest {
  label?: string;
  amount?: number;
  dueDate?: string;
}

export interface ScheduleResponse {
  id: string;
  sequenceNo: number;
  label: string | null;
  expectedAmount: number;
  dueDate: string;
  status: ScheduleStatus;
  amountAllocated: number;
  reminderEnabled: boolean;
  daysOverdue: number;
}
