import { useMutation } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { LogOut, Settings, Activity } from 'lucide-react';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Avatar, AvatarFallback } from '@/components/ui/avatar';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { useMe } from '@/hooks/useMe';
import { useCan } from '@/hooks/useCan';
import { logout as logoutApi } from '@/features/auth/api/authApi';
import { useAuthStore } from '@/features/auth/store/authStore';

function initialsFor(name: string): string {
  return name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('');
}

export function ProfileMenu() {
  const { t } = useTranslation('auth');
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const org = useAuthStore((s) => s.org);
  const entitlements = useAuthStore((s) => s.entitlements);
  const clear = useAuthStore((s) => s.clear);
  const canViewOps = useCan('SETTINGS_MANAGE');
  useMe();

  const logoutMutation = useMutation({
    mutationFn: () => logoutApi(),
    onSettled: () => {
      clear();
      navigate('/login', { replace: true });
    },
  });

  if (!user) return null;

  const planKey = `profileMenu.plan.${entitlements?.plan.toLowerCase() ?? 'free'}`;

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" className="rounded-full" aria-label={t('profileMenu.openMenu')}>
          <Avatar>
            <AvatarFallback>{initialsFor(user.fullName)}</AvatarFallback>
          </Avatar>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuLabel className="flex flex-col gap-0.5">
          <span className="font-medium text-foreground">{user.fullName}</span>
          <span className="text-xs font-normal text-muted-foreground">{user.email ?? user.mobile}</span>
        </DropdownMenuLabel>
        <div className="px-2 pb-1.5">
          <Badge variant="secondary">{t(planKey, { defaultValue: entitlements?.plan ?? '' })}</Badge>
        </div>
        <DropdownMenuSeparator />
        {org && (
          <DropdownMenuItem onSelect={() => navigate(`/${org.type.toLowerCase()}/settings/notifications`)}>
            <Settings />
            {t('profileMenu.notificationSettings')}
          </DropdownMenuItem>
        )}
        {org && canViewOps && (
          <DropdownMenuItem onSelect={() => navigate(`/${org.type.toLowerCase()}/admin/ops`)}>
            <Activity />
            {t('profileMenu.ops')}
          </DropdownMenuItem>
        )}
        <DropdownMenuItem variant="destructive" onSelect={() => logoutMutation.mutate()}>
          <LogOut />
          {t('profileMenu.logout')}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
