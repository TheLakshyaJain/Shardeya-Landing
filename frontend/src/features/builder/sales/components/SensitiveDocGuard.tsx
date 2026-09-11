import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation } from '@tanstack/react-query';
import { Eye, EyeOff } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { revealGovId } from '../api/saleApi';
import type { GovIdRevealResponse } from '../types';

interface SensitiveDocGuardProps {
  saleId: string;
  govIdType: string | null;
  last4: string | null;
}

// B-04 §9: gov-ID reveal is a separate, audited, permission-gated call --
// never bundled into the plain sale GET. This blurs the masked value by
// default and only fetches (and logs an audit entry for) the real number
// when the user explicitly clicks Reveal; clicking Hide clears it from
// local state entirely rather than just visually re-blurring already-
// fetched data, so a stale decrypted number never lingers in memory
// longer than the user actually wants it visible.
export function SensitiveDocGuard({ saleId, govIdType, last4 }: SensitiveDocGuardProps) {
  const { t } = useTranslation('sale');
  const [revealed, setRevealed] = useState<GovIdRevealResponse | null>(null);

  const revealMutation = useMutation({
    mutationFn: () => revealGovId(saleId, 'Viewed from plot detail'),
    onSuccess: (result) => setRevealed(result),
  });

  if (!govIdType && !last4) return null;

  return (
    <div className="flex items-center gap-2 text-sm">
      <span className="font-mono">
        {revealed?.govIdNumber ?? t('govId.masked', { last4: last4 ?? '????' })}
      </span>
      {revealed ? (
        <Button type="button" size="sm" variant="ghost" onClick={() => setRevealed(null)}>
          <EyeOff className="size-3.5" />
          {t('govId.hide')}
        </Button>
      ) : (
        <Button type="button" size="sm" variant="ghost" disabled={revealMutation.isPending} onClick={() => revealMutation.mutate()}>
          <Eye className="size-3.5" />
          {t('govId.reveal')}
        </Button>
      )}
      {revealed?.govIdMediaUrl && (
        <a href={revealed.govIdMediaUrl} target="_blank" rel="noreferrer" className="text-xs underline">
          {t('govId.viewScan')}
        </a>
      )}
    </div>
  );
}
