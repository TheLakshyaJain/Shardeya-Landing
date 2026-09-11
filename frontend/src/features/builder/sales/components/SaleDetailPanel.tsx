import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Plus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import { Skeleton } from '@/components/ui/skeleton';
import { formatIndianCurrency } from '@/lib/formatters';
import { useCan } from '@/hooks/useCan';
import { useAuthStore } from '@/features/auth/store/authStore';
import { getSaleByPlot, completeSale, setBuyerWhatsAppOptIn } from '../api/saleApi';
import { listPayments, listSchedule, getPaymentSummary } from '../../payments/api/paymentApi';
import { PaymentSummaryPanel } from '../../payments/components/PaymentSummaryPanel';
import { ScheduleTable } from '../../payments/components/ScheduleTable';
import { AddPaymentDialog } from '../../payments/components/AddPaymentDialog';
import { PaymentHistoryTable } from '../../payments/components/PaymentHistoryTable';
import { SensitiveDocGuard } from './SensitiveDocGuard';
import { DocumentSlots } from './DocumentSlots';
import { CancelSaleDialog } from './CancelSaleDialog';
import { GenerateDocumentButton } from '../../documents/components/GenerateDocumentButton';
import { DocumentList } from '../../documents/components/DocumentList';

interface SaleDetailPanelProps {
  plotId: string;
  projectId: string;
  onCancelled: () => void;
}

