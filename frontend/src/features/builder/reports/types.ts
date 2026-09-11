export interface ReportFilterDef {
  key: string;
  type: 'PROJECT' | 'DATE' | 'SELECT' | 'BROKER' | 'STAFF' | 'BOOLEAN' | 'NUMBER';
  options?: string[];
}

export interface ReportDefinitionSummary {
  code: string;
  profile: string;
  nameEn: string;
  nameHi: string;
  descriptionKey: string;
  supportedFilters: ReportFilterDef[];
  supportedFormats: string[];
  canExport: boolean;
}

export interface ReportColumn {
  key: string;
  labelKey: string;
  type: 'TEXT' | 'NUMBER' | 'MONEY' | 'DATE';
}

export interface ReportPreviewResponse {
  columns: ReportColumn[];
  rows: Record<string, string | number | null>[];
  totalCount: number;
  page: number;
  pageSize: number;
}
