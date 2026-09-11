import type { FieldErrors, UseFormRegister } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { PasswordInput } from '@/components/forms/PasswordInput';
import { FormError } from '@/components/forms/FormError';
import { Label } from '@/components/ui/label';
import type { ResetPasswordFormValues } from '../schemas';

interface NewPasswordFieldsProps {
  register: UseFormRegister<ResetPasswordFormValues>;
  errors: FieldErrors<ResetPasswordFormValues>;
}

// Shared by ForgotPasswordPage's mobile-OTP reset step and ResetPasswordPage's
// emailed-link step — both collect the same newPassword/confirmPassword pair
// against the same buildResetPasswordSchema.
export function NewPasswordFields({ register, errors }: NewPasswordFieldsProps) {
  const { t } = useTranslation(['auth', 'common']);

  return (
    <>
      <div className="space-y-2">
        <Label htmlFor="newPassword">{t('resetPassword.newPassword')}</Label>
        <PasswordInput
          id="newPassword"
          autoComplete="new-password"
          aria-invalid={!!errors.newPassword}
          showLabel={t('actions.showPassword', { ns: 'common' })}
          hideLabel={t('actions.hidePassword', { ns: 'common' })}
          {...register('newPassword')}
        />
        <p className="text-xs text-muted-foreground">{t('fields.passwordHint')}</p>
        <FormError message={errors.newPassword?.message} />
      </div>
      <div className="space-y-2">
        <Label htmlFor="confirmPassword">{t('resetPassword.confirmPassword')}</Label>
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
    </>
  );
}
