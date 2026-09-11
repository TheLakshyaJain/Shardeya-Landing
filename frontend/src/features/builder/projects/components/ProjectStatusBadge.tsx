import { useTranslation } from 'react-i18next';
import { Badge } from '@/components/ui/badge';
import type { ProjectStatus } from '../types';

const VARIANT: Record<ProjectStatus, 'secondary' | 'default' | 'outline'> = {
  UPCOMING: 'secondary',
  ACTIVE: 'default',
  COMPLETED: 'outline',
};

export function ProjectStatusBadge({ status }: { status: ProjectStatus }) {
  const { t } = useTranslation('project');
  return <Badge variant={VARIANT[status]}>{t(`status.${status}`)}</Badge>;
}
