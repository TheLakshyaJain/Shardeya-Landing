import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { FormError } from '@/components/forms/FormError';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { bulkGenerateDemandLetters } from '../api/documentApi';

export interface BulkGenerateCandidate {
  plotSaleId: string;
  buyerName: string;
  plotNumber: string;
}

interface BulkGenerateDialogProps {
  candidates: BulkGenerateCandidate[];
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

// B-11 §8 "Financials -> Overdue (27) -> Generate Demand Letters -> single
// ZIP of 27 personalised PDFs" -- candidates arrive de-duplicated by
// plot_sale (a sale can have more than one overdue instalment row; one
// demand letter per SALE, not per row).
export function BulkGenerateDialog({ candidates, open, onOpenChange }: BulkGenerateDialogProps) {
  const { t } = useTranslation('document');
  const [selected, setSelected] = useState<Set<string>>(() => new Set(candidates.map((c) => c.plotSaleId)));

  const bulkMutation = useMutation({
    mutationFn: () => bulkGenerateDemandLetters(Array.from(selected), 'en'),
    onSuccess: () => onOpenChange(false),
  });

  function toggle(id: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('bulkGenerate.title', { count: candidates.length })}</DialogTitle>
        </DialogHeader>
        <div className="max-h-72 space-y-1 overflow-y-auto">
          {candidates.map((c) => (
            <label key={c.plotSaleId} className="flex items-center gap-2 rounded px-1 py-1 text-sm hover:bg-accent">
              <Checkbox checked={selected.has(c.plotSaleId)} onCheckedChange={() => toggle(c.plotSaleId)} />
              {c.buyerName} — {c.plotNumber}
            </label>
          ))}
        </div>
        {bulkMutation.isError && <FormError message={resolveErrorMessage(bulkMutation.error)} />}
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>{t('bulkGenerate.cancel')}</Button>
          <Button disabled={selected.size === 0 || bulkMutation.isPending} onClick={() => bulkMutation.mutate()}>
            {bulkMutation.isPending ? t('bulkGenerate.generating') : t('bulkGenerate.confirm', { count: selected.size })}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
