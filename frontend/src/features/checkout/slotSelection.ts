import type { Meal } from '../../shared/api/types';
import { shanghaiDate } from '../menu/menuQuery';
export type Slot = { date: string; meal: Meal };
export type Selection = { slot: Slot; changed: boolean; error: string | null; notice: string | null };
export function selectSlot(current: Slot, serverNow: number): Selection {
  const unchanged = { slot: current, changed: false, error: null, notice: null };
  if (!Number.isFinite(serverNow)) return { ...unchanged, error: 'The server time is unavailable. Please try again.' };
  const today = shanghaiDate(new Date(serverNow));
  if (current.date < today) return { ...unchanged, error: 'Choose today or a future delivery date.' };
  if (current.date > today) return unchanged;
  const hour = Number(new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Shanghai', hour: '2-digit', hourCycle: 'h23' }).format(new Date(serverNow)));
  if (hour < (current.meal === 'Lunch' ? 10 : 15)) return unchanged;
  const tomorrow = new Date(Date.parse(`${today}T00:00:00Z`) + 86_400_000).toISOString().slice(0, 10);
  const slot: Slot = hour < 15 ? { date: today, meal: 'Dinner' } : { date: tomorrow, meal: 'Lunch' };
  return { slot, changed: true, error: null, notice: `${current.meal} has closed. We selected ${slot.meal} on ${slot.date}. Confirm the new delivery selection before reviewing your order.` };
}
