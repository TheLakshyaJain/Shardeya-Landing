import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Phone, MessageCircle } from 'lucide-react';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { OverdueSeverityChip } from './OverdueSeverityChip';
import { QuickLogDialog } from './QuickLogDialog';
import { markFollowUpDone } from '../api/trackerApi';
import type { FollowUpRow } from '../types';

interface FollowUpTableProps {
  rows: FollowUpRow[];
  canEdit: boolean;
}

// B-13 §19.1 columns: Follow-up Date · Customer Name + contact · Project
// Interested · Assigned To · Status · Last Remark · Actions: Log Follow-up /
// Reschedule / Mark Done. Reschedule is folded into "Log Follow-up" here
// (setting a new next-date IS the reschedule, per InteractionService's own
// single write path -- a separate dialog would just duplicate the same form).
export function FollowUpTable({ rows, canEdit }: FollowUpTableProps) {
  const { t } = useTranslation(['tracker', 'customer', 'common']);
  const queryClient = useQueryClient();
  const [logging, setLogging] = useState<string | null>(null);

  const markDoneMutation = useMutation({
    mutationFn: (customerId: string) => markFollowUpDone(customerId, t('quickLog.markDoneRemark')),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tracker-followups'] });
      queryClient.invalidateQueries({ queryKey: ['tracker-counts'] });
    },
  });

  return (
    <>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t('followUp.columns.date')}</TableHead>
            <TableHead>{t('followUp.columns.customer')}</TableHead>
            <TableHead>{t('followUp.columns.project')}</TableHead>
            <TableHead>{t('followUp.columns.assignedTo')}</TableHead>
            <TableHead>{t('followUp.columns.status')}</TableHead>
            <TableHead>{t('followUp.columns.lastRemark')}</TableHead>
            {canEdit && <TableHead>{t('followUp.columns.actions')}</TableHead>}
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((r) => (
            <TableRow key={r.customerId}>
              <TableCell>
                {r.followUpDate}
                {r.daysOverdue > 0 && <OverdueSeverityChip daysOverdue={r.daysOverdue} />}
              </TableCell>
              <TableCell>
                {r.customerName}
                <div className="text-xs text-muted-foreground">{r.customerMobile}</div>
              </TableCell>
              <TableCell>{r.projectName ?? '—'}</TableCell>
              <TableCell>{r.assignedToName ?? '—'}</TableCell>
              <TableCell>
                <Badge variant="outline">{t(`status.${r.status}`, { ns: 'customer' })}</Badge>
              </TableCell>
              <TableCell className="max-w-48 truncate">{r.lastRemark ?? '—'}</TableCell>
              {canEdit && (
                <TableCell className="space-x-1 whitespace-nowrap">
                  <Button size="icon" variant="ghost" asChild aria-label={t('actions.call')}>
                    <a href={`tel:${r.customerMobile}`}>
                      <Phone className="size-4" />
                    </a>
                  </Button>
                  <Button size="icon" variant="ghost" asChild aria-label={t('actions.whatsapp')}>
                    <a href={`https://wa.me/91${r.customerMobile}`} target="_blank" rel="noreferrer">
                      <MessageCircle className="size-4" />
                    </a>
                  </Button>
                  <Button size="sm" onClick={() => setLogging(r.customerId)}>
                    {t('followUp.logFollowUp')}
                  </Button>
                  <Button size="sm" variant="outline" disabled={markDoneMutation.isPending} onClick={() => markDoneMutation.mutate(r.customerId)}>
                    {t('followUp.markDone')}
                  </Button>
                </TableCell>
              )}
            </TableRow>
          ))}
        </TableBody>
      </Table>
      <QuickLogDialog customerId={logging} onOpenChange={(open) => !open && setLogging(null)} />
    </>
  );
}
