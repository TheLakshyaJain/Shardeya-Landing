import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { FormError } from '@/components/forms/FormError';
import { formatIndianCurrency } from '@/lib/formatters';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { recordPayment } from '../api/paymentApi';
import type { PaymentMode, ScheduleResponse } from '../types';

const MODES: PaymentMode[] = ['CASH', 'CHEQUE', 'BANK_TRANSFER', 'UPI', 'DD'];

interface AddPaymentDialogProps {
  saleId: string;
  schedule: ScheduleResponse[];
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

// Allocation preview is computed client-side (oldest-due-first, mirroring
// PaymentAllocationService's own logic) purely for display before saving --
// the backend performs the authoritative allocation on actual submit; there
// is no server-side "dry run" endpoint, so this is an approximation that
// matches the real algorithm closely enough to be a trustworthy preview.
function previewAllocation(amount: number, schedule: ScheduleResponse[]): { label: string; amount: number }[] {
  let remaining = amount;
  const result: { label: string; amount: number }[] = [];
  const sorted = [...schedule].filter((s) => s.status !== 'WAIVED').sort((a, b) => a.dueDate.localeCompare(b.dueDate));
  for (const row of sorted) {
    if (remaining <= 0) break;
    const outstanding = row.expectedAmount - row.amountAllocated;
    if (outstanding <= 0) continue;
    const toAllocate = Math.min(remaining, outstanding);
    result.push({ label: row.label || `#${row.sequenceNo}`, amount: toAllocate });
    remaining -= toAllocate;
  }
  if (remaining > 0.005) {
    result.push({ label: 'advance', amount: remaining });
  }
  return result;
}

export function AddPaymentDialog({ saleId, schedule, open, onOpenChange }: AddPaymentDialogProps) {
  const { t } = useTranslation(['payment', 'common', 'errors']);
  const queryClient = useQueryClient();
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID());
  const [amount, setAmount] = useState<number | ''>('');
  const [paidOn, setPaidOn] = useState(() => new Date().toISOString().slice(0, 10));
  const [mode, setMode] = useState<PaymentMode>('CASH');
  const [reference, setReference] = useState('');
  const [remarks, setRemarks] = useState('');

  // SaleDetailPanel renders this dialog unconditionally (only `open`
  // toggles), and TanStack Query keeps the previous `schedule` data around
  // during background refetches -- so the `{scheduleQuery.data && <AddPaymentDialog/>}`
  // guard around it never actually unmounts the component across separate
  // payments on the same sale. A plain useState initializer for the
  // Idempotency-Key therefore only ever ran once for the component's whole
  // page lifetime: every payment recorded on a sale AFTER the first replayed
  // the FIRST payment's cached response instead of being created. The dialog
  // still closed normally with no visible error, because the backend
  // correctly treats a repeat key as "already processed" -- confirmed
  // directly against the DB (recording Cash 1,00,000 then Bank Transfer
  // 2,50,000 then Cheque 6,00,000 on the same sale left exactly ONE
  // payment_record row and ONE `payment-record` idempotency_key row).
  // Regenerate on each genuine re-open instead of relying on mount timing --
  // a retried submit within the SAME open dialog still reuses the same key
  // (open doesn't change), which is the whole point of the header.
  useEffect(() => {
    if (open) setIdempotencyKey(crypto.randomUUID());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const preview = useMemo(() => (amount ? previewAllocation(amount, schedule) : []), [amount, schedule]);

  const mutation = useMutation({
    mutationFn: () =>
      recordPayment(
        saleId,
        { amount: amount || 0, paidOn, mode, reference: reference || undefined, remarks: remarks || undefined },
        idempotencyKey,
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['payments', saleId] });
      queryClient.invalidateQueries({ queryKey: ['payment-summary', saleId] });
      queryClient.invalidateQueries({ queryKey: ['schedule', saleId] });
      // SaleDetailPanel's own "Mark Complete" button gates on
      // sale.balanceDue from THIS query (not payment-summary, which is a
      // separate fetch) -- without invalidating it too, a payment that
      // brings the balance to exactly zero updates the visible "Balance
      // Remaining" figure (reads payment-summary) but "Mark Complete"
      // stays hidden until the drawer is closed and reopened, since the
      // sale object itself never refetches. No plotId is threaded down to
      // this dialog, so this invalidates every cached sale-by-plot entry
      // (query-key prefix match) rather than one specific plot -- safe
      // since only one sale detail panel is ever open at a time.
      queryClient.invalidateQueries({ queryKey: ['sale-by-plot'] });
      onOpenChange(false);
      setAmount('');
      setReference('');
      setRemarks('');
    },
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('addPayment.title')}</DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
          <div className="space-y-2">
            <Label htmlFor="payment-amount">{t('addPayment.amount')}</Label>
            <Input
              id="payment-amount"
              type="number"
              inputMode="decimal"
              min={0}
              value={amount}
              onChange={(e) => setAmount(e.target.valueAsNumber || '')}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="payment-paidOn">{t('addPayment.paidOn')}</Label>
            <Input id="payment-paidOn" type="date" value={paidOn} onChange={(e) => setPaidOn(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label>{t('addPayment.mode')}</Label>
            <Select value={mode} onValueChange={(v) => setMode(v as PaymentMode)}>
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {MODES.map((m) => (
                  <SelectItem key={m} value={m}>
                    {t(`mode.${m}`)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          {mode !== 'CASH' && (
            <div className="space-y-2">
              <Label htmlFor="payment-reference">{t('addPayment.reference')}</Label>
              <Input id="payment-reference" value={reference} onChange={(e) => setReference(e.target.value)} />
            </div>
          )}
          <div className="space-y-2">
            <Label htmlFor="payment-remarks">{t('addPayment.remarks')}</Label>
            <Textarea id="payment-remarks" value={remarks} onChange={(e) => setRemarks(e.target.value)} />
          </div>

          <div className="rounded-md border border-border bg-muted/30 p-2 text-sm">
            <p className="mb-1 font-medium">{t('addPayment.allocationPreview')}</p>
            {preview.length === 0 ? (
              <p className="text-muted-foreground">{t('addPayment.allocationPreviewEmpty')}</p>
            ) : (
              <ul className="space-y-0.5">
                {preview.map((p, i) => (
                  <li key={i}>
                    {p.label === 'advance' ? t('addPayment.advance', { amount: formatIndianCurrency(p.amount) }) : `${p.label} → ${formatIndianCurrency(p.amount)}`}
                  </li>
                ))}
              </ul>
            )}
          </div>

          {mutation.isError && <FormError message={resolveErrorMessage(mutation.error)} />}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('common:actions.cancel')}
          </Button>
          <Button disabled={!amount || amount <= 0 || mutation.isPending} onClick={() => mutation.mutate()}>
            {mutation.isPending ? t('addPayment.submitting') : t('addPayment.submit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
