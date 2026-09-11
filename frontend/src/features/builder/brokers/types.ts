// Mirrors backend/src/main/java/com/shardeya/builder/broker/dto exactly.

// DESIGNATION added by 06-BROKER-NETWORK-ENGINE.md §0. FIXED stays in the
// type (existing M6 brokers still use it) but is rejected by the backend
// for NEW brokers -- the create form never offers it as a choice anymore.
export type CommissionType = 'PERCENTAGE' | 'FIXED' | 'DESIGNATION';
export type BrokerStatus = 'ACTIVE' | 'INACTIVE' | 'BLOCKED';
export type CommissionConfigScope = 'GLOBAL' | 'PROJECT' | 'PLOT';
export type CommissionLedgerStatus = 'PENDING' | 'PARTIALLY_PAID' | 'PAID' | 'CANCELLED';
export type CommissionPaymentMode = 'CASH' | 'CHEQUE' | 'BANK_TRANSFER' | 'UPI' | 'DD';
export type BonusType = 'PCT' | 'FIXED' | 'NONE' | 'RATE_PER_SQFT';

export interface BrokerResponse {
  id: string;
  fullName: string;
  mobile: string;
  email: string | null;
  cityArea: string | null;
  firmName: string | null;
  commissionType: CommissionType;
  commissionPct: number | null;
  commissionFixed: number | null;
  perProjectRatesEnabled: boolean;
  tierId: string | null;
  tierName: string | null;
  dealsClosedCount: number;
  totalCommissionEarned: number;
  totalCommissionPaid: number;
  commissionDue: number;
  lastActiveAt: string | null;
  status: BrokerStatus;
  // 06-BROKER-NETWORK-ENGINE.md §11 -- null/0 for PERCENTAGE and FIXED
  // brokers, which don't participate in the hierarchy (§0).
  uplineBrokerId: string | null;
  currentDesignationId: string | null;
  currentDesignationName: string | null;
  currentDesignationNameHi: string | null;
  currentCommissionRate: number | null;
  personalSuccessfulBookings: number;
  teamSuccessfulBookings: number;
  designationManuallyOverridden: boolean;
}

export interface BrokerCreateRequest {
  fullName: string;
  mobile: string;
  email?: string;
  cityArea?: string;
  reraNumber?: string;
  firmName?: string;
  commissionType: CommissionType;
  commissionPct?: number;
  commissionFixed?: number;
  perProjectRatesEnabled: boolean;
  bankAccountName?: string;
  bankAccountNumber?: string;
  ifsc?: string;
  upiId?: string;
  notes?: string;
  uplineBrokerId?: string;
}

export type BrokerUpdateRequest = Partial<BrokerCreateRequest>;

export interface BrokerNetworkNodeResponse {
  id: string;
  fullName: string;
  uplineBrokerId: string | null;
  designationName: string | null;
  designationNameHi: string | null;
  currentCommissionRate: number | null;
  personalSuccessfulBookings: number;
  teamSuccessfulBookings: number;
  status: BrokerStatus;
}

export interface DesignationSlabResponse {
  id: string;
  name: string;
  nameHi: string | null;
  minTeamSales: number;
  maxTeamSales: number | null;
  ratePerSqft: number;
  sortOrder: number;
}

export interface BankDetailsResponse {
  bankAccountName: string | null;
  bankAccountNumber: string | null;
  ifsc: string | null;
  upiId: string | null;
}

export interface CommissionConfigResponse {
  id: string;
  brokerPartnerId: string;
  scope: CommissionConfigScope;
  projectId: string | null;
  projectName: string | null;
  plotId: string | null;
  plotNumber: string | null;
  commissionType: CommissionType;
  rateValue: number;
  effectiveFrom: string;
  effectiveTo: string | null;
}

export interface CommissionConfigCreateRequest {
  scope: CommissionConfigScope;
  projectId?: string;
  plotId?: string;
  commissionType: CommissionType;
  rateValue: number;
  effectiveFrom: string;
  effectiveTo?: string;
}

