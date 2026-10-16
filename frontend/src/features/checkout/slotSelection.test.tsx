import { expect, it } from 'vitest';
import { selectSlot } from './slotSelection';
const now = (time: string) => Date.parse(`2026-10-16T${time}+08:00`);
it('keeps valid slots and changes only closed same-day slots at the exact cutoff', () => {
  expect(selectSlot({ date: '2026-10-16', meal: 'Lunch' }, now('09:59:59')).changed).toBe(false);
  const lunch = selectSlot({ date: '2026-10-16', meal: 'Lunch' }, now('10:00:00'));expect(lunch.slot).toEqual({ date: '2026-10-16', meal: 'Dinner' });expect(lunch.notice).toMatch(/confirm/i);
  expect(selectSlot({ date: '2026-10-16', meal: 'Dinner' }, now('14:59:59')).changed).toBe(false);
  expect(selectSlot({ date: '2026-10-16', meal: 'Dinner' }, now('15:00:00')).slot).toEqual({ date: '2026-10-17', meal: 'Lunch' });
});
it('preserves future selections, rejects past dates and never skips past an empty suggested date', () => {
  expect(selectSlot({ date: '2026-10-20', meal: 'Dinner' }, now('23:59:59')).slot).toEqual({ date: '2026-10-20', meal: 'Dinner' });
  const past = selectSlot({ date: '2026-10-15', meal: 'Dinner' }, now('09:00:00'));expect(past.error).toMatch(/past|today/i);expect(past.changed).toBe(false);
  expect(selectSlot({ date: '2026-10-16', meal: 'Lunch' }, now('23:59:59')).slot.date).toBe('2026-10-17');
});

// The full cross-browser cart/order rules matrix lives in tests/e2e/order-rules.spec.ts.
import { useState } from 'react';
import { afterEach, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import * as client from '../../shared/api/client';
import { DeliverySelection } from './DeliverySelection';
import type { Slot } from './slotSelection';
afterEach(() => vi.restoreAllMocks());
function SelectionHarness({ locked = false }: { locked?: boolean }) {
  const [slot, setSlot] = useState<Slot>({ date: '2026-10-16', meal: 'Lunch' }), [ready, setReady] = useState(false);
  return <><DeliverySelection {...slot} locked={locked} onChange={setSlot} onReady={setReady} /><button disabled={!ready}>Review</button></>;
}
it('refreshes server time on focus and requires explicit confirmation after switching', async () => {
  let serverTime = '2026-10-16T09:59:58+08:00';
  vi.spyOn(client, 'api').mockImplementation(async () => ({ serverTime } as never));
  const queries = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<QueryClientProvider client={queries}><SelectionHarness /></QueryClientProvider>);
  await waitFor(() => expect(screen.getByRole('button', { name: /^Review$/ })).toBeEnabled());
  serverTime = '2026-10-16T10:00:00+08:00';fireEvent(window, new Event('focus'));
  await waitFor(() => expect(screen.getByLabelText('Meal')).toHaveValue('Dinner'));
  expect(screen.getByRole('button', { name: /^Review$/ })).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: 'Confirm delivery selection' }));
  await waitFor(() => expect(screen.getByRole('button', { name: /^Review$/ })).toBeEnabled());queries.clear();
});
it('preserves a frozen submission when the window regains focus after the cutoff', async () => {
  const request = vi.spyOn(client, 'api').mockResolvedValue({ serverTime: '2026-10-16T15:00:00+08:00' } as never);
  const queries = new QueryClient();render(<QueryClientProvider client={queries}><SelectionHarness locked /></QueryClientProvider>);
  fireEvent(window, new Event('focus'));expect(screen.getByLabelText('Meal')).toHaveValue('Lunch');expect(screen.getByLabelText('Meal')).toBeDisabled();expect(request).not.toHaveBeenCalled();queries.clear();
});
