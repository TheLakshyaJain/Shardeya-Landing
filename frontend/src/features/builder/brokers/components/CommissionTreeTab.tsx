import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { EmptyState } from '@/components/data/EmptyState';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { FormError } from '@/components/forms/FormError';
import { useCan } from '@/hooks/useCan';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { formatIndianCurrency } from '@/lib/formatters';
import {
  getBookingCommissions,
  getBrokerCommissionPayments,
  getCommissionSummary,
  recordBrokerCommissionPayment,
  reverseBrokerCommissionPayment,
} from '../api/brokerApi';
import { SummaryStat } from './SummaryStat';
import type { BookingCommissionResponse, BrokerCommissionPaymentResponse, CommissionPaymentMode } from '../types';

/**
 * 06-BROKER-NETWORK-ENGINE.md §7/§8/§8a -- the Ledger-tab-equivalent read
 * surface for a DESIGNATION broker: every booking they're a beneficiary of
 * (as seller or upline), the frozen total, how much has been proportionally
 * released so far, and -- since §8a -- how much has actually been PAID
 * OUT and is therefore still Due. "Record Payment" pays against the
 * broker's whole Due balance at once (not one entry at a time, unlike
 * LedgerTab's PERCENTAGE/FIXED equivalent), auto-allocated oldest-first by
 * the backend.
 *
 * Card rows, not a wide <Table> -- a first draft used a Table here and it
 * broke at 360px exactly the way CLAUDE.md already documents twice
 * (Financials pending/overdue, the Ops delivery/error tables): the page
 * itself never overflowed, but the table's own horizontal scroll hid every
 * column past Project/Plot, including Status and every money figure --
 * the entire reason this tab exists. Caught by actually looking at the
 * 360px/Hindi screenshot, not by any automated overflow check (which only
 * measures the page, not a scrollable child). Card rows reflow at any
 * width instead of needing that scroll -- the payment history section
 * below follows the identical pattern from the start, not as an
 * after-the-fact fix.
 */
export function CommissionTreeTab({ brokerId }: { brokerId: string }) {
  const { t } = useTranslation('broker');
  const canPay = useCan('BROKER_COMMISSION_PAY');
  const [payingOpen, setPayingOpen] = useState(false);
  const query = useQuery({ queryKey: ['broker-booking-commissions', brokerId], queryFn: () => getBookingCommissions(brokerId) });
  const rows = query.data ?? [];

  // The dialog's own hard cap -- summed client-side from the same rows
  // already on screen, so it can never disagree with what the backend's
  // own lockForPayoutOldestFirst sum computes at submit time.
  const totalDue = rows.filter((r) => r.status !== 'CANCELLED').reduce((sum, r) => sum + r.dueAmount, 0);

  return (
    <div className="space-y-4">
      <CommissionSummarySection brokerId={brokerId} />
      {canPay && (
        <div className="flex justify-end">
          <Button onClick={() => setPayingOpen(true)} disabled={totalDue <= 0}>
            {t('brokerPayment.recordButton')}
          </Button>
        </div>
      )}
      {rows.length === 0 ? (
        <EmptyState title={t('commissionTree.empty')} />
      ) : (
        <ul className="space-y-2">
          {rows.map((r) => (
            <CommissionTreeCard key={r.id} row={r} />
          ))}
        </ul>
      )}
      <PaymentHistorySection brokerId={brokerId} canPay={canPay} />
      <RecordBrokerPaymentDialog brokerId={brokerId} totalDue={totalDue} open={payingOpen} onOpenChange={setPayingOpen} />
    </div>
  );
}

// §26/§27's per-broker money roll-up: personal vs team earned, the
// three-type breakdown, released/pending -- above the per-booking list,
// which stays exactly as it was (this is additive, not a redesign).
function CommissionSummarySection({ brokerId }: { brokerId: string }) {
  const { t } = useTranslation('broker');
  const query = useQuery({ queryKey: ['broker-commission-summary', brokerId], queryFn: () => getCommissionSummary(brokerId) });
  if (!query.data) return null;
  const s = query.data;

  return (
    <div className="rounded-lg border border-border bg-card p-4">
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <SummaryStat label={t('designation.summary.personalEarned')} value={formatIndianCurrency(s.personalCommissionEarned)} />
        <SummaryStat label={t('designation.summary.teamEarned')} value={formatIndianCurrency(s.teamCommissionEarned)} />
        <SummaryStat label={t('designation.summary.released')} value={formatIndianCurrency(s.commissionReleased)} />
        <SummaryStat label={t('designation.summary.pending')} value={formatIndianCurrency(s.commissionPending)} />
      </div>
      <div className="mt-3 grid grid-cols-3 gap-3 border-t border-border pt-3 text-sm">
        <SummaryStat label={t('designation.summary.sellingBroker')} value={formatIndianCurrency(s.sellingBrokerEarned)} />
        <SummaryStat label={t('designation.summary.uplineDifferential')} value={formatIndianCurrency(s.uplineDifferentialEarned)} />
        <SummaryStat label={t('designation.summary.sameSlabBonus')} value={formatIndianCurrency(s.sameSlabBonusEarned)} />
      </div>
    </div>
  );
}