export interface CommissionPreviewRequest {
  projectId?: string;
  plotId?: string;
  dealValue: number;
  saleDate?: string;
}

export interface CommissionPreviewResponse {
  baseCommission: number;
  tierBonus: number;
  totalCommission: number;
  appliedScope: string;
  appliedCommissionType: CommissionType;
  appliedRateValue: number;
  tierName: string | null;
  usedBrokerDefault: boolean;
}

export interface BrokerTierResponse {
  id: string;
  name: string;
  nameHi: string | null;
  minDeals: number;
  maxDeals: number | null;
  bonusType: BonusType;
  bonusValue: number;
  perksDescription: string | null;
  sortOrder: number;
  active: boolean;
}

export interface BrokerTierCreateRequest {
  name: string;
  nameHi?: string;
  minDeals: number;
  maxDeals?: number;
  bonusType?: BonusType;
  bonusValue?: number;
  perksDescription?: string;
}

export interface TierOverrideRequest {
  tierId: string;
  reason: string;
}

export interface CommissionLedgerEntryResponse {
  id: string;
  brokerPartnerId: string;
  brokerName: string | null;
  plotSaleId: string;
  projectName: string | null;
  plotNumber: string | null;
  buyerName: string | null;
  dealDate: string;
  dealValue: number;
  baseCommission: number;
  tierBonus: number;
  totalCommission: number;
  amountPaid: number;
  balanceDue: number;
  status: CommissionLedgerStatus;
  needsRecovery: boolean;
  recoveryAmount: number | null;
}

// 06-BROKER-NETWORK-ENGINE.md §34, build-order step 8 -- mirrors TierOverrideRequest exactly.
export interface DesignationOverrideRequest {
  designationId: string;
  reason: string;
}

// §26/§27/§29, build-order step 10.
export type DesignationChangeType = 'AUTOMATIC' | 'MANUAL' | 'CANCELLATION_REVERSAL';

export interface DesignationHistoryResponse {
  id: string;
  brokerId: string;
  brokerName: string | null;
  previousDesignationName: string | null;
  previousDesignationNameHi: string | null;
  previousRate: number | null;
  newDesignationName: string | null;
  newDesignationNameHi: string | null;
  newRate: number;
  changeType: DesignationChangeType;
  reason: string | null;
  effectiveAt: string;
  changedByName: string | null;
}

export interface BrokerCommissionSummaryResponse {
  personalCommissionEarned: number;
  teamCommissionEarned: number;
  sellingBrokerEarned: number;
  uplineDifferentialEarned: number;
  sameSlabBonusEarned: number;
  commissionReleased: number;
  commissionPending: number;
}

export interface NetworkCommissionSummaryResponse {
  totalDesignationBrokers: number;
  totalCommissionEarned: number;
  sellingBrokerEarned: number;
  uplineDifferentialEarned: number;
  sameSlabBonusEarned: number;
  totalCommissionReleased: number;
  totalCommissionPending: number;
}

export interface CommissionPaymentCreateRequest {
  amount: number;
  paidOn: string;
  mode: CommissionPaymentMode;
  reference?: string;
  remarks?: string;
  confirmOverpayment: boolean;
}

export interface CommissionPaymentResponse {
  id: string;
  commissionLedgerEntryId: string;
  amount: number;
  paidOn: string;
  mode: CommissionPaymentMode;
  reference: string | null;
  remarks: string | null;
  reversesPaymentId: string | null;
  createdAt: string;
}

