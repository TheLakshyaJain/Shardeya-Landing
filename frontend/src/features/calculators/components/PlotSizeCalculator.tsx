import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { FormError } from '@/components/forms/FormError';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { calculatePlotSize, getUnits } from '../api/calculatorApi';
import { formatIndianNumber } from '@/lib/formatters';

export function PlotSizeCalculator() {
  const { t, i18n } = useTranslation('calculator');
  const [length, setLength] = useState('');
  const [width, setWidth] = useState('');
  const [unit, setUnit] = useState('SQ_FT');

  const unitsQuery = useQuery({ queryKey: ['calc-units'], queryFn: () => getUnits() });

  const mutation = useMutation({
    mutationFn: () => calculatePlotSize({ length: Number(length), width: Number(width), unit }),
  });

  return (
    <div className="max-w-md space-y-4">
      <div className="grid grid-cols-2 gap-3">
        <div className="space-y-2">
          <Label htmlFor="plotsize-length">{t('plotSize.length')}</Label>
          <Input id="plotsize-length" type="number" min="0" value={length} onChange={(e) => setLength(e.target.value)} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="plotsize-width">{t('plotSize.width')}</Label>
          <Input id="plotsize-width" type="number" min="0" value={width} onChange={(e) => setWidth(e.target.value)} />
        </div>
      </div>
      <div className="space-y-2">
        <Label htmlFor="plotsize-unit">{t('plotSize.unit')}</Label>
        <Select value={unit} onValueChange={setUnit}>
          <SelectTrigger id="plotsize-unit" className="w-full" aria-label={t('plotSize.unit')}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {(unitsQuery.data ?? []).map((u) => (
              <SelectItem key={u.code} value={u.code}>
                {i18n.language === 'hi' ? u.nameHi : u.nameEn}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <Button disabled={!length || !width || mutation.isPending} onClick={() => mutation.mutate()}>
        {t('plotSize.calculate')}
      </Button>

      {mutation.isError && <FormError message={resolveErrorMessage(mutation.error)} />}

      {mutation.isSuccess && (
        <dl className="grid grid-cols-2 gap-x-4 gap-y-2 rounded-lg border border-border p-4 text-sm">
          <dt className="text-muted-foreground">{t('plotSize.results.sqft')}</dt>
          <dd className="font-semibold">{formatIndianNumber(mutation.data.sqft)}</dd>
          <dt className="text-muted-foreground">{t('plotSize.results.sqm')}</dt>
          <dd>{formatIndianNumber(mutation.data.sqm)}</dd>
          <dt className="text-muted-foreground">{t('plotSize.results.sqyd')}</dt>
          <dd>{formatIndianNumber(mutation.data.sqyd)}</dd>
          {mutation.data.bigha != null && (
            <>
              <dt className="text-muted-foreground">
                {t('plotSize.results.bigha')} {mutation.data.bighaStateApplied && `(${mutation.data.bighaStateApplied})`}
              </dt>
              <dd>{formatIndianNumber(mutation.data.bigha)}</dd>
            </>
          )}
          <dt className="text-muted-foreground">{t('plotSize.results.gunta')}</dt>
          <dd>{formatIndianNumber(mutation.data.gunta)}</dd>
          <dt className="text-muted-foreground">{t('plotSize.results.dismil')}</dt>
          <dd>{formatIndianNumber(mutation.data.dismil)}</dd>
        </dl>
      )}
    </div>
  );
}
