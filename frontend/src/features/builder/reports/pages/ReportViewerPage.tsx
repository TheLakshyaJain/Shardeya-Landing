import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useParams, useSearchParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/data/EmptyState';
import { Skeleton } from '@/components/ui/skeleton';
import { Button } from '@/components/ui/button';
import { ChevronLeft, ChevronRight, FileText } from 'lucide-react';
import { listReports, previewReport } from '../api/reportApi';
import { ReportFilterPanel } from '../components/ReportFilterPanel';
import { ReportTable } from '../components/ReportTable';
import { ExportButton } from '../components/ExportButton';

const PAGE_SIZE = 50;

export function ReportViewerPage() {
  const { code } = useParams<{ code: string }>();
  const { t, i18n } = useTranslation('report');
  const [searchParams] = useSearchParams();
  // B-15 §7 "every chart is drill-through" -- a stats chart links here with
  // its own filters already in the query string (see MonthlySalesChart's
  // own navigate() call); read once on mount as the initial filter state,
  // same as a bookmarked/shared report URL would work.
  const [filters, setFilters] = useState<Record<string, string>>(() => Object.fromEntries(searchParams.entries()));
  const [page, setPage] = useState(0);

  const definitionsQuery = useQuery({ queryKey: ['report-definitions'], queryFn: listReports });
  const definition = definitionsQuery.data?.find((r) => r.code === code);

  const previewQuery = useQuery({
    queryKey: ['report-preview', code, filters, page],
    queryFn: () => previewReport(code!, filters, page),
    enabled: !!code,
  });

  function updateFilter(key: string, value: string) {
    setFilters((prev) => (value ? { ...prev, [key]: value } : Object.fromEntries(Object.entries(prev).filter(([k]) => k !== key))));
    setPage(0);
  }

  const title = definition ? (i18n.language === 'hi' ? definition.nameHi : definition.nameEn) : code;
  const totalPages = previewQuery.data ? Math.ceil(previewQuery.data.totalCount / PAGE_SIZE) : 1;

  return (
    <div>
      <Link to="/builder/reports" className="mb-2 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ChevronLeft className="size-4" />
        {t('viewer.back')}
      </Link>
      <PageHeader
        title={title ?? ''}
        actions={definition && <ExportButton code={definition.code} filters={filters} disabled={!definition.canExport} />}
      />

      {definition && <ReportFilterPanel filters={definition.supportedFilters} values={filters} onChange={updateFilter} />}

      {previewQuery.isLoading && <Skeleton className="h-64 w-full rounded-md" />}

      {previewQuery.data && previewQuery.data.rows.length === 0 && (
        <EmptyState icon={<FileText className="size-10" />} title={t('viewer.empty')} description={t('viewer.emptyHint')} />
      )}

      {previewQuery.data && previewQuery.data.rows.length > 0 && (
        <div className="space-y-3">
          <ReportTable columns={previewQuery.data.columns} rows={previewQuery.data.rows} />
          {totalPages > 1 && (
            <div className="flex items-center justify-between text-sm text-muted-foreground">
              <span>{t('viewer.pageOf', { page: page + 1, total: totalPages, count: previewQuery.data.totalCount })}</span>
              <div className="flex gap-2">
                <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                  <ChevronLeft className="size-4" />
                </Button>
                <Button variant="outline" size="sm" disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)}>
                  <ChevronRight className="size-4" />
                </Button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
