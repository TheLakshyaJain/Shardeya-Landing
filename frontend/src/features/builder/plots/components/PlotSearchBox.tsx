import { Search } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Input } from '@/components/ui/input';

interface PlotSearchBoxProps {
  value: string;
  onChange: (value: string) => void;
}

export function PlotSearchBox({ value, onChange }: PlotSearchBoxProps) {
  const { t } = useTranslation('plot');
  return (
    <div className="relative max-w-xs flex-1">
      <Search className="absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
      <Input
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={t('filter.searchPlaceholder')}
        className="pl-8"
      />
    </div>
  );
}
