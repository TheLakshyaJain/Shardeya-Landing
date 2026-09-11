import { useTranslation } from 'react-i18next';
import { PageHeader } from '@/components/layout/PageHeader';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { PlotSizeCalculator } from '../components/PlotSizeCalculator';
import { BrokerageCalculator } from '../components/BrokerageCalculator';
import { StampDutyCalculator } from '../components/StampDutyCalculator';

export function CalculatorsPage() {
  const { t } = useTranslation('calculator');

  return (
    <div>
      <PageHeader title={t('pageTitle')} />
      <Tabs defaultValue="plotSize">
        <TabsList>
          <TabsTrigger value="plotSize">{t('tabs.plotSize')}</TabsTrigger>
          <TabsTrigger value="brokerage">{t('tabs.brokerage')}</TabsTrigger>
          <TabsTrigger value="stampDuty">{t('tabs.stampDuty')}</TabsTrigger>
        </TabsList>
        <TabsContent value="plotSize" className="pt-4">
          <PlotSizeCalculator />
        </TabsContent>
        <TabsContent value="brokerage" className="pt-4">
          <BrokerageCalculator />
        </TabsContent>
        <TabsContent value="stampDuty" className="pt-4">
          <StampDutyCalculator />
        </TabsContent>
      </Tabs>
    </div>
  );
}
