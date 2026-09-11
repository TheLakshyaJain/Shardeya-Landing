import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { FormError } from '@/components/forms/FormError';
import { ApiError } from '@/lib/api/client';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { formatIndianCurrency } from '@/lib/formatters';
import { listBrokers, previewCommission } from '@/features/builder/brokers/api/brokerApi';
import { createSale } from '../api/saleApi';
import { buildSaleSchema, type SaleFormValues } from '../schemas';
import { GovIdInput } from './GovIdInput';
import { PaymentPlanBuilder } from './PaymentPlanBuilder';
import type { SaleCreateRequest, SalePaymentType } from '../types';

// EXTERNAL (free-text, not-in-system broker) was removed from this wizard:
// it produced zero commission-ledger automation (no tier tracking, no
// ledger entry, nothing under Brokers at all), which defeated the entire
// point of B-14. A broker must now be registered via the Brokers tab
// before a sale can be attributed to them -- the backend's own
// externalBrokerName/externalBrokerMobile fields are left in place (still
// readable/editable for pre-existing sales created before this change),
// just no longer reachable from this wizard's own UI.
type BrokerMode = 'NONE' | 'IN_SYSTEM';

const STEP_KEYS = ['buyer', 'deal', 'payment', 'broker', 'review'] as const;

interface SaleWizardProps {
  projectId: string;
  plotId: string;
  plotNumber: string;
  onSuccess: () => void;
  onCancel: () => void;
}

