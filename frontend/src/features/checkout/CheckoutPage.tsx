import { useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { Link, useNavigate } from 'react-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '../../shared/api/client';
import type { Address, ApiErrorBody, Meal, Order, Quote } from '../../shared/api/types';
import { money } from '../../shared/values';
import { ErrorNotice, Loading } from '../../shared/ui/Status';
import { useCart } from '../cart/CartProvider';
import { totalCents, totalQuantity } from '../cart/cartReducer';
import { useSession } from '../auth/session';
import { usePreferences } from '../preferences/usePreferences';
import { CheckoutWarnings } from './CheckoutWarnings';
import { DeliverySelection } from './DeliverySelection';
import { ExistingOrderAction } from './ExistingOrderAction';
type Reviewed = { quote: Quote; fingerprint: string; receivedAt: number };
export function CheckoutPage() {
  const preferences = usePreferences();
  const cart = useCart(), flow = cart.checkout, { user } = useSession(), navigate = useNavigate(), queries = useQueryClient();
  const [deliveryReady, setDeliveryReady] = useState(false), [activeReady, setActiveReady] = useState(false), [existingOrder, setExistingOrder] = useState<string | null>(null);
  const phase = useSyncExternalStore(flow.subscribe, () => flow.phase), sending = phase === 'submitting';
  const resuming = useRef(phase === 'submitting' || phase === 'unknown');
  const [meal, setMeal] = useState<Meal>(['unknown', 'submitting'].includes(flow.phase) && flow.quote ? flow.quote.meal : cart.meal);
  const [addressId, setAddressId] = useState(['unknown', 'submitting'].includes(flow.phase) ? flow.frozen?.request.addressId ?? '' : ''), [addressText, setAddressText] = useState(['unknown', 'submitting'].includes(flow.phase) ? flow.frozen?.request.addressText ?? '' : '');
  const [review, setReview] = useState<Reviewed | null>(['unknown', 'submitting'].includes(flow.phase) && flow.quote ? { quote: flow.quote, fingerprint: '', receivedAt: performance.now() } : null);
  const [reviewing, setReviewing] = useState(false), [error, setError] = useState(''), [stockConflict, setStockConflict] = useState<string | null>(null), [details, setDetails] = useState<ApiErrorBody | null>(null), [, tick] = useState(0);
  const alive = useRef(true), generation = useRef(0), request = useRef<AbortController | null>(null);
  const locked = sending || flow.phase === 'unknown' || flow.phase === 'submitting';
  const fingerprint = JSON.stringify([preferences.data?.version, cart.date, meal, cart.state.lines.map(line => [line.key, line.dish.dishVersion, line.quantity, line.unitPriceCents, line.issue])]);
  const currentFingerprint = useRef(fingerprint);currentFingerprint.current = fingerprint;
  const addresses = useQuery({ queryKey: ['addresses', user!.userId], queryFn: ({ signal }) => api<Address[]>('/me/addresses', { signal }) });
  useEffect(() => { alive.current = true;return () => { alive.current = false;generation.current++;request.current?.abort(); }; }, []);
  useEffect(() => { if (resuming.current && phase === 'succeeded' && flow.result) { resuming.current = false;navigate(`/orders/${flow.result.orderId}`, { replace: true }); } }, [phase, flow, navigate]);
  useEffect(() => {
    if (flow.phase === 'unknown' || flow.phase === 'submitting') return;
    generation.current++;request.current?.abort();setReview(null);setReviewing(false);setDetails(null);
  }, [fingerprint, flow]);
  useEffect(() => { if (!review) return;const timer = setInterval(() => tick(value => value + 1), 1000);return () => clearInterval(timer); }, [review]);
  const remaining = review ? Math.max(0, Date.parse(review.quote.expiresAt) - Date.parse(review.quote.serverTime) - (performance.now() - review.receivedAt)) : 0;
  const usableCart = totalQuantity(cart.state) > 0 && !cart.busy && cart.state.lines.every(line => !line.issue && line.dish.remaining > 0);
  const validCart = usableCart && deliveryReady && activeReady && !existingOrder;
  const summary = review?.quote.items ?? cart.state.lines.map(line => ({ dishId: line.key, dishName: line.dish.name, quantity: line.quantity, unitPriceCents: line.unitPriceCents, subtotalCents: line.unitPriceCents * line.quantity, selectedOptions: line.dish.optionGroups.flatMap(group => group.options.filter(option => line.optionIds.includes(option.optionId)).map(option => ({ optionName: option.name, extraCents: option.extraCents }))) }));
  function explainStock(failure: unknown) {
    if (!(failure instanceof ApiError) || failure.code !== 'INSUFFICIENT_STOCK') return;
    const issue = failure.details.itemIssues?.find(item => item.code === 'INSUFFICIENT_STOCK');
    const name = cart.state.lines.find(line => line.dish.dishId === issue?.dishId)?.dish.name, quantity = issue?.availableQuantity;
    if (name && quantity !== undefined) setStockConflict(`${name} · ${quantity} ${quantity === 1 ? 'portion' : 'portions'} available.`);
  }
  async function reviewOrder() {
    const controller = new AbortController();request.current?.abort();request.current = controller;const active = ++generation.current, startedAt = performance.now();setReviewing(true);setError('');setDetails(null);setStockConflict(null);
    try {
      const quote = await api<Quote>('/checkout/quotes', { method: 'POST', signal: controller.signal, json: { deliveryDate: cart.date, meal, items: cart.state.lines.map(line => ({ dishId: line.dish.dishId, dishVersion: line.dish.dishVersion, optionIds: line.optionIds, quantity: line.quantity })) } });
      if (active === generation.current && alive.current && currentFingerprint.current === fingerprint) setReview({ quote, fingerprint, receivedAt: startedAt });
    } catch (failure) {
      if (active !== generation.current || !alive.current || controller.signal.aborted) return;
      explainStock(failure);setError(failure instanceof Error ? failure.message : 'Could not prepare your order.');if (failure instanceof ApiError) { setDetails(failure.details);if (['DISH_CHANGED', 'DISH_UNAVAILABLE', 'INSUFFICIENT_STOCK'].includes(failure.code)) cart.refresh(); }
    } finally { if (active === generation.current && alive.current) setReviewing(false); }
  }
  async function placeOrder() {
    setError('');setDetails(null);setStockConflict(null);
    try {
      if (flow.phase !== 'unknown' && flow.phase !== 'submitting') {
        if (!review || !remaining || review.fingerprint !== currentFingerprint.current || !validCart) throw new Error('Review current prices and availability before placing your order.');
        flow.prepare(review.quote, addressId ? { addressId } : { addressText });
      }
      const order = await flow.submit(frozen => api<Order>('/orders', { method: 'POST', headers: { 'Idempotency-Key': frozen.key }, json: frozen.request, signal: AbortSignal.timeout(20_000) }), (_order, lines) => {
        cart.dispatch({ type: 'submitted', lines });cart.refresh();void queries.invalidateQueries({ queryKey: ['orders'] });void queries.invalidateQueries({ queryKey: ['active-order'] });void queries.invalidateQueries({ queryKey: ['menu'] });void queries.invalidateQueries({ queryKey: ['addresses'] });
      });
      if (alive.current) navigate(`/orders/${order.orderId}`, { replace: true });
    } catch (failure) {
      if (!alive.current) return;
      if (flow.phase === 'unknown') setError('We could not confirm the result. Your order request is saved in this session. Retry the same request to find out whether it succeeded.');
      else { explainStock(failure);setError(failure instanceof Error ? failure.message : 'Could not place your order.');if (failure instanceof ApiError) { setDetails(failure.details);setReview(null);if (['DISH_CHANGED', 'DISH_UNAVAILABLE', 'INSUFFICIENT_STOCK'].includes(failure.code)) cart.refresh(); } }
    }
  }
  return <section><div className="page-heading"><div><p className="eyebrow">ONE LAST LOOK</p><h1>Checkout</h1><p>Confirm your meal, delivery details and exact total.</p></div><Link to="/cart">Back to cart</Link></div>
    {error && <ErrorNotice message={error} />}{stockConflict && <p role="status" className="notice error">{stockConflict}</p>}{cart.notice && <ErrorNotice message={cart.notice} retry={cart.refresh} />}
    {details?.existingOrderId && !existingOrder && <Link className="button" to={`/orders/${details.existingOrderId}`}>View Existing Order</Link>}
    {!usableCart && !locked && <p className="notice">Review changed or unavailable items in <Link to="/cart">your cart</Link> before continuing.</p>}
    <ExistingOrderAction date={cart.date} meal={meal} locked={locked} onFound={setExistingOrder} onReady={setActiveReady} />
    <div className="cart-layout"><div className="panel"><h2>Delivery details</h2><DeliverySelection suggested={details?.suggestedDeliveryDate && details.suggestedMeal ? { date: details.suggestedDeliveryDate, meal: details.suggestedMeal } : null} date={locked && flow.quote ? flow.quote.deliveryDate : cart.date} meal={locked && flow.quote ? flow.quote.meal : meal} locked={locked} onChange={slot => { cart.setDate(slot.date);cart.setMeal(slot.meal);setMeal(slot.meal); }} onReady={setDeliveryReady} /><fieldset className="checkout-fields" disabled={locked}>
    {addresses.isPending && <Loading>Loading saved addresses…</Loading>}{addresses.isError && <ErrorNotice message="Saved addresses could not be loaded. You can enter a new address or try again." retry={() => { void addresses.refetch(); }} />}
    <div className="field"><label htmlFor="saved-address">Delivery address</label><select id="saved-address" value={addressId} onChange={event => setAddressId(event.target.value)}><option value="">Enter a new address</option>{addresses.data?.map(address => <option key={address.addressId} value={address.addressId}>{address.text}</option>)}</select></div>
    {!addressId && <div className="field"><label htmlFor="address-text">New address</label><textarea id="address-text" rows={3} value={addressText} onChange={event => setAddressText(event.target.value)} aria-describedby="address-hint" /><small id="address-hint">{Array.from(addressText).length}/200 characters. Saved only after your order succeeds.</small></div>}</fieldset></div>
    <aside className="panel cart-summary"><h2>Order summary</h2>{summary.map((line, index) => <div className="summary-line" key={`${line.dishId}-${index}`}><span>{line.quantity} × {line.dishName}<small>{line.selectedOptions.map(option => `${option.optionName} (+¥${money(option.extraCents)})`).join(' · ') || 'Standard preparation'}</small><small>¥{money(line.unitPriceCents)} each</small></span><strong>¥{money(line.subtotalCents)}</strong></div>)}<p className="cart-total">{review ? 'Total' : 'Estimate'} <strong>¥{money(review?.quote.totalCents ?? totalCents(cart.state))}</strong></p>
    {review && <CheckoutWarnings quote={review.quote} />}
    {review && <p role="status">{remaining ? `Quote valid for ${Math.ceil(remaining / 1000)} seconds.` : 'This quote has expired. Review your order again.'}</p>}
    {locked ? <button type="button" disabled={sending} onClick={() => { void placeOrder(); }}>{sending ? 'Confirming your order…' : 'Retry same order request'}</button> : existingOrder ? <Link className="button" to={`/orders/${existingOrder}`}>View Existing Order</Link> : review && remaining > 0 && validCart ? <button type="button" onClick={() => { void placeOrder(); }}>Place Order</button> : <button type="button" disabled={!validCart || reviewing} onClick={() => { void reviewOrder(); }}>{reviewing ? 'Checking your order…' : 'Review order'}</button>}
    <p className="form-hint">Quotes expire after five minutes. Prices and availability are checked again when you place your order.</p></aside></div>
  </section>;
}
