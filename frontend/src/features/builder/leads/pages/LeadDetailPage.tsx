import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'react-router-dom';
import { PageHeader } from '@/components/layout/PageHeader';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useCan } from '@/hooks/useCan';
import { formatIndianCurrency } from '@/lib/formatters';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { getLead, setImportant, updateLeadStatus, assignLead, updateLead } from '../api/leadApi';
import { listTeam } from '@/features/builder/team/api/teamApi';
import { InteractionTimeline } from '../components/InteractionTimeline';
import { AddInteractionDialog } from '../components/AddInteractionDialog';
import { LEAD_STATUSES } from '../types';

export function LeadDetailPage() {
  const { t } = useTranslation(['customer', 'common']);
  const { id } = useParams<{ id: string }>();
  const queryClient = useQueryClient();
  const canAssign = useCan('LEAD_ASSIGN');
  const canEditAll = useCan('DATA_EDIT_ALL');
  const canEditOwn = useCan('DATA_EDIT_OWN');
  const canEdit = canEditAll || canEditOwn;
  const [interactionOpen, setInteractionOpen] = useState(false);

  const leadQuery = useQuery({ queryKey: ['lead', id], queryFn: () => getLead(id!), enabled: !!id });
  const teamQuery = useQuery({ queryKey: ['team'], queryFn: () => listTeam(), enabled: canAssign });

  const importantMutation = useMutation({
    mutationFn: (important: boolean) => setImportant(id!, important),
    onSuccess: (updated) => queryClient.setQueryData(['lead', id], updated),
  });
  const statusMutation = useMutation({
    mutationFn: (status: string) => updateLeadStatus(id!, status),
    // A status change can create/remove the lead's FOLLOW_UP calendar
    // projection server-side (terminal status = removed) -- the Calendar
    // page's own cached query has no way to know that on its own.
    onSuccess: (updated) => {
      queryClient.setQueryData(['lead', id], updated);
      queryClient.invalidateQueries({ queryKey: ['calendar'] });
    },
  });
  const assignMutation = useMutation({
    mutationFn: (userId: string) => assignLead(id!, userId),
    // Reassignment moves the FOLLOW_UP event's visibility to the new
    // assignee server-side -- same reasoning as statusMutation above.
    onSuccess: (updated) => {
      queryClient.setQueryData(['lead', id], updated);
      queryClient.invalidateQueries({ queryKey: ['calendar'] });
    },
  });
  const siteVisitMutation = useMutation({
    mutationFn: (siteVisitDate: string) => updateLead(id!, { siteVisitDate }),
    // Setting/changing the site visit date upserts the SITE_VISIT calendar
    // projection server-side, same as followUpDate does for FOLLOW_UP.
    onSuccess: (updated) => {
      queryClient.setQueryData(['lead', id], updated);
      queryClient.invalidateQueries({ queryKey: ['calendar'] });
    },
  });

  if (leadQuery.isLoading || !leadQuery.data) {
    return null;
  }
  const lead = leadQuery.data;

  return (
    <div>
      <PageHeader
        title={lead.fullName}
        actions={
          canEdit && (
            <Button
              variant="outline"
              onClick={() => importantMutation.mutate(!lead.important)}
              aria-pressed={lead.important}
            >
              {lead.important ? '★' : '☆'} {t('detail.important')}
            </Button>
          )
        }
      />

      <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div className="space-y-1 text-sm">
          <p>
            <span className="text-muted-foreground">{t('form.mobile')}: </span>
            {lead.mobile}
          </p>
          <p>
            <span className="text-muted-foreground">{t('list.columns.budget')}: </span>
            {formatIndianCurrency(lead.budgetMin)} – {formatIndianCurrency(lead.budgetMax)}
          </p>
          <p>
            <span className="text-muted-foreground">{t('form.source')}: </span>
            {t(`source.${lead.source}`)}
          </p>
          <div className="flex items-center gap-2">
            <Label htmlFor="lead-siteVisitDate" className="text-muted-foreground">
              {t('form.siteVisitDate')}:
            </Label>
            {canEdit ? (
              <Input
                id="lead-siteVisitDate"
                type="date"
                className="h-8 w-40"
                value={lead.siteVisitDate ?? ''}
                onChange={(e) => e.target.value && siteVisitMutation.mutate(e.target.value)}
              />
            ) : (
              <span>{lead.siteVisitDate ?? '—'}</span>
            )}
          </div>
        </div>
        <div className="space-y-2">
          <div className="flex items-center gap-2">
            <span className="text-sm text-muted-foreground">{t('list.columns.status')}:</span>
            {canEdit ? (
              <Select value={lead.status} onValueChange={(v) => statusMutation.mutate(v)}>
                <SelectTrigger className="w-48">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {LEAD_STATUSES.map((s) => (
                    <SelectItem key={s} value={s}>
                      {t(`status.${s}`)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            ) : (
              <Badge variant="outline">{t(`status.${lead.status}`)}</Badge>
            )}
          </div>
          {canAssign && (
            <div className="flex items-center gap-2">
              <span className="text-sm text-muted-foreground">{t('detail.assignTo')}:</span>
              <Select value={lead.assignedTo ?? '__unassigned__'} onValueChange={(v) => assignMutation.mutate(v)}>
                <SelectTrigger className="w-48">
                  <SelectValue placeholder={t('detail.unassigned')} />
                </SelectTrigger>
                <SelectContent>
                  {(teamQuery.data ?? [])
                    .filter((m) => m.status === 'ACTIVE' && m.roleCode !== 'ACCOUNTS_STAFF' && m.roleCode !== 'VIEW_ONLY')
                    .map((m) => (
                      <SelectItem key={m.id} value={m.id}>
                        {m.fullName}
                      </SelectItem>
                    ))}
                </SelectContent>
              </Select>
            </div>
          )}
        </div>
      </div>

      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-base font-semibold">{t('detail.interactions')}</h2>
        {canEdit && (
          <Button size="sm" onClick={() => setInteractionOpen(true)}>
            {t('detail.addInteraction')}
          </Button>
        )}
      </div>
      <InteractionTimeline customerId={lead.id} />

      <AddInteractionDialog customerId={lead.id} open={interactionOpen} onOpenChange={setInteractionOpen} />
    </div>
  );
}
