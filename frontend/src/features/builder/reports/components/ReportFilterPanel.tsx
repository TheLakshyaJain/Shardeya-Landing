import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Input } from '@/components/ui/input';
import { Checkbox } from '@/components/ui/checkbox';
import { listProjects } from '../../projects/api/projectApi';
import { listBrokers } from '../../brokers/api/brokerApi';
import { listTeam } from '../../team/api/teamApi';
import type { ReportFilterDef } from '../types';

interface ReportFilterPanelProps {
  filters: ReportFilterDef[];
  values: Record<string, string>;
  onChange: (key: string, value: string) => void;
}

// M-10 §6 ReportFilterPanel: "dynamic from supported_filters" -- one
// generic renderer for every report's filter shape, driven entirely by
// the report_definition row's own declared filter list, rather than a
// bespoke filter bar per report.
export function ReportFilterPanel({ filters, values, onChange }: ReportFilterPanelProps) {
  const { t } = useTranslation('report');
  const projectsQuery = useQuery({
    queryKey: ['projects-for-report-filter'],
    queryFn: () => listProjects(undefined, 100),
    enabled: filters.some((f) => f.type === 'PROJECT'),
  });
  const brokersQuery = useQuery({
    queryKey: ['brokers-for-report-filter'],
    queryFn: () => listBrokers('ACTIVE'),
    enabled: filters.some((f) => f.type === 'BROKER'),
  });
  const staffQuery = useQuery({
    queryKey: ['staff-for-report-filter'],
    queryFn: () => listTeam(),
    enabled: filters.some((f) => f.type === 'STAFF'),
  });

  if (filters.length === 0) return null;

  return (
    <div className="mb-4 flex flex-wrap items-end gap-3">
      {filters.map((f) => {
        const label = t(`filters.${f.key}`, { defaultValue: f.key });
        if (f.type === 'PROJECT') {
          return (
            <div key={f.key} className="space-y-1">
              <label className="text-xs text-muted-foreground">{label}</label>
              <Select value={values[f.key] || '__all__'} onValueChange={(v) => onChange(f.key, v === '__all__' ? '' : v)}>
                <SelectTrigger className="w-48" aria-label={label}>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="__all__">{t('filters.all')}</SelectItem>
                  {(projectsQuery.data?.items ?? []).map((p) => (
                    <SelectItem key={p.id} value={p.id}>{p.name}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          );
        }
        if (f.type === 'BROKER') {
          return (
            <div key={f.key} className="space-y-1">
              <label className="text-xs text-muted-foreground">{label}</label>
              <Select value={values[f.key] || '__all__'} onValueChange={(v) => onChange(f.key, v === '__all__' ? '' : v)}>
                <SelectTrigger className="w-48" aria-label={label}>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="__all__">{t('filters.all')}</SelectItem>
                  {(brokersQuery.data ?? []).map((b) => (
                    <SelectItem key={b.id} value={b.id}>{b.fullName}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          );
        }
        if (f.type === 'STAFF') {
          return (
            <div key={f.key} className="space-y-1">
              <label className="text-xs text-muted-foreground">{label}</label>
              <Select value={values[f.key] || '__all__'} onValueChange={(v) => onChange(f.key, v === '__all__' ? '' : v)}>
                <SelectTrigger className="w-48" aria-label={label}>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="__all__">{t('filters.all')}</SelectItem>
                  {(staffQuery.data ?? []).map((s) => (
                    <SelectItem key={s.id} value={s.id}>{s.fullName}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          );
        }
        if (f.type === 'SELECT') {
          return (
            <div key={f.key} className="space-y-1">
              <label className="text-xs text-muted-foreground">{label}</label>
              <Select value={values[f.key] || '__all__'} onValueChange={(v) => onChange(f.key, v === '__all__' ? '' : v)}>
                <SelectTrigger className="w-40" aria-label={label}>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="__all__">{t('filters.all')}</SelectItem>
                  {(f.options ?? []).map((opt) => (
                    <SelectItem key={opt} value={opt}>{t(`filters.options.${opt}`, { defaultValue: opt })}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          );
        }
        if (f.type === 'DATE') {
          return (
            <div key={f.key} className="space-y-1">
              <label className="text-xs text-muted-foreground">{label}</label>
              <Input type="date" className="w-40" value={values[f.key] || ''} onChange={(e) => onChange(f.key, e.target.value)} />
            </div>
          );
        }
        if (f.type === 'NUMBER') {
          return (
            <div key={f.key} className="space-y-1">
              <label className="text-xs text-muted-foreground">{label}</label>
              <Input type="number" className="w-28" value={values[f.key] || ''} onChange={(e) => onChange(f.key, e.target.value)} />
            </div>
          );
        }
        // BOOLEAN
        return (
          <label key={f.key} className="flex items-center gap-2 pb-2 text-sm">
            <Checkbox checked={values[f.key] === 'true'} onCheckedChange={(c) => onChange(f.key, c ? 'true' : '')} />
            {label}
          </label>
        );
      })}
    </div>
  );
}
