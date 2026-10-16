import { useQuery } from '@tanstack/react-query';
import { api } from '../../shared/api/client';
import type { Category, MenuDish, MenuPage } from '../../shared/api/types';
export const categories: Category[] = ['Chinese', 'Western', 'Japanese', 'Light Meal', 'Korean', 'Southeast Asian'];
export function shanghaiDate(now = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(now);
  return ['year', 'month', 'day'].map(name => parts.find(part => part.type === name)!.value).join('-');
}
export function menuDish(date: string, id: string, signal?: AbortSignal): Promise<MenuDish> { return api(`/menus/${date}/dishes/${id}`, { signal }); }
export function useMenuQuery(date: string, selected: Category[], search: string, page: number, recommended = false, enabled = true, epoch = 0) {
  const params = new URLSearchParams({ search, page: String(page), size: '24', recommended: String(recommended) });
  [...selected].sort().forEach(category => params.append('categories', category));
  return useQuery({ queryKey: ['menu', date, params.toString(), epoch], enabled, queryFn: ({ signal }) => api<MenuPage>(`/menus/${date}?${params}`, { signal }) });
}
