import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { AuthLayout } from '../components/AuthLayout';
import { NewPasswordFields } from '../components/NewPasswordFields';
import { FormError } from '@/components/forms/FormError';
import { Button } from '@/components/ui/button';
import { buildResetPasswordSchema, type ResetPasswordFormValues } from '../schemas';
import { resetPassword } from '../api/authApi';
import { resolveErrorMessage } from '@/lib/api/errorMessage';

export function ResetPasswordPage() {
  const { t } = useTranslation('auth');
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');

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

  if (!token) {
    return (
      <AuthLayout title={t('resetPassword.title')}>
        <FormError message={t('resetPassword.invalidLink')} className="mb-4" />
        <Link to="/forgot-password" className="block text-center text-sm font-medium text-primary hover:underline">
          {t('forgotPassword.backToLogin')}
        </Link>
      </AuthLayout>
    );
  }

  const onSubmit = (values: ResetPasswordFormValues) => {
    mutation.mutate({ token, ...values });
  };

  return (
    <AuthLayout title={t('resetPassword.title')} subtitle={t('resetPassword.subtitle')}>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        <NewPasswordFields register={register} errors={errors} />
        <FormError message={mutation.isError ? resolveErrorMessage(mutation.error) : null} />
        <Button type="submit" className="w-full" disabled={mutation.isPending}>
          {t('resetPassword.submit')}
        </Button>
      </form>
    </AuthLayout>
  );
}
