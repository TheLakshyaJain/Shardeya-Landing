import { useState, type FormEvent } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { AuthLayout } from '../components/AuthLayout';
import { ResendTimer } from '../components/ResendTimer';
import { OtpInput } from '@/components/forms/OtpInput';
import { FormError } from '@/components/forms/FormError';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import { buildLoginOtpSchema, type LoginOtpFormValues } from '../schemas';
import { requestLoginOtp, resendOtp, verifyLoginOtp } from '../api/authApi';
import type { SignupResponse } from '../types';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { useAuthStore } from '../store/authStore';
import { dashboardPathFor } from '../redirect';

function RequestCodeStep({ onRequested }: { onRequested: (challenge: SignupResponse) => void }) {
  const { t } = useTranslation('auth');
  const schema = buildLoginOtpSchema(t);
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginOtpFormValues>({ resolver: zodResolver(schema) });

  const mutation = useMutation({
    mutationFn: requestLoginOtp,
    onSuccess: onRequested,
  });

  return (
    <AuthLayout title={t('loginOtp.title')} subtitle={t('loginOtp.subtitle')}>
      <form onSubmit={handleSubmit((v) => mutation.mutate(v))} className="space-y-4" noValidate>
        <div className="space-y-2">
          <Label htmlFor="identifier">{t('loginOtp.identifier')}</Label>
          <Input id="identifier" aria-invalid={!!errors.identifier} {...register('identifier')} />
          <FormError message={errors.identifier?.message} />
        </div>
        <FormError message={mutation.isError ? resolveErrorMessage(mutation.error) : null} />
        <Button type="submit" className="w-full" disabled={mutation.isPending}>
          {t('loginOtp.requestCode')}
        </Button>
        <Button type="button" variant="outline" className="w-full" asChild>
          <Link to="/login">{t('loginOtp.backToPassword')}</Link>
        </Button>
      </form>
    </AuthLayout>
  );
}

function VerifyCodeStep({ challenge }: { challenge: SignupResponse }) {
  const { t } = useTranslation('auth');
  const navigate = useNavigate();
  const setSession = useAuthStore((s) => s.setSession);
  const [challengeId, setChallengeId] = useState(challenge.challengeId);
  const [resendAfterSeconds, setResendAfterSeconds] = useState(challenge.resendAfterSeconds);
  const [code, setCode] = useState('');

  const verifyMutation = useMutation({
    mutationFn: verifyLoginOtp,
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

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    verifyMutation.mutate({ challengeId, code });
  };

  return (
    <AuthLayout title={t('loginOtp.verifyTitle')} subtitle={t('loginOtp.verifySubtitle', { recipient: challenge.maskedRecipient })}>
      <form onSubmit={handleSubmit} className="space-y-4">
        <OtpInput
          value={code}
          onChange={setCode}
          autoFocus
          digitLabel={(pos) => t('fields.otpDigit', { position: pos })}
        />
        <FormError message={verifyMutation.isError ? resolveErrorMessage(verifyMutation.error) : null} />
        <Button type="submit" className="w-full" disabled={code.length !== 6 || verifyMutation.isPending}>
          {t('loginOtp.submit')}
        </Button>
        <ResendTimer
          initialSeconds={resendAfterSeconds}
          onResend={() => resendMutation.mutate({ challengeId })}
          resendLabel={t('verify.resend')}
          countdownLabel={(seconds) => t('verify.resendIn', { seconds })}
          disabled={resendMutation.isPending}
        />
        <FormError message={resendMutation.isError ? resolveErrorMessage(resendMutation.error) : null} />
      </form>
    </AuthLayout>
  );
}

export function LoginOtpPage() {
  const [challenge, setChallenge] = useState<SignupResponse | null>(null);

  if (!challenge) {
    return <RequestCodeStep onRequested={setChallenge} />;
  }
  return <VerifyCodeStep challenge={challenge} />;
}