function CommissionTreeCard({ row }: { row: BookingCommissionResponse }) {
  const { t } = useTranslation('broker');
  const typeLabel =
    row.uplineLevel === 0
      ? t('commissionTree.type.SELLING_BROKER')
      : `${t(`commissionTree.type.${row.commissionType}`)} (${t('commissionTree.level', { level: row.uplineLevel })})`;

  return (
    <li className="rounded-lg border border-border bg-card p-3 shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="text-sm font-medium text-foreground">{typeLabel}</p>
          <p className="text-xs text-muted-foreground">
            {row.projectName ?? '—'} &middot; {row.plotNumber ?? '—'} &middot; {row.buyerName ?? '—'}
          </p>
          <p className="text-xs text-muted-foreground">{row.createdAt.slice(0, 10)}</p>
        </div>
        <Badge variant={row.status === 'FULLY_RELEASED' ? 'default' : row.status === 'CANCELLED' ? 'outline' : 'secondary'}>
          {t(`commissionTree.status.${row.status}`)}
        </Badge>
      </div>
      <div className="mt-3 grid grid-cols-3 gap-2 text-sm">
        <div>
          <p className="text-xs text-muted-foreground">{t('commissionTree.columns.total')}</p>
          <p className="font-semibold">{formatIndianCurrency(row.totalAmount)}</p>
        </div>
        <div>
          <p className="text-xs text-muted-foreground">{t('commissionTree.columns.released')}</p>
          <p className="font-semibold">{formatIndianCurrency(row.releasedAmount)}</p>
        </div>
        <div>
          <p className="text-xs text-muted-foreground">{t('commissionTree.columns.due')}</p>
          <p className="font-semibold">{formatIndianCurrency(row.dueAmount)}</p>
        </div>
      </div>
      {row.needsRecovery && (
        <p className="mt-2 text-xs text-destructive">
          {t('commissionTree.needsRecovery', { amount: formatIndianCurrency(row.recoveryAmount ?? 0) })}
        </p>
      )}
    </li>
  );
}

function PaymentHistorySection({ brokerId, canPay }: { brokerId: string; canPay: boolean }) {
  const { t } = useTranslation('broker');
  const query = useQuery({ queryKey: ['broker-commission-payments', brokerId], queryFn: () => getBrokerCommissionPayments(brokerId) });
  const payments = query.data ?? [];
  const reversedIds = new Set(payments.map((p) => p.reversesPaymentId).filter((id): id is string => id != null));
  const [reversing, setReversing] = useState<BrokerCommissionPaymentResponse | null>(null);

  return (
    <div className="space-y-2">
      <h3 className="text-sm font-semibold text-foreground">{t('brokerPayment.historyTitle')}</h3>
      {payments.length === 0 ? (
        <EmptyState title={t('brokerPayment.historyEmpty')} />
      ) : (
        <ul className="space-y-2">
          {payments.map((p) => {
            const isReversal = p.amount < 0;
            const alreadyReversed = reversedIds.has(p.id);
            return (
              <li key={p.id} className="rounded-lg border border-border bg-card p-3 shadow-sm">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="min-w-0">
                    <p className="text-sm font-semibold text-foreground">{formatIndianCurrency(Math.abs(p.amount))}</p>
                    <p className="text-xs text-muted-foreground">
                      {p.paidOn} &middot; {p.mode}
                      {p.reference ? ` · ${p.reference}` : ''}
                    </p>
                    {p.remarks && <p className="text-xs text-muted-foreground">{p.remarks}</p>}
                  </div>
                  <div className="flex shrink-0 items-center gap-2">
                    {isReversal && <Badge variant="outline">{t('brokerPayment.reversed')}</Badge>}
                    {canPay && !isReversal && !alreadyReversed && (
                      <Button variant="outline" size="sm" onClick={() => setReversing(p)}>
                        {t('payment.reverseSubmit')}
                      </Button>
                    )}
                  </div>
                </div>
              </li>
            );
          })}
        </ul>
      )}
      <ReverseBrokerPaymentDialog brokerId={brokerId} payment={reversing} onOpenChange={(open) => !open && setReversing(null)} />
    </div>
  );
}

