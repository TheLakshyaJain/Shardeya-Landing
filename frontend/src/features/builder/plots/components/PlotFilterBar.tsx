import { useTranslation } from 'react-i18next';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Input } from '@/components/ui/input';
import { Checkbox } from '@/components/ui/checkbox';
import { Button } from '@/components/ui/button';
import type { PlotFacing, PlotFilter, PlotStatus } from '../types';

const STATUSES: PlotStatus[] = ['AVAILABLE', 'RESERVED', 'SOLD'];
const FACINGS: PlotFacing[] = ['N', 'S', 'E', 'W', 'NE', 'NW', 'SE', 'SW'];

interface PlotFilterBarProps {
  filter: PlotFilter;
  onChange: (filter: PlotFilter) => void;
  /** Grid tab only supports status/size/hot filters (compact tuple limitation) — hides the rest. */
  compact?: boolean;
}

export function PlotFilterBar({ filter, onChange, compact }: PlotFilterBarProps) {
  const { t } = useTranslation('plot');
  const hasActiveFilter = Object.values(filter).some((v) => v !== undefined && v !== '' && v !== false);

  return (
    <div className="flex flex-wrap items-center gap-2">
      <Select
        value={filter.status ?? '__all__'}
        onValueChange={(v) => onChange({ ...filter, status: v === '__all__' ? undefined : (v as PlotStatus) })}
      >
        <SelectTrigger className="w-36">
          <SelectValue placeholder={t('filter.status')} />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="__all__">{t('filter.allStatuses')}</SelectItem>
          {STATUSES.map((s) => (
            <SelectItem key={s} value={s}>
              {t(`status.${s}`)}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      <Input
        type="number"
        placeholder={`${t('filter.min')} sqft`}
        className="w-28"
        value={filter.sizeMin ?? ''}
        onChange={(e) => onChange({ ...filter, sizeMin: e.target.value === '' ? undefined : Number(e.target.value) })}
      />
      <Input
        type="number"
        placeholder={`${t('filter.max')} sqft`}
        className="w-28"
        value={filter.sizeMax ?? ''}
        onChange={(e) => onChange({ ...filter, sizeMax: e.target.value === '' ? undefined : Number(e.target.value) })}
      />

      {!compact && (
        <>
          <Select
            value={filter.facing ?? '__all__'}
            onValueChange={(v) => onChange({ ...filter, facing: v === '__all__' ? undefined : (v as PlotFacing) })}
          >
            <SelectTrigger className="w-36">
              <SelectValue placeholder={t('filter.facing')} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="__all__">{t('filter.allFacings')}</SelectItem>
              {FACINGS.map((f) => (
                <SelectItem key={f} value={f}>
                  {t(`facing.${f}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Input
            type="number"
            placeholder={`${t('filter.min')} ₹`}
            className="w-28"
            value={filter.priceMin ?? ''}
            onChange={(e) => onChange({ ...filter, priceMin: e.target.value === '' ? undefined : Number(e.target.value) })}
          />
          <Input
            type="number"
            placeholder={`${t('filter.max')} ₹`}
            className="w-28"
            value={filter.priceMax ?? ''}
            onChange={(e) => onChange({ ...filter, priceMax: e.target.value === '' ? undefined : Number(e.target.value) })}
          />
          <label className="flex items-center gap-1.5 text-sm">
            <Checkbox checked={!!filter.isCorner} onCheckedChange={(c) => onChange({ ...filter, isCorner: c === true })} />
            {t('filter.cornerOnly')}
          </label>
          <label className="flex items-center gap-1.5 text-sm">
            <Checkbox checked={!!filter.isGarden} onCheckedChange={(c) => onChange({ ...filter, isGarden: c === true })} />
            {t('filter.gardenOnly')}
          </label>
        </>
      )}

      <label className="flex items-center gap-1.5 text-sm">
        <Checkbox checked={!!filter.isHot} onCheckedChange={(c) => onChange({ ...filter, isHot: c === true })} />
        {t('filter.hotOnly')}
      </label>

      {hasActiveFilter && (
        <Button type="button" variant="ghost" size="sm" onClick={() => onChange({})}>
          {t('filter.clearAll')}
        </Button>
      )}
    </div>
  );
}
