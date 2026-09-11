import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { listInteractions, amendInteraction } from '../api/leadApi';

interface InteractionTimelineProps {
  customerId: string;
}

// M-12 §7: interactions are append-only -- there is no delete button here at
// all (the DB blocks it outright), only Edit within the 15-minute window
// InteractionResponse.amendable already reflects server-side.
export function InteractionTimeline({ customerId }: InteractionTimelineProps) {
  const { t } = useTranslation(['customer', 'common']);
  const queryClient = useQueryClient();
  const [editingId, setEditingId] = useState<string | null>(null);
  const [draft, setDraft] = useState('');

  const query = useQuery({ queryKey: ['interactions', customerId], queryFn: () => listInteractions(customerId) });

  const amendMutation = useMutation({
    mutationFn: ({ id, remarks }: { id: string; remarks: string }) => amendInteraction(id, remarks),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['interactions', customerId] });
      setEditingId(null);
    },
  });

  const items = query.data ?? [];
  if (items.length === 0) {
    return <p className="text-sm text-muted-foreground">{t('list.empty')}</p>;
  }

  return (
    <ul className="space-y-3">
      {items.map((i) => (
        <li key={i.id} className="rounded-md border border-border p-3 text-sm">
          <div className="mb-1 flex items-center justify-between">
            <span className="font-medium">{t(`interaction.type.${i.type}`)}</span>
            <span className="text-xs text-muted-foreground">{i.occurredOn}</span>
          </div>
          {editingId === i.id ? (
            <div className="space-y-2">
              <Textarea value={draft} onChange={(e) => setDraft(e.target.value)} />
              <div className="flex gap-2">
                <Button size="sm" onClick={() => amendMutation.mutate({ id: i.id, remarks: draft })}>
                  {t('common:actions.save', { defaultValue: 'Save' })}
                </Button>
                <Button size="sm" variant="outline" onClick={() => setEditingId(null)}>
                  {t('common:actions.cancel')}
                </Button>
              </div>
            </div>
          ) : (
            <>
              <p>{i.remarks}</p>
              {i.amendedAt && <p className="mt-1 text-xs italic text-muted-foreground">{t('interaction.amendedNote')}</p>}
              {i.result && <p className="mt-1 text-xs text-muted-foreground">{t(`interaction.result.${i.result}`)}</p>}
              {i.amendable && (
                <Button
                  size="sm"
                  variant="ghost"
                  className="mt-1 h-auto p-0 text-xs"
                  onClick={() => {
                    setEditingId(i.id);
                    setDraft(i.remarks);
                  }}
                >
                  {t('interaction.amend')}
                </Button>
              )}
            </>
          )}
        </li>
      ))}
    </ul>
  );
}
