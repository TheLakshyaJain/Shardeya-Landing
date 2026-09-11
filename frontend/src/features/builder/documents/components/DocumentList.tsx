import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
import { Download, FileText } from 'lucide-react';
import { downloadDocument, listDocumentsForEntity } from '../api/documentApi';

interface DocumentListProps {
  entityType: string;
  entityId: string;
}

// B-11 §6 DocumentList: "per entity, with version history" -- version
// history here means every past generation stays listed (no document is
// ever replaced in place, matching generated_document's own immutability),
// not a version-diff view.
export function DocumentList({ entityType, entityId }: DocumentListProps) {
  const { t } = useTranslation('document');
  const docsQuery = useQuery({
    queryKey: ['generated-documents', entityType, entityId],
    queryFn: () => listDocumentsForEntity(entityType, entityId),
  });

  if (!docsQuery.data || docsQuery.data.length === 0) {
    return <p className="text-xs text-muted-foreground">{t('list.empty')}</p>;
  }

  return (
    <ul className="space-y-1.5">
      {docsQuery.data.map((doc) => (
        <li key={doc.id} className="flex items-center justify-between rounded border border-border px-2 py-1.5 text-sm">
          <span className="flex items-center gap-2">
            <FileText className="size-4 text-muted-foreground" />
            <span>{t(`generateButton.docType.${doc.docType}`)}</span>
            <span className="text-xs text-muted-foreground">{doc.documentNumber}</span>
          </span>
          <Button size="sm" variant="ghost" onClick={() => downloadDocument(doc.id, doc.documentNumber.replace(/\//g, '-'))}>
            <Download className="size-4" />
          </Button>
        </li>
      ))}
    </ul>
  );
}
