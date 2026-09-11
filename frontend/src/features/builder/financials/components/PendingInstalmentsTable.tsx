import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { formatIndianCurrency } from '@/lib/formatters';
import { useCan } from '@/hooks/useCan';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { FormError } from '@/components/forms/FormError';
import { recordScheduleQuickPayment, sendScheduleReminder } from '../api/financialApi';
import type { PendingInstalmentRow } from '../types';

interface PendingInstalmentsTableProps {
  rows: PendingInstalmentRow[];
}

function severityClass(daysOverdue: number): string {
  if (daysOverdue <= 0) return '';
  if (daysOverdue <= 7) return 'text-amber-600';
  if (daysOverdue <= 30) return 'text-orange-600';
  return 'text-destructive';
}

// B-08 §14.3 columns: Buyer · Project · Plot · Amount Due · Due Date ·
// Days Overdue (red if positive) · Status · Actions.
export function PendingInstalmentsTable({ rows }: PendingInstalmentsTableProps) {
  const { t } = useTranslation(['financial', 'payment', 'common']);
  const queryClient = useQueryClient();
  const canRecordPayment = useCan('FINANCIAL_RECORD_PAYMENT');
  const [payingId, setPayingId] = useState<string | null>(null);
  const [amount, setAmount] = useState('');
  const [paidOn, setPaidOn] = useState(() => new Date().toISOString().slice(0, 10));
  const [mode, setMode] = useState('CASH');
  const [reference, setReference] = useState('');

  const payMutation = useMutation({
    // PaymentService.record() requires a non-blank reference for every mode
    // except CASH -- this dialog had no field for it, so anything but CASH
    // always 400'd with REFERENCE_REQUIRED. Mirrors AddPaymentDialog's own
    // conditional reference field, same fix as CollectionTable's identical dialog.
    mutationFn: (scheduleId: string) =>
      recordScheduleQuickPayment(scheduleId, { amount: Number(amount), paidOn, mode: mode as never, reference: reference || undefined }),
    onSuccess: () => {
      // FinancialsPage renders THIS SAME component twice -- once fed by
      // pendingQuery ('financial-pending') for the Pending section, once
      // fed by overdueQuery ('financial-overdue') for the Overdue section
      // -- but this only ever invalidated the former. Recording a payment
      // from the Overdue section's own table correctly saved (confirmed:
      // the row was gone after navigating away and back, forcing a fresh
      // mount/refetch) but never disappeared live, since 'financial-overdue'
      // was never told to refetch. This component has no way to know which
      // of the two sections it's being rendered for, so invalidate both.
      queryClient.invalidateQueries({ queryKey: ['financial-pending'] });
      queryClient.invalidateQueries({ queryKey: ['financial-overdue'] });
      queryClient.invalidateQueries({ queryKey: ['financial-summary'] });
      setPayingId(null);
      setAmount('');
      setReference('');
    },
  });

  const remindMutation = useMutation({
    mutationFn: (scheduleId: string) => sendScheduleReminder(scheduleId),
  });

  const paying = rows.find((r) => r.scheduleId === payingId);

  return (
    <>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t('pending.columns.buyer')}</TableHead>
            <TableHead>{t('pending.columns.project')}</TableHead>
            <TableHead>{t('pending.columns.plot')}</TableHead>
            <TableHead>{t('pending.columns.amountDue')}</TableHead>
            <TableHead>{t('pending.columns.dueDate')}</TableHead>
            <TableHead>{t('pending.columns.daysOverdue')}</TableHead>
            <TableHead>{t('pending.columns.status')}</TableHead>
            {canRecordPayment && <TableHead>{t('pending.columns.actions')}</TableHead>}
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((r) => (
            <TableRow key={r.scheduleId}>
              <TableCell>
                {r.buyerName}
                <div className="text-xs text-muted-foreground">{r.buyerMobile}</div>
              </TableCell>
              <TableCell>{r.projectName}</TableCell>
              <TableCell>{r.plotNumber}</TableCell>
              <TableCell>{formatIndianCurrency(r.amountDue)}</TableCell>
              <TableCell>{r.dueDate}</TableCell>
              <TableCell className={severityClass(r.daysOverdue)}>{r.daysOverdue > 0 ? r.daysOverdue : '—'}</TableCell>
              <TableCell>{t(`schedule.status.${r.status}`, { ns: 'payment' })}</TableCell>
              {canRecordPayment && (
                <TableCell className="space-x-1">
                  <Button size="sm" onClick={() => { setPayingId(r.scheduleId); setAmount(String(r.amountDue)); setMode('CASH'); setReference(''); }}>
                    {t('pending.recordPayment')}
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={!r.reminderEnabled || remindMutation.isPending}
                    onClick={() => remindMutation.mutate(r.scheduleId)}
                  >
                    {r.reminderEnabled ? t('pending.sendReminder') : t('pending.reminderDisabled')}
                  </Button>
                </TableCell>
              )}
            </TableRow>
          ))}
        </TableBody>
      </Table>

      <Dialog open={!!payingId} onOpenChange={(open) => !open && setPayingId(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('pending.recordPayment')}{paying ? ` — ${paying.buyerName}` : ''}</DialogTitle>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-2">
              <Label htmlFor="quick-amount">{t('addPayment.amount', { ns: 'payment' })}</Label>
              <Input id="quick-amount" type="number" value={amount} onChange={(e) => setAmount(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="quick-paidOn">{t('addPayment.paidOn', { ns: 'payment' })}</Label>
              <Input id="quick-paidOn" type="date" value={paidOn} onChange={(e) => setPaidOn(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>{t('addPayment.mode', { ns: 'payment' })}</Label>
              <Select value={mode} onValueChange={setMode}>
                <SelectTrigger className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {['CASH', 'CHEQUE', 'BANK_TRANSFER', 'UPI', 'DD'].map((m) => (
                    <SelectItem key={m} value={m}>
                      {t(`mode.${m}`, { ns: 'payment' })}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            {mode !== 'CASH' && (
              <div className="space-y-2">
                <Label htmlFor="quick-reference">{t('addPayment.reference', { ns: 'payment' })}</Label>
                <Input id="quick-reference" value={reference} onChange={(e) => setReference(e.target.value)} />
              </div>
            )}
            {payMutation.isError && <FormError message={resolveErrorMessage(payMutation.error)} />}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setPayingId(null)}>
              {t('common:actions.cancel')}
            </Button>
            <Button
              disabled={!amount || Number(amount) <= 0 || (mode !== 'CASH' && !reference.trim()) || payMutation.isPending}
              onClick={() => payingId && payMutation.mutate(payingId)}
            >
              {t('addPayment.submit', { ns: 'payment' })}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
