import { useTranslation } from 'react-i18next';
import { TriangleAlert } from 'lucide-react';
import { parseQuotaError } from '@/hooks/useEntitlement';

/** Renders the Free-plan quota-exceeded message (used/limit) when `error` is a QUOTA_EXCEEDED ApiError; otherwise renders nothing. */
export function EntitlementGuard({ error }: { error: unknown }) {
  const { t } = useTranslation('common');
  const quota = parseQuotaError(error);
  if (!quota) return null;

  return (
    <div
      role="alert"
      className="flex items-start gap-2 rounded-md border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-200"
    >
      <TriangleAlert className="mt-0.5 size-4 shrink-0" />
      <span>{t('entitlement.quotaExceeded', { used: quota.used, limit: quota.limit })}</span>
    </div>
  );
}
