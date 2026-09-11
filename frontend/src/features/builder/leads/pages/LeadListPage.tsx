import { useMemo, useState } from 'react';
import { useInfiniteQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Contact, Plus } from 'lucide-react';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/data/EmptyState';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useCan } from '@/hooks/useCan';
import { useAuthStore } from '@/features/auth/store/authStore';
import { formatIndianCurrency } from '@/lib/formatters';
import { listLeads } from '../api/leadApi';
import { LeadFormDialog } from '../components/LeadFormDialog';

export function LeadListPage() {
  const { t } = useTranslation(['customer', 'common']);
  const navigate = useNavigate();
  const canViewAll = useCan('DATA_VIEW_ALL');
  const canViewOwn = useCan('DATA_VIEW_OWN');
  const canView = canViewAll || canViewOwn;
  const canCreate = useCan('DATA_CREATE');
  const userId = useAuthStore((s) => s.user?.id);
  const [tab, setTab] = useState<'all' | 'mine' | 'unassigned'>('all');
  const [search, setSearch] = useState('');
  const [formOpen, setFormOpen] = useState(false);

  const assignedTo = tab === 'mine' ? userId : undefined;

  const query = useInfiniteQuery({
    queryKey: ['leads', tab, search],
    queryFn: ({ pageParam }) =>
      listLeads({
        cursor: pageParam,
        assignedTo: tab === 'unassigned' ? undefined : assignedTo,
        search: search || undefined,
      }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => (lastPage.hasMore ? (lastPage.nextCursor ?? undefined) : undefined),
    enabled: canView,
  });

  const allLeads = useMemo(() => query.data?.pages.flatMap((p) => p.items) ?? [], [query.data]);
  const filtered = tab === 'unassigned' ? allLeads.filter((l) => !l.assignedTo) : allLeads;

  if (!canView) {
    return (
      <div>
        <PageHeader title={t('list.title')} />
        <EmptyState icon={<Contact className="size-10" />} title={t('list.noLeadAccess')} />
      </div>
    );
  }

  return (
    <div>
      <PageHeader
        title={t('list.title')}
        actions={
          canCreate && (
            <Button onClick={() => setFormOpen(true)}>
              <Plus className="size-4" />
              {t('list.addLead')}
            </Button>
          )
        }
      />

      <Tabs value={tab} onValueChange={(v) => setTab(v as typeof tab)} className="mb-4">
        <TabsList>
          <TabsTrigger value="all">{t('list.tabs.all')}</TabsTrigger>
          <TabsTrigger value="mine">{t('list.tabs.mine')}</TabsTrigger>
          <TabsTrigger value="unassigned">{t('list.tabs.unassigned')}</TabsTrigger>
        </TabsList>
      </Tabs>

      <Input
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        placeholder={t('list.searchPlaceholder')}
        className="mb-4 max-w-sm"
      />

      {query.isLoading ? null : filtered.length === 0 ? (
        <EmptyState icon={<Contact className="size-10" />} title={t('list.empty')} />
      ) : (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('list.columns.name')}</TableHead>
                <TableHead>{t('list.columns.contact')}</TableHead>
                <TableHead>{t('list.columns.budget')}</TableHead>
                <TableHead>{t('list.columns.status')}</TableHead>
                <TableHead>{t('list.columns.followUp')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filtered.map((lead) => (
                <TableRow key={lead.id} className="cursor-pointer" onClick={() => navigate(`/builder/leads/${lead.id}`)}>
                  <TableCell>
                    {lead.important && <span className="mr-1 text-amber-500">★</span>}
                    {lead.fullName}
                  </TableCell>
                  <TableCell>{lead.mobile}</TableCell>
                  <TableCell>
                    {formatIndianCurrency(lead.budgetMin)} – {formatIndianCurrency(lead.budgetMax)}
                  </TableCell>
                  <TableCell>
                    <Badge variant="outline">{t(`status.${lead.status}`)}</Badge>
                  </TableCell>
                  <TableCell>
                    {lead.followUpDate && (
                      <span className={new Date(lead.followUpDate) < new Date(new Date().toDateString()) ? 'text-destructive' : ''}>
                        {lead.followUpDate}
                      </span>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          {query.hasNextPage && (
            <div className="mt-4 flex justify-center">
              <Button variant="outline" onClick={() => query.fetchNextPage()} disabled={query.isFetchingNextPage}>
                {t('common:list.loadMore', { defaultValue: 'Load more' })}
              </Button>
            </div>
          )}
        </>
      )}

      <LeadFormDialog open={formOpen} onOpenChange={setFormOpen} />
    </div>
  );
}
