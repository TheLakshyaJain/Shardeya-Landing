import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { FormError } from '@/components/forms/FormError';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { formatIndianCurrency } from '@/lib/formatters';
import { INDIAN_STATES } from '@/lib/indianStates';
import { calculateStampDuty, getStampDutyStates } from '../api/calculatorApi';
import type { StampDutyBuyerGender, StampDutyPropertyType, StampDutyTransactionType } from '../types';

export function StampDutyCalculator() {
  const { t, i18n } = useTranslation('calculator');
  const [stateCode, setStateCode] = useState('');
  const [propertyType, setPropertyType] = useState<StampDutyPropertyType>('RESIDENTIAL');
  const [transactionType, setTransactionType] = useState<StampDutyTransactionType>('SALE');
  const [value, setValue] = useState('');
  const [buyerGender, setBuyerGender] = useState<StampDutyBuyerGender>('MALE');

  const statesQuery = useQuery({ queryKey: ['stamp-duty-states'], queryFn: () => getStampDutyStates() });
  const availableStates = INDIAN_STATES.filter((s) => (statesQuery.data ?? []).includes(s.code));

  const mutation = useMutation({
    mutationFn: () => calculateStampDuty({ stateCode, propertyType, transactionType, value: Number(value), buyerGender }),
  });

  return (
    <div className="max-w-md space-y-4">
      <div className="space-y-2">
        <Label htmlFor="stampduty-state">{t('stampDuty.state')}</Label>
        <Select value={stateCode} onValueChange={setStateCode}>
          <SelectTrigger id="stampduty-state" className="w-full" aria-label={t('stampDuty.state')}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {availableStates.map((s) => (
              <SelectItem key={s.code} value={s.code}>
                {i18n.language === 'hi' ? s.nameHi : s.nameEn}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <div className="space-y-2">
          <Label htmlFor="stampduty-propertyType">{t('stampDuty.propertyType')}</Label>
          <Select value={propertyType} onValueChange={(v) => setPropertyType(v as StampDutyPropertyType)}>
            <SelectTrigger id="stampduty-propertyType" className="w-full" aria-label={t('stampDuty.propertyType')}>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {(['RESIDENTIAL', 'COMMERCIAL', 'AGRICULTURAL'] as const).map((v) => (
                <SelectItem key={v} value={v}>
                  {t(`stampDuty.propertyTypes.${v}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-2">
          <Label htmlFor="stampduty-transactionType">{t('stampDuty.transactionType')}</Label>
          <Select value={transactionType} onValueChange={(v) => setTransactionType(v as StampDutyTransactionType)}>
            <SelectTrigger id="stampduty-transactionType" className="w-full" aria-label={t('stampDuty.transactionType')}>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {(['SALE', 'GIFT', 'MORTGAGE'] as const).map((v) => (
                <SelectItem key={v} value={v}>
                  {t(`stampDuty.transactionTypes.${v}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>
      <div className="space-y-2">
        <Label htmlFor="stampduty-value">{t('stampDuty.value')}</Label>
        <Input id="stampduty-value" type="number" min="0" value={value} onChange={(e) => setValue(e.target.value)} />
      </div>
      <div className="space-y-2">
        <Label htmlFor="stampduty-buyerGender">{t('stampDuty.buyerGender')}</Label>
        <Select value={buyerGender} onValueChange={(v) => setBuyerGender(v as StampDutyBuyerGender)}>
          <SelectTrigger id="stampduty-buyerGender" className="w-full" aria-label={t('stampDuty.buyerGender')}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {(['MALE', 'FEMALE', 'JOINT'] as const).map((v) => (
              <SelectItem key={v} value={v}>
                {t(`stampDuty.genders.${v}`)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <Button disabled={!stateCode || !value || mutation.isPending} onClick={() => mutation.mutate()}>
        {t('stampDuty.calculate')}
      </Button>

      {mutation.isError && <FormError message={resolveErrorMessage(mutation.error)} />}

      {mutation.isSuccess && (
        <div className="space-y-2 rounded-lg border border-border p-4 text-sm">
          <dl className="grid grid-cols-2 gap-x-4 gap-y-2">
            <dt className="text-muted-foreground">{t('stampDuty.results.stampDuty')}</dt>
            <dd className="font-semibold">{formatIndianCurrency(mutation.data.stampDuty)}</dd>
            <dt className="text-muted-foreground">{t('stampDuty.results.registrationCharges')}</dt>
            <dd>{formatIndianCurrency(mutation.data.registrationCharges)}</dd>
            <dt className="text-muted-foreground">{t('stampDuty.results.totalGovernmentCharges')}</dt>
            <dd className="font-semibold">{formatIndianCurrency(mutation.data.totalGovernmentCharges)}</dd>
          </dl>
          <p className="text-xs text-muted-foreground">
            {t('stampDuty.disclaimer', { date: mutation.data.effectiveFrom })}
          </p>
        </div>
      )}
    </div>
  );
}
