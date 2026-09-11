import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { MessageCircle } from 'lucide-react';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { FormError } from '@/components/forms/FormError';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { formatIndianCurrency } from '@/lib/formatters';
import { OverdueSeverityChip } from './OverdueSeverityChip';
import { recordCollectionPayment, remindCollection } from '../api/trackerApi';
import type { CollectionRow } from '../types';

interface CollectionTableProps {
  rows: CollectionRow[];
  canEdit: boolean;
}

// B-13 §19.2 columns: Due Date · Project · Plot Number · Buyer Name +
// contact · Amount Due · Days Overdue (red) · Total Balance · Actions:
// Record Payment / Send WhatsApp Reminder / View Plot.
export function CollectionTable({ rows, canEdit }: CollectionTableProps) {
  const { t } = useTranslation(['tracker', 'payment', 'common', 'errors']);
  const queryClient = useQueryClient();
  const [payingId, setPayingId] = useState<string | null>(null);
  const [amount, setAmount] = useState('');
  const [paidOn, setPaidOn] = useState(() => new Date().toISOString().slice(0, 10));
  const [mode, setMode] = useState('CASH');
  const [reference, setReference] = useState('');

  const payMutation = useMutation({
    // PaymentService.record() requires a non-blank reference for every mode
    // except CASH (cheque no. / UTR / UPI ref etc.) -- this dialog had no
    // field for it at all, so recording anything but a CASH payment always
    // 400'd with REFERENCE_REQUIRED. Mirrors AddPaymentDialog's own
    // conditional reference field exactly.
    mutationFn: (scheduleId: string) =>
      recordCollectionPayment(scheduleId, { amount: Number(amount), paidOn, mode: mode as never, reference: reference || undefined }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tracker-collections'] });
      queryClient.invalidateQueries({ queryKey: ['tracker-counts'] });
      setPayingId(null);
      setAmount('');
      setReference('');
    },
  });
  const remindMutation = useMutation({ mutationFn: (scheduleId: string) => remindCollection(scheduleId) });

  const paying = rows.find((r) => r.scheduleId === payingId);

  return (
    <>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t('collection.columns.dueDate')}</TableHead>
            <TableHead>{t('collection.columns.project')}</TableHead>
            <TableHead>{t('collection.columns.plot')}</TableHead>
            <TableHead>{t('collection.columns.buyer')}</TableHead>
            <TableHead>{t('collection.columns.amountDue')}</TableHead>
            <TableHead>{t('collection.columns.daysOverdue')}</TableHead>
            <TableHead>{t('collection.columns.totalBalance')}</TableHead>
            {canEdit && <TableHead>{t('collection.columns.actions')}</TableHead>}
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((r) => (
            <TableRow key={r.scheduleId}>
              <TableCell>{r.dueDate}</TableCell>
              <TableCell>{r.projectName}</TableCell>
              <TableCell>{r.plotNumber}</TableCell>
              <TableCell>
                {r.buyerName}
                <div className="text-xs text-muted-foreground">{r.buyerMobile}</div>
              </TableCell>
              <TableCell>{formatIndianCurrency(r.amountDue)}</TableCell>
              <TableCell>
                <OverdueSeverityChip daysOverdue={r.daysOverdue} />
              </TableCell>
              <TableCell>{formatIndianCurrency(r.totalBalance)}</TableCell>
              {canEdit && (
                <TableCell className="space-x-1 whitespace-nowrap">
                  <Button size="sm" onClick={() => { setPayingId(r.scheduleId); setAmount(String(r.amountDue)); setMode('CASH'); setReference(''); }}>
                    {t('collection.recordPayment')}
                  </Button>
                  <Button
                    size="icon"
                    variant="ghost"
                    disabled={!r.reminderEnabled || !r.buyerOptedIn || remindMutation.isPending}
                    aria-label={t('actions.whatsapp')}
                    title={!r.buyerOptedIn ? t('tracker.buyerNotOptedIn', { ns: 'errors' }) : undefined}
                    onClick={() => remindMutation.mutate(r.scheduleId)}
                  >
                    <MessageCircle className="size-4" />
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
            <DialogTitle>{t('collection.recordPayment')}{paying ? ` — ${paying.buyerName}` : ''}</DialogTitle>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-2">
              <Label htmlFor="tracker-quick-amount">{t('addPayment.amount', { ns: 'payment' })}</Label>
              <Input id="tracker-quick-amount" type="number" value={amount} onChange={(e) => setAmount(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="tracker-quick-paidOn">{t('addPayment.paidOn', { ns: 'payment' })}</Label>
              <Input id="tracker-quick-paidOn" type="date" value={paidOn} onChange={(e) => setPaidOn(e.target.value)} />
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
                <Label htmlFor="tracker-quick-reference">{t('addPayment.reference', { ns: 'payment' })}</Label>
                <Input id="tracker-quick-reference" value={reference} onChange={(e) => setReference(e.target.value)} />
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
