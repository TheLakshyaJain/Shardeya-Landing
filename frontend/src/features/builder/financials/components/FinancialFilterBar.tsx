import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Input } from '@/components/ui/input';
import { listProjects } from '../../projects/api/projectApi';

const MODES = ['CASH', 'CHEQUE', 'BANK_TRANSFER', 'UPI', 'DD'];

interface FinancialFilterBarProps {
  projectId: string;
  onProjectChange: (v: string) => void;
  from: string;
  onFromChange: (v: string) => void;
  to: string;
  onToChange: (v: string) => void;
  mode?: string;
  onModeChange?: (v: string) => void;
}

// B-08 §14.4: project, date range, payment mode, status -- cascades to
// every card and table on the page. The mode dropdown was missing entirely
// until this fix even though the backend endpoint already supported it
// (found while thoroughly re-testing every Financials filter combination).
export function FinancialFilterBar({
  projectId, onProjectChange, from, onFromChange, to, onToChange, mode, onModeChange,
}: FinancialFilterBarProps) {
  const { t } = useTranslation(['financial', 'payment']);
  const projectsQuery = useQuery({ queryKey: ['projects-for-financial-filter'], queryFn: () => listProjects(undefined, 100) });

  return (
    <div className="mb-4 flex flex-wrap items-end gap-3">
      <div className="space-y-1">
        <label className="text-xs text-muted-foreground">{t('filters.project')}</label>
        <Select value={projectId || '__all__'} onValueChange={(v) => onProjectChange(v === '__all__' ? '' : v)}>
          <SelectTrigger className="w-48" aria-label={t('filters.project')}>
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
        <label className="text-xs text-muted-foreground">{t('filters.from')}</label>
        <Input type="date" className="w-40" value={from} onChange={(e) => onFromChange(e.target.value)} />
      </div>
      <div className="space-y-1">
        <label className="text-xs text-muted-foreground">{t('filters.to')}</label>
        <Input type="date" className="w-40" value={to} onChange={(e) => onToChange(e.target.value)} />
      </div>
      {onModeChange && (
        <div className="space-y-1">
          <label className="text-xs text-muted-foreground">{t('filters.mode')}</label>
          <Select value={mode || '__all__'} onValueChange={(v) => onModeChange(v === '__all__' ? '' : v)}>
            <SelectTrigger className="w-40" aria-label={t('filters.mode')}>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="__all__">{t('filters.allModes')}</SelectItem>
              {MODES.map((m) => (
                <SelectItem key={m} value={m}>
                  {t(`mode.${m}`, { ns: 'payment' })}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      )}
    </div>
  );
}
