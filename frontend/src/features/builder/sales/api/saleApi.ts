import { apiFetch } from '@/lib/api/client';
import type {
  CancelSaleRequest,
  GovIdRevealResponse,
  PlotDocumentAttachRequest,
  PlotDocumentResponse,
  SaleCreateRequest,
  SaleResponse,
  SaleUpdateRequest,
} from '../types';

export function createSale(plotId: string, req: SaleCreateRequest, idempotencyKey: string): Promise<SaleResponse> {
  return apiFetch(`/plots/${plotId}/sale`, { method: 'POST', body: req, idempotencyKey });
}

export function getSaleByPlot(plotId: string): Promise<SaleResponse> {
  return apiFetch(`/plots/${plotId}/sale`);
}

export function getSale(saleId: string): Promise<SaleResponse> {
  return apiFetch(`/sales/${saleId}`);
}

export function updateSale(saleId: string, req: SaleUpdateRequest): Promise<SaleResponse> {
  return apiFetch(`/sales/${saleId}`, { method: 'PATCH', body: req });
}

export function cancelSale(saleId: string, req: CancelSaleRequest): Promise<SaleResponse> {
  return apiFetch(`/sales/${saleId}/cancel`, { method: 'POST', body: req });
}

export function completeSale(saleId: string): Promise<SaleResponse> {
  return apiFetch(`/sales/${saleId}/complete`, { method: 'POST' });
}

export function setBuyerWhatsAppOptIn(saleId: string, optedIn: boolean): Promise<SaleResponse> {
  return apiFetch(`/sales/${saleId}/buyer-whatsapp-optin`, { method: 'POST', body: { optedIn } });
}

export function revealGovId(saleId: string, reason?: string): Promise<GovIdRevealResponse> {
  const params = reason ? `?reason=${encodeURIComponent(reason)}` : '';
  return apiFetch(`/sales/${saleId}/gov-id${params}`);
}

export function listSaleDocuments(saleId: string): Promise<PlotDocumentResponse[]> {
  return apiFetch(`/sales/${saleId}/documents`);
}

export function attachSaleDocument(saleId: string, req: PlotDocumentAttachRequest): Promise<PlotDocumentResponse> {
  return apiFetch(`/sales/${saleId}/documents`, { method: 'POST', body: req });
}

export function deleteSaleDocument(documentId: string): Promise<void> {
  return apiFetch(`/sales/documents/${documentId}`, { method: 'DELETE' });
}
