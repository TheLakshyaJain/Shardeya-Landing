import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Users, Plus } from 'lucide-react';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/data/EmptyState';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { useCan } from '@/hooks/useCan';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { listTeam, reactivateTeamMember, removeTeamMember, resendInvite, resetTeamMemberPassword } from '../api/teamApi';
import { AddTeamMemberDialog } from '../components/AddTeamMemberDialog';
import { DeactivateDialog } from '../components/DeactivateDialog';
import type { TeamMemberResponse } from '../types';

export function TeamListPage() {
  const { t } = useTranslation(['team', 'common']);
  const queryClient = useQueryClient();
  const canManage = useCan('TEAM_MANAGE');
  const [addOpen, setAddOpen] = useState(false);
  const [deactivating, setDeactivating] = useState<TeamMemberResponse | null>(null);

  const query = useQuery({ queryKey: ['team'], queryFn: () => listTeam() });

  const reactivateMutation = useMutation({
    mutationFn: reactivateTeamMember,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['team'] }),
  });
  const removeMutation = useMutation({
    mutationFn: (id: string) => removeTeamMember(id, { leaveUnassigned: true }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['team'] }),
  });
  const resendMutation = useMutation({ mutationFn: resendInvite });
  const resetMutation = useMutation({ mutationFn: resetTeamMemberPassword });

  const members = query.data ?? [];

  return (
    <div>
      <PageHeader
        title={t('list.title')}
        actions={
          canManage && (
            <Button onClick={() => setAddOpen(true)}>
              <Plus className="size-4" />
              {t('list.addMember')}
            </Button>
          )
        }
      />

      {query.isLoading ? null : query.isError ? (
        <p className="text-sm text-destructive">{resolveErrorMessage(query.error)}</p>
      ) : members.length === 0 ? (
        <EmptyState icon={<Users className="size-10" />} title={t('list.empty')} />
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t('list.columns.name')}</TableHead>
              <TableHead>{t('list.columns.contact')}</TableHead>
              <TableHead>{t('list.columns.role')}</TableHead>
              <TableHead>{t('list.columns.status')}</TableHead>
              {canManage && <TableHead>{t('list.columns.actions')}</TableHead>}
            </TableRow>
          </TableHeader>
          <TableBody>
            {members.map((m) => (
              <TableRow key={m.id}>
                <TableCell>
                  {m.fullName}
                  {m.owner && <Badge variant="secondary" className="ml-2">{t('role.BUILDER_ADMIN')}</Badge>}
                </TableCell>
                <TableCell>
                  {m.mobile}
                  {m.email && <div className="text-xs text-muted-foreground">{m.email}</div>}
                </TableCell>
                <TableCell>{t(`role.${m.roleCode}`)}</TableCell>
                <TableCell>
                  <Badge variant={m.status === 'ACTIVE' ? 'default' : 'outline'}>{t(`status.${m.status}`)}</Badge>
                  {m.inviteStatus && m.inviteStatus !== 'ACCEPTED' && (
                    <div className="mt-1 text-xs text-muted-foreground">{t(`inviteStatus.${m.inviteStatus}`)}</div>
                  )}
                </TableCell>
                {canManage && (
                  <TableCell>
                    {!m.owner && (
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="outline" size="sm">
                            {t('list.columns.actions')}
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent>
                          {m.status === 'ACTIVE' && (
                            <DropdownMenuItem onClick={() => setDeactivating(m)}>{t('actions.deactivate')}</DropdownMenuItem>
                          )}
                          {m.status === 'INACTIVE' && (
                            <DropdownMenuItem onClick={() => reactivateMutation.mutate(m.id)}>
                              {t('actions.reactivate')}
                            </DropdownMenuItem>
                          )}
                          {m.status === 'INVITED' && (
                            <DropdownMenuItem onClick={() => resendMutation.mutate(m.id)}>
                              {t('actions.resendInvite')}
                            </DropdownMenuItem>
                          )}
                          {m.email && (
                            <DropdownMenuItem onClick={() => resetMutation.mutate(m.id)}>
                              {t('actions.resetPassword')}
                            </DropdownMenuItem>
                          )}
                          <DropdownMenuItem onClick={() => removeMutation.mutate(m.id)} className="text-destructive">
                            {t('actions.remove')}
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    )}
                  </TableCell>
                )}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <AddTeamMemberDialog open={addOpen} onOpenChange={setAddOpen} />
      <DeactivateDialog member={deactivating} onOpenChange={(open) => !open && setDeactivating(null)} />
    </div>
  );
}
