import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { FormError } from '@/components/forms/FormError';
import { EmptyState } from '@/components/data/EmptyState';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { formatIndianCurrency } from '@/lib/formatters';
import { createCommissionConfig, deleteCommissionConfig, listCommissionConfigs, previewCommission } from '../api/brokerApi';
import { listProjects } from '@/features/builder/projects/api/projectApi';
import { listPlots } from '@/features/builder/plots/api/plotApi';
import type { CommissionConfigScope, CommissionType } from '../types';

export function CommissionConfigTab({ brokerId, canManage }: { brokerId: string; canManage: boolean }) {
  const { t } = useTranslation(['broker', 'common']);
  const queryClient = useQueryClient();
  const [addOpen, setAddOpen] = useState(false);

  const configsQuery = useQuery({
    queryKey: ['commission-configs', brokerId],
    queryFn: () => listCommissionConfigs(brokerId),
    enabled: canManage, // admin-only surface, matching CommissionConfigService.list()'s server-side gate
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteCommissionConfig(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['commission-configs', brokerId] }),
  });

  if (!canManage) {
    return <p className="text-sm text-muted-foreground">{t('common:permission.adminOnly', { defaultValue: 'Only an Admin can view commission configuration.' })}</p>;
  }

  const configs = configsQuery.data ?? [];

  return (
    <div className="space-y-4">
      <div className="flex justify-between">
        <h3 className="text-sm font-medium">{t('commissionConfig.title')}</h3>
        <Button size="sm" onClick={() => setAddOpen(true)}>
          <Plus className="size-4" />
          {t('commissionConfig.addConfig')}
        </Button>
      </div>

      {configs.length === 0 ? (
        <EmptyState title={t('commissionConfig.empty')} />
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t('commissionConfig.columns.scope')}</TableHead>
              <TableHead>{t('commissionConfig.columns.target')}</TableHead>
              <TableHead>{t('commissionConfig.columns.type')}</TableHead>
              <TableHead>{t('commissionConfig.columns.rate')}</TableHead>
              <TableHead>{t('commissionConfig.columns.effectiveFrom')}</TableHead>
              <TableHead>{t('commissionConfig.columns.actions')}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {configs.map((c) => (
              <TableRow key={c.id}>
                <TableCell>{t(`commissionConfig.scope${c.scope.charAt(0)}${c.scope.slice(1).toLowerCase()}`)}</TableCell>
                <TableCell>{c.plotNumber ?? c.projectName ?? '—'}</TableCell>
                <TableCell>{c.commissionType}</TableCell>
                <TableCell>{c.commissionType === 'PERCENTAGE' ? `${c.rateValue}%` : formatIndianCurrency(c.rateValue)}</TableCell>
                <TableCell>{c.effectiveFrom}</TableCell>
                <TableCell>
                  <Button variant="ghost" size="icon" onClick={() => deleteMutation.mutate(c.id)}>
                    <Trash2 className="size-4" />
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <CommissionPreviewCard brokerId={brokerId} />

      <AddCommissionConfigDialog brokerId={brokerId} open={addOpen} onOpenChange={setAddOpen} />
    </div>
  );
}

function CommissionPreviewCard({ brokerId }: { brokerId: string }) {
  const { t } = useTranslation('broker');
  const [dealValue, setDealValue] = useState('');
  const [projectId, setProjectId] = useState<string | undefined>(undefined);

  const projectsQuery = useQuery({ queryKey: ['projects-for-preview'], queryFn: () => listProjects(undefined, 100) });

  const mutation = useMutation({
    mutationFn: () => previewCommission(brokerId, { dealValue: Number(dealValue), projectId }),
  });

  return (
    <div className="rounded-lg border border-border p-4">
      <h4 className="mb-3 text-sm font-medium">{t('commissionConfig.preview.title')}</h4>
      <div className="flex flex-wrap items-end gap-3">
        <div className="space-y-1">
          <Label htmlFor="preview-dealValue">{t('commissionConfig.preview.dealValue')}</Label>
          <Input id="preview-dealValue" type="number" value={dealValue} onChange={(e) => setDealValue(e.target.value)} className="w-40" />
        </div>
        <div className="space-y-1">
          <Label htmlFor="preview-project">{t('commissionConfig.project')}</Label>
          <Select value={projectId} onValueChange={setProjectId}>
            <SelectTrigger id="preview-project" className="w-48" aria-label={t('commissionConfig.project')}>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {(projectsQuery.data?.items ?? []).map((p) => (
                <SelectItem key={p.id} value={p.id}>
                  {p.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <Button disabled={!dealValue || mutation.isPending} onClick={() => mutation.mutate()}>
          {t('commissionConfig.preview.calculate')}
        </Button>
      </div>

      {mutation.isSuccess && (
        <div className="mt-3 space-y-1 text-sm">
          <p>
            {t('commissionConfig.preview.base')}: {formatIndianCurrency(mutation.data.baseCommission)}
          </p>
          {mutation.data.tierBonus > 0 && (
            <p>
              {t('commissionConfig.preview.tierBonus', { tierName: mutation.data.tierName ?? '' })}: {formatIndianCurrency(mutation.data.tierBonus)}
            </p>
          )}
          <p className="font-semibold">
            {t('commissionConfig.preview.total')}: {formatIndianCurrency(mutation.data.totalCommission)}
          </p>
          <p className="text-xs text-muted-foreground">
            {mutation.data.usedBrokerDefault
              ? t('commissionConfig.preview.usedDefault')
              : t('commissionConfig.preview.appliedScope', { scope: mutation.data.appliedScope })}
          </p>
        </div>
      )}
      {mutation.isError && <FormError message={resolveErrorMessage(mutation.error)} />}
    </div>
  );
}

function AddCommissionConfigDialog({
  brokerId,
  open,
  onOpenChange,
}: {
  brokerId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useTranslation(['broker', 'common']);
  const queryClient = useQueryClient();
  const [scope, setScope] = useState<CommissionConfigScope>('GLOBAL');
  const [projectId, setProjectId] = useState<string | undefined>(undefined);
  const [plotId, setPlotId] = useState<string | undefined>(undefined);
  const [commissionType, setCommissionType] = useState<CommissionType>('PERCENTAGE');
  const [rateValue, setRateValue] = useState('');
  const [effectiveFrom, setEffectiveFrom] = useState(() => new Date().toISOString().slice(0, 10));

  const projectsQuery = useQuery({ queryKey: ['projects-for-preview'], queryFn: () => listProjects(undefined, 100), enabled: open });
  const plotsQuery = useQuery({
    queryKey: ['plots-for-config', projectId],
    queryFn: () => listPlots(projectId!),
    enabled: open && scope === 'PLOT' && !!projectId,
  });

  const mutation = useMutation({
    mutationFn: () =>
      createCommissionConfig(brokerId, {
        scope,
        projectId: scope !== 'GLOBAL' ? projectId : undefined,
        plotId: scope === 'PLOT' ? plotId : undefined,
        commissionType,
        rateValue: Number(rateValue),
        effectiveFrom,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['commission-configs', brokerId] });
      onOpenChange(false);
    },
  });

  const canSubmit =
    rateValue !== '' &&
    effectiveFrom !== '' &&
    (scope === 'GLOBAL' || (scope === 'PROJECT' && projectId) || (scope === 'PLOT' && projectId && plotId));

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('commissionConfig.addConfig')}</DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
          <div className="space-y-2">
            <Label htmlFor="config-scope">{t('commissionConfig.scope')}</Label>
            <Select value={scope} onValueChange={(v) => setScope(v as CommissionConfigScope)}>
              <SelectTrigger id="config-scope" className="w-full" aria-label={t('commissionConfig.scope')}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="GLOBAL">{t('commissionConfig.scopeGlobal')}</SelectItem>
                <SelectItem value="PROJECT">{t('commissionConfig.scopeProject')}</SelectItem>
                <SelectItem value="PLOT">{t('commissionConfig.scopePlot')}</SelectItem>
              </SelectContent>
            </Select>
          </div>
          {scope !== 'GLOBAL' && (
            <div className="space-y-2">
              <Label htmlFor="config-project">{t('commissionConfig.project')}</Label>
              <Select value={projectId} onValueChange={setProjectId}>
                <SelectTrigger id="config-project" className="w-full" aria-label={t('commissionConfig.project')}>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {(projectsQuery.data?.items ?? []).map((p) => (
                    <SelectItem key={p.id} value={p.id}>
                      {p.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          )}
          {scope === 'PLOT' && projectId && (
            <div className="space-y-2">
              <Label htmlFor="config-plot">{t('commissionConfig.plot')}</Label>
              <Select value={plotId} onValueChange={setPlotId}>
                <SelectTrigger id="config-plot" className="w-full" aria-label={t('commissionConfig.plot')}>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {(plotsQuery.data ?? []).map((p) => (
                    <SelectItem key={p.id} value={p.id}>
                      {p.plotNumber}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          )}
          <div className="space-y-2">
            <Label htmlFor="config-commissionType">{t('commissionConfig.commissionType')}</Label>
            <Select value={commissionType} onValueChange={(v) => setCommissionType(v as CommissionType)}>
              <SelectTrigger id="config-commissionType" className="w-full" aria-label={t('commissionConfig.commissionType')}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="PERCENTAGE">{t('form.commissionTypePercentage')}</SelectItem>
                <SelectItem value="FIXED">{t('form.commissionTypeFixed')}</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label htmlFor="config-rateValue">{t('commissionConfig.rateValue')}</Label>
            <Input id="config-rateValue" type="number" step="0.001" value={rateValue} onChange={(e) => setRateValue(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="config-effectiveFrom">{t('commissionConfig.effectiveFrom')}</Label>
            <Input id="config-effectiveFrom" type="date" value={effectiveFrom} onChange={(e) => setEffectiveFrom(e.target.value)} />
          </div>
          {mutation.isError && <FormError message={resolveErrorMessage(mutation.error)} />}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('common:actions.cancel')}
          </Button>
          <Button disabled={!canSubmit || mutation.isPending} onClick={() => mutation.mutate()}>
            {t('commissionConfig.submit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
