import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { AuthLayout } from '../components/AuthLayout';
import { PasswordInput } from '@/components/forms/PasswordInput';
import { FormError } from '@/components/forms/FormError';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { buildLoginSchema, type LoginFormValues } from '../schemas';
import { login } from '../api/authApi';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { useAuthStore } from '../store/authStore';
import { dashboardPathFor } from '../redirect';

export function LoginPage() {
  const { t } = useTranslation(['auth', 'common']);
  const navigate = useNavigate();
  const setSession = useAuthStore((s) => s.setSession);

  const schema = buildLoginSchema(t);
  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(schema),
    defaultValues: { identifier: '', password: '', rememberMe: false },
  });

  const mutation = useMutation({
    mutationFn: login,
    onSuccess: (tokens) => {
      setSession(tokens);
      navigate(dashboardPathFor(tokens.org.type), { replace: true });
    },
  });

  return (
    <AuthLayout
      title={t('login.title')}
      subtitle={t('login.subtitle')}
      footer={
        <span className="text-muted-foreground">
          {t('login.noAccount')}{' '}
          <Link to="/signup" className="font-medium text-primary hover:underline">
            {t('login.signup')}
          </Link>
        </span>
      }
    >
      <form onSubmit={handleSubmit((v) => mutation.mutate(v))} className="space-y-4" noValidate>
        <div className="space-y-2">
          <Label htmlFor="identifier">{t('login.identifier')}</Label>
          <Input id="identifier" autoComplete="username" aria-invalid={!!errors.identifier} {...register('identifier')} />
          <FormError message={errors.identifier?.message} />
        </div>

        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <Label htmlFor="password">{t('login.password')}</Label>
            <Link to="/forgot-password" className="text-xs text-primary hover:underline">
              {t('login.forgotPassword')}
            </Link>
          </div>
          <PasswordInput
            id="password"
            autoComplete="current-password"
            aria-invalid={!!errors.password}
            showLabel={t('actions.showPassword', { ns: 'common' })}
            hideLabel={t('actions.hidePassword', { ns: 'common' })}
            {...register('password')}
          />
          <FormError message={errors.password?.message} />
        </div>

        <div className="flex items-center gap-2">
          <Checkbox
            id="rememberMe"
            checked={watch('rememberMe')}
            onCheckedChange={(c) => setValue('rememberMe', c === true)}
          />
          <Label htmlFor="rememberMe" className="text-sm font-normal text-muted-foreground">
            {t('login.rememberMe')}
          </Label>
        </div>

        <FormError message={mutation.isError ? resolveErrorMessage(mutation.error) : null} />

        <Button type="submit" className="w-full" disabled={mutation.isPending}>
          {t('login.submit')}
        </Button>

        <Button type="button" variant="outline" className="w-full" asChild>
          <Link to="/login/otp">{t('login.withOtp')}</Link>
        </Button>
      </form>
    </AuthLayout>
  );
}
