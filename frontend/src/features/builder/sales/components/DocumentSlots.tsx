import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FileText, Loader2, Trash2, Upload } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { uploadFile } from '@/features/media/api/mediaApi';
import { attachSaleDocument, deleteSaleDocument, listSaleDocuments } from '../api/saleApi';
import type { PlotDocumentType } from '../types';

const TYPED_SLOTS: PlotDocumentType[] = ['SALE_AGREEMENT', 'REGISTRY_DEED', 'PLOT_MAP', 'BUYER_ID_PROOF'];

interface DocumentSlotsProps {
  saleId: string;
  canEdit: boolean;
}

// B-04 §7/§12.3.3: four typed slots + multi-file Other with a required
// label. BUYER_ID_PROOF uploads go through the sensitive bucket
// (uploadFile(..., sensitive=true)) -- this deliberately does NOT try to
// preview it inline the way ImageUploader does for standard photos (that
// component reads media.url/derivatives, both of which are null for a
// sensitive asset by design; see MediaService.toResponse's own comment).
// Viewing an already-attached ID proof happens through SensitiveDocGuard's
// audited reveal action instead, not from this list.
export function DocumentSlots({ saleId, canEdit }: DocumentSlotsProps) {
  const { t } = useTranslation(['sale', 'common', 'errors']);
  const queryClient = useQueryClient();
  const [otherLabel, setOtherLabel] = useState('');
  const [uploadingSlot, setUploadingSlot] = useState<string | null>(null);
  const inputRefs = useRef<Record<string, HTMLInputElement | null>>({});

  const query = useQuery({ queryKey: ['sale-documents', saleId], queryFn: () => listSaleDocuments(saleId) });
  const documents = query.data ?? [];

  const attachMutation = useMutation({
    mutationFn: async ({ docType, file, label }: { docType: PlotDocumentType; file: File; label?: string }) => {
      const media = await uploadFile(file, 'sale_document', docType === 'BUYER_ID_PROOF');
      return attachSaleDocument(saleId, { docType, mediaId: media.id, label });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sale-documents', saleId] });
      setOtherLabel('');
    },
    onSettled: () => setUploadingSlot(null),
  });

  const deleteMutation = useMutation({
    mutationFn: deleteSaleDocument,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['sale-documents', saleId] }),
  });

  function handleFile(docType: PlotDocumentType, file: File, label?: string) {
    setUploadingSlot(docType);
    attachMutation.mutate({ docType, file, label });
  }

  return (
    <div className="space-y-3">
      {TYPED_SLOTS.map((docType) => {
        const existing = documents.filter((d) => d.docType === docType);
        return (
          <div key={docType} className="flex items-center justify-between rounded-md border border-border p-2">
            <div className="flex items-center gap-2 text-sm">
              <FileText className="size-4 text-muted-foreground" />
              <span>{t(`documents.slots.${docType}`)}</span>
              {docType === 'BUYER_ID_PROOF' && existing.length > 0 && (
                <span className="text-xs text-muted-foreground">({t('documents.sensitiveNotice')})</span>
              )}
            </div>
            <div className="flex items-center gap-2">
              {existing.map((doc) => (
                <Button key={doc.id} size="icon" variant="ghost" onClick={() => deleteMutation.mutate(doc.id)} disabled={!canEdit}>
                  <Trash2 className="size-3.5" />
                </Button>
              ))}
              {canEdit && (
                <Button
                  size="sm"
                  variant="outline"
                  disabled={uploadingSlot === docType}
                  onClick={() => inputRefs.current[docType]?.click()}
                >
                  {uploadingSlot === docType ? <Loader2 className="size-3.5 animate-spin" /> : <Upload className="size-3.5" />}
                  {t('documents.upload')}
                </Button>
              )}
              <input
                ref={(el) => {
                  inputRefs.current[docType] = el;
                }}
                type="file"
                className="hidden"
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (file) handleFile(docType, file);
                  e.target.value = '';
                }}
              />
            </div>
          </div>
        );
      })}

      <div className="space-y-2 rounded-md border border-dashed border-border p-2">
        <p className="text-sm font-medium">{t('documents.slots.OTHER')}</p>
        {documents
          .filter((d) => d.docType === 'OTHER')
          .map((doc) => (
            <div key={doc.id} className="flex items-center justify-between text-sm">
              <span>{doc.label}</span>
              {canEdit && (
                <Button size="icon" variant="ghost" onClick={() => deleteMutation.mutate(doc.id)}>
                  <Trash2 className="size-3.5" />
                </Button>
              )}
            </div>
          ))}
        {canEdit && (
          <div className="flex gap-2">
            <Input placeholder={t('documents.label')} value={otherLabel} onChange={(e) => setOtherLabel(e.target.value)} />
            <Button
              size="sm"
              variant="outline"
              disabled={!otherLabel.trim() || uploadingSlot === 'OTHER'}
              onClick={() => inputRefs.current['OTHER']?.click()}
            >
              {uploadingSlot === 'OTHER' ? <Loader2 className="size-3.5 animate-spin" /> : <Upload className="size-3.5" />}
              {t('documents.addOther')}
            </Button>
            <input
              ref={(el) => {
                inputRefs.current['OTHER'] = el;
              }}
              type="file"
              className="hidden"
              onChange={(e) => {
                const file = e.target.files?.[0];
                if (file) handleFile('OTHER', file, otherLabel);
                e.target.value = '';
              }}
            />
          </div>
        )}
      </div>
    </div>
  );
}