function ReverseBrokerPaymentDialog({
  brokerId,
  payment,
  onOpenChange,
}: {
  brokerId: string;
  payment: BrokerCommissionPaymentResponse | null;
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useTranslation(['broker', 'common']);
  const queryClient = useQueryClient();
  const [reason, setReason] = useState('');

  const mutation = useMutation({
    mutationFn: () => reverseBrokerCommissionPayment(payment!.id, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['broker-commission-payments', brokerId] });
      queryClient.invalidateQueries({ queryKey: ['broker-booking-commissions', brokerId] });
      queryClient.invalidateQueries({ queryKey: ['broker-commission-summary', brokerId] });
      queryClient.invalidateQueries({ queryKey: ['broker', brokerId] });
      queryClient.invalidateQueries({ queryKey: ['broker-performance', brokerId] });
      onOpenChange(false);
      setReason('');
    },
  });

  return (
    <Dialog open={!!payment} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('payment.reverseTitle')}</DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
          <div className="space-y-2">
            <Label htmlFor="broker-commission-payment-reverse-reason">{t('payment.reverseReason')}</Label>
            <Input id="broker-commission-payment-reverse-reason" value={reason} onChange={(e) => setReason(e.target.value)} />
          </div>
          {mutation.isError && <FormError message={resolveErrorMessage(mutation.error)} />}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('common:actions.cancel')}
          </Button>
          <Button disabled={reason.trim().length < 5 || mutation.isPending} onClick={() => mutation.mutate()}>
            {t('payment.reverseSubmit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function RecordBrokerPaymentDialog({
  brokerId,
  totalDue,
  open,
  onOpenChange,
}: {
  brokerId: string;
  totalDue: number;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useTranslation(['broker', 'common']);
  const queryClient = useQueryClient();
  const [amount, setAmount] = useState('');
  const [paidOn, setPaidOn] = useState(() => new Date().toISOString().slice(0, 10));
  const [mode, setMode] = useState<CommissionPaymentMode>('UPI');
  const [reference, setReference] = useState('');
  const [remarks, setRemarks] = useState('');

  // §8a's hard cap, mirrored client-side purely as a UX guard -- the
  // backend's own reject is the real enforcement (CLAUDE.md rule #5:
  // never trust the client for money math), this just disables the submit
  // button before a doomed request is ever sent.
  const exceedsDue = amount !== '' && Number(amount) > totalDue;

  const mutation = useMutation({
    mutationFn: () =>
      recordBrokerCommissionPayment(brokerId, {
        amount: Number(amount),
        paidOn,
        mode,
        reference: reference || undefined,
        remarks: remarks || undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['broker-booking-commissions', brokerId] });
      queryClient.invalidateQueries({ queryKey: ['broker-commission-payments', brokerId] });
      queryClient.invalidateQueries({ queryKey: ['broker-commission-summary', brokerId] });
      queryClient.invalidateQueries({ queryKey: ['broker', brokerId] });
      queryClient.invalidateQueries({ queryKey: ['broker-performance', brokerId] });
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
          <DialogTitle>{t('brokerPayment.recordTitle')}</DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
          <p className="text-sm text-muted-foreground">
            {t('brokerPayment.due')}: <span className="font-semibold text-foreground">{formatIndianCurrency(totalDue)}</span>
          </p>
          <div className="space-y-2">
            <Label htmlFor="broker-commission-payment-amount">{t('payment.amount')}</Label>
            <Input id="broker-commission-payment-amount" type="number" value={amount} onChange={(e) => setAmount(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="broker-commission-payment-paidOn">{t('payment.paidOn')}</Label>
            <Input id="broker-commission-payment-paidOn" type="date" value={paidOn} onChange={(e) => setPaidOn(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="broker-commission-payment-mode">{t('payment.mode')}</Label>
            <Select value={mode} onValueChange={(v) => setMode(v as CommissionPaymentMode)}>
              <SelectTrigger id="broker-commission-payment-mode" className="w-full" aria-label={t('payment.mode')}>
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
            <Label htmlFor="broker-commission-payment-reference">{t('payment.reference')}</Label>
            <Input id="broker-commission-payment-reference" value={reference} onChange={(e) => setReference(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="broker-commission-payment-remarks">{t('payment.remarks')}</Label>
            <Input id="broker-commission-payment-remarks" value={remarks} onChange={(e) => setRemarks(e.target.value)} />
          </div>
          {exceedsDue && <FormError message={t('brokerPayment.exceedsDue', { due: formatIndianCurrency(totalDue) })} />}
          {mutation.isError && <FormError message={resolveErrorMessage(mutation.error)} />}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('common:actions.cancel')}
          </Button>
          <Button disabled={!amount || exceedsDue || mutation.isPending} onClick={() => mutation.mutate()}>
            {mutation.isPending ? t('payment.submitting') : t('payment.submit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
