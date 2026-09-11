import { useTranslation } from 'react-i18next';
import { Badge } from '@/components/ui/badge';
import type { PlotStatus } from '../types';

const VARIANT: Record<PlotStatus, 'default' | 'secondary' | 'destructive'> = {
  AVAILABLE: 'default',
  RESERVED: 'secondary',
  SOLD: 'destructive',
};

export function PlotStatusBadge({ status }: { status: PlotStatus }) {
  const { t } = useTranslation('plot');
  return <Badge variant={VARIANT[status]}>{t(`status.${status}`)}</Badge>;
}
