import { useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getMe } from '@/features/auth/api/authApi';
import { useAuthStore } from '@/features/auth/store/authStore';

/**
 * Bootstraps the authenticated user's profile/org/permissions from `/me`.
 * Only runs when a session exists (there's an access token in memory) — on a
 * fresh page load with no token, this is deliberately a no-op (see
 * authStore's javadoc-equivalent comment on why sessions aren't persisted).
 */
export function useMe() {
  const accessToken = useAuthStore((s) => s.accessToken);
  const setMe = useAuthStore((s) => s.setMe);

  const query = useQuery({
    queryKey: ['me'],
    queryFn: getMe,
    enabled: accessToken !== null,
    staleTime: 60_000,
    retry: false,
  });

  useEffect(() => {
    if (query.data) {
      setMe(query.data);
    }
  }, [query.data, setMe]);

  return query;
}
