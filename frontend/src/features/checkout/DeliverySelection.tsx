import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../shared/api/client';
import type { Meal, MenuPage } from '../../shared/api/types';
import { ErrorNotice, Loading } from '../../shared/ui/Status';
import { selectSlot, type Slot } from './slotSelection';
export function DeliverySelection({ date, meal, locked, suggested, onChange, onReady }: { date: string; meal: Meal; locked: boolean; suggested?: Slot | null; onChange: (slot: Slot) => void; onReady: (ready: boolean) => void }) {
  const [notice, setNotice] = useState<string | null>(null), [confirmation, setConfirmation] = useState(false), [error, setError] = useState<string | null>(null), [, tick] = useState(0);
  const time = useQuery({ queryKey: ['business-clock', date], enabled: !locked, queryFn: async ({ signal }) => {
    const response = await api<MenuPage>(`/menus/${date}?size=1`, { signal });return { serverTime: Date.parse(response.serverTime), receivedAt: performance.now() };
  } });
  const refetchTime = time.refetch;
  useEffect(() => { if (locked) return;const focus = () => { void refetchTime(); };window.addEventListener('focus', focus);return () => window.removeEventListener('focus', focus); }, [locked, refetchTime]);
  useEffect(() => {
    if (locked || !suggested) return;
    onReady(false);setConfirmation(true);setNotice(`Your selected meal has closed. Delivery changed to ${suggested.date} · ${suggested.meal}. Please confirm the new selection before reviewing your order.`);onChange(suggested);void refetchTime();
  }, [suggested?.date, suggested?.meal, locked]);
  useEffect(() => { const timer = setInterval(() => tick(value => value + 1), 1000);return () => clearInterval(timer); }, []);
  const now = time.data ? time.data.serverTime + performance.now() - time.data.receivedAt : null;
  useEffect(() => {
    if (locked || now === null) return;
    const result = selectSlot({ date, meal }, now);setError(result.error);
    if (result.changed) { onReady(false);setConfirmation(true);setNotice(result.notice);onChange(result.slot); }
  }, [date, meal, locked, now, onChange]);
  const currentSelection=now===null?null:selectSlot({date,meal},now),validSelection=currentSelection!==null && !currentSelection.changed && !currentSelection.error;
  useEffect(() => { onReady(locked || (validSelection && !time.isError && !error && !confirmation)); }, [locked, validSelection, time.isError, error, confirmation, onReady]);
  const manual = (slot: Slot) => { setNotice(null);setConfirmation(false);onChange(slot); };
  return <><fieldset className="checkout-fields" disabled={locked}><div className="field"><label htmlFor="checkout-date">Delivery date</label><input id="checkout-date" type="date" value={date} onChange={event => { if (/^\d{4}-\d{2}-\d{2}$/.test(event.target.value)) manual({ date: event.target.value, meal }); }} /></div><div className="field"><label htmlFor="checkout-meal">Meal</label><select id="checkout-meal" value={meal} onChange={event => manual({ date, meal: event.target.value as Meal })}><option>Lunch</option><option>Dinner</option></select><small>Same-day orders close at 10:00 for lunch and 15:00 for dinner (Asia/Shanghai).</small></div></fieldset>
    {time.isPending && !locked && <Loading>Checking the delivery time…</Loading>}{time.isError && !locked && <ErrorNotice message="The delivery time could not be checked." retry={() => { void time.refetch(); }} />}{error && !locked && <p role="alert" className="notice error">{error}</p>}
    {notice && <div className="notice" role="status"><p>{notice}</p>{confirmation && <button type="button" disabled={locked} onClick={() => { setConfirmation(false);setNotice(null); }}>Confirm delivery selection</button>}</div>}
  </>;
}
