import React, { createContext, useContext, useState, useEffect } from 'react';
import { 
  Project, Plot, PlotSale, PaymentSchedule, PaymentRecord, 
  Lead, Interaction, BrokerPartner, CommissionVoucher, CalendarEvent 
} from '../types/crm';
import { api } from '../services/api';

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
  isLoading: boolean;
  refreshData: () => Promise<void>;

  // Mutators & Actions
  addProject: (project: Omit<Project, 'id'>) => Promise<void>;
  updateProject: (id: string, updates: Partial<Project>) => void;
  addPlot: (plot: Omit<Plot, 'id'>) => Promise<void>;
  updatePlot: (id: string, updates: Partial<Plot>) => Promise<void>;
  bulkAddPlots: (newPlots: Omit<Plot, 'id'>[]) => Promise<void>;
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
  }) => Promise<void>;
  recordPayment: (data: {
    plotSaleId: string;
    amount: number;
    paidOn: string;
    mode: 'CASH' | 'CHEQUE' | 'BANK_TRANSFER' | 'UPI';
    reference: string;
    receivedBy: string;
    remarks?: string;
  }) => Promise<void>;
  addLead: (lead: Omit<Lead, 'id' | 'createdAt' | 'lastInteractionAt'>) => Promise<void>;
  updateLeadStatus: (leadId: string, status: Lead['status']) => Promise<void>;
  addLeadInteraction: (interaction: Omit<Interaction, 'id'>) => Promise<void>;
  addBroker: (broker: Omit<BrokerPartner, 'id' | 'dealsClosedCount' | 'totalCommissionEarned' | 'totalCommissionPaid'>) => Promise<void>;
  updateBrokerTier: (brokerId: string, tier: BrokerPartner['tier'], rate: number) => Promise<void>;
  markVoucherPaid: (voucherId: string, paymentRef: string) => Promise<void>;
  addCalendarEvent: (event: Omit<CalendarEvent, 'id'>) => Promise<void>;
}

const CrmContext = createContext<CrmContextType | undefined>(undefined);