// M4: this component's various actions were all previously gated on ONE
// canEdit prop threaded down from the parent (itself PLOT_EDIT-derived) --
// correct by coincidence for M1-M3's only two roles (both held every
// permission or none), but wrong the moment a real role matrix exists:
// Accounts Staff holds FINANCIAL_RECORD_PAYMENT but not PLOT_EDIT, so
// "Record Payment" would have stayed hidden from the one role whose entire
// job is recording payments (B-05 §9). Each action now checks its own
// correct permission directly via useCan instead of relying on a prop drilled
// from a parent that was reasoning about the PLOT, not the sale. Cancel/
// documents are DATA_EDIT_ALL (Admin/Manager per B-04 §9, matching what
// PlotSaleController.cancel() actually enforces); Record Payment is
// FINANCIAL_RECORD_PAYMENT; reverse/cheque-status (passed down to
// PaymentHistoryTable) is FINANCIAL_EDIT. Waive is narrower still -- B-05
// §9 says "Admin only", not just FINANCIAL_EDIT (which Accounts Staff also
// holds) -- there's no dedicated permission code for this in the fixed
// M-02 catalogue, so it's gated on isOwner directly (BUILDER_ADMIN is
// exclusively the org owner's role), matching the same check the backend
// now enforces in ScheduleService.waive().
export function SaleDetailPanel({ plotId, projectId, onCancelled }: SaleDetailPanelProps) {
  const { t } = useTranslation(['sale', 'payment', 'common']);
  const queryClient = useQueryClient();
  const canEditSale = useCan('DATA_EDIT_ALL');
  const canRecordPayment = useCan('FINANCIAL_RECORD_PAYMENT');
  const canEditFinancial = useCan('FINANCIAL_EDIT');
  const canWaive = useAuthStore((s) => s.user?.isOwner ?? false);
  const canGenerateDocuments = useCan('DOCUMENT_GENERATE');
  const [addingPayment, setAddingPayment] = useState(false);
  const [cancelling, setCancelling] = useState(false);

  const saleQuery = useQuery({ queryKey: ['sale-by-plot', plotId], queryFn: () => getSaleByPlot(plotId) });
  const sale = saleQuery.data;

  // B-04 §7: PlotSaleService.complete() has existed since M3 but was never
  // reachable from any UI -- a fully-paid sale stayed ACTIVE forever unless
  // someone called the raw API directly. B-10 Deals History can only ever
  // show a COMPLETED deal via this transition, so M5 is what finally wires
  // it up. Shown only once balance is actually zero -- matches the same
  // guard PlotSaleService.complete() enforces server-side (SALE_NOT_FULLY_PAID).
  const completeMutation = useMutation({
    mutationFn: () => completeSale(sale!.id),
    onSuccess: (updated) => {
      queryClient.setQueryData(['sale-by-plot', plotId], updated);
      queryClient.invalidateQueries({ queryKey: ['builder-deals'] });
    },
  });

  // A buyer may agree to (or later withdraw) WhatsApp reminders any time
  // after the sale itself was created -- this is the "toggle it later"
  // half of consent capture, the wizard's own checkbox being the other.
  const whatsAppOptInMutation = useMutation({
    mutationFn: (optedIn: boolean) => setBuyerWhatsAppOptIn(sale!.id, optedIn),
    onSuccess: (updated) => {
      queryClient.setQueryData(['sale-by-plot', plotId], updated);
      queryClient.invalidateQueries({ queryKey: ['tracker-collections'] });
    },
  });

  const summaryQuery = useQuery({
    queryKey: ['payment-summary', sale?.id],
    queryFn: () => getPaymentSummary(sale!.id),
    enabled: !!sale,
  });
  const scheduleQuery = useQuery({ queryKey: ['schedule', sale?.id], queryFn: () => listSchedule(sale!.id), enabled: !!sale });
  const paymentsQuery = useQuery({ queryKey: ['payments', sale?.id], queryFn: () => listPayments(sale!.id), enabled: !!sale });

  if (saleQuery.isLoading) {
    return <Skeleton className="h-48 w-full rounded-md" />;
  }
  if (!sale) {
    return <p className="text-sm text-muted-foreground">{t('common:generic', { ns: 'errors' })}</p>;
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-medium">{t('detail.buyer')}: {sale.buyerName}</h3>
        <div className="flex gap-2">
          {canEditSale && sale.status === 'ACTIVE' && sale.balanceDue <= 0 && (
            <Button size="sm" onClick={() => completeMutation.mutate()} disabled={completeMutation.isPending}>
              {t('detail.markComplete')}
            </Button>
          )}
          {canEditSale && sale.status === 'ACTIVE' && (
            <Button size="sm" variant="outline" onClick={() => setCancelling(true)}>
              {t('detail.cancelSale')}
            </Button>
          )}
        </div>
      </div>

      <dl className="grid grid-cols-2 gap-x-4 gap-y-1.5 text-sm">
        <dt className="text-muted-foreground">{t('form.fields.buyerMobile')}</dt>
        <dd>{sale.buyerMobile}</dd>
        <dt className="text-muted-foreground">{t('detail.purchaseDate')}</dt>
        <dd>{sale.purchaseDate}</dd>
        {(sale.buyerGovIdType || sale.buyerGovIdLast4) && (
          <>
            <dt className="text-muted-foreground">{t('form.fields.govIdType')}</dt>
            <dd>
              <SensitiveDocGuard saleId={sale.id} govIdType={sale.buyerGovIdType} last4={sale.buyerGovIdLast4} />
            </dd>
          </>
        )}
        {sale.brokerPartnerId && (
          <>
            <dt className="text-muted-foreground">{t('detail.broker')}</dt>
            <dd>
              <Link to={`/builder/brokers/${sale.brokerPartnerId}`} className="text-primary hover:underline">
                {sale.brokerName ?? sale.brokerPartnerId}
              </Link>
            </dd>
          </>
        )}
        {/* Legacy sales created before the free-text external-broker path was
            removed from the sale wizard (see SaleWizard's own comment) --
            no in-system broker profile to link to, so this stays plain text. */}
        {!sale.brokerPartnerId && sale.externalBrokerName && (
          <>
            <dt className="text-muted-foreground">{t('detail.broker')}</dt>
            <dd>
              {sale.externalBrokerName}
              {sale.brokerCommissionAmount ? ` — ${formatIndianCurrency(sale.brokerCommissionAmount)}` : ''}
            </dd>
          </>
        )}
      </dl>

      <div className="flex items-center gap-2">
        <Checkbox
          id="buyerWhatsappOptInToggle"
          checked={sale.buyerWhatsappOptedIn}
          disabled={!canEditSale || whatsAppOptInMutation.isPending}
          onCheckedChange={(c) => whatsAppOptInMutation.mutate(c === true)}
        />
        <Label htmlFor="buyerWhatsappOptInToggle" className="text-sm font-normal text-muted-foreground">
          {t('whatsappOptIn.detailLabel')} — {sale.buyerWhatsappOptedIn ? t('whatsappOptIn.optedInHint') : t('whatsappOptIn.notOptedInHint')}
        </Label>
      </div>

      {summaryQuery.data && <PaymentSummaryPanel summary={summaryQuery.data} />}

      <div className="space-y-2">
        <div className="flex items-center justify-between">
          <h4 className="text-sm font-medium">{t('schedule.title', { ns: 'payment' })}</h4>
          {canRecordPayment && sale.status === 'ACTIVE' && (
            <Button size="sm" onClick={() => setAddingPayment(true)}>
              <Plus className="size-3.5" />
              {t('addPayment.title', { ns: 'payment' })}
            </Button>
          )}
        </div>
        {scheduleQuery.data && <ScheduleTable saleId={sale.id} schedule={scheduleQuery.data} canEdit={canWaive && sale.status === 'ACTIVE'} />}
      </div>

      <div className="space-y-2">
        <h4 className="text-sm font-medium">{t('history.title', { ns: 'payment' })}</h4>
        {paymentsQuery.data && (
          <PaymentHistoryTable saleId={sale.id} payments={paymentsQuery.data} canEdit={canEditFinancial && sale.status === 'ACTIVE'} />
        )}
      </div>

      <div className="space-y-2">
        <h4 className="text-sm font-medium">{t('documents.title')}</h4>
        <DocumentSlots saleId={sale.id} canEdit={canEditSale} />
      </div>

      {/* B-11 §17.2 legal documents -- a distinct concept from DocumentSlots
          above (plot_document scans the buyer/builder upload) -- these are
          generated PDFs rendered server-side from a template + live data. */}
      {canGenerateDocuments && (
        <div className="space-y-2">
          <h4 className="text-sm font-medium">{t('legalDocuments.title')}</h4>
          <div className="flex flex-wrap gap-2">
            <GenerateDocumentButton docType="ALLOTMENT_LETTER" entityId={sale.id} entityType="PLOT_SALE" />
            {sale.status === 'ACTIVE' && (
              <GenerateDocumentButton docType="DEMAND_LETTER" entityId={sale.id} entityType="PLOT_SALE" />
            )}
          </div>
          <DocumentList entityType="PLOT_SALE" entityId={sale.id} />
        </div>
      )}

      {scheduleQuery.data && (
        <AddPaymentDialog saleId={sale.id} schedule={scheduleQuery.data} open={addingPayment} onOpenChange={setAddingPayment} />
      )}
      <CancelSaleDialog
        saleId={sale.id}
        plotId={plotId}
        projectId={projectId}
        open={cancelling}
        onOpenChange={setCancelling}
        onCancelled={onCancelled}
      />
    </div>
  );
}
