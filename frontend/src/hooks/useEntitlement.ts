import { ApiError } from '@/lib/api/client';

export interface QuotaInfo {
  limitKey: string;
  used: number;
  limit: number;
}

// M-09 slice for M2 is quota-enforcement only (no plan-tier feature flags
// yet — EntitlementsSummary only carries `plan`), so there is no "current
// usage" endpoint to poll proactively. The only signal the frontend has is
// the 403 QUOTA_EXCEEDED the backend returns from the mutation itself
// (EntitlementService.assertWithinQuota, params: limitKey/used/limit) —
// this hook extracts that reactively rather than pretending to know quota
// state in advance.
export function parseQuotaError(error: unknown): QuotaInfo | null {
  if (!(error instanceof ApiError)) return null;
  const quotaEntry = error.errors.find((e) => e.code === 'QUOTA_EXCEEDED');
  if (!quotaEntry) return null;
  const { limitKey, used, limit } = quotaEntry.params as Record<string, unknown>;
  if (typeof limitKey !== 'string' || typeof used !== 'number' || typeof limit !== 'number') return null;
  return { limitKey, used, limit };
}

export interface FeatureGateInfo {
  featureKey: string;
}

// B-06 Path B (Excel import): a boolean/tier plan gate, distinct from
// QUOTA_EXCEEDED's numeric used/limit shape -- there's nothing to count,
// just "this plan doesn't include this feature." Path A (Quick Create) never
// produces this error at all, by design (03-BUILDER-MODULES.md B-06 §7).
export function parseFeatureGateError(error: unknown): FeatureGateInfo | null {
  if (!(error instanceof ApiError)) return null;
  const entry = error.errors.find((e) => e.code === 'FEATURE_NOT_ENABLED');
  if (!entry) return null;
  const { featureKey } = entry.params as Record<string, unknown>;
  if (typeof featureKey !== 'string') return null;
  return { featureKey };
}
