import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Handshake, Plus } from 'lucide-react';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/data/EmptyState';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useCan } from '@/hooks/useCan';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { formatIndianCurrency } from '@/lib/formatters';
import { listBrokers } from '../api/brokerApi';
import { BrokerFormDialog } from '../components/BrokerFormDialog';

export function BrokerListPage() {
  const { t, i18n } = useTranslation(['broker', 'common']);
  const canManage = useCan('BROKER_MANAGE');
  const [search, setSearch] = useState('');
  const [addOpen, setAddOpen] = useState(false);

  const query = useQuery({ queryKey: ['brokers', search], queryFn: () => listBrokers(undefined, undefined, search || undefined) });
  const brokers = query.data ?? [];

  return (
    <div>
      <PageHeader
        title={t('list.title')}
        actions={
          <div className="flex gap-2">
            <Button variant="outline" asChild>
              <Link to="/builder/brokers/network">{t('network.title')}</Link>
            </Button>
            {canManage && (
              <>
                <Button variant="outline" asChild>
                  <Link to="/builder/brokers/tiers">{t('tier.pageTitle')}</Link>
                </Button>
                <Button onClick={() => setAddOpen(true)}>
                  <Plus className="size-4" />
                  {t('list.addBroker')}
                </Button>
              </>
            )}
          </div>
        }
      />

      <Input
        placeholder={t('list.search')}
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        className="mb-4 max-w-sm"
      />

      {query.isLoading ? null : query.isError ? (
        <p className="text-sm text-destructive">{resolveErrorMessage(query.error)}</p>
      ) : brokers.length === 0 ? (
        <EmptyState icon={<Handshake className="size-10" />} title={t('list.empty')} />
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t('list.columns.name')}</TableHead>
              <TableHead>{t('list.columns.mobile')}</TableHead>
              <TableHead>{t('list.columns.city')}</TableHead>
              <TableHead>{t('list.columns.tier')}</TableHead>
              <TableHead>{t('list.columns.dealsClosed')}</TableHead>
              <TableHead>{t('list.columns.commissionDue')}</TableHead>
              <TableHead>{t('list.columns.status')}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {brokers.map((b) => (
              <TableRow key={b.id} className="cursor-pointer">
                <TableCell>
                  <Link to={`/builder/brokers/${b.id}`} className="font-medium text-foreground hover:underline">
                    {b.fullName}
                  </Link>
                </TableCell>
                <TableCell>{b.mobile}</TableCell>
                <TableCell>{b.cityArea ?? '—'}</TableCell>
                <TableCell>
                  {(i18n.language === 'hi' ? b.currentDesignationNameHi : b.currentDesignationName) ?? b.tierName ?? '—'}
                </TableCell>
                <TableCell>{b.dealsClosedCount}</TableCell>
                <TableCell>{formatIndianCurrency(b.commissionDue)}</TableCell>
                <TableCell>
                  <Badge variant={b.status === 'ACTIVE' ? 'default' : 'outline'}>{t(`status.${b.status}`)}</Badge>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <BrokerFormDialog open={addOpen} onOpenChange={setAddOpen} />
    </div>
  );
}
