import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { formatIndianCurrency, formatIndianNumber } from '@/lib/formatters';
import { waiveScheduleRow } from '../api/paymentApi';
import { OverdueBadge } from './OverdueBadge';
import type { ScheduleResponse } from '../types';

const STATUS_VARIANT: Record<ScheduleResponse['status'], 'secondary' | 'default' | 'destructive' | 'outline'> = {
  PENDING: 'secondary',
  PARTIALLY_PAID: 'default',
  PAID: 'default',
  OVERDUE: 'destructive',
  WAIVED: 'outline',
};

interface ScheduleTableProps {
  saleId: string;
  schedule: ScheduleResponse[];
  canEdit: boolean;
}

export function ScheduleTable({ saleId, schedule, canEdit }: ScheduleTableProps) {
  const { t } = useTranslation(['payment', 'common']);
  const queryClient = useQueryClient();
  const [waivingId, setWaivingId] = useState<string | null>(null);
  const [waiveReason, setWaiveReason] = useState('');

  const waiveMutation = useMutation({
    mutationFn: (scheduleId: string) => waiveScheduleRow(scheduleId, waiveReason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['schedule', saleId] });
      setWaivingId(null);
      setWaiveReason('');
    },
  });

  return (
    <>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t('schedule.columns.sequence')}</TableHead>
            <TableHead>{t('schedule.columns.label')}</TableHead>
            <TableHead>{t('schedule.columns.amount')}</TableHead>
            <TableHead>{t('schedule.columns.dueDate')}</TableHead>
            <TableHead>{t('schedule.columns.status')}</TableHead>
            <TableHead>{t('schedule.columns.allocated')}</TableHead>
            {canEdit && <TableHead>{t('schedule.columns.actions')}</TableHead>}
          </TableRow>
        </TableHeader>
        <TableBody>
          {schedule.map((row) => (
            <TableRow key={row.id}>
              <TableCell>{row.sequenceNo}</TableCell>
              <TableCell>{row.label || '—'}</TableCell>
              <TableCell>{formatIndianCurrency(row.expectedAmount)}</TableCell>
              <TableCell>{row.dueDate}</TableCell>
              <TableCell className="space-x-1">
                <Badge variant={STATUS_VARIANT[row.status]}>{t(`schedule.status.${row.status}`)}</Badge>
                {row.status === 'OVERDUE' && <OverdueBadge daysOverdue={row.daysOverdue} />}
              </TableCell>
              <TableCell>{formatIndianNumber(row.amountAllocated)}</TableCell>
              {canEdit && (
                <TableCell>
                  {row.status !== 'WAIVED' && row.status !== 'PAID' && (
                    <Button size="sm" variant="outline" onClick={() => setWaivingId(row.id)}>
                      {t('schedule.waive')}
                    </Button>
                  )}
                </TableCell>
              )}
            </TableRow>
          ))}
        </TableBody>
      </Table>

      <Dialog open={!!waivingId} onOpenChange={(open) => !open && setWaivingId(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('schedule.waive')}</DialogTitle>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="waiveReason">{t('schedule.waiveReason')}</Label>
            <Textarea id="waiveReason" value={waiveReason} onChange={(e) => setWaiveReason(e.target.value)} />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setWaivingId(null)}>
              {t('common:actions.cancel')}
            </Button>
            <Button
              disabled={waiveReason.trim().length < 5 || waiveMutation.isPending}
              onClick={() => waivingId && waiveMutation.mutate(waivingId)}
            >
              {t('schedule.waive')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
