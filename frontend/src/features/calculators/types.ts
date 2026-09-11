// Mirrors backend/src/main/java/com/shardeya/foundation/calculator/dto exactly.

export interface UnitResponse {
  code: string;
  nameEn: string;
  nameHi: string;
  toSqftFactor: number;
}

export interface PlotSizeRequest {
  length: number;
  width: number;
  unit: string;
  stateCode?: string;
}

export interface PlotSizeResponse {
  sqft: number;
  sqm: number;
  sqyd: number;
  bigha: number | null;
  bighaStateApplied: string | null;
  gunta: number;
  dismil: number;
}

export interface BrokerageCalcRequest {
  dealValue: number;
  brokeragePct: number;
  ownerSharePct?: number;
  buyerSharePct?: number;
}

export interface BrokerageCalcResponse {
  total: number;
  ownerShare: number | null;
  buyerShare: number | null;
  gst: number;
  netPlusGst: number;
  sharesSumMismatch: boolean;
}

export type StampDutyPropertyType = 'RESIDENTIAL' | 'COMMERCIAL' | 'AGRICULTURAL';
export type StampDutyTransactionType = 'SALE' | 'GIFT' | 'MORTGAGE';
export type StampDutyBuyerGender = 'MALE' | 'FEMALE' | 'JOINT' | 'ANY';

export interface StampDutyCalcRequest {
  stateCode: string;
  propertyType: StampDutyPropertyType;
  transactionType: StampDutyTransactionType;
  value: number;
  buyerGender: StampDutyBuyerGender;
}

export interface StampDutyCalcResponse {
  stampDuty: number;
  registrationCharges: number;
  totalGovernmentCharges: number;
  appliedGender: StampDutyBuyerGender;
  effectiveFrom: string;
  sourceNote: string | null;
}
