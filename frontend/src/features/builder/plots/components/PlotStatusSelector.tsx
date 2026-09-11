import { useTranslation } from 'react-i18next';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { FormError } from '@/components/forms/FormError';
import { PlotStatusBadge } from './PlotStatusBadge';
import type { PlotStatus } from '../types';

interface PlotStatusSelectorProps {
  status: PlotStatus;
  reservedFor: string;
  reservedUntil: string;
  onStatusChange: (status: PlotStatus) => void;
  onReservedForChange: (value: string) => void;
  onReservedUntilChange: (value: string) => void;
  reservedForError?: string;
}

// B-03 §7: status can NEVER be set to SOLD directly here — SOLD only ever
// happens as a side effect of recording a plot_sale (M3). Once a plot IS
// sold, this renders a read-only badge + explanatory note instead of a
// dropdown, rather than silently disabling an option the user might not
// notice is missing.
export function PlotStatusSelector({
  status,
  reservedFor,
  reservedUntil,
  onStatusChange,
  onReservedForChange,
  onReservedUntilChange,
  reservedForError,
}: PlotStatusSelectorProps) {
  const { t } = useTranslation('plot');

  if (status === 'SOLD') {
    return (
      <div className="space-y-2">
        <Label>{t('form.fields.status')}</Label>
        <div className="flex items-center gap-2">
          <PlotStatusBadge status="SOLD" />
        </div>
        <p className="text-xs text-muted-foreground">{t('form.soldReadOnlyNote')}</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="space-y-2">
        <Label>{t('form.fields.status')}</Label>
        <Select value={status} onValueChange={(v) => onStatusChange(v as PlotStatus)}>
          <SelectTrigger className="w-full">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="AVAILABLE">{t('status.AVAILABLE')}</SelectItem>
            <SelectItem value="RESERVED">{t('status.RESERVED')}</SelectItem>
          </SelectContent>
        </Select>
      </div>
      {status === 'RESERVED' && (
        <div className="grid grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label htmlFor="reservedFor">{t('form.fields.reservedFor')}</Label>
            <Input
              id="reservedFor"
              placeholder={t('form.placeholders.reservedFor')}
              value={reservedFor}
              onChange={(e) => onReservedForChange(e.target.value)}
            />
            <FormError message={reservedForError} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="reservedUntil">{t('form.fields.reservedUntil')}</Label>
            <Input
              id="reservedUntil"
              type="date"
              value={reservedUntil}
              onChange={(e) => onReservedUntilChange(e.target.value)}
            />
          </div>
        </div>
      )}
    </div>
  );
}
