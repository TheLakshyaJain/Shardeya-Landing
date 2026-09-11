import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { FormError } from '@/components/forms/FormError';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { deactivateTeamMember, listTeam } from '../api/teamApi';
import type { TeamMemberResponse } from '../types';

interface DeactivateDialogProps {
  member: TeamMemberResponse | null;
  onOpenChange: (open: boolean) => void;
}

// B-12 §7: deactivating someone with assigned leads requires an explicit
// reassignment choice -- leads silently vanishing into an inactive user's
// account is a real, expensive failure. We don't know up front whether they
// have any leads (the backend enforces this, not a separate precheck call),
// so the reassignment picker is always offered and only becomes load-bearing
// if the deactivate call comes back with the "reassignment required" conflict.
export function DeactivateDialog({ member, onOpenChange }: DeactivateDialogProps) {
  const { t } = useTranslation(['team', 'common']);
  const queryClient = useQueryClient();
  const [reason, setReason] = useState('');
  const [reassignTo, setReassignTo] = useState<string>('');
  const [leaveUnassigned, setLeaveUnassigned] = useState(false);

  const teamQuery = useQuery({ queryKey: ['team'], queryFn: () => listTeam(), enabled: !!member });
  const otherActiveMembers = (teamQuery.data ?? []).filter((m) => m.id !== member?.id && m.status === 'ACTIVE');

  const mutation = useMutation({
    mutationFn: () =>
      deactivateTeamMember(member!.id, {
        reason: reason || undefined,
        reassignToUserId: leaveUnassigned ? undefined : reassignTo || undefined,
        leaveUnassigned,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['team'] });
      onOpenChange(false);
      setReason('');
      setReassignTo('');
      setLeaveUnassigned(false);
    },
  });

  return (
    <Dialog open={!!member} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('deactivate.title', { name: member?.fullName })}</DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
          <p className="text-sm text-muted-foreground">{t('deactivate.description')}</p>
          <div className="space-y-2">
            <Label htmlFor="deactivate-reason">{t('deactivate.reason')}</Label>
            <Textarea id="deactivate-reason" value={reason} onChange={(e) => setReason(e.target.value)} />
          </div>

          <div className="rounded-md border border-border bg-muted/30 p-2 text-sm">
            <p className="mb-2">{t('deactivate.reassignPrompt')}</p>
            <Select value={leaveUnassigned ? '__unassigned__' : reassignTo} onValueChange={(v) => {
              if (v === '__unassigned__') {
                setLeaveUnassigned(true);
                setReassignTo('');
              } else {
                setLeaveUnassigned(false);
                setReassignTo(v);
              }
            }}>
              <SelectTrigger className="w-full">
                <SelectValue placeholder={t('deactivate.reassignTo')} />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="__unassigned__">{t('deactivate.leaveUnassigned')}</SelectItem>
                {otherActiveMembers.map((m) => (
                  <SelectItem key={m.id} value={m.id}>
                    {m.fullName}
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
          <Button variant="destructive" disabled={mutation.isPending} onClick={() => mutation.mutate()}>
            {t('deactivate.confirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
