// Mirrors backend/src/main/java/com/shardeya/foundation/media/dto exactly.

export type MediaStatus = 'PENDING' | 'READY' | 'REJECTED';

export interface UploadIntentRequest {
  filename: string;
  mimeType: string;
  sizeBytes: number;
  purpose: string;
  entityType?: string;
  entityId?: string;
  // M3: routes to the sensitive S3 bucket (CLAUDE.md rule #6) -- see
  // MediaService's own comment. Omitted/false for every M2-era caller.
  sensitive?: boolean;
}

export interface UploadIntentResponse {
  mediaId: string;
  uploadUrl: string;
  expiresIn: number;
}

export interface MediaResponse {
  id: string;
  status: MediaStatus;
  // null for a sensitive asset -- there is no unaudited URL to it at all
  // (see MediaService.toResponse's own comment); the only way to view one
  // is the owning entity's specific audited reveal endpoint.
  url: string | null;
  derivatives: Record<string, string>;
  width: number | null;
  height: number | null;
  mimeType: string;
  sizeBytes: number;
}
