import React, { createContext, useContext, useState, useEffect } from 'react';
import { 
  Project, Plot, PlotSale, PaymentSchedule, PaymentRecord, 
  Lead, Interaction, BrokerPartner, CommissionVoucher, CalendarEvent 
} from '../types/crm';

interface CrmContextType {
  // Data State
  projects: Project[];
  activeProjectId: string;
  setActiveProjectId: (id: string) => void;
  activeProject: Project | undefined;
  plots: Plot[];
  plotSales: PlotSale[];
  paymentSchedules: PaymentSchedule[];
  paymentRecords: PaymentRecord[];
  leads: Lead[];
  interactions: Interaction[];
  brokers: BrokerPartner[];
  vouchers: CommissionVoucher[];
  calendarEvents: CalendarEvent[];

  // Mutators & Actions
  addProject: (project: Omit<Project, 'id'>) => void;
  updateProject: (id: string, updates: Partial<Project>) => void;
  addPlot: (plot: Omit<Plot, 'id'>) => void;
  updatePlot: (id: string, updates: Partial<Plot>) => void;
  bulkAddPlots: (newPlots: Omit<Plot, 'id'>[]) => void;
  bookPlotSale: (data: {
    plotId: string;
    buyerName: string;
    buyerMobile: string;
    buyerEmail?: string;
    buyerGovIdType: 'AADHAAR' | 'PAN';
    buyerGovIdLast4: string;
    dealValue: number;
    paymentType: 'LUMP_SUM' | 'INSTALMENT';
    brokerPartnerId?: string;
    bookingAmount: number;
    paymentMode: 'CASH' | 'CHEQUE' | 'BANK_TRANSFER' | 'UPI';
    paymentRef: string;
  }) => void;
  recordPayment: (data: {
    plotSaleId: string;
    amount: number;
    paidOn: string;
    mode: 'CASH' | 'CHEQUE' | 'BANK_TRANSFER' | 'UPI';
    reference: string;
    receivedBy: string;
    remarks?: string;
  }) => void;
  addLead: (lead: Omit<Lead, 'id' | 'createdAt' | 'lastInteractionAt'>) => void;
  updateLeadStatus: (leadId: string, status: Lead['status']) => void;
  addLeadInteraction: (interaction: Omit<Interaction, 'id'>) => void;
  addBroker: (broker: Omit<BrokerPartner, 'id' | 'dealsClosedCount' | 'totalCommissionEarned' | 'totalCommissionPaid'>) => void;
  updateBrokerTier: (brokerId: string, tier: BrokerPartner['tier'], rate: number) => void;
  markVoucherPaid: (voucherId: string, paymentRef: string) => void;
  addCalendarEvent: (event: Omit<CalendarEvent, 'id'>) => void;
}

const SEEDED_PROJECTS: Project[] = [
  {
    id: 'proj_apex_01',
    name: 'Apex Greens Phase 1 & 2',
    projectType: 'RESIDENTIAL_PLOT_COLONY',
    status: 'ACTIVE',
    locality: 'Haridwar Corridor, Dehradun Express Highway',
    city: 'Saharanpur',
    stateCode: 'UP',
    address: 'Sector 14-A, Apex Knowledge City, Saharanpur Bypass',
    reraNumber: 'UPRERA/PRJ992182/2024',
    totalAreaValue: 42,
    totalAreaUnit: 'BIGHA',
    totalAreaSqft: 1134000,
    declaredPlotCount: 148,
    launchDate: '2024-04-15',
    expectedCompletionDate: '2026-12-31',
    description: 'Ultra-prime plotted residential township with 60ft arterial boulevard, underground cabling, private club house, and dedicated landscaped parks.',
    gridRows: 4,
    gridCols: 6,
  },
  {
    id: 'proj_royal_02',
    name: 'Royal Palm Orchards',
    projectType: 'VILLA',
    status: 'ACTIVE',
    locality: 'Sohna Elevated Corridor',
    city: 'Gurugram',
    stateCode: 'HR',
    address: 'Adjacent GD Goenka University, Sohna Road, Gurugram',
    reraNumber: 'HARERA/GGM/2025/112',
    totalAreaValue: 28,
    totalAreaUnit: 'ACRE',
    totalAreaSqft: 1219680,
    declaredPlotCount: 85,
    launchDate: '2025-01-10',
    expectedCompletionDate: '2027-06-30',
    description: 'Bespoke country villa plots nestled against the Aravali foothills with 100% power backup and 5-tier biometric perimeter security.',
    gridRows: 4,
    gridCols: 5,
  }
];

