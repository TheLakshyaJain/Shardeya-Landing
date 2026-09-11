import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { Download } from 'lucide-react';
import { formatIndianCurrency } from '@/lib/formatters';
import { reversePayment, updateChequeStatus } from '../api/paymentApi';
import { downloadDocument, listDocumentsForEntity } from '../../documents/api/documentApi';
import { ChequeStatusChip } from './ChequeStatusChip';
import type { PaymentResponse } from '../types';

// B-11 §17.2: a receipt PDF is auto-generated asynchronously (via the
// outbox, CLAUDE.md rule #6) on every payment -- there is a real, if
// short, window after recording a payment where it doesn't exist yet.
// Fetched on click rather than eagerly per-row (avoids an N-query fan-out
// against a payment history that can be long-lived).
async function downloadReceiptFor(paymentId: string, receiptNo: string) {
  const docs = await listDocumentsForEntity('PAYMENT_RECORD', paymentId);
  const receipt = docs.find((d) => d.docType === 'PAYMENT_RECEIPT');
  if (receipt) await downloadDocument(receipt.id, receiptNo.replace(/\//g, '-'));
}

interface PaymentHistoryTableProps {
  saleId: string;
  payments: PaymentResponse[];
  canEdit: boolean;
}

export function PaymentHistoryTable({ saleId, payments, canEdit }: PaymentHistoryTableProps) {
  const { t } = useTranslation(['payment', 'common']);
  const queryClient = useQueryClient();
  const [reversingId, setReversingId] = useState<string | null>(null);
  const [reverseReason, setReverseReason] = useState('');

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['payments', saleId] });
    queryClient.invalidateQueries({ queryKey: ['payment-summary', saleId] });
    queryClient.invalidateQueries({ queryKey: ['schedule', saleId] });
  };

  const reverseMutation = useMutation({
    mutationFn: (paymentId: string) => reversePayment(paymentId, reverseReason),
    onSuccess: () => {
      invalidate();
      setReversingId(null);
      setReverseReason('');
    },
  });

  const chequeMutation = useMutation({
    mutationFn: ({ paymentId, status }: { paymentId: string; status: 'CLEARED' | 'BOUNCED' }) => updateChequeStatus(paymentId, status),
    onSuccess: invalidate,
  });

  if (payments.length === 0) {
    return <p className="py-4 text-center text-sm text-muted-foreground">{t('history.empty')}</p>;
  }

  return (
    <>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t('history.columns.receiptNo')}</TableHead>
            <TableHead>{t('history.columns.amount')}</TableHead>
            <TableHead>{t('history.columns.date')}</TableHead>
            <TableHead>{t('history.columns.mode')}</TableHead>
            <TableHead>{t('history.columns.reference')}</TableHead>
            {canEdit && <TableHead>{t('history.columns.actions')}</TableHead>}
          </TableRow>
        </TableHeader>
        <TableBody>
          {payments.map((p) => {
            // The backend now rejects a second reversal of the same
            // original payment (a payment can only ever be reversed once),
            // but this button had no matching client-side check -- it kept
            // showing "Reverse Payment" on the original row even after it
            // had already been reversed, since that check only looked at
            // the ROW ITSELF (`!p.reversesPaymentId`, true for every
            // original payment) rather than whether some OTHER row already
            // reverses this one. Clicking it a second time used to create a
            // second negative allocation on top of the first instead of
            // being blocked.
            const alreadyReversed = payments.some((other) => other.reversesPaymentId === p.id);
            // Cross-referenced within the SAME response rather than a
            // second lookup -- both rows (a reversal and the original it
            // reverses) are always already present in this one sale's
            // payment list.
            const reversedOriginal = p.reversesPaymentId ? payments.find((other) => other.id === p.reversesPaymentId) : undefined;
            const dueToChequeBounce = reversedOriginal?.mode === 'CHEQUE' && reversedOriginal?.chequeStatus === 'BOUNCED';
            return (
            <TableRow key={p.id} className={p.amount < 0 ? 'text-muted-foreground italic' : ''}>
              <TableCell>
                <span className="inline-flex items-center gap-1">
                  {p.receiptNo}
                  {p.amount > 0 && (
                    <Button
                      size="icon"
                      variant="ghost"
                      className="size-6"
                      title={t('history.downloadReceipt')}
                      onClick={() => downloadReceiptFor(p.id, p.receiptNo)}
                    >
                      <Download className="size-3.5" />
                    </Button>
                  )}
                </span>
                {p.reversesPaymentId && (
                  <div className="text-xs">
                    {dueToChequeBounce
                      ? t('history.bouncedReversalOf', { receiptNo: reversedOriginal?.receiptNo ?? p.reversesPaymentId })
                      : t('history.reversalOf', { receiptNo: reversedOriginal?.receiptNo ?? p.reversesPaymentId })}
                  </div>
                )}
              </TableCell>
              <TableCell className={p.amount < 0 ? 'text-destructive' : ''}>{formatIndianCurrency(p.amount)}</TableCell>
              <TableCell>{p.paidOn}</TableCell>
              <TableCell>
                {t(`mode.${p.mode}`)}
                {p.chequeStatus && (
                  <div className="mt-1">
                    <ChequeStatusChip status={p.chequeStatus} />
                  </div>
                )}
              </TableCell>
              <TableCell>{p.reference || '—'}</TableCell>
              {canEdit && (
                <TableCell className="space-x-1">
                  {/* A reversal record inherits mode=CHEQUE + a default PENDING
                      chequeStatus from the original it corrects (see doReverse's
                      own comment) -- excluding !p.reversesPaymentId here keeps
                      a correction entry from showing Cleared/Bounced actions
                      that would recurse into reversing the reversal itself. */}
                  {p.mode === 'CHEQUE' && p.chequeStatus === 'PENDING' && !p.reversesPaymentId && (
                    <>
                      <Button size="sm" variant="outline" onClick={() => chequeMutation.mutate({ paymentId: p.id, status: 'CLEARED' })}>
                        {t('cheque.markCleared')}
                      </Button>
                      <Button size="sm" variant="outline" onClick={() => chequeMutation.mutate({ paymentId: p.id, status: 'BOUNCED' })}>
                        {t('cheque.markBounced')}
                      </Button>
                    </>
                  )}
                  {p.amount > 0 && !p.reversesPaymentId && !alreadyReversed && (
                    <Button size="sm" variant="outline" onClick={() => setReversingId(p.id)}>
                      {t('reverse.title')}
                    </Button>
                  )}
                </TableCell>
              )}
            </TableRow>
            );
          })}
        </TableBody>
      </Table>

      <Dialog open={!!reversingId} onOpenChange={(open) => !open && setReversingId(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('reverse.title')}</DialogTitle>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="reverseReason">{t('reverse.reason')}</Label>
            <Textarea id="reverseReason" value={reverseReason} onChange={(e) => setReverseReason(e.target.value)} />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setReversingId(null)}>
              {t('common:actions.cancel')}
            </Button>
            <Button
              disabled={reverseReason.trim().length < 5 || reverseMutation.isPending}
              onClick={() => reversingId && reverseMutation.mutate(reversingId)}
            >
              {t('reverse.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
