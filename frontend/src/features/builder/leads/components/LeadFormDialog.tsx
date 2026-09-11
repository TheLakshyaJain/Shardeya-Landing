import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { FormError } from '@/components/forms/FormError';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { ApiError } from '@/lib/api/client';
import { createLead } from '../api/leadApi';
import { listProjects } from '@/features/builder/projects/api/projectApi';
import { LEAD_SOURCES } from '../types';

interface LeadFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function LeadFormDialog({ open, onOpenChange }: LeadFormDialogProps) {
  const { t } = useTranslation(['customer', 'common']);
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [fullName, setFullName] = useState('');
  const [mobile, setMobile] = useState('');
  const [budgetMin, setBudgetMin] = useState('');
  const [budgetMax, setBudgetMax] = useState('');
  const [source, setSource] = useState<string>('WALK_IN');
  const [projectId, setProjectId] = useState<string>('');
  const [followUpDate, setFollowUpDate] = useState('');
  const [siteVisitDate, setSiteVisitDate] = useState('');
  const [remarks, setRemarks] = useState('');
  const [allowDuplicate, setAllowDuplicate] = useState(false);

  useEffect(() => {
    if (open) {
      setFullName('');
      setMobile('');
      setBudgetMin('');
      setBudgetMax('');
      setSource('WALK_IN');
      setProjectId('');
      setFollowUpDate('');
      setRemarks('');
      setAllowDuplicate(false);
    }
  }, [open]);

  const projectsQuery = useQuery({ queryKey: ['projects-for-lead-picker'], queryFn: () => listProjects(undefined, 100), enabled: open });

  const mutation = useMutation({
    mutationFn: () =>
      createLead({
        fullName,
        mobile,
        budgetMin: Number(budgetMin || 0),
        budgetMax: Number(budgetMax || 0),
        source: source as never,
        interestedProjectId: projectId || undefined,
        followUpDate: followUpDate || undefined,
        siteVisitDate: siteVisitDate || undefined,
        remarks: remarks || undefined,
        allowDuplicate,
      }),
    onSuccess: (lead) => {
      queryClient.invalidateQueries({ queryKey: ['leads'] });
      // A follow-up date on create upserts a FOLLOW_UP calendar event
      // server-side -- the Calendar page's cache has no way to know.
      queryClient.invalidateQueries({ queryKey: ['calendar'] });
      onOpenChange(false);
      navigate(`/builder/leads/${lead.id}`);
    },
  });

  const duplicateParams = mutation.error instanceof ApiError ? mutation.error.errors[0]?.params : null;
  const isDuplicate = Boolean(mutation.error instanceof ApiError && mutation.error.status === 409 && duplicateParams?.name);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{t('form.createTitle')}</DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
          <div className="space-y-2">
            <Label htmlFor="lead-fullName">{t('form.fullName')}</Label>
            <Input id="lead-fullName" value={fullName} onChange={(e) => setFullName(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="lead-mobile">{t('form.mobile')}</Label>
            <Input id="lead-mobile" value={mobile} onChange={(e) => setMobile(e.target.value)} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <Label htmlFor="lead-budgetMin">{t('form.budgetMin')}</Label>
              <Input id="lead-budgetMin" type="number" value={budgetMin} onChange={(e) => setBudgetMin(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="lead-budgetMax">{t('form.budgetMax')}</Label>
              <Input id="lead-budgetMax" type="number" value={budgetMax} onChange={(e) => setBudgetMax(e.target.value)} />
            </div>
          </div>
          <div className="space-y-2">
            <Label>{t('form.source')}</Label>
            <Select value={source} onValueChange={setSource}>
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {LEAD_SOURCES.map((s) => (
                  <SelectItem key={s} value={s}>
                    {t(`source.${s}`)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label>{t('form.project')}</Label>
            <Select value={projectId} onValueChange={setProjectId}>
              <SelectTrigger className="w-full">
                <SelectValue placeholder="—" />
              </SelectTrigger>
              <SelectContent>
                {(projectsQuery.data?.items ?? []).map((p) => (
                  <SelectItem key={p.id} value={p.id}>
                    {p.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label htmlFor="lead-followUp">{t('form.followUpDate')}</Label>
            <Input id="lead-followUp" type="date" value={followUpDate} onChange={(e) => setFollowUpDate(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="lead-siteVisit">{t('form.siteVisitDate')}</Label>
            <Input id="lead-siteVisit" type="date" value={siteVisitDate} onChange={(e) => setSiteVisitDate(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="lead-remarks">{t('form.remarks')}</Label>
            <Textarea id="lead-remarks" value={remarks} onChange={(e) => setRemarks(e.target.value)} />
          </div>

          {isDuplicate && (
            <div className="rounded-md border border-destructive/30 bg-destructive/10 p-2 text-sm">
              <p className="mb-2">{t('form.duplicateFound', { name: duplicateParams?.name, status: duplicateParams?.status })}</p>
              <div className="flex gap-2">
                <Button size="sm" variant="outline" onClick={() => navigate(`/builder/leads/${duplicateParams?.id}`)}>
                  {t('form.openExisting')}
                </Button>
                <Button size="sm" variant="outline" onClick={() => setAllowDuplicate(true)}>
                  {t('form.createAnyway')}
                </Button>
              </div>
            </div>
          )}
          {mutation.isError && !isDuplicate && <FormError message={resolveErrorMessage(mutation.error)} />}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('common:actions.cancel')}
          </Button>
          <Button disabled={!fullName || !mobile || !budgetMin || !budgetMax || mutation.isPending} onClick={() => mutation.mutate()}>
            {mutation.isPending ? t('form.submitting') : t('form.submit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