const SEEDED_PLOTS: Plot[] = [
  // Apex Greens Grid (Row 0)
  { id: 'plt_101', projectId: 'proj_apex_01', plotNumber: 'Plot #101', status: 'SOLD', sizeValue: 2150, sizeUnit: 'SQ_FT', sizeSqft: 2150, facing: 'NE', price: 4730000, pricePerSqft: 2200, isCorner: true, isGarden: false, isHot: true, gridRow: 0, gridCol: 0, currentSaleId: 'sale_01' },
  { id: 'plt_102', projectId: 'proj_apex_01', plotNumber: 'Plot #102', status: 'AVAILABLE', sizeValue: 1800, sizeUnit: 'SQ_FT', sizeSqft: 1800, facing: 'E', price: 3960000, pricePerSqft: 2200, isCorner: false, isGarden: false, isHot: false, gridRow: 0, gridCol: 1 },
  { id: 'plt_103', projectId: 'proj_apex_01', plotNumber: 'Plot #103', status: 'RESERVED', reservedFor: 'Col. Sanjeev Varma', reservedUntil: '2026-09-15', sizeValue: 2400, sizeUnit: 'SQ_FT', sizeSqft: 2400, facing: 'E', price: 5520000, pricePerSqft: 2300, isCorner: false, isGarden: true, isHot: true, gridRow: 0, gridCol: 2 },
  { id: 'plt_104', projectId: 'proj_apex_01', plotNumber: 'Plot #104', status: 'AVAILABLE', sizeValue: 2400, sizeUnit: 'SQ_FT', sizeSqft: 2400, facing: 'N', price: 5280000, pricePerSqft: 2200, isCorner: true, isGarden: false, isHot: false, gridRow: 0, gridCol: 3 },
  { id: 'plt_105', projectId: 'proj_apex_01', plotNumber: 'Plot #105', status: 'SOLD', sizeValue: 1800, sizeUnit: 'SQ_FT', sizeSqft: 1800, facing: 'N', price: 3960000, pricePerSqft: 2200, isCorner: false, isGarden: false, isHot: false, gridRow: 0, gridCol: 4, currentSaleId: 'sale_02' },
  { id: 'plt_106', projectId: 'proj_apex_01', plotNumber: 'Plot #106', status: 'AVAILABLE', sizeValue: 1800, sizeUnit: 'SQ_FT', sizeSqft: 1800, facing: 'N', price: 3960000, pricePerSqft: 2200, isCorner: false, isGarden: false, isHot: false, gridRow: 0, gridCol: 5 },

  // Row 1
  { id: 'plt_107', projectId: 'proj_apex_01', plotNumber: 'Plot #107', status: 'AVAILABLE', sizeValue: 2000, sizeUnit: 'SQ_FT', sizeSqft: 2000, facing: 'E', price: 4400000, pricePerSqft: 2200, isCorner: false, isGarden: false, isHot: false, gridRow: 1, gridCol: 0 },
  { id: 'plt_108', projectId: 'proj_apex_01', plotNumber: 'Plot #108', status: 'SOLD', sizeValue: 2000, sizeUnit: 'SQ_FT', sizeSqft: 2000, facing: 'E', price: 4400000, pricePerSqft: 2200, isCorner: false, isGarden: false, isHot: false, gridRow: 1, gridCol: 1, currentSaleId: 'sale_03' },
  { id: 'plt_109', projectId: 'proj_apex_01', plotNumber: 'Plot #109', status: 'AVAILABLE', sizeValue: 2700, sizeUnit: 'SQ_FT', sizeSqft: 2700, facing: 'E', price: 6210000, pricePerSqft: 2300, isCorner: false, isGarden: true, isHot: true, gridRow: 1, gridCol: 2 },
  { id: 'plt_110', projectId: 'proj_apex_01', plotNumber: 'Plot #110', status: 'AVAILABLE', sizeValue: 2700, sizeUnit: 'SQ_FT', sizeSqft: 2700, facing: 'W', price: 5940000, pricePerSqft: 2200, isCorner: false, isGarden: false, isHot: false, gridRow: 1, gridCol: 3 },
  { id: 'plt_111', projectId: 'proj_apex_01', plotNumber: 'Plot #111', status: 'RESERVED', reservedFor: 'Ananya Deshmukh', reservedUntil: '2026-09-14', sizeValue: 1800, sizeUnit: 'SQ_FT', sizeSqft: 1800, facing: 'W', price: 3960000, pricePerSqft: 2200, isCorner: false, isGarden: false, isHot: false, gridRow: 1, gridCol: 4 },
  { id: 'plt_112', projectId: 'proj_apex_01', plotNumber: 'Plot #112', status: 'AVAILABLE', sizeValue: 2150, sizeUnit: 'SQ_FT', sizeSqft: 2150, facing: 'NW', price: 4730000, pricePerSqft: 2200, isCorner: true, isGarden: false, isHot: false, gridRow: 1, gridCol: 5 },

  // Row 2
  { id: 'plt_113', projectId: 'proj_apex_01', plotNumber: 'Plot #113', status: 'AVAILABLE', sizeValue: 1800, sizeUnit: 'SQ_FT', sizeSqft: 1800, facing: 'SE', price: 4140000, pricePerSqft: 2300, isCorner: true, isGarden: true, isHot: false, gridRow: 2, gridCol: 0 },
  { id: 'plt_114', projectId: 'proj_apex_01', plotNumber: 'Plot #114', status: 'AVAILABLE', sizeValue: 1800, sizeUnit: 'SQ_FT', sizeSqft: 1800, facing: 'S', price: 3960000, pricePerSqft: 2200, isCorner: false, isGarden: false, isHot: false, gridRow: 2, gridCol: 1 },
  { id: 'plt_115', projectId: 'proj_apex_01', plotNumber: 'Plot #115', status: 'SOLD', sizeValue: 2400, sizeUnit: 'SQ_FT', sizeSqft: 2400, facing: 'S', price: 5280000, pricePerSqft: 2200, isCorner: false, isGarden: false, isHot: false, gridRow: 2, gridCol: 2, currentSaleId: 'sale_04' },
  { id: 'plt_116', projectId: 'proj_apex_01', plotNumber: 'Plot #116', status: 'AVAILABLE', sizeValue: 2400, sizeUnit: 'SQ_FT', sizeSqft: 2400, facing: 'S', price: 5280000, pricePerSqft: 2200, isCorner: false, isGarden: false, isHot: false, gridRow: 2, gridCol: 3 },
  { id: 'plt_117', projectId: 'proj_apex_01', plotNumber: 'Plot #117', status: 'AVAILABLE', sizeValue: 1800, sizeUnit: 'SQ_FT', sizeSqft: 1800, facing: 'S', price: 3960000, pricePerSqft: 2200, isCorner: false, isGarden: false, isHot: false, gridRow: 2, gridCol: 4 },
  { id: 'plt_118', projectId: 'proj_apex_01', plotNumber: 'Plot #118', status: 'AVAILABLE', sizeValue: 2000, sizeUnit: 'SQ_FT', sizeSqft: 2000, facing: 'SW', price: 4400000, pricePerSqft: 2200, isCorner: true, isGarden: false, isHot: false, gridRow: 2, gridCol: 5 },

  // Row 3
  { id: 'plt_119', projectId: 'proj_apex_01', plotNumber: 'Plot #119', status: 'AVAILABLE', sizeValue: 2200, sizeUnit: 'SQ_FT', sizeSqft: 2200, facing: 'N', price: 4840000, pricePerSqft: 2200, isCorner: false, isGarden: false, isHot: false, gridRow: 3, gridCol: 0 },
  { id: 'plt_120', projectId: 'proj_apex_01', plotNumber: 'Plot #120', status: 'AVAILABLE', sizeValue: 2200, sizeUnit: 'SQ_FT', sizeSqft: 2200, facing: 'N', price: 4840000, pricePerSqft: 2200, isCorner: false, isGarden: false, isHot: false, gridRow: 3, gridCol: 1 },
  { id: 'plt_121', projectId: 'proj_apex_01', plotNumber: 'Plot #121', status: 'AVAILABLE', sizeValue: 3000, sizeUnit: 'SQ_FT', sizeSqft: 3000, facing: 'NE', price: 6900000, pricePerSqft: 2300, isCorner: true, isGarden: true, isHot: true, gridRow: 3, gridCol: 2 },
  { id: 'plt_122', projectId: 'proj_apex_01', plotNumber: 'Plot #122', status: 'AVAILABLE', sizeValue: 3000, sizeUnit: 'SQ_FT', sizeSqft: 3000, facing: 'NW', price: 6900000, pricePerSqft: 2300, isCorner: true, isGarden: true, isHot: true, gridRow: 3, gridCol: 3 },
  { id: 'plt_123', projectId: 'proj_apex_01', plotNumber: 'Plot #123', status: 'AVAILABLE', sizeValue: 2200, sizeUnit: 'SQ_FT', sizeSqft: 2200, facing: 'N', price: 4840000, pricePerSqft: 2200, isCorner: false, isGarden: false, isHot: false, gridRow: 3, gridCol: 4 },
  { id: 'plt_124', projectId: 'proj_apex_01', plotNumber: 'Plot #124', status: 'AVAILABLE', sizeValue: 2200, sizeUnit: 'SQ_FT', sizeSqft: 2200, facing: 'N', price: 4840000, pricePerSqft: 2200, isCorner: false, isGarden: false, isHot: false, gridRow: 3, gridCol: 5 },
];

