import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { EmptyState } from '@/components/data/EmptyState';
import { Badge } from '@/components/ui/badge';
import { formatIndianCurrency } from '@/lib/formatters';
import { getDeals } from '../api/brokerApi';

export function DealsTab({ brokerId }: { brokerId: string }) {
  const { t } = useTranslation('broker');
  const dealsQuery = useQuery({ queryKey: ['broker-deals', brokerId], queryFn: () => getDeals(brokerId) });
  const deals = dealsQuery.data ?? [];

  if (deals.length === 0) {
    return <EmptyState title={t('deals.empty')} />;
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>{t('deals.columns.project')}</TableHead>
          <TableHead>{t('deals.columns.plot')}</TableHead>
          <TableHead>{t('deals.columns.buyer')}</TableHead>
          <TableHead>{t('deals.columns.date')}</TableHead>
          <TableHead>{t('deals.columns.dealValue')}</TableHead>
          <TableHead>{t('deals.columns.status')}</TableHead>
          <TableHead>{t('deals.columns.commission')}</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {deals.map((d) => (
          <TableRow key={d.saleId}>
            <TableCell>{d.projectName ?? '—'}</TableCell>
            <TableCell>{d.plotNumber ?? '—'}</TableCell>
            <TableCell>{d.buyerName}</TableCell>
            <TableCell>{d.purchaseDate}</TableCell>
            <TableCell>{formatIndianCurrency(d.dealValue)}</TableCell>
            <TableCell>
              <Badge variant={d.saleStatus === 'COMPLETED' ? 'default' : d.saleStatus === 'CANCELLED' ? 'outline' : 'secondary'}>
                {d.saleStatus}
              </Badge>
            </TableCell>
            <TableCell>{d.commissionEarned != null ? formatIndianCurrency(d.commissionEarned) : '—'}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
