import { apiFetch } from '@/lib/api/client';
import type { MediaResponse, UploadIntentRequest, UploadIntentResponse } from '../types';

export function createUploadIntent(req: UploadIntentRequest): Promise<UploadIntentResponse> {
  return apiFetch('/media/upload-intent', { method: 'POST', body: req });
}

export function completeUpload(mediaId: string): Promise<MediaResponse> {
  return apiFetch(`/media/${mediaId}/complete`, { method: 'POST' });
}

export function getMedia(mediaId: string): Promise<MediaResponse> {
  return apiFetch(`/media/${mediaId}`);
}

// The presigned upload URL points straight at MinIO/S3, not our API — it
// must NOT go through apiFetch (no Authorization header, no JSON body
// wrapping, and its own success semantics: S3 returns 200 with an empty body).
async function putToPresignedUrl(url: string, file: File): Promise<void> {
  const res = await fetch(url, { method: 'PUT', headers: { 'Content-Type': file.type }, body: file });
  if (!res.ok) {
    throw new Error(`Upload to storage failed with status ${res.status}`);
  }
}

/** Upload-intent -> PUT to storage -> complete, as one call. Used by ImageUploader and ImportWizard. */
export async function uploadFile(file: File, purpose: string, sensitive = false): Promise<MediaResponse> {
  const intent = await createUploadIntent({
    filename: file.name,
    mimeType: file.type,
    sizeBytes: file.size,
    purpose,
    sensitive,
  });
  await putToPresignedUrl(intent.uploadUrl, file);
  return completeUpload(intent.mediaId);
}
