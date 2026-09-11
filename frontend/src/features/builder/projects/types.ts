// Mirrors backend/src/main/java/com/shardeya/builder/project/dto exactly —
// keep these two in sync by hand (no shared-schema codegen in this project yet).

export type ProjectType = 'RESIDENTIAL_PLOT_COLONY' | 'APARTMENT' | 'VILLA' | 'COMMERCIAL' | 'MIXED_USE';
export type ProjectStatus = 'UPCOMING' | 'ACTIVE' | 'COMPLETED';

export interface ApprovalTag {
  label: string;
  note: string | null;
  docMediaId: string | null;
}

export interface PlotStatusCounts {
  total: number;
  available: number;
  reserved: number;
  sold: number;
}

export interface ProjectResponse {
  id: string;
  name: string;
  projectType: ProjectType;
  status: ProjectStatus;
  city: string;
  locality: string;
  coverMediaId: string | null;
  plotCounts: PlotStatusCounts;
}

export interface ProjectDetailResponse {
  id: string;
  name: string;
  projectType: ProjectType;
  status: ProjectStatus;
  address: string;
  locality: string;
  city: string;
  stateCode: string;
  pincode: string | null;
  googleMapsUrl: string | null;
  latitude: number | null;
  longitude: number | null;
  totalAreaValue: number;
  totalAreaUnit: string;
  totalAreaSqft: number;
  declaredPlotCount: number;
  launchDate: string | null;
  expectedCompletionDate: string | null;
  description: string | null;
  approvals: ApprovalTag[];
  reraNumber: string | null;
  coverMediaId: string | null;
  layoutMediaId: string | null;
  brochureMediaId: string | null;
  gridRows: number | null;
  gridCols: number | null;
  plotCounts: PlotStatusCounts;
  declaredVsActualDelta: number;
}

export interface ProjectCreateRequest {
  name: string;
  projectType: ProjectType;
  status?: ProjectStatus;
  address: string;
  locality: string;
  city: string;
  stateCode: string;
  pincode?: string;
  googleMapsUrl?: string;
  latitude?: number | null;
  longitude?: number | null;
  totalAreaValue: number;
  totalAreaUnit: string;
  declaredPlotCount: number;
  launchDate?: string;
  expectedCompletionDate?: string;
  description?: string;
  approvals?: ApprovalTag[];
  reraNumber?: string;
  coverMediaId?: string;
  layoutMediaId?: string;
  brochureMediaId?: string;
}

export type ProjectUpdateRequest = Partial<ProjectCreateRequest>;

export interface CursorPage<T> {
  items: T[];
  nextCursor: string | null;
  hasMore: boolean;
}

export interface GridCell {
  row: number;
  col: number;
}

export interface GridConfigRequest {
  rows: number;
  cols: number;
  blockedCells: GridCell[];
}

export interface GridConfigResponse {
  rows: number | null;
  cols: number | null;
  blockedCells: GridCell[];
}

export interface ProjectMediaItem {
  mediaId: string;
  role: string;
  sortOrder: number;
}
