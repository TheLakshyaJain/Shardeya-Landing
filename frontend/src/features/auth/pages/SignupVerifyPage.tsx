import { useState, type FormEvent } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { AuthLayout } from '../components/AuthLayout';
import { ResendTimer } from '../components/ResendTimer';
import { OtpInput } from '@/components/forms/OtpInput';
import { FormError } from '@/components/forms/FormError';
import { Button } from '@/components/ui/button';
import { resendOtp, verifySignupOtp } from '../api/authApi';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { useAuthStore } from '../store/authStore';
import { dashboardPathFor } from '../redirect';

interface LocationState {
  challengeId: string;
  maskedRecipient: string;
  resendAfterSeconds: number;
}

export function SignupVerifyPage() {
  const { t } = useTranslation('auth');
  const navigate = useNavigate();
  const location = useLocation();
  const setSession = useAuthStore((s) => s.setSession);
  const state = location.state as LocationState | null;

  const [challengeId, setChallengeId] = useState(state?.challengeId ?? '');
  const [resendAfterSeconds, setResendAfterSeconds] = useState(state?.resendAfterSeconds ?? 0);
  const [code, setCode] = useState('');

  const verifyMutation = useMutation({
    mutationFn: verifySignupOtp,
    onSuccess: (tokens) => {
      setSession(tokens);
      navigate(dashboardPathFor(tokens.org.type), { replace: true });
    },
  });

  const resendMutation = useMutation({
    mutationFn: resendOtp,
    onSuccess: (res) => {
      setChallengeId(res.challengeId);
      setResendAfterSeconds(res.resendAfterSeconds);
    },
  });

  // No state means this page was reached directly (refresh, back button,
  // bookmark) rather than via a completed signup submission — nothing to
  // verify without a challengeId, so send them back to start over.
  if (!state) {
    return <Navigate to="/signup" replace />;
  }

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    verifyMutation.mutate({ challengeId, code });
  };

  return (
    <AuthLayout title={t('verify.title')} subtitle={t('verify.subtitle', { recipient: state.maskedRecipient })}>
      <form onSubmit={handleSubmit} className="space-y-4">
        <div className="space-y-2">
          <OtpInput
            value={code}
            onChange={setCode}
            autoFocus
            digitLabel={(pos) => t('fields.otpDigit', { position: pos })}
          />
          <FormError message={verifyMutation.isError ? resolveErrorMessage(verifyMutation.error) : null} />
        </div>

        <Button type="submit" className="w-full" disabled={code.length !== 6 || verifyMutation.isPending}>
          {t('verify.submit')}
        </Button>

        <div className="flex items-center justify-between">
          <ResendTimer
            initialSeconds={resendAfterSeconds}
            onResend={() => resendMutation.mutate({ challengeId })}
            resendLabel={t('verify.resend')}
            countdownLabel={(seconds) => t('verify.resendIn', { seconds })}
            disabled={resendMutation.isPending}
          />
        </div>
        <FormError message={resendMutation.isError ? resolveErrorMessage(resendMutation.error) : null} />
      </form>
    </AuthLayout>
  );
}
