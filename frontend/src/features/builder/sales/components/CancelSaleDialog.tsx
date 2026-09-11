import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import { cancelSale } from '../api/saleApi';

interface CancelSaleDialogProps {
  saleId: string;
  plotId: string;
  projectId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCancelled: () => void;
}

// B-04 §7: cancelling never processes a refund automatically ("we don't
// move money") -- the consequences list here exists specifically so that's
// not a surprise after the fact.
export function CancelSaleDialog({ saleId, plotId, projectId, open, onOpenChange, onCancelled }: CancelSaleDialogProps) {
  const { t } = useTranslation(['sale', 'common', 'errors']);
  const queryClient = useQueryClient();
  const [reason, setReason] = useState('');
  const [refundHandling, setRefundHandling] = useState('');

  const mutation = useMutation({
    mutationFn: () => cancelSale(saleId, { reason, refundHandling: refundHandling || undefined }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['grid', projectId] });
      queryClient.invalidateQueries({ queryKey: ['plots', projectId] });
      queryClient.invalidateQueries({ queryKey: ['plot-stats', projectId] });
      queryClient.invalidateQueries({ queryKey: ['project', projectId] });
      queryClient.invalidateQueries({ queryKey: ['plot', plotId] });
      setReason('');
      setRefundHandling('');
      onOpenChange(false);
      onCancelled();
    },
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('cancel.title')}</DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
          <div className="rounded-md border border-border bg-muted/30 p-2 text-sm">
            <p className="mb-1 font-medium">{t('cancel.consequences.title')}</p>
            <ul className="list-inside list-disc space-y-0.5 text-muted-foreground">
              <li>{t('cancel.consequences.plotAvailable')}</li>
              <li>{t('cancel.consequences.scheduleWaived')}</li>
              <li>{t('cancel.consequences.paymentsRetained')}</li>
            </ul>
          </div>
          <div className="space-y-2">
            <Label htmlFor="cancel-reason">{t('cancel.reason')}</Label>
            <Textarea id="cancel-reason" value={reason} onChange={(e) => setReason(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="cancel-refund">{t('cancel.refundHandling')}</Label>
            <Textarea id="cancel-refund" value={refundHandling} onChange={(e) => setRefundHandling(e.target.value)} />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('common:actions.cancel')}
          </Button>
          <Button variant="destructive" disabled={reason.trim().length < 5 || mutation.isPending} onClick={() => mutation.mutate()}>
            {t('cancel.confirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
