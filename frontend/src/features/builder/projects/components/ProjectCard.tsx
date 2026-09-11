import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Card, CardContent, CardHeader } from '@/components/ui/card';
import { ProjectStatusBadge } from './ProjectStatusBadge';
import type { ProjectResponse } from '../types';

export function ProjectCard({ project }: { project: ProjectResponse }) {
  const { t } = useTranslation('project');

  return (
    <Link to={`/builder/projects/${project.id}`}>
      <Card className="h-full py-4 transition-shadow hover:shadow-md">
        <CardHeader className="px-4">
          <div className="flex items-start justify-between gap-2">
            <h3 className="text-base font-semibold text-foreground">{project.name}</h3>
            <ProjectStatusBadge status={project.status} />
          </div>
          <p className="text-sm text-muted-foreground">
            {project.locality}, {project.city}
          </p>
        </CardHeader>
        <CardContent className="px-4">
          <p className="text-sm text-muted-foreground">
            {project.plotCounts.total > 0
              ? t('card.plotsSold', { sold: project.plotCounts.sold, total: project.plotCounts.total })
              : t('card.noPlots')}
          </p>
        </CardContent>
      </Card>
    </Link>
  );
}
