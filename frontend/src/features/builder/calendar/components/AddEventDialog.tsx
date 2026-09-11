import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { FormError } from '@/components/forms/FormError';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { createCalendarEvent } from '../api/calendarApi';

interface AddEventDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function AddEventDialog({ open, onOpenChange }: AddEventDialogProps) {
  const { t } = useTranslation(['calendar', 'common']);
  const queryClient = useQueryClient();
  const [title, setTitle] = useState('');
  const [eventDate, setEventDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [eventTime, setEventTime] = useState('');
  const [importantDate, setImportantDate] = useState(false);
  const [notes, setNotes] = useState('');

  const mutation = useMutation({
    // reminderEnabled has no functional backing today -- nothing reads it
    // to actually send a reminder for a manually-created event (see
    // CLAUDE.md's own note on this) -- so the UI toggle was removed as
    // misleading, but the field itself stays required by the backend
    // contract; hardcoded true matches the column's own default and the
    // checkbox's old default state.
    mutationFn: () =>
      createCalendarEvent({
        title,
        eventDate,
        eventTime: eventTime || undefined,
        importantDate,
        notes: notes || undefined,
        reminderEnabled: true,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['calendar'] });
      onOpenChange(false);
      setTitle('');
      setNotes('');
    },
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('addEvent')}</DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
          <div className="space-y-2">
            <Label htmlFor="event-title">{t('form.title')}</Label>
            <Input id="event-title" value={title} onChange={(e) => setTitle(e.target.value)} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <Label htmlFor="event-date">{t('form.date')}</Label>
              <Input id="event-date" type="date" value={eventDate} onChange={(e) => setEventDate(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="event-time">{t('form.time')}</Label>
              <Input id="event-time" type="time" value={eventTime} onChange={(e) => setEventTime(e.target.value)} />
            </div>
          </div>
          <label className="flex items-center gap-2 text-sm">
            <Checkbox checked={importantDate} onCheckedChange={(c) => setImportantDate(Boolean(c))} />
            {t('form.importantDate')}
          </label>
          <div className="space-y-2">
            <Label htmlFor="event-notes">{t('form.notes')}</Label>
            <Textarea id="event-notes" value={notes} onChange={(e) => setNotes(e.target.value)} />
          </div>
          {mutation.isError && <FormError message={resolveErrorMessage(mutation.error)} />}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('common:actions.cancel')}
          </Button>
          <Button disabled={!title || mutation.isPending} onClick={() => mutation.mutate()}>
            {mutation.isPending ? t('form.submitting') : t('form.submit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
