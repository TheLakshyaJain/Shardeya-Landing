import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { PageHeader } from '@/components/layout/PageHeader';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { useCan } from '@/hooks/useCan';
import { cloneTemplate, listTemplates } from '../api/documentApi';
import type { DocType } from '../types';

const DOC_TYPES: DocType[] = ['ALLOTMENT_LETTER', 'PAYMENT_RECEIPT', 'DEMAND_LETTER'];

// B-11 §5 /builder/documents/templates. DOCUMENT_TEMPLATE_EDIT-gated at
// the route level (see nav.ts) -- only Admin ever reaches this page; every
// other role's "Generate" actions live directly on the plot/sale/payment
// screens instead (GenerateDocumentButton), never here.
export function TemplateListPage() {
  const { t } = useTranslation('document');
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const canEdit = useCan('DOCUMENT_TEMPLATE_EDIT');
  const templatesQuery = useQuery({ queryKey: ['document-templates'], queryFn: listTemplates });

  const cloneMutation = useMutation({
    mutationFn: ({ docType, language }: { docType: DocType; language: 'en' | 'hi' }) => cloneTemplate(docType, language),
    onSuccess: (template) => {
      queryClient.invalidateQueries({ queryKey: ['document-templates'] });
      navigate(`/builder/documents/templates/${template.id}/edit`);
    },
  });

  const grouped = DOC_TYPES.map((docType) => ({
    docType,
    templates: (templatesQuery.data ?? []).filter((tpl) => tpl.docType === docType),
  }));

  return (
    <div>
      <PageHeader title={t('templates.title')} description={t('templates.description')} />
      {templatesQuery.isLoading && <Skeleton className="h-64 w-full rounded-md" />}
      <div className="space-y-6">
        {grouped.map(({ docType, templates }) => (
          <div key={docType}>
            <h2 className="mb-2 font-medium">{t(`docType.${docType}`)}</h2>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              {templates.map((tpl) => (
                <div key={tpl.id} className="rounded-md border border-border p-3">
                  <div className="mb-1 flex items-center justify-between gap-2">
                    <span className="font-medium">{tpl.name}</span>
                    <div className="flex gap-1">
                      <Badge variant="outline">{tpl.language.toUpperCase()}</Badge>
                      {tpl.systemDefault && <Badge variant="secondary">{t('templates.systemDefault')}</Badge>}
                      {tpl.active && <Badge>{t('templates.active')}</Badge>}
                    </div>
                  </div>
                  {tpl.systemDefault ? (
                    canEdit && (
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={cloneMutation.isPending}
                        onClick={() => cloneMutation.mutate({ docType, language: tpl.language })}
                      >
                        {t('templates.cloneToEdit')}
                      </Button>
                    )
                  ) : (
                    canEdit && (
                      <Link to={`/builder/documents/templates/${tpl.id}/edit`}>
                        <Button size="sm" variant="outline">{t('templates.edit')}</Button>
                      </Link>
                    )
                  )}
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
