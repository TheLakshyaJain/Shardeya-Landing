import { useTranslation } from 'react-i18next';
import { PageHeader } from '@/components/layout/PageHeader';
import { NotificationPreferenceMatrix } from '../components/NotificationPreferenceMatrix';
import { WhatsAppOptInCard } from '../components/WhatsAppOptInCard';

export function NotificationSettingsPage() {
  const { t } = useTranslation('settings');
  return (
    <div className="space-y-6">
      <PageHeader title={t('notifications.title')} description={t('notifications.subtitle')} />
      <WhatsAppOptInCard />
      <NotificationPreferenceMatrix />
    </div>
  );
}
