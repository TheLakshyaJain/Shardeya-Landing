import { useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Download, ImagePlus, Loader2, X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { getMedia, uploadFile } from '@/features/media/api/mediaApi';

interface ImageUploaderProps {
  label?: string;
  mediaId?: string | null;
  purpose: string;
  onUploaded: (mediaId: string) => void;
  onRemove?: () => void;
  accept?: string;
  // Document-shaped uploads (layout plan, brochure) need an explicit way to
  // get the original file back out, not just view a cropped thumbnail --
  // opt-in rather than default-on so plain photo uploads (cover, gallery)
  // don't grow an affordance that doesn't make sense for them.
  allowDownload?: boolean;
}

// M-05 §10: server hard-caps at 10MB; this app relies on the browser having
// already compressed (no client-side compression pass here — out of scope
// for this milestone) so a friendly pre-check just avoids a wasted round trip.
const MAX_SIZE_BYTES = 10 * 1024 * 1024;

export function ImageUploader({ label, mediaId, purpose, onUploaded, onRemove, accept = 'image/*', allowDownload = false }: ImageUploaderProps) {
  const { t } = useTranslation(['common', 'errors']);
  const inputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const { data: media } = useQuery({
    queryKey: ['media', mediaId],
    queryFn: () => getMedia(mediaId!),
    enabled: !!mediaId,
  });

  async function handleFile(file: File) {
    setError(null);
    if (file.size > MAX_SIZE_BYTES) {
      setError(t('media.tooLarge', { ns: 'errors' }));
      return;
    }
    setUploading(true);
    try {
      const result = await uploadFile(file, purpose);
      if (result.status === 'REJECTED') {
        setError(t('media.unrecognizedType', { ns: 'errors' }));
        return;
      }
      onUploaded(result.id);
    } catch {
      setError(t('generic', { ns: 'errors' }));
    } finally {
      setUploading(false);
    }
  }

  return (
    <div className="space-y-2">
      {label && <p className="text-sm font-medium text-foreground">{label}</p>}
      {media?.status === 'READY' ? (
        <div className="relative w-full max-w-xs overflow-hidden rounded-md border border-border">
          {media.mimeType.startsWith('image/') ? (
            <img src={media.derivatives.card ?? media.url ?? undefined} alt={label} className="h-40 w-full object-cover" />
          ) : (
            // A non-image file (e.g. a PDF brochure) has no card derivative
            // and can't render inside an <img> at all -- rendering one
            // anyway just shows a broken-image icon. This was silently
            // wrong before allowDownload existed: nothing surfaced it
            // because nothing needed to distinguish "no thumbnail" from
            // "broken thumbnail" until a real download action needed to
            // point at the right file.
            <div className="flex h-40 w-full flex-col items-center justify-center gap-2 bg-muted/40 text-sm text-muted-foreground">
              <Download className="size-6" />
              <span>{media.mimeType.split('/')[1]?.toUpperCase() ?? 'FILE'}</span>
            </div>
          )}
          {onRemove && (
            <Button
              type="button"
              size="icon"
              variant="secondary"
              className="absolute right-2 top-2 size-7"
              onClick={onRemove}
              aria-label={t('actions.remove')}
            >
              <X className="size-4" />
            </Button>
          )}
          {allowDownload && media.url && (
            <a
              href={media.url}
              download
              target="_blank"
              rel="noopener noreferrer"
              className="absolute bottom-2 right-2 inline-flex size-7 items-center justify-center rounded-md bg-secondary text-secondary-foreground shadow hover:bg-secondary/80"
              aria-label={t('actions.download')}
              title={t('actions.download')}
            >
              <Download className="size-4" />
            </a>
          )}
        </div>
      ) : (
        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          disabled={uploading}
          className="flex h-40 w-full max-w-xs flex-col items-center justify-center gap-2 rounded-md border border-dashed border-border text-sm text-muted-foreground hover:bg-accent/50 disabled:opacity-60"
        >
          {uploading ? <Loader2 className="size-6 animate-spin" /> : <ImagePlus className="size-6" />}
          <span>{t('actions.upload')}</span>
        </button>
      )}
      <input
        ref={inputRef}
        type="file"
        accept={accept}
        className="hidden"
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) void handleFile(file);
          e.target.value = '';
        }}
      />
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}
