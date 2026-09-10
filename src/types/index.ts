export type Language = 'en' | 'hi';

export type UnitStatus = 'available' | 'booked' | 'negotiation' | 'reserved';

export type UnitType = 'Villa Plot' | 'Apartment' | 'Penthouse' | 'Commercial Shop';

export interface UnitData {
  id: string;
  number: string;
  type: UnitType;
  areaSqFt: number;
  facing: string;
  floor?: string;
  priceTotal: number; // in INR
  status: UnitStatus;
  buyer?: {
    name: string;
    phone: string;
    kycStatus: 'Verified' | 'Pending' | 'In Review';
    amountPaid: number;
    totalMilestones: number;
    completedMilestones: number;
    nextInstallmentDue: string;
    nextAmount: number;
  };
  broker?: {
    name: string;
    tier: 'Bronze' | 'Silver' | 'Gold' | 'Diamond Syndicate';
    commissionRate: number; // percentage
    commissionEarned: number;
    phone: string;
  };
}

export interface BrokerTier {
  name: string;
  minSalesCr: number;
  baseCommissionPercent: number;
  bonusPercent: number;
  badgeColor: string;
  perks: string[];
}

export interface WhatsAppMessage {
  id: string;
  sender: 'bot' | 'client';
  textEn: string;
  textHi: string;
  time: string;
  type?: 'text' | 'map' | 'payment_link' | 'calendar';
  mapTitle?: string;
  amount?: string;
}

export interface PricingPlan {
  id: string;
  nameEn: string;
  nameHi: string;
  taglineEn: string;
  taglineHi: string;
  monthlyPrice: number;
  annualPriceMonthly: number;
  popular?: boolean;
  featuresEn: string[];
  featuresHi: string[];
}
