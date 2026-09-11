import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { updateProjectStatus } from '../api/projectApi';
import { ProjectStatusBadge } from './ProjectStatusBadge';
import type { ProjectStatus } from '../types';

const STATUSES: ProjectStatus[] = ['UPCOMING', 'ACTIVE', 'COMPLETED'];

interface ProjectStatusSelectorProps {
  projectId: string;
  status: ProjectStatus;
  canEdit: boolean;
}

// Read-only badge for viewers; a plain Select for editors. Any status can be
// picked directly -- ProjectService.updateStatus (backend) has no transition
// guard (UPCOMING/ACTIVE/COMPLETED can move freely in either direction), so
// this doesn't restrict which options are shown either.
export function ProjectStatusSelector({ projectId, status, canEdit }: ProjectStatusSelectorProps) {
  const { t } = useTranslation('project');
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: (next: ProjectStatus) => updateProjectStatus(projectId, next),
    onSuccess: (result) => {
      queryClient.setQueryData(['project', projectId], result);
      queryClient.invalidateQueries({ queryKey: ['projects'] });
    },
  });

  if (!canEdit) {
    return <ProjectStatusBadge status={status} />;
  }

  return (
    <Select value={status} onValueChange={(v) => mutation.mutate(v as ProjectStatus)} disabled={mutation.isPending}>
      <SelectTrigger className="w-auto min-w-32" size="sm" aria-label={t('form.fields.status')}>
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {STATUSES.map((s) => (
          <SelectItem key={s} value={s}>
            {t(`status.${s}`)}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