const SEEDED_SALES: PlotSale[] = [
  {
    id: 'sale_01',
    plotId: 'plt_101',
    projectId: 'proj_apex_01',
    buyerName: 'Dr. Harshvardhan Kapoor',
    buyerMobile: '+91 98101 23456',
    buyerEmail: 'h.kapoor@fortis.com',
    buyerGovIdType: 'PAN',
    buyerGovIdLast4: '8812',
    purchaseDate: '2024-05-10',
    dealValue: 4730000,
    paymentType: 'INSTALMENT',
    status: 'ACTIVE',
    brokerPartnerId: 'brk_diamond_01',
    brokerCommissionAmount: 118250,
    totalPaid: 3311000,
    balanceDue: 1419000,
    allotmentLetterNo: 'APEX/2024/PLT-101',
  },
  {
    id: 'sale_02',
    plotId: 'plt_105',
    projectId: 'proj_apex_01',
    buyerName: 'Smt. Kavita Mittal',
    buyerMobile: '+91 98290 87654',
    buyerGovIdType: 'AADHAAR',
    buyerGovIdLast4: '4190',
    purchaseDate: '2024-06-18',
    dealValue: 3960000,
    paymentType: 'LUMP_SUM',
    status: 'COMPLETED',
    brokerPartnerId: 'brk_cityline_02',
    brokerCommissionAmount: 79200,
    totalPaid: 3960000,
    balanceDue: 0,
    allotmentLetterNo: 'APEX/2024/PLT-105',
  },
  {
    id: 'sale_03',
    plotId: 'plt_108',
    projectId: 'proj_apex_01',
    buyerName: 'Col. Sanjeev Varma',
    buyerMobile: '+91 94120 54321',
    buyerGovIdType: 'PAN',
    buyerGovIdLast4: '9920',
    purchaseDate: '2024-08-01',
    dealValue: 4400000,
    paymentType: 'INSTALMENT',
    status: 'ACTIVE',
    brokerPartnerId: 'brk_diamond_01',
    brokerCommissionAmount: 110000,
    totalPaid: 2200000,
    balanceDue: 2200000,
    allotmentLetterNo: 'APEX/2024/PLT-108',
  },
  {
    id: 'sale_04',
    plotId: 'plt_115',
    projectId: 'proj_apex_01',
    buyerName: 'Ramanathan Iyer',
    buyerMobile: '+91 97110 33221',
    buyerGovIdType: 'PAN',
    buyerGovIdLast4: '3412',
    purchaseDate: '2024-07-20',
    dealValue: 5280000,
    paymentType: 'INSTALMENT',
    status: 'ACTIVE',
    totalPaid: 3696000,
    balanceDue: 1584000,
    allotmentLetterNo: 'APEX/2024/PLT-115',
  }
];

