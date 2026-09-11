// Mirrors backend/src/main/java/com/shardeya/foundation/importexport/dto exactly.

export type ImportJobStatus = 'UPLOADED' | 'VALIDATING' | 'PREVIEW_READY' | 'IMPORTING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
export type ImportRowStatus = 'VALID' | 'INVALID' | 'IMPORTED' | 'SKIPPED';
export type DuplicateMode = 'SKIP' | 'UPDATE_EXISTING' | 'FAIL';

export interface ImportJobResponse {
  id: string;
  status: ImportJobStatus;
  totalRows: number;
  validRows: number;
  invalidRows: number;
  importedRows: number;
}

export interface RowError {
  field: string;
  code: string;
  messageKey: string;
  params: Record<string, unknown>;
}

export interface ImportRowResponse {
  id: string;
  rowNumber: number;
  data: Record<string, string>;
  status: ImportRowStatus;
  errors: RowError[];
}

export interface PlotImportStartRequest {
  mediaId: string;
  duplicateMode?: DuplicateMode;
  autoPlace: boolean;
}

export interface ImportCommitRequest {
  skipInvalid: boolean;
}