export const CrmProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [projects, setProjects] = useState<Project[]>([]);
  const [activeProjectId, setActiveProjectId] = useState<string>('');
  const [plots, setPlots] = useState<Plot[]>([]);
  const [plotSales, setPlotSales] = useState<PlotSale[]>([]);
  const [paymentSchedules, setPaymentSchedules] = useState<PaymentSchedule[]>([]);
  const [paymentRecords, setPaymentRecords] = useState<PaymentRecord[]>([]);
  const [leads, setLeads] = useState<Lead[]>([]);
  const [interactions, setInteractions] = useState<Interaction[]>([]);
  const [brokers, setBrokers] = useState<BrokerPartner[]>([]);
  const [vouchers, setVouchers] = useState<CommissionVoucher[]>([]);
  const [calendarEvents, setCalendarEvents] = useState<CalendarEvent[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  // Fetch all real data from backend SQLite database
  const refreshData = async () => {
    try {
      setIsLoading(true);
      const [
        projs,
        allPlots,
        allSales,
        allScheds,
        allPayments,
        allLeads,
        allBrokers,
        allVouchers,
        allEvents
      ] = await Promise.all([
        api.projects.getAll().catch(() => []),
        api.plots.getAll().catch(() => []),
        api.sales.getAll().catch(() => []),
        api.payments.getSchedules().catch(() => []),
        api.payments.getAll().catch(() => []),
        api.leads.getAll().catch(() => []),
        api.brokers.getAll().catch(() => []),
        api.vouchers.getAll().catch(() => []),
        api.calendar.getAll().catch(() => [])
      ]);

      setProjects(projs);
      if (projs.length > 0) {
        setActiveProjectId((prev) => {
          if (prev && projs.some((p) => p.id === prev)) return prev;
          return projs[0].id;
        });
      }
      setPlots(allPlots);
      setPlotSales(allSales);
      setPaymentSchedules(allScheds);
      setPaymentRecords(allPayments);
      setLeads(allLeads);
      setBrokers(allBrokers);
      setVouchers(allVouchers);
      setCalendarEvents(allEvents);
    } catch (err) {
      console.error('Failed to fetch CRM database:', err);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    refreshData();
  }, []);

  const activeProject = projects.find((p) => p.id === activeProjectId) || projects[0];

  // Actions connecting directly to backend API
  const addProject = async (data: Omit<Project, 'id'>) => {
    try {
      const created = await api.projects.create(data);
      setProjects((prev) => [created, ...prev]);
      setActiveProjectId(created.id);
      await refreshData();
    } catch (err) {
      console.error('Error adding project:', err);
    }
  };

  const updateProject = (id: string, updates: Partial<Project>) => {
    setProjects((prev) => prev.map((p) => (p.id === id ? { ...p, ...updates } : p)));
  };

  const addPlot = async (data: Omit<Plot, 'id'>) => {
    try {
      const created = await api.plots.create(data);
      setPlots((prev) => [...prev, created]);
    } catch (err) {
      console.error('Error adding plot:', err);
    }
  };

  const updatePlot = async (id: string, updates: Partial<Plot>) => {
    try {
      const updated = await api.plots.update(id, updates);
      setPlots((prev) => prev.map((p) => (p.id === id ? updated : p)));
    } catch (err) {
      console.error('Error updating plot:', err);
    }
  };

  const bulkAddPlots = async (newPlotsData: Omit<Plot, 'id'>[]) => {
    try {
      await api.plots.createBulk(newPlotsData);
      const updatedPlots = await api.plots.getAll();
      setPlots(updatedPlots);
    } catch (err) {
      console.error('Error bulk adding plots:', err);
    }
  };

  const bookPlotSale = async (data: {
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
    const targetPlot = plots.find((p) => p.id === data.plotId);
    const proj = projects.find((p) => p.id === (targetPlot?.projectId || activeProjectId));

    try {
      await api.sales.createBooking({
        plotId: data.plotId,
        plotNumber: targetPlot?.plotNumber || 'Plot',
        projectId: targetPlot?.projectId || activeProjectId,
        projectName: proj?.name || 'Township Layout',
        buyerName: data.buyerName,
        buyerMobile: data.buyerMobile,
        buyerEmail: data.buyerEmail,
        buyerGovIdType: data.buyerGovIdType,
        buyerGovIdLast4: data.buyerGovIdLast4,
        dealValue: data.dealValue,
        tokenAmount: data.bookingAmount,
        paymentType: data.paymentType === 'LUMP_SUM' ? 'FULL' : 'INSTALMENT',
        brokerId: data.brokerPartnerId,
        remarks: `Booking advance recorded via ${data.paymentMode} (Ref: ${data.paymentRef})`
      });

      await refreshData();
    } catch (err) {
      console.error('Error booking plot in database:', err);
    }
  };

  const recordPayment = async (data: {
    plotSaleId: string;
    amount: number;
    paidOn: string;
    mode: 'CASH' | 'CHEQUE' | 'BANK_TRANSFER' | 'UPI';
    reference: string;
    receivedBy: string;
    remarks?: string;
  }) => {
    try {
      await api.payments.record({
        plotSaleId: data.plotSaleId,
        projectId: activeProjectId,
        amount: data.amount,
        paidOn: data.paidOn,
        mode: data.mode,
        reference: data.reference,
        receivedBy: data.receivedBy,
        remarks: data.remarks
      });

      await refreshData();
    } catch (err) {
      console.error('Error recording payment in database:', err);
    }
  };

  const addLead = async (lead: Omit<Lead, 'id' | 'createdAt' | 'lastInteractionAt'>) => {
    try {
      const created = await api.leads.create(lead);
      setLeads((prev) => [created, ...prev]);
    } catch (err) {
      console.error('Error adding lead:', err);
    }
  };

  const updateLeadStatus = async (leadId: string, status: Lead['status']) => {
    try {
      const updated = await api.leads.update(leadId, { status });
      setLeads((prev) => prev.map((l) => (l.id === leadId ? updated : l)));
    } catch (err) {
      console.error('Error updating lead status:', err);
    }
  };

  const addLeadInteraction = async (interaction: Omit<Interaction, 'id'>) => {
    try {
      const created = await api.leads.addInteraction(interaction.customerId, interaction);
      setInteractions((prev) => [created, ...prev]);
      const updatedLeads = await api.leads.getAll();
      setLeads(updatedLeads);
    } catch (err) {
      console.error('Error adding lead interaction:', err);
    }
  };

  const addBroker = async (
    broker: Omit<BrokerPartner, 'id' | 'dealsClosedCount' | 'totalCommissionEarned' | 'totalCommissionPaid'>
  ) => {
    try {
      const created = await api.brokers.create(broker);
      setBrokers((prev) => [created, ...prev]);
    } catch (err) {
      console.error('Error adding broker:', err);
    }
  };

  const updateBrokerTier = async (brokerId: string, tier: BrokerPartner['tier'], rate: number) => {
    try {
      const updated = await api.brokers.update(brokerId, { tier, commissionRate: rate });
      setBrokers((prev) => prev.map((b) => (b.id === brokerId ? updated : b)));
    } catch (err) {
      console.error('Error updating broker tier:', err);
    }
  };

  const markVoucherPaid = async (voucherId: string, paymentRef: string) => {
    try {
      const updated = await api.vouchers.markPaid(voucherId, paymentRef);
      setVouchers((prev) => prev.map((v) => (v.id === voucherId ? updated : v)));
      const updatedBrokers = await api.brokers.getAll();
      setBrokers(updatedBrokers);
    } catch (err) {
      console.error('Error settling voucher:', err);
    }
  };

  const addCalendarEvent = async (event: Omit<CalendarEvent, 'id'>) => {
    try {
      const created = await api.calendar.create(event);
      setCalendarEvents((prev) => [...prev, created]);
    } catch (err) {
      console.error('Error adding calendar event:', err);
    }
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
        isLoading,
        refreshData,
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

export const useCrm = (): CrmContextType => {
  const context = useContext(CrmContext);
  if (!context) {
    throw new Error('useCrm must be used within a CrmProvider');
  }
  return context;
};