const SEEDED_SCHEDULES: PaymentSchedule[] = [
  // sale_01 schedules
  { id: 'sch_01_1', plotSaleId: 'sale_01', sequenceNo: 1, label: 'Booking Token (10%)', expectedAmount: 473000, dueDate: '2024-05-10', status: 'PAID', amountAllocated: 473000 },
  { id: 'sch_01_2', plotSaleId: 'sale_01', sequenceNo: 2, label: 'Boundary Demarcation (30%)', expectedAmount: 1419000, dueDate: '2024-07-15', status: 'PAID', amountAllocated: 1419000 },
  { id: 'sch_01_3', plotSaleId: 'sale_01', sequenceNo: 3, label: 'Road & Drainage Network (30%)', expectedAmount: 1419000, dueDate: '2024-10-30', status: 'PAID', amountAllocated: 1419000 },
  { id: 'sch_01_4', plotSaleId: 'sale_01', sequenceNo: 4, label: 'Final Sub-Registrar Deed (30%)', expectedAmount: 1419000, dueDate: '2026-08-15', status: 'OVERDUE', amountAllocated: 0, daysOverdue: 27 },

  // sale_03 schedules
  { id: 'sch_03_1', plotSaleId: 'sale_03', sequenceNo: 1, label: 'Booking Token (20%)', expectedAmount: 880000, dueDate: '2024-08-01', status: 'PAID', amountAllocated: 880000 },
  { id: 'sch_03_2', plotSaleId: 'sale_03', sequenceNo: 2, label: 'Internal Electrification (30%)', expectedAmount: 1320000, dueDate: '2024-11-15', status: 'PAID', amountAllocated: 1320000 },
  { id: 'sch_03_3', plotSaleId: 'sale_03', sequenceNo: 3, label: 'Streetlighting & Club (25%)', expectedAmount: 1100000, dueDate: '2026-08-30', status: 'OVERDUE', amountAllocated: 0, daysOverdue: 12 },
  { id: 'sch_03_4', plotSaleId: 'sale_03', sequenceNo: 4, label: 'Final Allotment & Possession (25%)', expectedAmount: 1100000, dueDate: '2026-11-30', status: 'PENDING', amountAllocated: 0 },
];

const SEEDED_PAYMENTS: PaymentRecord[] = [
  { id: 'pay_01', plotSaleId: 'sale_01', projectId: 'proj_apex_01', receiptNo: 'RCP/24-25/001', amount: 473000, paidOn: '2024-05-10', mode: 'BANK_TRANSFER', reference: 'UTR-HDFC00991203', receivedBy: 'Accounts Director', remarks: 'Booking token credited to Escrow' },
  { id: 'pay_02', plotSaleId: 'sale_01', projectId: 'proj_apex_01', receiptNo: 'RCP/24-25/034', amount: 1419000, paidOn: '2024-07-15', mode: 'BANK_TRANSFER', reference: 'UTR-ICIC88192031', receivedBy: 'Accounts Director', remarks: 'Milestone 2 Demarcation verified' },
  { id: 'pay_03', plotSaleId: 'sale_01', projectId: 'proj_apex_01', receiptNo: 'RCP/24-25/082', amount: 1419000, paidOn: '2024-10-30', mode: 'CHEQUE', reference: 'CHQ-882190 (SBI)', receivedBy: 'Front Desk Officer', remarks: 'Cleared on 02-Nov-2024' },
  { id: 'pay_04', plotSaleId: 'sale_02', projectId: 'proj_apex_01', receiptNo: 'RCP/24-25/042', amount: 3960000, paidOn: '2024-06-18', mode: 'BANK_TRANSFER', reference: 'UTR-SBIN00441928', receivedBy: 'Accounts Director', remarks: 'Full lump sum registry payment' },
];

const SEEDED_LEADS: Lead[] = [
  { id: 'lead_01', fullName: 'Vikrant Goel', mobile: '+91 98119 44332', email: 'vikrant@goelsteels.com', budgetMin: 4000000, budgetMax: 5500000, preferredPropertyType: 'PLOT', source: 'BROKER', sourceBrokerId: 'brk_diamond_01', status: 'SITE_VISIT_SCHEDULED', interestedProjectId: 'proj_apex_01', assignedTo: 'Rajeshwar Singhania', followUpDate: '2026-09-12', isImportant: true, remarks: 'Wants 250 Gaj park facing corner plot. Prefers immediate registry.', lastInteractionAt: '2026-09-10T14:30:00Z', createdAt: '2026-09-08' },
  { id: 'lead_02', fullName: 'Dr. Archana Sen', mobile: '+91 98711 00293', email: 'dr.archana@aiims.edu', budgetMin: 5000000, budgetMax: 7000000, preferredPropertyType: 'PLOT', source: 'REFERRAL', status: 'FOLLOWING_UP', interestedProjectId: 'proj_apex_01', assignedTo: 'Rajeshwar Singhania', followUpDate: '2026-09-14', isImportant: true, remarks: 'Site visit completed on Sunday. Negotiating 2% club waiver.', lastInteractionAt: '2026-09-09T11:00:00Z', createdAt: '2026-09-01' },
  { id: 'lead_03', fullName: 'Pradeep Chawla', mobile: '+91 99991 88273', email: 'chawla.p@exportcorp.in', budgetMin: 3500000, budgetMax: 4500000, preferredPropertyType: 'PLOT', source: 'FACEBOOK', status: 'INTERESTED', interestedProjectId: 'proj_apex_01', assignedTo: 'Rajeshwar Singhania', followUpDate: '2026-09-13', isImportant: false, remarks: 'Inquired from Delhi NCR. Shared brochure & layout video on WhatsApp.', lastInteractionAt: '2026-09-10T16:45:00Z', createdAt: '2026-09-10' },
  { id: 'lead_04', fullName: 'Manish Tibrewal', mobile: '+91 98200 44921', email: 'manish@tibrewal.org', budgetMin: 8000000, budgetMax: 12000000, preferredPropertyType: 'VILLA', source: 'WALK_IN', status: 'SITE_VISIT_DONE', interestedProjectId: 'proj_royal_02', assignedTo: 'Rajeshwar Singhania', followUpDate: '2026-09-16', isImportant: true, remarks: 'Visited Sohna site with family. Comparing with DLF plots.', lastInteractionAt: '2026-09-07T12:00:00Z', createdAt: '2026-08-28' },
  { id: 'lead_05', fullName: 'Sunita Aggarwal', mobile: '+91 98105 77218', budgetMin: 3800000, budgetMax: 4200000, preferredPropertyType: 'PLOT', source: 'BROKER', sourceBrokerId: 'brk_cityline_02', status: 'DEAL_CLOSED', interestedProjectId: 'proj_apex_01', assignedTo: 'Rajeshwar Singhania', followUpDate: '2026-08-20', isImportant: false, remarks: 'Converted to Plot #105 allotment.', lastInteractionAt: '2026-08-20T10:00:00Z', createdAt: '2026-08-10' },
  { id: 'lead_06', fullName: 'Kishore Jha', mobile: '+91 97170 12890', budgetMin: 3000000, budgetMax: 3500000, preferredPropertyType: 'PLOT', source: 'WEBSITE', status: 'LOST', interestedProjectId: 'proj_apex_01', assignedTo: 'Rajeshwar Singhania', followUpDate: '2026-09-02', isImportant: false, remarks: 'Budget constraint. Purchased smaller agricultural land elsewhere.', lastInteractionAt: '2026-09-02T15:00:00Z', createdAt: '2026-08-15' },
];

