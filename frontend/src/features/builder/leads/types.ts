// Mirrors backend/src/main/java/com/shardeya/foundation/customer/dto exactly.

export type LeadStatus = 'INTERESTED' | 'SITE_VISIT_SCHEDULED' | 'SITE_VISIT_DONE' | 'FOLLOWING_UP' | 'DEAL_CLOSED' | 'LOST';
export type LeadSource =
  | 'REFERRAL' | 'FACEBOOK' | 'INSTAGRAM' | 'WALK_IN' | 'COLD_CALL' | 'WEBSITE' | 'BROKER' | 'EXHIBITION'
  | 'SOCIAL_MEDIA' | 'OTHER';

export const LEAD_SOURCES: LeadSource[] = [
  'REFERRAL', 'FACEBOOK', 'INSTAGRAM', 'WALK_IN', 'COLD_CALL', 'WEBSITE', 'BROKER', 'EXHIBITION', 'SOCIAL_MEDIA', 'OTHER',
];
export const LEAD_STATUSES: LeadStatus[] = [
  'INTERESTED', 'SITE_VISIT_SCHEDULED', 'SITE_VISIT_DONE', 'FOLLOWING_UP', 'DEAL_CLOSED', 'LOST',
];

export interface CustomerResponse {
  id: string;
  fullName: string;
  mobile: string;
  alternateMobile?: string;
  email?: string;
  budgetMin: number;
  budgetMax: number;
  preferredPropertyType?: string;
  preferredLocality?: string;
  sizeRequirement?: string;
  source: LeadSource;
  sourceBrokerId?: string;
  status: LeadStatus;
  interestedProjectId?: string;
  interestedPlotId?: string;
  assignedTo?: string;
  followUpDate?: string;
  siteVisitDate?: string;
  noFurtherFollowUp: boolean;
  important: boolean;
  remarks?: string;
  lastInteractionAt?: string;
  closedAt?: string;
  createdAt: string;
  createdBy?: string;
}

export interface CustomerCreateRequest {
  fullName: string;
  mobile: string;
  alternateMobile?: string;
  email?: string;
  budgetMin: number;
  budgetMax: number;
  preferredPropertyType?: string;
  preferredLocality?: string;
  sizeRequirement?: string;
  source: LeadSource;
  sourceBrokerId?: string;
  status?: LeadStatus;
  interestedProjectId?: string;
  interestedPlotId?: string;
  followUpDate?: string;
  siteVisitDate?: string;
  remarks?: string;
  assignedTo?: string;
  allowDuplicate: boolean;
}

export type CustomerUpdateRequest = Partial<Omit<CustomerCreateRequest, 'allowDuplicate'>> & { noFurtherFollowUp?: boolean };

export interface FunnelResponse {
  interested: number;
  siteVisitScheduled: number;
  siteVisitDone: number;
  followingUp: number;
  dealClosed: number;
  lost: number;
}

export type InteractionType = 'CALL' | 'VISIT' | 'WHATSAPP' | 'MEETING' | 'EMAIL' | 'SMS' | 'NOTE';
export type InteractionResult = 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE' | 'NOT_INTERESTED' | 'NEXT_SCHEDULED' | 'NO_FURTHER';

export const INTERACTION_TYPES: InteractionType[] = ['CALL', 'VISIT', 'WHATSAPP', 'MEETING', 'EMAIL', 'SMS', 'NOTE'];
export const INTERACTION_RESULTS: InteractionResult[] = [
  'POSITIVE', 'NEUTRAL', 'NEGATIVE', 'NOT_INTERESTED', 'NEXT_SCHEDULED', 'NO_FURTHER',
];

export interface InteractionResponse {
  id: string;
  customerId: string;
  propertyId?: string;
  projectId?: string;
  plotId?: string;
  occurredOn: string;
  type: InteractionType;
  remarks: string;
  nextFollowUpDate?: string;
  result?: InteractionResult;
  conductedBy?: string;
  amendedAt?: string;
  amendedBy?: string;
  amendable: boolean;
  createdAt: string;
}

export interface InteractionCreateRequest {
  occurredOn: string;
  type: InteractionType;
  remarks: string;
  nextFollowUpDate?: string;
  result?: InteractionResult;
  projectId?: string;
  plotId?: string;
}

export interface CursorPage<T> {
  items: T[];
  nextCursor: string | null;
  hasMore: boolean;
}
