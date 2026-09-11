import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { Download } from 'lucide-react';
import { exportReport } from '../api/reportApi';

interface ExportButtonProps {
  code: string;
  filters: Record<string, string>;
  disabled?: boolean;
}

// M-10 §6 ExportButton: "dropdown: Excel / CSV / PDF" -- PDF export is not
// built this round (report PDFs are a distinct concern from B-11's legal
// documents and weren't in this milestone's own verification list; see
// CLAUDE.md). "All records vs Filtered only" (§3.5) is also trimmed --
// every export here is always the currently-filtered view, matching
// ReportService.export()'s own scope=FILTERED default.
export function ExportButton({ code, filters, disabled }: ExportButtonProps) {
  const { t } = useTranslation('report');
  const [exporting, setExporting] = useState(false);

  async function handle(format: 'XLSX' | 'CSV') {
    setExporting(true);
    try {
      await exportReport(code, filters, format);
    } finally {
      setExporting(false);
    }
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="outline" disabled={disabled || exporting}>
          <Download className="mr-1 size-4" />
          {exporting ? t('export.exporting') : t('export.label')}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent>
        <DropdownMenuItem onClick={() => handle('XLSX')}>{t('export.excel')}</DropdownMenuItem>
        <DropdownMenuItem onClick={() => handle('CSV')}>{t('export.csv')}</DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
