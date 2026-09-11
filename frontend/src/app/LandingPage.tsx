import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';

export function LandingPage() {
  const { t } = useTranslation();

  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-6 bg-background p-6">
      <h1 className="text-2xl font-semibold text-foreground">{t('app.name')}</h1>
      <div className="grid w-full max-w-2xl gap-4 sm:grid-cols-3">
        <Card>
          <CardHeader>
            <CardTitle>{t('nav.builder')}</CardTitle>
            <CardDescription>Builder tenant shell</CardDescription>
          </CardHeader>
          <CardContent>
            <Button asChild className="w-full">
              <Link to="/builder/dashboard">{t('nav.dashboard')}</Link>
            </Button>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>{t('nav.broker')}</CardTitle>
            <CardDescription>Broker tenant shell</CardDescription>
          </CardHeader>
          <CardContent>
            <Button asChild variant="secondary" className="w-full">
              <Link to="/broker/dashboard">{t('nav.dashboard')}</Link>
            </Button>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>{t('nav.design')}</CardTitle>
            <CardDescription>Component library</CardDescription>
          </CardHeader>
          <CardContent>
            <Button asChild variant="outline" className="w-full">
              <Link to="/design">{t('nav.design')}</Link>
            </Button>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
