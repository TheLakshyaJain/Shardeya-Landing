import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useNavigate, useParams } from 'react-router-dom';
import { PageHeader } from '@/components/layout/PageHeader';
import { Button } from '@/components/ui/button';
import { getProject } from '@/features/builder/projects/api/projectApi';
import { RangeCreateForm } from '../components/RangeCreateForm';

// B-06: Quick Create (Path A) only. Excel import (Path B) needs a Pro/
// Premium plan (BULK_UPLOAD_ENABLED) that doesn't exist in this app yet --
// it was previously offered here as a permanently-locked, unclickable card
// (no plan can ever unlock it today), which is dead-end clutter rather than
// a real choice. Removed per direct user feedback; the import route/backend
// themselves are untouched, this just stops advertising an entry point that
// can never actually be used right now.
export function AddPlotsPage() {
  const { t } = useTranslation(['plot', 'common']);
  const navigate = useNavigate();
  const { projectId } = useParams<{ projectId: string }>();
  const [summary, setSummary] = useState<{ created: number; unplaced: number } | null>(null);

  const { data: project } = useQuery({ queryKey: ['project', projectId], queryFn: () => getProject(projectId!), enabled: !!projectId });

  if (summary) {
    return (
      <div className="mx-auto max-w-lg space-y-4 py-8 text-center">
        <h1 className="text-lg font-semibold">{t('quickCreate.summary.title', { count: summary.created })}</h1>
        {summary.unplaced > 0 && (
          <p className="text-sm text-muted-foreground">{t('quickCreate.summary.unplaced', { count: summary.unplaced })}</p>
        )}
        <div className="flex justify-center gap-2">
          <Button variant="outline" onClick={() => setSummary(null)}>
            {t('quickCreate.summary.createMore')}
          </Button>
          <Button onClick={() => navigate(`/builder/projects/${projectId}`)}>{t('quickCreate.summary.viewGrid')}</Button>
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <PageHeader title={t('addPlotsPage.title')} description={project ? project.name : undefined} />
      <RangeCreateForm
        projectId={projectId!}
        stateCode={project?.stateCode}
        onSuccess={setSummary}
        onCancel={() => navigate(`/builder/projects/${projectId}`)}
      />
    </div>
  );
}
