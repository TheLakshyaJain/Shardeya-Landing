import { useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useNavigate, useParams } from 'react-router-dom';
import { Download, Loader2, Upload } from 'lucide-react';
import { PageHeader } from '@/components/layout/PageHeader';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Checkbox } from '@/components/ui/checkbox';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Input } from '@/components/ui/input';
import { FormError } from '@/components/forms/FormError';
import { resolveErrorMessage, resolveRowError } from '@/lib/api/errorMessage';
import { getProject } from '@/features/builder/projects/api/projectApi';
import { uploadFile } from '@/features/media/api/mediaApi';
import {
  cancelImport,
  commitImport,
  downloadImportTemplate,
  getImportRows,
  patchImportRow,
  startImport,
} from '../api/importApi';
import type { DuplicateMode, ImportRowResponse } from '../types';

const STEP_KEYS = ['download', 'upload', 'preview', 'commit'] as const;

export function ImportWizardPage() {
  const { t } = useTranslation(['import', 'common', 'errors', 'plot']);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { projectId } = useParams<{ projectId: string }>();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [step, setStep] = useState(0);
  const [duplicateMode, setDuplicateMode] = useState<DuplicateMode>('SKIP');
  const [autoPlace, setAutoPlace] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [jobId, setJobId] = useState<string | null>(null);
  const [showOnlyErrors, setShowOnlyErrors] = useState(false);
  const [skipInvalid, setSkipInvalid] = useState(true);
  const [editingRow, setEditingRow] = useState<ImportRowResponse | null>(null);

  const project = useQuery({ queryKey: ['project', projectId], queryFn: () => getProject(projectId!) });

  const rowsQuery = useQuery({
    queryKey: ['import-rows', jobId, showOnlyErrors],
    queryFn: () => getImportRows(jobId!, showOnlyErrors ? 'INVALID' : undefined),
    enabled: !!jobId && step === 2,
  });

  const startMutation = useMutation({
    mutationFn: async (file: File) => {
      const media = await uploadFile(file, 'plot_import');
      return startImport(projectId!, { mediaId: media.id, duplicateMode, autoPlace });
    },
    onSuccess: (job) => {
      setJobId(job.id);
      setStep(2);
    },
    onError: (err) => setUploadError(resolveErrorMessage(err)),
    onSettled: () => setUploading(false),
  });

  const commitMutation = useMutation({
    mutationFn: () => commitImport(jobId!, { skipInvalid }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['grid', projectId] });
      queryClient.invalidateQueries({ queryKey: ['plots', projectId] });
      queryClient.invalidateQueries({ queryKey: ['plot-stats', projectId] });
      // Same gap as PlotForm/PlotDetailDrawer's mutations (see their own
      // comments): ProjectDetailPage's dashboard StatCards read plotCounts
      // off ['project', projectId], not plot-stats. Bulk-importing plots
      // changes the total just as much as a single create does, but this
      // handler never invalidated it, so the overview page stayed stale
      // after a commit until an unrelated navigation remounted it.
      queryClient.invalidateQueries({ queryKey: ['project', projectId] });
      setStep(3);
    },
  });

  const cancelMutation = useMutation({
    mutationFn: () => cancelImport(jobId!),
    onSuccess: () => navigate(`/builder/projects/${projectId}`),
  });

  async function handleDownload() {
    if (project.data) await downloadImportTemplate(projectId!, project.data.name);
  }

  function handleFile(file: File) {
    setUploadError(null);
    setUploading(true);
    startMutation.mutate(file);
  }

  const rows = rowsQuery.data ?? [];
  const jobSummary = startMutation.data;

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader title={t('wizard.title')} />

      <div className="mb-6 flex gap-1">
        {STEP_KEYS.map((key, i) => (
          <div key={key} className={`h-1.5 flex-1 rounded-full ${i <= step ? 'bg-primary' : 'bg-muted'}`} />
        ))}
      </div>

      {step === 0 && (
        <div className="space-y-4">
          <p className="text-sm font-medium text-foreground">{t('wizard.download.title')}</p>
          <p className="text-sm text-muted-foreground">{t('wizard.download.description')}</p>
          <Button onClick={handleDownload} disabled={!project.data}>
            <Download className="size-4" />
            {t('wizard.download.button')}
          </Button>
          <div className="flex justify-end">
            <Button onClick={() => setStep(1)}>{t('common:actions.next')}</Button>
          </div>
        </div>
      )}

      {step === 1 && (
        <div className="space-y-4">
          <p className="text-sm font-medium text-foreground">{t('wizard.upload.title')}</p>
          <p className="text-sm text-muted-foreground">{t('wizard.upload.description')}</p>

          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            disabled={uploading}
            className="flex h-32 w-full flex-col items-center justify-center gap-2 rounded-md border border-dashed border-border text-sm text-muted-foreground hover:bg-accent/50 disabled:opacity-60"
          >
            {uploading ? <Loader2 className="size-6 animate-spin" /> : <Upload className="size-6" />}
            <span>{t('wizard.upload.dropHint')}</span>
          </button>
          <input
            ref={fileInputRef}
            type="file"
            accept=".xlsx"
            className="hidden"
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (file) handleFile(file);
              e.target.value = '';
            }}
          />
          <FormError message={uploadError} />

          <div className="space-y-2">
            <p className="text-sm font-medium text-foreground">{t('wizard.upload.duplicateMode')}</p>
            <Select value={duplicateMode} onValueChange={(v) => setDuplicateMode(v as DuplicateMode)}>
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="SKIP">{t('wizard.upload.duplicateModes.SKIP')}</SelectItem>
                <SelectItem value="UPDATE_EXISTING">{t('wizard.upload.duplicateModes.UPDATE_EXISTING')}</SelectItem>
                <SelectItem value="FAIL">{t('wizard.upload.duplicateModes.FAIL')}</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <label className="flex items-center gap-2 text-sm">
            <Checkbox checked={autoPlace} onCheckedChange={(c) => setAutoPlace(c === true)} />
            {t('wizard.upload.autoPlace')}
          </label>

          <div className="flex justify-between">
            <Button variant="outline" onClick={() => setStep(0)}>
              {t('common:actions.back')}
            </Button>
          </div>
        </div>
      )}

      {step === 2 && jobSummary && (
        <div className="space-y-4">
          <p className="text-sm font-medium text-foreground">{t('wizard.preview.title')}</p>
          <p className="text-sm text-muted-foreground">
            {t('wizard.preview.summary', {
              valid: jobSummary.validRows,
              invalid: jobSummary.invalidRows,
              total: jobSummary.totalRows,
            })}
          </p>

          <label className="flex items-center gap-2 text-sm">
            <Checkbox checked={showOnlyErrors} onCheckedChange={(c) => setShowOnlyErrors(c === true)} />
            {t('wizard.preview.onlyErrors')}
          </label>

          <div className="max-h-96 overflow-auto rounded-md border border-border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('wizard.preview.rowNumber')}</TableHead>
                  <TableHead>{t('plot:form.fields.plotNumber')}</TableHead>
                  <TableHead>{t('wizard.preview.errorsColumn')}</TableHead>
                  <TableHead />
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((row) => (
                  <TableRow key={row.id}>
                    <TableCell>{row.rowNumber}</TableCell>
                    <TableCell>{row.data.plot_number}</TableCell>
                    <TableCell className={row.status === 'INVALID' ? 'text-destructive' : 'text-muted-foreground'}>
                      {row.errors.map((e) => resolveRowError(e)).join(', ')}
                    </TableCell>
                    <TableCell>
                      {row.status === 'INVALID' && (
                        <Button size="sm" variant="outline" onClick={() => setEditingRow(row)}>
                          {t('wizard.preview.editRow')}
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>

          {editingRow && (
            <RowEditor
              jobId={jobId!}
              row={editingRow}
              onDone={() => {
                setEditingRow(null);
                queryClient.invalidateQueries({ queryKey: ['import-rows', jobId] });
              }}
            />
          )}

          <div className="flex justify-between">
            <Button variant="outline" onClick={() => cancelMutation.mutate()}>
              {t('wizard.cancel')}
            </Button>
            <Button onClick={() => setStep(3)}>{t('common:actions.next')}</Button>
          </div>
        </div>
      )}

      {step === 3 && !commitMutation.data && (
        <div className="space-y-4">
          <p className="text-sm font-medium text-foreground">{t('wizard.commit.title')}</p>
          <p className="text-sm text-muted-foreground">{t('wizard.commit.description')}</p>
          <label className="flex items-center gap-2 text-sm">
            <Checkbox checked={skipInvalid} onCheckedChange={(c) => setSkipInvalid(c === true)} />
            {t('wizard.commit.skipInvalid')}
          </label>
          {commitMutation.isError && <FormError message={resolveErrorMessage(commitMutation.error)} />}
          <div className="flex justify-between">
            <Button variant="outline" onClick={() => setStep(2)}>
              {t('common:actions.back')}
            </Button>
            <Button onClick={() => commitMutation.mutate()} disabled={commitMutation.isPending}>
              {commitMutation.isPending ? t('wizard.commit.committing') : t('wizard.commit.button')}
            </Button>
          </div>
        </div>
      )}

      {commitMutation.data && (
        <div className="space-y-4 text-center">
          <p className="text-lg font-semibold text-foreground">{t('wizard.commit.resultTitle')}</p>
          <p className="text-sm text-muted-foreground">
            {t('wizard.commit.resultSummary', { imported: commitMutation.data.importedRows })}
          </p>
          <Button onClick={() => navigate(`/builder/projects/${projectId}`)}>{t('common:actions.close')}</Button>
        </div>
      )}
    </div>
  );
}

function RowEditor({ jobId, row, onDone }: { jobId: string; row: ImportRowResponse; onDone: () => void }) {
  const { t } = useTranslation(['import', 'common']);
  const [data, setData] = useState<Record<string, string>>(row.data);

  const mutation = useMutation({
    mutationFn: () => patchImportRow(jobId, row.id, data),
    onSuccess: onDone,
  });

  return (
    <div className="space-y-3 rounded-md border border-border p-4">
      <div className="grid grid-cols-2 gap-3">
        {Object.entries(data).map(([key, value]) => (
          <div key={key} className="space-y-1">
            <label className="text-xs text-muted-foreground">{key}</label>
            <Input value={value} onChange={(e) => setData((d) => ({ ...d, [key]: e.target.value }))} />
          </div>
        ))}
      </div>
      <div className="flex justify-end gap-2">
        <Button size="sm" variant="outline" onClick={onDone}>
          {t('common:actions.cancel')}
        </Button>
        <Button size="sm" onClick={() => mutation.mutate()} disabled={mutation.isPending}>
          {t('common:actions.save')}
        </Button>
      </div>
    </div>
  );
}
