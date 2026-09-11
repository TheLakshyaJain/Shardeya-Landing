// Mirrors backend/src/main/java/com/shardeya/builder/plot/dto exactly —
// keep these two in sync by hand (no shared-schema codegen in this project yet).

// B-03 §7: SOLD is never directly settable — it's a side effect of creating
// a plot_sale (M3). The status dropdown must only ever offer AVAILABLE and
// RESERVED; SOLD is shown read-only when it's already the current status.
export type PlotStatus = 'AVAILABLE' | 'RESERVED' | 'SOLD';
// Must mirror backend Plot.Facing exactly (Plot.java) -- these are sent
// as-is in JSON request/response bodies, and Jackson enum deserialization
// rejects any string that isn't one of the enum's own constant names,
// failing the WHOLE request with a generic 400 "Failed to read request"
// (no field-level error) rather than a validation error.
export type PlotFacing = 'N' | 'S' | 'E' | 'W' | 'NE' | 'NW' | 'SE' | 'SW';

export interface PlotResponse {
  id: string;
  projectId: string;
  plotNumber: string;
  status: PlotStatus;
  reservedFor: string | null;
  reservedUntil: string | null;
  sizeValue: number;
  sizeUnit: string;
  sizeSqft: number;
  facing: PlotFacing | null;
  price: number;
  pricePerUnit: number | null;
  isGarden: boolean;
  isCorner: boolean;
  isHot: boolean;
  remarks: string | null;
  gridRow: number | null;
  gridCol: number | null;
  currentSaleId: string | null;
}

export interface PlotCreateRequest {
  plotNumber: string;
  status?: PlotStatus;
  reservedFor?: string;
  reservedUntil?: string;
  sizeValue: number;
  sizeUnit: string;
  facing?: PlotFacing;
  price: number;
  isGarden?: boolean;
  isCorner?: boolean;
  isHot?: boolean;
  remarks?: string;
  gridRow?: number;
  gridCol?: number;
}

export interface PlotUpdateRequest {
  plotNumber?: string;
  sizeValue?: number;
  sizeUnit?: string;
  facing?: PlotFacing;
  price?: number;
  isGarden?: boolean;
  isCorner?: boolean;
  isHot?: boolean;
  remarks?: string;
}

export interface PlotStatusUpdateRequest {
  status: PlotStatus;
  reservedFor?: string;
  reservedUntil?: string;
}

export interface PlotFilter {
  status?: PlotStatus;
  facing?: PlotFacing;
  sizeMin?: number;
  sizeMax?: number;
  priceMin?: number;
  priceMax?: number;
  isHot?: boolean;
  isCorner?: boolean;
  isGarden?: boolean;
  search?: string;
}

// Deliberately compact (B-03 §4): tuple order is
// [row, col, plotNumber, statusCode, sizeSqft, isHot] — see GridResponse's
// backend javadoc. Never re-key this into an object; the whole point of the
// tuple shape is to avoid repeated field names at 5,000-plot scale.
export type GridPlotTuple = [number, number, string, number, number, number];

export interface GridResponse {
  rows: number;
  cols: number;
  blocked: [number, number][];
  plots: GridPlotTuple[];
  legend: { statusCodes: Record<string, PlotStatus> };
  unplaced: string[];
}

export interface PlotStatsResponse {
  totalPlots: number;
  available: number;
  reserved: number;
  sold: number;
  totalValue: number;
  availableValue: number;
  reservedValue: number;
  soldValue: number;
}

export interface BulkPositionPlacement {
  plotId: string;
  gridRow: number;
  gridCol: number;
}

export interface BulkUpdateRequest {
  plotIds: string[];
  pricePerSqft?: number;
  status?: PlotStatus;
}

// B-06 Path A: Quick Range Create. sharedProperties are written as each
// generated plot's individual starting values, not a link back to a
// template -- editing one plot afterward never affects the others.
export interface QuickCreateRangeRequest {
  prefix?: string;
  separator?: string;
  start: number;
  end: number;
  padWidth?: number;
}

export interface QuickCreateSharedPropertiesRequest {
  sizeValue: number;
  sizeUnit: string;
  facing?: PlotFacing;
  price: number;
  isGarden?: boolean;
  isCorner?: boolean;
  isHot?: boolean;
  remarks?: string;
}

export interface QuickCreateRequest {
  ranges: QuickCreateRangeRequest[];
  sharedProperties: QuickCreateSharedPropertiesRequest;
  autoPlace: boolean;
}

export interface QuickCreatePlotNumberPreview {
  plotNumber: string;
  collidesWithExisting: boolean;
  collidesWithinRequest: boolean;
}

export interface QuickCreatePreviewResponse {
  plotNumbers: QuickCreatePlotNumberPreview[];
  totalCount: number;
  quotaUsed: number;
  quotaLimit: number;
  withinQuota: boolean;
  currentPlotCount: number;
  declaredPlotCount: number;
  withinDeclaredCount: boolean;
}

export interface QuickCreateCommitResponse {
  created: number;
  unplaced: number;
}
