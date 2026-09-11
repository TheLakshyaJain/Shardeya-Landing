import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Plus } from 'lucide-react';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Button } from '@/components/ui/button';
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { EmptyState } from '@/components/data/EmptyState';
import { Skeleton } from '@/components/ui/skeleton';
import { useCan } from '@/hooks/useCan';
import { useDebounce } from '@/hooks/useDebounce';
import { formatIndianCurrency, formatIndianNumber } from '@/lib/formatters';
import { listPlots } from '../api/plotApi';
import type { PlotFilter } from '../types';
import { PlotFilterBar } from './PlotFilterBar';
import { PlotSearchBox } from './PlotSearchBox';
import { PlotStatusBadge } from './PlotStatusBadge';
import { PlotDetailDrawer } from './PlotDetailDrawer';
import { PlotForm } from './PlotForm';

interface PlotsListTabProps {
  projectId: string;
  stateCode?: string;
}

export function PlotsListTab({ projectId, stateCode }: PlotsListTabProps) {
  const { t } = useTranslation(['plot', 'common']);
  const canCreate = useCan('PLOT_CREATE');
  const canEdit = useCan('PLOT_EDIT');
  const canDelete = useCan('PLOT_DELETE');

  const [filter, setFilter] = useState<PlotFilter>({});
  const [search, setSearch] = useState('');
  const debouncedSearch = useDebounce(search, 300);
  const [selectedPlotId, setSelectedPlotId] = useState<string | null>(null);
  const [addingPlot, setAddingPlot] = useState(false);

  const query = useQuery({
    queryKey: ['plots', projectId, filter, debouncedSearch],
    queryFn: () => listPlots(projectId, { ...filter, search: debouncedSearch || undefined }),
  });

  const plots = query.data ?? [];

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-wrap items-center gap-2">
          <PlotSearchBox value={search} onChange={setSearch} />
          <PlotFilterBar filter={filter} onChange={setFilter} />
        </div>
        {canCreate && (
          <Button size="sm" onClick={() => setAddingPlot(true)}>
            <Plus className="size-4" />
            {t('form.createTitle')}
          </Button>
        )}
      </div>

      <p className="text-sm text-muted-foreground">{t('filter.resultsCount', { count: plots.length })}</p>

      {query.isLoading ? (
        <Skeleton className="h-64 w-full rounded-md" />
      ) : plots.length === 0 ? (
        <EmptyState title={t('common:emptyState.genericTitle')} description={t('common:emptyState.genericDescription')} />
      ) : (
        <div className="overflow-auto rounded-md border border-border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('form.fields.plotNumber')}</TableHead>
                <TableHead>{t('form.fields.status')}</TableHead>
                <TableHead>{t('form.fields.sizeValue')}</TableHead>
                <TableHead>{t('form.fields.price')}</TableHead>
                <TableHead>{t('form.fields.facing')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {plots.map((plot) => (
                <TableRow key={plot.id} className="cursor-pointer" onClick={() => setSelectedPlotId(plot.id)}>
                  <TableCell className="font-medium">{plot.plotNumber}</TableCell>
                  <TableCell>
                    <PlotStatusBadge status={plot.status} />
                  </TableCell>
                  <TableCell>{formatIndianNumber(plot.sizeSqft)} sqft</TableCell>
                  <TableCell>{formatIndianCurrency(plot.price)}</TableCell>
                  <TableCell>{plot.facing ? t(`facing.${plot.facing}`) : '—'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      <PlotDetailDrawer
        projectId={projectId}
        plotId={selectedPlotId}
        stateCode={stateCode}
        onClose={() => setSelectedPlotId(null)}
        canEdit={canEdit}
        canDelete={canDelete}
      />

      <Sheet open={addingPlot} onOpenChange={setAddingPlot}>
        <SheetContent className="w-full overflow-y-auto sm:max-w-md">
          <SheetHeader>
            <SheetTitle>{t('form.createTitle')}</SheetTitle>
          </SheetHeader>
          <div className="px-4 pb-4">
            <PlotForm projectId={projectId} stateCode={stateCode} onSuccess={() => setAddingPlot(false)} onCancel={() => setAddingPlot(false)} />
          </div>
        </SheetContent>
      </Sheet>
    </div>
  );
}
