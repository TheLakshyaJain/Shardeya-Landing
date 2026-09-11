import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { PageHeader } from '@/components/layout/PageHeader';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Skeleton } from '@/components/ui/skeleton';
import { FormError } from '@/components/forms/FormError';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { ChevronLeft } from 'lucide-react';
import { getTemplate, getVariablePalette, previewTemplate, updateTemplate, activateTemplate } from '../api/documentApi';

type ActiveField = 'body' | 'header' | 'footer';

// B-11 §6 TemplateEditor: variable palette (click-to-insert) + live preview
// against a real record + Activate. Plain HTML textareas, not a full
// WYSIWYG rich-text editor -- a deliberate scope trim given this
// milestone's time budget (see CLAUDE.md); the sandbox only ever
// interprets {{...}} tokens and passes surrounding markup straight
// through, so raw HTML editing is functionally complete, just not as
// friendly as a real rich-text toolbar would be.
export function TemplateEditorPage() {
  const { id } = useParams<{ id: string }>();
  const { t } = useTranslation('document');
  const queryClient = useQueryClient();

  const templateQuery = useQuery({ queryKey: ['document-template', id], queryFn: () => getTemplate(id!), enabled: !!id });
  const paletteQuery = useQuery({
    queryKey: ['document-variables', templateQuery.data?.docType],
    queryFn: () => getVariablePalette(templateQuery.data!.docType),
    enabled: !!templateQuery.data,
  });

  const [name, setName] = useState('');
  const [bodyHtml, setBodyHtml] = useState('');
  const [headerHtml, setHeaderHtml] = useState('');
  const [footerHtml, setFooterHtml] = useState('');
  const [activeField, setActiveField] = useState<ActiveField>('body');
  const [sampleEntityId, setSampleEntityId] = useState('');
  const [previewHtml, setPreviewHtml] = useState('');

  useEffect(() => {
    if (templateQuery.data) {
      setName(templateQuery.data.name);
      setBodyHtml(templateQuery.data.bodyHtml);
      setHeaderHtml(templateQuery.data.headerHtml ?? '');
      setFooterHtml(templateQuery.data.footerHtml ?? '');
    }
  }, [templateQuery.data]);

  const saveMutation = useMutation({
    mutationFn: () => updateTemplate(id!, { name, bodyHtml, headerHtml, footerHtml }),
    onSuccess: (updated) => {
      queryClient.setQueryData(['document-template', id], updated);
    },
  });

  const previewMutation = useMutation({
    mutationFn: () => previewTemplate(id!, sampleEntityId),
    onSuccess: (res) => setPreviewHtml(res.renderedHtml),
  });

  const activateMutation = useMutation({
    mutationFn: () => activateTemplate(id!),
    onSuccess: (updated) => {
      queryClient.setQueryData(['document-template', id], updated);
      queryClient.invalidateQueries({ queryKey: ['document-templates'] });
    },
  });

  // A bare {{label}} outside a loop is invalid (TemplateRenderer only
  // resolves a collection's own fields inside its #each block) -- a
  // collection-field click inserts a complete, independently valid
  // {{#each collection}}{{field}}{{/each}} unit every time, rather than
  // relying on the user to open/close the loop by hand across two
  // separate button clicks.
  function insertVariable(token: string, collection?: string) {
    const snippet = collection ? `{{#each ${collection}}}{{${token}}}{{/each}}` : `{{${token}}}`;
    if (activeField === 'body') setBodyHtml((v) => v + snippet);
    else if (activeField === 'header') setHeaderHtml((v) => v + snippet);
    else setFooterHtml((v) => v + snippet);
  }

  if (templateQuery.isLoading) return <Skeleton className="h-96 w-full rounded-md" />;
  if (!templateQuery.data) return null;

  return (
    <div>
      <Link to="/builder/documents/templates" className="mb-2 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ChevronLeft className="size-4" />
        {t('templates.back')}
      </Link>
      <PageHeader
        title={t('templates.editTitle', { name: templateQuery.data.name })}
        actions={
          <>
            <Button variant="outline" onClick={() => saveMutation.mutate()} disabled={saveMutation.isPending}>
              {t('templates.save')}
            </Button>
            <Button onClick={() => activateMutation.mutate()} disabled={activateMutation.isPending}>
              {t('templates.activate')}
            </Button>
          </>
        }
      />
      <FormError message={activateMutation.isError ? resolveErrorMessage(activateMutation.error) : undefined} />

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[1fr_260px]">
        <div className="space-y-4">
          <div className="space-y-1">
            <Label htmlFor="tpl-name">{t('templates.fields.name')}</Label>
            <Input id="tpl-name" value={name} onChange={(e) => setName(e.target.value)} />
          </div>
          <div className="space-y-1">
            <Label htmlFor="tpl-header">{t('templates.fields.header')}</Label>
            <Textarea id="tpl-header" rows={3} value={headerHtml} onFocus={() => setActiveField('header')}
              onChange={(e) => setHeaderHtml(e.target.value)} className="font-mono text-xs" />
          </div>
          <div className="space-y-1">
            <Label htmlFor="tpl-body">{t('templates.fields.body')}</Label>
            <Textarea id="tpl-body" rows={12} value={bodyHtml} onFocus={() => setActiveField('body')}
              onChange={(e) => setBodyHtml(e.target.value)} className="font-mono text-xs" />
          </div>
          <div className="space-y-1">
            <Label htmlFor="tpl-footer">{t('templates.fields.footer')}</Label>
            <Textarea id="tpl-footer" rows={3} value={footerHtml} onFocus={() => setActiveField('footer')}
              onChange={(e) => setFooterHtml(e.target.value)} className="font-mono text-xs" />
          </div>

          <div className="space-y-2 rounded-md border border-border p-3">
            <Label htmlFor="tpl-sample-entity">{t('templates.preview.sampleEntityId')}</Label>
            <div className="flex gap-2">
              <Input id="tpl-sample-entity" value={sampleEntityId} onChange={(e) => setSampleEntityId(e.target.value)} placeholder={t('templates.preview.sampleEntityIdHint')} />
              <Button variant="outline" disabled={!sampleEntityId || previewMutation.isPending} onClick={() => previewMutation.mutate()}>
                {t('templates.preview.run')}
              </Button>
            </div>
            {previewMutation.isError && <FormError message={resolveErrorMessage(previewMutation.error)} />}
            {previewHtml && (
              <div className="mt-2 max-h-96 overflow-auto rounded-md border border-border bg-white p-4 text-black" dangerouslySetInnerHTML={{ __html: previewHtml }} />
            )}
          </div>
        </div>

        <aside className="space-y-3 rounded-md border border-border p-3">
          <h3 className="text-sm font-medium">{t('templates.palette.title')}</h3>
          <p className="text-xs text-muted-foreground">{t('templates.palette.hint', { field: t(`templates.fields.${activeField}`) })}</p>
          {paletteQuery.data && (
            <div className="space-y-3">
              <div>
                <h4 className="mb-1 text-xs font-semibold uppercase text-muted-foreground">{t('templates.palette.fields')}</h4>
                <div className="flex flex-wrap gap-1">
                  {paletteQuery.data.topLevel.map((v) => (
                    <button
                      key={v}
                      type="button"
                      className="rounded border border-border px-1.5 py-0.5 text-xs hover:bg-accent"
                      onClick={() => insertVariable(v)}
                    >
                      {v}
                    </button>
                  ))}
                </div>
              </div>
              {Object.entries(paletteQuery.data.collections).map(([collection, fields]) => (
                <div key={collection}>
                  <h4 className="mb-1 text-xs font-semibold uppercase text-muted-foreground">{collection}</h4>
                  <p className="mb-1 text-xs text-muted-foreground">{t('templates.palette.loopHint', { collection })}</p>
                  <div className="flex flex-wrap gap-1">
                    {fields.map((f) => (
                      <button
                        key={f}
                        type="button"
                        className="rounded border border-border px-1.5 py-0.5 text-xs hover:bg-accent"
                        onClick={() => insertVariable(f, collection)}
                      >
                        {f}
                      </button>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}
        </aside>
      </div>
    </div>
  );
}
