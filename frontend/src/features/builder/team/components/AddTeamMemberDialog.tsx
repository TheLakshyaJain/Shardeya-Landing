import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Checkbox } from '@/components/ui/checkbox';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { FormError } from '@/components/forms/FormError';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { createTeamMember, getRolePermissions } from '../api/teamApi';
import { listProjects } from '@/features/builder/projects/api/projectApi';
import { STAFF_ROLE_CODES } from '../types';

interface AddTeamMemberDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function AddTeamMemberDialog({ open, onOpenChange }: AddTeamMemberDialogProps) {
  const { t } = useTranslation(['team', 'common']);
  const queryClient = useQueryClient();
  const [fullName, setFullName] = useState('');
  const [mobile, setMobile] = useState('');
  const [email, setEmail] = useState('');
  const [roleCode, setRoleCode] = useState<string>('SALES_EXECUTIVE');
  const [scoped, setScoped] = useState(false);
  const [selectedProjects, setSelectedProjects] = useState<string[]>([]);
  const [sendInvite, setSendInvite] = useState(true);

  useEffect(() => {
    if (open) {
      setFullName('');
      setMobile('');
      setEmail('');
      setRoleCode('SALES_EXECUTIVE');
      setScoped(false);
      setSelectedProjects([]);
      setSendInvite(true);
    }
  }, [open]);

  const projectsQuery = useQuery({
    queryKey: ['projects-for-access-picker'],
    queryFn: () => listProjects(undefined, 100),
    enabled: open,
  });

  const permissionsQuery = useQuery({
    queryKey: ['role-permissions', roleCode],
    queryFn: () => getRolePermissions(roleCode),
    enabled: open,
  });

  const permissionLabels = useMemo(() => {
    const perms = permissionsQuery.data ?? [];
    if (!perms.some((p) => p.startsWith('FINANCIAL_'))) {
      return [...perms.map((p) => t(`form.permissionPreview.${p}`, { ns: 'team', defaultValue: p })), t('form.permissionPreview.noFinancialAccess')];
    }
    return perms.map((p) => t(`form.permissionPreview.${p}`, { ns: 'team', defaultValue: p }));
  }, [permissionsQuery.data, t]);

  const mutation = useMutation({
    mutationFn: () =>
      createTeamMember({
        fullName,
        mobile,
        email: email || undefined,
        roleCode,
        projectAccess: scoped ? selectedProjects : undefined,
        sendInvite,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['team'] });
      onOpenChange(false);
    },
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{t('form.createTitle')}</DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
          <div className="space-y-2">
            <Label htmlFor="team-fullName">{t('form.fullName')}</Label>
            <Input id="team-fullName" value={fullName} onChange={(e) => setFullName(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="team-mobile">{t('form.mobile')}</Label>
            <Input id="team-mobile" value={mobile} onChange={(e) => setMobile(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="team-email">{t('form.email')}</Label>
            <Input id="team-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label>{t('form.role')}</Label>
            <Select value={roleCode} onValueChange={setRoleCode}>
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {STAFF_ROLE_CODES.map((code) => (
                  <SelectItem key={code} value={code}>
                    {t(`role.${code}`, { ns: 'team' })}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {/* RoleSelector's inline permission-matrix preview (B-12 §6): the
              admin sees exactly what they're granting before saving. */}
          <div className="rounded-md border border-border bg-muted/30 p-2 text-sm">
            <p className="mb-1 font-medium">{t('form.permissionPreview.title')}</p>
            <ul className="list-inside list-disc space-y-0.5">
              {permissionLabels.map((label) => (
                <li key={label}>{label}</li>
              ))}
            </ul>
          </div>

          <div className="space-y-2">
            <Label>{t('form.projectAccess')}</Label>
            <div className="flex items-center gap-4">
              <label className="flex items-center gap-2 text-sm">
                <input type="radio" checked={!scoped} onChange={() => setScoped(false)} />
                {t('form.allProjects')}
              </label>
              <label className="flex items-center gap-2 text-sm">
                <input type="radio" checked={scoped} onChange={() => setScoped(true)} />
                {t('form.scopedProjects')}
              </label>
            </div>
            {scoped && (
              <div className="max-h-40 space-y-1 overflow-y-auto rounded-md border border-border p-2">
                {(projectsQuery.data?.items ?? []).map((p) => (
                  <label key={p.id} className="flex items-center gap-2 text-sm">
                    <Checkbox
                      checked={selectedProjects.includes(p.id)}
                      onCheckedChange={(checked) =>
                        setSelectedProjects((prev) => (checked ? [...prev, p.id] : prev.filter((id) => id !== p.id)))
                      }
                    />
                    {p.name}
                  </label>
                ))}
              </div>
            )}
          </div>

          <label className="flex items-center gap-2 text-sm">
            <Checkbox checked={sendInvite} onCheckedChange={(checked) => setSendInvite(Boolean(checked))} />
            {t('form.sendInvite')}
          </label>

          {mutation.isError && <FormError message={resolveErrorMessage(mutation.error)} />}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('common:actions.cancel')}
          </Button>
          <Button
            disabled={!fullName || !mobile || mutation.isPending}
            onClick={() => mutation.mutate()}
          >
            {mutation.isPending ? t('form.submitting') : t('form.submit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
