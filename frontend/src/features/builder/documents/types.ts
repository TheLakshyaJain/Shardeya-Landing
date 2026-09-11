export type DocType = 'ALLOTMENT_LETTER' | 'PAYMENT_RECEIPT' | 'DEMAND_LETTER' | 'BOOKING_CONFIRMATION';

export interface DocumentTemplateResponse {
  id: string;
  orgId: string | null;
  systemDefault: boolean;
  docType: DocType;
  name: string;
  language: 'en' | 'hi';
  bodyHtml: string;
  headerHtml: string | null;
  footerHtml: string | null;
  variables: string[];
  version: number;
  active: boolean;
}

export interface VariablePaletteResponse {
  topLevel: string[];
  collections: Record<string, string[]>;
}

export interface GeneratedDocumentResponse {
  id: string;
  docType: DocType;
  documentNumber: string;
  entityType: string;
  entityId: string;
  mediaId: string;
  language: 'en' | 'hi';
  generatedBy: string;
  generatedAt: string;
}
