import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/data/EmptyState';
import { Skeleton } from '@/components/ui/skeleton';
import { FileText } from 'lucide-react';
import { listReports } from '../api/reportApi';

// B-11 §17.1 / M-10 §6 ReportCatalog: nine report cards, permission-filtered
// server-side already (ReportService.list() only returns what this
// caller's role can see) -- no client-side re-filtering needed.
export function ReportCatalogPage() {
  const { t, i18n } = useTranslation('report');
  const reportsQuery = useQuery({ queryKey: ['report-definitions'], queryFn: listReports });

  return (
    <div>
      <PageHeader title={t('catalog.title')} description={t('catalog.description')} />
      {reportsQuery.isLoading && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-28 rounded-md" />)}
        </div>
      )}
      {reportsQuery.data && reportsQuery.data.length === 0 && (
        <EmptyState icon={<FileText className="size-10" />} title={t('catalog.empty')} />
      )}
      {reportsQuery.data && reportsQuery.data.length > 0 && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {reportsQuery.data.map((r) => (
            <Link
              key={r.code}
              to={`/builder/reports/${r.code}`}
              className="rounded-lg border border-border bg-card p-4 shadow-sm transition hover:border-primary hover:shadow-md"
            >
              <div className="mb-2 flex items-center gap-2">
                <FileText className="size-5 text-primary" />
                <h3 className="font-medium">{i18n.language === 'hi' ? r.nameHi : r.nameEn}</h3>
              </div>
              <p className="text-sm text-muted-foreground">{t(r.descriptionKey, { defaultValue: '' })}</p>
              {!r.canExport && (
                <p className="mt-2 text-xs text-amber-600">{t('catalog.exportLocked')}</p>
              )}
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
