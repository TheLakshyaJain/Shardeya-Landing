import { apiFetch } from '@/lib/api/client';
import type {
  BankDetailsResponse,
  BookingCommissionResponse,
  BrokerCommissionPaymentCreateRequest,
  BrokerCommissionPaymentResponse,
  BrokerCreateRequest,
  BrokerDealResponse,
  BrokerInteractionCreateRequest,
  BrokerInteractionResponse,
  BrokerCommissionSummaryResponse,
  BrokerNetworkNodeResponse,
  BrokerPerformanceResponse,
  BrokerResponse,
  BrokerTierCreateRequest,
  BrokerTierResponse,
  BrokerUpdateRequest,
  CommissionConfigCreateRequest,
  CommissionConfigResponse,
  CommissionLedgerEntryResponse,
  CommissionPaymentCreateRequest,
  CommissionPaymentResponse,
  CommissionPreviewRequest,
  CommissionPreviewResponse,
  DesignationHistoryResponse,
  DesignationOverrideRequest,
  DesignationSlabResponse,
  NetworkCommissionSummaryResponse,
  TierOverrideRequest,
} from '../types';

export function listBrokers(status?: string, tierId?: string, search?: string): Promise<BrokerResponse[]> {
  const params = new URLSearchParams();
  if (status) params.set('status', status);
  if (tierId) params.set('tierId', tierId);
  if (search) params.set('search', search);
  const qs = params.toString();
  return apiFetch(`/brokers${qs ? `?${qs}` : ''}`);
}

export function getBroker(id: string): Promise<BrokerResponse> {
  return apiFetch(`/brokers/${id}`);
}

export function createBroker(req: BrokerCreateRequest): Promise<BrokerResponse> {
  return apiFetch('/brokers', { method: 'POST', body: req });
}

// -- Network (06-BROKER-NETWORK-ENGINE.md) --

export function listBrokerNetwork(): Promise<BrokerNetworkNodeResponse[]> {
  return apiFetch('/brokers/network');
}

export function listDesignationSlabs(): Promise<DesignationSlabResponse[]> {
  return apiFetch('/designation-slabs');
}

export function updateBroker(id: string, req: BrokerUpdateRequest): Promise<BrokerResponse> {
  return apiFetch(`/brokers/${id}`, { method: 'PATCH', body: req });
}

export function deactivateBroker(id: string): Promise<void> {
  return apiFetch(`/brokers/${id}/deactivate`, { method: 'POST' });
}

export function reactivateBroker(id: string): Promise<void> {
  return apiFetch(`/brokers/${id}/reactivate`, { method: 'POST' });
}

export function blockBroker(id: string): Promise<void> {
  return apiFetch(`/brokers/${id}/block`, { method: 'POST' });
}

export function deleteBroker(id: string): Promise<void> {
  return apiFetch(`/brokers/${id}`, { method: 'DELETE' });
}

export function getBankDetails(id: string, reason?: string): Promise<BankDetailsResponse> {
  const params = reason ? `?reason=${encodeURIComponent(reason)}` : '';
  return apiFetch(`/brokers/${id}/bank-details${params}`);
}

export function getPerformance(id: string): Promise<BrokerPerformanceResponse> {
  return apiFetch(`/brokers/${id}/performance`);
}

export function getDeals(id: string): Promise<BrokerDealResponse[]> {
  return apiFetch(`/brokers/${id}/deals`);
}

export function listInteractions(id: string): Promise<BrokerInteractionResponse[]> {
  return apiFetch(`/brokers/${id}/interactions`);
}

export function addInteraction(id: string, req: BrokerInteractionCreateRequest): Promise<BrokerInteractionResponse> {
  return apiFetch(`/brokers/${id}/interactions`, { method: 'POST', body: req });
}

// -- Commission configuration --

export function listCommissionConfigs(brokerId: string): Promise<CommissionConfigResponse[]> {
  return apiFetch(`/brokers/${brokerId}/commission-configs`);
}

export function createCommissionConfig(brokerId: string, req: CommissionConfigCreateRequest): Promise<CommissionConfigResponse> {
  return apiFetch(`/brokers/${brokerId}/commission-configs`, { method: 'POST', body: req });
}

export function updateCommissionConfig(id: string, req: CommissionConfigCreateRequest): Promise<CommissionConfigResponse> {
  return apiFetch(`/commission-configs/${id}`, { method: 'PATCH', body: req });
}

export function deleteCommissionConfig(id: string): Promise<void> {
  return apiFetch(`/commission-configs/${id}`, { method: 'DELETE' });
}

export function previewCommission(brokerId: string, req: CommissionPreviewRequest): Promise<CommissionPreviewResponse> {
  return apiFetch(`/brokers/${brokerId}/commission-preview`, { method: 'POST', body: req });
}

