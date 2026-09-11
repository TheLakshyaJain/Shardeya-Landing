export type UserRole = 'developer' | 'broker';

export type ProjectStatus = 'UPCOMING' | 'ACTIVE' | 'COMPLETED';
export type PlotStatus = 'AVAILABLE' | 'RESERVED' | 'SOLD';
export type PlotFacing = 'N' | 'S' | 'E' | 'W' | 'NE' | 'NW' | 'SE' | 'SW';

export interface Project {
  id: string;
  name: string;
  projectType: 'RESIDENTIAL_PLOT_COLONY' | 'APARTMENT' | 'VILLA' | 'COMMERCIAL';
  status: ProjectStatus;
  locality: string;
  city: string;
  stateCode: string;
  address: string;
  reraNumber: string;
  totalAreaValue: number;
  totalAreaUnit: 'SQ_FT' | 'SQ_YD' | 'ACRE' | 'BIGHA';
  totalAreaSqft: number;
  declaredPlotCount: number;
  launchDate: string;
  expectedCompletionDate: string;
  description: string;
  gridRows: number;
  gridCols: number;
  coverImage?: string;
  layoutPlanUrl?: string;
}

export interface Plot {
  id: string;
  projectId: string;
  plotNumber: string;
  status: PlotStatus;
  reservedFor?: string;
  reservedUntil?: string;
  sizeValue: number;
  sizeUnit: 'SQ_FT' | 'SQ_YD';
  sizeSqft: number;
  facing: PlotFacing;
  price: number;
  pricePerSqft: number;
  isCorner: boolean;
  isGarden: boolean;
  isHot: boolean;
  remarks?: string;
  gridRow: number;
  gridCol: number;
  currentSaleId?: string;
}

export interface PlotSale {
  id: string;
  plotId: string;
  projectId: string;
  buyerName: string;
  buyerMobile: string;
  buyerEmail?: string;
  buyerGovIdType: 'AADHAAR' | 'PAN';
  buyerGovIdLast4: string;
  purchaseDate: string;
  dealValue: number;
  paymentType: 'LUMP_SUM' | 'INSTALMENT';
  status: 'BOOKED' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED';
  brokerPartnerId?: string;
  brokerCommissionAmount?: number;
  totalPaid: number;
  balanceDue: number;
  allotmentLetterNo: string;
}

export interface PaymentSchedule {
  id: string;
  plotSaleId: string;
  sequenceNo: number;
  label: string;
  expectedAmount: number;
  dueDate: string;
  status: 'PENDING' | 'PARTIALLY_PAID' | 'PAID' | 'OVERDUE';
  amountAllocated: number;
  daysOverdue?: number;
}

export interface PaymentRecord {
  id: string;
  plotSaleId: string;
  projectId: string;
  receiptNo: string;
  amount: number;
  paidOn: string;
  mode: 'CASH' | 'CHEQUE' | 'BANK_TRANSFER' | 'UPI';
  reference: string;
  receivedBy: string;
  remarks?: string;
}

export type LeadStatus = 
  | 'INTERESTED' 
  | 'SITE_VISIT_SCHEDULED' 
  | 'SITE_VISIT_DONE' 
  | 'FOLLOWING_UP' 
  | 'DEAL_CLOSED' 
  | 'LOST';

export interface Lead {
  id: string;
  fullName: string;
  mobile: string;
  email?: string;
  budgetMin: number;
  budgetMax: number;
  preferredPropertyType: 'PLOT' | 'VILLA' | 'APARTMENT';
  source: 'REFERRAL' | 'FACEBOOK' | 'WALK_IN' | 'WEBSITE' | 'BROKER' | 'EXHIBITION';
  sourceBrokerId?: string;
  status: LeadStatus;
  interestedProjectId?: string;
  interestedPlotId?: string;
  assignedTo: string;
  followUpDate: string;
  isImportant: boolean;
  remarks: string;
  lastInteractionAt: string;
  createdAt: string;
}

export interface Interaction {
  id: string;
  customerId: string;
  occurredOn: string;
  type: 'CALL' | 'VISIT' | 'WHATSAPP' | 'MEETING' | 'NOTE';
  remarks: string;
  result: 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE' | 'NEXT_SCHEDULED';
  conductedBy: string;
}

export type BrokerTier = 'Bronze' | 'Silver' | 'Gold' | 'Platinum' | 'Sole Selling';

export interface BrokerPartner {
  id: string;
  fullName: string;
  mobile: string;
  email: string;
  firmName: string;
  cityArea: string;
  reraNumber: string;
  commissionType: 'PERCENTAGE' | 'FIXED';
  commissionRate: number; // e.g., 2.0% or fixed Rs
  tier: BrokerTier;
  dealsClosedCount: number;
  totalCommissionEarned: number;
  totalCommissionPaid: number;
  status: 'ACTIVE' | 'INACTIVE';
  parentBrokerId?: string; // For network tree hierarchy
}

export interface CommissionVoucher {
  id: string;
  voucherNo: string;
  brokerId: string;
  plotSaleId: string;
  plotNumber: string;
  projectName: string;
  dealValue: number;
  commissionAmount: number;
  tdsDeduction: number;
  netPayable: number;
  dealDate: string;
  status: 'PENDING' | 'APPROVED' | 'PAID';
  paymentRef?: string;
}

export interface CalendarEvent {
  id: string;
  title: string;
  eventDate: string;
  eventTime?: string;
  eventType: 'SITE_VISIT' | 'FOLLOW_UP' | 'INSTALMENT_DUE' | 'REGISTRY';
  entityName: string;
  contactNumber: string;
  assignedTo: string;
  status: 'SCHEDULED' | 'DONE' | 'CANCELLED';
}
