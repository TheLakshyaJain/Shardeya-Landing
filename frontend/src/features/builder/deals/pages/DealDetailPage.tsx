import { useTranslation } from 'react-i18next';
import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { PageHeader } from '@/components/layout/PageHeader';
import { Skeleton } from '@/components/ui/skeleton';
import { Badge } from '@/components/ui/badge';
import { formatIndianCurrency } from '@/lib/formatters';
import { getDeal } from '../api/dealsApi';
import { DealStatusBadge } from '../components/DealStatusBadge';
import { DealTimeline } from '../components/DealTimeline';

// B-10 §6: "Detail view assembles buyer info, plot/project info, the full
// payment history with receipts, all documents, the lead's interaction
// timeline, commission ledger entry, and every audit event."
export function DealDetailPage() {
  const { t } = useTranslation(['deal', 'common']);
  const { id } = useParams<{ id: string }>();
  const dealQuery = useQuery({ queryKey: ['deal-detail', id], queryFn: () => getDeal(id!), enabled: !!id });

  if (dealQuery.isLoading || !dealQuery.data) {
    return <Skeleton className="h-64 w-full rounded-md" />;
  }
  const deal = dealQuery.data;

  return (
    <div>
      <PageHeader
        title={`${deal.buyerName} — ${deal.plotNumber}`}
        actions={<DealStatusBadge status={deal.status} />}
      />

      <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div className="space-y-1 text-sm">
          <p><span className="text-muted-foreground">{t('detail.project')}: </span>{deal.projectName}</p>
          <p><span className="text-muted-foreground">{t('detail.plotSize')}: </span>{deal.plotSizeSqft.toLocaleString('en-IN')} sqft</p>
          <p><span className="text-muted-foreground">{t('detail.buyerMobile')}: </span>{deal.buyerMobile}</p>
          {deal.buyerEmail && <p><span className="text-muted-foreground">{t('detail.buyerEmail')}: </span>{deal.buyerEmail}</p>}
          <p>
            <span className="text-muted-foreground">{t('detail.handledBy')}: </span>
            {deal.handledByName ?? '—'}
            {deal.handledByFormerStaff && <span className="text-muted-foreground"> ({t('detail.formerStaff')})</span>}
          </p>
        </div>
        <div className="space-y-1 text-sm">
          {deal.dealValue != null && <p><span className="text-muted-foreground">{t('detail.dealValue')}: </span>{formatIndianCurrency(deal.dealValue)}</p>}
          {deal.totalCollected != null && <p><span className="text-muted-foreground">{t('detail.totalCollected')}: </span>{formatIndianCurrency(deal.totalCollected)}</p>}
          {deal.balance != null && (
            <p>
              <span className="text-muted-foreground">{t('detail.balance')}: </span>
              {formatIndianCurrency(deal.balance)}
              {deal.balance !== 0 && deal.status === 'COMPLETED' && (
                <Badge variant="outline" className="ml-2 border-amber-300 bg-amber-100 text-amber-800">
                  {t('detail.balanceNonZero')}
                </Badge>
              )}
            </p>
          )}
          {deal.status === 'CANCELLED' && deal.cancellationReason && (
            <p><span className="text-muted-foreground">{t('detail.cancellationReason')}: </span>{deal.cancellationReason}</p>
          )}
        </div>
      </div>

      {deal.documents.length > 0 ? (
        <div className="mb-6">
          <h3 className="mb-2 text-sm font-medium">{t('detail.documents')}</h3>
          <ul className="space-y-1 text-sm">
            {deal.documents.map((d) => (
              <li key={d.id}>
                {t(`documentType.${d.docType}`, { defaultValue: d.docType })}
                {d.label ? ` — ${d.label}` : ''}
              </li>
            ))}
          </ul>
        </div>
      ) : (
        <p className="mb-6 text-sm text-amber-600">{t('detail.documentsIncomplete')}</p>
      )}

      <div>
        <h3 className="mb-2 text-sm font-medium">{t('detail.timeline')}</h3>
        <DealTimeline deal={deal} />
      </div>
    </div>
  );
}
