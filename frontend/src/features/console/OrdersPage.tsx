import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '../../shared/api/client';
import type { AdminOrder, AdminOrderPage, StatusChange } from '../../shared/api/types';
import { money } from '../../shared/values';
import { ErrorNotice, Loading } from '../../shared/ui/Status';
import { useSession } from '../auth/session';
import { ConsoleDialog, Pagination } from './consoleShared';
function OrderDetails({ id, onClose }: { id: string; onClose: () => void }) {
  const { user } = useSession(), queries = useQueryClient(), [busy, setBusy] = useState(false), [error, setError] = useState('');
  const key = ['admin-orders', user!.userId, 'detail', id];const query = useQuery({ queryKey: key, queryFn: ({ signal }) => api<AdminOrder>(`/admin/orders/${id}`, { signal }) });
  async function transition(status: StatusChange['status']) {
    if (!query.data) return;setBusy(true);setError('');try {
      const result = await api<AdminOrder>(`/admin/orders/${id}/status`, { method: 'PATCH', json: { status, expectedVersion: query.data.version } });queries.setQueryData(key, result);
      for (const prefix of ['admin-orders', 'orders', 'active-order', 'menu', 'admin-menus', 'dashboard']) void queries.invalidateQueries({ queryKey: [prefix] });
    } catch (failure) { setError(failure instanceof ApiError && failure.code === 'VERSION_CONFLICT' ? 'This order changed. Review its current status before trying again.' : failure instanceof Error ? failure.message : 'The status could not be confirmed. Review the current order.');await query.refetch(); }finally { setBusy(false); }
  }
  const order = query.data;
  return <ConsoleDialog title="Order details" onClose={onClose} busy={busy}>{query.isPending && <Loading>Loading order…</Loading>}{query.isError && <ErrorNotice message="The current order could not be loaded." retry={() => { void query.refetch(); }} />}{error && <ErrorNotice message={error} />}
    {order && <><p className="order-number">{order.orderNumber}</p><div className="order-card-heading"><h3>{order.deliveryDate} · {order.meal}</h3><span className={`order-status status-${order.status.toLowerCase()}`}>{order.status}</span></div><dl className="order-customer"><dt>Customer</dt><dd>{order.customer.email}</dd><dt>Delivery address</dt><dd>{order.addressText}</dd></dl><h3>Items at purchase</h3>{order.items.map((item, i) => <article className="ordered-item" key={i}><h4>{item.quantity} × {item.dishName}</h4><p>{item.selectedOptions.map(o => `${o.optionName} (+¥${money(o.extraCents)})`).join(' · ') || 'Standard preparation'}</p><p>¥{money(item.unitPriceCents)} each · Subtotal ¥{money(item.subtotalCents)}</p></article>)}<p className="cart-total">Total <strong>¥{money(order.totalCents)}</strong></p>
      <div className="form-actions">{order.status === 'Pending' && <><button type="button" disabled={busy || query.isError} onClick={() => { void transition('Confirmed'); }}>Confirm order</button><button type="button" className="secondary" disabled={busy || query.isError} onClick={() => { void transition('Cancelled'); }}>Cancel order</button></>}{order.status === 'Confirmed' && <button type="button" disabled={busy || query.isError} onClick={() => { void transition('Completed'); }}>Complete order</button>}</div>{busy && <Loading>Updating order…</Loading>}</>}
  </ConsoleDialog>;
}
export function OrdersPage() {
  const { user } = useSession();const [date, setDate] = useState(''), [meal, setMeal] = useState(''), [status, setStatus] = useState(''), [page, setPage] = useState(0), [selected, setSelected] = useState<string | null>(null);
  const params = new URLSearchParams({ page: String(page), size: '24' });if (date) params.set('deliveryDate', date);if (meal) params.set('meal', meal);if (status) params.set('status', status);
  const query = useQuery({ queryKey: ['admin-orders', user!.userId, 'list', params.toString()], queryFn: ({ signal }) => api<AdminOrderPage>(`/admin/orders?${params}`, { signal }) });
  return <section><div className="page-heading"><div><p className="eyebrow">ORDER OPERATIONS</p><h1>Manage orders</h1><p>Review every order and keep its status up to date.</p></div><button className="secondary" type="button" disabled={query.isFetching} onClick={() => { void query.refetch(); }}>Refresh orders</button></div>
    <div className="console-filters"><label className="field">Delivery date<input type="date" value={date} onChange={e => { setDate(e.target.value);setPage(0); }} /></label><label className="field">Meal<select value={meal} onChange={e => { setMeal(e.target.value);setPage(0); }}><option value="">All meals</option><option>Lunch</option><option>Dinner</option></select></label><label className="field">Status<select value={status} onChange={e => { setStatus(e.target.value);setPage(0); }}><option value="">All statuses</option>{['Pending', 'Confirmed', 'Completed', 'Cancelled'].map(s => <option key={s}>{s}</option>)}</select></label></div>
    {query.isPending && <Loading>Loading orders…</Loading>}{query.isError && <ErrorNotice message="Orders could not be refreshed. Any displayed results are from the previous successful load." retry={() => { void query.refetch(); }} />}
    {query.data && <><div className="table-scroll"><table className="console-table"><caption>{query.data.totalElements} orders</caption><thead><tr><th scope="col">Order</th><th scope="col">Delivery</th><th scope="col">Customer</th><th scope="col">Amount</th><th scope="col">Status</th><th scope="col">Action</th></tr></thead><tbody>{query.data.items.map(order => <tr key={order.orderId}><th scope="row" className="order-number">{order.orderNumber}</th><td>{order.deliveryDate}<br />{order.meal}</td><td>{order.customer.email}</td><td>¥{money(order.totalCents)}</td><td><span className={`order-status status-${order.status.toLowerCase()}`}>{order.status}</span></td><td><button className="secondary" type="button" aria-label={`View ${order.orderNumber}`} onClick={() => setSelected(order.orderId)}>View</button></td></tr>)}</tbody></table></div>{!query.data.items.length && <p className="empty-state">No orders match these filters.</p>}<Pagination page={page} size={24} total={query.data.totalElements} onChange={setPage} /></>}
    {selected && <OrderDetails key={selected} id={selected} onClose={() => setSelected(null)} />}
  </section>;
}
