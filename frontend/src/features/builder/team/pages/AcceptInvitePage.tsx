import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { AuthLayout } from '@/features/auth/components/AuthLayout';
import { PasswordInput } from '@/components/forms/PasswordInput';
import { FormError } from '@/components/forms/FormError';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { acceptInvite } from '../api/teamApi';

/** B-12 §8: "sets password -> logs in" — this page only sets the password; a separate login afterward is the intended flow, matching AuthService.acceptInvite's own contract. */
export function AcceptInvitePage() {
  const { t } = useTranslation(['team', 'common']);
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const mutation = useMutation({
    mutationFn: () => acceptInvite(token ?? '', password, confirmPassword),
  });

  if (!token) {
    return (
      <AuthLayout title={t('acceptInvite.title')}>
        <FormError message={t('errors:auth.inviteTokenInvalid', { defaultValue: 'This invite link is invalid.' })} />
      </AuthLayout>
    );
  }

  if (mutation.isSuccess) {
    return (
      <AuthLayout title={t('acceptInvite.title')}>
        <p className="mb-4 text-sm text-muted-foreground">{t('acceptInvite.success')}</p>
        <Button asChild className="w-full">
          <Link to="/login">{t('acceptInvite.goToLogin')}</Link>
        </Button>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout title={t('acceptInvite.title')} subtitle={t('acceptInvite.description')}>
      <form
        className="space-y-4"
        noValidate
        onSubmit={(e) => {
          e.preventDefault();
          mutation.mutate();
        }}
      >
        <div className="space-y-2">
          <Label htmlFor="invite-password">{t('acceptInvite.password')}</Label>
          <PasswordInput
            id="invite-password"
            autoComplete="new-password"
            showLabel={t('actions.showPassword', { ns: 'common' })}
            hideLabel={t('actions.hidePassword', { ns: 'common' })}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="invite-confirmPassword">{t('acceptInvite.confirmPassword')}</Label>
          <PasswordInput
            id="invite-confirmPassword"
            autoComplete="new-password"
            showLabel={t('actions.showPassword', { ns: 'common' })}
            hideLabel={t('actions.hidePassword', { ns: 'common' })}
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
          />
        </div>
        <FormError message={mutation.isError ? resolveErrorMessage(mutation.error) : null} />
        <Button type="submit" className="w-full" disabled={!password || !confirmPassword || mutation.isPending}>
          {mutation.isPending ? t('acceptInvite.submitting') : t('acceptInvite.submit')}
        </Button>
      </form>
    </AuthLayout>
  );
}
