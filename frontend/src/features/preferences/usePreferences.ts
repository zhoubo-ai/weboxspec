import { useQuery } from '@tanstack/react-query';
import { api } from '../../shared/api/client';
import type { Preferences } from '../../shared/api/types';
import { useSession } from '../auth/session';
export function usePreferences() {
  const { user } = useSession();
  const query = useQuery({ queryKey: ['preferences', user!.userId], queryFn: ({ signal }) => api<Preferences>('/me/preferences', { signal }) });
  return query;
}
