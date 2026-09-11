import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Building2, CheckCircle2, Circle, Upload, UserPlus } from 'lucide-react';

// B-01 §7/§10: "a new builder with zero projects sees a three-step
// onboarding checklist (Create project -> Add plots -> Add your first
// lead) instead of ten zeroes."
export function EmptyDashboard() {
  const { t } = useTranslation('dashboard');
  const steps = [
    { to: '/builder/projects/new', label: t('onboarding.step1'), icon: Building2, done: false },
    { to: '/builder/projects', label: t('onboarding.step2'), icon: Upload, done: false },
    { to: '/builder/leads', label: t('onboarding.step3'), icon: UserPlus, done: false },
  ];

  return (
    <div className="rounded-lg border border-dashed p-8 text-center">
      <h2 className="mb-2 text-lg font-semibold">{t('onboarding.title')}</h2>
      <p className="mb-6 text-sm text-muted-foreground">{t('onboarding.subtitle')}</p>
      <ol className="mx-auto max-w-sm space-y-3 text-left">
        {steps.map((s) => (
          <li key={s.to}>
            <Link to={s.to} className="flex items-center gap-3 rounded-md border p-3 hover:bg-accent/50">
              {s.done ? <CheckCircle2 className="size-5 text-green-600" /> : <Circle className="size-5 text-muted-foreground" />}
              <s.icon className="size-4" />
              <span className="text-sm">{s.label}</span>
            </Link>
          </li>
        ))}
      </ol>
    </div>
  );
}
