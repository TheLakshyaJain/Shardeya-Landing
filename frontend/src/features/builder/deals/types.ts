// Mirrors backend/src/main/java/com/shardeya/builder/deal/dto exactly.
import type { PaymentResponse } from '../payments/types';
import type { InteractionResponse } from '../leads/types';

export interface DealRow {
  saleId: string;
  date: string;
  projectName: string;
  plotNumber: string;
  plotSizeSqft: number;
  buyerName: string;
  buyerMobile: string;
  dealValue: number | null;
  totalCollected: number | null;
  balance: number | null;
  brokerName: string | null;
  brokerCommission: number | null;
  status: 'COMPLETED' | 'CANCELLED';
  handledByName: string | null;
}

export interface PlotDocumentResponse {
  id: string;
  docType: string;
  label: string | null;
  mediaId: string;
  sensitive: boolean;
}

export interface DealDetailResponse {
  saleId: string;
  purchaseDate: string;
  status: string;
  projectName: string;
  plotNumber: string;
  plotSizeSqft: number;
  buyerName: string;
  buyerMobile: string;
  buyerEmail: string | null;
  dealValue: number | null;
  totalCollected: number | null;
  balance: number | null;
  cancellationReason: string | null;
  handledByName: string | null;
  handledByFormerStaff: boolean;
  payments: PaymentResponse[];
  documents: PlotDocumentResponse[];
  interactionTimeline: InteractionResponse[];
  commissionLedger: unknown[];
}

export interface DealsSummary {
  completedCount: number;
  cancelledCount: number;
  completedValue: number;
}

export interface CursorPage<T> {
  items: T[];
  nextCursor: string | null;
  hasMore: boolean;
}
