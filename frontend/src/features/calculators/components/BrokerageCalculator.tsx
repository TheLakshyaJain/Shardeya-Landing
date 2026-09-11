import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { FormError } from '@/components/forms/FormError';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { formatIndianCurrency } from '@/lib/formatters';
import { calculateBrokerage } from '../api/calculatorApi';

export function BrokerageCalculator() {
  const { t } = useTranslation('calculator');
  const [dealValue, setDealValue] = useState('');
  const [brokeragePct, setBrokeragePct] = useState('');
  const [ownerSharePct, setOwnerSharePct] = useState('');
  const [buyerSharePct, setBuyerSharePct] = useState('');

  const mutation = useMutation({
    mutationFn: () =>
      calculateBrokerage({
        dealValue: Number(dealValue),
        brokeragePct: Number(brokeragePct),
        ownerSharePct: ownerSharePct !== '' ? Number(ownerSharePct) : undefined,
        buyerSharePct: buyerSharePct !== '' ? Number(buyerSharePct) : undefined,
      }),
  });

  return (
    <div className="max-w-md space-y-4">
      <div className="space-y-2">
        <Label htmlFor="brokerage-dealValue">{t('brokerage.dealValue')}</Label>
        <Input id="brokerage-dealValue" type="number" min="0" value={dealValue} onChange={(e) => setDealValue(e.target.value)} />
      </div>
      <div className="space-y-2">
        <Label htmlFor="brokerage-brokeragePct">{t('brokerage.brokeragePct')}</Label>
        <Input id="brokerage-brokeragePct" type="number" min="0" max="100" step="0.001" value={brokeragePct} onChange={(e) => setBrokeragePct(e.target.value)} />
      </div>
      <div className="grid grid-cols-2 gap-3">
        <div className="space-y-2">
          <Label htmlFor="brokerage-ownerSharePct">{t('brokerage.ownerSharePct')}</Label>
          <Input id="brokerage-ownerSharePct" type="number" min="0" max="100" value={ownerSharePct} onChange={(e) => setOwnerSharePct(e.target.value)} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="brokerage-buyerSharePct">{t('brokerage.buyerSharePct')}</Label>
          <Input id="brokerage-buyerSharePct" type="number" min="0" max="100" value={buyerSharePct} onChange={(e) => setBuyerSharePct(e.target.value)} />
        </div>
      </div>
      <Button disabled={!dealValue || !brokeragePct || mutation.isPending} onClick={() => mutation.mutate()}>
        {t('brokerage.calculate')}
      </Button>

      {mutation.isError && <FormError message={resolveErrorMessage(mutation.error)} />}

      {mutation.isSuccess && (
        <div className="space-y-2 rounded-lg border border-border p-4 text-sm">
          <dl className="grid grid-cols-2 gap-x-4 gap-y-2">
            <dt className="text-muted-foreground">{t('brokerage.results.total')}</dt>
            <dd className="font-semibold">{formatIndianCurrency(mutation.data.total)}</dd>
            {mutation.data.ownerShare != null && (
              <>
                <dt className="text-muted-foreground">{t('brokerage.results.ownerShare')}</dt>
                <dd>{formatIndianCurrency(mutation.data.ownerShare)}</dd>
              </>
            )}
            {mutation.data.buyerShare != null && (
              <>
                <dt className="text-muted-foreground">{t('brokerage.results.buyerShare')}</dt>
                <dd>{formatIndianCurrency(mutation.data.buyerShare)}</dd>
              </>
            )}
            <dt className="text-muted-foreground">{t('brokerage.results.gst')}</dt>
            <dd>{formatIndianCurrency(mutation.data.gst)}</dd>
            <dt className="text-muted-foreground">{t('brokerage.results.netPlusGst')}</dt>
            <dd className="font-semibold">{formatIndianCurrency(mutation.data.netPlusGst)}</dd>
          </dl>
          {mutation.data.sharesSumMismatch && (
            <p className="text-xs text-amber-600">{t('brokerage.mismatchNote')}</p>
          )}
        </div>
      )}
    </div>
  );
}
