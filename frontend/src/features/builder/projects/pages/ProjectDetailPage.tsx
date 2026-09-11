import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, MoreVertical, Pencil, Trash2, Upload } from 'lucide-react';
import { PageHeader } from '@/components/layout/PageHeader';
import { Button } from '@/components/ui/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { PhotoGrid } from '@/components/media/PhotoGrid';
import { ImageUploader } from '@/components/media/ImageUploader';
import { useCan } from '@/hooks/useCan';
import { formatIndianNumber } from '@/lib/formatters';
import { attachProjectMedia, deleteProject, detachProjectMedia, getProject, listProjectMedia } from '../api/projectApi';
import { ProjectStatusSelector } from '../components/ProjectStatusSelector';
import { GridLayoutEditor } from '@/features/builder/plots/components/GridLayoutEditor';
import { PlotsListTab } from '@/features/builder/plots/components/PlotsListTab';
import { ProjectLocationMap } from '@/components/maps/ProjectLocationMap';

export function ProjectDetailPage() {
  const { t } = useTranslation(['project', 'common']);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { id } = useParams<{ id: string }>();
  const [confirmingDelete, setConfirmingDelete] = useState(false);

  const canEdit = useCan('PROJECT_EDIT');
  const canDelete = useCan('PROJECT_DELETE');
  const canBulkUpload = useCan('PLOT_BULK_UPLOAD');
  // "Add Plots" is the single entry point for BOTH B-06 paths -- Quick
  // Create only needs PLOT_CREATE (no PLOT_BULK_UPLOAD), so the button must
  // stay visible for a role that can create plots even without bulk-upload
  // permission, not just the roles that already had the old Excel-only button.
  const canCreatePlots = useCan('PLOT_CREATE');

  const query = useQuery({ queryKey: ['project', id], queryFn: () => getProject(id!), enabled: !!id });

  const deleteMutation = useMutation({
    mutationFn: () => deleteProject(id!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects'] });
      navigate('/builder/projects');
    },
  });

  const galleryQuery = useQuery({
    queryKey: ['project-media', id, 'GALLERY'],
    queryFn: () => listProjectMedia(id!, 'GALLERY'),
    enabled: !!id,
  });

  const attachMutation = useMutation({
    mutationFn: ({ mediaId, role }: { mediaId: string; role: string }) => attachProjectMedia(id!, mediaId, role),
    onSuccess: (_, { role }) => {
      if (role === 'GALLERY') {
        queryClient.invalidateQueries({ queryKey: ['project-media', id, 'GALLERY'] });
      } else {
        queryClient.invalidateQueries({ queryKey: ['project', id] });
      }
    },
  });
  const detachMutation = useMutation({
    mutationFn: (mediaId: string) => detachProjectMedia(id!, mediaId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['project-media', id, 'GALLERY'] });
      queryClient.invalidateQueries({ queryKey: ['project', id] });
    },
  });

  if (query.isLoading) {
    return <Skeleton className="h-64 w-full rounded-md" />;
  }
  if (!query.data) {
    return <p className="text-sm text-muted-foreground">{t('detail.notFound')}</p>;
  }
  const project = query.data;

  return (
    <div>
      <Link to="/builder/projects" className="mb-2 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" />
        {t('detail.backToProjects')}
      </Link>

      <PageHeader
        title={project.name}
        description={`${project.locality}, ${project.city}`}
        actions={
          <>
            <ProjectStatusSelector projectId={project.id} status={project.status} canEdit={canEdit} />
            {(canEdit || canDelete) && (
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button size="icon" variant="outline">
                    <MoreVertical className="size-4" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  {canEdit && (
                    <DropdownMenuItem onClick={() => navigate(`/builder/projects/${project.id}/edit`)}>
                      <Pencil className="size-4" />
                      {t('detail.editProject')}
                    </DropdownMenuItem>
                  )}
                  {canDelete && (
                    <DropdownMenuItem variant="destructive" onClick={() => setConfirmingDelete(true)}>
                      <Trash2 className="size-4" />
                      {t('common:actions.delete')}
                    </DropdownMenuItem>
                  )}
                </DropdownMenuContent>
              </DropdownMenu>
            )}
          </>
        }
      />

      <div className="mb-4 grid grid-cols-4 gap-2 sm:max-w-md">
        <StatCard label={t('detail.plotCounts.total')} value={project.plotCounts.total} />
        <StatCard label={t('detail.plotCounts.available')} value={project.plotCounts.available} />
        <StatCard label={t('detail.plotCounts.reserved')} value={project.plotCounts.reserved} />
        <StatCard label={t('detail.plotCounts.sold')} value={project.plotCounts.sold} />
      </div>

      <Tabs defaultValue="overview">
        <div className="flex items-center justify-between">
          <TabsList>
            <TabsTrigger value="overview">{t('detail.tabs.overview')}</TabsTrigger>
            <TabsTrigger value="grid">{t('detail.tabs.grid')}</TabsTrigger>
            <TabsTrigger value="plots">{t('detail.tabs.plots')}</TabsTrigger>
            <TabsTrigger value="documents">{t('detail.tabs.documents')}</TabsTrigger>
          </TabsList>
          {(canCreatePlots || canBulkUpload) && (
            <Button asChild size="sm" variant="outline">
              <Link to={`/builder/projects/${project.id}/plots/new-bulk`}>
                <Upload className="size-4" />
                {t('detail.addPlots')}
              </Link>
            </Button>
          )}
        </div>

        <TabsContent value="overview" className="space-y-3 pt-4 text-sm">
          <dl className="grid grid-cols-2 gap-x-4 gap-y-2">
            <dt className="text-muted-foreground">{t('form.fields.address')}</dt>
            <dd>{project.address}</dd>
            <dt className="text-muted-foreground">{t('form.fields.totalArea')}</dt>
            <dd>
              {project.totalAreaValue} {project.totalAreaUnit} ({formatIndianNumber(project.totalAreaSqft)} sqft)
            </dd>
            <dt className="text-muted-foreground">{t('form.fields.declaredPlotCount')}</dt>
            <dd>{project.declaredPlotCount}</dd>
            {project.declaredVsActualDelta > 0 && (
              <>
                <dt className="text-muted-foreground" />
                <dd className="text-amber-600">{t('detail.declaredVsActual', { count: project.declaredVsActualDelta })}</dd>
              </>
            )}
            {project.reraNumber && (
              <>
                <dt className="text-muted-foreground">{t('form.fields.reraNumber')}</dt>
                <dd>{project.reraNumber}</dd>
              </>
            )}
            {project.description && (
              <>
                <dt className="text-muted-foreground">{t('form.fields.description')}</dt>
                <dd className="col-span-2">{project.description}</dd>
              </>
            )}
          </dl>

          <div className="pt-2">
            <ProjectLocationMap
              latitude={project.latitude}
              longitude={project.longitude}
              googleMapsUrl={project.googleMapsUrl}
              projectName={project.name}
              address={project.address}
              locality={project.locality}
              city={project.city}
              onEditLocation={canEdit ? () => navigate(`/builder/projects/${project.id}/edit`) : undefined}
            />
          </div>
        </TabsContent>

        <TabsContent value="grid" className="pt-4">
          <GridLayoutEditor projectId={project.id} stateCode={project.stateCode} />
        </TabsContent>

        <TabsContent value="plots" className="pt-4">
          <PlotsListTab projectId={project.id} stateCode={project.stateCode} />
        </TabsContent>

        <TabsContent value="documents" className="space-y-6 pt-4">
          <div className="flex flex-wrap gap-6">
            <ImageUploader
              label={t('form.coverPhoto')}
              mediaId={project.coverMediaId}
              purpose="project_cover"
              onUploaded={(mediaId) => attachMutation.mutate({ mediaId, role: 'COVER' })}
              onRemove={project.coverMediaId ? () => detachMutation.mutate(project.coverMediaId!) : undefined}
            />
            <ImageUploader
              label={t('form.layoutPlan')}
              mediaId={project.layoutMediaId}
              purpose="project_layout"
              onUploaded={(mediaId) => attachMutation.mutate({ mediaId, role: 'LAYOUT' })}
              onRemove={project.layoutMediaId ? () => detachMutation.mutate(project.layoutMediaId!) : undefined}
              allowDownload
            />
            <ImageUploader
              label={t('form.brochure')}
              mediaId={project.brochureMediaId}
              purpose="project_brochure"
              onUploaded={(mediaId) => attachMutation.mutate({ mediaId, role: 'BROCHURE' })}
              onRemove={project.brochureMediaId ? () => detachMutation.mutate(project.brochureMediaId!) : undefined}
              accept="image/*,application/pdf"
              allowDownload
            />
          </div>
          <PhotoGrid
            items={(galleryQuery.data ?? []).map((m) => ({ mediaId: m.mediaId, sortOrder: m.sortOrder }))}
            purpose="project_gallery"
            onAdd={(mediaId) => attachMutation.mutate({ mediaId, role: 'GALLERY' })}
            onRemove={(mediaId) => detachMutation.mutate(mediaId)}
            onReorder={() => {}}
          />
        </TabsContent>
      </Tabs>

      <Dialog open={confirmingDelete} onOpenChange={setConfirmingDelete}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('detail.deleteConfirmTitle')}</DialogTitle>
            <DialogDescription>{t('detail.deleteConfirmDescription')}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirmingDelete(false)}>
              {t('common:actions.cancel')}
            </Button>
            <Button variant="destructive" onClick={() => deleteMutation.mutate()} disabled={deleteMutation.isPending}>
              {t('common:actions.delete')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function StatCard({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-md border border-border p-2 text-center">
      <p className="text-lg font-semibold text-foreground">{value}</p>
      <p className="text-xs text-muted-foreground">{label}</p>
    </div>
  );
}
