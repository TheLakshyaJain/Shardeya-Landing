import { cn } from '@/lib/utils';
import type { OrgType } from '../types';

interface RoleSelectCardProps {
  value: OrgType;
  label: string;
  description: string;
  selected: boolean;
  onSelect: (value: OrgType) => void;
}

export function RoleSelectCard({ value, label, description, selected, onSelect }: RoleSelectCardProps) {
  return (
    <button
      type="button"
      onClick={() => onSelect(value)}
      aria-pressed={selected}
      className={cn(
        'flex w-full flex-col items-start gap-1 rounded-lg border p-4 text-left transition-colors',
        selected ? 'border-primary bg-primary/5 ring-1 ring-primary' : 'border-border hover:bg-accent',
      )}
    >
      <span className="text-sm font-semibold text-foreground">{label}</span>
      <span className="text-xs text-muted-foreground">{description}</span>
    </button>
  );
}
