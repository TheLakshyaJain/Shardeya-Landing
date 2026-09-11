import { useTranslation } from 'react-i18next';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import type { GovIdType } from '../types';

const GOV_ID_TYPES: GovIdType[] = ['AADHAAR', 'PAN', 'PASSPORT', 'VOTER_ID', 'DL'];

interface GovIdInputProps {
  type: GovIdType | undefined;
  number: string;
  onTypeChange: (type: GovIdType) => void;
  onNumberChange: (number: string) => void;
}

// Buyer ID is optional at sale-creation time (B-04 doesn't require it up
// front) -- this just captures type + plaintext number, which
// PlotSaleService encrypts server-side before persisting. The number is
// never masked HERE (the user just typed it); masking only applies once
// it's stored and redisplayed via SaleDetailPanel's buyerGovIdLast4.
export function GovIdInput({ type, number, onTypeChange, onNumberChange }: GovIdInputProps) {
  const { t } = useTranslation('sale');

  return (
    <div className="grid grid-cols-2 gap-4">
      <div className="space-y-2">
        <Label>{t('form.fields.govIdType')}</Label>
        <Select value={type} onValueChange={(v) => onTypeChange(v as GovIdType)}>
          <SelectTrigger className="w-full">
            <SelectValue placeholder={t('form.fields.govIdType')} />
          </SelectTrigger>
          <SelectContent>
            {GOV_ID_TYPES.map((t2) => (
              <SelectItem key={t2} value={t2}>
                {t(`govId.types.${t2}`)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="space-y-2">
        <Label htmlFor="govIdNumber">{t('form.fields.govIdNumber')}</Label>
        <Input id="govIdNumber" value={number} onChange={(e) => onNumberChange(e.target.value)} />
      </div>
    </div>
  );
}
