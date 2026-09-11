import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Plus, Settings2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Skeleton } from '@/components/ui/skeleton';
import { FormError } from '@/components/forms/FormError';
import { useCan } from '@/hooks/useCan';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { getGridConfig, putGridConfig } from '@/features/builder/projects/api/projectApi';
import { getGrid, listPlots, updatePlotPosition } from '../api/plotApi';
import type { PlotFilter } from '../types';
import { PlotFilterBar } from './PlotFilterBar';
import { PlotSearchBox } from './PlotSearchBox';
import { PlotLegend } from './PlotLegend';
import { PlotGridCanvas } from './PlotGridCanvas';
import { PlotGridDom } from './PlotGridDom';
import { UnplacedPlotsTray } from './UnplacedPlotsTray';
import { PlotDetailDrawer } from './PlotDetailDrawer';
import { PlotForm } from './PlotForm';

const CANVAS_THRESHOLD = 400;

interface GridLayoutEditorProps {
  projectId: string;
  stateCode?: string;
}

export function GridLayoutEditor({ projectId, stateCode }: GridLayoutEditorProps) {
  const { t } = useTranslation(['plot', 'project', 'common']);
  const queryClient = useQueryClient();
  const canEdit = useCan('PLOT_EDIT');
  const canCreate = useCan('PLOT_CREATE');
  const canDelete = useCan('PLOT_DELETE');

  const [filter, setFilter] = useState<PlotFilter>({});
  const [search, setSearch] = useState('');
  const [selectedPlotId, setSelectedPlotId] = useState<string | null>(null);
  const [armedPlotId, setArmedPlotId] = useState<string | null>(null);
  const [addingPlot, setAddingPlot] = useState(false);
  const [configuringGrid, setConfiguringGrid] = useState(false);

  const gridQuery = useQuery({ queryKey: ['grid', projectId], queryFn: () => getGrid(projectId) });

  const positionMutation = useMutation({
    mutationFn: ({ plotId, gridRow, gridCol }: { plotId: string; gridRow: number; gridCol: number }) =>
      updatePlotPosition(plotId, gridRow, gridCol),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['grid', projectId] });
      setArmedPlotId(null);
    },
  });

  async function handleSelectPlotNumber(plotNumber: string) {
    const matches = await listPlots(projectId, { search: plotNumber }, 5);
    const exact = matches.find((p) => p.plotNumber === plotNumber) ?? matches[0];
    if (exact) setSelectedPlotId(exact.id);
  }

  function handleCellClick(gridRow: number, gridCol: number) {
    if (armedPlotId) {
      positionMutation.mutate({ plotId: armedPlotId, gridRow, gridCol });
    }
  }

  if (gridQuery.isLoading) {
    return <Skeleton className="h-96 w-full rounded-md" />;
  }
  const grid = gridQuery.data!;

  if (grid.rows === 0 || grid.cols === 0) {
    return (
      <div className="space-y-3">
        <p className="text-sm text-muted-foreground">{t('grid.notConfigured')}</p>
        {canEdit && (
          <Button onClick={() => setConfiguringGrid(true)}>
            <Settings2 className="size-4" />
            {t('project:detail.configureGrid')}
          </Button>
        )}
        <GridSizeDialog
          projectId={projectId}
          open={configuringGrid}
          onOpenChange={setConfiguringGrid}
        />
      </div>
    );
  }

  const useCanvas = grid.plots.length > CANVAS_THRESHOLD;

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-wrap items-center gap-2">
          <PlotSearchBox value={search} onChange={setSearch} />
          <PlotFilterBar filter={filter} onChange={setFilter} compact />
        </div>
        <div className="flex items-center gap-2">
          {canEdit && (
            <Button type="button" size="sm" variant="outline" onClick={() => setConfiguringGrid(true)}>
              <Settings2 className="size-4" />
            </Button>
          )}
          {canCreate && (
            <Button type="button" size="sm" onClick={() => setAddingPlot(true)}>
              <Plus className="size-4" />
              {t('project:detail.addPlot')}
            </Button>
          )}
        </div>
      </div>

      <PlotLegend />

      <div className="h-[480px]">
        {useCanvas ? (
          <PlotGridCanvas
            grid={grid}
            filter={filter}
            searchTerm={search || undefined}
            onSelectPlotNumber={handleSelectPlotNumber}
            armedPlacement={!!armedPlotId}
            onPlaceArmedPlot={handleCellClick}
          />
        ) : (
          <PlotGridDom
            grid={grid}
            filter={filter}
            searchTerm={search || undefined}
            onSelectPlotNumber={handleSelectPlotNumber}
            armedPlacement={!!armedPlotId}
            onPlaceArmedPlot={handleCellClick}
          />
        )}
      </div>

      <UnplacedPlotsTray unplacedIds={grid.unplaced} armedId={armedPlotId} onArm={setArmedPlotId} />

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
            <PlotForm
              projectId={projectId}
              stateCode={stateCode}
              onSuccess={() => setAddingPlot(false)}
              onCancel={() => setAddingPlot(false)}
            />
          </div>
        </SheetContent>
      </Sheet>

      <GridSizeDialog projectId={projectId} open={configuringGrid} onOpenChange={setConfiguringGrid} />
    </div>
  );
}

