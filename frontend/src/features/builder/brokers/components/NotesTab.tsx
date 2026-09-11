import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { FormError } from '@/components/forms/FormError';
import { EmptyState } from '@/components/data/EmptyState';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { addInteraction, listInteractions } from '../api/brokerApi';

export function NotesTab({ brokerId }: { brokerId: string }) {
  const { t } = useTranslation(['broker', 'common']);
  const queryClient = useQueryClient();
  const [addOpen, setAddOpen] = useState(false);
  const [occurredOn, setOccurredOn] = useState(() => new Date().toISOString().slice(0, 10));
  const [remarks, setRemarks] = useState('');
  const [nextFollowUpDate, setNextFollowUpDate] = useState('');

  const interactionsQuery = useQuery({ queryKey: ['broker-interactions', brokerId], queryFn: () => listInteractions(brokerId) });

  const mutation = useMutation({
    mutationFn: () =>
      addInteraction(brokerId, { occurredOn, remarks, nextFollowUpDate: nextFollowUpDate || undefined }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['broker-interactions', brokerId] });
      setAddOpen(false);
      setRemarks('');
      setNextFollowUpDate('');
    },
  });

  const notes = interactionsQuery.data ?? [];

  return (
    <div className="space-y-4">
      <div className="flex justify-between">
        <h3 className="text-sm font-medium">{t('notes.title')}</h3>
        <Button size="sm" onClick={() => setAddOpen(true)}>
          <Plus className="size-4" />
          {t('notes.addNote')}
        </Button>
      </div>

      {notes.length === 0 ? (
        <EmptyState title={t('notes.empty')} />
      ) : (
        <ul className="space-y-3">
          {notes.map((n) => (
            <li key={n.id} className="rounded-md border border-border p-3 text-sm">
              <div className="flex justify-between text-xs text-muted-foreground">
                <span>{n.occurredOn}</span>
                {n.nextFollowUpDate && <span>{t('notes.nextFollowUpDate')}: {n.nextFollowUpDate}</span>}
              </div>
              <p className="mt-1">{n.remarks}</p>
            </li>
          ))}
        </ul>
      )}

      <Dialog open={addOpen} onOpenChange={setAddOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('notes.addNote')}</DialogTitle>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-2">
              <Label htmlFor="note-occurredOn">{t('notes.occurredOn')}</Label>
              <Input id="note-occurredOn" type="date" value={occurredOn} onChange={(e) => setOccurredOn(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="note-remarks">{t('notes.remarks')}</Label>
              <Input id="note-remarks" value={remarks} onChange={(e) => setRemarks(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="note-nextFollowUpDate">{t('notes.nextFollowUpDate')}</Label>
              <Input id="note-nextFollowUpDate" type="date" value={nextFollowUpDate} onChange={(e) => setNextFollowUpDate(e.target.value)} />
            </div>
            {mutation.isError && <FormError message={resolveErrorMessage(mutation.error)} />}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setAddOpen(false)}>
              {t('common:actions.cancel')}
            </Button>
            <Button disabled={!remarks || mutation.isPending} onClick={() => mutation.mutate()}>
              {t('notes.submit')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
