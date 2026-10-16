import { useState } from 'react';
import { Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../shared/api/client';
import type { OrderPage, OrderStatus } from '../../shared/api/types';
import { money } from '../../shared/values';
import { ErrorNotice, Loading } from '../../shared/ui/Status';
import { useSession } from '../auth/session';
export function OrdersPage() {
  const { user } = useSession();const [page, setPage] = useState(0), [status, setStatus] = useState<OrderStatus | ''>('');
  const params = new URLSearchParams({ page: String(page), size: '24' });if (status) params.set('status', status);
  const orders = useQuery({ queryKey: ['orders', user!.userId, params.toString()], queryFn: ({ signal }) => api<OrderPage>(`/orders?${params}`, { signal }) });
  return <section><div className="page-heading"><div><p className="eyebrow">YOUR WORKDAY TABLE</p><h1>Your orders</h1><p>Your meals, delivery details and current order status.</p></div><Link to="/menu">Browse menu</Link></div><div className="field order-filter"><label htmlFor="order-status">Order status</label><select id="order-status" value={status} onChange={event => { setStatus(event.target.value as OrderStatus | '');setPage(0); }}><option value="">All orders</option>{['Pending', 'Confirmed', 'Completed', 'Cancelled'].map(value => <option key={value}>{value}</option>)}</select></div>
    {orders.isPending && <Loading>Loading your orders…</Loading>}{orders.isError && <ErrorNotice message="Your orders could not be loaded." retry={() => { void orders.refetch(); }} />}
    {orders.data && <>{!orders.data.items.length && <div className="panel empty-state"><h2>No orders found</h2><p>Try another status or explore the menu for your next meal.</p></div>}<div className="order-list">{orders.data.items.map(order => <article className="panel order-card" key={order.orderId}><div className="order-card-heading"><h2>{order.deliveryDate} · {order.meal}</h2><span className={`order-status status-${order.status.toLowerCase()}`}>{order.status}</span></div><p className="order-number">{order.orderNumber}</p><p>{order.items.map(item => `${item.quantity} × ${item.dishName}`).join(' · ')}</p><p>{order.addressText}</p><div className="order-card-heading"><strong>¥{money(order.totalCents)}</strong><Link to={`/orders/${order.orderId}`}>View order</Link></div></article>)}</div>{orders.data.totalElements > 24 && <nav className="pagination" aria-label="Order pages"><button type="button" disabled={page === 0} onClick={() => setPage(value => value - 1)}>Previous</button><span>Page {page + 1} of {Math.ceil(orders.data.totalElements / 24)}</span><button type="button" disabled={(page + 1) * 24 >= orders.data.totalElements} onClick={() => setPage(value => value + 1)}>Next</button></nav>}</>}
  </section>;
}
