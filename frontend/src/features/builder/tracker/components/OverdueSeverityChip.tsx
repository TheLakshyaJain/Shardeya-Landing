import { Badge } from '@/components/ui/badge';

interface OverdueSeverityChipProps {
  daysOverdue: number;
}

// B-13 §6: 1-7 days amber, 8-30 orange, 30+ red.
export function OverdueSeverityChip({ daysOverdue }: OverdueSeverityChipProps) {
  if (daysOverdue <= 0) return <span>—</span>;
  const className =
    daysOverdue <= 7
      ? 'bg-amber-100 text-amber-800 border-amber-300'
      : daysOverdue <= 30
        ? 'bg-orange-100 text-orange-800 border-orange-300'
        : 'bg-red-100 text-red-800 border-red-300';
  return (
    <Badge variant="outline" className={className}>
      {daysOverdue}
    </Badge>
  );
}
