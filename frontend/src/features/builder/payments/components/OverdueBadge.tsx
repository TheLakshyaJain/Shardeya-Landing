import { useTranslation } from 'react-i18next';
import { Badge } from '@/components/ui/badge';

export function OverdueBadge({ daysOverdue }: { daysOverdue: number }) {
  const { t } = useTranslation('payment');
  if (daysOverdue <= 0) return null;
  return <Badge variant="destructive">{t('schedule.overdueDays', { count: daysOverdue })}</Badge>;
}
