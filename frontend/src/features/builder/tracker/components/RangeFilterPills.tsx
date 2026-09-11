import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import type { TrackerRange } from '../types';

interface RangeFilterPillsProps {
  value: TrackerRange;
  onChange: (v: TrackerRange) => void;
}

const RANGES: TrackerRange[] = ['today', 'week', 'overdue', 'all'];

export function RangeFilterPills({ value, onChange }: RangeFilterPillsProps) {
  const { t } = useTranslation('tracker');
  return (
    <div className="mb-3 flex flex-wrap gap-2">
      {RANGES.map((r) => (
        <Button key={r} size="sm" variant={value === r ? 'default' : 'outline'} onClick={() => onChange(r)}>
          {t(`range.${r}`)}
        </Button>
      ))}
    </div>
  );
}
