import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { formatIndianCurrency } from '@/lib/formatters';
import { DealStatusBadge } from './DealStatusBadge';
import type { DealRow } from '../types';

interface DealsHistoryTableProps {
  rows: DealRow[];
  showFinancials: boolean;
}

// B-10 §16 columns: Date · Project · Plot Number · Plot Size · Buyer Name ·
// Buyer Contact · Deal Value · Total Collected · Balance · Broker · Broker
// Commission · Deal Status · Staff Handled By · Actions. Financial columns
// (Deal Value/Collected/Balance/Commission) are omitted entirely, not
// blanked, when the caller lacks FINANCIAL_VIEW -- backend already sends
// null for them in that case (§9), this just doesn't render the columns.
export function DealsHistoryTable({ rows, showFinancials }: DealsHistoryTableProps) {
  const { t } = useTranslation(['deal', 'common']);
  const navigate = useNavigate();

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>{t('columns.date')}</TableHead>
          <TableHead>{t('columns.project')}</TableHead>
          <TableHead>{t('columns.plotNumber')}</TableHead>
          <TableHead>{t('columns.plotSize')}</TableHead>
          <TableHead>{t('columns.buyer')}</TableHead>
          {showFinancials && <TableHead>{t('columns.dealValue')}</TableHead>}
          {showFinancials && <TableHead>{t('columns.collected')}</TableHead>}
          {showFinancials && <TableHead>{t('columns.balance')}</TableHead>}
          <TableHead>{t('columns.status')}</TableHead>
          <TableHead>{t('columns.handledBy')}</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((r) => (
          <TableRow key={r.saleId} className="cursor-pointer" onClick={() => navigate(`/builder/deals/${r.saleId}`)}>
            <TableCell>{r.date}</TableCell>
            <TableCell>{r.projectName}</TableCell>
            <TableCell>{r.plotNumber}</TableCell>
            <TableCell>{r.plotSizeSqft.toLocaleString('en-IN')} {t('sqft', { ns: 'common', defaultValue: 'sqft' })}</TableCell>
            <TableCell>
              {r.buyerName}
              <div className="text-xs text-muted-foreground">{r.buyerMobile}</div>
            </TableCell>
            {showFinancials && <TableCell>{r.dealValue != null ? formatIndianCurrency(r.dealValue) : '—'}</TableCell>}
            {showFinancials && <TableCell>{r.totalCollected != null ? formatIndianCurrency(r.totalCollected) : '—'}</TableCell>}
            {showFinancials && <TableCell>{r.balance != null ? formatIndianCurrency(r.balance) : '—'}</TableCell>}
            <TableCell>
              <DealStatusBadge status={r.status} />
            </TableCell>
            <TableCell>{r.handledByName ?? '—'}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
