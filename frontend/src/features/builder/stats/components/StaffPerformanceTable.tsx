import { useTranslation } from 'react-i18next';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { ChartCard } from './ChartCard';
import { EmptyChartNote } from './EmptyChartNote';
import type { StaffPerformanceRow } from '../types';

export function StaffPerformanceTable({ data }: { data: StaffPerformanceRow[] }) {
  const { t } = useTranslation('stats');

  return (
    <ChartCard title={t('charts.staffPerformance.title')}>
      {data.length === 0 ? (
        <EmptyChartNote />
      ) : (
        <div className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('charts.staffPerformance.columns.staff')}</TableHead>
                <TableHead className="text-right">{t('charts.staffPerformance.columns.leadsHandled')}</TableHead>
                <TableHead className="text-right">{t('charts.staffPerformance.columns.dealsClosed')}</TableHead>
                <TableHead className="text-right">{t('charts.staffPerformance.columns.followUpsLogged')}</TableHead>
                <TableHead className="text-right">{t('charts.staffPerformance.columns.paymentsRecorded')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.map((row) => (
                <TableRow key={row.userId}>
                  <TableCell>{row.staffName}</TableCell>
                  <TableCell className="text-right">{row.leadsHandled}</TableCell>
                  <TableCell className="text-right">{row.dealsClosed}</TableCell>
                  <TableCell className="text-right">{row.followUpsLogged}</TableCell>
                  <TableCell className="text-right">{row.paymentsRecorded}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </ChartCard>
  );
}