const SEEDED_INTERACTIONS: Interaction[] = [
  { id: 'int_01', customerId: 'lead_01', occurredOn: '2026-09-10', type: 'CALL', remarks: 'Confirmed Sunday 11 AM site visit with luxury chauffeur pickup from Saharanpur junction.', result: 'NEXT_SCHEDULED', conductedBy: 'Rajeshwar Singhania' },
  { id: 'int_02', customerId: 'lead_01', occurredOn: '2026-09-08', type: 'WHATSAPP', remarks: 'Dispatched masterplan cadastral map, UPRERA registration copy, and price quote.', result: 'POSITIVE', conductedBy: 'Diamond CP Desk' },
  { id: 'int_03', customerId: 'lead_02', occurredOn: '2026-09-09', type: 'VISIT', remarks: 'Walked the physical site. Inspected Plot #103 & #104. Verified underground cabling and sewer lines.', result: 'POSITIVE', conductedBy: 'Rajeshwar Singhania' },
];

const SEEDED_BROKERS: BrokerPartner[] = [
  {
    id: 'brk_diamond_01',
    fullName: 'Vikram Malhotra',
    mobile: '+91 98111 22334',
    email: 'partner@apexrealty.com',
    firmName: 'Diamond Channel Syndicate',
    cityArea: 'Delhi NCR & Western UP',
    reraNumber: 'MAHARERA/A51800001234',
    commissionType: 'PERCENTAGE',
    commissionRate: 2.5,
    tier: 'Platinum',
    dealsClosedCount: 28,
    totalCommissionEarned: 6420000,
    totalCommissionPaid: 5850000,
    status: 'ACTIVE'
  },
  {
    id: 'brk_cityline_02',
    fullName: 'Ankit Sharma',
    mobile: '+91 98290 11223',
    email: 'ankit@citylinerealty.in',
    firmName: 'Cityline Associates',
    cityArea: 'Saharanpur & Dehradun',
    reraNumber: 'UPRERA/A/2023/8812',
    commissionType: 'PERCENTAGE',
    commissionRate: 2.0,
    tier: 'Gold',
    dealsClosedCount: 14,
    totalCommissionEarned: 2840000,
    totalCommissionPaid: 2500000,
    status: 'ACTIVE'
  },
  {
    id: 'brk_urban_03',
    fullName: 'Rohan Mehta',
    mobile: '+91 99100 88776',
    email: 'rohan@urbannest.in',
    firmName: 'Urban Nest Properties',
    cityArea: 'Gurugram Golf Course Extn',
    reraNumber: 'HARERA/A/GGM/2024/09',
    commissionType: 'PERCENTAGE',
    commissionRate: 1.5,
    tier: 'Silver',
    dealsClosedCount: 6,
    totalCommissionEarned: 980000,
    totalCommissionPaid: 980000,
    status: 'ACTIVE'
  }
];

const SEEDED_VOUCHERS: CommissionVoucher[] = [
  { id: 'vch_01', voucherNo: 'VCH/24-25/012', brokerId: 'brk_diamond_01', plotSaleId: 'sale_01', plotNumber: 'Plot #101', projectName: 'Apex Greens Phase 1 & 2', dealValue: 4730000, commissionAmount: 118250, tdsDeduction: 5912.5, netPayable: 112337.5, dealDate: '2024-05-10', status: 'PAID', paymentRef: 'NEFT-5519201' },
  { id: 'vch_02', voucherNo: 'VCH/24-25/028', brokerId: 'brk_cityline_02', plotSaleId: 'sale_02', plotNumber: 'Plot #105', projectName: 'Apex Greens Phase 1 & 2', dealValue: 3960000, commissionAmount: 79200, tdsDeduction: 3960, netPayable: 75240, dealDate: '2024-06-18', status: 'PAID', paymentRef: 'RTGS-8812033' },
  { id: 'vch_03', voucherNo: 'VCH/24-25/045', brokerId: 'brk_diamond_01', plotSaleId: 'sale_03', plotNumber: 'Plot #108', projectName: 'Apex Greens Phase 1 & 2', dealValue: 4400000, commissionAmount: 110000, tdsDeduction: 5500, netPayable: 104500, dealDate: '2024-08-01', status: 'APPROVED' },
];

