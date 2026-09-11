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
import { addInteraction } from '../api/leadApi';
import { INTERACTION_RESULTS, INTERACTION_TYPES } from '../types';

interface AddInteractionDialogProps {
  customerId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function AddInteractionDialog({ customerId, open, onOpenChange }: AddInteractionDialogProps) {
  const { t } = useTranslation(['customer', 'common']);
  const queryClient = useQueryClient();
  const [occurredOn, setOccurredOn] = useState(() => new Date().toISOString().slice(0, 10));
  const [type, setType] = useState<string>('CALL');
  const [remarks, setRemarks] = useState('');
  const [nextFollowUpDate, setNextFollowUpDate] = useState('');
  const [result, setResult] = useState<string>('');

  const mutation = useMutation({
    mutationFn: () =>
      addInteraction(customerId, {
        occurredOn,
        type: type as never,
        remarks,
        nextFollowUpDate: nextFollowUpDate || undefined,
        result: (result || undefined) as never,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['interactions', customerId] });
      queryClient.invalidateQueries({ queryKey: ['lead', customerId] });
      // Logging a follow-up can set/change nextFollowUpDate, which
      // upserts the lead's FOLLOW_UP calendar event server-side.
      queryClient.invalidateQueries({ queryKey: ['calendar'] });
      onOpenChange(false);
      setRemarks('');
      setNextFollowUpDate('');
      setResult('');
    },
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('detail.addInteraction')}</DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
          <div className="space-y-2">
            <Label htmlFor="interaction-occurredOn">{t('interaction.occurredOn')}</Label>
            <Input id="interaction-occurredOn" type="date" value={occurredOn} onChange={(e) => setOccurredOn(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label>{t('interaction.typeLabel')}</Label>
            <Select value={type} onValueChange={setType}>
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {INTERACTION_TYPES.map((ty) => (
                  <SelectItem key={ty} value={ty}>
                    {t(`interaction.type.${ty}`)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label htmlFor="interaction-remarks">{t('interaction.remarks')}</Label>
            <Textarea id="interaction-remarks" value={remarks} onChange={(e) => setRemarks(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="interaction-nextFollowUp">{t('interaction.nextFollowUpDate')}</Label>
            <Input
              id="interaction-nextFollowUp"
              type="date"
              value={nextFollowUpDate}
              onChange={(e) => setNextFollowUpDate(e.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label>{t('interaction.resultLabel')}</Label>
            <Select value={result} onValueChange={setResult}>
              <SelectTrigger className="w-full">
                <SelectValue placeholder="—" />
              </SelectTrigger>
              <SelectContent>
                {INTERACTION_RESULTS.map((r) => (
                  <SelectItem key={r} value={r}>
                    {t(`interaction.result.${r}`)}
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
            {t('interaction.submit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
