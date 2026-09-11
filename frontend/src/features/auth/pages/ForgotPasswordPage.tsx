import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { AuthLayout } from '../components/AuthLayout';
import { NewPasswordFields } from '../components/NewPasswordFields';
import { OtpInput } from '@/components/forms/OtpInput';
import { FormError } from '@/components/forms/FormError';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import { buildForgotPasswordSchema, buildResetPasswordSchema, type ForgotPasswordFormValues, type ResetPasswordFormValues } from '../schemas';
import { forgotPassword, resetPassword } from '../api/authApi';
import { resolveErrorMessage } from '@/lib/api/errorMessage';

// Backend's own isMobile check (AuthService.forgotPassword) — mirrored here
// purely to decide which step to render next, not for validation.
const MOBILE_PATTERN = /^\d{10}$/;

interface MobileChallenge {
  challengeId: string;
  maskedMobile: string;
}

function IdentifierStep({
  onMobileChallenge,
  onEmailSent,
}: {
  onMobileChallenge: (challenge: MobileChallenge) => void;
  onEmailSent: () => void;
}) {
  const { t } = useTranslation('auth');
  const schema = buildForgotPasswordSchema(t);
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ForgotPasswordFormValues>({ resolver: zodResolver(schema) });

  const mutation = useMutation({
    mutationFn: forgotPassword,
    onSuccess: (res, variables) => {
      if (MOBILE_PATTERN.test(variables.identifier) && res.challengeId) {
        onMobileChallenge({ challengeId: res.challengeId, maskedMobile: res.maskedRecipient });
      } else {
        onEmailSent();
      }
    },
  });

  return (
    <AuthLayout
      title={t('forgotPassword.title')}
      subtitle={t('forgotPassword.subtitle')}
      footer={
        <Link to="/login" className="font-medium text-primary hover:underline">
          {t('forgotPassword.backToLogin')}
        </Link>
      }
    >
      <form onSubmit={handleSubmit((v) => mutation.mutate(v))} className="space-y-4" noValidate>
        <div className="space-y-2">
          <Label htmlFor="identifier">{t('forgotPassword.identifier')}</Label>
          <Input id="identifier" aria-invalid={!!errors.identifier} {...register('identifier')} />
          <FormError message={errors.identifier?.message} />
        </div>
        <FormError message={mutation.isError ? resolveErrorMessage(mutation.error) : null} />
        <Button type="submit" className="w-full" disabled={mutation.isPending}>
          {t('forgotPassword.submit')}
        </Button>
      </form>
    </AuthLayout>
  );
}

function MobileResetStep({ challenge }: { challenge: MobileChallenge }) {
  const { t } = useTranslation('auth');
  const navigate = useNavigate();
  const [code, setCode] = useState('');

  const schema = buildResetPasswordSchema(t);
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ResetPasswordFormValues>({ resolver: zodResolver(schema) });

  const mutation = useMutation({
    mutationFn: resetPassword,
    onSuccess: () => navigate('/login', { replace: true, state: { resetSuccess: true } }),
  });

  const onSubmit = (values: ResetPasswordFormValues) => {
    mutation.mutate({ challengeId: challenge.challengeId, code, ...values });
  };

  return (
    <AuthLayout title={t('forgotPassword.mobileSentTitle')} subtitle={t('forgotPassword.mobileSentSubtitle', { mobile: challenge.maskedMobile })}>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        <div className="space-y-2">
          <OtpInput value={code} onChange={setCode} autoFocus digitLabel={(pos) => t('fields.otpDigit', { position: pos })} />
        </div>
        <NewPasswordFields register={register} errors={errors} />
        <FormError message={mutation.isError ? resolveErrorMessage(mutation.error) : null} />
        <Button type="submit" className="w-full" disabled={code.length !== 6 || mutation.isPending}>
          {t('resetPassword.submit')}
        </Button>
      </form>
    </AuthLayout>
  );
}

function EmailSentStep() {
  const { t } = useTranslation('auth');
  return (
    <AuthLayout title={t('forgotPassword.emailSentTitle')} subtitle={t('forgotPassword.emailSentSubtitle')}>
      <Link to="/login" className="block text-center text-sm font-medium text-primary hover:underline">
        {t('forgotPassword.backToLogin')}
      </Link>
    </AuthLayout>
  );
}

type Step = { kind: 'identifier' } | { kind: 'mobile'; challenge: MobileChallenge } | { kind: 'email' };

export function ForgotPasswordPage() {
  const [step, setStep] = useState<Step>({ kind: 'identifier' });

  if (step.kind === 'mobile') {
    return <MobileResetStep challenge={step.challenge} />;
  }
  if (step.kind === 'email') {
    return <EmailSentStep />;
  }
  return (
    <IdentifierStep
      onMobileChallenge={(challenge) => setStep({ kind: 'mobile', challenge })}
      onEmailSent={() => setStep({ kind: 'email' })}
    />
  );
}
