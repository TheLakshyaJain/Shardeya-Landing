import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { FormError } from '@/components/forms/FormError';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { INTERACTION_RESULTS, INTERACTION_TYPES } from '../../leads/types';
import { logFollowUp } from '../api/trackerApi';

interface QuickLogDialogProps {
  customerId: string | null;
  onOpenChange: (open: boolean) => void;
}

// B-13 §6: "inline, does not navigate away -- logging a call must take 3 taps."
export function QuickLogDialog({ customerId, onOpenChange }: QuickLogDialogProps) {
  const { t } = useTranslation(['tracker', 'customer', 'common']);
  const queryClient = useQueryClient();
  const [type, setType] = useState<string>('CALL');
  const [remarks, setRemarks] = useState('');
  const [nextDate, setNextDate] = useState('');
  const [result, setResult] = useState<string>('');

  const mutation = useMutation({
    mutationFn: () => logFollowUp(customerId!, { type: type as never, remarks, nextDate: nextDate || undefined, result: (result || undefined) as never }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tracker-followups'] });
      queryClient.invalidateQueries({ queryKey: ['tracker-counts'] });
      onOpenChange(false);
      setRemarks('');
      setNextDate('');
      setResult('');
    },
  });

  return (
    <Dialog open={!!customerId} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('quickLog.title')}</DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
          <div className="space-y-2">
            <Label>{t('interaction.typeLabel', { ns: 'customer' })}</Label>
            <Select value={type} onValueChange={setType}>
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {INTERACTION_TYPES.map((ty) => (
                  <SelectItem key={ty} value={ty}>
                    {t(`interaction.type.${ty}`, { ns: 'customer' })}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label htmlFor="quicklog-remarks">{t('interaction.remarks', { ns: 'customer' })}</Label>
            <Textarea id="quicklog-remarks" value={remarks} onChange={(e) => setRemarks(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="quicklog-next">{t('interaction.nextFollowUpDate', { ns: 'customer' })}</Label>
            <Input id="quicklog-next" type="date" value={nextDate} onChange={(e) => setNextDate(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label>{t('interaction.resultLabel', { ns: 'customer' })}</Label>
            <Select value={result} onValueChange={setResult}>
              <SelectTrigger className="w-full">
                <SelectValue placeholder="—" />
              </SelectTrigger>
              <SelectContent>
                {INTERACTION_RESULTS.map((r) => (
                  <SelectItem key={r} value={r}>
                    {t(`interaction.result.${r}`, { ns: 'customer' })}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          {mutation.isError && <FormError message={resolveErrorMessage(mutation.error)} />}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('common:actions.cancel')}
          </Button>
          <Button disabled={!remarks || mutation.isPending} onClick={() => mutation.mutate()}>
            {t('quickLog.submit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
