import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { EmptyState } from '@/components/data/EmptyState';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Checkbox } from '@/components/ui/checkbox';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { FormError } from '@/components/forms/FormError';
import { useCan } from '@/hooks/useCan';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { formatIndianCurrency } from '@/lib/formatters';
import { getBrokerLedger, recordCommissionPayment } from '../api/brokerApi';
import type { CommissionLedgerEntryResponse, CommissionPaymentMode } from '../types';

export function LedgerTab({ brokerId }: { brokerId: string }) {
  const { t } = useTranslation('broker');
  const canPay = useCan('BROKER_COMMISSION_PAY');
  const [payingEntry, setPayingEntry] = useState<CommissionLedgerEntryResponse | null>(null);

  const ledgerQuery = useQuery({ queryKey: ['broker-ledger', brokerId], queryFn: () => getBrokerLedger(brokerId) });
  const entries = ledgerQuery.data ?? [];

  if (entries.length === 0) {
    return <EmptyState title={t('ledger.empty')} />;
  }

  return (
    <div className="space-y-4">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t('ledger.columns.dealDate')}</TableHead>
            <TableHead>{t('ledger.columns.project')}</TableHead>
            <TableHead>{t('ledger.columns.plot')}</TableHead>
            <TableHead>{t('ledger.columns.dealValue')}</TableHead>
            <TableHead>{t('ledger.columns.commission')}</TableHead>
            <TableHead>{t('ledger.columns.status')}</TableHead>
            <TableHead>{t('ledger.columns.balanceDue')}</TableHead>
            {canPay && <TableHead>{t('ledger.columns.actions')}</TableHead>}
          </TableRow>
        </TableHeader>
        <TableBody>
          {entries.map((e) => (
            <TableRow key={e.id}>
              <TableCell>{e.dealDate}</TableCell>
              <TableCell>{e.projectName ?? '—'}</TableCell>
              <TableCell>{e.plotNumber ?? '—'}</TableCell>
              <TableCell>{formatIndianCurrency(e.dealValue)}</TableCell>
              <TableCell>{formatIndianCurrency(e.totalCommission)}</TableCell>
              <TableCell>
                <Badge variant={e.status === 'PAID' ? 'default' : e.status === 'CANCELLED' ? 'outline' : 'secondary'}>
                  {t(`ledger.status.${e.status}`)}
                </Badge>
                {e.needsRecovery && (
                  <div className="mt-1 text-xs text-destructive">
                    {t('ledger.needsRecovery', { amount: formatIndianCurrency(e.recoveryAmount ?? 0), brokerName: e.brokerName ?? '' })}
                  </div>
                )}
              </TableCell>
              <TableCell>{formatIndianCurrency(e.balanceDue)}</TableCell>
              {canPay && (
                <TableCell>
                  {e.status !== 'PAID' && e.status !== 'CANCELLED' && (
                    <Button variant="outline" size="sm" onClick={() => setPayingEntry(e)}>
                      {t('ledger.recordPayment')}
                    </Button>
                  )}
                </TableCell>
              )}
            </TableRow>
          ))}
        </TableBody>
      </Table>

      <RecordPaymentDialog entry={payingEntry} onOpenChange={(open) => !open && setPayingEntry(null)} />
    </div>
  );
}

function RecordPaymentDialog({
  entry,
  onOpenChange,
}: {
  entry: CommissionLedgerEntryResponse | null;
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useTranslation(['broker', 'common']);
  const queryClient = useQueryClient();
  const [amount, setAmount] = useState('');
  const [paidOn, setPaidOn] = useState(() => new Date().toISOString().slice(0, 10));
  const [mode, setMode] = useState<CommissionPaymentMode>('UPI');
  const [reference, setReference] = useState('');
  const [remarks, setRemarks] = useState('');
  const [confirmOverpayment, setConfirmOverpayment] = useState(false);

  // The ledger table (and this dialog's own "Balance Due" figure, read from
  // the same `entry` prop) always displays money rounded to whole rupees
  // via formatIndianCurrency -- but balance_due is a real BigDecimal that
  // can land on paise (e.g. a 6% commission on a non-round deal value). A
  // real account hit exactly this: balance_due = 0.62, displayed as "₹1",
  // and typing the "1" the user actually saw triggered "exceeds balance"
  // against the raw 0.62 -- mathematically correct, but confusing since
  // nothing else in this dialog ever shows a non-whole-rupee figure to
  // compare against. Gate the warning on the SAME rounded figure the user
  // is looking at, not the raw decimal.
  const roundedBalanceDue = entry != null ? Math.round(entry.balanceDue) : 0;
  const exceedsBalance = entry != null && amount !== '' && Number(amount) > roundedBalanceDue;

  const mutation = useMutation({
    mutationFn: () =>
      recordCommissionPayment(entry!.id, {
        amount: Number(amount),
        paidOn,
        mode,
        reference: reference || undefined,
        remarks: remarks || undefined,
        // A genuine overpayment (beyond the rounded, user-visible balance)
        // still requires the explicit checkbox. An amount that only exceeds
        // the RAW balance by a sub-rupee rounding artifact (<=1 rupee,
        // exactly the "user paid off what they saw displayed" case) is
        // auto-confirmed here so the backend's own exact BigDecimal check
        // doesn't reject a payment the UI never warned about in the first
        // place.
        confirmOverpayment: confirmOverpayment || (entry != null && Number(amount) <= roundedBalanceDue),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['broker-ledger'] });
      onOpenChange(false);
      setAmount('');
      setReference('');
      setRemarks('');
      setConfirmOverpayment(false);
    },
  });

  return (
    <Dialog open={!!entry} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('payment.recordTitle')}</DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
          <div className="space-y-2">
            <Label htmlFor="commission-payment-amount">{t('payment.amount')}</Label>
            <Input id="commission-payment-amount" type="number" value={amount} onChange={(e) => setAmount(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="commission-payment-paidOn">{t('payment.paidOn')}</Label>
            <Input id="commission-payment-paidOn" type="date" value={paidOn} onChange={(e) => setPaidOn(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="commission-payment-mode">{t('payment.mode')}</Label>
            <Select value={mode} onValueChange={(v) => setMode(v as CommissionPaymentMode)}>
              <SelectTrigger id="commission-payment-mode" className="w-full" aria-label={t('payment.mode')}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {(['CASH', 'CHEQUE', 'BANK_TRANSFER', 'UPI', 'DD'] as const).map((m) => (
                  <SelectItem key={m} value={m}>
                    {m}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label htmlFor="commission-payment-reference">{t('payment.reference')}</Label>
            <Input id="commission-payment-reference" value={reference} onChange={(e) => setReference(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="commission-payment-remarks">{t('payment.remarks')}</Label>
            <Input id="commission-payment-remarks" value={remarks} onChange={(e) => setRemarks(e.target.value)} />
          </div>
          {exceedsBalance && (
            <label className="flex items-center gap-2 text-sm text-amber-600">
              <Checkbox checked={confirmOverpayment} onCheckedChange={(c) => setConfirmOverpayment(Boolean(c))} />
              {t('payment.confirmOverpayment')}
            </label>
          )}
          {mutation.isError && <FormError message={resolveErrorMessage(mutation.error)} />}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('common:actions.cancel')}
          </Button>
          <Button
            disabled={!amount || (exceedsBalance && !confirmOverpayment) || mutation.isPending}
            onClick={() => mutation.mutate()}
          >
            {mutation.isPending ? t('payment.submitting') : t('payment.submit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
