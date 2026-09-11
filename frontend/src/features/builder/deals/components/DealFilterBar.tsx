import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Input } from '@/components/ui/input';
import { listProjects } from '../../projects/api/projectApi';

interface DealFilterBarProps {
  status: string;
  onStatusChange: (v: string) => void;
  projectId: string;
  onProjectChange: (v: string) => void;
  search: string;
  onSearchChange: (v: string) => void;
}

// B-10 §16: project, date range, broker, deal status, staff member. Broker
// and staff filters are omitted here -- B-14 doesn't exist yet, and staff
// filtering is a small enough addition to defer until there's more than one
// real staff member's worth of deals to filter across in practice.
export function DealFilterBar({ status, onStatusChange, projectId, onProjectChange, search, onSearchChange }: DealFilterBarProps) {
  const { t } = useTranslation('deal');
  const projectsQuery = useQuery({ queryKey: ['projects-for-deal-filter'], queryFn: () => listProjects(undefined, 100) });

  return (
    <div className="mb-4 flex flex-wrap items-end gap-3">
      <div className="space-y-1">
        <label className="text-xs text-muted-foreground">{t('filters.status')}</label>
        <Select value={status || '__all__'} onValueChange={(v) => onStatusChange(v === '__all__' ? '' : v)}>
          <SelectTrigger className="w-40">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="__all__">{t('filters.allStatuses')}</SelectItem>
            <SelectItem value="COMPLETED">{t('status.COMPLETED')}</SelectItem>
            <SelectItem value="CANCELLED">{t('status.CANCELLED')}</SelectItem>
          </SelectContent>
        </Select>
      </div>
      <div className="space-y-1">
        <label className="text-xs text-muted-foreground">{t('filters.project')}</label>
        <Select value={projectId || '__all__'} onValueChange={(v) => onProjectChange(v === '__all__' ? '' : v)}>
          <SelectTrigger className="w-48">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="__all__">{t('filters.allProjects')}</SelectItem>
            {(projectsQuery.data?.items ?? []).map((p) => (
              <SelectItem key={p.id} value={p.id}>
                {p.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="space-y-1">
        <label className="text-xs text-muted-foreground">{t('filters.search')}</label>
        <Input className="w-48" value={search} onChange={(e) => onSearchChange(e.target.value)} placeholder={t('filters.searchPlaceholder')} />
      </div>
    </div>
  );
}
