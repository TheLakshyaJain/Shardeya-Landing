import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { AuthLayout } from '../components/AuthLayout';
import { RoleSelectCard } from '../components/RoleSelectCard';
import { TermsCheckbox } from '../components/TermsCheckbox';
import { PhoneInput } from '@/components/forms/PhoneInput';
import { PasswordInput } from '@/components/forms/PasswordInput';
import { FormError } from '@/components/forms/FormError';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import { buildSignupSchema, type SignupFormValues } from '../schemas';
import { signup } from '../api/authApi';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import type { OrgType } from '../types';

export function SignupPage() {
  const { t } = useTranslation(['auth', 'errors', 'common']);
  const navigate = useNavigate();
  const [role, setRole] = useState<OrgType | null>(null);
  const [roleError, setRoleError] = useState<string | null>(null);

  const schema = buildSignupSchema(t);
  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors },
  } = useForm<SignupFormValues>({
    resolver: zodResolver(schema),
    defaultValues: { acceptTerms: false },
  });

  const mutation = useMutation({
    mutationFn: signup,
    onSuccess: (res) => {
      navigate('/signup/verify', {
        state: {
          challengeId: res.challengeId,
          maskedRecipient: res.maskedRecipient,
          resendAfterSeconds: res.resendAfterSeconds,
        },
      });
    },
  });

  const onSubmit = (values: SignupFormValues) => {
    if (!role) {
      setRoleError(t('role.required', { ns: 'errors' }));
      return;
    }
    setRoleError(null);
    mutation.mutate({ ...values, role });
  };

  return (
    <AuthLayout
      title={t('signup.title')}
      subtitle={t('signup.subtitle')}
      footer={
        <span className="text-muted-foreground">
          {t('signup.haveAccount')}{' '}
          <Link to="/login" className="font-medium text-primary hover:underline">
            {t('signup.login')}
          </Link>
        </span>
      }
    >
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        <div className="space-y-2">
          <Label>{t('roleSelect.title')}</Label>
          <div className="grid grid-cols-2 gap-2">
            <RoleSelectCard
              value="BUILDER"
              label={t('roleSelect.builder.label')}
              description={t('roleSelect.builder.description')}
              selected={role === 'BUILDER'}
              onSelect={setRole}
            />
            <RoleSelectCard
              value="BROKER"
              label={t('roleSelect.broker.label')}
              description={t('roleSelect.broker.description')}
              selected={role === 'BROKER'}
              onSelect={setRole}
            />
          </div>
          <FormError message={roleError} />
        </div>

        <div className="space-y-2">
          <Label htmlFor="fullName">{t('signup.fullName')}</Label>
          <Input id="fullName" autoComplete="name" aria-invalid={!!errors.fullName} {...register('fullName')} />
          <FormError message={errors.fullName?.message} />
        </div>

        <div className="space-y-2">
          <Label htmlFor="mobile">{t('signup.mobile')}</Label>
          <PhoneInput id="mobile" aria-invalid={!!errors.mobile} {...register('mobile')} />
          <FormError message={errors.mobile?.message} />
        </div>

        <div className="space-y-2">
          <Label htmlFor="email">{t('signup.email')}</Label>
          <Input id="email" type="email" autoComplete="email" aria-invalid={!!errors.email} {...register('email')} />
          <FormError message={errors.email?.message} />
        </div>

        <div className="space-y-2">
          <Label htmlFor="city">{t('signup.city')}</Label>
          <Input id="city" autoComplete="address-level2" aria-invalid={!!errors.city} {...register('city')} />
          <FormError message={errors.city?.message} />
        </div>

        <div className="space-y-2">
          <Label htmlFor="password">{t('signup.password')}</Label>
          <PasswordInput
            id="password"
            autoComplete="new-password"
            aria-invalid={!!errors.password}
            showLabel={t('actions.showPassword', { ns: 'common' })}
            hideLabel={t('actions.hidePassword', { ns: 'common' })}
            {...register('password')}
          />
          <p className="text-xs text-muted-foreground">{t('fields.passwordHint')}</p>
          <FormError message={errors.password?.message} />
        </div>

        <div className="space-y-2">
          <Label htmlFor="confirmPassword">{t('signup.confirmPassword')}</Label>
          <PasswordInput
            id="confirmPassword"
            autoComplete="new-password"
            aria-invalid={!!errors.confirmPassword}
            showLabel={t('actions.showPassword', { ns: 'common' })}
            hideLabel={t('actions.hidePassword', { ns: 'common' })}
            {...register('confirmPassword')}
          />
          <FormError message={errors.confirmPassword?.message} />
        </div>

        <TermsCheckbox
          checked={watch('acceptTerms')}
          onCheckedChange={(checked) => setValue('acceptTerms', checked, { shouldValidate: true })}
          label={t('signup.terms')}
        />
        <FormError message={errors.acceptTerms?.message} />

        <FormError message={mutation.isError ? resolveErrorMessage(mutation.error) : null} />

        <Button type="submit" className="w-full" disabled={mutation.isPending}>
          {t('signup.submit')}
        </Button>
      </form>
    </AuthLayout>
  );
}
