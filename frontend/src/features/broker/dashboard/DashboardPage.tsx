import { useTranslation } from 'react-i18next';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/data/EmptyState';

export function BrokerDashboardPage() {
  const { t } = useTranslation();

  return (
    <>
      <PageHeader title={t('nav.dashboard')} />
      <EmptyState title={t('emptyState.genericTitle')} description={t('placeholder.comingSoon')} />
    </>
  );
}
