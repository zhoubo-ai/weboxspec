import { useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../shared/api/client';
import type { Meal, OrderPage } from '../../shared/api/types';
import { useSession } from '../auth/session';
import { ErrorNotice, Loading } from '../../shared/ui/Status';
export function ExistingOrderAction({ date, meal, locked, onFound, onReady }: { date: string; meal: Meal; locked: boolean; onFound: (id: string | null) => void; onReady: (ready: boolean) => void }) {
  const { user } = useSession();
  const query = useQuery({ queryKey: ['active-order', user!.userId, date, meal], enabled: !locked, queryFn: async ({ signal }) => {
    for (const status of ['Pending', 'Confirmed']) {
      for (let page = 0; ; page++) {
        const result = await api<OrderPage>(`/orders?status=${status}&size=100&page=${page}`, { signal });
        const match = result.items.find(order => order.deliveryDate === date && order.meal === meal);if (match) return match.orderId;
        if ((page + 1) * 100 >= result.totalElements) break;
      }
    }
    return null;
  } });
  useEffect(() => { onFound(query.data ?? null);onReady(locked || (!query.isPending && !query.isError)); }, [query.data, query.isPending, query.isError, locked, onFound, onReady]);
  if (locked) return null;
  if (query.isPending) return <Loading>Checking for an existing order…</Loading>;
  if (query.isError) return <ErrorNotice message="Existing orders could not be checked." retry={() => { void query.refetch(); }} />;
  return query.data ? <div className="notice"><p>You already have an active order for this delivery date and meal.</p></div> : null;
}