// -- Tiers --

export function listTiers(): Promise<BrokerTierResponse[]> {
  return apiFetch('/broker-tiers');
}

export function createTier(req: BrokerTierCreateRequest): Promise<BrokerTierResponse> {
  return apiFetch('/broker-tiers', { method: 'POST', body: req });
}

export function updateTier(id: string, req: BrokerTierCreateRequest): Promise<BrokerTierResponse> {
  return apiFetch(`/broker-tiers/${id}`, { method: 'PATCH', body: req });
}

export function deleteTier(id: string): Promise<void> {
  return apiFetch(`/broker-tiers/${id}`, { method: 'DELETE' });
}

export function overrideTier(brokerId: string, req: TierOverrideRequest): Promise<void> {
  return apiFetch(`/brokers/${brokerId}/tier-override`, { method: 'POST', body: req });
}

export function recalculateTiers(): Promise<{ evaluated: number }> {
  return apiFetch('/broker-tiers/recalculate', { method: 'POST' });
}

// -- Commission ledger + payments --

export function getBrokerLedger(brokerId: string, status?: string): Promise<CommissionLedgerEntryResponse[]> {
  const params = status ? `?status=${status}` : '';
  return apiFetch(`/brokers/${brokerId}/ledger${params}`);
}

export function getOrgWideLedger(filters: { brokerId?: string; projectId?: string; status?: string } = {}): Promise<CommissionLedgerEntryResponse[]> {
  const params = new URLSearchParams();
  if (filters.brokerId) params.set('brokerId', filters.brokerId);
  if (filters.projectId) params.set('projectId', filters.projectId);
  if (filters.status) params.set('status', filters.status);
  const qs = params.toString();
  return apiFetch(`/commission-ledger${qs ? `?${qs}` : ''}`);
}

export function recordCommissionPayment(ledgerEntryId: string, req: CommissionPaymentCreateRequest): Promise<CommissionPaymentResponse> {
  return apiFetch(`/commission-ledger/${ledgerEntryId}/payments`, { method: 'POST', body: req });
}

export function reverseCommissionPayment(paymentId: string, reason: string): Promise<CommissionPaymentResponse> {
  return apiFetch(`/commission-payments/${paymentId}/reverse`, { method: 'POST', body: { reason } });
}

// 06-BROKER-NETWORK-ENGINE.md §7/§8 -- the DESIGNATION-broker equivalent of getBrokerLedger.
export function getBookingCommissions(brokerId: string): Promise<BookingCommissionResponse[]> {
  return apiFetch(`/brokers/${brokerId}/booking-commissions`);
}

// -- §8a: the "Record Payment" action for a DESIGNATION broker, paying
// against their whole Commission Due balance, auto-allocated oldest-first
// by the backend. Distinct from recordCommissionPayment/reverseCommissionPayment
// above (M6's PERCENTAGE/FIXED-broker path, one ledger entry at a time).

export function recordBrokerCommissionPayment(brokerId: string, req: BrokerCommissionPaymentCreateRequest): Promise<BrokerCommissionPaymentResponse> {
  return apiFetch(`/brokers/${brokerId}/commission-payments`, { method: 'POST', body: req });
}

export function getBrokerCommissionPayments(brokerId: string): Promise<BrokerCommissionPaymentResponse[]> {
  return apiFetch(`/brokers/${brokerId}/commission-payments`);
}

export function reverseBrokerCommissionPayment(paymentId: string, reason: string): Promise<BrokerCommissionPaymentResponse> {
  return apiFetch(`/broker-commission-payments/${paymentId}/reverse`, { method: 'POST', body: { reason } });
}

// -- §34, build-order step 8: manual designation override --

export function setDesignationOverride(brokerId: string, req: DesignationOverrideRequest): Promise<void> {
  return apiFetch(`/brokers/${brokerId}/designation-override`, { method: 'POST', body: req });
}

export function clearDesignationOverride(brokerId: string): Promise<void> {
  return apiFetch(`/brokers/${brokerId}/designation-override/clear`, { method: 'POST' });
}

// -- §26/§27/§29, build-order step 10: dashboard aggregates --

export function getCommissionSummary(brokerId: string): Promise<BrokerCommissionSummaryResponse> {
  return apiFetch(`/brokers/${brokerId}/commission-summary`);
}

export function getDesignationHistory(brokerId: string): Promise<DesignationHistoryResponse[]> {
  return apiFetch(`/brokers/${brokerId}/designation-history`);
}

export function getNetworkCommissionSummary(): Promise<NetworkCommissionSummaryResponse> {
  return apiFetch('/brokers/network/commission-summary');
}

export function getNetworkDesignationHistory(): Promise<DesignationHistoryResponse[]> {
  return apiFetch('/brokers/network/designation-history');
}
