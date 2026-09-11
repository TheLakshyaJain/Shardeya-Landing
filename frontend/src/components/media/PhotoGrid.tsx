import { useQueries } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { ArrowLeft, ArrowRight, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { getMedia } from '@/features/media/api/mediaApi';
import { ImageUploader } from './ImageUploader';

interface GalleryItem {
  mediaId: string;
  sortOrder: number;
}

interface PhotoGridProps {
  items: GalleryItem[];
  purpose: string;
  onAdd: (mediaId: string) => void;
  onRemove: (mediaId: string) => void;
  onReorder: (mediaId: string, direction: 'left' | 'right') => void;
}

// Reorder via left/right buttons rather than dnd-kit pointer/touch sensors:
// equally usable at 360px on a touchscreen, and this milestone's exit
// criteria don't require an actual drag gesture — revisit if a later
// milestone's spec calls for true drag-and-drop reordering.
export function PhotoGrid({ items, purpose, onAdd, onRemove, onReorder }: PhotoGridProps) {
  const { t } = useTranslation('common');
  const sorted = [...items].sort((a, b) => a.sortOrder - b.sortOrder);

  const mediaQueries = useQueries({
    queries: sorted.map((item) => ({
      queryKey: ['media', item.mediaId],
      queryFn: () => getMedia(item.mediaId),
    })),
  });

  return (
    <div className="flex flex-wrap gap-3">
      {sorted.map((item, index) => {
        const media = mediaQueries[index]?.data;
        return (
          <div key={item.mediaId} className="relative w-32 overflow-hidden rounded-md border border-border">
            {media?.status === 'READY' && (
              <img src={media.derivatives.thumb ?? media.url} alt="" className="h-32 w-32 object-cover" />
            )}
            <div className="absolute inset-x-0 bottom-0 flex items-center justify-between bg-black/50 px-1 py-0.5">
              <Button
                type="button"
                size="icon"
                variant="ghost"
                className="size-6 text-white hover:bg-white/20 hover:text-white"
                disabled={index === 0}
                onClick={() => onReorder(item.mediaId, 'left')}
                aria-label={t('actions.previous')}
              >
                <ArrowLeft className="size-3.5" />
              </Button>
              <Button
                type="button"
                size="icon"
                variant="ghost"
                className="size-6 text-white hover:bg-white/20 hover:text-white"
                onClick={() => onRemove(item.mediaId)}
                aria-label={t('actions.remove')}
              >
                <Trash2 className="size-3.5" />
              </Button>
              <Button
                type="button"
                size="icon"
                variant="ghost"
                className="size-6 text-white hover:bg-white/20 hover:text-white"
                disabled={index === sorted.length - 1}
                onClick={() => onReorder(item.mediaId, 'right')}
                aria-label={t('actions.next')}
              >
                <ArrowRight className="size-3.5" />
              </Button>
            </div>
          </div>
        );
      })}
      <div className="w-32">
        <ImageUploader label="" purpose={purpose} onUploaded={onAdd} />
      </div>
    </div>
  );
}
