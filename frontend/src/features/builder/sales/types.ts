// Mirrors backend/src/main/java/com/shardeya/builder/sale/dto exactly.

export type GovIdType = 'AADHAAR' | 'PAN' | 'PASSPORT' | 'VOTER_ID' | 'DL';
export type SalePaymentType = 'LUMP_SUM' | 'INSTALMENT';
export type SaleStatus = 'BOOKED' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED';

export interface ScheduleRowRequest {
  label?: string;
  amount: number;
  dueDate: string;
}

export interface SaleCreateRequest {
  customerId?: string;
  buyerName: string;
  buyerMobile: string;
  buyerEmail?: string;
  buyerGovIdType?: GovIdType;
  buyerGovIdNumber?: string;
  buyerGovIdMediaId?: string;
  purchaseDate: string;
  dealValue: number;
  brokerPartnerId?: string;
  externalBrokerName?: string;
  externalBrokerMobile?: string;
  brokerCommissionAmount?: number;
  paymentType: SalePaymentType;
  schedule: ScheduleRowRequest[];
  handledBy?: string;
  // Consent captured on the buyer's behalf, by whoever is running the
  // wizard -- distinct from a user's own OTP-confirmed opt-in.
  buyerWhatsappOptIn?: boolean;
}

export interface SaleUpdateRequest {
  buyerName?: string;
  buyerMobile?: string;
  buyerEmail?: string;
  dealValue?: number;
  externalBrokerName?: string;
  externalBrokerMobile?: string;
  brokerCommissionAmount?: number;
}

export interface CancelSaleRequest {
  reason: string;
  refundHandling?: string;
}

export interface SaleResponse {
  id: string;
  plotId: string;
  projectId: string;
  customerId: string | null;
  buyerName: string;
  buyerMobile: string;
  buyerEmail: string | null;
  buyerGovIdType: GovIdType | null;
  buyerGovIdLast4: string | null;
  buyerGovIdMediaId: string | null;
  purchaseDate: string;
  dealValue: number;
  brokerPartnerId: string | null;
  brokerName: string | null;
  externalBrokerName: string | null;
  externalBrokerMobile: string | null;
  brokerCommissionAmount: number | null;
  paymentType: SalePaymentType;
  status: SaleStatus;
  cancelledAt: string | null;
  cancellationReason: string | null;
  handledBy: string | null;
  totalPaid: number;
  balanceDue: number;
  buyerWhatsappOptedIn: boolean;
}

export interface GovIdRevealResponse {
  govIdType: GovIdType | null;
  govIdNumber: string | null;
  govIdMediaUrl: string | null;
}

export type PlotDocumentType = 'SALE_AGREEMENT' | 'REGISTRY_DEED' | 'PLOT_MAP' | 'BUYER_ID_PROOF' | 'OTHER';

export interface PlotDocumentAttachRequest {
  docType: PlotDocumentType;
  label?: string;
  mediaId: string;
}

export interface PlotDocumentResponse {
  id: string;
  docType: PlotDocumentType;
  label: string | null;
  mediaId: string;
  sensitive: boolean;
}
