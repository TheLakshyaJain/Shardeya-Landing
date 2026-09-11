import { useTranslation } from 'react-i18next';
import { Badge } from '@/components/ui/badge';
import type { ChequeStatus } from '../types';

const VARIANT: Record<ChequeStatus, 'secondary' | 'default' | 'destructive'> = {
  PENDING: 'secondary',
  CLEARED: 'default',
  BOUNCED: 'destructive',
};

export function ChequeStatusChip({ status }: { status: ChequeStatus }) {
  const { t } = useTranslation('payment');
  return <Badge variant={VARIANT[status]}>{t(`cheque.status.${status}`)}</Badge>;
}