const SEEDED_EVENTS: CalendarEvent[] = [
  { id: 'evt_01', title: 'Site Inspection with Vikrant Goel (Corner Plot #104)', eventDate: '2026-09-12', eventTime: '11:00 AM', eventType: 'SITE_VISIT', entityName: 'Vikrant Goel', contactNumber: '+91 98119 44332', assignedTo: 'Rajeshwar Singhania', status: 'SCHEDULED' },
  { id: 'evt_02', title: 'Registry Deed Execution for Plot #105 at Sub-Registrar Office', eventDate: '2026-09-15', eventTime: '02:30 PM', eventType: 'REGISTRY', entityName: 'Kavita Mittal', contactNumber: '+91 98290 87654', assignedTo: 'Legal Team', status: 'SCHEDULED' },
  { id: 'evt_03', title: 'Overdue Follow-up Call: Plot #101 Final Instalment', eventDate: '2026-09-13', eventTime: '10:30 AM', eventType: 'INSTALMENT_DUE', entityName: 'Dr. Harshvardhan Kapoor', contactNumber: '+91 98101 23456', assignedTo: 'Accounts Director', status: 'SCHEDULED' },
];

const CrmContext = createContext<CrmContextType | undefined>(undefined);

export const CrmProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [projects, setProjects] = useState<Project[]>(() => {
    const saved = localStorage.getItem('shardeya_crm_projects');
    return saved ? JSON.parse(saved) : SEEDED_PROJECTS;
  });

  const [activeProjectId, setActiveProjectId] = useState<string>(projects[0]?.id || 'proj_apex_01');

  const [plots, setPlots] = useState<Plot[]>(() => {
    const saved = localStorage.getItem('shardeya_crm_plots');
    return saved ? JSON.parse(saved) : SEEDED_PLOTS;
  });

  const [plotSales, setPlotSales] = useState<PlotSale[]>(() => {
    const saved = localStorage.getItem('shardeya_crm_sales');
    return saved ? JSON.parse(saved) : SEEDED_SALES;
  });

  const [paymentSchedules, setPaymentSchedules] = useState<PaymentSchedule[]>(() => {
    const saved = localStorage.getItem('shardeya_crm_schedules');
    return saved ? JSON.parse(saved) : SEEDED_SCHEDULES;
  });

  const [paymentRecords, setPaymentRecords] = useState<PaymentRecord[]>(() => {
    const saved = localStorage.getItem('shardeya_crm_payments');
    return saved ? JSON.parse(saved) : SEEDED_PAYMENTS;
  });

  const [leads, setLeads] = useState<Lead[]>(() => {
    const saved = localStorage.getItem('shardeya_crm_leads');
    return saved ? JSON.parse(saved) : SEEDED_LEADS;
  });

  const [interactions, setInteractions] = useState<Interaction[]>(() => {
    const saved = localStorage.getItem('shardeya_crm_interactions');
    return saved ? JSON.parse(saved) : SEEDED_INTERACTIONS;
  });

  const [brokers, setBrokers] = useState<BrokerPartner[]>(() => {
    const saved = localStorage.getItem('shardeya_crm_brokers');
    return saved ? JSON.parse(saved) : SEEDED_BROKERS;
  });

  const [vouchers, setVouchers] = useState<CommissionVoucher[]>(() => {
    const saved = localStorage.getItem('shardeya_crm_vouchers');
    return saved ? JSON.parse(saved) : SEEDED_VOUCHERS;
  });

  const [calendarEvents, setCalendarEvents] = useState<CalendarEvent[]>(() => {
    const saved = localStorage.getItem('shardeya_crm_events');
    return saved ? JSON.parse(saved) : SEEDED_EVENTS;
  });

  // Sync to local storage
  useEffect(() => { localStorage.setItem('shardeya_crm_projects', JSON.stringify(projects)); }, [projects]);
  useEffect(() => { localStorage.setItem('shardeya_crm_plots', JSON.stringify(plots)); }, [plots]);
  useEffect(() => { localStorage.setItem('shardeya_crm_sales', JSON.stringify(plotSales)); }, [plotSales]);
  useEffect(() => { localStorage.setItem('shardeya_crm_schedules', JSON.stringify(paymentSchedules)); }, [paymentSchedules]);
  useEffect(() => { localStorage.setItem('shardeya_crm_payments', JSON.stringify(paymentRecords)); }, [paymentRecords]);
  useEffect(() => { localStorage.setItem('shardeya_crm_leads', JSON.stringify(leads)); }, [leads]);
  useEffect(() => { localStorage.setItem('shardeya_crm_interactions', JSON.stringify(interactions)); }, [interactions]);
  useEffect(() => { localStorage.setItem('shardeya_crm_brokers', JSON.stringify(brokers)); }, [brokers]);
  useEffect(() => { localStorage.setItem('shardeya_crm_vouchers', JSON.stringify(vouchers)); }, [vouchers]);
  useEffect(() => { localStorage.setItem('shardeya_crm_events', JSON.stringify(calendarEvents)); }, [calendarEvents]);

  const activeProject = projects.find((p) => p.id === activeProjectId);

  const addProject = (data: Omit<Project, 'id'>) => {
    const newProj: Project = { ...data, id: `proj_${Date.now()}` };
    setProjects((prev) => [newProj, ...prev]);
    setActiveProjectId(newProj.id);
  };

  const updateProject = (id: string, updates: Partial<Project>) => {
    setProjects((prev) => prev.map((p) => p.id === id ? { ...p, ...updates } : p));
  };

  const addPlot = (data: Omit<Plot, 'id'>) => {
    const newPlot: Plot = { ...data, id: `plt_${Date.now()}` };
    setPlots((prev) => [...prev, newPlot]);
  };

  const updatePlot = (id: string, updates: Partial<Plot>) => {
    setPlots((prev) => prev.map((p) => p.id === id ? { ...p, ...updates } : p));
  };

  const bulkAddPlots = (newPlotsData: Omit<Plot, 'id'>[]) => {
    const created: Plot[] = newPlotsData.map((d, i) => ({ ...d, id: `plt_${Date.now()}_${i}` }));
    setPlots((prev) => [...prev, ...created]);
  };

  const bookPlotSale = (data: {
    plotId: string;
    buyerName: string;
    buyerMobile: string;
    buyerEmail?: string;
    buyerGovIdType: 'AADHAAR' | 'PAN';
    buyerGovIdLast4: string;
    dealValue: number;
    paymentType: 'LUMP_SUM' | 'INSTALMENT';
    brokerPartnerId?: string;
    bookingAmount: number;
    paymentMode: 'CASH' | 'CHEQUE' | 'BANK_TRANSFER' | 'UPI';
    paymentRef: string;
  }) => {
    const saleId = `sale_${Date.now()}`;
    const targetPlot = plots.find((p) => p.id === data.plotId);
    if (!targetPlot) return;

    // Calculate commission if broker linked
    let brokerComm = 0;
    if (data.brokerPartnerId) {
      const broker = brokers.find((b) => b.id === data.brokerPartnerId);
      if (broker) {
        brokerComm = broker.commissionType === 'PERCENTAGE' 
          ? (data.dealValue * broker.commissionRate) / 100 
          : broker.commissionRate;
      }
    }

    const newSale: PlotSale = {
      id: saleId,
      plotId: data.plotId,
      projectId: targetPlot.projectId,
      buyerName: data.buyerName,
      buyerMobile: data.buyerMobile,
      buyerEmail: data.buyerEmail,
      buyerGovIdType: data.buyerGovIdType,
      buyerGovIdLast4: data.buyerGovIdLast4,
      purchaseDate: new Date().toISOString().split('T')[0],
      dealValue: data.dealValue,
      paymentType: data.paymentType,
      status: 'ACTIVE',
      brokerPartnerId: data.brokerPartnerId,
      brokerCommissionAmount: brokerComm,
      totalPaid: data.bookingAmount,
      balanceDue: data.dealValue - data.bookingAmount,
      allotmentLetterNo: `APEX/${new Date().getFullYear()}/${targetPlot.plotNumber.replace(/[^A-Za-z0-9]/g, '')}`,
    };

    // Update plot status
    setPlots((prev) => prev.map((p) => p.id === data.plotId ? { ...p, status: 'SOLD', currentSaleId: saleId } : p));
    setPlotSales((prev) => [newSale, ...prev]);

    // Create payment record for booking amount
    if (data.bookingAmount > 0) {
      const newPay: PaymentRecord = {
        id: `pay_${Date.now()}`,
        plotSaleId: saleId,
        projectId: targetPlot.projectId,
        receiptNo: `RCP/${new Date().getFullYear()}/${Math.floor(1000 + Math.random() * 9000)}`,
        amount: data.bookingAmount,
        paidOn: new Date().toISOString().split('T')[0],
        mode: data.paymentMode,
        reference: data.paymentRef,
        receivedBy: 'Chief Operations Director',
        remarks: 'Initial Booking Token'
      };
      setPaymentRecords((prev) => [newPay, ...prev]);
    }

    // Generate instalment schedule milestones if instalment mode
    if (data.paymentType === 'INSTALMENT') {
      const remaining = data.dealValue - data.bookingAmount;
      const milestone2 = Math.round(remaining * 0.35);
      const milestone3 = Math.round(remaining * 0.35);
      const milestone4 = remaining - (milestone2 + milestone3);

      const d = new Date();
      const m2Date = new Date(d.getFullYear(), d.getMonth() + 2, 15).toISOString().split('T')[0];
      const m3Date = new Date(d.getFullYear(), d.getMonth() + 5, 15).toISOString().split('T')[0];
      const m4Date = new Date(d.getFullYear(), d.getMonth() + 9, 15).toISOString().split('T')[0];

      const schedules: PaymentSchedule[] = [
        { id: `sch_${saleId}_1`, plotSaleId: saleId, sequenceNo: 1, label: 'Booking Token Deposit', expectedAmount: data.bookingAmount, dueDate: newSale.purchaseDate, status: 'PAID', amountAllocated: data.bookingAmount },
        { id: `sch_${saleId}_2`, plotSaleId: saleId, sequenceNo: 2, label: 'Road Demarcation & Drainage (35%)', expectedAmount: milestone2, dueDate: m2Date, status: 'PENDING', amountAllocated: 0 },
        { id: `sch_${saleId}_3`, plotSaleId: saleId, sequenceNo: 3, label: 'Electrification & Water Supply (35%)', expectedAmount: milestone3, dueDate: m3Date, status: 'PENDING', amountAllocated: 0 },
        { id: `sch_${saleId}_4`, plotSaleId: saleId, sequenceNo: 4, label: 'Final Sub-Registrar Deed & Registry (30%)', expectedAmount: milestone4, dueDate: m4Date, status: 'PENDING', amountAllocated: 0 },
      ];
      setPaymentSchedules((prev) => [...prev, ...schedules]);
    }

    // Create Commission Voucher if broker involved
    if (data.brokerPartnerId && brokerComm > 0) {
      const newVoucher: CommissionVoucher = {
        id: `vch_${Date.now()}`,
        voucherNo: `VCH/${new Date().getFullYear()}/${Math.floor(100 + Math.random() * 900)}`,
        brokerId: data.brokerPartnerId,
        plotSaleId: saleId,
        plotNumber: targetPlot.plotNumber,
        projectName: activeProject?.name || 'Apex Greens',
        dealValue: data.dealValue,
        commissionAmount: brokerComm,
        tdsDeduction: brokerComm * 0.05,
        netPayable: brokerComm * 0.95,
        dealDate: newSale.purchaseDate,
        status: 'PENDING',
      };
      setVouchers((prev) => [newVoucher, ...prev]);

      // Update broker stats
      setBrokers((prev) => prev.map((b) => b.id === data.brokerPartnerId ? {
        ...b,
        dealsClosedCount: b.dealsClosedCount + 1,
        totalCommissionEarned: b.totalCommissionEarned + brokerComm
      } : b));
    }
  };

  const recordPayment = (data: {
    plotSaleId: string;
    amount: number;
    paidOn: string;
    mode: 'CASH' | 'CHEQUE' | 'BANK_TRANSFER' | 'UPI';
    reference: string;
    receivedBy: string;
    remarks?: string;
  }) => {
    const sale = plotSales.find((s) => s.id === data.plotSaleId);
    if (!sale) return;

    const receiptNo = `RCP/${new Date().getFullYear()}/${Math.floor(1000 + Math.random() * 9000)}`;
    const newRecord: PaymentRecord = {
      id: `pay_${Date.now()}`,
      plotSaleId: data.plotSaleId,
      projectId: sale.projectId,
      receiptNo,
      amount: data.amount,
      paidOn: data.paidOn,
      mode: data.mode,
      reference: data.reference,
      receivedBy: data.receivedBy,
      remarks: data.remarks
    };

    setPaymentRecords((prev) => [newRecord, ...prev]);

    // Update sale total paid & balance
    const updatedPaid = sale.totalPaid + data.amount;
    const updatedBalance = Math.max(0, sale.dealValue - updatedPaid);
    const updatedStatus = updatedBalance === 0 ? 'COMPLETED' : 'ACTIVE';

    setPlotSales((prev) => prev.map((s) => s.id === data.plotSaleId ? {
      ...s,
      totalPaid: updatedPaid,
      balanceDue: updatedBalance,
      status: updatedStatus
    } : s));

    // Allocate payment into schedules
    let unallocated = data.amount;
    setPaymentSchedules((prev) => prev.map((sch) => {
      if (sch.plotSaleId !== data.plotSaleId || sch.status === 'PAID') return sch;
      if (unallocated <= 0) return sch;

      const needed = sch.expectedAmount - sch.amountAllocated;
      if (unallocated >= needed) {
        unallocated -= needed;
        return { ...sch, amountAllocated: sch.expectedAmount, status: 'PAID' };
      } else {
        const newAllocated = sch.amountAllocated + unallocated;
        unallocated = 0;
        return { ...sch, amountAllocated: newAllocated, status: 'PARTIALLY_PAID' };
      }
    }));
  };

  const addLead = (data: Omit<Lead, 'id' | 'createdAt' | 'lastInteractionAt'>) => {
    const newLead: Lead = {
      ...data,
      id: `lead_${Date.now()}`,
      createdAt: new Date().toISOString().split('T')[0],
      lastInteractionAt: new Date().toISOString(),
    };
    setLeads((prev) => [newLead, ...prev]);
  };

  const updateLeadStatus = (leadId: string, status: Lead['status']) => {
    setLeads((prev) => prev.map((l) => l.id === leadId ? { ...l, status, lastInteractionAt: new Date().toISOString() } : l));
  };

  const addLeadInteraction = (data: Omit<Interaction, 'id'>) => {
    const newInt: Interaction = { ...data, id: `int_${Date.now()}` };
    setInteractions((prev) => [newInt, ...prev]);
    setLeads((prev) => prev.map((l) => l.id === data.customerId ? { ...l, lastInteractionAt: new Date().toISOString() } : l));
  };

  const addBroker = (data: Omit<BrokerPartner, 'id' | 'dealsClosedCount' | 'totalCommissionEarned' | 'totalCommissionPaid'>) => {
    const newBroker: BrokerPartner = {
      ...data,
      id: `brk_${Date.now()}`,
      dealsClosedCount: 0,
      totalCommissionEarned: 0,
      totalCommissionPaid: 0,
    };
    setBrokers((prev) => [newBroker, ...prev]);
  };

  const updateBrokerTier = (brokerId: string, tier: BrokerPartner['tier'], rate: number) => {
    setBrokers((prev) => prev.map((b) => b.id === brokerId ? { ...b, tier, commissionRate: rate } : b));
  };

  const markVoucherPaid = (voucherId: string, paymentRef: string) => {
    setVouchers((prev) => prev.map((v) => v.id === voucherId ? { ...v, status: 'PAID', paymentRef } : v));
  };

  const addCalendarEvent = (data: Omit<CalendarEvent, 'id'>) => {
    const newEvt: CalendarEvent = { ...data, id: `evt_${Date.now()}` };
    setCalendarEvents((prev) => [...prev, newEvt]);
  };

  return (
    <CrmContext.Provider
      value={{
        projects,
        activeProjectId,
        setActiveProjectId,
        activeProject,
        plots,
        plotSales,
        paymentSchedules,
        paymentRecords,
        leads,
        interactions,
        brokers,
        vouchers,
        calendarEvents,

        addProject,
        updateProject,
        addPlot,
        updatePlot,
        bulkAddPlots,
        bookPlotSale,
        recordPayment,
        addLead,
        updateLeadStatus,
        addLeadInteraction,
        addBroker,
        updateBrokerTier,
        markVoucherPaid,
        addCalendarEvent,
      }}
    >
      {children}
    </CrmContext.Provider>
  );
};

export const useCrm = () => {
  const context = useContext(CrmContext);
  if (!context) {
    throw new Error('useCrm must be used within a CrmProvider');
  }
  return context;
};
