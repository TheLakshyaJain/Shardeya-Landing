import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Card, CardContent } from '@/components/ui/card';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Checkbox } from '@/components/ui/checkbox';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { FormError } from '@/components/forms/FormError';
import { getPreferenceMatrix, updatePreferenceMatrix } from '../api/settingsApi';
import type { NotificationPreferenceRow, NotificationPreferenceUpdateRequest } from '../types';

type Channel = 'inApp' | 'whatsapp' | 'sms' | 'email';
const CHANNELS: Channel[] = ['inApp', 'whatsapp', 'sms', 'email'];

function toEditState(rows: NotificationPreferenceRow[]): Record<string, NotificationPreferenceUpdateRequest> {
  return Object.fromEntries(
    rows.map((r) => [r.typeCode, { typeCode: r.typeCode, inApp: r.inApp, whatsapp: r.whatsapp, sms: r.sms, email: r.email }]),
  );
}

export function NotificationPreferenceMatrix() {
  const { t } = useTranslation('settings');
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: ['notification-preference-matrix'], queryFn: getPreferenceMatrix });

  const [edits, setEdits] = useState<Record<string, NotificationPreferenceUpdateRequest>>({});
  // Same "touched" guard GridSizeDialog already established (CLAUDE.md M6
  // notes): without it, a slow-resolving fetch that completes after the
  // user has already flipped a checkbox would silently overwrite their
  // in-progress edit back to the server's stale value.
  const touched = useRef(false);
  useEffect(() => {
    if (query.data && !touched.current) {
      setEdits(toEditState(query.data));
    }
  }, [query.data]);

  const saveMutation = useMutation({
    mutationFn: () => updatePreferenceMatrix(Object.values(edits)),
    onSuccess: () => {
      toast.success(t('notifications.saved'));
      queryClient.invalidateQueries({ queryKey: ['notification-preference-matrix'] });
    },
  });

  function toggle(typeCode: string, channel: Channel, mandatory: boolean) {
    if (channel === 'inApp' && mandatory) return;
    touched.current = true;
    setEdits((prev) => ({ ...prev, [typeCode]: { ...prev[typeCode], [channel]: !prev[typeCode]?.[channel] } }));
  }

  if (query.isLoading) {
    return <Skeleton className="h-64 w-full" />;
  }
  if (query.isError) {
    return <FormError message={t('notifications.loadError')} />;
  }

  const rows = query.data ?? [];
  const byCategory = new Map<string, NotificationPreferenceRow[]>();
  for (const row of rows) {
    const list = byCategory.get(row.category) ?? [];
    list.push(row);
    byCategory.set(row.category, list);
  }

  return (
    <Card>
      {/* No CardHeader here -- NotificationSettingsPage's own PageHeader
          already renders this exact title/subtitle; a second copy inside
          the card duplicated the text verbatim (caught in a real Hindi/
          360px screenshot, not by any automated check -- overflow and
          raw-key scans don't catch a title rendered twice). */}
      <CardContent className="space-y-6">
        {[...byCategory.entries()].map(([category, categoryRows]) => (
          <div key={category} className="space-y-2">
            <h3 className="text-sm font-medium text-muted-foreground">
              {t(`notifications.categories.${category}`, { defaultValue: category })}
            </h3>
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('notifications.columns.type')}</TableHead>
                    <TableHead className="text-center">{t('notifications.columns.inApp')}</TableHead>
                    <TableHead className="text-center">{t('notifications.columns.whatsapp')}</TableHead>
                    <TableHead className="text-center">{t('notifications.columns.sms')}</TableHead>
                    <TableHead className="text-center">{t('notifications.columns.email')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {categoryRows.map((row) => {
                    const edit = edits[row.typeCode];
                    if (!edit) return null;
                    return (
                      <TableRow key={row.typeCode}>
                        <TableCell>
                          {t(`notifications.types.${row.typeCode}`, { defaultValue: row.typeCode })}
                          {row.mandatory && (
                            <span className="ml-2 text-xs text-muted-foreground">({t('notifications.mandatoryHint')})</span>
                          )}
                        </TableCell>
                        {CHANNELS.map((channel) => (
                          <TableCell key={channel} className="text-center">
                            <Checkbox
                              checked={edit[channel]}
                              disabled={channel === 'inApp' && row.mandatory}
                              onCheckedChange={() => toggle(row.typeCode, channel, row.mandatory)}
                              aria-label={`${t(`notifications.types.${row.typeCode}`, { defaultValue: row.typeCode })} - ${t(`notifications.columns.${channel}`)}`}
                            />
                          </TableCell>
                        ))}
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </div>
          </div>
        ))}
        <FormError message={saveMutation.isError ? t('notifications.loadError') : null} />
        <Button onClick={() => saveMutation.mutate()} disabled={saveMutation.isPending}>
          {t('notifications.save')}
        </Button>
      </CardContent>
    </Card>
  );
}
