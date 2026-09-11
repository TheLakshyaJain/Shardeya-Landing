import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus, Trash2 } from 'lucide-react';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/data/EmptyState';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { FormError } from '@/components/forms/FormError';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { createTier, deleteTier, listDesignationSlabs, listTiers, recalculateTiers, updateTier } from '../api/brokerApi';
import type { BonusType, BrokerTierResponse } from '../types';

export function TierConfigPage() {
  const { t, i18n } = useTranslation(['broker', 'common']);
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<BrokerTierResponse | 'new' | null>(null);

  const tiersQuery = useQuery({ queryKey: ['broker-tiers'], queryFn: () => listTiers() });
  const slabsQuery = useQuery({ queryKey: ['designation-slabs'], queryFn: listDesignationSlabs });
  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteTier(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['broker-tiers'] }),
  });
  const recalcMutation = useMutation({ mutationFn: recalculateTiers });

  const tiers = [...(tiersQuery.data ?? [])].sort((a, b) => a.sortOrder - b.sortOrder);
  const rateMap = new Map((slabsQuery.data ?? []).map((s) => [s.name, s.ratePerSqft]));

  return (
    <div>
      <PageHeader
        title={t('tier.pageTitle')}
        actions={
          <div className="flex gap-2">
            <Button variant="outline" onClick={() => recalcMutation.mutate()} disabled={recalcMutation.isPending}>
              {t('tier.recalculate')}
            </Button>
            <Button onClick={() => setEditing('new')}>
              <Plus className="size-4" />
              {t('tier.addTier')}
            </Button>
          </div>
        }
      />

      {tiers.length === 0 ? (
        <EmptyState title={t('tier.pageTitle')} />
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t('tier.columns.name')}</TableHead>
              <TableHead>{t('tier.columns.dealsRange')}</TableHead>
              <TableHead>{t('tier.columns.rate')}</TableHead>
              <TableHead>{t('tier.columns.bonus')}</TableHead>
              <TableHead>{t('tier.columns.actions')}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {tiers.map((tier) => {
              const slabRate = rateMap.get(tier.name);
              const effectiveRate = tier.bonusType === 'RATE_PER_SQFT' ? tier.bonusValue : slabRate;
              return (
                <TableRow key={tier.id} className="cursor-pointer" onClick={() => setEditing(tier)}>
                  <TableCell className="font-medium">
                    {(i18n.language === 'hi' ? tier.nameHi : tier.name) || tier.name}
                  </TableCell>
                  <TableCell>
                    {tier.minDeals} – {tier.maxDeals ?? '∞'}
                  </TableCell>
                  <TableCell>
                    {effectiveRate != null ? (
                      <span className="font-medium text-emerald-700 dark:text-emerald-400">₹{effectiveRate}/sq.ft.</span>
                    ) : (
                      '—'
                    )}
                  </TableCell>
                  <TableCell>
                    {tier.bonusType === 'NONE'
                      ? t('tier.bonusTypeNone')
                      : tier.bonusType === 'PCT'
                        ? `${tier.bonusValue}%`
                        : tier.bonusType === 'RATE_PER_SQFT'
                          ? `₹${tier.bonusValue}/sq.ft.`
                          : `₹${tier.bonusValue}`}
                  </TableCell>
                <TableCell>
                  <Button
                    variant="ghost"
                    size="icon"
                    onClick={(e) => {
                      e.stopPropagation();
                      deleteMutation.mutate(tier.id);
                    }}
                  >
                    <Trash2 className="size-4" />
                  </Button>
                </TableCell>
              </TableRow>
            );
          })}
          </TableBody>
        </Table>
      )}

      <TierFormDialog tier={editing === 'new' ? null : editing} open={editing !== null} onOpenChange={(open) => !open && setEditing(null)} />
    </div>
  );
}