function GridSizeDialog({ projectId, open, onOpenChange }: { projectId: string; open: boolean; onOpenChange: (v: boolean) => void }) {
  const { t } = useTranslation(['plot', 'common', 'errors']);
  const queryClient = useQueryClient();
  const [rows, setRows] = useState('');
  const [cols, setCols] = useState('');

  const configQuery = useQuery({
    queryKey: ['grid-config', projectId],
    queryFn: () => getGridConfig(projectId),
    enabled: open,
  });

  const mutation = useMutation({
    mutationFn: () =>
      putGridConfig(projectId, {
        rows: Number(rows),
        cols: Number(cols),
        blockedCells: configQuery.data?.blockedCells ?? [],
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['grid', projectId] });
      onOpenChange(false);
    },
  });

  // Sync rows/cols from the fetched config, but ONLY while the user hasn't
  // touched either field yet (touchedRef). A plain `[open, configQuery.data]`
  // effect -- even guarded to fire "only once" -- still races: if the GET
  // resolves AFTER the user (or a fast-typing test) has already filled in
  // values but BEFORE that one-time sync runs, it silently overwrites their
  // input back to the fetched value (rows/cols null on a fresh project),
  // permanently disabling Save since the fields read empty again. Guarding
  // on "has the user edited anything" rather than "have we synced once" is
  // the actual fix -- once touched, the fetch is never allowed to clobber
  // the fields regardless of when it resolves. Caught by driving the dialog
  // through a real browser and typing immediately after it opens --
  // invisible from code review since the effect "looks" like ordinary
  // initial-value syncing.
  const touchedRef = useRef(false);
  useEffect(() => {
    if (!open) {
      touchedRef.current = false;
      return;
    }
    if (configQuery.data && !touchedRef.current) {
      setRows(String(configQuery.data.rows ?? ''));
      setCols(String(configQuery.data.cols ?? ''));
    }
  }, [open, configQuery.data]);

  const handleRowsChange = (value: string) => {
    touchedRef.current = true;
    setRows(value);
  };
  const handleColsChange = (value: string) => {
    touchedRef.current = true;
    setCols(value);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('project:detail.gridLayout')}</DialogTitle>
        </DialogHeader>
        <div className="grid grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label htmlFor="grid-rows">{t('form.fields.gridRow')}</Label>
            <Input id="grid-rows" type="number" min={1} max={500} value={rows} onChange={(e) => handleRowsChange(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="grid-cols">{t('form.fields.gridCol')}</Label>
            <Input id="grid-cols" type="number" min={1} max={500} value={cols} onChange={(e) => handleColsChange(e.target.value)} />
          </div>
        </div>
        {mutation.isError && <FormError message={resolveErrorMessage(mutation.error)} />}
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('common:actions.cancel')}
          </Button>
          <Button onClick={() => mutation.mutate()} disabled={mutation.isPending || !rows || !cols}>
            {t('common:actions.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
