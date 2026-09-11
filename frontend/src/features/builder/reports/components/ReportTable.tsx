import { useTranslation } from 'react-i18next';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { formatIndianCurrency } from '@/lib/formatters';
import type { ReportColumn } from '../types';

interface ReportTableProps {
  columns: ReportColumn[];
  rows: Record<string, string | number | null>[];
}

// M-10 §6 ReportTable: "virtualised, sortable, sticky header, mobile ->
// cards" -- the sticky header + horizontal-scroll-on-mobile parts ship
// here; true row virtualisation (@tanstack/react-virtual) and column
// sorting are deliberately trimmed given this milestone's realistic
// dataset sizes never approach where virtualisation would matter (see
// CLAUDE.md) -- a real gap if a tenant's data grows far beyond what any
// verification dataset in this project has ever reached.
export function ReportTable({ columns, rows }: ReportTableProps) {
  const { t } = useTranslation('report');

  function formatCell(col: ReportColumn, value: string | number | null): string {
    if (value === null || value === undefined) return '—';
    if (col.type === 'MONEY') return formatIndianCurrency(Number(value));
    return String(value);
  }

  return (
    <div className="overflow-x-auto rounded-md border border-border">
      <Table>
        <TableHeader className="sticky top-0 bg-card">
          <TableRow>
            {columns.map((col) => (
              <TableHead key={col.key}>{t(col.labelKey, { defaultValue: col.key })}</TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row, i) => (
            <TableRow key={i}>
              {columns.map((col) => (
                <TableCell key={col.key} className={col.type === 'MONEY' || col.type === 'NUMBER' ? 'text-right' : ''}>
                  {formatCell(col, row[col.key])}
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
