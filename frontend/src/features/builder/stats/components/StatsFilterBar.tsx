import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Input } from '@/components/ui/input';
import { listProjects } from '../../projects/api/projectApi';
import { IndianTime } from '../lib/dateRangePresets';

interface StatsFilterBarProps {
  projectId: string;
  onProjectChange: (v: string) => void;
  from: string;
  to: string;
  onRangeChange: (from: string, to: string) => void;
}

// B-15 §6 StatsFilterBar + DateRangePicker: "project, date range, staff
// member, broker -- applied globally" (§21.2). Only project + date range
// are wired as GLOBAL top-of-page filters here -- staff/broker drill-down
// happens at the individual chart level instead (Top Brokers links
// straight to a broker's profile on click, Staff Performance is already a
// full per-staff table) rather than a second pair of global filters every
// one of the ten endpoints would need to additionally accept. A real,
// deliberate scope trim; see CLAUDE.md.
export function StatsFilterBar({ projectId, onProjectChange, from, to, onRangeChange }: StatsFilterBarProps) {
  const { t } = useTranslation('stats');
  const projectsQuery = useQuery({ queryKey: ['projects-for-stats-filter'], queryFn: () => listProjects(undefined, 100) });

  function applyPreset(preset: string) {
    const { from: f, to: tt } = IndianTime.presetRange(preset);
    onRangeChange(f, tt);
  }

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
              <SelectItem key={p.id} value={p.id}>{p.name}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="space-y-1">
        <label className="text-xs text-muted-foreground">{t('filters.preset')}</label>
        <Select onValueChange={applyPreset}>
          <SelectTrigger className="w-48" aria-label={t('filters.preset')}>
            <SelectValue placeholder={t('filters.presetPlaceholder')} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="THIS_MONTH">{t('filters.presets.THIS_MONTH')}</SelectItem>
            <SelectItem value="LAST_MONTH">{t('filters.presets.LAST_MONTH')}</SelectItem>
            <SelectItem value="THIS_QUARTER">{t('filters.presets.THIS_QUARTER')}</SelectItem>
            <SelectItem value="THIS_FY">{t('filters.presets.THIS_FY')}</SelectItem>
            <SelectItem value="LAST_12_MONTHS">{t('filters.presets.LAST_12_MONTHS')}</SelectItem>
          </SelectContent>
        </Select>
      </div>
      <div className="space-y-1">
        <label className="text-xs text-muted-foreground">{t('filters.from')}</label>
        <Input type="date" className="w-40" value={from} onChange={(e) => onRangeChange(e.target.value, to)} />
      </div>
      <div className="space-y-1">
        <label className="text-xs text-muted-foreground">{t('filters.to')}</label>
        <Input type="date" className="w-40" value={to} onChange={(e) => onRangeChange(from, e.target.value)} />
      </div>
    </div>
  );
}
