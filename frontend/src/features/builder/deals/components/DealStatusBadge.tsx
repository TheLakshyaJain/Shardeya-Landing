import { useTranslation } from 'react-i18next';
import { Badge } from '@/components/ui/badge';

interface DealStatusBadgeProps {
  status: string;
}

// B-10 §6: Completed green / Cancelled red.
export function DealStatusBadge({ status }: DealStatusBadgeProps) {
  const { t } = useTranslation('deal');
  return (
    <Badge className={status === 'COMPLETED' ? 'bg-green-100 text-green-800 border-green-300' : 'bg-red-100 text-red-800 border-red-300'} variant="outline">
      {t(`status.${status}`)}
    </Badge>
  );
}
