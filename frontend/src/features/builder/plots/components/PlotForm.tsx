import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Checkbox } from '@/components/ui/checkbox';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Button } from '@/components/ui/button';
import { FormError } from '@/components/forms/FormError';
import { AreaInput } from '@/components/forms/AreaInput';
import { EntitlementGuard } from '@/components/feedback/EntitlementGuard';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { createPlot, updatePlot, updatePlotStatus } from '../api/plotApi';
import { buildPlotSchema, type PlotFormValues } from '../schemas';
import type { PlotFacing, PlotResponse } from '../types';
import { PlotStatusSelector } from './PlotStatusSelector';

const FACINGS: PlotFacing[] = ['N', 'S', 'E', 'W', 'NE', 'NW', 'SE', 'SW'];

interface PlotFormProps {
  projectId: string;
  plot?: PlotResponse;
  stateCode?: string;
  defaultPosition?: { gridRow: number; gridCol: number };
  onSuccess: (plot: PlotResponse) => void;
  onCancel: () => void;
}

export function PlotForm({ projectId, plot, stateCode, defaultPosition, onSuccess, onCancel }: PlotFormProps) {
  const { t } = useTranslation(['plot', 'common', 'errors']);
  const queryClient = useQueryClient();
  const isEdit = !!plot;

  const schema = buildPlotSchema(t);
  const form = useForm<PlotFormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      plotNumber: plot?.plotNumber ?? '',
      status: plot?.status ?? 'AVAILABLE',
      reservedFor: plot?.reservedFor ?? '',
      reservedUntil: plot?.reservedUntil ?? '',
      sizeValue: plot?.sizeValue,
      sizeUnit: plot?.sizeUnit ?? 'SQ_FT',
      facing: plot?.facing ?? undefined,
      price: plot?.price,
      isGarden: plot?.isGarden ?? false,
      isCorner: plot?.isCorner ?? false,
      isHot: plot?.isHot ?? false,
      remarks: plot?.remarks ?? '',
      gridRow: plot?.gridRow ?? defaultPosition?.gridRow,
      gridCol: plot?.gridCol ?? defaultPosition?.gridCol,
    },
  });

  const values = form.watch();

  const mutation = useMutation({
    mutationFn: async (v: PlotFormValues) => {
      if (isEdit) {
        // PlotUpdateRequest (the general PATCH /plots/{id} this hits) has no
        // status/reservedFor/reservedUntil fields at all -- status changes
        // go through the dedicated PATCH /plots/{id}/status endpoint
        // instead, which is where AVAILABLE<->RESERVED's guard (and the
        // "never direct-edit to/from SOLD" rule) actually lives. Previously
        // this form didn't even render the status control in edit mode, so
        // a RESERVED plot had no way back to AVAILABLE short of the API.
        const updated = await updatePlot(plot.id, {
          plotNumber: v.plotNumber,
          sizeValue: v.sizeValue,
          sizeUnit: v.sizeUnit,
          facing: v.facing,
          price: v.price,
          isGarden: v.isGarden,
          isCorner: v.isCorner,
          isHot: v.isHot,
          remarks: v.remarks || undefined,
        });
        if (plot.status === 'SOLD' || v.status === plot.status) {
          return updated;
        }
        return updatePlotStatus(plot.id, {
          status: v.status,
          reservedFor: v.status === 'RESERVED' ? v.reservedFor || undefined : undefined,
          reservedUntil: v.status === 'RESERVED' ? v.reservedUntil || undefined : undefined,
        });
      }
      return createPlot(projectId, {
        plotNumber: v.plotNumber,
        status: v.status === 'SOLD' ? undefined : v.status,
        reservedFor: v.reservedFor || undefined,
        reservedUntil: v.reservedUntil || undefined,
        sizeValue: v.sizeValue,
        sizeUnit: v.sizeUnit,
        facing: v.facing,
        price: v.price,
        isGarden: v.isGarden,
        isCorner: v.isCorner,
        isHot: v.isHot,
        remarks: v.remarks || undefined,
        gridRow: v.gridRow,
        gridCol: v.gridCol,
      });
    },
    onSuccess: (result) => {
      // PlotDetailDrawer/GridLayoutEditor's own ['plot', plotId] query is a
      // SEPARATE cache entry from the ones below -- without updating it too,
      // editing a plot here (this form is embedded in that same drawer) left
      // the drawer showing the pre-edit values after "Save Changes" until an
      // unrelated refetch happened to occur, even though the edit succeeded
      // server-side. setQueryData with the mutation's own response is both
      // correct and avoids a redundant round trip vs. invalidating + refetching.
      queryClient.setQueryData(['plot', result.id], result);
      queryClient.invalidateQueries({ queryKey: ['grid', projectId] });
      queryClient.invalidateQueries({ queryKey: ['plots', projectId] });
      queryClient.invalidateQueries({ queryKey: ['plot-stats', projectId] });
      // ProjectDetailPage's dashboard StatCards read plotCounts off this
      // query, not plot-stats -- creating a plot changes the total count,
      // same gap as PlotDetailDrawer's delete handler.
      queryClient.invalidateQueries({ queryKey: ['project', projectId] });
      onSuccess(result);
    },
  });

  return (
    <form onSubmit={form.handleSubmit((v) => mutation.mutate(v))} className="space-y-4" noValidate>
      <div className="space-y-2">
        <Label htmlFor="plotNumber">{t('form.fields.plotNumber')}</Label>
        <Input id="plotNumber" placeholder={t('form.placeholders.plotNumber')} {...form.register('plotNumber')} />
        <FormError message={form.formState.errors.plotNumber?.message} />
      </div>

      <PlotStatusSelector
        status={values.status}
        reservedFor={values.reservedFor ?? ''}
        reservedUntil={values.reservedUntil ?? ''}
        onStatusChange={(s) => form.setValue('status', s)}
        onReservedForChange={(v) => form.setValue('reservedFor', v)}
        onReservedUntilChange={(v) => form.setValue('reservedUntil', v)}
        reservedForError={form.formState.errors.reservedFor?.message}
      />

      <div className="space-y-2">
        <Label>{t('form.fields.sizeValue')}</Label>
        <AreaInput
          value={values.sizeValue}
          unit={values.sizeUnit}
          onValueChange={(v) => form.setValue('sizeValue', v ?? (undefined as unknown as number), { shouldValidate: true })}
          onUnitChange={(u) => form.setValue('sizeUnit', u, { shouldValidate: true })}
          stateCode={stateCode}
          error={form.formState.errors.sizeValue?.message ?? form.formState.errors.sizeUnit?.message}
        />
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div className="space-y-2">
          <Label htmlFor="price">{t('form.fields.price')}</Label>
          <Input id="price" type="number" inputMode="decimal" min={0} {...form.register('price', { valueAsNumber: true })} />
          <FormError message={form.formState.errors.price?.message} />
        </div>
        <div className="space-y-2">
          <Label>{t('form.fields.facing')}</Label>
          <Select value={values.facing} onValueChange={(v) => form.setValue('facing', v as PlotFacing)}>
            <SelectTrigger className="w-full">
              <SelectValue placeholder={t('form.fields.facing')} />
            </SelectTrigger>
            <SelectContent>
              {FACINGS.map((f) => (
                <SelectItem key={f} value={f}>
                  {t(`facing.${f}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      {!isEdit && (
        <div className="grid grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label htmlFor="gridRow">{t('form.fields.gridRow')}</Label>
            <Input id="gridRow" type="number" inputMode="numeric" min={0} {...form.register('gridRow', { valueAsNumber: true })} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="gridCol">{t('form.fields.gridCol')}</Label>
            <Input id="gridCol" type="number" inputMode="numeric" min={0} {...form.register('gridCol', { valueAsNumber: true })} />
          </div>
          <p className="col-span-2 text-xs text-muted-foreground">{t('form.unplacedNote')}</p>
        </div>
      )}

      <div className="flex flex-wrap gap-4">
        <label className="flex items-center gap-2 text-sm">
          <Checkbox checked={values.isGarden} onCheckedChange={(c) => form.setValue('isGarden', c === true)} />
          {t('form.flags.isGarden')}
        </label>
        <label className="flex items-center gap-2 text-sm">
          <Checkbox checked={values.isCorner} onCheckedChange={(c) => form.setValue('isCorner', c === true)} />
          {t('form.flags.isCorner')}
        </label>
        <label className="flex items-center gap-2 text-sm">
          <Checkbox checked={values.isHot} onCheckedChange={(c) => form.setValue('isHot', c === true)} />
          {t('form.flags.isHot')}
        </label>
      </div>

      <div className="space-y-2">
        <Label htmlFor="remarks">{t('form.fields.remarks')}</Label>
        <Textarea id="remarks" placeholder={t('form.placeholders.remarks')} {...form.register('remarks')} />
        <FormError message={form.formState.errors.remarks?.message} />
      </div>

      {mutation.isError && (
        <>
          <EntitlementGuard error={mutation.error} />
          <FormError message={resolveErrorMessage(mutation.error)} />
        </>
      )}

      <div className="flex justify-end gap-2 pt-2">
        <Button type="button" variant="outline" onClick={onCancel}>
          {t('common:actions.cancel')}
        </Button>
        <Button type="submit" disabled={mutation.isPending}>
          {mutation.isPending
            ? isEdit
              ? t('form.savingChanges')
              : t('form.submitting')
            : isEdit
              ? t('form.saveChanges')
              : t('form.submit')}
        </Button>
      </div>
    </form>
  );
}
