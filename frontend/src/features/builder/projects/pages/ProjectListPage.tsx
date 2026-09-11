import { useMemo, useState } from 'react';
import { useInfiniteQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Building2, Plus } from 'lucide-react';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/data/EmptyState';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Skeleton } from '@/components/ui/skeleton';
import { useCan } from '@/hooks/useCan';
import { listProjects } from '../api/projectApi';
import { ProjectCard } from '../components/ProjectCard';

export function ProjectListPage() {
  const { t } = useTranslation(['project', 'common']);
  const [search, setSearch] = useState('');
  const canCreate = useCan('PROJECT_CREATE');

  const query = useInfiniteQuery({
    queryKey: ['projects'],
    queryFn: ({ pageParam }) => listProjects(pageParam),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => (lastPage.hasMore ? (lastPage.nextCursor ?? undefined) : undefined),
  });

  const allProjects = useMemo(() => query.data?.pages.flatMap((p) => p.items) ?? [], [query.data]);

  // ProjectController.list() only takes cursor/limit — no search param this
  // milestone. Filtering client-side over loaded pages is fine at the
  // project-count scale this app has (a handful to low hundreds), not a
  // stand-in for real server-side search.
  const filtered = search.trim()
    ? allProjects.filter(
        (p) =>
          p.name.toLowerCase().includes(search.toLowerCase()) || p.city.toLowerCase().includes(search.toLowerCase()),
      )
    : allProjects;

  return (
    <div>
      <PageHeader
        title={t('list.title')}
        actions={
          canCreate && (
            <Button asChild>
              <Link to="/builder/projects/new">
                <Plus className="size-4" />
                {t('list.newProject')}
              </Link>
            </Button>
          )
        }
      />

      <Input
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        placeholder={t('list.searchPlaceholder')}
        className="mb-4 max-w-sm"
      />

      {query.isLoading ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-32 rounded-xl" />
          ))}
        </div>
      ) : filtered.length === 0 ? (
        <EmptyState
          icon={<Building2 className="size-10" />}
          title={t('list.empty.title')}
          description={t('list.empty.description')}
          action={
            canCreate && (
              <Button asChild>
                <Link to="/builder/projects/new">{t('list.newProject')}</Link>
              </Button>
            )
          }
        />
      ) : (
        <>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {filtered.map((project) => (
              <ProjectCard key={project.id} project={project} />
            ))}
          </div>
          {query.hasNextPage && (
            <div className="mt-4 flex justify-center">
              <Button variant="outline" onClick={() => query.fetchNextPage()} disabled={query.isFetchingNextPage}>
                {t('list.loadMore')}
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
