import { 
  Project, Plot, PlotSale, PaymentSchedule, PaymentRecord, 
  Lead, Interaction, BrokerPartner, CommissionVoucher, CalendarEvent 
} from '../types/crm';
import { User, UserRole } from '../context/AuthContext';

const API_BASE = '/api';

async function fetchJson<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    headers: {
      'Content-Type': 'application/json',
      ...(options?.headers || {})
    },
    ...options
  });

  if (!res.ok) {
    let errorMsg = `HTTP Error ${res.status}`;
    try {
      const body = await res.json();
      if (body.error) errorMsg = body.error;
    } catch {
      // Fallback
    }
    throw new Error(errorMsg);
  }

  return res.json();
}

export const api = {
  auth: {
    login: (email: string, password: string, role?: UserRole) =>
      fetchJson<{ success: boolean; user: User }>(`${API_BASE}/auth/login`, {
        method: 'POST',
        body: JSON.stringify({ email, password, role })
      }),
    signup: (payload: {
      name: string;
      email: string;
      password: string;
      role: UserRole;
      organization?: string;
      designation?: string;
      reraNumber?: string;
    }) =>
      fetchJson<{ success: boolean; user: User }>(`${API_BASE}/auth/signup`, {
        method: 'POST',
        body: JSON.stringify(payload)
      })
  },

  projects: {
    getAll: () => fetchJson<Project[]>(`${API_BASE}/projects`),
    create: (project: Omit<Project, 'id'>) =>
      fetchJson<Project>(`${API_BASE}/projects`, {
        method: 'POST',
        body: JSON.stringify(project)
      }),
    delete: (id: string) =>
      fetchJson<{ success: boolean }>(`${API_BASE}/projects/${id}`, {
        method: 'DELETE'
      })
  },

  plots: {
    getAll: (projectId?: string) =>
      fetchJson<Plot[]>(projectId ? `${API_BASE}/plots?projectId=${encodeURIComponent(projectId)}` : `${API_BASE}/plots`),
    create: (plot: Omit<Plot, 'id'>) =>
      fetchJson<Plot>(`${API_BASE}/plots`, {
        method: 'POST',
        body: JSON.stringify(plot)
      }),
    createBulk: (plots: Omit<Plot, 'id'>[]) =>
      fetchJson<{ success: boolean; count: number }>(`${API_BASE}/plots/bulk`, {
        method: 'POST',
        body: JSON.stringify({ plots })
      }),
    update: (id: string, plot: Partial<Plot>) =>
      fetchJson<Plot>(`${API_BASE}/plots/${id}`, {
        method: 'PUT',
        body: JSON.stringify(plot)
      })
  },

  leads: {
    getAll: () => fetchJson<Lead[]>(`${API_BASE}/leads`),
    create: (lead: Omit<Lead, 'id' | 'createdAt' | 'lastInteractionAt'>) =>
      fetchJson<Lead>(`${API_BASE}/leads`, {
        method: 'POST',
        body: JSON.stringify(lead)
      }),
    update: (id: string, lead: Partial<Lead>) =>
      fetchJson<Lead>(`${API_BASE}/leads/${id}`, {
        method: 'PUT',
        body: JSON.stringify(lead)
      }),
    getInteractions: (leadId: string) =>
      fetchJson<Interaction[]>(`${API_BASE}/leads/${leadId}/interactions`),
    addInteraction: (leadId: string, interaction: Omit<Interaction, 'id'>) =>
      fetchJson<Interaction>(`${API_BASE}/leads/${leadId}/interactions`, {
        method: 'POST',
        body: JSON.stringify(interaction)
      })
  },

  sales: {
    getAll: () => fetchJson<PlotSale[]>(`${API_BASE}/sales`),
    createBooking: (booking: {
      plotId: string;
      plotNumber: string;
      projectId: string;
      projectName: string;
      buyerName: string;
      buyerMobile: string;
      buyerEmail?: string;
      buyerGovIdType?: string;
      buyerGovIdLast4?: string;
      purchaseDate?: string;
      dealValue: number;
      tokenAmount: number;
      paymentType: 'FULL' | 'INSTALMENT' | 'CUSTOM';
      brokerId?: string;
      remarks?: string;
    }) =>
      fetchJson<PlotSale>(`${API_BASE}/sales`, {
        method: 'POST',
        body: JSON.stringify(booking)
      })
  },

  payments: {
    getAll: () => fetchJson<PaymentRecord[]>(`${API_BASE}/payments`),
    record: (payment: {
      plotSaleId: string;
      projectId?: string;
      amount: number;
      paidOn?: string;
      mode: 'CASH' | 'CHEQUE' | 'BANK_TRANSFER' | 'UPI';
      reference?: string;
      receivedBy?: string;
      remarks?: string;
    }) =>
      fetchJson<PaymentRecord>(`${API_BASE}/payments`, {
        method: 'POST',
        body: JSON.stringify(payment)
      }),
    getSchedules: () => fetchJson<PaymentSchedule[]>(`${API_BASE}/schedules`)
  },

  brokers: {
    getAll: () => fetchJson<BrokerPartner[]>(`${API_BASE}/brokers`),
    create: (broker: Omit<BrokerPartner, 'id' | 'dealsClosedCount' | 'totalCommissionEarned' | 'totalCommissionPaid'>) =>
      fetchJson<BrokerPartner>(`${API_BASE}/brokers`, {
        method: 'POST',
        body: JSON.stringify(broker)
      }),
    update: (id: string, broker: Partial<BrokerPartner>) =>
      fetchJson<BrokerPartner>(`${API_BASE}/brokers/${id}`, {
        method: 'PUT',
        body: JSON.stringify(broker)
      })
  },

  vouchers: {
    getAll: () => fetchJson<CommissionVoucher[]>(`${API_BASE}/vouchers`),
    markPaid: (id: string, paymentRef: string) =>
      fetchJson<CommissionVoucher>(`${API_BASE}/vouchers/${id}/pay`, {
        method: 'PUT',
        body: JSON.stringify({ paymentRef })
      })
  },

  calendar: {
    getAll: () => fetchJson<CalendarEvent[]>(`${API_BASE}/calendar`),
    create: (event: Omit<CalendarEvent, 'id'>) =>
      fetchJson<CalendarEvent>(`${API_BASE}/calendar`, {
        method: 'POST',
        body: JSON.stringify(event)
      }),
    delete: (id: string) =>
      fetchJson<{ success: boolean }>(`${API_BASE}/calendar/${id}`, {
        method: 'DELETE'
      })
  }
};
