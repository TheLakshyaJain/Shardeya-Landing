import { useTranslation } from 'react-i18next';
import { Plus, Trash2 } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import { formatIndianCurrency } from '@/lib/formatters';
import type { SalePaymentType, ScheduleRowRequest } from '../types';

interface PaymentPlanBuilderProps {
  rows: ScheduleRowRequest[];
  dealValue: number;
  paymentType: SalePaymentType;
  onChange: (rows: ScheduleRowRequest[]) => void;
}

// B-04 §6: "live total vs deal value reconciliation bar" -- a schedule that
// doesn't sum to the deal value is allowed (builders often leave a final
// amount unscheduled, per §10), the bar just makes the gap visible rather
// than silently hiding it.
//
// LUMP_SUM locks this down to exactly one row, amount fixed to the deal
// value, no add/remove controls -- previously this component rendered the
// full multi-row instalment builder ("Add instalment" and all) regardless
// of paymentType, so a builder could pick "Lump Sum" and still assemble a
// multi-row instalment schedule underneath it; nothing on the backend ties
// paymentType to the schedule's own shape at all (it's stored purely as a
// label), so that combination would have saved silently. A real user
// question ("why do I still see an instalment option under Lump Sum?")
// is what surfaced this.
export function PaymentPlanBuilder({ rows, dealValue, paymentType, onChange }: PaymentPlanBuilderProps) {
  const { t } = useTranslation('sale');
  const total = rows.reduce((sum, r) => sum + (r.amount || 0), 0);

  function updateRow(index: number, patch: Partial<ScheduleRowRequest>) {
    onChange(rows.map((r, i) => (i === index ? { ...r, ...patch } : r)));
  }

  function addRow() {
    onChange([...rows, { label: '', amount: 0, dueDate: '' }]);
  }

  function removeRow(index: number) {
    onChange(rows.filter((_, i) => i !== index));
  }

  if (paymentType === 'LUMP_SUM') {
    const row = rows[0] ?? { label: t('schedule.fullPaymentLabel'), amount: dealValue, dueDate: '' };
    return (
      <div className="space-y-3">
        <div className="grid grid-cols-2 items-end gap-2">
          <div className="space-y-1">
            <Label className="text-xs">{t('form.fields.dealValue')}</Label>
            <Input type="number" value={dealValue || ''} disabled />
          </div>
          <div className="space-y-1">
            <Label className="text-xs">{t('form.fields.purchaseDate')}</Label>
            <Input
              type="date"
              value={row.dueDate}
              onChange={(e) => onChange([{ label: t('schedule.fullPaymentLabel'), amount: dealValue, dueDate: e.target.value }])}
            />
          </div>
        </div>
      </div>
    );
  }

  const reconciliationText =
    total === dealValue
      ? t('schedule.reconciliationMatch', { amount: formatIndianCurrency(total), dealValue: formatIndianCurrency(dealValue) })
      : total < dealValue
        ? t('schedule.reconciliationUnder', {
            amount: formatIndianCurrency(total),
            dealValue: formatIndianCurrency(dealValue),
            remaining: formatIndianCurrency(dealValue - total),
          })
        : t('schedule.reconciliationOver', {
            amount: formatIndianCurrency(total),
            dealValue: formatIndianCurrency(dealValue),
            excess: formatIndianCurrency(total - dealValue),
          });

  return (
    <div className="space-y-3">
      {rows.map((row, index) => (
        <div key={index} className="grid grid-cols-[1fr_1fr_1fr_auto] items-end gap-2">
          <div className="space-y-1">
            <Label className="text-xs">{t('schedule.addRow')} #{index + 1}</Label>
            <Input placeholder={t('schedule.addRow')} value={row.label ?? ''} onChange={(e) => updateRow(index, { label: e.target.value })} />
          </div>
          <div className="space-y-1">
            <Input
              type="number"
              inputMode="decimal"
              min={0}
              value={row.amount || ''}
              onChange={(e) => updateRow(index, { amount: e.target.valueAsNumber || 0 })}
            />
          </div>
          <div className="space-y-1">
            <Input type="date" value={row.dueDate} onChange={(e) => updateRow(index, { dueDate: e.target.value })} />
          </div>
          <Button type="button" size="icon" variant="outline" onClick={() => removeRow(index)}>
            <Trash2 className="size-4" />
          </Button>
        </div>
      ))}
      <Button type="button" variant="outline" size="sm" onClick={addRow}>
        <Plus className="size-4" />
        {t('schedule.addRow')}
      </Button>
      <p
        className={`text-sm font-medium ${total > dealValue ? 'text-destructive' : total === dealValue ? 'text-green-600 dark:text-green-500' : 'text-muted-foreground'}`}
      >
        {reconciliationText}
      </p>
    </div>
  );
}
