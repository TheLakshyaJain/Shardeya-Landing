import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Checkbox } from '@/components/ui/checkbox';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { FormError } from '@/components/forms/FormError';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { createBroker, listBrokerNetwork } from '../api/brokerApi';
import type { CommissionType } from '../types';

// FIXED is retired for new brokers (06-BROKER-NETWORK-ENGINE.md §0) --
// existing FIXED brokers are unaffected (this dialog only ever creates),
// so it's simply never offered as a choice here.
const NEW_BROKER_COMMISSION_TYPES: CommissionType[] = ['PERCENTAGE', 'DESIGNATION'];

interface BrokerFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function BrokerFormDialog({ open, onOpenChange }: BrokerFormDialogProps) {
  const { t, i18n } = useTranslation(['broker', 'common']);
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const [fullName, setFullName] = useState('');
  const [mobile, setMobile] = useState('');
  const [email, setEmail] = useState('');
  const [cityArea, setCityArea] = useState('');
  const [reraNumber, setReraNumber] = useState('');
  const [firmName, setFirmName] = useState('');
  const [commissionType, setCommissionType] = useState<CommissionType>('PERCENTAGE');
  const [commissionPct, setCommissionPct] = useState('');
  const [commissionFixed, setCommissionFixed] = useState('');
  const [perProjectRatesEnabled, setPerProjectRatesEnabled] = useState(false);
  const [bankAccountName, setBankAccountName] = useState('');
  const [bankAccountNumber, setBankAccountNumber] = useState('');
  const [ifsc, setIfsc] = useState('');
  const [upiId, setUpiId] = useState('');
  const [notes, setNotes] = useState('');
  const [uplineBrokerId, setUplineBrokerId] = useState<string>('');

  // §31: upline is always an explicit choice from the real network list --
  // never inferred from anything about the current user. Only fetched
  // (and only matters) when Designation is the selected commission type.
  const networkQuery = useQuery({
    queryKey: ['broker-network'],
    queryFn: listBrokerNetwork,
    enabled: open && commissionType === 'DESIGNATION',
  });

  useEffect(() => {
    if (open) {
      setFullName('');
      setMobile('');
      setEmail('');
      setCityArea('');
      setReraNumber('');
      setFirmName('');
      setCommissionType('PERCENTAGE');
      setCommissionPct('');
      setCommissionFixed('');
      setPerProjectRatesEnabled(false);
      setBankAccountName('');
      setBankAccountNumber('');
      setIfsc('');
      setUpiId('');
      setNotes('');
      setUplineBrokerId('');
    }
  }, [open]);

  const mutation = useMutation({
    mutationFn: () =>
      createBroker({
        fullName,
        mobile,
        email: email || undefined,
        cityArea: cityArea || undefined,
        reraNumber: reraNumber || undefined,
        firmName: firmName || undefined,
        commissionType,
        commissionPct: commissionType === 'PERCENTAGE' && commissionPct !== '' ? Number(commissionPct) : undefined,
        commissionFixed: commissionType === 'FIXED' && commissionFixed !== '' ? Number(commissionFixed) : undefined,
        perProjectRatesEnabled,
        bankAccountName: bankAccountName || undefined,
        bankAccountNumber: bankAccountNumber || undefined,
        ifsc: ifsc || undefined,
        upiId: upiId || undefined,
        notes: notes || undefined,
        uplineBrokerId: commissionType === 'DESIGNATION' && uplineBrokerId !== '' ? uplineBrokerId : undefined,
      }),
    onSuccess: (broker) => {
      queryClient.invalidateQueries({ queryKey: ['brokers'] });
      queryClient.invalidateQueries({ queryKey: ['broker-network'] });
      onOpenChange(false);
      navigate(`/builder/brokers/${broker.id}`);
    },
  });

