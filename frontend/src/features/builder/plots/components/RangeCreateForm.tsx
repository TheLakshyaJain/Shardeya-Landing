import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Plus, Trash2 } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { FormError } from '@/components/forms/FormError';
import { EntitlementGuard } from '@/components/feedback/EntitlementGuard';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { useDebounce } from '@/hooks/useDebounce';
import { quickCreateCommit, quickCreatePreview } from '../api/plotApi';
import { SharedPropertiesPanel } from './SharedPropertiesPanel';
import { RangePreviewList } from './RangePreviewList';
import type { QuickCreateRequest, QuickCreateSharedPropertiesRequest } from '../types';

interface RangeCreateFormProps {
  projectId: string;
  stateCode?: string;
  onSuccess: (result: { created: number; unplaced: number }) => void;
  onCancel: () => void;
}

// start/end are optional here (blank while the user is still typing) --
// distinct from QuickCreateRangeRequest, which requires both, and is only
// ever constructed once rangesValid confirms every row has real numbers.
interface RangeRowValue {
  prefix: string;
  separator: string;
  start?: number;
  end?: number;
  padWidth: number;
}

const EMPTY_RANGE: RangeRowValue = { prefix: '', separator: '-', start: undefined, end: undefined, padWidth: 0 };

// B-06 Path A. The preview endpoint is the single source of truth for what
// will actually be created (range parsing, collision detection, quota) --
// this component never re-implements that logic client-side, it just calls
// preview() reactively (debounced) as the user types, exactly the same
// request the Create button eventually commits.
export function RangeCreateForm({ projectId, stateCode, onSuccess, onCancel }: RangeCreateFormProps) {
  const { t } = useTranslation(['plot', 'common', 'errors']);
  const queryClient = useQueryClient();
  const [ranges, setRanges] = useState<RangeRowValue[]>([{ ...EMPTY_RANGE }]);
  const [shared, setShared] = useState<Partial<QuickCreateSharedPropertiesRequest>>({});
  const [autoPlace, setAutoPlace] = useState(true);

  const rangesValid = ranges.every((r) => r.start != null && r.end != null && r.end >= r.start);
  const sharedValid = !!(shared.sizeValue && shared.sizeUnit && shared.price !== undefined && shared.price >= 0);
  const requestPayload: QuickCreateRequest | null = rangesValid && sharedValid
    ? {
        ranges: ranges.map((r) => ({
          prefix: r.prefix || undefined,
          separator: r.separator || undefined,
          start: r.start as number,
          end: r.end as number,
          padWidth: r.padWidth,
        })),
        sharedProperties: shared as QuickCreateSharedPropertiesRequest,
        autoPlace,
      }
    : null;
  const debouncedPayload = useDebounce(requestPayload, 400);

  const previewQuery = useQuery({
    queryKey: ['quick-create-preview', projectId, debouncedPayload],
    queryFn: () => quickCreatePreview(projectId, debouncedPayload!),
    enabled: debouncedPayload !== null,
  });

  const commitMutation = useMutation({
    mutationFn: () => quickCreateCommit(projectId, requestPayload!),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ['grid', projectId] });
      queryClient.invalidateQueries({ queryKey: ['plots', projectId] });
      queryClient.invalidateQueries({ queryKey: ['plot-stats', projectId] });
      queryClient.invalidateQueries({ queryKey: ['project', projectId] });
      onSuccess(result);
    },
  });

  function updateRange(index: number, patch: Partial<RangeRowValue>) {
    setRanges((rs) => rs.map((r, i) => (i === index ? { ...r, ...patch } : r)));
  }

  function addRange() {
    setRanges((rs) => [...rs, { ...EMPTY_RANGE }]);
  }

  function removeRange(index: number) {
    setRanges((rs) => rs.filter((_, i) => i !== index));
  }

  const preview = previewQuery.data;
  const hasCollisions = useMemo(() => preview?.plotNumbers.some((p) => p.collidesWithExisting || p.collidesWithinRequest) ?? false, [preview]);
  const canSubmit = !!preview && !hasCollisions && preview.withinQuota && preview.withinDeclaredCount && !commitMutation.isPending;

  return (
    <div className="space-y-4">
      <div className="space-y-3">
        {ranges.map((range, index) => (
          <div key={index} className="grid grid-cols-[1fr_1fr_1fr_1fr_1fr_auto] items-end gap-2">
            <div className="space-y-1">
              <Label className="text-xs">{t('quickCreate.range.prefix')}</Label>
              <Input value={range.prefix ?? ''} onChange={(e) => updateRange(index, { prefix: e.target.value })} placeholder="A" />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">{t('quickCreate.range.separator')}</Label>
              <Input value={range.separator ?? ''} onChange={(e) => updateRange(index, { separator: e.target.value })} placeholder="-" />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">{t('quickCreate.range.start')}</Label>
              <Input
                type="number"
                inputMode="numeric"
                min={1}
                value={range.start ?? ''}
                onChange={(e) => updateRange(index, { start: e.target.valueAsNumber })}
              />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">{t('quickCreate.range.end')}</Label>
              <Input
                type="number"
                inputMode="numeric"
                min={1}
                value={range.end ?? ''}
                onChange={(e) => updateRange(index, { end: e.target.valueAsNumber })}
              />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">{t('quickCreate.range.padWidth')}</Label>
              <Input
                type="number"
                inputMode="numeric"
                min={0}
                max={6}
                value={range.padWidth ?? 0}
                onChange={(e) => updateRange(index, { padWidth: e.target.valueAsNumber || 0 })}
              />
            </div>
            <Button type="button" size="icon" variant="outline" disabled={ranges.length === 1} onClick={() => removeRange(index)}>
              <Trash2 className="size-4" />
            </Button>
          </div>
        ))}
        <Button type="button" variant="outline" size="sm" onClick={addRange}>
          <Plus className="size-4" />
          {t('quickCreate.addAnotherRange')}
        </Button>
      </div>

      <SharedPropertiesPanel value={shared} onChange={setShared} stateCode={stateCode} />

      <label className="flex items-center gap-2 text-sm">
        <Checkbox checked={autoPlace} onCheckedChange={(c) => setAutoPlace(c === true)} />
        {t('quickCreate.autoPlace')}
      </label>

      {debouncedPayload && previewQuery.isLoading && (
        <p className="text-sm text-muted-foreground">{t('quickCreate.preview.loading')}</p>
      )}
      {preview && <RangePreviewList preview={preview} />}

      {commitMutation.isError && (
        <>
          <EntitlementGuard error={commitMutation.error} />
          <FormError message={resolveErrorMessage(commitMutation.error)} />
        </>
      )}

      <div className="flex justify-end gap-2 pt-2">
        <Button type="button" variant="outline" onClick={onCancel}>
          {t('common:actions.cancel')}
        </Button>
        <Button type="button" disabled={!canSubmit} onClick={() => commitMutation.mutate()}>
          {commitMutation.isPending ? t('quickCreate.creating') : t('quickCreate.create')}
        </Button>
      </div>
    </div>
  );
}
