import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { FormError } from '@/components/forms/FormError';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { formatIndianCurrency } from '@/lib/formatters';
import { listDesignationSlabs, setDesignationOverride } from '../api/brokerApi';

/**
 * 06-BROKER-NETWORK-ENGINE.md §34, build-order step 8. Mirrors
 * CommissionConfigTab's AddCommissionConfigDialog exactly (plain useState,
 * no react-hook-form -- this dialog has only two fields). Admin-only
 * server-side (DesignationPromotionService.manuallyOverride's own
 * requireAdmin()) -- a non-admin who somehow reaches this (BROKER_MANAGE
 * without BUILDER_ADMIN, if that combination is ever granted) gets a real
 * 403 surfaced via FormError, not silently blocked client-side only.
 */
export function DesignationOverrideDialog({
  brokerId,
  open,
  onOpenChange,
}: {
  brokerId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t, i18n } = useTranslation(['broker', 'common']);
  const queryClient = useQueryClient();
  const [designationId, setDesignationId] = useState<string | undefined>(undefined);
  const [reason, setReason] = useState('');

  const slabsQuery = useQuery({ queryKey: ['designation-slabs'], queryFn: listDesignationSlabs, enabled: open });
  const slabs = [...(slabsQuery.data ?? [])].sort((a, b) => a.sortOrder - b.sortOrder);

  const mutation = useMutation({
    mutationFn: () => setDesignationOverride(brokerId, { designationId: designationId!, reason }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['broker', brokerId] });
      queryClient.invalidateQueries({ queryKey: ['broker-designation-history', brokerId] });
      queryClient.invalidateQueries({ queryKey: ['network-designation-history'] });
      queryClient.invalidateQueries({ queryKey: ['broker-network'] });
      setDesignationId(undefined);
      setReason('');
      onOpenChange(false);
    },
  });

  const canSubmit = !!designationId && reason.trim().length >= 5;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('designation.overrideDialog.title')}</DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
          <div className="space-y-2">
            <Label htmlFor="override-designation">{t('designation.overrideDialog.designation')}</Label>
            <Select value={designationId} onValueChange={setDesignationId}>
              <SelectTrigger id="override-designation" className="w-full" aria-label={t('designation.overrideDialog.designation')}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {slabs.map((s) => (
                  <SelectItem key={s.id} value={s.id}>
                    {(i18n.language === 'hi' ? (s.nameHi ?? s.name) : s.name)} — {formatIndianCurrency(s.ratePerSqft)}
                    {t('network.perSqft')}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label htmlFor="override-reason">{t('designation.overrideDialog.reason')}</Label>
            <Input
              id="override-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder={t('designation.overrideDialog.reasonPlaceholder')}
            />
          </div>
          {mutation.isError && <FormError message={resolveErrorMessage(mutation.error)} />}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('common:actions.cancel')}
          </Button>
          <Button disabled={!canSubmit || mutation.isPending} onClick={() => mutation.mutate()}>
            {t('designation.overrideDialog.submit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
