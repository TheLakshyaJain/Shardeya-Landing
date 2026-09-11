import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog';
import { OtpInput } from '@/components/forms/OtpInput';
import { FormError } from '@/components/forms/FormError';
import { ResendTimer } from '@/features/auth/components/ResendTimer';
import { resendOtp } from '@/features/auth/api/authApi';
import { confirmWhatsAppOptIn, getWhatsAppOptInStatus, optOutWhatsApp, startWhatsAppOptIn } from '../api/settingsApi';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import type { WhatsAppOptInChallengeResponse } from '../types';

export function WhatsAppOptInCard() {
  const { t } = useTranslation('settings');
  const queryClient = useQueryClient();
  const [challenge, setChallenge] = useState<WhatsAppOptInChallengeResponse | null>(null);
  const [code, setCode] = useState('');

  const statusQuery = useQuery({ queryKey: ['whatsapp-optin-status'], queryFn: getWhatsAppOptInStatus });

  const startMutation = useMutation({
    mutationFn: startWhatsAppOptIn,
    onSuccess: setChallenge,
  });

  const resendMutation = useMutation({
    mutationFn: resendOtp,
    // resendOtp() is the shared OTP-resend endpoint (signup/login-OTP/
    // WhatsApp opt-in all funnel through it) -- its response shape
    // (SignupResponse: {challengeId, maskedRecipient, resendAfterSeconds})
    // only coincidentally used to structurally match
    // WhatsAppOptInChallengeResponse before the Email OTP fix renamed
    // maskedMobile -> maskedRecipient there. The masked mobile itself never
    // changes on a resend (same recipient, new code) -- only the
    // challengeId/resendAfterSeconds do, so merge just those into the
    // existing challenge state instead of assuming the two response types
    // are interchangeable.
    onSuccess: (res) => setChallenge((prev) => (prev ? { ...prev, challengeId: res.challengeId, resendAfterSeconds: res.resendAfterSeconds } : prev)),
  });

  const confirmMutation = useMutation({
    mutationFn: () => confirmWhatsAppOptIn({ challengeId: challenge!.challengeId, code }),
    onSuccess: () => {
      toast.success(t('whatsapp.otp.confirmed'));
      setChallenge(null);
      setCode('');
      queryClient.invalidateQueries({ queryKey: ['whatsapp-optin-status'] });
    },
  });

  const optOutMutation = useMutation({
    mutationFn: optOutWhatsApp,
    onSuccess: () => {
      toast.success(t('whatsapp.disabled'));
      queryClient.invalidateQueries({ queryKey: ['whatsapp-optin-status'] });
    },
  });

  if (statusQuery.isLoading) {
    return <Skeleton className="h-32 w-full" />;
  }

  const optedIn = statusQuery.data?.optedIn ?? false;
  const mobile = statusQuery.data?.mobile ?? '';

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('whatsapp.title')}</CardTitle>
        <p className="text-sm text-muted-foreground">{t('whatsapp.description')}</p>
      </CardHeader>
      <CardContent className="space-y-3">
        <p className="text-sm">{optedIn ? t('whatsapp.statusOptedIn', { mobile }) : t('whatsapp.statusOptedOut')}</p>
        {optedIn ? (
          <Button variant="outline" onClick={() => optOutMutation.mutate()} disabled={optOutMutation.isPending}>
            {t('whatsapp.disable')}
          </Button>
        ) : (
          <Button onClick={() => startMutation.mutate()} disabled={startMutation.isPending}>
            {t('whatsapp.enable')}
          </Button>
        )}
        <FormError message={startMutation.isError ? resolveErrorMessage(startMutation.error) : null} />
        <FormError message={optOutMutation.isError ? resolveErrorMessage(optOutMutation.error) : null} />
      </CardContent>

      <Dialog open={challenge !== null} onOpenChange={(open) => !open && setChallenge(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('whatsapp.otp.title')}</DialogTitle>
            <DialogDescription>{t('whatsapp.otp.subtitle', { mobile: challenge?.maskedMobile })}</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <OtpInput value={code} onChange={setCode} autoFocus digitLabel={(pos) => `${pos}`} />
            <FormError message={confirmMutation.isError ? resolveErrorMessage(confirmMutation.error) : null} />
            <Button
              className="w-full"
              disabled={code.length !== 6 || confirmMutation.isPending}
              onClick={() => confirmMutation.mutate()}
            >
              {t('whatsapp.otp.submit')}
            </Button>
            {challenge && (
              <ResendTimer
                initialSeconds={challenge.resendAfterSeconds}
                onResend={() => resendMutation.mutate({ challengeId: challenge.challengeId })}
                resendLabel={t('verify.resend', { ns: 'auth' })}
                countdownLabel={(seconds) => t('verify.resendIn', { ns: 'auth', seconds })}
                disabled={resendMutation.isPending}
              />
            )}
            <FormError message={resendMutation.isError ? resolveErrorMessage(resendMutation.error) : null} />
          </div>
        </DialogContent>
      </Dialog>
    </Card>
  );
}
