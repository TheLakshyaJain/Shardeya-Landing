import { useTranslation } from 'react-i18next';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { formatIndianCurrency } from '@/lib/formatters';
import type { FinancialPaymentRow } from '../types';

interface PaymentRecordsTableProps {
  rows: FinancialPaymentRow[];
}

// B-08 §14.2 columns: Date · Project · Plot Number · Buyer Name ·
// Instalment # · Amount · Mode · Reference · Recorded By · Actions.
// "Actions" has nothing to show here (reverse/cheque-status live on the
// sale's own Payment History tab, B-05) -- this is a cross-org read view,
// not a place to mutate from.
export function PaymentRecordsTable({ rows }: PaymentRecordsTableProps) {
  const { t } = useTranslation(['financial', 'payment', 'common']);

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>{t('payments.columns.date')}</TableHead>
          <TableHead>{t('payments.columns.project')}</TableHead>
          <TableHead>{t('payments.columns.plotNumber')}</TableHead>
          <TableHead>{t('payments.columns.buyerName')}</TableHead>
          <TableHead>{t('payments.columns.instalmentNo')}</TableHead>
          <TableHead>{t('payments.columns.amount')}</TableHead>
          <TableHead>{t('payments.columns.mode')}</TableHead>
          <TableHead>{t('payments.columns.reference')}</TableHead>
          <TableHead>{t('payments.columns.recordedBy')}</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((r) => (
          <TableRow key={r.id}>
            <TableCell>{r.paidOn}</TableCell>
            <TableCell>{r.projectName}</TableCell>
            <TableCell>{r.plotNumber}</TableCell>
            <TableCell>{r.buyerName}</TableCell>
            <TableCell>{r.instalmentSequenceNo ?? '—'}</TableCell>
            <TableCell className={r.isReversal ? 'text-destructive' : ''}>
              {formatIndianCurrency(r.amount)}
              {r.isReversal && (
                <Badge variant="destructive" className="ml-1" title={r.remarks ?? undefined}>
                  {r.dueToChequeBounce ? t('payments.bounced') : t('payments.reversal')}
                </Badge>
              )}
            </TableCell>
            <TableCell>{t(`mode.${r.mode}`, { ns: 'payment' })}</TableCell>
            <TableCell>{r.reference ?? '—'}</TableCell>
            <TableCell>{r.recordedByName ?? '—'}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
