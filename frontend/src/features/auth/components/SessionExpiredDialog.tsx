import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { useAuthStore } from '../store/authStore';

// Mounted once, globally (see AppProviders) — apiFetch's refresh-on-401
// interceptor sets authStore.sessionExpired when a refresh attempt fails,
// which is the only thing that opens this dialog.
export function SessionExpiredDialog() {
  const { t } = useTranslation('auth');
  const navigate = useNavigate();
  const sessionExpired = useAuthStore((s) => s.sessionExpired);
  const clear = useAuthStore((s) => s.clear);

  const handleLoginAgain = () => {
    clear();
    navigate('/login');
  };

  return (
    <Dialog open={sessionExpired}>
      <DialogContent showCloseButton={false} onEscapeKeyDown={(e) => e.preventDefault()} onInteractOutside={(e) => e.preventDefault()}>
        <DialogHeader>
          <DialogTitle>{t('sessionExpired.title')}</DialogTitle>
          <DialogDescription>{t('sessionExpired.description')}</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button onClick={handleLoginAgain}>{t('sessionExpired.action')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