// 06-BROKER-NETWORK-ENGINE.md §8a -- totalCommissionReleased is null for
// PERCENTAGE/FIXED brokers (no release concept -- their whole commission
// is payable immediately at sale time) and a real figure for DESIGNATION
// brokers, kept visible alongside commissionDue (now genuinely
// Released - Paid, not just Released -- see BrokerDetailPage.tsx for the
// label logic this drives).
export interface BrokerPerformanceResponse {
  totalDealsAttributed: number;
  dealsCompleted: number;
  dealsActive: number;
  dealsCancelled: number;
  totalRevenueGenerated: number;
  totalCommissionEarned: number;
  totalCommissionPaid: number;
  totalCommissionReleased: number | null;
  commissionDue: number;
  conversionRatePct: number;
}

export interface BrokerDealResponse {
  saleId: string;
  projectName: string | null;
  plotNumber: string | null;
  buyerName: string;
  purchaseDate: string;
  dealValue: number;
  saleStatus: string;
  commissionEarned: number | null;
  commissionStatus: CommissionLedgerStatus | null;
}

export interface BrokerInteractionResponse {
  id: string;
  occurredOn: string;
  type: string | null;
  remarks: string;
  nextFollowUpDate: string | null;
  conductedBy: string | null;
  createdAt: string;
}

export interface BrokerInteractionCreateRequest {
  occurredOn: string;
  type?: string;
  remarks: string;
  nextFollowUpDate?: string;
}

// 06-BROKER-NETWORK-ENGINE.md §7/§8 -- the Ledger-tab-equivalent for a
// DESIGNATION broker, mirrored from booking_commission's own column shape
// (backend/src/main/java/com/shardeya/builder/broker/dto/BookingCommissionResponse.java).
export type BookingCommissionType = 'SELLING_BROKER' | 'UPLINE_DIFFERENTIAL' | 'NETWORK_SAME_SLAB_BONUS';
export type BookingCommissionStatus = 'PENDING' | 'PARTIALLY_RELEASED' | 'FULLY_RELEASED' | 'CANCELLED';

export interface BookingCommissionResponse {
  id: string;
  plotSaleId: string;
  projectName: string | null;
  plotNumber: string | null;
  buyerName: string | null;
  sellingBrokerId: string;
  sellingBrokerName: string | null;
  uplineLevel: number;
  commissionType: BookingCommissionType;
  plotAreaSqft: number;
  commissionPerSqft: number;
  totalAmount: number;
  releasedAmount: number;
  paidAmount: number;
  // 06-BROKER-NETWORK-ENGINE.md §8a -- Commission Due for this one entry
  // (releasedAmount - paidAmount, booking_commission.pending_amount, now
  // wired up for real by BrokerCommissionPaymentService). This is the
  // per-entry figure the payout's own oldest-first allocation actually
  // fills against.
  dueAmount: number;
  // The commission not yet released AT ALL (totalAmount - releasedAmount)
  // -- a genuinely different question from dueAmount ("how much more will
  // release as the customer pays more" vs. "how much can I pay the broker
  // right now"). See BookingCommissionResponse.java's own javadoc.
  outstandingAmount: number;
  status: BookingCommissionStatus;
  createdAt: string;
  needsRecovery: boolean;
  recoveryAmount: number | null;
}

// 06-BROKER-NETWORK-ENGINE.md §8a -- the "Record Payment" action for a
// DESIGNATION broker, paying against their whole Commission Due balance
// (auto-allocated oldest-first across booking_commission entries).
// Deliberately no confirmOverpayment field, unlike CommissionPaymentCreateRequest
// -- a payout past Due is always a flat, hard reject here (see
// BrokerCommissionPaymentService's own class javadoc), never a
// confirm-to-proceed flow.
export interface BrokerCommissionPaymentCreateRequest {
  amount: number;
  paidOn: string;
  mode: CommissionPaymentMode;
  reference?: string;
  remarks?: string;
}

export interface BrokerCommissionPaymentReverseRequest {
  reason: string;
}

export interface BrokerCommissionPaymentResponse {
  id: string;
  beneficiaryBrokerId: string;
  amount: number;
  paidOn: string;
  mode: CommissionPaymentMode;
  reference: string | null;
  remarks: string | null;
  reversesPaymentId: string | null;
  createdAt: string;
}