export function SaleWizard({ projectId, plotId, plotNumber, onSuccess, onCancel }: SaleWizardProps) {
  const { t } = useTranslation(['sale', 'common', 'errors', 'broker']);
  const queryClient = useQueryClient();
  const [step, setStep] = useState(0);
  // Generated once per wizard session (not per submit attempt) so a retried
  // submit after a network error replays the SAME Idempotency-Key rather
  // than minting a new one each click -- that's the whole point of the
  // header (B-04's API contract marks this endpoint [Idempotency-Key]).
  const [idempotencyKey] = useState(() => crypto.randomUUID());
  const [brokerMode, setBrokerMode] = useState<BrokerMode>('NONE');

  const schema = buildSaleSchema(t);
  const form = useForm<SaleFormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      buyerName: '',
      buyerMobile: '',
      buyerEmail: '',
      buyerGovIdType: undefined,
      buyerGovIdNumber: '',
      buyerWhatsappOptIn: false,
      purchaseDate: new Date().toISOString().slice(0, 10),
      dealValue: undefined,
      brokerPartnerId: undefined,
      brokerCommissionAmount: undefined,
      paymentType: 'INSTALMENT',
      schedule: [{ label: '', amount: 0, dueDate: '' }],
    },
  });
  const values = form.watch();

  // Lump Sum's one locked row must always equal the CURRENT deal value, not
  // whatever it happened to equal at the moment Lump Sum was selected --
  // without this, changing the deal value on step 1 after already picking
  // Lump Sum on step 2 would silently leave the submitted schedule row's
  // amount stale (PaymentPlanBuilder's own "Deal Value" display always
  // shows the live figure, so this mismatch wouldn't even be visible before
  // submit).
  useEffect(() => {
    if (values.paymentType === 'LUMP_SUM' && values.schedule[0]?.amount !== (values.dealValue || 0)) {
      form.setValue('schedule', [{ label: t('schedule.fullPaymentLabel'), amount: values.dealValue || 0, dueDate: values.schedule[0]?.dueDate ?? '' }]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [values.paymentType, values.dealValue]);

  const brokersQuery = useQuery({ queryKey: ['brokers-for-sale-picker'], queryFn: () => listBrokers('ACTIVE') });

  // B-14 §8/§10: live commission preview as soon as a real (in-system)
  // broker + a non-zero deal value are both known -- resolve() is the same
  // algorithm the backend snapshots at sale-creation time, so this preview
  // is never wrong at the moment it's shown; it just isn't re-checked after
  // the user keeps typing without this query re-firing (React Query does
  // that automatically via the queryKey below).
  const previewQuery = useQuery({
    queryKey: ['commission-preview', values.brokerPartnerId, values.dealValue, projectId, plotId],
    // projectId/plotId must be passed through -- without them, resolve()
    // can only ever match a GLOBAL-scope config, silently skipping any
    // PLOT/PROJECT-level override the broker actually has for this exact
    // deal (the preview would show the wrong, too-generic figure, and a
    // careful builder relying on it to sanity-check the deal would never
    // know). Caught by M6's own verification pass: a broker with a 5%
    // PLOT override showed the 3% GLOBAL rate here instead.
    queryFn: () => previewCommission(values.brokerPartnerId!, { dealValue: values.dealValue!, projectId, plotId }),
    enabled: brokerMode === 'IN_SYSTEM' && !!values.brokerPartnerId && !!values.dealValue,
    retry: false,
  });
  // B-14 §10: "if [the broker's default is] also unset, the sale form
  // REQUIRES a manual commission amount rather than silently recording
  // zero" -- surfaced here by the exact NO_COMMISSION_CONFIG code the
  // backend's preview endpoint already throws for this same situation.
  const noConfigForBroker =
    previewQuery.isError &&
    previewQuery.error instanceof ApiError &&
    previewQuery.error.errors[0]?.code === 'NO_COMMISSION_CONFIG';

  const mutation = useMutation({
    mutationFn: (v: SaleFormValues) => {
      const req: SaleCreateRequest = {
        buyerName: v.buyerName,
        buyerMobile: v.buyerMobile,
        buyerEmail: v.buyerEmail || undefined,
        buyerGovIdType: v.buyerGovIdType,
        buyerGovIdNumber: v.buyerGovIdNumber || undefined,
        purchaseDate: v.purchaseDate,
        dealValue: v.dealValue,
        brokerPartnerId: v.brokerPartnerId || undefined,
        brokerCommissionAmount: v.brokerCommissionAmount,
        paymentType: v.paymentType,
        schedule: v.schedule,
        buyerWhatsappOptIn: v.buyerWhatsappOptIn ?? false,
      };
      return createSale(plotId, req, idempotencyKey);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['grid', projectId] });
      queryClient.invalidateQueries({ queryKey: ['plots', projectId] });
      queryClient.invalidateQueries({ queryKey: ['plot-stats', projectId] });
      queryClient.invalidateQueries({ queryKey: ['project', projectId] });
      queryClient.invalidateQueries({ queryKey: ['plot', plotId] });
      onSuccess();
    },
  });

  const isLastStep = step === STEP_KEYS.length - 1;

  async function goNext() {
    const fieldsByStep: (keyof SaleFormValues)[][] = [
      ['buyerName', 'buyerMobile', 'buyerEmail'],
      ['purchaseDate', 'dealValue'],
      ['paymentType', 'schedule'],
      [],
      [],
    ];
    const valid = await form.trigger(fieldsByStep[step]);
    if (valid) setStep((s) => Math.min(s + 1, STEP_KEYS.length - 1));
  }

  function goBack() {
    setStep((s) => Math.max(s - 1, 0));
  }

  return (
    <form onSubmit={form.handleSubmit((v) => mutation.mutate(v))} className="space-y-4" noValidate>
      <h2 className="text-sm font-medium text-muted-foreground">{t('wizard.title')} — {plotNumber}</h2>
      <div className="flex flex-wrap gap-2 text-xs">
        {STEP_KEYS.map((key, i) => (
          <span
            key={key}
            className={`rounded-full px-2 py-1 ${i === step ? 'bg-primary text-primary-foreground' : i < step ? 'bg-muted text-foreground' : 'text-muted-foreground'}`}
          >
            {i + 1}. {t(`wizard.steps.${key}`)}
          </span>
        ))}
      </div>

      {step === 0 && (
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="buyerName">{t('form.fields.buyerName')}</Label>
            <Input id="buyerName" {...form.register('buyerName')} />
            <FormError message={form.formState.errors.buyerName?.message} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="buyerMobile">{t('form.fields.buyerMobile')}</Label>
            <Input id="buyerMobile" {...form.register('buyerMobile')} />
            <FormError message={form.formState.errors.buyerMobile?.message} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="buyerEmail">{t('form.fields.buyerEmail')}</Label>
            <Input id="buyerEmail" type="email" {...form.register('buyerEmail')} />
            <FormError message={form.formState.errors.buyerEmail?.message} />
          </div>
          <GovIdInput
            type={values.buyerGovIdType}
            number={values.buyerGovIdNumber ?? ''}
            onTypeChange={(type) => form.setValue('buyerGovIdType', type)}
            onNumberChange={(number) => form.setValue('buyerGovIdNumber', number)}
          />
          {/* Buyers aren't users -- there's no OTP-confirmed opt-in flow
              reachable for them, so consent is captured here as a builder
              attestation instead (recorded server-side with who ticked it
              and when, distinct from a staff member's own self-service
              opt-in -- see BuyerWhatsAppOptInService). Can be changed later
              from the sale detail page if the buyer agrees or withdraws
              after the sale. */}
          <div className="flex items-center gap-2">
            <Checkbox
              id="buyerWhatsappOptIn"
              checked={values.buyerWhatsappOptIn ?? false}
              onCheckedChange={(c) => form.setValue('buyerWhatsappOptIn', c === true)}
            />
            <Label htmlFor="buyerWhatsappOptIn" className="text-sm font-normal text-muted-foreground">
              {t('whatsappOptIn.consentCheckbox')}
            </Label>
          </div>
        </div>
      )}

      {step === 1 && (
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="purchaseDate">{t('form.fields.purchaseDate')}</Label>
            <Input id="purchaseDate" type="date" {...form.register('purchaseDate')} />
            <FormError message={form.formState.errors.purchaseDate?.message} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="dealValue">{t('form.fields.dealValue')}</Label>
            <Input id="dealValue" type="number" inputMode="decimal" min={0} {...form.register('dealValue', { valueAsNumber: true })} />
            <FormError message={form.formState.errors.dealValue?.message} />
          </div>
        </div>
      )}

      {step === 2 && (
        <div className="space-y-4">
          <div className="space-y-2">
            <Label>{t('form.fields.paymentType')}</Label>
            <Select
              value={values.paymentType}
              onValueChange={(v) => {
                const paymentType = v as SalePaymentType;
                form.setValue('paymentType', paymentType);
                // Collapse to a single full-value row when switching to Lump
                // Sum (PaymentPlanBuilder locks this to one row anyway, but
                // resetting here means any instalment rows already typed in
                // don't linger in form state under a Lump Sum sale); reset to
                // one blank row when switching back to Instalment so a
                // leftover Lump Sum row doesn't get mistaken for a real
                // instalment plan.
                form.setValue(
                  'schedule',
                  paymentType === 'LUMP_SUM'
                    ? [{ label: t('schedule.fullPaymentLabel'), amount: values.dealValue || 0, dueDate: values.purchaseDate }]
                    : [{ label: '', amount: 0, dueDate: '' }],
                  { shouldValidate: true },
                );
              }}
            >
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="LUMP_SUM">{t('form.paymentType.LUMP_SUM')}</SelectItem>
                <SelectItem value="INSTALMENT">{t('form.paymentType.INSTALMENT')}</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <PaymentPlanBuilder
            rows={values.schedule}
            dealValue={values.dealValue || 0}
            paymentType={values.paymentType}
            onChange={(rows) => form.setValue('schedule', rows, { shouldValidate: true })}
          />
          <FormError message={form.formState.errors.schedule?.message} />
        </div>
      )}

      {step === 3 && (
        <div className="space-y-4">
          <div className="flex flex-wrap gap-2">
            {(['NONE', 'IN_SYSTEM'] as const).map((mode) => (
              <Button
                key={mode}
                type="button"
                variant={brokerMode === mode ? 'default' : 'outline'}
                size="sm"
                onClick={() => {
                  setBrokerMode(mode);
                  form.setValue('brokerPartnerId', undefined);
                  form.setValue('brokerCommissionAmount', undefined);
                }}
              >
                {mode === 'NONE' ? t('saleWizard.noBroker', { ns: 'broker' }) : t('saleWizard.selectBroker', { ns: 'broker' })}
              </Button>
            ))}
          </div>
          {brokerMode === 'IN_SYSTEM' && brokersQuery.isSuccess && brokersQuery.data.length === 0 && (
            <p className="text-sm text-muted-foreground">{t('saleWizard.notInSystem', { ns: 'broker' })}</p>
          )}

          {brokerMode === 'IN_SYSTEM' && (
            <div className="space-y-3">
              <div className="space-y-2">
                <Label>{t('saleWizard.selectBroker', { ns: 'broker' })}</Label>
                <Select value={values.brokerPartnerId} onValueChange={(v) => form.setValue('brokerPartnerId', v)}>
                  <SelectTrigger className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {(brokersQuery.data ?? []).map((b) => (
                      <SelectItem key={b.id} value={b.id}>
                        {b.fullName}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              {previewQuery.isSuccess && (
                <p className="text-sm font-medium">
                  {t('saleWizard.commissionPreview', { ns: 'broker', amount: formatIndianCurrency(previewQuery.data.totalCommission) })}
                </p>
              )}

              {noConfigForBroker && (
                <div className="space-y-2">
                  <p className="text-sm text-amber-600">{t('saleWizard.manualCommissionRequired', { ns: 'broker' })}</p>
                  <Label htmlFor="brokerCommissionAmount">{t('saleWizard.manualCommissionAmount', { ns: 'broker' })}</Label>
                  <Input
                    id="brokerCommissionAmount"
                    type="number"
                    inputMode="decimal"
                    min={0}
                    {...form.register('brokerCommissionAmount', { setValueAs: (v) => (v === '' ? undefined : Number(v)) })}
                  />
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {step === 4 && (
        <div className="space-y-3 text-sm">
          <h3 className="font-medium">{t('review.title')}</h3>
          <dl className="grid grid-cols-2 gap-x-4 gap-y-1.5">
            <dt className="text-muted-foreground">{t('form.fields.buyerName')}</dt>
            <dd>{values.buyerName}</dd>
            <dt className="text-muted-foreground">{t('form.fields.buyerMobile')}</dt>
            <dd>{values.buyerMobile}</dd>
            <dt className="text-muted-foreground">{t('form.fields.dealValue')}</dt>
            <dd>{formatIndianCurrency(values.dealValue || 0)}</dd>
            <dt className="text-muted-foreground">{t('form.fields.paymentType')}</dt>
            <dd>{t(`form.paymentType.${values.paymentType}`)}</dd>
            <dt className="text-muted-foreground">{t('schedule.total')}</dt>
            <dd>{formatIndianCurrency(values.schedule.reduce((s, r) => s + (r.amount || 0), 0))}</dd>
          </dl>
          {mutation.isError && <FormError message={resolveErrorMessage(mutation.error)} />}
        </div>
      )}

      <div className="flex justify-between gap-2 pt-2">
        <Button type="button" variant="outline" onClick={step === 0 ? onCancel : goBack}>
          {step === 0 ? t('common:actions.cancel') : t('common:actions.back')}
        </Button>
        {isLastStep ? (
          <Button key="submit" type="submit" disabled={mutation.isPending}>
            {mutation.isPending ? t('wizard.submitting') : t('wizard.submit')}
          </Button>
        ) : (
          <Button key="next" type="button" onClick={goNext}>
            {t('common:actions.next')}
          </Button>
        )}
      </div>
    </form>
  );
}
