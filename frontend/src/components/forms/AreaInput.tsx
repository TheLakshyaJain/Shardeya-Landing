import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { getUnits } from '@/features/calculators/api/calculatorApi';
import { formatIndianNumber } from '@/lib/formatters';

interface AreaInputProps {
  value: number | undefined;
  unit: string | undefined;
  onValueChange: (value: number | undefined) => void;
  onUnitChange: (unit: string) => void;
  stateCode?: string;
  error?: string;
  disabled?: boolean;
}

// M-08 §4: plot-size maths runs client-side for instant feedback (the /calc
// endpoints exist for server-authoritative consistency, not to round-trip on
// every keystroke) — this fetches the unit list once (cached) and does the
// sqft conversion locally as the user types.
export function AreaInput({ value, unit, onValueChange, onUnitChange, stateCode, error, disabled }: AreaInputProps) {
  const { t, i18n } = useTranslation('common');
  const { data: units } = useQuery({
    queryKey: ['calc-units', stateCode ?? null],
    queryFn: () => getUnits(stateCode),
    staleTime: 5 * 60_000,
  });

  const selectedUnit = units?.find((u) => u.code === unit);
  const sqft = value !== undefined && selectedUnit ? value * selectedUnit.toSqftFactor : undefined;

  return (
    <div className="space-y-1">
      <div className="flex gap-2">
        <Input
          type="number"
          inputMode="decimal"
          min={0}
          step="any"
          value={value ?? ''}
          onChange={(e) => onValueChange(e.target.value === '' ? undefined : Number(e.target.value))}
          disabled={disabled}
          aria-invalid={!!error}
          className="flex-1"
        />
        <Select value={unit} onValueChange={onUnitChange} disabled={disabled}>
          <SelectTrigger className="w-40">
            <SelectValue placeholder={t('unit.selectPlaceholder')} />
          </SelectTrigger>
          <SelectContent>
            {units?.map((u) => (
              <SelectItem key={u.code} value={u.code}>
                {i18n.language === 'hi' ? u.nameHi : u.nameEn}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      {sqft !== undefined && (
        <p className="text-xs text-muted-foreground">≈ {formatIndianNumber(sqft)} sqft</p>
      )}
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}
