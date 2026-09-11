import { useTranslation } from 'react-i18next';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Checkbox } from '@/components/ui/checkbox';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { FormError } from '@/components/forms/FormError';
import { AreaInput } from '@/components/forms/AreaInput';
import type { PlotFacing, QuickCreateSharedPropertiesRequest } from '../types';

const FACINGS: PlotFacing[] = ['N', 'S', 'E', 'W', 'NE', 'NW', 'SE', 'SW'];

interface SharedPropertiesPanelProps {
  value: Partial<QuickCreateSharedPropertiesRequest>;
  onChange: (value: Partial<QuickCreateSharedPropertiesRequest>) => void;
  stateCode?: string;
  errors?: Partial<Record<keyof QuickCreateSharedPropertiesRequest, string>>;
}

// B-06 §7: applied to every generated plot as its OWN starting values, not a
// link back to a template -- editing one plot afterward (B-03) never
// affects the others.
export function SharedPropertiesPanel({ value, onChange, stateCode, errors }: SharedPropertiesPanelProps) {
  const { t } = useTranslation('plot');

  return (
    <div className="space-y-4 rounded-md border border-border p-3">
      <h3 className="text-sm font-medium">{t('quickCreate.sharedProperties.title')}</h3>
      <div className="space-y-2">
        <Label>{t('form.fields.sizeValue')}</Label>
        <AreaInput
          value={value.sizeValue}
          unit={value.sizeUnit}
          onValueChange={(v) => onChange({ ...value, sizeValue: v })}
          onUnitChange={(u) => onChange({ ...value, sizeUnit: u })}
          stateCode={stateCode}
          error={errors?.sizeValue ?? errors?.sizeUnit}
        />
      </div>
      <div className="grid grid-cols-2 gap-4">
        <div className="space-y-2">
          <Label htmlFor="quick-create-price">{t('form.fields.price')}</Label>
          <Input
            id="quick-create-price"
            type="number"
            inputMode="decimal"
            min={0}
            value={value.price ?? ''}
            onChange={(e) => onChange({ ...value, price: e.target.valueAsNumber || 0 })}
          />
          <FormError message={errors?.price} />
        </div>
        <div className="space-y-2">
          <Label>{t('form.fields.facing')}</Label>
          <Select value={value.facing} onValueChange={(v) => onChange({ ...value, facing: v as PlotFacing })}>
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
      <div className="flex flex-wrap gap-4">
        <label className="flex items-center gap-2 text-sm">
          <Checkbox checked={value.isGarden ?? false} onCheckedChange={(c) => onChange({ ...value, isGarden: c === true })} />
          {t('form.flags.isGarden')}
        </label>
        <label className="flex items-center gap-2 text-sm">
          <Checkbox checked={value.isCorner ?? false} onCheckedChange={(c) => onChange({ ...value, isCorner: c === true })} />
          {t('form.flags.isCorner')}
        </label>
        <label className="flex items-center gap-2 text-sm">
          <Checkbox checked={value.isHot ?? false} onCheckedChange={(c) => onChange({ ...value, isHot: c === true })} />
          {t('form.flags.isHot')}
        </label>
      </div>
      <div className="space-y-2">
        <Label htmlFor="quick-create-remarks">{t('form.fields.remarks')}</Label>
        <Input
          id="quick-create-remarks"
          value={value.remarks ?? ''}
          onChange={(e) => onChange({ ...value, remarks: e.target.value })}
        />
        <FormError message={errors?.remarks} />
      </div>
    </div>
  );
}
