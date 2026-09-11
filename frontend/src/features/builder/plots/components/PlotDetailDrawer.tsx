import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Pencil, Tag, Trash2 } from 'lucide-react';
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { formatIndianCurrency, formatIndianNumber } from '@/lib/formatters';
import { useCan } from '@/hooks/useCan';
import { getPlot, deletePlot } from '../api/plotApi';
import { PlotStatusBadge } from './PlotStatusBadge';
import { PlotForm } from './PlotForm';
import { SaleWizard } from '@/features/builder/sales/components/SaleWizard';
import { SaleDetailPanel } from '@/features/builder/sales/components/SaleDetailPanel';

interface PlotDetailDrawerProps {
  projectId: string;
  plotId: string | null;
  stateCode?: string;
  onClose: () => void;
  canEdit: boolean;
  canDelete: boolean;
}

export function PlotDetailDrawer({ projectId, plotId, stateCode, onClose, canEdit, canDelete }: PlotDetailDrawerProps) {
  const { t } = useTranslation(['plot', 'sale', 'common']);
  const queryClient = useQueryClient();
  // M4: "Mark as Sold" creates a plot_sale, which is DATA_EDIT_ALL-gated
  // server-side (B-04 §9, Admin/Manager) -- distinct from PLOT_EDIT (the
  // `canEdit` prop here), which only governs editing the plot ROW itself.
  // Reusing PLOT_EDIT for this button happened to work while every role
  // held every permission (M1-M3); it stopped being correct the moment a
  // real role matrix existed. Viewing the sale/payment panel at all requires
  // FINANCIAL_VIEW -- Sales Executive must not see financials from B-04/B-05
  // via any screen, not just the API (the API itself already 403s; this is
  // the UI not dangling a panel that would just show empty/error state).
  const canCreateSale = useCan('DATA_EDIT_ALL');
  const canViewFinancial = useCan('FINANCIAL_VIEW');
  const [editing, setEditing] = useState(false);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [selling, setSelling] = useState(false);

  const query = useQuery({ queryKey: ['plot', plotId], queryFn: () => getPlot(plotId!), enabled: !!plotId });

  const deleteMutation = useMutation({
    mutationFn: () => deletePlot(plotId!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['grid', projectId] });
      queryClient.invalidateQueries({ queryKey: ['plots', projectId] });
      queryClient.invalidateQueries({ queryKey: ['plot-stats', projectId] });
      // ProjectDetailPage's dashboard StatCards (total/available/reserved/
      // sold) read plotCounts off THIS query, not plot-stats -- deleting a
      // plot changes that total, but nothing invalidated it, so the overview
      // page's counts stayed stale until an unrelated navigation remounted
      // the page and refetched. Same root cause as PlotForm's own
      // create/edit success handler below (missing the identical key).
      queryClient.invalidateQueries({ queryKey: ['project', projectId] });
      setConfirmingDelete(false);
      onClose();
    },
  });

  const plot = query.data;

  return (
    <>
      <Sheet
        open={!!plotId}
        onOpenChange={(open) => {
          if (!open) {
            setEditing(false);
            onClose();
          }
        }}
      >
        <SheetContent className="w-full overflow-y-auto sm:max-w-md">
          <SheetHeader>
            <SheetTitle>{plot ? t('detail.drawerTitle', { number: plot.plotNumber }) : ''}</SheetTitle>
          </SheetHeader>

          <div className="px-4 pb-4">
            {!plot ? null : editing ? (
              <PlotForm
                projectId={projectId}
                plot={plot}
                stateCode={stateCode}
                onSuccess={() => setEditing(false)}
                onCancel={() => setEditing(false)}
              />
            ) : (
              <div className="space-y-4">
                <div className="flex items-center justify-between">
                  <PlotStatusBadge status={plot.status} />
                  <div className="flex gap-2">
                    {canCreateSale && plot.status !== 'SOLD' && (
                      <Button size="sm" onClick={() => setSelling(true)}>
                        <Tag className="size-3.5" />
                        {t('detail.markAsSold', { ns: 'sale' })}
                      </Button>
                    )}
                    {/* Real bug found live: this button had no status guard
                        at all, so it stayed visible and functional even
                        after Mark Complete. Now matches its Delete sibling's
                        own existing plot.status !== 'SOLD' condition just
                        below -- the backend enforces the same rule
                        server-side (PlotService.update()), this is the
                        cosmetic half (CLAUDE.md rule #5). */}
                    {canEdit && plot.status !== 'SOLD' && (
                      <Button size="sm" variant="outline" onClick={() => setEditing(true)}>
                        <Pencil className="size-3.5" />
                        {t('common:actions.edit')}
                      </Button>
                    )}
                    {canDelete && plot.status !== 'SOLD' && (
                      <Button size="sm" variant="outline" onClick={() => setConfirmingDelete(true)}>
                        <Trash2 className="size-3.5" />
                      </Button>
                    )}
                  </div>
                </div>

                <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
                  <dt className="text-muted-foreground">{t('form.fields.sizeValue')}</dt>
                  <dd>
                    {plot.sizeValue} {plot.sizeUnit} ({formatIndianNumber(plot.sizeSqft)} sqft)
                  </dd>
                  <dt className="text-muted-foreground">{t('form.fields.price')}</dt>
                  <dd>{formatIndianCurrency(plot.price)}</dd>
                  <dt className="text-muted-foreground">{t('form.fields.pricePerUnit')}</dt>
                  <dd>{plot.pricePerUnit !== null ? formatIndianCurrency(plot.pricePerUnit) : '—'}</dd>
                  {plot.facing && (
                    <>
                      <dt className="text-muted-foreground">{t('form.fields.facing')}</dt>
                      <dd>{t(`facing.${plot.facing}`)}</dd>
                    </>
                  )}
                  {plot.reservedFor && (
                    <>
                      <dt className="text-muted-foreground">{t('form.fields.reservedFor')}</dt>
                      <dd>{plot.reservedFor}</dd>
                    </>
                  )}
                  {plot.remarks && (
                    <>
                      <dt className="text-muted-foreground">{t('form.fields.remarks')}</dt>
                      <dd className="col-span-2">{plot.remarks}</dd>
                    </>
                  )}
                </dl>

                <div className="flex gap-2 text-xs text-muted-foreground">
                  {plot.isGarden && <span>{t('form.flags.isGarden')}</span>}
                  {plot.isCorner && <span>{t('form.flags.isCorner')}</span>}
                  {plot.isHot && <span>{t('form.flags.isHot')}</span>}
                </div>

                {plot.status === 'SOLD' && plot.currentSaleId && canViewFinancial && (
                  <div className="border-t border-border pt-4">
                    <SaleDetailPanel plotId={plot.id} projectId={projectId} onCancelled={() => query.refetch()} />
                  </div>
                )}
              </div>
            )}
          </div>
        </SheetContent>
      </Sheet>

      <Dialog open={selling} onOpenChange={setSelling}>
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-lg">
          {plot && (
            <SaleWizard
              projectId={projectId}
              plotId={plot.id}
              plotNumber={plot.plotNumber}
              onSuccess={() => {
                setSelling(false);
                query.refetch();
              }}
              onCancel={() => setSelling(false)}
            />
          )}
        </DialogContent>
      </Dialog>

      <Dialog open={confirmingDelete} onOpenChange={setConfirmingDelete}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{plot && t('detail.deleteConfirmTitle', { number: plot.plotNumber })}</DialogTitle>
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
    </>
  );
}