  const canSubmit =
    fullName.trim() !== '' &&
    mobile.trim() !== '' &&
    (commissionType === 'PERCENTAGE' ? commissionPct !== '' : commissionType === 'FIXED' ? commissionFixed !== '' : true);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{t('form.createTitle')}</DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
          <div className="space-y-2">
            <Label htmlFor="broker-fullName">{t('form.fullName')}</Label>
            <Input id="broker-fullName" value={fullName} onChange={(e) => setFullName(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="broker-mobile">{t('form.mobile')}</Label>
            <Input id="broker-mobile" value={mobile} onChange={(e) => setMobile(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="broker-email">{t('form.email')}</Label>
            <Input id="broker-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <Label htmlFor="broker-cityArea">{t('form.cityArea')}</Label>
              <Input id="broker-cityArea" value={cityArea} onChange={(e) => setCityArea(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="broker-reraNumber">{t('form.reraNumber')}</Label>
              <Input id="broker-reraNumber" value={reraNumber} onChange={(e) => setReraNumber(e.target.value)} />
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="broker-firmName">{t('form.firmName')}</Label>
            <Input id="broker-firmName" value={firmName} onChange={(e) => setFirmName(e.target.value)} />
          </div>

          <div className="space-y-2">
            <Label htmlFor="broker-commissionType">{t('form.commissionType')}</Label>
            <Select value={commissionType} onValueChange={(v) => setCommissionType(v as CommissionType)}>
              <SelectTrigger id="broker-commissionType" className="w-full" aria-label={t('form.commissionType')}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {NEW_BROKER_COMMISSION_TYPES.map((ct) => (
                  <SelectItem key={ct} value={ct}>
                    {t(`form.commissionType${ct === 'PERCENTAGE' ? 'Percentage' : 'Designation'}`)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          {commissionType === 'PERCENTAGE' && (
            <div className="space-y-2">
              <Label htmlFor="broker-commissionPct">{t('form.commissionPct')}</Label>
              <Input id="broker-commissionPct" type="number" step="0.001" min="0" max="20" value={commissionPct}
                onChange={(e) => setCommissionPct(e.target.value)} />
            </div>
          )}
          {commissionType === 'DESIGNATION' && (
            <div className="space-y-2">
              <Label htmlFor="broker-upline">{t('form.uplineBroker')}</Label>
              <Select value={uplineBrokerId || 'NONE'} onValueChange={(v) => setUplineBrokerId(v === 'NONE' ? '' : v)}>
                <SelectTrigger id="broker-upline" className="w-full" aria-label={t('form.uplineBroker')}>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="NONE">{t('form.uplineNone')}</SelectItem>
                  {networkQuery.data?.map((n) => {
                    // DB-sourced bilingual data, not an i18n key -- same
                    // explicit language check NetworkTreePage uses.
                    const designationLabel = i18n.language === 'hi' ? (n.designationNameHi ?? n.designationName) : n.designationName;
                    return (
                      <SelectItem key={n.id} value={n.id}>
                        {n.fullName} {designationLabel ? `— ${designationLabel}` : ''}
                      </SelectItem>
                    );
                  })}
                </SelectContent>
              </Select>
              <p className="text-xs text-muted-foreground">{t('form.designationStartNote')}</p>
            </div>
          )}

          <label className="flex items-center gap-2 text-sm">
            <Checkbox checked={perProjectRatesEnabled} onCheckedChange={(c) => setPerProjectRatesEnabled(Boolean(c))} />
            {t('form.perProjectRatesEnabled')}
          </label>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <Label htmlFor="broker-bankAccountName">{t('form.bankAccountName')}</Label>
              <Input id="broker-bankAccountName" value={bankAccountName} onChange={(e) => setBankAccountName(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="broker-bankAccountNumber">{t('form.bankAccountNumber')}</Label>
              <Input id="broker-bankAccountNumber" value={bankAccountNumber} onChange={(e) => setBankAccountNumber(e.target.value)} />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <Label htmlFor="broker-ifsc">{t('form.ifsc')}</Label>
              <Input id="broker-ifsc" value={ifsc} onChange={(e) => setIfsc(e.target.value.toUpperCase())} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="broker-upiId">{t('form.upiId')}</Label>
              <Input id="broker-upiId" value={upiId} onChange={(e) => setUpiId(e.target.value)} />
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="broker-notes">{t('form.notes')}</Label>
            <Input id="broker-notes" value={notes} onChange={(e) => setNotes(e.target.value)} />
          </div>

          {mutation.isError && <FormError message={resolveErrorMessage(mutation.error)} />}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('common:actions.cancel')}
          </Button>
          <Button disabled={!canSubmit || mutation.isPending} onClick={() => mutation.mutate()}>
            {mutation.isPending ? t('form.submitting') : t('form.submit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
