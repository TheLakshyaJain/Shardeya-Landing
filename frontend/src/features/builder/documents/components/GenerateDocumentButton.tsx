import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { FormError } from '@/components/forms/FormError';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { generateDocument, downloadDocument } from '../api/documentApi';
import type { DocType } from '../types';

interface GenerateDocumentButtonProps {
  docType: DocType;
  entityId: string;
  entityType: string;
}

// B-11 §6/§8 GenerateDocumentButton: "on plot, sale, and payment views" --
// generate -> ~2s -> download, per the spec's own user-flow narrative.
// Language defaults to English; B-11 §7's "defaulting to the buyer's
// preference" isn't tracked anywhere in this schema (no per-buyer language
// field exists), so the picker here is the only way to choose Hindi -- a
// documented simplification, not a silent gap.
export function GenerateDocumentButton({ docType, entityId, entityType }: GenerateDocumentButtonProps) {
  const { t } = useTranslation('document');
  const queryClient = useQueryClient();
  const [language, setLanguage] = useState<'en' | 'hi'>('en');

  const generateMutation = useMutation({
    mutationFn: () => generateDocument({ docType, entityId, language }, crypto.randomUUID()),
    onSuccess: async (doc) => {
      queryClient.invalidateQueries({ queryKey: ['generated-documents', entityType, entityId] });
      await downloadDocument(doc.id, doc.documentNumber.replace(/\//g, '-'));
    },
  });

  return (
    <div className="flex items-center gap-2">
      <Select value={language} onValueChange={(v) => setLanguage(v as 'en' | 'hi')}>
        <SelectTrigger className="w-24" aria-label={t('generateButton.language')}>
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="en">EN</SelectItem>
          <SelectItem value="hi">HI</SelectItem>
        </SelectContent>
      </Select>
      <Button size="sm" onClick={() => generateMutation.mutate()} disabled={generateMutation.isPending}>
        {generateMutation.isPending ? t('generateButton.generating') : t(`generateButton.docType.${docType}`)}
      </Button>
      {generateMutation.isError && <FormError message={resolveErrorMessage(generateMutation.error)} />}
    </div>
  );
}