function TierFormDialog({
  tier,
  open,
  onOpenChange,
}: {
  tier: BrokerTierResponse | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useTranslation(['broker', 'common']);
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [nameHi, setNameHi] = useState('');
  const [minDeals, setMinDeals] = useState('0');
  const [maxDeals, setMaxDeals] = useState('');
  const [bonusType, setBonusType] = useState<BonusType>('NONE');
  const [bonusValue, setBonusValue] = useState('0');
  const [perksDescription, setPerksDescription] = useState('');

  useEffect(() => {
    if (open) {
      setName(tier?.name ?? '');
      setNameHi(tier?.nameHi ?? '');
      setMinDeals(String(tier?.minDeals ?? 0));
      setMaxDeals(tier?.maxDeals != null ? String(tier.maxDeals) : '');
      setBonusType(tier?.bonusType ?? 'NONE');
      setBonusValue(String(tier?.bonusValue ?? 0));
      setPerksDescription(tier?.perksDescription ?? '');
    }
  }, [open, tier]);

  const mutation = useMutation({
    mutationFn: () => {
      const req = {
        name,
        nameHi: nameHi || undefined,
        minDeals: Number(minDeals),
        maxDeals: maxDeals !== '' ? Number(maxDeals) : undefined,
        bonusType,
        bonusValue: Number(bonusValue),
        perksDescription: perksDescription || undefined,
      };
      return tier ? updateTier(tier.id, req) : createTier(req);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['broker-tiers'] });
      queryClient.invalidateQueries({ queryKey: ['designation-slabs'] });
      queryClient.invalidateQueries({ queryKey: ['brokers'] });
      onOpenChange(false);
    },
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{tier ? t('tier.editTitle') : t('tier.createTitle')}</DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
          <div className="space-y-2">
            <Label htmlFor="tier-name">{t('tier.name')}</Label>
            <Input id="tier-name" value={name} onChange={(e) => setName(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="tier-nameHi">{t('tier.nameHi')}</Label>
            <Input id="tier-nameHi" value={nameHi} onChange={(e) => setNameHi(e.target.value)} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <Label htmlFor="tier-minDeals">{t('tier.minDeals')}</Label>
              <Input id="tier-minDeals" type="number" min="0" value={minDeals} onChange={(e) => setMinDeals(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="tier-maxDeals">{t('tier.maxDeals')}</Label>
              <Input id="tier-maxDeals" type="number" min="0" value={maxDeals} onChange={(e) => setMaxDeals(e.target.value)} />
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="tier-bonusType">{t('tier.bonusType')}</Label>
            <Select value={bonusType} onValueChange={(v) => setBonusType(v as BonusType)}>
              <SelectTrigger id="tier-bonusType" className="w-full" aria-label={t('tier.bonusType')}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="RATE_PER_SQFT">{t('tier.bonusTypeRatePerSqft')}</SelectItem>
                <SelectItem value="NONE">{t('tier.bonusTypeNone')}</SelectItem>
                <SelectItem value="PCT">{t('tier.bonusTypePct')}</SelectItem>
                <SelectItem value="FIXED">{t('tier.bonusTypeFixed')}</SelectItem>
              </SelectContent>
            </Select>
          </div>
          {bonusType !== 'NONE' && (
            <div className="space-y-2">
              <Label htmlFor="tier-bonusValue">
                {bonusType === 'RATE_PER_SQFT'
                  ? t('tier.ratePerSqftLabel')
                  : bonusType === 'PCT'
                    ? t('tier.bonusPctLabel')
                    : t('tier.bonusValue')}
              </Label>
              <Input
                id="tier-bonusValue"
                type="number"
                min="0"
                step={bonusType === 'PCT' ? '0.01' : '1'}
                value={bonusValue}
                onChange={(e) => setBonusValue(e.target.value)}
              />
            </div>
          )}
          <div className="space-y-2">
            <Label htmlFor="tier-perksDescription">{t('tier.perksDescription')}</Label>
            <Input id="tier-perksDescription" value={perksDescription} onChange={(e) => setPerksDescription(e.target.value)} />
          </div>
          {mutation.isError && <FormError message={resolveErrorMessage(mutation.error)} />}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('common:actions.cancel')}
          </Button>
          <Button disabled={!name || mutation.isPending} onClick={() => mutation.mutate()}>
            {t('tier.submit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
