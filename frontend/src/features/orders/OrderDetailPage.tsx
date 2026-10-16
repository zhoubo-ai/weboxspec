import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../shared/api/client';
import type { Order } from '../../shared/api/types';
import { money } from '../../shared/values';
import { ErrorNotice, Loading } from '../../shared/ui/Status';
import { useSession } from '../auth/session';
import { useCart } from '../cart/CartProvider';
export function OrderDetailPage() {
  const { orderId = '' } = useParams(), { user } = useSession(), queries = useQueryClient(), cart = useCart();
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  const query = useQuery({ queryKey: ['orders', user!.userId, 'detail', orderId], queryFn: ({ signal }) => api<Order>(`/orders/${encodeURIComponent(orderId)}`, { signal }) });
  async function cancel() {
    setBusy(true);setError('');
    try { await api<Order>(`/orders/${encodeURIComponent(orderId)}/cancel`, { method: 'POST' });void queries.invalidateQueries({ queryKey: ['orders'] });void queries.invalidateQueries({ queryKey: ['active-order'] });void queries.invalidateQueries({ queryKey: ['menu'] });cart.refresh(); }
    catch { setError('The cancellation result could not be confirmed. Check the current status below before trying again.'); }
    finally { await query.refetch();setBusy(false); }
  }
  return <section><div className="page-heading"><div><p className="eyebrow">YOUR WORKDAY MEAL</p><h1>Order details</h1></div><Link to="/orders">All orders</Link></div>{query.isPending && <Loading>Loading your order…</Loading>}{query.isError && <ErrorNotice message="This order could not be loaded. It may not be available to your account." retry={() => { void query.refetch(); }} />}{error && <ErrorNotice message={error} />}
    {query.data && <div className="cart-layout"><div className="panel"><div className="order-card-heading"><h2>{query.data.deliveryDate} · {query.data.meal}</h2><span className={`order-status status-${query.data.status.toLowerCase()}`}>{query.data.status}</span></div><p className="order-number">{query.data.orderNumber}</p><h3>Deliver to</h3><p>{query.data.addressText}</p><h3>Your items</h3>{query.data.items.map((item, index) => <article className="ordered-item" key={index}><h4>{item.quantity} × {item.dishName}</h4><p>{item.selectedOptions.map(option => `${option.optionName} (+¥${money(option.extraCents)})`).join(' · ') || 'Standard preparation'}</p><p>¥{money(item.unitPriceCents)} each · Subtotal ¥{money(item.subtotalCents)}</p></article>)}</div><aside className="panel cart-summary"><h2>Order total</h2><p>{query.data.totalQuantity} portions</p><p className="cart-total"><strong>¥{money(query.data.totalCents)}</strong></p>{query.data.status === 'Pending' && <><button type="button" className="secondary" disabled={busy} onClick={() => { void cancel(); }}>{busy ? 'Checking cancellation…' : 'Cancel order'}</button><p className="form-hint">You can cancel while your order is pending. Cancelled portions return to the original delivery date.</p></>}{query.data.status === 'Cancelled' && <p>Your order has been cancelled.</p>}<Link to="/menu">Back to menu</Link></aside></div>}
  </section>;
}
